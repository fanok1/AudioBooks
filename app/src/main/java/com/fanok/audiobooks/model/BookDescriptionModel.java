package com.fanok.audiobooks.model;

import androidx.annotation.OptIn;
import androidx.media3.common.util.UnstableApi;

import com.fanok.audiobooks.App;
import com.fanok.audiobooks.Url;
import com.fanok.audiobooks.interface_pacatge.book_content.DescriptionModel;
import com.fanok.audiobooks.net.SiteHttp;
import com.fanok.audiobooks.pojo.BookPOJO;
import com.fanok.audiobooks.pojo.DescriptionPOJO;
import com.fanok.audiobooks.search.Provider;
import com.fanok.audiobooks.source.SourceParser;
import io.reactivex.Maybe;
import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.functions.Function;
import java.io.IOException;
import java.util.ArrayList;

import org.jsoup.nodes.Document;

/**
 * Book page of any source: downloaded once per model through {@link SiteHttp} and shared by
 * {@link #getDescription()} and {@link #getBooks()}; the static {@code parse*} functions are pure.
 */
public class BookDescriptionModel implements DescriptionModel {

    /** The URL as stored (favorites, history and downloads are keyed by it). */
    private final String mUrl;

    /** {@link #mUrl} on the source's current host. */
    private final String mPageUrl;

    private final Function<String, Document> mFetch;

    /** The in-flight or downloaded page; a failed download is forgotten so the next load retries. Guarded by this. */
    private Single<Document> mPage;

    public BookDescriptionModel(String url) {
        this(url, fetchFor(url));
    }

    /** The site's own fetch, shared with the chapter list of this opening ({@link BookPages}); else {@link SiteHttp#get}. */
    private static Function<String, Document> fetchFor(String url) {
        SourceParser parser = Provider.parserOf(url);
        return parser != null ? page -> BookPages.get(page, parser::fetch) : SiteHttp::get;
    }

    BookDescriptionModel(String url, Function<String, Document> fetch) {
        mUrl = url;
        mPageUrl = Url.canonical(url);
        mFetch = fetch;
    }

    private synchronized Single<Document> page() {
        if (mPage == null) {
            mPage = Single.fromCallable(() -> mFetch.apply(BookPOJO.requireSupported(mPageUrl)))
                    .doOnError(e -> forgetPage())
                    .cache();
        }
        return mPage;
    }

    private synchronized void forgetPage() {
        mPage = null;
    }

    @OptIn(markerClass = UnstableApi.class)
    @Override
    public Observable<ArrayList<BookPOJO>> getBooks() {
        return Single.defer(this::page)
                .map(document -> parseBooks(document, mPageUrl))
                .toObservable();
    }

    @OptIn(markerClass = UnstableApi.class)
    @Override
    public Observable<DescriptionPOJO> getDescription() {
        // shown at once; the other-narrations search (a request of its own) re-emits it only when it finds some
        Single<DescriptionPOJO> parsed = Single.defer(this::page).map(document -> parseDescription(document, mPageUrl));
        if (!findsOtherReaders(mPageUrl)) {
            return parsed.toObservable();
        }
        // a fresh parse of the cached page: the first emission is the view's, never changed behind its back
        return parsed.toObservable()
                .concatWith(parsed.map(this::withOtherReader).filter(DescriptionPOJO::isOtherReader).toObservable());
    }

    /** What favorites, history or downloads saved for this book; empty when nothing is saved. */
    @Override
    public Maybe<DescriptionPOJO> getSavedDescription() {
        return Maybe.fromCallable(() -> {
            if (App.getInstance() == null || mUrl == null) {
                return null;
            }
            BooksDBModel dbModel = new BooksDBModel(App.getInstance());
            try {
                BookPOJO savedBook = dbModel.getSomewhere(mUrl);
                return savedBook == null ? null : savedBook.getDescriptionPOJO();
            } finally {
                dbModel.closeDB();
            }
        });
    }

    /** Pure: picks the description parser by host; a page without a title is an {@link IOException}. */
    static DescriptionPOJO parseDescription(Document document, String bookUrl) throws IOException {
        String url = BookPOJO.requireSupported(bookUrl);
        DescriptionPOJO description;
        SourceParser parser = Provider.parserOf(url);
        if (parser != null) {
            description = parser.description(document, url);
        } else {
            throw new IllegalArgumentException("unsupported host: " + url);
        }
        if (description.getTitle().isEmpty()) {
            throw new IOException("no book on page: " + url);
        }
        return description;
    }

    /** Pure: the "other books" block of the page; sources without one give an empty list. */
    static ArrayList<BookPOJO> parseBooks(Document document, String bookUrl) {
        String url = BookPOJO.requireSupported(bookUrl);
        SourceParser parser = Provider.parserOf(url);
        if (parser != null) {
            return parser.recommendations(document, url);
        }
        return new ArrayList<>();
    }

    /** True when the site of {@code url} can be searched for other narrations of a book ({@link #withOtherReader}). */
    static boolean findsOtherReaders(String url) {
        SourceParser parser = Provider.parserOf(url);
        if (parser != null) {
            return parser.probesOtherReaders();
        }
        return false;
    }

    /** Network step after parsing: whether the site has other narrations; best effort, never fails the load. */
    @OptIn(markerClass = UnstableApi.class)
    private DescriptionPOJO withOtherReader(DescriptionPOJO description) {
        SourceParser parser = Provider.parserOf(mPageUrl);
        try {
            if (parser != null) {
                if (parser.findsOtherReaders()) {
                    parser.probeOtherReaders(description, mPageUrl);
                } else {
                    description.setOtherReaders(null);
                }
            }
        } catch (Exception ignored) {
            description.setOtherReaders(null);
        }
        return description;
    }

}
