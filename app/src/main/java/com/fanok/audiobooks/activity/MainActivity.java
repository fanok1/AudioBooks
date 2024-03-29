package com.fanok.audiobooks.activity;

import static com.fanok.audiobooks.Consts.handleUserInput;
import static com.fanok.audiobooks.service.MediaPlayerService.getNotificationId;

import android.app.NotificationManager;
import android.app.UiModeManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
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
import com.arellomobile.mvp.presenter.ProvidePresenter;
import com.fanok.audiobooks.FragmentTagSteck;
import com.fanok.audiobooks.LocaleManager;
import com.fanok.audiobooks.R;
import com.fanok.audiobooks.interface_pacatge.main.MainView;
import com.fanok.audiobooks.pojo.BookPOJO;
import com.fanok.audiobooks.pojo.StorageAds;
import com.fanok.audiobooks.pojo.StorageUtil;
import com.fanok.audiobooks.presenter.MainPresenter;
import com.fanok.audiobooks.service.MediaPlayerService;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.MobileAds;
import com.google.android.material.navigation.NavigationView;
import java.util.ArrayList;
import java.util.Locale;
import org.jetbrains.annotations.NotNull;


public class MainActivity extends MvpAppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener, MainView {
    private static final String TAG = "MainActivity";
    public static final String Broadcast_DISABLE_ADS = "DISABLE_ADS";


    private static final String EXSTRA_FRAGMENT = "startFragment";
    private static final String EXSTRA_URL = "url";
    private static boolean closeApp = false;
    private AdView mAdView;
    private boolean isSavedInstanceState = false;

    public static boolean isCloseApp() {
        return closeApp;
    }

    private ArrayList<TextView> mTextViewArrayList;

    @InjectPresenter
    MainPresenter mPresenter;

    @ProvidePresenter
    MainPresenter provideBookPresenter() {
        return new MainPresenter(getApplicationContext());
    }

    private ArrayList<FragmentTagSteck> fragmentsTag;
    private NavigationView navigationView;
    private AlertDialog.Builder alert;
    private boolean firstStart = true;

