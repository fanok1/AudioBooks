package com.fanok.audiobooks;


import android.content.Context;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.util.TypedValue;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.core.graphics.drawable.DrawableCompat;

import java.util.regex.Pattern;

public class Consts {
    private static final String TAG = "Consts";
    public static final Pattern REGEXP_URL = Pattern.compile(
            "^https?://.+\\..+$");
    public static final Pattern REGEXP_URL_PHOTO = Pattern.compile(
            "^https?://.+\\.((jpg)|(png)|(jpeg)).*$");

    public static final Pattern REGEXP_URL_MP3 = Pattern.compile(
            "^https?://.+\\.mp3.*$");

    public static final Pattern REGEXP_SIKRET_KEY = Pattern.compile(
            "\"security_ls_key\":\"(\\w+)\"");

    public static final Pattern REGEXP_EMAIL = Pattern.compile(
            "^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,6}$", Pattern.CASE_INSENSITIVE);

    public static final int MODEL_BOOKS = 0;
    public static final int MODEL_GENRE = 1;
    public static final int MODEL_AUTOR = 2;
    public static final int MODEL_ARTIST = 3;

    public static final int TABLE_FAVORITE = 1;
    public static final int TABLE_HISTORY = 2;

    public static final int REQEST_CODE_SEARCH = 157;

    public static final String ARG_MODEL = "ARG_MODEL";

    public static final String DBName = "audioBooksDB";

    public static final double COLLAPS_BUTTON_VISIBLE = 0.7;
    public static final double COLLAPS_BUTTON_VISIBLE_STEP = (1 - Consts.COLLAPS_BUTTON_VISIBLE);


    public static final int FRAGMENT_AUDIOBOOK = 0;
    public static final int FRAGMENT_GENRE = 1;
    public static final int FRAGMENT_AUTOR = 2;
    public static final int FRAGMENT_ARTIST = 3;
    public static final int FRAGMENT_FAVORITE = 4;
    public static final int FRAGMENT_HISTORY = 5;

    public static final int IMPORT_SITE_KNIGA_V_UHE = 0;

    public static final String APP_PREFERENCES = "audiobooks_settings";
    public static final String APP_FRAGMENT = "start_fragment";

    public static final int ADDS_SHOWING_COUNT = 3;
    public static int adsCount = 0;

    public static final String MY_EMAIL = "fanok.inc@gmail.com";


    public static int getAttributeColor(Context context, int attributeId) {
        TypedValue typedValue = new TypedValue();
        context.getTheme().resolveAttribute(attributeId, typedValue, true);
        int colorRes = typedValue.resourceId;
        int color = -1;
        try {
            color = context.getResources().getColor(colorRes);
        } catch (Resources.NotFoundException e) {
            Log.w(TAG, "Not found color resource by id: " + colorRes);
        }
        return color;
    }

    public static void setColorPrimeriTextInIconItemMenu(MenuItem item, @NonNull Context context) {
        Drawable drawable = item.getIcon();
        drawable = DrawableCompat.wrap(drawable);
        DrawableCompat.setTint(drawable,
                Consts.getAttributeColor(context, R.attr.colorItemMenu));
        item.setIcon(drawable);
    }

    public static int indexOfByNumber(@NonNull String str, char c, int index) {
        int count = 0;
        for (int i = 0; i < str.length(); i++) {
            if (str.charAt(i) == c) {
                count++;
                if (count == index) return i;
            }
        }
        return -1;
    }
}
