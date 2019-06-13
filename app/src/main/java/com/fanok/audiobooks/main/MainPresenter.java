package com.fanok.audiobooks.main;

import android.support.annotation.NonNull;
import android.view.MenuItem;

import com.fanok.audiobooks.R;

public class MainPresenter implements MainContract.Presenter {


    private MainContract.View mView;


    MainPresenter(MainContract.View view) {
        mView = view;
    }

    @Override
    public void onItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.nav_audiobooks) {
            // Handle the camera action
        } else if (id == R.id.nav_genre) {

        } else if (id == R.id.nav_autor) {

        } else if (id == R.id.nav_artist) {

        } else if (id == R.id.nav_favorite) {

        } else if (id == R.id.nav_history) {

        } else if (id == R.id.nav_settings) {

        } else if (id == R.id.nav_about) {

        }
    }

    @Override
    public void onDestroy() {

    }

    @Override
    public void onCreate() {

    }
}
