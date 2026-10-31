package com.fanok.audiobooks.source;

import static com.fanok.audiobooks.source.Html.afterLabel;
import static com.fanok.audiobooks.source.Html.href;
import static com.fanok.audiobooks.source.Html.iconText;
import static com.fanok.audiobooks.source.Html.imgSrc;
import static com.fanok.audiobooks.source.Html.labelled;
import static com.fanok.audiobooks.source.Html.text;

import androidx.annotation.Nullable;

import com.fanok.audiobooks.BookRemovedException;
import com.fanok.audiobooks.Url;
import com.fanok.audiobooks.model.OtherSourceModel;
import com.fanok.audiobooks.net.SiteHttp;
import com.fanok.audiobooks.pojo.AudioPOJO;
import com.fanok.audiobooks.pojo.BookPOJO;
import com.fanok.audiobooks.pojo.ComentsPOJO;
import com.fanok.audiobooks.pojo.ContentParentPOJO;
import com.fanok.audiobooks.pojo.DescriptionPOJO;
import com.fanok.audiobooks.pojo.GenrePOJO;
import com.fanok.audiobooks.pojo.SeriesPOJO;
import com.fanok.audiobooks.pojo.SubComentsPOJO;
import com.fanok.audiobooks.search.ProviderPage;
import com.fanok.audiobooks.search.SearchUrls;

import io.reactivex.functions.BooleanSupplier;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.HttpUrl;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

/**
 * audiokniga-online.ru: DataLife Engine, template "coolserial-light-qv3" (probed live, 2026-10-31); no challenge, no
 * Referer or cookie needed. {@code www.} and {@code http://} answer 301 to {@link #SERVER} with the path kept.
 * <ul>
 * <li>index {@code /}, then {@code /page/N/} ({@code /page/1/} is a 301 to {@code /}); a genre {@code /<slug>/} or
 * {@code /<parent>/<slug>/} with the same scheme. 10 cards a page; the pager {@code .navigation} has no "next" link,
 * a past-the-end page is a 404. Times are H:MM:SS; the two-part times of the old short stories are MM:SS ("50:44"
 * is one 49 MB file), as {@link BookPOJO#setTime} reads them.</li>
 * <li>sorts: the DLE sort form POSTed to the listing page itself: {@code dlenewssortby} date, rating, news_read
 * (views), comm_num or title, the direction in lower case, keys {@code dle_sort_main}/{@code dle_direction_main},
 * in a genre {@code dle_sort_cat_<id>} ({@link #GENRE_ROWS}). The choice sticks to the PHPSESSID session, so
 * {@link #fetch} sends index and genre pages with their sort. No periods.</li>
 * <li>genres: built in ({@link #GENRE_ROWS}): the site menu, the categories only its sitemap lists, and the
 * collections index {@code /collections/} ({@code /collections/page/N/}), whose cards are collections
 * ({@code /collections/<id>-<slug>}, a book listing of the same scheme, not sortable).</li>
 * <li>search: {@code /index.php?do=search&subaction=search&story=Q&search_start=N}, GET, 10 cards, 4 characters at
 * least. Author, reader, series ("Цикл"): {@code /xfsearch/avtor|ispolnitel|serie/<value>/}, paged like the index,
 * newest first, not sortable; there is no index of all authors or readers.</li>
 * <li>book {@code /<category>/<id>-<slug>.html}: comments inline (oldest first, replies nested), later pages from
 * {@code /engine/ajax/controller.php?mod=comments}; similar books. Playerjs {@code file} is the URL of a JSON
 * playlist ({@code /f/audiobooks<token>/.../playlist.txt.php}, served as text/html, folders of volumes) or, on old
 * books, an inline JS list with empty placeholder items. The token rotates (an old one answers 404), so chapter URLs
 * are read off the page each time. A book the site only sells has a LitRes trial or a "buy" notice instead.</li>
 * </ul>
 */
public final class AudioknigaOnline implements SourceParser {

