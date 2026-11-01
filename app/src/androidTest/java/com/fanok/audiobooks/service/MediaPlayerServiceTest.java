package com.fanok.audiobooks.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.IBinder;
import android.os.SystemClock;
import android.service.notification.StatusBarNotification;
import android.support.v4.media.session.PlaybackStateCompat;
import android.view.KeyEvent;
import androidx.media3.common.Player;
import androidx.media3.datasource.okhttp.OkHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.preference.PreferenceManager;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.GrantPermissionRule;
import androidx.test.rule.ServiceTestRule;
import com.fanok.audiobooks.Consts;
import com.fanok.audiobooks.R;
import com.fanok.audiobooks.activity.BookActivity;
import com.fanok.audiobooks.model.AudioDBModel;
import com.fanok.audiobooks.model.AudioListDBModel;
import com.fanok.audiobooks.pojo.AudioListPOJO;
import com.fanok.audiobooks.pojo.AudioPOJO;
import com.fanok.audiobooks.pojo.BookPOJO;
import com.fanok.audiobooks.pojo.StorageUtil;
import com.fanok.audiobooks.presenter.BookPresenter;
import com.fanok.audiobooks.presenter.SleepTimerPresenter;
import java.io.File;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okhttp3.mockwebserver.SocketPolicy;
import okhttp3.tls.HandshakeCertificates;
import okhttp3.tls.HeldCertificate;
import okio.Buffer;
import okio.BufferedSource;
import okio.Okio;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;
import org.junit.runner.RunWith;

/**
 * Drives the real service through its start command, its notification actions and its command methods, against
 * a loopback HTTPS server serving generated WAV chapters (the app forbids cleartext, so the test hands the service
 * an upstream that trusts the server's own certificate).
 */
@RunWith(AndroidJUnit4.class)
public class MediaPlayerServiceTest {

    private static final String REWIND_PREF = "rewind_back";
    private static final String SEEK_PLAYS_PREF = "seekToPausePref";

    @Rule
    public final ServiceTestRule serviceRule = ServiceTestRule.withTimeout(5, TimeUnit.SECONDS);

