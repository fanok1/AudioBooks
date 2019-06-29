package com.fanok.audiobooks;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.support.annotation.NonNull;
import android.support.v4.graphics.drawable.DrawableCompat;
import android.util.Log;
import android.util.TypedValue;
import android.view.MenuItem;

import java.util.regex.Pattern;

public class Consts {
    private static final String TAG = "Consts";
    public static final Pattern REGEXP_URL = Pattern.compile(
            "^https?://.+\\..+$");
    public static final Pattern REGEXP_URL_PHOTO = Pattern.compile(
            "^https?://.+\\.((jpg)|(png)|(jpeg))$");
    public static final Pattern REGEXP_RETING = Pattern.compile("^([+-]\\d+)|0$");

    public static final int MODEL_BOOKS = 0;
    public static final int MODEL_GENRE = 1;
    public static final int MODEL_AUTOR = 2;
    public static final int MODEL_ARTIST = 3;

    public static final int TABLE_FAVORITE = 1;
    public static final int TABLE_HISTORY = 2;


    public static final String DBName = "audioBooksDB";


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
                Consts.getAttributeColor(context, R.attr.colorPrimaryText));
        item.setIcon(drawable);
    }


}
