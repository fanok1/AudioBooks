package com.fanok.audiobooks.search;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.fanok.audiobooks.CookesExeption;
import com.fanok.audiobooks.pojo.BookPOJO;

import io.reactivex.schedulers.Schedulers;

import org.junit.Test;

import java.net.SocketTimeoutException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/** A source is up when its first catalog page lists books; every other outcome keeps its own status. */
public class SourceProbeTest {

    private final AtomicLong clock = new AtomicLong();

    private SourceProbe.Result probe(SourceProbe.Loader loader) {
        return SourceProbe.probe(Provider.UKNIG, provider -> {
            clock.addAndGet(250_000_000L); // the check takes 250 ms
            return loader.load(provider);
        }, clock::get);
    }

    private static ProviderPage<BookPOJO> books(int count) {
        BookPOJO book = new BookPOJO();
        book.setUrl("https://uknig.com/books/1");
        book.setName("Книга");
        return ProviderPage.of(Collections.nCopies(count, book), true);
    }

    @Test
    public void aPageOfBooksIsUp() {
        SourceProbe.Result result = probe(p -> books(3));
        assertTrue(result.isUp());
        assertSame(Provider.UKNIG, result.provider);
        assertEquals(250, result.millis);
    }

    @Test
    public void aPageWithoutBooksIsEmptyNotUp() {
        SourceProbe.Result result = probe(p -> ProviderPage.of(Collections.<BookPOJO>emptyList(), false, 5));
        assertFalse(result.isUp());
        assertEquals(ProviderPage.Status.EMPTY, result.status);
    }

    @Test
    public void failuresKeepTheirKind() {
        assertEquals(ProviderPage.Status.BLOCKED, probe(p -> {
            throw new CookesExeption("https://uknig.com/");
        }).status);
        assertEquals(ProviderPage.Status.TIMEOUT, probe(p -> {
            throw new SocketTimeoutException("read timed out");
        }).status);
        SourceProbe.Result error = probe(p -> {
            throw new java.io.IOException("refused");
        });
        assertEquals(ProviderPage.Status.ERROR, error.status);
        assertTrue(error.message.contains("refused"));
    }

    @Test
    public void allSourcesAreCheckedInTheirOrder() {
        List<Provider> providers = Arrays.asList(Provider.AKNIGA, Provider.UKNIG);
        List<SourceProbe.Result> results = SourceProbe.probeAll(providers, 2, Schedulers.io(),
                p -> p == Provider.UKNIG ? books(1) : ProviderPage.<BookPOJO>empty(),
                SourceProbe.TIMEOUT_SECONDS).blockingGet();
        assertEquals(2, results.size());
        assertSame(Provider.AKNIGA, results.get(0).provider);
        assertEquals(ProviderPage.Status.EMPTY, results.get(0).status);
        assertSame(Provider.UKNIG, results.get(1).provider);
        assertTrue(results.get(1).isUp());
    }

    /** A check that hangs on the only io thread still times out: the timer runs elsewhere. */
    @Test
    public void aHangingCheckTimesOutEvenOnASingleThread() {
        long start = System.nanoTime();
        List<SourceProbe.Result> results = SourceProbe.probeAll(Collections.singletonList(Provider.AUDIOBOO), 1,
                Schedulers.single(), p -> {
                    Thread.sleep(10_000);
                    return books(1);
                }, 1).blockingGet();
        assertEquals(ProviderPage.Status.TIMEOUT, results.get(0).status);
        assertTrue("timed out after about a second", System.nanoTime() - start < 5_000_000_000L);
    }
}