    public static final String SERVER = "https://audiokniga-online.ru";
    public static final String INDEX = SERVER + "/";
    /** The genre list is built in ({@link #loadGenres}); this is only its drawer key. */
    public static final String GENRES = SERVER + "/";
    public static final String COLLECTIONS = SERVER + "/collections/";

    static final String SKIN = "coolserial-light-qv3";
    /** Bound of the pages read for a book's series tab (10 books each). */
    static final int MAX_SERIES_PAGES = 10;

    /** Name, path and DLE category id (empty for a collection) of every genre, in menu order. */
    private static final String[][] GENRE_ROWS = {
            {"Фантастика", "/fantastika/", "10"},
            {"Фэнтези", "/fjentezi/", "43"},
            {"ЛитРПГ", "/fjentezi/litrpg/", "44"},
            {"Попаданцы", "/popadancy/", "54"},
            {"Попаданцы в ВОВ", "/popadancy/popadancy-v-vov/", "57"},
            {"Детективы", "/detektivy/", "11"},
            {"Роман, проза", "/roman/", "46"},
            {"Проза", "/proza/", "53"},
            {"Ужасы и Мистика", "/uzhasy-mistika/", "12"},
            {"Приключения", "/prikljuchenija/", "13"},
            {"Классика", "/klasika/", "51"},
            {"История", "/istorija/", "50"},
            {"Бизнес", "/biznes/", "56"},
            {"Аудиосказки для детей", "/dlja-detej-audioskazki/", "16"},
            {"S.T.A.L.K.E.R.", "/podborki/stalker/", "15"},
            {"S-T-I-K-S", "/stiks/", "55"},
            {"Истории на ночь", "/podborki/strashnye-rasskazy/", "47"},
            {"Истории про деревню", "/collections/7-istorii-pro-derevnju", ""},
            {"Постапокалипсис", "/collections/11-postapokalipsis", ""},
            {"Подборки", "/collections/", ""},
    };
    /** Category root URL → its DLE id. */
    private static final Map<String, String> CATEGORY_IDS = new HashMap<>();

    static {
        for (String[] row : GENRE_ROWS) {
            if (!row[2].isEmpty()) {
                CATEGORY_IDS.put(SERVER + row[1], row[2]);
            }
        }
    }

    private static final Pattern INDEX_PATH = Pattern.compile("^/(page/\\d+/?)?$");
    private static final Pattern PAGE = Pattern.compile("page/(\\d+)/?$");
    private static final Pattern COLLECTIONS_INDEX = Pattern.compile("^https?://[^/]+/collections/(page/\\d+/?)?$");
    private static final Pattern COMMENT_PAGES = Pattern.compile("total_comments_pages\\s*=\\s*'(\\d+)'");
    /** A collection's card title: "4. Гарри Поттер и Кубок Огня". */
    private static final Pattern NUMBERED = Pattern.compile("^(\\d{1,3})\\.\\s*(.+)$");

    // ---------------------------------------------------------------- SourceParser

    /**
     * Sorted URLs are POSTed; the index and the genres always go out with their sort ("new" too), so the session's last
     * sort never leaks into a plain listing. Everything else is a GET.
     */
    @Override
    public Document fetch(String url) throws IOException {
        if (url.contains(Dle.SORT_PARAM + "=")) {
            return Dle.fetch(url);
        }
        String genre = genreOf(url);
        if (genre != null) {
            return Dle.fetch(Dle.pageUrl(sortedUrl(genre, Dle.Order.DATE, CATEGORY_IDS.get(genre)), pageOf(url)));
        }
        return isIndex(url) ? Dle.fetch(Dle.pageUrl(sortedUrl(INDEX, Dle.Order.DATE, null), pageOf(url)))
                : SiteHttp.get(url);
    }

    @Override
    public ProviderPage<BookPOJO> listing(Document page, String url, int pageNo) {
        return parseListing(page, pageNo);
    }

    @Override
    public String pageUrl(String url, int page) {
        return Dle.pageUrl(url, page);
    }

    /** The index and the genres sort, plain or sorted; author, reader, series, collection and search pages do not. */
    @Override
    public List<Sort.Entry> sorts(String url) {
        return isIndex(url) || genreOf(url) != null ? Dle.MENU : Collections.<Sort.Entry>emptyList();
    }

