package com.dialectrec.data;

import org.junit.Test;
import java.util.Arrays;
import static org.junit.Assert.assertEquals;

public class CorpusHashTest {
    @Test public void hashesCanonicalSentenceFields() {
        Sentence s = new Sentence("S000001", "你好", "日常", "short");
        assertEquals("65176d5b01249cea9ff9713686dfc09503ab8a7f00101b4d484528069f141c68", CorpusHash.sha256(Arrays.asList(s)));
    }
}
