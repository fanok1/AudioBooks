package com.fanok.audiobooks.search;

import io.reactivex.Observable;
import io.reactivex.Scheduler;
import io.reactivex.Single;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.functions.Function;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * One generation-scoped search over several sources. Every {@link #search} starts a new generation:
 * the previous run is disposed and its late answers are ignored. Sources run in parallel, each bounded
 * by a timeout and reduced to a {@link ProviderPage}; results are merged (deduplicated by {@code key})
 * and published as they arrive. Per-source page cursors let {@link #loadNext} continue only the sources
 * that still have pages. All methods and callbacks run on the {@code main} scheduler.
 */
public final class SearchSession<T> {

    public interface Source<T> {
        String name();

        Single<ProviderPage<T>> load(String query, int page);

        /** How long this source may take when that is longer than the session's limit (a slow site); 0: the session's. */
        default long timeoutSeconds() {
            return 0;
        }
    }

    public interface Listener<T> {
        /** New items arrived: the merged list so far, a copy, in order of arrival. */
        void onItems(List<T> items);

        /** Every source of the pass has answered (or none was enabled). */
        void onFinished(List<T> items, int failedSources);
    }

    private static final class Arrival<T> {
        final Source<T> source;
        final ProviderPage<T> page;

        Arrival(Source<T> source, ProviderPage<T> page) {
            this.source = source;
            this.page = page;
        }
    }

    private final List<Source<T>> sources;
    private final Function<T, String> key;
    private final Scheduler io;
    private final Scheduler main;
    private final long timeoutSeconds;
    /**
     * Sources loading at once; the rest start as these answer. Today's number of sites, so their search runs as before;
     * it bounds the fan-out on a phone's network once there are more.
     */
    static final int MAX_PARALLEL = 13;
    private final Listener<T> listener;

    private final CompositeDisposable running = new CompositeDisposable();
    private final ArrayList<T> items = new ArrayList<>();
    private final HashSet<String> seen = new HashSet<>();
    /** Next page per source; a source leaves the map when it has no more pages. */
    private final LinkedHashMap<Source<T>, Integer> nextPage = new LinkedHashMap<>();
    private final LinkedHashMap<Source<T>, String> queryOf = new LinkedHashMap<>();
    private final LinkedHashMap<Source<T>, ProviderPage.Status> statuses = new LinkedHashMap<>();
    private final LinkedHashSet<Source<T>> failed = new LinkedHashSet<>();
    private int generation;
    private int pending;
    private String query = "";
    private boolean caseRetried;

    public SearchSession(List<Source<T>> sources, Function<T, String> key, Scheduler io, Scheduler main,
            long timeoutSeconds, Listener<T> listener) {
        this.sources = new ArrayList<>(sources);
        this.key = key;
        this.io = io;
        this.main = main;
        this.timeoutSeconds = timeoutSeconds;
        this.listener = listener;
    }

    /** Starts a new generation for {@code q}: page 1 of every source, in parallel. */
    public void search(String q) {
        generation++;
        running.clear();
        pending = 0;
        items.clear();
        seen.clear();
        statuses.clear();
        failed.clear();
        nextPage.clear();
        queryOf.clear();
        query = q;
        caseRetried = false;
        for (Source<T> s : sources) {
            nextPage.put(s, 1);
            queryOf.put(s, q);
        }
        run(new ArrayList<>(sources));
    }

    /** Loads the next page of every source that still has one; false when nothing was started. */
    public boolean loadNext() {
        if (pending > 0) {
            return false;
        }
        ArrayList<Source<T>> batch = new ArrayList<>();
        for (Source<T> s : nextPage.keySet()) {
            if (!failed.contains(s)) {
                batch.add(s);
            }
        }
        if (batch.isEmpty()) {
            return false;
        }
        run(batch);
        return true;
    }

    /** Re-requests the same page from the sources whose last answer was a failure. */
    public boolean retryFailed() {
        if (pending > 0 || failed.isEmpty()) {
            return false;
        }
        ArrayList<Source<T>> batch = new ArrayList<>(failed);
        failed.clear();
        run(batch);
        return true;
    }

    public boolean isRunning() {
        return pending > 0;
    }

    public boolean hasMore() {
        for (Source<T> s : nextPage.keySet()) {
            if (!failed.contains(s)) {
                return true;
            }
        }
        return false;
    }

    public int failedCount() {
        return failed.size();
    }

    /** The last status of every source that has answered in this generation, by source name. */
    public Map<String, ProviderPage.Status> statuses() {
        LinkedHashMap<String, ProviderPage.Status> out = new LinkedHashMap<>();
        for (Map.Entry<Source<T>, ProviderPage.Status> e : statuses.entrySet()) {
            out.put(e.getKey().name(), e.getValue());
        }
        return out;
    }

    public List<T> items() {
        return new ArrayList<>(items);
    }

    /** Cancels everything; later answers are dropped. */
    public void dispose() {
        generation++;
        pending = 0;
        running.clear();
    }

    private void run(List<Source<T>> batch) {
        final int gen = generation;
        if (batch.isEmpty()) {
            listener.onFinished(items(), failed.size());
            return;
        }
        pending = batch.size();
        List<Observable<Arrival<T>>> calls = new ArrayList<>(batch.size());
        for (final Source<T> s : batch) {
            final Integer cursor = nextPage.get(s);
            final int page = cursor == null ? 1 : cursor;
            final String q = queryOf.get(s) == null ? query : queryOf.get(s);
            calls.add(Single.defer(() -> s.load(q, page))
                    .subscribeOn(io)
                    .timeout(Math.max(timeoutSeconds, s.timeoutSeconds()), TimeUnit.SECONDS, io)
                    .onErrorReturn(e -> ProviderPage.<T>failed(e))
                    .map(p -> new Arrival<>(s, p))
                    .toObservable());
        }
        // merge subscribes lazily: a source's timeout starts when it gets a slot, not while it waits for one
        running.add(Observable.merge(calls, MAX_PARALLEL)
                .observeOn(main)
                .subscribe(a -> {
                    if (gen == generation) {
                        apply(a.source, a.page);
                    }
                }, e -> {
                    if (gen == generation) {
                        pending = 0;
                        finish();
                    }
                }, () -> {
                    if (gen == generation) {
                        pending = 0;
                        finish();
                    }
                }));
    }

    private void apply(Source<T> s, ProviderPage<T> page) {
        pending--;
        statuses.put(s, page.status);
        boolean added = false;
        for (T item : page.items) {
            String k;
            try {
                k = key.apply(item);
            } catch (Exception e) {
                k = null;
            }
            if (k == null || seen.add(k)) {
                items.add(item);
                added = true;
            }
        }
        if (page.isFailure()) {
            failed.add(s); // the cursor stays, so a retry asks for the same page
        } else {
            failed.remove(s);
            // a page of nothing but seen items ends the source, as in listings: some providers
            // repeat their last page for any page number past the end
            if (page.hasMore && (added || page.items.isEmpty())) {
                nextPage.put(s, nextPage.get(s) == null ? 2 : nextPage.get(s) + 1);
            } else {
                nextPage.remove(s);
            }
        }
        if (added) {
            listener.onItems(items());
        }
    }

    private void finish() {
        if (!caseRetried) {
            // one extra pass, only for sources that found nothing, with the other letter case (SRCH-10)
            caseRetried = true;
            String variant = caseVariant(query);
            if (variant != null) {
                ArrayList<Source<T>> batch = new ArrayList<>();
                for (Source<T> s : sources) {
                    if (statuses.get(s) == ProviderPage.Status.EMPTY) {
                        batch.add(s);
                        queryOf.put(s, variant);
                        nextPage.put(s, 1);
                    }
                }
                if (!batch.isEmpty()) {
                    run(batch);
                    return;
                }
            }
        }
        listener.onFinished(items(), failed.size());
    }

    /** The query in the other letter case: lower when it has capitals, otherwise capitalised; null when identical. */
    static String caseVariant(String q) {
        if (q == null || q.isEmpty()) {
            return null;
        }
        String lower = q.toLowerCase(Locale.ROOT);
        if (!lower.equals(q)) {
            return lower;
        }
        String capitalised = q.substring(0, 1).toUpperCase(Locale.ROOT) + q.substring(1);
        return capitalised.equals(q) ? null : capitalised;
    }
}