    /** The index in {@code sort}; "new" is the plain index, which {@link #fetch} POSTs in the date sort. */
    @Override
    public String sortUrl(Sort sort, String listingUrl) {
        Dle.Order order = Dle.Order.of(sort);
        if (order == null) {
            return null;
        }
        return order == Dle.Order.DATE ? INDEX : sortedUrl(INDEX, order, null);
    }

    /** The collections index lists collections, which carry no time. */
    @Override
    public boolean listsDuration(String url) {
        return !COLLECTIONS_INDEX.matcher(url).matches();
    }

    /** The category root ({@code https://audiokniga-online.ru/<slug>/}) of a genre listing, plain or sorted. */
    @Override
    public String genreOf(String url) {
        HttpUrl parsed = HttpUrl.parse(url);
        if (parsed == null || !Url.isHost(url, SERVER)) {
            return null;
        }
        String root = SERVER + PAGE.matcher(parsed.encodedPath()).replaceFirst("");
        return CATEGORY_IDS.containsKey(root) ? root : null;
    }

    /** The genre's own sort, by its category id; "new" is the plain genre. */
    @Override
    public String inGenre(String siteWide, String genre) {
        HttpUrl parsed = HttpUrl.parse(siteWide);
        Dle.Order order = Dle.Order.ofField(parsed == null ? null : parsed.queryParameter(Dle.SORT_PARAM));
        return order == null ? genre : sortedUrl(genre, order, CATEGORY_IDS.get(genre));
    }

    @Override
    public ProviderPage<GenrePOJO> genres(Document page, String url, int pageNo) {
        return ProviderPage.of(genreList(), false);
    }

    /** The genre list is built in: nothing is fetched. */
    @Override
    public ProviderPage<GenrePOJO> loadGenres(String url, int pageNo) {
        return ProviderPage.of(genreList(), false);
    }

    @Override
    public String searchUrl(SearchUrls.Category category, String query, int page) {
        return category == SearchUrls.Category.BOOKS ? Dle.searchUrl(SERVER, query, page) : null;
    }

    /** The title alone: the site's search finds nothing once an author or a reader is added. */
    @Override
    public String otherSourceTerms(BookPOJO book) {
        return OtherSourceModel.query(book.getName());
    }

    /**
     * The search in the titles only ({@code titleonly=3}): the plain search reads the annotations too, and "Война и мир"
     * finds 50 newer books before the novel.
     */
    @Override
    public String otherSourceSearchUrl(String terms, int page) {
        return Dle.searchUrl(SERVER, terms, page).replace("full_search=0", "full_search=1&titleonly=3");
    }

    @Override
    public DescriptionPOJO description(Document page, String bookUrl) {
        return parseDescription(page);
    }

    @Override
    public BookPOJO book(Document page, String bookUrl) {
        return parseBook(page, bookUrl);
    }

    /** A collection card (the collections index) opens as the list of its books. */
    @Override
    public boolean opensAsListing(String url) {
        return url.contains("/collections/");
    }

    @Override
    public ArrayList<BookPOJO> recommendations(Document page, String bookUrl) {
        return parseRecommendations(page);
    }

    /** The Playerjs playlist; a book the site only sells is taken down. */
    @Override
    public ArrayList<AudioPOJO> tracks(Document page, String base) throws IOException {
        if (soldOnly(page)) {
            throw new BookRemovedException(base);
        }
        return encoded(Players.loadPlayerjs(page, base, text(page, "h1.short-title")));
    }

    /** The books of the "Цикл" link, all its pages, first book first ({@link #inSeriesOrder}). */
    @Override
    public ArrayList<SeriesPOJO> series(Document page, String bookUrl) throws IOException {
        ArrayList<SeriesPOJO> result = new ArrayList<>();
        String series = parseDescription(page).getSeriesUrl();
        for (int no = 1; !series.isEmpty() && no <= MAX_SERIES_PAGES; no++) {
            Document doc = fetch(pageUrl(series, no));
            result.addAll(parseSeries(doc));
            if (lastPage(doc) <= no) {
                break;
            }
        }
        return inSeriesOrder(result);
    }

