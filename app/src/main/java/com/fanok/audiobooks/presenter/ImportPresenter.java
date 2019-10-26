package com.fanok.audiobooks.presenter;


import android.content.Context;
import android.widget.EditText;

import com.arellomobile.mvp.InjectViewState;
import com.arellomobile.mvp.MvpPresenter;
import com.fanok.audiobooks.R;
import com.fanok.audiobooks.interface_pacatge.import_favorite.ActivityImportInterface;
import com.fanok.audiobooks.interface_pacatge.import_favorite.ImportPresenterInterface;
import com.fanok.audiobooks.model.ImportModel;

import org.jetbrains.annotations.NotNull;

import io.reactivex.Observer;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

@InjectViewState
public class ImportPresenter extends MvpPresenter<ActivityImportInterface> implements
        ImportPresenterInterface {

    private ImportModel mImportModel;
    private boolean loading = false;

    public ImportPresenter(@NotNull Context context, int src) {
        mImportModel = new ImportModel(context, src);
    }

    @Override
    public void validate(@NotNull EditText editText) {
        if (editText.getText().toString().isEmpty()) {
            editText.setError(editText.getContext().getString(R.string.empty_text));
        }
    }

    @Override
    public void login(@NotNull String username, @NotNull String password) {
        if (mImportModel != null) {
            if (!loading) {
                loading = true;
                getViewState().showProgress(true);
                mImportModel.importBooks(username, password)
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(new Observer<Integer>() {
                            @Override
                            public void onSubscribe(Disposable d) {
                            }

                            @Override
                            public void onNext(Integer id) {
                                getViewState().showToast(id);
                            }

                            @Override
                            public void onError(Throwable e) {
                                getViewState().showToast(R.string.error_import);
                            }

                            @Override
                            public void onComplete() {
                                getViewState().showProgress(false);
                                loading = false;
                            }
                        });

            }
        }
    }
}