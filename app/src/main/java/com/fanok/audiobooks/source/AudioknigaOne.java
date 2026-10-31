package com.fanok.audiobooks.source;

import static com.fanok.audiobooks.source.Html.text;
import static com.fanok.audiobooks.source.Json.seconds;
import static com.fanok.audiobooks.source.Json.string;

import androidx.annotation.Nullable;

import com.fanok.audiobooks.BookRemovedException;
import com.fanok.audiobooks.R;
import com.fanok.audiobooks.model.ComentsModel;
import com.fanok.audiobooks.net.JsonPage;
import com.fanok.audiobooks.net.SiteHttp;
import com.fanok.audiobooks.pojo.AudioPOJO;
import com.fanok.audiobooks.pojo.BookPOJO;
import com.fanok.audiobooks.pojo.ComentsPOJO;
import com.fanok.audiobooks.pojo.ContentParentPOJO;
import com.fanok.audiobooks.pojo.DescriptionPOJO;
import com.fanok.audiobooks.pojo.GenrePOJO;
import com.fanok.audiobooks.pojo.SeriesPOJO;
import com.fanok.audiobooks.search.ListingFilters;
import com.fanok.audiobooks.search.ProviderPage;
import com.fanok.audiobooks.search.SearchUrls;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import io.reactivex.functions.BooleanSupplier;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.HttpUrl;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

/**
 * audiokniga.one: DataLife Engine, template "tema-23" (probed live, 2026-10-31). Plain GETs, no Referer or cookies
 * needed. A listing page holds up to 16 {@code div.short-item} cards (a ranking often fewer, so only the
 * {@code .navigation} pager tells the end) and pages as {@code <listing>page/N/}.
 * <ul>
 * <li>index {@code /}; sorts are the DLE form POSTed to the page ({@code dlenewssortby} news_read, rating, comm_num,
 * title; date is the plain order). The choice sticks to the PHPSESSID session and comes back on a later plain index
 * page (no other listing), so {@link #fetch} sends every index page with its sort. One page each, no pager:
 * {@link #TOP_100}, {@link #TOP_WEEK} (the best books added this week, by likes), {@link #TOP_MONTH}.</li>
 * <li>genres {@link #GENRES}, a genre {@code /<slug>/} or {@code /<parent>/<slug>/}. A genre, author or reader ranks
 * itself by URL: {@code popular/} (views, all time), {@code popular/day|week|month/}, {@code rating/} (likes); the
 * POSTed sort is ignored there. {@code ?fd=x|s|m|l} bounds the duration (under 2 h, 2-5 h, 5-10 h, over 10 h); the
 * pager drops it, {@link Dle#pageUrl} keeps it.</li>
 * <li>authors {@link #AUTHORS}, readers {@link #READERS}: indexes of 16 rows a page; a person
 * {@code /xfsearch/avtor|ispolnitel/<name>/}, a series {@code /xfsearch/serie/<name>/} (by its numbers, no sorts).
 * People search: {@code /engine/ajax/controller.php?mod=site_search&t=author|reader&q=Q&off=N}, JSON, 30 a call.</li>
 * <li>search {@code /index.php?do=search&subaction=search&story=Q&search_start=N}: {@code search_start} is an offset
 * of 16 cards, "найдено" stops at 1000.</li>
 * <li>book {@code /<id>-<slug>.html} (only the id counts): chapters inline in
 * {@code playerInit(id, 'title', 'json', [...])}, one file each with its duration: an MP3 (s3.regru.cloud), or an
 * HLS {@code index.m3u8} (s3.regru.cloud, hls-cloud.net: CDNs other sites share) whose AES-128 key comes from this
 * host; no headers needed. A book sold through LitRes has only its {@code .litres-player} fragment; such books are
 * listed under {@link #PAID} (cards without a time, no rankings) and in the genres. Comments: page 1
 * inline (a DLE tree, newest first), the others from {@code mod=comments&cstart=N} as JSON. The series tab reads the
 * series listing (the page's own series block shows only a few neighbours).</li>
 * </ul>
 */
public final class AudioknigaOne implements SourceParser {

