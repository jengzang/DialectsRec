package com.dialectrec;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class ExportChoiceTest {
    @Test public void offersSeparateShareAndLocalSavePaths() {
        assertEquals("分享到微信", ExportChoice.SHARE_WECHAT.label);
        assertEquals("仅保存本地", ExportChoice.LOCAL_ONLY.label);
    }
}
