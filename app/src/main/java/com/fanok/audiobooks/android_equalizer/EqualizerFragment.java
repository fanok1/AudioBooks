package com.fanok.audiobooks.android_equalizer;

import android.graphics.Color;

/**
 * Holder of the constants the service, the dialog and {@link AnalogController} share. The fragment UI that once
 * lived here was never instantiated and is gone; {@link DialogEqualizerFragment} is the equalizer screen.
 */
public final class EqualizerFragment {

    public static final String Broadcast_EqualizerEnabled = "equalizer_enabled";
    static int themeColor = Color.parseColor("#B24242");

    private EqualizerFragment() {
    }
}
