package com.dialectrec;

import org.junit.Test;
import static org.junit.Assert.*;

public class RecordingUiStateTest {
    @Test public void playbackRequiresSavedAudioAndIdleRecorder() {
        assertTrue(RecordingUiState.canPlay(true,false,false));
        assertFalse(RecordingUiState.canPlay(false,false,false));
        assertFalse(RecordingUiState.canPlay(true,true,false));
        assertFalse(RecordingUiState.canPlay(true,false,true));
    }
    @Test public void playbackStatesHaveClearUserLabels() {
        assertEquals("未试听", RecordingUiState.PLAYBACK_IDLE.label);
        assertEquals("正在试听", RecordingUiState.PLAYBACK_PLAYING.label);
        assertEquals("试听完成", RecordingUiState.PLAYBACK_COMPLETED.label);
        assertEquals("试听失败", RecordingUiState.PLAYBACK_ERROR.label);
    }
}
