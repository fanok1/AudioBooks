package com.fanok.audiobooks.activity;

import static com.fanok.audiobooks.Consts.handleUserInput;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;
import android.app.UiModeManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.os.PowerManager;
import android.util.Log;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
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
import com.fanok.audiobooks.AppThemes;
import com.fanok.audiobooks.BuildConfig;
import com.fanok.audiobooks.R;
import com.fanok.audiobooks.fragment.BooksFragment;
import com.fanok.audiobooks.interface_pacatge.main.MainView;
import com.fanok.audiobooks.pojo.BookPOJO;
import com.fanok.audiobooks.pojo.StorageUtil;
import com.fanok.audiobooks.presenter.MainPresenter;
import com.fanok.audiobooks.service.MediaPlayerService;
import com.fanok.audiobooks.update.Updater;
import com.google.android.material.navigation.NavigationView;

import java.util.ArrayList;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;


public class MainActivity extends MvpAppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener, MainView {
    private static final String TAG = "MainActivity";
    private static final String EXSTRA_FRAGMENT = "startFragment";
    private static final String EXSTRA_URL = "url";
    private static boolean closeApp = false;
    /** True on a fresh creation only: a restored activity keeps its fragments and has had its first-launch dialogs. */
    private boolean freshStart = false;



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

    private NavigationView navigationView;
    private final DialogQueue dialogs = new DialogQueue();
    private boolean updateQueued;
    private Runnable permissionDone;

    private SharedPreferences preferences;

