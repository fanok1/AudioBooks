package com.fanok.audiobooks.presenter;

import static com.fanok.audiobooks.Consts.getAttributeColor;
import static com.fanok.audiobooks.util.DownloadUtil.getDownloadManager;

import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
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
import androidx.annotation.OptIn;
import androidx.appcompat.app.AlertDialog;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.offline.Download;
import androidx.media3.exoplayer.offline.DownloadIndex;
import androidx.media3.exoplayer.offline.DownloadManager;
import androidx.preference.PreferenceManager;
import com.arellomobile.mvp.InjectViewState;
import com.arellomobile.mvp.MvpPresenter;
import com.fanok.audiobooks.BookRemovedException;
import com.fanok.audiobooks.Consts;
import com.fanok.audiobooks.EncodingExeption;
import com.fanok.audiobooks.R;
import com.fanok.audiobooks.activity.SleepTimerActivity;
import com.fanok.audiobooks.interface_pacatge.book_content.Activity;
import com.fanok.audiobooks.interface_pacatge.book_content.ActivityPresenter;
import com.fanok.audiobooks.interface_pacatge.book_content.AudioModelInterfece;
import com.fanok.audiobooks.model.AudioDBModel;
import com.fanok.audiobooks.model.AudioListDBModel;
import com.fanok.audiobooks.model.AudioModel;
import com.fanok.audiobooks.model.BooksDBModel;
import com.fanok.audiobooks.model.OtherArtistModel;
import com.fanok.audiobooks.model.OtherSourceModel;
import com.fanok.audiobooks.pojo.AudioListPOJO;
import com.fanok.audiobooks.pojo.AudioPOJO;
import com.fanok.audiobooks.pojo.BookPOJO;
import com.fanok.audiobooks.pojo.OtherArtistPOJO;
import com.fanok.audiobooks.pojo.StorageUtil;
import com.fanok.audiobooks.service.Checkpoint;
import com.fanok.audiobooks.service.MediaPlayerService;
import com.fanok.audiobooks.service.PlaybackSettings;
import com.fanok.audiobooks.util.DownloadUtil;
import com.fanok.audiobooks.util.ErrorReport;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import io.reactivex.Observer;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;


@InjectViewState
public class BookPresenter extends MvpPresenter<Activity> implements ActivityPresenter {
    private static final String TAG = "BookPresenter";

    public static final String Broadcast_PLAY_PREVIOUS = "PlayPriveos";
    public static final String Broadcast_PLAY_NEXT = "PlayNext";
    /** Toggles: the play/pause button and the PLAY_PAUSE key. */
    public static final String Broadcast_PLAY = "Play";
    /** The PLAY key: plays, never pauses. */
    public static final String Broadcast_RESUME = "Resume";
    /** The PAUSE key: pauses, never plays. */
    public static final String Broadcast_PAUSE = "Pause";
    public static final String Broadcast_SEEK_TO = "SeekTo";
    public static final String Broadcast_SEEK_NEXT_30 = "SeekToNext30";

    public static final String Broadcast_SEEK_PREVIOUS_10 = "SeekToPrevious10";

    public static final String Broadcast_SHOW_TITLE = "SHOW_TITLE";

    public static final String Broadcast_SET_SPEED = "SET_SPEED";

    public static final String Broadcast_Equalizer = "Equalizer";

    private final BookPOJO mBookPOJO;

    private AudioDBModel mAudioDBModel;

    private AudioListDBModel mAudioListDBModel;

    private final OtherSourceModel mOtherSourceModel;

    private AudioModelInterfece mAudioModel;

    private ArrayList<AudioPOJO> mAudioPOJO;

    /** Index of each chapter of {@link #mAudioPOJO} in the book's full list when it is the offline subset, else null. */
    private int[] mOrdinals;

    /** Legacy last-chapter title, shown until the chapter list is loaded; never used to resume. */
    private String last = "";

    private BooksDBModel mBooksDBModel;

    private final CompositeDisposable mDisposables = new CompositeDisposable();

    private static float speed = 1;

    /** The open speed dialog, or null; it belongs to the current screen and goes with it. */
    private AlertDialog mSpeedDialog;

    private boolean mIsOfflineFallback = false;

    private Throwable mLastError = null;

    private final ServiceConnection serviceConnection;

    //the context the service was bound through; unbinding must use the very same one
    private Context bindContext;

    private Context mContext;

    private boolean error = false;

    private SharedPreferences pref;