    public static final String SERVER = "https://audiokniga.one";
    public static final String INDEX = SERVER + "/";
    public static final String GENRES = SERVER + "/genres.html";
    public static final String AUTHORS = SERVER + "/xfsearch/avtor/";
    public static final String READERS = SERVER + "/xfsearch/ispolnitel/";
    public static final String TOP_100 = SERVER + "/top-audioknig.html";
    public static final String TOP_WEEK = SERVER + "/top-za-nedelu.html";
    public static final String TOP_MONTH = SERVER + "/top-za-mesjac.html";
    /** The books sold through LitRes, fragments only: {@code /paid/<genre>/}. */
    static final String PAID = SERVER + "/paid/";

    static final int SEARCH_PAGE_SIZE = 16;
    static final int PEOPLE_SEARCH_PAGE_SIZE = 30;
    /** Bound of the pages read for a book's series tab (16 books each). */
    static final int MAX_SERIES_PAGES = 10;
    /** Bound of the comment pages read (10 threads each). */
    static final int MAX_COMMENT_PAGES = 50;

    private static final List<Sort.Entry> MENU = Collections.unmodifiableList(Arrays.asList(Sort.item(Sort.NEW),
            Sort.item(Sort.POPULAR),
            Sort.submenu(R.string.reting, Sort.RATING_WEEK, Sort.RATING_MONTH, Sort.RATING_ALL_TIME),
            Sort.item(Sort.COMMENTS), Sort.item(Sort.TITLE), Sort.item(Sort.TOP_100)));
    /** A genre, author or reader listing ranks itself by URL. */
    private static final List<Sort.Entry> RANKED_MENU = Collections.unmodifiableList(Arrays.asList(
            Sort.item(Sort.NEW),
            Sort.submenu(R.string.popular, Sort.POPULAR_DAY, Sort.POPULAR_WEEK, Sort.POPULAR_MONTH,
                    Sort.POPULAR_ALL_TIME),
            Sort.item(Sort.RATING)));

    private static final String HOST = HttpUrl.get(SERVER).host();
    private static final Pattern INDEX_PATH = Pattern.compile("^/(page/(\\d+)/?)?$");
    /**
     * A genre or person listing (group 1, its root) and its ranking or page; not a tag, a series, the index or the
     * LitRes section {@code /paid/} (it ignores the rankings).
     */
    private static final Pattern RANKED = Pattern.compile("^(/(?:xfsearch/(?:avtor|ispolnitel)/[^/]+"
            + "|(?!(?:xfsearch|tags|user|page|paid)/)[a-z0-9-]+(?:/(?!(?:popular|rating|best|page)/)[a-z0-9-]+)?)/)"
            + "(popular/(?:(?:day|week|month|all)/)?|rating/)?(?:page/\\d+/)?$");
    private static final Pattern SEARCH_FOUND =
            Pattern.compile("найдено (\\d+) ответов \\(Результаты запроса \\d+ - (\\d+)\\)");
    private static final Pattern SERIES_NUMBER = Pattern.compile("\\(#(\\d+)\\)");
    private static final Pattern COMMENT_PAGES = Pattern.compile("total_comments_pages\\s*=\\s*'(\\d+)'");
    private static final Pattern NUMBER = Pattern.compile("(\\d+)");

    // ---------------------------------------------------------------- SourceParser

    /**
     * Sorted index URLs are POSTed, and so is every plain index page, with the date sort: the session's last sort
     * would come back otherwise. People search answers JSON.
     */
    @Override
    public Document fetch(String url) throws IOException {
        if (url.contains("mod=site_search")) {
            return SiteHttp.getJson(url);
        }
        if (url.contains(Dle.SORT_PARAM + "=")) {
            return Dle.fetch(url);
        }
        int index = indexPage(url);
        return index > 0 ? Dle.fetch(Dle.pageUrl(sortedIndex(Dle.Order.DATE), index)) : SiteHttp.get(url);
    }

    @Override
    public ProviderPage<BookPOJO> listing(Document page, String url, int pageNo) {
        return parseListing(page, pageNo);
    }

    @Override
    public String pageUrl(String url, int page) {
        // the tops and the genre list are single .html pages, which answer 404 with a slash after them
        return url.endsWith(".html") ? url : Dle.pageUrl(url, page);
    }

