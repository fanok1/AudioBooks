package com.fanok.audiobooks.presenter;


import com.arellomobile.mvp.InjectViewState;
import com.arellomobile.mvp.MvpPresenter;
import com.fanok.audiobooks.R;
import com.fanok.audiobooks.model.ParentalControlModel;
import com.fanok.audiobooks.pojo.ParentControlPOJO;
import io.reactivex.Observer;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import java.util.ArrayList;
import org.jetbrains.annotations.NotNull;

@InjectViewState
public class ParentalControlPresenter extends
        MvpPresenter<com.fanok.audiobooks.interface_pacatge.parental_control.View> {

    private ArrayList<ParentControlPOJO> mArrayList;

    private ParentalControlModel mModel;

    @Override
    protected void onFirstViewAttach() {
        super.onFirstViewAttach();
        loadBoks();
    }

    private void getData() {
        mModel.getBooks()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Observer<ArrayList<ParentControlPOJO>>() {
                    @Override
                    public void onError(@NotNull Throwable e) {
                        getViewState().showToast(R.string.error_load_data);
                        onComplete();

                    }

                    @Override
                    public void onNext(@NotNull ArrayList<ParentControlPOJO> list) {
                        mArrayList.addAll(list);
                    }

                    @Override
                    public void onSubscribe(@NotNull Disposable d) {
                    }

                    @Override
                    public void onComplete() {
                        getViewState().showData(mArrayList);
                        getViewState().showProgress(false);
                    }
                });

    }

    private void loadBoks() {
        mModel = new ParentalControlModel();
        mArrayList = new ArrayList<>();
        getViewState().showProgress(true);
        getData();
    }
}
