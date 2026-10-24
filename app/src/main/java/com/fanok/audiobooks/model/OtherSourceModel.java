package com.fanok.audiobooks.model;


import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.fanok.audiobooks.Url;
import com.fanok.audiobooks.net.SiteHttp;
import com.fanok.audiobooks.pojo.BookPOJO;
import com.fanok.audiobooks.pojo.OtherArtistPOJO;
import io.reactivex.Observable;
import io.reactivex.Scheduler;
import io.reactivex.Single;
import io.reactivex.schedulers.Schedulers;
import okhttp3.HttpUrl;

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

    /** baza-knig search result pages read at most before giving up. */
    static final int MAX_SEARCH_PAGES = 3;

    private static final Logger LOG = Logger.getLogger("parsers");

    /** The providers, in the order other-source results are shown; {@code label} is what the user sees. */
    enum Site {
        AKNIGA(Url.SERVER_AKNIGA, "akniga.org"),
        KNIGAVUHE(Url.SERVER, "knigavuhe.org"),
        IZIBUK(Url.SERVER_IZIBUK, "izib.uk"),
        ABMP3(Url.SERVER_ABMP3, "audiobook-mp3.com"),
        BAZA_KNIG(Url.SERVER_BAZA_KNIG, "baza-knig.top"),
        KNIGOBLUD(Url.SERVER_KNIGOBLUD, "Книгоблуд"),
        AUDIOBOO(Url.SERVER_AUDIOBOO, "Audioboo"),
        OTRUB(Url.SERVER_OTRUB, "Отрубин"),
        POLEKNIG(Url.SERVER_POLEKNIG, "Полекниг");

        final String server;

        final String label;

        Site(String server, String label) {
            this.server = server;
            this.label = label;
        }

        /** The provider serving {@code url} (parsed host); an unknown host is an error, not "no data". */
        static Site of(@Nullable String url) {
            for (Site site : values()) {
                if (Url.isHost(url, site.server)) {
                    return site;
                }
            }
            throw new IllegalArgumentException("unsupported host: " + Url.hostOf(url));
        }
    }

    /** One search result as the provider lists it; a missing field is "". */
    static final class Hit {

        final String url;

        final String title;

        final String author;

        final String reader;

        /** baza-knig: the result advertises alternative voice-overs on its own page. */
        final boolean altVoices;

        Hit(String url, String title, String author, String reader) {
            this(url, title, author, reader, false);
        }

        Hit(String url, String title, String author, String reader, boolean altVoices) {
            this.url = url;
            this.title = title;
            this.author = author;
            this.reader = reader;
            this.altVoices = altVoices;
        }
    }

    /** Parses one list element; returns null for an element that is legitimately not an item. */
    interface ItemParser<T> {

        @Nullable
        T parse(Element element);
    }

    @Override
    public Observable<ArrayList<OtherArtistPOJO>> getOtherArtist(@NonNull BookPOJO bookPOJO) {
        if (isBlank(bookPOJO.getName())) {
            return Observable.just(new ArrayList<>());
        }
        String url = Url.canonical(bookPOJO.getUrl());
        List<Single<ArrayList<OtherArtistPOJO>>> lookups = new ArrayList<>();
        for (Site site : Site.values()) {
            if (!Url.isHost(url, site.server)) {
                lookups.add(Single.<ArrayList<OtherArtistPOJO>>create(emitter -> {
                    try {
                        emitter.onSuccess(lookup(site, bookPOJO));
                    } catch (Exception e) {
                        emitter.tryOnError(e); // after a timeout nobody listens: dropped, not a crash
                    }
                }).subscribeOn(Schedulers.io()));
            }
        }
        return searchAll(lookups, Schedulers.computation()).toObservable();
    }

    /**
     * Runs the lookups in parallel; each is cut off after {@link #SOURCE_TIMEOUT_SECONDS} on {@code timer},
     * and a failed or timed-out lookup counts as "not found". Results keep the order of {@code lookups}.
     */
    static Single<ArrayList<OtherArtistPOJO>> searchAll(List<Single<ArrayList<OtherArtistPOJO>>> lookups,
            Scheduler timer) {
        if (lookups.isEmpty()) {
            return Single.just(new ArrayList<>());
        }
        List<Single<ArrayList<OtherArtistPOJO>>> bounded = new ArrayList<>();
        for (Single<ArrayList<OtherArtistPOJO>> lookup : lookups) {
            bounded.add(lookup.timeout(SOURCE_TIMEOUT_SECONDS, TimeUnit.SECONDS, timer)
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
    private static ArrayList<OtherArtistPOJO> lookup(Site site, BookPOJO book) throws IOException {
        ArrayList<OtherArtistPOJO> found = new ArrayList<>();
        String terms = terms(site, book);
        int pages = site == Site.BAZA_KNIG ? MAX_SEARCH_PAGES : 1;
        for (int page = 1; page <= pages; page++) {
            Document doc = SiteHttp.get(searchUrl(site, terms, page));
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
    static String terms(Site site, BookPOJO book) {
        switch (site) {
            case ABMP3:
            case BAZA_KNIG:
            case AUDIOBOO:
                return query(book.getName());
            case KNIGAVUHE:
            case KNIGOBLUD:
                return query(book.getName(), book.getAutor());
            default:
                return query(book.getName(), book.getAutor(), book.getArtist());
        }
    }

    /** The non-blank parts joined by spaces. */
    static String query(String... parts) {
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
    static String searchUrl(Site site, String terms, int page) {
        HttpUrl.Builder url = HttpUrl.get(site.server).newBuilder();
        switch (site) {
            case AKNIGA:
                url.addPathSegments("search/books").addQueryParameter("q", terms);
                break;
            case KNIGAVUHE:
                url.addPathSegments("search/").addQueryParameter("q", terms);
                break;
            case ABMP3:
                url.addPathSegment("search").addQueryParameter("text", terms);
                break;
            case BAZA_KNIG:
            case AUDIOBOO:
                url.addPathSegment("index.php").addQueryParameter("do", "search")
                        .addQueryParameter("subaction", "search").addQueryParameter("story", terms);
                if (page > 1) {
                    url.addQueryParameter("search_start", String.valueOf(page));
                }
                break;
            case POLEKNIG:
                url.addQueryParameter("q", terms);
                break;
            default:
                url.addPathSegment("search").addQueryParameter("q", terms);
                break;
        }
        return url.build().toString();
    }

    static ArrayList<Hit> parseSearch(Site site, Document doc) {
        switch (site) {
            case AKNIGA:
                return parseAknigaSearch(doc);
            case KNIGAVUHE:
                return parseKnigavuheSearch(doc);
            case IZIBUK:
                return parseIzibukSearch(doc);
            case ABMP3:
                return parseAbmp3Search(doc);
            case BAZA_KNIG:
                return parseBazaKnigSearch(doc);
            case KNIGOBLUD:
                return parseKnigobludSearch(doc);
            case AUDIOBOO:
                return parseAudiobooSearch(doc);
            case OTRUB:
                return parseOtrubSearch(doc);
            default:
                return parsePoleknigSearch(doc);
        }
    }

    static ArrayList<Hit> parseAknigaSearch(Document doc) {
        return each(doc.select(".content__main__articles--item"), book -> {
            if (book.selectFirst("a[href$=/paid/]") != null
                    || "Фрагмент".equals(text(book, ".caption__article-preview"))) {
                return null;
            }
            String author = "";
            String reader = "";
            for (Element action : book.select(".link__action--author")) {
                Element use = action.selectFirst("use");
                Element a = action.selectFirst("a");
                if (use == null || a == null) {
                    continue;
                }
                String kind = use.attr("xlink:href");
                if (kind.endsWith("author") && author.isEmpty()) {
                    author = a.text().trim();
                } else if (kind.endsWith("performer") && reader.isEmpty()) {
                    reader = a.text().trim();
                }
            }
            String title = withoutAuthor(text(book, ".caption__article-main"), author);
            return new Hit(href(book, "a.content__article-main-link"), title, author, reader);
        });
    }

    static ArrayList<Hit> parseKnigavuheSearch(Document doc) {
        Element list = doc.getElementById("books_updates_list");
        if (list == null) {
            list = doc.getElementById("books_list");
        }
        if (list == null) {
            return new ArrayList<>();
        }
        return each(list.getElementsByClass("bookkitem"), book -> {
            if (!book.getElementsByClass("bookkitem_litres_icon").isEmpty()) {
                return null;
            }
            Element a = book.selectFirst("a.bookkitem_name, .bookkitem_name a");
            if (a == null) {
                return null;
            }
            Element icon = book.selectFirst(".bookkitem_icon.-reader");
            Element block = icon == null ? null : icon.parent();
            String reader = block == null ? "" : text(block, "a");
            return new Hit(a.absUrl("href"), a.text().trim(), text(book, ".bookkitem_author a"), reader);
        });
    }

    static ArrayList<Hit> parseIzibukSearch(Document doc) {
        Element list = doc.getElementById("books_list");
        if (list == null) {
            list = doc.getElementById("books_updates_list");
        }
        if (list == null) {
            return new ArrayList<>();
        }
        return each(list.getElementsByClass("_ccb9b7"), book -> {
            Element a = book.selectFirst("a._3dc935, ._3dc935 a");
            if (a == null) {
                return null;
            }
            String author = "";
            List<String> readers = new ArrayList<>();
            for (Element person : book.select("._eeab32 a")) {
                String link = person.attr("href");
                if (link.contains("author") && author.isEmpty()) {
                    author = person.text().trim();
                } else if (link.contains("reader")) {
                    readers.add(person.text().trim());
                }
            }
            return new Hit(a.absUrl("href"), a.text().trim(), author, join(readers));
        });
    }

    static ArrayList<Hit> parseAbmp3Search(Document doc) {
        ArrayList<Hit> hits = new ArrayList<>();
        for (Element block : doc.getElementsByClass("b-statictop-search")) {
            Element items = block.selectFirst(".b-statictop__items");
            if (!text(block, ".b-statictop__title").contains("в книгах") || items == null) {
                continue;
            }
            hits.addAll(each(items.children(), item -> {
                Element a = item.selectFirst("a");
                if (a == null) {
                    return null;
                }
                String full = a.text();
                int cut = full.indexOf(" (");
                String title = (cut >= 0 ? full.substring(0, cut) : full).trim();
                String info = text(a, ".add_info");
                String author = firstParenthesis(info);
                String reader = lastParenthesis(info);
                return new Hit(a.absUrl("href"), title, author, author.equals(reader) ? "" : reader);
            }));
        }
        return hits;
    }

    static ArrayList<Hit> parseBazaKnigSearch(Document doc) {
        Element content = doc.getElementById("dle-content");
        if (content == null) {
            return new ArrayList<>();
        }
        return each(content.getElementsByClass("short"), book -> {
            String url = href(book, ".short-img a");
            if (url.isEmpty()) {
                return null;
            }
            Element title = book.selectFirst(".short-title a");
            String author = "";
            String reader = "";
            boolean alt = false;
            for (Element li : book.select(".short-items li")) {
                String line = li.text();
                if (line.contains("Автор")) {
                    author = text(li, "b");
                } else if (line.contains("Читает")) {
                    reader = text(li, "b");
                    alt = line.contains("альтернативная озвучка");
                }
            }
            return new Hit(url, title == null ? "" : title.ownText().trim(), author, reader, alt);
        });
    }

    static ArrayList<Hit> parseKnigobludSearch(Document doc) {
        Element list = doc.getElementById("BL");
        if (list == null) {
            return new ArrayList<>();
        }
        return each(list.getElementsByClass("bookListItem"), book -> {
            String author = "";
            String reader = "";
            for (Element block : book.getElementsByClass("bookListItemMetaBlock")) {
                if (author.isEmpty()) {
                    author = linkAfterMarker(block, "✍");
                }
                if (reader.isEmpty()) {
                    reader = linkAfterMarker(block, "🎙");
                }
            }
            return new Hit(href(book, "a.bookListItemCover"), text(book, ".bookListItemCoverNameText"), author,
                    reader);
        });
    }

    /** Search cards: "Author - Title" headings, the author and reader ("Исполнитель:") in the card's list. */
    static ArrayList<Hit> parseAudiobooSearch(Document doc) {
        return each(doc.select("article.card"), book -> {
            Element a = book.selectFirst(".card__title a");
            if (a == null) {
                return null;
            }
            String author = "";
            String reader = "";
            for (Element li : book.select(".card__list li")) {
                String label = li.text();
                if (label.contains("Автор:")) {
                    author = text(li, "a");
                } else if (label.contains("Исполнитель:") || label.contains("Читает:")) {
                    reader = text(li, "a");
                }
            }
            return new Hit(a.absUrl("href"), withoutAuthor(a.text().trim(), author), author, reader);
        });
    }

    static ArrayList<Hit> parseOtrubSearch(Document doc) {
        Element list = doc.getElementById("books_updates_list");
        if (list == null) {
            list = doc.getElementById("books_list");
        }
        if (list == null) {
            return new ArrayList<>();
        }
        return each(list.getElementsByClass("_dad4fa"), book -> {
            Element a = book.selectFirst("._3dc935 a");
            if (a == null) {
                return null;
            }
            String author = "";
            String reader = "";
            for (Element block : book.select("._5d10b1 ._eeab32")) {
                String label = text(block, "._a6370b");
                if (label.contains("Автор")) {
                    author = text(block, "a");
                } else if (label.contains("Читает")) {
                    reader = text(block, "a");
                }
            }
            return new Hit(a.absUrl("href"), a.text().trim(), author, reader);
        });
    }

    static ArrayList<Hit> parsePoleknigSearch(Document doc) {
        Element root = doc.selectFirst("div#content");
        return each((root == null ? doc : root).select("div.book-item"), book -> {
            Element a = book.selectFirst("a.book-title, .book-title a");
            if (a == null) {
                return null;
            }
            return new Hit(a.absUrl("href"), a.text().trim(), text(book, ".book-author a"),
                    text(book, ".book-reader a"));
        });
    }

    /** The first hit that is this book (title, and author/reader where we know them), labelled by its site. */
    @Nullable
    static OtherArtistPOJO pickSameBook(Site site, List<Hit> hits, BookPOJO book) {
        for (Hit hit : hits) {
            if (!hit.url.isEmpty() && sameBook(hit, book.getName(), book.getAutor())
                    && (isBlank(book.getArtist()) || samePerson(hit.reader, book.getArtist()))) {
                OtherArtistPOJO pojo = new OtherArtistPOJO();
                pojo.setName(site.label);
                pojo.setUrl(hit.url);
                return pojo;
            }
        }
        return null;
    }

    /** Same title (ignoring case) and, when we know our author, the same author. */
    static boolean sameBook(Hit hit, @Nullable String name, @Nullable String author) {
        return !isBlank(name) && !hit.title.isEmpty() && hit.title.equalsIgnoreCase(name.trim())
                && (isBlank(author) || samePerson(hit.author, author));
    }

    /** The providers' long-standing match: same length and every word of {@code found} occurs in {@code ours}. */
    static boolean samePerson(String found, String ours) {
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
    static int lastPage(Document doc) {
        int last = 1;
        for (Element item : doc.select(".page-nav .navigation > *")) {
            String label = item.text().trim();
            if (label.matches("\\d{1,4}")) {
                last = Math.max(last, Integer.parseInt(label));
            }
        }
        return last;
    }

    /**
     * Applies {@code parser} to every element: an element that throws is skipped and counted (logged once
     * per list), so one odd card never loses the whole list.
     */
    static <T> ArrayList<T> each(Iterable<Element> elements, ItemParser<T> parser) {
        ArrayList<T> result = new ArrayList<>();
        int skipped = 0;
        RuntimeException first = null;
        for (Element element : elements) {
            try {
                T item = parser.parse(element);
                if (item != null) {
                    result.add(item);
                }
            } catch (RuntimeException e) {
                skipped++;
                if (first == null) {
                    first = e;
                }
            }
        }
        if (skipped > 0) {
            LOG.warning("skipped " + skipped + " unparsable item(s): " + first);
        }
        return result;
    }

    /** Trimmed text of the first match of {@code css} under {@code scope}; "" when absent. */
    static String text(Element scope, String css) {
        Element element = scope.selectFirst(css);
        return element == null ? "" : element.text().trim();
    }

    /** Absolute href of the first match of {@code css} under {@code scope}; "" when absent or unresolvable. */
    static String href(Element scope, String css) {
        Element element = scope.selectFirst(css);
        return element == null ? "" : element.absUrl("href");
    }

    static boolean isBlank(@Nullable String s) {
        return s == null || s.trim().isEmpty();
    }

    /** "Author – Title" (any dash) → "Title" when the title starts with the author; otherwise unchanged. */
    static String withoutAuthor(String title, String author) {
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

    private static String firstParenthesis(String s) {
        int open = s.indexOf('(');
        int close = open < 0 ? -1 : s.indexOf(')', open);
        return close > open ? s.substring(open + 1, close).trim() : "";
    }

    private static String lastParenthesis(String s) {
        int open = s.lastIndexOf('(');
        int close = s.lastIndexOf(')');
        return open >= 0 && close > open ? s.substring(open + 1, close).trim() : "";
    }

    /** Text of the link right after the marker span (knigoblud: ✍ author, 🎙 reader); "" when absent. */
    private static String linkAfterMarker(Element block, String marker) {
        for (Element span : block.getElementsByTag("span")) {
            Element next = span.nextElementSibling();
            if (span.text().contains(marker) && next != null && next.tagName().equals("a")) {
                return next.text().trim();
            }
        }
        return "";
    }

    private static String join(List<String> parts) {
        StringBuilder s = new StringBuilder();
        for (String part : parts) {
            if (!part.isEmpty()) {
                s.append(s.length() == 0 ? "" : ", ").append(part);
            }
        }
        return s.toString();
    }
}