    /** Page 1 is on the book page, the others come from the site's comments XHR; oldest first. */
    @Override
    public ArrayList<ComentsPOJO> comments(String bookUrl, BooleanSupplier cancelled) throws Exception {
        Document page = fetch(bookUrl);
        ArrayList<ComentsPOJO> comments = parseComments(page);
        int pages = commentPages(page);
        for (int no = 2; no <= pages && !cancelled.getAsBoolean(); no++) {
            String url = SERVER + "/engine/ajax/controller.php?mod=comments&cstart=" + no + "&news_id="
                    + Dle.newsId(bookUrl) + "&skin=" + SKIN + "&massact=disable";
            ArrayList<ComentsPOJO> more = parseCommentsPage(SiteHttp.body(SiteHttp.connect(url)));
            if (more.isEmpty()) {
                break;
            }
            comments.addAll(more);
        }
        return comments;
    }

    // ---------------------------------------------------------------- URLs

    /**
     * The index or a genre root sorted by {@code sort}: the sort form as the query, which {@link Dle#fetch} POSTs to
     * the page itself. {@code categoryId} is the genre's DLE id, null for the index.
     */
    private static String sortedUrl(String root, Dle.Order sort, @Nullable String categoryId) {
        return Dle.sorted(root, sort, categoryId == null ? "main" : "cat_" + categoryId);
    }

    /** The index, plain or sorted: {@code /} or {@code /page/N/}. */
    private static boolean isIndex(String url) {
        HttpUrl parsed = HttpUrl.parse(url);
        return parsed != null && Url.isHost(url, SERVER) && INDEX_PATH.matcher(parsed.encodedPath()).matches();
    }

    private static int pageOf(String url) {
        HttpUrl parsed = HttpUrl.parse(url);
        Matcher m = PAGE.matcher(parsed == null ? "" : parsed.encodedPath());
        return m.find() ? Integer.parseInt(m.group(1)) : 1;
    }

    // ---------------------------------------------------------------- listings

    /** Index, sorted, genre, collection, xfsearch and search pages, and the collections index (collection cards). */
    public static ProviderPage<BookPOJO> parseListing(Document doc, int page) {
        Elements raw = doc.select("#dle-content > .short-item");
        return ProviderPage.of(Html.cards(raw, AudioknigaOnline::card), lastPage(doc) > page, raw.size());
    }

    /** The highest page number in the pager; 0 without one (a single page). */
    private static int lastPage(Document doc) {
        return Html.lastPage(doc.select(".navigation a, .navigation span"));
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
        book.setPhoto(imgSrc(card.selectFirst("a.short-img img")));
        book.setGenre(text(card, ".short-label"));
        book.setAutor(iconText(card, ".short-meta-item .fa-pencil"));
        book.setUrlAutor(href(card, ".short-meta-item a[href*=/xfsearch/avtor/]"));
        book.setArtist(iconText(card, ".short-meta-item .fa-microphone"));
        book.setUrlArtist(href(card, ".short-meta-item a[href*=/xfsearch/ispolnitel/]"));
        book.setTime(iconText(card, ".short-meta-item .fa-clock-o"));
        String likes = text(card, "[id^=likes-id-]");
        String dislikes = text(card, "[id^=dislikes-id-]");
        if (likes.matches("\\d+") && dislikes.matches("\\d+")) {
            int net = Integer.parseInt(likes) - Integer.parseInt(dislikes);
            if (net != 0) {
                book.setReting(net > 0 ? "+" + net : Integer.toString(net));
            }
        }
        // a book's annotation; a collection's ("Подборки") is its short-list
        book.setDesc(text(card, ".short-desc > .hide-on-mobile, .short-desc > .short-list"));
        return book;
    }

    // ---------------------------------------------------------------- genres

