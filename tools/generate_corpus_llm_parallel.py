"""Parallel LLM corpus generation with an independent review per batch."""
from __future__ import annotations

import argparse
import csv
import hashlib
import json
import os
import threading
from concurrent.futures import ThreadPoolExecutor, as_completed
from pathlib import Path

from .generate_corpus_llm import (
    ChatClient,
    NARRATIVE_CATEGORIES,
    SHORT_CATEGORIES,
    SITUATIONAL_CATEGORIES,
    deterministic_valid,
    generate_batch,
    review_batch,
)

FIELDS = ["sentence_id", "text", "category", "difficulty", "length_bucket", "notes"]


def _allocate(total, categories):
    base, remainder = divmod(total, len(categories))
    return {c: base + (i < remainder) for i, c in enumerate(categories)}


def _tasks():
    tasks = []
    for difficulty, total, categories in [
        ("short", 3500, SHORT_CATEGORIES),
        ("situational", 1000, SITUATIONAL_CATEGORIES),
        ("narrative", 500, NARRATIVE_CATEGORIES),
    ]:
        for category, total_for_category in _allocate(total, categories).items():
            left = total_for_category
            while left:
                n = min(100, left)
                tasks.append((difficulty, category, n))
                left -= n
    return tasks


def _one(client, difficulty, category, target, task_id):
    accepted, audit = [], []
    for attempt in range(1, 6):
        if len(accepted) >= target:
            break
        candidates = generate_batch(client, category, difficulty, target - len(accepted) + 12, accepted)
        candidates = [x for x in candidates if deterministic_valid(x) and x not in accepted]
        candidates = candidates[: target - len(accepted)]
        if not candidates:
            continue
        for offset in range(0, len(candidates), 25):
            review_slice = candidates[offset : offset + 25]
            verdicts = review_batch(client, category, difficulty, review_slice)
            for text, verdict in zip(review_slice, verdicts):
                replacement = str(verdict.get("replacement") or "").strip()
                ok = bool(verdict.get("ok")) and not replacement
                selected = text if ok else replacement
                if deterministic_valid(selected) and selected not in accepted:
                    accepted.append(selected)
                audit.append({"task": task_id, "attempt": attempt, "category": category, "difficulty": difficulty, "input": text, "verdict": verdict})
    if len(accepted) < target:
        raise RuntimeError(f"batch {task_id} only produced {len(accepted)}/{target}")
    return accepted[:target], audit


