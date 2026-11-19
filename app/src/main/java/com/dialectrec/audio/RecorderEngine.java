package com.dialectrec.audio;

import android.annotation.SuppressLint;

import android.media.*;
import android.os.Process;
import java.io.*;
import java.util.UUID;

public final class RecorderEngine {
    public interface Listener { void onError(String message); }
    private static final long MAX_DURATION_MS = 60_000L; private final Listener listener; private AudioRecord recorder; private Thread thread; private File pcmFile; private int actualRate; private String sourceName; private volatile boolean recording; private volatile boolean limitReached; private volatile long startedAtMs;
    public RecorderEngine(Listener listener) { this.listener = listener; }
    public synchronized boolean isRecording() { return recorder != null && (recording || limitReached); }
    public synchronized int captureRate() { return actualRate; }
    public synchronized String audioSource() { return sourceName == null ? "" : sourceName; }
    public long elapsedMs() { return startedAtMs == 0L ? 0L : Math.max(0L, System.currentTimeMillis() - startedAtMs); }
    @SuppressLint("MissingPermission")
    public synchronized boolean start(File directory) {
        if (recording) return false;
        int[] rates = {16000, 48000, 44100};
        for (int rate : rates) {
            int min = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
            if (min <= 0) continue;
            try { recorder = new AudioRecord(MediaRecorder.AudioSource.UNPROCESSED, rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, Math.max(min * 2, 4096)); if (recorder.getState() == AudioRecord.STATE_INITIALIZED) { actualRate=rate; sourceName="UNPROCESSED"; break; } recorder.release(); recorder=null; } catch (Exception ignored) { }
            try { recorder = new AudioRecord(MediaRecorder.AudioSource.MIC, rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, Math.max(min * 2, 4096)); if (recorder.getState() == AudioRecord.STATE_INITIALIZED) { actualRate=rate; sourceName="MIC"; break; } recorder.release(); recorder=null; } catch (Exception ignored) { }
        }
        if (recorder == null) { listener.onError("录音设备初始化失败"); return false; }
        directory.mkdirs(); pcmFile = new File(directory, UUID.randomUUID() + ".pcm.tmp"); recording=true; limitReached=false; startedAtMs=System.currentTimeMillis(); recorder.startRecording();
        thread = new Thread(() -> captureLoop(), "dialect-audio-capture"); thread.start(); return true;
    }
    private void captureLoop() { Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO); byte[] buffer = new byte[8192]; try (FileOutputStream out = new FileOutputStream(pcmFile)) { long started=System.currentTimeMillis(); while (recording) { if(System.currentTimeMillis()-started >= MAX_DURATION_MS){ limitReached=true; recording=false; listener.onError("已达到60秒上限，请点击结束并保存"); break; } int n=recorder.read(buffer,0,buffer.length); if(n>0) out.write(buffer,0,n); else if(n<0) throw new IOException("AudioRecord read error " + n); } } catch(Exception e) { if(recording) listener.onError("录音写入失败：" + e.getMessage()); } }
    public synchronized File stop(File outputWav) throws Exception { if (recorder == null) return null; recording=false; limitReached=false; try { recorder.stop(); } catch(Exception ignored) {} if(thread!=null){thread.join(5000);if(thread.isAlive()){recorder.release();recorder=null;startedAtMs=0L;throw new IOException("录音线程未能安全结束");}} recorder.release(); recorder=null; startedAtMs=0L; if(pcmFile.length()<2) throw new IOException("录音为空"); if(actualRate==16000) { WavWriter.writeFromPcm(outputWav, pcmFile, actualRate, 1, 16); } else { byte[] raw=readAll(pcmFile); byte[] converted=PcmResampler.resample(raw, actualRate, 16000); WavWriter.write(outputWav, converted, 16000, 1, 16); } if(!WavInspector.inspect(outputWav).valid) throw new IOException("WAV规格校验失败"); if(!pcmFile.delete()) pcmFile.deleteOnExit(); return outputWav; }
    private static byte[] readAll(File file) throws IOException { try(FileInputStream in=new FileInputStream(file); ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[8192];int n;while((n=in.read(b))>0)out.write(b,0,n);return out.toByteArray();} }
    public synchronized void cancel() { if(recorder==null)return; recording=false; limitReached=false; try{recorder.stop();recorder.release();}catch(Exception ignored){} if(thread!=null)try{thread.join(1000);}catch(InterruptedException ignored){} if(pcmFile!=null)pcmFile.delete(); recorder=null; startedAtMs=0L; }
}
