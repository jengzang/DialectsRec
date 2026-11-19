package com.dialectrec.audio;

public final class RecorderConfig {
    private RecorderConfig() {}
    public static String primarySourceName() { return "MIC"; }
    public static boolean applySoftwareEffects() { return false; }
}
