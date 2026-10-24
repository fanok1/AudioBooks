package com.fanok.audiobooks;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import androidx.annotation.NonNull;
import androidx.annotation.StyleRes;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.preference.PreferenceManager;

/**
 * The app's theme preference ({@code pref_theme}) in one place: night mode goes through
 * {@link AppCompatDelegate#setDefaultNightMode} (never by editing a {@code Configuration}), and an activity's
 * style is chosen once, before {@code setContentView}.
 */
public final class AppThemes {

    private AppThemes() {
    }

    /** The stored theme value (dark by default). */
    @NonNull
    public static String current(@NonNull Context context) {
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(context);
        return pref.getString("pref_theme", context.getString(R.string.theme_dark_value));
    }

    /** Applies the night mode the preference implies; called at startup and when the preference changes. */
    public static void applyNightMode(@NonNull Context context) {
        String theme = current(context);
        boolean night = !theme.equals(context.getString(R.string.theme_light_value));
        AppCompatDelegate.setDefaultNightMode(night ? AppCompatDelegate.MODE_NIGHT_YES
                : AppCompatDelegate.MODE_NIGHT_NO);
    }

    /** The one of the three styles that matches the preference. */
    @StyleRes
    public static int style(@NonNull Context context, @StyleRes int dark, @StyleRes int light, @StyleRes int black) {
        String theme = current(context);
        if (theme.equals(context.getString(R.string.theme_light_value))) {
            return light;
        }
        if (theme.equals(context.getString(R.string.theme_black_value))) {
            return black;
        }
        return dark;
    }

    /** Sets the matching style on {@code activity}; call before {@code super.onCreate}/{@code setContentView}. */
    public static void apply(@NonNull Activity activity, @StyleRes int dark, @StyleRes int light, @StyleRes int black) {
        activity.setTheme(style(activity, dark, light, black));
    }
}
