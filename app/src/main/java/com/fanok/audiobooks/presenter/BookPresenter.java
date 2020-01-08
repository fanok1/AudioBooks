package com.fanok.audiobooks.presenter;

import static com.fanok.audiobooks.Consts.isServiceRunning;
import static com.fanok.audiobooks.activity.BookActivity.Broadcast_PLAY_NEW_AUDIO;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.IBinder;
import android.util.Log;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;

import com.arellomobile.mvp.InjectViewState;
import com.arellomobile.mvp.MvpPresenter;
import com.crashlytics.android.Crashlytics;
import com.fanok.audiobooks.MyInterstitialAd;
import com.fanok.audiobooks.R;
import com.fanok.audiobooks.interface_pacatge.book_content.Activity;
import com.fanok.audiobooks.interface_pacatge.book_content.ActivityPresenter;
import com.fanok.audiobooks.interface_pacatge.book_content.AudioModelInterfece;
import com.fanok.audiobooks.model.AudioDBModel;
import com.fanok.audiobooks.model.AudioModel;
import com.fanok.audiobooks.model.BooksDBModel;
import com.fanok.audiobooks.pojo.AudioPOJO;
import com.fanok.audiobooks.pojo.BookPOJO;
import com.fanok.audiobooks.pojo.StorageAds;
import com.fanok.audiobooks.pojo.StorageUtil;
import com.fanok.audiobooks.service.MediaPlayerService;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Objects;

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
    public static final String Broadcast_SET_SPEED = "SET_SPEED";
    public static final String Broadcast_CloseNotPrepered = "CloseNotPrepered";
    public static boolean start = false;
    public static boolean resume = false;

    private BookPOJO mBookPOJO;
    private BooksDBModel mBooksDBModel;
    private AudioDBModel mAudioDBModel;
    private ArrayList<AudioPOJO> mAudioPOJO;
    private String last = "";
    private Context mContext;
    private boolean serviceBound = false;
    private int curentTrack = 0;
    public static float speed = 1;

    private AudioModelInterfece mAudioModel;
    private ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            //Crashlytics.setBool("onServiceConnected", true);
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

    public BookPresenter(@NonNull BookPOJO bookPOJO, @NonNull Context context) {
        mContext = context;
        mBookPOJO = bookPOJO;
        mBooksDBModel = new BooksDBModel(context);
        mBooksDBModel.addHistory(mBookPOJO);
        mAudioModel = new AudioModel();
        mAudioDBModel = new AudioDBModel(context);

    }

    @Override
    protected void onFirstViewAttach() {
        super.onFirstViewAttach();
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
            getViewState().broadcastSend(broadcastIntent);
        }
        getAudio();

        MyInterstitialAd.show();
    }

    @Override
    public void onCreateOptionsMenu(Menu menu) {
        if (mBookPOJO != null && mBooksDBModel != null) {
            if (mBooksDBModel.inFavorite(mBookPOJO)) {
                getViewState().setIsFavorite(true);
            } else {
                getViewState().setIsFavorite(false);
            }
        }
    }

    @Override
    public void onStop() {
        Crashlytics.setBool("serviceBound", serviceBound);
        if (serviceBound) {
            getViewState().myUnbindService(serviceConnection);
            /*if(!MediaPlayerService.isPlay()&&player!=null){
                player.stopSelf();
            }*/
        }

    }

    @Override
    public void onDestroy() {
        mAudioDBModel.closeDB();
        mBooksDBModel.closeDB();
        if (mContext != null) {
            Intent broadcastIntent = new Intent(Broadcast_CloseNotPrepered);
            mContext.sendBroadcast(broadcastIntent);
        }
        super.onDestroy();
    }

    @Override
    public void onOptionsMenuItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.addFavorite:
                if (!mBooksDBModel.inFavorite(mBookPOJO)) {
                    mBooksDBModel.addFavorite(mBookPOJO);
                    getViewState().setIsFavorite(true);
                }
                break;
            case R.id.removeFavorite:
                if (mBooksDBModel.inFavorite(mBookPOJO)) {
                    mBooksDBModel.removeFavorite(mBookPOJO);
                    getViewState().setIsFavorite(false);
                }
                break;
            case R.id.refresh:
                getViewState().refreshActivity();
                break;
            case R.id.share:
                getViewState().shareTextUrl();
                break;
            case R.id.addMainScreen:
                getViewState().addToMainScreen(mBookPOJO);
                break;
            case R.id.openSite:
                Intent browserIntent = new Intent(Intent.ACTION_VIEW,
                        Uri.parse(mBookPOJO.getUrl()));
                getViewState().activityStart(browserIntent);
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
            playAudio(position, 0);
        }

    }

    private void playAudio(int audioIndex, int timeStart) {
        StorageUtil storage = new StorageUtil(mContext.getApplicationContext());
        storage.storeAudioIndex(audioIndex);
        if (!serviceBound || !isServiceRunning(mContext, MediaPlayerService.class)) {
            Crashlytics.setBool("playAudio", true);
            storage.storeAudio(mAudioPOJO);
            storage.storeUrlBook(mBookPOJO.getUrl());
            storage.storeImageUrl(mBookPOJO.getPhoto());
            storage.storeTimeStart(timeStart);
            Intent playerIntent = new Intent(mContext, MediaPlayerService.class);
            playerIntent.setAction("start");
            mContext.startService(playerIntent);
            mContext.bindService(playerIntent, serviceConnection, Context.BIND_AUTO_CREATE);
        } else {
            Intent broadcastIntent = new Intent(Broadcast_PLAY_NEW_AUDIO);
            getViewState().broadcastSend(broadcastIntent);
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
                        Log.d(TAG, Objects.requireNonNull(e.getMessage()));
                    }

                    @Override
                    public void onComplete() {
                        Log.d(TAG, "onComplete");

                        AudioPOJO pojo = null;
                        for (int i = 0; i < mAudioPOJO.size(); i++) {
                            if (mAudioPOJO.get(i).getName().equals(last)) {
                                pojo = mAudioPOJO.get(i);
                                curentTrack = i;
                            }
                        }
                        if (pojo == null) {
                            pojo = mAudioPOJO.get(0);
                            curentTrack = 0;
                            if (!MediaPlayerService.isPlay()) {
                                getViewState().showTitle(pojo.getName());
                            }
                            mAudioDBModel.add(mBookPOJO.getUrl(), pojo.getName());
                        }

                        if (!MediaPlayerService.isPlay()) {
                            start = false;
                            playAudio(curentTrack, mAudioDBModel.getTime(mBookPOJO.getUrl()));
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
        if (!isServiceRunning(mContext, MediaPlayerService.class) && mAudioPOJO != null
                && mAudioPOJO.size() != 0 &&
                curentTrack >= 0 && mAudioPOJO.size() > curentTrack) {
            serviceBound = false;
            start = true;
            resume = true;
            curentTrack = 0;
            for (int i = 0; i < mAudioPOJO.size(); i++) {
                if (mAudioPOJO.get(i).getName().equals(last)) {
                    curentTrack = i;
                }
            }

            playAudio(curentTrack, mAudioDBModel.getTime(mBookPOJO.getUrl()));
        }
        Intent broadcastIntent = new Intent(Broadcast_PLAY);
        getViewState().broadcastSend(broadcastIntent);
    }


    @Override
    public void buttomPreviousClick(View view) {
        if (mAudioPOJO != null && mAudioPOJO.size() > 0) {
            curentTrack--;
            Intent broadcastIntent = new Intent(Broadcast_PLAY_PREVIOUS);
            getViewState().broadcastSend(broadcastIntent);
        }
    }

    @Override
    public void buttomNextClick(View view) {
        if (mAudioPOJO != null && mAudioPOJO.size() - 2 > curentTrack) {
            curentTrack++;
            Intent broadcastIntent = new Intent(Broadcast_PLAY_NEXT);
            getViewState().broadcastSend(broadcastIntent);
        }

    }

    @Override
    public void buttomRewindClick(View view) {
        Intent broadcastIntent = new Intent(Broadcast_SEEK_PREVIOUS_10);
        getViewState().broadcastSend(broadcastIntent);
    }

    @Override
    public void buttomForwardClick(View view) {
        Intent broadcastIntent = new Intent(Broadcast_SEEK_NEXT_30);
        getViewState().broadcastSend(broadcastIntent);
    }

    @Override
    public void seekChange(View view) {
        SeekBar sb = (SeekBar) view;
        Intent broadcastIntent = new Intent(Broadcast_SEEK_TO);
        broadcastIntent.putExtra("postion", sb.getProgress());
        getViewState().broadcastSend(broadcastIntent);
    }

    @Override
    public void buttonSpeedClick(View view) {

        final int step = 25;

        final AlertDialog.Builder popDialog = new AlertDialog.Builder(view.getContext());
        final LinearLayout linearLayout = new LinearLayout(view.getContext());
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        final TextView textView = new TextView(view.getContext());
        textView.setGravity(Gravity.CENTER_HORIZONTAL);
        textView.setText(String.valueOf(speed));
        linearLayout.addView(textView);
        final SeekBar seek = new SeekBar(view.getContext());
        seek.setMax(200);
        seek.incrementProgressBy(step);
        seek.setProgress((int) (speed * 100 - 100));
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                progress = progress / step;
                progress = progress * step;
                String s = progress / 100 + 1 + "." + progress % 100;
                textView.setText(s);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });

        linearLayout.addView(seek);

        popDialog.setIcon(R.drawable.ic_play_speed);
        popDialog.setTitle(view.getContext().getString(R.string.title_set_speed_popup));
        popDialog.setView(linearLayout);


        // Button OK
        popDialog.setPositiveButton("OK",
                (dialog, which) -> {
                    float s = Float.valueOf(textView.getText().toString());
                    if (s != speed) {
                        setSpeed(s);
                    }
                    dialog.dismiss();
                });


        popDialog.create();
        popDialog.show();
    }

    @Override
    public void setImageDrawable(int id) {
        getViewState().setImageDrawable(id);
    }

    @Override
    public void updateTime(int timeCurrent, int timeEnd) {
        getViewState().updateTime(timeCurrent, timeEnd);
    }

    @Override
    public void setSelected(int pos, @NonNull String name) {
        getViewState().setSelected(pos, name);
    }

    @Override
    public void showTitle(@NonNull String title) {
        getViewState().showTitle(title);
    }

    @Override
    public void stateCollapsed() {
        getViewState().stateCollapsed();
    }

    @Override
    public void stateExpanded() {
        getViewState().stateExpanded();
    }

    @Override
    public void stateElse() {
        getViewState().stateElse();
    }

    @Override
    public void dowland(HashSet<String> data) {
        if (!StorageAds.idDisableAds()) {
            getViewState().showShowAdsBeforeDownload();
        } else {
            loadBooks(data);
        }
    }

    public void loadBooks(HashSet<String> data) {
        for (String url : data) {
            getViewState().downloadFile(url, mBookPOJO.getName());
        }
    }


    private void setSpeed(float value) {
        speed = value;
        getViewState().broadcastSend(new Intent(Broadcast_SET_SPEED));
    }
}
