package com.dialectrec.audio;

import android.media.MediaPlayer;
import java.io.File;

public final class AudioPlayer {
    private MediaPlayer player;
    public void play(File file) throws Exception { stop(); player = new MediaPlayer(); player.setDataSource(file.getAbsolutePath()); player.prepare(); player.setOnCompletionListener(p -> stop()); player.start(); }
    public void stop() { if(player!=null){try{player.stop();}catch(Exception ignored){} player.release(); player=null;} }
}
