package com.fanok.audiobooks.service;

import static android.support.v4.media.MediaBrowserCompat.MediaItem.FLAG_PLAYABLE;
import static androidx.media3.exoplayer.DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS;
import static androidx.media3.exoplayer.DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS;
import static com.fanok.audiobooks.activity.BookActivity.Broadcast_SET_IMAGE;
import static com.fanok.audiobooks.activity.BookActivity.Broadcast_SET_PROGRESS;
import static com.fanok.audiobooks.activity.BookActivity.Broadcast_SET_SELECTION;
import static com.fanok.audiobooks.activity.BookActivity.Broadcast_SET_TITLE;
import static com.fanok.audiobooks.activity.BookActivity.Broadcast_SHOW_EQUALIZER;
import static com.fanok.audiobooks.activity.BookActivity.Broadcast_SHOW_RATING;
import static com.fanok.audiobooks.activity.SleepTimerActivity.Broadcast_GET_TIME_LEFT;
import static com.fanok.audiobooks.activity.SleepTimerActivity.Broadcast_SET_TIME_LEFT;
import static com.fanok.audiobooks.activity.SleepTimerActivity.Broadcast_UPDATE_TIMER_START;
import static com.fanok.audiobooks.activity.SleepTimerActivity.bradcast_FinishTimer;
import static com.fanok.audiobooks.activity.SleepTimerActivity.bradcast_UpdateTimer;
import static com.fanok.audiobooks.presenter.BookPresenter.Broadcast_Equalizer;
import static com.fanok.audiobooks.util.DownloadUtil.buildAnnotatedUri;
import static com.fanok.audiobooks.android_equalizer.EqualizerFragment.Broadcast_EqualizerEnabled;


