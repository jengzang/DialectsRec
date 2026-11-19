package com.dialectrec.audio;

import org.junit.Test;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.file.Files;
import static org.junit.Assert.*;

public class WavWriterTest {
    @Test public void writesPcm16Mono16kHeaderAndFrameCount() throws Exception {
        File file = Files.createTempFile("test", ".wav").toFile();
        ByteArrayOutputStream pcm = new ByteArrayOutputStream();
        pcm.write(new byte[] {0, 1, 2, 3, 4, 5});
        WavWriter.write(file, pcm.toByteArray(), 16000, 1, 16);
        WavInspector.Info info = WavInspector.inspect(file);
        assertEquals(16000, info.sampleRate);
        assertEquals(1, info.channels);
        assertEquals(16, info.bitsPerSample);
        assertEquals(3, info.frames);
        assertTrue(info.valid);
        assertNull(info.spokenTranscript);
    }
}