    public static ArrayList<GenrePOJO> genreList() {
        ArrayList<GenrePOJO> genres = new ArrayList<>();
        for (String[] row : GENRE_ROWS) {
            genres.add(new GenrePOJO(row[0], SERVER + row[1]));
        }
        return genres;
    }

    // ---------------------------------------------------------------- book page

    public static DescriptionPOJO parseDescription(Document doc) {
        DescriptionPOJO description = new DescriptionPOJO();
        description.setTitle(text(doc, "h1.short-title"));
        description.setPoster(imgSrc(doc.selectFirst(".fimg img")));
        Element author = row(doc, "Автор");
        description.setAutor(afterLabel(author));
        description.setAutorUrl(href(author, "a"));
        Element reader = row(doc, "Читает");
        description.setArtist(afterLabel(reader));
        description.setArtistUrl(href(reader, "a"));
        description.setGenre(afterLabel(row(doc, "Жанр")));
        description.setGenreUrl(category(doc));
        Element series = row(doc, "Цикл");
        Element seriesLink = series == null ? null : series.selectFirst("a");
        if (seriesLink != null) {
            description.setSeries(seriesLink.text().trim());
            description.setSeriesUrl(seriesLink.absUrl("href"));
        }
        description.setTime(afterLabel(row(doc, "Время")));
        description.setReiting(text(doc, ".frate [id^=ratig-layer-]"));
        description.setLike(text(doc, ".frate [id^=likes-id-]"));
        description.setDisLike(text(doc, ".frate [id^=dislikes-id-]"));
        description.setDescription(annotation(doc));
        return description;
    }

    /** A book opened by its link: the card fields of its page, the net votes and the comment count included. */
    public static BookPOJO parseBook(Document doc, String url) {
        DescriptionPOJO description = parseDescription(doc);
        BookPOJO book = BookPOJO.of(description, url);
        if (!description.getReiting().isEmpty()) {
            book.setReting(description.getReiting());
        }
        book.setComents(iconText(doc, ".short-header .fa-comment-dots"));
        return book;
    }

    /** The "Жанр:", "Время:", "Автор:", "Читает:", "Цикл" rows of the book page. */
    @Nullable
    private static Element row(Document doc, String label) {
        return labelled(doc, "ul.short-list > li", label);
    }

    /** The book's category from the breadcrumb (its deepest link) when it is one of the genres; "" otherwise. */
    private static String category(Document doc) {
        Elements crumbs = doc.select(".speedbar a[itemprop=item]");
        String url = crumbs.isEmpty() ? "" : crumbs.last().absUrl("href");
        return CATEGORY_IDS.containsKey(url) ? url : "";
    }

    /** The "Аннотация к аудиокниге …" block without its heading. */
    private static String annotation(Document doc) {
        Element heading = doc.selectFirst(".full-text > .nav-title");
        if (heading == null) {
            return "";
        }
        Element box = heading.parent().clone();
        box.select(".nav-title").remove();
        return box.text().trim();
    }

    /** "Похожие аудиокниги". */
    public static ArrayList<BookPOJO> parseRecommendations(Document doc) {
        return Html.cards(doc.select("#owl-rels a.popular-item-img"), a -> {
            BookPOJO book = new BookPOJO();
            book.setUrl(a.absUrl("href"));
            book.setName(text(a, ".popular-item-title"));
            book.setPhoto(imgSrc(a.selectFirst("img")));
            book.setGenre(text(a, ".short-label"));
            return book;
        });
    }

    /** One page of a series or collection listing; a collection's "N. title" gives the book's number. */
    public static ArrayList<SeriesPOJO> parseSeries(Document listing) {
        ArrayList<SeriesPOJO> out = new ArrayList<>();
        for (BookPOJO book : parseListing(listing, 1).items) {
            SeriesPOJO item = new SeriesPOJO();
            item.setUrl(book.getUrl());
            item.setPhoto(book.getPhoto() == null ? "" : book.getPhoto());
            Matcher m = NUMBERED.matcher(book.getName());
            if (m.matches()) {
                item.setNumber(m.group(1));
                item.setName(m.group(2));
            } else {
                item.setName(book.getName());
            }
            out.add(item);
        }
        return out;
    }

