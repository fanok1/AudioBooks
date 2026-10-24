package com.fanok.audiobooks.service;

import static android.support.v4.media.MediaBrowserCompat.MediaItem.FLAG_PLAYABLE;
import static com.fanok.audiobooks.activity.BookActivity.Broadcast_SET_IMAGE;
import static com.fanok.audiobooks.activity.BookActivity.Broadcast_SET_PROGRESS;
import static com.fanok.audiobooks.activity.BookActivity.Broadcast_SET_SELECTION;
import static com.fanok.audiobooks.activity.BookActivity.Broadcast_SET_TITLE;
import static com.fanok.audiobooks.activity.BookActivity.Broadcast_SHOW_EQUALIZER;
import static com.fanok.audiobooks.activity.BookActivity.Broadcast_SHOW_RATING;
import static com.fanok.audiobooks.activity.SleepTimerActivity.Broadcast_UPDATE_TIMER_START;
import static com.fanok.audiobooks.activity.SleepTimerActivity.bradcast_FinishTimer;
import static com.fanok.audiobooks.activity.SleepTimerActivity.bradcast_UpdateTimer;
import static com.fanok.audiobooks.android_equalizer.EqualizerFragment.Broadcast_EqualizerEnabled;
import static com.fanok.audiobooks.presenter.BookPresenter.Broadcast_Equalizer;
import static com.fanok.audiobooks.presenter.BookPresenter.Broadcast_PAUSE;
import static com.fanok.audiobooks.presenter.BookPresenter.Broadcast_PLAY;
import static com.fanok.audiobooks.presenter.BookPresenter.Broadcast_PLAY_NEXT;
import static com.fanok.audiobooks.presenter.BookPresenter.Broadcast_PLAY_PREVIOUS;
import static com.fanok.audiobooks.presenter.BookPresenter.Broadcast_RESUME;
import static com.fanok.audiobooks.presenter.BookPresenter.Broadcast_SEEK_NEXT_30;
import static com.fanok.audiobooks.presenter.BookPresenter.Broadcast_SEEK_PREVIOUS_10;
import static com.fanok.audiobooks.presenter.BookPresenter.Broadcast_SEEK_TO;
import static com.fanok.audiobooks.presenter.BookPresenter.Broadcast_SET_SPEED;
import static com.fanok.audiobooks.presenter.BookPresenter.Broadcast_SHOW_TITLE;
import static com.fanok.audiobooks.presenter.SleepTimerPresenter.Broadcast_SleepTimer;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.SearchManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.media.audiofx.BassBoost;
import android.media.audiofx.Equalizer;
import android.media.audiofx.PresetReverb;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaBrowserCompat.MediaItem;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;
import android.view.KeyEvent;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.annotation.VisibleForTesting;
import androidx.core.app.NotificationCompat;
import androidx.core.app.ServiceCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.IntentCompat;
import androidx.media.MediaBrowserServiceCompat;
import androidx.media.session.MediaButtonReceiver;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem.ClippingConfiguration;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.HttpDataSource;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector;
import androidx.media3.extractor.DefaultExtractorsFactory;
import androidx.preference.PreferenceManager;
import com.fanok.audiobooks.Hourglass;
import com.fanok.audiobooks.R;
import com.fanok.audiobooks.activity.BookActivity;
import com.fanok.audiobooks.activity.LoadBook;
import com.fanok.audiobooks.activity.SearchableActivity;
import com.fanok.audiobooks.android_equalizer.EqualizerModel;
import com.fanok.audiobooks.android_equalizer.Settings;
import com.fanok.audiobooks.broadcasts.OnCancelBroadcastReceiver;
import com.fanok.audiobooks.model.AudioDBModel;
import com.fanok.audiobooks.model.AudioListDBModel;
import com.fanok.audiobooks.model.AudioModel;
import com.fanok.audiobooks.model.BooksDBModel;
import com.fanok.audiobooks.pojo.AudioPOJO;
import com.fanok.audiobooks.pojo.BookPOJO;
import com.fanok.audiobooks.pojo.StorageUtil;
import com.fanok.audiobooks.policy.ParentalControl;
import com.fanok.audiobooks.presenter.BookPresenter;
import com.fanok.audiobooks.presenter.SleepTimerPresenter;
import com.fanok.audiobooks.room.BooksAudioEntity;
import com.fanok.audiobooks.util.DeletionPlanner;
import com.fanok.audiobooks.util.DownloadStorageHelper;
import com.fanok.audiobooks.util.DownloadUtil;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.Target;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import org.jsoup.HttpStatusException;

/**
 * Plays one book. The user's intent ({@link #wantPlaying}) is changed only by the commands {@link #play()},
 * {@link #pause()}, {@link #toggle()}, {@link #seekTo(long)}, {@link #setSpeed(float)}, {@link #next()},
 * {@link #previous()}, {@link #select(int, boolean)}, {@link #seekAndPlay(long)} and {@link #stop()}; notification
 * actions, media buttons, session callbacks and the app's broadcasts are thin adapters over them. Every command
 * sets the intent first; asynchronous completions read the intent as it is when they land. One {@link ExoPlayer}
 * lives as long as the service; a chapter change only replaces its media source. Every replacement (requested,
 * not completed) and stop bumps {@link #gen}, and asynchronous work started for an older generation is dropped.
 */
@OptIn(markerClass = UnstableApi.class)
public class MediaPlayerService extends MediaBrowserServiceCompat {

    public static final String MY_MEDIA_ROOT_ID = "Root";

    public static final String ACTION_START = "start";

    public static final String ACTION_PLAY = "audioBook.ACTION_PLAY";

    public static final String ACTION_PAUSE = "audioBook.ACTION_PAUSE";

    public static final String ACTION_PREVIOUS = "audioBook.ACTION_PREVIOUS";

    public static final String ACTION_REWIND = "audioBook.ACTION_REWIND";

    public static final String ACTION_FORWARD = "audioBook.ACTION_FAST_FORWARD";

    public static final String ACTION_NEXT = "audioBook.ACTION_NEXT";

    public static final String ACTION_STOP = "audioBook.ACTION_STOP";

    private static final String TAG = "MediaPlayerService";
    //AudioPlayer notification ID
    private static final int NOTIFICATION_ID = 101;
    private static final String CHANNEL_ID = "124";
    private static final String CHANNEL_NAME = "Player";
    public static final int countAudioWereShowingRatingPopUp = 15;
    private static final int REWIND = 30000;
    private static final int FAST_FORWARD = 30000;
    private static final long PROGRESS_INTERVAL_MS = 500;
    private static final long CHECKPOINT_INTERVAL_MS = 10_000;
    /** How long the retry wake lock outlasts the delay of the retry it keeps on time. */
    private static final long RETRY_WAKE_MARGIN_MS = 20_000;
    /** The retry wake lock while a chapter list refresh is fetched (a few page loads, 10 s timeout each). */
    private static final long REFRESH_WAKE_MS = 60_000;
    /** The retry wake lock after the retry ran, until the player holds its own locks for the load. */
    private static final long HANDOVER_WAKE_MS = 5_000;
    /** A retry that comes during a call waits this long and looks again: focus would be refused. */
    private static final long CALL_RECHECK_MS = 3_000;
    /** A transient audio focus loss longer than this, outside a call, is taken as a pause. */
    private static final long FOCUS_LOSS_LIMIT_MS = 30 * 60_000L;
    private static final long FOCUS_LOSS_RECHECK_MS = 60_000;
    /** Waiting for data longer than this, while it should play, is shown as a stall, not as silent playback. */
    private static final long STALL_NOTICE_MS = 3_000;

    private static final long SESSION_ACTIONS = PlaybackStateCompat.ACTION_PLAY
            | PlaybackStateCompat.ACTION_PAUSE
            | PlaybackStateCompat.ACTION_PLAY_PAUSE
            | PlaybackStateCompat.ACTION_SKIP_TO_NEXT
            | PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
            | PlaybackStateCompat.ACTION_SEEK_TO
            | PlaybackStateCompat.ACTION_REWIND
            | PlaybackStateCompat.ACTION_FAST_FORWARD
            | PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID
            | PlaybackStateCompat.ACTION_PLAY_FROM_SEARCH
            | PlaybackStateCompat.ACTION_STOP;

    // Published for in-process readers; written on the main thread only.
    private static volatile boolean running;
    private static volatile boolean isPlay;
    private static volatile String currentBookUrl;

    private static final AtomicReference<Request> pendingRequest = new AtomicReference<>();

    private static volatile DataSource.Factory upstreamForTest;

    /** What to play. Handed to the service in-process by {@link #start}, never through preferences. */
    public static final class Request {
        @Nullable
        final BookPOJO book;
        @NonNull
        final String bookUrl;
        @NonNull
        final ArrayList<AudioPOJO> chapters;
        @Nullable
        final int[] ordinals;
        final int index;
        final long positionMs;
        final boolean play;

        /**
         * @param ordinals each chapter's index in the book's full chapter list when {@code chapters} is a subset
         *                 (offline copies), else null; checkpoints always record the full-list index
         */
        public Request(@Nullable BookPOJO book, @NonNull String bookUrl, @NonNull List<AudioPOJO> chapters,
                @Nullable int[] ordinals, int index, long positionMs, boolean play) {
            this.book = book;
            this.bookUrl = bookUrl;
            this.chapters = new ArrayList<>(chapters);
            this.ordinals = ordinals != null && ordinals.length == chapters.size() ? ordinals.clone() : null;
            this.index = index;
            this.positionMs = positionMs;
            this.play = play;
        }
    }

