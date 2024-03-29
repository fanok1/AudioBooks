package com.fanok.audiobooks.presenter;

import static com.fanok.audiobooks.Consts.getAttributeColor;
import static com.fanok.audiobooks.Consts.isServiceRunning;
import static com.fanok.audiobooks.activity.BookActivity.Broadcast_PLAY_NEW_AUDIO;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
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
import androidx.preference.PreferenceManager;
import com.arellomobile.mvp.InjectViewState;
import com.arellomobile.mvp.MvpPresenter;
import com.fanok.audiobooks.Consts;
import com.fanok.audiobooks.EncodingExeption;
import com.fanok.audiobooks.MyInterstitialAd;
import com.fanok.audiobooks.R;
import com.fanok.audiobooks.Url;
import com.fanok.audiobooks.activity.PopupClearSaved;
import com.fanok.audiobooks.activity.SleepTimerActivity;
import com.fanok.audiobooks.interface_pacatge.book_content.Activity;
import com.fanok.audiobooks.interface_pacatge.book_content.ActivityPresenter;
import com.fanok.audiobooks.interface_pacatge.book_content.AudioModelInterfece;
import com.fanok.audiobooks.model.AudioDBModel;
import com.fanok.audiobooks.model.AudioListDBModel;
import com.fanok.audiobooks.model.AudioModel;
import com.fanok.audiobooks.model.BooksDBModel;
import com.fanok.audiobooks.model.OtherSourceModel;
import com.fanok.audiobooks.pojo.AudioListPOJO;
import com.fanok.audiobooks.pojo.AudioPOJO;
import com.fanok.audiobooks.pojo.BookPOJO;
import com.fanok.audiobooks.pojo.OtherArtistPOJO;
import com.fanok.audiobooks.pojo.StorageAds;
import com.fanok.audiobooks.pojo.StorageUtil;
import com.fanok.audiobooks.service.MediaPlayerService;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.firebase.crashlytics.FirebaseCrashlytics;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.reactivex.Observer;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;


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

    public static final String Broadcast_Equalizer = "Equalizer";

    public static boolean start = false;

    public static boolean resume = false;

    private final BookPOJO mBookPOJO;

    private AudioDBModel mAudioDBModel;

    private AudioListDBModel mAudioListDBModel;

    private final OtherSourceModel mOtherSourceModel;

    private AudioModelInterfece mAudioModel;

    private ArrayList<AudioPOJO> mAudioPOJO;

    private String last = "";

    private BooksDBModel mBooksDBModel;

    private boolean serviceBound = false;

    private static float speed = 1;

    private ServiceConnection serviceConnection;

    private Context mContext;

    private boolean error = false;

    private SharedPreferences pref;

    private ArrayList<OtherArtistPOJO> mOtherArtistPOJOS;

    private int state = BottomSheetBehavior.STATE_COLLAPSED;

    public int getState() {
        return state;
    }

    public BookPresenter(@NonNull BookPOJO bookPOJO, @NonNull Context context) {
        mBookPOJO = bookPOJO;
        mBooksDBModel = new BooksDBModel(context);
        mBooksDBModel.addHistory(mBookPOJO);
        mAudioDBModel = new AudioDBModel(context);
        mAudioModel = new AudioModel(context);
        mAudioListDBModel = new AudioListDBModel(context);
        mOtherSourceModel = new OtherSourceModel();
        pref = PreferenceManager.getDefaultSharedPreferences(context);
        mContext = context;

        serviceConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                serviceBound = true;
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                serviceConnection = null;
                serviceBound = false;
            }
        };
        FirebaseCrashlytics crashlytics = FirebaseCrashlytics.getInstance();
        crashlytics.setCustomKey("urlBooks", bookPOJO.getUrl());
    }

    public ArrayList<OtherArtistPOJO> getOtherArtistPOJOS() {
        return mOtherArtistPOJOS;
    }

    public static void setSpeedWithoutBroadcast(float value) {
        speed = value;
    }

    public static float getSpeed() {
        return speed;
    }

    private void setSpeed(float value) {
        speed = value;
        new StorageUtil(mContext).storeSpeed(value);
        Intent intent = new Intent(Broadcast_SET_SPEED);
        intent.putExtra("speed", value);
        getViewState().broadcastSend(intent);
    }

    public boolean isServiceBound() {
        return serviceBound;
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
            getViewState().setIsFavorite(mBooksDBModel.inFavorite(mBookPOJO));
        }
    }

    @Override
    public void onStop() {
        if (serviceBound) {
            getViewState().myUnbindService(serviceConnection);
            /*if(!MediaPlayerService.isPlay()&&player!=null){
                player.stopSelf();
            }*/
        }

    }

    @Override
    public void onCreate(final Context context) {
        if (mBooksDBModel == null || mContext == null) {
            mBooksDBModel = new BooksDBModel(context);
        }
        if (mAudioDBModel == null || mContext == null) {
            mAudioDBModel = new AudioDBModel(context);
        }
        if (mAudioModel == null || mContext == null) {
            mAudioModel = new AudioModel(context);
        }
        if (mAudioListDBModel == null || mContext == null) {
            mAudioListDBModel = new AudioListDBModel(context);
        }
        if (pref == null || mContext == null) {
            pref = PreferenceManager.getDefaultSharedPreferences(context);
        }
        mContext = context;
    }

    public void setServiceBound(boolean serviceBound) {
        this.serviceBound = serviceBound;
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
            playAudio(position, 0);
        }

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
            case R.id.sleep_timer:
                Intent sleepIntent = new Intent(mContext, SleepTimerActivity.class);
                getViewState().activityStart(sleepIntent);
                break;
            case R.id.equalizer:
                Intent breadCast = new Intent(Broadcast_Equalizer);
                getViewState().broadcastSend(breadCast);
                break;
            case R.id.settings:
                getViewState().startMainActivity(Consts.FRAGMENT_SETTINGS);
                break;
        }
    }

    @Override
    public void onDestroy() {
        mAudioDBModel.closeDB();
        mAudioListDBModel.closeDB();
        mBooksDBModel.closeDB();
        if (mContext != null) {
            Intent broadcastIntent = new Intent(Broadcast_CloseNotPrepered);
            mContext.sendBroadcast(broadcastIntent);
        }
        super.onDestroy();
    }

    @Override
    public void addDecodeData(final String string) {
        ArrayList<AudioPOJO> result = new ArrayList<>();
        JsonElement jsonTree = JsonParser.parseString(string);
        if (jsonTree.isJsonArray()) {
            JsonArray jsonArray = jsonTree.getAsJsonArray();
            for (JsonElement element : jsonArray) {
                if (element.isJsonObject()) {
                    AudioPOJO audioPOJO = new AudioPOJO();
                    JsonObject jsonObject = element.getAsJsonObject();
                    audioPOJO.setName(jsonObject.get("title").getAsString());
                    audioPOJO.setUrl(jsonObject.get("file").getAsString());
                    audioPOJO.setBookName(mBookPOJO.getName());
                    result.add(audioPOJO);
                }
            }
        }
        mAudioPOJO = result;
        getViewState().showData(mAudioPOJO, mBookPOJO.getUrl());
        error = false;
        onCompleteAudio();

    }

    private void onCompleteAudio() {
        if (mAudioPOJO != null && mAudioPOJO.size() > 0) {
            Log.d(TAG, "onComplete");
            int curentTrack = 0;
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

            if (!error) {
                if (mAudioListDBModel.isset(mBookPOJO.getUrl())) {
                    mAudioListDBModel.remove(mBookPOJO.getUrl());
                }
                for (int i = 0; i < mAudioPOJO.size(); i++) {
                    AudioListPOJO audioListPOJO = new AudioListPOJO();
                    audioListPOJO.setBookUrl(mBookPOJO.getUrl());
                    audioListPOJO.setBookName(mBookPOJO.getName());
                    audioListPOJO.setAudioName(mAudioPOJO.get(i).getName());
                    audioListPOJO.setAudioUrl(mAudioPOJO.get(i).getUrl());
                    audioListPOJO.setTime(mAudioPOJO.get(i).getTime());
                    audioListPOJO.setTimeStart(mAudioPOJO.get(i).getTimeStart());
                    audioListPOJO.setTimeEnd(mAudioPOJO.get(i).getTimeFinish());
                    mAudioListDBModel.add(audioListPOJO);
                }
            }

            boolean b = pref.getBoolean("reproductionPref", true);
            boolean autoStart = pref.getBoolean("autoPlayPref", false);

            if (autoStart) {
                if (!MediaPlayerService.isPlay()) {
                    start = true;
                    resume = true;
                    playAudio(curentTrack,
                            mAudioDBModel.getTime(mBookPOJO.getUrl()));
                } else if (!b) {
                    start = true;
                    resume = true;
                    playAudio(curentTrack,
                            mAudioDBModel.getTime(mBookPOJO.getUrl()));
                }

            } else if (!MediaPlayerService.isPlay() || !b) {
                start = false;
                playAudio(curentTrack, mAudioDBModel.getTime(mBookPOJO.getUrl()));
            } else {
                start = true;
            }
            getViewState().setSelected(curentTrack,
                    mAudioPOJO.get(curentTrack).getName());

            getViewState().showProgres(false);
        }
    }

    @Override
    public void buttonSpeedClick(View view) {

        final int step = 10;

        final AlertDialog.Builder popDialog = new AlertDialog.Builder(view.getContext());
        final LinearLayout linearLayout = new LinearLayout(view.getContext());
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        final TextView textView = new TextView(view.getContext());
        textView.setGravity(Gravity.CENTER_HORIZONTAL);
        textView.setText(String.valueOf(speed));
        textView.setTextColor(getAttributeColor(textView.getContext(), R.attr.colorPrimaryText));
        linearLayout.addView(textView);
        final SeekBar seek = new SeekBar(view.getContext());
        seek.setMax(300);
        seek.incrementProgressBy(step);
        seek.setProgress((int) (speed * 100));
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                progress = progress / step;
                progress = progress * step;
                String s = progress / 100 + "." + progress % 100;
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
                    float s = Float.parseFloat(textView.getText().toString());
                    if (s != speed) {
                        setSpeed(s);
                    }
                    dialog.dismiss();
                });

        popDialog.create();
        popDialog.show();
    }


    @Override
    public void buttomPreviousClick(View view) {
        if (mAudioPOJO != null && mAudioPOJO.size() > 0) {
            Intent broadcastIntent = new Intent(Broadcast_PLAY_PREVIOUS);
            getViewState().broadcastSend(broadcastIntent);
        }
    }

    @Override
    public void buttomPlayClick(View view) {
        if (!isServiceRunning(mContext, MediaPlayerService.class) && mAudioPOJO != null
                && mAudioPOJO.size() != 0) {
            serviceBound = false;
            start = true;
            resume = true;
            int curentTrack = 0;
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
    public void buttomNextClick(View view) {
        if (mAudioPOJO != null && mAudioPOJO.size() > 0) {
            Intent broadcastIntent = new Intent(Broadcast_PLAY_NEXT);
            getViewState().broadcastSend(broadcastIntent);
        }

    }

    @Override
    public void setImageDrawable(int id) {
        getViewState().setImageDrawable(id);
    }

    @Override
    public void updateTime(int timeCurrent, int timeEnd, int buffered) {
        getViewState().updateTime(timeCurrent, timeEnd, buffered);
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
        state = BottomSheetBehavior.STATE_COLLAPSED;
        getViewState().stateCollapsed();
    }

    @Override
    public void stateExpanded() {
        state = BottomSheetBehavior.STATE_EXPANDED;
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

    @Override
    public void delete(HashSet<String> data) {
        File[] folders = mContext.getExternalFilesDirs(null);
        for (File folder : folders) {
            if (folder != null) {
                    String source = Consts.getSorceName(mContext, mBookPOJO.getUrl());
                    String filePath = folder.getAbsolutePath() + "/" + source
                            + "/" + mBookPOJO.getAutor()
                            + "/" + mBookPOJO.getArtist()
                            + "/" + mBookPOJO.getName();
                File dir = new File(filePath);
                if (dir.exists() && dir.isDirectory()) {
                    if(source!=mContext.getString(R.string.abook)) {
                        for (String url : data) {
                            File file = new File(dir, url.substring(url.lastIndexOf("/") + 1));
                            if (file.exists()) {
                                if (!file.delete()) {
                                    Log.d(TAG, file + " delete: false");
                                } else {
                                    Log.d(TAG, file + " delete: true");
                                }
                            }
                        }
                    }else {
                        File file = new File(dir, "pl");
                        if (file.exists()) {
                            PopupClearSaved.delete(file);
                        }
                    }
                    if (Objects.requireNonNull(dir.list()).length == 0) {
                        if (!dir.delete()) {
                            Log.d(TAG, dir + " delete: false");
                        } else {
                            Log.d(TAG, dir + " delete: true");
                        }
                    }

                }
                PopupClearSaved.deleteEmtyFolder(folder);
            }
        }
        getViewState().updateAdapter(null);
        getViewState().showToast(R.string.delete_complite);
    }

    @Override
    public void getAudio() {
        error = false;
        getViewState().showProgres(true);
        mAudioModel.getAudio(mBookPOJO.getUrl())
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Observer<ArrayList<AudioPOJO>>() {
                    @Override
                    public void onComplete() {
                        onCompleteAudio();
                    }

                    @Override
                    public void onError(@NotNull Throwable e) {

                        if (e.getClass() == EncodingExeption.class) {
                            getViewState().decode(Objects.requireNonNull(e.getMessage()));

                        } else {

                            Log.d(TAG, Objects.requireNonNull(e.getMessage()));
                            boolean b = pref.getBoolean("offline", false);
                            ArrayList<AudioListPOJO> list = mAudioListDBModel.get(mBookPOJO.getUrl());
                            ArrayList<AudioPOJO> audioPOJOS = new ArrayList<>();
                            File[] folders = mContext.getExternalFilesDirs(null);
                            for (int i = 0; i < list.size(); i++) {
                                AudioListPOJO audioListPOJO = list.get(i);
                                for (File folder : folders) {
                                    File file = null;
                                    if (folder != null) {
                                        String source = Consts.getSorceName(mContext, mBookPOJO.getUrl());
                                        String filePath = folder.getAbsolutePath() + "/" + source
                                                + "/" + mBookPOJO.getAutor()
                                                + "/" + mBookPOJO.getArtist()
                                                + "/" + mBookPOJO.getName();
                                        file = new File(
                                                filePath + "/"
                                                        + audioListPOJO.getAudioUrl().substring(
                                                        audioListPOJO.getAudioUrl().lastIndexOf("/")
                                                                + 1));
                                    }
                                    if ((file != null && file.exists()) || !b) {
                                        AudioPOJO audioPOJO = new AudioPOJO();
                                        audioPOJO.setName(audioListPOJO.getAudioName());
                                        audioPOJO.setBookName(audioListPOJO.getBookName());
                                        audioPOJO.setUrl(audioListPOJO.getAudioUrl());
                                        audioPOJO.setTime(audioListPOJO.getTime());
                                        audioPOJO.setTimeStart(audioListPOJO.getTimeStart());
                                        audioPOJO.setTimeFinish(audioListPOJO.getTimeEnd());
                                        audioPOJOS.add(audioPOJO);
                                        break;
                                    }
                                }
                            }
                            mAudioPOJO = audioPOJOS;
                            getViewState().showData(mAudioPOJO, mBookPOJO.getUrl());
                            getViewState().showToast(mContext.getString(R.string.error_load_data)
                                    + "\n" + mContext.getString(R.string.go_offline));
                            error = true;
                            onComplete();
                        }
                    }

                    @Override
                    public void onNext(@NotNull ArrayList<AudioPOJO> bookPOJOS) {
                        mAudioPOJO = bookPOJOS;
                        getViewState().showData(mAudioPOJO, mBookPOJO.getUrl());
                        error = false;
                    }

                    @Override
                    public void onSubscribe(@NotNull Disposable d) {
                    }
                });

        mOtherSourceModel.getOtherArtist(mBookPOJO)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Observer<ArrayList<OtherArtistPOJO>>() {
                    @Override
                    public void onComplete() {
                        if (!mOtherArtistPOJOS.isEmpty()) {
                            getViewState().showOtherSource();
                        }
                    }

                    @Override
                    public void onError(@NotNull Throwable e) {
                        Log.d(TAG, Objects.requireNonNull(e.getMessage()));
                    }

                    @Override
                    public void onNext(@NotNull ArrayList<OtherArtistPOJO> bookPOJOS) {
                        mOtherArtistPOJOS = bookPOJOS;
                    }

                    @Override
                    public void onSubscribe(@NotNull Disposable d) {
                    }
                });
    }

    public void loadBooks(HashSet<String> data) {
        for (String url : data) {
            if (mBookPOJO.getUrl().contains(Url.SERVER_ABMP3)) {
                getViewState().downloadFileABMP3(url, mBookPOJO);
            } else {
                getViewState().downloadFile(url, mBookPOJO);
            }
        }
    }

    private void playAudio(int audioIndex, int timeStart) {
        StorageUtil storage = new StorageUtil(mContext.getApplicationContext());
        storage.storeAudioIndex(audioIndex);
        if (!serviceBound || !isServiceRunning(mContext, MediaPlayerService.class)) {
            storage.storeAudio(mAudioPOJO);
            storage.storeUrlBook(mBookPOJO.getUrl());
            storage.storeImageUrl(mBookPOJO.getPhoto());
            storage.storeTimeStart(timeStart);
            Intent playerIntent = new Intent(mContext, MediaPlayerService.class);
            playerIntent.setAction("start");
            if (serviceConnection != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    mContext.startForegroundService(playerIntent);
                } else {
                    mContext.startService(playerIntent);
                }
                mContext.bindService(playerIntent, serviceConnection, Context.BIND_AUTO_CREATE);
            } else {
                serviceConnection = new ServiceConnection() {
                    @Override
                    public void onServiceConnected(ComponentName name, IBinder service) {
                        serviceBound = true;
                    }

                    @Override
                    public void onServiceDisconnected(ComponentName name) {
                        serviceConnection = null;
                        serviceBound = false;
                    }
                };
                playAudio(audioIndex, timeStart);
            }
        } else {
            Intent broadcastIntent = new Intent(Broadcast_PLAY_NEW_AUDIO);
            getViewState().broadcastSend(broadcastIntent);
        }
    }
}
