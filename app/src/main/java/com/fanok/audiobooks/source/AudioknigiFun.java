package com.fanok.audiobooks.source;

import static com.fanok.audiobooks.source.Html.text;

import com.fanok.audiobooks.BookRemovedException;
import com.fanok.audiobooks.net.SiteHttp;
import com.fanok.audiobooks.pojo.AudioPOJO;
import com.fanok.audiobooks.pojo.BookPOJO;
import com.fanok.audiobooks.pojo.ComentsPOJO;
import com.fanok.audiobooks.pojo.DescriptionPOJO;
import com.fanok.audiobooks.pojo.GenrePOJO;
import com.fanok.audiobooks.pojo.SeriesPOJO;
import com.fanok.audiobooks.search.ProviderPage;
import com.fanok.audiobooks.search.SearchUrls;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import io.reactivex.functions.BooleanSupplier;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import okhttp3.HttpUrl;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

/**
 * audioknigi.fun (DataLife Engine, template "audiobooks"), probed live 2026-10-25. Pure parsers and URL builders.
 * <ul>
 * <li>Listings of 30 cards: the index {@code /}, genres {@code /zhanr/<slug>/}, authors {@code /author/<slug>/},
 * readers {@code /read/<slug>/}, series {@code /cikl/<slug>/}, collections {@code /podborki/<slug>/}. Page N is
 * {@code <listing>page/N/}; page 1 is the listing itself ({@code /page/1/} redirects). {@code /top-100.html} is one
 * page of 100 cards.</li>
 * <li>Sorts (date, rating, views, comments, title; no periods) are a DLE form POSTed to any page of a listing:
 * {@link #sorted} puts it in the URL's query for {@link Dle#fetch}; a GET with a query is redirected to the
 * bare listing. The site also keeps the last sort of each kind of listing (main, xfsearch, selection) in the PHPSESSID
 * session, so a plain GET after a sort comes back sorted: fetch listings through {@link #fetchUrl}.</li>
 * <li>Indexes of everything, one page each: genres {@code /c/zhanr.html}, authors {@code /c/avtor.html}, readers
 * {@code /c/chtec.html}, series {@code /c/cikl.html}, collections {@code /podborki/}.</li>
 * <li>Search: GET {@code /index.php?do=search&subaction=search&story=Q&search_start=N}, 10 cards a page, 4-6 s.</li>
 * <li>Book {@code /<id>-<slug>.html}: chips, annotation, "Рекомендуем послушать" and its few comments inline. The
 * chapters are GET from its {@code data-ap-src} with {@code X-Requested-With: XMLHttpRequest} and a Referer on the
 * site (else 403); newer files are signed ({@code md5=..&expires=..}, 24 h).</li>
 * </ul>
 */
public final class AudioknigiFun implements SourceParser {

    public static final String SERVER = "https://audioknigi.fun";
    public static final String INDEX = SERVER + "/";
    public static final String GENRES = SERVER + "/c/zhanr.html";
    public static final String AUTHORS = SERVER + "/c/avtor.html";
    public static final String READERS = SERVER + "/c/chtec.html";
    public static final String SERIES = SERVER + "/c/cikl.html";
    public static final String COLLECTIONS = SERVER + "/podborki/";
    public static final String TOP_100 = SERVER + "/top-100.html";

    /** The playlist answers 403 without this header (and without a Referer on the site, e.g. the book page). */
    public static final String AJAX_HEADER = "X-Requested-With";
    public static final String AJAX_VALUE = "XMLHttpRequest";

    /** Series pages read at most (30 books each). */
    static final int MAX_SERIES_PAGES = 10;

    // ---------------------------------------------------------------- SourceParser

    /** Every sortable listing goes out as a POST with its sort spelled out ({@link #fetchUrl}); the rest is a GET. */
    @Override
    public Document fetch(String url) throws IOException {
        return Dle.fetch(fetchUrl(url));
    }

    /** Its search is slow: 7 s of server time for two words (31.10.2026), more than the default on a phone. */
    @Override
    public int timeoutMs() {
        return 25_000;
    }

    @Override
    public ProviderPage<BookPOJO> listing(Document page, String url, int pageNo) {
        return parseListing(page, pageNo);
    }

