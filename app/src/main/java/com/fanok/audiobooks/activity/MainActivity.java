package com.fanok.audiobooks.activity;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.util.Log;
import android.util.TypedValue;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.preference.PreferenceManager;

import com.arellomobile.mvp.MvpAppCompatActivity;
import com.arellomobile.mvp.presenter.InjectPresenter;
import com.fanok.audiobooks.FragmentTagSteck;
import com.fanok.audiobooks.LocaleManager;
import com.fanok.audiobooks.R;
import com.fanok.audiobooks.interface_pacatge.main.MainView;
import com.fanok.audiobooks.pojo.StorageAds;
import com.fanok.audiobooks.presenter.MainPresenter;
import com.fanok.audiobooks.service.MediaPlayerService;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.MobileAds;
import com.google.android.material.navigation.NavigationView;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Locale;
import java.util.Objects;

import butterknife.BindView;
import butterknife.ButterKnife;


public class MainActivity extends MvpAppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener, MainView {
    private static final String TAG = "MainActivity";
    public static final String Broadcast_DISABLE_ADS = "DISABLE_ADS";


    private static final String EXSTRA_FRAGMENT = "startFragment";
    private static final String EXSTRA_URL = "url";
    private static boolean closeApp = false;
    @BindView(R.id.adView)
    AdView mAdView;
    private boolean isSavedInstanceState = false;

    public static boolean isCloseApp() {
        return closeApp;
    }

    private ArrayList<TextView> mTextViewArrayList;

    @InjectPresenter
    MainPresenter mPresenter;

    private ArrayList<FragmentTagSteck> fragmentsTag;
    private NavigationView navigationView;
    private AlertDialog.Builder alert;
    private boolean firstStart = true;