    private final ActivityResultLauncher<String> notificationPermission = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), granted -> {
                if (permissionDone != null) {
                    Runnable done = permissionDone;
                    permissionDone = null;
                    done.run();
                }
            });


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

    public ArrayList<TextView> getTextViewArrayList() {
        return mTextViewArrayList;
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (!freshStart) {
            return;
        }
        freshStart = false;

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

        // one decision at a time, each with its reason (UI-18); parental control is in the settings, not asked here
        if (!preferences.getBoolean("first", false)) {
            dialogs.add(this::showPrivacyDialog);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            dialogs.add(this::showNotificationRationale);
        }
        if (shouldAskBatteryOptimization()) {
            dialogs.add(this::showBatteryDialog);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateNowPlayingEntry();
        Updater.setListener(this::offerUpdate);
        offerUpdate();
    }

    @Override
    protected void onPause() {
        Updater.setListener(null);
        super.onPause();
    }

    /** A downloaded update (or one on 4pda that needs a login) is put to the user once, after other dialogs. */
    private void offerUpdate() {
        Updater.Offer offer = Updater.pending(this);
        if (offer == null || updateQueued) {
            return;
        }
        updateQueued = true;
        dialogs.add(done -> showUpdateDialog(offer, done));
    }

    private void showUpdateDialog(@NonNull Updater.Offer offer, @NonNull Runnable done) {
        if (isFinishing() || isDestroyed()) {
            updateQueued = false;
            done.run();
            return;
        }
        // any answer puts it off for a day (a check by hand shows it again); a dialog closed by recreation does not
        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(R.string.update_title)
                .setIcon(R.drawable.ic_update)
                .setNegativeButton(R.string.update_later, (dialog, which) -> Updater.snooze(this, offer))
                .setOnCancelListener(dialog -> Updater.snooze(this, offer))
                .setOnDismissListener(dialog -> {
                    updateQueued = false;
                    done.run();
                });
        String version = offer.release.version;
        if (offer.apk != null) {
            builder.setMessage(getString(R.string.update_ready, version, BuildConfig.VERSION_NAME))
                    .setPositiveButton(R.string.update_install, (dialog, which) -> {
                        Updater.snooze(this, offer);
                        Updater.install(this, offer);
                    });
        } else {
            String page = offer.release.page;
            builder.setMessage(getString(R.string.update_forum, version, BuildConfig.VERSION_NAME))
                    .setNeutralButton(R.string.update_in_browser, (dialog, which) -> {
                        Updater.snooze(this, offer);
                        try {
                            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(page)));
                        } catch (ActivityNotFoundException e) {
                            Toast.makeText(this, R.string.browser_not_found, Toast.LENGTH_SHORT).show();
                        }
                    });
            if (getPackageManager().hasSystemFeature(PackageManager.FEATURE_WEBVIEW)) {
                builder.setPositiveButton(R.string.update_forum_login, (dialog, which) -> {
                    Updater.snooze(this, offer);
                    ForumLoginActivity.start(this, page);
                });
            }
        }
        builder.show();
    }

    /** "Now playing" is offered only while the player service holds a book. */
    private void updateNowPlayingEntry() {
        boolean visible = MediaPlayerService.isRunning() && MediaPlayerService.currentBook() != null;
        if (navigationView != null) {
            MenuItem item = navigationView.getMenu().findItem(R.id.nav_now_playing);
            if (item != null) {
                item.setVisible(visible);
            }
        }
        View textView = findViewById(R.id.layout_nav_now_playing);
        if (textView != null) {
            textView.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
    }

    private boolean shouldAskBatteryOptimization() {
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (pm == null || pm.isIgnoringBatteryOptimizations(getPackageName())) {
            return false;
        }
        UiModeManager uiModeManager = (UiModeManager) getApplicationContext().getSystemService(UI_MODE_SERVICE);
        boolean dontShow = new StorageUtil(this).loadBattaryOptimizeDisenbled();
        return uiModeManager != null
                && uiModeManager.getCurrentModeType() != Configuration.UI_MODE_TYPE_TELEVISION && !dontShow;
    }

    private void showPrivacyDialog(@NonNull Runnable done) {
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.privacy))
                .setMessage(getString(R.string.privacy_message))
                .setIcon(R.drawable.ic_privacy)
                .setCancelable(false)
                .setPositiveButton(getString(R.string.yes), (dialog, id) -> {
                    preferences.edit().putBoolean("first", true).apply();
                    dialog.cancel();
                })
                .setOnDismissListener(dialog -> done.run())
                .show();
    }

    private void showNotificationRationale(@NonNull Runnable done) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.app_name)
                .setMessage(R.string.notification_permission_rationale)
                .setCancelable(false)
                .setPositiveButton(R.string.continue_, (dialog, which) -> {
                    permissionDone = done;
                    notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS);
                })
                .setNegativeButton(R.string.cancel, (dialog, which) -> done.run())
                .show();
    }

    private void showBatteryDialog(@NonNull Runnable done) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.app_name)
                .setMessage(R.string.setIgnoredBatteryOptimyze)
                .setNegativeButton(R.string.cancel, null)
                .setCancelable(true)
                .setNeutralButton(R.string.do_not_how, (dialogInterface, i) -> setBattaryOptimizeDisenbled(true))
                .setPositiveButton(R.string.ok,
                        (dialogInterface, i) -> mPresenter.openSettingsOptimizeBattery(dialogInterface))
                .setOnDismissListener(dialog -> done.run())
                .show();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        View currentFocus = getCurrentFocus();
        if (currentFocus!=null){
            if(keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                if (currentFocus.getId()==R.id.layout_nav_audiobooks||currentFocus.getId()==R.id.toolbar) {
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
                    View view = findViewById(R.id.layout_nav_audiobooks);
                    if(view!=null){
                        view.requestFocus();
                    }
                }
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // the style before any inflation; the preference is read once here (UI-19)
        AppThemes.apply(this, R.style.AppTheme_NoAnimTheme, R.style.LightAppTheme_NoAnimTheme,
                R.style.AppThemeBlack_NoAnimTheme);
        super.onCreate(savedInstanceState);
        closeApp = false;
        Log.d(TAG, "onCreate: called");

        setContentView(R.layout.activity_main);
        Updater.start(this); // once per process: a check in a few seconds, then every 1.5 h

        preferences = getSharedPreferences("FIRST", Context.MODE_PRIVATE);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);


        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        navigationView = findViewById(R.id.nav_view);
        if (drawer != null && navigationView != null) {
            ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                    this, drawer, toolbar, R.string.navigation_drawer_open,
                    R.string.navigation_drawer_close);
            drawer.addDrawerListener(toggle);
            drawer.addDrawerListener(new DrawerLayout.SimpleDrawerListener() {
                @Override
                public void onDrawerStateChanged(int newState) {
                    // the entry shows the player as it is when the drawer starts to open
                    if (newState != DrawerLayout.STATE_IDLE && !drawer.isDrawerOpen(GravityCompat.START)) {
                        updateNowPlayingEntry();
                    }
                }
            });
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
                            if (view1.getId() == R.id.layout_nav_exit) {
                                MediaPlayerService.exitApp(this);
                                return;
                            }
                            if (view1.getId() == R.id.layout_nav_now_playing) {
                                // opens the book screen, so the current section stays highlighted
                                mPresenter.onItemSelected(view1.getId());
                                updateNowPlayingEntry(); //a dead entry (the player stopped meanwhile) hides itself
                                return;
                            }
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


        freshStart = savedInstanceState == null;


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
                    getSupportFragmentManager().popBackStack();
                    return;
                }
                setEnabled(false);
                getOnBackPressedDispatcher().onBackPressed();
                setEnabled(true);
            }
        });
    }


    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.nav_exit) {
            MediaPlayerService.exitApp(this);
            return true;
        }
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

    /**
     * Shows {@code fragment} under {@code tag}. The fragment manager's back stack is the only navigation state
     * (UI-03): a screen already on top is kept, an older copy of the same screen leaves the stack before the new
     * one is pushed, and nothing is committed after the state was saved.
     */
    @Override
    public void showFragment(@NotNull Fragment fragment, @NonNull String tag) {
        FragmentManager fragmentManager = getSupportFragmentManager();
        if (fragmentManager.isStateSaved()) {
            return;
        }
        Fragment current = fragmentManager.findFragmentById(R.id.container);
        // the same tag on another URL (a filter-bar period rewrote the current one) is a different listing
        if (current != null && tag.equals(current.getTag()) && !tag.equals("searchebleBooks")
                && Objects.equals(urlArg(current), urlArg(fragment))) {
            return;
        }
        fragmentManager.popBackStackImmediate(tag, FragmentManager.POP_BACK_STACK_INCLUSIVE);
        Fragment top = fragmentManager.findFragmentById(R.id.container);
        FragmentTransaction transaction = fragmentManager.beginTransaction();
        transaction.replace(R.id.container, fragment, tag);
        // the root screen still on top after the pop and re-opened on the URL it was opened on (a filter-bar
        // period rewrote its current one) is replaced in place, so Back does not show a copy of it; another
        // listing under the same tag (an author's, opened from a book) is pushed
        String opened = top != null && top.getArguments() != null
                ? top.getArguments().getString(BooksFragment.ARG_OPENED_URL) : null;
        if (top != null && !(tag.equals(top.getTag()) && opened != null && opened.equals(urlArg(fragment)))) {
            transaction.addToBackStack(tag);
        }
        transaction.commit();

    }

    private static String urlArg(Fragment fragment) {
        Bundle args = fragment.getArguments();
        return args == null ? null : args.getString("url");
    }

    @Override
    public void showToast(final int id) {
        Toast.makeText(this, id, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void setBattaryOptimizeDisenbled(boolean b) {
        new StorageUtil(this).storeBattaryOptimizeDisenbled(b);
    }

    @Override
    protected void onDestroy() {
        //playback belongs to the player service: closing (or rotating) this screen neither stops nor hides it
        super.onDestroy();
        closeApp = true;
    }


    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        // media keys are not shortcuts while the user types (UI-16)
        if (event.getAction() == KeyEvent.ACTION_DOWN && !(getCurrentFocus() instanceof EditText)) {
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
