package com.fanok.audiobooks.presenter;


import android.support.annotation.NonNull;
import android.util.Log;

import com.arellomobile.mvp.InjectViewState;
import com.arellomobile.mvp.MvpPresenter;
import com.fanok.audiobooks.R;
import com.fanok.audiobooks.interface_pacatge.book_content.ComentsPresenter;
import com.fanok.audiobooks.interface_pacatge.book_content.Series;
import com.fanok.audiobooks.interface_pacatge.book_content.SeriesModel;
import com.fanok.audiobooks.pojo.SeriesPOJO;

import java.util.ArrayList;

import io.reactivex.Observer;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

@InjectViewState
public class BookSeriesPresenter extends MvpPresenter<Series> implements
        ComentsPresenter {

    private static final String TAG = "BookSeriesPresenter";
    private boolean isLoading = false;
    private SeriesModel mComentsModel;

    private ArrayList<SeriesPOJO> mComentsPOJOS;
    private String mUrl;


    @Override
    public void onCreate(@NonNull String url) {
        mComentsPOJOS = new ArrayList<>();
        mComentsModel = new com.fanok.audiobooks.model.SeriesModel();
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


    @Override
    public void onChageOrintationScreen() {
        if (isLoading) {
            getViewState().showProgress(true);
        } else if (mComentsPOJOS != null) {
            getViewState().showSeries(mComentsPOJOS);
        }
    }

    private void getData() {
        if (!isLoading) {
            isLoading = true;
            mComentsModel.getSeries(mUrl)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(new Observer<ArrayList<SeriesPOJO>>() {
                        @Override
                        public void onSubscribe(Disposable d) {
                        }

                        @Override
                        public void onNext(ArrayList<SeriesPOJO> descriptionPOJO) {
                            mComentsPOJOS.addAll(descriptionPOJO);
                        }

                        @Override
                        public void onError(Throwable e) {
                            e.printStackTrace();
                            Log.e(TAG, e.getMessage());
                            getViewState().showToast(R.string.error_load_data);
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
