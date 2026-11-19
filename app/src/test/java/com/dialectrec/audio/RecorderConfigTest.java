package com.dialectrec.audio;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class RecorderConfigTest {
    @Test public void usesNativeMicAsPrimarySourceWithoutProcessing() {
        assertEquals("MIC", RecorderConfig.primarySourceName());
        assertEquals(false, RecorderConfig.applySoftwareEffects());
    }
}
