package com.fanok.audiobooks.presenter;


import android.util.Log;
import com.arellomobile.mvp.InjectViewState;
import com.arellomobile.mvp.MvpPresenter;
import com.fanok.audiobooks.R;
import com.fanok.audiobooks.interface_pacatge.book_content.ComentsPresenter;
import com.fanok.audiobooks.interface_pacatge.book_content.Series;
import com.fanok.audiobooks.interface_pacatge.book_content.SeriesModel;
import com.fanok.audiobooks.pojo.SeriesPOJO;
import io.reactivex.Observer;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import java.util.ArrayList;

@InjectViewState
public class BookSeriesPresenter extends MvpPresenter<Series> implements
        ComentsPresenter {

    private static final String TAG = "BookSeriesPresenter";

    private boolean isLoading = false;

    private final SeriesModel mComentsModel;

    private final ArrayList<SeriesPOJO> mComentsPOJOS;

    private final String mUrl;

    public BookSeriesPresenter(String url) {
        mComentsPOJOS = new ArrayList<>();
        mComentsModel = new com.fanok.audiobooks.model.SeriesModel();
        mUrl = url;
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