    private final IBinder mBinder = new LocalBinder();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService dbExecutor = Executors.newSingleThreadExecutor();
    /** Generation of the newest queued legacy write per kind and book; an older one queued before it is stale. */
    private final ConcurrentHashMap<String, Integer> legacyWrites = new ConcurrentHashMap<>();
    private final PlaybackErrorPolicy errorPolicy = new PlaybackErrorPolicy();
    private final PatientLoadErrorPolicy loadErrorPolicy = new PatientLoadErrorPolicy(() -> this.wantPlaying);
    private final DefaultExtractorsFactory extractorsFactory =
            new DefaultExtractorsFactory().setConstantBitrateSeekingEnabled(true);

    private ExoPlayer player;
    private EndAwareRenderersFactory renderersFactory;
    private MediaSessionCompat mediaSession;
    private StorageUtil storage;
    private SharedPreferences mPreferences;
    private AudioDBModel mAudioDBModel;
    private boolean receiverRegistered;

    // the book
    @Nullable
    private BookPOJO book;
    @Nullable
    private String bookUrl;
    @Nullable
    private ArrayList<AudioPOJO> chapters;
    @Nullable
    private int[] ordinals;
    private int chapterIndex;
    @Nullable
    private PendingIntent contentIntent;
    private String imageUrl = "";
    private Bitmap image;
    private Target artworkTarget;
    private String artworkFailedUrl;

    // the state machine
    /** The user's intent; volatile because the load policy reads it on the playback thread. */
    private volatile boolean wantPlaying;
    private int gen;
    /** The chapter's source is being resolved on the executor. */
    private boolean resolving;
    /** A cold start is reading the last book from the database. */
    private boolean restoring;
    /** Where the chapter being loaded starts. */
    private long startPositionMs;
    /** Where the chapter's audio ended early and the chapter was loaded again, {@link C#TIME_UNSET} for nowhere. */
    private long recoveredAtMs = C.TIME_UNSET;
    /** The chapter was ended where its audio ended early, short of an end Media3 never reached: Play replays. */
    private boolean finishedEarly;
    /** The error being shown, or retried while {@link #retryTask}/{@link #refreshTask} is pending. */
    @Nullable
    private PlaybackErrorPolicy.Kind failedKind;
    @Nullable
    private Runnable retryTask;
    @Nullable
    private Disposable refreshTask;
    /** Keeps a pending retry or refresh on time with the screen off: in IDLE the player holds no lock. */
    @Nullable
    private PowerManager.WakeLock retryWakeLock;
    @Nullable
    private ConnectivityManager.NetworkCallback networkCallback;
    /** The player gave up while playing: Next and Previous play the chapter they load. Any other command clears it. */
    private boolean resumeOnSkip;
    /** A stall notice is shown (chapter title, notification), or due after {@link #STALL_NOTICE_MS}. */
    private boolean stallShown;
    private boolean stallPending;
    private final Runnable stallNotice = this::onStallNotice;
    /** elapsedRealtime when a transient audio focus loss silenced the player, -1 while it is not silenced. */
    private long suppressedSince = -1;
    /** elapsedRealtime of the pause, 0 for "long ago" (a freshly loaded book), -1 while not paused. */
    private long pausedAt = -1;
    private long lastCheckpointAt;
    private int shownIcon;
    private boolean isForeground;
    @Nullable
    private Hourglass sleepTimer;

    private boolean mEqualizerEnabled = true;
    private Equalizer mEqualizer;
    private BassBoost bassBoost;
    private PresetReverb presetReverb;

    /** Starts (or reaches the running) service and makes it play {@code request}. */
    public static void start(@NonNull Context context, @NonNull Request request) {
        pendingRequest.set(request);
        try {
            ContextCompat.startForegroundService(context,
                    new Intent(context, MediaPlayerService.class).setAction(ACTION_START));
        } catch (RuntimeException e) {
            pendingRequest.compareAndSet(request, null);
            throw e;
        }
    }

    /** Whether the user wants the loaded book to play (it may still be buffering). */
    public static boolean isPlay() {
        return isPlay;
    }

    public static boolean isRunning() {
        return running;
    }

    /** The book the service holds, or null. */
    @Nullable
    public static String currentBookUrl() {
        return currentBookUrl;
    }

    public static int getNotificationId() {
        return NOTIFICATION_ID;
    }

    /** Instrumentation tests serve fixtures from a loopback HTTPS server through their own upstream. */
    @VisibleForTesting
    public static void setUpstreamFactoryForTest(@Nullable DataSource.Factory factory) {
        upstreamForTest = factory;
    }

