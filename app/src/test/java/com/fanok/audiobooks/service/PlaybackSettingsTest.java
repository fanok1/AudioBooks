package com.fanok.audiobooks.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import androidx.media3.exoplayer.DefaultLoadControl;
import org.junit.Test;

public class PlaybackSettingsTest {

    private static final float DELTA = 0.0001f;

    @Test
    public void clampSpeed_rejectsZeroNegativeAndTooSlow() {
        assertEquals(0.25f, PlaybackSettings.clampSpeed(0f), DELTA);
        assertEquals(0.25f, PlaybackSettings.clampSpeed(-1f), DELTA);
        assertEquals(0.25f, PlaybackSettings.clampSpeed(0.1f), DELTA);
        assertEquals(0.25f, PlaybackSettings.clampSpeed(Float.NEGATIVE_INFINITY), DELTA);
    }

    @Test
    public void clampSpeed_keepsValidAndCapsTooFast() {
        assertEquals(1f, PlaybackSettings.clampSpeed(1f), DELTA);
        assertEquals(1.5f, PlaybackSettings.clampSpeed(1.5f), DELTA);
        assertEquals(0.25f, PlaybackSettings.clampSpeed(0.25f), DELTA);
        assertEquals(4f, PlaybackSettings.clampSpeed(4f), DELTA);
        assertEquals(4f, PlaybackSettings.clampSpeed(10f), DELTA);
        assertEquals(4f, PlaybackSettings.clampSpeed(Float.POSITIVE_INFINITY), DELTA);
    }

    @Test
    public void clampSpeed_nanFallsBackToNormalSpeed() {
        assertEquals(1f, PlaybackSettings.clampSpeed(Float.NaN), DELTA);
    }

    @Test
    public void bufferDurations_normalValueIsUsedAsMinAndMax() {
        PlaybackSettings.BufferDurations d = PlaybackSettings.bufferDurations("60");
        assertEquals(60_000, d.minBufferMs);
        assertEquals(60_000, d.maxBufferMs);
        assertBuildsRealLoadControl(d);
    }

    @Test
    public void bufferDurations_tooSmallValuesAreRaisedToThePlaybackThresholds() {
        int floor = Math.max(DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS,
                DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS);
        for (String value : new String[]{"1", "0", "00", "000", "-5"}) {
            PlaybackSettings.BufferDurations d = PlaybackSettings.bufferDurations(value);
            assertEquals(value, floor, d.minBufferMs);
            assertBuildsRealLoadControl(d);
        }
    }

    @Test
    public void bufferDurations_nonNumericAndMissingFallBackToDefault() {
        for (String value : new String[]{"abc", "", " ", "1.5", "12s", null}) {
            PlaybackSettings.BufferDurations d = PlaybackSettings.bufferDurations(value);
            assertEquals(String.valueOf(value), PlaybackSettings.DEFAULT_BUFFER_SECONDS * 1000, d.minBufferMs);
            assertBuildsRealLoadControl(d);
        }
    }

    @Test
    public void bufferDurations_hugeValuesAreCappedOrDefaulted() {
        // fits into a long: capped at the largest value the settings screen can produce
        PlaybackSettings.BufferDurations capped = PlaybackSettings.bufferDurations("999999999999");
        assertEquals(PlaybackSettings.MAX_BUFFER_SECONDS * 1000, capped.minBufferMs);
        assertBuildsRealLoadControl(capped);
        // overflows a long: not a number we can use, so the default applies
        PlaybackSettings.BufferDurations overflow =
                PlaybackSettings.bufferDurations("99999999999999999999999");
        assertEquals(PlaybackSettings.DEFAULT_BUFFER_SECONDS * 1000, overflow.minBufferMs);
        assertBuildsRealLoadControl(overflow);
    }

    /** The invariants DefaultLoadControl enforces, checked against the real builder. */
    private static void assertBuildsRealLoadControl(PlaybackSettings.BufferDurations d) {
        assertTrue(d.bufferForPlaybackMs >= 0);
        assertTrue(d.bufferForPlaybackAfterRebufferMs >= 0);
        assertTrue(d.bufferForPlaybackMs <= d.minBufferMs);
        assertTrue(d.bufferForPlaybackAfterRebufferMs <= d.minBufferMs);
        assertTrue(d.minBufferMs <= d.maxBufferMs);
        new DefaultLoadControl.Builder()
                .setBufferDurationsMs(d.minBufferMs, d.maxBufferMs, d.bufferForPlaybackMs,
                        d.bufferForPlaybackAfterRebufferMs)
                .setBackBuffer(PlaybackSettings.BACK_BUFFER_MS, true)
                .build();
    }
}
