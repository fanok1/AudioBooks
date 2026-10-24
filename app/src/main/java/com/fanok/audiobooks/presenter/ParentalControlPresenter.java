package com.fanok.audiobooks.presenter;


import android.text.TextUtils;
import com.arellomobile.mvp.InjectViewState;
import com.arellomobile.mvp.MvpPresenter;
import com.fanok.audiobooks.App;
import com.fanok.audiobooks.R;
import com.fanok.audiobooks.model.ParentalControlModel;
import io.reactivex.Observer;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import org.jetbrains.annotations.NotNull;

@InjectViewState
public class ParentalControlPresenter extends
        MvpPresenter<com.fanok.audiobooks.interface_pacatge.parental_control.View> {

    private Disposable mDisposable;

    @Override
    protected void onFirstViewAttach() {
        super.onFirstViewAttach();
        getViewState().showProgress(true);
        new ParentalControlModel().getBooks()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Observer<ParentalControlModel.Loaded>() {
                    @Override
                    public void onError(@NotNull Throwable e) {
                        getViewState().showToast(R.string.error_load_data);
                        getViewState().showProgress(false);
                    }

                    @Override
                    public void onNext(@NotNull ParentalControlModel.Loaded loaded) {
                        getViewState().showData(loaded.items);
                        if (!loaded.failedSources.isEmpty()) {
                            // their books stay blocked until the list loads: say so instead of hiding it
                            getViewState().showToast(App.getInstance().getString(
                                    R.string.parental_control_sources_failed,
                                    TextUtils.join(", ", loaded.failedSources)));
                        }
                    }

                    @Override
                    public void onSubscribe(@NotNull Disposable d) {
                        mDisposable = d;
                    }

                    @Override
                    public void onComplete() {
                        getViewState().showProgress(false);
                    }
                });
    }

    @Override
    public void onDestroy() {
        if (mDisposable != null) {
            mDisposable.dispose();
        }
        super.onDestroy();
    }
}
