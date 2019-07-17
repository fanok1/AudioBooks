package com.fanok.audiobooks.activity;

import static com.fanok.audiobooks.Consts.APP_FRAGMENT;
import static com.fanok.audiobooks.Consts.APP_PREFERENCES;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.NavigationView;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatDelegate;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.MenuItem;

import com.arellomobile.mvp.MvpAppCompatActivity;
import com.arellomobile.mvp.presenter.InjectPresenter;
import com.fanok.audiobooks.Consts;
import com.fanok.audiobooks.FragmentTagSteck;
import com.fanok.audiobooks.R;
import com.fanok.audiobooks.interface_pacatge.main.MainView;
import com.fanok.audiobooks.presenter.MainPresenter;

import java.util.ArrayList;


public class MainActivity extends MvpAppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener, MainView {
    private static final String TAG = "MainActivity";


    private static final String EXSTRA_FRAGMENT = "startFragment";
    private static final String EXSTRA_URL = "url";


    @InjectPresenter
    MainPresenter mPresenter;

    private ArrayList<FragmentTagSteck> fragmentsTag;
    private NavigationView navigationView;


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
    }

    @Override
    protected void onStart() {
        super.onStart();
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
    public void openActivity(@NonNull Intent intent) {
        startActivity(intent);
    }

    @Override
    public void showFragment(@NonNull Fragment fragment, @NonNull String tag) {
        if (fragmentsTag.size() != 0 && tag.equals(
                fragmentsTag.get(fragmentsTag.size() - 1).getTag())) {
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
