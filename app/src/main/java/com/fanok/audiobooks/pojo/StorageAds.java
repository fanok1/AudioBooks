package com.fanok.audiobooks.pojo;

import android.content.Context;
import android.content.SharedPreferences;

public class StorageAds {
    private final String STORAGE = "ADS";
    private SharedPreferences preferences;
    private Context context;

    public StorageAds(Context context) {
        this.context = context;
    }

    public void storeDisableAds(boolean b) {
        preferences = context.getSharedPreferences(STORAGE, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean("disableAds", b);
        editor.apply();
    }

    public boolean idDisableAds() {
        preferences = context.getSharedPreferences(STORAGE, Context.MODE_PRIVATE);
        return preferences.getBoolean("disableAds", false);
    }

    public void clearStorege() {
        preferences = context.getSharedPreferences(STORAGE, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();
        editor.clear();
        editor.apply();
    }
}
