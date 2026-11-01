package com.fanok.audiobooks.service;

import android.content.Context;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;
import com.fanok.audiobooks.R;

/**
 * The step of a tap on rewind/forward, the "seek_step" setting (10, 20, 30 or 45 s; 30 by default), with the icons
 * that show it: the player screen, the notification and the headset and car keys all seek by it.
 */
public final class SeekStep {

    public static final String PREF = "seek_step";
    private static final int[] SECONDS = {10, 20, 30, 45};
    private static final int DEFAULT = 2;
    private static final int[] REWIND = {R.drawable.ic_rewind_10, R.drawable.ic_rewind_20, R.drawable.ic_rewind_30,
            R.drawable.ic_rewind_45};
    private static final int[] FORWARD = {R.drawable.ic_fast_forward_10, R.drawable.ic_fast_forward_20,
            R.drawable.ic_fast_forward_30, R.drawable.ic_fast_forward_45};
    private static final int[] NOTIFICATION_REWIND = {R.drawable.ic_notification_rewind_10,
            R.drawable.ic_notification_rewind_20, R.drawable.ic_notification_rewind_30,
            R.drawable.ic_notification_rewind_45};
    private static final int[] NOTIFICATION_FORWARD = {R.drawable.ic_notification_fast_forward_10,
            R.drawable.ic_notification_fast_forward_20, R.drawable.ic_notification_fast_forward_30,
            R.drawable.ic_notification_fast_forward_45};

    private SeekStep() {
    }

    /** The position of the setting in {@link #SECONDS}; anything unknown is the default. */
    private static int index(@NonNull Context context) {
        String value = PreferenceManager.getDefaultSharedPreferences(context).getString(PREF, null);
        for (int i = 0; i < SECONDS.length; i++) {
            if (String.valueOf(SECONDS[i]).equals(value)) {
                return i;
            }
        }
        return DEFAULT;
    }

    public static int seconds(@NonNull Context context) {
        return SECONDS[index(context)];
    }

    public static long millis(@NonNull Context context) {
        return seconds(context) * 1000L;
    }

    @DrawableRes
    public static int rewindIcon(@NonNull Context context) {
        return REWIND[index(context)];
    }

    @DrawableRes
    public static int forwardIcon(@NonNull Context context) {
        return FORWARD[index(context)];
    }

    @DrawableRes
    public static int notificationRewindIcon(@NonNull Context context) {
        return NOTIFICATION_REWIND[index(context)];
    }

    @DrawableRes
    public static int notificationForwardIcon(@NonNull Context context) {
        return NOTIFICATION_FORWARD[index(context)];
    }

    @NonNull
    public static String rewindLabel(@NonNull Context context) {
        return context.getString(R.string.seek_back_n, seconds(context));
    }

    @NonNull
    public static String forwardLabel(@NonNull Context context) {
        return context.getString(R.string.seek_forward_n, seconds(context));
    }
}
