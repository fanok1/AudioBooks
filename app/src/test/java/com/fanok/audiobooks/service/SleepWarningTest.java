package com.fanok.audiobooks.service;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class SleepWarningTest {

    @Test
    public void aBurstIsDueAtThirtyTwentyAndTenSecondsLeft() {
        assertEquals(0, SleepWarning.burstsDue(30_001));
        assertEquals(1, SleepWarning.burstsDue(30_000));
        assertEquals(1, SleepWarning.burstsDue(20_001));
        assertEquals(2, SleepWarning.burstsDue(20_000));
        assertEquals(2, SleepWarning.burstsDue(10_001));
        assertEquals(3, SleepWarning.burstsDue(10_000));
        assertEquals(3, SleepWarning.burstsDue(0));
    }

    @Test
    public void theFirstBurstStartsTheWarning() {
        assertEquals(SleepWarning.WARNING_MS, SleepWarning.BURSTS_AT_MS[0]);
    }
}
