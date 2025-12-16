package com.fanok.audiobooks;

import static com.fanok.audiobooks.Consts.PROXY_PASSWORD;
import static com.fanok.audiobooks.Consts.PROXY_USERNAME;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import androidx.media3.database.StandaloneDatabaseProvider;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.datasource.cache.Cache;
import androidx.media3.datasource.cache.CacheDataSource;
import androidx.media3.datasource.cache.NoOpCacheEvictor;
import androidx.media3.datasource.cache.SimpleCache;
import androidx.media3.exoplayer.offline.DownloadManager;
import androidx.preference.PreferenceManager;

import com.fanok.audiobooks.pojo.StorageUtil;
import com.fanok.audiobooks.presenter.BookPresenter;
import com.fanok.audiobooks.util.DownloadUtil;


import java.io.File;
import java.net.Authenticator;
import java.net.PasswordAuthentication;
import java.util.concurrent.Executors;

@UnstableApi
public class App extends Application {

    public static boolean useProxy;
    private DownloadManager downloadManager;
    private SimpleCache downloadCache;

    private StandaloneDatabaseProvider databaseProvider;

    private static App instance;

    @Override
    protected void attachBaseContext(Context base) {
        SharedPreferences pref = PreferenceManager
                .getDefaultSharedPreferences(base);

        String lang = pref.getString("pref_lang", "ru");
        super.attachBaseContext(LocaleManager.onAttach(base, lang));
    }

    @UnstableApi
    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;

        // --- ВОЗВРАЩАЕМ ПРОСТОЙ И ПОНЯТНЫЙ КОД ---
        databaseProvider = new StandaloneDatabaseProvider(this);
        File downloadDirectory = new File(getCacheDir(), "downloads");
        downloadCache = new SimpleCache(downloadDirectory, new NoOpCacheEvictor(), databaseProvider);

        DataSource.Factory resolvingDataSourceFactory = DownloadUtil.getResolvingUpstreamFactory(this);

        downloadManager = new DownloadManager(
                this,
                databaseProvider,
                downloadCache,
                resolvingDataSourceFactory, // <-- Используем вашу фабрику для сети
                Executors.newFixedThreadPool(3)
        );
        downloadManager.setMaxParallelDownloads(1);


        // User's original code
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

        Consts.setBazaKnigCookies(pref.getString("cookes_baza_knig", ""));


        BookPresenter.setSpeedWithoutBroadcast(new StorageUtil(getBaseContext()).loadSpeed());

        String source = pref.getString("sorce_books", getString(R.string.abook_value));

        Consts.setSOURCE(this, source);

        useProxy = pref.getBoolean("pref_proxy", false);
        if(useProxy){
            Authenticator authenticator = new Authenticator() {
                public PasswordAuthentication getPasswordAuthentication() {
                    return (new PasswordAuthentication(PROXY_USERNAME,
                            PROXY_PASSWORD.toCharArray()));
                }
            };
            Authenticator.setDefault(authenticator);
        }
    }


    public StandaloneDatabaseProvider getDatabaseProvider() {
        return databaseProvider;
    }

    public static App getInstance() {
        return instance;
    }

    @UnstableApi
    public DownloadManager getDownloadManager() {
        return downloadManager;
    }

    public SimpleCache getDownloadCache() {
        return downloadCache;
    }

}
