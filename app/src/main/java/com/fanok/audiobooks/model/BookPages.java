package com.fanok.audiobooks.model;

import androidx.annotation.NonNull;

import org.jsoup.nodes.Document;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;

/**
 * The book page shared by one opening of a book: the description (with its other books) and the chapter list are
 * loaded side by side and need the same page, which is fetched once. A fetch in flight is joined; a page that arrived is
 * reused for {@link #TTL_MS}; a failed fetch is never kept. Every caller gets its own copy, as parsers may change it. A
 * chapter refresh after a refused media address {@link #forget}s the page first, so it always reads a fresh one.
 */
public final class BookPages {

    /** Long enough for the screens of one opening, short enough for any signed media address in the page. */
    static final long TTL_MS = 60_000;
    private static final int MAX_PAGES = 4;

    /** Fetches a page: the site's own {@code fetch}. */
    public interface Fetch {
        Document get(String url) throws IOException;
    }

    private static final class Page {
        final FutureTask<Document> task;
        final long at;

        Page(FutureTask<Document> task, long at) {
            this.task = task;
            this.at = at;
        }
    }

    private static final Map<String, Page> PAGES = new LinkedHashMap<String, Page>(8, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Page> eldest) {
            return size() > MAX_PAGES;
        }
    };

    private BookPages() {
    }

    /** A copy of the page at {@code url}: joined, reused or fetched with {@code fetch}. */
    @NonNull
    public static Document get(@NonNull String url, @NonNull Fetch fetch) throws IOException {
        return get(url, fetch, System.currentTimeMillis());
    }

    @NonNull
    static Document get(@NonNull String url, @NonNull Fetch fetch, long now) throws IOException {
        FutureTask<Document> task;
        boolean mine = false;
        synchronized (PAGES) {
            Page entry = PAGES.get(url);
            if (entry == null || now - entry.at >= TTL_MS || now < entry.at) {
                task = new FutureTask<>(() -> fetch.get(url));
                PAGES.put(url, new Page(task, now));
                mine = true;
            } else {
                task = entry.task;
            }
        }
        if (mine) {
            task.run();
        }
        try {
            return task.get().clone();
        } catch (ExecutionException e) {
            drop(url, task);
            if (!mine) {
                return fetch.get(url); // another caller's fetch failed (or was cancelled): this one tries itself
            }
            Throwable cause = e.getCause();
            if (cause instanceof IOException) {
                throw (IOException) cause;
            }
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            throw new IOException(cause);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new InterruptedIOException("interrupted waiting for " + url);
        }
    }

    /** The next {@link #get} of {@code url} fetches it again. */
    public static void forget(@NonNull String url) {
        synchronized (PAGES) {
            PAGES.remove(url);
        }
    }

    private static void drop(String url, FutureTask<Document> task) {
        synchronized (PAGES) {
            Page entry = PAGES.get(url);
            if (entry != null && entry.task == task) {
                PAGES.remove(url);
            }
        }
    }
}