    @Rule
    public final TestRule notifications = Build.VERSION.SDK_INT >= 33
            ? GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS) : RuleChain.emptyRuleChain();

    private Context context;
    private MockWebServer server;
    private final Map<String, byte[]> files = new ConcurrentHashMap<>();
    private final List<String> books = new ArrayList<>();
    /** Every response has this status while it is not 0. */
    private volatile int failWith;
    private volatile long headersDelayMs;
    private volatile boolean neverAnswer;
    private String savedLast;
    private final Map<String, Boolean> savedSettings = new ConcurrentHashMap<>();

    @Before
    public void setUp() throws Exception {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertFalse("a player service from another test is still running", awaitStopped());
        savedLast = storagePrefs().getString(Checkpoint.KEY_LAST, null);
        //positions are asserted exactly: no rewind after a pause; the app's seeks resume (the default)
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
        for (String key : new String[]{REWIND_PREF, SEEK_PLAYS_PREF}) {
            if (settings.contains(key)) {
                savedSettings.put(key, settings.getBoolean(key, false));
            }
        }
        settings.edit().putBoolean(REWIND_PREF, false).putBoolean(SEEK_PLAYS_PREF, true).commit();

        String localhost = InetAddress.getByName("localhost").getCanonicalHostName();
        HeldCertificate certificate = new HeldCertificate.Builder().addSubjectAlternativeName(localhost).build();
        HandshakeCertificates serverCertificates = new HandshakeCertificates.Builder()
                .heldCertificate(certificate).build();
        HandshakeCertificates clientCertificates = new HandshakeCertificates.Builder()
                .addTrustedCertificate(certificate.certificate()).build();
        server = new MockWebServer();
        server.useHttps(serverCertificates.sslSocketFactory(), false);
        server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                return respond(request);
            }
        });
        server.start();
        OkHttpClient client = new OkHttpClient.Builder()
                .sslSocketFactory(clientCertificates.sslSocketFactory(), clientCertificates.trustManager())
                .build();
        MediaPlayerService.setUpstreamFactoryForTest(new OkHttpDataSource.Factory(client));
        BookPresenter.setSpeedWithoutBroadcast(1f);
    }

    @After
    public void tearDown() throws Exception {
        neverAnswer = false;
        failWith = 0;
        serviceRule.unbindService();
        context.stopService(new Intent(context, MediaPlayerService.class));
        boolean stillRunning = awaitStopped();
        MediaPlayerService.setUpstreamFactoryForTest(null);
        server.shutdown();
        SharedPreferences.Editor editor = storagePrefs().edit();
        for (String book : books) {
            editor.remove(Checkpoint.key(book));
        }
        if (savedLast != null) {
            editor.putString(Checkpoint.KEY_LAST, savedLast);
        } else {
            editor.remove(Checkpoint.KEY_LAST);
        }
        editor.commit();
        SharedPreferences.Editor settings = PreferenceManager.getDefaultSharedPreferences(context).edit();
        for (String key : new String[]{REWIND_PREF, SEEK_PLAYS_PREF}) {
            Boolean saved = savedSettings.get(key);
            if (saved != null) {
                settings.putBoolean(key, saved);
            } else {
                settings.remove(key);
            }
        }
        settings.commit();
        AudioDBModel legacy = new AudioDBModel(context);
        AudioListDBModel rows = new AudioListDBModel(context);
        for (String book : books) {
            legacy.remove(book);
            rows.remove(book);
        }
        assertFalse("the service did not stop", stillRunning);
    }

    @Test
    public void duplicatePauseStaysPaused() throws Exception {
        MediaPlayerService service = startAndBind(book("pause-twice", 20), true);
        await("playing", 8_000, () -> player(service).isPlaying());

        sendAction(MediaPlayerService.ACTION_PAUSE);
        sendAction(MediaPlayerService.ACTION_PAUSE);
        await("paused", 3_000, () -> !player(service).getPlayWhenReady() && !service.isWantPlaying());
        Thread.sleep(1_000);

        assertTrue(describe(service), onMain(() -> !player(service).getPlayWhenReady()
                && !player(service).isPlaying() && !service.isWantPlaying()));
        assertEquals(PlaybackStateCompat.STATE_PAUSED,
                (int) onMain(() -> service.getSessionPlaybackState().getState()));
    }

    @Test
    public void pauseWhileBufferingClearsIntentAndNothingPlaysAfterTheResponse() throws Exception {
        headersDelayMs = 3_000;
        MediaPlayerService service = startAndBind(book("pause-buffering", 20), true);
        await("buffering on a held response", 5_000, () -> server.getRequestCount() >= 1
                && player(service).getPlaybackState() == Player.STATE_BUFFERING
                && player(service).getPlayWhenReady());

        sendAction(MediaPlayerService.ACTION_PAUSE);
        await("intent cleared while buffering", 2_000,
                () -> !player(service).getPlayWhenReady() && !service.isWantPlaying());
        await("response delivered", 8_000, () -> player(service).getPlaybackState() == Player.STATE_READY);
        Thread.sleep(700);

        assertFalse(describe(service), onMain(() -> player(service).isPlaying()
                || player(service).getPlayWhenReady() || service.isWantPlaying()));
    }

    /**
     * A refused chapter (404 fails the load at once) is retried once, then the player gives up honestly: the intent
     * goes, the error is published, the notification offers Play, and Play recovers with a full budget.
     */
    @Test
    public void givingUpDropsTheIntentAndPlayRecovers() throws Exception {
        failWith = 404;
        MediaPlayerService service = startAndBind(book("errors", 20), true);
        //the first load, the one retry after its delay, its error
        await("gave up: the error is published", PlaybackErrorPolicy.OTHER_RETRY_MS + 10_000,
                () -> service.getSessionPlaybackState().getState() == PlaybackStateCompat.STATE_ERROR
                        && !service.isWantPlaying());
        assertFalse(describe(service), onMain(() -> player(service).getPlayWhenReady()));
        assertTrue("first attempt and one retry", server.getRequestCount() >= 2);
        await("the notification offers Play", 3_000, () -> {
            Notification notification = activeNotification();
            if (notification == null || notification.actions == null) {
                return false;
            }
            for (Notification.Action action : notification.actions) {
                if (context.getString(R.string.play).contentEquals(action.title)) {
                    return true;
                }
            }
            return false;
        });

        onMain(() -> {
            failWith = 0;
            service.play();
            return null;
        });
        await("playing after Play", 8_000, () -> player(service).isPlaying() && service.isWantPlaying());
    }

    /**
     * The server fails (503) for longer than Media3's default retry budget, then recovers: the player waits in
     * BUFFERING without an error and resumes by itself, without a Play.
     */
    @Test
    public void aServerThatRecoversWithinThePatientWindowResumesWithoutPlay() throws Exception {
        failWith = 503;
        MediaPlayerService service = startAndBind(book("patient", 20), true);
        //Media3 retries a load after min((errors - 1) s, 5 s): the sixth request comes about 10 s in, well past
        //the default minimum of three retries after which the error used to reach the player
        await("past the default retry budget", 25_000, () -> server.getRequestCount() >= 6);
        assertTrue(describe(service), onMain(() -> player(service).getPlayerError() == null
                && player(service).getPlaybackState() == Player.STATE_BUFFERING
                && player(service).getPlayWhenReady() && service.isWantPlaying()));
        assertEquals(PlaybackStateCompat.STATE_BUFFERING,
                (int) onMain(() -> service.getSessionPlaybackState().getState()));

        failWith = 0;
        //the next attempt is at most 5 s away
        await("playing without Play", 12_000, () -> player(service).isPlaying());
        assertTrue(describe(service), onMain(service::isWantPlaying));
    }

    @Test
    public void pauseDuringAPendingRetryKeepsItPausedWhenTheServerRecovers() throws Exception {
        //a 404 fails the load at once; its one retry waits OTHER_RETRY_MS
        failWith = 404;
        MediaPlayerService service = startAndBind(book("retry-pause", 20), true);
        await("first error, retry pending", 15_000, () -> player(service).getPlayerError() != null
                && service.getSessionPlaybackState().getState() == PlaybackStateCompat.STATE_BUFFERING);

        sendAction(MediaPlayerService.ACTION_PAUSE);
        await("paused", 1_500, () -> !service.isWantPlaying());
        int requests = server.getRequestCount();
        failWith = 0;
        //past the retry's delay
        Thread.sleep(PlaybackErrorPolicy.OTHER_RETRY_MS + 3_000);

        assertFalse(describe(service), onMain(() -> player(service).isPlaying()
                || player(service).getPlayWhenReady() || service.isWantPlaying()));
        assertEquals("no retry after the pause", requests, server.getRequestCount());
    }

    @Test
    public void stopServiceWhilePlayingPersistsTheCheckpoint() throws Exception {
        MediaPlayerService.Request request = book("stop", 20);
        MediaPlayerService service = startAndBind(request, true);
        await("playing past a second", 8_000,
                () -> player(service).isPlaying() && player(service).getCurrentPosition() >= 1_000);

        serviceRule.unbindService();
        context.stopService(new Intent(context, MediaPlayerService.class));
        assertFalse("service destroyed", awaitStopped());

        Checkpoint checkpoint = new StorageUtil(context).loadCheckpoint(request.bookUrl);
        assertNotNull(checkpoint);
        assertEquals(0, checkpoint.chapterIndex);
        assertTrue("position " + checkpoint.positionMs, checkpoint.positionMs >= 1_000);
    }

    @Test
    public void stopServiceWhileBufferingPersistsTheCheckpoint() throws Exception {
        headersDelayMs = 4_000;
        MediaPlayerService.Request request = book("stop-buffering", 20);
        MediaPlayerService service = start(request, 5_000, true);
        await("buffering on a held response", 5_000, () -> server.getRequestCount() >= 1
                && player(service).getPlaybackState() == Player.STATE_BUFFERING);

        serviceRule.unbindService();
        context.stopService(new Intent(context, MediaPlayerService.class));
        assertFalse("service destroyed", awaitStopped());

        Checkpoint checkpoint = new StorageUtil(context).loadCheckpoint(request.bookUrl);
        assertNotNull(checkpoint);
        assertEquals(0, checkpoint.chapterIndex);
        assertTrue("position " + checkpoint.positionMs,
                checkpoint.positionMs >= 4_500 && checkpoint.positionMs <= 5_500);
    }

    /** The stop command (notification, dismiss) writes the position to disk before the book is let go. */
    @Test
    public void stopActionWritesTheCheckpointToDisk() throws Exception {
        MediaPlayerService.Request request = book("stop-action", 20);
        MediaPlayerService service = startAndBind(request, true);
        await("playing past a second", 8_000,
                () -> player(service).isPlaying() && player(service).getCurrentPosition() >= 1_000);

        serviceRule.unbindService();
        sendAction(MediaPlayerService.ACTION_STOP);
        assertFalse("service destroyed", awaitStopped());

        File file = new File(context.getApplicationInfo().dataDir, "shared_prefs/STORAGE.xml");
        String xml;
        try (BufferedSource source = Okio.buffer(Okio.source(file))) {
            xml = source.readUtf8();
        }
        assertTrue(xml.contains("\"" + Checkpoint.key(request.bookUrl) + "\""));
        Checkpoint checkpoint = new StorageUtil(context).loadCheckpoint(request.bookUrl);
        assertNotNull(checkpoint);
        assertTrue("position " + checkpoint.positionMs, checkpoint.positionMs >= 1_000);
    }

    @Test
    public void coldStartPausedAgainstASilentServerIsForegroundAtOnce() throws Exception {
        neverAnswer = true;
        MediaPlayerService.start(context, book("cold", 20));
        await("foreground notification", 5_000, this::hasForegroundNotification);

        MediaPlayerService service = bind();
        Thread.sleep(1_500);
        assertTrue(MediaPlayerService.isRunning());
        assertTrue(describe(service), onMain(() -> !service.isWantPlaying()
                && !player(service).getPlayWhenReady()));
        assertTrue(hasForegroundNotification());
    }

    /** A pause that arrives while the book is still being read from the database wins over the Play before it. */
    @Test
    public void pauseDuringAColdRestoreWins() throws Exception {
        MediaPlayerService.Request known = knownBook("cold-pause", 20);
        MediaPlayerService service = bind();

        //one main-thread turn: the database read cannot land in between
        onMain(() -> {
            service.play();
            service.pause();
            return null;
        });
        await("book restored and ready", 8_000, () -> known.bookUrl.equals(MediaPlayerService.currentBookUrl())
                && player(service).getPlaybackState() == Player.STATE_READY);
        Thread.sleep(700);

        assertFalse(describe(service), onMain(() -> player(service).isPlaying()
                || player(service).getPlayWhenReady() || service.isWantPlaying()));
    }

    @Test
    public void theNewestOfTwoBookRequestsWins() throws Exception {
        MediaPlayerService.Request first = knownBook("newest-first", 20);
        MediaPlayerService.Request second = knownBook("newest-second", 20);
        MediaPlayerService service = bind();

        onMain(() -> {
            service.playFromMediaId(first.bookUrl);
            service.playFromMediaId(second.bookUrl);
            return null;
        });
        await("the second book playing", 10_000,
                () -> second.bookUrl.equals(MediaPlayerService.currentBookUrl()) && player(service).isPlaying());
        Thread.sleep(700);

        assertEquals(second.bookUrl, MediaPlayerService.currentBookUrl());
    }

    /** No saved, favorite or history entry, only chapter rows: the notification opens the book screen itself. */
    @Test
    public void aBookKnownByItsChapterRowsOpensItsScreenFromTheNotification() throws Exception {
        MediaPlayerService.Request known = knownBook("rows-only", 20);
        MediaPlayerService service = bind();

        onMain(() -> {
            service.play();
            return null;
        });
        await("book restored", 8_000, () -> known.bookUrl.equals(MediaPlayerService.currentBookUrl()));
        await("the notification opens the book screen", 5_000, () -> {
            Notification notification = activeNotification();
            PendingIntent bookScreen = PendingIntent.getActivity(context, 0,
                    new Intent(context, BookActivity.class),
                    PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
            return notification != null && notification.contentIntent != null
                    && notification.contentIntent.equals(bookScreen);
        });
    }

    @Test
    public void previousOnFirstChapterRestartsItThenNaturalEndContinuesToTheNextChapter() throws Exception {
        MediaPlayerService service = startAndBind(book("chapters", 3, 3), true);
        await("chapter 0 playing past a second", 8_000, () -> service.getChapterIndex() == 0
                && player(service).isPlaying() && player(service).getCurrentPosition() >= 1_000);

        long restart = onMain(() -> {
            service.previous();
            return player(service).getCurrentPosition();
        });
        assertTrue("restarted at " + restart, restart < 500);
        await("chapter 1 playing", 10_000, () -> service.getChapterIndex() == 1 && player(service).isPlaying());
    }

    /** Seeks that come while the chapter's source is resolved move where it starts; previous on chapter 0 to 0. */
    @Test
    public void seeksWhileTheSourceResolvesMoveWhereTheChapterStarts() throws Exception {
        MediaPlayerService service = startAndBind(book("resolving", 20), false);
        await("ready and paused", 8_000, () -> player(service).getPlaybackState() == Player.STATE_READY);

        //one main-thread turn each: the source resolution cannot land in between
        onMain(() -> {
            service.select(0, true);
            service.seekTo(8_000);
            return null;
        });
        await("playing", 8_000, () -> player(service).isPlaying());
        long started = onMain(() -> player(service).getCurrentPosition());
        assertTrue("started at " + started, started >= 8_000);

        onMain(() -> {
            service.select(0, true);
            service.seekTo(8_000);
            service.previous();
            return null;
        });
        await("playing again", 8_000, () -> player(service).isPlaying());
        long restarted = onMain(() -> player(service).getCurrentPosition());
        assertTrue("restarted at " + restarted, restarted < 4_000);
    }

    @Test
    public void aChapterThatCannotLoadNeverPlaysThePreviousOneUnderItsName() throws Exception {
        MediaPlayerService.Request request = book("bad-clip", 20, 20);
        AudioPOJO bad = request.chapters.get(1);
        bad.setTimeStart(5);
        bad.setTimeFinish(2);
        MediaPlayerService service = startAndBind(request, true);
        await("chapter 0 playing past a second", 8_000, () -> service.getChapterIndex() == 0
                && player(service).isPlaying() && player(service).getCurrentPosition() >= 1_000);

        onMain(() -> {
            service.next();
            return null;
        });
        await("the chapter's error is published", 3_000, () -> service.getChapterIndex() == 1
                && service.getSessionPlaybackState().getState() == PlaybackStateCompat.STATE_ERROR);
        onMain(() -> {
            service.play();
            return null;
        });
        Thread.sleep(1_000);

        assertTrue(describe(service), onMain(() -> !player(service).isPlaying()
                && player(service).getMediaItemCount() == 0 && service.getChapterIndex() == 1
                && service.getSessionPlaybackState().getState() == PlaybackStateCompat.STATE_ERROR));
        Checkpoint checkpoint = new StorageUtil(context).loadCheckpoint(request.bookUrl);
        assertNotNull(checkpoint);
        assertEquals(1, checkpoint.chapterIndex);
        assertEquals(0, checkpoint.positionMs);
    }

    @Test
    public void speedChangeWhilePausedStaysPaused() throws Exception {
        MediaPlayerService service = startAndBind(book("speed", 20), false);
        await("ready and paused", 8_000, () -> player(service).getPlaybackState() == Player.STATE_READY);

        context.sendBroadcast(new Intent(BookPresenter.Broadcast_SET_SPEED)
                .putExtra("speed", 1.5f).setPackage(context.getPackageName()));
        await("speed applied", 3_000, () -> player(service).getPlaybackParameters().speed == 1.5f);
        Thread.sleep(500);

        assertFalse(describe(service), onMain(() -> player(service).isPlaying()
                || player(service).getPlayWhenReady() || service.isWantPlaying()));
    }

    /** The activities' hardware keys: PAUSE while paused stays paused, PLAY while playing keeps playing. */
    @Test
    public void hardwarePlayAndPauseKeysNeverToggle() throws Exception {
        MediaPlayerService service = startAndBind(book("keys", 20), false);
        await("ready and paused", 8_000, () -> player(service).getPlaybackState() == Player.STATE_READY);

        Consts.handleUserInput(context, KeyEvent.KEYCODE_MEDIA_PAUSE);
        Thread.sleep(700);
        assertFalse(describe(service), onMain(() -> player(service).isPlaying()
                || player(service).getPlayWhenReady() || service.isWantPlaying()));

        Consts.handleUserInput(context, KeyEvent.KEYCODE_MEDIA_PLAY);
        await("playing", 3_000, () -> player(service).isPlaying());
        Consts.handleUserInput(context, KeyEvent.KEYCODE_MEDIA_PLAY);
        Thread.sleep(700);
        assertTrue(describe(service), onMain(() -> player(service).isPlaying() && service.isWantPlaying()));

        Consts.handleUserInput(context, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
        await("toggled to paused", 3_000, () -> !player(service).getPlayWhenReady() && !service.isWantPlaying());
    }

    /** Binder and session seeks never change the intent; the app's seek bar resumes while the preference is on. */
    @Test
    public void onlyTheAppsSeekAdaptersResumeAfterSeeking() throws Exception {
        MediaPlayerService service = startAndBind(book("seek", 20), false);
        await("ready and paused", 8_000, () -> player(service).getPlaybackState() == Player.STATE_READY);

        onMain(() -> {
            service.seekTo(5_000);
            return null;
        });
        Thread.sleep(500);
        assertFalse(describe(service), onMain(() -> player(service).isPlaying()
                || player(service).getPlayWhenReady() || service.isWantPlaying()));

        context.sendBroadcast(new Intent(BookPresenter.Broadcast_SEEK_TO)
                .putExtra("postion", 8_000).setPackage(context.getPackageName()));
        await("playing after the app's seek", 3_000,
                () -> player(service).isPlaying() && service.isWantPlaying());
        assertTrue(onMain(() -> player(service).getCurrentPosition() >= 8_000));
    }

    /** Spec §8: an end-of-chapter timer armed on one chapter ends the chapter chosen after it. */
    @Test
    public void endOfChapterTimerFollowsTheCurrentChapter() throws Exception {
        MediaPlayerService service = startAndBind(book("end-of-chapter", 20, 3, 3), true);
        await("chapter 0 playing", 8_000, () -> service.getChapterIndex() == 0 && player(service).isPlaying());

        context.sendBroadcast(new Intent(SleepTimerPresenter.Broadcast_SleepTimer).putExtra("start", true)
                .putExtra("endOfChapter", true).setPackage(context.getPackageName()));
        Thread.sleep(500);
        onMain(() -> {
            service.next();
            return null;
        });
        await("chapter 1 playing", 8_000, () -> service.getChapterIndex() == 1 && player(service).isPlaying());
        await("chapter 1 ended into a paused chapter 2", 10_000, () -> service.getChapterIndex() == 2
                && player(service).getPlaybackState() == Player.STATE_READY);
        Thread.sleep(500);

        assertFalse(describe(service), onMain(() -> player(service).isPlaying()
                || player(service).getPlayWhenReady() || service.isWantPlaying()));
    }

    @Test
    public void pauseInEndedStaysPaused() throws Exception {
        MediaPlayerService service = startAndBind(book("ended", 2), true);
        await("ended", 10_000, () -> player(service).getPlaybackState() == Player.STATE_ENDED);

        sendAction(MediaPlayerService.ACTION_PAUSE);
        Thread.sleep(700);

        assertTrue(describe(service), onMain(() -> player(service).getPlaybackState() == Player.STATE_ENDED
                && !player(service).getPlayWhenReady() && !service.isWantPlaying()));
        assertEquals(PlaybackStateCompat.STATE_PAUSED,
                (int) onMain(() -> service.getSessionPlaybackState().getState()));
    }

    /** The presenter binds and unbinds with the screen: doing it while buffering must not touch playback. */
    @Test
    public void rebindingWhileBufferingKeepsPlaying() throws Exception {
        headersDelayMs = 3_000;
        MediaPlayerService service = startAndBind(book("rotate", 20), true);
        await("buffering on a held response", 5_000, () -> server.getRequestCount() >= 1
                && player(service).getPlaybackState() == Player.STATE_BUFFERING);

        serviceRule.unbindService();
        MediaPlayerService again = bind();
        assertSame(service, again);
        await("playing after the held response", 8_000, () -> player(again).isPlaying());
        assertTrue(onMain(again::isWantPlaying));
    }

    @Test
    public void foregroundNotificationStaysAcrossAChapterChange() throws Exception {
        MediaPlayerService service = startAndBind(book("foreground", 20, 20), true);
        await("chapter 0 playing", 8_000, () -> service.getChapterIndex() == 0 && player(service).isPlaying());
        assertTrue(hasForegroundNotification());

        onMain(() -> {
            service.next();
            return null;
        });
        assertTrue("foreground while the next chapter loads", hasForegroundNotification());
        await("chapter 1 playing", 8_000, () -> service.getChapterIndex() == 1 && player(service).isPlaying());
        assertTrue(hasForegroundNotification());
    }

    // -----------------------------------------------------------------------------------------------------

    private MockResponse respond(RecordedRequest request) {
        if (neverAnswer) {
            return new MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE);
        }
        if (failWith != 0) {
            return new MockResponse().setResponseCode(failWith);
        }
        byte[] wav = request.getRequestUrl() != null ? files.get(request.getRequestUrl().encodedPath()) : null;
        if (wav == null) {
            return new MockResponse().setResponseCode(404);
        }
        int start = 0;
        String range = request.getHeader("Range");
        if (range != null && range.startsWith("bytes=")) {
            String from = range.substring(6).split("-", 2)[0].trim();
            if (!from.isEmpty()) {
                start = (int) Math.min(Long.parseLong(from), wav.length);
            }
        }
        MockResponse response = new MockResponse()
                .setHeader("Content-Type", "audio/wav")
                .setHeader("Accept-Ranges", "bytes")
                .setBody(new Buffer().write(wav, start, wav.length - start));
        if (start > 0) {
            response.setResponseCode(206)
                    .setHeader("Content-Range", "bytes " + start + "-" + (wav.length - 1) + "/" + wav.length);
        }
        if (headersDelayMs > 0) {
            response.setHeadersDelay(headersDelayMs, TimeUnit.MILLISECONDS);
        }
        return response;
    }

    /** 8 kHz mono 16-bit PCM silence. */
    private static byte[] wav(int seconds) {
        int rate = 8_000;
        int dataLength = seconds * rate * 2;
        ByteBuffer buffer = ByteBuffer.allocate(44 + dataLength).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put("RIFF".getBytes(StandardCharsets.US_ASCII)).putInt(36 + dataLength)
                .put("WAVE".getBytes(StandardCharsets.US_ASCII))
                .put("fmt ".getBytes(StandardCharsets.US_ASCII)).putInt(16)
                .putShort((short) 1).putShort((short) 1).putInt(rate).putInt(rate * 2)
                .putShort((short) 2).putShort((short) 16)
                .put("data".getBytes(StandardCharsets.US_ASCII)).putInt(dataLength);
        return buffer.array();
    }

    /** A paused-or-playing request for a book whose chapters last {@code seconds} each. */
    private MediaPlayerService.Request book(String name, int... seconds) {
        String bookUrl = "https://test.invalid/" + name + "/" + SystemClock.elapsedRealtimeNanos();
        books.add(bookUrl);
        ArrayList<AudioPOJO> chapters = new ArrayList<>();
        for (int i = 0; i < seconds.length; i++) {
            String path = "/" + name + "/" + i + ".wav";
            files.put(path, wav(seconds[i]));
            AudioPOJO audio = new AudioPOJO();
            audio.setName(name + " " + i);
            audio.setBookName(name);
            audio.setUrl(server.url(path).toString());
            chapters.add(audio);
        }
        BookPOJO book = new BookPOJO();
        book.setUrl(bookUrl);
        book.setName(name);
        return new MediaPlayerService.Request(book, bookUrl, chapters, null, 0, 0, false);
    }

    /**
     * A book the database knows only by its chapter rows (no saved, favorite or history entry), made the last
     * one played, so a cold Play restores it.
     */
    private MediaPlayerService.Request knownBook(String name, int... seconds) {
        MediaPlayerService.Request request = book(name, seconds);
        AudioListDBModel rows = new AudioListDBModel(context);
        for (AudioPOJO audio : request.chapters) {
            AudioListPOJO row = new AudioListPOJO();
            row.setBookUrl(request.bookUrl);
            row.setBookName(audio.getBookName());
            row.setAudioName(audio.getName());
            row.setAudioUrl(audio.getUrl());
            rows.add(row);
        }
        new StorageUtil(context).storeCheckpoint(new Checkpoint(request.bookUrl, 0, 0), true);
        return request;
    }

    private MediaPlayerService startAndBind(MediaPlayerService.Request paused, boolean play) throws Exception {
        return start(paused, 0, play);
    }

    private MediaPlayerService start(MediaPlayerService.Request paused, long positionMs, boolean play)
            throws Exception {
        MediaPlayerService.start(context, new MediaPlayerService.Request(paused.book, paused.bookUrl,
                paused.chapters, null, 0, positionMs, play));
        return bind();
    }

    private MediaPlayerService bind() throws Exception {
        IBinder binder = serviceRule.bindService(new Intent(context, MediaPlayerService.class));
        return ((MediaPlayerService.LocalBinder) binder).getService();
    }

    private void sendAction(String action) {
        context.startService(new Intent(context, MediaPlayerService.class).setAction(action));
    }

    private Notification activeNotification() {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        for (StatusBarNotification notification : manager.getActiveNotifications()) {
            if (notification.getId() == MediaPlayerService.getNotificationId()) {
                return notification.getNotification();
            }
        }
        return null;
    }

    private boolean hasForegroundNotification() {
        Notification notification = activeNotification();
        return notification != null && (notification.flags & Notification.FLAG_FOREGROUND_SERVICE) != 0;
    }

    private SharedPreferences storagePrefs() {
        return context.getSharedPreferences("STORAGE", Context.MODE_PRIVATE);
    }

    /** @return true when the service is still running after 5 s; false once its teardown has completed */
    private static boolean awaitStopped() throws InterruptedException {
        long end = SystemClock.elapsedRealtime() + 5_000;
        while (MediaPlayerService.isRunning() && SystemClock.elapsedRealtime() < end) {
            Thread.sleep(50);
        }
        return MediaPlayerService.isRunning();
    }

    private static ExoPlayer player(MediaPlayerService service) {
        ExoPlayer player = service.getPlayer();
        assertNotNull("player released", player);
        return player;
    }

    private static <T> T onMain(Callable<T> callable) {
        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<Throwable> error = new AtomicReference<>();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            try {
                result.set(callable.call());
            } catch (Throwable t) {
                error.set(t);
            }
        });
        if (error.get() != null) {
            throw new AssertionError(error.get());
        }
        return result.get();
    }

    private void await(String what, long timeoutMs, Callable<Boolean> condition) throws InterruptedException {
        long end = SystemClock.elapsedRealtime() + timeoutMs;
        while (SystemClock.elapsedRealtime() < end) {
            if (Boolean.TRUE.equals(onMain(condition))) {
                return;
            }
            Thread.sleep(50);
        }
        fail("timed out waiting for " + what + "; requests=" + server.getRequestCount());
    }

    private static String describe(MediaPlayerService service) {
        return onMain(() -> {
            ExoPlayer p = service.getPlayer();
            PlaybackStateCompat session = service.getSessionPlaybackState();
            return p == null ? "no player" : "state=" + p.getPlaybackState() + " playWhenReady="
                    + p.getPlayWhenReady() + " playing=" + p.isPlaying() + " error=" + p.getPlayerError()
                    + " chapter=" + service.getChapterIndex() + " intent=" + service.isWantPlaying()
                    + " session=" + (session == null ? null : session.getState());
        });
    }
}
