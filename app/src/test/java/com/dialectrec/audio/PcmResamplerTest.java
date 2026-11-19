package com.dialectrec.audio;

import org.junit.Test;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import static org.junit.Assert.assertEquals;

public class PcmResamplerTest {
    @Test public void changesFrameCountWithoutChangingPcm16Encoding() {
        byte[] input = new byte[4410 * 2];
        byte[] output = PcmResampler.resample(input, 44100, 16000);
        assertEquals(1600 * 2, output.length);
        assertEquals(0, ByteBuffer.wrap(output).order(ByteOrder.LITTLE_ENDIAN).getShort(0));
    }
}
