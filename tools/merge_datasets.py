from __future__ import annotations

import argparse
import csv
import hashlib
import json
import shutil
import sys
import zipfile
from dataclasses import dataclass, field
from pathlib import Path

try:
    from .validate_dataset import validate_dataset
except ImportError:
    from validate_dataset import validate_dataset

@dataclass
class MergeResult:
    conflicts: list[str] = field(default_factory=list)
    speakers: int = 0
    recordings: int = 0


def _json(z, name): return json.loads(z.read(name))
def _rows(z, name):
    with z.open(name) as raw: return list(csv.DictReader(__import__('io').TextIOWrapper(raw, encoding='utf-8', newline='')))


def merge_datasets(inputs, output: str | Path) -> MergeResult:
    result = MergeResult(); output = Path(output); output.mkdir(parents=True, exist_ok=True); (output / "wav").mkdir(exist_ok=True)
    base_identity = None; sentence_rows = []; speakers = []; recordings = []; progress = []; seen_audio = {}
    for source in inputs:
        check = validate_dataset(source)
        if check.errors: result.conflicts.extend(f"{source}: {e}" for e in check.errors); continue
        with zipfile.ZipFile(source) as z:
            manifest, corpus, speaker = _json(z, "manifest.json"), _json(z, "corpus.json"), _json(z, "speaker.json")
            identity = (corpus.get("corpus_id"), corpus.get("corpus_version"), corpus.get("corpus_hash"))
            if base_identity is None: base_identity = identity; sentence_rows = _rows(z, "sentences.csv")
            elif identity != base_identity: result.conflicts.append(f"{source}: corpus identity mismatch"); continue
            sid = speaker.get("speaker_id", "")
            if any(s.get("speaker_id") == sid and s.get("local_speaker_uuid") != speaker.get("local_speaker_uuid") for s in speakers): result.conflicts.append(f"speaker conflict: {sid}"); continue
            if not any(s.get("speaker_id") == sid for s in speakers): speakers.append(speaker)
            for rec in _rows(z, "recordings.csv"):
                data = z.read(rec["audio_path"]); digest = hashlib.sha256(data).hexdigest(); key = (sid, rec["sentence_id"])
                if key in seen_audio:
                    if seen_audio[key] != digest: result.conflicts.append(f"different audio for {sid}/{rec['sentence_id']}")
                    continue
                seen_audio[key] = digest; target = output / "wav" / sid / f"{rec['sentence_id']}.wav"; target.parent.mkdir(parents=True, exist_ok=True); target.write_bytes(data)
                rec = dict(rec); rec["audio_path"] = f"wav/{sid}/{rec['sentence_id']}.wav"; recordings.append(rec)
            for row in _rows(z, "progress.csv"):
                row = dict(row); row["speaker_id"] = sid; progress.append(row)
    with (output / "sentences.csv").open("w", encoding="utf-8", newline="") as f:
        if sentence_rows: w = csv.DictWriter(f, fieldnames=sentence_rows[0].keys()); w.writeheader(); w.writerows(sentence_rows)
    with (output / "speakers.csv").open("w", encoding="utf-8", newline="") as f:
        fields = ["speaker_id", "local_speaker_uuid"]; w = csv.DictWriter(f, fieldnames=fields); w.writeheader(); w.writerows({k:s.get(k,"") for k in fields} for s in speakers)
    if recordings:
        with (output / "recordings.csv").open("w", encoding="utf-8", newline="") as f: w = csv.DictWriter(f, fieldnames=recordings[0].keys()); w.writeheader(); w.writerows(recordings)
    if progress:
        with (output / "progress.csv").open("w", encoding="utf-8", newline="") as f: w = csv.DictWriter(f, fieldnames=progress[0].keys()); w.writeheader(); w.writerows(progress)
    (output / "merge_manifest.json").write_text(json.dumps({"corpus": base_identity, "speaker_count": len(speakers), "recording_count": len(recordings), "conflicts": result.conflicts}, ensure_ascii=False, indent=2), encoding="utf-8")
    result.speakers, result.recordings = len(speakers), len(recordings)
    return result


def main(argv=None):
    p = argparse.ArgumentParser(); p.add_argument("inputs", nargs="+"); p.add_argument("--output", required=True); args = p.parse_args(argv)
    result = merge_datasets([Path(x) for x in args.inputs], args.output)
    for conflict in result.conflicts: print(f"CONFLICT: {conflict}")
    print(json.dumps({"speakers": result.speakers, "recordings": result.recordings, "conflicts": len(result.conflicts)}, ensure_ascii=False))
    return 1 if result.conflicts else 0

if __name__ == "__main__": sys.exit(main())
