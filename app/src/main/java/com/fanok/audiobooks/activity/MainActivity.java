package com.fanok.audiobooks.activity;

import static com.fanok.audiobooks.Consts.APP_FRAGMENT;
import static com.fanok.audiobooks.Consts.APP_PREFERENCES;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.arellomobile.mvp.MvpAppCompatActivity;
import com.arellomobile.mvp.presenter.InjectPresenter;
import com.fanok.audiobooks.Consts;
import com.fanok.audiobooks.FragmentTagSteck;
import com.fanok.audiobooks.R;
import com.fanok.audiobooks.interface_pacatge.main.MainView;
import com.fanok.audiobooks.presenter.MainPresenter;
import com.google.android.material.navigation.NavigationView;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;


public class MainActivity extends MvpAppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener, MainView {
    private static final String TAG = "MainActivity";


    private static final String EXSTRA_FRAGMENT = "startFragment";
    private static final String EXSTRA_URL = "url";
    private boolean isSavedInstanceState = false;


    @InjectPresenter
    MainPresenter mPresenter;

    private ArrayList<FragmentTagSteck> fragmentsTag;
    private NavigationView navigationView;
    private boolean firstStart = true;


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
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate: called");
        setContentView(R.layout.activity_main);
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        navigationView = findViewById(R.id.nav_view);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open,
                R.string.navigation_drawer_close);
        drawer.addDrawerListener(toggle);
        toggle.syncState();
        navigationView.setNavigationItemSelectedListener(this);
        mPresenter.onCreate();
        fragmentsTag = new ArrayList<>();
        isSavedInstanceState = savedInstanceState != null;
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (!isSavedInstanceState) {
            if (firstStart) {
                Intent intent = getIntent();
                int fragment = intent.getIntExtra(EXSTRA_FRAGMENT, -1);
                String url = intent.getStringExtra(EXSTRA_URL);
                if (url != null && !url.isEmpty() && fragment != -1) {
                    mPresenter.startFragment(fragment, url);
                } else {
                    SharedPreferences mSettings = getSharedPreferences(APP_PREFERENCES,
                            Context.MODE_PRIVATE);
                    fragment = mSettings.getInt(APP_FRAGMENT, Consts.FRAGMENT_AUDIOBOOK);
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
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        mPresenter.onItemSelected(item);
        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mPresenter.onDestroy();
    }

    @Override
    public void openActivity(@NotNull @NonNull Intent intent) {
        startActivity(intent);
    }

    @Override
    public void showFragment(@NotNull @NonNull Fragment fragment, @NonNull String tag) {
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
}