    @Override
    public String pageUrl(String url, int page) {
        // the /c/*.html indexes of authors and readers are single pages
        return url.endsWith(".html") ? url : Dle.pageUrl(url, page);
    }

    @Override
    public List<Sort.Entry> sorts(String url) {
        String key = sortKey(url);
        return key == null ? Collections.<Sort.Entry>emptyList() : "main".equals(key) ? Dle.MENU_WITH_TOP : Dle.MENU;
    }

    /** The index in the item's sort; "new" is the bare index, which {@link #fetch} POSTs in the date sort. */
    @Override
    public String sortUrl(Sort sort, String listingUrl) {
        if (sort == Sort.TOP_100) {
            return TOP_100;
        }
        Dle.Order order = Dle.Order.of(sort);
        return order == null ? null : order == Dle.Order.DATE ? INDEX : sorted(INDEX, order);
    }

    /** The root of a genre, author, reader, series or collection listing: the site sorts each of them on the server. */
    @Override
    public String genreOf(String url) {
        String root = Dle.pageUrl(HttpUrl.get(url).newBuilder().query(null).build().toString(), 1);
        return sortKey(root) == null || INDEX.equals(root) ? null : root;
    }

    @Override
    public String inGenre(String siteWide, String genre) {
        Dle.Order order = Dle.Order.ofField(HttpUrl.get(siteWide).queryParameter(Dle.SORT_PARAM));
        return sorted(genre, order == null ? Dle.Order.DATE : order);
    }

    @Override
    public ProviderPage<GenrePOJO> genres(Document page, String url, int pageNo) {
        return parseIndex(page);
    }

    @Override
    public ProviderPage<GenrePOJO> people(Document page, String url, int pageNo, boolean performers) {
        return parseIndex(page);
    }

    @Override
    public String searchUrl(SearchUrls.Category category, String query, int page) {
        return category == SearchUrls.Category.BOOKS ? Dle.searchUrl(SERVER, query, page) : null;
    }

    @Override
    public ProviderPage<BookPOJO> searchResults(Document page, int pageNo) {
        return parseListing(page, pageNo);
    }

    @Override
    public DescriptionPOJO description(Document page, String bookUrl) {
        return parseDescription(page);
    }

    @Override
    public BookPOJO book(Document page, String bookUrl) {
        return parseBook(page, bookUrl);
    }

    @Override
    public ArrayList<BookPOJO> recommendations(Document page, String bookUrl) {
        return parseRecommendations(page);
    }

    /** The playlist answers only an XHR with a Referer on the site. */
    @Override
    public ArrayList<AudioPOJO> tracks(Document page, String base) throws IOException {
        String playlist = playlistUrl(page);
        if (playlist.isEmpty()) {
            // LitRes books carry only its trial fragment (a Playerjs "Ознакомительный фрагмент"): taken down, as on uknig
            if (page.text().contains("Ознакомительный фрагмент")) {
                throw new BookRemovedException(base);
            }
            throw new IllegalStateException("audioknigi.fun page has no full audio: " + base);
        }
        return parseTracks(SiteHttp.body(SiteHttp.connect(playlist).referrer(base).header(AJAX_HEADER, AJAX_VALUE)),
                text(page.selectFirst("h1")));
    }

    /** The series listing linked from the book page, all its pages, oldest first. */
    @Override
    public ArrayList<SeriesPOJO> series(Document page, String bookUrl) throws IOException {
        ArrayList<SeriesPOJO> result = new ArrayList<>();
        String series = seriesUrl(page);
        for (int no = 1; !series.isEmpty() && no <= MAX_SERIES_PAGES; no++) {
            Document doc = fetch(pageUrl(series, no));
            result.addAll(parseSeries(doc));
            if (!hasMore(doc, no)) {
                break;
            }
        }
        Collections.reverse(result);
        return result;
    }

    @Override
    public ArrayList<ComentsPOJO> comments(String bookUrl, BooleanSupplier cancelled) throws Exception {
        return parseComments(fetch(bookUrl));
    }

    // ---------------------------------------------------------------- URLs

