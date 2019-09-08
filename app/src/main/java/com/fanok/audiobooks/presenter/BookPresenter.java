package com.fanok.audiobooks.presenter;

import static android.content.Context.AUDIO_SERVICE;

import static com.fanok.audiobooks.activity.BookActivity.Broadcast_PLAY_NEW_AUDIO;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.SeekBar;

import androidx.annotation.NonNull;

import com.arellomobile.mvp.InjectViewState;
import com.arellomobile.mvp.MvpPresenter;
import com.crashlytics.android.Crashlytics;
import com.fanok.audiobooks.R;
import com.fanok.audiobooks.interface_pacatge.book_content.Activity;
import com.fanok.audiobooks.interface_pacatge.book_content.ActivityPresenter;
import com.fanok.audiobooks.interface_pacatge.book_content.AudioModelInterfece;
import com.fanok.audiobooks.model.AudioDBModel;
import com.fanok.audiobooks.model.AudioModel;
import com.fanok.audiobooks.model.BooksDBModel;
import com.fanok.audiobooks.pojo.AudioPOJO;
import com.fanok.audiobooks.pojo.BookPOJO;
import com.fanok.audiobooks.pojo.StorageUtil;
import com.fanok.audiobooks.service.MediaPlayerService;

import java.util.ArrayList;

import io.reactivex.Observer;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;


@InjectViewState
public class BookPresenter extends MvpPresenter<Activity> implements ActivityPresenter {
    private static final String TAG = "BookPresenter";

    public static final String Broadcast_PLAY_PREVIOUS = "PlayPriveos";
    public static final String Broadcast_PLAY_NEXT = "PlayNext";
    public static final String Broadcast_PLAY = "Play";
    public static final String Broadcast_SEEK_TO = "SeekTo";
    public static final String Broadcast_SEEK_NEXT_30 = "SeekToNext30";
    public static final String Broadcast_SEEK_PREVIOUS_10 = "SeekToPrevious10";
    public static final String Broadcast_SHOW_TITLE = "SHOW_TITLE";
    public static final String Broadcast_GET_POSITION = "GET_POSITION";
    public static boolean start = false;

    private BookPOJO mBookPOJO;
    private BooksDBModel mBooksDBModel;
    private MenuItem mAddFavorite;
    private MenuItem mRemoveFavorite;
    private final Handler handler = new Handler();
    private AudioDBModel mAudioDBModel;
    private ArrayList<AudioPOJO> mAudioPOJO;
    private String last = "";
    private AudioManager mAudioManager;
    private Context mContext;
    private MediaPlayerService player;
    private boolean serviceBound = false;
    private int curentTrack = 0;

