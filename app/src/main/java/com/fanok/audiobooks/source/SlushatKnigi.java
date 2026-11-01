package com.fanok.audiobooks.source;

import static com.fanok.audiobooks.source.Html.href;
import static com.fanok.audiobooks.source.Html.labelled;
import static com.fanok.audiobooks.source.Html.text;

import androidx.annotation.Nullable;

import com.fanok.audiobooks.BookRemovedException;
import com.fanok.audiobooks.net.JsonPage;
import com.fanok.audiobooks.net.SiteHttp;
import com.fanok.audiobooks.pojo.AudioPOJO;
import com.fanok.audiobooks.pojo.BookPOJO;
import com.fanok.audiobooks.pojo.ComentsPOJO;
import com.fanok.audiobooks.pojo.DescriptionPOJO;
import com.fanok.audiobooks.pojo.GenrePOJO;
import com.fanok.audiobooks.pojo.SeriesPOJO;
import com.fanok.audiobooks.search.ProviderPage;
import com.fanok.audiobooks.search.SearchUrls;

import io.reactivex.functions.BooleanSupplier;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * slushat-knigi.com: DataLife Engine, template "audiobooksdark" (probed live, 2026-10-31). Plain GETs, no cookies.
 * <ul>
 * <li>new: {@link #INDEX} ({@code /lastnews/}, then {@code /page/N/}; the home page is two fixed blocks), 12 cards a
 * page, pager {@code .pagination__pages}, a page past the end is a 404. Blog, quote and "what famous people read" posts
 * are mixed in; only {@code /<id>-audiokniga-<slug>.html} cards are books.</li>
 * <li>sorts: the DLE sort form is absent and a POSTed sort is ignored, here and in a genre. Besides "new" only the
 * fixed one-page {@link #TOP_100} (views) and {@link #RATING} (30 books), site-wide; the cards of both carry the author
 * instead of the genre, so a genre has no sorts. No periods.</li>
 * <li>genres: the header menu of every page; a genre is {@code /<slug>/} with the index's paging.</li>
 * <li>search: {@code /index.php?do=search&subaction=search&story=Q&search_start=N}, 12 cards, at least 4 letters
 * (a shorter query finds nothing). The hit count and its pager stop at 72 (6 pages), the pages do not ("Кинг": 33
 * pages, then a page without cards).</li>
 * <li>author {@code /tags/<name>/} and series {@code /xfsearch/series/<name>/} (newest first, no numbers) page like the
 * index; readers are not linked. The authors index is one page of 4.7 MB: none is offered.</li>
 * <li>book {@code /<id>-audiokniga-<slug>.html}: the "Информация о книге" rows, the genre's top three in the sidebar,
 * comments 30 a page (oldest first; page N by {@code /engine/ajax/controller.php?mod=comments&cstart=N}, JSON).
 * Playerjs {@code file} is a JSON playlist on a redirectto.cc host (a CDN of several sites), one MP3 per chapter
 * without durations; the playlist and the MP3s answer 403 unless the Referer is on this site (the book page is).</li>
 * </ul>
 */
public final class SlushatKnigi implements SourceParser {

    public static final String SERVER = "https://slushat-knigi.com";
    public static final String INDEX = SERVER + "/lastnews/";
    public static final String TOP_100 = SERVER + "/top.html";
    public static final String RATING = SERVER + "/rating.html";
    /** The genres are the header menu of every page; this one (the advanced search) has no cards. */
    public static final String GENRES = SERVER + "/index.php?do=search&mode=advanced";

    /** Bound of the series listing pages read for a book's series tab. */
    static final int MAX_SERIES_PAGES = 5;

    private static final List<Sort.Entry> MENU = Sort.menu(Sort.NEW, Sort.TOP_100, Sort.RATING);
    private static final Pattern BOOK = Pattern.compile("^https?://[^/]+/\\d+-audiokniga-");
    /** A card's author part with a trailing volume number: author, "(… N)". */
    private static final Pattern VOLUME = Pattern.compile("(.*\\S)\\s*(\\([^()]*\\d[^()]*\\))");
    /** Menu entries that are no audiobook genres: blog, quotes, "what famous people read". */
    private static final Set<String> NOT_GENRES = new HashSet<>(Arrays.asList(SERVER + "/blog/",
            SERVER + "/tsitatu/", SERVER + "/chto-chitayut-izvestnie-lyudi/"));
    private static final String CARDS = "a.poster-item.grid-item";
    /** Cards on a full search page. */
    private static final int SEARCH_PAGE = 12;
    private static final String GENRE_LINKS = "ul.header__menu-hidden > li > a";
    private static final String DETAILS = "ul.page__details-list > li";

    // ---------------------------------------------------------------- SourceParser

    @Override
    public ProviderPage<BookPOJO> listing(Document page, String url, int pageNo) {
        return parseListing(page, pageNo);
    }

    /** The tops are one page; every other listing pages as DLE does. */
    @Override
    public String pageUrl(String url, int page) {
        return isTop(url) || url.equals(GENRES) ? url : Dle.pageUrl(url, page);
    }

    @Override
    public List<Sort.Entry> sorts(String url) {
        return url.startsWith(INDEX) || isTop(url) ? MENU : Collections.<Sort.Entry>emptyList();
    }

    @Override
    public String sortUrl(Sort sort, String listingUrl) {
        switch (sort) {
            case NEW: return INDEX;
            case TOP_100: return TOP_100;
            case RATING: return RATING;
            default: return null;
        }
    }

    @Override
    public ProviderPage<GenrePOJO> genres(Document page, String url, int pageNo) {
        return ProviderPage.of(parseGenres(page), false);
    }

    @Override
    public String searchUrl(SearchUrls.Category category, String query, int page) {
        return category == SearchUrls.Category.BOOKS ? SERVER + "/index.php?do=search&subaction=search&story="
                + SearchUrls.enc(query) + "&search_start=" + page : null;
    }

    @Override
    public ProviderPage<BookPOJO> searchResults(Document page, int pageNo) {
        return parseSearch(page, pageNo);
    }

    @Override
    public DescriptionPOJO description(Document page, String bookUrl) {
        return parseDescription(page);
    }

    @Override
    public ArrayList<BookPOJO> recommendations(Document page, String bookUrl) {
        return parseRecommendations(page, bookUrl);
    }

    @Override
    public ArrayList<AudioPOJO> tracks(Document page, String base) throws IOException {
        String playlist = playlistUrl(page, base);
        return parseTracks(SiteHttp.body(SiteHttp.connect(playlist).referrer(base)), playlist,
                parseDescription(page).getTitle());
    }

    /** The series listing, oldest first (the site lists it newest first). */
    @Override
    public ArrayList<SeriesPOJO> series(Document page, String bookUrl) throws IOException {
        ArrayList<SeriesPOJO> result = new ArrayList<>();
        String series = href(detail(page, "Серия"), "a");
        for (int no = 1; !series.isEmpty() && no <= MAX_SERIES_PAGES; no++) {
            ProviderPage<BookPOJO> books = parseListing(fetch(pageUrl(series, no)), no);
            result.addAll(seriesItems(books.items));
            if (!books.hasMore) {
                break;
            }
        }
        Collections.reverse(result);
        return result;
    }

    /** Page 1 is on the book page, the others come from the comments AJAX; newest first, like the other DLE sites. */
    @Override
    public ArrayList<ComentsPOJO> comments(String bookUrl, BooleanSupplier cancelled) throws Exception {
        Document page = fetch(bookUrl);
        ArrayList<ComentsPOJO> result = parseComments(page);
        String newsId = Dle.newsId(bookUrl);
        int pages = commentPages(page);
        for (int no = 2; no <= pages && !cancelled.getAsBoolean(); no++) {
            result.addAll(parseCommentsPage(SiteHttp.getJson(commentsUrl(newsId, no))));
        }
        Collections.reverse(result);
        return result;
    }

    // ---------------------------------------------------------------- URLs

    private static boolean isTop(String url) {
        return url.equals(TOP_100) || url.equals(RATING);
    }

    /** Comments page {@code page} of the book {@code newsId} as JSON {@code {navigation, comments}}. */
    public static String commentsUrl(String newsId, int page) {
        return SERVER + "/engine/ajax/controller.php?mod=comments&cstart=" + page + "&news_id=" + newsId
                + "&skin=audiobooksdark&massact=disable";
    }

    // ---------------------------------------------------------------- listings

    /**
     * Any listing: index, genre, tops, author, series and the cards of a search page. A genre's
     * "best of the genre" block ({@code .sect--top}, the same on every page) is not the listing.
     */
    public static ProviderPage<BookPOJO> parseListing(Document doc, int page) {
        List<Element> raw = new ArrayList<>();
        for (Element card : doc.select(CARDS)) {
            if (card.closest(".sect--top") == null) {
                raw.add(card);
            }
        }
        Map<String, String> genres = genreUrls(doc);
        boolean authorMeta = isTop(doc.location());
        ArrayList<BookPOJO> books = Html.cards(raw, card -> card(card, genres, authorMeta));
        int last = Html.lastPage(doc.select(".pagination__pages a, .pagination__pages span"));
        return ProviderPage.of(books, last > page, raw.size());
    }

    /** A search page: its pager ends at page 6 whatever the hits, so a full page of cards has more too. */
    public static ProviderPage<BookPOJO> parseSearch(Document doc, int page) {
        ProviderPage<BookPOJO> listing = parseListing(doc, page);
        int cards = doc.select(CARDS).size();
        return ProviderPage.of(listing.items, listing.hasMore || cards >= SEARCH_PAGE, cards);
    }

    /**
     * A card {@code <a href>} of a book, null for another post. Its title is "Title - Author, Author"; the meta line is
     * the genres ("A / B"), on the tops the author.
     */
    @Nullable
    private static BookPOJO card(Element a, Map<String, String> genreUrls, boolean authorMeta) {
        String url = a.absUrl("href");
        if (!BOOK.matcher(url).find()) {
            return null;
        }
        BookPOJO book = new BookPOJO();
        book.setUrl(url);
        String[] title = splitTitle(text(a, ".poster-item__title"));
        book.setName(title[0]);
        book.setAutor(title[1]);
        Element img = a.selectFirst("img[data-src]");
        if (img != null) {
            book.setPhoto(img.absUrl("data-src"));
        }
        Element label = a.selectFirst(".poster-item__label");
        if (label != null) {
            book.setTime(label.ownText().trim());
        }
        String meta = text(a, ".poster-item__meta");
        if (!authorMeta && !meta.isEmpty()) {
            book.setGenre(meta);
            String first = meta.split(" / ")[0];
            book.setUrlGenre(genreUrls.get(first));
        }
        return book;
    }

    /**
     * "Title - Author" split at the last " - "; the author is "" when there is none. A volume number after the author
     * ("Нора Сакавич (2)", "Илона Шикова (книга 4)") belongs to the title.
     */
    private static String[] splitTitle(String title) {
        int dash = title.lastIndexOf(" - ");
        if (dash <= 0) {
            return new String[]{title, ""};
        }
        String name = title.substring(0, dash).trim();
        String author = title.substring(dash + 3).trim();
        Matcher volume = VOLUME.matcher(author);
        return volume.matches() ? new String[]{name + " " + volume.group(2), volume.group(1)}
                : new String[]{name, author};
    }

    // ---------------------------------------------------------------- genres

    /** The header menu: the audiobook genres in menu order, each URL once (two pairs of entries share one). */
    public static ArrayList<GenrePOJO> parseGenres(Document doc) {
        ArrayList<GenrePOJO> genres = new ArrayList<>();
        Set<String> seen = new HashSet<>(NOT_GENRES);
        for (Element a : doc.select(GENRE_LINKS)) {
            String url = a.absUrl("href");
            String name = a.text().trim();
            if (!url.isEmpty() && !name.isEmpty() && seen.add(url)) {
                genres.add(new GenrePOJO(name, url));
            }
        }
        return genres;
    }

    /** Genre name → listing, from the menu of the same page: the cards name their genre without a link. */
    private static Map<String, String> genreUrls(Document doc) {
        Map<String, String> urls = new HashMap<>();
        for (Element a : doc.select(GENRE_LINKS)) {
            urls.putIfAbsent(a.text().trim(), a.absUrl("href"));
        }
        return urls;
    }

    // ---------------------------------------------------------------- book page

    public static DescriptionPOJO parseDescription(Document doc) {
        DescriptionPOJO description = new DescriptionPOJO();
        String title = text(detail(doc, "Название"));
        if (title.isEmpty()) {
            // h1: Слушать книгу - "Title - Author"[edit]
            title = text(doc, "h1").replaceFirst("^[^\"]*\"", "").replaceFirst("\"\\s*\\[edit]$", "");
        }
        description.setTitle(title);
        Element poster = doc.selectFirst(".page__poster[data-poster]");
        if (poster != null) {
            description.setPoster(poster.absUrl("data-poster"));
        }
        Element author = detail(doc, "Автор");
        description.setAutor(text(author));
        description.setAutorUrl(href(author, "a"));
        description.setArtist(text(detail(doc, "Озвучивает")));
        Element genre = detail(doc, "Жанр");
        description.setGenre(text(genre));
        description.setGenreUrl(href(genre, "a"));
        Element series = detail(doc, "Серия");
        description.setSeries(text(series));
        description.setSeriesUrl(href(series, "a"));
        description.setTime(text(detail(doc, "Время озвучки")));
        String rating = text(doc, ".page__rating-item--audience > div");
        if (!rating.isEmpty() && !rating.equals("0")) {
            description.setReiting(rating);
        }
        description.setDescription(text(doc, ".page__text.js-hide-text"));
        return description;
    }

    /** The value of the details row {@code label} ("Автор", "Время озвучки", ...); null when the book has none. */
    @Nullable
    private static Element detail(Document doc, String label) {
        Element row = labelled(doc, DETAILS, label);
        return row == null || row.children().size() < 2 ? null : row.child(1);
    }

    /** The sidebar's "best books of the genre", without the open book. */
    public static ArrayList<BookPOJO> parseRecommendations(Document doc, String bookUrl) {
        Map<String, String> genres = genreUrls(doc);
        ArrayList<BookPOJO> books = Html.cards(doc.select(".page__side-block a.ftop-item"),
                card -> card(card, genres, false));
        books.removeIf(book -> book.getUrl().equals(bookUrl));
        return books;
    }

    /** One page of a series listing as series entries (no numbers on this site), in the page's order. */
    public static ArrayList<SeriesPOJO> parseSeries(Document doc) {
        return seriesItems(parseListing(doc, 1).items);
    }

    private static ArrayList<SeriesPOJO> seriesItems(List<BookPOJO> books) {
        ArrayList<SeriesPOJO> series = new ArrayList<>();
        for (BookPOJO book : books) {
            SeriesPOJO item = new SeriesPOJO();
            item.setUrl(book.getUrl());
            item.setName(book.getName());
            item.setPhoto(book.getPhoto());
            series.add(item);
        }
        return series;
    }

    /** The comment pages of a book page (its comments pager); 1 without one. */
    public static int commentPages(Document doc) {
        return Math.max(1, Html.lastPage(doc.select(".dle-comments-navigation .pagination__pages a, "
                + ".dle-comments-navigation .pagination__pages span")));
    }

    /** The comments AJAX answer: its {@code comments} HTML. */
    public static ArrayList<ComentsPOJO> parseCommentsPage(JsonPage page) {
        String html = Json.string(JsonPage.of(page).getAsJsonObject(), "comments");
        return parseComments(Jsoup.parseBodyFragment(html, page.location()));
    }

    /** The comments under {@code scope} (a book page or an AJAX page), in its order; a reply quotes. */
    public static ArrayList<ComentsPOJO> parseComments(Element scope) {
        ArrayList<ComentsPOJO> out = new ArrayList<>();
        for (Element comment : scope.select("div[id^=comment-id-]")) {
            Element body = comment.selectFirst("div[id^=comm-id-]");
            if (body == null) {
                continue;
            }
            ComentsPOJO pojo = new ComentsPOJO();
            pojo.setName(text(comment, ".comment-item__author"));
            pojo.setDate(text(comment, ".comment-item__date"));
            Element avatar = comment.selectFirst(".comment-item__img img");
            String image = avatar == null ? "" : avatar.absUrl("src");
            pojo.setImage(image.contains("noavatar") ? "" : image);
            Element quote = body.selectFirst(".quote");
            if (quote != null) {
                pojo.setQuoteText(quote.text());
                pojo.setQuoteName(text(body, ".title_quote").replace("Цитата:", "").trim());
                Element own = body.clone();
                own.select(".quote, .title_quote").remove();
                pojo.setText(own.text().isEmpty() ? quote.text() : own.text());
            } else {
                pojo.setText(body.text());
            }
            if (!pojo.isEmty()) {
                out.add(pojo);
            }
        }
        return out;
    }

    /**
     * The Playerjs playlist URL of a book page. No takedown notice was found on the site; a page that shows one
     * instead of the player is a {@link BookRemovedException}, any other page without the player an
     * {@link IllegalStateException}.
     */
    public static String playlistUrl(Document doc, String bookUrl) {
        if (doc.select("script:containsData(new Playerjs)").isEmpty()) {
            if (BookRemovedException.isNotice(doc.text())) {
                throw new BookRemovedException(bookUrl);
            }
            throw new IllegalStateException("no player on " + bookUrl);
        }
        return Players.resolve(bookUrl, Players.playerjsFile(doc));
    }

    /** The playlist {@code [{title, file}]}: one MP3 per chapter, no durations. */
    public static ArrayList<AudioPOJO> parseTracks(String json, String playlistUrl, String bookName) {
        return Players.parsePlayerjsPlaylist(json, playlistUrl, bookName);
    }
}
