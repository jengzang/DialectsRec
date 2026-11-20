package com.dialectrec.data;

import org.junit.Test;
import java.util.Arrays;
import static org.junit.Assert.assertEquals;

public class CorpusHashTest {
    @Test public void hashesCanonicalSentenceFields() {
        Sentence s = new Sentence("S000001", "你好", "日常", "short");
        assertEquals("65176d5b01249cea9ff9713686dfc09503ab8a7f00101b4d484528069f141c68", CorpusHash.sha256(Arrays.asList(s)));
    }

    @Test public void includesOriginalNotesInCanonicalHash() {
        Sentence s = new Sentence("S000001", "你好", "日常", "short", "short", "复核备注");
        assertEquals("3611c932cc458a0a481ba76003acfd1e087800d8623ad986b6db41217aba2406", CorpusHash.sha256(Arrays.asList(s)));
    }
}