    private ArrayList<OtherArtistPOJO> mOtherArtistPOJOS;

    /** The other narrators are being loaded or were loaded; the description asks on every show. */
    private boolean mOtherArtistsRequested = false;

    private int state = BottomSheetBehavior.STATE_COLLAPSED;

    public int getState() {
        return state;
    }

    public BookPresenter(@NonNull BookPOJO bookPOJO, @NonNull Context context) {
        mBookPOJO = bookPOJO;
        mBooksDBModel = new BooksDBModel(context);
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    mBooksDBModel.addHistory(mBookPOJO);
                } catch (Exception ignored) {}
            }
        }).start();
        mAudioDBModel = new AudioDBModel(context);
        mAudioModel = new AudioModel(context);
        mAudioListDBModel = new AudioListDBModel(context);
        mOtherSourceModel = new OtherSourceModel();
        pref = PreferenceManager.getDefaultSharedPreferences(context);
        mContext = context;

        //commands go through MediaPlayerService.start and broadcasts; the binding only ties the service to this screen
        serviceConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
            }
        };
    }

    public ArrayList<OtherArtistPOJO> getOtherArtistPOJOS() {
        return mOtherArtistPOJOS;
    }

    /** Loads the book's other narrations once; their tab is shown only when there is at least one. */
    public void loadOtherArtists() {
        if (mOtherArtistsRequested) {
            return;
        }
        mOtherArtistsRequested = true;
        mDisposables.add(new OtherArtistModel().getOtherArtist(mBookPOJO)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(artists -> {
                    if (!artists.isEmpty()) {
                        getViewState().showOtherArtist(artists);
                    }
                }, e -> {
                    Log.d(TAG, "other narrators not loaded: " + e.getMessage());
                    mOtherArtistsRequested = false; // the next description show tries again
                }));
    }

    /** Other narrations found elsewhere (the description's own search): shown once, no load repeats them. */
    public void otherArtistsLoaded(@NonNull ArrayList<OtherArtistPOJO> artists) {
        if (mOtherArtistsRequested || artists.isEmpty()) {
            return;
        }
        mOtherArtistsRequested = true;
        getViewState().showOtherArtist(artists);
    }

    public static void setSpeedWithoutBroadcast(float value) {
        speed = PlaybackSettings.clampSpeed(value);
    }

    public static float getSpeed() {
        return speed;
    }

    private void setSpeed(float value) {
        value = PlaybackSettings.clampSpeed(value);
        speed = value;
        new StorageUtil(mContext).storeSpeed(value);
        playAtSpeed(value);
    }

    /** Plays at this speed without keeping it: the speed dialog lets it be heard before OK. */
    private void playAtSpeed(float value) {
        Intent intent = new Intent(Broadcast_SET_SPEED);
        intent.putExtra("speed", PlaybackSettings.clampSpeed(value));
        getViewState().broadcastSend(intent);
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
    }

    @Override
    public void onCreateOptionsMenu(Menu menu) {
        if (mBookPOJO != null && mBooksDBModel != null) {
            getViewState().setIsFavorite(mBooksDBModel.inFavorite(mBookPOJO));
        }
    }

    @Override
    public void onStop() {
        //the service binding is released in unbindPlayerService() when the screen is really finished:
        //unbinding on every stop (rotation, opening another screen) would release a paused player
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

    @Override
    public void onItemSelected(View view, int position) {
        if (mAudioPOJO != null && position >= 0 && position < mAudioPOJO.size()) {
            last = mAudioPOJO.get(position).getName();
            getViewState().showTitle(last);
            playAudio(position, 0, true);
        }
    }

    @Override
    public void onOptionsMenuItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.addFavorite) {
            if (!mBooksDBModel.inFavorite(mBookPOJO)) {
                mBooksDBModel.addFavorite(mBookPOJO);
                getViewState().setIsFavorite(true);
            }
        } else if (itemId == R.id.removeFavorite) {
            if (mBooksDBModel.inFavorite(mBookPOJO)) {
                mBooksDBModel.removeFavorite(mBookPOJO);
                getViewState().setIsFavorite(false);
            }
        } else if (itemId == R.id.refresh) {
            getViewState().refreshActivity();
        } else if (itemId == R.id.share) {
            getViewState().shareTextUrl();
        } else if (itemId == R.id.addMainScreen) {
            getViewState().addToMainScreen(mBookPOJO);
        } else if (itemId == R.id.openSite) {
            Intent browserIntent = new Intent(Intent.ACTION_VIEW,
                    Uri.parse(mBookPOJO.getUrl()));
            getViewState().activityStart(browserIntent);
        } else if (itemId == R.id.sleep_timer) {
            Intent sleepIntent = new Intent(mContext, SleepTimerActivity.class);
            getViewState().activityStart(sleepIntent);
        } else if (itemId == R.id.equalizer) {
            Intent breadCast = new Intent(Broadcast_Equalizer);
            getViewState().broadcastSend(breadCast);
        } else if (itemId == R.id.settings) {
            getViewState().startMainActivity(Consts.FRAGMENT_SETTINGS);
        }
    }

    /** Called by Moxy when the screen is gone for good; playback is the service's business and goes on. */
    @Override
    public void onDestroy() {
        mDisposables.dispose();
        unbindPlayerService();
        mAudioDBModel.closeDB();
        mAudioListDBModel.closeDB();
        mBooksDBModel.closeDB();
        super.onDestroy();
    }

    private void onCompleteAudio() {
        if (mAudioPOJO != null && !mAudioPOJO.isEmpty()) {
            if (mAudioPOJO.size() == 1 && mAudioPOJO.get(0).getUrl() != null && mAudioPOJO.get(0).getUrl().isEmpty()) {
                getViewState().showToast(mAudioPOJO.get(0).getName());
                return;
            }
            Log.d(TAG, "onComplete");
            if (!error && !mIsOfflineFallback) {
                ArrayList<AudioListPOJO> chapters = new ArrayList<>();
                for (int i = 0; i < mAudioPOJO.size(); i++) {
                    AudioListPOJO audioListPOJO = new AudioListPOJO();
                    audioListPOJO.setBookUrl(mBookPOJO.getUrl());
                    audioListPOJO.setBookName(mBookPOJO.getName());
                    audioListPOJO.setAudioName(mAudioPOJO.get(i).getName());
                    audioListPOJO.setAudioUrl(mAudioPOJO.get(i).getUrl());
                    audioListPOJO.setTime(mAudioPOJO.get(i).getTime());
                    audioListPOJO.setTimeStart(mAudioPOJO.get(i).getTimeStart());
                    audioListPOJO.setTimeEnd(mAudioPOJO.get(i).getTimeFinish());
                    chapters.add(audioListPOJO);
                }
                mAudioListDBModel.syncChapters(mBookPOJO.getUrl(), chapters);
            }

            if (mBookPOJO.getUrl().equals(MediaPlayerService.currentBookUrl())) {
                //the player already holds this book (e.g. opened from the notification): show it, never restart it
                getViewState().broadcastSend(new Intent(Broadcast_SHOW_TITLE));
                getViewState().showProgres(false);
                return;
            }

            Checkpoint checkpoint = resumePoint();
            int curentTrack = checkpoint.chapterIndex;
            AudioPOJO pojo = mAudioPOJO.get(curentTrack);
            if (!MediaPlayerService.isPlay()) {
                getViewState().showTitle(pojo.getName());
            }

            boolean keepOtherBook = pref.getBoolean("reproductionPref", true) && MediaPlayerService.isPlay();
            if (!keepOtherBook) {
                //one command; autoplay decides the intent, nothing toggles afterwards
                playAudio(curentTrack, checkpoint.positionMs, pref.getBoolean("autoPlayPref", false));
            } else {
                //play now toggles the other book: say once how to switch to this one
                getViewState().showSwitchBookHint();
            }
            getViewState().setSelected(curentTrack, pojo.getName());

            getViewState().showProgres(false);
        }
    }

    /** Where to resume this book: its checkpoint, mapped onto the loaded (possibly offline) chapter list. */
    private Checkpoint resumePoint() {
        String url = mBookPOJO.getUrl();
        Checkpoint saved = new StorageUtil(mContext.getApplicationContext()).loadCheckpoint(url);
        if (saved == null) {
            return new Checkpoint(url, 0, 0);
        }
        if (mOrdinals == null) {
            return saved.clampTo(mAudioPOJO.size());
        }
        for (int i = 0; i < mOrdinals.length; i++) {
            if (mOrdinals[i] == saved.chapterIndex) {
                return new Checkpoint(url, i, saved.positionMs);
            }
        }
        //the chapter has no offline copy: start the first one that has, from its beginning
        return new Checkpoint(url, 0, 0);
    }

    /**
     * The speed is heard while the slider moves; only OK keeps it. Cancel, back or a tap outside return to the
     * speed the dialog opened with.
     */
    @Override
    public void buttonSpeedClick(View view) {

        final int step = 10;
        final float initial = speed;
        final boolean[] confirmed = {false};
        dismissSpeedDialog();

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
                if (fromUser) {
                    playAtSpeed(Float.parseFloat(s));
                }
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

        popDialog.setPositiveButton(R.string.ok,
                (dialog, which) -> {
                    confirmed[0] = true;
                    float s = PlaybackSettings.clampSpeed(Float.parseFloat(textView.getText().toString()));
                    if (s != speed) {
                        setSpeed(s);
                    }
                    dialog.dismiss();
                });
        popDialog.setNegativeButton(R.string.cancel, null);
        popDialog.setOnDismissListener(dialog -> {
            mSpeedDialog = null;
            if (!confirmed[0]) {
                playAtSpeed(initial);
            }
        });

        mSpeedDialog = popDialog.show();
    }

    /** Closes the speed dialog as a cancel would (the screen is going): an unconfirmed speed is not kept playing. */
    public void dismissSpeedDialog() {
        if (mSpeedDialog != null) {
            mSpeedDialog.dismiss();
        }
    }


    @Override
    public void buttomPreviousClick(View view) {
        if (mAudioPOJO != null && !mAudioPOJO.isEmpty()) {
            Intent broadcastIntent = new Intent(Broadcast_PLAY_PREVIOUS);
            getViewState().broadcastSend(broadcastIntent);
        }
    }

    @Override
    public void buttomPlayClick(View view) {
        if (MediaPlayerService.currentBookUrl() == null) {
            //no book in the player: start this one playing, from where it was left
            if (mAudioPOJO != null && !mAudioPOJO.isEmpty()) {
                Checkpoint checkpoint = resumePoint();
                playAudio(checkpoint.chapterIndex, checkpoint.positionMs, true);
            }
            return;
        }
        Intent broadcastIntent = new Intent(Broadcast_PLAY);
        getViewState().broadcastSend(broadcastIntent);
    }

    /** Whether the player holds another book than this loaded one, so holding play may switch to this one. */
    @Override
    public boolean canSwitchToThisBook() {
        String playing = MediaPlayerService.currentBookUrl();
        return playing != null && !playing.equals(mBookPOJO.getUrl()) && mAudioPOJO != null && !mAudioPOJO.isEmpty();
    }

    /** Replaces the other book in the player with this one, started where it was left, as a normal start does. */
    @Override
    public void switchToThisBook() {
        if (!canSwitchToThisBook()) {
            return;
        }
        Checkpoint checkpoint = resumePoint();
        AudioPOJO pojo = mAudioPOJO.get(checkpoint.chapterIndex);
        getViewState().showTitle(pojo.getName());
        getViewState().setSelected(checkpoint.chapterIndex, pojo.getName());
        playAudio(checkpoint.chapterIndex, checkpoint.positionMs, true);
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
    public void seekChange(int progress) {
        Intent broadcastIntent = new Intent(Broadcast_SEEK_TO);
        broadcastIntent.putExtra("postion", progress);
        getViewState().broadcastSend(broadcastIntent);
    }

    @Override
    public void buttomNextClick(View view) {
        if (mAudioPOJO != null && !mAudioPOJO.isEmpty()) {
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

    @UnstableApi
    @Override
    public void dowland(HashSet<String> data) {
        if (mAudioPOJO == null) return;
        DownloadUtil.addDownloads(mContext, mBookPOJO, mAudioPOJO, data);
    }
    @UnstableApi
    @Override
    public void delete(HashSet<String> data) {
        if (data == null || data.isEmpty() || mBookPOJO == null || mBookPOJO.getUrl() == null) return;

        final boolean isImported = mBookPOJO.getUrl().contains("/imported/");
        com.fanok.audiobooks.util.DownloadStorageHelper.deleteChapters(mContext, mBookPOJO.getUrl(), new HashSet<>(data),
                (deleted, total) -> {
                    getViewState().showToast(mContext.getString(R.string.delete_result, deleted, total));
                    if (isImported) {
                        getAudio();
                    } else {
                        getViewState().updateAdapter();
                    }
                });
    }

    @Override
    public void getAudio() {
        error = false;
        mIsOfflineFallback = false;
        mLastError = null;
        getViewState().showProgres(true);

        // For imported/local books — skip network, load from DB directly
        if (mBookPOJO.getUrl() != null && mBookPOJO.getUrl().contains("/imported/")) {
            try {
                ArrayList<AudioListPOJO> list = mAudioListDBModel.get(mBookPOJO.getUrl());
                ArrayList<AudioPOJO> audioPOJOS = new ArrayList<>();
                if (list != null) {
                    for (AudioListPOJO pojo : list) {
                        AudioPOJO audioPOJO = new AudioPOJO();
                        audioPOJO.setName(pojo.getAudioName());
                        audioPOJO.setBookName(pojo.getBookName() != null ? pojo.getBookName() : "");
                        audioPOJO.setUrl(pojo.getAudioUrl() != null ? pojo.getAudioUrl() : "");
                        audioPOJO.setTime(pojo.getTime());
                        audioPOJO.setTimeStart(pojo.getTimeStart());
                        audioPOJO.setTimeFinish(pojo.getTimeEnd());
                        audioPOJOS.add(audioPOJO);
                    }
                }
                mAudioPOJO = audioPOJOS;
                mOrdinals = null;
                getViewState().showData(mAudioPOJO, mBookPOJO.getUrl(), mAudioPOJO.size());
                getViewState().showProgres(false);
                error = false;
                onCompleteAudio();
            } catch (Exception e) {
                Log.e(TAG, "getAudio: imported book error", e);
                getViewState().showProgres(false);
                getViewState().showToast("Ошибка загрузки импортированной книги: " + e.getMessage());
            }
            return;
        }
        if (!Consts.isNetworkAvailable(mContext)) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    loadOfflineLocalAudioSync();
                }
            }, "offline-fallback-thread").start();
            return;
        }

        mAudioModel.getAudio(mBookPOJO.getUrl())
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Observer<ArrayList<AudioPOJO>>() {
                    @Override
                    public void onComplete() {
                        onCompleteAudio();
                    }
                    @UnstableApi
                    @Override
                    public void onError(@NotNull Throwable e) {
                        Log.e(TAG, "getAudio network error: ", e);
                        mLastError = e;
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                loadOfflineLocalAudioSync();
                            }
                        }, "offline-fallback-thread").start();
                    }

                    @Override
                    public void onNext(@NotNull ArrayList<AudioPOJO> bookPOJOS) {
                        mAudioPOJO = bookPOJOS;
                        mOrdinals = null;
                        getViewState().showData(mAudioPOJO, mBookPOJO.getUrl(), mAudioPOJO.size());
                        error = false;
                    }

                    @Override
                    public void onSubscribe(@NotNull Disposable d) {
                        mDisposables.add(d);
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
                        Log.d(TAG, String.valueOf(e.getMessage()));
                    }

                    @Override
                    public void onNext(@NotNull ArrayList<OtherArtistPOJO> bookPOJOS) {
                        mOtherArtistPOJOS = bookPOJOS;
                    }

                    @Override
                    public void onSubscribe(@NotNull Disposable d) {
                        mDisposables.add(d);
                    }
                });
    }

    @OptIn(markerClass = UnstableApi.class)
    private void loadOfflineLocalAudioSync() {
        ArrayList<AudioPOJO> localAudio = new ArrayList<>();
        ArrayList<Integer> localOrdinals = new ArrayList<>();

        boolean foundSaved = false;
        int rowsTotal = 0;
        if (mBookPOJO != null && mBookPOJO.getUrl() != null) {
            try {
                java.util.List<com.fanok.audiobooks.room.BooksAudioEntity> savedList = mAudioListDBModel.getRows(mBookPOJO.getUrl());
                rowsTotal = savedList.size();
                if (!savedList.isEmpty()) {
                    DownloadIndex downloadIndex = null;
                    try {
                        downloadIndex = getDownloadManager().getDownloadIndex();
                    } catch (Exception ignored) {}

                    java.util.List<com.fanok.audiobooks.download.DownloadIds.Entry> entries =
                            DownloadUtil.entriesOf(mBookPOJO.getUrl(), savedList);
                    for (int j = 0; j < savedList.size(); j++) {
                        com.fanok.audiobooks.room.BooksAudioEntity row = savedList.get(j);
                        // Only the chapter's own row identifies its copy: the recorded export, its own local
                        // file, or the completed Media3 download of its remote URL.
                        String resolvedUrl = com.fanok.audiobooks.util.DownloadStorageHelper.localCopyOf(mContext, row);
                        if (resolvedUrl == null && downloadIndex != null
                                && com.fanok.audiobooks.util.DeletionPlanner.isRemote(row.urlAudio)) {
                            Download download = DownloadUtil.find(downloadIndex, entries.get(j), row.urlAudio);
                            if (download != null && download.state == Download.STATE_COMPLETED) resolvedUrl = row.urlAudio;
                        }

                        if (resolvedUrl != null) {
                            // an exported copy is exactly its chapter: the stream's clipping no longer applies
                            boolean ownCopy = resolvedUrl.equals(row.localUri);
                            AudioPOJO audioPOJO = new AudioPOJO();
                            audioPOJO.setName(row.nameAudio);
                            audioPOJO.setBookName(row.booksName);
                            audioPOJO.setUrl(resolvedUrl);
                            audioPOJO.setTime(row.time);
                            audioPOJO.setTimeStart(ownCopy ? -1 : row.timeStart);
                            audioPOJO.setTimeFinish(ownCopy ? -1 : row.timeEnd);
                            localAudio.add(audioPOJO);
                            //rows come in book order: j is the chapter's index in the full list
                            localOrdinals.add(j);
                        }
                    }

                    if (!localAudio.isEmpty()) {
                        foundSaved = true;
                    }
                }
            } catch (Exception ex) {
                Log.w(TAG, "getAudio: offline fallback failed", ex);
            }
        }
        final boolean finalFoundSaved = foundSaved;
        final int finalRowsTotal = rowsTotal;
        final ArrayList<AudioPOJO> finalLocalAudio = localAudio;
        final int[] finalOrdinals = new int[localOrdinals.size()];
        for (int i = 0; i < finalOrdinals.length; i++) {
            finalOrdinals[i] = localOrdinals.get(i);
        }

        new android.os.Handler(android.os.Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                mIsOfflineFallback = true;
                mAudioPOJO = finalLocalAudio;
                mOrdinals = finalOrdinals;
                // only the held chapters are listed: the book's status counts them against all its saved rows
                getViewState().showData(mAudioPOJO, mBookPOJO.getUrl(), finalRowsTotal);
                getViewState().showProgres(false);
                if (!finalLocalAudio.isEmpty()) {
                    if (mLastError instanceof BookRemovedException) {
                        getViewState().showLongToast(R.string.book_taken_down_partial);
                    }
                    error = false;
                    onCompleteAudio();
                } else if (mLastError instanceof BookRemovedException) {
                    getViewState().showLongToast(R.string.book_taken_down);
                    error = true;
                } else {
                    String errDetail = mLastError != null ? (" (" + mLastError.getClass().getSimpleName() + (mLastError.getMessage() != null ? ": " + mLastError.getMessage() : "") + ")") : "";
                    getViewState().showLoadError(mContext.getString(R.string.error_load_data)
                            + errDetail + "\n" + mContext.getString(R.string.go_offline),
                            ErrorReport.of(mLastError, mBookPOJO.getUrl()));
                    error = true;
                }
            }
        });
    }

    /** Binds through the application context; {@link #unbindPlayerService()} releases it through the same one. */
    private void bindPlayerService(Intent playerIntent) {
        if (bindContext != null) {
            return; //already bound: a single binding, released exactly once
        }
        Context app = mContext.getApplicationContext();
        app.bindService(playerIntent, serviceConnection, Context.BIND_AUTO_CREATE);
        bindContext = app;
    }

    /** Releases the binding made by {@link #playAudio}; safe to call any number of times. */
    private void unbindPlayerService() {
        Context app = bindContext;
        if (app == null) {
            return;
        }
        bindContext = null;
        try {
            app.unbindService(serviceConnection);
        } catch (IllegalArgumentException e) {
            Log.w(TAG, "service was not bound", e);
        }
    }

    /** Hands the book to the player in one command: this chapter, this position, this intent. */
    private void playAudio(int audioIndex, long positionMs, boolean play) {
        if (mAudioPOJO == null || audioIndex < 0 || audioIndex >= mAudioPOJO.size()) {
            return;
        }
        String url = mAudioPOJO.get(audioIndex).getUrl();
        if (url == null || url.isEmpty()) {
            getViewState().showToast(mAudioPOJO.get(audioIndex).getName());
            return;
        }
        try {
            MediaPlayerService.start(mContext, new MediaPlayerService.Request(mBookPOJO, mBookPOJO.getUrl(),
                    mAudioPOJO, mOrdinals, audioIndex, positionMs, play));
        } catch (RuntimeException e) {
            Log.e(TAG, "Failed to start the player", e);
            return;
        }
        bindPlayerService(new Intent(mContext, MediaPlayerService.class));
    }
}
