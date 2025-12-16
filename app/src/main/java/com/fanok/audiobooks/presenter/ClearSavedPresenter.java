package com.fanok.audiobooks.presenter;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.media3.common.C;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.offline.Download;
import androidx.media3.exoplayer.offline.DownloadCursor;
import androidx.media3.exoplayer.offline.DownloadIndex;

import com.arellomobile.mvp.InjectViewState;
import com.arellomobile.mvp.MvpPresenter;
import com.fanok.audiobooks.R;
import com.fanok.audiobooks.broadcasts.DownloadActionReceiver;
import com.fanok.audiobooks.interface_pacatge.clearSeved.ClearSavedView;
import com.fanok.audiobooks.model.ClearSavedModel;
import com.fanok.audiobooks.pojo.DownloadItem;
import com.fanok.audiobooks.util.DownloadUtil;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

import io.reactivex.Observable;
import io.reactivex.Observer;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;


@InjectViewState
public class ClearSavedPresenter extends MvpPresenter<ClearSavedView> {

    private ArrayList<DownloadItem> downloads;
    private boolean isLoading = false;
    private ClearSavedModel mModel;
    private Context mContext;

    private CompositeDisposable compositeDisposable;



    public ClearSavedPresenter(Context c){
        downloads = new ArrayList<>();
        mModel = new ClearSavedModel(c);
        mContext = c;
        compositeDisposable = new CompositeDisposable();
        //updateDisposable = new CompositeDisposable();
    }

    @Override
    protected void onFirstViewAttach() {
        super.onFirstViewAttach();
        load();
    }

    @SuppressLint("UnsafeOptInUsageError")
    public void load(){
        if(!isLoading){
            getViewState().showProgress(true);
            mModel.getAllSaved()
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(new Observer<ArrayList<DownloadItem>>() {
                        @Override
                        public void onSubscribe(Disposable d) {
                            compositeDisposable.add(d);
                        }

                        @Override
                        public void onNext(ArrayList<DownloadItem> downloadItems) {
                            downloads = downloadItems;
                        }

                        @Override
                        public void onError(Throwable e) {
                            getViewState().showToast(R.string.error_load_data);
                            onComplete();
                        }

                        @Override
                        public void onComplete() {
                            getViewState().showProgress(false);
                            getViewState().showData(downloads);
                            isLoading = false;
                        }
                    });
        }

    }

    public void remove(int position){
        String id = downloads.get(position).getFileName();
        Intent intent = new Intent(mContext, DownloadActionReceiver.class);
        intent.setAction("CANCEL");
        intent.putExtra("id", id);
        mContext.sendBroadcast(intent);
        downloads.remove(position);
        getViewState().showData(downloads);
    }

    public void remove(ArrayList<DownloadItem> arrayList){
        for (DownloadItem downloadItem : arrayList) {
            String id = downloadItem.getFileName();
            Intent intent = new Intent(mContext, DownloadActionReceiver.class);
            intent.setAction("CANCEL");
            intent.putExtra("id", id);
            mContext.sendBroadcast(intent);
            downloads.remove(downloadItem);
        }
        getViewState().showData(downloads);
    }

    public void onDestroy(){
        compositeDisposable.dispose();
        if (mModel!=null){
            mModel.closeDB();
            mModel = null;
        }
        mContext = null;
    }

    public void restart (String id){
        Intent intent = new Intent(mContext, DownloadActionReceiver.class);
        intent.setAction("RESTART");
        intent.putExtra("id", id);
        mContext.sendBroadcast(intent);
    }

    public void resume (String id){
        Intent intent = new Intent(mContext, DownloadActionReceiver.class);
        intent.setAction("RESUME");
        intent.putExtra("id", id);
        mContext.sendBroadcast(intent);
    }

    public void pause (String id) {
        Intent intent = new Intent(mContext, DownloadActionReceiver.class);
        intent.setAction("PAUSE");
        intent.putExtra("id", id);
        mContext.sendBroadcast(intent);

    }

    @UnstableApi
    public void updateData() {
        if (isLoading || mModel == null) {
            return;
        }
        getActualDownloadsObservable()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Observer<Boolean>() {
                    @Override
                    public void onSubscribe(Disposable d) {
                        compositeDisposable.add(d);
                    }

                    @Override
                    public void onNext(Boolean dataChanged) {
                        if (dataChanged) {
                            getViewState().showData(downloads);
                        }
                    }

                    @Override
                    public void onError(Throwable e) {
                        if (e instanceof IOException) {
                            getViewState().showToast(R.string.error_load_data);
                        }
                    }

                    @Override
                    public void onComplete() {
                    }
                });
    }


    @UnstableApi
    private Observable<Boolean> getActualDownloadsObservable() {
        return Observable.fromCallable(() -> {
            DownloadIndex downloadIndex = DownloadUtil.getDownloadManager().getDownloadIndex();
            HashMap<String, Download> actualDownloadsMap = new HashMap<>();
            try (DownloadCursor cursor = downloadIndex.getDownloads()) {
                while (cursor.moveToNext()) {
                    Download download = cursor.getDownload();
                    actualDownloadsMap.put(download.request.id, download);
                }
            }

            boolean dataChanged = false;
            ArrayList<DownloadItem> itemsToRemove = new ArrayList<>();

            for (DownloadItem currentItem : downloads) {
                Download actualDownload = actualDownloadsMap.get(currentItem.getFileName());

                if (actualDownload != null) {
                    int oldStatus = currentItem.getStatus();
                    float oldProgress = currentItem.getProgress();
                    currentItem.setStatus(actualDownload.state);
                    int progress = 0;
                    if (actualDownload.getPercentDownloaded() != C.PERCENTAGE_UNSET) {
                        progress = Math.max(0, Math.min(100, Math.round(actualDownload.getPercentDownloaded())));
                    } else if (actualDownload.contentLength > 0) {
                        progress = (int) Math.round(100.0 * actualDownload.getBytesDownloaded() / (double) actualDownload.contentLength);
                        progress = Math.max(0, Math.min(100, progress));
                    }
                    currentItem.setProgress(progress);
                    if (oldStatus != currentItem.getStatus() || oldProgress != currentItem.getProgress()) {
                        dataChanged = true;
                    }
                    actualDownloadsMap.remove(currentItem.getFileName());
                } else {
                    itemsToRemove.add(currentItem);
                    dataChanged = true;
                }
            }

            if (!itemsToRemove.isEmpty()) {
                downloads.removeAll(itemsToRemove);
            }

            if (!actualDownloadsMap.isEmpty()) {
                for (Download newDownload : actualDownloadsMap.values()) {
                    DownloadItem newItem = mModel.buildDownloadItem(newDownload);
                    if (newItem != null) {
                        downloads.add(newItem);
                    }
                }
                dataChanged = true;
            }

            return dataChanged;
        });
    }
}