    private final BroadcastReceiver commandReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            onCommandBroadcast(intent);
        }
    };

    private final Player.Listener playerListener = new Player.Listener() {
        @Override
        public void onPlaybackStateChanged(int playbackState) {
            if (playbackState == Player.STATE_ENDED) {
                onChapterEnded();
                return;
            }
            if (playbackState == Player.STATE_READY) {
                //READY never changes the intent and never seeks: the start position went with the source
                errorPolicy.reset();
                failedKind = null;
                publishMetadata();
            }
            publishState();
        }

        @Override
        public void onIsPlayingChanged(boolean isPlaying) {
            if (isPlaying) {
                startProgress();
                if (sleepTimer != null) {
                    sleepTimer.resumeTimer();
                }
            } else {
                stopProgress();
                if (sleepTimer != null) {
                    sleepTimer.pauseTimer();
                }
                sendProgress();
            }
            publishState();
        }

        @Override
        public void onPlayWhenReadyChanged(boolean playWhenReady, int reason) {
            if (!playWhenReady && wantPlaying && reason != Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST) {
                //focus loss or unplugged headphones paused the player: from now on that is the intent
                wantPlaying = false;
                pausedAt = SystemClock.elapsedRealtime();
                checkpointNow(false);
            }
            publishState();
        }

        @Override
        public void onPlaybackSuppressionReasonChanged(int playbackSuppressionReason) {
            //another app's transient focus silences the player: the controls offer Play, which asks for it back
            handler.removeCallbacks(focusLossWatchdog);
            if (playbackSuppressionReason == Player.PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS) {
                suppressedSince = SystemClock.elapsedRealtime();
                handler.postDelayed(focusLossWatchdog, FOCUS_LOSS_LIMIT_MS);
            } else {
                suppressedSince = -1;
            }
            publishState();
        }

        @Override
        public void onPlayerError(@NonNull PlaybackException error) {
            handleError(error);
        }

        @Override
        public void onAudioSessionIdChanged(int audioSessionId) {
            attachEffects();
        }
    };

    private final Runnable progressTick = new Runnable() {
        @Override
        public void run() {
            if (finishIfAudioEndedEarly()) {
                return;
            }
            sendProgress();
            if (SystemClock.elapsedRealtime() - lastCheckpointAt >= CHECKPOINT_INTERVAL_MS) {
                checkpointNow(false);
            }
            handler.postDelayed(this, PROGRESS_INTERVAL_MS);
        }
    };

    private final Runnable notifyTask = this::postNotification;

    private final Runnable focusLossWatchdog = this::onLongFocusLoss;

    @Override
    public void onCreate() {
        super.onCreate();
        running = true;
        storage = new StorageUtil(getApplicationContext());
        mPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        mAudioDBModel = new AudioDBModel(this);
        createNotificationChannel();
        initMediaSession();
        initPlayer();
        registerCommandReceiver();
        registerNetworkCallback();
        publishState();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        //1. nothing started before this point may act any more; the final position is written synchronously
        gen++;
        checkpointNow(true);

        //2. timers, handlers, artwork
        if (sleepTimer != null) {
            sleepTimer.cancel();
            sleepTimer = null;
        }
        SleepTimerPresenter.setTimerStarted(false);
        unregisterNetworkCallback();
        cancelRetry();
        handler.removeCallbacksAndMessages(null);
        cancelArtwork();

        //3. effects, player, session
        stopEqualizer();
        if (player != null) {
            try {
                player.release();
            } catch (RuntimeException e) {
                Log.e(TAG, "player.release failed", e);
            }
            player = null;
        }
        if (mediaSession != null) {
            try {
                mediaSession.setActive(false);
                mediaSession.release();
            } catch (RuntimeException e) {
                Log.e(TAG, "mediaSession.release failed", e);
            }
            mediaSession = null;
        }

        //4. receivers
        if (receiverRegistered) {
            try {
                unregisterReceiver(commandReceiver);
            } catch (IllegalArgumentException e) {
                Log.w(TAG, "receiver was not registered", e);
            }
            receiverRegistered = false;
        }
        leaveForeground();
        removeNotification();
        isPlay = false;
        currentBookUrl = null;

        //5. the executor goes last; every submit checks isShutdown()
        dbExecutor.shutdown();
        //only now is the teardown complete, checkpoint included
        running = false;
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

    /** The current book's chapters, unless parental control hides the book; the policy is read off this thread. */
    @Override
    public void onLoadChildren(@NonNull final String parentId, @NonNull final Result<List<MediaItem>> result) {
        if (!MY_MEDIA_ROOT_ID.equals(parentId) || !hasBook()) {
            result.sendResult(new ArrayList<>());
            return;
        }
        final String url = bookUrl;
        final String subtitle = bookName();
        final List<MediaBrowserCompat.MediaItem> mediaItems = new ArrayList<>();
        for (int i = 0; i < chapters.size(); i++) {
            MediaDescriptionCompat description = new MediaDescriptionCompat.Builder()
                    .setMediaId(Integer.toString(i))
                    .setTitle(chapters.get(i).getName())
                    .setSubtitle(subtitle)
                    .build();
            mediaItems.add(new MediaBrowserCompat.MediaItem(description, FLAG_PLAYABLE));
        }
        final Context app = getApplicationContext();
        result.detach();
        boolean queued = execDb(() -> {
            boolean allowed;
            try {
                allowed = ParentalControl.isAllowed(app, new BooksDBModel(app).getSomewhere(url));
            } catch (RuntimeException e) {
                Log.w(TAG, "onLoadChildren: cannot read the book", e);
                allowed = false;
            }
            final List<MediaBrowserCompat.MediaItem> children = allowed ? mediaItems : new ArrayList<>();
            handler.post(() -> result.sendResult(children));
        });
        if (!queued) {
            result.sendResult(new ArrayList<>());
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        //startForegroundService() requires this within seconds, whatever the start reason
        if (!enterForeground()) {
            stop();
            return START_NOT_STICKY;
        }
        String action = intent != null ? intent.getAction() : null;
        if (ACTION_START.equals(action)) {
            Request request = pendingRequest.getAndSet(null);
            if (request != null) {
                apply(request);
            }
        } else if (Intent.ACTION_MEDIA_BUTTON.equals(action)) {
            KeyEvent event = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_KEY_EVENT, KeyEvent.class);
            if (event != null) {
                handleMediaKey(event);
            }
        } else if (action != null) {
            handleIncomingAction(action);
        }
        if (!hasBook() && !restoring) {
            //nothing to play and nothing on its way: never stay foreground on a placeholder
            stop();
        }
        return START_NOT_STICKY;
    }

    // ---------------------------------------------------------------------------------------------------------
    // Commands: the only code that changes the intent or playWhenReady

    /** Plays the loaded book; after an error re-prepares the chapter, after the end replays the book. */
    public void play() {
        resumeOnSkip = false;
        wantPlaying = true;
        if (player == null) {
            return;
        }
        if (!hasBook()) {
            //the book is read from the database; its arrival plays it unless a pause came first
            if (!restoring) {
                restoreBook(null);
            }
            publishState();
            return;
        }
        int state = player.getPlaybackState();
        if (!resolving && (state == Player.STATE_ENDED || finishedEarly) && chapterIndex >= chapters.size() - 1) {
            //the finished book starts over
            pausedAt = -1;
            loadChapter(0, 0, true);
            return;
        }
        if (!resolving && pausedAt >= 0 && state != Player.STATE_IDLE) {
            long position = player.getCurrentPosition();
            long rewound = rewoundAfterPause(position, pausedAt);
            if (rewound != position) {
                player.seekTo(rewound);
            }
        }
        pausedAt = -1;
        if (!resolving && state == Player.STATE_IDLE) {
            //after an error, or a retry still waiting: the user asked to try now
            retryNow(true);
            publishState();
            return;
        }
        //also while another app's transient focus silences the player: asking again takes the focus back
        player.setPlayWhenReady(true);
        publishState();
    }

    /**
     * Re-prepares the failed chapter now, at the position the player keeps. Only the user's Play restores the
     * error budget; an automatic retry goes on spending it.
     */
    private void retryNow(boolean byUser) {
        cancelRetry();
        failedKind = null;
        if (byUser) {
            errorPolicy.reset();
        }
        if (player.getMediaItemCount() == 0) {
            //the chapter never loaded (invalid clip, unresolved source): load it again, never another one
            loadChapter(chapterIndex, startPositionMs, false);
            return;
        }
        player.prepare();
        player.setPlayWhenReady(true);
    }

    /** Pauses in every state; pending retries and source resolution can no longer start audio. */
    public void pause() {
        //also before the book or the player exists: a pending restore or resolution reads this when it lands
        resumeOnSkip = false;
        wantPlaying = false;
        cancelRetry();
        if (pausedAt < 0) {
            pausedAt = SystemClock.elapsedRealtime();
        }
        if (player != null) {
            player.setPlayWhenReady(false);
        }
        checkpointNow(false);
        publishState();
    }

    /**
     * The only toggling command: decided by the intent, not by what the engine is doing. A player silenced by
     * another app's transient audio focus shows Play, so the press plays (takes the focus back).
     */
    public void toggle() {
        if (wantPlaying && !isFocusSuppressed()) {
            pause();
        } else {
            play();
        }
    }

    /**
     * Seeks within the chapter, clamped to it; never changes the intent. While the chapter is not loaded (its
     * source is being resolved, or it failed to load) the seek moves where it will start.
     */
    public void seekTo(long positionMs) {
        if (player == null || !hasBook()) {
            return;
        }
        long target = Math.max(0, positionMs);
        if (resolving || player.getMediaItemCount() == 0) {
            startPositionMs = target;
        } else {
            if (!player.getCurrentTimeline().isEmpty() && !player.isCurrentMediaItemSeekable()) {
                return;
            }
            long duration = player.getDuration();
            if (duration != C.TIME_UNSET) {
                target = Math.min(target, duration);
            }
            player.seekTo(target);
        }
        //an explicit position is not rewound after the pause
        pausedAt = -1;
        sendProgress();
        publishState();
    }

    /**
     * The "resume after seeking" preference (spec §8): the one command in which a seek also plays. Only the app's
     * own seek bar and skip buttons use it, and only while the preference is on; binder and session seeks go to
     * {@link #seekTo(long)}, which never changes the intent.
     */
    public void seekAndPlay(long positionMs) {
        seekTo(positionMs);
        if (!wantPlaying && hasBook()) {
            play();
        }
    }

    private void seekBy(long deltaMs) {
        if (player != null && hasBook()) {
            seekTo(currentPositionMs() + deltaMs);
        }
    }

    /** Changes the speed only; a paused player stays paused. */
    public void setSpeed(float speed) {
        if (player == null) {
            return;
        }
        player.setPlaybackParameters(new PlaybackParameters(PlaybackSettings.clampSpeed(speed)));
        publishState();
    }

    /** The next chapter with the current intent; skips a chapter that failed. */
    public void next() {
        if (player != null && hasBook() && chapterIndex < chapters.size() - 1) {
            resumeAfterGiveUp();
            loadChapter(chapterIndex + 1, 0, true);
        }
    }

    /** Skipping the chapter that made the player give up goes on playing, as it would have without the error. */
    private void resumeAfterGiveUp() {
        if (resumeOnSkip) {
            resumeOnSkip = false;
            wantPlaying = true;
            pausedAt = -1;
        }
    }

    /** The previous chapter with the current intent; on the first chapter restarts it. */
    public void previous() {
        if (player == null || !hasBook()) {
            return;
        }
        if (chapterIndex > 0) {
            resumeAfterGiveUp();
            loadChapter(chapterIndex - 1, 0, true);
        } else {
            seekTo(0);
        }
    }

    /** A chapter chosen from the list, with an explicit intent. */
    public void select(int index, boolean play) {
        if (player == null || !hasBook() || index < 0 || index >= chapters.size()) {
            return;
        }
        resumeOnSkip = false;
        wantPlaying = play;
        pausedAt = play ? -1 : 0;
        loadChapter(index, 0, true);
    }

    /** Ends playback: checkpoint, release the media, remove the notification, stop the service. */
    public void stop() {
        wantPlaying = false;
        resumeOnSkip = false;
        cancelRetry();
        gen++;
        //on disk before the book's identity goes: onDestroy has nothing left to snapshot
        checkpointNow(true);
        if (player != null) {
            player.setPlayWhenReady(false);
            player.stop();
            player.clearMediaItems();
        }
        stopProgress();
        if (sleepTimer != null) {
            sleepTimer.cancel();
            sleepTimer = null;
            SleepTimerPresenter.setTimerStarted(false);
            sendInternal(new Intent(bradcast_FinishTimer));
        }
        resolving = false;
        restoring = false;
        failedKind = null;
        book = null;
        bookUrl = null;
        chapters = null;
        ordinals = null;
        contentIntent = null;
        currentBookUrl = null;
        cancelArtwork();
        image = null;
        imageUrl = "";
        leaveForeground();
        if (mediaSession != null) {
            mediaSession.setActive(false);
            publishMetadata();
        }
        publishState();
        removeNotification();
        notifyChildrenChanged(MY_MEDIA_ROOT_ID);
        stopSelf();
    }

    // ---------------------------------------------------------------------------------------------------------
    // Test accessors (main thread)

    @VisibleForTesting
    @Nullable
    public ExoPlayer getPlayer() {
        return player;
    }

    public int getChapterIndex() {
        return chapterIndex;
    }

    public boolean isWantPlaying() {
        return wantPlaying;
    }

    @VisibleForTesting
    @Nullable
    public PlaybackStateCompat getSessionPlaybackState() {
        return mediaSession != null ? mediaSession.getController().getPlaybackState() : null;
    }

    // ---------------------------------------------------------------------------------------------------------
    // Loading

    private boolean hasBook() {
        return bookUrl != null && chapters != null && !chapters.isEmpty();
    }

    private void apply(@NonNull Request request) {
        if (request.chapters.isEmpty() || player == null) {
            return;
        }
        //the outgoing book keeps where it was left
        checkpointNow(false);
        book = request.book;
        bookUrl = request.bookUrl;
        chapters = request.chapters;
        ordinals = request.ordinals;
        currentBookUrl = bookUrl;
        contentIntent = buildContentIntent();
        String photo = book != null && book.getPhoto() != null ? book.getPhoto() : "";
        if (!photo.equals(imageUrl)) {
            cancelArtwork();
            imageUrl = photo;
            image = null;
        }
        resumeOnSkip = false;
        wantPlaying = request.play;
        long position = request.positionMs;
        if (request.play) {
            position = rewoundAfterPause(position, 0);
            pausedAt = -1;
        } else {
            pausedAt = 0;
        }
        int index = Math.max(0, Math.min(request.index, chapters.size() - 1));
        if (mediaSession != null) {
            mediaSession.setActive(true);
        }
        if (!isForeground) {
            //a browser client bound the service and asked to play: become a started foreground service
            try {
                ContextCompat.startForegroundService(this, new Intent(this, MediaPlayerService.class));
            } catch (RuntimeException e) {
                Log.w(TAG, "cannot start in the foreground", e);
            }
        }
        loadChapter(index, position, true);
        notifyChildrenChanged(MY_MEDIA_ROOT_ID);
    }

    /**
     * Replaces the chapter. The source is resolved (downloads, cache) on the executor and built on this thread;
     * the player and the foreground state stay as they are.
     */
    private void loadChapter(int index, long positionMs, boolean freshChapter) {
        final int generation = ++gen;
        cancelRetry();
        //the newest command wins over a book still being read from the database
        restoring = false;
        if (freshChapter) {
            errorPolicy.reset();
            recoveredAtMs = C.TIME_UNSET;
        }
        finishedEarly = false;
        failedKind = null;
        chapterIndex = index;
        startPositionMs = Math.max(0, positionMs);
        final AudioPOJO audio = chapters.get(index);
        resolving = true;
        //the previous chapter must not go on while this one resolves, nor come back if this one fails to load:
        //Play would prepare it under this chapter's identity (stopped first, so the empty player stays IDLE)
        player.stop();
        player.clearMediaItems();
        checkpointNow(false);
        if (freshChapter) {
            recordChapter(audio);
        }
        publishMetadata();
        publishState();
        sendChapterToUi();

        if (!PlaybackErrorPolicy.isValidClip(audio.getTimeStart(), audio.getTimeFinish())) {
            resolving = false;
            failedKind = PlaybackErrorPolicy.Kind.CONTENT;
            if (wantPlaying) {
                //nothing can play: the controls must offer Play (and Next), not a pause that does nothing
                giveUp("invalid clip", -1, null);
                return;
            }
            publishState();
            return;
        }
        final String book = bookUrl;
        final ArrayList<AudioPOJO> playlist = new ArrayList<>(chapters);
        final Context app = getApplicationContext();
        boolean queued = execDb(() -> {
            //download lookup and database reads stay off the main thread
            DownloadUtil.Playback playback = null;
            try {
                playback = DownloadUtil.playbackFor(app, book, playlist, index);
            } catch (RuntimeException e) {
                Log.e(TAG, "Cannot resolve the chapter source", e);
            }
            final DownloadUtil.Playback resolved = playback;
            handler.post(() -> onSourceResolved(generation, audio, resolved));
        });
        if (!queued) {
            resolving = false;
        }
    }

    private void onSourceResolved(int generation, @NonNull AudioPOJO audio, @Nullable DownloadUtil.Playback playback) {
        if (generation != gen || player == null) {
            return;
        }
        resolving = false;
        if (playback == null) {
            failedKind = PlaybackErrorPolicy.Kind.OTHER;
            if (wantPlaying) {
                giveUp("no source", -1, hostOf(audio.getUrl()));
                return;
            }
            publishState();
            return;
        }
        DataSource.Factory dataSourceFactory = playback.dataSourceFactory;
        if (upstreamForTest != null && DeletionPlanner.isRemote(audio.getUrl())) {
            dataSourceFactory = upstreamForTest;
        }
        androidx.media3.common.MediaItem.Builder item = playback.mediaItem.setMediaId(Integer.toString(chapterIndex));
        if (audio.getTimeStart() >= 0 && audio.getTimeFinish() >= 0) {
            item.setClippingConfiguration(new ClippingConfiguration.Builder()
                    .setStartPositionMs(audio.getTimeStart() * 1000L)
                    .setEndPositionMs(audio.getTimeFinish() * 1000L)
                    .build());
        } else if (audio.getTimeStart() > 0) {
            //the open-ended last chapter of a shared stream plays from its start to the end of the stream
            item.setClippingConfiguration(new ClippingConfiguration.Builder()
                    .setStartPositionMs(audio.getTimeStart() * 1000L)
                    .setEndPositionMs(C.TIME_END_OF_SOURCE)
                    .build());
        }
        MediaSource mediaSource = new DefaultMediaSourceFactory(dataSourceFactory, extractorsFactory)
                .setLoadErrorHandlingPolicy(loadErrorPolicy)
                .createMediaSource(item.build());
        player.setMediaSource(mediaSource, startPositionMs);
        player.setPlayWhenReady(wantPlaying);
        player.prepare();
        renderersFactory.onNewSource(player);
        publishState();
    }

    /**
     * A cold start (media button, browser client) loads the last book, or the given known one, from the database
     * with the intent as it is when the book arrives.
     */
    private void restoreBook(@Nullable String requestedUrl) {
        Checkpoint last = storage.loadLastCheckpoint();
        final String url = requestedUrl != null ? requestedUrl : last != null ? last.bookUrl : null;
        if (url == null) {
            //nothing to play: the intent cannot be honoured
            wantPlaying = false;
            publishState();
            return;
        }
        //taken at request time, so the newest request wins and an older one still on its way is dropped
        final int generation = ++gen;
        final Context app = getApplicationContext();
        restoring = true;
        boolean queued = execDb(() -> {
            final ArrayList<AudioPOJO> list = new ArrayList<>();
            BookPOJO found = null;
            try {
                for (BooksAudioEntity row : new AudioListDBModel(app).getRows(url)) {
                    String local = DownloadStorageHelper.localCopyOf(app, row);
                    AudioPOJO audio = new AudioPOJO();
                    audio.setName(row.nameAudio);
                    audio.setBookName(row.booksName);
                    audio.setUrl(local != null ? local : row.urlAudio);
                    audio.setTime(row.time);
                    audio.setTimeStart(row.timeStart);
                    audio.setTimeFinish(row.timeEnd);
                    list.add(audio);
                }
                found = new BooksDBModel(app).getSomewhere(url);
            } catch (RuntimeException e) {
                Log.w(TAG, "restoreBook: cannot read the book", e);
            }
            final BookPOJO restored = found != null || list.isEmpty() ? found : knownBook(url, list.get(0));
            handler.post(() -> {
                if (generation != gen || player == null) {
                    return;
                }
                restoring = false;
                if (restored != null && !ParentalControl.isAllowed(app, restored)) {
                    //a blocked book is never started by a command: the book screen asks for the PIN first
                    wantPlaying = false;
                    if (resolving && hasBook()) {
                        //this request dropped the loaded chapter's resolution: resolve it again, paused
                        loadChapter(chapterIndex, startPositionMs, false);
                        return;
                    }
                    publishState();
                    return;
                }
                if (list.isEmpty()) {
                    if (!hasBook()) {
                        stop();
                    } else if (resolving) {
                        //this request dropped the loaded chapter's resolution: resolve it again
                        loadChapter(chapterIndex, startPositionMs, false);
                    } else if (wantPlaying && player.getPlaybackState() == Player.STATE_IDLE) {
                        //the unknown book is ignored; this request dropped the loaded one's pending retry: retry now
                        play();
                    } else {
                        //the unknown book is ignored; the loaded one follows the intent the request set
                        player.setPlayWhenReady(wantPlaying);
                        publishState();
                    }
                    return;
                }
                Checkpoint checkpoint = storage.loadCheckpoint(url);
                Checkpoint at = checkpoint != null ? checkpoint.clampTo(list.size()) : new Checkpoint(url, 0, 0);
                //the intent as it is now: a pause that came while the book was read wins
                apply(new Request(restored, url, list, null, at.chapterIndex, at.positionMs, wantPlaying));
            });
        });
        if (!queued) {
            restoring = false;
        }
        publishState();
    }

    /**
     * A book the database knows only by its chapter rows (no saved, favorite or history entry): enough for the
     * notification to open its screen, offline too, instead of loading the page.
     */
    @NonNull
    private static BookPOJO knownBook(@NonNull String url, @NonNull AudioPOJO firstChapter) {
        BookPOJO book = new BookPOJO();
        book.setUrl(url);
        book.setName(firstChapter.getBookName());
        return book;
    }

    @VisibleForTesting
    void playFromMediaId(@Nullable String mediaId) {
        int index = parseMediaIndex(mediaId);
        if (index >= 0) {
            select(index, true);
        } else if (mediaId != null && (mediaId.startsWith("https://") || mediaId.startsWith("http://"))) {
            //a book this app knows: restoreBook ignores URLs without chapters in the database
            if (mediaId.equals(bookUrl)) {
                play();
            } else {
                wantPlaying = true;
                restoreBook(mediaId);
            }
        } else {
            Log.w(TAG, "onPlayFromMediaId: ignoring invalid media id");
        }
    }

    /** @return the chapter index for a client-supplied media id, or -1 when it is not one this service issued. */
    private int parseMediaIndex(@Nullable String mediaId) {
        if (mediaId == null || mediaId.isEmpty() || !hasBook()) {
            return -1;
        }
        try {
            int index = Integer.parseInt(mediaId);
            return index >= 0 && index < chapters.size() ? index : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    // ---------------------------------------------------------------------------------------------------------
    // Engine events

    private void onChapterEnded() {
        if (resolving || !hasBook()) {
            publishState();
            return;
        }
        //an armed end-of-chapter sleep timer wins over auto-advance
        boolean sleepNow = sleepTimer != null && sleepTimer.chapterEnded();
        if (chapterIndex < chapters.size() - 1) {
            if (sleepNow) {
                wantPlaying = false;
                pausedAt = SystemClock.elapsedRealtime();
            }
            loadChapter(chapterIndex + 1, 0, true);
        } else {
            //end of the book: paused at the end, Play replays it
            wantPlaying = false;
            pausedAt = SystemClock.elapsedRealtime();
            player.setPlayWhenReady(false);
            checkpointNow(false);
            publishState();
        }
    }

    /**
     * The chapter's audio has played out while the clock is still short of the chapter's declared duration: Media3
     * would play silence up to it, and a seek into that stretch plays nothing either, as its source has finished
     * loading. Only a new source brings the sound back, so the chapter is loaded again from a little before where it
     * went silent, once per spot. An early end again at about that spot is where its audio really ends: the chapter
     * ends, through Media3's own end where it can seek (STATE_ENDED, then {@link #onChapterEnded}). One that cannot
     * seek would start over from its beginning: it ends at once. Checked while it plays.
     */
    private boolean finishIfAudioEndedEarly() {
        if (player == null || resolving || player.getPlaybackState() != Player.STATE_READY) {
            return false;
        }
        long position = player.getCurrentPosition();
        long duration = player.getDuration();
        if (!EndAwareRenderersFactory.endedEarly(renderersFactory.isAudioEnded(), position, duration)) {
            return false;
        }
        boolean seekable = player.isCurrentMediaItemSeekable();
        if (seekable && EndAwareRenderersFactory.shouldRecover(recoveredAtMs, position)) {
            Log.w(TAG, "the audio ended " + (duration - position) + " ms before the chapter's end: loading it again");
            recoveredAtMs = position;
            loadChapter(chapterIndex, Math.max(0, position - EndAwareRenderersFactory.RECOVERY_REWIND_MS), false);
            return true;
        }
        Log.w(TAG, "the audio ended " + (duration - position) + " ms before the chapter's end, seekable=" + seekable
                + ": ending the chapter");
        if (seekable) {
            player.seekTo(duration);
        } else {
            //the player stays READY: Play must still replay a book whose last chapter this was
            finishedEarly = true;
            onChapterEnded();
        }
        return true;
    }

    private void handleError(@NonNull PlaybackException error) {
        Log.w(TAG, "onPlayerError: " + error.getErrorCodeName(), error);
        cancelRetry();
        checkpointNow(false);
        int status = httpStatusOf(error);
        PlaybackErrorPolicy.Decision decision =
                errorPolicy.onError(error.errorCode, status, SystemClock.elapsedRealtime());
        failedKind = decision.kind;
        if (wantPlaying && decision.action == PlaybackErrorPolicy.Action.RETRY) {
            scheduleRetry(decision.delayMs);
        } else if (wantPlaying && decision.action == PlaybackErrorPolicy.Action.REFRESH && ordinals == null) {
            refreshChapters();
        } else if (wantPlaying) {
            //FAIL, or a refused address of offline copies, which no fresh chapter list can replace
            giveUp(error.getErrorCodeName(), status, hostOf(error));
            return;
        }
        publishState();
    }

    private static int httpStatusOf(@NonNull Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (t instanceof HttpDataSource.InvalidResponseCodeException) {
                return ((HttpDataSource.InvalidResponseCodeException) t).responseCode;
            }
        }
        return -1;
    }

    /** The host the failed request went to, else the chapter's: never a path or a query, which may be signed. */
    @Nullable
    private String hostOf(@NonNull Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (t instanceof HttpDataSource.HttpDataSourceException) {
                return ((HttpDataSource.HttpDataSourceException) t).dataSpec.uri.getHost();
            }
        }
        return hasBook() ? hostOf(chapters.get(chapterIndex).getUrl()) : null;
    }

    @Nullable
    private static String hostOf(@Nullable String url) {
        return url != null ? Uri.parse(url).getHost() : null;
    }

    /**
     * The policy stopped trying: the intent goes, so the app's button, the notification and the headset button
     * offer Play, and one press of it retries with a full budget. The error stays published until then.
     */
    private void giveUp(@NonNull String what, int httpStatus, @Nullable String host) {
        //the code, the status and the host only: chapter addresses carry signatures, some carry credentials
        Log.w(TAG, "giving up: " + what + " status=" + httpStatus + " host=" + host);
        pause();
        resumeOnSkip = true;
        Toast.makeText(getApplicationContext(), getString(errorMessage()), Toast.LENGTH_LONG).show();
    }

    /**
     * Re-prepares the chapter at the checkpoint position (the player keeps it) after {@code delayMs} unless the
     * intent changed, kept on time by the retry wake lock. During a call it looks again a little later without
     * spending the budget: the focus would be refused, and the refusal taken as a pause.
     */
    private void scheduleRetry(long delayMs) {
        final int generation = gen;
        retryTask = new Runnable() {
            @Override
            public void run() {
                if (retryTask != this) {
                    return;
                }
                if (generation != gen || player == null || !wantPlaying) {
                    retryTask = null;
                    releaseRetryWakeLock();
                    publishState();
                    return;
                }
                if (isInCall() && !errorPolicy.isExpired(SystemClock.elapsedRealtime())) {
                    handler.removeCallbacks(this);
                    holdRetryWakeLock(CALL_RECHECK_MS + RETRY_WAKE_MARGIN_MS);
                    handler.postDelayed(this, CALL_RECHECK_MS);
                    return;
                }
                retryTask = null;
                if (isInCall() || isOtherAudioActive()) {
                    //a call outlasted the retries, or the user listens to something else now: never take the focus
                    giveUp(isInCall() ? "call outlasted the retries" : "another app plays", -1, null);
                    return;
                }
                retryNow(false);
                //the player takes its own locks on its own thread
                holdRetryWakeLock(HANDOVER_WAKE_MS);
                publishState();
            }
        };
        holdRetryWakeLock(delayMs + RETRY_WAKE_MARGIN_MS);
        handler.postDelayed(retryTask, delayMs);
    }

    /**
     * The chapter address was refused: fetch the chapter list once more, quietly, and retry with it. A fetch that
     * fails is retried like network trouble; a list that no longer matches the book gives up.
     */
    private void refreshChapters() {
        final int generation = gen;
        final String url = bookUrl;
        final int index = chapterIndex;
        final int count = chapters.size();
        holdRetryWakeLock(REFRESH_WAKE_MS);
        refreshTask = new AudioModel(getApplicationContext()).getAudio(url)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(fresh -> {
                    refreshTask = null;
                    releaseRetryWakeLock();
                    if (generation != gen || !wantPlaying || !hasBook()) {
                        publishState();
                        return;
                    }
                    if (fresh.size() != count) {
                        //the book changed on the site: the checkpoint's chapter may be another one now
                        giveUp("refresh: " + fresh.size() + " chapters, not " + count, -1, hostOf(url));
                        return;
                    }
                    chapters = new ArrayList<>(fresh);
                    //where the chapter is now: a seek during the fetch moved the idle player
                    loadChapter(index, currentPositionMs(), false);
                    holdRetryWakeLock(HANDOVER_WAKE_MS);
                }, e -> {
                    refreshTask = null;
                    releaseRetryWakeLock();
                    Log.w(TAG, "chapter list refresh failed", e);
                    if (generation != gen || !wantPlaying || !hasBook()) {
                        publishState();
                        return;
                    }
                    int status = e instanceof HttpStatusException ? ((HttpStatusException) e).getStatusCode() : -1;
                    if (!PlaybackErrorPolicy.isRetryableRefreshError(e instanceof IOException, status)) {
                        //a challenge, a changed page, a book the site removed: asking again will not help
                        failedKind = PlaybackErrorPolicy.Kind.OTHER;
                        giveUp("refresh: " + e.getClass().getSimpleName(), status, hostOf(url));
                        return;
                    }
                    PlaybackErrorPolicy.Decision decision =
                            errorPolicy.onRefreshFailed(SystemClock.elapsedRealtime());
                    failedKind = decision.kind;
                    if (decision.action == PlaybackErrorPolicy.Action.RETRY) {
                        scheduleRetry(decision.delayMs);
                        publishState();
                    } else {
                        giveUp("refresh: " + e.getClass().getSimpleName(), -1, hostOf(url));
                    }
                });
    }

    private void cancelRetry() {
        if (retryTask != null) {
            handler.removeCallbacks(retryTask);
            retryTask = null;
        }
        if (refreshTask != null) {
            refreshTask.dispose();
            refreshTask = null;
        }
        releaseRetryWakeLock();
    }

    /** (Re)arms the retry wake lock for {@code timeoutMs}; it is not reference counted. */
    private void holdRetryWakeLock(long timeoutMs) {
        try {
            if (retryWakeLock == null) {
                PowerManager power = (PowerManager) getSystemService(Context.POWER_SERVICE);
                if (power == null) {
                    return;
                }
                retryWakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "audiobooks:retry");
                retryWakeLock.setReferenceCounted(false);
            }
            retryWakeLock.acquire(timeoutMs);
        } catch (RuntimeException e) {
            Log.w(TAG, "cannot hold the retry wake lock", e);
        }
    }

    private void releaseRetryWakeLock() {
        if (retryWakeLock != null && retryWakeLock.isHeld()) {
            try {
                retryWakeLock.release();
            } catch (RuntimeException e) {
                Log.w(TAG, "cannot release the retry wake lock", e);
            }
        }
    }

    /** A call rings or is in progress: audio focus requests are refused until it ends. */
    private boolean isInCall() {
        int mode = audioMode();
        return mode == AudioManager.MODE_IN_CALL || mode == AudioManager.MODE_IN_COMMUNICATION
                || mode == AudioManager.MODE_RINGTONE
                || VERSION.SDK_INT >= VERSION_CODES.R && mode == AudioManager.MODE_CALL_SCREENING;
    }

    /** Some app plays music now; this player is idle while it retries, so it is another one. */
    private boolean isOtherAudioActive() {
        AudioManager audio = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        return audio != null && audio.isMusicActive();
    }

    private int audioMode() {
        AudioManager audio = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        return audio != null ? audio.getMode() : AudioManager.MODE_NORMAL;
    }

    /** A network coming up (registering reports the current one at once) runs a pending retry now. */
    private void registerNetworkCallback() {
        ConnectivityManager connectivity = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivity == null) {
            return;
        }
        ConnectivityManager.NetworkCallback callback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(@NonNull Network network) {
                //called on a connectivity thread
                handler.post(MediaPlayerService.this::onNetworkAvailable);
            }
        };
        try {
            connectivity.registerDefaultNetworkCallback(callback);
            networkCallback = callback;
        } catch (RuntimeException e) {
            //SecurityException without the permission, TooManyRequestsException past the platform's limit
            Log.w(TAG, "cannot watch the network", e);
        }
    }

    private void unregisterNetworkCallback() {
        if (networkCallback == null) {
            return;
        }
        ConnectivityManager connectivity = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        try {
            if (connectivity != null) {
                connectivity.unregisterNetworkCallback(networkCallback);
            }
        } catch (RuntimeException e) {
            Log.w(TAG, "network callback was not registered", e);
        }
        networkCallback = null;
    }

    /**
     * Retries after network trouble now instead of after the backoff. Only a retry that is still pending: once the
     * policy gave up, nothing resumes without the user.
     */
    private void onNetworkAvailable() {
        Runnable pending = retryTask;
        if (player != null && wantPlaying && failedKind == PlaybackErrorPolicy.Kind.TRANSIENT && pending != null) {
            //the retry keeps its own guards: generation, intent, a call in progress
            handler.removeCallbacks(pending);
            pending.run();
        }
    }

    /** Another app's transient audio focus holds the player silent while it would play. */
    private boolean isFocusSuppressed() {
        return player != null && player.getPlaybackSuppressionReason()
                == Player.PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS;
    }

    /** What the play/pause controls show: Pause while the book plays or is on its way, else Play. */
    private boolean showsPause() {
        return wantPlaying && !isFocusSuppressed();
    }

    /**
     * Another app took the audio focus "for a moment" half an hour ago and never gave it back (ExoPlayer issue
     * #7182): outside a call the player stops waiting and is paused as of when it went silent.
     */
    private void onLongFocusLoss() {
        if (!wantPlaying || !isFocusSuppressed() || suppressedSince < 0) {
            return;
        }
        if (audioMode() != AudioManager.MODE_NORMAL) {
            //a call: the focus comes back when it ends
            handler.postDelayed(focusLossWatchdog, FOCUS_LOSS_RECHECK_MS);
            return;
        }
        long since = suppressedSince;
        pause();
        pausedAt = since;
    }

    // ---------------------------------------------------------------------------------------------------------
    // Checkpoints

    private int ordinalOf(int index) {
        return ordinals != null && index < ordinals.length ? ordinals[index] : index;
    }

    /** An immutable snapshot of where the book is, or null when nothing is loaded. */
    @Nullable
    private Checkpoint snapshot() {
        if (!hasBook() || player == null) {
            return null;
        }
        return new Checkpoint(bookUrl, ordinalOf(chapterIndex), currentPositionMs());
    }

    /** Where the chapter is: the pending start while it loads or after it failed to load. */
    private long currentPositionMs() {
        return resolving || player.getMediaItemCount() == 0 ? startPositionMs : player.getCurrentPosition();
    }

    private void checkpointNow(boolean synchronous) {
        Checkpoint checkpoint = snapshot();
        if (checkpoint == null) {
            return;
        }
        storage.storeCheckpoint(checkpoint, synchronous);
        lastCheckpointAt = SystemClock.elapsedRealtime();
        //legacy book-level seconds, written for the book lists only
        final String url = checkpoint.bookUrl;
        final int seconds = (int) (checkpoint.positionMs / 1000);
        queueLegacyWrite("time:" + url, () -> mAudioDBModel.setTime(url, seconds));
    }

    /** Legacy last-chapter title of the book, and the rating prompt counter, once per chapter played. */
    private void recordChapter(@NonNull AudioPOJO audio) {
        final String url = bookUrl;
        final String name = audio.getName() != null ? audio.getName() : "";
        final int seconds = (int) (startPositionMs / 1000);
        queueLegacyWrite("chapter:" + url, () -> {
            mAudioDBModel.add(url, name);
            mAudioDBModel.setTime(url, seconds);
        });
        if (storage.loadShowRating()) {
            int count = storage.loadCountAudioListneredForRating() + 1;
            if (count >= countAudioWereShowingRatingPopUp) {
                storage.storeCountAudioListneredForRating(0);
                sendInternal(new Intent(Broadcast_SHOW_RATING));
            } else {
                storage.storeCountAudioListneredForRating(count);
            }
        }
    }

    /** The "rewind after a long pause" preference: 10 s after 10 s of pause, 30 s after 30 s. */
    private long rewoundAfterPause(long position, long pausedSince) {
        if (!mPreferences.getBoolean("rewind_back", false) || pausedSince < 0) {
            return position;
        }
        long paused = pausedSince == 0 ? Long.MAX_VALUE : SystemClock.elapsedRealtime() - pausedSince;
        if (paused < 10_000) {
            return position;
        }
        return Math.max(0, position - (paused < 30_000 ? 10_000 : 30_000));
    }

    // ---------------------------------------------------------------------------------------------------------
    // Published state: session, notification, UI broadcasts

    private void publishState() {
        isPlay = hasBook() && wantPlaying;
        if (mediaSession != null) {
            mediaSession.setPlaybackState(buildPlaybackState());
        }
        int icon = showsPause() ? R.drawable.ic_pause : R.drawable.ic_play;
        if (icon != shownIcon) {
            shownIcon = icon;
            sendPlayIcon();
        }
        updateStallNotice();
        handler.removeCallbacks(notifyTask);
        handler.post(notifyTask);
    }

    /** Waiting for data while it should play: a moment at a chapter start or a seek, or a network stall. */
    private boolean isStalled() {
        return hasBook() && player != null && wantPlaying && !isShowingError() && !isFocusSuppressed()
                && (isWaiting() || player.getPlaybackState() == Player.STATE_BUFFERING);
    }

    /**
     * A wait longer than {@link #STALL_NOTICE_MS} is shown in the chapter title and the notification, so a network
     * stall does not look like playback without sound; it goes away when the audio plays again.
     */
    private void updateStallNotice() {
        if (isStalled()) {
            if (!stallShown && !stallPending) {
                stallPending = true;
                handler.postDelayed(stallNotice, STALL_NOTICE_MS);
            }
            return;
        }
        handler.removeCallbacks(stallNotice);
        stallPending = false;
        if (stallShown) {
            stallShown = false;
            sendChapterToUi();
            publishMetadata();
        }
    }

    private void onStallNotice() {
        stallPending = false;
        if (!stallShown && isStalled()) {
            stallShown = true;
            sendChapterToUi();
            publishMetadata();
            handler.removeCallbacks(notifyTask);
            handler.post(notifyTask);
        }
    }

    private boolean isWaiting() {
        return resolving || restoring || retryTask != null || refreshTask != null;
    }

    private boolean isShowingError() {
        return failedKind != null && !isWaiting() && player != null
                && player.getPlaybackState() == Player.STATE_IDLE;
    }

    @NonNull
    private PlaybackStateCompat buildPlaybackState() {
        PlaybackStateCompat.Builder builder = new PlaybackStateCompat.Builder().setActions(SESSION_ACTIONS);
        int state;
        long position = 0;
        float speed = 0f;
        if (!hasBook() || player == null) {
            state = restoring ? PlaybackStateCompat.STATE_CONNECTING : PlaybackStateCompat.STATE_NONE;
        } else {
            position = currentPositionMs();
            builder.setActiveQueueItemId(chapterIndex);
            if (isShowingError()) {
                state = PlaybackStateCompat.STATE_ERROR;
                builder.setErrorMessage(PlaybackStateCompat.ERROR_CODE_APP_ERROR, getString(errorMessage()));
            } else if (player.isPlaying()) {
                state = PlaybackStateCompat.STATE_PLAYING;
                speed = player.getPlaybackParameters().speed;
            } else if (!wantPlaying) {
                state = PlaybackStateCompat.STATE_PAUSED;
            } else if (isWaiting() || player.getPlaybackState() != Player.STATE_READY) {
                state = PlaybackStateCompat.STATE_BUFFERING;
            } else {
                //ready but silenced for a moment (transient focus loss)
                state = PlaybackStateCompat.STATE_PAUSED;
            }
        }
        builder.setState(state, position, speed, SystemClock.elapsedRealtime());
        return builder.build();
    }

    private int errorMessage() {
        return failedKind == PlaybackErrorPolicy.Kind.CONTENT
                ? R.string.playback_error_skip : R.string.playback_error_retry;
    }

    @NonNull
    private String bookName() {
        if (book != null && book.getName() != null && !book.getName().isEmpty()) {
            return book.getName();
        }
        if (hasBook() && chapters.get(chapterIndex).getBookName() != null) {
            return chapters.get(chapterIndex).getBookName();
        }
        return "";
    }

    private void publishMetadata() {
        if (mediaSession == null) {
            return;
        }
        if (!hasBook()) {
            mediaSession.setMetadata(null);
            return;
        }
        AudioPOJO audio = chapters.get(chapterIndex);
        //Android 13+ media controls show the session's title, not the notification text: the stall goes there too
        String title = stallShown
                ? getString(R.string.playback_stalled_title, audio.getName() != null ? audio.getName() : "")
                : audio.getName();
        MediaMetadataCompat.Builder metadata = new MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, Integer.toString(chapterIndex))
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
                .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, title)
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, bookName())
                .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE, bookName())
                .putLong(MediaMetadataCompat.METADATA_KEY_TRACK_NUMBER, chapterIndex + 1)
                .putLong(MediaMetadataCompat.METADATA_KEY_NUM_TRACKS, chapters.size());
        if (book != null) {
            if (book.getAutor() != null) {
                metadata.putString(MediaMetadataCompat.METADATA_KEY_ARTIST, book.getAutor());
                metadata.putString(MediaMetadataCompat.METADATA_KEY_AUTHOR, book.getAutor());
            }
            if (book.getArtist() != null) {
                //the reader
                metadata.putString(MediaMetadataCompat.METADATA_KEY_COMPOSER, book.getArtist());
                metadata.putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_DESCRIPTION, book.getArtist());
            }
        }
        if (!imageUrl.isEmpty()) {
            metadata.putString(MediaMetadataCompat.METADATA_KEY_ART_URI, imageUrl);
        }
        if (image != null) {
            metadata.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, image);
        }
        if (!resolving && player != null && player.getPlaybackState() == Player.STATE_READY
                && player.getDuration() != C.TIME_UNSET) {
            metadata.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, player.getDuration());
        }
        mediaSession.setMetadata(metadata.build());
    }

    private void sendInternal(Intent intent) {
        intent.setPackage(getPackageName());
        sendBroadcast(intent);
    }

    private void sendPlayIcon() {
        sendInternal(new Intent(Broadcast_SET_IMAGE).putExtra("id", showsPause() ? R.drawable.ic_pause : R.drawable.ic_play));
    }

    private void sendProgress() {
        if (player == null || !hasBook()) {
            return;
        }
        long duration = player.getDuration();
        Intent intent = new Intent(Broadcast_SET_PROGRESS);
        intent.putExtra("timeCurrent", (int) currentPositionMs());
        intent.putExtra("timeEnd", duration == C.TIME_UNSET ? 0 : (int) duration);
        intent.putExtra("buffered", (int) player.getBufferedPosition());
        sendInternal(intent);
    }

    private void sendChapterToUi() {
        if (!hasBook()) {
            return;
        }
        String name = chapters.get(chapterIndex).getName();
        sendInternal(new Intent(Broadcast_SET_SELECTION).putExtra("postion", chapterIndex).putExtra("name", name));
        String title = stallShown ? getString(R.string.playback_stalled_title, name != null ? name : "") : name;
        sendInternal(new Intent(Broadcast_SET_TITLE).putExtra("title", title));
    }

    private void startProgress() {
        handler.removeCallbacks(progressTick);
        lastCheckpointAt = SystemClock.elapsedRealtime();
        handler.post(progressTick);
    }

    private void stopProgress() {
        handler.removeCallbacks(progressTick);
    }

    // ---------------------------------------------------------------------------------------------------------
    // Notification

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_LOW);
            channel.setShowBadge(false);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }

    /** @return false when the platform refused: the service must not go on as a background one */
    private boolean enterForeground() {
        try {
            Notification notification = buildNotification();
            if (VERSION.SDK_INT >= VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
            } else {
                startForeground(NOTIFICATION_ID, notification);
            }
            isForeground = true;
            return true;
        } catch (IllegalStateException e) {
            Log.e(TAG, "startForeground refused", e);
            return false;
        }
    }

    private void leaveForeground() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE);
        isForeground = false;
    }

    private void removeNotification() {
        NotificationManager notificationManager = (NotificationManager) getSystemService(
                Context.NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            notificationManager.cancel(NOTIFICATION_ID);
        }
    }

    /** Updates the foreground notification from the current state; chapter changes never touch foreground. */
    private void postNotification() {
        if (!isForeground) {
            return;
        }
        NotificationManager notificationManager = (NotificationManager) getSystemService(
                Context.NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            notificationManager.notify(NOTIFICATION_ID, buildNotification());
        }
        loadArtwork();
    }

    @NonNull
    private Notification buildNotification() {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_headset)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setShowWhen(false)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setCategory(NotificationCompat.CATEGORY_TRANSPORT);
        if (!hasBook()) {
            return builder.setContentTitle(getString(R.string.app_name))
                    .setContentText(getString(R.string.loading))
                    .setOngoing(true)
                    .build();
        }
        boolean showPause = showsPause();
        builder.setContentTitle(chapters.get(chapterIndex).getName())
                .setContentText(isShowingError() ? getString(errorMessage())
                        : stallShown ? getString(R.string.playback_stalled) : bookName())
                .setLargeIcon(image)
                .setOngoing(wantPlaying)
                .setContentIntent(contentIntent)
                .setDeleteIntent(PendingIntent.getBroadcast(getApplicationContext(), 0,
                        new Intent(this, OnCancelBroadcastReceiver.class), PendingIntent.FLAG_IMMUTABLE))
                .addAction(R.drawable.ic_notification_rewind_30, getString(R.string.playback_rewind_30),
                        playbackAction(ACTION_REWIND, 4))
                .addAction(R.drawable.ic_notification_previous, getString(R.string.previous),
                        playbackAction(ACTION_PREVIOUS, 3))
                .addAction(showPause ? R.drawable.ic_notification_pause : R.drawable.ic_notification_play,
                        getString(showPause ? R.string.pause : R.string.play),
                        showPause ? playbackAction(ACTION_PAUSE, 1) : playbackAction(ACTION_PLAY, 0))
                .addAction(R.drawable.ic_notification_next, getString(R.string.next),
                        playbackAction(ACTION_NEXT, 2))
                .addAction(android.R.drawable.ic_delete, getString(R.string.stop),
                        playbackAction(ACTION_STOP, 6));
        if (book != null && book.getAutor() != null) {
            builder.setSubText(book.getAutor());
        }
        androidx.media.app.NotificationCompat.MediaStyle style =
                new androidx.media.app.NotificationCompat.MediaStyle().setShowActionsInCompactView(1, 2, 3);
        if (mediaSession != null) {
            style.setMediaSession(mediaSession.getSessionToken());
        }
        return builder.setStyle(style).build();
    }

    /** Opens the book screen from the saved book itself; only an unknown book goes through the page loader. */
    @Nullable
    private PendingIntent buildContentIntent() {
        Intent intent;
        if (book != null && book.getUrl() != null && !book.getUrl().isEmpty()) {
            intent = BookActivity.notificationIntent(this, book);
        } else if (bookUrl != null) {
            intent = new Intent(this, LoadBook.class)
                    .putExtra("url", bookUrl)
                    .putExtra("notificationClick", true);
        } else {
            return null;
        }
        return PendingIntent.getActivity(this, 0, intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private PendingIntent playbackAction(String action, int requestCode) {
        Intent playbackAction = new Intent(this, MediaPlayerService.class).setAction(action);
        return PendingIntent.getService(this, requestCode, playbackAction, PendingIntent.FLAG_IMMUTABLE);
    }

    /** One strong target per cover; a result for a cover that is no longer shown is dropped. */
    private void loadArtwork() {
        if (image != null || imageUrl.isEmpty() || artworkTarget != null || imageUrl.equals(artworkFailedUrl)) {
            return;
        }
        final String url = imageUrl;
        artworkTarget = new Target() {
            @Override
            public void onBitmapLoaded(Bitmap bitmap, Picasso.LoadedFrom from) {
                if (artworkTarget != this) {
                    return;
                }
                artworkTarget = null;
                if (url.equals(imageUrl)) {
                    image = bitmap;
                    publishMetadata();
                    handler.removeCallbacks(notifyTask);
                    handler.post(notifyTask);
                }
            }

            @Override
            public void onBitmapFailed(Exception e, Drawable errorDrawable) {
                if (artworkTarget == this) {
                    artworkTarget = null;
                    artworkFailedUrl = url;
                }
            }

            @Override
            public void onPrepareLoad(Drawable placeHolderDrawable) {
            }
        };
        try {
            Picasso.get().load(url).into(artworkTarget);
        } catch (RuntimeException e) {
            Log.w(TAG, "artwork", e);
            artworkTarget = null;
            artworkFailedUrl = url;
        }
    }

    private void cancelArtwork() {
        if (artworkTarget != null) {
            Picasso.get().cancelRequest(artworkTarget);
            artworkTarget = null;
        }
    }

    // ---------------------------------------------------------------------------------------------------------
    // Adapters: session, media buttons, notification actions, in-app broadcasts

    private void initMediaSession() {
        mediaSession = new MediaSessionCompat(getApplicationContext(), "AudioPlayer");
        Intent mediaButtonIntent = new Intent(Intent.ACTION_MEDIA_BUTTON, null, getApplicationContext(),
                MediaButtonReceiver.class);
        mediaSession.setMediaButtonReceiver(PendingIntent.getBroadcast(getApplicationContext(), 0,
                mediaButtonIntent, PendingIntent.FLAG_IMMUTABLE));
        mediaSession.setCallback(new MediaSessionCompat.Callback() {
            @Override
            public boolean onMediaButtonEvent(Intent mediaButtonEvent) {
                KeyEvent event = IntentCompat.getParcelableExtra(mediaButtonEvent, Intent.EXTRA_KEY_EVENT,
                        KeyEvent.class);
                if (event != null && handleMediaKey(event)) {
                    return true;
                }
                return super.onMediaButtonEvent(mediaButtonEvent);
            }

            @Override
            public void onPlay() {
                play();
            }

            @Override
            public void onPause() {
                pause();
            }

            @Override
            public void onSkipToNext() {
                next();
            }

            @Override
            public void onSkipToPrevious() {
                previous();
            }

            @Override
            public void onStop() {
                stop();
            }

            @Override
            public void onSeekTo(long position) {
                seekTo(position);
            }

            @Override
            public void onRewind() {
                seekBy(-REWIND);
            }

            @Override
            public void onFastForward() {
                seekBy(FAST_FORWARD);
            }

            @Override
            public void onPlayFromMediaId(final String mediaId, final Bundle extras) {
                playFromMediaId(mediaId);
            }

            /** An empty query ("play an audiobook") resumes the last book; anything else opens the search. */
            @Override
            public void onPlayFromSearch(final String query, final Bundle extras) {
                if (query == null || query.trim().isEmpty()) {
                    play();
                    return;
                }
                Intent search = new Intent(MediaPlayerService.this, SearchableActivity.class)
                        .setAction(Intent.ACTION_SEARCH)
                        .putExtra(SearchManager.QUERY, query)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(search);
            }
        });
        setSessionToken(mediaSession.getSessionToken());
    }

    /** PLAY → play, PAUSE → pause, PLAY_PAUSE and the headset button → toggle. */
    private boolean handleMediaKey(@NonNull KeyEvent event) {
        int code = event.getKeyCode();
        boolean known = code == KeyEvent.KEYCODE_MEDIA_PLAY || code == KeyEvent.KEYCODE_MEDIA_PAUSE
                || code == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE || code == KeyEvent.KEYCODE_HEADSETHOOK
                || code == KeyEvent.KEYCODE_MEDIA_NEXT || code == KeyEvent.KEYCODE_MEDIA_PREVIOUS
                || code == KeyEvent.KEYCODE_MEDIA_STOP || code == KeyEvent.KEYCODE_MEDIA_REWIND
                || code == KeyEvent.KEYCODE_MEDIA_FAST_FORWARD;
        if (!known) {
            return false;
        }
        if (event.getAction() != KeyEvent.ACTION_DOWN || event.getRepeatCount() > 0) {
            return true;
        }
        switch (code) {
            case KeyEvent.KEYCODE_MEDIA_PLAY:
                play();
                break;
            case KeyEvent.KEYCODE_MEDIA_PAUSE:
                pause();
                break;
            case KeyEvent.KEYCODE_MEDIA_NEXT:
                next();
                break;
            case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
                previous();
                break;
            case KeyEvent.KEYCODE_MEDIA_STOP:
                stop();
                break;
            case KeyEvent.KEYCODE_MEDIA_REWIND:
                seekBy(-REWIND);
                break;
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                seekBy(FAST_FORWARD);
                break;
            default:
                toggle();
        }
        return true;
    }

    /** Notification actions. */
    private void handleIncomingAction(@NonNull String action) {
        switch (action) {
            case ACTION_PLAY:
                play();
                break;
            case ACTION_PAUSE:
                pause();
                break;
            case ACTION_NEXT:
                next();
                break;
            case ACTION_PREVIOUS:
                previous();
                break;
            case ACTION_STOP:
                stop();
                break;
            case ACTION_REWIND:
                seekBy(-REWIND);
                break;
            case ACTION_FORWARD:
                seekBy(FAST_FORWARD);
                break;
            default:
        }
    }

    /** App-internal actions are never exported: other apps must not be able to drive the player. */
    private void registerCommandReceiver() {
        IntentFilter filter = new IntentFilter();
        for (String action : new String[]{Broadcast_PLAY, Broadcast_RESUME, Broadcast_PAUSE, Broadcast_PLAY_NEXT,
                Broadcast_PLAY_PREVIOUS,
                Broadcast_SEEK_TO, Broadcast_SEEK_NEXT_30, Broadcast_SEEK_PREVIOUS_10, Broadcast_SET_SPEED,
                Broadcast_SHOW_TITLE, Broadcast_SleepTimer, Broadcast_UPDATE_TIMER_START, Broadcast_Equalizer,
                Broadcast_EqualizerEnabled}) {
            filter.addAction(action);
        }
        ContextCompat.registerReceiver(this, commandReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);
        receiverRegistered = true;
    }

    private void onCommandBroadcast(@NonNull Intent intent) {
        String action = intent.getAction();
        if (action == null) {
            return;
        }
        switch (action) {
            case Broadcast_PLAY:
                //the app's play/pause button and the PLAY_PAUSE key
                toggle();
                break;
            case Broadcast_RESUME:
                play();
                break;
            case Broadcast_PAUSE:
                pause();
                break;
            case Broadcast_PLAY_NEXT:
                next();
                break;
            case Broadcast_PLAY_PREVIOUS:
                previous();
                break;
            case Broadcast_SEEK_TO:
                seekFromApp(intent.getIntExtra("postion", 0));
                break;
            case Broadcast_SEEK_NEXT_30:
                if (player != null && hasBook()) {
                    seekFromApp(currentPositionMs() + FAST_FORWARD);
                }
                break;
            case Broadcast_SEEK_PREVIOUS_10:
                if (player != null && hasBook()) {
                    seekFromApp(currentPositionMs() - REWIND);
                }
                break;
            case Broadcast_SET_SPEED:
                setSpeed(intent.getFloatExtra("speed", BookPresenter.getSpeed()));
                break;
            case Broadcast_SHOW_TITLE:
                sendChapterToUi();
                sendPlayIcon();
                sendProgress();
                break;
            case Broadcast_SleepTimer:
                onSleepTimerCommand(intent);
                break;
            case Broadcast_UPDATE_TIMER_START:
                if (sleepTimer != null) {
                    sendInternal(new Intent(bradcast_UpdateTimer).putExtra("time", sleepTimer.getTimeRemaining()));
                }
                break;
            case Broadcast_Equalizer:
                Intent reply = new Intent(Broadcast_SHOW_EQUALIZER);
                if (player != null) {
                    reply.putExtra("id", player.getAudioSessionId());
                }
                sendInternal(reply);
                break;
            case Broadcast_EqualizerEnabled:
                mEqualizerEnabled = intent.getBooleanExtra("enabled", false);
                if (!mEqualizerEnabled) {
                    stopEqualizer();
                } else {
                    storage.loadEqualizerSettings();
                    startEqualizer();
                }
                break;
            default:
        }
    }

    /** The app's seek adapters: {@link #seekAndPlay(long)} while "resume after seeking" is on, else a plain seek. */
    private void seekFromApp(long positionMs) {
        if (mPreferences.getBoolean("seekToPausePref", true)) {
            seekAndPlay(positionMs);
        } else {
            seekTo(positionMs);
        }
    }

    // ---------------------------------------------------------------------------------------------------------
    // Sleep timer

    /**
     * Arms or cancels the sleep timer. An end-of-chapter timer is a boundary on whatever chapter is current when
     * one ENDS (spec §8): a manual next/previous/select or a book replacement re-arms it on the new chapter's end,
     * since only {@link #onChapterEnded()} consumes it.
     */
    private void onSleepTimerCommand(@NonNull Intent intent) {
        if (sleepTimer != null) {
            sleepTimer.cancel();
            sleepTimer = null;
        }
        if (!intent.getBooleanExtra("start", false)) {
            return;
        }
        boolean endOfChapter = intent.getBooleanExtra("endOfChapter", false);
        Hourglass timer = new Hourglass(intent.getLongExtra("time", 0), endOfChapter) {
            @Override
            public void onTimerFinish() {
                if (sleepTimer == this) {
                    sleepTimer = null;
                }
                SleepTimerPresenter.setTimerStarted(false);
                sendInternal(new Intent(bradcast_FinishTimer));
                if (!isEndOfChapter()) {
                    stop();
                }
            }

            @Override
            public void onTimerTick(final long timeRemaining) {
                sendInternal(new Intent(bradcast_UpdateTimer).putExtra("time", timeRemaining));
            }

            @Override
            protected long boundaryRemainingMs() {
                return chapterRemainingMs();
            }
        };
        sleepTimer = timer;
        timer.startTimer();
        if (player == null || !player.isPlaying()) {
            timer.pauseTimer();
        }
        sendInternal(new Intent(bradcast_UpdateTimer).putExtra("time", timer.getTimeRemaining()));
    }

    /** Media time left in the chapter at the current speed. */
    private long chapterRemainingMs() {
        if (player == null || resolving) {
            return 0;
        }
        long duration = player.getDuration();
        if (duration == C.TIME_UNSET) {
            return 0;
        }
        float speed = player.getPlaybackParameters().speed;
        return (long) (Math.max(0, duration - player.getCurrentPosition()) / speed);
    }

    // ---------------------------------------------------------------------------------------------------------
    // Player and effects

    private static float currentSpeed() {
        return PlaybackSettings.clampSpeed(BookPresenter.getSpeed());
    }

    private void initPlayer() {
        PlaybackSettings.BufferDurations buffer = PlaybackSettings.bufferDurations(
                mPreferences.getString("bufferSize", String.valueOf(PlaybackSettings.DEFAULT_BUFFER_SECONDS)));
        DefaultLoadControl loadControl = new DefaultLoadControl.Builder()
                .setBufferDurationsMs(buffer.minBufferMs, buffer.maxBufferMs,
                        buffer.bufferForPlaybackMs, buffer.bufferForPlaybackAfterRebufferMs)
                .setBackBuffer(PlaybackSettings.BACK_BUFFER_MS, true)
                .build();
        AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                .build();
        renderersFactory = new EndAwareRenderersFactory(this);
        player = new ExoPlayer.Builder(this, renderersFactory)
                .setLoadControl(loadControl)
                .setTrackSelector(new DefaultTrackSelector(this))
                //ExoPlayer owns audio focus and becoming-noisy; both change audibility, onPlayWhenReadyChanged syncs intent
                .setAudioAttributes(audioAttributes, true)
                .setHandleAudioBecomingNoisy(true)
                .setWakeMode(C.WAKE_MODE_NETWORK)
                .build();
        player.addListener(playerListener);
        player.setPlaybackParameters(new PlaybackParameters(currentSpeed()));
        attachEffects();
    }

    /** Effects belong to the audio session, which outlives chapter changes: attached once and on a new session. */
    private void attachEffects() {
        if (!mEqualizerEnabled) {
            return;
        }
        storage.loadEqualizerSettings();
        if (Settings.isEqualizerEnabled) {
            startEqualizer();
        } else {
            stopEqualizer();
        }
    }

    private void startEqualizer() {
        if (player == null) return;
        stopEqualizer();
        int audioSesionId = player.getAudioSessionId();
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

    /**
     * Queues a legacy DB write taken from an immutable snapshot. It carries the generation it was taken in and is
     * dropped when a write of the same kind for the same book from a newer generation is queued behind it.
     */
    private void queueLegacyWrite(@NonNull final String key, @NonNull final Runnable write) {
        final int generation = gen;
        legacyWrites.put(key, generation);
        boolean queued = execDb(() -> {
            Integer newest = legacyWrites.get(key);
            if (newest != null && newest != generation) {
                return;
            }
            legacyWrites.remove(key, generation);
            try {
                write.run();
            } catch (RuntimeException e) {
                Log.e(TAG, "legacy DB write failed", e);
            }
        });
        if (!queued) {
            legacyWrites.remove(key, generation);
        }
    }

    private boolean execDb(Runnable task) {
        if (dbExecutor.isShutdown()) {
            return false;
        }
        try {
            dbExecutor.execute(task);
            return true;
        } catch (java.util.concurrent.RejectedExecutionException e) {
            return false;
        }
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
