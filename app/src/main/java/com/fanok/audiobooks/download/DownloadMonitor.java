package com.fanok.audiobooks.download;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.media3.common.C;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.HttpDataSource;
import androidx.media3.exoplayer.offline.Download;
import androidx.media3.exoplayer.offline.DownloadCursor;
import androidx.media3.exoplayer.offline.DownloadManager;
import androidx.media3.exoplayer.offline.DownloadRequest;
import androidx.media3.exoplayer.scheduler.Requirements;

import com.fanok.audiobooks.App;
import com.fanok.audiobooks.NotificationDownload;
import com.fanok.audiobooks.R;
import com.fanok.audiobooks.pojo.BookPOJO;
import com.fanok.audiobooks.util.DownloadStorageHelper;
import com.fanok.audiobooks.util.DownloadUtil;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Live view of the process's one {@link DownloadManager}: the transfers it has in hand, their speed (per-transfer
 * byte deltas over a sliding window), the reason nothing moves (requirements, pause, failure detail from the final
 * exception) and, for the notification, the transfers of every book as the index last had them; once the service
 * stops, it leaves the notification that says why a book rests (paused, waiting, failed). Main thread only,
 * like the manager; the index is read off it. Created with the manager ({@code App.onCreate}), before the service
 * can resume it, so it sees every run from its first event. Screens combine the parts they read with
 * {@link #statusOf} and listen for changes and ~1 s ticks while something downloads.
 */
@OptIn(markerClass = UnstableApi.class)
public final class DownloadMonitor implements DownloadManager.Listener {

    private static final String TAG = "DownloadMonitor";
    private static final long TICK_MS = 1_000;
    /** Finished transfers kept until the screens re-read the index (a few seconds); bounded, requests are large. */
    private static final int ENDED_MAX = 64;
    /** What the resting notification shows, carried in it so that a later process can take it over. */
    private static final String EXTRA_RESTING_KEY = "com.fanok.audiobooks.resting.key";
    private static final String EXTRA_RESTING_STATE = "com.fanok.audiobooks.resting.state";
    private static final String EXTRA_RESTING_SHOWN = "com.fanok.audiobooks.resting.shown";
    private static final String EXTRA_RESTING_OTHERS = "com.fanok.audiobooks.resting.others";
    /** Index scans: off the main thread, and off the download worker that screens and actions queue on. */
    private static final ExecutorService SCANS = Executors.newSingleThreadExecutor();

    public interface Listener {
        /** A download changed state, the requirements changed, or a second passed while something downloads. */
        void onDownloadsChanged();
    }

    /** A book's transfers as the index last had them (refreshed off the main thread on every state change). */
    private static final class Book {
        final String name;
        final List<DownloadStatus.Part> parts;

        Book(String name, List<DownloadStatus.Part> parts) {
            this.name = name;
            this.parts = parts;
        }
    }

    private static DownloadMonitor instance;

    @MainThread
    @NonNull
    public static DownloadMonitor get() {
        if (instance == null) instance = new DownloadMonitor(DownloadUtil.getDownloadManager());
        return instance;
    }

    private final DownloadManager manager;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final DownloadStatus.SpeedMeter meter = new DownloadStatus.SpeedMeter();
    private final Map<String, String> failures = new HashMap<>();
    /** Book key by download id: a request's data (the whole book as JSON) is parsed once, not every second. */
    private final Map<String, String> keyOfId = new HashMap<>();
    /**
     * The last state of transfers that left the manager (completed, failed): the manager no longer has them and a
     * screen's index snapshot may still show them running, until its next read.
     */
    private final Map<String, Download> ended = new LinkedHashMap<String, Download>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Download> eldest) {
            return size() > ENDED_MAX;
        }
    };
    private final List<Listener> listeners = new ArrayList<>();
    private boolean ticking;
    private Map<String, Book> books = Collections.emptyMap();
    private boolean refreshing;
    private boolean refreshAgain;
    /**
     * Books whose transfers changed since the resting notification was last reconciled, the latest last: queued,
     * run, paused, failed or finished (deletions only for the book it shows).
     */
    private final Set<String> touched = new LinkedHashSet<>();
    /** The resting notification's book, state and words; null and NONE while none is up or it is not known. */
    private String restingKey;
    private int restingState = DownloadStatus.NONE;
    private String restingShown;
    /** The other books the resting notification counts ("· ещё N книг"): its pause and resume reach them too. */
    private final Set<String> restingOthers = new LinkedHashSet<>();
    /** The book that ran last since the last reconcile: a global pause names it, not the last one queued. */
    private String runKey;
    /** False only while no resting notification is up (one an earlier process left included). */
    private boolean restingPosted;
    /** Reconcile after the scan in flight: the first one, and the ones the manager going idle asks for. */
    private boolean restingPending = true;

    private DownloadMonitor(@NonNull DownloadManager manager) {
        this.manager = manager;
        manager.addListener(this);
        adoptResting(restingLeft()); // one binder call, before any event can act on it
        sample();
        refreshBooks();
    }

    // ---------------------------------------------------------------- listeners and ticks

    public void addListener(@NonNull Listener listener) {
        if (!listeners.contains(listener)) listeners.add(listener);
        ensureTicking();
    }

    public void removeListener(@NonNull Listener listener) {
        listeners.remove(listener);
    }

    private void notifyListeners() {
        for (Listener l : new ArrayList<>(listeners)) l.onDownloadsChanged();
    }

    private void ensureTicking() {
        if (ticking || listeners.isEmpty() || !hasRunning()) return;
        ticking = true;
        main.postDelayed(this::tick, TICK_MS);
    }

    private void tick() {
        ticking = false;
        sample();
        notifyListeners();
        ensureTicking();
    }

    private boolean hasRunning() {
        for (Download d : manager.getCurrentDownloads()) {
            if (isRunning(d.state)) return true;
        }
        return false;
    }

    private static boolean isRunning(int state) {
        return state == Download.STATE_DOWNLOADING || state == Download.STATE_RESTARTING;
    }

    // ---------------------------------------------------------------- live facts

    /** Accounts the bytes of the running transfers; cheap, so callers may ask often. */
    private void sample() {
        long now = SystemClock.elapsedRealtime();
        if (!meter.sampleDue(now)) return;
        boolean running = false;
        for (Download d : manager.getCurrentDownloads()) {
            if (!isRunning(d.state)) continue;
            running = true;
            meter.account(d.request.id, d.getBytesDownloaded(), now);
        }
        if (!running) {
            meter.reset();
        }
    }

    @NonNull
    public DownloadStatus.Live live() {
        sample();
        long now = SystemClock.elapsedRealtime();
        int notMet = manager.getNotMetRequirements();
        boolean networkMissing = (notMet & (Requirements.NETWORK | Requirements.NETWORK_UNMETERED)) != 0;
        return new DownloadStatus.Live(networkMissing, manager.getDownloadsPaused(), meter.bytesPerSecond(now),
                meter.msSinceLastByte(now));
    }

    /** The manager's current (not completed, not failed) transfers by id. */
    @NonNull
    private Map<String, Download> currentById() {
        List<Download> current = manager.getCurrentDownloads();
        Map<String, Download> byId = new HashMap<>(current.size() * 2);
        for (Download d : current) byId.put(d.request.id, d);
        return byId;
    }

    /** The transfer as the manager has it now, else null (completed, failed or unknown). */
    @Nullable
    public Download live(@NonNull String id) {
        return currentById().get(id);
    }

    /** A transfer as a status part; the failure reason is the one this monitor saw, if any. */
    @NonNull
    public static DownloadStatus.Part partOf(@NonNull Download d) {
        return new DownloadStatus.Part(d.request.id, d.state, d.getBytesDownloaded(),
                d.contentLength == C.LENGTH_UNSET ? DownloadStatus.UNKNOWN : d.contentLength,
                d.getPercentDownloaded() == C.PERCENTAGE_UNSET ? DownloadStatus.UNKNOWN : d.getPercentDownloaded(),
                d.stopReason, null);
    }

    /**
     * The status of a book from the parts a screen read from the index (any state but removing), replacing the ones
     * the manager still has in hand by their live version, with the chapter counts the screen knows.
     */
    @NonNull
    public DownloadStatus statusOf(@NonNull List<DownloadStatus.Part> parts, int chaptersDone, int chaptersSelected,
                                   int chaptersTotal) {
        return DownloadStatus.compute(refresh(parts, currentById()), chaptersDone, chaptersSelected, chaptersTotal,
                live());
    }

    /** The parts as the manager has them now, else as they ended in this process, else as given. */
    @NonNull
    private List<DownloadStatus.Part> refresh(@NonNull List<DownloadStatus.Part> parts,
                                              @NonNull Map<String, Download> current) {
        List<DownloadStatus.Part> fresh = new ArrayList<>(parts.size());
        for (DownloadStatus.Part p : parts) {
            Download d = current.get(p.id);
            if (d == null) d = ended.get(p.id);
            DownloadStatus.Part f = d != null ? partOf(d) : p;
            if (f.state == Download.STATE_FAILED && f.failure == null && failures.containsKey(p.id)) {
                f = f.withFailure(failures.get(p.id));
            }
            fresh.add(f);
        }
        return fresh;
    }

    /**
     * The live version of a chapter's download: the manager's, else how it ended in this process (a screen's
     * snapshot still says downloading), else the given one.
     */
    @NonNull
    public Download refresh(@NonNull Download download) {
        Download live = live(download.request.id);
        if (live != null) return live;
        Download last = ended.get(download.request.id);
        return last != null ? last : download;
    }

    /** Percent of one transfer: the downloader's, else from its length; -1 when unknown. */
    public static int percentOf(@NonNull Download d) {
        if (d.getPercentDownloaded() != C.PERCENTAGE_UNSET) {
            return (int) Math.max(0, Math.min(100, d.getPercentDownloaded()));
        }
        if (d.contentLength > 0) {
            return (int) Math.max(0, Math.min(100, 100 * d.getBytesDownloaded() / d.contentLength));
        }
        return -1;
    }

    // ---------------------------------------------------------------- books (for the notification)

    /** The {@link DownloadIds#bookKey} of a transfer's book, "" when unknown; {@link #keyOf}, once per download id. */
    @NonNull
    public String bookKeyOf(@NonNull Download d) {
        String key = keyOfId.get(d.request.id);
        if (key == null) {
            key = keyOf(d.request);
            keyOfId.put(d.request.id, key);
        }
        return key;
    }

    /**
     * The book key of a request, "" when unknown: read from a v1 id, else (legacy ids) from the book its data
     * carries. Any thread; the notification's actions name a book by it.
     */
    @NonNull
    public static String keyOf(@NonNull DownloadRequest request) {
        String key = DownloadIds.bookKeyOfId(request.id);
        return key != null ? key : DownloadIds.bookKey(DownloadUtil.bookUrlOf(request));
    }

    /**
     * The status of the book {@code key} ({@link #bookKeyOf}) at transfer granularity, from the index as last read
     * plus the live transfers; for the notification, which has no chapter rows at hand. A transfer is a chapter
     * unless the book has an HLS stream (one transfer for many chapters): then only bytes and percent are known.
     */
    @NonNull
    public DownloadStatus statusOfBook(@NonNull String key) {
        Book book = books.get(key);
        Map<String, Download> current = currentById();
        Map<String, DownloadStatus.Part> parts = new LinkedHashMap<>();
        if (book != null) {
            for (DownloadStatus.Part p : refresh(book.parts, current)) parts.put(p.id, p);
        }
        // transfers added since the last index read
        if (!key.isEmpty()) {
            for (Download d : current.values()) {
                if (!parts.containsKey(d.request.id) && key.equals(bookKeyOf(d))) parts.put(d.request.id, partOf(d));
            }
        }
        int done = 0;
        boolean streams = false;
        for (DownloadStatus.Part p : parts.values()) {
            if (p.state == Download.STATE_COMPLETED) done++;
            if (DownloadIds.isStreamId(p.id)) streams = true;
        }
        int chapters = streams ? 0 : parts.size();
        return DownloadStatus.compute(new ArrayList<>(parts.values()), done, chapters, chapters, live());
    }

    /** The name of the book {@code key} as its downloads carry it, or null. */
    @Nullable
    public String bookName(@NonNull String key) {
        Book book = books.get(key);
        return book != null ? book.name : null;
    }

    private void refreshBooks() {
        if (refreshing) {
            refreshAgain = true;
            return;
        }
        refreshing = true;
        // read on the scan thread; both maps are only replaced, never changed, on the main thread
        final Map<String, String> known = new HashMap<>(keyOfId);
        final Map<String, Book> previous = books;
        SCANS.execute(() -> {
            Map<String, Book> read = new HashMap<>();
            Map<String, String> keys = new HashMap<>();
            try (DownloadCursor cursor = manager.getDownloadIndex().getDownloads()) {
                Map<String, List<DownloadStatus.Part>> parts = new HashMap<>();
                Map<String, String> names = new HashMap<>();
                while (cursor.moveToNext()) {
                    Download d = cursor.getDownload();
                    if (d.state == Download.STATE_REMOVING) continue;
                    String key = known.get(d.request.id);
                    if (key == null) {
                        key = keyOf(d.request); // no parsing for v1 ids; a request is parsed once per book, for its name
                        keys.put(d.request.id, key);
                    }
                    if (key.isEmpty()) continue;
                    List<DownloadStatus.Part> list = parts.get(key);
                    if (list == null) {
                        list = new ArrayList<>();
                        parts.put(key, list);
                        Book before = previous.get(key);
                        String name = before != null ? before.name : null;
                        if (name == null) {
                            BookPOJO book = DownloadUtil.bookOf(d.request);
                            name = book != null ? book.getName() : null;
                        }
                        names.put(key, name);
                    }
                    list.add(partOf(d));
                }
                for (Map.Entry<String, List<DownloadStatus.Part>> e : parts.entrySet()) {
                    read.put(e.getKey(), new Book(names.get(e.getKey()), Collections.unmodifiableList(e.getValue())));
                }
            } catch (IOException | RuntimeException e) { // a scan that throws would leave refreshing set for good
                Log.w(TAG, "Download index unreadable", e);
            }
            final Map<String, Book> result = read;
            main.post(() -> {
                books = result;
                keyOfId.putAll(keys);
                refreshing = false;
                if (refreshAgain) {
                    refreshAgain = false;
                    refreshBooks();
                } else if (restingPending) {
                    restingPending = false;
                    postResting();
                }
            });
        });
    }

    // ---------------------------------------------------------------- the notification left when the service stops

    /**
     * Reconciles the dismissible notification left once the service stopped (with its own) with the index just
     * read: it says why a book rests (paused, waiting for the network, or failed with the reason), else none is up.
     * The book is the one touched since the last reconcile (the one that ran first, then the latest), a failure
     * before a pause; only when none of them rests, the one shown, then the others it counted. A failure shown stays
     * until its own book is retried, paused or deleted, or another touched book rests. Unchanged words are not
     * posted again, so a notification the user swiped away stays away.
     */
    private void postResting() {
        App app = App.getInstance();
        if (app == null || hasRunning()) return;
        List<String> candidates = new ArrayList<>(touched);
        Collections.reverse(candidates);
        if (runKey != null && candidates.remove(runKey)) candidates.add(0, runKey);
        runKey = null;
        touched.clear();
        int fresh = candidates.size(); // the shown book and its others only when no touched book rests
        if (restingKey != null) candidates.add(restingKey);
        candidates.addAll(restingOthers);
        String key = null;
        DownloadStatus status = null;
        for (int i = 0; i < candidates.size(); i++) {
            if (i == fresh && status != null) break;
            String k = candidates.get(i);
            if (k.isEmpty()) continue;
            DownloadStatus s = statusOfBook(k);
            if (s.state == DownloadStatus.FAILED || (status == null
                    && (s.state == DownloadStatus.PAUSED || s.state == DownloadStatus.WAITING_NETWORK))) {
                key = k;
                status = s;
                if (s.state == DownloadStatus.FAILED) break;
            }
        }
        if (status == null) {
            restingOthers.clear();
            cancelResting();
            return;
        }
        if (status.state == DownloadStatus.FAILED && status.failure == null && restingState == DownloadStatus.FAILED
                && key.equals(restingKey)) {
            return; // the reason died with the process that showed it: its words stay
        }
        // pause and resume act on every book: the others they reach are named in the text and the buttons
        Set<String> counted = new LinkedHashSet<>();
        int others = status.state == DownloadStatus.FAILED ? 0 : otherBooksWaiting(key, counted);
        String shown = key + "|" + status.state + "|" + others + "|" + DownloadStatusText.line(app, status);
        if (shown.equals(restingShown)) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && ContextCompat.checkSelfPermission(app,
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        Notification n = NotificationDownload.resting(app, key, bookName(key), status, others);
        n.extras.putString(EXTRA_RESTING_KEY, key);
        n.extras.putInt(EXTRA_RESTING_STATE, status.state);
        n.extras.putString(EXTRA_RESTING_SHOWN, shown);
        n.extras.putStringArrayList(EXTRA_RESTING_OTHERS, new ArrayList<>(counted));
        NotificationManagerCompat.from(app).notify(NotificationDownload.RESTING_ID, n);
        restingOthers.clear();
        restingOthers.addAll(counted);
        restingKey = key;
        restingState = status.state;
        restingShown = shown;
        restingPosted = true;
    }

    private void cancelResting() {
        if (!restingPosted) return;
        restingPosted = false;
        restingKey = null;
        restingState = DownloadStatus.NONE;
        restingShown = null;
        App app = App.getInstance();
        if (app != null) NotificationManagerCompat.from(app).cancel(NotificationDownload.RESTING_ID);
    }

    /**
     * Books but {@code key} with transfers queued or paused, added to {@code into}: the global pause and resume reach
     * them too.
     */
    private int otherBooksWaiting(@NonNull String key, @NonNull Set<String> into) {
        int n = 0;
        for (Map.Entry<String, Book> e : books.entrySet()) {
            if (e.getKey().equals(key)) continue;
            for (DownloadStatus.Part p : e.getValue().parts) {
                if (p.state == Download.STATE_QUEUED || p.state == Download.STATE_STOPPED) {
                    n++;
                    into.add(e.getKey());
                    break;
                }
            }
        }
        return n;
    }

    /** The resting notification up now, as an earlier process left it; null when none is. */
    @Nullable
    private static Notification restingLeft() {
        App app = App.getInstance();
        NotificationManager nm = app != null ? app.getSystemService(NotificationManager.class) : null;
        if (nm == null) return null;
        try {
            for (StatusBarNotification n : nm.getActiveNotifications()) {
                if (n.getId() == NotificationDownload.RESTING_ID) return n.getNotification();
            }
        } catch (RuntimeException e) {
            Log.w(TAG, "Active notifications unreadable", e);
        }
        return null;
    }

    /**
     * Takes over what the resting notification of an earlier process shows; the first reconcile keeps, updates or
     * cancels it. One without its book (an older version's) is reconciled as showing none: it goes.
     */
    private void adoptResting(@Nullable Notification left) {
        restingPosted = left != null;
        Bundle extras = left != null ? left.extras : null;
        if (extras == null) return;
        restingKey = extras.getString(EXTRA_RESTING_KEY);
        restingState = extras.getInt(EXTRA_RESTING_STATE, DownloadStatus.NONE);
        restingShown = extras.getString(EXTRA_RESTING_SHOWN);
        ArrayList<String> others = extras.getStringArrayList(EXTRA_RESTING_OTHERS);
        if (others != null) restingOthers.addAll(others);
    }

    private void touch(@NonNull String key) {
        if (key.isEmpty()) return;
        touched.remove(key);
        touched.add(key);
    }

    // ---------------------------------------------------------------- DownloadManager.Listener

    @Override
    public void onDownloadChanged(@NonNull DownloadManager downloadManager, @NonNull Download download,
                                  @Nullable Exception finalException) {
        long now = SystemClock.elapsedRealtime();
        String id = download.request.id;
        String key = bookKeyOf(download);
        boolean running = isRunning(download.state);
        if (running) {
            runKey = key;
            meter.account(id, download.getBytesDownloaded(), now);
            ended.remove(id);
        } else if (download.state == Download.STATE_COMPLETED) {
            meter.account(id, download.getBytesDownloaded(), now); // the last delta counts
            meter.forget(id);
            failures.remove(id);
            ended.put(id, download);
        } else if (download.state == Download.STATE_FAILED) {
            meter.forget(id);
            if (finalException != null) failures.put(id, describe(finalException)); // not a stop reason set on it
            ended.put(id, download);
        } else {
            ended.remove(id);
            if (download.state == Download.STATE_QUEUED) failures.remove(id); // re-added after a failure
        }
        // the resting notification goes at once when its own book moves on (retried, resumed, paused, deleted), and
        // for any running transfer, which the service shows, unless it tells another book's failure; the next
        // reconcile puts up what is true then
        boolean own = key.equals(restingKey);
        // a download removed once its chapters are exported: the book did not move on, its notice stays
        boolean released = download.state == Download.STATE_REMOVING && DownloadStorageHelper.isReleased(id);
        if (!released && (own ? download.state != Download.STATE_FAILED
                : running && restingState != DownloadStatus.FAILED)) {
            cancelResting();
        }
        if (!released && (download.state != Download.STATE_REMOVING || own)) touch(key);
        refreshBooks();
        notifyListeners();
        ensureTicking();
    }

    @Override
    public void onDownloadRemoved(@NonNull DownloadManager downloadManager, @NonNull Download download) {
        DownloadStorageHelper.forgetReleased(download.request.id);
        meter.forget(download.request.id);
        failures.remove(download.request.id);
        keyOfId.remove(download.request.id);
        ended.remove(download.request.id);
        refreshBooks();
        notifyListeners();
    }

    @Override
    public void onDownloadsPausedChanged(@NonNull DownloadManager downloadManager, boolean downloadsPaused) {
        notifyListeners();
    }

    @Override
    public void onRequirementsStateChanged(@NonNull DownloadManager downloadManager,
                                           @NonNull Requirements requirements, int notMetRequirements) {
        notifyListeners();
    }

    @Override
    public void onIdle(@NonNull DownloadManager downloadManager) {
        if (!touched.isEmpty() || restingPosted) {
            // the resting notification follows a fresh index read: failed transfers are no longer in hand
            restingPending = true;
            refreshBooks();
        }
        notifyListeners();
    }

    /** A short reason for the user from the exception that ended a download; null when there is none. */
    @Nullable
    static String describe(@Nullable Throwable e) {
        App app = App.getInstance();
        if (e == null || app == null) return null;
        Throwable t = e;
        for (int depth = 0; t != null && depth < 8; t = t.getCause(), depth++) {
            String message = t.getMessage();
            if (t instanceof HttpDataSource.InvalidResponseCodeException) {
                return app.getString(R.string.download_failure_http,
                        ((HttpDataSource.InvalidResponseCodeException) t).responseCode);
            }
            if (message != null && (message.contains("ENOSPC") || message.contains("No space left"))) {
                return app.getString(R.string.download_failure_no_space);
            }
            if (t instanceof UnknownHostException || t instanceof ConnectException
                    || t instanceof SocketTimeoutException || t instanceof SocketException
                    || t instanceof javax.net.ssl.SSLException) {
                return app.getString(R.string.download_failure_network);
            }
        }
        Throwable root = e;
        while (root.getCause() != null && root.getCause() != root) root = root.getCause();
        return root.getClass().getSimpleName();
    }
}
