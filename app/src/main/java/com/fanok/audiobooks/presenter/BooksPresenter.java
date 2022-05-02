package com.fanok.audiobooks.presenter;


import static androidx.preference.PreferenceManager.getDefaultSharedPreferences;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import com.arellomobile.mvp.InjectViewState;
import com.arellomobile.mvp.MvpPresenter;
import com.fanok.audiobooks.Consts;
import com.fanok.audiobooks.MyInterstitialAd;
import com.fanok.audiobooks.R;
import com.fanok.audiobooks.Url;
import com.fanok.audiobooks.fragment.BooksFragment;
import com.fanok.audiobooks.interface_pacatge.books.BooksView;
import com.fanok.audiobooks.model.AutorsModel;
import com.fanok.audiobooks.model.BooksDBModel;
import com.fanok.audiobooks.model.BooksModel;
import com.fanok.audiobooks.model.GenreModel;
import com.fanok.audiobooks.pojo.BookPOJO;
import com.fanok.audiobooks.pojo.GenrePOJO;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.squareup.picasso.Picasso;
import io.reactivex.Observer;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import java.util.ArrayList;
import java.util.Objects;

@InjectViewState
public class BooksPresenter extends MvpPresenter<BooksView> implements
        com.fanok.audiobooks.interface_pacatge.books.BooksPresenter {

    private static final String TAG = "BooksPresenter";
    private boolean isLoading = false;
    private boolean isRefreshing = false;
    private int page = 0;
    private final int mModelId;
    private final String mSubTitle;
    private ArrayList<BookPOJO> books;
    private ArrayList<GenrePOJO> genre;
    private com.fanok.audiobooks.interface_pacatge.books.BooksModel mModelBook;
    private com.fanok.audiobooks.interface_pacatge.books.GenreModel mModelGenre;
    private final BooksDBModel mBooksDBModel;
    private final Context mContext;


    private final String mUrl;
    private boolean isEnd;

    public BooksPresenter(@NonNull String url, int modelId, @NonNull String subTitle,
            @NonNull Context context) {
        mUrl = url;
        mModelId = modelId;
        mSubTitle = subTitle;
        isEnd = false;
        mBooksDBModel = new BooksDBModel(context);
        switch (mModelId) {
            case Consts.MODEL_BOOKS:
                books = new ArrayList<>();
                mModelBook = new BooksModel();
                break;
            case Consts.MODEL_GENRE:
                genre = new ArrayList<>();
                mModelGenre = new GenreModel();
                break;
            case Consts.MODEL_AUTOR:
            case Consts.MODEL_ARTIST:
                genre = new ArrayList<>();
                mModelGenre = new AutorsModel();
                break;
        }
        mContext = context;

    }

    @Override
    public void onDestroy() {
        mBooksDBModel.closeDB();
        //mContext = null;
    }

    @Override
    protected void onFirstViewAttach() {
        super.onFirstViewAttach();
        loadBoks();
    }

    @Override
    public void loadBoks() {
        if (!isEnd && !isRefreshing && !isLoading) {
            getViewState().showProgres(true);
            page++;
            if (mModelId != Consts.MODEL_GENRE) {
                if ((!mUrl.contains("genre") || mUrl.contains("izib.uk") || mUrl.contains("audiobook-mp3.com"))
                        && !mUrl.contains("akniga.org")) {
                    getData(mUrl + page + "/");
                } else {
                    getData(mUrl.replace("<page>", Integer.toString(page)));
                }
            } else {
                getData(mUrl);
            }
        }

    }

    @Override
    public void onBookItemLongClick(View view, int position, LayoutInflater layoutInflater) {

        @SuppressLint("InflateParams") View layout = layoutInflater.inflate(
                R.layout.bootom_sheet_books_menu, null);
        final BottomSheetDialog dialog = new BottomSheetDialog(view.getContext());
        dialog.setContentView(layout);

        TextView open = layout.findViewById(R.id.open);
        TextView add = layout.findViewById(R.id.addFavorite);
        TextView remove = layout.findViewById(R.id.removeFavorite);
        TextView genre = layout.findViewById(R.id.genre);
        TextView author = layout.findViewById(R.id.author);
        TextView artist = layout.findViewById(R.id.artist);
        TextView series = layout.findViewById(R.id.series);

        if (books.get(position).getPhoto() != null) {
            ImageView imageView = layout.findViewById(R.id.imageView);
            Picasso.get()
                    .load(books.get(position).getPhoto())
                    .error(R.drawable.image_placeholder)
                    .placeholder(R.drawable.image_placeholder)
                    .into(imageView);
        }

        TextView title = layout.findViewById(R.id.title);
        title.setText(books.get(position).getName());

        TextView authorName = layout.findViewById(R.id.authorName);
        if (books.get(position).getAutor() != null) {
            authorName.setText(books.get(position).getAutor());
            authorName.setVisibility(View.VISIBLE);
        } else {
            authorName.setVisibility(View.GONE);
        }

        if (books.get(position).getSeries() == null || books.get(position).getUrlSeries() == null) {
            series.setVisibility(View.GONE);
        } else {
            series.setVisibility(View.VISIBLE);
        }

        if (mBooksDBModel.inFavorite(books.get(position))) {
            add.setVisibility(View.GONE);
            remove.setVisibility(View.VISIBLE);
        } else {
            add.setVisibility(View.VISIBLE);
            remove.setVisibility(View.GONE);
        }

        open.setOnClickListener(view1 -> {
            dialog.dismiss();
            MyInterstitialAd.increase();
            getViewState().showBooksActivity(books.get(position));
        });

        add.setOnClickListener(view1 -> {
            dialog.dismiss();
            mBooksDBModel.addFavorite(books.get(position));
        });

        remove.setOnClickListener(view1 -> {
            dialog.dismiss();
            mBooksDBModel.removeFavorite(books.get(position));
        });

        if (books.get(position).getUrlGenre() != null) {
            genre.setVisibility(View.VISIBLE);
            genre.setOnClickListener(view1 -> {
                dialog.dismiss();
                getViewState().showFragment(BooksFragment.newInstance(
                        books.get(position).getUrlGenre(),
                        R.string.menu_audiobooks,
                        books.get(position).getGenre(), Consts.MODEL_BOOKS),
                        "genreBooks");
            });
        } else {
            genre.setVisibility(View.GONE);
        }

        if (books.get(position).getUrlAutor() != null) {
            author.setVisibility(View.VISIBLE);
            author.setOnClickListener(view1 -> {
                dialog.dismiss();
                if (!books.get(position).getUrlAutor().isEmpty()) {
                    getViewState().showFragment(BooksFragment.newInstance(
                            books.get(position).getUrlAutor(),
                            R.string.menu_audiobooks,
                            books.get(position).getAutor(), Consts.MODEL_BOOKS),
                            "autorBooks");
                }
            });
        } else {
            author.setVisibility(View.GONE);
        }

        if (books.get(position).getUrlArtist() != null) {
            artist.setVisibility(View.VISIBLE);
            artist.setOnClickListener(view1 -> {
                dialog.dismiss();
                getViewState().showFragment(BooksFragment.newInstance(
                        books.get(position).getUrlArtist(),
                        R.string.menu_audiobooks,
                        books.get(position).getArtist(), Consts.MODEL_BOOKS),
                        "artistBooks");
            });
        } else {
            artist.setVisibility(View.GONE);
        }

        series.setOnClickListener(view12 -> {
            dialog.dismiss();
            getViewState().showFragment(BooksFragment.newInstance(
                    books.get(position).getUrlSeries(),
                    R.string.menu_audiobooks,
                    books.get(position).getSeries(), Consts.MODEL_BOOKS),
                    "seriesBooks");
        });
        dialog.show();
    }

    @SuppressLint("ApplySharedPref")
    @Override
    public void onOptionItemSelected(int itemId) {
        String subTitle = mSubTitle.replace(" " + getStringById(R.string.order_new), "");
        subTitle = subTitle.replace(" " + getStringById(R.string.order_reting), "");
        subTitle = subTitle.replace(" " + getStringById(R.string.order_popular), "");
        String url = "";

        if (itemId == R.id.source_izi_book || itemId == R.id.source_kniga_v_uhe
                || itemId == R.id.source_audio_book_mp3 || itemId == R.id.source_abook) {
            SharedPreferences pref = getDefaultSharedPreferences(Objects.requireNonNull(mContext));
            SharedPreferences.Editor editor = pref.edit();
            if (itemId == R.id.source_kniga_v_uhe) {
                editor.putString("sorce_books", getStringById(R.string.kniga_v_uhe_value));
            } else if (itemId == R.id.source_izi_book) {
                editor.putString("sorce_books", getStringById(R.string.izibuc_value));
            } else if (itemId == R.id.source_audio_book_mp3) {
                editor.putString("sorce_books", getStringById(R.string.audiobook_mp3_value));
            } else if (itemId == R.id.source_abook) {
                editor.putString("sorce_books", getStringById(R.string.abook_value));
            }
            editor.commit();
            getViewState().recreate();
        }

        if (mUrl.contains("knigavuhe.org")) {
            if (!mUrl.contains("genre")) {
                switch (itemId) {
                    case R.id.new_data:
                        url = Url.NEW_BOOK;
                        break;
                    case R.id.reting_all_time:
                        url = Url.RATING_ALL_TIME;
                        break;
                    case R.id.reting_month:
                        url = Url.RATING_MONTH;
                        break;
                    case R.id.reting_week:
                        url = Url.RATING_WEEK;
                        break;
                    case R.id.reting_day:
                        url = Url.RATING_TODATY;
                        break;
                    case R.id.popular_all_time:
                        url = Url.BEST_ALL_TIME;
                        break;
                    case R.id.popular_month:
                        url = Url.BEST_MONTH;
                        break;
                    case R.id.popular_week:
                        url = Url.BEST_WEEK;
                        break;
                    case R.id.popular_day:
                        url = Url.BEST_TODAY;
                        break;
                }
            } else {
                url = mUrl.substring(0, Consts.indexOfByNumber(mUrl, '/', 5) + 1);
                switch (itemId) {
                    case R.id.new_data:
                        url = url + "<page>/";
                        break;
                    case R.id.reting_all_time:
                        url = url + "rating/<page>/?period=alltime";
                        break;
                    case R.id.reting_month:
                        url = url + "rating/<page>/?period=month";
                        break;
                    case R.id.reting_week:
                        url = url + "rating/<page>/?period=week";
                        break;
                    case R.id.reting_day:
                        url = url + "rating/<page>/?period=today";
                        break;
                    case R.id.popular_all_time:
                        url = url + "popular/<page>/?period=alltime";
                        break;
                    case R.id.popular_month:
                        url = url + "popular/<page>/?period=month";
                        break;
                    case R.id.popular_week:
                        url = url + "popular/<page>/?period=week";
                        break;
                    case R.id.popular_day:
                        url = url + "popular/<page>/?period=today";
                        break;
                }
            }
        } else if (mUrl.contains("izib.uk")) {
            if (itemId == R.id.order) {
                Consts.izibuk_reiting = !Consts.izibuk_reiting;
                if (Consts.izibuk_reiting) {
                    getViewState().showFragment(BooksFragment.newInstance(
                            Url.INDEX_IZIBUK,
                            R.string.menu_audiobooks,
                            subTitle + " " + getStringById(R.string.order_popular),
                            Consts.MODEL_BOOKS),
                            "audioBooksOrederDiscussedAllTime");
                } else {
                    getViewState().showFragment(BooksFragment.newInstance(
                            Url.INDEX_IZIBUK, R.string.menu_audiobooks,
                            subTitle + " " + getStringById(R.string.order_new), Consts.MODEL_BOOKS),
                            "audioBooksOrederNew");
                }
            }
        } else if (mUrl.contains("audiobook-mp3.com")) {
            if (itemId == R.id.order) {
                Consts.izibuk_reiting = !Consts.izibuk_reiting;
                if (Consts.izibuk_reiting) {
                    getViewState().showFragment(BooksFragment.newInstance(
                            Url.SERVER_ABMP3 + "/top?page=",
                            R.string.menu_audiobooks,
                            subTitle + " " + getStringById(R.string.order_popular),
                            Consts.MODEL_BOOKS),
                            "audioBooksOrederDiscussedAllTime");
                } else {
                    getViewState().showFragment(BooksFragment.newInstance(
                            Url.INDEX_ABMP3, R.string.menu_audiobooks,
                            subTitle + " " + getStringById(R.string.order_new), Consts.MODEL_BOOKS),
                            "audioBooksOrederNew");
                }
            }
        } else if (mUrl.contains("akniga.org")) {
            if (!mUrl.contains("section")) {
                switch (itemId) {
                    case R.id.new_data:
                        url = Url.NEW_BOOK_AKNIGA;
                        break;
                    case R.id.reting_all_time:
                        url = Url.RATING_ALL_TIME_AKNIGA;
                        break;
                    case R.id.reting_month:
                        url = Url.RATING_MONTH_AKNIGA;
                        break;
                    case R.id.reting_week:
                        url = Url.RATING_WEEK_AKNIGA;
                        break;
                    case R.id.reting_day:
                        url = Url.RATING_TODATY_AKNIGA;
                        break;
                    case R.id.popular_all_time:
                        url = Url.BEST_ALL_TIME_AKNIGA;
                        break;
                    case R.id.popular_month:
                        url = Url.BEST_MONTH_AKNIGA;
                        break;
                    case R.id.popular_week:
                        url = Url.BEST_WEEK_AKNIGA;
                        break;
                    case R.id.popular_day:
                        url = Url.BEST_TODAY_AKNIGA;
                        break;
                }
            } else {
                url = mUrl.substring(0, Consts.indexOfByNumber(mUrl, '/', 5) + 1);
                switch (itemId) {
                    case R.id.new_data:
                        url += "page<page>/";
                        break;
                    case R.id.reting_all_time:
                        url += "top/page<page>/?period=all";
                        break;
                    case R.id.reting_month:
                        url += "top/page<page>/?period=30";
                        break;
                    case R.id.reting_week:
                        url += "top/page<page>/?period=7";
                        break;
                    case R.id.reting_day:
                        url += "top/page<page>/?period=1";
                        break;
                    case R.id.popular_all_time:
                        url += "discussed/page<page>/?period=all";
                        break;
                    case R.id.popular_month:
                        url += "discussed/page<page>/?period=30";
                        break;
                    case R.id.popular_week:
                        url += "discussed/page<page>/?period=7";
                        break;
                    case R.id.popular_day:
                        url += "discussed/page<page>/?period=1";
                        break;
                }
            }
        }

        switch (itemId) {
            case R.id.app_bar_search:
                if (mUrl.contains("audiobook-mp3.com")) {
                    if (mModelId == Consts.MODEL_AUTOR) {
                        getViewState().showSearchActivity(Consts.MODEL_AUTOR);
                    } else {
                        getViewState().showSearchActivity(Consts.MODEL_BOOKS);
                    }
                } else {
                    getViewState().showSearchActivity(mModelId);
                }
                break;
            case R.id.new_data:
                getViewState().showFragment(BooksFragment.newInstance(
                        url, R.string.menu_audiobooks,
                        subTitle + " " + getStringById(R.string.order_new), Consts.MODEL_BOOKS),
                        "audioBooksOrederNew");
                break;
            case R.id.reting_all_time:
                getViewState().showFragment(BooksFragment.newInstance(
                        url,
                        R.string.menu_audiobooks,
                        subTitle + " " + getStringById(R.string.order_reting), Consts.MODEL_BOOKS),
                        "audioBooksOrederBestAllTime");
                break;
            case R.id.reting_month:
                getViewState().showFragment(BooksFragment.newInstance(
                        url,
                        R.string.menu_audiobooks,
                        subTitle + " " + getStringById(R.string.order_reting), Consts.MODEL_BOOKS),
                        "audioBooksOrederBestMonth");
                break;
            case R.id.reting_week:
                getViewState().showFragment(BooksFragment.newInstance(
                        url,
                        R.string.menu_audiobooks,
                        subTitle + " " + getStringById(R.string.order_reting), Consts.MODEL_BOOKS),
                        "audioBooksOrederBestWeek");
                break;
            case R.id.reting_day:
                getViewState().showFragment(BooksFragment.newInstance(
                        url,
                        R.string.menu_audiobooks,
                        subTitle + " " + getStringById(R.string.order_reting), Consts.MODEL_BOOKS),
                        "audioBooksOrederBestDay");
                break;
            case R.id.popular_all_time:
                getViewState().showFragment(BooksFragment.newInstance(
                        url,
                        R.string.menu_audiobooks,
                        subTitle + " " + getStringById(R.string.order_popular),
                        Consts.MODEL_BOOKS),
                        "audioBooksOrederDiscussedAllTime");
                break;
            case R.id.popular_month:
                getViewState().showFragment(BooksFragment.newInstance(
                        url,
                        R.string.menu_audiobooks,
                        subTitle + " " + getStringById(R.string.order_popular),
                        Consts.MODEL_BOOKS),
                        "audioBooksOrederDiscussedMonth");
                break;
            case R.id.popular_week:
                getViewState().showFragment(BooksFragment.newInstance(
                        url,
                        R.string.menu_audiobooks,
                        subTitle + " " + getStringById(R.string.order_popular),
                        Consts.MODEL_BOOKS),
                        "audioBooksOrederDiscussedWeek");
                break;
            case R.id.popular_day:
                getViewState().showFragment(BooksFragment.newInstance(
                        url,
                        R.string.menu_audiobooks,
                        subTitle + " " + getStringById(R.string.order_popular),
                        Consts.MODEL_BOOKS),
                        "audioBooksOrederDiscussedDay");
                break;
        }
    }


    @Override
    public void onGenreItemClick(View view, int position) {
        String tag;
        switch (mModelId) {
            case Consts.MODEL_GENRE:
                tag = "genreBooks";
                break;
            case Consts.MODEL_AUTOR:
                tag = "autorBooks";
                break;
            case Consts.MODEL_ARTIST:
                tag = "artistBooks";
                break;
            default:
                return;
        }

        if (genre != null && genre.size() - 1 >= position) {
            String url = genre.get(position).getUrl();

            if (url.contains("genre") && mUrl.contains("knigavuhe.org")) url += "<page>/";
            getViewState().showFragment(BooksFragment.newInstance(
                    url,
                    R.string.menu_audiobooks,
                    genre.get(position).getName(), Consts.MODEL_BOOKS), tag);
        }
    }

    @Override
    public void onBookItemClick(View view, int position) {
        getViewState().showBooksActivity(books.get(position));
    }

    @Override
    public void onRefresh() {
        if (!isLoading) {
            isRefreshing = true;
            isEnd = false;
            getViewState().showRefreshing(true);
            page = 1;
            if (mModelId != Consts.MODEL_GENRE) {
                if ((!mUrl.contains("genre") || mUrl.contains("izib.uk") || mUrl.contains("audiobook-mp3.com"))
                        && !mUrl.contains("akniga.org")) {
                    getData(mUrl + page + "/");
                } else {
                    getData(mUrl.replace("<page>", Integer.toString(page)));
                }
            } else {
                getData(mUrl);
            }
        } else {
            getViewState().showRefreshing(false);
        }

    }


    private void getData(String url) {
        if (!isLoading) {
            isLoading = true;
            if (mModelId == Consts.MODEL_BOOKS) {
                mModelBook.getBooks(url, page)
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(new Observer<ArrayList<BookPOJO>>() {
                            @Override
                            public void onSubscribe(Disposable d) {
                            }

                            @Override
                            public void onNext(ArrayList<BookPOJO> bookPOJOS) {
                                if (isRefreshing) {
                                    books.clear();
                                }
                                books.addAll(bookPOJOS);
                                if (isRefreshing) {
                                    isRefreshing = false;
                                    getViewState().showData(books);
                                    getViewState().setPosition(0);
                                }
                            }

                            @Override
                            public void onError(Throwable e) {
                                if (e.getClass() == NullPointerException.class) {
                                    isEnd = true;
                                } else {
                                    getViewState().showToast(R.string.error_load_data);
                                    page--;
                                }
                                onComplete();

                            }

                            @Override
                            public void onComplete() {
                                Log.d(TAG, "onComplete");
                                getViewState().showData(books);
                                getViewState().showProgres(false);
                                getViewState().showRefreshing(false);
                                isLoading = false;
                            }
                        });
            } else {
                mModelGenre.getBooks(url, page)
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(new Observer<ArrayList<GenrePOJO>>() {
                            @Override
                            public void onSubscribe(Disposable d) {
                            }

                            @Override
                            public void onNext(ArrayList<GenrePOJO> bookPOJOS) {
                                if (isRefreshing) {
                                    genre.clear();
                                }
                                genre.addAll(bookPOJOS);
                                if (isRefreshing) {
                                    isRefreshing = false;
                                    getViewState().showData(genre);
                                    getViewState().setPosition(0);
                                }
                            }

                            @Override
                            public void onError(Throwable e) {
                                if (e.getClass() == NullPointerException.class) {
                                    isEnd = true;
                                } else {
                                    getViewState().showToast(R.string.error_load_data);
                                    page--;
                                }
                                onComplete();

                            }

                            @Override
                            public void onComplete() {
                                Log.d(TAG, "onComplete");
                                getViewState().showData(genre);
                                getViewState().showProgres(false);
                                getViewState().showRefreshing(false);
                                isLoading = false;
                            }
                        });
            }
        }
    }

    private String getStringById(int id) {
        return mContext.getString(id);
    }

    @Override
    public void onActivityResult(@NonNull Intent intent) {
        String url = intent.getStringExtra("url");
        String name = intent.getStringExtra("name");
        int modelId = intent.getIntExtra("modelId", 0);
        String tag = intent.getStringExtra("tag");

        if (url != null) {
            Fragment fragment = BooksFragment.newInstance(url,
                    R.string.menu_audiobooks, name, modelId);
            getViewState().showFragment(fragment, tag);
        }

    }
}
