package com.fanok.audiobooks.presenter;

import static android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.arellomobile.mvp.InjectViewState;
import com.arellomobile.mvp.MvpPresenter;
import com.fanok.audiobooks.Consts;
import com.fanok.audiobooks.R;
import com.fanok.audiobooks.fragment.AboutFragment;
import com.fanok.audiobooks.fragment.BooksFragment;
import com.fanok.audiobooks.fragment.FavoriteFragment;
import com.fanok.audiobooks.fragment.SettingsFragment;
import com.fanok.audiobooks.interface_pacatge.main.MainView;
import com.fanok.audiobooks.model.BooksDBModel;
import com.fanok.audiobooks.pojo.BookPOJO;
import com.fanok.audiobooks.search.Provider;
import com.fanok.audiobooks.service.MediaPlayerService;

@InjectViewState
public class MainPresenter extends MvpPresenter<MainView> implements
        com.fanok.audiobooks.interface_pacatge.main.MainPresenter {

    private static final String TAG = "MainPresenter";

    private final Context mContext;

    public MainPresenter(@NonNull Context context) {
        mContext = context;
    }

    /** Opens a drawer section of the selected source at {@code url}; a source without the section says so. */
    private void openSection(String url, int title, int modelId, String tag) {
        if (url == null) {
            getViewState().showToast(R.string.section_not_available);
            return;
        }
        getViewState().showFragment(BooksFragment.newInstance(url, title, modelId), tag);
    }

    private void openAudiobooks(Provider source) {
        openSection(source.index, R.string.menu_audiobooks, Consts.MODEL_BOOKS, "audioBook");
    }

    @Override
    public void onItemSelected(int id) {
        Provider source = Provider.current();
        if (source == null) {
            return;
        }
        if (id == R.id.nav_audiobooks || id == R.id.layout_nav_audiobooks) {
            openAudiobooks(source);
        } else if (id == R.id.nav_genre || id == R.id.layout_nav_genre) {
            openSection(source.sections, R.string.menu_genre, Consts.MODEL_GENRE, "genre");
        } else if (id == R.id.nav_autor || id == R.id.layout_nav_autor) {
            openSection(source.authors, R.string.menu_autor, Consts.MODEL_AUTOR, "autor");
        } else if (id == R.id.nav_artist || id == R.id.layout_nav_artist) {
            openSection(source.performers, R.string.menu_artist, Consts.MODEL_ARTIST, "artist");
        } else if (id == R.id.nav_favorite || id == R.id.layout_nav_favorite) {
            Fragment fragment = FavoriteFragment.newInstance(R.string.menu_favorite,
                    Consts.TABLE_FAVORITE);
            getViewState().showFragment(fragment, "favorite");

        } else if (id == R.id.nav_history || id == R.id.layout_nav_history) {
            Fragment fragment = FavoriteFragment.newInstance(R.string.menu_history,
                    Consts.TABLE_HISTORY);
            getViewState().showFragment(fragment, "history");

        } else if (id == R.id.nav_settings || id == R.id.layout_nav_settings) {
            getViewState().showFragment(new SettingsFragment(), "settings");


        } else if (id == R.id.nav_about || id == R.id.layout_nav_about) {
            getViewState().showFragment(new AboutFragment(), "about");
        } else if (id == R.id.nav_saved || id == R.id.layout_nav_saved) {
            Fragment fragment = FavoriteFragment.newInstance(R.string.menu_saved,
                    Consts.TABLE_SAVED);
            getViewState().showFragment(fragment, "saved");
        } else if (id == R.id.nav_now_playing || id == R.id.layout_nav_now_playing) {
            // the activity opens it, so parental control can ask for the PIN on screen
            BookPOJO book = MediaPlayerService.currentBook();
            if (book != null) {
                getViewState().showBooksActivity(book);
            }
        }
    }

    @Override
    public void onDestroy() {

    }

    @Override
    public void startFragment(int fragmentID, String url) {

        if (url.isEmpty()) {
            startFragment(fragmentID, false);
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
            case Consts.FRAGMENT_SETTINGS:
            case Consts.FRAGMENT_SAVED:
                startFragment(fragmentID, false);
                break;
        }
    }

    @Override
    public void startFragment(int fragmentID, boolean b) {
        System.out.println(getViewState());
        Provider source = Provider.current();
        Fragment fragment;
        if (fragmentID == Consts.FRAGMENT_AUDIOBOOK) {
            if (source != null) {
                openAudiobooks(source);
            }
        } else if (fragmentID == Consts.FRAGMENT_GENRE) {
            if (source != null) {
                openSection(source.sections, R.string.menu_genre, Consts.MODEL_GENRE, "genre");
            }
        } else if (fragmentID == Consts.FRAGMENT_AUTOR) {
            if (source != null) {
                openSection(source.authors, R.string.menu_autor, Consts.MODEL_AUTOR, "autor");
            }
        } else if (fragmentID == Consts.FRAGMENT_ARTIST) {
            if (source != null) {
                openSection(source.performers, R.string.menu_artist, Consts.MODEL_ARTIST, "artist");
            }
        } else if (fragmentID == Consts.FRAGMENT_FAVORITE) {
            fragment = FavoriteFragment.newInstance(R.string.menu_favorite,
                    Consts.TABLE_FAVORITE);
            getViewState().showFragment(fragment, "favorite");

        } else if (fragmentID == Consts.FRAGMENT_HISTORY) {
            fragment = FavoriteFragment.newInstance(R.string.menu_history,
                    Consts.TABLE_HISTORY);
            getViewState().showFragment(fragment, "history");
        } else if (fragmentID == Consts.FRAGMENT_SAVED) {
            fragment = FavoriteFragment.newInstance(R.string.menu_saved,
                    Consts.TABLE_SAVED);
            getViewState().showFragment(fragment, "saved");
        } else if (fragmentID == Consts.LAST_BOOK) {
            if (source != null) {
                openAudiobooks(source);
            }
            if (!b) {
                BooksDBModel booksDBModel = new BooksDBModel(mContext);
                BookPOJO bookPOJO = booksDBModel.getHistory();
                getViewState().showBooksActivity(bookPOJO);
            }
        } else if (fragmentID == Consts.FRAGMENT_SETTINGS) {
            getViewState().showFragment(new SettingsFragment(), "settings");
        }
    }

    @Override
    public void openSettingsOptimizeBattery(@NonNull DialogInterface dialogInterface) {
        try {
            getViewState().openActivity(
                    new Intent(ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
        } catch (ActivityNotFoundException e) {
            getViewState().setBattaryOptimizeDisenbled(true);
        }
    }
}
