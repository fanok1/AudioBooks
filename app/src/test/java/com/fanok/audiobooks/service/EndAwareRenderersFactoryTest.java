package com.fanok.audiobooks.service;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.media3.common.C;
import org.junit.Test;

public class EndAwareRenderersFactoryTest {

    private static final long HOUR_MS = 3_600_000;

    @Test
    public void audioThatEndsShortOfTheDurationEndedEarly() {
        assertTrue(EndAwareRenderersFactory.endedEarly(true, HOUR_MS - 600_000, HOUR_MS));
        assertTrue(EndAwareRenderersFactory.endedEarly(true, HOUR_MS - EndAwareRenderersFactory.EARLY_END_MS,
                HOUR_MS));
        //a seek past the audio ends it at once, wherever the clock is
        assertTrue(EndAwareRenderersFactory.endedEarly(true, 0, HOUR_MS));
    }

    @Test
    public void theUsualEndIsLeftToMedia3() {
        assertFalse(EndAwareRenderersFactory.endedEarly(true, HOUR_MS, HOUR_MS));
        assertFalse(EndAwareRenderersFactory.endedEarly(true, HOUR_MS - 300, HOUR_MS));
        assertFalse(EndAwareRenderersFactory.endedEarly(true, HOUR_MS + 500, HOUR_MS));
        //without a duration Media3 ends the chapter itself when the audio ends
        assertFalse(EndAwareRenderersFactory.endedEarly(true, 0, C.TIME_UNSET));
    }

    @Test
    public void audioStillPlayingNeverEndedEarly() {
        assertFalse(EndAwareRenderersFactory.endedEarly(false, 0, HOUR_MS));
    }

    @Test
    public void theFirstEarlyEndOfAChapterIsRecovered() {
        assertTrue(EndAwareRenderersFactory.shouldRecover(C.TIME_UNSET, HOUR_MS - 600_000));
        assertTrue(EndAwareRenderersFactory.shouldRecover(C.TIME_UNSET, 0));
    }

    @Test
    public void anEarlyEndAgainAtAboutTheRecoveredSpotEndsTheChapter() {
        long spot = HOUR_MS - 600_000;
        //the clock is read twice a second and the chapter starts again a little before the spot
        assertFalse(EndAwareRenderersFactory.shouldRecover(spot, spot + 500));
        assertFalse(EndAwareRenderersFactory.shouldRecover(spot, spot - 1_500));
        assertFalse(EndAwareRenderersFactory.shouldRecover(spot, spot + EndAwareRenderersFactory.SAME_SPOT_MS));
    }

    @Test
    public void anEarlyEndElsewhereIsRecoveredToo() {
        long spot = HOUR_MS - 600_000;
        assertTrue(EndAwareRenderersFactory.shouldRecover(spot, spot + 300_000));
        assertTrue(EndAwareRenderersFactory.shouldRecover(spot, spot - 300_000));
    }
}