    /** The listing (any page of it) in {@code sort}, to be POSTed; null when the listing has no server sort. */
    public static String sorted(String url, Dle.Order sort) {
        String key = sortKey(url);
        if (key == null) {
            return null;
        }
        return Dle.sorted(HttpUrl.get(url).newBuilder().query(null).fragment(null).build().toString(), sort, key);
    }

    /** What to fetch for {@code url}: a sortable listing without a sort gets the date sort, so no session sort leaks in. */
    public static String fetchUrl(String url) {
        String sorted = HttpUrl.get(url).query() == null ? sorted(url, Dle.Order.DATE) : null;
        return sorted == null ? url : sorted;
    }

    /** The DLE session key of a listing's sort, or null for a page without sorts (search, top, indexes, books). */
    static String sortKey(String url) {
        String path = HttpUrl.get(url).encodedPath();
        if (path.matches("/(page/\\d+/)?")) {
            return "main";
        } else if (path.matches("/(zhanr|author|read|cikl)/[^/]+/(page/\\d+/)?")) {
            return "xfsearch";
        } else if (path.matches("/podborki/[^/]+/(page/\\d+/)?")) {
            return "selection";
        }
        return null;
    }

    // ---------------------------------------------------------------- listings

    /** Cards of a listing, a sorted listing, a series or a search page; also the other-source search hits. */
    public static ProviderPage<BookPOJO> parseListing(Document doc, int page) {
        Elements cards = doc.select("article.card");
        ArrayList<BookPOJO> books = new ArrayList<>();
        for (Element card : cards) {
            BookPOJO book = card(card);
            if (book != null) {
                books.add(book);
            }
        }
        return ProviderPage.of(books, hasMore(doc, page), cards.size());
    }

    /** The pager names a page after {@code page} (listing links and search {@code list_submit(N)} alike). */
    public static boolean hasMore(Document doc, int page) {
        int last = 0;
        for (Element a : doc.select("#pagination .pagination__pages a, #pagination .pagination__pages span")) {
            try {
                last = Math.max(last, Integer.parseInt(a.text().trim()));
            } catch (NumberFormatException ignored) {
                // "..." between the numbers
            }
        }
        return last > page;
    }

    private static BookPOJO card(Element card) {
        Element title = card.selectFirst(".card__title a");
        if (title == null || title.absUrl("href").isEmpty()) {
            return null;
        }
        BookPOJO book = new BookPOJO();
        book.setUrl(title.absUrl("href"));
        book.setPhoto(cover(card.selectFirst(".card__img img")));
        for (Element li : card.select(".card__list li")) {
            String label = text(li.selectFirst("span"));
            Element a = li.selectFirst("a");
            if (label.startsWith("Продолжительность")) {
                book.setTime(li.ownText().trim());
            } else if (a == null) {
                continue;
            } else if (label.startsWith("Жанр")) {
                book.setGenre(a.text());
                book.setUrlGenre(a.absUrl("href"));
            } else if (label.startsWith("Автор")) {
                book.setAutor(a.text());
                book.setUrlAutor(a.absUrl("href"));
            } else if (label.startsWith("Исполнитель")) {
                book.setArtist(a.text());
                book.setUrlArtist(a.absUrl("href"));
            } else if (label.startsWith("Цикл")) {
                book.setSeries(a.text());
                book.setUrlSeries(a.absUrl("href"));
            }
        }
        // the top-100 titles end in " - <author>"
        String name = title.text().trim();
        String byAuthor = " - " + book.getAutor();
        book.setName(name.endsWith(byAuthor) ? name.substring(0, name.length() - byAuthor.length()) : name);
        Element comments = card.selectFirst(".card__meta:has(.fa-comment-alt-dots)");
        if (comments != null) {
            book.setComents(comments.text());
        }
        return book.isNull() ? null : book;
    }

    /** {@code src} is the poster itself (often a small webp); {@code data-src} a lazily loaded copy. */
    private static String cover(Element img) {
        if (img == null) {
            return "";
        }
        String src = img.absUrl("src");
        return src.isEmpty() ? img.absUrl("data-src") : src;
    }

