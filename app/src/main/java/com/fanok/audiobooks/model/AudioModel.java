package com.fanok.audiobooks.model;

import android.content.Context;
import androidx.annotation.NonNull;
import com.fanok.audiobooks.BookRemovedException;
import com.fanok.audiobooks.Url;
import com.fanok.audiobooks.interface_pacatge.book_content.AudioModelInterfece;
import com.fanok.audiobooks.net.SiteHttp;
import com.fanok.audiobooks.pojo.AudioPOJO;
import com.fanok.audiobooks.search.Provider;
import com.fanok.audiobooks.source.SourceParser;
import io.reactivex.Observable;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import org.jetbrains.annotations.NotNull;
import org.jsoup.nodes.Document;

/**
 * Chapter lists for all sources. Fetching ({@link SiteHttp}) is kept apart from parsing: every
 * {@code parseXxx} takes already-fetched content, calls no Android API and runs in JVM tests.
 * Failures are errors, never fake chapters: an unknown host, a page without the player or a page
 * that yields no playable chapter make {@link #getAudio} emit {@code onError}, so the caller can
 * fall back to offline chapters. One malformed chapter is skipped and counted, not fatal.
 */
public class AudioModel implements AudioModelInterfece {

    /** Fetches a provider page: the site's {@code fetch} in production, fixtures in tests. */
    interface PageSource {
        Document get(String url) throws IOException;
    }

    Context mContext;

    public AudioModel(@NotNull final Context context) {
        mContext = context;
    }

    @Override
    public Observable<ArrayList<AudioPOJO>> getAudio(@NonNull String url) {
        SourceParser parser = Provider.parserOf(url);
        // the page the description of this opening fetched too (BookPages); an unknown host fails before fetching
        return audio(url, parser != null ? page -> BookPages.get(page, parser::fetch) : SiteHttp::get);
    }

    static Observable<ArrayList<AudioPOJO>> audio(String url, PageSource pages) {
        return Observable.create(emitter -> {
            ArrayList<AudioPOJO> chapters;
            try {
                chapters = load(url, pages);
            } catch (Exception e) {
                emitter.tryOnError(e);
                return;
            }
            if (!emitter.isDisposed()) {
                emitter.onNext(chapters);
                emitter.onComplete();
            }
        });
    }

    /** A provider's parser: {@code base} is the page's final URL, which relative media URLs resolve against. */
    private interface PageParser {
        ArrayList<AudioPOJO> parse(Document doc, String base) throws IOException;
    }

    /** Selects the parser by host (moved hosts are rewritten first); never returns an empty list. */
    static ArrayList<AudioPOJO> load(String rawUrl, PageSource pages) throws IOException {
        String url = Url.canonical(rawUrl);
        PageParser parser = parser(url);
        if (parser == null) {
            throw new IllegalArgumentException("unsupported host: " + Url.hostOf(rawUrl));
        }
        Document doc = pages.get(url);
        try {
            return parser.parse(doc, location(doc, url));
        } catch (IllegalStateException e) {
            if (BookRemovedException.isNotice(doc.text())) {
                throw new BookRemovedException(url);
            }
            throw e;
        }
    }

    private static PageParser parser(String url) {
        SourceParser parser = Provider.parserOf(url);
        if (parser != null) {
            // with the requested URL: what it selects (a voice) holds even when a redirect drops its query
            return (doc, base) -> parser.tracks(doc, base, url);
        }
        return null;
    }

    /** Where {@code doc} was served from after redirects ({@code fallback} when unknown). */
    private static String location(Document doc, String fallback) {
        String location = doc.location();
        return location == null || location.isEmpty() ? fallback : location;
    }

}
