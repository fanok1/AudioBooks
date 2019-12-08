package com.fanok.audiobooks.presenter;


import com.arellomobile.mvp.InjectViewState;
import com.arellomobile.mvp.MvpPresenter;
import com.fanok.audiobooks.R;
import com.fanok.audiobooks.Url;
import com.fanok.audiobooks.model.ParentalControlModel;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

import io.reactivex.Observer;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

@InjectViewState
public class ParentalControlPresenter extends
        MvpPresenter<com.fanok.audiobooks.interface_pacatge.parental_control.View> {

    private ArrayList<String> mArrayList;
    private ParentalControlModel mModel;

    @Override
    protected void onFirstViewAttach() {
        super.onFirstViewAttach();
        loadBoks();
    }

    private void loadBoks() {
        mModel = new ParentalControlModel();
        mArrayList = new ArrayList<>();
        getViewState().showProgress(true);
        getData(Url.SECTIONS);
    }

    private void getData(@NotNull String url) {
        mModel.getBooks(url)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Observer<ArrayList<String>>() {
                    @Override
                    public void onSubscribe(Disposable d) {
                    }

                    @Override
                    public void onNext(@NotNull ArrayList<String> strings) {
                        mArrayList.addAll(strings);
                    }

                    @Override
                    public void onError(Throwable e) {
                        getViewState().showToast(R.string.error_load_data);
                        onComplete();

                    }

                    @Override
                    public void onComplete() {
                        getViewState().showData(mArrayList);
                        getViewState().showProgress(false);
                    }
                });

    }
}