    private SharedPreferences preferences;
    private final BroadcastReceiver disebledAds = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            showAds(context);
        }
    };

    private void showAds(Context context) {
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
                            UiModeManager uiModeManager = (UiModeManager) getSystemService(
                                    UI_MODE_SERVICE);
                            StorageUtil storageUtil = new StorageUtil(this);
                            boolean b = storageUtil.loadBattaryOptimizeDisenbled();
                            if (uiModeManager != null && uiModeManager.getCurrentModeType()
                                    != Configuration.UI_MODE_TYPE_TELEVISION && !b) {
                                alert.show();
                            }
                        }
                    }
                }

                if (!preferences.getBoolean("first", false)) {

                    AlertDialog.Builder parentalControlBuilder =
                            new AlertDialog.Builder(this);
                    parentalControlBuilder.setTitle(R.string.parental_control)
                            .setMessage(R.string.enabled_parental_control)
                            .setIcon(R.drawable.ic_lock)
                            .setCancelable(false)
                            .setPositiveButton(getString(R.string.yes),
                                    (dialogInterface, i) -> {
                                        dialogInterface.dismiss();
                                        Intent intent = new Intent(getApplicationContext(),
                                                ParentalControlActivity.class);
                                        intent.putExtra("enabled", true);
                                        startActivity(intent);
                                    })
                            .setNegativeButton(getString(R.string.cancel), null);

                    AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    builder.setTitle(getString(R.string.privacy))
                            .setMessage(getString(R.string.privacy_message))
                            .setIcon(R.drawable.ic_privacy)
                            .setCancelable(false)
                            .setPositiveButton(getString(R.string.yes),
                                    (dialog, id) -> {
                                        SharedPreferences.Editor editor = preferences.edit();
                                        editor.putBoolean("first", true);
                                        editor.apply();
                                        dialog.cancel();
                                        AlertDialog parentControlAlert =
                                                parentalControlBuilder.create();
                                        parentControlAlert.show();
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
                    mPresenter.startFragment(fragment,
                            intent.getBooleanExtra("notificationClick", false));
                }
                firstStart = false;
            }
        }
    }

    @Override
    public void onBackPressed() {
        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        if (drawer != null && drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else {
            if (getSupportFragmentManager().getBackStackEntryCount() > 0) {
                if (drawer == null) {
                    LinearLayout linearLayout = findViewById(R.id.liner_nav_view);
                    if (linearLayout != null) {
                        final TypedValue outValue = new TypedValue();
                        getTheme().resolveAttribute(android.R.attr.selectableItemBackground,
                                outValue,
                                true);
                        for (int i = 0; i < linearLayout.getChildCount(); i++) {
                            View view = linearLayout.getChildAt(i);
                            if (view instanceof TextView) {
                                view.setBackgroundResource(outValue.resourceId);
                            }
                        }
                    }
                }
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
        UiModeManager uiModeManager = (UiModeManager) getSystemService(UI_MODE_SERVICE);
        if (uiModeManager != null
                && uiModeManager.getCurrentModeType() == Configuration.UI_MODE_TYPE_TELEVISION) {
            setContentView(R.layout.activity_main_television);
        } else {
            setContentView(R.layout.activity_main);
        }
        mAdView = findViewById(R.id.adView);

        SharedPreferences pref = PreferenceManager
                .getDefaultSharedPreferences(this);

        preferences = getSharedPreferences("FIRST", Context.MODE_PRIVATE);

        String themeName = pref.getString("pref_theme", getString(R.string.theme_dark_value));
        if (themeName.equals(getString(R.string.theme_dark_value))) {
            setTheme(R.style.AppTheme_NoAnimTheme);
        } else if (themeName.equals(getString(R.string.theme_light_value))) {
            setTheme(R.style.LightAppTheme_NoAnimTheme);
        } else if (themeName.equals(getString(R.string.theme_black_value))) {
            setTheme(R.style.AppThemeBlack_NoAnimTheme);
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


        showAds(this);

        alert = new AlertDialog.Builder(this);
        alert.setTitle(R.string.app_name);
        alert.setMessage(R.string.setIgnoredBatteryOptimyze);
        alert.setNegativeButton(R.string.cancel, null);
        alert.setCancelable(true);
        alert.setNeutralButton(R.string.do_not_how,
                (dialogInterface, i) -> setBattaryOptimizeDisenbled(true));
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
        if (fragmentsTag.size() > 1) {
            transaction.addToBackStack(tag);
        }
        transaction.commit();

    }

    @Override
    public void showToast(final int id) {
        Toast.makeText(this, id, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void setBattaryOptimizeDisenbled(boolean b) {
        new StorageUtil(this).storeBattaryOptimizeDisenbled(b);
    }

    private void addFragmentTag(@NonNull String tag) {
        for (int i = 0; i < fragmentsTag.size(); i++) {
            if (fragmentsTag.get(i).getTag().equals(tag)) {
                fragmentsTag.get(i).setSkip(true);
            }
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
        } else if (themeName.equals(getString(R.string.theme_black_value))) {
            theme.applyStyle(R.style.AppThemeBlack_NoActionBar, true);
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
        sendBroadcast(new Intent(MediaPlayerService.Broadcast_CloseIfPause));
        NotificationManager notificationManager =
                (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            notificationManager.cancel(getNotificationId());
        }
        super.onDestroy();
        unregisterReceiver(disebledAds);
        mPresenter.onDestroy();
        closeApp = true;
    }

    private void register_disebledAds() {
        IntentFilter filter = new IntentFilter(Broadcast_DISABLE_ADS);
        registerReceiver(disebledAds, filter);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            if (handleUserInput(getApplicationContext(), event.getKeyCode())) {
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public void showBooksActivity(@Nullable BookPOJO bookPOJO) {
        if (bookPOJO == null) return;
        BookActivity.startNewActivity(this, bookPOJO);
    }
}
