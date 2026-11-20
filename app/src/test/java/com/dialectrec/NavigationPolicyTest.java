package com.dialectrec;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class NavigationPolicyTest {
    @Test public void backFromCorpusSelectionReturnsHome() {
        assertEquals("home", NavigationPolicy.backTarget("corpus", false));
    }

    @Test public void backFromTaskReturnsHome() {
        assertEquals("home", NavigationPolicy.backTarget("task", false));
    }

    @Test public void backFromRecordingReturnsTaskWhenIdle() {
        assertEquals("task", NavigationPolicy.backTarget("recording", false));
    }

    @Test public void backWhileRecordingStaysOnRecordingForDialog() {
        assertEquals("recording", NavigationPolicy.backTarget("recording", true));
    }
}
