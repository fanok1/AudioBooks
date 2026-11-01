package com.fanok.audiobooks.service;

/**
 * When the sleep timer's warning sounds. The last {@link #WARNING_MS} before a timed stop beep in bursts, one at
 * each of {@link #BURSTS_AT_MS} remaining, so someone half asleep hears more than one chance to shake the phone.
 */
final class SleepWarning {

    static final long WARNING_MS = 30_000;

    /** Remaining times (ms) that start a burst, latest first. */
    static final long[] BURSTS_AT_MS = {30_000, 20_000, 10_000};

    private SleepWarning() {
    }

    /** How many bursts are due by {@code remainingMs}: one more each time a burst time is reached. */
    static int burstsDue(long remainingMs) {
        int due = 0;
        for (long at : BURSTS_AT_MS) {
            if (remainingMs <= at) {
                due++;
            }
        }
        return due;
    }
}
