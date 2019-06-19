package com.fanok.audiobooks.presenter;

import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.view.MenuItem;

import com.fanok.audiobooks.R;
import com.fanok.audiobooks.fragment.BooksFragment;
import com.fanok.audiobooks.interface_pacatge.MainContract;

public class MainPresenter implements MainContract.Presenter {


    private MainContract.View mView;


    public MainPresenter(MainContract.View view) {
        mView = view;
    }

    @Override
    public void onItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.nav_audiobooks) {
            Fragment fragment = BooksFragment.newInstance("https://audioknigi.club/index/page", 1);
            mView.showFragment(fragment, "audioBook");
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
