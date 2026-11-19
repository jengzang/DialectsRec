import csv
import hashlib
import tempfile
import unittest
from pathlib import Path

from tools.generate_corpus import generate, canonical_hash, EXPECTED_COUNT


class CorpusGenerationTests(unittest.TestCase):
    def test_generates_stable_5000_sentence_corpus(self):
        with tempfile.TemporaryDirectory() as d:
            out = Path(d) / "sentences.csv"
            manifest = Path(d) / "manifest.json"
            generate(out, manifest)
            with out.open(encoding="utf-8", newline="") as handle:
                rows = list(csv.DictReader(handle))
            self.assertEqual(EXPECTED_COUNT, len(rows))
            self.assertEqual([f"S{i:06d}" for i in range(1, EXPECTED_COUNT + 1)], [r["sentence_id"] for r in rows])
            self.assertEqual(3500, sum(r["difficulty"] == "short" for r in rows))
            self.assertEqual(1000, sum(r["difficulty"] == "situational" for r in rows))
            self.assertEqual(500, sum(r["difficulty"] == "narrative" for r in rows))
            self.assertEqual(EXPECTED_COUNT, len({r["text"] for r in rows}))
            self.assertEqual(canonical_hash(out), __import__("json").loads(manifest.read_text())["corpus_hash"])

    def test_generation_is_deterministic(self):
        with tempfile.TemporaryDirectory() as d:
            a, b = Path(d) / "a.csv", Path(d) / "b.csv"
            generate(a, Path(d) / "a.json")
            generate(b, Path(d) / "b.json")
            self.assertEqual(a.read_bytes(), b.read_bytes())
            self.assertEqual(hashlib.sha256(a.read_bytes()).hexdigest(), hashlib.sha256(b.read_bytes()).hexdigest())


if __name__ == "__main__":
    unittest.main()
