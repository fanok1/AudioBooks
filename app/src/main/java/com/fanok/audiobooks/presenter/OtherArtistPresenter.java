package com.fanok.audiobooks.presenter;


import android.util.Log;

import androidx.annotation.NonNull;

import com.arellomobile.mvp.InjectViewState;
import com.arellomobile.mvp.MvpPresenter;
import com.fanok.audiobooks.interface_pacatge.book_content.ComentsPresenter;
import com.fanok.audiobooks.interface_pacatge.book_content.OtherArtist;
import com.fanok.audiobooks.interface_pacatge.book_content.OtherArtistModel;
import com.fanok.audiobooks.pojo.BookPOJO;
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
    private BookPOJO mBookPOJO;

    public OtherArtistPresenter(@NonNull BookPOJO bookPOJO) {
        mComentsPOJOS = new ArrayList<>();
        mComentsModel = new com.fanok.audiobooks.model.OtherArtistModel();
        mBookPOJO = bookPOJO;
    }

    @Override
    protected void onFirstViewAttach() {
        super.onFirstViewAttach();
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
            mComentsModel.getOtherArtist(mBookPOJO)
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
