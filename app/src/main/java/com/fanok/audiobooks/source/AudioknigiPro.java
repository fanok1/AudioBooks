package com.fanok.audiobooks.source;

import static com.fanok.audiobooks.source.Html.afterLabel;
import static com.fanok.audiobooks.source.Html.href;
import static com.fanok.audiobooks.source.Html.labelled;
import static com.fanok.audiobooks.source.Html.text;

import androidx.annotation.Nullable;

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
import com.fanok.audiobooks.source.Players.Chapters;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import io.reactivex.functions.BooleanSupplier;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import okhttp3.HttpUrl;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

/**
 * audioknigi.pro (DataLife Engine, template "default"), probed live 2026-10-31: no cookies, no Referer for pages.
 * <ul>
 * <li>Listings of 20 {@code #dle-content .short-nm} cards: the index {@code /}, genres {@code /zhanr/<slug>/},
 * authors {@code /author/<slug>/}, readers {@code /read/<slug>/}, years {@code /year/<yyyy>/}, series
 * {@code /cikl/<slug>/} (in the order of their numbers). Page N is {@code <listing>page/N/}; the next page is the
 * "Загрузить еще" link {@code #pages-load > a} (the page numbers lag behind). {@link #TOP_100} is one page of 100.</li>
 * <li>Sorts (date, rating, views, comments, title; no periods) are the DLE form POSTed to any page of a listing, key
 * {@code main} on the index and {@code xfsearch} on every listing of a genre, person, year or series; a series keeps
 * the order of its numbers whatever is POSTed. The PHPSESSID session keeps the last sort of each key, so a plain GET
 * of an uncached listing comes back sorted, while a cached one lags hours behind: {@link #fetch} POSTs every listing
 * with its sort, "new" (date) too.</li>
 * <li>Indexes of one page each: genres {@link #GENRES} (87), authors {@link #AUTHORS} (4094, 2.7 MB), readers
 * {@link #READERS} (2327), most books first.</li>
 * <li>Search: GET {@code /index.php?do=search&subaction=search&story=Q&search_start=N}, 20 cards, 5 pages at most.</li>
 * <li>Book {@code /audioknigi/<id>-<slug>.html}: "Label: value" rows, the series' books, the author's and the
 * genre's books, comments inline (oldest first, no paging). The player names its playlist in {@code data-ap-ajax}
 * ({@code /engine/ajax/controller.php?mod=audio_secure_playlist&id=<id>}), which answers only an XHR with a Referer
 * on the site: {@code {"id","encoded"}}, see {@link #decode}. One MP3 per chapter on {@code s5.vozknig.com} (shared
 * with audioknigi.fun, no headers needed), newer ones signed for 24 h. A book the site sells through LitRes has only
 * the LitRes trial fragment instead of the player.</li>
 * </ul>
 */
public final class AudioknigiPro implements SourceParser {

    public static final String SERVER = "https://audioknigi.pro";
    public static final String INDEX = SERVER + "/";
    public static final String TOP_100 = SERVER + "/top-100-audioknig.html";
    public static final String GENRES = SERVER + "/c/genres.html";
    public static final String AUTHORS = SERVER + "/c/authors.html";
    public static final String READERS = SERVER + "/c/ispolniteli.html";

    /** The playlist codec of the site's {@code ak-playlist-codec.js}: a permuted base64 alphabet and the key's salt. */
    static final String ALPHABET = "f3RqmzK8TvNpLn0Xd7WhjYeA9QobsSl1HCgu5cyEDi4GBwrk2aFxJtMOPZUIV6+/";
    static final String SALT = "KxOdv42AVmaq";

    // ---------------------------------------------------------------- SourceParser

    /** Every listing goes out as a POST with its sort spelled out ({@link #fetchUrl}); the rest is a GET. */
    @Override
    public Document fetch(String url) throws IOException {
        return Dle.fetch(fetchUrl(url));
    }

    @Override
    public ProviderPage<BookPOJO> listing(Document page, String url, int pageNo) {
        return parseListing(page);
    }

    /** The one-page {@code .html} lists (top 100, indexes) have no page 2. */
    @Override
    public String pageUrl(String url, int page) {
        return HttpUrl.get(url).encodedPath().endsWith(".html") ? url : Dle.pageUrl(url, page);
    }

