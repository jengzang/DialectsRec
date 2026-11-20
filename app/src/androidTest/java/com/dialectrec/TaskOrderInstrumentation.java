package com.dialectrec;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.ContextWrapper;
import android.database.DatabaseErrorHandler;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import com.dialectrec.data.*;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Runs against an isolated cache database, leaving collected data untouched. */
public final class TaskOrderInstrumentation extends Instrumentation {
    private Context databaseContext;
    private int passed;
    private final List<String> failures = new ArrayList<>();
    private final StringBuilder report = new StringBuilder();
    private interface Check { void run(CorpusDatabase db) throws Exception; }

    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments); start(); }
    @Override public void onStart() {
        databaseContext = new ContextWrapper(getTargetContext()) {
            @Override public File getDatabasePath(String name) {
                return new File(getCacheDir(), "random-order-test-" + name);
            }
            @Override public SQLiteDatabase openOrCreateDatabase(String name, int mode, SQLiteDatabase.CursorFactory factory) {
                return SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name), factory);
            }
            @Override public SQLiteDatabase openOrCreateDatabase(String name, int mode, SQLiteDatabase.CursorFactory factory, DatabaseErrorHandler handler) {
                return SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name).getPath(), factory, handler);
            }
            @Override public boolean deleteDatabase(String name) {
                return SQLiteDatabase.deleteDatabase(getDatabasePath(name));
            }
        };
        check("atomic snapshot and cursor survive database reopen", db -> {
            List<Sentence> task = TaskOrder.shuffled(db.list("", 1, 5000), new Random(123));
            db.saveTask("", 1, 5000, 37, task);
            db.close();
            require(db.hasTaskForCurrentContext(), "saved task context lost");
            List<Sentence> restored = TaskOrder.restore(db.list("", 1, 5000), db.getSetting("task_order_ids"));
            require(TaskOrder.encode(task).equals(TaskOrder.encode(restored)), "queue changed");
            require("37".equals(db.getSetting("task_current")), "cursor changed");
            require(task.get(37).id.equals(restored.get(37).id), "current sentence changed");
            require("S000001".equals(db.list("", 1, 5000).get(0).id), "browse order changed");
        });
        check("stored corpus checksum includes original sentence metadata", db -> {
            String expected;
            try (android.database.Cursor c = db.getReadableDatabase().rawQuery("SELECT corpus_hash FROM corpus WHERE corpus_id=? AND corpus_version=?", new String[]{db.getSetting("corpus_id"), db.getSetting("corpus_version")})) {
                require(c.moveToFirst(), "missing corpus"); expected=c.getString(0);
            }
            require(expected.equals(CorpusHash.sha256(db.list("", 1, 5000))), "sentence metadata lost from canonical checksum");
        });
        check("CSV import retains metadata used by canonical checksum", db -> {
            String csv="sentence_id,text,category,difficulty,length_bucket,notes\nI1,测试提示,测试,short,medium,\"reviewed, preserved\"\n";
            db.importCorpus(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)), "QA metadata", true);
            List<Sentence> rows=db.list("",1,1);
            require(rows.size()==1, "import row missing");
            require("medium".equals(rows.get(0).lengthBucket)&&"reviewed, preserved".equals(rows.get(0).notes), "import metadata lost");
            try(android.database.Cursor c=db.getReadableDatabase().rawQuery("SELECT corpus_hash FROM corpus WHERE corpus_id=?",new String[]{db.getSetting("corpus_id")})){
                require(c.moveToFirst()&&CorpusHash.sha256(rows).equals(c.getString(0)), "import hash mismatch");
            }
        });
        check("different speaker cannot reuse saved task", db -> {
            db.saveTask("", 1, 50, 7, TaskOrder.shuffled(db.list("", 1, 50), new Random(1)));
            db.saveSpeaker("QA_OTHER", "测试");
            require(!db.hasTaskForCurrentContext(), "other speaker reused queue");
        });
        check("different corpus version cannot reuse saved task", db -> {
            db.saveTask("", 1, 50, 7, TaskOrder.shuffled(db.list("", 1, 50), new Random(1)));
            db.setSetting("corpus_version", "changed");
            require(!db.hasTaskForCurrentContext(), "other corpus reused queue");
        });
        check("missing new-format snapshot is not mistaken for legacy", db -> {
            db.saveTask("", 1, 50, 7, TaskOrder.shuffled(db.list("", 1, 50), new Random(1)));
            db.getWritableDatabase().delete("settings", "key=?", new String[]{"task_order_ids"});
            require(!db.hasTaskForCurrentContext(), "damaged task accepted as legacy");
        });
        check("legacy queue migrates without moving cursor", db -> {
            legacyTask(db);
            require(db.hasTaskForCurrentContext(), "genuine legacy task rejected");
            List<Sentence> ordered = db.list("", 1, 100);
            List<Sentence> task = TaskOrder.migrateLegacy(ordered, 37, new Random(1));
            db.saveTask("", 1, 100, 37, task);
            require("S000038".equals(task.get(37).id), "legacy current moved");
            require(!TaskOrder.encode(ordered).equals(TaskOrder.encode(task)), "legacy tail not randomized");
            require(db.hasTaskForCurrentContext(), "migrated context invalid");
        });
        check("upgrade then import cannot apply old cursor to new corpus", db -> {
            legacyTask(db);
            db.skip("S000001", "测试保留进度");
            db.importCorpus(new ByteArrayInputStream("甲\n乙\n丙\n".getBytes(StandardCharsets.UTF_8)), "QA corpus", false);
            require(!db.hasTaskForCurrentContext(), "legacy task carried into imported corpus");
            require(db.getReadableDatabase().compileStatement("SELECT COUNT(*) FROM progress").simpleQueryForLong() == 1, "import erased progress");
        });
        check("fresh install defaults to Chaozhou and exposes four corpora", true, db -> {
            require("jnu_chaozhou".equals(db.getSetting("corpus_id")), "default corpus wrong");
            require(db.availableCorpora().size()==4, "expected four corpora");
            int total=0;
            for(CorpusDatabase.CorpusInfo corpus:db.availableCorpora()) {
                db.selectCorpus(corpus.id,corpus.version);
                List<Sentence> rows=db.list("",1,db.count(null));
                require(rows.size()==corpus.count, "count mismatch");
                require(corpus.hash.equals(CorpusHash.sha256(rows)), "bundled hash mismatch: "+corpus.id);
                if(corpus.id.startsWith("jnu_"))total+=rows.size();
            }
            require(total==7728, "source rows lost or duplicated");
            db.selectCorpus("jnu_local","1.0");
            require(db.categories().equals(Arrays.asList("潮州","苏州")), "local parts missing");
            for(Sentence s:db.list("",1,db.count(null)))
                require(s.id.startsWith("CZ_")||s.id.startsWith("SZ_"), "source namespace missing");
        });
        check("switching corpora restores random queue cursor and progress", db -> {
            List<Sentence> queue=TaskOrder.shuffled(db.list("",1,50),new Random(12));
            db.saveTask("",1,50,17,queue);db.skip(queue.get(0).id,"QA");
            db.selectCorpus("jnu_chaozhou","1.0");
            require(!db.hasTaskForCurrentContext(), "old task leaked into Chaozhou");
            require(db.count("skipped")==0, "progress leaked across corpora");
            db.saveTask("",1,30,3,TaskOrder.shuffled(db.list("",1,30),new Random(2)));
            db.selectCorpus("dialect_general","2.0");
            require(db.hasTaskForCurrentContext(), "original task missing");
            require("17".equals(db.getSetting("task_current")), "original cursor lost");
            require(TaskOrder.encode(queue).equals(db.getSetting("task_order_ids")), "original random order changed");
            require(db.count("skipped")==1, "original progress lost");
            db.seed(getTargetContext());
            require("dialect_general".equals(db.getSetting("corpus_id")), "reseed changed active corpus");
            db.selectCorpus("jnu_chaozhou","1.0");
            require("3".equals(db.getSetting("task_current")), "second cursor lost");
        });
        check("switching speakers and import retain prior tasks", db -> {
            List<Sentence> queue=TaskOrder.shuffled(db.list("",1,20),new Random(3));
            db.saveTask("",1,20,9,queue);
            db.saveSpeaker("QA_SECOND","另一方言");require(!db.hasTaskForCurrentContext(),"speaker queue leaked");
            db.saveSpeaker("QA_RANDOM_ORDER","测试");require("9".equals(db.getSetting("task_current")),"speaker cursor lost");
            db.importCorpus(new ByteArrayInputStream("导入句子。".getBytes(StandardCharsets.UTF_8)),"QA",false);
            require(!db.hasTaskForCurrentContext(),"import leaked task");
            db.seed(getTargetContext());require(db.getSetting("corpus_id").startsWith("import_"),"seed switched import");
            db.selectCorpus("dialect_general","2.0");
            require("9".equals(db.getSetting("task_current"))&&TaskOrder.encode(queue).equals(db.getSetting("task_order_ids")),"import erased old task");
            String before=db.getSetting("corpus_id");
            try { db.selectCorpus("missing","1.0");throw new AssertionError("invalid corpus accepted"); }
            catch(IllegalArgumentException expected) {require(before.equals(db.getSetting("corpus_id")),"invalid selection changed context");}
        });
        check("supplementary Unicode speaker IDs restore their task", db -> {
            db.saveSpeaker("QA_🎙_𠮷_%", "测试");
            List<Sentence> queue=TaskOrder.shuffled(db.list("",1,10),new Random(3));
            db.saveTask("",1,10,4,queue);
            db.selectCorpus("jnu_chaozhou","1.0");db.selectCorpus("dialect_general","2.0");
            require("4".equals(db.getSetting("task_current")),"Unicode speaker lost cursor");
            require(TaskOrder.encode(queue).equals(db.getSetting("task_order_ids")),"Unicode speaker lost queue");
        });
        check("export uses captured context after active corpus changes", db -> {
            db.skip("S000001","QA export");
            CorpusDatabase.ContextSnapshot snapshot=db.currentContext();
            db.selectCorpus("jnu_chaozhou","1.0");
            File out=new File(getTargetContext().getCacheDir(),"snapshot-export.zip");
            try {
                com.dialectrec.importexport.DatasetExporter.export(getTargetContext(),db,android.net.Uri.fromFile(out),snapshot);
                try(java.util.zip.ZipFile zip=new java.util.zip.ZipFile(out)){
                    String manifest=new String(zip.getInputStream(zip.getEntry("manifest.json")).readAllBytes(),StandardCharsets.UTF_8);
                    String sentences=new String(zip.getInputStream(zip.getEntry("sentences.csv")).readAllBytes(),StandardCharsets.UTF_8);
                    String progress=new String(zip.getInputStream(zip.getEntry("progress.csv")).readAllBytes(),StandardCharsets.UTF_8);
                    require(manifest.contains("dialect_general")&&manifest.contains("\"skipped_count\":1"),"manifest context changed");
                    require(sentences.contains("S000001")&&!sentences.contains("CZ_"),"sentence context changed");
                    require(progress.contains("S000001,skipped")&&!progress.contains("CZ_"),"progress context changed");
                }
            } finally {out.delete();}
        });
        check("four corpus exports retain selected WAVs and namespaced prompts", true, db -> {
            File wavDir=new File(getTargetContext().getFilesDir(),"qa-jnu-instrumentation");wavDir.mkdirs();
            File exports=new File(getTargetContext().getCacheDir(),"qa-jnu-exports");exports.mkdirs();
            try {
                for(CorpusDatabase.CorpusInfo corpus:db.availableCorpora()){
                    db.selectCorpus(corpus.id,corpus.version);
                    List<Sentence> samples=new ArrayList<>();
                    if("jnu_local".equals(corpus.id)){
                        samples.add(db.list("潮州",1,db.count(null)).get(0));
                        samples.add(db.list("苏州",1,db.count(null)).get(0));
                    }else samples.add(db.list("",1,1).get(0));
                    for(Sentence sentence:samples){
                        String rid=UUID.randomUUID().toString();File wav=new File(wavDir,rid+".wav");
                        // Deterministic fixture audio tests the data path, not microphone quality.
                        byte[] pcm=new byte[32000];for(int i=0;i<16000;i++){short sample=(short)(Math.sin(2*Math.PI*440*i/16000)*4000);pcm[i*2]=(byte)sample;pcm[i*2+1]=(byte)(sample>>8);}
                        com.dialectrec.audio.WavWriter.write(wav,pcm,16000,1,16);
                        byte[] digest=java.security.MessageDigest.getInstance("SHA-256").digest(java.nio.file.Files.readAllBytes(wav.toPath()));
                        StringBuilder hex=new StringBuilder();for(byte b:digest)hex.append(String.format(Locale.ROOT,"%02x",b));
                        db.saveRecording(rid,sentence.id,"qa-jnu-instrumentation/"+rid+".wav",1000,16000,"MIC",hex.toString());
                    }
                    require(db.count("completed")==samples.size(),"selected fixture count wrong");
                    File out=new File(exports,corpus.id+".zip");
                    com.dialectrec.importexport.DatasetExporter.export(getTargetContext(),db,android.net.Uri.fromFile(out));
                    try(java.util.zip.ZipFile zip=new java.util.zip.ZipFile(out)){
                        for(Sentence sentence:samples)require(zip.getEntry("wav/"+sentence.id+".wav")!=null,"missing WAV: "+sentence.id);
                    }
                }
            } finally {File[] files=wavDir.listFiles();if(files!=null)for(File f:files)f.delete();wavDir.delete();}
        });
        Bundle result = new Bundle();
        result.putString("stream", report + "\n" + passed + " passed; " + failures.size() + " failed\n");
        result.putInt("failures", failures.size());
        finish(failures.isEmpty() ? Activity.RESULT_OK : Activity.RESULT_CANCELED, result);
    }

    private void legacyTask(CorpusDatabase db) {
        db.setSetting("task_category", ""); db.setSetting("task_start", "1");
        db.setSetting("task_end", "100"); db.setSetting("task_current", "37");
    }

    private void check(String name, Check check) { check(name,false,check); }
    private void check(String name, boolean useDefault, Check check) {
        databaseContext.deleteDatabase("dialect_recorder.db");
        try (CorpusDatabase db = new CorpusDatabase(databaseContext)) {
            db.seed(getTargetContext());
            if(!useDefault)db.selectCorpus("dialect_general","2.0");
            db.saveSpeaker("QA_RANDOM_ORDER", "测试");
            check.run(db); passed++; report.append("PASS: ").append(name).append('\n');
        } catch (Exception | AssertionError e) {
            failures.add(name); report.append("FAIL: ").append(name).append(": ").append(e).append('\n');
        } finally { databaseContext.deleteDatabase("dialect_recorder.db"); }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
