package com.dialectrec;

import java.util.Locale;

public final class RecordingTimer {
    private RecordingTimer() {}
    public static String format(long elapsedMs) {
        long seconds = Math.max(0L, elapsedMs) / 1000L;
        return String.format(Locale.ROOT, "%02d:%02d", seconds / 60L, seconds % 60L);
    }

    public static String recordingLabel(long elapsedMs) {
        return "● 录音中 " + format(Math.min(Math.max(0L, elapsedMs), 60_000L));
    }
}
