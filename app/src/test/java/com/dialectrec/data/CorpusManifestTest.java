package com.dialectrec.data;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class CorpusManifestTest {
    @Test public void readsCorpusIdentityFromManifestJson() {
        CorpusDatabase.ManifestInfo info = CorpusDatabase.parseManifest("{\"corpus_id\":\"dialect_general\",\"corpus_version\":\"2.0\",\"corpus_hash\":\"abc\"}");
        assertEquals("dialect_general", info.id);
        assertEquals("2.0", info.version);
        assertEquals("abc", info.hash);
    }
}