def _repair_global_duplicates(client, rows, audits, expected):
    """Replace duplicate texts after all parallel batches have completed.

    Parallel workers cannot see each other's in-flight output, so a small number
    of cross-batch duplicates is possible even though every batch is reviewed.
    Repair those rows with fresh LLM-generated and reviewed sentences while
    preserving the original category and difficulty allocation.
    """
    seen = set()
    unique = []
    missing = []
    for row in rows:
        if row[0] in seen:
            missing.append(row)
        else:
            seen.add(row[0])
            unique.append(row)
    if not missing:
        return unique, audits

    # Keep the replacement in the same bucket/category as the duplicate row so
    # the configured 3,500/1,000/500 allocation remains unchanged.
    grouped = {}
    for row in missing:
        grouped.setdefault((row[1], row[2], row[3]), 0)
        grouped[(row[1], row[2], row[3])] += 1

    for (category, difficulty, bucket), needed in grouped.items():
        accepted = []
        for attempt in range(1, 8):
            if len(accepted) >= needed:
                break
            request_count = needed - len(accepted) + 12
            candidates = generate_batch(client, category, difficulty, request_count, list(seen)[-120:])
            candidates = [
                text for text in candidates
                if deterministic_valid(text) and text not in seen and text not in accepted
            ]
            for offset in range(0, len(candidates), 25):
                review_slice = candidates[offset : offset + 25]
                verdicts = review_batch(client, category, difficulty, review_slice)
                for text, verdict in zip(review_slice, verdicts):
                    replacement = str(verdict.get("replacement") or "").strip()
                    ok = bool(verdict.get("ok")) and not replacement
                    selected = text if ok else replacement
                    if deterministic_valid(selected) and selected not in seen and selected not in accepted:
                        accepted.append(selected)
                    audits.append({
                        "task": "global_repair",
                        "attempt": attempt,
                        "category": category,
                        "difficulty": difficulty,
                        "input": text,
                        "verdict": verdict,
                    })
                    if len(accepted) >= needed:
                        break
                if len(accepted) >= needed:
                    break
        if len(accepted) < needed:
            raise RuntimeError(
                f"global duplicate repair for {difficulty}/{category} only produced "
                f"{len(accepted)}/{needed} rows"
            )
        for text in accepted:
            unique.append((text, category, difficulty, bucket, "llm_generated_and_reviewed_global_repair"))
            seen.add(text)

    if len(unique) != expected or len({row[0] for row in unique}) != expected:
        raise RuntimeError(f"global exact-text dedup left {len(unique)}/{expected} rows after repair")
    return unique, audits


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--audit", type=Path, required=True)
    parser.add_argument("--workers", type=int, default=8)
    parser.add_argument("--checkpoint", type=Path)
    args = parser.parse_args()
    key = os.environ.get("OPENAI_API_KEY")
    if not key:
        raise SystemExit("OPENAI_API_KEY is required")
    client_args = (os.environ.get("OPENAI_BASE_URL", "http://127.0.0.1:8080/v1"), os.environ.get("OPENAI_MODEL", "gpt-6-luna"), key)
    tasks = _tasks()
    checkpoint = args.checkpoint or args.output.with_suffix(".checkpoint.json")
    completed = {}
    if checkpoint.exists():
        completed = json.loads(checkpoint.read_text(encoding="utf-8"))
    results, audits = [], []
    pending = [x for i, x in enumerate(tasks) if str(i) not in completed]
    for i, value in completed.items():
        results.extend(tuple(row) for row in value["rows"])
        audits.extend(value["audit"])
    print(f"resuming: {len(completed)}/{len(tasks)} batches already complete", flush=True)
    with ThreadPoolExecutor(max_workers=args.workers) as pool:
        futures = {pool.submit(_one, ChatClient(*client_args), d, c, n, i): (d, c, n, i) for i, (d, c, n) in enumerate(tasks) if str(i) not in completed}
        for future in as_completed(futures):
            d, c, n, i = futures[future]
            values, audit = future.result()
            results.extend((text, c, d, "long" if d == "narrative" else "medium" if d == "situational" else "short", "llm_generated_and_reviewed") for text in values)
            audits.extend(audit)
            completed[str(i)] = {"rows": [[text, c, d, "long" if d == "narrative" else "medium" if d == "situational" else "short", "llm_generated_and_reviewed"] for text in values], "audit": audit}
            checkpoint.write_text(json.dumps(completed, ensure_ascii=False), encoding="utf-8")
            print(f"completed batch {i + 1}/{len(tasks)}: {d}/{c}/{n}; accepted={len(results)}", flush=True)

    seen = set(); rows = []
    for row in results:
        if row[0] in seen:
            continue
        seen.add(row[0]); rows.append(row)
    expected = 5000
    if len(rows) != expected:
        rows, audits = _repair_global_duplicates(ChatClient(*client_args), results, audits, expected)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    with args.output.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=FIELDS, lineterminator="\n"); writer.writeheader()
        for i, (text, category, difficulty, bucket, notes) in enumerate(rows, 1):
            writer.writerow({"sentence_id": f"S{i:06d}", "text": text, "category": category, "difficulty": difficulty, "length_bucket": bucket, "notes": notes})
    digest = hashlib.sha256()
    with args.output.open("r", encoding="utf-8", newline="") as handle:
        for row in csv.DictReader(handle): digest.update(("\x1f".join(row.get(k, "") for k in FIELDS) + "\n").encode())
    args.audit.write_text(json.dumps({"model": client_args[1], "batch_count": len(tasks), "review_count": len(audits), "corpus_hash": digest.hexdigest(), "items": audits}, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"wrote {args.output} hash={digest.hexdigest()}")


if __name__ == "__main__":
    main()
