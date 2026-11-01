package com.fanok.audiobooks.source;

import static com.fanok.audiobooks.source.Html.afterLabel;
import static com.fanok.audiobooks.source.Html.cards;
import static com.fanok.audiobooks.source.Html.each;
import static com.fanok.audiobooks.source.Html.href;
import static com.fanok.audiobooks.source.Html.iconText;
import static com.fanok.audiobooks.source.Html.imgSrc;
import static com.fanok.audiobooks.source.Html.labelled;
import static com.fanok.audiobooks.source.Html.text;

import androidx.annotation.Nullable;

import com.fanok.audiobooks.BookRemovedException;
import com.fanok.audiobooks.model.OtherSourceModel;
import com.fanok.audiobooks.net.SiteHttp;
import com.fanok.audiobooks.pojo.AudioPOJO;
import com.fanok.audiobooks.pojo.BookPOJO;
import com.fanok.audiobooks.pojo.DescriptionPOJO;
import com.fanok.audiobooks.pojo.GenrePOJO;
import com.fanok.audiobooks.pojo.OtherArtistPOJO;
import com.fanok.audiobooks.pojo.SeriesPOJO;
import com.fanok.audiobooks.search.ProviderPage;
import com.fanok.audiobooks.search.SearchUrls;
import com.fanok.audiobooks.source.Players.Chapters;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import org.jsoup.Connection;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * golosknig.com, formerly sweetbook.net (probed live, 2026-10-31): LiveStreet CMS, plain GETs, no Referer. Every
 * listing is 12 {@code article.topic} cards paged with a {@code pageN/} path segment before the query
 * ({@code /index/page2/}, {@code /search/books/page2/?q=Q}); the pager's {@code li.next} link means more.
 * <ul>
 * <li>new: {@link #INDEX}, in a genre {@code /genre/<g>/}; popular (views, all time): {@link #VIEWS},
 * {@code /genre/<g>/views/?period=all}. The views periods 1/7/30 are one page of at most 12 books published within
 * the period, and {@code /index/new/} is the last 24 h of "new" in its order, so neither is offered.</li>
 * <li>genres: {@link #GENRES}, two pages; authors {@link #AUTHORS} and readers {@link #READERS}, 30 rows a page.
 * {@code /author/<name>/}, {@code /performer/<name>/} and {@code /series/<name>/} are listings without sorts.</li>
 * <li>cards: "Title — Author, Author" (no author link: built from the name as the book page has it), one reader,
 * the time, a cover and a short text; no genre, rating or comments. Whether a book plays shows on its page only.</li>
 * <li>book {@code /<slug>}: the series block with numbers (for a series of two books or more), other narrations
 * ({@code .block-sametitle}), similar books. The player {@code sweetPlayer(<bid>)} POSTs {@code /ajax/mbid/<bid>}
 * with the page's session key and that key encrypted ({@link CryptoJs}) in the page's PHPSESSID session (the
 * {@link SiteHttp} jar); the answer's {@code res} decrypts to one MP3 of the whole book on the akniga CDN (a browser
 * User-Agent, no Referer; stable URL), its {@code items} cut it into chapters. A book closed by the rights holder,
 * sold on LitRes or unavailable shows a notice instead of the player. Comments are VK's only.</li>
 * </ul>
 */
public final class Golosknig implements SourceParser {

    public static final String SERVER = "https://golosknig.com";
    public static final String INDEX = SERVER + "/index/";
    public static final String VIEWS = SERVER + "/index/views/?period=all";
    public static final String GENRES = SERVER + "/genres/";
    public static final String AUTHORS = SERVER + "/authors/";
    public static final String READERS = SERVER + "/performers/";

    /** The player's passphrase (pl2.js), the one akniga's player uses too. */
    private static final String PASSPHRASE = "EKxtcg46V";
    /** The playlist's answer to a key of another session. */
    private static final String HACKING = "Hacking attemp";
    /** Bound of the genre list's pages (two now). */
    private static final int MAX_GENRE_PAGES = 5;
    private static final List<Sort.Entry> MENU = Sort.menu(Sort.NEW, Sort.POPULAR);
    private static final Pattern ROOT = Pattern.compile("^https?://[^/]+/?$");
    private static final Pattern GENRE = Pattern.compile("^https?://[^/]+/genre/[^/?#]+");
    /** Listings of one author, reader or series, and the search: nothing to re-sort. */
    private static final Pattern UNSORTED = Pattern.compile("^https?://[^/]+/(author|performer|series|search)/");
    private static final Pattern PLAYER = Pattern.compile("sweetPlayer\\((\\d+)\\)");
    private static final Pattern NUMBER = Pattern.compile("\\s+#\\d+$");
    private static final Pattern COMMA = Pattern.compile("\\s*,\\s*");
    /** The "Автор:", "Исполняет:", "Из серии:" rows of a book page; select links by href ({@code rel} lies). */
    private static final String ROWS = ".topic-content-begin div:has(> span.text-muted)";

    // ---------------------------------------------------------------- SourceParser

    @Override
    public ProviderPage<BookPOJO> listing(Document page, String url, int pageNo) {
        return parseListing(page);
    }

    /** {@code pageN/} after the path, before the query; the site root pages on as {@link #INDEX}. */
    @Override
    public String pageUrl(String url, int page) {
        if (page <= 1) {
            return url;
        }
        int at = url.indexOf('?');
        String path = at < 0 ? url : url.substring(0, at);
        String query = at < 0 ? "" : url.substring(at);
        if (ROOT.matcher(path).matches()) {
            path = INDEX;
        }
        return path + (path.endsWith("/") ? "" : "/") + "page" + page + "/" + query;
    }

    @Override
    public List<Sort.Entry> sorts(String url) {
        return UNSORTED.matcher(url).find() ? Collections.<Sort.Entry>emptyList() : MENU;
    }

    @Override
    public String sortUrl(Sort sort, String listingUrl) {
        String siteWide;
        switch (sort) {
            case NEW: siteWide = INDEX; break;
            case POPULAR: siteWide = VIEWS; break;
            default: return null;
        }
        String genre = genreOf(listingUrl);
        boolean same = genre == null ? siteWide.equals(listingUrl) : inGenre(siteWide, genre).equals(listingUrl);
        return same ? listingUrl : siteWide;
    }

    /** The genre root {@code /genre/<g>/} of a genre listing or of its sorts. */
    @Override
    public String genreOf(String url) {
        Matcher m = GENRE.matcher(url);
        return m.find() ? m.group() + "/" : null;
    }

    /** The genre's own popular list {@code views/}; "new" is the genre itself. */
    @Override
    public String inGenre(String siteWide, String genre) {
        switch (siteWide) {
            case INDEX: return genre;
            case VIEWS: return genre + "views/?period=all";
            default: return siteWide;
        }
    }

    @Override
    public ProviderPage<GenrePOJO> genres(Document page, String url, int pageNo) {
        return ProviderPage.of(parseGenres(page), false);
    }

    /** The genre list's pages (20 rows each) as the one page of the genre screen. */
    @Override
    public ProviderPage<GenrePOJO> loadGenres(String url, int pageNo) throws IOException {
        ArrayList<GenrePOJO> genres = new ArrayList<>();
        String next = url;
        for (int no = 0; next != null && no < MAX_GENRE_PAGES; no++) {
            Document page = fetch(next);
            genres.addAll(parseGenres(page));
            next = nextPage(page);
        }
        return ProviderPage.of(genres, false);
    }

    @Override
    public ProviderPage<GenrePOJO> people(Document page, String url, int pageNo, boolean performers) {
        return parsePeople(page);
    }

    @Override
    public String searchUrl(SearchUrls.Category category, String query, int page) {
        return category == SearchUrls.Category.BOOKS
                ? pageUrl(SERVER + "/search/books/?q=" + SearchUrls.enc(query), page) : null;
    }

    @Override
    public DescriptionPOJO description(Document page, String bookUrl) {
        return parseDescription(page);
    }

    @Override
    public ArrayList<BookPOJO> recommendations(Document page, String bookUrl) {
        return parseRecommendations(page);
    }

    /**
     * The playlist in the session the page came from. A page of another session (a copy the app kept) gets
     * {@link #HACKING}: a fresh page carries this session's key.
     */
    @Override
    public ArrayList<AudioPOJO> tracks(Document page, String base) throws IOException {
        String bid = bid(page, base);
        String answer = playlist(page, bid);
        if (answer.startsWith(HACKING)) {
            answer = playlist(SiteHttp.get(base), bid);
        }
        return parseTracks(answer, title(page), base);
    }

    /** The chapters are parts of one MP3 of the book. */
    @Override
    public boolean downloadsSharedFiles() {
        return true;
    }

    @Override
    public ArrayList<SeriesPOJO> series(Document page, String bookUrl) {
        return parseSeries(page);
    }

    @Override
    public boolean findsOtherReaders() {
        return true;
    }

    /** The book page itself tells whether there are other narrations ({@link #parseDescription}). */
    @Override
    public boolean probesOtherReaders() {
        return false;
    }

    /** The "Другая озвучка" ("Другие озвучки") block of the book page. */
    @Override
    public ArrayList<OtherArtistPOJO> otherReaders(String bookUrl, String title, String author, String reader)
            throws IOException {
        return parseOtherReaders(fetch(bookUrl));
    }

    /** Title and author: every word must match, and a reader of another site rarely reads here. */
    @Override
    public String otherSourceTerms(BookPOJO book) {
        return OtherSourceModel.query(book.getName(), book.getAutor());
    }

    // ---------------------------------------------------------------- requests

    private static String playlist(Document page, String bid) throws IOException {
        String key = Akniga.securityKey(page);
        return SiteHttp.body(SiteHttp.connect(SERVER + "/ajax/mbid/" + bid)
                .method(Connection.Method.POST)
                .data("bid", bid)
                .data("hash", hash(key))
                .data("security_ls_key", key));
    }

    /** What the player sends to prove the session key: the key as a JSON string, encrypted. */
    static String hash(String key) {
        return CryptoJs.encrypt(new JsonPrimitive(key).toString(), PASSPHRASE);
    }

    // ---------------------------------------------------------------- pure parsers

    /** Any listing: index, sorts, genre, author, reader, series and search (also the other-source search). */
    public static ProviderPage<BookPOJO> parseListing(Document doc) {
        Elements cards = doc.select("article.topic");
        return ProviderPage.of(cards(cards, Golosknig::card), nextPage(doc) != null, cards.size());
    }

    /** The pager's next page; null on the last one. */
    @Nullable
    private static String nextPage(Document doc) {
        String next = href(doc, "ul.pagination li.next > a");
        return next.isEmpty() ? null : next;
    }

    @Nullable
    private static BookPOJO card(Element card) {
        Element title = card.selectFirst("h2.topic-title > a");
        if (title == null) {
            return null;
        }
        BookPOJO book = new BookPOJO();
        book.setUrl(title.absUrl("href"));
        // "Title — Author, Author"; a title may have a dash of its own
        String name = title.text().trim();
        String alt = card.select("img.topic_preview").attr("alt").trim();
        if (!name.contains(" — ") && alt.startsWith(name + " — ")) {
            name = alt; // five authors or more: only the cover's "Title — Author,Author,..." names them
        }
        int dash = name.lastIndexOf(" — ");
        if (dash > 0) {
            String authors = COMMA.matcher(name.substring(dash + 3).trim()).replaceAll(", ");
            book.setAutor(authors);
            book.setUrlAutor(SERVER + "/author/" + SearchUrls.segment(authors.split(", ")[0]) + "/");
            name = name.substring(0, dash).trim();
        }
        book.setName(name);
        book.setPhoto(imgSrc(card.selectFirst("img.topic_preview")));
        Element reader = card.selectFirst(".book-performer a[href*=/performer/]");
        if (reader != null) {
            book.setArtist(text(card, ".book-performer")); // "Name" or "Name и др."
            book.setUrlArtist(reader.absUrl("href"));
        }
        book.setTime(iconText(card, "i.fa-clock-o"));
        book.setDesc(text(card, ".topic-content"));
        return book;
    }

    /** One page of {@code /genres/}: name and listing. */
    public static ArrayList<GenrePOJO> parseGenres(Document doc) {
        return Html.rows(doc.select("td.cell-name a.blog-name"), Golosknig::row);
    }

    /** A page of {@code /authors/} or {@code /performers/}; the nameless first author is left out. */
    public static ProviderPage<GenrePOJO> parsePeople(Document doc) {
        Elements rows = doc.select("td.cell-name a.blog-name");
        return ProviderPage.of(Html.rows(rows, Golosknig::row), nextPage(doc) != null, rows.size());
    }

    @Nullable
    private static GenrePOJO row(Element a) {
        String name = a.text().trim();
        return name.isEmpty() ? null : new GenrePOJO(name, a.absUrl("href"));
    }

    public static DescriptionPOJO parseDescription(Document doc) {
        DescriptionPOJO d = new DescriptionPOJO();
        d.setTitle(title(doc));
        d.setPoster(imgSrc(doc.selectFirst(".topic-content-begin img")));
        Element genre = doc.selectFirst(".topic-info a[href*=/genre/]");
        d.setGenre(text(genre));
        d.setGenreUrl(genre == null ? "" : genre.absUrl("href"));
        d.setTime(iconText(doc, ".topic-info i.fa-clock-o"));
        Element authors = labelled(doc, ROWS, "Автор"); // "Автор:", "Авторы:"
        d.setAutor(afterLabel(authors));
        d.setAutorUrl(href(authors, "a[href*=/author/]"));
        Element readers = labelled(doc, ROWS, "Исполня"); // "Исполняет:", "Исполняют:"
        d.setArtist(afterLabel(readers));
        d.setArtistUrl(href(readers, "a[href*=/performer/]"));
        Element series = labelled(doc, ROWS, "Из серии");
        // "Name #1"; the number is the series tab's
        d.setSeries(NUMBER.matcher(text(series, "a[href*=/series/]")).replaceFirst(""));
        d.setSeriesUrl(href(series, "a[href*=/series/]"));
        Element content = doc.selectFirst("article.topic .topic-content");
        if (content != null) {
            content = content.clone();
            content.select(".block-sametitle").remove();
            d.setDescription(content.text().trim());
        }
        d.setOtherReader(!parseOtherReaders(doc).isEmpty());
        return d;
    }

    private static String title(Document doc) {
        Element og = doc.selectFirst("meta[property=og:title]");
        return og == null ? "" : og.attr("content").trim();
    }

    /** "Другие произведения автора" and "Еще из раздела": title, cover and (in the second) the author. */
    public static ArrayList<BookPOJO> parseRecommendations(Document doc) {
        return cards(doc.select("div.books-block-desc"), item -> {
            BookPOJO book = new BookPOJO();
            book.setUrl(href(item, "a[href]"));
            book.setName(text(item, ".books-block-title strong"));
            book.setAutor(text(item, ".books-block-title small"));
            book.setPhoto(imgSrc(item.selectFirst("img")));
            return book;
        });
    }

    /** The series block of a book page: every book with its number ("" for the cycle as a whole). */
    public static ArrayList<SeriesPOJO> parseSeries(Document doc) {
        return each(doc.select("div.series li"), li -> {
            SeriesPOJO item = Html.seriesItem(li.selectFirst("a[href]"), null);
            if (item != null) {
                item.setNumber(text(li, "span.text-muted").replace(".", ""));
            }
            return item;
        });
    }

    /** "Другая озвучка": the reader and year of each other narration, with its page. */
    public static ArrayList<OtherArtistPOJO> parseOtherReaders(Document doc) {
        return each(doc.select(".block-sametitle a[href]"), a -> {
            String url = a.absUrl("href");
            if (url.isEmpty()) {
                return null;
            }
            OtherArtistPOJO other = new OtherArtistPOJO();
            other.setUrl(url);
            other.setName(a.text().trim());
            return other;
        });
    }

    /**
     * The player id of a book page. A page that shows the site's notice instead of the player (closed by the rights
     * holder, sold on LitRes, unavailable) is a {@link BookRemovedException}.
     */
    public static String bid(Document doc, String bookUrl) {
        for (Element script : doc.select("script")) {
            Matcher m = PLAYER.matcher(script.data());
            if (m.find()) {
                return m.group(1);
            }
        }
        if (doc.selectFirst("article.topic > header.topic-header + div[style*=font-weight]") != null) {
            throw new BookRemovedException(bookUrl);
        }
        throw new IllegalStateException("no player on " + bookUrl);
    }

    /**
     * The {@code /ajax/mbid/<bid>} answer: {@code res}, the encrypted MP3 URL (a JSON string), and {@code items}, the
     * chapters as a JSON string of {@code {title, time_from_start, time_finish, duration}}.
     */
    public static ArrayList<AudioPOJO> parseTracks(String json, String bookName, String bookUrl) {
        JsonObject answer = Json.object(json);
        String res = Json.string(answer, "res");
        String items = Json.string(answer, "items");
        if (res.isEmpty() || items.isEmpty()) {
            throw new IllegalStateException("golosknig playlist refused: " + json);
        }
        String mp3 = Json.string(Json.parse(CryptoJs.decrypt(res, PASSPHRASE)));
        String version = Json.string(answer, "version");
        if (version.matches("\\d+") && Integer.parseInt(version) > 1) {
            mp3 += "&v=" + version; // as the player asks a re-uploaded file
        }
        Chapters chapters = new Chapters(bookName, bookUrl);
        Akniga.addClips(chapters, Json.array(items), mp3);
        return chapters.done("golosknig playlist");
    }
}
