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
import com.fanok.audiobooks.CookesExeption;
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
import org.jetbrains.annotations.NotNull;

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
                if (mUrl.contains(Url.SERVER_KNIGOBLUD)){
                    getData(mUrl + "/" +page);
                } else if ((!mUrl.contains("genre") || mUrl.contains(Url.SERVER_IZIBUK)
                        || mUrl.contains(Url.SERVER_ABMP3)
                        || mUrl.contains(Url.SERVER_BAZA_KNIG)
                        || mUrl.contains(Url.SERVER_BOOKOOF))
                        && !mUrl.contains(Url.SERVER_AKNIGA)) {
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
                    .error(android.R.drawable.ic_menu_gallery)
                    .placeholder(android.R.drawable.ic_menu_gallery)
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
        subTitle = subTitle.replace(" " + getStringById(R.string.order_coments), "");
        subTitle = subTitle.replace(" " + getStringById(R.string.order_year), "");
        String url = "";

        if (itemId == R.id.source_izi_book || itemId == R.id.source_kniga_v_uhe
                || itemId == R.id.source_audio_book_mp3 || itemId == R.id.source_abook
                || itemId == R.id.source_baza_knig||itemId == R.id.source_knigoblud || itemId == R.id.source_bookoof) {
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
            } else if (itemId == R.id.source_baza_knig) {
                editor.putString("sorce_books", getStringById(R.string.baza_knig_value));
            } else if (itemId == R.id.source_knigoblud){
                editor.putString("sorce_books", getStringById(R.string.knigoblud_value));
            }else if (itemId == R.id.source_bookoof){
                editor.putString("sorce_books", getStringById(R.string.bookoof_value));
            }
            editor.commit();
            getViewState().recreate();
        }

        if (mUrl.contains(Url.SERVER)) {
            if (!mUrl.contains("genre")) {
                if (itemId == R.id.new_data) {
                    url = Url.NEW_BOOK;
                } else if (itemId == R.id.reting_all_time) {
                    url = Url.RATING_ALL_TIME;
                } else if (itemId == R.id.reting_month) {
                    url = Url.RATING_MONTH;
                } else if (itemId == R.id.reting_week) {
                    url = Url.RATING_WEEK;
                } else if (itemId == R.id.reting_day) {
                    url = Url.RATING_TODATY;
                } else if (itemId == R.id.popular_all_time) {
                    url = Url.BEST_ALL_TIME;
                } else if (itemId == R.id.popular_month) {
                    url = Url.BEST_MONTH;
                } else if (itemId == R.id.popular_week) {
                    url = Url.BEST_WEEK;
                } else if (itemId == R.id.popular_day) {
                    url = Url.BEST_TODAY;
                }
            } else {
                url = mUrl.substring(0, Consts.indexOfByNumber(mUrl, '/', 5) + 1);
                if (itemId == R.id.new_data) {
                    url = url + "<page>/";
                } else if (itemId == R.id.reting_all_time) {
                    url = url + "rating/<page>/?period=alltime";
                } else if (itemId == R.id.reting_month) {
                    url = url + "rating/<page>/?period=month";
                } else if (itemId == R.id.reting_week) {
                    url = url + "rating/<page>/?period=week";
                } else if (itemId == R.id.reting_day) {
                    url = url + "rating/<page>/?period=today";
                } else if (itemId == R.id.popular_all_time) {
                    url = url + "popular/<page>/?period=alltime";
                } else if (itemId == R.id.popular_month) {
                    url = url + "popular/<page>/?period=month";
                } else if (itemId == R.id.popular_week) {
                    url = url + "popular/<page>/?period=week";
                } else if (itemId == R.id.popular_day) {
                    url = url + "popular/<page>/?period=today";
                }
            }
        } else if (mUrl.contains(Url.SERVER_IZIBUK)) {
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
        } else if (mUrl.contains(Url.SERVER_ABMP3)) {
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
        } else if (mUrl.contains(Url.SERVER_AKNIGA)) {
            if (!mUrl.contains("section")) {
                if (itemId == R.id.new_data) {
                    url = Url.NEW_BOOK_AKNIGA;
                } else if (itemId == R.id.reting_all_time) {
                    url = Url.RATING_ALL_TIME_AKNIGA;
                } else if (itemId == R.id.reting_month) {
                    url = Url.RATING_MONTH_AKNIGA;
                } else if (itemId == R.id.reting_week) {
                    url = Url.RATING_WEEK_AKNIGA;
                } else if (itemId == R.id.reting_day) {
                    url = Url.RATING_TODATY_AKNIGA;
                } else if (itemId == R.id.popular_all_time) {
                    url = Url.BEST_ALL_TIME_AKNIGA;
                } else if (itemId == R.id.popular_month) {
                    url = Url.BEST_MONTH_AKNIGA;
                } else if (itemId == R.id.popular_week) {
                    url = Url.BEST_WEEK_AKNIGA;
                } else if (itemId == R.id.popular_day) {
                    url = Url.BEST_TODAY_AKNIGA;
                }
            } else {
                url = mUrl.substring(0, Consts.indexOfByNumber(mUrl, '/', 5) + 1);
                if (itemId == R.id.new_data) {
                    url += "page<page>/";
                } else if (itemId == R.id.reting_all_time) {
                    url += "top/page<page>/?period=all";
                } else if (itemId == R.id.reting_month) {
                    url += "top/page<page>/?period=30";
                } else if (itemId == R.id.reting_week) {
                    url += "top/page<page>/?period=7";
                } else if (itemId == R.id.reting_day) {
                    url += "top/page<page>/?period=1";
                } else if (itemId == R.id.popular_all_time) {
                    url += "discussed/page<page>/?period=all";
                } else if (itemId == R.id.popular_month) {
                    url += "discussed/page<page>/?period=30";
                } else if (itemId == R.id.popular_week) {
                    url += "discussed/page<page>/?period=7";
                } else if (itemId == R.id.popular_day) {
                    url += "discussed/page<page>/?period=1";
                }
            }
        } else if (mUrl.contains(Url.SERVER_BAZA_KNIG)) {
            if (itemId == R.id.new_data) {
                url = Url.NEW_BOOK_BAZA_KNIG;
            } else if (itemId == R.id.reting) {
                url = Url.RATING_BAZA_KNIG;
            } else if (itemId == R.id.popular) {
                url = Url.BEST_BAZA_KNIG;
            } else if (itemId == R.id.coments) {
                url = Url.COMENTS_BAZA_KNIG;
            } else if (itemId == R.id.years) {
                url = Url.YEARS_BAZA_KNIG;
            }
        }   else if (mUrl.contains(Url.SERVER_BOOKOOF)) {
            url = Url.INDEX_BOOKOOF;
        }

        if (itemId == R.id.app_bar_search) {
            if (mUrl.contains(Url.SERVER_ABMP3)) {
                if (mModelId == Consts.MODEL_AUTOR) {
                    getViewState().showSearchActivity(Consts.MODEL_AUTOR);
                } else {
                    getViewState().showSearchActivity(Consts.MODEL_BOOKS);
                }
            } else {
                getViewState().showSearchActivity(mModelId);
            }
        } else if (itemId == R.id.new_data) {
            getViewState().showFragment(BooksFragment.newInstance(
                            url, R.string.menu_audiobooks,
                            subTitle + " " + getStringById(R.string.order_new), Consts.MODEL_BOOKS),
                    "audioBooksOrederNew");
        } else if (itemId == R.id.reting_all_time || itemId == R.id.reting) {
            getViewState().showFragment(BooksFragment.newInstance(
                            url,
                            R.string.menu_audiobooks,
                            subTitle + " " + getStringById(R.string.order_reting), Consts.MODEL_BOOKS),
                    "audioBooksOrederBestAllTime");
        } else if (itemId == R.id.reting_month) {
            getViewState().showFragment(BooksFragment.newInstance(
                            url,
                            R.string.menu_audiobooks,
                            subTitle + " " + getStringById(R.string.order_reting), Consts.MODEL_BOOKS),
                    "audioBooksOrederBestMonth");
        } else if (itemId == R.id.reting_week) {
            getViewState().showFragment(BooksFragment.newInstance(
                            url,
                            R.string.menu_audiobooks,
                            subTitle + " " + getStringById(R.string.order_reting), Consts.MODEL_BOOKS),
                    "audioBooksOrederBestWeek");
        } else if (itemId == R.id.reting_day) {
            getViewState().showFragment(BooksFragment.newInstance(
                            url,
                            R.string.menu_audiobooks,
                            subTitle + " " + getStringById(R.string.order_reting), Consts.MODEL_BOOKS),
                    "audioBooksOrederBestDay");
        } else if (itemId == R.id.popular_all_time || itemId == R.id.popular) {
            getViewState().showFragment(BooksFragment.newInstance(
                            url,
                            R.string.menu_audiobooks,
                            subTitle + " " + getStringById(R.string.order_popular),
                            Consts.MODEL_BOOKS),
                    "audioBooksOrederDiscussedAllTime");
        } else if (itemId == R.id.popular_month) {
            getViewState().showFragment(BooksFragment.newInstance(
                            url,
                            R.string.menu_audiobooks,
                            subTitle + " " + getStringById(R.string.order_popular),
                            Consts.MODEL_BOOKS),
                    "audioBooksOrederDiscussedMonth");
        } else if (itemId == R.id.popular_week) {
            getViewState().showFragment(BooksFragment.newInstance(
                            url,
                            R.string.menu_audiobooks,
                            subTitle + " " + getStringById(R.string.order_popular),
                            Consts.MODEL_BOOKS),
                    "audioBooksOrederDiscussedWeek");
        } else if (itemId == R.id.popular_day) {
            getViewState().showFragment(BooksFragment.newInstance(
                            url,
                            R.string.menu_audiobooks,
                            subTitle + " " + getStringById(R.string.order_popular),
                            Consts.MODEL_BOOKS),
                    "audioBooksOrederDiscussedDay");
        } else if (itemId == R.id.coments) {
            getViewState().showFragment(BooksFragment.newInstance(
                            url,
                            R.string.menu_audiobooks,
                            subTitle + " " + getStringById(R.string.order_coments),
                            Consts.MODEL_BOOKS),
                    "audioBooksOrederComents");
        } else if (itemId == R.id.years) {
            getViewState().showFragment(BooksFragment.newInstance(
                            url,
                            R.string.menu_audiobooks,
                            subTitle + " " + getStringById(R.string.order_year),
                            Consts.MODEL_BOOKS),
                    "audioBooksOrederYears");
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
                if (mUrl.contains(Url.SERVER_KNIGOBLUD)) {
                    getData(mUrl + "/" +page);
                } else if ((!mUrl.contains("genre") || mUrl.contains(Url.SERVER_IZIBUK) || mUrl.contains(Url.SERVER_ABMP3)
                        || mUrl.contains(Url.SERVER_BAZA_KNIG)
                        || mUrl.contains(Url.SERVER_BOOKOOF))
                        && !mUrl.contains(Url.SERVER_AKNIGA)) {
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
                mModelBook.getBooks(url, page, mSubTitle.trim(), mContext.getApplicationContext())
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(new Observer<ArrayList<BookPOJO>>() {
                            @Override
                            public void onError(@NotNull Throwable e) {
                                if (e.getClass() == CookesExeption.class) {
                                    if (Objects.requireNonNull(e.getMessage()).contains(Url.SERVER_BAZA_KNIG)) {
                                        getViewState().showToast(R.string.cookes_baza_knig_exeption);
                                    }
                                } else if (e.getClass() == NullPointerException.class) {
                                    isEnd = true;
                                } else {
                                    getViewState().showToast(R.string.error_load_data);
                                    page--;
                                }
                                onComplete();

                            }

                            @Override
                            public void onNext(@NotNull ArrayList<BookPOJO> bookPOJOS) {
                                if (isRefreshing) {
                                    books.clear();
                                }
                                books.addAll(bookPOJOS);
                                if (isRefreshing) {
                                    isRefreshing = false;
                                    getViewState().showDataBooks(books);
                                    getViewState().setPosition(0);
                                }
                            }

                            @Override
                            public void onSubscribe(@NotNull Disposable d) {
                            }

                            @Override
                            public void onComplete() {
                                Log.d(TAG, "onComplete");
                                getViewState().showDataBooks(books);
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
                            public void onError(@NotNull Throwable e) {
                                if (e.getClass() == CookesExeption.class) {
                                    if (Objects.requireNonNull(e.getMessage()).contains(Url.SERVER_BAZA_KNIG)) {
                                        getViewState().showToast(R.string.cookes_baza_knig_exeption);
                                    }
                                } else if (e.getClass() == NullPointerException.class) {
                                    isEnd = true;
                                } else {
                                    getViewState().showToast(R.string.error_load_data);
                                    page--;
                                }
                                onComplete();

                            }

                            @Override
                            public void onNext(@NotNull ArrayList<GenrePOJO> bookPOJOS) {
                                if (isRefreshing) {
                                    genre.clear();
                                }
                                genre.addAll(bookPOJOS);
                                if (isRefreshing) {
                                    isRefreshing = false;
                                    getViewState().showDataGenres(genre);
                                    getViewState().setPosition(0);
                                }
                            }

                            @Override
                            public void onSubscribe(@NotNull Disposable d) {
                            }

                            @Override
                            public void onComplete() {
                                Log.d(TAG, "onComplete");
                                getViewState().showDataGenres(genre);
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
