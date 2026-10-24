package com.fanok.audiobooks.presenter;


import android.util.Log;
import com.arellomobile.mvp.InjectViewState;
import com.arellomobile.mvp.MvpPresenter;
import com.fanok.audiobooks.R;
import com.fanok.audiobooks.interface_pacatge.book_content.ComentsPresenter;
import com.fanok.audiobooks.interface_pacatge.book_content.Series;
import com.fanok.audiobooks.interface_pacatge.book_content.SeriesModel;
import com.fanok.audiobooks.pojo.SeriesPOJO;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import java.util.ArrayList;

@InjectViewState
public class BookSeriesPresenter extends MvpPresenter<Series> implements
        ComentsPresenter {

    private static final String TAG = "BookSeriesPresenter";

    private boolean isLoading = false;

    private boolean isLoaded = false;

    private Disposable mDisposable;

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
    public void attachView(Series view) {
        super.attachView(view);
        if (!isLoaded && !isLoading) {
            loadComents(); // a load cancelled by onDestroy (the fragment calls it when its view goes)
        }
    }

    @Override
    public void onDestroy() {
        if (mDisposable != null) {
            mDisposable.dispose();
            mDisposable = null;
        }
        isLoading = false;
    }

    @Override
    public void loadComents() {
        getViewState().showProgress(true);
        getData();
    }

    private void getData() {
        if (!isLoading) {
            isLoading = true;
            mDisposable = mComentsModel.getSeries(mUrl)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(series -> {
                        mComentsPOJOS.clear();
                        mComentsPOJOS.addAll(series);
                    }, e -> {
                        Log.w(TAG, "series not loaded", e);
                        // offline there is nothing to report: the series is online-only content
                        if (!(e instanceof java.io.IOException
                                && com.fanok.audiobooks.Consts.isOffline(com.fanok.audiobooks.App.getInstance()))) {
                            getViewState().showToast(R.string.error_load_data);
                        }
                        finish();
                    }, this::finish);
        }
    }

    private void finish() {
        getViewState().showSeries(mComentsPOJOS);
        getViewState().showProgress(false);
        isLoading = false;
        isLoaded = true;
    }

}
