package com.fanok.audiobooks.net;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.fanok.audiobooks.App;
import com.fanok.audiobooks.Consts;
import com.fanok.audiobooks.Url;
import com.fanok.audiobooks.search.Provider;
import com.fanok.audiobooks.search.Sources;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Proxy;
import java.net.SocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CompletionService;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

import okhttp3.ConnectionPool;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * The "Автоматический подбор" proxy mode: downloads public proxy lists ({@link ProxySources}), checks candidates
 * against the selected catalog site and keeps the fastest working ones. {@link AppProxy} routes traffic through
 * the proxy in use and its spares ({@link #proxiesForRequest()}) and reports a suspect proxy, which moves to the next one when a probe confirms the failure; an exhausted list starts a new
 * search. Automatic searches run at most once per {@link #AUTO_SCAN_INTERVAL_MS} and never without a network; the
 * user's "Найти заново" ({@link #rescan()}) always may. Listeners hear every state change on the main thread.
 */
public final class ProxyFinder {

    public enum Phase { IDLE, SCANNING, READY, FAILED, NO_NETWORK }

    /** Called on the main thread. */
    public interface Listener {
        void onProxyState(@NonNull State state);
    }

    /** A consistent snapshot of the finder. */
    public static final class State {
        @NonNull
        public final Phase phase;
        /** Candidates checked so far in the running (or last) search, out of {@link #total}. */
        public final int tested;
        public final int total;
        /** Working proxies found by the running (or last) search. */
        public final int found;
        /** The proxy in use, or null. */
        @Nullable
        public final ProxyCandidate current;
        /** Verified proxies after the one in use, not failed yet. */
        public final int spares;
        /** When the last search finished (wall clock), or 0. */
        public final long lastScanAt;

        State(@NonNull Phase phase, int tested, int total, int found, @Nullable ProxyCandidate current, int spares,
                long lastScanAt) {
            this.phase = phase;
            this.tested = tested;
            this.total = total;
            this.found = found;
            this.current = current;
            this.spares = spares;
            this.lastScanAt = lastScanAt;
        }
    }

    /** The finder's network: list downloads and proxy checks. Replaced in tests. */
    interface Network {
        /** The text of a list (at most {@link #MAX_LIST_BYTES}), fetched directly, never through a proxy. */
        @NonNull
        String fetch(@NonNull ProxySources.Source source) throws IOException;

        /**
         * Opens {@code target} through the candidate; returns the milliseconds it took, or throws when it fails or the
         * site's answer is not {@link ProxyFinder#usable} (a 403 geo-block or challenge, a 429, a 5xx).
         */
        long probe(@NonNull ProxyCandidate candidate, @NonNull String target, int timeoutMs) throws IOException;
    }

    static final String PREF_LIST = "pref_proxy_auto_list";
    static final int KEEP = 8;
    static final int ENOUGH = 5;
    /** Verified proxies offered after the one in use, so a client fails over within the same call. */
    static final int SPARES_IN_ROUTE = 2;
    static final int MAX_CANDIDATES = 400;
    static final int PROBE_THREADS = 24;
    static final int PROBE_TIMEOUT_MS = 5_000;
    static final int RECHECK_TIMEOUT_MS = 10_000;
    static final int LIST_TIMEOUT_MS = 15_000;
    static final long SCAN_DEADLINE_MS = 60_000;
    /** The list downloads get this much of the search; the checks get the rest. */
    static final long FETCH_DEADLINE_MS = 25_000;
    static final long AUTO_SCAN_INTERVAL_MS = 10 * 60_000;
    static final long NO_NETWORK_RETRY_MS = 30_000;
    /** After a confirmation probe passed, reports of the same proxy are not checked again for this long. */
    static final long CONFIRM_QUIET_MS = 15_000;
    /** A failed recheck is repeated once after this pause (Wi-Fi still associating, a captive portal) before moving on. */
    static final long RETRY_DELAY_MS = 5_000;
    static final int MAX_LIST_BYTES = 256 * 1024;
    static final String FALLBACK_TARGET = "https://akniga.org/";

    private static volatile ProxyFinder instance;

    private final SharedPreferences prefs;
    private final Network network;
    private final Executor main;
    private final Executor worker;
    private final BooleanSupplier online;
    private final Supplier<String> target;
    private final LongSupplier clock;
    private final Random random;
    private final List<ProxySources.Source> sources;
    private final int probeThreads;
    /** Told when the proxy in use changes (pooled connections through the old route are then dropped). */
    private final Runnable switched;
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private final AtomicBoolean scanning = new AtomicBoolean();
    /** A confirmation probe of the proxy in use is queued or running. */
    private final AtomicBoolean confirming = new AtomicBoolean();
    /** Replaced in tests. */
    volatile long retryDelayMs = RETRY_DELAY_MS;

    // guarded by this
    private List<ProxyCandidate> verified = Collections.emptyList();
    private int index;
    private long lastScanAt;
    private Phase phase = Phase.IDLE;
    private int tested;
    private int total;
    private int found;
    /** The proxy whose last confirmation probe passed, and when: see {@link #CONFIRM_QUIET_MS}. */
    private ProxyCandidate confirmed;
    private long confirmedAt;

    /** Derived from verified/index under the lock; read without it by the selector. */
    private volatile ProxyCandidate current;
    private volatile Proxy currentProxy;
    /** The proxy in use and up to {@link #SPARES_IN_ROUTE} spares; empty without a verified proxy. */
    private volatile List<Proxy> route = Collections.emptyList();
    private volatile long nextAutoScanAt;

    ProxyFinder(@NonNull SharedPreferences prefs, @NonNull Network network, @NonNull Executor main,
            @NonNull Executor worker, @NonNull BooleanSupplier online, @NonNull Supplier<String> target,
            @NonNull LongSupplier clock, @NonNull Random random, @NonNull List<ProxySources.Source> sources,
            int probeThreads, @NonNull Runnable switched) {
        this.prefs = prefs;
        this.network = network;
        this.main = main;
        this.worker = worker;
        this.online = online;
        this.target = target;
        this.clock = clock;
        this.random = random;
        this.sources = sources;
        this.probeThreads = probeThreads;
        this.switched = switched;
        Saved saved = Saved.fromJson(readSaved(prefs));
        synchronized (this) {
            verified = saved.proxies;
            index = saved.index;
            lastScanAt = saved.scannedAt;
            nextAutoScanAt = saved.scannedAt > 0 ? saved.scannedAt + AUTO_SCAN_INTERVAL_MS : 0;
            updateCurrent();
            phase = current != null ? Phase.READY : Phase.IDLE;
        }
    }

    /** The process-wide finder, created on first use. */
    @NonNull
    public static ProxyFinder get(@NonNull Context context) {
        ProxyFinder finder = instance;
        if (finder == null) {
            synchronized (ProxyFinder.class) {
                finder = instance;
                if (finder == null) {
                    Context app = context.getApplicationContext() != null ? context.getApplicationContext() : context;
                    SharedPreferences prefs = app.getSharedPreferences(app.getPackageName() + "_preferences",
                            Context.MODE_PRIVATE);
                    Handler handler = new Handler(Looper.getMainLooper());
                    finder = new ProxyFinder(prefs, new OkNetwork(), handler::post,
                            Executors.newSingleThreadExecutor(daemon("proxy-finder")),
                            () -> !Consts.isOffline(app), () -> targetOf(prefs), System::currentTimeMillis,
                            new Random(), ProxySources.ALL, PROBE_THREADS, App::evictConnections);
                    instance = finder;
                }
            }
        }
        return finder;
    }

    /** The finder if it exists yet; the selector never creates it. */
    @Nullable
    static ProxyFinder peek() {
        return instance;
    }

    public void addListener(@NonNull Listener listener) {
        listeners.add(listener);
    }

    public void removeListener(@NonNull Listener listener) {
        listeners.remove(listener);
    }

    @NonNull
    public synchronized State state() {
        int spares = current != null ? verified.size() - index - 1 : 0;
        return new State(phase, tested, total, found, current, spares, lastScanAt);
    }

    /** The proxy in use, or null when none is verified. */
    @Nullable
    public Proxy currentProxy() {
        return currentProxy;
    }

    /**
     * For the selector: the proxy in use, then up to {@link #SPARES_IN_ROUTE} verified spares, so a client tries
     * them in order within one call. Empty without a verified proxy; a search then starts when one is due.
     */
    @NonNull
    List<Proxy> proxiesForRequest() {
        List<Proxy> proxies = route;
        if (proxies.isEmpty()) {
            scanIfDue();
        }
        return proxies;
    }

    /** The automatic mode was chosen: search unless a proxy is known (an automatic search, so rate-limited). */
    public void ensureProxy() {
        if (currentProxy == null) {
            scanIfDue();
        }
    }

    /** "Найти заново": a new search now, whenever the last one was, unless one is running. */
    public void rescan() {
        startScan(true);
    }

    /** An automatic search, when the last one is old enough. */
    void scanIfDue() {
        if (clock.getAsLong() >= nextAutoScanAt) {
            startScan(false);
        }
    }

    /**
     * App start in the automatic mode, or another catalog site chosen: one request through the proxy in use to the
     * selected site (repeated once after {@link #RETRY_DELAY_MS}); a failure moves on (or searches).
     */
    public void recheckCurrent() {
        confirming.set(true);
        if (!execute(this::confirmCurrent)) {
            confirming.set(false);
        }
    }

    /**
     * A client reported a failure through {@code address}, see {@link #reportFailure(SocketAddress, boolean)}.
     */
    public void reportFailure(@Nullable SocketAddress address) {
        reportFailure(address, false);
    }

    /**
     * A client reported a failure through {@code address}. Such reports are hints only: OkHttp also reports a cancelled
     * call (a skipped chapter) or any error while sending the request, so the proxy in use is checked by one probe
     * ({@link #recheckCurrent()}) and moves on only when that fails. Only the proxy in use counts (a spare, or a
     * proxy already left behind, does not), offline reports are not the proxy's, and at most one probe is queued at a
     * time. A proxy whose probe passed within {@link #CONFIRM_QUIET_MS} is not probed again unless the failure is
     * {@code definite} (a refused tunnel or handshake of a call that was not cancelled).
     */
    public void reportFailure(@Nullable SocketAddress address, boolean definite) {
        ProxyCandidate candidate = current;
        if (candidate != null && candidate.hasAddress(address)) {
            suspect(candidate, definite);
        }
    }

    /** The site refused a request (403, 429) through the proxy in use: confirmed by one probe like a failure. */
    public void reportSuspect() {
        ProxyCandidate candidate = current;
        if (candidate != null) {
            suspect(candidate, false);
        }
    }

    private void suspect(@NonNull ProxyCandidate candidate, boolean definite) {
        if (!online.getAsBoolean()) {
            return;
        }
        synchronized (this) {
            if (!definite && candidate.equals(confirmed) && clock.getAsLong() - confirmedAt < CONFIRM_QUIET_MS) {
                return;
            }
        }
        if (confirming.compareAndSet(false, true) && !execute(this::confirmCurrent)) {
            confirming.set(false);
        }
    }

    /** On the worker: probes the proxy in use; a failure, repeated once after a pause while online, moves on. */
    private void confirmCurrent() {
        try {
            ProxyCandidate candidate = current;
            if (candidate == null) {
                scanIfDue();
                return;
            }
            String url = target.get();
            for (int attempt = 0; attempt < 2; attempt++) {
                if (!online.getAsBoolean() || current != candidate || (attempt > 0 && !pause(retryDelayMs))) {
                    return;
                }
                try {
                    long ms = network.probe(candidate, url, RECHECK_TIMEOUT_MS);
                    refresh(candidate, ms);
                    synchronized (this) {
                        confirmed = candidate;
                        confirmedAt = clock.getAsLong();
                    }
                    return;
                } catch (IOException | RuntimeException e) {
                    // repeated once, then the proxy is left
                }
            }
            if (online.getAsBoolean()) {
                advancePast(candidate);
            }
        } finally {
            confirming.set(false);
        }
    }

    private static boolean pause(long ms) {
        if (ms <= 0) {
            return true;
        }
        try {
            Thread.sleep(ms);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private void advancePast(@NonNull ProxyCandidate failed) {
        boolean exhausted;
        synchronized (this) {
            if (current != failed) {
                return;
            }
            index++;
            updateCurrent();
            save();
            exhausted = current == null;
            if (phase != Phase.SCANNING) {
                phase = exhausted ? Phase.FAILED : Phase.READY;
            }
        }
        publish();
        if (exhausted) {
            scanIfDue();
        }
    }

    private synchronized void refresh(@NonNull ProxyCandidate candidate, long ms) {
        int at = verified.indexOf(candidate);
        if (at < 0) {
            return;
        }
        List<ProxyCandidate> list = new ArrayList<>(verified);
        list.set(at, candidate.checked(ms, clock.getAsLong()));
        verified = Collections.unmodifiableList(list);
        if (current == candidate) {
            updateCurrent();
        }
        save();
    }

    private void startScan(boolean manual) {
        if (!scanning.compareAndSet(false, true)) {
            return;
        }
        synchronized (this) {
            long now = clock.getAsLong();
            if (!manual && now < nextAutoScanAt) {
                scanning.set(false);
                return;
            }
            nextAutoScanAt = now + AUTO_SCAN_INTERVAL_MS;
            phase = Phase.SCANNING;
            tested = 0;
            total = 0;
            found = 0;
        }
        publish();
        if (!execute(this::runScan)) {
            synchronized (this) {
                phase = current != null ? Phase.READY : Phase.FAILED;
            }
            scanning.set(false);
            publish();
        }
    }

    private boolean execute(@NonNull Runnable task) {
        try {
            worker.execute(task);
            return true;
        } catch (RejectedExecutionException e) {
            return false;
        }
    }

    private void runScan() {
        try {
            if (!online.getAsBoolean()) {
                synchronized (this) {
                    phase = Phase.NO_NETWORK;
                    nextAutoScanAt = clock.getAsLong() + NO_NETWORK_RETRY_MS;
                }
                return;
            }
            List<ProxyCandidate> working = scan();
            if (working.isEmpty() && !online.getAsBoolean()) {
                // the network went away during the search: nothing was disproved, the known list stays
                synchronized (this) {
                    phase = Phase.NO_NETWORK;
                    nextAutoScanAt = clock.getAsLong() + NO_NETWORK_RETRY_MS;
                }
                return;
            }
            adopt(best(working, KEEP));
        } catch (RuntimeException e) {
            synchronized (this) {
                phase = current != null ? Phase.READY : Phase.FAILED;
            }
        } finally {
            scanning.set(false);
            publish();
        }
    }

    /** Downloads the lists, then checks candidates until {@link #ENOUGH} work or the deadline passes. */
    @NonNull
    private List<ProxyCandidate> scan() {
        long start = System.nanoTime();
        long deadline = start + TimeUnit.MILLISECONDS.toNanos(SCAN_DEADLINE_MS);
        String url = target.get();
        ExecutorService pool = Executors.newFixedThreadPool(probeThreads, daemon("proxy-probe"));
        try {
            List<List<ProxyCandidate>> lists = fetchAll(pool,
                    Math.min(deadline, start + TimeUnit.MILLISECONDS.toNanos(FETCH_DEADLINE_MS)));
            List<ProxyCandidate> previous;
            synchronized (this) {
                previous = new ArrayList<>(verified.subList(Math.min(index, verified.size()), verified.size()));
            }
            List<ProxyCandidate> candidates = ProxySources.interleave(previous, lists, MAX_CANDIDATES, random);
            progress(0, candidates.size(), 0);
            return probeAll(pool, candidates, url, deadline);
        } finally {
            pool.shutdownNow();
        }
    }

    @NonNull
    private List<List<ProxyCandidate>> fetchAll(@NonNull ExecutorService pool, long deadline) {
        List<Future<List<ProxyCandidate>>> futures = new ArrayList<>();
        for (ProxySources.Source source : sources) {
            futures.add(pool.submit(() -> ProxySources.parse(source, network.fetch(source))));
        }
        List<List<ProxyCandidate>> lists = new ArrayList<>();
        for (Future<List<ProxyCandidate>> future : futures) {
            try {
                lists.add(future.get(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS));
            } catch (ExecutionException | TimeoutException e) {
                future.cancel(true);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return lists;
    }

    @NonNull
    private List<ProxyCandidate> probeAll(@NonNull ExecutorService pool, @NonNull List<ProxyCandidate> candidates,
            @NonNull String url, long deadline) {
        CompletionService<ProxyCandidate> checks = new ExecutorCompletionService<>(pool);
        AtomicBoolean stop = new AtomicBoolean();
        for (ProxyCandidate candidate : candidates) {
            checks.submit(() -> {
                if (stop.get() || System.nanoTime() > deadline) {
                    return null;
                }
                long ms = network.probe(candidate, url, PROBE_TIMEOUT_MS);
                return candidate.checked(Math.max(ms, 0), clock.getAsLong());
            });
        }
        List<ProxyCandidate> working = new ArrayList<>();
        int done = 0;
        try {
            while (done < candidates.size() && working.size() < ENOUGH) {
                long wait = deadline - System.nanoTime();
                Future<ProxyCandidate> result = wait > 0 ? checks.poll(wait, TimeUnit.NANOSECONDS) : null;
                if (result == null) {
                    break;
                }
                done++;
                try {
                    ProxyCandidate ok = result.get();
                    if (ok != null) {
                        working.add(ok);
                    }
                } catch (ExecutionException e) {
                    // the proxy failed
                }
                progress(done, candidates.size(), working.size());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            stop.set(true);
        }
        return working;
    }

    private void progress(int tested, int total, int found) {
        synchronized (this) {
            this.tested = tested;
            this.total = total;
            this.found = found;
        }
        publish();
    }

    private synchronized void adopt(@NonNull List<ProxyCandidate> best) {
        verified = Collections.unmodifiableList(new ArrayList<>(best));
        index = 0;
        lastScanAt = clock.getAsLong();
        updateCurrent();
        phase = current != null ? Phase.READY : Phase.FAILED;
        save();
    }

    /** A check passes on 200-399 only: a page, a partial page or a redirect of the site itself. */
    static boolean usable(int code) {
        return code >= 200 && code < 400;
    }

    /** The {@code keep} fastest of {@code working}, fastest first. */
    @NonNull
    static List<ProxyCandidate> best(@NonNull List<ProxyCandidate> working, int keep) {
        List<ProxyCandidate> sorted = new ArrayList<>(working);
        Collections.sort(sorted, Comparator.comparingLong(ProxyCandidate::latencyMs));
        return new ArrayList<>(sorted.subList(0, Math.min(keep, sorted.size())));
    }

    // guarded by this
    private void updateCurrent() {
        ProxyCandidate before = current;
        ProxyCandidate candidate = index >= 0 && index < verified.size() ? verified.get(index) : null;
        List<Proxy> proxies = new ArrayList<>();
        if (candidate != null) {
            for (int i = index; i < verified.size() && i <= index + SPARES_IN_ROUTE; i++) {
                proxies.add(verified.get(i).toProxy());
            }
        }
        current = candidate;
        currentProxy = candidate != null ? proxies.get(0) : null;
        route = Collections.unmodifiableList(proxies);
        if (before == null ? candidate != null : !before.equals(candidate)) {
            try {
                switched.run();
            } catch (RuntimeException e) {
                // dropping pooled connections is best effort
            }
        }
    }

    // guarded by this
    private void save() {
        prefs.edit().putString(PREF_LIST, Saved.toJson(verified, index, lastScanAt)).apply();
    }

    private void publish() {
        if (listeners.isEmpty()) {
            return;
        }
        State state = state();
        main.execute(() -> {
            for (Listener listener : listeners) {
                listener.onProxyState(state);
            }
        });
    }

    @Nullable
    private static String readSaved(@NonNull SharedPreferences prefs) {
        try {
            return prefs.getString(PREF_LIST, null);
        } catch (ClassCastException e) {
            return null;
        }
    }

    /**
     * The home page of the site the proxy has to reach: the catalog source selected in the settings, unless it goes
     * direct ({@link AppProxy#PREF_DIRECT}); then the first source that takes the proxy, in the user's order.
     */
    @NonNull
    static String targetOf(@NonNull SharedPreferences prefs) {
        String selected;
        try {
            selected = prefs.getString("sorce_books", null);
        } catch (RuntimeException e) {
            selected = null; // a wrongly typed value: the default site
        }
        return targetOf(selected, AppProxy.readDirect(prefs));
    }

    /** {@link #targetOf(SharedPreferences)} for the selected source's {@code prefValue} and the direct sources. */
    @NonNull
    static String targetOf(@Nullable String selected, @NonNull Set<String> direct) {
        if (selected != null && !direct.contains(selected)) {
            for (Provider provider : Provider.values()) {
                if (selected.equals(provider.prefValue)) {
                    return provider.server + "/";
                }
            }
        }
        if (!direct.isEmpty()) {
            for (Provider provider : Sources.all()) { // the user's order: an active source first
                if (!direct.contains(provider.prefValue)) {
                    return provider.server + "/";
                }
            }
        }
        return FALLBACK_TARGET;
    }

    private static ThreadFactory daemon(@NonNull String name) {
        AtomicInteger count = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable, name + "-" + count.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }

    /** The persisted list: the verified proxies, the one in use and when they were found. */
    static final class Saved {
        final List<ProxyCandidate> proxies;
        final int index;
        final long scannedAt;

        Saved(@NonNull List<ProxyCandidate> proxies, int index, long scannedAt) {
            this.proxies = proxies;
            this.index = index;
            this.scannedAt = scannedAt;
        }

        @NonNull
        static String toJson(@NonNull List<ProxyCandidate> proxies, int index, long scannedAt) {
            JsonObject root = new JsonObject();
            root.addProperty("v", 1);
            root.addProperty("current", index);
            root.addProperty("scannedAt", scannedAt);
            JsonArray array = new JsonArray();
            for (ProxyCandidate proxy : proxies) {
                JsonObject item = new JsonObject();
                item.addProperty("host", proxy.host());
                item.addProperty("port", proxy.port());
                item.addProperty("type", proxy.typeKey());
                item.addProperty("ms", proxy.latencyMs());
                item.addProperty("at", proxy.checkedAt());
                array.add(item);
            }
            root.add("proxies", array);
            return root.toString();
        }

        /** Anything unreadable is an empty list: the finder then searches again. */
        @NonNull
        static Saved fromJson(@Nullable String json) {
            List<ProxyCandidate> proxies = new ArrayList<>();
            if (json == null || json.isEmpty()) {
                return new Saved(Collections.unmodifiableList(proxies), 0, 0);
            }
            try {
                JsonObject root = JsonParser.parseString(json).getAsJsonObject();
                JsonArray array = root.has("proxies") ? root.getAsJsonArray("proxies") : new JsonArray();
                for (JsonElement element : array) {
                    JsonObject item = element.getAsJsonObject();
                    String host = item.get("host").getAsString();
                    int port = item.get("port").getAsInt();
                    Proxy.Type type = ProxyCandidate.typeOf(item.get("type").getAsString());
                    if (type == null || !ProxySources.isPublicIpv4(host) || port < 1 || port > 65535) {
                        continue;
                    }
                    long ms = item.has("ms") ? item.get("ms").getAsLong() : ProxyCandidate.UNCHECKED;
                    long at = item.has("at") ? item.get("at").getAsLong() : 0;
                    proxies.add(new ProxyCandidate(host, port, type, ms, at));
                }
                int index = root.has("current") ? root.get("current").getAsInt() : 0;
                long scannedAt = root.has("scannedAt") ? root.get("scannedAt").getAsLong() : 0;
                return new Saved(Collections.unmodifiableList(proxies), Math.max(0, index), scannedAt);
            } catch (RuntimeException e) {
                return new Saved(Collections.<ProxyCandidate>emptyList(), 0, 0);
            }
        }
    }

    /**
     * The real network. Lists come directly ({@link Proxy#NO_PROXY}), never through the proxy in use. A check is one
     * GET (a POST for audioboo, as {@link SiteHttp} sends) of the catalog site through the candidate without following redirects; only a {@link ProxyFinder#usable}
     * answer passes: over HTTPS the TLS handshake with the real site proves the tunnel reached it. A 4xx (403 geo-block
     * or challenge, 429 throttled) or 5xx means the site does not serve pages through this proxy. No connection
     * outlives its check.
     */
    static final class OkNetwork implements Network {
        /** Built on first use, on the finder's threads: the finder itself is created during app start. */
        private OkHttpClient base;
        private OkHttpClient lists;

        private synchronized OkHttpClient base() {
            if (base == null) {
                base = new OkHttpClient.Builder()
                        .proxy(Proxy.NO_PROXY)
                        .connectionPool(new ConnectionPool(0, 1, TimeUnit.SECONDS))
                        .retryOnConnectionFailure(false)
                        .followRedirects(false)
                        .followSslRedirects(false)
                        .build();
            }
            return base;
        }

        private synchronized OkHttpClient lists() {
            if (lists == null) {
                lists = base().newBuilder()
                        .followRedirects(true)
                        .followSslRedirects(true)
                        .connectTimeout(LIST_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                        .readTimeout(LIST_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                        .writeTimeout(LIST_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                        .callTimeout(FETCH_DEADLINE_MS, TimeUnit.MILLISECONDS)
                        .build();
            }
            return lists;
        }

        @NonNull
        @Override
        public String fetch(@NonNull ProxySources.Source source) throws IOException {
            Request request = new Request.Builder().url(source.url).header("User-Agent", Consts.USER_AGENT).build();
            try (Response response = lists().newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    throw new IOException("HTTP " + response.code() + " from " + source.name);
                }
                return readCapped(response.body().byteStream(), MAX_LIST_BYTES);
            }
        }

        @Override
        public long probe(@NonNull ProxyCandidate candidate, @NonNull String target, int timeoutMs) throws IOException {
            OkHttpClient client = base().newBuilder()
                    .proxy(candidate.toProxy())
                    .connectTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                    .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                    .writeTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                    .callTimeout(timeoutMs * 2L, TimeUnit.MILLISECONDS)
                    .build();
            Request.Builder builder = new Request.Builder().url(target).header("User-Agent", Consts.USER_AGENT);
            Provider site = Provider.ofUrl(target);
            if (site != null && site.parser.postsPages() && Url.isHost(target, site.server)) {
                // like SiteHttp: the site's wall answers a GET with a challenge, a POST passes
                builder.post(new FormBody.Builder().build());
            } else {
                builder.header("Range", "bytes=0-0");
            }
            Request request = builder.build();
            long start = System.nanoTime();
            try (Response response = client.newCall(request).execute()) {
                if (!usable(response.code())) {
                    throw new IOException("HTTP " + response.code() + " through " + candidate);
                }
                return Math.max(1, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start));
            }
        }

        private static String readCapped(@NonNull InputStream in, int max) throws IOException {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while (out.size() < max && (read = in.read(buffer, 0, Math.min(buffer.length, max - out.size()))) != -1) {
                out.write(buffer, 0, read);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