    /** The index, its sorts and top pages; a genre, author or reader ranks itself; nothing else sorts. */
    @Override
    public List<Sort.Entry> sorts(String url) {
        if (rankedRoot(url) != null) {
            return RANKED_MENU;
        }
        boolean siteWide = indexPage(url) > 0 || url.contains(Dle.SORT_PARAM + "=") || url.equals(TOP_100)
                || url.equals(TOP_WEEK) || url.equals(TOP_MONTH);
        return siteWide ? MENU : Collections.<Sort.Entry>emptyList();
    }

    /**
     * A genre, author or reader listing ranks its own root, and stays itself (with its duration bound) for the sort it
     * already has; any other listing gets the site-wide one.
     */
    @Override
    public String sortUrl(Sort sort, String listingUrl) {
        String root = rankedRoot(listingUrl);
        if (root == null) {
            return siteWide(sort);
        }
        String url = ranked(root, sort);
        return url != null && url.equals(SERVER + HttpUrl.get(listingUrl).encodedPath()) ? listingUrl : url;
    }

    /** The LitRes section's cards carry no time. */
    @Override
    public boolean listsDuration(String url) {
        return !url.startsWith(PAID);
    }

    /**
     * {@code fd} on a genre, author or reader listing, when {@code duration} lies within one of the server's four
     * ranges: the server then never drops a match, and the cards are filtered exactly afterwards.
     */
    @Override
    public String withDuration(String url, ListingFilters.Duration duration) {
        if (rankedRoot(url) == null) {
            return url;
        }
        String fd = null;
        if (duration.toMinutes <= 120) {
            fd = "x";
        } else if (duration.fromMinutes >= 600) {
            fd = "l";
        } else if (duration.fromMinutes >= 300 && duration.toMinutes <= 600) {
            fd = "m";
        } else if (duration.fromMinutes >= 120 && duration.toMinutes <= 300) {
            fd = "s";
        }
        HttpUrl.Builder builder = HttpUrl.get(url).newBuilder().removeAllQueryParameters("fd");
        return (fd == null ? builder : builder.addQueryParameter("fd", fd)).build().toString();
    }

    @Override
    public ProviderPage<GenrePOJO> genres(Document page, String url, int pageNo) {
        return parseGenres(page);
    }

    @Override
    public ProviderPage<GenrePOJO> people(Document page, String url, int pageNo, boolean performers) {
        return parsePeople(page, pageNo);
    }

    @Override
    public String searchUrl(SearchUrls.Category category, String query, int page) {
        String q = SearchUrls.enc(query);
        switch (category) {
            case BOOKS:
                return SERVER + "/index.php?do=search&subaction=search&story=" + q + "&search_start="
                        + (page - 1) * SEARCH_PAGE_SIZE + "&full_search=0";
            case AUTHORS:
                return peopleSearchUrl("author", q, page);
            case PERFORMERS:
                return peopleSearchUrl("reader", q, page);
            default:
                return null;
        }
    }

    @Override
    public ProviderPage<BookPOJO> searchResults(Document page, int pageNo) {
        return parseSearch(page);
    }

    @Override
    public ProviderPage<GenrePOJO> searchPeople(Document page, int pageNo, boolean performers) {
        return parsePeopleSearch(page);
    }

    @Override
    public DescriptionPOJO description(Document page, String bookUrl) {
        return parseDescription(page);
    }

    /** The card of a deep link: the page's fields plus the likes and the comment count. */
    @Override
    public BookPOJO book(Document page, String bookUrl) {
        return parseBook(page, bookUrl);
    }

    @Override
    public ArrayList<BookPOJO> recommendations(Document page, String bookUrl) {
        return parseRecommendations(page);
    }

    @Override
    public ArrayList<AudioPOJO> tracks(Document page, String base) {
        return parseTracks(page, base);
    }

    /** The series listing linked from the book page, all its pages, by the site's order (the series numbers). */
    @Override
    public ArrayList<SeriesPOJO> series(Document page, String bookUrl) throws IOException {
        ArrayList<SeriesPOJO> result = new ArrayList<>();
        String series = seriesUrl(page);
        for (int no = 1; !series.isEmpty() && no <= MAX_SERIES_PAGES; no++) {
            Document doc = fetch(pageUrl(series, no));
            result.addAll(parseSeries(doc));
            if (lastPage(doc) <= no) {
                break;
            }
        }
        return result;
    }

