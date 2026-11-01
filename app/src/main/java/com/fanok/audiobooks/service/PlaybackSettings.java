package com.fanok.audiobooks.service;

import static androidx.media3.exoplayer.DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS;
import static androidx.media3.exoplayer.DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS;

import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.media3.common.util.UnstableApi;

/**
 * Pure validation of user-controlled playback settings, kept free of Android types so it is unit-testable.
 */
@OptIn(markerClass = UnstableApi.class)
public final class PlaybackSettings {

    public static final float MIN_SPEED = 0.25f;
    public static final float MAX_SPEED = 4.0f;

    public static final int DEFAULT_BUFFER_SECONDS = 120;
    /** The settings screen accepts at most three digits. */
    public static final int MAX_BUFFER_SECONDS = 999;
    public static final int BACK_BUFFER_MS = 30_000;

    private PlaybackSettings() {
    }

    /** Playback speed accepted by Media3 and sensible for speech: [0.25, 4.0]; NaN becomes 1. */
    public static float clampSpeed(float speed) {
        if (Float.isNaN(speed)) {
            return 1f;
        }
        return Math.max(MIN_SPEED, Math.min(MAX_SPEED, speed));
    }

    /** Arguments of {@code DefaultLoadControl.Builder#setBufferDurationsMs}, satisfying its preconditions. */
    public static final class BufferDurations {
        public final int minBufferMs;
        public final int maxBufferMs;
        public final int bufferForPlaybackMs;
        public final int bufferForPlaybackAfterRebufferMs;

        BufferDurations(int minBufferMs, int maxBufferMs, int bufferForPlaybackMs,
                int bufferForPlaybackAfterRebufferMs) {
            this.minBufferMs = minBufferMs;
            this.maxBufferMs = maxBufferMs;
            this.bufferForPlaybackMs = bufferForPlaybackMs;
            this.bufferForPlaybackAfterRebufferMs = bufferForPlaybackAfterRebufferMs;
        }
    }

    /**
     * @param bufferSeconds the "bufferSize" preference (seconds). Non-numeric input falls back to the default;
     *                      numeric input is clamped so that the playback thresholds never exceed the minimum
     *                      buffer and the minimum never exceeds the maximum.
     */
    public static BufferDurations bufferDurations(@Nullable String bufferSeconds) {
        long seconds = DEFAULT_BUFFER_SECONDS;
        if (bufferSeconds != null) {
            try {
                seconds = Long.parseLong(bufferSeconds.trim());
            } catch (NumberFormatException ignored) {
                // keep the default
            }
        }
        int forPlayback = DEFAULT_BUFFER_FOR_PLAYBACK_MS;
        int afterRebuffer = DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS;
        int floorMs = Math.max(forPlayback, afterRebuffer);
        int bufferMs = (int) Math.max(floorMs, Math.min(seconds, MAX_BUFFER_SECONDS) * 1000L);
        return new BufferDurations(bufferMs, bufferMs, forPlayback, afterRebuffer);
    }
}
