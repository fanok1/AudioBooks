package com.fanok.audiobooks.presenter;


import android.support.annotation.NonNull;
import android.util.Log;

import com.arellomobile.mvp.InjectViewState;
import com.arellomobile.mvp.MvpPresenter;
import com.fanok.audiobooks.R;
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
        if (!isEnd) {
            getViewState().showProgres(true);
            page++;
            getData(mUrl + page + "/");
        }

    }

    @Override
    public void onRefresh() {
        isEnd = false;
        getViewState().showRefreshing(true);
        books.clear();
        page = 1;
        getData(mUrl + page + "/");
    }

    @Override
    public void onChageOrintationScreen() {
        getViewState().showData(books);
        if (isLoading) getViewState().showProgres(true);
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
                            getViewState().showProgres(false);
                            getViewState().showRefreshing(false);
                            isLoading = false;
                        }
                    });
        }
    }

}
