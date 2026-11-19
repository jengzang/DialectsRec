package com.dialectrec.audio;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import java.io.File;
import java.io.IOException;

public final class AudioPlayer {
    public interface Listener {
        void onPreparing();
        void onPlaying();
        void onCompleted();
        void onError(String message);
    }

    private final AudioManager audioManager;
    private final AudioFocusRequest focusRequest;
    private MediaPlayer player;
    private Listener listener;

    public AudioPlayer(Context context) {
        audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        AudioAttributes attributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build();
        focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(attributes)
                .setOnAudioFocusChangeListener(change -> { if (change <= 0) stop(); })
                .build();
    }

    public synchronized void play(File file, Listener callback) throws Exception {
        if (file == null || !file.isFile()) throw new IOException("录音文件不存在");
        stop();
        listener = callback;
        if (listener != null) listener.onPreparing();
        int focus = audioManager.requestAudioFocus(focusRequest);
        if (focus != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) throw new IOException("无法获得媒体播放焦点");
        MediaPlayer next = new MediaPlayer();
        player = next;
        next.setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build());
        next.setVolume(1.0f, 1.0f);
        next.setDataSource(file.getAbsolutePath());
        next.setOnPreparedListener(prepared -> {
            synchronized (AudioPlayer.this) {
                if (player != prepared) return;
                if (listener != null) listener.onPlaying();
                prepared.start();
            }
        });
        next.setOnCompletionListener(completed -> {
            Listener done;
            synchronized (AudioPlayer.this) { done = listener; }
            if (done != null) done.onCompleted();
            stop();
        });
        next.setOnErrorListener((failed, what, extra) -> {
            Listener failedListener;
            synchronized (AudioPlayer.this) { failedListener = listener; }
            if (failedListener != null) failedListener.onError("播放器错误（" + what + ", " + extra + "）");
            stop();
            return true;
        });
        next.prepareAsync();
    }

    public synchronized boolean isPlaying() { return player != null && player.isPlaying(); }

    public synchronized void stop() {
        MediaPlayer old = player;
        player = null;
        listener = null;
        if (old != null) {
            try { old.setOnCompletionListener(null); old.setOnErrorListener(null); old.stop(); } catch (Exception ignored) {}
            old.release();
        }
        if (audioManager != null) audioManager.abandonAudioFocusRequest(focusRequest);
    }
}
