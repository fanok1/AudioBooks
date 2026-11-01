package com.fanok.audiobooks.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.Test;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** One opening of a book fetches its page once: the description and the chapter list share it. */
public class BookPagesTest {

    private static final String PAGE = "<html><body><h1>Книга</h1></body></html>";

    /** Counts its fetches; each URL of a test is new, so the shared cache of other tests does not interfere. */
    private static final class Counting implements BookPages.Fetch {
        final AtomicInteger calls = new AtomicInteger();

        @Override
        public Document get(String url) {
            calls.incrementAndGet();
            return Jsoup.parse(PAGE, url);
        }
    }

    @Test
    public void aSecondReaderWithinTheTtlGetsItsOwnCopyOfTheSamePage() throws IOException {
        Counting fetch = new Counting();
        String url = "https://example.org/book-ttl";
        Document first = BookPages.get(url, fetch, 1_000);
        Document second = BookPages.get(url, fetch, 1_000 + BookPages.TTL_MS - 1);
        assertEquals(1, fetch.calls.get());
        assertNotSame(first, second);
        first.select("h1").remove(); // a parser changing its copy changes nobody else's
        assertEquals("Книга", second.select("h1").text());
        BookPages.get(url, fetch, 1_000 + BookPages.TTL_MS);
        assertEquals("an old page is fetched again", 2, fetch.calls.get());
    }

    @Test
    public void forgetMakesTheNextReaderFetch() throws IOException {
        Counting fetch = new Counting();
        String url = "https://example.org/book-forget";
        BookPages.get(url, fetch, 5_000);
        BookPages.forget(url);
        BookPages.get(url, fetch, 5_001);
        assertEquals(2, fetch.calls.get());
    }

    @Test
    public void aFailureIsNotKept() throws IOException {
        String url = "https://example.org/book-fail";
        AtomicInteger calls = new AtomicInteger();
        BookPages.Fetch flaky = u -> {
            if (calls.incrementAndGet() == 1) {
                throw new IOException("refused");
            }
            return Jsoup.parse(PAGE, u);
        };
        try {
            BookPages.get(url, flaky, 7_000);
            fail();
        } catch (IOException expected) {
            assertEquals("refused", expected.getMessage());
        }
        assertEquals("Книга", BookPages.get(url, flaky, 7_001).select("h1").text());
        assertEquals(2, calls.get());
    }

    @Test
    public void readersAtTheSameTimeShareOneFetch() throws Exception {
        String url = "https://example.org/book-together";
        AtomicInteger calls = new AtomicInteger();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        BookPages.Fetch slow = u -> {
            calls.incrementAndGet();
            started.countDown();
            try {
                release.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                throw new IOException(e);
            }
            return Jsoup.parse(PAGE, u);
        };
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Document> description = pool.submit(() -> BookPages.get(url, slow));
            assertTrue(started.await(5, TimeUnit.SECONDS));
            Future<Document> chapters = pool.submit(() -> BookPages.get(url, slow));
            Thread.sleep(100); // the chapter list now waits for the description's fetch
            release.countDown();
            assertEquals("Книга", description.get(5, TimeUnit.SECONDS).select("h1").text());
            assertEquals("Книга", chapters.get(5, TimeUnit.SECONDS).select("h1").text());
            assertEquals(1, calls.get());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    public void aReaderWhoseSharedFetchFailedTriesItself() throws Exception {
        String url = "https://example.org/book-retry";
        AtomicInteger calls = new AtomicInteger();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        BookPages.Fetch firstFails = u -> {
            if (calls.incrementAndGet() == 1) {
                started.countDown();
                try {
                    release.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    throw new IOException(e);
                }
                throw new IOException("cancelled");
            }
            return Jsoup.parse(PAGE, u);
        };
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Document> leader = pool.submit(() -> BookPages.get(url, firstFails));
            assertTrue(started.await(5, TimeUnit.SECONDS));
            Future<Document> joined = pool.submit(() -> BookPages.get(url, firstFails));
            Thread.sleep(100);
            release.countDown();
            try {
                leader.get(5, TimeUnit.SECONDS);
                fail();
            } catch (java.util.concurrent.ExecutionException expected) {
                assertTrue(expected.getCause() instanceof IOException);
            }
            assertEquals("Книга", joined.get(5, TimeUnit.SECONDS).select("h1").text());
            assertEquals(2, calls.get());
        } finally {
            pool.shutdownNow();
        }
    }
}
