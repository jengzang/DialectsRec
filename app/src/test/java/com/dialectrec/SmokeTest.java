package com.dialectrec;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class SmokeTest {
    @Test public void appPackageIsStable() { assertEquals("com.dialectrec", MainActivity.APP_PACKAGE); }
}
