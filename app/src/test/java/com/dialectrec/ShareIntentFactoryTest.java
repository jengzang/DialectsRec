package com.dialectrec;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class ShareIntentFactoryTest {
    @Test public void usesZipShareContract() {
        assertEquals("android.intent.action.SEND", ShareIntentSpec.ACTION);
        assertEquals("application/zip", ShareIntentSpec.MIME);
    }
}
