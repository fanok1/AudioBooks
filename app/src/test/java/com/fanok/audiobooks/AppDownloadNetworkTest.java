package com.fanok.audiobooks;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AppDownloadNetworkTest {

    @Test
    public void downloadsRunOnAnyNetworkWithInternetAccessValidatedOrNot() {
        // Media3's own requirement also wants Android's validation, which fails on some networks with internet
        assertTrue(App.downloadsMayRun(true, false));
        // offline, or a captive portal before its login: downloads wait instead of failing
        assertFalse(App.downloadsMayRun(false, false));
        assertFalse(App.downloadsMayRun(true, true));
    }
}
