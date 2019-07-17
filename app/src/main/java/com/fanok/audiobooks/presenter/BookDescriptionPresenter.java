package com.fanok.audiobooks.presenter;


import android.support.annotation.NonNull;
import android.util.Log;

import com.arellomobile.mvp.InjectViewState;
import com.arellomobile.mvp.MvpPresenter;
import com.fanok.audiobooks.interface_pacatge.book_content.Description;
import com.fanok.audiobooks.interface_pacatge.book_content.DescriptionModel;
import com.fanok.audiobooks.interface_pacatge.book_content.DescriptionPresenter;
import com.fanok.audiobooks.model.BookDescriptionModel;
import com.fanok.audiobooks.pojo.BookPOJO;
import com.fanok.audiobooks.pojo.DescriptionPOJO;

import java.util.ArrayList;

import io.reactivex.Observer;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

@InjectViewState
public class BookDescriptionPresenter extends MvpPresenter<Description> implements
        DescriptionPresenter {

    private static final String TAG = "BookDescriptionPresente";
    private boolean isLoading = false;
    private DescriptionModel mModelDescription;
    private DescriptionPOJO mDescriptionPOJO;


    @Override
    public void onCreate(@NonNull String url) {
        mModelDescription = new BookDescriptionModel(url);
        loadDescription();
        loadBooks(0);
        loadBooks(1);
    }

    @Override
    public void onDestroy() {

    }

    @Override
    public void loadDescription() {
        getViewState().showProgress(true);
        getData();
    }


    @Override
    public void onChageOrintationScreen() {
        if (isLoading) {
            getViewState().showProgress(true);
        } else if (mDescriptionPOJO != null) {
            getViewState().showDescription(mDescriptionPOJO);
        }
    }


    private void loadBooks(int position) {
        mModelDescription.getBooks(position)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Observer<ArrayList<BookPOJO>>() {
                    @Override
                    public void onSubscribe(Disposable d) {
                    }

                    @Override
                    public void onNext(ArrayList<BookPOJO> bookPOJOS) {
                        getViewState().showOtherBooks(bookPOJOS, position);
                    }

                    @Override
                    public void onError(Throwable e) {
                        e.printStackTrace();
                        Log.e(TAG, e.getMessage());
                    }

                    @Override
                    public void onComplete() {
                        Log.d(TAG, "onComplete");
                    }
                });
    }


    private void getData() {
        if (!isLoading) {
            isLoading = true;
            mModelDescription.getDescription()
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(new Observer<DescriptionPOJO>() {
                        @Override
                        public void onSubscribe(Disposable d) {
                        }

                        @Override
                        public void onNext(DescriptionPOJO descriptionPOJO) {
                            mDescriptionPOJO = descriptionPOJO;
                        }

                        @Override
                        public void onError(Throwable e) {
                            e.printStackTrace();
                            Log.e(TAG, e.getMessage());
                            getViewState().showRefreshDialog();
                        }

                        @Override
                        public void onComplete() {
                            Log.d(TAG, "onComplete");
                            getViewState().showDescription(mDescriptionPOJO);
                            getViewState().showProgress(false);
                            isLoading = false;
                        }
                    });

        }
    }

}
