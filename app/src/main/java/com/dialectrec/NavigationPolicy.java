package com.dialectrec;

/** Small, UI-independent navigation rule used by the activity and unit tests. */
public final class NavigationPolicy {
    private NavigationPolicy() {}

    public static String backTarget(String screen, boolean recording) {
        if ("recording".equals(screen)) return recording ? "recording" : "task";
        if ("corpus".equals(screen) || "task".equals(screen) || "list".equals(screen) || "settings".equals(screen)) return "home";
        if ("guide".equals(screen)) return "onboarding";
        return "exit";
    }
}
