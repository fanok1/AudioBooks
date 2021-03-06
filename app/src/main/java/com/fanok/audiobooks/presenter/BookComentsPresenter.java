package com.fanok.audiobooks.presenter;


import android.util.Log;
import androidx.annotation.NonNull;
import com.arellomobile.mvp.InjectViewState;
import com.arellomobile.mvp.MvpPresenter;
import com.fanok.audiobooks.R;
import com.fanok.audiobooks.interface_pacatge.book_content.Coments;
import com.fanok.audiobooks.interface_pacatge.book_content.ComentsModel;
import com.fanok.audiobooks.interface_pacatge.book_content.ComentsPresenter;
import com.fanok.audiobooks.pojo.ComentsPOJO;
import io.reactivex.Observer;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import java.util.ArrayList;

@InjectViewState
public class BookComentsPresenter extends MvpPresenter<Coments> implements
        ComentsPresenter {

    private static final String TAG = "BookComentsPresenter";

    private boolean isLoading = false;

    private final ComentsModel mComentsModel;

    private final ArrayList<ComentsPOJO> mComentsPOJOS;

    private String mUrl;

    public BookComentsPresenter(@NonNull String url) {
        mUrl = url;
        mComentsPOJOS = new ArrayList<>();
        mComentsModel = new com.fanok.audiobooks.model.ComentsModel();
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
            mComentsModel.getComents(mUrl)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(new Observer<ArrayList<ComentsPOJO>>() {
                        @Override
                        public void onSubscribe(Disposable d) {
                        }

                        @Override
                        public void onNext(ArrayList<ComentsPOJO> descriptionPOJO) {
                            mComentsPOJOS.addAll(descriptionPOJO);
                        }

                        @Override
                        public void onError(Throwable e) {
                            getViewState().setPlaceholder(R.string.error_load_coments);
                            getViewState().showProgress(false);
                            isLoading = false;
                        }

                        @Override
                        public void onComplete() {
                            Log.d(TAG, "onComplete");
                            getViewState().showComents(mComentsPOJOS);
                            getViewState().setPlaceholder(R.string.no_comments);
                            getViewState().showProgress(false);
                            isLoading = false;
                        }
                    });

        }
    }

}