    /** Page 1 of the comments is on the book page, the others come from the site's comments XHR; oldest first. */
    @Override
    public ArrayList<ComentsPOJO> comments(String bookUrl, BooleanSupplier cancelled) throws Exception {
        Document page = fetch(bookUrl);
        ArrayList<ComentsPOJO> comments = parseComments(page);
        int pages = Math.min(commentPages(page), MAX_COMMENT_PAGES);
        for (int no = 2; no <= pages && !cancelled.getAsBoolean(); no++) {
            ArrayList<ComentsPOJO> more = parseCommentsPage(SiteHttp.getJson(commentsUrl(bookUrl, no)));
            if (more.isEmpty()) {
                break;
            }
            comments.addAll(more);
        }
        Collections.reverse(comments);
        return comments;
    }

    // ---------------------------------------------------------------- URLs

    /** The index sorted by {@code order}, as {@link Dle#fetch} POSTs it. */
    private static String sortedIndex(Dle.Order order) {
        return Dle.sorted(INDEX, order, "main");
    }

    /** The page number of a plain index URL ({@code /}, {@code /page/N/}); 0 for any other URL. */
    private static int indexPage(String url) {
        HttpUrl parsed = HttpUrl.parse(url);
        if (parsed == null || parsed.query() != null || !parsed.host().equals(HOST)) {
            return 0;
        }
        Matcher m = INDEX_PATH.matcher(parsed.encodedPath());
        return !m.matches() ? 0 : m.group(2) == null ? 1 : Integer.parseInt(m.group(2));
    }

    /** The root ({@code https://audiokniga.one/<genre>/}) of a genre, author or reader listing or ranking, or null. */
    @Nullable
    static String rankedRoot(String url) {
        HttpUrl parsed = HttpUrl.parse(url);
        if (parsed == null || !parsed.host().equals(HOST)) {
            return null;
        }
        Matcher m = RANKED.matcher(parsed.encodedPath());
        return m.matches() ? SERVER + m.group(1) : null;
    }

    @Nullable
    private static String ranked(String root, Sort sort) {
        switch (sort) {
            case NEW: return root;
            case POPULAR_DAY: return root + "popular/day/";
            case POPULAR_WEEK: return root + "popular/week/";
            case POPULAR_MONTH: return root + "popular/month/";
            case POPULAR_ALL_TIME: return root + "popular/";
            case RATING: return root + "rating/";
            default: return null;
        }
    }

    @Nullable
    private static String siteWide(Sort sort) {
        switch (sort) {
            case NEW: return INDEX;
            case POPULAR: return sortedIndex(Dle.Order.VIEWS);
            case RATING_WEEK: return TOP_WEEK;
            case RATING_MONTH: return TOP_MONTH;
            case RATING_ALL_TIME: return sortedIndex(Dle.Order.RATING);
            case COMMENTS: return sortedIndex(Dle.Order.COMMENTS);
            case TITLE: return sortedIndex(Dle.Order.TITLE);
            case TOP_100: return TOP_100;
            default: return null;
        }
    }

    private static String peopleSearchUrl(String type, String query, int page) {
        return SERVER + "/engine/ajax/controller.php?mod=site_search&ctx=page&t=" + type + "&q=" + query + "&off="
                + (page - 1) * PEOPLE_SEARCH_PAGE_SIZE;
    }

    static String commentsUrl(String bookUrl, int page) {
        return SERVER + "/engine/ajax/controller.php?mod=comments&cstart=" + page + "&news_id=" + Dle.newsId(bookUrl)
                + "&skin=tema-23&massact=disable";
    }

    // ---------------------------------------------------------------- listings

    /** Index, sorted, top, genre, ranking, author, reader and series pages. */
    public static ProviderPage<BookPOJO> parseListing(Document doc, int page) {
        Elements raw = doc.select("div.short-item");
        return ProviderPage.of(Html.cards(raw, AudioknigaOne::card), lastPage(doc) > page, raw.size());
    }

    /** A search page; more while the "Результаты запроса A - B" end before the count found. */
    public static ProviderPage<BookPOJO> parseSearch(Document doc) {
        Elements raw = doc.select("div.short-item");
        Matcher m = SEARCH_FOUND.matcher(text(doc, "#sp-msg"));
        boolean hasMore = m.find() && Integer.parseInt(m.group(2)) < Integer.parseInt(m.group(1));
        return ProviderPage.of(Html.cards(raw, AudioknigaOne::card), hasMore, raw.size());
    }

