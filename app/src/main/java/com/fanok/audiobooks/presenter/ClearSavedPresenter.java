package com.fanok.audiobooks.presenter;

import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.media3.common.util.UnstableApi;

import com.arellomobile.mvp.InjectViewState;
import com.arellomobile.mvp.MvpPresenter;
import com.fanok.audiobooks.R;
import com.fanok.audiobooks.broadcasts.DownloadActionReceiver;
import com.fanok.audiobooks.interface_pacatge.clearSeved.ClearSavedView;
import com.fanok.audiobooks.model.ClearSavedModel;
import com.fanok.audiobooks.pojo.DownloadItem;
import com.fanok.audiobooks.util.DownloadStorageHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.reactivex.Single;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;


/**
 * The Downloads screen. Every refresh builds a new immutable snapshot off the main thread and replaces the previous
 * one on main; refreshes asked for while one runs are coalesced into one more, so snapshots never interleave.
 */
@InjectViewState
public class ClearSavedPresenter extends MvpPresenter<ClearSavedView> {

    /** Current snapshot; read and replaced on the main thread only. */
    private List<DownloadItem> downloads = Collections.emptyList();
    private boolean isLoading = false;
    private boolean reloadRequested = false;
    private ClearSavedModel mModel;
    private Context mContext;

    private final CompositeDisposable compositeDisposable;


    public ClearSavedPresenter(Context c){
        mModel = new ClearSavedModel(c);
        mContext = c;
        compositeDisposable = new CompositeDisposable();
    }

    @Override
    protected void onFirstViewAttach() {
        super.onFirstViewAttach();
        load();
    }

    public void load(){
        refresh(true);
    }

    public void updateData() {
        refresh(false);
    }

    @OptIn(markerClass = UnstableApi.class)
    private void refresh(boolean showProgress) {
        if (mModel == null) return;
        if (isLoading) {
            reloadRequested = true;
            return;
        }
        isLoading = true;
        if (showProgress) getViewState().showProgress(true);
        final ClearSavedModel model = mModel;
        compositeDisposable.add(Single.fromCallable(model::loadSnapshot)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(snapshot -> {
                    downloads = snapshot;
                    getViewState().showData(snapshot);
                    finishRefresh(showProgress);
                }, e -> {
                    android.util.Log.e("ClearSavedPresenter", "Downloads not loaded", e);
                    getViewState().showToast(R.string.error_load_data);
                    finishRefresh(showProgress);
                }));
    }

    private void finishRefresh(boolean showProgress) {
        isLoading = false;
        if (showProgress) getViewState().showProgress(false);
        if (reloadRequested) {
            reloadRequested = false;
            refresh(false);
        }
    }

    /** Removes the given books (resolved by id when the user chose them) from the library and the screen. */
    public void remove(@NonNull List<DownloadItem> items) {
        if (items.isEmpty()) return;
        Set<String> ids = new HashSet<>();
        List<String> bookUrls = new ArrayList<>();
        for (DownloadItem item : items) {
            ids.add(item.getId());
            bookUrls.add(item.getBookUrl());
        }
        List<DownloadItem> kept = new ArrayList<>();
        for (DownloadItem item : downloads) {
            if (!ids.contains(item.getId())) kept.add(item);
        }
        downloads = Collections.unmodifiableList(kept);
        getViewState().showData(downloads);

        if (mContext != null) {
            DownloadStorageHelper.removeBooks(mContext, bookUrls, (removed, total, failed) -> {
                if (failed > 0 && mContext != null) {
                    android.widget.Toast.makeText(mContext, mContext.getString(R.string.remove_partial, removed, total, failed),
                            android.widget.Toast.LENGTH_LONG).show();
                    refresh(false);
                }
            });
        }
    }

    /** Writes the completed chapters of the given books without a copy to the download folder. */
    @OptIn(markerClass = UnstableApi.class)
    public void export(@NonNull List<DownloadItem> items) {
        if (mContext == null || items.isEmpty()) return;
        List<String> bookUrls = new ArrayList<>();
        for (DownloadItem item : items) bookUrls.add(item.getBookUrl());
        getViewState().showMessage(mContext.getString(R.string.export_started));
        DownloadStorageHelper.exportBooks(mContext, bookUrls, (exported, failed, error) -> {
            if (mContext == null) return;
            String message;
            if (failed > 0) {
                message = mContext.getString(R.string.export_failed, exported, failed, error != null ? error : "");
            } else if (exported > 0) {
                message = mContext.getString(R.string.export_done, exported);
            } else {
                message = mContext.getString(R.string.export_nothing);
            }
            getViewState().showMessage(message);
        });
    }

    public void onDestroy(){
        compositeDisposable.dispose();
        if (mModel!=null){
            mModel.closeDB();
            mModel = null;
        }
        mContext = null;
    }

    public void restart(@NonNull DownloadItem item) {
        sendAction("RESTART", item);
    }

    public void resume(@NonNull DownloadItem item) {
        sendAction("RESUME", item);
    }

    public void pause(@NonNull DownloadItem item) {
        sendAction("PAUSE", item);
    }

    private void sendAction(@NonNull String action, @NonNull DownloadItem item) {
        if (mContext == null || item.getDownloadIds().isEmpty()) return;
        Intent intent = new Intent(mContext, DownloadActionReceiver.class);
        intent.setAction(action);
        intent.setPackage(mContext.getPackageName());
        intent.putExtra(DownloadActionReceiver.EXTRA_IDS, item.getDownloadIds().toArray(new String[0]));
        mContext.sendBroadcast(intent);
    }
}
