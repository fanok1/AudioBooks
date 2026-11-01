package com.fanok.audiobooks;

import android.os.StrictMode;
import androidx.annotation.OptIn;
import android.app.Application;
import android.content.SharedPreferences;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.database.StandaloneDatabaseProvider;
import androidx.media3.datasource.cache.NoOpCacheEvictor;
import androidx.media3.datasource.cache.SimpleCache;
import androidx.media3.exoplayer.offline.DefaultDownloadIndex;
import androidx.media3.exoplayer.offline.DownloadManager;
import androidx.media3.exoplayer.scheduler.Requirements;
import androidx.preference.PreferenceManager;

import com.fanok.audiobooks.download.DownloadMonitor;
import com.fanok.audiobooks.net.AppProxy;
import com.fanok.audiobooks.net.MemoryCookieJar;
import com.fanok.audiobooks.pojo.StorageUtil;
import com.fanok.audiobooks.presenter.BookPresenter;
import com.fanok.audiobooks.search.Provider;
import com.fanok.audiobooks.service.PlaybackSettings;
import com.fanok.audiobooks.util.DownloadStorageHelper;
import com.fanok.audiobooks.util.DownloadUtil;


import io.reactivex.exceptions.UndeliverableException;
import io.reactivex.plugins.RxJavaPlugins;

import java.io.File;
import java.io.IOException;
import java.net.SocketException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@OptIn(markerClass = UnstableApi.class)
public class App extends Application {

    /** Name of the Media3 download store below the app-specific files directory. */
    public static final String DOWNLOAD_CACHE_DIR = "media3_downloads";
    private static final String PREF_DOWNLOAD_CACHE_DIR = "media3_download_cache_dir";
    /** The store a move left behind, deleted at the next start ({@link #moveStoreOnNextStart}). */
    private static final String PREF_OLD_STORE_DIR = "media3_old_store_dir";
    /** The id file name ({@code *.uid}) the moved store holds: the old one goes only once the open store has it. */
    private static final String PREF_STORE_UID = "media3_store_uid";
    /** Set once the stored "bufferSize" was moved from the old default (60 s) to the new one. */
    private static final String PREF_BUFFER_120 = "buffer_default_120";

    // One download manager, cache and executor for the process lifetime, created once on first use and never
    // released or re-created: the download folder setting only names where explicit exports go.
    private volatile DownloadManager downloadManager;
    private volatile SimpleCache downloadCache;
    private File downloadCacheDir;
    private ExecutorService downloadExecutor;

    private volatile StandaloneDatabaseProvider databaseProvider;
    private static final MemoryCookieJar COOKIE_JAR = new MemoryCookieJar();

    private static App instance;

    public static File getCrashFile() {
        return new java.io.File(getInstance().getFilesDir(), "crash.txt");
    }

    private static volatile String currentActivityName = "None";

    /** The app's activities from creation to destruction (main thread only). */
    private static final java.util.Set<android.app.Activity> LIVE_ACTIVITIES = new java.util.LinkedHashSet<>();

    /** Closes every screen of the app; its tasks stay in the recent apps. Main thread only. */
    public static void finishActivities() {
        for (android.app.Activity activity : new java.util.ArrayList<>(LIVE_ACTIVITIES)) {
            activity.finish();
        }
    }

    private static void appendCapped(File file, String content, int maxBytes) {
        try {
            StringBuilder sb = new StringBuilder();
            if (file.exists() && file.length() > maxBytes) {
                try (java.io.BufferedReader br = new java.io.BufferedReader(new java.io.FileReader(file))) {
                    String line;
                    java.util.List<String> lines = new java.util.ArrayList<>();
                    while ((line = br.readLine()) != null) {
                        lines.add(line);
                    }
                    int keepCount = Math.max(1, lines.size() / 2);
                    for (int i = lines.size() - keepCount; i < lines.size(); i++) {
                        sb.append(lines.get(i)).append("\n");
                    }
                }
            } else if (file.exists()) {
                try (java.io.BufferedReader br = new java.io.BufferedReader(new java.io.FileReader(file))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        sb.append(line).append("\n");
                    }
                }
            }
            sb.append(content);
            try (java.io.FileOutputStream fw = new java.io.FileOutputStream(file, false)) {
                fw.write(sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                fw.flush();
            }
        } catch (Exception ignored) {}
    }

