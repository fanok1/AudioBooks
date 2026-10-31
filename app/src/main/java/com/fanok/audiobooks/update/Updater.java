package com.fanok.audiobooks.update;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.webkit.CookieManager;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;
import androidx.core.content.pm.PackageInfoCompat;
import androidx.preference.PreferenceManager;

import com.fanok.audiobooks.App;
import com.fanok.audiobooks.BuildConfig;
import com.fanok.audiobooks.R;
import com.fanok.audiobooks.net.AppProxy;
import com.fanok.audiobooks.update.UpdateSources.Release;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import okhttp3.Call;
import okhttp3.Cookie;
import okhttp3.CookieJar;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * "Автообновление": finds a newer version of the app, downloads it and offers to install it. A check runs
 * {@link #START_DELAY_S} after the app starts and then every {@link #PERIOD_MS} while the process lives (time asleep
 * counts); "О программе" runs one by hand. The places are asked in order: GitHub and the mirror through the settings'
 * route, both again through the other routes ({@link AppProxy#updateRoutes}), then 4pda. The first that answers decides
 * whether there is a newer version; its APK, or failing that the next place's, is downloaded, checked (hash, package,
 * version code, signature) and offered. 4pda's files need a forum login: without one the offer is to log in.
 * All work runs on one background thread, one check at a time.
 */
public final class Updater {

    public static final String PREF = "auto_update";
    /** The WebView's user agent, stored by the forum screen: 4pda's cookies are sent only with it. */
    private static final String PREF_FORUM_AGENT = "update_forum_agent";
    private static final String PREF_SNOOZE_VERSION = "update_snooze_version";
    private static final String PREF_SNOOZE_AT = "update_snooze_at";
    private static final long START_DELAY_S = 15;
    private static final long TICK_S = 15 * 60;
    static final long PERIOD_MS = TimeUnit.MINUTES.toMillis(90);
    /** An offer the user has seen comes back by itself after this long. */
    private static final long SNOOZE_MS = TimeUnit.DAYS.toMillis(1);
    private static final long MAX_APK_BYTES = 100L << 20;
    private static final String DIR = "update";
    private static final String TAG = "Updater";

    public enum Result { LATEST, READY, LOGIN, FAILED }

    /** What to put to the user: a checked APK to install, or a 4pda release whose file needs a forum login. */
    public static final class Offer {
        @NonNull public final Release release;
        @Nullable public final File apk;
        /** The hash the file must have, also from another place (GitHub) for the same version; null when unknown. */
        @Nullable final String sha;

        Offer(@NonNull Release release, @Nullable File apk, @Nullable String sha) {
            this.release = release;
            this.apk = apk;
            this.sha = sha;
        }
    }

    private enum Site { GITHUB, MIRROR, FORUM }

    /** A 4pda answer that is not the file: a login (or the site's browser check) is needed first. */
    private static final class LoginRequired extends IOException {
        LoginRequired(String message) {
            super(message);
        }
    }

    private static final ScheduledExecutorService WORKER = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "updater");
        thread.setDaemon(true);
        return thread;
    });
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static boolean started; // main thread
    private static Runnable listener; // main thread
    /** {@link SystemClock#elapsedRealtime} of the last automatic check; the first one is due at once. */
    private static long lastCheck = -PERIOD_MS; // worker thread
    private static volatile Offer offer;

    private Updater() {
    }

    /** Starts the periodic checks, once per process; each runs only while the setting is on. */
    public static void start(@NonNull Context context) {
        if (started) {
            return;
        }
        started = true;
        Context app = context.getApplicationContext();
        // a short tick, so a check that came due while the device slept runs soon after it wakes
        WORKER.scheduleWithFixedDelay(() -> {
            long now = SystemClock.elapsedRealtime();
            if (now - lastCheck >= PERIOD_MS && prefs(app).getBoolean(PREF, true)) {
                lastCheck = now;
                run(app);
            }
        }, START_DELAY_S, TICK_S, TimeUnit.SECONDS);
    }

    /** A check now, whatever the setting; its offer is shown even if the user put it off. {@code done} on main. */
    public static void check(@NonNull Context context, @Nullable Consumer<Result> done) {
        Context app = context.getApplicationContext();
        WORKER.execute(() -> {
            prefs(app).edit().remove(PREF_SNOOZE_VERSION).remove(PREF_SNOOZE_AT).apply();
            Result result = run(app);
            if (done != null) {
                MAIN.post(() -> done.accept(result));
            }
        });
    }

    /** Called (on main) when there may be a new offer; null stops the calls. */
    public static void setListener(@Nullable Runnable onOffer) {
        listener = onOffer;
    }

    /** The offer to put to the user now: null when there is none or the user put this version off lately. */
    @Nullable
    public static Offer pending(@NonNull Context context) {
        Offer current = offer;
        if (current == null) {
            return null;
        }
        SharedPreferences prefs = prefs(context);
        long since = System.currentTimeMillis() - prefs.getLong(PREF_SNOOZE_AT, 0);
        boolean snoozed = current.release.version.equals(prefs.getString(PREF_SNOOZE_VERSION, null))
                && since >= 0 && since < SNOOZE_MS;
        return snoozed ? null : current;
    }

    /** The user has seen {@code shown}: not offered by itself again for a day. */
    public static void snooze(@NonNull Context context, @NonNull Offer shown) {
        prefs(context).edit().putString(PREF_SNOOZE_VERSION, shown.release.version)
                .putLong(PREF_SNOOZE_AT, System.currentTimeMillis()).apply();
    }

    /** Hands the APK to the system installer, from the user's tap (a visible activity may start it). */
    public static void install(@NonNull Activity activity, @NonNull Offer ready) {
        if (ready.apk == null || !ready.apk.isFile()) {
            Toast.makeText(activity, R.string.update_install_failed, Toast.LENGTH_SHORT).show();
            return;
        }
        Uri uri = FileProvider.getUriForFile(activity, activity.getPackageName() + ".update", ready.apk);
        Intent intent = new Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            activity.startActivity(intent);
        } catch (ActivityNotFoundException | SecurityException e) {
            Log.w(TAG, "No installer", e);
            Toast.makeText(activity, R.string.update_install_failed, Toast.LENGTH_SHORT).show();
        }
    }

    /** From the forum screen, on main: its user agent goes with the cookies it leaves. */
    public static void rememberForumAgent(@NonNull Context context, @NonNull String agent) {
        prefs(context).edit().putString(PREF_FORUM_AGENT, agent).apply();
    }

    /** The user left the forum screen: the offered 4pda file is fetched again with the login now in the cookies. */
    public static void forumLeft(@NonNull Context context) {
        Offer current = offer;
        if (current == null) {
            check(context, null); // the process was restarted meanwhile: the whole check again, with the login
            return;
        }
        if (current.apk != null) {
            return;
        }
        Context app = context.getApplicationContext();
        WORKER.execute(() -> {
            try {
                CookieManager.getInstance().flush();
                OkHttpClient base = App.getOkHttpClient();
                if (base == null) {
                    throw new IOException("no HTTP client");
                }
                File apk = download(app, Site.FORUM, forumClient(app, base), current.release, current.sha);
                prefs(app).edit().remove(PREF_SNOOZE_VERSION).remove(PREF_SNOOZE_AT).apply();
                publish(new Offer(current.release, apk, current.sha));
            } catch (IOException | RuntimeException e) {
                Log.i(TAG, "4pda after login: " + e);
                MAIN.post(() -> Toast.makeText(app, R.string.update_forum_failed, Toast.LENGTH_LONG).show());
            }
        });
    }

    /** One check, on the worker thread; never throws. */
    private static Result run(@NonNull Context app) {
        try {
            OkHttpClient base = App.getOkHttpClient();
            if (base == null) {
                return Result.FAILED;
            }
            cleanUp(new File(app.getCacheDir(), DIR));
            List<Site> sites = new ArrayList<>();
            List<OkHttpClient> clients = new ArrayList<>();
            for (OkHttpClient route : AppProxy.updateRoutes(base)) {
                sites.add(Site.GITHUB);
                clients.add(route);
                sites.add(Site.MIRROR);
                clients.add(route);
            }
            sites.add(Site.FORUM);
            clients.add(forumClient(app, base));

            boolean newer = false;
            Release github = null; // its hash also checks the same version from another place
            Release login = null;
            String loginSha = null;
            for (int i = 0; i < sites.size(); i++) {
                Site site = sites.get(i);
                Release release;
                try {
                    release = latest(site, clients.get(i));
                } catch (IOException | RuntimeException e) {
                    Log.i(TAG, site + " #" + i + " does not answer: " + e);
                    continue;
                }
                if (UpdateSources.compare(release.version, BuildConfig.VERSION_NAME) <= 0) {
                    if (newer) {
                        continue;
                    }
                    Log.i(TAG, site + ": " + release.version + " is not newer");
                    offer = null;
                    return Result.LATEST;
                }
                newer = true;
                if (site == Site.GITHUB) {
                    github = release;
                }
                String sha = release.sha256 != null ? release.sha256
                        : github != null && github.version.equals(release.version) ? github.sha256 : null;
                try {
                    File apk = download(app, site, clients.get(i), release, sha);
                    publish(new Offer(release, apk, sha));
                    return Result.READY;
                } catch (LoginRequired e) {
                    Log.i(TAG, "4pda: " + e.getMessage());
                    login = release;
                    loginSha = sha;
                } catch (IOException | RuntimeException e) {
                    Log.w(TAG, site + " #" + i + " download failed: " + e);
                }
            }
            if (login != null) {
                publish(new Offer(login, null, loginSha));
                return Result.LOGIN;
            }
            return Result.FAILED;
        } catch (RuntimeException e) {
            Log.e(TAG, "Check failed", e);
            return Result.FAILED;
        }
    }

    private static void publish(@NonNull Offer next) {
        offer = next;
        MAIN.post(() -> {
            if (listener != null) {
                listener.run();
            }
        });
    }

    /** The newest version {@code site} publishes. */
    @NonNull
    private static Release latest(@NonNull Site site, @NonNull OkHttpClient client) throws IOException {
        switch (site) {
            case GITHUB: {
                String tag;
                try (Response response = execute(client.newBuilder().followRedirects(false).build(),
                        UpdateSources.GITHUB_LATEST, site)) {
                    tag = UpdateSources.githubTag(response.header("Location"));
                }
                String version = tag == null ? null : UpdateSources.versionOf(tag);
                if (version == null) {
                    throw new IOException("no release tag");
                }
                Release release = UpdateSources.githubRelease(tag, version,
                        text(client, UpdateSources.GITHUB_DOWNLOAD + tag + "/SHA256SUMS.txt", site));
                if (release == null) {
                    throw new IOException("no APK in SHA256SUMS.txt of " + tag);
                }
                return release;
            }
            case MIRROR: {
                Release release = UpdateSources.mirrorRelease(text(client, UpdateSources.MIRROR_INFO, site));
                if (release == null) {
                    throw new IOException("unreadable update.txt");
                }
                return release;
            }
            default: {
                String html = text(client, UpdateSources.FORUM_TOPIC, site);
                String[] newest = UpdateSources.forumNewest(html);
                if (newest == null) {
                    throw new IOException("no download list (a browser check?)");
                }
                String version = newest[0];
                String link = newest[1];
                String page = link.contains("findpost") ? link : "https://4pda.to/forum/index.php?showtopic=1128418";
                if (UpdateSources.compare(version, BuildConfig.VERSION_NAME) <= 0) {
                    return new Release(version, "", null, page);
                }
                String file = UpdateSources.forumFile(link, html, version);
                for (String older : UpdateSources.forumLastPages(html)) {
                    if (file != null) {
                        break;
                    }
                    file = UpdateSources.forumFile(link, text(client, older, site), version);
                }
                if (file == null) {
                    throw new IOException("no file for " + version);
                }
                return new Release(version, file, null, page);
            }
        }
    }

    /**
     * {@code release}'s APK in the cache, downloaded unless a checked copy is there. A 4pda link first gives a page
     * with the real link (valid for this session only); an answer that is not the file then means a login is needed.
     */
    @NonNull
    private static File download(@NonNull Context app, @NonNull Site site, @NonNull OkHttpClient client,
            @NonNull Release release, @Nullable String sha) throws IOException {
        File dir = new File(app.getCacheDir(), DIR);
        File apk = new File(dir, "AudioBooks-" + release.version + ".apk");
        if (apk.isFile()) {
            try {
                verify(app, apk, sha, digest(new FileInputStream(apk), null));
                return apk;
            } catch (IOException e) {
                Log.i(TAG, "Cached " + apk.getName() + " dropped: " + e.getMessage());
                apk.delete();
            }
        }
        if (!dir.isDirectory() && !dir.mkdirs()) {
            throw new IOException("no folder " + dir);
        }
        File part = new File(dir, apk.getName() + ".part");
        String url = release.apkUrl;
        String hash = null;
        for (int hop = 0; hash == null; hop++) {
            Call call = client.newCall(request(url, site));
            call.timeout().timeout(10, TimeUnit.MINUTES);
            try (Response response = call.execute()) {
                ResponseBody body = response.body();
                if (response.isSuccessful() && isZip(response)) {
                    hash = digest(body.byteStream(), part);
                } else if (site != Site.FORUM) {
                    throw new IOException("HTTP " + response.code() + ", not an APK: " + url);
                } else {
                    String next = hop == 0 && response.isSuccessful() ? UpdateSources.forumAttach(body.string()) : null;
                    if (next == null) {
                        throw new LoginRequired("HTTP " + response.code() + " instead of the file " + url);
                    }
                    url = next;
                }
            }
        }
        try {
            verify(app, part, sha, hash);
        } catch (IOException e) {
            part.delete();
            throw e;
        }
        if (!part.renameTo(apk)) {
            throw new IOException("cannot rename " + part);
        }
        Log.i(TAG, site + ": " + apk.getName() + " ready");
        return apk;
    }

    /**
     * Throws unless {@code apk} is a newer version of this app signed as it is (the installer would refuse anything
     * else) and, when {@code sha} is known, has that SHA-256.
     */
    @SuppressWarnings("deprecation")
    private static void verify(@NonNull Context app, @NonNull File apk, @Nullable String sha, @NonNull String hash)
            throws IOException {
        if (sha != null && !sha.equals(hash)) {
            throw new IOException("SHA-256 " + hash + " instead of " + sha);
        }
        PackageManager pm = app.getPackageManager();
        int flags = Build.VERSION.SDK_INT >= 28 ? PackageManager.GET_SIGNING_CERTIFICATES : PackageManager.GET_SIGNATURES;
        PackageInfo archive = pm.getPackageArchiveInfo(apk.getPath(), flags);
        PackageInfo installed;
        try {
            installed = pm.getPackageInfo(app.getPackageName(), flags);
        } catch (PackageManager.NameNotFoundException e) {
            throw new IOException(e);
        }
        if (archive == null || !app.getPackageName().equals(archive.packageName)) {
            throw new IOException("not this app's package");
        }
        if (PackageInfoCompat.getLongVersionCode(archive) <= PackageInfoCompat.getLongVersionCode(installed)) {
            throw new IOException("version code " + PackageInfoCompat.getLongVersionCode(archive) + " is not newer");
        }
        Signature[] theirs = signers(archive);
        if (theirs == null || theirs.length == 0 || !Arrays.equals(theirs, signers(installed))) {
            throw new IOException("signed with another key");
        }
    }

    @Nullable
    @SuppressWarnings("deprecation")
    private static Signature[] signers(@NonNull PackageInfo info) {
        if (Build.VERSION.SDK_INT >= 28) {
            return info.signingInfo == null ? null : info.signingInfo.getApkContentsSigners();
        }
        return info.signatures;
    }

    /** The SHA-256 of {@code in} (closed), copied to {@code to} when given, at most {@link #MAX_APK_BYTES}. */
    @NonNull
    private static String digest(@NonNull InputStream in, @Nullable File to) throws IOException {
        MessageDigest sha;
        try {
            sha = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
        try (InputStream input = in; OutputStream out = to == null ? null : new FileOutputStream(to)) {
            byte[] buffer = new byte[64 * 1024];
            long total = 0;
            for (int n; (n = input.read(buffer)) != -1; ) {
                total += n;
                if (total > MAX_APK_BYTES) {
                    throw new IOException("larger than " + MAX_APK_BYTES + " bytes");
                }
                sha.update(buffer, 0, n);
                if (out != null) {
                    out.write(buffer, 0, n);
                }
            }
        }
        return String.format("%064x", new BigInteger(1, sha.digest()));
    }

    /** An APK is a ZIP: it starts with "PK\3\4". */
    private static boolean isZip(@NonNull Response response) throws IOException {
        byte[] head = response.peekBody(4).bytes();
        return head.length == 4 && head[0] == 'P' && head[1] == 'K' && head[2] == 3 && head[3] == 4;
    }

    @NonNull
    private static String text(@NonNull OkHttpClient client, @NonNull String url, @NonNull Site site)
            throws IOException {
        try (Response response = execute(client, url, site)) {
            if (!response.isSuccessful()) {
                throw new IOException("HTTP " + response.code() + " " + url);
            }
            ResponseBody body = response.body();
            MediaType type = body.contentType();
            // 4pda is windows-1251 and does not always say so in the header
            Charset fallback = Charset.forName(site == Site.FORUM ? "windows-1251" : "UTF-8");
            return new String(body.bytes(), type == null ? fallback : type.charset(fallback));
        }
    }

    /** A metadata request: it fails after 30 s, so a place that does not answer costs no more. */
    @NonNull
    private static Response execute(@NonNull OkHttpClient client, @NonNull String url, @NonNull Site site)
            throws IOException {
        Call call = client.newCall(request(url, site));
        call.timeout().timeout(30, TimeUnit.SECONDS);
        Response response = call.execute();
        if (response.code() >= 400) {
            response.close();
            throw new IOException("HTTP " + response.code() + " " + url);
        }
        return response;
    }

    @NonNull
    private static Request request(@NonNull String url, @NonNull Site site) {
        Request.Builder builder = new Request.Builder().url(url);
        if (site == Site.FORUM) {
            builder.header("Referer", UpdateSources.FORUM_TOPIC);
        }
        return builder.build();
    }

    /**
     * The client for 4pda: once the user has used the forum screen, with the WebView's cookies (the login and the
     * site's browser check) and its user agent, which that check is bound to.
     */
    @NonNull
    private static OkHttpClient forumClient(@NonNull Context app, @NonNull OkHttpClient base) {
        String agent = prefs(app).getString(PREF_FORUM_AGENT, null);
        if (agent == null) {
            return base;
        }
        return base.newBuilder()
                .cookieJar(new WebViewCookies())
                .addInterceptor(chain -> chain.proceed(chain.request().newBuilder().header("User-Agent", agent).build()))
                .build();
    }

    /** The WebView's cookie store, per request URL; empty where the device has no WebView. */
    private static final class WebViewCookies implements CookieJar {
        @Override
        public void saveFromResponse(@NonNull HttpUrl url, @NonNull List<Cookie> cookies) {
            try {
                CookieManager manager = CookieManager.getInstance();
                for (Cookie cookie : cookies) {
                    manager.setCookie(url.toString(), cookie.toString(), null);
                }
            } catch (RuntimeException e) {
                Log.w(TAG, "No WebView cookies", e);
            }
        }

        @NonNull
        @Override
        public List<Cookie> loadForRequest(@NonNull HttpUrl url) {
            List<Cookie> cookies = new ArrayList<>();
            try {
                String header = CookieManager.getInstance().getCookie(url.toString());
                if (header != null) {
                    for (String pair : header.split(";")) {
                        Cookie cookie = Cookie.parse(url, pair.trim());
                        if (cookie != null) {
                            cookies.add(cookie);
                        }
                    }
                }
            } catch (RuntimeException e) {
                Log.w(TAG, "No WebView cookies", e);
            }
            return cookies;
        }
    }

    /** Leaves only APKs newer than this app: half downloads and installed versions go. */
    private static void cleanUp(@NonNull File dir) {
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            String name = file.getName();
            boolean newer = name.startsWith("AudioBooks-") && name.endsWith(".apk")
                    && UpdateSources.compare(name.substring(11, name.length() - 4), BuildConfig.VERSION_NAME) > 0;
            if (!newer && !file.delete()) {
                Log.w(TAG, "Cannot delete " + file);
            }
        }
    }

    private static SharedPreferences prefs(@NonNull Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context);
    }
}
