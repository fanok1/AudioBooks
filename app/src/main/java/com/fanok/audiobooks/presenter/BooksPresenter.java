package com.fanok.audiobooks.presenter;


import android.support.annotation.NonNull;
import android.util.Log;
import android.view.View;

import com.arellomobile.mvp.InjectViewState;
import com.arellomobile.mvp.MvpPresenter;
import com.fanok.audiobooks.Consts;
import com.fanok.audiobooks.R;
import com.fanok.audiobooks.fragment.BooksFragment;
import com.fanok.audiobooks.interface_pacatge.books.BooksView;
import com.fanok.audiobooks.model.AutorsModel;
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

    public static boolean isEnd = false;
    private static final String TAG = "BooksPresenter";
    private boolean isLoading = false;
    private boolean isRefreshing = false;
    private int page = 0;
    private int mModelId;
    private ArrayList<BookPOJO> books;
    private ArrayList<GenrePOJO> genre;
    private com.fanok.audiobooks.interface_pacatge.books.BooksModel mModelBook;
    private com.fanok.audiobooks.interface_pacatge.books.GenreModel mModelGenre;


    private String mUrl;


    @Override
    public void onCreate(@NonNull String url, int modelId) {
        mUrl = url;
        mModelId = modelId;
        isEnd = false;
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
                genre = new ArrayList<>();
                mModelGenre = new AutorsModel();
                break;
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
        if (!isEnd && !isRefreshing) {
            getViewState().showProgres(true);
            page++;
            getData(mUrl.replace("page", "page" + page));
        }

    }

    @Override
    public void onRefresh() {
        if (!isLoading) {
            isRefreshing = true;
            isEnd = false;
            getViewState().showRefreshing(true);
            page = 1;
            getData(mUrl.replace("page", "page" + page));
        } else {
            getViewState().showRefreshing(false);
        }

    }

    @Override
    public void onChageOrintationScreen(String url) {
        if (mUrl == null || mUrl.isEmpty()) {
            mUrl = url;
            page = 0;
            isEnd = false;
        }
        if ((books == null || books.size() == 0) && (genre == null || genre.size() == 0)
                && !isLoading) {
            getData(mUrl);
            isEnd = false;
        } else {
            switch (mModelId) {
                case Consts.MODEL_BOOKS:
                    getViewState().showData(books);
                    break;
                case Consts.MODEL_GENRE:
                    getViewState().showData(genre);
                    break;
                case Consts.MODEL_AUTOR:
                    getViewState().showData(genre);
                    break;
                case Consts.MODEL_ARTIST:
                    getViewState().showData(genre);
                    break;
            }
        }
        if (isLoading) getViewState().showProgres(true);
    }

    @Override
    public void onOptionItemSelected(int itemId) {
        switch (itemId) {
            case R.id.app_bar_search:
                break;
            case R.id.new_data:
                getViewState().showFragment(BooksFragment.newInstance(
                        "https://audioknigi.club/index/newall/page/", R.string.menu_audiobooks,
                        R.string.order_new, Consts.MODEL_BOOKS), "audioBooksOrederNew");
                break;
            case R.id.reting_all_time:
                getViewState().showFragment(BooksFragment.newInstance(
                        "https://audioknigi.club/index/top/page/?period=all",
                        R.string.menu_audiobooks,
                        R.string.order_reting, Consts.MODEL_BOOKS), "audioBooksOrederBestAllTime");
                break;
            case R.id.reting_month:
                getViewState().showFragment(BooksFragment.newInstance(
                        "https://audioknigi.club/index/top/page/?period=30",
                        R.string.menu_audiobooks,
                        R.string.order_reting, Consts.MODEL_BOOKS), "audioBooksOrederBestMonth");
                break;
            case R.id.reting_week:
                getViewState().showFragment(BooksFragment.newInstance(
                        "https://audioknigi.club/index/top/page/?period=7",
                        R.string.menu_audiobooks,
                        R.string.order_reting, Consts.MODEL_BOOKS), "audioBooksOrederBestWeek");
                break;
            case R.id.discussed_all_time:
                getViewState().showFragment(BooksFragment.newInstance(
                        "https://audioknigi.club/index/discussed/page/?period=all",
                        R.string.menu_audiobooks,
                        R.string.order_discussed, Consts.MODEL_BOOKS),
                        "audioBooksOrederDiscussedAllTime");
                break;
            case R.id.discussed_month:
                getViewState().showFragment(BooksFragment.newInstance(
                        "https://audioknigi.club/index/discussed/page/?period=30",
                        R.string.menu_audiobooks,
                        R.string.order_discussed, Consts.MODEL_BOOKS),
                        "audioBooksOrederDiscussedMonth");
                break;
            case R.id.discussed_week:
                getViewState().showFragment(BooksFragment.newInstance(
                        "https://audioknigi.club/index/discussed/page/?period=7",
                        R.string.menu_audiobooks,
                        R.string.order_discussed, Consts.MODEL_BOOKS),
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
            getViewState().showFragment(BooksFragment.newInstance(
                    genre.get(position).getUrl(),
                    R.string.menu_audiobooks,
                    genre.get(position).getName(), Consts.MODEL_BOOKS), tag);
        }
    }


    private void getData(String url) {
        if (!isLoading) {
            isLoading = true;
            if (mModelId == Consts.MODEL_BOOKS) {
                mModelBook.getBooks(url)
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
                                e.printStackTrace();
                                Log.e(TAG, e.getMessage());
                                getViewState().showToast(R.string.error_load_data);
                                page--;
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
                mModelGenre.getBooks(url)
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
                                e.printStackTrace();
                                Log.e(TAG, e.getMessage());
                                getViewState().showToast(R.string.error_load_data);
                                page--;
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

}
