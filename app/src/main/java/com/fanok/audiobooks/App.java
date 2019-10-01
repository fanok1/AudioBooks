package com.fanok.audiobooks;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.preference.PreferenceManager;

public class App extends Application {

    @Override
    protected void attachBaseContext(Context base) {
        SharedPreferences pref = PreferenceManager
                .getDefaultSharedPreferences(base);

        String lang = pref.getString("pref_lang", "ru");
        super.attachBaseContext(LocaleManager.onAttach(base, lang));
    }

    @Override
    public void onCreate() {
        super.onCreate();
        SharedPreferences pref = PreferenceManager
                .getDefaultSharedPreferences(this);

        String themeName = pref.getString("pref_theme", getString(R.string.theme_dark_value));
        if (themeName.equals(getString(R.string.theme_dark_value))) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        } else if (themeName.equals(getString(R.string.theme_light_value))) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        }
    }


}
