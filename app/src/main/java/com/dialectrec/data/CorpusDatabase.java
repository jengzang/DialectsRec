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
    public CorpusDatabase(Context c) { super(c, DB, null, 1); }
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE corpus(corpus_id TEXT NOT NULL, corpus_version TEXT NOT NULL, corpus_hash TEXT NOT NULL, title TEXT, sentence_count INTEGER, PRIMARY KEY(corpus_id,corpus_version))");
        db.execSQL("CREATE TABLE sentence(corpus_id TEXT, corpus_version TEXT, sentence_id TEXT, text TEXT, category TEXT, difficulty TEXT, order_index INTEGER, PRIMARY KEY(corpus_id,corpus_version,sentence_id))");
        db.execSQL("CREATE TABLE speaker(speaker_id TEXT PRIMARY KEY, dialect TEXT, local_uuid TEXT)");
        db.execSQL("CREATE TABLE progress(speaker_id TEXT, corpus_id TEXT, corpus_version TEXT, sentence_id TEXT, status TEXT, selected_recording_id TEXT, skip_reason TEXT, review_flag INTEGER DEFAULT 0, updated_at INTEGER, PRIMARY KEY(speaker_id,corpus_id,corpus_version,sentence_id))");
        db.execSQL("CREATE TABLE recording(recording_id TEXT PRIMARY KEY, speaker_id TEXT, corpus_id TEXT, corpus_version TEXT, sentence_id TEXT, wav_path TEXT, duration_ms INTEGER, sample_rate INTEGER, bits INTEGER, channels INTEGER, capture_rate INTEGER, audio_sha256 TEXT, status TEXT, selected INTEGER, review_flag INTEGER, spoken_transcript TEXT, transcription_status TEXT, take_no INTEGER, created_at INTEGER)");
        db.execSQL("CREATE TABLE settings(key TEXT PRIMARY KEY, value TEXT)");
    }
    @Override public void onUpgrade(SQLiteDatabase db,int oldV,int newV) {}
    private static byte[] readAll(InputStream in) throws IOException { ByteArrayOutputStream b=new ByteArrayOutputStream(); byte[] buf=new byte[8192]; int n; while((n=in.read(buf))>=0){if(n>0)b.write(buf,0,n);} return b.toByteArray(); }
    public void seed(Context context) throws Exception {
        if (getSetting("seeded") != null) return;
        SQLiteDatabase db=getWritableDatabase(); db.beginTransaction();
        try (InputStream in=context.getAssets().open("corpus_v1/sentences.csv")) {
            List<String[]> rows=CsvReader.read(in); String[] h=rows.remove(0); int id=0;
            String corpusId="dialect_general", version="1.0", hash="";
            try (InputStream mi=context.getAssets().open("corpus_v1/manifest.json")) { String m=new String(readAll(mi), StandardCharsets.UTF_8); Matcher mm=Pattern.compile("\"corpus_hash\"\\s*:\\s*\"([^\"]+)\"").matcher(m); if(mm.find()) hash=mm.group(1); }
            ContentValues cv=new ContentValues(); cv.put("corpus_id",corpusId);cv.put("corpus_version",version);cv.put("corpus_hash",hash);cv.put("title","统一方言提示句库");cv.put("sentence_count",rows.size());db.insertOrThrow("corpus",null,cv);
            for(String[] r:rows){if(r.length<4||r[0].trim().isEmpty()||r[1].trim().isEmpty())continue; ContentValues v=new ContentValues();v.put("corpus_id",corpusId);v.put("corpus_version",version);v.put("sentence_id",r[0]);v.put("text",r[1]);v.put("category",r.length>2?r[2]:"");v.put("difficulty",r.length>3?r[3]:"short");v.put("order_index",++id);db.insertOrThrow("sentence",null,v);}
            setSetting(db,"seeded","1"); setSetting(db,"corpus_id",corpusId); setSetting(db,"corpus_version",version); db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }
    private void setSetting(SQLiteDatabase db,String k,String v){ContentValues c=new ContentValues();c.put("key",k);c.put("value",v);db.insertWithOnConflict("settings",null,c,SQLiteDatabase.CONFLICT_REPLACE);}
    public String getSetting(String key){try(Cursor c=getReadableDatabase().query("settings",new String[]{"value"},"key=?",new String[]{key},null,null,null)){return c.moveToFirst()?c.getString(0):null;}}
    public void setSetting(String key,String value){setSetting(getWritableDatabase(),key,value);}
    public void saveSpeaker(String id,String dialect){ContentValues c=new ContentValues();c.put("speaker_id",id);c.put("dialect",dialect);c.put("local_uuid",java.util.UUID.randomUUID().toString());getWritableDatabase().insertWithOnConflict("speaker",null,c,SQLiteDatabase.CONFLICT_REPLACE);setSetting("speaker_id",id);setSetting("dialect",dialect);}
    public List<Sentence> list(String category,int start,int end){List<Sentence> out=new ArrayList<>();StringBuilder q=new StringBuilder("SELECT sentence_id,text,category,difficulty FROM sentence WHERE 1=1");List<String>a=new ArrayList<>();if(category!=null&&!category.isEmpty()){q.append(" AND category=?");a.add(category);}q.append(" AND order_index BETWEEN ? AND ? ORDER BY order_index");a.add(String.valueOf(Math.max(1,start)));a.add(String.valueOf(Math.max(start,end)));try(Cursor c=getReadableDatabase().rawQuery(q.toString(),a.toArray(new String[0]))){while(c.moveToNext())out.add(new Sentence(c.getString(0),c.getString(1),c.getString(2),c.getString(3)));}return out;}
    public List<String> categories(){List<String> out=new ArrayList<>();try(Cursor c=getReadableDatabase().rawQuery("SELECT DISTINCT category FROM sentence ORDER BY category",null)){while(c.moveToNext())out.add(c.getString(0));}return out;}
    public int count(String status){String sid=getSetting("speaker_id");String q=status==null?"SELECT COUNT(*) FROM sentence":"SELECT COUNT(*) FROM sentence s LEFT JOIN progress p ON p.sentence_id=s.sentence_id AND p.speaker_id=? WHERE "+(status.equals("completed")?"p.status='completed'":status.equals("skipped")?"p.status='skipped'":"p.status IS NULL OR p.status='not_recorded'");try(Cursor c=status==null?getReadableDatabase().rawQuery(q,null):getReadableDatabase().rawQuery(q,new String[]{sid})){return c.moveToFirst()?c.getInt(0):0;}}
    public void skip(String sentenceId,String reason){ContentValues v=new ContentValues();v.put("speaker_id",getSetting("speaker_id"));v.put("corpus_id",getSetting("corpus_id"));v.put("corpus_version",getSetting("corpus_version"));v.put("sentence_id",sentenceId);v.put("status","skipped");v.put("skip_reason",reason);v.put("updated_at",System.currentTimeMillis());getWritableDatabase().insertWithOnConflict("progress",null,v,SQLiteDatabase.CONFLICT_REPLACE);}
    public String importCorpus(InputStream input, String title, boolean csv) throws Exception {
        List<Sentence> rows=new ArrayList<>();
        if(csv){ List<String[]> all=CsvReader.read(input); if(all.isEmpty()) throw new IOException("CSV为空"); String[] header=all.remove(0); int idCol=-1,textCol=-1; for(int i=0;i<header.length;i++){if("sentence_id".equalsIgnoreCase(header[i]))idCol=i;if("text".equalsIgnoreCase(header[i]))textCol=i;} if(textCol<0) throw new IOException("CSV必须包含text列"); int n=1; for(String[] r:all){if(r.length<=textCol||r[textCol].trim().isEmpty())continue; String id=idCol>=0&&r.length>idCol&&!r[idCol].trim().isEmpty()?r[idCol]:String.format(Locale.ROOT,"S%06d",n);rows.add(new Sentence(id,r[textCol],r.length>2?r[2]:"导入", "short"));n++;}}
        else { BufferedReader br=new BufferedReader(new InputStreamReader(input,StandardCharsets.UTF_8)); String line; int n=1; while((line=br.readLine())!=null){if(!line.trim().isEmpty())rows.add(new Sentence(String.format("S%06d",n++),line.trim(),"导入","short"));} }
        if(rows.isEmpty()) throw new IOException("句库没有有效句子"); String corpusId="import_"+System.currentTimeMillis(), version="1.0"; SQLiteDatabase db=getWritableDatabase(); db.beginTransaction(); try{ContentValues cv=new ContentValues();cv.put("corpus_id",corpusId);cv.put("corpus_version",version);cv.put("corpus_hash",Integer.toHexString(rows.hashCode()));cv.put("title",title);cv.put("sentence_count",rows.size());db.insertOrThrow("corpus",null,cv);int order=0;for(Sentence s:rows){ContentValues v=new ContentValues();v.put("corpus_id",corpusId);v.put("corpus_version",version);v.put("sentence_id",s.id);v.put("text",s.text);v.put("category",s.category);v.put("difficulty",s.difficulty);v.put("order_index",++order);db.insertOrThrow("sentence",null,v);}db.setTransactionSuccessful();}finally{db.endTransaction();}return corpusId;
    }

    public void saveRecording(String id,String sentenceId,String path,long duration,int take,int captureRate){SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try{ContentValues r=new ContentValues();r.put("recording_id",id);r.put("speaker_id",getSetting("speaker_id"));r.put("corpus_id",getSetting("corpus_id"));r.put("corpus_version",getSetting("corpus_version"));r.put("sentence_id",sentenceId);r.put("wav_path",path);r.put("duration_ms",duration);r.put("sample_rate",16000);r.put("bits",16);r.put("channels",1);r.put("capture_rate",captureRate);r.put("status","completed");r.put("selected",1);r.put("transcription_status","not_annotated");r.put("take_no",take);r.put("created_at",System.currentTimeMillis());db.insertOrThrow("recording",null,r);ContentValues p=new ContentValues();p.put("speaker_id",getSetting("speaker_id"));p.put("corpus_id",getSetting("corpus_id"));p.put("corpus_version",getSetting("corpus_version"));p.put("sentence_id",sentenceId);p.put("status","completed");p.put("selected_recording_id",id);p.put("updated_at",System.currentTimeMillis());db.insertWithOnConflict("progress",null,p,SQLiteDatabase.CONFLICT_REPLACE);db.setTransactionSuccessful();}finally{db.endTransaction();}}
}
