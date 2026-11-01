package com.fanok.audiobooks.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.fanok.audiobooks.Url;
import com.fanok.audiobooks.pojo.BookPOJO;
import com.fanok.audiobooks.pojo.OtherArtistPOJO;
import com.fanok.audiobooks.search.Provider;
import com.fanok.audiobooks.search.Sources;
import io.reactivex.Observable;
import io.reactivex.Scheduler;
import io.reactivex.Single;
import io.reactivex.schedulers.Schedulers;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

/**
 * "The same book on other sites": one search per other provider, run in parallel, each bounded by
 * {@link #SOURCE_TIMEOUT_SECONDS}; a provider that fails or is slow simply contributes nothing.
 * Also holds the shared provider table, search URLs, search-page parsers and matching used by
 * {@link OtherArtistModel} and {@link SeriesModel}. Parsers are pure (no Android, no network).
 */
public class OtherSourceModel implements
        com.fanok.audiobooks.interface_pacatge.book_content.OtherArtistModel {

    /** Budget per provider for the cross-source lookup. */
    static final long SOURCE_TIMEOUT_SECONDS = 10;

    private static final Logger LOG = Logger.getLogger("parsers");

    /** What the user sees as the name of a hit's site: the first sites keep their long-standing labels. */
    static String label(Provider site) {
        switch (site) {
            case AKNIGA: return "akniga.org";
            case KNIGAVUHE: return "knigavuhe.org";
            case IZIBUK: return "izib.uk";
            case ABMP3: return "audiobook-mp3.com";
            case BAZA_KNIG: return "baza-knig.top";
            default: return site.title;
        }
    }

    /** One search result as the provider lists it; a missing field is "". */
    public static final class Hit {

        public final String url;

        public final String title;

        public final String author;

        public final String reader;

        /** baza-knig: the result advertises alternative voice-overs on its own page. */
        public final boolean altVoices;

        public Hit(String url, String title, String author, String reader) {
            this(url, title, author, reader, false);
        }

        public Hit(String url, String title, String author, String reader, boolean altVoices) {
            this.url = url == null ? "" : url;
            this.title = title == null ? "" : title;
            this.author = author == null ? "" : author;
            this.reader = reader == null ? "" : reader;
            this.altVoices = altVoices;
        }
    }

    @Override
    public Observable<ArrayList<OtherArtistPOJO>> getOtherArtist(@NonNull BookPOJO bookPOJO) {
        if (isBlank(bookPOJO.getName())) {
            return Observable.just(new ArrayList<>());
        }
        String url = Url.canonical(bookPOJO.getUrl());
        List<Single<ArrayList<OtherArtistPOJO>>> lookups = new ArrayList<>();
        List<Long> limits = new ArrayList<>();
        for (Provider site : Sources.active()) {
            if (!Url.isHost(url, site.server)) {
                lookups.add(Single.<ArrayList<OtherArtistPOJO>>create(emitter -> {
                    try {
                        emitter.onSuccess(lookup(site, bookPOJO));
                    } catch (Exception e) {
                        emitter.tryOnError(e); // after a timeout nobody listens: dropped, not a crash
                    }
                }).subscribeOn(Schedulers.io()));
                limits.add(Math.max(SOURCE_TIMEOUT_SECONDS, site.requestSeconds()));
            }
        }
        return searchAll(lookups, limits, Schedulers.computation()).toObservable();
    }

    /**
     * Runs the lookups in parallel; each is cut off after its {@code limits} seconds on {@code timer} (at least
     * {@link #SOURCE_TIMEOUT_SECONDS}; longer for a site that declares slow pages), and a failed or timed-out lookup
     * counts as "not found". Results keep the order of {@code lookups}.
     */
    static Single<ArrayList<OtherArtistPOJO>> searchAll(List<Single<ArrayList<OtherArtistPOJO>>> lookups,
            List<Long> limits, Scheduler timer) {
        if (lookups.isEmpty()) {
            return Single.just(new ArrayList<>());
        }
        List<Single<ArrayList<OtherArtistPOJO>>> bounded = new ArrayList<>();
        for (int i = 0; i < lookups.size(); i++) {
            bounded.add(lookups.get(i).timeout(limits.get(i), TimeUnit.SECONDS, timer)
                    .onErrorReturn(e -> new ArrayList<>()));
        }
        return Single.zip(bounded, results -> {
            ArrayList<OtherArtistPOJO> all = new ArrayList<>();
            for (Object result : results) {
                for (Object pojo : (List<?>) result) {
                    all.add((OtherArtistPOJO) pojo);
                }
            }
            return all;
        });
    }

    /** Searches {@code site} for {@code book}: an empty list or the one matching page. */
    private static ArrayList<OtherArtistPOJO> lookup(Provider site, BookPOJO book) throws IOException {
        ArrayList<OtherArtistPOJO> found = new ArrayList<>();
        String terms = terms(site, book);
        int pages = site.parser.otherSourcePages();
        for (int page = 1; page <= pages; page++) {
            String pageUrl = searchUrl(site, terms, page);
            Document doc = site.parser.fetch(pageUrl);
            OtherArtistPOJO pojo = pickSameBook(site, parseSearch(site, doc), book);
            if (pojo != null) {
                found.add(pojo);
                break;
            }
            if (page >= lastPage(doc)) {
                break;
            }
        }
        return found;
    }

    /** The words searched on {@code site} for {@code book}; a missing field is left out, never sent as "null". */
    static String terms(Provider site, BookPOJO book) {
        return site.parser.otherSourceTerms(book);
    }

    /** The non-blank parts joined by spaces. */
    public static String query(String... parts) {
        StringBuilder q = new StringBuilder();
        for (String part : parts) {
            if (!isBlank(part)) {
                if (q.length() > 0) {
                    q.append(' ');
                }
                q.append(part.trim());
            }
        }
        return q.toString();
    }

    /** The search page URL of {@code site}; {@code terms} is encoded as one query parameter. */
    static String searchUrl(Provider site, String terms, int page) {
        return site.parser.otherSourceSearchUrl(terms, page);
    }

    static ArrayList<Hit> parseSearch(Provider site, Document doc) {
        return site.parser.otherSourceHits(doc);
    }

    /** The first hit that is this book (title, and author/reader where we know them), labelled by its site. */
    @Nullable
    static OtherArtistPOJO pickSameBook(Provider site, List<Hit> hits, BookPOJO book) {
        for (Hit hit : hits) {
            if (!hit.url.isEmpty() && sameBook(hit, book.getName(), book.getAutor())
                    && (isBlank(book.getArtist()) || samePerson(hit.reader, book.getArtist()))) {
                OtherArtistPOJO pojo = new OtherArtistPOJO();
                pojo.setName(label(site));
                pojo.setUrl(hit.url);
                return pojo;
            }
        }
        return null;
    }

    /** Same title (ignoring case) and, when we know our author, the same author. */
    public static boolean sameBook(Hit hit, @Nullable String name, @Nullable String author) {
        return !isBlank(name) && !hit.title.isEmpty() && hit.title.equalsIgnoreCase(name.trim())
                && (isBlank(author) || samePerson(hit.author, author));
    }

    /** The providers' long-standing match: same length and every word of {@code found} occurs in {@code ours}. */
    public static boolean samePerson(String found, String ours) {
        String o = ours.trim().toLowerCase(Locale.ROOT);
        String f = found.trim().toLowerCase(Locale.ROOT);
        if (f.length() != o.length()) {
            return false;
        }
        for (String word : f.split(" ")) {
            if (!o.contains(word)) {
                return false;
            }
        }
        return true;
    }

    /** Highest page number in a DLE pager ("1 2 … 12 Далее"); 1 when there is none. Text labels are ignored. */
    public static int lastPage(Document doc) {
        int last = 1;
        for (Element item : doc.select(".page-nav .navigation > *")) {
            String label = item.text().trim();
            if (label.matches("\\d{1,4}")) {
                last = Math.max(last, Integer.parseInt(label));
            }
        }
        return last;
    }

    public static boolean isBlank(@Nullable String s) {
        return s == null || s.trim().isEmpty();
    }

    /** "Author – Title" (any dash) → "Title" when the title starts with the author; otherwise unchanged. */
    public static String withoutAuthor(String title, String author) {
        if (author.isEmpty() || !title.startsWith(author)) {
            return title;
        }
        for (String dash : new String[]{" – ", " — ", " - "}) {
            int at = title.indexOf(dash, author.length());
            if (at >= 0) {
                return title.substring(at + dash.length()).trim();
            }
        }
        return title;
    }
}