    private AudioModelInterfece mAudioModel;
    private ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            Crashlytics.setBool("onServiceConnected", true);
            MediaPlayerService.LocalBinder binder = (MediaPlayerService.LocalBinder) service;
            player = binder.getService();
            serviceBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            serviceConnection = null;
            serviceBound = false;
        }
    };

    public boolean isServiceBound() {
        return serviceBound;
    }

    public void setServiceBound(boolean serviceBound) {
        this.serviceBound = serviceBound;
    }

    @Override
    public void onCreate(@NonNull BookPOJO bookPOJO, @NonNull Context context) {
        mContext = context;
        mBookPOJO = bookPOJO;
        mBooksDBModel = new BooksDBModel(context);
        mBooksDBModel.addHistory(mBookPOJO);
        mAudioModel = new AudioModel();
        mAudioDBModel = new AudioDBModel(context);
        if (mAudioDBModel.isset(mBookPOJO.getUrl())) {
            last = mAudioDBModel.getName(mBookPOJO.getUrl());
        }
        if (!MediaPlayerService.isPlay()) {
            if (!last.isEmpty()) {
                getViewState().showTitle(last);
            }
        } else {
            getViewState().setImageDrawable(R.drawable.ic_pause);
            Intent broadcastIntent = new Intent(Broadcast_SHOW_TITLE);
            mContext.sendBroadcast(broadcastIntent);
        }
        mAudioManager = (AudioManager) context.getSystemService(AUDIO_SERVICE);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu) {
        mAddFavorite = menu.findItem(R.id.addFavorite);
        mRemoveFavorite = menu.findItem(R.id.removeFavorite);
        if (mBooksDBModel.inFavorite(mBookPOJO)) {
            mAddFavorite.setVisible(false);
            mRemoveFavorite.setVisible(true);
        } else {
            mAddFavorite.setVisible(true);
            mRemoveFavorite.setVisible(false);
        }
    }

    @Override
    public void onDestroy() {
        Crashlytics.setBool("serviceBound", serviceBound);
        if (serviceBound) {
            try {
                mContext.unbindService(serviceConnection);
            } catch (IllegalArgumentException ignored) {

            }
            if (!MediaPlayerService.isPlay() && player != null) {
                player.stopSelf();
            }
        }

    }

    @Override
    public void onOptionsMenuItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.addFavorite:
                if (!mBooksDBModel.inFavorite(mBookPOJO)) {
                    mBooksDBModel.addFavorite(mBookPOJO);
                    mAddFavorite.setVisible(false);
                    mRemoveFavorite.setVisible(true);
                }
                break;
            case R.id.removeFavorite:
                if (mBooksDBModel.inFavorite(mBookPOJO)) {
                    mBooksDBModel.removeFavorite(mBookPOJO);
                    mAddFavorite.setVisible(true);
                    mRemoveFavorite.setVisible(false);
                }
                break;
            case R.id.refresh:
                getViewState().refreshActivity();
                break;
            case R.id.share:
                getViewState().shareTextUrl();
                break;
            case R.id.addMainScreen:
                getViewState().addToMainScreen();
                break;
            case R.id.openSite:
                Intent browserIntent = new Intent(Intent.ACTION_VIEW,
                        Uri.parse(mBookPOJO.getUrl()));
                mContext.startActivity(browserIntent);
                break;
        }
    }

    @Override
    public void onItemSelected(View view, int position) {
        if (mAudioDBModel.isset(mBookPOJO.getUrl())) {
            mAudioDBModel.remove(mBookPOJO.getUrl());
        }
        if (mAudioPOJO != null) {
            mAudioDBModel.add(mBookPOJO.getUrl(), mAudioPOJO.get(position).getName());
            last = mAudioPOJO.get(position).getName();
            getViewState().showTitle(last);
            curentTrack = position;
            playAudio(position);
        }

    }

    private void playAudio(int audioIndex) {
        StorageUtil storage = new StorageUtil(mContext.getApplicationContext());
        storage.storeAudioIndex(audioIndex);
        //Check is service is active
        if (!serviceBound) {
            Crashlytics.setBool("playAudio", true);
            storage.storeAudio(mAudioPOJO);
            storage.storeUrlBook(mBookPOJO.getUrl());
            storage.storeImageUrl(mBookPOJO.getPhoto());
            //Store Serializable audioList to SharedPreferences
            Intent playerIntent = new Intent(mContext, MediaPlayerService.class);
            playerIntent.setAction("start");
            mContext.startService(playerIntent);
            mContext.bindService(playerIntent, serviceConnection, Context.BIND_AUTO_CREATE);
        } else {
            Intent broadcastIntent = new Intent(Broadcast_PLAY_NEW_AUDIO);
            mContext.sendBroadcast(broadcastIntent);
        }
    }


    @Override
    public void getAudio() {
        getViewState().showProgres(true);
        mAudioModel.getAudio(mBookPOJO.getUrl())
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Observer<ArrayList<AudioPOJO>>() {
                    @Override
                    public void onSubscribe(Disposable d) {
                    }

                    @Override
                    public void onNext(ArrayList<AudioPOJO> bookPOJOS) {
                        mAudioPOJO = bookPOJOS;
                        getViewState().showData(mAudioPOJO);
                    }

                    @Override
                    public void onError(Throwable e) {
                        Log.d(TAG, e.getMessage());
                    }

                    @Override
                    public void onComplete() {
                        Log.d(TAG, "onComplete");

                        AudioPOJO pojo = null;
                        for (int i = 0; i < mAudioPOJO.size(); i++) {
                            if (mAudioPOJO.get(i).getName().equals(last)) {
                                pojo = mAudioPOJO.get(i);
                                pojo.setTime(mAudioDBModel.getTime(mBookPOJO.getUrl()));
                                curentTrack = i;
                            }
                        }
                        if (pojo == null) {
                            pojo = mAudioPOJO.get(0);
                            curentTrack = 0;
                            if (!MediaPlayerService.isPlay()) {
                                getViewState().showTitle(pojo.getName());
                            }
                        }

                        if (!MediaPlayerService.isPlay()) {
                            start = false;
                            playAudio(curentTrack);
                        } else {
                            start = true;
                        }
                        getViewState().setSelected(curentTrack,
                                mAudioPOJO.get(curentTrack).getName());


                        getViewState().showProgres(false);
                    }
                });
    }

    @Override
    public void buttomPlayClick(View view) {
        Intent broadcastIntent = new Intent(Broadcast_PLAY);
        mContext.sendBroadcast(broadcastIntent);
    }


    @Override
    public void buttomPreviousClick(View view) {
        if (mAudioPOJO.size() > 0) {
            curentTrack--;
            Intent broadcastIntent = new Intent(Broadcast_PLAY_PREVIOUS);
            mContext.sendBroadcast(broadcastIntent);
        }
    }

    @Override
    public void buttomNextClick(View view) {
        if (mAudioPOJO.size() - 1 > curentTrack) {
            curentTrack++;
            Intent broadcastIntent = new Intent(Broadcast_PLAY_NEXT);
            mContext.sendBroadcast(broadcastIntent);
        }

    }

    @Override
    public void buttomRewindClick(View view) {
        Intent broadcastIntent = new Intent(Broadcast_SEEK_PREVIOUS_10);
        mContext.sendBroadcast(broadcastIntent);
    }

    @Override
    public void buttomForwardClick(View view) {
        Intent broadcastIntent = new Intent(Broadcast_SEEK_NEXT_30);
        mContext.sendBroadcast(broadcastIntent);
    }

    @Override
    public void seekChange(View view) {
        SeekBar sb = (SeekBar) view;
        Intent broadcastIntent = new Intent(Broadcast_SEEK_TO);
        broadcastIntent.putExtra("postion", sb.getProgress());
        mContext.sendBroadcast(broadcastIntent);
    }

    @Override
    public void onOrintationChangeListner() {
        if (MediaPlayerService.isPlay()) {
            getViewState().setImageDrawable(R.drawable.ic_pause);
            Intent broadcastIntent = new Intent(Broadcast_SHOW_TITLE);
            mContext.sendBroadcast(broadcastIntent);
        } else {
            getViewState().setImageDrawable(R.drawable.ic_play);
            Intent broadcastIntent = new Intent(Broadcast_GET_POSITION);
            mContext.sendBroadcast(broadcastIntent);
        }
    }
}
