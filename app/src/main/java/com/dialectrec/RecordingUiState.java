package com.dialectrec;

public final class RecordingUiState {
    public static final Playback PLAYBACK_IDLE = Playback.PLAYBACK_IDLE;
    public static final Playback PLAYBACK_PREPARING = Playback.PLAYBACK_PREPARING;
    public static final Playback PLAYBACK_PLAYING = Playback.PLAYBACK_PLAYING;
    public static final Playback PLAYBACK_COMPLETED = Playback.PLAYBACK_COMPLETED;
    public static final Playback PLAYBACK_ERROR = Playback.PLAYBACK_ERROR;
    public enum Playback {
        PLAYBACK_IDLE("未试听"),
        PLAYBACK_PREPARING("正在准备试听"),
        PLAYBACK_PLAYING("正在试听"),
        PLAYBACK_COMPLETED("试听完成"),
        PLAYBACK_ERROR("试听失败");
        public final String label;
        Playback(String label) { this.label = label; }
    }
    public static boolean canPlay(boolean hasSavedAudio, boolean recording, boolean saving) {
        return hasSavedAudio && !recording && !saving;
    }
    private RecordingUiState() {}
}
