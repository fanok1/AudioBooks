package com.fanok.audiobooks.presenter;


import android.support.annotation.NonNull;
import android.util.Log;

import com.arellomobile.mvp.InjectViewState;
import com.arellomobile.mvp.MvpPresenter;
import com.fanok.audiobooks.R;
import com.fanok.audiobooks.interface_pacatge.book_content.Coments;
import com.fanok.audiobooks.interface_pacatge.book_content.ComentsModel;
import com.fanok.audiobooks.interface_pacatge.book_content.ComentsPresenter;
import com.fanok.audiobooks.pojo.ComentsPOJO;

import java.util.ArrayList;

import io.reactivex.Observer;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

@InjectViewState
public class BookComentsPresenter extends MvpPresenter<Coments> implements
        ComentsPresenter {

    private static final String TAG = "BookComentsPresenter";
    private boolean isLoading = false;
    private ComentsModel mComentsModel;

    private ArrayList<ComentsPOJO> mComentsPOJOS;
    private String mUrl;


    @Override
    public void onCreate(@NonNull String url) {
        mComentsPOJOS = new ArrayList<>();
        mComentsModel = new com.fanok.audiobooks.model.ComentsModel();
        mUrl = url;
        loadComents();
    }

    @Override
    public void onDestroy() {

    }

    @Override
    public void loadComents() {
        getViewState().showProgress(true);
        getData();
    }

    private void getData() {
        if (!isLoading) {
            isLoading = true;
            mComentsModel.getComents(mUrl)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(new Observer<ArrayList<ComentsPOJO>>() {
                        @Override
                        public void onSubscribe(Disposable d) {
                        }

                        @Override
                        public void onNext(ArrayList<ComentsPOJO> descriptionPOJO) {
                            mComentsPOJOS.addAll(descriptionPOJO);
                        }

                        @Override
                        public void onError(Throwable e) {
                            getViewState().showToast(R.string.error_load_data);
                            onComplete();
                        }

                        @Override
                        public void onComplete() {
                            Log.d(TAG, "onComplete");
                            getViewState().showComents(mComentsPOJOS);
                            getViewState().showProgress(false);
                            isLoading = false;
                        }
                    });

        }
    }

}
