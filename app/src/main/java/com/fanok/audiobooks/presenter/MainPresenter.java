package com.fanok.audiobooks.presenter;

import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.arellomobile.mvp.InjectViewState;
import com.arellomobile.mvp.MvpPresenter;
import com.fanok.audiobooks.Consts;
import com.fanok.audiobooks.R;
import com.fanok.audiobooks.Url;
import com.fanok.audiobooks.fragment.BooksFragment;
import com.fanok.audiobooks.fragment.FavoriteFragment;
import com.fanok.audiobooks.fragment.SettingsFragment;
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
            getViewState().showFragment(new SettingsFragment(), "swttings");


        } else if (id == R.id.nav_about) {

        }
    }

    @Override
    public void onDestroy() {

    }

    @Override
    public void startFragment(int fragmentID, String url) {
        if (url == null || url.isEmpty()) {
            startFragment(fragmentID);
            return;
        }

        Fragment fragment;

        switch (fragmentID) {
            case Consts.FRAGMENT_AUDIOBOOK:
                fragment = BooksFragment.newInstance(url,
                        R.string.menu_audiobooks, Consts.MODEL_BOOKS);
                getViewState().showFragment(fragment, "audioBook");
                break;
            case Consts.FRAGMENT_GENRE:
                fragment = BooksFragment.newInstance(url,
                        R.string.menu_genre, Consts.MODEL_GENRE);
                getViewState().showFragment(fragment, "genre");
                break;
            case Consts.FRAGMENT_AUTOR:
                fragment = BooksFragment.newInstance(url,
                        R.string.menu_autor, Consts.MODEL_AUTOR);
                getViewState().showFragment(fragment, "autor");
                break;
            case Consts.FRAGMENT_ARTIST:
                fragment = BooksFragment.newInstance(
                        url, R.string.menu_artist, Consts.MODEL_ARTIST);
                getViewState().showFragment(fragment, "artist");
                break;
            case Consts.FRAGMENT_FAVORITE:
            case Consts.FRAGMENT_HISTORY:
                startFragment(fragmentID);
                break;
        }
    }

    @Override
    public void startFragment(int fragmentID) {
        System.out.println(getViewState());
        if (fragmentID == Consts.FRAGMENT_AUDIOBOOK) {
            Fragment fragment = BooksFragment.newInstance(Url.INDEX,
                    R.string.menu_audiobooks, Consts.MODEL_BOOKS);
            getViewState().showFragment(fragment, "audioBook");
        } else if (fragmentID == Consts.FRAGMENT_GENRE) {
            Fragment fragment = BooksFragment.newInstance(Url.SECTIONS,
                    R.string.menu_genre, Consts.MODEL_GENRE);
            getViewState().showFragment(fragment, "genre");

        } else if (fragmentID == Consts.FRAGMENT_AUTOR) {
            Fragment fragment = BooksFragment.newInstance(Url.AUTHORS,
                    R.string.menu_autor, Consts.MODEL_AUTOR);
            getViewState().showFragment(fragment, "autor");

        } else if (fragmentID == Consts.FRAGMENT_ARTIST) {
            Fragment fragment = BooksFragment.newInstance(
                    Url.PERFORMERS, R.string.menu_artist, Consts.MODEL_ARTIST);
            getViewState().showFragment(fragment, "artist");

        } else if (fragmentID == Consts.FRAGMENT_FAVORITE) {
            Fragment fragment = FavoriteFragment.newInstance(R.string.menu_favorite,
                    Consts.TABLE_FAVORITE);
            getViewState().showFragment(fragment, "favorite");

        } else if (fragmentID == Consts.FRAGMENT_HISTORY) {
            Fragment fragment = FavoriteFragment.newInstance(R.string.menu_history,
                    Consts.TABLE_HISTORY);
            getViewState().showFragment(fragment, "history");
        }
    }


}
