"""Generate and batch-review the bundled corpus through an OpenAI-compatible API.

This intentionally has no template fallback. If the LLM endpoint is unavailable,
generation stops rather than producing synthetic concatenations.
"""
from __future__ import annotations

import argparse
import csv
import hashlib
import json
import os
import re
import time
import urllib.request
from pathlib import Path

FIELDS = ["sentence_id", "text", "category", "difficulty", "length_bucket", "notes"]
DEFAULT_BASE = "http://127.0.0.1:8080/v1"

SHORT_CATEGORIES = ["家庭", "人际", "饮食", "时间", "地点", "出行", "工作", "学习", "健康", "情绪", "交易", "天气", "活动", "日常"]
SITUATIONAL_CATEGORIES = SHORT_CATEGORIES
NARRATIVE_CATEGORIES = ["家庭", "人际", "出行", "工作", "日常", "乡土生活"]


class LLMError(RuntimeError):
    pass


def _json_from_text(text: str):
    text = text.strip()
    if text.startswith("```"):
        text = re.sub(r"^```(?:json)?\s*", "", text)
        text = re.sub(r"\s*```$", "", text)
    try:
        return json.loads(text)
    except json.JSONDecodeError:
        start = min([p for p in (text.find("["), text.find("{")) if p >= 0], default=-1)
        if start < 0:
            raise LLMError("LLM did not return JSON")
        end = max(text.rfind("]"), text.rfind("}"))
        if end <= start:
            raise LLMError("LLM returned truncated JSON")
        return json.loads(text[start : end + 1])


class ChatClient:
    def __init__(self, base_url: str, model: str, api_key: str):
        self.base_url = base_url.rstrip("/")
        self.model = model
        self.api_key = api_key

    def call(self, prompt: str, max_tokens: int = 12000, retries: int = 5):
        payload = {
            "model": self.model,
            "messages": [
                {"role": "system", "content": "你是严格的中文母语编辑。只输出用户要求的 JSON，不要解释。"},
                {"role": "user", "content": prompt},
            ],
            "temperature": 0.8,
            "max_tokens": max_tokens,
            "stream": True,
        }
        request = urllib.request.Request(
            self.base_url + "/chat/completions",
            data=json.dumps(payload, ensure_ascii=False).encode("utf-8"),
            headers={"Authorization": "Bearer " + self.api_key, "Content-Type": "application/json"},
        )
        last = None
        for attempt in range(retries):
            try:
                pieces = []
                with urllib.request.urlopen(request, timeout=300) as response:
                    for raw_line in response:
                        if not raw_line.startswith(b"data: "):
                            continue
                        payload = raw_line[6:].strip()
                        if payload == b"[DONE]":
                            break
                        chunk = json.loads(payload.decode("utf-8"))
                        choices = chunk.get("choices") or []
                        if choices:
                            pieces.append(choices[0].get("delta", {}).get("content", ""))
                return _json_from_text("".join(pieces))
            except Exception as exc:
                last = exc
                if attempt + 1 < retries:
                    time.sleep(2 ** attempt)
        raise LLMError(str(last))


def deterministic_valid(text: str) -> bool:
    if not isinstance(text, str):
        return False
    text = text.strip()
    if not 6 <= len(text) <= 80:
        return False
    if not text.endswith(("。", "？", "！")):
        return False
    if re.search(r"第\s*\d+\s*(次|种)|回想|占位|示例|待补", text):
        return False
    if any(x in text for x in ("<", ">", "\\n", "\n", "\r")):
        return False
    return True


def allocate(total: int, categories: list[str]) -> dict[str, int]:
    base, remainder = divmod(total, len(categories))
    return {category: base + (i < remainder) for i, category in enumerate(categories)}


def generate_batch(client: ChatClient, category: str, difficulty: str, count: int, forbidden: list[str]) -> list[str]:
    forbidden_text = json.dumps(forbidden[-120:], ensure_ascii=False)
    prompt = f"""
请生成 {count} 条【{category}】主题的现代标准书面汉语完整句子，用作方言语音采集的统一提示句。
难度类型：{difficulty}。

硬性要求：
1. 每条必须是自然、通顺、独立完整的一句话，像真实中国人会说或会写的话。
2. 不能通过给同一个句子加时间前缀、序号、括号后缀来凑数；每条内容和语义都要真正不同。
3. 不要出现“第几次”“第几种情况”“回想”“示例”“句子一”“占位”等元话语。
4. 避免政治、违法、隐私、医疗诊断、仇恨、色情和未成年人危险内容。
5. 句子长度 6 到 80 个汉字，使用自然中文标点，以“。”、“？”或“！”结尾。
6. 只输出 JSON 字符串数组，数组长度必须正好是 {count}，不要 Markdown，不要编号。

以下是近期已生成句子，不能重复或只改一两个字：
{forbidden_text}
"""
    value = client.call(prompt, max_tokens=max(6000, count * 90))
    if not isinstance(value, list):
        raise LLMError("generator JSON is not an array")
    return [x.strip() for x in value if isinstance(x, str) and x.strip()]


