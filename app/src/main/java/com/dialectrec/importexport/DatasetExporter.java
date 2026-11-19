package com.dialectrec.importexport;

import android.content.*;
import android.database.Cursor;
import com.dialectrec.data.CorpusDatabase;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.zip.*;

public final class DatasetExporter {
    private DatasetExporter() {}
    public static void export(Context context, CorpusDatabase db, android.net.Uri uri) throws Exception {
        String speakerId=db.getSetting("speaker_id");
        if (speakerId==null||speakerId.isEmpty()) throw new IOException("请先建立发音人资料");
        OutputStream raw=context.getContentResolver().openOutputStream(uri);
        if (raw==null) throw new IOException("无法打开导出目标");
        try (ZipOutputStream zip=new ZipOutputStream(new BufferedOutputStream(raw))) {
            String corpusId=db.getSetting("corpus_id"), version=db.getSetting("corpus_version");
            SimpleDateFormat formatter=new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'",Locale.US); formatter.setTimeZone(TimeZone.getTimeZone("UTC")); String exported=formatter.format(new Date());
            String corpusHash="";
            try(Cursor c=db.getReadableDatabase().rawQuery("SELECT corpus_hash FROM corpus WHERE corpus_id=? AND corpus_version=?",new String[]{corpusId,version})){if(c.moveToFirst()) corpusHash=c.getString(0);}
            put(zip,"manifest.json","{\n  \"schema_version\":\"1.0\",\n  \"export_id\":\""+UUID.randomUUID()+"\",\n  \"exported_at_utc\":\""+exported+"\",\n  \"app_version\":\"1.0\",\n  \"corpus_id\":\""+escape(corpusId)+"\",\n  \"corpus_version\":\""+escape(version)+"\",\n  \"corpus_hash\":\""+escape(corpusHash)+"\",\n  \"speaker_id\":\""+escape(speakerId)+"\"\n}");
            put(zip,"corpus.json","{\"corpus_id\":\""+escape(corpusId)+"\",\"corpus_version\":\""+escape(version)+"\",\"corpus_hash\":\""+escape(corpusHash)+"\"}");
            String dialect="", localUuid=""; try(Cursor c=db.getReadableDatabase().rawQuery("SELECT dialect,local_uuid FROM speaker WHERE speaker_id=?",new String[]{speakerId})){if(c.moveToFirst()){dialect=c.getString(0);localUuid=c.getString(1);}}
            put(zip,"speaker.json","{\"speaker_id\":\""+escape(speakerId)+"\",\"local_speaker_uuid\":\""+escape(localUuid)+"\",\"dialect\":\""+escape(dialect)+"\"}");
            put(zip,"sentences.csv",sentencesCsv(db));
            put(zip,"recordings.csv",recordingsCsv(db,zip,context));
            put(zip,"progress.csv",progressCsv(db));
        }
        db.setSetting("last_export",String.valueOf(System.currentTimeMillis()));
    }
    private static String sentencesCsv(CorpusDatabase db){
        StringBuilder b=new StringBuilder("sentence_id,prompt_text,text,category,difficulty,length_bucket,notes,order_index\n");
        try(Cursor c=db.getReadableDatabase().rawQuery("SELECT sentence_id,text,category,difficulty,order_index FROM sentence WHERE corpus_id=? AND corpus_version=? ORDER BY order_index",new String[]{db.getSetting("corpus_id"),db.getSetting("corpus_version")})){
            while(c.moveToNext()){
                String difficulty=c.getString(3); String bucket=difficulty.equals("narrative")?"long":difficulty.equals("situational")?"medium":"short";
                b.append(csv(c.getString(0))).append(',').append(csv(c.getString(1))).append(',').append(csv(c.getString(1))).append(',').append(csv(c.getString(2))).append(',').append(csv(difficulty)).append(',').append(bucket).append(",,").append(c.getInt(4)).append('\n');
            }
        }
        return b.toString();
    }
    private static String recordingsCsv(CorpusDatabase db,ZipOutputStream zip,Context context)throws Exception{
        StringBuilder b=new StringBuilder("recording_id,sentence_id,speaker_id,audio_path,duration_ms,status,review_flag,spoken_transcript,transcription_status\n");
        try(Cursor c=db.getReadableDatabase().rawQuery("SELECT recording_id,sentence_id,speaker_id,wav_path,duration_ms,status,review_flag,spoken_transcript,transcription_status FROM recording WHERE selected=1 AND status='completed' AND speaker_id=? AND corpus_id=? AND corpus_version=?",new String[]{db.getSetting("speaker_id"),db.getSetting("corpus_id"),db.getSetting("corpus_version")})){
            while(c.moveToNext()){
                File f=new File(context.getFilesDir(),c.getString(3)); if(!f.exists()) throw new IOException("缺少录音文件："+c.getString(3));
                String out="wav/"+safeName(c.getString(1))+".wav"; zip.putNextEntry(new ZipEntry(out));
                try(FileInputStream in=new FileInputStream(f)){byte[]buf=new byte[8192];int n;while((n=in.read(buf))>0)zip.write(buf,0,n);} zip.closeEntry();
                b.append(csv(c.getString(0))).append(',').append(csv(c.getString(1))).append(',').append(csv(c.getString(2))).append(',').append(csv(out)).append(',').append(c.getLong(4)).append(',').append(csv(c.getString(5))).append(',').append(c.getInt(6)).append(',').append(csv(c.getString(7))).append(',').append(csv(c.getString(8))).append('\n');
            }
        }
        return b.toString();
    }
    private static String progressCsv(CorpusDatabase db){
        StringBuilder b=new StringBuilder("speaker_id,sentence_id,status,selected_recording_id,skip_reason,review_flag\n");
        String sid=db.getSetting("speaker_id"), cid=db.getSetting("corpus_id"), ver=db.getSetting("corpus_version");
        try(Cursor c=db.getReadableDatabase().rawQuery("SELECT s.sentence_id,COALESCE(p.status,'not_recorded'),COALESCE(p.selected_recording_id,''),COALESCE(p.skip_reason,''),COALESCE(p.review_flag,0) FROM sentence s LEFT JOIN progress p ON p.sentence_id=s.sentence_id AND p.speaker_id=? AND p.corpus_id=? AND p.corpus_version=? WHERE s.corpus_id=? AND s.corpus_version=? ORDER BY s.order_index",new String[]{sid,cid,ver,cid,ver})){
            while(c.moveToNext()) b.append(csv(sid)).append(',').append(csv(c.getString(0))).append(',').append(csv(c.getString(1))).append(',').append(csv(c.getString(2))).append(',').append(csv(c.getString(3))).append(',').append(c.getInt(4)).append('\n');
        }
        return b.toString();
    }
    private static void put(ZipOutputStream z,String name,String data)throws IOException{z.putNextEntry(new ZipEntry(name));z.write(data.getBytes(StandardCharsets.UTF_8));z.closeEntry();}
    private static String csv(String x){if(x==null)x="";return x.indexOf(',')>=0||x.indexOf('"')>=0||x.indexOf('\n')>=0?"\""+x.replace("\"","\"\"")+"\"":x;}
    private static String escape(String x){return x==null?"":x.replace("\\","\\\\").replace("\"","\\\"");}
    private static String safeName(String x){return x==null?"unknown":x.replaceAll("[^A-Za-z0-9._-]","_");}
}
