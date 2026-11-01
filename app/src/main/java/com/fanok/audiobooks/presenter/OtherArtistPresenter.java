package com.fanok.audiobooks.presenter;


import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.arellomobile.mvp.InjectViewState;
import com.arellomobile.mvp.MvpPresenter;
import com.fanok.audiobooks.interface_pacatge.book_content.ComentsPresenter;
import com.fanok.audiobooks.interface_pacatge.book_content.OtherArtist;
import com.fanok.audiobooks.interface_pacatge.book_content.OtherArtistModel;
import com.fanok.audiobooks.pojo.BookPOJO;
import com.fanok.audiobooks.pojo.OtherArtistPOJO;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import java.util.ArrayList;

@InjectViewState
public class OtherArtistPresenter extends MvpPresenter<OtherArtist> implements
        ComentsPresenter {

    private static final String TAG = "OtherArtistPresenter";

    private boolean isLoading = false;

    private boolean isLoaded = false;

    private Disposable mDisposable;

    private final BookPOJO mBookPOJO;

    private final OtherArtistModel mComentsModel;

    private final ArrayList<OtherArtistPOJO> mComentsPOJOS;

    /** {@code loaded}: the narrators the book screen already loaded; empty means this presenter loads them. */
    public OtherArtistPresenter(@NonNull BookPOJO bookPOJO, @Nullable ArrayList<OtherArtistPOJO> loaded) {
        mComentsPOJOS = new ArrayList<>();
        mComentsModel = new com.fanok.audiobooks.model.OtherArtistModel();
        mBookPOJO = bookPOJO;
        if (loaded != null && !loaded.isEmpty()) {
            mComentsPOJOS.addAll(loaded);
            isLoaded = true;
        }
    }

    @Override
    protected void onFirstViewAttach() {
        super.onFirstViewAttach();
        if (isLoaded) {
            finish();
        } else {
            loadComents();
        }
    }

    @Override
    public void attachView(OtherArtist view) {
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
            mDisposable = mComentsModel.getOtherArtist(mBookPOJO)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(readers -> {
                        mComentsPOJOS.clear();
                        mComentsPOJOS.addAll(readers);
                    }, e -> {
                        Log.w(TAG, "other readers not loaded", e);
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
