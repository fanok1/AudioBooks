package com.fanok.audiobooks.service;

import static com.fanok.audiobooks.activity.BookActivity.Broadcast_SET_IMAGE;
import static com.fanok.audiobooks.activity.BookActivity.Broadcast_SET_PROGRESS;
import static com.fanok.audiobooks.activity.BookActivity.Broadcast_SET_SELECTION;
import static com.fanok.audiobooks.activity.BookActivity.Broadcast_SET_TITLE;
import static com.fanok.audiobooks.activity.BookActivity.Broadcast_SHOW_GET_PLUS;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.session.MediaSessionManager;
import android.os.Binder;
import android.os.Build;
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

import androidx.core.app.NotificationCompat;

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
import com.squareup.picasso.Picasso;
import com.squareup.picasso.Target;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Objects;

public class MediaPlayerService extends Service implements MediaPlayer.OnCompletionListener,
        MediaPlayer.OnPreparedListener, MediaPlayer.OnErrorListener,
        MediaPlayer.OnSeekCompleteListener,
        MediaPlayer.OnInfoListener, MediaPlayer.OnBufferingUpdateListener,

        AudioManager.OnAudioFocusChangeListener {


    public static final String ACTION_PLAY = "audioBook.ACTION_PLAY";
    public static final String ACTION_PAUSE = "audioBook.ACTION_PAUSE";
    public static final String ACTION_PREVIOUS = "audioBook.ACTION_PREVIOUS";
    public static final String ACTION_NEXT = "audioBook.ACTION_NEXT";
    public static final String ACTION_STOP = "audioBook.ACTION_STOP";
    public static final String Broadcast_DELETE_NOTIFICATION = "brodcast.DELETE_NOTIFICATION";
    public static final String Broadcast_CloseIfPause = "brodcast.CloseIfPause";

    private static final String TAG = "MediaPlayerService";
    //AudioPlayer notification ID
    private static final int NOTIFICATION_ID = 101;
    private static final String CHANNEL_ID = "124";
    private static final String CHANNEL_NAME = "Notification";
    private static boolean isPlay = false;

    private static final int countAudioWereShowingActivity = 25;
    // Binder given to clients
    private final IBinder mBinder = new LocalBinder();
    private final Handler handler = new Handler();
    private MediaPlayer mediaPlayer;
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
    private boolean prepared = false;
    private String imageUrl = "";
    private Bitmap image = null;
    private int timeStart = 0;
    private StorageUtil storage;


    /**
     * ACTION_AUDIO_BECOMING_NOISY -- change in audio outputs
     */
    private BroadcastReceiver becomingNoisyReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            //pause audio on ACTION_AUDIO_BECOMING_NOISY
            pauseMedia();
        }
    };
    /**
     * Play new Audio
     */
    private BroadcastReceiver playNewAudio = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            buttonClick = true;
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
            stopMedia();
            mediaPlayer.reset();
            initMediaPlayer();
            updateMetaData();
            buildNotification(PlaybackStatus.PLAYING);
        }
    };
    private BroadcastReceiver playPriveos = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            buttonClick = true;
            skipToPrevious();
            updateMetaData();
        }
    };
    private BroadcastReceiver playNext = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            buttonClick = true;
            skipToNext();
            updateMetaData();
        }
    };
    private BroadcastReceiver play = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (isPlaying()) {
                pauseMedia();
            } else if (prepared) {
                resumeMedia();
            } else {
                Toast.makeText(context, getString(R.string.worning_loading_not_finish),
                        Toast.LENGTH_SHORT).show();
            }
        }
    };
    private BroadcastReceiver seekTo = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            seekTo(intent.getIntExtra("postion", 0));
        }
    };
    private BroadcastReceiver seekToNext30Sec = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            seekTo(mediaPlayer.getCurrentPosition() + 30000);
            if (!isPlaying()) {
                resumeMedia();
            }
        }
    };
    private BroadcastReceiver seekToPrevious10Sec = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            seekTo(mediaPlayer.getCurrentPosition() - 10000);
            if (!isPlaying()) {
                resumeMedia();
            }
        }
    };
    private BroadcastReceiver showTitle = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Intent broadcastIntent = new Intent(Broadcast_SET_TITLE);
            broadcastIntent.putExtra("title", audioList.get(audioIndex).getName());
            sendBroadcast(broadcastIntent);
        }
    };
    private BroadcastReceiver getPosition = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Intent broadcastIntent = new Intent(Broadcast_SET_PROGRESS);
            broadcastIntent.putExtra("timeCurrent", resumePosition);
            broadcastIntent.putExtra("timeEnd", timeDuration);
            sendBroadcast(broadcastIntent);
        }
    };

    private BroadcastReceiver setSpeed = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && mediaPlayer != null) {
                Intent broadcastIntent = new Intent(Broadcast_SET_IMAGE);
                broadcastIntent.putExtra("id", R.drawable.ic_pause);
                sendBroadcast(broadcastIntent);
                mediaPlayer.setPlaybackParams(
                        mediaPlayer.getPlaybackParams().setSpeed(BookPresenter.speed));
            }
        }
    };

    private BroadcastReceiver closeNotPrerepred = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "onReceive: closeNotPrerepred");
            if (!prepared) stopSelf();
        }
    };

    private BroadcastReceiver closeIfPause = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (mediaPlayer == null || !mediaPlayer.isPlaying()) {
                stopForeground(true);
                stopSelf();
            }
        }
    };


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
            mAudioDBModel = new AudioDBModel(this);
            String urlImage = storage.loadImageUrl();
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
        return START_NOT_STICKY;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        if (!isPlaying()) {
            mediaSession.release();
        }
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mediaPlayer != null) {
            int time = mediaPlayer.getCurrentPosition() / 1000;
            mAudioDBModel.setTime(urlBook, time);
            stopMedia();
            mediaPlayer.release();
        }

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

        //clear cached playlist
        storage.clearCachedAudioPlaylist();
        mAudioDBModel.closeDB();
    }

    /**
     * MediaPlayer callback methods
     */
    @Override
    public void onBufferingUpdate(MediaPlayer mp, int percent) {
        //Invoked indicating buffering status of
        //a media resource being streamed over the network.
    }

    @Override
    public void onCompletion(MediaPlayer mp) {
        //Invoked when playback of a media source has completed.

        if (!buttonClick) {
            skipToNext();
            updateMetaData();
        } else {
            stopMedia();
            stopSelf();
        }
        buttonClick = false;
    }

    @Override
    public boolean onError(MediaPlayer mp, int what, int extra) {
        //Invoked when there has been an error during an asynchronous operation
        switch (what) {
            case MediaPlayer.MEDIA_ERROR_NOT_VALID_FOR_PROGRESSIVE_PLAYBACK:
                Log.d("MediaPlayer Error",
                        "MEDIA ERROR NOT VALID FOR PROGRESSIVE PLAYBACK " + extra);
                break;
            case MediaPlayer.MEDIA_ERROR_SERVER_DIED:
                Log.d("MediaPlayer Error", "MEDIA ERROR SERVER DIED " + extra);
                break;
            case MediaPlayer.MEDIA_ERROR_UNKNOWN:
                Log.d("MediaPlayer Error", "MEDIA ERROR UNKNOWN " + extra);
                break;
        }
        return false;
    }

    @Override
    public boolean onInfo(MediaPlayer mp, int what, int extra) {
        //Invoked to communicate some info
        return false;
    }

    @Override
    public void onPrepared(MediaPlayer mp) {
        //Invoked when the media source is ready for playback.
        prepared = true;
        timeDuration = mp.getDuration();
        playMedia();

        if (!BookPresenter.start) {
            if (timeStart != 0) seekTo(timeStart * 1000);
            pauseMedia();
            BookPresenter.start = true;
        } else if (BookPresenter.resume) {
            if (timeStart != 0) seekTo(timeStart * 1000);
            BookPresenter.resume = false;
        }
    }

    @Override
    public void onSeekComplete(MediaPlayer mp) {
        //Invoked indicating the completion of a seek operation.
    }

    @Override
    public void onAudioFocusChange(int focusState) {

        //Invoked when the audio focus of the system is updated.
        switch (focusState) {
            case AudioManager.AUDIOFOCUS_GAIN:
                // resume playback
                if (mediaPlayer == null) {
                    initMediaPlayer();
                } else {
                    if (!isPlaying() && !pause) {
                        mediaPlayer.start();
                        buildNotification(PlaybackStatus.PLAYING);
                    } else {
                        mediaPlayer.pause();
                        buildNotification(PlaybackStatus.PAUSED);
                    }
                }
                mediaPlayer.setVolume(1.0f, 1.0f);
                break;
            case AudioManager.AUDIOFOCUS_LOSS:
                pauseMedia();
                pause = false;
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                if (isPlaying()) {
                    pause = false;
                    pauseMedia();
                } else {
                    pause = true;
                }
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                // Lost focus for a short time, but it's ok to keep playing
                // at an attenuated level
                pause = false;
                if (isPlaying()) mediaPlayer.setVolume(0.1f, 0.1f);
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
            return mediaPlayer != null && mediaPlayer.isPlaying();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * MediaPlayer actions
     */
    private void initMediaPlayer() {
        prepared = false;
        if (mediaPlayer == null) {
            mediaPlayer = new MediaPlayer();//new MediaPlayer instance
        }

        //Set up MediaPlayer event listeners
        mediaPlayer.setOnCompletionListener(this);
        mediaPlayer.setOnErrorListener(this);
        mediaPlayer.setOnPreparedListener(this);
        mediaPlayer.setOnBufferingUpdateListener(this);
        mediaPlayer.setOnSeekCompleteListener(this);
        mediaPlayer.setOnInfoListener(this);
        //Reset so that the MediaPlayer is not pointing to another data source
        mediaPlayer.reset();


        mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
        try {
            // Set the data source to the mediaFile location
            mediaPlayer.setDataSource(activeAudio.getUrl());
        } catch (IOException e) {
            e.printStackTrace();
            stopSelf();
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            mediaPlayer.setPlaybackParams(
                    mediaPlayer.getPlaybackParams().setSpeed(BookPresenter.speed));
        }

        mediaPlayer.prepareAsync();

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
        Log.d(TAG, "initMediaPlayer: calld");
    }

    private void playMedia() {
        if (prepared) {
            if (!requestAudioFocus()) {
                stopSelf();
            }
            isPlay = true;
            mediaPlayer.start();
            mediaSession.setActive(true);
            startPlayProgressUpdater();
            startTimeProgressUpdater();
        }
    }

    private void stopMedia() {
        if (mediaPlayer == null) return;
        if (isPlaying()) {
            isPlay = false;
            mediaPlayer.stop();
            mediaSession.setActive(false);
        }
    }

    private void pauseMedia() {
        if (isPlaying() && prepared) {
            buildNotification(PlaybackStatus.PAUSED);
            isPlay = false;
            mediaPlayer.pause();
            mediaSession.setActive(false);
            resumePosition = mediaPlayer.getCurrentPosition();
            int time = resumePosition / 1000;
            mAudioDBModel.setTime(urlBook, time);
        }
    }

    private void resumeMedia() {
        try {
            if (!isPlaying() && prepared) {
                if (!requestAudioFocus()) {
                    stopSelf();
                }
                isPlay = true;
                mediaPlayer.seekTo(resumePosition);
                mediaPlayer.start();
                mediaSession.setActive(true);
                startPlayProgressUpdater();
                startTimeProgressUpdater();
                buildNotification(PlaybackStatus.PLAYING);
            }
        } catch (Exception ignored) {

        }
    }

    private void skipToNext() {
        if (!prepared) return;
        if (audioIndex == audioList.size() - 1) {
            if (!buttonClick) {
                stopMedia();
                mediaSession.setActive(false);
            }
        } else {
            //get next in playlist
            activeAudio = audioList.get(++audioIndex);
            storage.storeAudioIndex(audioIndex);

            stopMedia();
            mediaSession.setActive(false);
            //reset mediaPlayer
            mediaPlayer.reset();
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
        if (!prepared) return;
        if (audioIndex != 0) {
            activeAudio = audioList.get(--audioIndex);
            storage.storeAudioIndex(audioIndex);
            stopMedia();
            mediaSession.setActive(false);
            //reset mediaPlayer
            mediaPlayer.reset();
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
        // through its MediaSessionCompat.Callback.
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
                                | PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS)
                .setState(PlaybackStateCompat.STATE_PLAYING, 0, 1, SystemClock.elapsedRealtime())
                .build();

        mediaSession.setPlaybackState(state);

        // Attach Callback to receive MediaSession updates
        mediaSession.setCallback(new MediaSessionCompat.Callback() {
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
                .setShowWhen(false)
                .setStyle(new androidx.media.app.NotificationCompat.MediaStyle()
                        .setMediaSession(mediaSession.getSessionToken())
                        .setShowActionsInCompactView(0, 1, 2))
                .setSmallIcon(android.R.drawable.stat_sys_headset)
                .setContentText("")
                .setContentTitle(activeAudio.getName())
                .setContentInfo("")
                .addAction(R.drawable.ic_notification_previous, "previous", playbackAction(3))
                .addAction(notificationAction, "pause", play_pauseAction)
                .addAction(R.drawable.ic_notification_next, "next", playbackAction(2))
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
            default:
                break;
        }
        return null;
    }

    private void removeNotification() {
        //stopForeground(true);
        NotificationManager notificationManager = (NotificationManager) getSystemService(
                Context.NOTIFICATION_SERVICE);
        Objects.requireNonNull(notificationManager).cancel(NOTIFICATION_ID);
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
            if (prepared) {
                Intent broadcastIntent = new Intent(Broadcast_SET_PROGRESS);
                broadcastIntent.putExtra("timeCurrent", mediaPlayer.getCurrentPosition());
                broadcastIntent.putExtra("timeEnd", timeDuration);
                this.sendBroadcast(broadcastIntent);
                if (mediaPlayer.getCurrentPosition() >= mediaPlayer.getDuration()) {
                    buttonClick = true;
                    skipToNext();
                }

                if (isPlaying()) {
                    Runnable notification = this::startPlayProgressUpdater;
                    handler.postDelayed(notification, 200);
                }
            }
        } catch (IllegalStateException ignored) {

        }
    }

    private void startTimeProgressUpdater() {
        try {
            if (prepared) {
                if (isPlaying()) {
                    int time = mediaPlayer.getCurrentPosition() / 1000;
                    mAudioDBModel.setTime(urlBook, time);
                    Runnable notification = this::startTimeProgressUpdater;
                    handler.postDelayed(notification, 10000);
                }
            }
        } catch (IllegalStateException ignored) {

        }
    }

    private void seekTo(long postion) {
        try {
            if (prepared && postion >= 0 && timeDuration > postion) {
                mediaPlayer.seekTo((int) postion);
                resumePosition = (int) postion;
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
