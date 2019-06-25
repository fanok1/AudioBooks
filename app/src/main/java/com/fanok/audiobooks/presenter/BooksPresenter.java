package com.fanok.audiobooks.presenter;


import android.support.annotation.NonNull;
import android.util.Log;

import com.arellomobile.mvp.InjectViewState;
import com.arellomobile.mvp.MvpPresenter;
import com.fanok.audiobooks.R;
import com.fanok.audiobooks.fragment.BooksFragment;
import com.fanok.audiobooks.interface_pacatge.books.BooksView;
import com.fanok.audiobooks.model.BooksModel;
import com.fanok.audiobooks.pojo.BookPOJO;

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
    private ArrayList<BookPOJO> books;
    private com.fanok.audiobooks.interface_pacatge.books.BooksModel mModel;
    private String mUrl;

    public BooksPresenter() {
        books = new ArrayList<>();
        mModel = new BooksModel();
    }

    @Override
    public void onCreate(@NonNull String url) {
        mUrl = url;
        isEnd = false;
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
        if (books.size() == 0 && !isLoading) {
            getData(mUrl);
            isEnd = false;
        } else {
            getViewState().showData(books);
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
                        "https://audioknigi.club/index/newall/page/", R.string.title_books,
                        R.string.order_new), "audioBooksOrederNew");
                break;
            case R.id.reting_all_time:
                getViewState().showFragment(BooksFragment.newInstance(
                        "https://audioknigi.club/index/top/page/?period=all", R.string.title_books,
                        R.string.order_reting), "audioBooksOrederBestAllTime");
                break;
            case R.id.reting_month:
                getViewState().showFragment(BooksFragment.newInstance(
                        "https://audioknigi.club/index/top/page/?period=30", R.string.title_books,
                        R.string.order_reting), "audioBooksOrederBestMonth");
                break;
            case R.id.reting_week:
                getViewState().showFragment(BooksFragment.newInstance(
                        "https://audioknigi.club/index/top/page/?period=7", R.string.title_books,
                        R.string.order_reting), "audioBooksOrederBestWeek");
                break;
            case R.id.discussed_all_time:
                getViewState().showFragment(BooksFragment.newInstance(
                        "https://audioknigi.club/index/discussed/page/?period=all",
                        R.string.title_books,
                        R.string.order_discussed), "audioBooksOrederDiscussedAllTime");
                break;
            case R.id.discussed_month:
                getViewState().showFragment(BooksFragment.newInstance(
                        "https://audioknigi.club/index/discussed/page/?period=30",
                        R.string.title_books,
                        R.string.order_discussed), "audioBooksOrederDiscussedMonth");
                break;
            case R.id.discussed_week:
                getViewState().showFragment(BooksFragment.newInstance(
                        "https://audioknigi.club/index/discussed/page/?period=7",
                        R.string.title_books,
                        R.string.order_discussed), "audioBooksOrederDiscussedWeek");
                break;
        }
    }



    private void getData(String url) {
        if (!isLoading) {
            isLoading = true;
            mModel.getBooks(url)
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
        }
    }

}
