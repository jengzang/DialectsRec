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
        Bundle result = new Bundle();
        result.putString("stream", report + "\n" + passed + " passed; " + failures.size() + " failed\n");
        result.putInt("failures", failures.size());
        finish(failures.isEmpty() ? Activity.RESULT_OK : Activity.RESULT_CANCELED, result);
    }

    private void legacyTask(CorpusDatabase db) {
        db.setSetting("task_category", ""); db.setSetting("task_start", "1");
        db.setSetting("task_end", "100"); db.setSetting("task_current", "37");
    }

    private void check(String name, Check check) {
        databaseContext.deleteDatabase("dialect_recorder.db");
        try (CorpusDatabase db = new CorpusDatabase(databaseContext)) {
            db.seed(getTargetContext());
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
