"""Package and validate the reviewed LLM-generated corpus.

The app ships a checked-in corpus that was generated and reviewed through the
LLM pipeline in :mod:`tools.generate_corpus_llm_parallel`. This module keeps a
small deterministic packaging entry point for CI and release scripts; it does
not contain template expansion or a synthetic fallback.
"""
from __future__ import annotations

import csv
import hashlib
import json
from pathlib import Path

EXPECTED_COUNT = 5000
FIELDS = ["sentence_id", "text", "category", "difficulty", "length_bucket", "notes"]
DEFAULT_SOURCE = Path(__file__).resolve().parents[1] / "app" / "src" / "main" / "assets" / "corpus_v1" / "sentences.csv"


def canonical_hash(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("r", encoding="utf-8", newline="") as handle:
        for row in csv.DictReader(handle):
            digest.update(("\x1f".join(row.get(field, "") for field in FIELDS) + "\n").encode("utf-8"))
    return digest.hexdigest()


def validate(path: Path) -> list[dict[str, str]]:
    with path.open("r", encoding="utf-8", newline="") as handle:
        rows = list(csv.DictReader(handle))
    if len(rows) != EXPECTED_COUNT:
        raise ValueError(f"expected {EXPECTED_COUNT} rows, got {len(rows)}")
    if len({row["text"] for row in rows}) != EXPECTED_COUNT:
        raise ValueError("corpus contains duplicate text")
    expected_ids = [f"S{i:06d}" for i in range(1, EXPECTED_COUNT + 1)]
    if [row["sentence_id"] for row in rows] != expected_ids:
        raise ValueError("sentence ids are not contiguous")
    for row in rows:
        text = row["text"]
        if not 6 <= len(text) <= 80 or not text.endswith(("。", "？", "！")):
            raise ValueError(f"invalid sentence: {text}")
        if any(token in text for token in ("第1次", "第2次", "第几次", "回想", "占位", "示例", "待补")):
            raise ValueError(f"template/meta wording found: {text}")
    counts = {difficulty: sum(row["difficulty"] == difficulty for row in rows) for difficulty in ("short", "situational", "narrative")}
    if counts != {"short": 3500, "situational": 1000, "narrative": 500}:
        raise ValueError(f"unexpected difficulty counts: {counts}")
    return rows


def generate(output: Path, manifest: Path, source: Path = DEFAULT_SOURCE) -> None:
    """Copy the reviewed corpus and create a matching manifest."""
    validate(source)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_bytes(source.read_bytes())
    payload = {
        "corpus_id": "dialect_general",
        "corpus_version": "2.0",
        "sentence_count": EXPECTED_COUNT,
        "corpus_hash": canonical_hash(output),
        "hash_algorithm": "sha256(join(fields, U+001F) + LF)",
        "schema_version": "1.0",
    }
    manifest.parent.mkdir(parents=True, exist_ok=True)
    manifest.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    import argparse

    parser = argparse.ArgumentParser()
    parser.add_argument("output", type=Path)
    parser.add_argument("manifest", type=Path)
    parser.add_argument("--source", type=Path, default=DEFAULT_SOURCE)
    args = parser.parse_args()
    generate(args.output, args.manifest, args.source)
