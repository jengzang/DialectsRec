import hashlib
import json
import tempfile
import unittest
from pathlib import Path

from tools.generate_corpus import EXPECTED_COUNT, canonical_hash, generate, validate

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "app" / "src" / "main" / "assets" / "corpus_v1" / "sentences.csv"
MANIFEST = ROOT / "app" / "src" / "main" / "assets" / "corpus_v1" / "manifest.json"


class CorpusGenerationTests(unittest.TestCase):
    def test_bundled_corpus_is_fully_validated(self):
        rows = validate(SOURCE)
        self.assertEqual(EXPECTED_COUNT, len(rows))
        self.assertEqual({"short": 3500, "situational": 1000, "narrative": 500}, {
            difficulty: sum(row["difficulty"] == difficulty for row in rows)
            for difficulty in ("short", "situational", "narrative")
        })
        manifest = json.loads(MANIFEST.read_text(encoding="utf-8"))
        self.assertEqual("2.0", manifest["corpus_version"])
        self.assertEqual(canonical_hash(SOURCE), manifest["corpus_hash"])

    def test_packaging_is_deterministic(self):
        with tempfile.TemporaryDirectory() as directory:
            out = Path(directory) / "sentences.csv"
            manifest = Path(directory) / "manifest.json"
            generate(out, manifest)
            self.assertEqual(SOURCE.read_bytes(), out.read_bytes())
            self.assertEqual(canonical_hash(out), json.loads(manifest.read_text(encoding="utf-8"))["corpus_hash"])
            self.assertEqual(hashlib.sha256(SOURCE.read_bytes()).hexdigest(), hashlib.sha256(out.read_bytes()).hexdigest())


if __name__ == "__main__":
    unittest.main()
