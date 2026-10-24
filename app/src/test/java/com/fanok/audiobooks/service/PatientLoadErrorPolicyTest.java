package com.fanok.audiobooks.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.media3.common.C;
import org.junit.Test;

public class PatientLoadErrorPolicyTest {

    @Test
    public void refusedAddressesFailAtOnce() {
        for (int status : new int[]{400, 401, 403, 404, 410, 416, 451, 499}) {
            assertTrue("HTTP " + status, PatientLoadErrorPolicy.isPermanentHttpStatus(status));
        }
    }

    @Test
    public void timeoutsThrottlingAndServerErrorsAreRetried() {
        for (int status : new int[]{408, 425, 429, 500, 502, 503, 504, 200, 206, 302, 399, -1}) {
            assertFalse("HTTP " + status, PatientLoadErrorPolicy.isPermanentHttpStatus(status));
        }
    }

    @Test
    public void onlyNetworkErrorsWhileListeningAreRetriedPastMediasUsualCount() {
        int usual = PatientLoadErrorPolicy.DEFAULT_MIN_LOADABLE_RETRY_COUNT;
        assertTrue(PatientLoadErrorPolicy.isPatient(true, true, 1000));
        // a missing local file, or any error while paused, stops after Media3's usual retries
        assertTrue(PatientLoadErrorPolicy.isPatient(false, true, usual));
        assertFalse(PatientLoadErrorPolicy.isPatient(false, true, usual + 1));
        assertTrue(PatientLoadErrorPolicy.isPatient(true, false, usual));
        assertFalse(PatientLoadErrorPolicy.isPatient(true, false, usual + 1));
    }

    @Test
    public void noRetryCountMakesALoadErrorFatal() {
        PatientLoadErrorPolicy policy = new PatientLoadErrorPolicy(() -> true);
        for (int dataType : new int[]{C.DATA_TYPE_MEDIA, C.DATA_TYPE_MANIFEST,
                C.DATA_TYPE_MEDIA_PROGRESSIVE_LIVE, C.DATA_TYPE_UNKNOWN}) {
            assertEquals(Integer.MAX_VALUE, policy.getMinimumLoadableRetryCount(dataType));
        }
    }
}
