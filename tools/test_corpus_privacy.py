import unittest
from tools.check_corpus_privacy import is_private_path

class CorpusPrivacyTests(unittest.TestCase):
    def test_original_and_derived_jnu_material_is_private(self):
        for path in ['docs/暨大/source.txt','docs/corpus-review/jnu-v1/audit.json','docs/corpus-review/jnu-v2/pass1/0001.json','app/src/main/assets/corpus_jnu_local/sentences.csv']:
            with self.subTest(path=path):self.assertTrue(is_private_path(path))
    def test_packaged_and_collected_data_cannot_be_pushed(self):
        for path in ['release.apk','release.aab','export.zip','record.wav','capture.pcm.tmp','backup.db']:
            with self.subTest(path=path):self.assertTrue(is_private_path(path))
    def test_source_code_prompts_and_ai_corpus_are_public(self):
        for path in ['tools/review_jnu_corpus.py','docs/corpus-review/locality-prompt.txt','docs/DATA_FORMAT.md','app/src/main/assets/corpus_v1/sentences.csv']:
            with self.subTest(path=path):self.assertFalse(is_private_path(path))

if __name__=='__main__':unittest.main()
