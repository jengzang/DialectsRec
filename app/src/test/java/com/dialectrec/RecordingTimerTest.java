package com.dialectrec;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class RecordingTimerTest {
    @Test public void formatsElapsedMillisecondsAsMinutesAndSeconds() {
        assertEquals("00:00", RecordingTimer.format(0));
        assertEquals("00:12", RecordingTimer.format(12_345));
        assertEquals("01:05", RecordingTimer.format(65_999));
    }

    @Test public void labelsRecordingButtonWithLiveElapsedTime() {
        assertEquals("● 录音中 00:05", RecordingTimer.recordingLabel(5_000));
    }

    @Test public void capsRecordingButtonAtSixtySecondLimit() {
        assertEquals("● 录音中 01:00", RecordingTimer.recordingLabel(70_000));
    }
}
