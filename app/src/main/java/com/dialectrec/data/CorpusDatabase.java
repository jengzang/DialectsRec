package com.dialectrec.data;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import java.io.*;
import java.security.MessageDigest;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.util.regex.*;

public final class CorpusDatabase extends SQLiteOpenHelper {
    private static final String DB="dialect_recorder.db";
    private final Context context;
    public CorpusDatabase(Context c) { super(c, DB, null, 4); context=c; }
    @Override public void onConfigure(SQLiteDatabase db) { super.onConfigure(db); db.setForeignKeyConstraintsEnabled(true); }
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE corpus(corpus_id TEXT NOT NULL, corpus_version TEXT NOT NULL, corpus_hash TEXT NOT NULL, title TEXT, sentence_count INTEGER, PRIMARY KEY(corpus_id,corpus_version))");
        db.execSQL("CREATE TABLE sentence(corpus_id TEXT, corpus_version TEXT, sentence_id TEXT, text TEXT, category TEXT, difficulty TEXT, length_bucket TEXT NOT NULL DEFAULT '', notes TEXT NOT NULL DEFAULT '', order_index INTEGER, PRIMARY KEY(corpus_id,corpus_version,sentence_id))");
        db.execSQL("CREATE TABLE speaker(speaker_id TEXT PRIMARY KEY, dialect TEXT, local_uuid TEXT)");
        db.execSQL("CREATE TABLE progress(speaker_id TEXT, corpus_id TEXT, corpus_version TEXT, sentence_id TEXT, status TEXT, selected_recording_id TEXT, skip_reason TEXT, review_flag INTEGER DEFAULT 0, updated_at INTEGER, PRIMARY KEY(speaker_id,corpus_id,corpus_version,sentence_id), FOREIGN KEY(speaker_id) REFERENCES speaker(speaker_id), FOREIGN KEY(corpus_id,corpus_version,sentence_id) REFERENCES sentence(corpus_id,corpus_version,sentence_id))");
        db.execSQL("CREATE TABLE recording(recording_id TEXT PRIMARY KEY, speaker_id TEXT, corpus_id TEXT, corpus_version TEXT, sentence_id TEXT, wav_path TEXT, duration_ms INTEGER, sample_rate INTEGER, bits INTEGER, channels INTEGER, capture_rate INTEGER, audio_source TEXT, audio_sha256 TEXT, status TEXT, selected INTEGER, review_flag INTEGER, spoken_transcript TEXT, transcription_status TEXT, take_no INTEGER, created_at INTEGER, FOREIGN KEY(speaker_id) REFERENCES speaker(speaker_id), FOREIGN KEY(corpus_id,corpus_version,sentence_id) REFERENCES sentence(corpus_id,corpus_version,sentence_id))");
        db.execSQL("CREATE UNIQUE INDEX selected_recording_per_sentence ON recording(speaker_id,corpus_id,corpus_version,sentence_id) WHERE selected=1");
        db.execSQL("CREATE TABLE settings(key TEXT PRIMARY KEY, value TEXT)");
    }
    @Override public void onUpgrade(SQLiteDatabase db,int oldV,int newV) {
        if(oldV<2){
            // v1 had no uniqueness constraint. Rebuild selection from the durable
            // progress pointer so an upgrade never silently chooses another take.
            db.beginTransaction();
            try {
                db.execSQL("CREATE TEMP TABLE legacy_selected AS SELECT recording_id,speaker_id,corpus_id,corpus_version,sentence_id,created_at FROM recording WHERE selected=1");
                db.execSQL("UPDATE recording SET selected=0");
                db.execSQL("UPDATE recording SET selected=1 WHERE recording_id IN (SELECT r.recording_id FROM recording r JOIN progress p ON p.selected_recording_id=r.recording_id AND p.speaker_id=r.speaker_id AND p.corpus_id=r.corpus_id AND p.corpus_version=r.corpus_version AND p.sentence_id=r.sentence_id WHERE p.status='completed')");
                // Older rows created before progress pointers existed are retained
                // by selecting the newest legacy take for each sentence.
                db.execSQL("UPDATE recording SET selected=1 WHERE recording_id IN (SELECT l.recording_id FROM legacy_selected l WHERE NOT EXISTS (SELECT 1 FROM progress p WHERE p.speaker_id=l.speaker_id AND p.corpus_id=l.corpus_id AND p.corpus_version=l.corpus_version AND p.sentence_id=l.sentence_id AND p.status='completed' AND p.selected_recording_id IS NOT NULL) AND NOT EXISTS (SELECT 1 FROM legacy_selected newer WHERE newer.speaker_id=l.speaker_id AND newer.corpus_id=l.corpus_id AND newer.corpus_version=l.corpus_version AND newer.sentence_id=l.sentence_id AND (newer.created_at>l.created_at OR (newer.created_at=l.created_at AND newer.recording_id>l.recording_id))))");
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS selected_recording_per_sentence ON recording(speaker_id,corpus_id,corpus_version,sentence_id) WHERE selected=1");
                db.setTransactionSuccessful();
            } finally { db.endTransaction(); }
        }
        if(oldV<3) db.execSQL("ALTER TABLE recording ADD COLUMN audio_source TEXT");
        if(oldV<4){
            db.execSQL("ALTER TABLE sentence ADD COLUMN length_bucket TEXT NOT NULL DEFAULT ''");
            db.execSQL("ALTER TABLE sentence ADD COLUMN notes TEXT NOT NULL DEFAULT ''");
            restoreBundledMetadata(db);
        }
    }
    private void restoreBundledMetadata(SQLiteDatabase db){
        try(InputStream in=context.getAssets().open("corpus_v1/sentences.csv")){
            ManifestInfo manifest=readManifest(context);
            List<String[]> rows=CsvReader.read(in); rows.remove(0);
            for(String[] row:rows){
                if(row.length<6)continue;
                ContentValues values=new ContentValues(); values.put("length_bucket",row[4]); values.put("notes",row[5]);
                // Recover metadata only for the exact bundled prompt; never replace collected text or progress.
                db.update("sentence",values,"corpus_id=? AND corpus_version=? AND sentence_id=? AND text=? AND category=? AND difficulty=?",
                    new String[]{manifest.id,manifest.version,row[0],row[1],row[2],row[3]});
            }
        }catch(IOException e){throw new SQLiteException("句库元数据迁移失败",e);}
    }
    public static final class ManifestInfo {
        public final String id, version, hash;
        ManifestInfo(String id, String version, String hash) { this.id=id; this.version=version; this.hash=hash; }
    }
    public static ManifestInfo parseManifest(String json) {
        return new ManifestInfo(manifestValue(json, "corpus_id", "dialect_general"), manifestValue(json, "corpus_version", "1.0"), manifestValue(json, "corpus_hash", ""));
    }
    private static String manifestValue(String json, String key, String fallback) {
        Matcher matcher=Pattern.compile("\""+Pattern.quote(key)+"\"\\s*:\\s*\"([^\"]*)\"").matcher(json);
        return matcher.find()?matcher.group(1):fallback;
    }
    private static byte[] readAll(InputStream in) throws IOException { ByteArrayOutputStream b=new ByteArrayOutputStream(); byte[] buf=new byte[8192]; int n; while((n=in.read(buf))>=0){if(n>0)b.write(buf,0,n);} return b.toByteArray(); }
    private ManifestInfo readManifest(Context context) throws IOException {
        try (InputStream mi=context.getAssets().open("corpus_v1/manifest.json")) {
            return parseManifest(new String(readAll(mi), StandardCharsets.UTF_8));
        }
    }
    public static final class CorpusInfo {
        public final String id,version,hash,title; public final int count;
        CorpusInfo(String id,String version,String hash,String title,int count){this.id=id;this.version=version;this.hash=hash;this.title=title;this.count=count;}
    }
    public List<CorpusInfo> availableCorpora(){
        List<CorpusInfo> out=new ArrayList<>();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT corpus_id,corpus_version,corpus_hash,title,sentence_count FROM corpus ORDER BY CASE corpus_id WHEN 'jnu_chaozhou' THEN 0 WHEN 'jnu_suzhou' THEN 1 WHEN 'dialect_general' THEN 2 WHEN 'jnu_local' THEN 3 ELSE 4 END, title, corpus_version",null)){
            while(c.moveToNext())out.add(new CorpusInfo(c.getString(0),c.getString(1),c.getString(2),c.getString(3),c.getInt(4)));
        }
        return out;
    }
    public String currentTitle(){
        try(Cursor c=getReadableDatabase().rawQuery("SELECT title FROM corpus WHERE corpus_id=? AND corpus_version=?",new String[]{getSetting("corpus_id"),getSetting("corpus_version")})){
            return c.moveToFirst()?c.getString(0):"未选择句库";
        }
    }
    public void seed(Context context) throws Exception {
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try {
            for(CorpusCatalog.Entry entry:CorpusCatalog.BUNDLED)installBundled(db,context,entry);
            if(getSetting("corpus_id")==null){
                try(Cursor c=db.rawQuery("SELECT corpus_version FROM corpus WHERE corpus_id=?",new String[]{CorpusCatalog.DEFAULT_ID})){
                    if(!c.moveToFirst())throw new IOException("缺少默认句库");
                    setSetting(db,"corpus_id",CorpusCatalog.DEFAULT_ID);setSetting(db,"corpus_version",c.getString(0));
                }
            }
            setSetting(db,"seeded","1");db.setTransactionSuccessful();
        } finally {db.endTransaction();}
    }
    private void installBundled(SQLiteDatabase db,Context context,CorpusCatalog.Entry entry) throws Exception {
        ManifestInfo manifest;
        try(InputStream in=context.getAssets().open(entry.directory+"/manifest.json")){
            manifest=parseManifest(new String(readAll(in),StandardCharsets.UTF_8));
        }
        try(Cursor c=db.rawQuery("SELECT corpus_hash FROM corpus WHERE corpus_id=? AND corpus_version=?",new String[]{manifest.id,manifest.version})){
            if(c.moveToFirst()){
                if(!manifest.hash.equals(c.getString(0)))throw new IOException("同版本句库校验值变化："+entry.title);
                ContentValues title=new ContentValues();title.put("title",entry.title);
                db.update("corpus",title,"corpus_id=? AND corpus_version=?",new String[]{manifest.id,manifest.version});return;
            }
        }
        List<Sentence> sentences=new ArrayList<>();
        try(InputStream in=context.getAssets().open(entry.directory+"/sentences.csv")){
            List<String[]> rows=CsvReader.read(in);if(rows.isEmpty())throw new IOException("句库为空");rows.remove(0);
            for(String[] row:rows){
                if(row.length!=6||row[0].isEmpty()||row[1].isEmpty())throw new IOException("句库格式错误："+entry.title);
                sentences.add(new Sentence(row[0],row[1],row[2],row[3],row[4],row[5]));
            }
        }
        if(!manifest.hash.equals(CorpusHash.sha256(sentences)))throw new IOException("句库校验失败："+entry.title);
        ContentValues cv=new ContentValues();cv.put("corpus_id",manifest.id);cv.put("corpus_version",manifest.version);cv.put("corpus_hash",manifest.hash);cv.put("title",entry.title);cv.put("sentence_count",sentences.size());db.insertOrThrow("corpus",null,cv);
        int order=0;
        for(Sentence s:sentences){
            ContentValues v=new ContentValues();v.put("corpus_id",manifest.id);v.put("corpus_version",manifest.version);v.put("sentence_id",s.id);v.put("text",s.text);v.put("category",s.category);v.put("difficulty",s.difficulty);v.put("length_bucket",s.lengthBucket);v.put("notes",s.notes);v.put("order_index",++order);db.insertOrThrow("sentence",null,v);
        }
    }
    public void selectCorpus(String id,String version){
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try {
            try(Cursor c=db.rawQuery("SELECT 1 FROM corpus WHERE corpus_id=? AND corpus_version=?",new String[]{id,version})){
                if(!c.moveToFirst())throw new IllegalArgumentException("句库不存在");
            }
            if(!Objects.equals(id,getSetting("corpus_id"))||!Objects.equals(version,getSetting("corpus_version"))){
                archiveTask(db);setSetting(db,"corpus_id",id);setSetting(db,"corpus_version",version);restoreTask(db);
            }
            db.setTransactionSuccessful();
        } finally {db.endTransaction();}
    }
    private String taskNamespace(){
        StringBuilder key=new StringBuilder("saved_task.");
        for(String field:new String[]{"speaker_id","corpus_id","corpus_version"}){
            String value=getSetting(field);if(value==null)return null;
            key.append(value.length()).append(':').append(value).append('|');
        }
        return key.append('.').toString();
    }
    private void archiveTask(SQLiteDatabase db){
        String prefix=taskNamespace();if(prefix==null||!hasTaskForCurrentContext())return;
        try(Cursor c=db.rawQuery("SELECT key,value FROM settings WHERE key GLOB 'task_*'",null)){
            while(c.moveToNext())setSetting(db,prefix+c.getString(0),c.getString(1));
        }
    }
    private void restoreTask(SQLiteDatabase db){
        clearTask(db);String prefix=taskNamespace();if(prefix==null)return;
        // Prefix comparison is literal: speaker IDs can contain SQL wildcard characters.
        try(Cursor c=db.rawQuery("SELECT key,value FROM settings WHERE substr(key,1,length(?))=?",new String[]{prefix,prefix})){
            while(c.moveToNext())setSetting(db,c.getString(0).substring(prefix.length()),c.getString(1));
        }
    }
    private void setSetting(SQLiteDatabase db,String k,String v){ContentValues c=new ContentValues();c.put("key",k);c.put("value",v);db.insertWithOnConflict("settings",null,c,SQLiteDatabase.CONFLICT_REPLACE);}
    public String getSetting(String key){try(Cursor c=getReadableDatabase().query("settings",new String[]{"value"},"key=?",new String[]{key},null,null,null)){return c.moveToFirst()?c.getString(0):null;}}
    public void setSetting(String key,String value){setSetting(getWritableDatabase(),key,value);}
    private void clearTask(SQLiteDatabase db){db.delete("settings","key GLOB ?",new String[]{"task_*"});}
    public void saveTask(String category,int start,int end,int current,List<Sentence> task){
        SQLiteDatabase db=getWritableDatabase(); db.beginTransaction();
        try{
            setSetting(db,"task_category",category);
            setSetting(db,"task_start",String.valueOf(start));
            setSetting(db,"task_end",String.valueOf(end));
            setSetting(db,"task_current",String.valueOf(current));
            setSetting(db,"task_order_ids",TaskOrder.encode(task));
            setSetting(db,"task_speaker_id",getSetting("speaker_id"));
            setSetting(db,"task_corpus_id",getSetting("corpus_id"));
            setSetting(db,"task_corpus_version",getSetting("corpus_version"));
            archiveTask(db);
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }
    public boolean hasTaskForCurrentContext(){
        if(getSetting("task_start")==null||getSetting("task_end")==null)return false;
        // Tasks from versions before 1.5 have no snapshot; migrate them on first resume.
        if(getSetting("task_order_ids")==null)
            return getSetting("task_speaker_id")==null&&getSetting("task_corpus_id")==null&&getSetting("task_corpus_version")==null;
        return Objects.equals(getSetting("task_speaker_id"),getSetting("speaker_id"))
            &&Objects.equals(getSetting("task_corpus_id"),getSetting("corpus_id"))
            &&Objects.equals(getSetting("task_corpus_version"),getSetting("corpus_version"));
    }
    public void saveSpeaker(String id,String dialect){
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try{
            boolean changed=!Objects.equals(id,getSetting("speaker_id"));if(changed)archiveTask(db);
            ContentValues c=new ContentValues();c.put("speaker_id",id);c.put("dialect",dialect);
            if(db.update("speaker",c,"speaker_id=?",new String[]{id})==0){c.put("local_uuid",UUID.randomUUID().toString());db.insertOrThrow("speaker",null,c);}
            setSetting(db,"speaker_id",id);setSetting(db,"dialect",dialect);if(changed)restoreTask(db);db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }
    public List<Sentence> list(String category,int start,int end){List<Sentence> out=new ArrayList<>();StringBuilder q=new StringBuilder("SELECT sentence_id,text,category,difficulty,length_bucket,notes FROM sentence WHERE corpus_id=? AND corpus_version=?");List<String>a=new ArrayList<>();a.add(getSetting("corpus_id"));a.add(getSetting("corpus_version"));if(category!=null&&!category.isEmpty()){q.append(" AND category=?");a.add(category);}q.append(" AND order_index BETWEEN ? AND ? ORDER BY order_index");a.add(String.valueOf(Math.max(1,start)));a.add(String.valueOf(Math.max(start,end)));try(Cursor c=getReadableDatabase().rawQuery(q.toString(),a.toArray(new String[0]))){while(c.moveToNext())out.add(new Sentence(c.getString(0),c.getString(1),c.getString(2),c.getString(3),c.getString(4),c.getString(5)));}return out;}
    public List<String> categories(){List<String> out=new ArrayList<>();try(Cursor c=getReadableDatabase().rawQuery("SELECT DISTINCT category FROM sentence WHERE corpus_id=? AND corpus_version=? ORDER BY category",new String[]{getSetting("corpus_id"),getSetting("corpus_version")})){while(c.moveToNext())out.add(c.getString(0));}return out;}
    public static final class ContextSnapshot {
        public final String speakerId,corpusId,version;
        ContextSnapshot(String speakerId,String corpusId,String version){this.speakerId=speakerId;this.corpusId=corpusId;this.version=version;}
    }
    public ContextSnapshot currentContext(){
        // One SQLite statement sees all three settings from the same committed state.
        try(Cursor c=getReadableDatabase().rawQuery("SELECT (SELECT value FROM settings WHERE key='speaker_id'),(SELECT value FROM settings WHERE key='corpus_id'),(SELECT value FROM settings WHERE key='corpus_version')",null)){
            if(!c.moveToFirst())throw new IllegalStateException("缺少采集上下文");
            return new ContextSnapshot(c.getString(0),c.getString(1),c.getString(2));
        }
    }
    public int count(String status){return count(currentContext(),status);}
    public int count(ContextSnapshot snapshot,String status){
        String sid=snapshot.speakerId, cid=snapshot.corpusId, ver=snapshot.version;
        if(status==null){try(Cursor c=getReadableDatabase().rawQuery("SELECT COUNT(*) FROM sentence WHERE corpus_id=? AND corpus_version=?",new String[]{cid,ver})){return c.moveToFirst()?c.getInt(0):0;}}
        String condition=status.equals("completed")?"p.status='completed'":status.equals("skipped")?"p.status='skipped'":"p.status IS NULL OR p.status='not_recorded'";
        String q="SELECT COUNT(*) FROM sentence s LEFT JOIN progress p ON p.sentence_id=s.sentence_id AND p.speaker_id=? AND p.corpus_id=? AND p.corpus_version=? WHERE s.corpus_id=? AND s.corpus_version=? AND ("+condition+")";
        try(Cursor c=getReadableDatabase().rawQuery(q,new String[]{sid,cid,ver,cid,ver})){return c.moveToFirst()?c.getInt(0):0;}
    }
    public String statusFor(String sentenceId){String sid=getSetting("speaker_id"),cid=getSetting("corpus_id"),ver=getSetting("corpus_version");try(Cursor c=getReadableDatabase().rawQuery("SELECT COALESCE(status,'not_recorded') FROM progress WHERE speaker_id=? AND corpus_id=? AND corpus_version=? AND sentence_id=?",new String[]{sid,cid,ver,sentenceId})){return c.moveToFirst()?c.getString(0):"not_recorded";}}
    public void skip(String sentenceId,String reason){SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try{String sid=getSetting("speaker_id"),cid=getSetting("corpus_id"),ver=getSetting("corpus_version");db.execSQL("UPDATE recording SET selected=0 WHERE speaker_id=? AND corpus_id=? AND corpus_version=? AND sentence_id=?",new String[]{sid,cid,ver,sentenceId});ContentValues v=new ContentValues();v.put("speaker_id",sid);v.put("corpus_id",cid);v.put("corpus_version",ver);v.put("sentence_id",sentenceId);v.put("status","skipped");v.putNull("selected_recording_id");v.put("skip_reason",reason);v.put("updated_at",System.currentTimeMillis());db.insertWithOnConflict("progress",null,v,SQLiteDatabase.CONFLICT_REPLACE);db.setTransactionSuccessful();}finally{db.endTransaction();}}
    public String importCorpus(InputStream input, String title, boolean csv) throws Exception {
        List<Sentence> rows=new ArrayList<>();
        if(csv){
            List<String[]> all=CsvReader.read(input); if(all.isEmpty()) throw new IOException("CSV为空"); String[] header=all.remove(0);
            int idCol=-1,textCol=-1,catCol=-1,diffCol=-1,bucketCol=-1,notesCol=-1; for(int i=0;i<header.length;i++){String h=header[i].trim().toLowerCase(Locale.ROOT);if("sentence_id".equals(h))idCol=i;if("text".equals(h)||"prompt_text".equals(h))textCol=i;if("category".equals(h))catCol=i;if("difficulty".equals(h))diffCol=i;if("length_bucket".equals(h))bucketCol=i;if("notes".equals(h))notesCol=i;}
            if(textCol<0) throw new IOException("CSV必须包含 text 或 prompt_text 列"); int n=1;
            for(String[] r:all){if(r.length<=textCol||r[textCol].trim().isEmpty())continue; String id=idCol>=0&&r.length>idCol&&!r[idCol].trim().isEmpty()?r[idCol]:String.format(Locale.ROOT,"S%06d",n);if(!id.matches("[A-Za-z0-9._-]+"))throw new IOException("sentence_id 含不安全字符："+id);String cat=catCol>=0&&r.length>catCol?r[catCol]:"导入";String diff=diffCol>=0&&r.length>diffCol&&!r[diffCol].isEmpty()?r[diffCol]:"short";rows.add(new Sentence(id,r[textCol],cat,diff,bucketCol>=0&&r.length>bucketCol?r[bucketCol]:"",notesCol>=0&&r.length>notesCol?r[notesCol]:""));n++;}
        } else { BufferedReader br=new BufferedReader(new InputStreamReader(input,StandardCharsets.UTF_8)); String line; int n=1; while((line=br.readLine())!=null){if(!line.trim().isEmpty())rows.add(new Sentence(String.format(Locale.ROOT,"S%06d",n++),line.trim(),"导入","short"));} }
        if(rows.isEmpty()) throw new IOException("句库没有有效句子"); Set<String> ids=new HashSet<>(); for(Sentence row:rows) if(!ids.add(row.id)) throw new IOException("重复 sentence_id："+row.id); String corpusId="import_"+System.currentTimeMillis(), version="1.0", hash=CorpusHash.sha256(rows); SQLiteDatabase db=getWritableDatabase(); db.beginTransaction();
        try{archiveTask(db);ContentValues cv=new ContentValues();cv.put("corpus_id",corpusId);cv.put("corpus_version",version);cv.put("corpus_hash",hash);cv.put("title",title);cv.put("sentence_count",rows.size());db.insertOrThrow("corpus",null,cv);int order=0;for(Sentence s:rows){ContentValues v=new ContentValues();v.put("corpus_id",corpusId);v.put("corpus_version",version);v.put("sentence_id",s.id);v.put("text",s.text);v.put("category",s.category);v.put("difficulty",s.difficulty);v.put("length_bucket",s.lengthBucket);v.put("notes",s.notes);v.put("order_index",++order);db.insertOrThrow("sentence",null,v);}clearTask(db);setSetting(db,"corpus_id",corpusId);setSetting(db,"corpus_version",version);db.setTransactionSuccessful();}finally{db.endTransaction();}
        return corpusId;
    }

    public void saveRecording(String id,String sentenceId,String path,long duration,int captureRate,String audioSource,String audioSha256){
        SQLiteDatabase db=getWritableDatabase(); db.beginTransaction();
        try{
            String sid=getSetting("speaker_id"), cid=getSetting("corpus_id"), ver=getSetting("corpus_version"); int take=1;
            try(Cursor c=db.rawQuery("SELECT COALESCE(MAX(take_no),0)+1 FROM recording WHERE speaker_id=? AND corpus_id=? AND corpus_version=? AND sentence_id=?",new String[]{sid,cid,ver,sentenceId})){if(c.moveToFirst())take=c.getInt(0);}
            db.execSQL("UPDATE recording SET selected=0 WHERE speaker_id=? AND corpus_id=? AND corpus_version=? AND sentence_id=?",new String[]{sid,cid,ver,sentenceId});
            ContentValues r=new ContentValues();r.put("recording_id",id);r.put("speaker_id",sid);r.put("corpus_id",cid);r.put("corpus_version",ver);r.put("sentence_id",sentenceId);r.put("wav_path",path);r.put("duration_ms",duration);r.put("sample_rate",16000);r.put("bits",16);r.put("channels",1);r.put("capture_rate",captureRate);r.put("audio_source",audioSource);r.put("audio_sha256",audioSha256);r.put("status","completed");r.put("selected",1);r.put("transcription_status","not_annotated");r.put("take_no",take);r.put("created_at",System.currentTimeMillis());db.insertOrThrow("recording",null,r);
            ContentValues prog=new ContentValues();prog.put("speaker_id",sid);prog.put("corpus_id",cid);prog.put("corpus_version",ver);prog.put("sentence_id",sentenceId);prog.put("status","completed");prog.put("selected_recording_id",id);prog.put("updated_at",System.currentTimeMillis());db.insertWithOnConflict("progress",null,prog,SQLiteDatabase.CONFLICT_REPLACE);db.setTransactionSuccessful();
        } finally {db.endTransaction();}
    }
}