    @Nullable
    private static BookPOJO card(Element card) {
        Element title = card.selectFirst("a.short-title");
        if (title == null) {
            return null;
        }
        BookPOJO book = new BookPOJO();
        book.setUrl(title.absUrl("href"));
        book.setName(title.text().trim());
        book.setPhoto(Html.imgSrc(card.selectFirst("a.short-img img")));
        Elements genres = card.select(".icon_genre a");
        book.setGenre(names(genres));
        book.setUrlGenre(listingUrl(genres.first()));
        Elements authors = card.select(".icon_author a");
        book.setAutor(names(authors));
        book.setUrlAutor(listingUrl(authors.first()));
        Elements readers = card.select(".icon_reader a");
        book.setArtist(names(readers));
        book.setUrlArtist(listingUrl(readers.first()));
        Element series = card.selectFirst(".icon_serie a");
        if (series != null) {
            book.setSeries(series.text().trim());
            book.setUrlSeries(listingUrl(series));
        }
        book.setTime(text(card, ".icon_duration"));
        setLikes(book, text(card, "span[data-likes-id]"));
        book.setDesc(text(card, ".hide-on-mobile"));
        return book;
    }

    /** The highest page number of the pager (the current one is a bare span); 0 without one. */
    private static int lastPage(Document doc) {
        return Html.lastPage(doc.select(".navigation a, .navigation span"));
    }

    // ---------------------------------------------------------------- genres, people

    /** {@link #GENRES}: every genre and sub-genre with its book count. */
    public static ProviderPage<GenrePOJO> parseGenres(Document doc) {
        return ProviderPage.of(Html.rows(doc.select("div.genre_list_item"), row -> {
            GenrePOJO genre = row(row.selectFirst("a.genre_list_item_name"));
            if (genre != null) {
                genre.setReting(number(text(row, ".genre_item_books_count b")));
            }
            return genre;
        }), false);
    }

    /** A page of {@link #AUTHORS} or {@link #READERS}: the person's listing and book count. */
    public static ProviderPage<GenrePOJO> parsePeople(Document doc, int page) {
        Elements rows = doc.select("div.item_list_item");
        ArrayList<GenrePOJO> people = Html.rows(rows, row -> {
            GenrePOJO person = row(row.selectFirst("a.item_list_item_name"));
            if (person != null) {
                person.setReting(number(text(row, ".item_stat b")));
            }
            return person;
        });
        return ProviderPage.of(people, lastPage(doc) > page, rows.size());
    }

    /**
     * The people search JSON: {@code {items: [{name, url, n}], more}}; a one-letter query gets only the
     * {@code counts}, all 0.
     */
    public static ProviderPage<GenrePOJO> parsePeopleSearch(Document page) {
        JsonObject answer = JsonPage.of(page).getAsJsonObject();
        if (!answer.has("items") && answer.has("counts")) {
            return ProviderPage.empty();
        }
        ArrayList<GenrePOJO> people = new ArrayList<>();
        for (JsonElement item : Json.arrayOf(answer, "items")) {
            JsonObject o = item.getAsJsonObject();
            String name = string(o, "name");
            String url = Players.resolve(SERVER, string(o, "url"));
            if (!name.isEmpty() && !url.isEmpty()) {
                GenrePOJO person = new GenrePOJO(name, url);
                person.setReting(number(string(o, "n")));
                people.add(person);
            }
        }
        JsonElement more = answer.get("more");
        return ProviderPage.of(people, more != null && more.isJsonPrimitive() && more.getAsBoolean());
    }

    // ---------------------------------------------------------------- book page

