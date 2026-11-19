from __future__ import annotations

import argparse
import csv
import hashlib
import io
import json
import sys
import wave
import zipfile
from dataclasses import dataclass, field
from pathlib import Path

REQUIRED = ["manifest.json", "corpus.json", "speaker.json", "sentences.csv", "recordings.csv", "progress.csv"]
SENTENCE_FIELDS = ["sentence_id", "text", "category", "difficulty", "length_bucket", "notes"]

@dataclass
class ValidationResult:
    errors: list[str] = field(default_factory=list)
    warnings: list[str] = field(default_factory=list)
    stats: dict[str, int | float] = field(default_factory=dict)


def canonical_sentence_hash(rows):
    digest = hashlib.sha256()
    for row in rows:
        digest.update("\x1f".join(row.get(k, "") for k in SENTENCE_FIELDS).encode("utf-8"))
        digest.update(b"\n")
    return digest.hexdigest()


def _csv(z, name, result):
    try:
        return list(csv.DictReader(io.TextIOWrapper(z.open(name), encoding="utf-8", newline="")))
    except Exception as e:
        result.errors.append(f"{name}: cannot parse CSV: {e}")
        return []


def validate_dataset(path: str | Path) -> ValidationResult:
    result = ValidationResult()
    try:
        z = zipfile.ZipFile(path)
    except Exception as e:
        result.errors.append(f"cannot open ZIP: {e}")
        return result
    with z:
        names = set(z.namelist())
        for required in REQUIRED:
            if required not in names:
                result.errors.append(f"missing {required}")
        if result.errors:
            return result
        try:
            manifest = json.loads(z.read("manifest.json"))
            corpus = json.loads(z.read("corpus.json"))
            speaker = json.loads(z.read("speaker.json"))
        except Exception as e:
            result.errors.append(f"invalid JSON: {e}")
            return result
        sentences = _csv(z, "sentences.csv", result)
        recordings = _csv(z, "recordings.csv", result)
        progress = _csv(z, "progress.csv", result)
        ids = [r.get("sentence_id", "") for r in sentences]
        if len(ids) != len(set(ids)): result.errors.append("duplicate sentence_id")
        if any(not i or not (r.get("text", "") or r.get("prompt_text", "")).strip() for i, r in zip(ids, sentences)): result.errors.append("empty sentence ID or text")
        computed = canonical_sentence_hash(sentences)
        if corpus.get("corpus_hash") != computed: result.errors.append("corpus hash mismatch")
        for key in ("corpus_id", "corpus_version", "corpus_hash"):
            if manifest.get(key) != corpus.get(key): result.errors.append(f"manifest/corpus mismatch: {key}")
        if manifest.get("speaker_id") != speaker.get("speaker_id"): result.errors.append("manifest/speaker mismatch")
        sentence_set = set(ids); recording_ids = set(); paths = set()
        for row in recordings:
            rid, sid, audio = row.get("recording_id", ""), row.get("sentence_id", ""), row.get("audio_path", "")
            if rid in recording_ids: result.errors.append(f"duplicate recording_id: {rid}")
            recording_ids.add(rid)
            if sid not in sentence_set: result.errors.append(f"recording references unknown sentence: {sid}")
            if audio in paths: result.errors.append(f"duplicate audio_path: {audio}")
            paths.add(audio)
            if row.get("status") == "completed":
                if audio not in names: result.errors.append(f"missing audio: {audio}")
                else:
                    try:
                        with wave.open(io.BytesIO(z.read(audio)), "rb") as w:
                            if (w.getnchannels(), w.getsampwidth(), w.getframerate()) != (1, 2, 16000): result.errors.append(f"invalid WAV spec: {audio}")
                            if w.getnframes() == 0: result.errors.append(f"empty WAV: {audio}")
                    except Exception as e: result.errors.append(f"invalid WAV {audio}: {e}")
                if row.get("spoken_transcript", "") and row.get("transcription_status") == "not_annotated": result.errors.append(f"transcript status mismatch: {rid}")
        for row in progress:
            if row.get("sentence_id") not in sentence_set: result.errors.append(f"progress references unknown sentence: {row.get('sentence_id')}")
            if row.get("status") == "completed" and row.get("selected_recording_id") not in recording_ids: result.errors.append(f"completed progress has no recording: {row.get('sentence_id')}")
        result.stats = {"sentences": len(sentences), "recordings": len(recordings), "progress": len(progress), "completed": sum(r.get("status") == "completed" for r in recordings), "skipped": sum(r.get("status") == "skipped" for r in progress)}
    return result


def main(argv=None):
    parser = argparse.ArgumentParser(description="Validate a DialectRecorder ZIP")
    parser.add_argument("zip_path", type=Path)
    args = parser.parse_args(argv)
    result = validate_dataset(args.zip_path)
    for error in result.errors: print(f"ERROR: {error}")
    for warning in result.warnings: print(f"WARNING: {warning}")
    print(json.dumps(result.stats, ensure_ascii=False, sort_keys=True))
    return 1 if result.errors else 0

if __name__ == "__main__": sys.exit(main())
