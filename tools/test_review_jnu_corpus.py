import io
import json
import tempfile
import unittest
from pathlib import Path
from tools import review_jnu_corpus as review


class LocalityReviewTests(unittest.TestCase):
    def test_parser_preserves_duplicate_text_and_source_ids(self):
        with tempfile.TemporaryDirectory() as d:
            p=Path(d)/'input.txt'
            p.write_text('00000001\t相同句子。\n00000002\t相同句子。\n',encoding='utf-16')
            rows=review.read_source(p,'suzhou','utf-16')
            self.assertEqual(['SZ_00000001','SZ_00000002'],[r['key'] for r in rows])
            self.assertEqual([1,2],[r['line'] for r in rows])
            self.assertEqual(rows[0]['text'],rows[1]['text'])

    def test_verdict_rejects_missing_duplicate_and_foreign_ids(self):
        rows=[{'key':'CZ_1'},{'key':'CZ_2'}]
        for value in ([['CZ_1','G','',[]]], [['CZ_1','G','',[]]]*2,
                      [['CZ_1','G','',[]],['SZ_2','L','当地谐音',[]]]):
            with self.subTest(value=value),self.assertRaises(ValueError):
                review.validate_verdicts(rows,value)
        result=review.validate_verdicts(rows,[['CZ_2','L','地方食物',['gloss']],['CZ_1','G','',[]]])
        self.assertEqual(['CZ_1','CZ_2'],[r['key'] for r in result])
        self.assertEqual('L',result[1]['label'])

    def test_original_id_alias_is_only_accepted_when_unique(self):
        rows=[{'key':'CZ_00000001','id':'00000001'}]
        result=review.validate_verdicts(rows,[['00000001','L','地方俗语','[]']])
        self.assertEqual('CZ_00000001',result[0]['key'])
        self.assertEqual([],result[0]['flags'])
        with self.assertRaises(ValueError):
            review.validate_verdicts(rows+[{'key':'SZ_00000001','id':'00000001'}],[['00000001','G','',[]]])

    def test_checkpoint_bound_to_prompt_and_input(self):
        rows=[{'key':'CZ_1','text':'原文'}]
        a=review.fingerprint(rows,'prompt','model')
        self.assertNotEqual(a,review.fingerprint(rows,'changed','model'))
        self.assertNotEqual(a,review.fingerprint([{'key':'CZ_1','text':'变化'}],'prompt','model'))

    def test_stream_rejects_incomplete_response_even_with_valid_json(self):
        def sse(events):return io.BytesIO(''.join('data: '+json.dumps(x)+'\n\n' for x in events).encode())
        delta={'type':'response.output_text.delta','delta':'[]'}
        self.assertEqual('[]',review.read_stream(sse([delta,{'type':'response.completed','response':{'status':'completed'}}])))
        with self.assertRaises(ValueError):review.read_stream(sse([delta]))
        with self.assertRaises(ValueError):review.read_stream(sse([delta,{'type':'response.incomplete'}]))

    def test_checkpoint_uses_validated_raw_not_untrusted_derived_verdicts(self):
        class Client: model='test-model'
        rows=[{'key':'CZ_1','text':'原文'}]
        body=review.PROMPT+'\n相邻上下文：'+json.dumps([],ensure_ascii=False)+'\n目标条目：'+json.dumps(rows,ensure_ascii=False)
        with tempfile.TemporaryDirectory() as d:
            p=Path(d)/'batch.json'
            p.write_text(json.dumps({'fingerprint':review.fingerprint(rows,body,Client.model),'raw':[['CZ_1','G','',[]]],'verdicts':[{'key':'CZ_1','label':'L','reason':'篡改','flags':[]}]}))
            self.assertEqual('G',review.review_one(Client(),rows,[],p)[0]['label'])

    def test_local_text_exports_keep_original_ids_and_text(self):
        parts={'local':[{'source':'chaozhou','id':'00000001','text':'地方词 & 原文'}, {'source':'suzhou','id':'00000001','text':'当地俗语'}]}
        with tempfile.TemporaryDirectory() as d:
            review.write_local_texts(parts,Path(d))
            self.assertEqual('00000001\t地方词 & 原文\n',(Path(d)/'暨大·地方特色_潮州.txt').read_text())
            self.assertEqual('00000001\t当地俗语\n',(Path(d)/'暨大·地方特色_苏州.txt').read_text())

    def test_packaging_records_actual_review_model(self):
        with tempfile.TemporaryDirectory() as d:
            review.package({'chaozhou':[],'suzhou':[],'local':[]},Path(d),model='actual-model')
            manifest=json.loads((Path(d)/'corpus_jnu_local/manifest.json').read_text())
            self.assertEqual('actual-model',manifest['review_model'])

    def test_partition_preserves_every_row_and_no_cross_source_collision(self):
        rows=[{'key':'CZ_1','source':'chaozhou','id':'1','line':1,'text':'普通句'},
              {'key':'SZ_1','source':'suzhou','id':'1','line':1,'text':'当地俗语'}]
        verdicts=[{'key':'CZ_1','label':'G','reason':'通用','flags':[]},
                  {'key':'SZ_1','label':'L','reason':'苏州谐音','flags':[]}]
        parts=review.partition(rows,verdicts)
        self.assertEqual(['CZ_1'],[r['key'] for r in parts['chaozhou']])
        self.assertEqual(['SZ_1'],[r['key'] for r in parts['local']])
        self.assertEqual(2,sum(map(len,parts.values())))
        with self.assertRaises(ValueError):review.partition(rows,[dict(verdicts[0],label='U'),verdicts[1]])

if __name__=='__main__':unittest.main()