    /** Rows of the index pages ({@code /c/*.html}: genres, authors, readers, series) and of {@code /podborki/}. */
    public static ProviderPage<GenrePOJO> parseIndex(Document doc) {
        ArrayList<GenrePOJO> rows = new ArrayList<>();
        for (Element a : doc.select("#fields-list li > a, .pb-card__title a")) {
            String name = a.text().trim();
            String url = a.absUrl("href");
            if (name.isEmpty() || url.isEmpty()) {
                continue;
            }
            GenrePOJO genre = new GenrePOJO();
            genre.setName(name);
            genre.setUrl(url);
            Element card = a.closest(".pb-card");
            String count = text(card == null ? a.parent().selectFirst(".field_counter")
                    : card.selectFirst(".pb-card__badge"));
            if (!count.isEmpty()) {
                genre.setDescription(count);
            }
            rows.add(genre);
        }
        return ProviderPage.of(rows, false);
    }

    // ---------------------------------------------------------------- book page

    public static DescriptionPOJO parseDescription(Document doc) {
        DescriptionPOJO description = new DescriptionPOJO();
        description.setTitle(text(doc.selectFirst("h1")));
        description.setPoster(poster(doc));
        Element a = chipLink(doc, "Автор");
        if (a != null) {
            description.setAutor(a.text());
            description.setAutorUrl(a.absUrl("href"));
        }
        a = chipLink(doc, "Исполнитель");
        if (a != null) {
            description.setArtist(a.text());
            description.setArtistUrl(a.absUrl("href"));
        }
        a = chipLink(doc, "Жанр");
        if (a != null) {
            description.setGenre(a.text());
            description.setGenreUrl(a.absUrl("href"));
        }
        a = chipLink(doc, "Цикл");
        if (a != null) {
            description.setSeries(a.text());
            description.setSeriesUrl(a.absUrl("href"));
        }
        description.setTime(chipValue(doc, "Длительность"));
        description.setDescription(annotation(doc));
        description.setLike(text(doc.selectFirst("[id^=likes-id-]")));
        description.setDisLike(text(doc.selectFirst("[id^=dislikes-id-]")));
        return description;
    }

    /** A book opened by its link. */
    public static BookPOJO parseBook(Document doc, String url) {
        return BookPOJO.of(parseDescription(doc), url);
    }

    /** The "Рекомендуем послушать" block. */
    public static ArrayList<BookPOJO> parseRecommendations(Document doc) {
        ArrayList<BookPOJO> books = new ArrayList<>();
        for (Element poster : doc.select(".pmovie__related a.poster")) {
            BookPOJO book = new BookPOJO();
            book.setUrl(poster.absUrl("href"));
            Element img = poster.selectFirst("img");
            book.setPhoto(cover(img));
            // the caption is "<title> - <author>"; the cover's alt names the title alone
            String caption = text(poster.selectFirst(".poster__title"));
            String name = img == null ? "" : img.attr("alt").replaceFirst("^обложка аудиокниги\\s*", "").trim();
            if (name.isEmpty()) {
                name = caption;
            } else if (caption.startsWith(name + " - ")) {
                book.setAutor(caption.substring(name.length() + 3));
            }
            book.setName(name);
            if (!book.getUrl().isEmpty() && !book.isNull()) {
                books.add(book);
            }
        }
        return books;
    }

    /** The series ("Цикл") link of a book page; "" when the book is in none. */
    public static String seriesUrl(Document bookPage) {
        Element a = chipLink(bookPage, "Цикл");
        return a == null ? "" : a.absUrl("href");
    }

    /**
     * One page of a series listing ({@code /cikl/<slug>/}), newest first as the site lists it; a series of more than
     * 30 books goes on over {@link #pageUrl} pages while {@link #hasMore}.
     */
    public static ArrayList<SeriesPOJO> parseSeries(Document seriesPage) {
        ArrayList<SeriesPOJO> series = new ArrayList<>();
        for (BookPOJO book : parseListing(seriesPage, 1).items) {
            SeriesPOJO item = new SeriesPOJO();
            item.setUrl(book.getUrl());
            item.setName(book.getName());
            item.setPhoto(book.getPhoto() == null ? "" : book.getPhoto());
            series.add(item);
        }
        return series;
    }

