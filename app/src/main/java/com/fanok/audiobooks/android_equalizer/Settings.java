package com.fanok.audiobooks.android_equalizer;

import java.util.Arrays;

public class Settings {
    public static boolean isEqualizerEnabled = true;
    public static boolean isEqualizerReloaded = true;
    /** Band levels in millibels; sized to the device's bands on first use, never assumed to be five. */
    public static int[] seekbarpos = new int[0];
    public static int presetPos;
    public static short reverbPreset = -1, bassStrength = -1;
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
}