    /** The index (and the top 100 it opens) and the listings of a genre, person or year; a series has none. */
    @Override
    public List<Sort.Entry> sorts(String url) {
        String key = sortKey(url);
        if ("main".equals(key) || TOP_100.equals(url)) {
            return Dle.MENU_WITH_TOP;
        }
        return genreOf(url) != null ? Dle.MENU : Collections.<Sort.Entry>emptyList();
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

    /** The root of a genre, author, reader or year listing, plain or sorted: the server sorts each of them. */
    @Override
    public String genreOf(String url) {
        String root = Dle.pageUrl(HttpUrl.get(url).newBuilder().query(null).build().toString(), 1);
        return "xfsearch".equals(sortKey(root)) && !root.startsWith(SERVER + "/cikl/") ? root : null;
    }

    /** "New" is the plain listing, every other sort the listing's own form. */
    @Override
    public String inGenre(String siteWide, String genre) {
        Dle.Order order = Dle.Order.ofField(HttpUrl.get(siteWide).queryParameter(Dle.SORT_PARAM));
        return order == null || order == Dle.Order.DATE ? genre : sorted(genre, order);
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
    public DescriptionPOJO description(Document page, String bookUrl) {
        return parseDescription(page);
    }

    @Override
    public BookPOJO book(Document page, String bookUrl) {
        return parseBook(page, bookUrl);
    }

    @Override
    public ArrayList<BookPOJO> recommendations(Document page, String bookUrl) {
        return parseRecommendations(page, bookUrl);
    }

    /** The playlist answers only an XHR with a Referer on the site; 204 when the book has none. */
    @Override
    public ArrayList<AudioPOJO> tracks(Document page, String base) throws IOException {
        String answer = SiteHttp.body(SiteHttp.connect(playlistUrl(page, base)).referrer(base)
                .header("X-Requested-With", "XMLHttpRequest"));
        if (answer.trim().isEmpty()) {
            throw unavailable(page, base);
        }
        return parseTracks(answer, title(page));
    }

    @Override
    public ArrayList<SeriesPOJO> series(Document page, String bookUrl) {
        return parseSeries(page, bookUrl);
    }

    @Override
    public ArrayList<ComentsPOJO> comments(String bookUrl, BooleanSupplier cancelled) throws Exception {
        return parseComments(fetch(bookUrl));
    }

    // ---------------------------------------------------------------- URLs

    /** The listing (any page of it) in {@code order}, to be POSTed; null when the listing has no server sort. */
    @Nullable
    public static String sorted(String url, Dle.Order order) {
        String key = sortKey(url);
        return key == null ? null
                : Dle.sorted(HttpUrl.get(url).newBuilder().query(null).build().toString(), order, key);
    }

    /** What to fetch for {@code url}: a listing without a sort gets the date sort, so no session sort leaks in. */
    public static String fetchUrl(String url) {
        String sorted = HttpUrl.get(url).query() == null ? sorted(url, Dle.Order.DATE) : null;
        return sorted == null ? url : sorted;
    }

    /** The DLE session key of a listing's sort; null for a page without sorts (search, top, indexes, books). */
    @Nullable
    static String sortKey(String url) {
        String path = HttpUrl.get(url).encodedPath();
        if (path.matches("/(page/\\d+/)?")) {
            return "main";
        }
        return path.matches("/(zhanr|author|read|year|cikl)/[^/]+/(page/\\d+/)?") ? "xfsearch" : null;
    }

    // ---------------------------------------------------------------- listings

    /** Index, sorted, genre, person, year, series, top-100 and search pages; also the other-source search hits. */
    public static ProviderPage<BookPOJO> parseListing(Document doc) {
        Elements cards = doc.select("#dle-content div.short-nm");
        return ProviderPage.of(Html.cards(cards, AudioknigiPro::card), doc.selectFirst("#pages-load > a") != null,
                cards.size());
    }

    @Nullable
    private static BookPOJO card(Element card) {
        Element title = card.selectFirst("a.name-kniga");
        if (title == null) {
            return null;
        }
        BookPOJO book = new BookPOJO();
        book.setUrl(title.absUrl("href"));
        book.setName(title.text().trim());
        book.setPhoto(Html.imgSrc(card.selectFirst(".short-img img")));
        book.setGenre(text(card, "span.zhanr"));
        book.setUrlGenre(href(card, "span.zhanr a"));
        book.setAutor(text(card, "span.author"));
        book.setUrlAutor(href(card, "span.author a"));
        book.setArtist(text(card, "span.reader"));
        book.setUrlArtist(href(card, "span.reader a"));
        book.setSeries(text(card, "span.cikl a"));
        book.setUrlSeries(href(card, "span.cikl a"));
        book.setTime(text(card, "span.js-duration"));
        book.setComents(card.attr("data-comments"));
        setRating(book, text(card, ".mrating"));
        book.setDesc(text(card, ".short-story-descr"));
        return book;
    }

    /** The rows of {@link #GENRES}, {@link #AUTHORS} and {@link #READERS}, with their book counts. */
    public static ProviderPage<GenrePOJO> parseIndex(Document doc) {
        return ProviderPage.of(Html.rows(doc.select("#fields-list a.fc-item__link"), a -> {
            GenrePOJO row = new GenrePOJO(a.text().trim(), a.absUrl("href"));
            row.setDescription(text(a.parent(), ".field_counter"));
            return row;
        }), false);
    }

    // ---------------------------------------------------------------- book page

    public static DescriptionPOJO parseDescription(Document doc) {
        DescriptionPOJO description = new DescriptionPOJO();
        description.setTitle(title(doc));
        description.setPoster(Html.imgSrc(doc.selectFirst(".full-img img")));
        Element row = info(doc, "Автор");
        description.setAutor(afterLabel(row));
        description.setAutorUrl(href(row, "a"));
        row = info(doc, "Читает");
        description.setArtist(afterLabel(row));
        description.setArtistUrl(href(row, "a"));
        row = info(doc, "Жанр");
        description.setGenre(afterLabel(row));
        description.setGenreUrl(href(row, "a"));
        row = info(doc, "Цикл");
        description.setSeries(text(row, "a"));
        description.setSeriesUrl(href(row, "a"));
        description.setTime(afterLabel(info(doc, "Продолжительность")));
        description.setReiting(text(doc, ".full-rating"));
        description.setDescription(text(doc, ".full-desc"));
        return description;
    }

    /** A book opened by its link: its page's description plus the card's rating and comment count. */
    public static BookPOJO parseBook(Document doc, String url) {
        DescriptionPOJO description = parseDescription(doc);
        BookPOJO book = BookPOJO.of(description, url);
        setRating(book, description.getReiting());
        book.setComents(text(doc, ".full-addcom-title span"));
        return book;
    }

    /** "Еще книги автора", "Похожие аудиокниги жанра" and "Послушайте и эти", each book once, the open one left out. */
    public static ArrayList<BookPOJO> parseRecommendations(Document doc, String bookUrl) {
        Map<String, BookPOJO> books = new LinkedHashMap<>();
        for (BookPOJO book : Html.cards(doc.select("a.book-seo-card"), a -> {
            BookPOJO item = new BookPOJO();
            item.setUrl(a.absUrl("href"));
            item.setName(text(a, ".book-seo-card__name"));
            item.setPhoto(Html.imgSrc(a.selectFirst("img")));
            item.setAutor(text(a, ".book-seo-card__meta"));
            return item;
        })) {
            if (!book.getUrl().equals(bookUrl) && !books.containsKey(book.getUrl())) {
                books.put(book.getUrl(), book);
            }
        }
        return new ArrayList<>(books.values());
    }

    /** "Все книги из цикла": the series' books in the order of their numbers, the open one ({@code bookUrl}) too. */
    public static ArrayList<SeriesPOJO> parseSeries(Document doc, String bookUrl) {
        ArrayList<SeriesPOJO> series = new ArrayList<>();
        for (Element item : doc.select(".cycle-books .cycle-books__item")) {
            SeriesPOJO book = new SeriesPOJO();
            book.setUrl(item.hasAttr("href") ? item.absUrl("href") : bookUrl);
            book.setName(text(item, ".cycle-books__name"));
            // "07." → "7", "0.1." → "0.1"
            book.setNumber(text(item, ".cycle-books__prefix").replaceFirst("\\.$", "").replaceFirst("^0+(?=\\d)", ""));
            if (!book.getUrl().isEmpty() && !book.getName().isEmpty()) {
                series.add(book);
            }
        }
        return series;
    }

    /** All comments are on the book page, oldest first and flat. */
    public static ArrayList<ComentsPOJO> parseComments(Document doc) {
        ArrayList<ComentsPOJO> comments = new ArrayList<>();
        for (Element item : doc.select("#dle-comments-list .comment-card")) {
            ComentsPOJO comment = new ComentsPOJO();
            comment.setName(text(item, ".comment-card__author"));
            comment.setDate(text(item, ".comment-card__date"));
            comment.setText(text(item, ".comment-card__text"));
            Element avatar = item.selectFirst("img.comment-card__avatar-img");
            comment.setImage(avatar == null ? "" : avatar.absUrl("src"));
            String rating = text(item, ".ratingtypeplusminus");
            comment.setReting(rating.equals("0") ? "" : rating);
            if (!comment.isEmty()) {
                comments.add(comment);
            }
        }
        return comments;
    }

    // ---------------------------------------------------------------- chapters

    /**
     * The playlist URL of a book page ({@code data-ap-ajax}). A page without one that carries the LitRes trial fragment
     * (or a takedown notice) is a {@link BookRemovedException}, any other an {@link IllegalStateException}.
     */
    public static String playlistUrl(Document doc, String bookUrl) {
        Element player = doc.selectFirst("#vk-player-inline[data-ap-ajax]");
        if (player == null) {
            throw unavailable(doc, bookUrl);
        }
        return player.absUrl("data-ap-ajax");
    }

    private static IllegalStateException unavailable(Document doc, String bookUrl) {
        if (doc.selectFirst("a.full-litres") != null || BookRemovedException.isNotice(doc.text())) {
            return new BookRemovedException(bookUrl);
        }
        return new IllegalStateException("audioknigi.pro page has no playlist: " + bookUrl);
    }

    /** The chapters of a playlist answer {@code {"id":<news id>,"encoded":"..."}}; one MP3 each, no durations. */
    public static ArrayList<AudioPOJO> parseTracks(String json, String bookName) {
        JsonObject answer = Json.object(json);
        return parsePlaylist(decode(Json.string(answer, "encoded"), Json.string(answer, "id")), bookName);
    }

    /** The decoded playlist: {@code [{title, file}]}; an item that is not a chapter is skipped, not the list. */
    static ArrayList<AudioPOJO> parsePlaylist(String decoded, String bookName) {
        String playlist = decoded.replace("&quot;", "\"").replace("&#34;", "\"").replace("&amp;", "&").trim();
        JsonArray items;
        try {
            items = Json.array(asList(playlist));
        } catch (IllegalStateException escaped) {
            // older answers escape every quote of the list (\"title\":...)
            items = Json.array(asList(playlist.replaceAll("\\\\+\"", "\"").replace("\\'", "'")));
        }
        Chapters chapters = new Chapters(bookName, "");
        int[] numbered = {0};
        chapters.each(items, item -> {
            JsonObject o = item.getAsJsonObject();
            String file = Json.string(o, "file").trim()
                    .replaceFirst("(?i)^https?://54txtbjq4y\\.a\\.trbcdn\\.net/", "https://s4.slovushko.com/");
            String title = Json.string(o, "title").trim();
            // bare file numbers ("1", "01 141183696") say nothing more than the position
            AudioPOJO chapter = chapters.chapter(
                    title.matches("[\\d\\s]*") ? "Глава " + (numbered[0] + 1) : title, file, 0);
            if (chapter != null) {
                numbered[0]++;
            }
            return chapter;
        });
        return chapters.done("audioknigi.pro playlist");
    }

    /** A JSON list, objects without their outer brackets and braces (..},{..) or flat "title":..,"file":..,"title":.. */
    private static String asList(String playlist) {
        return playlist.startsWith("[") ? playlist
                : "[{" + playlist.replaceAll(",\\s*\"title\"\\s*:", "},{\"title\":") + "}]";
    }

    /**
     * The site's playlist codec: base64 over {@link #ALPHABET}, XORed with the MD5 digests of
     * {@code "<news id>:<SALT>:<block>"} for blocks 0, 1, 2, ... of 16 bytes; UTF-8 text.
     */
    public static String decode(String encoded, String newsId) {
        byte[] data = Players.decodeBase64(encoded, ALPHABET);
        try {
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            byte[] key = null;
            for (int i = 0; i < data.length; i++) {
                if (i % 16 == 0) {
                    key = md5.digest((newsId + ":" + SALT + ":" + i / 16).getBytes(StandardCharsets.UTF_8));
                }
                data[i] ^= key[i % 16];
            }
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        return new String(data, StandardCharsets.UTF_8);
    }

    // ---------------------------------------------------------------- helpers

    /** The book's title: the page's heading without its "Аудиокнига" prefix. */
    private static String title(Document doc) {
        return text(doc, "h1.title").replaceFirst("^Аудиокнига\\s+", "");
    }

    /** The "Label: value" row of the book page's info block; null when absent. */
    @Nullable
    private static Element info(Document doc, String label) {
        return labelled(doc, ".full-info li", label);
    }

    /** The site's average vote, 0 to 10; a card without votes ("0") keeps the "0" the list hides. */
    private static void setRating(BookPOJO book, String rating) {
        if (!rating.isEmpty()) {
            book.setReting(rating);
        }
    }
}
