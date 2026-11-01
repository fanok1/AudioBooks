package com.fanok.audiobooks.android_equalizer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SettingsTest {

    @Test
    public void everyBassStepReadsBackAndOnlyZeroIsOff() {
        for (int step = 0; step <= Settings.BASS_STEPS; step++) {
            assertEquals(step, Settings.bassStep(Settings.bassStrength(step)));
        }
        assertEquals(1000, Settings.bassStrength(Settings.BASS_STEPS));
        boolean enabled = Settings.isEqualizerEnabled;
        try {
            Settings.isEqualizerEnabled = true;
            assertFalse(Settings.isBassOn(Settings.bassStrength(0)));
            assertTrue(Settings.isBassOn(Settings.bassStrength(1)));
            // the old knob's minimum, 1000/19 * 1, is the new knob's "off"
            assertFalse(Settings.isBassOn((short) 52));
            assertFalse(Settings.isBassOn((short) -1));
            Settings.isEqualizerEnabled = false;
            assertFalse(Settings.isBassOn((short) 1000));
        } finally {
            Settings.isEqualizerEnabled = enabled;
        }
    }
}
