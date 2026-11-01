package com.fanok.audiobooks.android_equalizer;

import android.media.audiofx.Equalizer;
import android.media.audiofx.PresetReverb;
import androidx.annotation.NonNull;
import java.util.Arrays;

public class Settings {
    public static boolean isEqualizerEnabled = true;
    public static boolean isEqualizerReloaded = true;
    /** Band levels in millibels; sized to the device's bands on first use, never assumed to be five. */
    public static int[] seekbarpos = new int[0];
    public static int presetPos;
    public static short reverbPreset = -1, bassStrength = -1;
    /** Left/right balance, -100 (left) to 100 (right), 0 centre; played regardless of {@link #isEqualizerEnabled}. */
    public static int balance;
    public static EqualizerModel equalizerModel;
    public static double ratio = 1.0;
    public static boolean isEditing = false;

    /** The shared level array grown to {@code bands} entries (extra bands start flat), kept in the model too. */
    public static int[] bandLevels(int bands) {
        if (seekbarpos == null || seekbarpos.length < bands) {
            seekbarpos = Arrays.copyOf(seekbarpos == null ? new int[0] : seekbarpos, bands);
        }
        if (equalizerModel != null) {
            equalizerModel.setSeekbarpos(seekbarpos);
        }
        return seekbarpos;
    }

    /**
     * Restores the saved sound on an equalizer exactly as the dialog shows it: {@link #presetPos} is the spinner
     * position, 0 being "custom" (the band levels, clamped to the device's range), n the device preset n - 1.
     */
    public static void applyTo(@NonNull Equalizer equalizer) {
        short bands = equalizer.getNumberOfBands();
        if (presetPos > 0 && presetPos <= equalizer.getNumberOfPresets()) {
            equalizer.usePreset((short) (presetPos - 1));
            return;
        }
        presetPos = 0;
        int[] levels = bandLevels(bands);
        short min = equalizer.getBandLevelRange()[0];
        short max = equalizer.getBandLevelRange()[1];
        for (short bandIdx = 0; bandIdx < bands; bandIdx++) {
            equalizer.setBandLevel(bandIdx, (short) Math.max(min, Math.min(max, levels[bandIdx])));
        }
    }

    /** Steps of the bass knob above 0, "off". */
    public static final int BASS_STEPS = 19;

    /** The bass knob's step of a strength (0..1000); a strength below the first step is "off". */
    public static int bassStep(short strength) {
        return Math.max(0, Math.min(BASS_STEPS, strength * BASS_STEPS / 1000));
    }

    /** The strength of a bass knob step, the smallest that reads back as that step. */
    public static short bassStrength(int step) {
        return (short) Math.ceil(step * 1000.0 / BASS_STEPS);
    }

    /** Bass boost runs only above "off": at the knob's minimum the effect is disabled, not run at a low strength. */
    public static boolean isBassOn(short strength) {
        return isEqualizerEnabled && bassStep(strength) > 0;
    }

    /** An insert reverb runs only with a room chosen: even at PRESET_NONE it takes over the session's volume. */
    public static boolean isReverbOn(short preset) {
        return isEqualizerEnabled && preset != PresetReverb.PRESET_NONE;
    }
}