    /** The comments on the book page, oldest first and flat (the site has a handful per book at most). */
    public static ArrayList<ComentsPOJO> parseComments(Document doc) {
        ArrayList<ComentsPOJO> comments = new ArrayList<>();
        for (Element item : doc.select("#dle-comments-list .comment-item")) {
            ComentsPOJO comment = new ComentsPOJO();
            comment.setName(text(item.selectFirst(".comment-item__author")));
            comment.setDate(text(item.selectFirst(".comment-item__date")));
            comment.setText(text(item.selectFirst(".comment-item__main")));
            Element img = item.selectFirst(".comment-item__img img");
            comment.setImage(img == null ? "" : img.absUrl("src"));
            if (!comment.isEmty()) {
                comments.add(comment);
            }
        }
        return comments;
    }

    // ---------------------------------------------------------------- chapters

    /** The playlist URL of a book page ({@code data-ap-src}); "" when the page has no player. */
    public static String playlistUrl(Document bookPage) {
        Element player = bookPage.selectFirst("[data-ap-src]");
        return player == null ? "" : player.absUrl("data-ap-src");
    }

    /**
     * The chapters of a playlist answer {@code {"ok":true,"playlist":"<items>"}}, where the items are the inside of a
     * JSON list of {@code {"title","file"}} objects, for newer books once more backslash-escaped. Chapters carry no
     * duration. Throws IllegalStateException when there is none.
     */
    public static ArrayList<AudioPOJO> parseTracks(String json, String bookName) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        JsonElement playlist = root.get("playlist");
        if (playlist == null || !playlist.isJsonPrimitive()) {
            throw new IllegalStateException("audioknigi.fun playlist: " + root.get("error"));
        }
        String items = playlist.getAsString();
        JsonArray list;
        try {
            list = JsonParser.parseString("[{" + items + "}]").getAsJsonArray();
        } catch (JsonParseException escaped) {
            list = JsonParser.parseString("[{" + items.replaceAll("\\\\(.)", "$1") + "}]").getAsJsonArray();
        }
        ArrayList<AudioPOJO> chapters = new ArrayList<>();
        for (JsonElement item : list) {
            JsonElement file = item.getAsJsonObject().get("file");
            JsonElement title = item.getAsJsonObject().get("title");
            if (file == null || !file.isJsonPrimitive() || file.getAsString().trim().isEmpty()) {
                continue;
            }
            String name = title == null || !title.isJsonPrimitive() ? "" : title.getAsString().trim();
            AudioPOJO chapter = new AudioPOJO();
            chapter.setBookName(bookName);
            // bare file numbers ("1", "01 1") say nothing more than the position
            chapter.setName(name.matches("[\\d\\s]*") ? "Глава " + (chapters.size() + 1) : name);
            chapter.setUrl(file.getAsString().trim());
            chapters.add(chapter);
        }
        if (chapters.isEmpty()) {
            throw new IllegalStateException("no playable chapters in the audioknigi.fun playlist");
        }
        return chapters;
    }

    // ---------------------------------------------------------------- helpers

    /** The {@code <b>Label:</b> value} chip of the book page that starts with {@code label}; null when absent. */
    private static Element chip(Document doc, String label) {
        for (Element chip : doc.select(".book-chip")) {
            if (text(chip.selectFirst("b")).startsWith(label)) {
                return chip;
            }
        }
        return null;
    }

    private static Element chipLink(Document doc, String label) {
        Element chip = chip(doc, label);
        return chip == null ? null : chip.selectFirst("a");
    }

    private static String chipValue(Document doc, String label) {
        Element chip = chip(doc, label);
        return chip == null ? "" : chip.ownText().trim();
    }

    private static String poster(Document doc) {
        Element og = doc.selectFirst("meta[property=og:image]");
        if (og != null && !og.absUrl("content").isEmpty()) {
            return og.absUrl("content");
        }
        Element player = doc.selectFirst("[data-cover]");
        return player == null ? "" : player.absUrl("data-cover");
    }

    /** The annotation without its "Аннотация к аудиокниге" heading. */
    private static String annotation(Document doc) {
        Element text = doc.selectFirst(".book-content-main .full-text");
        if (text == null) {
            return "";
        }
        Element copy = text.clone();
        copy.select("h2").remove();
        return copy.text().trim();
    }
}
