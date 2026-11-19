package com.dialectrec;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class RecordingUiStateTest {
    @Test public void playbackStatesHaveClearUserLabels() {
        assertEquals("未试听", RecordingUiState.PLAYBACK_IDLE.label);
        assertEquals("正在试听", RecordingUiState.PLAYBACK_PLAYING.label);
        assertEquals("试听完成", RecordingUiState.PLAYBACK_COMPLETED.label);
        assertEquals("试听失败", RecordingUiState.PLAYBACK_ERROR.label);
    }
}