def review_batch(client: ChatClient, category: str, difficulty: str, texts: list[str]) -> list[dict]:
    prompt = f"""
请逐条复核下面这批【{category}】主题、难度为【{difficulty}】的中文提示句。
每条检查：语法是否通顺、语义是否完整、用词是否自然、标点是否正确、是否适合作为方言自然表达提示、是否像模板拼接或重复改写。
如果不合格，给出一条完整自然的替换句。为了减少输出，只返回紧凑 JSON 对象：
{{"ok_indices":[通过的输入下标],"replacements":[{{"index":不通过的输入下标,"text":"完整自然的替换句"}}]}}
下标从 0 开始。没有替换项时 replacements 必须是空数组。不要输出 reason，不要输出其它字段。

输入：
{json.dumps(texts, ensure_ascii=False)}
"""
    value = client.call(prompt, max_tokens=max(2500, len(texts) * 35))
    if not isinstance(value, dict):
        raise LLMError("reviewer returned non-object")
    ok_indices = {int(i) for i in value.get("ok_indices", [])}
    replacements = {int(item["index"]): str(item.get("text", "")).strip() for item in value.get("replacements", [])}
    return [{"ok": i in ok_indices, "replacement": replacements.get(i, "")} for i in range(len(texts))]


def build_corpus(client: ChatClient, target: Path, audit_path: Path, batch_size: int = 50):
    plans = []
    for difficulty, total, categories in [
        ("short", 3500, SHORT_CATEGORIES),
        ("situational", 1000, SITUATIONAL_CATEGORIES),
        ("narrative", 500, NARRATIVE_CATEGORIES),
    ]:
        for category, count in allocate(total, categories).items():
            plans.append((difficulty, category, count))

    seen: set[str] = set()
    rows: list[tuple[str, str, str, str, str]] = []
    audit = []
    for difficulty, category, total in plans:
        remaining = total
        while remaining:
            requested = min(batch_size, remaining)
            accepted: list[str] = []
            attempts = 0
            while len(accepted) < requested:
                attempts += 1
                if attempts > 5:
                    raise LLMError(f"could not fill batch for {difficulty}/{category}")
                candidates = generate_batch(client, category, difficulty, requested - len(accepted) + 10, list(seen))
                candidates = [x for x in candidates if deterministic_valid(x) and x not in seen and x not in accepted]
                if not candidates:
                    continue
                candidates = candidates[: requested - len(accepted)]
                review = review_batch(client, category, difficulty, candidates)
                replacements = []
                for text, verdict in zip(candidates, review):
                    ok = bool(verdict.get("ok")) and not verdict.get("replacement")
                    replacement = str(verdict.get("replacement") or "").strip()
                    if ok:
                        replacements.append(text)
                    elif deterministic_valid(replacement) and replacement not in seen and replacement not in accepted:
                        replacements.append(replacement)
                    audit.append({"difficulty": difficulty, "category": category, "input": text, "verdict": verdict})
                for text in replacements:
                    if len(accepted) < requested and text not in accepted:
                        accepted.append(text)
                time.sleep(0.2)
            for text in accepted:
                seen.add(text)
                bucket = "long" if difficulty == "narrative" else "medium" if difficulty == "situational" else "short"
                rows.append((text, category, difficulty, bucket, "llm_generated_and_reviewed"))
            remaining -= requested
            print(f"completed {len(rows)}/5000 ({difficulty}/{category})", flush=True)

    target.parent.mkdir(parents=True, exist_ok=True)
    with target.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=FIELDS, lineterminator="\n")
        writer.writeheader()
        for i, (text, category, difficulty, bucket, notes) in enumerate(rows, 1):
            writer.writerow({"sentence_id": f"S{i:06d}", "text": text, "category": category, "difficulty": difficulty, "length_bucket": bucket, "notes": notes})
    digest = hashlib.sha256()
    with target.open("r", encoding="utf-8", newline="") as handle:
        for row in csv.DictReader(handle):
            digest.update(("\x1f".join(row.get(k, "") for k in FIELDS) + "\n").encode("utf-8"))
    audit_path.write_text(json.dumps({"review_count": len(audit), "corpus_hash": digest.hexdigest(), "items": audit}, ensure_ascii=False, indent=2), encoding="utf-8")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--audit", type=Path, required=True)
    parser.add_argument("--batch-size", type=int, default=50)
    args = parser.parse_args()
    api_key = os.environ.get("OPENAI_API_KEY")
    if not api_key:
        raise SystemExit("OPENAI_API_KEY is required")
    base = os.environ.get("OPENAI_BASE_URL", DEFAULT_BASE)
    model = os.environ.get("OPENAI_MODEL", "gpt-6-luna")
    build_corpus(ChatClient(base, model, api_key), args.output, args.audit, args.batch_size)


if __name__ == "__main__":
    main()
