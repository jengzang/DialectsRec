"""Contract tests for the fully reviewed source corpus, not semantic keyword checks."""
import csv
import hashlib
import json
from pathlib import Path
import tempfile
import unittest
from tools import review_jnu_corpus as review

ROOT=Path(__file__).resolve().parents[1]
ASSETS=ROOT/'app/src/main/assets'
AUDIT=ROOT/'docs/corpus-review/jnu-v1/audit.json'


@unittest.skipUnless(AUDIT.is_file(), "Licensed JNU corpus is local-only; supply it to run private-data tests")
class JnuAssetsTests(unittest.TestCase):
    def setUp(self):
        self.audit=json.loads(AUDIT.read_text(encoding='utf-8'))
        self.items=self.audit['items']

    def test_every_source_row_has_a_final_model_verdict(self):
        self.assertEqual(7728,len(self.items))
        self.assertEqual(7728,len({r['key'] for r in self.items}))
        self.assertEqual({'chaozhou':2230,'suzhou':5498},{source:sum(r['source']==source for r in self.items) for source in review.SOURCE_NAMES})
        self.assertEqual(hashlib.sha256(review.PROMPT.encode()).hexdigest(),self.audit['prompt_sha256'])
        for r in self.items:
            self.assertEqual(r['key'],r['first_pass']['key'])
            self.assertEqual(r['key'],r['final']['key'])
            self.assertIn(r['final']['label'],('G','L'))
            self.assertTrue(r['final']['reason'])

    def test_packaged_sources_preserve_exact_text_and_metadata_once(self):
        by_key={r['key']:r for r in self.items};seen=set()
        for part in ('chaozhou','suzhou','local'):
            directory=ASSETS/('corpus_jnu_'+part)
            with (directory/'sentences.csv').open(encoding='utf-8',newline='') as f:rows=list(csv.DictReader(f))
            manifest=json.loads((directory/'manifest.json').read_text())
            self.assertEqual(len(rows),manifest['sentence_count'])
            digest=hashlib.sha256()
            for row in rows:
                key=row['sentence_id'];self.assertNotIn(key,seen);seen.add(key)
                source=by_key[key];self.assertEqual(source['text'],row['text'])
                meta=json.loads(row['notes']);self.assertEqual(source['id'],meta['source_id']);self.assertEqual(source['line'],meta['source_line'])
                self.assertEqual(source['final']['label'],meta['locality'])
                self.assertEqual(source['final']['reason'],meta['reason'])
                self.assertEqual(source['final']['flags'],meta['quality_flags'])
                self.assertEqual('L' if part=='local' else 'G',meta['locality'])
                if part=='local':self.assertEqual(review.SOURCE_NAMES[source['source']],row['category'])
                else:self.assertEqual(part,source['source'])
                digest.update(('\x1f'.join(row[k] for k in review.FIELDS)+'\n').encode())
            self.assertEqual(digest.hexdigest(),manifest['corpus_hash'])
            self.assertEqual(self.audit['model'],manifest['review_model'])
        self.assertEqual(set(by_key),seen)

    def test_packaging_can_be_reproduced_from_audit(self):
        rows=[{k:r[k] for k in ('key','source','id','line','text')} for r in self.items]
        parts=review.partition(rows,[r['final'] for r in self.items])
        with tempfile.TemporaryDirectory() as d:
            review.package(parts,Path(d),self.audit['model'])
            for part in parts:
                for name in ('sentences.csv','manifest.json'):
                    path=Path('corpus_jnu_'+part)/name
                    self.assertEqual((ASSETS/path).read_bytes(),(Path(d)/path).read_bytes())

    def test_requested_examples_are_in_local_part(self):
        by_key={r['key']:r for r in self.items}
        for key in ('CZ_00010091','CZ_00010784','SZ_00080001','SZ_00080364'):
            self.assertEqual('L',by_key[key]['final']['label'],key)

if __name__=='__main__':unittest.main()
