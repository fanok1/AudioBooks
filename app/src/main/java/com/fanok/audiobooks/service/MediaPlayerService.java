package com.fanok.audiobooks.service;

import static com.fanok.audiobooks.activity.BookActivity.Broadcast_SET_IMAGE;
import static com.fanok.audiobooks.activity.BookActivity.Broadcast_SET_PROGRESS;
import static com.fanok.audiobooks.activity.BookActivity.Broadcast_SET_SELECTION;
import static com.fanok.audiobooks.activity.BookActivity.Broadcast_SET_TITLE;
import static com.fanok.audiobooks.activity.BookActivity.Broadcast_SHOW_EQUALIZER;
import static com.fanok.audiobooks.activity.BookActivity.Broadcast_SHOW_GET_PLUS;
import static com.fanok.audiobooks.activity.BookActivity.Broadcast_SHOW_RATING;
import static com.fanok.audiobooks.activity.SleepTimerActivity.bradcast_FinishTimer;
import static com.fanok.audiobooks.activity.SleepTimerActivity.bradcast_UpdateTimer;
import static com.fanok.audiobooks.presenter.BookPresenter.Broadcast_Equalizer;
import static com.fanok.audiobooks.аndroid_equalizer.EqualizerFragment.Broadcast_EqualizerEnabled;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.media.audiofx.BassBoost;
import android.media.audiofx.Equalizer;
import android.media.audiofx.PresetReverb;
import android.media.session.MediaSessionManager;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.SystemClock;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.media.session.MediaButtonReceiver;
import androidx.preference.PreferenceManager;

import com.fanok.audiobooks.MyInterstitialAd;
import com.fanok.audiobooks.PlaybackStatus;
import com.fanok.audiobooks.R;
import com.fanok.audiobooks.activity.BookActivity;
import com.fanok.audiobooks.activity.LoadBook;
import com.fanok.audiobooks.broadcasts.OnCancelBroadcastReceiver;
import com.fanok.audiobooks.model.AudioDBModel;
import com.fanok.audiobooks.pojo.AudioPOJO;
import com.fanok.audiobooks.pojo.StorageAds;
import com.fanok.audiobooks.pojo.StorageUtil;
import com.fanok.audiobooks.presenter.BookPresenter;
import com.fanok.audiobooks.presenter.SleepTimerPresenter;
import com.fanok.audiobooks.аndroid_equalizer.EqualizerModel;
import com.fanok.audiobooks.аndroid_equalizer.Settings;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.audio.AudioAttributes;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.util.Util;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.Target;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.Objects;

public class MediaPlayerService extends Service implements AudioManager.OnAudioFocusChangeListener {


    public static final String ACTION_PLAY = "audioBook.ACTION_PLAY";
    public static final String ACTION_PAUSE = "audioBook.ACTION_PAUSE";
    public static final String ACTION_PREVIOUS = "audioBook.ACTION_PREVIOUS";
    public static final String ACTION_REWIND = "audioBook.ACTION_REWIND";
    public static final String ACTION_FORWARD = "audioBook.ACTION_FAST_FORWARD";
    public static final String ACTION_NEXT = "audioBook.ACTION_NEXT";
    public static final String ACTION_STOP = "audioBook.ACTION_STOP";
    public static final String Broadcast_DELETE_NOTIFICATION = "brodcast.DELETE_NOTIFICATION";
    public static final String Broadcast_CloseIfPause = "brodcast.CloseIfPause";

    private static final String TAG = "MediaPlayerService";
    //AudioPlayer notification ID
    private static final int NOTIFICATION_ID = 101;
    private static final String CHANNEL_ID = "124";
    private static final String CHANNEL_NAME = "Player";
    private static boolean isPlay = false;
    public static final int countAudioWereShowingRatingPopUp = 50;
    private static final int REWIND = 30000;
    private static final int FAST_FORWARD = 30000;

    private static final int countAudioWereShowingActivity = 25;
    private long timeStarting;

    // Binder given to clients
    private final IBinder mBinder = new LocalBinder();
    private final Handler handler1 = new Handler();
    private final Handler handler2 = new Handler();
    //private MediaPlayer mediaPlayer;
    private SimpleExoPlayer mediaPlayer;
    //MediaSession
    private MediaSessionManager mediaSessionManager;
    private MediaSessionCompat mediaSession;
    private MediaControllerCompat.TransportControls transportControls;
    //Used to pause/resume MediaPlayer
    private int resumePosition;
    private int timeDuration;
    //AudioFocus
    private AudioManager audioManager;
    private ArrayList<AudioPOJO> audioList;
    private int audioIndex = -1;
    private AudioPOJO activeAudio;

    private PhoneStateListener phoneStateListener;
    private TelephonyManager telephonyManager;
    private boolean buttonClick;
    private String urlBook;
    private AudioDBModel mAudioDBModel;
    private boolean pause;
    //private boolean prepared = false;
    private String imageUrl = "";
    private Bitmap image = null;
    private int timeStart = 0;
    private StorageUtil storage;
    private CountDownTimer mCountDownTimer;
    private boolean mEqualizerEnabled = true;
    private SharedPreferences mPreferences;

    private Equalizer mEqualizer;
    private BassBoost bassBoost;
    private PresetReverb presetReverb;