    private SharedPreferences preferences;
    private BroadcastReceiver disebledAds = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!StorageAds.idDisableAds()) {
                MobileAds.initialize(context, initializationStatus -> {
                });
                AdRequest adRequest = new AdRequest.Builder().build();
                mAdView.loadAd(adRequest);
                mAdView.setVisibility(View.VISIBLE);
            } else {
                mAdView.setVisibility(View.GONE);
            }
        }
    };

    public static void setCloseApp(boolean closeApp) {
        MainActivity.closeApp = closeApp;
    }

    public NavigationView getNavigationView() {
        return navigationView;
    }

    public static void startMainActivity(@NonNull Context context, int fragment, String url) {
        if (url == null || url.isEmpty()) {
            startMainActivity(context, fragment);
            return;
        }

        Intent intent = new Intent(context, MainActivity.class);
        intent.putExtra(EXSTRA_FRAGMENT, fragment);
        intent.putExtra(EXSTRA_URL, url);
        context.startActivity(intent);
    }

    public static void startMainActivity(@NonNull Context context, int fragment) {
        Intent intent = new Intent(context, MainActivity.class);
        intent.putExtra(EXSTRA_FRAGMENT, fragment);
        context.startActivity(intent);
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(LocaleManager.onAttach(base));
    }

    public ArrayList<TextView> getTextViewArrayList() {
        return mTextViewArrayList;
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (!isSavedInstanceState) {
            if (firstStart) {

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {

                    PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
                    if (pm != null) {
                        if (!pm.isIgnoringBatteryOptimizations("com.fanok.audiobooks")) {
                            alert.show();
                        }
                    }
                }

                if (!preferences.getBoolean("first", false)) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(
                            Objects.requireNonNull(this));
                    builder.setTitle(getString(R.string.privacy))
                            .setMessage(getString(R.string.privacy_message))
                            .setIcon(R.drawable.ic_privacy)
                            .setCancelable(false)
                            .setNegativeButton(getString(R.string.yes),
                                    (dialog, id) -> {
                                        SharedPreferences.Editor editor = preferences.edit();
                                        editor.putBoolean("first", true);
                                        editor.apply();
                                        dialog.cancel();
                                    });
                    AlertDialog alert = builder.create();
                    alert.show();
                }

                Intent intent = getIntent();
                int fragment = intent.getIntExtra(EXSTRA_FRAGMENT, -1);
                String url = intent.getStringExtra(EXSTRA_URL);
                if (url != null && !url.isEmpty() && fragment != -1) {
                    mPresenter.startFragment(fragment, url);
                } else {
                    SharedPreferences pref = PreferenceManager
                            .getDefaultSharedPreferences(this);
                    fragment = Integer.parseInt(pref.getString("pref_start_screen", "0"));
                    mPresenter.startFragment(fragment);
                }
                firstStart = false;
            }
        }
    }

    @Override
    public void onBackPressed() {
        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else {
            if (getSupportFragmentManager().getBackStackEntryCount() > 0) {
                if (fragmentsTag.size() > 0) fragmentsTag.remove(fragmentsTag.size() - 1);
                getSupportFragmentManager().popBackStack();
                while (true) {
                    if (fragmentsTag.size() > 0 && fragmentsTag.get(
                            fragmentsTag.size() - 1).isSkip()) {
                        fragmentsTag.remove(fragmentsTag.size() - 1);
                        getSupportFragmentManager().popBackStack();
                    } else {
                        break;
                    }
                }

            } else {
                super.onBackPressed();
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        closeApp = false;
        Log.d(TAG, "onCreate: called");
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);

        SharedPreferences pref = PreferenceManager
                .getDefaultSharedPreferences(this);

        preferences = getSharedPreferences("FIRST", Context.MODE_PRIVATE);

        String themeName = pref.getString("pref_theme", getString(R.string.theme_dark_value));
        if (themeName.equals(getString(R.string.theme_dark_value))) {
            setTheme(R.style.AppTheme_NoAnimTheme);
        } else if (themeName.equals(getString(R.string.theme_light_value))) {
            setTheme(R.style.LightAppTheme_NoAnimTheme);
        }

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        navigationView = findViewById(R.id.nav_view);
        if (drawer != null && navigationView != null) {
            ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                    this, drawer, toolbar, R.string.navigation_drawer_open,
                    R.string.navigation_drawer_close);
            drawer.addDrawerListener(toggle);
            toggle.syncState();
            navigationView.setNavigationItemSelectedListener(this);
        } else {
            LinearLayout linearLayout = findViewById(R.id.liner_nav_view);
            if (linearLayout != null) {
                mTextViewArrayList = new ArrayList<>();
                final TypedValue outValue = new TypedValue();
                getTheme().resolveAttribute(android.R.attr.selectableItemBackground, outValue,
                        true);
                final TypedValue SelectedValue = new TypedValue();
                getTheme().resolveAttribute(R.attr.mySelectableItemBackground, SelectedValue, true);
                for (int i = 0; i < linearLayout.getChildCount(); i++) {
                    View view = linearLayout.getChildAt(i);
                    if (view instanceof TextView) {
                        mTextViewArrayList.add((TextView) view);
                        view.setOnClickListener(view1 -> {
                            for (TextView textView : mTextViewArrayList) {
                                textView.setBackgroundResource(outValue.resourceId);
                            }
                            view1.setBackgroundResource(SelectedValue.resourceId);
                            mPresenter.onItemSelected(view1.getId());
                        });

                    }
                }
            }
        }


        fragmentsTag = new ArrayList<>();
        isSavedInstanceState = savedInstanceState != null;
        register_disebledAds();
        mAdView.setVisibility(View.GONE);

        alert = new AlertDialog.Builder(this);
        alert.setTitle(R.string.app_name);
        alert.setMessage(R.string.setIgnoredBatteryOptimyze);
        alert.setNegativeButton(R.string.cancel, null);
        alert.setCancelable(true);
        alert.setNeutralButton(R.string.help, (dialogInterface, i) -> {
            Intent intent = new Intent(alert.getContext(), ActivitySendEmail.class);
            intent.putExtra("enebled", false);
            intent.putExtra("message",
                    getString(R.string.message_help_disable_battery_optimisetion));
            intent.putExtra("subject", 0);
            startActivity(intent);
        });
        alert.setPositiveButton("OK",
                (dialogInterface, i) -> mPresenter.openSettingsOptimizeBattery(dialogInterface));

    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        mPresenter.onItemSelected(item.getItemId());
        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        if (drawer != null) {
            drawer.closeDrawer(GravityCompat.START);
        }
        return true;
    }

    @Override
    public void openActivity(@NotNull Intent intent) {
        startActivity(intent);
    }

    @Override
    public void showFragment(@NotNull Fragment fragment, @NonNull String tag) {
        if (fragmentsTag.size() != 0 && tag.equals(
                fragmentsTag.get(fragmentsTag.size() - 1).getTag())
                && !tag.equals("searchebleBooks")) {
            return;
        }
        addFragmentTag(tag);
        FragmentManager fragmentManager = getSupportFragmentManager();
        FragmentTransaction transaction = fragmentManager.beginTransaction();
        transaction.replace(R.id.container, fragment, tag);
        if (fragmentsTag.size() > 1) transaction.addToBackStack(tag);
        transaction.commit();

    }

    private void addFragmentTag(@NonNull String tag) {
        for (int i = 0; i < fragmentsTag.size(); i++) {
            if (fragmentsTag.get(i).getTag().equals(tag)) fragmentsTag.get(i).setSkip(true);
        }
        fragmentsTag.add(new FragmentTagSteck(tag));
    }

    @Override
    public Resources.Theme getTheme() {
        Resources.Theme theme = super.getTheme();

        SharedPreferences pref = PreferenceManager
                .getDefaultSharedPreferences(this);

        String themeName = pref.getString("pref_theme", getString(R.string.theme_dark_value));
        if (themeName.equals(getString(R.string.theme_dark_value))) {
            theme.applyStyle(R.style.AppTheme_NoActionBar, true);
        } else if (themeName.equals(getString(R.string.theme_light_value))) {
            theme.applyStyle(R.style.LightAppTheme_NoActionBar, true);
        }


        return theme;
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        SharedPreferences pref = PreferenceManager
                .getDefaultSharedPreferences(this);

        String lang = pref.getString("pref_lang", "ru");
        Locale locale = new Locale(lang);
        newConfig.setLocale(locale);
        super.onConfigurationChanged(newConfig);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(disebledAds);
        mPresenter.onDestroy();
        sendBroadcast(new Intent(MediaPlayerService.Broadcast_CloseIfPause));
        closeApp = true;
    }

    private void register_disebledAds() {
        IntentFilter filter = new IntentFilter(Broadcast_DISABLE_ADS);
        registerReceiver(disebledAds, filter);
    }
}
