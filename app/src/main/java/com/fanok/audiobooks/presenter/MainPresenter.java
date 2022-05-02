package com.fanok.audiobooks.presenter;

import static android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Build;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import com.arellomobile.mvp.InjectViewState;
import com.arellomobile.mvp.MvpPresenter;
import com.fanok.audiobooks.Consts;
import com.fanok.audiobooks.R;
import com.fanok.audiobooks.Url;
import com.fanok.audiobooks.fragment.AboutFragment;
import com.fanok.audiobooks.fragment.BooksFragment;
import com.fanok.audiobooks.fragment.FavoriteFragment;
import com.fanok.audiobooks.fragment.SettingsFragment;
import com.fanok.audiobooks.interface_pacatge.main.MainView;
import com.fanok.audiobooks.model.BooksDBModel;
import com.fanok.audiobooks.pojo.BookPOJO;
import java.util.Objects;

@InjectViewState
public class MainPresenter extends MvpPresenter<MainView> implements
        com.fanok.audiobooks.interface_pacatge.main.MainPresenter {

    private static final String TAG = "MainPresenter";

    private final Context mContext;

    public MainPresenter(@NonNull Context context) {
        mContext = context;
    }

    @Override
    public void onItemSelected(int id) {

        if (id == R.id.nav_audiobooks || id == R.id.layout_nav_audiobooks) {
            Fragment fragment = null;
            if (Consts.getSOURCE() == Consts.SOURCE_KNIGA_V_UHE) {
                fragment = BooksFragment.newInstance(Url.INDEX,
                        R.string.menu_audiobooks, Consts.MODEL_BOOKS);
            } else if (Consts.getSOURCE() == Consts.SOURCE_IZI_BUK) {
                fragment = BooksFragment.newInstance(Url.INDEX_IZIBUK,
                        R.string.menu_audiobooks, Consts.MODEL_BOOKS);
            } else if (Consts.getSOURCE() == Consts.SOURCE_AUDIO_BOOK_MP3) {
                fragment = BooksFragment.newInstance(Url.INDEX_ABMP3,
                        R.string.menu_audiobooks, Consts.MODEL_BOOKS);
            } else if (Consts.getSOURCE() == Consts.SOURCE_ABOOK) {
                fragment = BooksFragment.newInstance(Url.INDEX_AKNIGA,
                        R.string.menu_audiobooks, Consts.MODEL_BOOKS);
            }
            getViewState().showFragment(Objects.requireNonNull(fragment), "audioBook");
        } else if (id == R.id.nav_genre || id == R.id.layout_nav_genre) {
            Fragment fragment = null;
            if (Consts.getSOURCE() == Consts.SOURCE_KNIGA_V_UHE) {
                fragment = BooksFragment.newInstance(Url.SECTIONS,
                        R.string.menu_genre, Consts.MODEL_GENRE);
            } else if (Consts.getSOURCE() == Consts.SOURCE_IZI_BUK) {
                fragment = BooksFragment.newInstance(Url.SECTIONS_IZIBUK,
                        R.string.menu_genre, Consts.MODEL_GENRE);
            } else if (Consts.getSOURCE() == Consts.SOURCE_AUDIO_BOOK_MP3) {
                fragment = BooksFragment.newInstance(Url.SECTIONS_ABMP3,
                        R.string.menu_genre, Consts.MODEL_GENRE);
            } else if (Consts.getSOURCE() == Consts.SOURCE_ABOOK) {
                fragment = BooksFragment.newInstance(Url.SECTIONS_AKNIGA,
                        R.string.menu_genre, Consts.MODEL_GENRE);
            }
            getViewState().showFragment(Objects.requireNonNull(fragment), "genre");

        } else if (id == R.id.nav_autor || id == R.id.layout_nav_autor) {

            Fragment fragment = null;
            if (Consts.getSOURCE() == Consts.SOURCE_KNIGA_V_UHE) {
                fragment = BooksFragment.newInstance(Url.AUTHORS,
                        R.string.menu_autor, Consts.MODEL_AUTOR);
            } else if (Consts.getSOURCE() == Consts.SOURCE_IZI_BUK) {
                fragment = BooksFragment.newInstance(Url.AUTHORS_IZIBUK,
                        R.string.menu_autor, Consts.MODEL_AUTOR);
            } else if (Consts.getSOURCE() == Consts.SOURCE_AUDIO_BOOK_MP3) {
                fragment = BooksFragment.newInstance(Url.AUTHORS_ABMP3,
                        R.string.menu_autor, Consts.MODEL_AUTOR);
            } else if (Consts.getSOURCE() == Consts.SOURCE_ABOOK) {
                fragment = BooksFragment.newInstance(Url.AUTHORS_AKNIGA,
                        R.string.menu_autor, Consts.MODEL_AUTOR);
            }

            getViewState().showFragment(Objects.requireNonNull(fragment), "autor");

        } else if (id == R.id.nav_artist || id == R.id.layout_nav_artist) {

            Fragment fragment = null;
            if (Consts.getSOURCE() == Consts.SOURCE_KNIGA_V_UHE) {
                fragment = BooksFragment.newInstance(
                        Url.PERFORMERS, R.string.menu_artist, Consts.MODEL_ARTIST);
            } else if (Consts.getSOURCE() == Consts.SOURCE_IZI_BUK) {
                fragment = BooksFragment.newInstance(
                        Url.PERFORMERS_IZIBUK, R.string.menu_artist, Consts.MODEL_ARTIST);
            } else if (Consts.getSOURCE() == Consts.SOURCE_AUDIO_BOOK_MP3) {
                fragment = BooksFragment.newInstance(
                        Url.PERFORMERS_ABMP3, R.string.menu_artist, Consts.MODEL_ARTIST);
            } else if (Consts.getSOURCE() == Consts.SOURCE_ABOOK) {
                fragment = BooksFragment.newInstance(
                        Url.PERFORMERS_AKNIGA, R.string.menu_artist, Consts.MODEL_ARTIST);
            }
            getViewState().showFragment(Objects.requireNonNull(fragment), "artist");

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

        }
    }

    @Override
    public void onDestroy() {

    }

    @Override
    public void startFragment(int fragmentID, String url) {
        if (url == null || url.isEmpty()) {
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
                startFragment(fragmentID, false);
                break;
        }
    }

    @Override
    public void startFragment(int fragmentID, boolean b) {
        System.out.println(getViewState());
        Fragment fragment = null;
        if (fragmentID == Consts.FRAGMENT_AUDIOBOOK) {
            if (Consts.getSOURCE() == Consts.SOURCE_KNIGA_V_UHE) {
                fragment = BooksFragment.newInstance(Url.INDEX,
                        R.string.menu_audiobooks, Consts.MODEL_BOOKS);
            } else if (Consts.getSOURCE() == Consts.SOURCE_IZI_BUK) {
                fragment = BooksFragment.newInstance(Url.INDEX_IZIBUK,
                        R.string.menu_audiobooks, Consts.MODEL_BOOKS);
            } else if (Consts.getSOURCE() == Consts.SOURCE_AUDIO_BOOK_MP3) {
                fragment = BooksFragment.newInstance(Url.INDEX_ABMP3,
                        R.string.menu_audiobooks, Consts.MODEL_BOOKS);
            } else if (Consts.getSOURCE() == Consts.SOURCE_ABOOK) {
                fragment = BooksFragment.newInstance(Url.INDEX_AKNIGA,
                        R.string.menu_audiobooks, Consts.MODEL_BOOKS);
            }


            getViewState().showFragment(Objects.requireNonNull(fragment), "audioBook");
        } else if (fragmentID == Consts.FRAGMENT_GENRE) {
            if (Consts.getSOURCE() == Consts.SOURCE_KNIGA_V_UHE) {
                fragment = BooksFragment.newInstance(Url.SECTIONS,
                        R.string.menu_genre, Consts.MODEL_GENRE);
            } else if (Consts.getSOURCE() == Consts.SOURCE_IZI_BUK) {
                fragment = BooksFragment.newInstance(Url.SECTIONS_IZIBUK,
                        R.string.menu_genre, Consts.MODEL_GENRE);
            } else if (Consts.getSOURCE() == Consts.SOURCE_AUDIO_BOOK_MP3) {
                fragment = BooksFragment.newInstance(Url.SECTIONS_ABMP3,
                        R.string.menu_genre, Consts.MODEL_GENRE);
            } else if (Consts.getSOURCE() == Consts.SOURCE_ABOOK) {
                fragment = BooksFragment.newInstance(Url.SECTIONS_AKNIGA,
                        R.string.menu_genre, Consts.MODEL_GENRE);
            }

            getViewState().showFragment(Objects.requireNonNull(fragment), "genre");

        } else if (fragmentID == Consts.FRAGMENT_AUTOR) {
            if (Consts.getSOURCE() == Consts.SOURCE_KNIGA_V_UHE) {
                fragment = BooksFragment.newInstance(Url.AUTHORS,
                        R.string.menu_autor, Consts.MODEL_AUTOR);
            } else if (Consts.getSOURCE() == Consts.SOURCE_IZI_BUK) {
                fragment = BooksFragment.newInstance(Url.AUTHORS_IZIBUK,
                        R.string.menu_autor, Consts.MODEL_AUTOR);
            } else if (Consts.getSOURCE() == Consts.SOURCE_AUDIO_BOOK_MP3) {
                fragment = BooksFragment.newInstance(Url.AUTHORS_ABMP3,
                        R.string.menu_autor, Consts.MODEL_AUTOR);
            } else if (Consts.getSOURCE() == Consts.SOURCE_ABOOK) {
                fragment = BooksFragment.newInstance(Url.AUTHORS_AKNIGA,
                        R.string.menu_autor, Consts.MODEL_AUTOR);
            }
            getViewState().showFragment(Objects.requireNonNull(fragment), "autor");

        } else if (fragmentID == Consts.FRAGMENT_ARTIST) {
            if (Consts.getSOURCE() == Consts.SOURCE_KNIGA_V_UHE) {
                fragment = BooksFragment.newInstance(
                        Url.PERFORMERS, R.string.menu_artist, Consts.MODEL_ARTIST);
            } else if (Consts.getSOURCE() == Consts.SOURCE_IZI_BUK) {
                fragment = BooksFragment.newInstance(
                        Url.PERFORMERS_IZIBUK, R.string.menu_artist, Consts.MODEL_ARTIST);
            } else if (Consts.getSOURCE() == Consts.SOURCE_AUDIO_BOOK_MP3) {
                fragment = BooksFragment.newInstance(
                        Url.PERFORMERS_ABMP3, R.string.menu_artist, Consts.MODEL_ARTIST);
            } else if (Consts.getSOURCE() == Consts.SOURCE_ABOOK) {
                fragment = BooksFragment.newInstance(
                        Url.PERFORMERS_AKNIGA, R.string.menu_artist, Consts.MODEL_ARTIST);
            }
            getViewState().showFragment(Objects.requireNonNull(fragment), "artist");

        } else if (fragmentID == Consts.FRAGMENT_FAVORITE) {
            fragment = FavoriteFragment.newInstance(R.string.menu_favorite,
                    Consts.TABLE_FAVORITE);
            getViewState().showFragment(fragment, "favorite");

        } else if (fragmentID == Consts.FRAGMENT_HISTORY) {
            fragment = FavoriteFragment.newInstance(R.string.menu_history,
                    Consts.TABLE_HISTORY);
            getViewState().showFragment(fragment, "history");
        } else if (fragmentID == Consts.LAST_BOOK) {
            if (Consts.getSOURCE() == Consts.SOURCE_KNIGA_V_UHE) {
                fragment = BooksFragment.newInstance(Url.INDEX,
                        R.string.menu_audiobooks, Consts.MODEL_BOOKS);
            } else if (Consts.getSOURCE() == Consts.SOURCE_IZI_BUK) {
                fragment = BooksFragment.newInstance(Url.INDEX_IZIBUK,
                        R.string.menu_audiobooks, Consts.MODEL_BOOKS);
            } else if (Consts.getSOURCE() == Consts.SOURCE_AUDIO_BOOK_MP3) {
                fragment = BooksFragment.newInstance(Url.INDEX_ABMP3,
                        R.string.menu_audiobooks, Consts.MODEL_BOOKS);
            } else if (Consts.getSOURCE() == Consts.SOURCE_ABOOK) {
                fragment = BooksFragment.newInstance(Url.INDEX_AKNIGA,
                        R.string.menu_audiobooks, Consts.MODEL_BOOKS);
            }
            getViewState().showFragment(Objects.requireNonNull(fragment), "audioBook");
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                getViewState().openActivity(
                        new Intent(ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
            } catch (ActivityNotFoundException e) {
                getViewState().setBattaryOptimizeDisenbled(true);
            }
        }
    }


}
