package com.fanok.audiobooks.presenter;


import android.content.Context;
import android.content.Intent;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.support.v7.widget.PopupMenu;
import android.util.Log;
import android.view.Gravity;
import android.view.View;

import com.arellomobile.mvp.InjectViewState;
import com.arellomobile.mvp.MvpPresenter;
import com.fanok.audiobooks.Consts;
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

import java.util.ArrayList;

import io.reactivex.Observer;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

@InjectViewState
public class BooksPresenter extends MvpPresenter<BooksView> implements
        com.fanok.audiobooks.interface_pacatge.books.BooksPresenter {

    private static final String TAG = "BooksPresenter";
    private boolean isLoading = false;
    private boolean isRefreshing = false;
    private int page = 0;
    private int mModelId;
    private String mSubTitle;
    private ArrayList<BookPOJO> books;
    private ArrayList<GenrePOJO> genre;
    private com.fanok.audiobooks.interface_pacatge.books.BooksModel mModelBook;
    private com.fanok.audiobooks.interface_pacatge.books.GenreModel mModelGenre;
    private BooksDBModel mBooksDBModel;

    private Context mContext;


    private String mUrl;
    private boolean isEnd;


    @Override
    public void onCreate(@NonNull String url, int modelId, @NonNull String subTitle,
            Context context) {
        mUrl = url;
        mModelId = modelId;
        mSubTitle = subTitle;
        isEnd = false;
        mContext = context;
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
    }

    @Override
    public void onDestroy() {

    }

    @Override
    public void loadBoks() {
        if (!isEnd && !isRefreshing && !isLoading) {
            getViewState().showProgres(true);
            page++;
            if (mModelId != Consts.MODEL_GENRE) {
                if (!mUrl.contains("genre")) {
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
    public void onRefresh() {
        if (!isLoading) {
            isRefreshing = true;
            isEnd = false;
            getViewState().showRefreshing(true);
            page = 1;
            if (mModelId != Consts.MODEL_GENRE) {
                getData(mUrl + page + "/");
            } else {
                getData(mUrl);
            }
        } else {
            getViewState().showRefreshing(false);
        }

    }


    @Override
    public void onOptionItemSelected(int itemId) {
        String subTitle = mSubTitle.replace(" " + getStringById(R.string.order_new), "");
        subTitle = subTitle.replace(" " + getStringById(R.string.order_reting), "");
        subTitle = subTitle.replace(" " + getStringById(R.string.order_popular), "");
        String url = "";
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


        switch (itemId) {
            case R.id.app_bar_search:
                getViewState().showSearchActivity(mModelId);
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
                        "audioBooksOrederBestWeek");
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
                        "audioBooksOrederDiscussedWeek");
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
            if (url.contains("genre")) url += "<page>/";
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
    public void onBookItemLongClick(View view, int position) {
        PopupMenu popupMenu = new PopupMenu(view.getContext(), view, Gravity.END);
        popupMenu.inflate(R.menu.popup_books_item_menu);

        if (books.get(position).getSeries() == null || books.get(position).getUrlSeries() == null) {
            popupMenu.getMenu().findItem(R.id.series).setVisible(false);
        } else {
            popupMenu.getMenu().findItem(R.id.series).setVisible(true);
        }

        if (mBooksDBModel.inFavorite(books.get(position))) {
            popupMenu.getMenu().findItem(R.id.addFavorite).setVisible(false);
            popupMenu.getMenu().findItem(R.id.removeFavorite).setVisible(true);
        } else {
            popupMenu.getMenu().findItem(R.id.addFavorite).setVisible(true);
            popupMenu.getMenu().findItem(R.id.removeFavorite).setVisible(false);
        }

        popupMenu.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case R.id.open:
                    getViewState().showBooksActivity(books.get(position));
                    return true;
                case R.id.addFavorite:
                    mBooksDBModel.addFavorite(books.get(position));
                    return true;
                case R.id.removeFavorite:
                    mBooksDBModel.removeFavorite(books.get(position));
                    return true;
                case R.id.genre:
                    getViewState().showFragment(BooksFragment.newInstance(
                            books.get(position).getUrlGenre(),
                            R.string.menu_audiobooks,
                            books.get(position).getGenre(), Consts.MODEL_BOOKS),
                            "genreBooks");
                    return true;
                case R.id.author:
                    if (!books.get(position).getUrlAutor().isEmpty()) {
                        getViewState().showFragment(BooksFragment.newInstance(
                                books.get(position).getUrlAutor(),
                                R.string.menu_audiobooks,
                                books.get(position).getAutor(), Consts.MODEL_BOOKS),
                                "autorBooks");
                    }
                    return true;
                case R.id.artist:
                    getViewState().showFragment(BooksFragment.newInstance(
                            books.get(position).getUrlArtist(),
                            R.string.menu_audiobooks,
                            books.get(position).getArtist(), Consts.MODEL_BOOKS),
                            "artistBooks");
                    return true;
                case R.id.series:
                    getViewState().showFragment(BooksFragment.newInstance(
                            books.get(position).getUrlSeries() + "?page=",
                            R.string.menu_audiobooks,
                            books.get(position).getSeries(), Consts.MODEL_BOOKS),
                            "seriesBooks");
                    return true;
                default:
                    return false;
            }
        });
        popupMenu.show();
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
                                if (isRefreshing) getViewState().setPosition(0);
                                getViewState().showProgres(false);
                                getViewState().showRefreshing(false);
                                isLoading = false;
                                isRefreshing = false;
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
                                if (isRefreshing) getViewState().setPosition(0);
                                getViewState().showProgres(false);
                                getViewState().showRefreshing(false);
                                isLoading = false;
                                isRefreshing = false;
                            }
                        });
            }
        }
    }

    private String getStringById(int id) {
        return mContext.getResources().getString(id);
    }

    @Override
    public void onActivityResult(@NonNull Intent intent) {
        String url = intent.getStringExtra("url");
        String name = intent.getStringExtra("name");
        int modelId = intent.getIntExtra("modelId", 0);
        String tag = intent.getStringExtra("tag");

        Fragment fragment = BooksFragment.newInstance(url,
                R.string.menu_audiobooks, name, modelId);
        getViewState().showFragment(fragment, tag);
    }
}