import android.annotation.SuppressLint;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
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
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.SystemClock;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaBrowserCompat.MediaItem;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.media.MediaBrowserServiceCompat;
import androidx.media.session.MediaButtonReceiver;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.offline.Download;
import androidx.media3.exoplayer.offline.DownloadCursor;
import androidx.media3.exoplayer.offline.DownloadIndex;
import androidx.preference.PreferenceManager;
import com.fanok.audiobooks.Hourglass;
import com.fanok.audiobooks.PlaybackStatus;
import com.fanok.audiobooks.R;
import com.fanok.audiobooks.activity.BookActivity;
import com.fanok.audiobooks.activity.LoadBook;
import com.fanok.audiobooks.broadcasts.OnCancelBroadcastReceiver;
import com.fanok.audiobooks.model.AudioDBModel;
import com.fanok.audiobooks.pojo.AudioPOJO;
import com.fanok.audiobooks.pojo.StorageUtil;
import com.fanok.audiobooks.presenter.BookPresenter;
import com.fanok.audiobooks.presenter.SleepTimerPresenter;
import com.fanok.audiobooks.util.DownloadUtil;
import com.fanok.audiobooks.android_equalizer.EqualizerModel;
import com.fanok.audiobooks.android_equalizer.Settings;
import androidx.media3.common.C;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.common.MediaItem.ClippingConfiguration;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.Player;
import androidx.media3.common.Player.Listener;
import androidx.media3.common.Timeline.Window;
import androidx.media3.common.AudioAttributes;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.cache.Cache;
import androidx.media3.datasource.cache.CacheDataSource;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.Target;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class MediaPlayerService extends MediaBrowserServiceCompat implements AudioManager.OnAudioFocusChangeListener {

    public static final String MY_MEDIA_ROOT_ID = "Root";


    public static final String ACTION_PLAY = "audioBook.ACTION_PLAY";

    public static final String ACTION_PAUSE = "audioBook.ACTION_PAUSE";

    public static final String ACTION_PREVIOUS = "audioBook.ACTION_PREVIOUS";

    public static final String ACTION_REWIND = "audioBook.ACTION_REWIND";

    public static final String ACTION_FORWARD = "audioBook.ACTION_FAST_FORWARD";

    public static final String ACTION_NEXT = "audioBook.ACTION_NEXT";

    public static final String ACTION_STOP = "audioBook.ACTION_STOP";
    public static final String Broadcast_CloseIfPause = "brodcast.CloseIfPause";

    private static final String TAG = "MediaPlayerService";
    //AudioPlayer notification ID
    private static final int NOTIFICATION_ID = 101;
    private static final String CHANNEL_ID = "124";
    private static final String CHANNEL_NAME = "Player";
    private static boolean isPlay = false;
    public static final int countAudioWereShowingRatingPopUp = 15;
    private static final int REWIND = 30000;
    private static final int FAST_FORWARD = 30000;

    private final IBinder mBinder = new LocalBinder();
    private final Handler handler1 = new Handler();
    private final Handler handler2 = new Handler();
    //private MediaPlayer mediaPlayer;
    private ExoPlayer mediaPlayer;
    //MediaSession
    private MediaSessionManager mediaSessionManager;
    private MediaSessionCompat mediaSession;
    private MediaControllerCompat.TransportControls transportControls;
    //Used to pause/resume MediaPlayer
    private int resumePosition;
    //AudioFocus
    private AudioManager audioManager;
    private ArrayList<AudioPOJO> audioList;
    private int audioIndex = -1;
    private AudioPOJO activeAudio;

    private boolean buttonClick;

    private String urlBook;

    private AudioDBModel mAudioDBModel;

    private boolean pause;

    //private boolean prepared = false;
    private String imageUrl = "";

    private Bitmap image = null;

    private int timeStart = 0;

    private StorageUtil storage;

    private boolean mEqualizerEnabled = true;

    private Equalizer mEqualizer;

    private BassBoost bassBoost;

    private PresetReverb presetReverb;


    private final BroadcastReceiver equalizer = new BroadcastReceiver() {
        @UnstableApi
        @Override
        public void onReceive(Context context, Intent intent) {
            Intent broadcastIntent = new Intent(Broadcast_SHOW_EQUALIZER);
            if (mediaPlayer != null) {
                broadcastIntent.putExtra("id", mediaPlayer.getAudioSessionId());
            }
            sendBroadcast(broadcastIntent);
        }
    };

    private final BroadcastReceiver equalizerEnabled = new BroadcastReceiver() {
        @UnstableApi
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

    @UnstableApi
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

    private final BroadcastReceiver getTimeLeft = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Intent broadcastIntent = new Intent(Broadcast_SET_TIME_LEFT);
            if (mediaPlayer != null) {
                broadcastIntent.putExtra("timeLet", mediaPlayer.getDuration() - mediaPlayer.getCurrentPosition());
            } else {
                broadcastIntent.putExtra("timeLet", 0);
            }
            sendBroadcast(broadcastIntent);
        }
    };

    private SharedPreferences mPreferences;

    private Hourglass mCountDownTimer;

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
            broadcastIntent.putExtra("timeEnd", (int) mediaPlayer.getDuration());
            sendBroadcast(broadcastIntent);
        }
    };

    private final PlaybackStateCompat.Builder mState = new PlaybackStateCompat.Builder()
            .setActions(
                    PlaybackStateCompat.ACTION_PLAY | PlaybackStateCompat.ACTION_PLAY_PAUSE |
                            PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID
                            | PlaybackStateCompat.ACTION_PAUSE |
                            PlaybackStateCompat.ACTION_SKIP_TO_NEXT
                            | PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS |
                            PlaybackStateCompat.ACTION_FAST_FORWARD |
                            PlaybackStateCompat.ACTION_SEEK_TO |
                            PlaybackStateCompat.ACTION_REWIND | PlaybackStateCompat.ACTION_STOP);

    private final BroadcastReceiver sleepTimer = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (SleepTimerPresenter.isTimerStarted()) {
                //startTimer
                if (mCountDownTimer != null) {
                    mCountDownTimer.cancel();
                }
                long time = intent.getLongExtra("time", 0);
                mCountDownTimer = new Hourglass(time) {
                    @Override
                    public void onTimerFinish() {
                        SleepTimerPresenter.setTimerStarted(false);
                        sendBroadcast(new Intent(bradcast_FinishTimer));
                        stopMedia();
                        stopForeground(true);
                        stopSelf();
                    }

                    @Override
                    public void onTimerTick(final long timeRemaining) {
                        Intent broadcastIntent = new Intent(bradcast_UpdateTimer);
                        broadcastIntent.putExtra("time", timeRemaining);
                        sendBroadcast(broadcastIntent);
                    }
                };
                mCountDownTimer.startTimer();
                if (!isPlaying()) {
                    mCountDownTimer.pauseTimer();
                }
            } else {
                //stopTimer
                if (mCountDownTimer != null) {
                    mCountDownTimer.cancel();
                }
            }
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

    private final BroadcastReceiver updateTimer = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (mCountDownTimer != null) {
                Intent broadcastIntent = new Intent(bradcast_UpdateTimer);
                broadcastIntent.putExtra("time", mCountDownTimer.getTimeRemaining());
                sendBroadcast(broadcastIntent);
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
        @UnstableApi
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
        @UnstableApi
        @Override
        public void onReceive(Context context, Intent intent) {
            buttonClick = true;
            skipToPrevious();
            updateMetaData();
        }
    };
    private final BroadcastReceiver playNext = new BroadcastReceiver() {
        @UnstableApi
        @Override
        public void onReceive(Context context, Intent intent) {
            buttonClick = true;
            skipToNext();
            updateMetaData();
        }
    };
    private final BroadcastReceiver play = new BroadcastReceiver() {
        @UnstableApi
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
            if (mediaPlayer != null) {
                resumeMedia();
                PlaybackParameters param = new PlaybackParameters(BookPresenter.getSpeed());
                mediaPlayer.setPlaybackParameters(param);
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



    public static boolean isPlay() {
        return isPlay;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        // Perform one-time setup procedures

        // Manage incoming phone calls during playback.
        // Pause MediaPlayer on incoming call,
        // Resume on hangup.
        //ACTION_AUDIO_BECOMING_NOISY -- change in audio outputs -- BroadcastReceiver
        registerBecomingNoisyReceiver();

        registerAllBroadcast();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        SleepTimerPresenter.setTimerStarted(false);
        stopMedia();
        removeNotification();
        removeAudioFocus();
        stopEqualizer();
        if(mAudioDBModel!=null){
            mAudioDBModel.closeDB();
        }

        //unregister BroadcastReceivers
        try{
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
            unregisterReceiver(getTimeLeft);
            unregisterReceiver(updateTimer);
            unregisterReceiver(equalizer);
            unregisterReceiver(equalizerEnabled);
        } catch (Exception ignored){

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
                if (isPlaying()) {
                    mediaPlayer.setVolume(0.1f);
                }
                break;
        }
    }

    /**
     * Service lifecycle methods
     */
    @Override
    public IBinder onBind(Intent intent) {
        if (SERVICE_INTERFACE.equals(intent.getAction())) {
            return super.onBind(intent);
        }
        return mBinder;
    }

    @Nullable
    @Override
    public BrowserRoot onGetRoot(@NonNull final String clientPackageName, final int clientUid,
            @Nullable final Bundle rootHints) {
        return new MediaBrowserServiceCompat.BrowserRoot(MY_MEDIA_ROOT_ID, null);
    }

    @Override
    public void onLoadChildren(@NonNull final String parentId, @NonNull final Result<List<MediaItem>> result) {
        List<MediaBrowserCompat.MediaItem> mediaItems = new ArrayList<>();
        MediaDescriptionCompat.Builder descriptionBuilder =
                new MediaDescriptionCompat.Builder();

        for (int i = 0; i < audioList.size(); i++) {
            AudioPOJO audioPOJO = audioList.get(i);
            MediaDescriptionCompat description = descriptionBuilder
                    .setTitle(audioPOJO.getName())
                    .setMediaId(Integer.toString(i))
                    .build();
            mediaItems.add(new MediaBrowserCompat.MediaItem(description, FLAG_PLAYABLE));
        }
        result.sendResult(mediaItems);
    }

    //The system calls this method when an activity, requests the service be started
    @UnstableApi
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

        mPreferences = PreferenceManager
                .getDefaultSharedPreferences(this);

        if (mediaSessionManager == null || (intent.getAction() != null && intent.getAction().equals(
                "start"))) {
            try {
                initMediaSession();
                initMediaPlayer();
            } catch (RemoteException e) {
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
        return START_NOT_STICKY;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        if (!isPlaying()) {
            stopMedia();
        }
        return super.onUnbind(intent);
    }

    private void addLastBookToDB(String name) {
        if (mAudioDBModel == null) {
            mAudioDBModel = new AudioDBModel(this);
        }
        if (mAudioDBModel.isset(urlBook)) {
            mAudioDBModel.remove(urlBook);
        }
        mAudioDBModel.add(urlBook, name);
    }

    private void buildNotification(PlaybackStatus playbackStatus) {
        stopForeground(false);

        int notificationAction = R.drawable.ic_notification_pause;//needs to be initialized
        PendingIntent play_pauseAction = null;
        float playbackSpeed = 0f;

        Intent broadcastIntent = new Intent(Broadcast_SET_IMAGE);
        //Build a new notification according to the current state of the MediaPlayer
        if (playbackStatus == PlaybackStatus.PLAYING) {
            broadcastIntent.putExtra("id", R.drawable.ic_pause);
            //create the pause action
            play_pauseAction = playbackAction(1);
            playbackSpeed = BookPresenter.getSpeed();
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
                PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this,
                CHANNEL_ID)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setShowWhen(false)
                .setSmallIcon(android.R.drawable.stat_sys_headset)
                .setContentText("")
                .setContentTitle(activeAudio.getName())
                .setContentInfo("")
                .setGroup("GroupPlayer")
                .setAutoCancel(true)
                .setContentIntent(resultPendingIntent)
                .setLargeIcon(image);

        if (VERSION.SDK_INT < VERSION_CODES.Q) {
            notificationBuilder.setStyle(new androidx.media.app.NotificationCompat.MediaStyle()
                            .setMediaSession(mediaSession.getSessionToken())
                            .setShowActionsInCompactView(0, 2, 4))
                    .addAction(R.drawable.ic_notification_rewind_30, "rewind", playbackAction(4))
                    .addAction(R.drawable.ic_notification_previous, "previous", playbackAction(3))
                    .addAction(notificationAction, "pause", play_pauseAction)
                    .addAction(R.drawable.ic_notification_next, "next", playbackAction(2))
                    .addAction(R.drawable.ic_notification_fast_forward_30, "forward", playbackAction(5));
        } else {
            notificationBuilder.setStyle(new androidx.media.app.NotificationCompat.MediaStyle()
                            .setMediaSession(mediaSession.getSessionToken())
                            .setShowActionsInCompactView(0, 1, 2))
                    .addAction(R.drawable.ic_notification_previous, "previous", playbackAction(3))
                    .addAction(notificationAction, "pause", play_pauseAction)
                    .addAction(R.drawable.ic_notification_next, "next", playbackAction(2))
                    .addAction(android.R.drawable.ic_delete, "delete", playbackAction(6));
            updateMetaData();
            long position = 0;
            if (mediaPlayer != null) {
                position = mediaPlayer.getContentPosition();
            }
            if (playbackStatus == PlaybackStatus.PLAYING) {
                mediaSession.setPlaybackState(
                        mState.setState(PlaybackStateCompat.STATE_PLAYING, position, playbackSpeed).build());
            } else {
                mediaSession.setPlaybackState(
                        mState.setState(PlaybackStateCompat.STATE_PAUSED, position, playbackSpeed).build());
            }
        }

        if (playbackStatus == PlaybackStatus.PLAYING) {
            if (VERSION.SDK_INT >= VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notificationBuilder.build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
            } else {
                startForeground(NOTIFICATION_ID, notificationBuilder.build());
            }

        } else {
            notificationBuilder.setOngoing(false);
            Intent onCancelIntent = new Intent(this, OnCancelBroadcastReceiver.class);
            PendingIntent onDismissPendingIntent = PendingIntent.getBroadcast(
                    this.getApplicationContext(), 0, onCancelIntent, PendingIntent.FLAG_IMMUTABLE);
            notificationBuilder.setDeleteIntent(onDismissPendingIntent);
            stopForeground(false);
            Objects.requireNonNull(notificationManager).notify(NOTIFICATION_ID,
                    notificationBuilder.build());
        }

        if (image == null && !imageUrl.isEmpty()) {
            Picasso.get().load(imageUrl).into(new Target() {
                @Override
                public void onBitmapFailed(Exception e, Drawable errorDrawable) {
                    image = null;
                    Objects.requireNonNull(notificationManager).notify(NOTIFICATION_ID,
                            notificationBuilder.build());
                }

                @Override
                public void onBitmapLoaded(Bitmap bitmap, Picasso.LoadedFrom from) {
                    image = bitmap;
                    notificationBuilder.setLargeIcon(bitmap);
                    Objects.requireNonNull(notificationManager).notify(NOTIFICATION_ID,
                            notificationBuilder.build());
                }

                @Override
                public void onPrepareLoad(Drawable placeHolderDrawable) {

                }
            });
        }

    }
    @UnstableApi
    private void initMediaPlayer() {

        if (!requestAudioFocus()) {
            stopSelf();
        }

        String fileUrl = activeAudio.getUrl();
        Uri annotated = buildAnnotatedUri(fileUrl, urlBook);

        try {
            DownloadIndex index = DownloadUtil.getDownloadManager().getDownloadIndex();
            try (DownloadCursor cursor = index.getDownloads(Download.STATE_COMPLETED)) {
                while (cursor.moveToNext()) {
                    Download d = cursor.getDownload();
                    if (d.request.id.equals(activeAudio.getCleanUrl())){
                        annotated = d.request.uri;
                    }
                }
            }

        } catch (IOException e) {
            Log.w(TAG, "Failed to query downloads", e);
        }

        Cache downloadCache = DownloadUtil.getDownloadCache();

        DataSource.Factory upstream = DownloadUtil.getResolvingUpstreamFactory(getApplicationContext());

        DataSource.Factory cacheDataSourceFactory =
                new CacheDataSource.Factory()
                        .setCache(downloadCache)
                        .setUpstreamDataSourceFactory(upstream)
                        //.setCacheKeyFactory(DownloadUtil.getCleanCacheKeyFactory())
                        .setCacheWriteDataSinkFactory(null) // read-only
                        .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR);




        androidx.media3.common.MediaItem.Builder builderMediaItem =
                new androidx.media3.common.MediaItem.Builder()
                        //.setCustomCacheKey(fileUrl)
                        .setUri(annotated);



        if(activeAudio.getTimeStart()>=0 && activeAudio.getTimeFinish()>=0){
            long timeStart = activeAudio.getTimeStart()* 1000L;
            long timeFinish = activeAudio.getTimeFinish()* 1000L;
            builderMediaItem.setClippingConfiguration(
                    new ClippingConfiguration.Builder()
                            .setStartPositionMs(timeStart)
                            .setEndPositionMs(timeFinish)
                            .build());
        }

        androidx.media3.common.MediaItem mediaItem = builderMediaItem.build();

        buttonClick = false;

        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }

        DefaultLoadControl.Builder builder = new
                DefaultLoadControl.Builder();
        int loadControlBufferMs = Integer.parseInt(mPreferences.getString("bufferSize", "60"));
        loadControlBufferMs *= 1000;
        builder.setBufferDurationsMs(loadControlBufferMs, loadControlBufferMs,
                DEFAULT_BUFFER_FOR_PLAYBACK_MS, DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS);





        mediaPlayer = new ExoPlayer.Builder(this)
                .setMediaSourceFactory(new DefaultMediaSourceFactory(cacheDataSourceFactory))
                .setLoadControl(builder.build())
                .setTrackSelector(new DefaultTrackSelector(this))
                .build();
        mediaPlayer.addMediaItem(mediaItem);

        //mediaPlayer.addAnalyticsListener(new EventLogger());


        //Set up MediaPlayer event listeners

        mediaPlayer.addListener(new Player.Listener() {


            @Override
            public void onPlayWhenReadyChanged(final boolean playWhenReady, final int reason) {
                Listener.super.onPlayWhenReadyChanged(playWhenReady, reason);
                onPlaybackStateChanged(mediaPlayer.getPlaybackState());
            }

            @Override
            public void onPlaybackStateChanged(final int playbackState) {
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

                if(mediaPlayer!=null) {
                    if (mediaPlayer.getPlayWhenReady() && playbackState == Player.STATE_READY) {
                        if (!BookPresenter.start || BookPresenter.resume) {
                            if (!mPreferences.getBoolean("rewind_back", false)) {
                                if (timeStart != 0)
                                    seekTo(timeStart * 1000L);
                            } else {
                                if (timeStart > 30)
                                    seekTo((timeStart - 30) * 1000L);
                            }


                        }
                        if (BookPresenter.resume) {
                            BookPresenter.resume = false;
                        }
                        isPlay = true;
                        mediaSession.setActive(true);
                        startPlayProgressUpdater();
                        startTimeProgressUpdater();
                        if (mCountDownTimer != null) {
                            mCountDownTimer.resumeTimer();
                        }
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

                    } else if (!mediaPlayer.getPlayWhenReady() && playbackState == Player.STATE_READY) {
                        buildNotification(PlaybackStatus.PAUSED);
                        isPlay = false;
                        mediaSession.setActive(false);
                        resumePosition = (int) mediaPlayer.getCurrentPosition();
                        int time = resumePosition / 1000;
                        mAudioDBModel.setTime(urlBook, time);
                        if (mCountDownTimer != null) {
                            mCountDownTimer.pauseTimer();
                        }
                    }
                }

            }

            @Override
            public void onPlayerError(@NonNull final PlaybackException error) {
                Log.d(TAG, "onPlayerError: " + error);
            }
        });


        AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                .build();
        mediaPlayer.setAudioAttributes(audioAttributes, false);

        PlaybackParameters param = new PlaybackParameters(BookPresenter.getSpeed());
        mediaPlayer.setPlaybackParameters(param);


        mediaPlayer.setPlayWhenReady(true);
        mediaPlayer.prepare();

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

    /**
     * MediaSession and Notification actions
     */
    private void initMediaSession() throws RemoteException {
        if (mediaSessionManager != null) {
            return; //mediaSessionManager exists
        }

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

        updateMetaData();
        mediaSession.setPlaybackState(
                mState.setState(PlaybackStateCompat.STATE_PLAYING, 0, 1, SystemClock.elapsedRealtime()).build());
        setSessionToken(mediaSession.getSessionToken());

        Intent mediaButtonIntent = new Intent(
                Intent.ACTION_MEDIA_BUTTON, null, getApplicationContext(),
                MediaButtonReceiver.class);
        mediaSession.setMediaButtonReceiver(
                PendingIntent.getBroadcast(getApplicationContext(), 0, mediaButtonIntent, PendingIntent.FLAG_IMMUTABLE));

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

            @UnstableApi
            @Override
            public void onSkipToNext() {
                super.onSkipToNext();
                buttonClick = true;
                skipToNext();
                updateMetaData();
            }

            @UnstableApi
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
                pauseMedia();
                stopForeground(false);
                removeNotification();
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

            @UnstableApi
            @Override
            public void onPlayFromMediaId(final String mediaId, final Bundle extras) {
                super.onPlayFromMediaId(mediaId, extras);
                audioIndex = Integer.parseInt(mediaId);
                activeAudio = audioList.get(audioIndex);
                storage.storeAudioIndex(audioIndex);

                stopMediaNotSave();
                initMediaPlayer();
                sendBroadcastItemSelected(audioIndex);
                addLastBookToDB(audioList.get(audioIndex).getName());
                Intent broadcastIntent = new Intent(Broadcast_SET_TITLE);
                broadcastIntent.putExtra("title", audioList.get(audioIndex).getName());
                sendBroadcast(broadcastIntent);
                buildNotification(PlaybackStatus.PLAYING);
                Log.d(TAG, "onPlayFromMediaId: calld");
            }
        });
    }

    private boolean isPrepared() {
        return mediaPlayer != null && mediaPlayer.getPlaybackState() == Player.STATE_READY;
    }


    private void pauseMedia() {
        if (isPlaying()) {
            mediaPlayer.setPlayWhenReady(false);
            pauseTime = System.currentTimeMillis();
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private void registerAllBroadcast() {
        if (VERSION.SDK_INT >= VERSION_CODES.TIRAMISU) {
            registerReceiver(playNewAudio, new IntentFilter(BookActivity.Broadcast_PLAY_NEW_AUDIO), RECEIVER_EXPORTED);
            registerReceiver(playPriveos, new IntentFilter(BookPresenter.Broadcast_PLAY_PREVIOUS), RECEIVER_EXPORTED);
            registerReceiver(playNext, new IntentFilter(BookPresenter.Broadcast_PLAY_NEXT), RECEIVER_EXPORTED);
            registerReceiver(play, new IntentFilter(BookPresenter.Broadcast_PLAY), RECEIVER_EXPORTED);
            registerReceiver(seekTo, new IntentFilter(BookPresenter.Broadcast_SEEK_TO), RECEIVER_EXPORTED);
            registerReceiver(seekToNext30Sec, new IntentFilter(BookPresenter.Broadcast_SEEK_NEXT_30), RECEIVER_EXPORTED);
            registerReceiver(seekToPrevious10Sec, new IntentFilter(BookPresenter.Broadcast_SEEK_PREVIOUS_10), RECEIVER_EXPORTED);
            registerReceiver(showTitle, new IntentFilter(BookPresenter.Broadcast_SHOW_TITLE), RECEIVER_EXPORTED);
            registerReceiver(getPosition, new IntentFilter(BookPresenter.Broadcast_GET_POSITION), RECEIVER_EXPORTED);
            registerReceiver(setSpeed, new IntentFilter(BookPresenter.Broadcast_SET_SPEED), RECEIVER_EXPORTED);
            registerReceiver(closeNotPrerepred, new IntentFilter(BookPresenter.Broadcast_CloseNotPrepered), RECEIVER_EXPORTED);
            registerReceiver(closeIfPause, new IntentFilter(Broadcast_CloseIfPause), RECEIVER_EXPORTED);
            registerReceiver(sleepTimer, new IntentFilter(SleepTimerPresenter.Broadcast_SleepTimer), RECEIVER_EXPORTED);
            registerReceiver(getTimeLeft, new IntentFilter(Broadcast_GET_TIME_LEFT), RECEIVER_EXPORTED);
            registerReceiver(updateTimer, new IntentFilter(Broadcast_UPDATE_TIMER_START), RECEIVER_EXPORTED);
            registerReceiver(equalizer, new IntentFilter(Broadcast_Equalizer), RECEIVER_EXPORTED);
            registerReceiver(equalizerEnabled, new IntentFilter(Broadcast_EqualizerEnabled), RECEIVER_EXPORTED);
        }else {
            registerReceiver(playNewAudio, new IntentFilter(BookActivity.Broadcast_PLAY_NEW_AUDIO));
            registerReceiver(playPriveos, new IntentFilter(BookPresenter.Broadcast_PLAY_PREVIOUS));
            registerReceiver(playNext, new IntentFilter(BookPresenter.Broadcast_PLAY_NEXT));
            registerReceiver(play, new IntentFilter(BookPresenter.Broadcast_PLAY));
            registerReceiver(seekTo, new IntentFilter(BookPresenter.Broadcast_SEEK_TO));
            registerReceiver(seekToNext30Sec, new IntentFilter(BookPresenter.Broadcast_SEEK_NEXT_30));
            registerReceiver(seekToPrevious10Sec, new IntentFilter(BookPresenter.Broadcast_SEEK_PREVIOUS_10));
            registerReceiver(showTitle, new IntentFilter(BookPresenter.Broadcast_SHOW_TITLE));
            registerReceiver(getPosition, new IntentFilter(BookPresenter.Broadcast_GET_POSITION));
            registerReceiver(setSpeed, new IntentFilter(BookPresenter.Broadcast_SET_SPEED));
            registerReceiver(closeNotPrerepred, new IntentFilter(BookPresenter.Broadcast_CloseNotPrepered));
            registerReceiver(closeIfPause, new IntentFilter(Broadcast_CloseIfPause));
            registerReceiver(sleepTimer, new IntentFilter(SleepTimerPresenter.Broadcast_SleepTimer));
            registerReceiver(getTimeLeft, new IntentFilter(Broadcast_GET_TIME_LEFT));
            registerReceiver(updateTimer, new IntentFilter(Broadcast_UPDATE_TIMER_START));
            registerReceiver(equalizer, new IntentFilter(Broadcast_Equalizer));
            registerReceiver(equalizerEnabled, new IntentFilter(Broadcast_EqualizerEnabled));
        }
    }

    private boolean removeAudioFocus() {
        try {
            return AudioManager.AUDIOFOCUS_REQUEST_GRANTED ==
                    audioManager.abandonAudioFocus(this);
        } catch (Exception e) {
            return false;
        }
    }

    private PendingIntent playbackAction(int actionNumber) {
        Intent playbackAction = new Intent(this, MediaPlayerService.class);

        String action;
        switch (actionNumber) {
            case 0:
                action = ACTION_PLAY;
                break;
            case 1:
                action = ACTION_PAUSE;
                break;
            case 2:
                action = ACTION_NEXT;
                break;
            case 3:
                action = ACTION_PREVIOUS;
                break;
            case 4:
                action = ACTION_REWIND;
                break;
            case 5:
                action = ACTION_FORWARD;
                break;
            case 6:
                action = ACTION_STOP;
                break;
            default:
                return null;
        }

        playbackAction.setAction(action);
        return PendingIntent.getService(this, actionNumber, playbackAction, PendingIntent.FLAG_IMMUTABLE);
    }

    private void registerBecomingNoisyReceiver() {
        //register after getting audio focus
        IntentFilter intentFilter = new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
        registerReceiver(becomingNoisyReceiver, intentFilter);
    }

    /**
     * AudioFocus
     */
    private boolean requestAudioFocus() {
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        int result = Objects.requireNonNull(audioManager).requestAudioFocus(this,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN);
        return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;

    }

    @UnstableApi
    private void skipToNext() {
        if (mediaPlayer == null) {
            return;
        }
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

    @UnstableApi
    private void skipToPrevious() {
        if (mediaPlayer == null) {
            return;
        }
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

    private void stopMedia() {
        if (mediaPlayer == null) {
            return;
        }
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
        if (mediaPlayer == null) {
            return;
        }
        isPlay = false;
        mediaPlayer.stop();
        mediaPlayer.release();
        mediaPlayer = null;
        mediaSession.setActive(false);
        BookPresenter.resume = false;
        buildNotification(PlaybackStatus.PAUSED);
    }

    private void updateMetaData() {
        if (VERSION.SDK_INT < VERSION_CODES.Q || mediaPlayer == null) {
            mediaSession.setMetadata(new MediaMetadataCompat.Builder()
                    //.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, albumArt)
                    //.putString(MediaMetadataCompat.METADATA_KEY_ARTIST, activeAudio.getArtist())
                    //.putString(MediaMetadataCompat.METADATA_KEY_ALBUM, activeAudio.getAlbum())
                    .putString(MediaMetadataCompat.METADATA_KEY_TITLE, activeAudio.getName())
                    .build());
        } else {
            mediaSession.setMetadata(new MediaMetadataCompat.Builder()
                    .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, mediaPlayer.getDuration())
                    .putString(MediaMetadataCompat.METADATA_KEY_TITLE, activeAudio.getName())
                    .build());
        }

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

    private void resumeMedia() {
        try {
            if (!isPlaying() && isPrepared()) {
                if (!requestAudioFocus()) {
                    stopSelf();
                }
                isPlay = true;

                Window window = new Window();
                window = mediaPlayer.getCurrentTimeline().getWindow(mediaPlayer.getCurrentMediaItemIndex(), window);
                if(window.isSeekable) {
                    if (!mPreferences.getBoolean("rewind_back", false)) {
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
                }
                mediaPlayer.setPlayWhenReady(true);
            }
        } catch (Exception ignored) {

        }
    }

    private void startPlayProgressUpdater() {
        try {
            if (isPrepared()) {
                Intent broadcastIntent = new Intent(Broadcast_SET_PROGRESS);
                broadcastIntent.putExtra("timeCurrent", (int) mediaPlayer.getCurrentPosition());
                broadcastIntent.putExtra("timeEnd", (int) mediaPlayer.getDuration());
                broadcastIntent.putExtra("buffered", (int) mediaPlayer.getBufferedPosition());
                this.sendBroadcast(broadcastIntent);

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
            if (mediaPlayer != null && postion >= 0 && mediaPlayer.getDuration() > postion) {
                Window window = new Window();
                window = mediaPlayer.getCurrentTimeline().getWindow(mediaPlayer.getCurrentMediaItemIndex(), window);
                if(window.isSeekable) {
                    mediaPlayer.seekTo(postion);
                    resumePosition = (int) postion;
                    if (!mediaPlayer.getPlayWhenReady()) {
                        Intent broadcastIntent = new Intent(Broadcast_SET_PROGRESS);
                        broadcastIntent.putExtra("timeCurrent", (int) mediaPlayer.getCurrentPosition());
                        broadcastIntent.putExtra("timeEnd", (int) mediaPlayer.getDuration());
                        this.sendBroadcast(broadcastIntent);
                    }
                }
            }
        } catch (Exception e) {
            Toast.makeText(this, getString(R.string.worning_loading_not_finish),
                    Toast.LENGTH_SHORT).show();
        }
    }


    private void sendBroadcastItemSelected(int id) {
        Intent broadcastIntent = new Intent(Broadcast_SET_SELECTION);
        broadcastIntent.putExtra("postion", id);
        broadcastIntent.putExtra("name", audioList.get(id).getName());
        sendBroadcast(broadcastIntent);
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