    public static DescriptionPOJO parseDescription(Document doc) {
        DescriptionPOJO description = new DescriptionPOJO();
        description.setTitle(text(doc, "h1[itemprop=name]"));
        description.setPoster(Html.imgSrc(doc.selectFirst(".fimg img")));
        // the sidebar lists other books with the same classes
        Element info = doc.selectFirst(".fright");
        Elements genres = links(info, ".icon_genre a");
        description.setGenre(names(genres));
        description.setGenreUrl(listingUrl(genres.first()));
        Elements authors = links(info, ".icon_author a");
        description.setAutor(names(authors));
        description.setAutorUrl(listingUrl(authors.first()));
        Elements readers = links(info, ".icon_reader a");
        description.setArtist(names(readers));
        description.setArtistUrl(listingUrl(readers.first()));
        Element series = info == null ? null : info.selectFirst(".icon_serie a");
        if (series != null) {
            description.setSeries(series.text().trim());
            description.setSeriesUrl(listingUrl(series));
        }
        description.setTime(text(info, ".icon_duration"));
        description.setReiting(rating(doc));
        description.setLike(text(doc, ".frate span[data-likes-id]"));
        description.setDisLike(text(doc, ".frate span[data-dislikes-id]"));
        description.setDescription(annotation(doc.selectFirst("div.fullstory")));
        return description;
    }

    /** A book opened by its link: the card fields, read off the book page. */
    public static BookPOJO parseBook(Document doc, String url) {
        BookPOJO book = BookPOJO.of(parseDescription(doc), url);
        setLikes(book, text(doc, ".frate span[data-likes-id]"));
        book.setComents(text(doc, ".fullstor-title"));
        return book;
    }

    /** "Другие аудиокниги автора" and "Аудиокниги схожие по жанру" (the sidebar's like-styled lists left out). */
    public static ArrayList<BookPOJO> parseRecommendations(Document doc) {
        ArrayList<BookPOJO> books = new ArrayList<>();
        for (Element block : doc.select("div.content__main__audiobook-item")) {
            // the author's other books name their reader, the similar ones their author
            boolean byAuthor = block.selectFirst(".author-other-books") != null;
            books.addAll(Html.cards(block.select("a.content__main__audiobook-item-small"), a -> {
                BookPOJO book = new BookPOJO();
                book.setUrl(a.absUrl("href"));
                Element caption = a.selectFirst(".caption");
                book.setName(caption == null ? "" : caption.ownText().trim()); // without the LitRes badge
                book.setPhoto(Html.imgSrc(a.selectFirst("img")));
                if (byAuthor) {
                    book.setArtist(text(a, ".author"));
                } else {
                    book.setAutor(text(a, ".author"));
                }
                setLikes(book, text(a, "span[data-likes-id]"));
                book.setComents(Html.iconText(a, ".fa-comments"));
                return book;
            }));
        }
        return books;
    }

    /**
     * The chapters of {@code playerInit}: one MP3 or HLS playlist each, with its duration. A book the site only sells
     * through LitRes (a fragment player) or took down is a {@link BookRemovedException}.
     */
    public static ArrayList<AudioPOJO> parseTracks(Document doc, String bookUrl) {
        String list = Players.findPlayerScript(doc, "playerInit(", '[');
        if (list == null) {
            if (doc.selectFirst(".litres-player, .berrors") != null) {
                throw new BookRemovedException(bookUrl);
            }
            throw new IllegalStateException("playerInit not found on " + bookUrl);
        }
        Players.Chapters chapters = new Players.Chapters(text(doc, "h1[itemprop=name]"), bookUrl);
        chapters.each(Json.array(list), item -> {
            JsonObject o = item.getAsJsonObject();
            if (!"ready".equals(string(o, "state")) || !"0".equals(string(o, "error"))) {
                return null; // still being converted, or broken
            }
            return chapters.chapter(string(o, "title"), string(o, "url"), seconds(o.get("duration")));
        });
        return chapters.done("playerInit of " + bookUrl);
    }

    /** The series listing of a book page ({@code /xfsearch/serie/<name>/}); "" when the book is in none. */
    public static String seriesUrl(Document doc) {
        Element info = doc.selectFirst(".fright");
        return listingUrl(info == null ? null : info.selectFirst(".icon_serie a"));
    }

    /** One page of a series listing, with each book's number in the series. */
    public static ArrayList<SeriesPOJO> parseSeries(Document doc) {
        return Html.each(doc.select("div.short-item"), card -> {
            SeriesPOJO item = Html.seriesItem(card.selectFirst("a.short-title"), card.selectFirst("a.short-img img"));
            Matcher n = SERIES_NUMBER.matcher(text(card, ".icon_serie"));
            if (item != null && n.find()) {
                item.setNumber(n.group(1));
            }
            return item;
        });
    }