    private final BroadcastReceiver equalizer = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Intent broadcastIntent = new Intent(Broadcast_SHOW_EQUALIZER);
            if (mediaPlayer != null) {
                broadcastIntent.putExtra("id", mediaPlayer.getAudioSessionId());
            }
            sendBroadcast(broadcastIntent);
        }
    };

    public static int getNotificationId() {
        return NOTIFICATION_ID;
    }

    private final BroadcastReceiver showTitle = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Intent broadcastIntent = new Intent(Broadcast_SET_TITLE);
            broadcastIntent.putExtra("title", audioList.get(audioIndex).getName());
            sendBroadcast(broadcastIntent);
        }
    };
    private final BroadcastReceiver getPosition = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Intent broadcastIntent = new Intent(Broadcast_SET_PROGRESS);
            broadcastIntent.putExtra("timeCurrent", resumePosition);
            broadcastIntent.putExtra("timeEnd", timeDuration);
            sendBroadcast(broadcastIntent);
        }
    };
    private final BroadcastReceiver closeNotPrerepred = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "onReceive: closeNotPrerepred");
            if (!isPrepared()) {
                stopMediaNotSave();
                stopForeground(true);
                stopSelf();
            }
        }
    };
    private final BroadcastReceiver closeIfPause = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!isPlaying()) {
                stopMedia();
                stopForeground(true);
                stopSelf();
            }
        }
    };
    private final BroadcastReceiver equalizerEnabled = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            mEqualizerEnabled = intent.getBooleanExtra("enabled", false);
            if (!mEqualizerEnabled) {
                stopEqualizer();
            } else {
                storage.loadEqualizerSettings();
                startEqualizer();
            }
        }
    };
    private final BroadcastReceiver sleepTimer = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (SleepTimerPresenter.isTimerStarted()) {
                //startTimer
                long time = intent.getLongExtra("time", 0);
                mCountDownTimer = new CountDownTimer(time, 1000) {
                    @Override
                    public void onTick(long l) {
                        Intent broadcastIntent = new Intent(bradcast_UpdateTimer);
                        broadcastIntent.putExtra("time", l);
                        sendBroadcast(broadcastIntent);
                    }

                    @Override
                    public void onFinish() {
                        SleepTimerPresenter.setTimerStarted(false);
                        sendBroadcast(new Intent(bradcast_FinishTimer));
                        stopMedia();
                        stopForeground(true);
                        stopSelf();
                    }
                };
                mCountDownTimer.start();
            } else {
                //stopTimer
                if (mCountDownTimer != null) {
                    mCountDownTimer.cancel();
                }
            }
        }
    };
    private long pauseTime = 0;
    /**
     * ACTION_AUDIO_BECOMING_NOISY -- change in audio outputs
     */
    private final BroadcastReceiver becomingNoisyReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            //pause audio on ACTION_AUDIO_BECOMING_NOISY
            pauseMedia();
        }
    };
    /**
     * Play new Audio
     */
    private final BroadcastReceiver playNewAudio = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            buttonClick = true;
            BookPresenter.resume = false;
            //Get the new media index form SharedPreferences
            audioIndex = storage.loadAudioIndex();
            if (audioIndex != -1 && audioIndex < audioList.size()) {
                //index is in a valid range
                activeAudio = audioList.get(audioIndex);
                sendBroadcastItemSelected(audioIndex);
            } else {
                stopSelf();
            }

            //A PLAY_NEW_AUDIO action received
            //reset mediaPlayer to play the new Audio
            stopMediaNotSave();
            initMediaPlayer();
            updateMetaData();
            buildNotification(PlaybackStatus.PLAYING);
        }
    };
    private final BroadcastReceiver playPriveos = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            buttonClick = true;
            skipToPrevious();
            updateMetaData();
        }
    };
    private final BroadcastReceiver playNext = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            buttonClick = true;
            skipToNext();
            updateMetaData();
        }
    };
    private final BroadcastReceiver play = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (isPlaying()) {
                pauseMedia();
            } else if (mediaPlayer != null) {
                if (isPrepared()) {
                    resumeMedia();
                } else {
                    Toast.makeText(context, getString(R.string.worning_loading_not_finish),
                            Toast.LENGTH_SHORT).show();
                }
            } else {
                initMediaPlayer();
            }
        }
    };
    private final BroadcastReceiver seekTo = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            boolean b = mPreferences.getBoolean("seekToPausePref", true);
            if (b) {
                resumeMedia();
            }
            seekTo(intent.getIntExtra("postion", 0));
        }
    };
    private final BroadcastReceiver seekToNext30Sec = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            fastForward(FAST_FORWARD);
        }
    };
    private final BroadcastReceiver seekToPrevious10Sec = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            rewind(REWIND);
        }
    };
    private final BroadcastReceiver setSpeed = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && mediaPlayer != null) {
                resumeMedia();
                try {
                    PlaybackParameters param = new PlaybackParameters(BookPresenter.getSpeed());
                    mediaPlayer.setPlaybackParameters(param);
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
    };

    private void rewind(int ms) {
        if (mediaPlayer != null) {
            seekTo(mediaPlayer.getCurrentPosition() - ms);
            boolean b = mPreferences.getBoolean("seekToPausePref", true);
            if (!isPlaying() && b) {
                resumeMedia();
            }
        }
    }

    private void fastForward(int ms) {
        if (mediaPlayer != null) {
            seekTo(mediaPlayer.getCurrentPosition() + ms);
            boolean b = mPreferences.getBoolean("seekToPausePref", true);
            if (!isPlaying() && b) {
                resumeMedia();
            }
        }
    }

    private void startEqualizer() {
        if (mediaPlayer == null) return;
        stopEqualizer();
        int audioSesionId = mediaPlayer.getAudioSessionId();
        if (audioSesionId > 0) {
            if (Settings.equalizerModel == null) {
                Settings.equalizerModel = new EqualizerModel();
                Settings.equalizerModel.setReverbPreset(PresetReverb.PRESET_NONE);
                Settings.equalizerModel.setBassStrength((short) (1000 / 19));
            }

            try {
                mEqualizer = new Equalizer(0, audioSesionId);

                bassBoost = new BassBoost(0, audioSesionId);
                bassBoost.setEnabled(Settings.isEqualizerEnabled);
                BassBoost.Settings bassBoostSettingTemp = bassBoost.getProperties();
                BassBoost.Settings bassBoostSetting = new BassBoost.Settings(
                        bassBoostSettingTemp.toString());
                bassBoostSetting.strength = Settings.equalizerModel.getBassStrength();
                bassBoost.setProperties(bassBoostSetting);

                presetReverb = new PresetReverb(0, audioSesionId);
                presetReverb.setPreset(Settings.equalizerModel.getReverbPreset());
                presetReverb.setEnabled(Settings.isEqualizerEnabled);

                mEqualizer.setEnabled(Settings.isEqualizerEnabled);

                if (Settings.presetPos == 0) {
                    for (short bandIdx = 0; bandIdx < mEqualizer.getNumberOfBands(); bandIdx++) {
                        mEqualizer.setBandLevel(bandIdx, (short) Settings.seekbarpos[bandIdx]);
                    }
                } else {
                    mEqualizer.usePreset((short) Settings.presetPos);
                }
            } catch (RuntimeException e) {
                stopEqualizer();
            }
        }
    }

    private void stopEqualizer() {
        if (mEqualizer != null) {
            mEqualizer.release();
            mEqualizer = null;
        }

        if (bassBoost != null) {
            bassBoost.release();
            bassBoost = null;
        }

        if (presetReverb != null) {
            presetReverb.release();
            presetReverb = null;
        }
    }


    public static boolean isPlay() {
        return isPlay;
    }

    /**
     * Service lifecycle methods
     */
    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        // Perform one-time setup procedures

        // Manage incoming phone calls during playback.
        // Pause MediaPlayer on incoming call,
        // Resume on hangup.
        callStateListener();
        //ACTION_AUDIO_BECOMING_NOISY -- change in audio outputs -- BroadcastReceiver
        registerBecomingNoisyReceiver();
        //Listen for new Audio to play -- BroadcastReceiver
        register_playNewAudio();
        register_playPriveos();
        register_playNext();
        register_play();
        register_seekTo();
        register_seekToNext30Sec();
        register_seekToPrevious10Sec();
        register_showTitle();
        register_getPosition();
        register_setSpeed();
        register_closeIfNotPrepered();
        register_closeIfPause();
        register_sleepTimer();
        register_equalizer();
        register_equalizerEnabled();
        timeStarting = new Date().getTime();
    }

    //The system calls this method when an activity, requests the service be started
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        storage = new StorageUtil(getApplicationContext());

        try {
            //Load data from SharedPreferences
            urlBook = storage.loadUrlBook();
            audioList = storage.loadAudio();
            audioIndex = storage.loadAudioIndex();
            timeStart = storage.loadTimeStart();
            String urlImage = storage.loadImageUrl();
            mAudioDBModel = new AudioDBModel(this);
            if (urlImage.isEmpty()) {
                imageUrl = "";
                image = null;
            } else if (!imageUrl.equals(urlImage)) {
                imageUrl = urlImage;
                image = null;
            }


            if (audioIndex != -1 && audioIndex < audioList.size()) {
                //index is in a valid range
                activeAudio = audioList.get(audioIndex);
            } else {
                stopSelf();
            }
        } catch (NullPointerException e) {
            stopSelf();
        }

        //Request audio focus
        if (!requestAudioFocus()) {
            //Could not gain focus
            stopSelf();
        }


        if (mediaSessionManager == null || (intent.getAction() != null && intent.getAction().equals(
                "start"))) {
            try {
                initMediaSession();
                initMediaPlayer();
            } catch (RemoteException e) {
                e.printStackTrace();
                stopSelf();
            }
            if (!BookPresenter.start) {
                buildNotification(PlaybackStatus.PAUSED);
            } else {
                buildNotification(PlaybackStatus.PLAYING);
            }
        }

        //Handle Intent action from MediaSession.TransportControls
        handleIncomingActions(intent);
        MediaButtonReceiver.handleIntent(mediaSession, intent);
        mPreferences = PreferenceManager
                .getDefaultSharedPreferences(this);
        return START_NOT_STICKY;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        if (!isPlaying()) {
            stopMedia();
        }
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopMedia();
        removeNotification();
        removeAudioFocus();
        //Disable the PhoneStateListener
        if (phoneStateListener != null) {
            telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE);
        }


        //unregister BroadcastReceivers
        unregisterReceiver(becomingNoisyReceiver);
        unregisterReceiver(playNewAudio);
        unregisterReceiver(playPriveos);
        unregisterReceiver(playNext);
        unregisterReceiver(play);
        unregisterReceiver(seekTo);
        unregisterReceiver(seekToNext30Sec);
        unregisterReceiver(seekToPrevious10Sec);
        unregisterReceiver(showTitle);
        unregisterReceiver(getPosition);
        unregisterReceiver(setSpeed);
        unregisterReceiver(closeNotPrerepred);
        unregisterReceiver(closeIfPause);
        unregisterReceiver(sleepTimer);
        unregisterReceiver(equalizer);
        unregisterReceiver(equalizerEnabled);
        mAudioDBModel.closeDB();
        SleepTimerPresenter.setTimerStarted(false);
    }

    private boolean isPrepared() {
        return mediaPlayer != null && mediaPlayer.getPlaybackState() == Player.STATE_READY;
    }

    @Override
    public void onAudioFocusChange(int focusState) {

        //Invoked when the audio focus of the system is updated.
        switch (focusState) {
            case AudioManager.AUDIOFOCUS_GAIN:
                // resume playback
                if (mediaPlayer != null) {
                    mediaPlayer.setVolume(1.0f);
                    if (!isPlaying()) {
                        if (!pause) {
                            mediaPlayer.setPlayWhenReady(true);
                            buildNotification(PlaybackStatus.PLAYING);
                        } else {
                            mediaPlayer.setPlayWhenReady(false);
                            buildNotification(PlaybackStatus.PAUSED);
                        }
                    }
                }
                break;
            case AudioManager.AUDIOFOCUS_LOSS:
                pauseMedia();
                pause = true;
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                if (isPlaying()) {
                    pauseMedia();
                    pause = false;
                } else {
                    pause = true;
                }
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                // Lost focus for a short time, but it's ok to keep playing
                // at an attenuated level
                pause = true;
                if (isPlaying()) mediaPlayer.setVolume(0.1f);
                break;
        }
    }

    /**
     * AudioFocus
     */
    private boolean requestAudioFocus() {
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        int result = Objects.requireNonNull(audioManager).requestAudioFocus(this,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN);
        //Focus gained
        return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
        //Could not gain focus
    }

    private boolean removeAudioFocus() {
        try {
            return AudioManager.AUDIOFOCUS_REQUEST_GRANTED ==
                    audioManager.abandonAudioFocus(this);
        } catch (Exception e) {
            return false;
        }
    }

    public boolean isPlaying() {
        try {
            return mediaPlayer != null && mediaPlayer.getPlaybackState() == Player.STATE_READY
                    && mediaPlayer.getPlayWhenReady();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * MediaPlayer actions
     */

    @Nullable
    private File getSaveFile() {
        File[] folders = getExternalFilesDirs(null);
        for (File folder : folders) {
            if (folder != null) {
                String url = activeAudio.getUrl();
                if (url != null && !url.isEmpty()) {
                    File file = new File(folder.getAbsolutePath() + "/"
                            + activeAudio.getBookName() + "/"
                            + url.substring(url.lastIndexOf("/") + 1));
                    if (file.exists()) return file;
                } else {
                    File file = new File(
                            folder.getAbsolutePath() + "/" + activeAudio.getBookName() + "/"
                                    + activeAudio.getName() + ".mp3");
                    if (file.exists()) return file;
                }
            }

        }
        return null;
    }

    private void initMediaPlayer() {

        File file = getSaveFile();
        TrackSelection.Factory trackSelectionFactory = new AdaptiveTrackSelection.Factory();
        DataSource.Factory dateSourceFactory = new DefaultDataSourceFactory(this,
                Util.getUserAgent(this, getPackageName()));

        Uri uri;
        if (file == null) {
            uri = Uri.parse(activeAudio.getUrl());
        } else {
            uri = Uri.fromFile(file);
        }

        MediaSource mediaSource = new ProgressiveMediaSource.Factory(
                dateSourceFactory).createMediaSource(uri);

        buttonClick = false;

        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
        mediaPlayer = ExoPlayerFactory.newSimpleInstance(this,
                new DefaultTrackSelector(trackSelectionFactory));

        //Set up MediaPlayer event listeners

        mediaPlayer.addListener(new Player.EventListener() {
            @Override
            public void onTimelineChanged(Timeline timeline, @Nullable Object manifest,
                    int reason) {

            }

            @Override
            public void onTracksChanged(TrackGroupArray trackGroups,
                    TrackSelectionArray trackSelections) {

            }

            @Override
            public void onLoadingChanged(boolean isLoading) {

            }

            @Override
            public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
                if (playbackState == Player.STATE_READY
                        && timeDuration != (int) mediaPlayer.getDuration()) {
                    timeDuration = (int) mediaPlayer.getDuration();
                }
                if (playbackState == Player.STATE_ENDED) {
                    if (!buttonClick) {
                        skipToNext();
                        updateMetaData();
                    } else {
                        stopMedia();
                        stopSelf();
                    }
                    buttonClick = false;
                }
                if (playWhenReady && playbackState == Player.STATE_READY) {
                    if (!BookPresenter.start || BookPresenter.resume) {
                        if (!mPreferences.getBoolean("rewind", false)) {
                            if (timeStart != 0) seekTo(timeStart * 1000);
                        } else {
                            if (timeStart > 30) seekTo((timeStart - 30) * 1000);
                        }


                    }
                    if (BookPresenter.resume) {
                        BookPresenter.resume = false;
                    }
                    isPlay = true;
                    mediaSession.setActive(true);
                    startPlayProgressUpdater();
                    startTimeProgressUpdater();
                    buildNotification(PlaybackStatus.PLAYING);

                    if (!BookPresenter.start) {
                        BookPresenter.start = true;
                        pauseMedia();
                    }

                    if (mEqualizerEnabled) {
                        storage.loadEqualizerSettings();
                        if (Settings.isEqualizerEnabled) {
                            startEqualizer();
                        }
                    }
                } else if (!playWhenReady && playbackState == Player.STATE_READY) {
                    buildNotification(PlaybackStatus.PAUSED);
                    isPlay = false;
                    mediaSession.setActive(false);
                    resumePosition = (int) mediaPlayer.getCurrentPosition();
                    int time = resumePosition / 1000;
                    mAudioDBModel.setTime(urlBook, time);
                }

            }

            @Override
            public void onPlayerError(ExoPlaybackException error) {
                Log.d(TAG, "onPlayerError: " + error);
            }

            @Override
            public void onPositionDiscontinuity(int reason) {

            }

            @Override
            public void onPlaybackParametersChanged(PlaybackParameters playbackParameters) {

            }

            @Override
            public void onSeekProcessed() {

            }
        });


        AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.CONTENT_TYPE_SPEECH)
                .build();
        mediaPlayer.setAudioAttributes(audioAttributes);



        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                PlaybackParameters param = new PlaybackParameters(BookPresenter.getSpeed());
                mediaPlayer.setPlaybackParameters(param);
            } catch (IllegalArgumentException ignored) {
            }
        }


        if (BookPresenter.start && !StorageAds.idDisableAds()) {
            long carentTime = new Date().getTime();
            if ((carentTime - timeStarting) / 60000 >= 30) {
                timeStarting = carentTime;
                MyInterstitialAd.showRequire();
            }
        }

        mediaPlayer.setPlayWhenReady(true);


        mediaPlayer.prepare(mediaSource);

        if (!StorageAds.idDisableAds()) {

            int countAudioListnered = storage.loadCountAudioListnered() + 1;
            if (countAudioListnered >= countAudioWereShowingActivity) {
                storage.storeCountAudioListnered(0);
                Intent broadcastIntent = new Intent(Broadcast_SHOW_GET_PLUS);
                sendBroadcast(broadcastIntent);
            } else {
                storage.storeCountAudioListnered(countAudioListnered);
            }
        }

        if (storage.loadShowRating()) {
            int countAudioListnered = storage.loadCountAudioListneredForRating() + 1;
            if (countAudioListnered >= countAudioWereShowingRatingPopUp) {
                storage.storeCountAudioListneredForRating(0);
                Intent broadcastIntent = new Intent(Broadcast_SHOW_RATING);
                sendBroadcast(broadcastIntent);
            } else {
                storage.storeCountAudioListneredForRating(countAudioListnered);
            }
        }

        Log.d(TAG, "initMediaPlayer: calld");
    }

    private void stopMedia() {
        if (mediaPlayer == null) return;
        resumePosition = (int) mediaPlayer.getCurrentPosition();
        int time = resumePosition / 1000;
        timeStart = time;
        mAudioDBModel.setTime(urlBook, time);
        stopMediaNotSave();
        BookPresenter.resume = true;
        BookPresenter.start = true;
    }

    private void stopMediaNotSave() {
        removeAudioFocus();
        if (mediaPlayer == null) return;
        isPlay = false;
        mediaPlayer.stop();
        mediaPlayer.release();
        mediaPlayer = null;
        mediaSession.setActive(false);
        BookPresenter.resume = false;
        buildNotification(PlaybackStatus.PAUSED);
    }

    private void pauseMedia() {
        if (isPlaying()) {
            mediaPlayer.setPlayWhenReady(false);
            pauseTime = System.currentTimeMillis();
        }
    }

    private void resumeMedia() {
        try {
            if (!isPlaying() && isPrepared()) {
                if (!requestAudioFocus()) {
                    stopSelf();
                }
                isPlay = true;
                if (!mPreferences.getBoolean("rewind", false)) {
                    mediaPlayer.seekTo(resumePosition);
                } else {
                    int time = (int) ((System.currentTimeMillis() - pauseTime) / 1000);
                    if (time < 10) {
                        mediaPlayer.seekTo(resumePosition);
                    } else if (resumePosition - 10000 <= 0) {
                        mediaPlayer.seekTo(0);
                    } else if (time < 30) {
                        mediaPlayer.seekTo(resumePosition - 10000);
                    } else {
                        mediaPlayer.seekTo(resumePosition - 30000);
                    }
                }
                mediaPlayer.setPlayWhenReady(true);
            }
        } catch (Exception ignored) {

        }
    }

    private void skipToNext() {
        if (mediaPlayer == null) return;
        if (audioIndex == audioList.size() - 1) {
            if (!buttonClick) {
                stopMedia();
            }
        } else {
            //get next in playlist
            activeAudio = audioList.get(++audioIndex);
            storage.storeAudioIndex(audioIndex);

            stopMediaNotSave();
            initMediaPlayer();
            sendBroadcastItemSelected(audioIndex);
            addLastBookToDB(audioList.get(audioIndex).getName());
            Intent broadcastIntent = new Intent(Broadcast_SET_TITLE);
            broadcastIntent.putExtra("title", audioList.get(audioIndex).getName());
            this.sendBroadcast(broadcastIntent);
            buildNotification(PlaybackStatus.PLAYING);
            Log.d(TAG, "skipToNext: calld");
        }

    }

    private void skipToPrevious() {
        if (mediaPlayer == null) return;
        if (audioIndex != 0) {
            activeAudio = audioList.get(--audioIndex);
            storage.storeAudioIndex(audioIndex);
            stopMediaNotSave();
            initMediaPlayer();
            sendBroadcastItemSelected(audioIndex);
            addLastBookToDB(audioList.get(audioIndex).getName());
            Intent broadcastIntent = new Intent(Broadcast_SET_TITLE);
            broadcastIntent.putExtra("title", audioList.get(audioIndex).getName());
            this.sendBroadcast(broadcastIntent);
            buildNotification(PlaybackStatus.PLAYING);
        }
    }

    private void addLastBookToDB(String name) {
        if (mAudioDBModel == null) mAudioDBModel = new AudioDBModel(this);
        if (mAudioDBModel.isset(urlBook)) {
            mAudioDBModel.remove(urlBook);
        }
        mAudioDBModel.add(urlBook, name);
    }

    private void registerBecomingNoisyReceiver() {
        //register after getting audio focus
        IntentFilter intentFilter = new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
        registerReceiver(becomingNoisyReceiver, intentFilter);
    }

    /**
     * Handle PhoneState changes
     */
    private void callStateListener() {
        // Get the telephony manager
        telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        //Starting listening for PhoneState changes
        phoneStateListener = new PhoneStateListener() {
            @Override
            public void onCallStateChanged(int state, String incomingNumber) {
                switch (state) {
                    //if at least one call exists or the phone is ringing
                    //pause the MediaPlayer
                    case TelephonyManager.CALL_STATE_OFFHOOK:
                    case TelephonyManager.CALL_STATE_RINGING:
                        if (mediaPlayer != null) {
                            pauseMedia();
                        }
                        break;
                    case TelephonyManager.CALL_STATE_IDLE:
                        requestAudioFocus();
                        break;
                }
            }
        };
        // Register the listener with the telephony manager
        // Listen for changes to the device call state.
        telephonyManager.listen(phoneStateListener,
                PhoneStateListener.LISTEN_CALL_STATE);
    }


    /**
     * MediaSession and Notification actions
     */
    private void initMediaSession() throws RemoteException {
        if (mediaSessionManager != null) return; //mediaSessionManager exists

        mediaSessionManager = (MediaSessionManager) getSystemService(Context.MEDIA_SESSION_SERVICE);
        // Create a new MediaSession
        mediaSession = new MediaSessionCompat(getApplicationContext(), "AudioPlayer");
        //Get MediaSessions transport controls
        transportControls = mediaSession.getController().getTransportControls();
        //set MediaSession -> ready to receive media commands
        mediaSession.setActive(true);
        //indicate that the MediaSession handles transport control commands
        // through its MediaSessionCompat.@+id/.
        mediaSession.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS
                | MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);


        //Set mediaSession's MetaData
        updateMetaData();


        PlaybackStateCompat state = new PlaybackStateCompat.Builder()
                .setActions(
                        PlaybackStateCompat.ACTION_PLAY | PlaybackStateCompat.ACTION_PLAY_PAUSE |
                                PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID
                                | PlaybackStateCompat.ACTION_PAUSE |
                                PlaybackStateCompat.ACTION_SKIP_TO_NEXT
                                | PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS |
                                PlaybackStateCompat.ACTION_FAST_FORWARD |
                                PlaybackStateCompat.ACTION_SEEK_TO |
                                PlaybackStateCompat.ACTION_REWIND)
                .setState(PlaybackStateCompat.STATE_PLAYING, 0, 1, SystemClock.elapsedRealtime())
                .build();

        mediaSession.setPlaybackState(state);

        Intent mediaButtonIntent = new Intent(
                Intent.ACTION_MEDIA_BUTTON, null, getApplicationContext(),
                MediaButtonReceiver.class);
        mediaSession.setMediaButtonReceiver(
                PendingIntent.getBroadcast(getApplicationContext(), 0, mediaButtonIntent, 0));

        // Attach Callback to receive MediaSession updates
        mediaSession.setCallback(new MediaSessionCompat.Callback() {

            @Override
            public boolean onMediaButtonEvent(Intent mediaButtonEvent) {
                return super.onMediaButtonEvent(mediaButtonEvent);
            }

            // Implement callbacks
            @Override
            public void onPlay() {
                super.onPlay();
                resumeMedia();
            }

            @Override
            public void onPause() {
                super.onPause();
                if (isPlaying()) {
                    pauseMedia();
                } else {
                    resumeMedia();
                }
            }

            @Override
            public void onSkipToNext() {
                super.onSkipToNext();
                buttonClick = true;
                skipToNext();
                updateMetaData();
            }

            @Override
            public void onSkipToPrevious() {
                super.onSkipToPrevious();
                buttonClick = true;
                skipToPrevious();
                updateMetaData();
            }

            @Override
            public void onStop() {
                super.onStop();
                //Stop the service
                stopSelf();
            }

            @Override
            public void onSeekTo(long position) {
                super.onSeekTo(position);
                seekTo(position);
            }

            @Override
            public void onRewind() {
                super.onRewind();
                rewind(REWIND);
            }

            @Override
            public void onFastForward() {
                super.onFastForward();
                fastForward(FAST_FORWARD);
            }
        });
    }

    private void updateMetaData() {
        mediaSession.setMetadata(new MediaMetadataCompat.Builder()
                //.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, albumArt)
                //.putString(MediaMetadataCompat.METADATA_KEY_ARTIST, activeAudio.getArtist())
                //.putString(MediaMetadataCompat.METADATA_KEY_ALBUM, activeAudio.getAlbum())
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, activeAudio.getName())
                .build());
    }

    private void buildNotification(PlaybackStatus playbackStatus) {
        stopForeground(false);

        int notificationAction = R.drawable.ic_notification_pause;//needs to be initialized
        PendingIntent play_pauseAction = null;

        Intent broadcastIntent = new Intent(Broadcast_SET_IMAGE);
        //Build a new notification according to the current state of the MediaPlayer
        if (playbackStatus == PlaybackStatus.PLAYING) {
            broadcastIntent.putExtra("id", R.drawable.ic_pause);
            //create the pause action
            play_pauseAction = playbackAction(1);
        } else if (playbackStatus == PlaybackStatus.PAUSED) {
            notificationAction = R.drawable.ic_notification_play;
            broadcastIntent.putExtra("id", R.drawable.ic_play);
            //create the play action
            play_pauseAction = playbackAction(0);
        }

        this.sendBroadcast(broadcastIntent);


        // I removed one of the semi-colons in the next line of code
        NotificationManager notificationManager = (NotificationManager) this.getSystemService(
                Context.NOTIFICATION_SERVICE);


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // I would suggest that you use IMPORTANCE_DEFAULT instead of IMPORTANCE_HIGH
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_LOW);
            channel.enableVibration(true);
            channel.setLightColor(Color.BLUE);
            channel.enableLights(true);
            channel.setShowBadge(true);
            Objects.requireNonNull(notificationManager).createNotificationChannel(channel);
        }

        Intent intent = new Intent(this, LoadBook.class);
        intent.putExtra("url", urlBook);
        intent.putExtra("notificationClick", true);
        PendingIntent resultPendingIntent = PendingIntent.getActivity(this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this,
                CHANNEL_ID)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setShowWhen(false)
                .setStyle(new androidx.media.app.NotificationCompat.MediaStyle()
                        .setMediaSession(mediaSession.getSessionToken())
                        .setShowActionsInCompactView(0, 2, 4))
                .setSmallIcon(android.R.drawable.stat_sys_headset)
                .setContentText("")
                .setContentTitle(activeAudio.getName())
                .setContentInfo("")
                .setGroup("GroupPlayer")
                .addAction(R.drawable.ic_notification_rewind_30, "rewind", playbackAction(4))
                .addAction(R.drawable.ic_notification_previous, "previous", playbackAction(3))
                .addAction(notificationAction, "pause", play_pauseAction)
                .addAction(R.drawable.ic_notification_next, "next", playbackAction(2))
                .addAction(R.drawable.ic_notification_fast_forward_30, "forward", playbackAction(5))
                .setAutoCancel(true)
                .setContentIntent(resultPendingIntent)
                .setLargeIcon(image);

        if (playbackStatus == PlaybackStatus.PLAYING) {
            startForeground(NOTIFICATION_ID, notificationBuilder.build());
        } else {
            notificationBuilder.setOngoing(false);
            Intent onCancelIntent = new Intent(this, OnCancelBroadcastReceiver.class);
            PendingIntent onDismissPendingIntent = PendingIntent.getBroadcast(
                    this.getApplicationContext(), 0, onCancelIntent, 0);
            notificationBuilder.setDeleteIntent(onDismissPendingIntent);
            stopForeground(false);
            Objects.requireNonNull(notificationManager).notify(NOTIFICATION_ID,
                    notificationBuilder.build());
        }


        if (image == null && !imageUrl.isEmpty()) {
            Picasso.get().load(imageUrl).into(new Target() {
                @Override
                public void onBitmapLoaded(Bitmap bitmap, Picasso.LoadedFrom from) {
                    image = bitmap;
                    notificationBuilder.setLargeIcon(bitmap);
                    Objects.requireNonNull(notificationManager).notify(NOTIFICATION_ID,
                            notificationBuilder.build());
                }

                @Override
                public void onBitmapFailed(Exception e, Drawable errorDrawable) {
                    notificationBuilder.setLargeIcon(null);
                    image = null;
                    Objects.requireNonNull(notificationManager).notify(NOTIFICATION_ID,
                            notificationBuilder.build());
                }

                @Override
                public void onPrepareLoad(Drawable placeHolderDrawable) {

                }
            });
        }

    }

    private PendingIntent playbackAction(int actionNumber) {
        Intent playbackAction = new Intent(this, MediaPlayerService.class);
        switch (actionNumber) {
            case 0:
                // Play
                playbackAction.setAction(ACTION_PLAY);
                return PendingIntent.getService(this, actionNumber, playbackAction, 0);
            case 1:
                // Pause
                playbackAction.setAction(ACTION_PAUSE);
                return PendingIntent.getService(this, actionNumber, playbackAction, 0);
            case 2:
                // Next track
                playbackAction.setAction(ACTION_NEXT);
                return PendingIntent.getService(this, actionNumber, playbackAction, 0);
            case 3:
                // Previous track
                playbackAction.setAction(ACTION_PREVIOUS);
                return PendingIntent.getService(this, actionNumber, playbackAction, 0);
            case 4:
                // Rewind track
                playbackAction.setAction(ACTION_REWIND);
                return PendingIntent.getService(this, actionNumber, playbackAction, 0);
            case 5:
                // Fast Froward track
                playbackAction.setAction(ACTION_FORWARD);
                return PendingIntent.getService(this, actionNumber, playbackAction, 0);
            default:
                break;
        }
        return null;
    }

    private void removeNotification() {
        //stopForeground(true);
        NotificationManager notificationManager = (NotificationManager) getSystemService(
                Context.NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            notificationManager.cancel(NOTIFICATION_ID);
        }
    }

    private void handleIncomingActions(Intent playbackAction) {
        if (playbackAction == null || playbackAction.getAction() == null) return;

        String actionString = playbackAction.getAction();
        if (actionString.equalsIgnoreCase(ACTION_PLAY)) {
            transportControls.play();
        } else if (actionString.equalsIgnoreCase(ACTION_PAUSE)) {
            transportControls.pause();
        } else if (actionString.equalsIgnoreCase(ACTION_NEXT)) {
            transportControls.skipToNext();
        } else if (actionString.equalsIgnoreCase(ACTION_PREVIOUS)) {
            transportControls.skipToPrevious();
        } else if (actionString.equalsIgnoreCase(ACTION_STOP)) {
            transportControls.stop();
        } else if (actionString.equalsIgnoreCase(ACTION_REWIND)) {
            transportControls.rewind();
        } else if (actionString.equalsIgnoreCase(ACTION_FORWARD)) {
            transportControls.fastForward();
        }
    }

    private void register_playNewAudio() {
        //Register playNewMedia receiver
        IntentFilter filter = new IntentFilter(BookActivity.Broadcast_PLAY_NEW_AUDIO);
        registerReceiver(playNewAudio, filter);
    }

    private void register_playPriveos() {
        //Register playNewMedia receiver
        IntentFilter filter = new IntentFilter(BookPresenter.Broadcast_PLAY_PREVIOUS);
        registerReceiver(playPriveos, filter);
    }

    private void register_playNext() {
        //Register playNewMedia receiver
        IntentFilter filter = new IntentFilter(BookPresenter.Broadcast_PLAY_NEXT);
        registerReceiver(playNext, filter);
    }

    private void register_play() {
        //Register playNewMedia receiver
        IntentFilter filter = new IntentFilter(BookPresenter.Broadcast_PLAY);
        registerReceiver(play, filter);
    }


    private void startPlayProgressUpdater() {
        try {
            if (isPrepared()) {
                Intent broadcastIntent = new Intent(Broadcast_SET_PROGRESS);
                broadcastIntent.putExtra("timeCurrent", (int) mediaPlayer.getCurrentPosition());
                broadcastIntent.putExtra("timeEnd", timeDuration);
                this.sendBroadcast(broadcastIntent);
                if (mediaPlayer.getCurrentPosition() >= mediaPlayer.getDuration()) {
                    buttonClick = false;
                    skipToNext();
                }

                if (isPlaying()) {
                    Runnable notification = this::startPlayProgressUpdater;
                    handler1.postDelayed(notification, 200);
                }
            }
        } catch (IllegalStateException ignored) {

        }
    }

    private void startTimeProgressUpdater() {
        try {
            if (isPrepared()) {
                if (isPlaying()) {
                    int time = (int) (mediaPlayer.getCurrentPosition() / 1000);
                    mAudioDBModel.setTime(urlBook, time);
                    Runnable notification = this::startTimeProgressUpdater;
                    handler2.postDelayed(notification, 10000);
                }
            }
        } catch (IllegalStateException ignored) {

        }
    }

    private void seekTo(long postion) {
        try {
            if (mediaPlayer != null && postion >= 0 && timeDuration > postion) {
                mediaPlayer.seekTo(postion);
                resumePosition = (int) postion;
                if (!mediaPlayer.getPlayWhenReady()) {
                    Intent broadcastIntent = new Intent(Broadcast_SET_PROGRESS);
                    broadcastIntent.putExtra("timeCurrent", (int) mediaPlayer.getCurrentPosition());
                    broadcastIntent.putExtra("timeEnd", timeDuration);
                    this.sendBroadcast(broadcastIntent);
                }
            }
        } catch (Exception e) {
            Toast.makeText(this, getString(R.string.worning_loading_not_finish),
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void register_seekTo() {
        //Register playNewMedia receiver
        IntentFilter filter = new IntentFilter(BookPresenter.Broadcast_SEEK_TO);
        registerReceiver(seekTo, filter);
    }

    private void sendBroadcastItemSelected(int id) {
        Intent broadcastIntent = new Intent(Broadcast_SET_SELECTION);
        broadcastIntent.putExtra("postion", id);
        broadcastIntent.putExtra("name", audioList.get(id).getName());
        sendBroadcast(broadcastIntent);
    }

    private void register_seekToNext30Sec() {
        //Register playNewMedia receiver
        IntentFilter filter = new IntentFilter(BookPresenter.Broadcast_SEEK_NEXT_30);
        registerReceiver(seekToNext30Sec, filter);
    }

    private void register_seekToPrevious10Sec() {
        //Register playNewMedia receiver
        IntentFilter filter = new IntentFilter(BookPresenter.Broadcast_SEEK_PREVIOUS_10);
        registerReceiver(seekToPrevious10Sec, filter);
    }

    private void register_showTitle() {
        //Register playNewMedia receiver
        IntentFilter filter = new IntentFilter(BookPresenter.Broadcast_SHOW_TITLE);
        registerReceiver(showTitle, filter);
    }

    private void register_getPosition() {
        IntentFilter filter = new IntentFilter(BookPresenter.Broadcast_GET_POSITION);
        registerReceiver(getPosition, filter);
    }

    private void register_setSpeed() {
        IntentFilter filter = new IntentFilter(BookPresenter.Broadcast_SET_SPEED);
        registerReceiver(setSpeed, filter);
    }

    private void register_closeIfNotPrepered() {
        IntentFilter filter = new IntentFilter(BookPresenter.Broadcast_CloseNotPrepered);
        registerReceiver(closeNotPrerepred, filter);
    }

    private void register_closeIfPause() {
        IntentFilter filter = new IntentFilter(Broadcast_CloseIfPause);
        registerReceiver(closeIfPause, filter);
    }

    private void register_sleepTimer() {
        IntentFilter filter = new IntentFilter(SleepTimerPresenter.Broadcast_SleepTimer);
        registerReceiver(sleepTimer, filter);
    }

    private void register_equalizer() {
        IntentFilter filter = new IntentFilter(Broadcast_Equalizer);
        registerReceiver(equalizer, filter);
    }

    private void register_equalizerEnabled() {
        IntentFilter filter = new IntentFilter(Broadcast_EqualizerEnabled);
        registerReceiver(equalizerEnabled, filter);
    }



    /**
     * Service Binder
     */
    public class LocalBinder extends Binder {
        public MediaPlayerService getService() {
            // Return this instance of LocalService so clients can call public methods
            return MediaPlayerService.this;
        }
    }

}
