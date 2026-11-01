package com.fanok.audiobooks.presenter;


import android.util.Log;
import androidx.annotation.NonNull;
import com.arellomobile.mvp.InjectViewState;
import com.arellomobile.mvp.MvpPresenter;
import com.fanok.audiobooks.R;
import com.fanok.audiobooks.interface_pacatge.book_content.Coments;
import com.fanok.audiobooks.interface_pacatge.book_content.ComentsPresenter;
import com.fanok.audiobooks.model.ComentsModel;
import com.fanok.audiobooks.pojo.ComentsPOJO;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import java.util.ArrayList;

@InjectViewState
public class BookComentsPresenter extends MvpPresenter<Coments> implements
        ComentsPresenter {

    private static final String TAG = "BookComentsPresenter";

    private final ArrayList<ComentsPOJO> mComentsPOJOS;

    private final String mUrl;

    /** The load in flight, null otherwise. */
    private Disposable mDisposable;

    private boolean mLoaded = false;

    /** A failed fetch stays cached: the next attempt fetches anew. */
    private boolean mFailed = false;

    public BookComentsPresenter(@NonNull String url) {
        mUrl = url;
        mComentsPOJOS = new ArrayList<>();
    }

    @Override
    protected void onFirstViewAttach() {
        super.onFirstViewAttach();
        loadComents();
    }

    @Override
    public void attachView(Coments view) {
        super.attachView(view);
        // onDestroy() may have cancelled an unfinished load while the presenter lives on (the fragment
        // calls it from onDestroyView); the page is fetched again only then, never after it was loaded
        if (!mLoaded && mDisposable == null) {
            loadComents();
        }
    }

    @Override
    public void onDestroy() {
        if (mDisposable != null) {
            mDisposable.dispose();
            mDisposable = null;
        }
        super.onDestroy();
    }

    @Override
    public void loadComents() {
        if (mDisposable != null) {
            return;
        }
        getViewState().showProgress(true);
        // the fetch the book screen started for the tab's count, if it is still the last one
        mDisposable = ComentsModel.shared(mUrl, mFailed)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(comments -> {
                    mDisposable = null;
                    mLoaded = true;
                    mComentsPOJOS.clear();
                    mComentsPOJOS.addAll(comments);
                    getViewState().showComents(mComentsPOJOS);
                    getViewState().setPlaceholder(R.string.no_comments);
                    getViewState().showProgress(false);
                }, e -> {
                    mDisposable = null;
                    mFailed = true;
                    Log.w(TAG, "comments failed for " + mUrl, e);
                    getViewState().setPlaceholder(R.string.error_load_coments);
                    getViewState().showProgress(false);
                });
    }

}