    // ---------------------------------------------------------------- comments

    /** The comment threads on a book page, newest first; replies of any depth join their thread. */
    public static ArrayList<ComentsPOJO> parseComments(Document doc) {
        return ComentsModel.nest(doc.select("#dle-comments-list li.comments-tree-item"), "li.comments-tree-item",
                AudioknigaOne::comment).comments;
    }

    /** A page of the comments XHR: {@code {"comments": "<ol class=comments-tree-list>…"}}; "" past the last. */
    public static ArrayList<ComentsPOJO> parseCommentsPage(Document json) {
        String html = string(JsonPage.of(json).getAsJsonObject(), "comments");
        return ComentsModel.nest(Jsoup.parseBodyFragment(html, SERVER + "/").select("li.comments-tree-item"),
                "li.comments-tree-item", AudioknigaOne::comment).comments;
    }

    /** The number of comment pages the book page announces; 1 without comments. */
    static int commentPages(Document doc) {
        for (Element script : doc.select("script")) {
            Matcher m = COMMENT_PAGES.matcher(script.data());
            if (m.find()) {
                return Integer.parseInt(m.group(1));
            }
        }
        return 1;
    }

    /** One comment of a thread item: its own block comes before the replies nested in it. */
    private static void comment(Element item, ContentParentPOJO pojo) {
        pojo.setName(text(item, ".comm-author1"));
        pojo.setImage(Html.imgSrc(item.selectFirst(".comm-avatar img")));
        pojo.setDate(text(item, "span[title=Опубликовано]"));
        String likes = text(item, "span[data-comments-likes-id]");
        pojo.setReting(likes.equals("0") ? "" : likes);
        pojo.setText(text(item, "div[id^=comm-id-]"));
    }

    // ---------------------------------------------------------------- helpers

    /** The 1-5 stars of the page's Audiobook ld+json ({@code aggregateRating}); "" without votes or readable data. */
    private static String rating(Document doc) {
        for (Element script : doc.select("script[type=application/ld+json]")) {
            try {
                JsonElement json = Json.parse(script.data());
                JsonElement rating = json.isJsonObject() ? json.getAsJsonObject().get("aggregateRating") : null;
                if (rating != null && rating.isJsonObject()) {
                    JsonObject o = rating.getAsJsonObject();
                    return BookPOJO.formatRating(Float.parseFloat(string(o, "ratingValue")), string(o, "ratingCount"));
                }
            } catch (IllegalStateException | NumberFormatException optional) {
                // the stars are optional: the page shows without them
            }
        }
        return "";
    }

    /** The annotation's paragraphs, one a line. */
    private static String annotation(@Nullable Element story) {
        if (story == null) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        for (Element p : story.select("p")) {
            if (!p.text().trim().isEmpty()) {
                out.append(out.length() == 0 ? "" : "\n").append(p.text().trim());
            }
        }
        return out.length() == 0 ? story.text().trim() : out.toString();
    }

    /** The site's likes (thumbs up); a card without any keeps the "0" the list hides. */
    private static void setLikes(BookPOJO book, String likes) {
        if (!likes.isEmpty()) {
            book.setReting(likes);
        }
    }

    /** A genre, people or series link, encoded like the cards' ones (a book page writes raw spaces); "" when absent. */
    private static String listingUrl(@Nullable Element a) {
        HttpUrl url = a == null ? null : HttpUrl.parse(a.absUrl("href"));
        return url == null ? "" : url.toString();
    }

    /** A genre or person row of an index: its link's name and listing; null without either. */
    @Nullable
    private static GenrePOJO row(@Nullable Element a) {
        String url = listingUrl(a);
        return url.isEmpty() || text(a).isEmpty() ? null : new GenrePOJO(text(a), url);
    }

    private static Elements links(@Nullable Element scope, String css) {
        return scope == null ? new Elements() : scope.select(css);
    }

    private static String names(Elements links) {
        StringBuilder out = new StringBuilder();
        for (Element a : links) {
            out.append(out.length() == 0 ? "" : ", ").append(a.text().trim());
        }
        return out.toString();
    }

    private static int number(String text) {
        Matcher m = NUMBER.matcher(text);
        return m.find() ? Integer.parseInt(m.group(1)) : 0;
    }
}