    private static void writeStackToFile(File file, Throwable t) {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("=== CRASH ").append(new java.util.Date()).append(" ===\n");
            sb.append("Screen: ").append(currentActivityName).append("\n");
            Runtime r = Runtime.getRuntime();
            long maxMb = r.maxMemory() / (1024 * 1024);
            long totalMb = r.totalMemory() / (1024 * 1024);
            long freeMb = r.freeMemory() / (1024 * 1024);
            long usedMb = totalMb - freeMb;
            sb.append("Memory: used ").append(usedMb).append("MB / free ").append(freeMb).append("MB / max ").append(maxMb).append("MB\n");
            if (t != null) {
                sb.append("EXCEPTION: ").append(t.getClass().getName()).append(": ").append(t.getMessage()).append("\n");
                for (StackTraceElement e : t.getStackTrace()) {
                    sb.append("    at ").append(e).append("\n");
                }
                Throwable cause = t.getCause();
                while (cause != null) {
                    sb.append("Caused by: ").append(cause.getClass().getName()).append(": ").append(cause.getMessage()).append("\n");
                    for (StackTraceElement e : cause.getStackTrace()) {
                        sb.append("    at ").append(e).append("\n");
                    }
                    cause = cause.getCause();
                }
            }
            sb.append("\n");
            appendCapped(file, sb.toString(), 30000);
        } catch (Exception ignored) {
        }
    }

    /**
     * RxJava 2 global error handler: errors that can no longer be delivered (the subscriber is
     * disposed) are logged when they are expected network/cancellation noise; bugs are forwarded
     * to the thread's uncaught-exception handler instead of being swallowed.
     */
    static void handleRxError(Throwable e) {
        if (e instanceof UndeliverableException && e.getCause() != null) {
            e = e.getCause();
        }
        if (e instanceof IOException || e instanceof SocketException || e instanceof InterruptedException) {
            android.util.Log.w("App", "Undeliverable Rx error", e);
            return;
        }
        Thread thread = Thread.currentThread();
        Thread.UncaughtExceptionHandler handler = thread.getUncaughtExceptionHandler();
        if (handler != null) {
            handler.uncaughtException(thread, e);
        }
    }

    public static okhttp3.OkHttpClient getOkHttpClient() {
        return okHttpClientInstance;
    }

    /** After a proxy change: idle pooled connections (direct or via the old proxy) of the shared client are dropped,
     *  off the calling thread; the download client shares this pool. */
    public static void evictConnections() {
        okhttp3.OkHttpClient client = okHttpClientInstance;
        if (client != null) {
            new Thread(() -> client.connectionPool().evictAll(), "evict-connections").start();
        }
    }

    private static volatile okhttp3.OkHttpClient downloadHttpClient;

    /**
     * The client of downloads: the shared one (connection pool, cookies, headers, proxy) restricted to HTTP/1.1.
     * akniga's CDN limits every connection to about 300 KB/s, and HTTP/2 puts all parallel segment requests of a
     * download on one connection; one connection per request makes a book download about three times faster.
     * Playback, pages and covers keep the shared client.
     */
    public static okhttp3.OkHttpClient getDownloadHttpClient() {
        okhttp3.OkHttpClient client = downloadHttpClient;
        if (client == null && okHttpClientInstance != null) {
            client = okHttpClientInstance.newBuilder()
                    .protocols(java.util.Collections.singletonList(okhttp3.Protocol.HTTP_1_1))
                    .build();
            downloadHttpClient = client;
        }
        return client;
    }

    public static okhttp3.OkHttpClient okHttpClientInstance;

    /** Cookies of the shared client: response cookies are resent to their host for the process lifetime. */
    public static MemoryCookieJar getCookieJar() {
        return COOKIE_JAR;
    }

    @OptIn(markerClass = UnstableApi.class)
    @Override
    public void onCreate() {
        super.onCreate();
        // the user's sources first: the proxy is verified against one of them, the selected source must be active
        com.fanok.audiobooks.search.Sources.load(PreferenceManager.getDefaultSharedPreferences(this));
        //before any HTTP client exists: OkHttp captures the default ProxySelector when it is built
        AppProxy.install(this);
        if (BuildConfig.DEBUG) {
            // Debug only: report main-thread disk/network access and leaked closeables/cursors (DATA-07).
            StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder().detectAll().penaltyLog().build());
            StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder().detectAll().penaltyLog().build());
        }
        instance = this;
        com.fanok.audiobooks.search.SourceHealth.checkIfDue(this); // at most daily, delayed, unmetered only

        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override
            public void onActivityCreated(@androidx.annotation.NonNull android.app.Activity activity, @androidx.annotation.Nullable android.os.Bundle savedInstanceState) {
                LIVE_ACTIVITIES.add(activity);
            }
            @Override
            public void onActivityStarted(@androidx.annotation.NonNull android.app.Activity activity) {
                currentActivityName = activity.getClass().getSimpleName();
            }
            @Override
            public void onActivityResumed(@androidx.annotation.NonNull android.app.Activity activity) {
                currentActivityName = activity.getClass().getSimpleName();
            }
            @Override
            public void onActivityPaused(@androidx.annotation.NonNull android.app.Activity activity) {}
            @Override
            public void onActivityStopped(@androidx.annotation.NonNull android.app.Activity activity) {}
            @Override
            public void onActivitySaveInstanceState(@androidx.annotation.NonNull android.app.Activity activity, @androidx.annotation.NonNull android.os.Bundle outState) {}
            @Override
            public void onActivityDestroyed(@androidx.annotation.NonNull android.app.Activity activity) {
                LIVE_ACTIVITIES.remove(activity);
            }
        });

        final Thread.UncaughtExceptionHandler previousHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            try {
                writeStackToFile(getCrashFile(), throwable);
            } catch (Exception ignored) {
            } finally {
                if (previousHandler != null) {
                    previousHandler.uncaughtException(thread, throwable);
                } else {
                    android.os.Process.killProcess(android.os.Process.myPid());
                    System.exit(10);
                }
            }
        });
        RxJavaPlugins.setErrorHandler(App::handleRxError);

        try {
            okhttp3.OkHttpClient.Builder builder = new okhttp3.OkHttpClient.Builder().cookieJar(COOKIE_JAR);

            // Application interceptor (runs before OkHttp adds its own User-Agent): fills in defaults
            // only, never overrides a header the caller or the DownloadUtil resolver already set.
            builder.addInterceptor(chain -> {
                okhttp3.Request original = chain.request();
                okhttp3.Request.Builder requestBuilder = original.newBuilder();
                if (original.header("User-Agent") == null) {
                    requestBuilder.header("User-Agent", Consts.USER_AGENT);
                }
                if (original.header("Referer") == null) {
                    String url = original.url().toString();
                    Provider provider = Provider.ofNetworkHost(Url.hostOf(url));
                    String referer = provider == null ? null : provider.parser.referer();
                    if (referer != null) {
                        requestBuilder.header("Referer", referer);
                    }
                }
                return chain.proceed(requestBuilder.build());
            });

            // OkHttp 5 tells the ProxySelector only of failed TCP connects; a refused tunnel or handshake through an
            // automatic proxy is reported here. The download client (newBuilder) inherits this listener.
            builder.eventListener(new okhttp3.EventListener() {
                @Override
                public void connectFailed(@androidx.annotation.NonNull okhttp3.Call call,
                        @androidx.annotation.NonNull java.net.InetSocketAddress inetSocketAddress,
                        @androidx.annotation.NonNull java.net.Proxy proxy,
                        @androidx.annotation.Nullable okhttp3.Protocol protocol,
                        @androidx.annotation.NonNull IOException ioe) {
                    if (proxy.type() != java.net.Proxy.Type.DIRECT && !call.isCanceled()) {
                        AppProxy.reportConnectFailure(proxy.address(), ioe);
                    }
                }
            });

            //no happy-eyeballs race across [current, spare] proxy routes: a lost race would count as a proxy failure
            builder.fastFallback(false);
            // an HTTP built-in proxy asks for its login with a 407; a SOCKS one gets it from the Authenticator
            builder.proxyAuthenticator(AppProxy::proxyLogin);
            builder.addNetworkInterceptor(AppProxy::keepProxyLoginToProxy);
            okHttpClientInstance = builder.build();

            com.squareup.picasso.Picasso picasso = new com.squareup.picasso.Picasso.Builder(this)
                    .downloader(new com.squareup.picasso.OkHttp3Downloader(okHttpClientInstance))
                    .loggingEnabled(BuildConfig.DEBUG)
                    .build();
            com.squareup.picasso.Picasso.setSingletonInstance(picasso);

                    } catch (Exception e) {
            e.printStackTrace();
                    }


        // User's original code
        SharedPreferences pref = PreferenceManager
                .getDefaultSharedPreferences(this);
        // the default buffer grew from 60 s to 120 s: the old default the settings screen stored moves with it, once
        if (!pref.getBoolean(PREF_BUFFER_120, false)) {
            SharedPreferences.Editor editor = pref.edit().putBoolean(PREF_BUFFER_120, true);
            if ("60".equals(pref.getString("bufferSize", null))) {
                editor.putString("bufferSize", String.valueOf(PlaybackSettings.DEFAULT_BUFFER_SECONDS));
            }
            editor.apply();
        }

        AppThemes.applyNightMode(this);

        Consts.setBazaKnigCookies(pref.getString("cookes_baza_knig", ""));


        BookPresenter.setSpeedWithoutBroadcast(new StorageUtil(getBaseContext()).loadSpeed());

        String source = pref.getString("sorce_books", getString(R.string.abook_value));

        Consts.setSOURCE(source);

        getDownloadManager();
        deleteOldStore();
        // before the download service can resume the manager: the monitor sees every run from its first event and
        // takes over the resting notification an earlier process left
        DownloadMonitor.get();
        // what finished while the app was not running, or an export left half done, goes to the download folder now
        DownloadStorageHelper.exportPending(this);
    }


    public StandaloneDatabaseProvider getDatabaseProvider() {
        StandaloneDatabaseProvider provider = databaseProvider;
        if (provider == null) {
            synchronized (this) {
                if (databaseProvider == null) databaseProvider = new StandaloneDatabaseProvider(this);
                provider = databaseProvider;
            }
        }
        return provider;
    }

    public static App getInstance() {
        return instance;
    }

    /**
     * The one download manager. Contract: it is created on the main thread (eagerly at the end of {@link #onCreate}),
     * so its listeners and requirement watcher run on the main looper; a caller on another thread that comes first
     * has the creation posted to the main looper and waits for it, failing with an IllegalStateException after
     * {@link #MAIN_THREAD_WAIT_SECONDS} instead of creating it on its own thread.
     */
    @OptIn(markerClass = UnstableApi.class)
    public DownloadManager getDownloadManager() {
        DownloadManager dm = downloadManager;
        if (dm == null) {
            createDownloadStackOnMain();
            dm = downloadManager;
        }
        return dm;
    }

    public SimpleCache getDownloadCache() {
        SimpleCache cache = downloadCache;
        if (cache == null) {
            createDownloadStackOnMain();
            cache = downloadCache;
        }
        return cache;
    }

    private static final long MAIN_THREAD_WAIT_SECONDS = 10;

    @OptIn(markerClass = UnstableApi.class)
    private void createDownloadStackOnMain() {
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            createDownloadStack();
            return;
        }
        java.util.concurrent.FutureTask<Void> task = new java.util.concurrent.FutureTask<>(this::createDownloadStack, null);
        new android.os.Handler(android.os.Looper.getMainLooper()).post(task);
        try {
            task.get(MAIN_THREAD_WAIT_SECONDS, java.util.concurrent.TimeUnit.SECONDS);
        } catch (java.util.concurrent.TimeoutException e) {
            throw new IllegalStateException("DownloadManager not created: the main thread did not run the creation within "
                    + MAIN_THREAD_WAIT_SECONDS + " s", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while the main thread creates the DownloadManager", e);
        } catch (java.util.concurrent.ExecutionException e) {
            throw new IllegalStateException("DownloadManager creation failed", e.getCause());
        }
    }

    /**
     * Makes {@code dir}, a copy of the store, the store from the next start on, and {@code old} the store to delete
     * then. Committed at once: the process ends right after ({@link com.fanok.audiobooks.download.StoreLocation}).
     */
    public void moveStoreOnNextStart(@androidx.annotation.NonNull File dir, @androidx.annotation.NonNull File old,
                                     @androidx.annotation.NonNull String uid) {
        PreferenceManager.getDefaultSharedPreferences(this).edit()
                .putString(PREF_DOWNLOAD_CACHE_DIR, dir.getAbsolutePath())
                .putString(PREF_OLD_STORE_DIR, old.getAbsolutePath())
                .putString(PREF_STORE_UID, uid)
                .commit();
    }

    /**
     * The store a move left behind, now that its copy is open: deleted file by file, never through SimpleCache (that
     * would drop the database tables the copy uses). Only a directory named like a store and not the open one, and
     * only once the open store holds the copied id file: a store the cache had to create anew (a memory card taken
     * out or wiped) has an id of its own. On the storage worker, after and before any move.
     */
    private void deleteOldStore() {
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(this);
        String old = pref.getString(PREF_OLD_STORE_DIR, null);
        String uid = pref.getString(PREF_STORE_UID, null);
        if (old == null) return;
        File oldDir = new File(old);
        File current = getDownloadCacheDir();
        DownloadStorageHelper.submit(() -> {
            if (uid == null || !new File(current, uid).isFile()) {
                return; // the copy is not there: the old store stays, and waits for the next start
            }
            if (!oldDir.equals(current) && DOWNLOAD_CACHE_DIR.equals(oldDir.getName())) {
                com.fanok.audiobooks.download.StoreLocation.deleteTree(oldDir);
            }
            pref.edit().remove(PREF_OLD_STORE_DIR).remove(PREF_STORE_UID).apply();
        });
    }

    /** Directory of the Media3 download store (app-specific, never the user folder or the clearable cache dir). */
    public File getDownloadCacheDir() {
        getDownloadCache();
        return downloadCacheDir;
    }

    /**
     * Creates the download store once: a non-evicting {@link SimpleCache} in app-specific storage, its executor (Media3
     * downloads HLS segments in parallel on it) and the manager. The volatile manager is published last, so a reader
     * that sees it also sees the cache.
     */
    @OptIn(markerClass = UnstableApi.class)
    private synchronized void createDownloadStack() {
        if (downloadManager != null) return;
        File dir = downloadStoreDir();
        SimpleCache cache = new SimpleCache(dir, new NoOpCacheEvictor(), getDatabaseProvider());
        ExecutorService executor = Executors.newFixedThreadPool(DOWNLOAD_THREADS);
        DownloadManager dm = new DownloadManager(this, new DefaultDownloadIndex(getDatabaseProvider()),
                DownloadUtil.downloaderFactory(this, cache, executor));
        dm.setMaxParallelDownloads(1);
        dm.setRequirements(new Requirements(Requirements.NETWORK));
        followNetworkForDownloads(dm);
        downloadCacheDir = dir;
        downloadExecutor = executor;
        downloadCache = cache;
        downloadManager = dm;
    }

    /** Parallel segment requests of one HLS download, each on its own connection (see {@link #getDownloadHttpClient}). */
    private static final int DOWNLOAD_THREADS = 4;

    /**
     * Media3's NETWORK requirement also wants Android to have validated the network (reached Google's connectivity
     * check). Where that check fails while the internet works, every download stayed queued behind a spinner. The
     * requirement follows the default network instead: none while it has internet access (a captive portal excepted),
     * NETWORK while there is none, so downloads still wait offline and go on when a network returns. The manager is
     * only touched on the main thread, where it was created.
     */
    @OptIn(markerClass = UnstableApi.class)
    private void followNetworkForDownloads(@androidx.annotation.NonNull DownloadManager dm) {
        android.net.ConnectivityManager connectivity =
                (android.net.ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        if (connectivity == null) {
            return;
        }
        android.os.Handler main = new android.os.Handler(android.os.Looper.getMainLooper());
        try {
            //callbacks come on a connectivity thread; every decision and its state live on the main thread
            connectivity.registerDefaultNetworkCallback(new android.net.ConnectivityManager.NetworkCallback() {
                /** The default network the last decision was about; a late loss of an older one is ignored. */
                private android.net.Network current;
                /** Whether that network was validated; null before the first decision. */
                private Boolean validated;

                @Override
                public void onAvailable(@androidx.annotation.NonNull android.net.Network network) {
                    android.net.NetworkCapabilities caps = connectivity.getNetworkCapabilities(network);
                    main.post(() -> decide(network, caps));
                    if (caps == null) {
                        //the lookup can race the callback (API 24-25 send no capabilities right after it)
                        main.postDelayed(() -> {
                            if (network.equals(current)) {
                                decide(network, connectivity.getNetworkCapabilities(network));
                            }
                        }, 1_000);
                    }
                }

                @Override
                public void onCapabilitiesChanged(@androidx.annotation.NonNull android.net.Network network,
                                                  @androidx.annotation.NonNull android.net.NetworkCapabilities caps) {
                    main.post(() -> decide(network, caps));
                }

                @Override
                public void onLost(@androidx.annotation.NonNull android.net.Network network) {
                    main.post(() -> {
                        if (network.equals(current)) {
                            decide(null, null);
                        }
                    });
                }

                private void decide(@androidx.annotation.Nullable android.net.Network network,
                                    @androidx.annotation.Nullable android.net.NetworkCapabilities caps) {
                    current = network;
                    boolean run = caps != null && downloadsMayRun(
                            caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET),
                            caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL));
                    boolean nowValidated = caps != null
                            && caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED);
                    boolean internetBack = Boolean.FALSE.equals(validated) && nowValidated;
                    validated = nowValidated;
                    dm.setRequirements(new Requirements(run ? 0 : Requirements.NETWORK));
                    if (internetBack) {
                        retryFailedDownloads(dm, main);
                    }
                }
            });
        } catch (RuntimeException e) {
            //no permission or too many callbacks: downloads keep Media3's validated-network requirement
            android.util.Log.w("App", "downloads cannot follow the network", e);
        }
    }

    /**
     * The internet came back (the network is validated again): downloads that ran out of retries meanwhile, on a
     * network that was up but could not reach anything, are queued again and keep the bytes already in the cache.
     */
    @OptIn(markerClass = UnstableApi.class)
    private static void retryFailedDownloads(@androidx.annotation.NonNull DownloadManager dm,
                                             @androidx.annotation.NonNull android.os.Handler main) {
        DownloadUtil.runInBackground(() -> {
            java.util.List<androidx.media3.exoplayer.offline.DownloadRequest> failed = new java.util.ArrayList<>();
            try (androidx.media3.exoplayer.offline.DownloadCursor cursor =
                         dm.getDownloadIndex().getDownloads(androidx.media3.exoplayer.offline.Download.STATE_FAILED)) {
                while (cursor.moveToNext()) {
                    failed.add(cursor.getDownload().request);
                }
            } catch (IOException e) {
                android.util.Log.w("App", "failed downloads not read", e);
            }
            if (!failed.isEmpty()) {
                main.post(() -> {
                    for (androidx.media3.exoplayer.offline.DownloadRequest request : failed) {
                        dm.addDownload(request);
                    }
                });
            }
        });
    }

    /** Whether downloads may run on the default network: internet access that is not a captive portal's. */
    static boolean downloadsMayRun(boolean internet, boolean captivePortal) {
        return internet && !captivePortal;
    }

    /**
     * The store directory, chosen once and remembered so it never moves between runs. A store left in an old place
     * (below the download folder, or the cache-dir fallback) is moved here; the new place is remembered only once it
     * holds the store, so after a failed move the old store stays in use and the move is retried on the next start.
     */
    private File downloadStoreDir() {
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(this);
        String saved = pref.getString(PREF_DOWNLOAD_CACHE_DIR, null);
        if (saved != null) return new File(saved);
        File base = getExternalFilesDir(null);
        if (base == null) base = getFilesDir();
        File target = new File(base, DOWNLOAD_CACHE_DIR);
        File dir = chooseStoreDir(target, java.util.Arrays.asList(
                new File(DownloadStorageHelper.getDownloadPath(this), ".audiobooks_cache"),
                new File(getCacheDir(), "downloads")),
                (from, to) -> (!to.exists() || to.delete()) && from.renameTo(to));
        if (dir.equals(target)) {
            pref.edit().putString(PREF_DOWNLOAD_CACHE_DIR, dir.getAbsolutePath()).apply();
        } else {
            android.util.Log.w("App", "Download store not moved, still using " + dir);
        }
        return dir;
    }

    /** Moves a store directory; true when {@code to} now holds it. */
    interface StoreMover {
        boolean move(File from, File to);
    }

    /**
     * Where the download store lives: {@code target} when it already holds data or no legacy store does; else the
     * first legacy store with data, moved into {@code target} by {@code mover}, or left where it is (and returned)
     * when the move fails.
     */
    static File chooseStoreDir(File target, java.util.List<File> legacy, StoreMover mover) {
        if (hasEntries(target)) return target;
        for (File old : legacy) {
            if (!hasEntries(old)) continue;
            return mover.move(old, target) ? target : old;
        }
        return target;
    }

    private static boolean hasEntries(File dir) {
        String[] names = dir.list();
        return names != null && names.length > 0;
    }

    @Override
    public void onTerminate() {
        ExecutorService executor = downloadExecutor;
        if (executor != null) executor.shutdown();
        DownloadUtil.shutdown();
        super.onTerminate();
    }

}
