package com.fanok.audiobooks.presenter;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.util.Log;

import com.fanok.audiobooks.Consts;
import com.fanok.audiobooks.R;
import com.fanok.audiobooks.interface_pacatge.BooksContract;
import com.fanok.audiobooks.model.BooksModel;
import com.fanok.audiobooks.pojo.BookPOJO;

import java.util.ArrayList;

import io.reactivex.Observer;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

public class BooksPresenter implements BooksContract.Presenter {
    private static final String TAG = "BooksPresenter";
    private static final String ARG_COLUMN_COUNT = "column-count";
    private static final String ARG_URL = "url";
    private boolean isLoading = false;
    private int page = 0;
    private ArrayList<BookPOJO> books;
    private BooksContract.View mView;
    private BooksContract.Model mModel;
    private int mColumnCount = 1;
    private String mUrl;


    public BooksPresenter(@NonNull BooksContract.View view) {
        mView = view;
        mModel = new BooksModel();
        books = new ArrayList<>();
    }

    public static Bundle getArg(@NonNull String url, int columnCount) {
        if (!Consts.REGEXP_URL.matcher(url).matches()) {
            throw new IllegalArgumentException(
                    "Variable 'url' contains not url");
        }
        Bundle args = new Bundle();
        args.putInt(ARG_COLUMN_COUNT, columnCount);
        args.putString(ARG_URL, url);

        return args;
    }

    @Override
    public void onCreate() {
        Bundle arg = mView.getArg();
        if (arg != null) {
            mColumnCount = arg.getInt(ARG_COLUMN_COUNT, 1);
            mUrl = arg.getString(ARG_URL, "");
        }
        if (mUrl.isEmpty()) throw new IllegalArgumentException("Variable 'url' contains not url");
    }

    @Override
    public void onCreateView() {
        if (mColumnCount <= 1) {
            mView.setLayoutManager();
        } else {
            mView.setLayoutManager(mColumnCount);
        }
    }

    @Override
    public void onDestroy() {

    }

    @Override
    public void loadBoks() {
        mView.showProgres(true);
        page++;
        getData(mUrl + page + "/");

    }

    @Override
    public void onRefresh() {
        mView.showRefreshing(true);
        books.clear();
        page = 1;
        getData(mUrl + page + "/");
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
                            mView.showToast(R.string.error_load_data);
                            page--;
                            onComplete();

                        }

                        @Override
                        public void onComplete() {
                            Log.d(TAG, "onComplete");
                            mView.showData(books);
                            mView.showProgres(false);
                            mView.showRefreshing(false);
                            isLoading = false;
                        }
                    });
        }
    }

}