    /**
     * The pages of a series listing, first book first: a "Цикл" ({@code /xfsearch/serie/}) lists its newest book first
     * and a collection its titles from Я to А, so both are turned round; then a collection's numbers ("2. …") decide,
     * an unnumbered book (the first of "Янкылма", "Янкылма 2") keeping its place before them.
     */
    static ArrayList<SeriesPOJO> inSeriesOrder(ArrayList<SeriesPOJO> books) {
        Collections.reverse(books);
        Collections.sort(books, (x, y) -> Integer.compare(number(x), number(y)));
        return books;
    }

    private static int number(SeriesPOJO item) {
        return item.getNumber().isEmpty() ? 0 : Integer.parseInt(item.getNumber());
    }

    // ---------------------------------------------------------------- comments

    /** The comment threads of a page or of an XHR answer's HTML: each comment with its replies of any depth. */
    public static ArrayList<ComentsPOJO> parseComments(Element scope) {
        ArrayList<ComentsPOJO> out = new ArrayList<>();
        for (Element li : scope.select("li.comments-tree-item")) {
            if (li.parent() != null && li.parent().closest("li.comments-tree-item") != null) {
                continue; // a reply: read with its thread
            }
            ComentsPOJO comment = new ComentsPOJO();
            fill(comment, li);
            if (!comment.isEmty()) {
                addReplies(comment, li, comment.getName());
                out.add(comment);
            }
        }
        return out;
    }

    /** A comments XHR answer: {@code {"navigation": "", "comments": "<ol>…"}}, the HTML empty past the last page. */
    public static ArrayList<ComentsPOJO> parseCommentsPage(String json) {
        return parseComments(Jsoup.parseBodyFragment(Json.string(Json.object(json), "comments"), SERVER));
    }

    /** The comment pages the book page announces ({@code total_comments_pages}); 1 without paging. */
    static int commentPages(Document page) {
        for (Element script : page.select("script")) {
            Matcher m = COMMENT_PAGES.matcher(script.data());
            if (m.find()) {
                return Integer.parseInt(m.group(1));
            }
        }
        return 1;
    }

    private static void addReplies(ComentsPOJO thread, Element li, String parentName) {
        for (Element child : li.select("> ol.comments-tree-list > li.comments-tree-item")) {
            SubComentsPOJO reply = new SubComentsPOJO();
            fill(reply, child);
            reply.setParentName(parentName);
            if (!reply.isEmty()) {
                thread.setChildComents(reply);
            }
            addReplies(thread, child, reply.getName());
        }
    }

    /** The comment of {@code li} itself (its first block; its replies follow it). */
    private static void fill(ContentParentPOJO pojo, Element li) {
        Element item = li.selectFirst("> div[id^=comment-id-] .comm-item");
        if (item == null) {
            return;
        }
        pojo.setName(text(item, ".comm-author"));
        pojo.setDate(text(item, ".comm-one > span:not(.comm-author)"));
        pojo.setImage(imgSrc(item.selectFirst(".comm-avatar img")));
        String rating = text(item, "[id^=comments-ratig-layer-]");
        pojo.setReting(rating.equals("0") ? "" : rating);
        pojo.setText(text(item, "div[id^=comm-id-]"));
    }

    // ---------------------------------------------------------------- audio

    /** A book the site only sells: a LitRes trial or its "buy the full version" notice where the player would be. */
    private static boolean soldOnly(Document page) {
        return page.getElementById("playerjs1") == null
                && page.selectFirst("iframe[src*=litres.ru], #dle-content .berrors") != null;
    }

    /** The chapters with their URLs percent-encoded: the playlist spells the Cyrillic of its folders raw. */
    static ArrayList<AudioPOJO> encoded(ArrayList<AudioPOJO> chapters) {
        for (AudioPOJO chapter : chapters) {
            HttpUrl url = HttpUrl.parse(chapter.getUrl());
            if (url != null) {
                chapter.setUrl(url.toString());
            }
        }
        return chapters;
    }
}
