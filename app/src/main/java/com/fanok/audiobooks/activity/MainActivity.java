package com.fanok.audiobooks.activity;

import static com.fanok.audiobooks.Consts.handleUserInput;
import static com.fanok.audiobooks.service.MediaPlayerService.getNotificationId;

import android.app.NotificationManager;
import android.app.UiModeManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.PowerManager;
import android.util.Log;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
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
import com.fanok.audiobooks.pojo.StorageUtil;
import com.fanok.audiobooks.presenter.MainPresenter;
import com.fanok.audiobooks.service.MediaPlayerService;
import com.fanok.audiobooks.util.AvatarGenerator;
import com.google.android.material.navigation.NavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.squareup.picasso.Picasso;
import de.hdodenhof.circleimageview.CircleImageView;
import java.util.ArrayList;
import java.util.Locale;
import org.jetbrains.annotations.NotNull;


public class MainActivity extends MvpAppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener, MainView {
    private static final String TAG = "MainActivity";
    private static final String EXSTRA_FRAGMENT = "startFragment";
    private static final String EXSTRA_URL = "url";
    private static boolean closeApp = false;
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
    
    private CircleImageView headerImageView;
    private TextView headerLoginView;
    private TextView headerEmailView;
    private ImageView headerLogoutView;

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
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        View currentFocus = getCurrentFocus();
        if (currentFocus!=null){
            if(keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                if (currentFocus.getId()==R.id.imageViewLogout||currentFocus.getId()==R.id.toolbar) {
                    View menuItem = findViewById(R.id.app_bar_search);
                    if (menuItem != null) {
                        menuItem.requestFocus();
                    }
                }
            }
            if(keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                if (currentFocus.getId()==R.id.app_bar_search){
                    Toolbar toolbar = findViewById(R.id.toolbar);
                    toolbar.requestFocus();
                }
                if (currentFocus.getId()==R.id.toolbar){
                    View view = findViewById(R.id.imageViewLogout);
                    if(view!=null){
                        view.requestFocus();
                    }
                }
            }

            if(keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_DPAD_CENTER) {
                if (currentFocus.getId()==R.id.imageViewLogout){
                    mPresenter.onLoginLogoutClicked();
                }
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        closeApp = false;
        Log.d(TAG, "onCreate: called");

        setContentView(R.layout.activity_main);







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
            
            View headerView = navigationView.getHeaderView(0);
            headerImageView = headerView.findViewById(R.id.imageView);
            headerLoginView = headerView.findViewById(R.id.textViewLogin);
            headerEmailView = headerView.findViewById(R.id.textViewEmail);
            headerLogoutView = headerView.findViewById(R.id.imageViewLogout);

            
            if (headerLogoutView != null) {
                headerLogoutView.setOnClickListener(v -> {
                    if (FirebaseAuth.getInstance().getCurrentUser() == null) {
                        mPresenter.onLoginLogoutClicked();
                    }else {
                        new AlertDialog.Builder(this)
                                .setTitle(R.string.exit)
                                .setMessage(R.string.logout_text)
                                .setPositiveButton(R.string.yes, (dialog, which) -> {
                                    FirebaseAuth.getInstance().signOut();
                                })
                                .setNegativeButton(R.string.cancel, null)
                                .show();
                    }
                });
            }

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
        alert = new AlertDialog.Builder(this);
        alert.setTitle(R.string.app_name);
        alert.setMessage(R.string.setIgnoredBatteryOptimyze);
        alert.setNegativeButton(R.string.cancel, null);
        alert.setCancelable(true);
        alert.setNeutralButton(R.string.do_not_how,
                (dialogInterface, i) -> setBattaryOptimizeDisenbled(true));
        alert.setPositiveButton("OK",
                (dialogInterface, i) -> mPresenter.openSettingsOptimizeBattery(dialogInterface));


        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                DrawerLayout drawer = findViewById(R.id.drawer_layout);
                if (drawer != null && drawer.isDrawerOpen(GravityCompat.START)) {
                    drawer.closeDrawer(GravityCompat.START);
                    return;
                }


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
                    if (!fragmentsTag.isEmpty()) fragmentsTag.remove(fragmentsTag.size() - 1);
                    getSupportFragmentManager().popBackStack();
                    while (true) {
                        if (!fragmentsTag.isEmpty() && fragmentsTag.get(
                                fragmentsTag.size() - 1).isSkip()) {
                            fragmentsTag.remove(fragmentsTag.size() - 1);
                            getSupportFragmentManager().popBackStack();
                        } else {
                            break;
                        }
                    }
                    return;
                }
                setEnabled(false);
                getOnBackPressedDispatcher().onBackPressed();
                setEnabled(true);
            }
        });
    }


    @Override
    protected void onResume() {
        super.onResume();
        mPresenter.onResume();
    }
    
    @Override
    protected void onPause() {
        super.onPause();
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
        if (!fragmentsTag.isEmpty() && tag.equals(
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
        mPresenter.onDestroy();
        closeApp = true;
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

    @Override
    public void updateUserInfo(String name, String email, String photo) {
        if (headerLoginView != null) {
            headerLoginView.setText(name);
        }
        if (headerEmailView != null) {
            headerEmailView.setText(email);
        }
        if (headerImageView != null && photo != null && !photo.isEmpty()) {
            Picasso.get().load(photo).placeholder(R.mipmap.ic_launcher).into(headerImageView);
        } else if (headerImageView != null) {
            if (name != null && !name.isEmpty() && FirebaseAuth.getInstance().getCurrentUser() != null) {
                headerImageView.setImageBitmap(
                        AvatarGenerator.generateAvatar(name, 200));
            } else {
                headerImageView.setImageResource(R.mipmap.ic_launcher);
            }
        }
    }

    @Override
    public void updateIconLoginLogout(int id) {
        headerLogoutView.setImageResource(id);
    }


}
