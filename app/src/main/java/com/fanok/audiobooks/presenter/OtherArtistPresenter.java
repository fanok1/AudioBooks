package com.fanok.audiobooks.presenter;


import android.support.annotation.NonNull;
import android.util.Log;

import com.arellomobile.mvp.InjectViewState;
import com.arellomobile.mvp.MvpPresenter;
import com.fanok.audiobooks.interface_pacatge.book_content.ComentsPresenter;
import com.fanok.audiobooks.interface_pacatge.book_content.OtherArtist;
import com.fanok.audiobooks.interface_pacatge.book_content.OtherArtistModel;
import com.fanok.audiobooks.pojo.OtherArtistPOJO;

import java.util.ArrayList;

import io.reactivex.Observer;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

@InjectViewState
public class OtherArtistPresenter extends MvpPresenter<OtherArtist> implements
        ComentsPresenter {

    private static final String TAG = "OtherArtistPresenter";
    private boolean isLoading = false;
    private OtherArtistModel mComentsModel;

    private ArrayList<OtherArtistPOJO> mComentsPOJOS;
    private String mUrl;


    @Override
    public void onCreate(@NonNull String url) {
        mComentsPOJOS = new ArrayList<>();
        mComentsModel = new com.fanok.audiobooks.model.OtherArtistModel();
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
            mComentsModel.getOtherArtist(mUrl)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(new Observer<ArrayList<OtherArtistPOJO>>() {
                        @Override
                        public void onSubscribe(Disposable d) {
                        }

                        @Override
                        public void onNext(ArrayList<OtherArtistPOJO> descriptionPOJO) {
                            mComentsPOJOS.addAll(descriptionPOJO);
                        }

                        @Override
                        public void onError(Throwable e) {
                            onComplete();
                        }

                        @Override
                        public void onComplete() {
                            Log.d(TAG, "onComplete");
                            getViewState().showSeries(mComentsPOJOS);
                            getViewState().showProgress(false);
                            isLoading = false;
                        }
                    });

        }
    }

}