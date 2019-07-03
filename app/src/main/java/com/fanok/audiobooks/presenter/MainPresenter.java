package com.fanok.audiobooks.presenter;

import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.view.MenuItem;

import com.arellomobile.mvp.InjectViewState;
import com.arellomobile.mvp.MvpPresenter;
import com.fanok.audiobooks.Consts;
import com.fanok.audiobooks.R;
import com.fanok.audiobooks.Url;
import com.fanok.audiobooks.fragment.BooksFragment;
import com.fanok.audiobooks.fragment.FavoriteFragment;
import com.fanok.audiobooks.interface_pacatge.main.MainView;

@InjectViewState
public class MainPresenter extends MvpPresenter<MainView> implements
        com.fanok.audiobooks.interface_pacatge.main.MainPresenter {


    @Override
    public void onItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.nav_audiobooks) {
            Fragment fragment = BooksFragment.newInstance(Url.INDEX,
                    R.string.menu_audiobooks, Consts.MODEL_BOOKS);
            getViewState().showFragment(fragment, "audioBook");
        } else if (id == R.id.nav_genre) {
            Fragment fragment = BooksFragment.newInstance(Url.SECTIONS,
                    R.string.menu_genre, Consts.MODEL_GENRE);
            getViewState().showFragment(fragment, "genre");

        } else if (id == R.id.nav_autor) {
            Fragment fragment = BooksFragment.newInstance(Url.AUTHORS,
                    R.string.menu_autor, Consts.MODEL_AUTOR);
            getViewState().showFragment(fragment, "autor");

        } else if (id == R.id.nav_artist) {
            Fragment fragment = BooksFragment.newInstance(
                    Url.PERFORMERS, R.string.menu_artist, Consts.MODEL_ARTIST);
            getViewState().showFragment(fragment, "artist");

        } else if (id == R.id.nav_favorite) {
            Fragment fragment = FavoriteFragment.newInstance(R.string.menu_favorite,
                    Consts.TABLE_FAVORITE);
            getViewState().showFragment(fragment, "favorite");

        } else if (id == R.id.nav_history) {
            Fragment fragment = FavoriteFragment.newInstance(R.string.menu_history,
                    Consts.TABLE_HISTORY);
            getViewState().showFragment(fragment, "history");

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
