package com.fanok.audiobooks.presenter;


import android.content.Context;
import android.widget.EditText;
import com.arellomobile.mvp.InjectViewState;
import com.arellomobile.mvp.MvpPresenter;
import com.fanok.audiobooks.R;
import com.fanok.audiobooks.interface_pacatge.import_favorite.ActivityImportInterface;
import com.fanok.audiobooks.interface_pacatge.import_favorite.ImportPresenterInterface;
import com.fanok.audiobooks.model.ImportModel;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import org.jetbrains.annotations.NotNull;

@InjectViewState
public class ImportPresenter extends MvpPresenter<ActivityImportInterface> implements
        ImportPresenterInterface {

    private final ImportModel mImportModel;

    private boolean loading = false;

    private Disposable mDisposable;

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
                mDisposable = mImportModel.importBooks(username, password)
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .doFinally(() -> {
                            loading = false;
                            getViewState().showProgress(false);
                        })
                        .subscribe(id -> getViewState().showToast(id),
                                e -> getViewState().showToast(R.string.error_import));
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mDisposable != null) {
            mDisposable.dispose();
        }
    }
}