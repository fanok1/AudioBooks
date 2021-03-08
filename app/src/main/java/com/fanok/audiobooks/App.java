package com.fanok.audiobooks;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.preference.PreferenceManager;
import com.fanok.audiobooks.pojo.StorageUtil;
import com.fanok.audiobooks.presenter.BookPresenter;
import com.google.android.gms.ads.MobileAds;

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
        } else if (themeName.equals(getString(R.string.theme_black_value))) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        }
        MobileAds.initialize(this, "ca-app-pub-3595775191373219~2371571769");

        BookPresenter.setSpeedWithoutBroadcast(new StorageUtil(getBaseContext()).loadSpeed());

        String source = pref.getString("sorce_books", getString(R.string.kniga_v_uhe_value));
        Consts.setSOURCE(this, source);


        //Billing.initBilding(getBaseContext());
    }


}
