package com.fanok.audiobooks;

import static com.fanok.audiobooks.Consts.PROXY_PASSWORD;
import static com.fanok.audiobooks.Consts.PROXY_USERNAME;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.database.StandaloneDatabaseProvider;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.cache.NoOpCacheEvictor;
import androidx.media3.datasource.cache.SimpleCache;
import androidx.media3.exoplayer.offline.DownloadManager;
import androidx.preference.PreferenceManager;

import com.fanok.audiobooks.model.AudioDBModel;
import com.fanok.audiobooks.model.AudioListDBModel;
import com.fanok.audiobooks.model.BooksDBModel;
import com.fanok.audiobooks.model.FirebaseSyncModel;
import com.fanok.audiobooks.pojo.StorageUtil;
import com.fanok.audiobooks.presenter.BookPresenter;
import com.fanok.audiobooks.util.DownloadUtil;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Logger;


import java.io.File;
import java.net.Authenticator;
import java.net.PasswordAuthentication;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

@UnstableApi
public class App extends Application {

    public static boolean useProxy;
    private DownloadManager downloadManager;
    private SimpleCache downloadCache;

    private StandaloneDatabaseProvider databaseProvider;

    private static App instance;
    private FirebaseSyncModel mFirebaseSyncModel;
    private FirebaseAuth mAuth;
    private FirebaseAuth.AuthStateListener mAuthListener;
    private final AtomicBoolean isFirstAuthCheck = new AtomicBoolean(true);

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

        // Включаем подробные логи Firebase Database для диагностики
        FirebaseDatabase.getInstance().setLogLevel(Logger.Level.DEBUG);

        mFirebaseSyncModel = new FirebaseSyncModel(this);
        mAuth = FirebaseAuth.getInstance();
        mAuthListener = firebaseAuth -> {
            FirebaseUser user = firebaseAuth.getCurrentUser();
            if (user != null) {
                // User is signed in
                Log.d("App", "onAuthStateChanged:signed_in:" + user.getUid());
                mFirebaseSyncModel.markLocalDataAsDirty();
                mFirebaseSyncModel.uploadLocalChanges();
                mFirebaseSyncModel.startListening();
                isFirstAuthCheck.set(false);
            } else {
                // This is the initial check on app startup and the user is not logged in. Do nothing.
                if(isFirstAuthCheck.getAndSet(false)){
                    return;
                }

                // User is signed out
                Log.d("App", "onAuthStateChanged:signed_out");
                mFirebaseSyncModel.stopListening();
                // Clear user data
                ExecutorService executor = Executors.newSingleThreadExecutor();
                executor.execute(() -> {
                    new BooksDBModel(App.this).clearUserData();
                    new AudioDBModel(App.this).clearPhysical();
                    new AudioListDBModel(App.this).clearPhysical();
                });
                executor.shutdown();
            }
        };
        mAuth.addAuthStateListener(mAuthListener);

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

    @Override
    public void onTerminate() {
        super.onTerminate();
        if (mAuthListener != null) {
            mAuth.removeAuthStateListener(mAuthListener);
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
