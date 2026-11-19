import hashlib
import io
import json
import tempfile
import unittest
import wave
import zipfile
from pathlib import Path

from tools.merge_datasets import merge_datasets
from tools.validate_dataset import validate_dataset


def wav_bytes(frames=1600):
    b = io.BytesIO()
    with wave.open(b, "wb") as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(16000)
        w.writeframes(b"\x00\x01" * frames)
    return b.getvalue()


def make_zip(path: Path, speaker="SP001", bad_audio=False, frames=1600):
    audio = b"not wav" if bad_audio else wav_bytes(frames)
    sentences = "sentence_id,text,category,difficulty,length_bucket,notes\nS000001,你好,日常,short,short,\n"
    corpus = {"corpus_id": "c1", "corpus_version": "1", "corpus_hash": hashlib.sha256("S000001\x1f你好\x1f日常\x1fshort\x1fshort\x1f\n".encode()).hexdigest()}
    manifest = {"schema_version":"1.0", "export_id":f"export-{speaker}", "corpus_id":"c1", "corpus_version":"1", "corpus_hash":corpus["corpus_hash"], "speaker_id":speaker, "completed_count":1}
    recordings = "recording_id,sentence_id,speaker_id,audio_path,duration_ms,status,selected,review_flag,spoken_transcript,transcription_status\nr1,S000001," + speaker + ",wav/S000001.wav,100,completed,1,false,,not_annotated\n"
    progress = "sentence_id,status,selected_recording_id,skip_reason,review_flag\nS000001,completed,r1,,false\n"
    with zipfile.ZipFile(path, "w") as z:
        z.writestr("manifest.json", json.dumps(manifest))
        z.writestr("corpus.json", json.dumps(corpus))
        z.writestr("speaker.json", json.dumps({"speaker_id":speaker,"local_speaker_uuid":speaker+"-uuid"}))
        z.writestr("sentences.csv", sentences)
        z.writestr("recordings.csv", recordings)
        z.writestr("progress.csv", progress)
        z.writestr("wav/S000001.wav", audio)


class DatasetToolsTests(unittest.TestCase):
    def test_valid_zip_passes_and_bad_wav_fails(self):
        with tempfile.TemporaryDirectory() as d:
            good, bad = Path(d)/"good.zip", Path(d)/"bad.zip"
            make_zip(good); make_zip(bad, bad_audio=True)
            valid = validate_dataset(good)
            self.assertEqual([], valid.errors)
            self.assertEqual(1, valid.stats["recordings"])
            self.assertTrue(validate_dataset(bad).errors)

    def test_merge_keeps_speakers_separate(self):
        with tempfile.TemporaryDirectory() as d:
            one, two, out = Path(d)/"one.zip", Path(d)/"two.zip", Path(d)/"out"
            make_zip(one, "SP001"); make_zip(two, "SP002")
            result = merge_datasets([one, two], out)
            self.assertEqual([], result.conflicts)
            self.assertTrue((out/"wav"/"SP001"/"S000001.wav").exists())
            self.assertTrue((out/"wav"/"SP002"/"S000001.wav").exists())

    def test_same_speaker_different_audio_is_conflict(self):
        with tempfile.TemporaryDirectory() as d:
            one, two, out = Path(d)/"one.zip", Path(d)/"two.zip", Path(d)/"out"
            make_zip(one, "SP001")
            make_zip(two, "SP001", frames=800)
            result = merge_datasets([one, two], out)
            self.assertTrue(result.conflicts)

    def test_progress_must_point_to_selected_recording_for_same_sentence(self):
        with tempfile.TemporaryDirectory() as d:
            path = Path(d) / "bad.zip"
            audio = wav_bytes()
            sentences = "sentence_id,text,category,difficulty,length_bucket,notes\nS000001,你好,日常,short,short,\nS000002,再见,日常,short,short,\n"
            corpus_hash = hashlib.sha256(("S000001\x1f你好\x1f日常\x1fshort\x1fshort\x1f\nS000002\x1f再见\x1f日常\x1fshort\x1fshort\x1f\n").encode()).hexdigest()
            with zipfile.ZipFile(path, "w") as z:
                z.writestr("manifest.json", json.dumps({"schema_version":"1.0","export_id":"x","corpus_id":"c1","corpus_version":"1","corpus_hash":corpus_hash,"speaker_id":"SP001"}))
                z.writestr("corpus.json", json.dumps({"corpus_id":"c1","corpus_version":"1","corpus_hash":corpus_hash}))
                z.writestr("speaker.json", json.dumps({"speaker_id":"SP001"}))
                z.writestr("sentences.csv", sentences)
                z.writestr("recordings.csv", "recording_id,sentence_id,speaker_id,audio_path,duration_ms,status,selected\nr1,S000001,SP001,wav/S000001.wav,100,completed,1\nr2,S000002,SP001,wav/S000002.wav,100,completed,1\n")
                z.writestr("progress.csv", "sentence_id,status,selected_recording_id\nS000001,completed,r2\nS000002,completed,r2\n")
                z.writestr("wav/S000001.wav", audio)
                z.writestr("wav/S000002.wav", audio)
            errors = validate_dataset(path).errors
            self.assertTrue(any("sentence mismatch" in e for e in errors))

    def test_merge_deduplicates_identical_progress(self):
        with tempfile.TemporaryDirectory() as d:
            one, two, out = Path(d)/"one.zip", Path(d)/"two.zip", Path(d)/"out"
            make_zip(one, "SP001"); make_zip(two, "SP001")
            result = merge_datasets([one, two], out)
            self.assertEqual([], result.conflicts)
            with (out / "progress.csv").open(encoding="utf-8") as progress_file:
                self.assertEqual(1, sum(1 for _ in progress_file) - 1)


if __name__ == "__main__": unittest.main()
