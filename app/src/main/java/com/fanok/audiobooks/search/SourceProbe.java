package com.fanok.audiobooks.search;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.fanok.audiobooks.pojo.BookPOJO;
import com.fanok.audiobooks.source.SourceParser;

import io.reactivex.Observable;
import io.reactivex.Scheduler;
import io.reactivex.Single;
import io.reactivex.schedulers.Schedulers;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

/**
 * The live check of a source ("can the app use it from here?"): the first page of its catalog, fetched and parsed
 * exactly as the catalog does it — the adapter's own request (GET or POST, headers, cookies) through the normal proxy
 * route — so "up" means books can be listed, not merely that a home page answered. The statuses are
 * {@link ProviderPage}'s; a page with no book at all is {@link ProviderPage.Status#EMPTY}: the site changed, or it shows
 * a wall that is no known challenge.
 */
public final class SourceProbe {

    /** The bound of one check, queueing excluded: a hanging site is "timeout", not a stuck list. */
    public static final long TIMEOUT_SECONDS = 15;

    /** The outcome of one check. */
    public static final class Result {
        @NonNull
        public final Provider provider;
        @NonNull
        public final ProviderPage.Status status;
        /** How long the check took. */
        public final long millis;
        /** Diagnostic text of a failure; null otherwise. */
        @Nullable
        public final String message;

        public Result(@NonNull Provider provider, @NonNull ProviderPage.Status status, long millis,
                @Nullable String message) {
            this.provider = provider;
            this.status = status;
            this.millis = millis;
            this.message = message;
        }

        public boolean isUp() {
            return status == ProviderPage.Status.OK;
        }
    }

    /** Loads the page a check reads: {@link #firstPage} in production, a fake in tests. */
    interface Loader {
        ProviderPage<BookPOJO> load(Provider provider) throws Exception;
    }

    private SourceProbe() {
    }

    /** Checks {@code provider} now, on the calling thread (a network call: never the main thread). */
    @NonNull
    public static Result probe(@NonNull Provider provider) {
        return probe(provider, SourceProbe::firstPage, System::nanoTime);
    }

    @NonNull
    static Result probe(@NonNull Provider provider, @NonNull Loader loader, @NonNull LongSupplier nanos) {
        long start = nanos.getAsLong();
        ProviderPage<BookPOJO> page;
        try {
            page = loader.load(provider);
        } catch (Exception e) {
            page = ProviderPage.failed(e);
        }
        long millis = (nanos.getAsLong() - start) / 1_000_000;
        ProviderPage.Status status = page.status == ProviderPage.Status.OK && page.items.isEmpty()
                ? ProviderPage.Status.EMPTY : page.status;
        return new Result(provider, status, millis, page.message);
    }

    /** The first page of the source's catalog, as the catalog screen opens it. */
    static ProviderPage<BookPOJO> firstPage(@NonNull Provider provider) throws Exception {
        SourceParser parser = provider.parser;
        if (provider.index == null) {
            throw new IllegalStateException(provider + " has no catalog to check");
        }
        String url = parser.pageUrl(provider.index, 1);
        return parser.loadListing(url, 1);
    }

    /**
     * Checks {@code providers}, at most {@code parallel} at once, each cut off after {@link #TIMEOUT_SECONDS} from its
     * own start; the results come in the order of {@code providers}.
     */
    @NonNull
    public static Single<List<Result>> probeAll(@NonNull List<Provider> providers, int parallel,
            @NonNull Scheduler io) {
        return probeAll(providers, parallel, io, SourceProbe::firstPage, TIMEOUT_SECONDS);
    }

    @NonNull
    static Single<List<Result>> probeAll(@NonNull List<Provider> providers, int parallel, @NonNull Scheduler io,
            @NonNull Loader loader, long timeoutSeconds) {
        return probeEach(providers, parallel, io, loader, timeoutSeconds).toList();
    }

    /** {@link #probeAll}, each result as soon as its turn in the order of {@code providers} comes. */
    @NonNull
    public static Observable<Result> probeEach(@NonNull List<Provider> providers, int parallel,
            @NonNull Scheduler io) {
        return probeEach(providers, parallel, io, SourceProbe::firstPage, TIMEOUT_SECONDS);
    }

    @NonNull
    static Observable<Result> probeEach(@NonNull List<Provider> providers, int parallel, @NonNull Scheduler io,
            @NonNull Loader loader, long timeoutSeconds) {
        return Observable.fromIterable(providers)
                .concatMapEager(provider -> Single.fromCallable(() -> probe(provider, loader, System::nanoTime))
                        .subscribeOn(io)
                        // the timer is not {@code io}: a busy io thread must not hold back the timeout it bounds
                        .timeout(timeoutSeconds, TimeUnit.SECONDS, Schedulers.computation(),
                                Single.just(new Result(provider, ProviderPage.Status.TIMEOUT,
                                        TimeUnit.SECONDS.toMillis(timeoutSeconds),
                                        "no answer in " + timeoutSeconds + " s")))
                        .toObservable(), parallel, 1);
    }
}
