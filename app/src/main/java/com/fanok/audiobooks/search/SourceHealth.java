package com.fanok.audiobooks.search;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.schedulers.Schedulers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * The last availability check of every active site ({@link SourceProbe}), kept in the preferences: run by hand from the
 * sources dialog, or by itself at most once a day, some time after the app starts and only on an unmetered network, so it
 * never competes with the user's first pages or costs mobile data.
 */
public final class SourceHealth {

    static final String PREF_RESULTS = "source_health";
    static final String PREF_CHECKED_AT = "source_health_at";
    static final long DAY_MS = TimeUnit.DAYS.toMillis(1);
    /** The automatic check waits this long after the start: the user's own pages come first. */
    static final long START_DELAY_SECONDS = 30;
    /** Sites checked at once by the automatic check. */
    static final int BACKGROUND_PARALLEL = 2;

    private SourceHealth() {
    }

    /** Checks every active site ({@link Sources}) through its current route and keeps the results. */
    @NonNull
    public static Single<List<SourceProbe.Result>> check(@NonNull SharedPreferences prefs, int parallel) {
        return check(prefs, Sources.active(), parallel).toList();
    }

    /**
     * Checks {@code providers} through their current routes, each result kept as it comes in the order of
     * {@code providers}; a check that covers every active site is also the time of the last check.
     */
    @NonNull
    public static Observable<SourceProbe.Result> check(@NonNull SharedPreferences prefs,
            @NonNull List<Provider> providers, int parallel) {
        List<Provider> sites = new ArrayList<>(providers);
        boolean all = sites.containsAll(Sources.active());
        Observable<SourceProbe.Result> each = Observable.defer(() -> {
            Map<Provider, Integer> mine = startChecks(sites);
            return SourceProbe.probeEach(sites, parallel, Schedulers.io())
                    .doOnNext(result -> keep(prefs, result, mine.get(result.provider)));
        });
        return all ? each.doOnComplete(() -> prefs.edit().putLong(PREF_CHECKED_AT, System.currentTimeMillis()).apply())
                : each;
    }

    /** The latest check of each site: an older one that ends later (a route changed meanwhile) is not kept. */
    private static final Map<Provider, Integer> CHECKS = new EnumMap<>(Provider.class);

    /** Numbers a new check of each of {@code sites}; returns their numbers. */
    private static synchronized Map<Provider, Integer> startChecks(@NonNull List<Provider> sites) {
        Map<Provider, Integer> mine = new EnumMap<>(Provider.class);
        for (Provider site : sites) {
            Integer last = CHECKS.get(site);
            int next = last == null ? 1 : last + 1;
            CHECKS.put(site, next);
            mine.put(site, next);
        }
        return mine;
    }

    /**
     * Replaces the kept result of {@code result}'s site, unless a newer check of the site started since {@code check};
     * results of several sites arrive on several threads.
     */
    private static synchronized void keep(@NonNull SharedPreferences prefs, @NonNull SourceProbe.Result result,
            @Nullable Integer check) {
        if (check == null || !check.equals(CHECKS.get(result.provider))) {
            return;
        }
        Set<String> entries = new HashSet<>(prefs.getStringSet(PREF_RESULTS, Collections.<String>emptySet()));
        String prefix = result.provider.prefValue + "=";
        Iterator<String> it = entries.iterator();
        while (it.hasNext()) {
            if (it.next().startsWith(prefix)) {
                it.remove();
            }
        }
        entries.add(encode(result));
        prefs.edit().putStringSet(PREF_RESULTS, entries).apply();
    }

    /** Starts the automatic check when the last one is a day old and the network is unmetered; never fails. */
    @SuppressLint("CheckResult") // process-wide and short: it ends by itself, nothing to dispose
    public static void checkIfDue(@NonNull Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        if (!due(prefs.getLong(PREF_CHECKED_AT, 0), System.currentTimeMillis()) || !unmetered(context)) {
            return;
        }
        Single.timer(START_DELAY_SECONDS, TimeUnit.SECONDS)
                .flatMap(tick -> unmetered(context) ? check(prefs, BACKGROUND_PARALLEL)
                        : Single.just(Collections.<SourceProbe.Result>emptyList()))
                .subscribe(results -> { }, e -> Log.w("SourceHealth", "Automatic source check failed", e));
    }

    /** The kept result of each site (no message); a site never checked is absent. */
    @NonNull
    public static Map<Provider, SourceProbe.Result> last(@NonNull SharedPreferences prefs) {
        return decode(prefs.getStringSet(PREF_RESULTS, Collections.<String>emptySet()));
    }

    /** When the last check ended (epoch millis); 0 when there was none. */
    public static long lastCheckedAt(@NonNull SharedPreferences prefs) {
        return prefs.getLong(PREF_CHECKED_AT, 0);
    }

    /** Pure: a day has passed since {@code lastAt}, or the clock went back. */
    static boolean due(long lastAt, long now) {
        return now - lastAt >= DAY_MS || now < lastAt;
    }

    /** Pure: one {@code prefValue=STATUS=millis} entry per site. */
    @NonNull
    static Set<String> encode(@NonNull List<SourceProbe.Result> results) {
        Set<String> entries = new HashSet<>();
        for (SourceProbe.Result result : results) {
            entries.add(encode(result));
        }
        return entries;
    }

    @NonNull
    private static String encode(@NonNull SourceProbe.Result result) {
        return result.provider.prefValue + "=" + result.status.name() + "=" + result.millis;
    }

    /**
     * Pure: {@link #encode} read back; an entry of an unknown site or status is skipped, one without the time (an
     * older version's) has 0.
     */
    @NonNull
    static Map<Provider, SourceProbe.Result> decode(@NonNull Set<String> entries) {
        Map<Provider, SourceProbe.Result> results = new EnumMap<>(Provider.class);
        for (String entry : entries) {
            String[] parts = entry.split("=");
            if (parts.length < 2) {
                continue;
            }
            for (Provider provider : Provider.values()) {
                if (provider.prefValue.equals(parts[0])) {
                    try {
                        long millis = parts.length > 2 ? Long.parseLong(parts[2]) : 0;
                        results.put(provider, new SourceProbe.Result(provider,
                                ProviderPage.Status.valueOf(parts[1]), millis, null));
                    } catch (IllegalArgumentException ignored) {
                        // a status of an older version: unknown now
                    }
                }
            }
        }
        return results;
    }

    private static boolean unmetered(@NonNull Context context) {
        ConnectivityManager connectivity = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkCapabilities network = connectivity == null ? null
                : connectivity.getNetworkCapabilities(connectivity.getActiveNetwork());
        return network != null && network.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                && network.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED);
    }
}
