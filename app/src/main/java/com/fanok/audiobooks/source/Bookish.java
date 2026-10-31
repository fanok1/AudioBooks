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
import com.fanok.audiobooks.pojo.ContentParentPOJO;
import com.fanok.audiobooks.pojo.DescriptionPOJO;
import com.fanok.audiobooks.pojo.GenrePOJO;
import com.fanok.audiobooks.pojo.SeriesPOJO;
import com.fanok.audiobooks.pojo.SubComentsPOJO;
import com.fanok.audiobooks.search.ProviderPage;
import com.fanok.audiobooks.search.SearchUrls;
import com.google.gson.JsonObject;

import io.reactivex.functions.BooleanSupplier;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.HttpUrl;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

/**
 * bookish.site: DataLife Engine, template "coolserial-dark-re6" (probed live, 2026-10-31). Plain HTML, no protection,
 * no Referer or cookies needed; 16 {@code div.short-item} cards a page, the same on every listing and the search.
 * <ul>
 * <li>index {@code /}, then {@code /page/N/} ({@code /page/1/} is a 301 to {@code /}); a genre {@code /<slug>/},
 * an author, reader or series {@code /xfsearch/avtor|chitaet|cikl/<name>/}, all with the index's page scheme. The
 * pager {@code .navigation} prints the current page as a bare span. The genre menu sits in the sidebar of every page;
 * {@link #HIDDEN_GENRES} are categories outside it. No index of all authors or readers.</li>
 * <li>sorts: the DLE sort form POSTed to the listing page itself ({@code dlenewssortby} date, rating, news_read,
 * comm_num, title), key {@code main} on the index, {@code cat_<id>} in a genre ({@link Dle#categoryId}),
 * {@code xfsearch} on author, reader and series pages; a GET with that query is ignored. The choice sticks to the
 * PHPSESSID session per key, so {@link #fetch} sends every listing page with its sort, "new" included. No periods.</li>
 * <li>search: {@code /index.php?do=search&subaction=search&story=Q&search_start=N&sortby=date&resorder=desc}, GET
 * (oldest first without the sort). Its count and pager stop at 80 hits / 5 pages while the pages go on.</li>
 * <li>book {@code /<id>-<slug>.html}: titles are "Title - Authors" or, on old posts, "Authors - Title"; comments
 * inline (a DLE tree), "Ещё есть" recommendations. Chapters: an inline {@code var playlist = [{title, file}]}
 * (single-quoted on the newest posts) of MP3s without durations: archive.org direct, raw (spaces, Cyrillic) or
 * {@code /engine/go.php?url=<base64>}; since 31.10.2026 also {@code a1.knizhulki.xyz}, which answers 403 unless
 * the Referer is on bookish.site (the book page, the default). Old books have instead an archive.org embed (mostly
 * dark items), a LitRes trial widget or an empty player. The site-wide rating and views sorts take 20-25 s.</li>
 * </ul>
 */
public final class Bookish implements SourceParser {

    public static final String SERVER = "https://bookish.site";
    public static final String INDEX = SERVER + "/";
    /** Any page carries the genre menu; the index is the one the catalog already loads. */
    public static final String GENRES = SERVER + "/";

    static final int PAGE_SIZE = 16;
    /** Bound of the pages read for a book's series tab. */
    static final int MAX_SERIES_PAGES = 10;

    /** Categories filed in but missing from the menu (from the advanced search's list), name and slug. */
    static final String[][] HIDDEN_GENRES = {{"Античность", "antichnost"}, {"Биография", "biografija"},
            {"Война", "vojna"}, {"Драма", "drama"}, {"Мистика", "mistika"}, {"Новелла", "novella"},
            {"Повесть", "povest"}, {"Поэзия", "pojezija"}, {"Проза", "proza"}, {"Публицистика", "publicistika"},
            {"Стихи", "stihi"}, {"Философия", "filosofija"}, {"Хоррор", "horror"}};

    /** Genre root → DLE category id, learnt from the genre's own pages (their sort form). */
    private static final Map<String, String> CATEGORY_IDS = new ConcurrentHashMap<>();

    private static final Pattern INDEX_PATH = Pattern.compile("^/(page/\\d+/?)?$");
    private static final Pattern LISTING_ROOT =
            Pattern.compile("^(/xfsearch/[^/]+/[^/]+/|/(?!page/|xfsearch/)[a-z0-9-]+/)(page/\\d+/?)?$");
    /** The "Автор:", "Читает:", "Время:", "Цикл:" rows of a card or book page. */
    private static final String ROWS = "ul.short-list li";
    private static final Pattern SERIES_NUMBER = Pattern.compile("№\\s*(\\S+)");
    /** "06:24:33", "10:9:27", "57:10:00.", "00:41::47", "10 дн. 15:50:44"; "57 мин." is left as it is. */
    private static final Pattern DURATION = Pattern.compile("^(?:(\\d+) дн\\.\\s*)?(\\d+):+(\\d+):+(\\d+)\\.?$");
    private static final Pattern NON_WORD = Pattern.compile("[^\\p{L}\\p{N}]+");
    private static final Pattern ARCHIVE_EMBED = Pattern.compile("archive\\.org/embed/([^?&#\"'/]+)");

    // ---------------------------------------------------------------- SourceParser

    /**
     * Sorted URLs are POSTed. Every other index, genre or xfsearch page goes out in the date sort under its key; a
     * genre whose id is not known yet cannot have been sorted by this session, so its plain GET is in order and tells
     * the id.
     */
    @Override
    public Document fetch(String url) throws IOException {
        if (url.contains("?")) {
            return Dle.fetch(url);
        }
        String key = sortKey(url);
        if (key != null) {
            return Dle.fetch(Dle.sorted(url, Dle.Order.DATE, key));
        }
        String genre = listingRoot(url);
        return genre == null ? SiteHttp.get(url) : learnCategory(genre, SiteHttp.get(url));
    }

    /** The site-wide rating and views sorts take 20-25 s of server time (31.10.2026); every other page 1-2 s. */
    @Override
    public int timeoutMs() {
        return 40_000;
    }

    /** Remembers the category id {@code page} of {@code genre} shows; returns the page. */
    static Document learnCategory(String genre, Document page) {
        String id = Dle.categoryId(page);
        if (id != null) {
            CATEGORY_IDS.put(genre, id);
        }
        return page;
    }

    @Override
    public ProviderPage<BookPOJO> listing(Document page, String url, int pageNo) {
        return parseListing(page, pageNo);
    }

    @Override
    public String pageUrl(String url, int page) {
        return Dle.pageUrl(url, page);
    }

    /** The index, genres and author, reader and series pages sort, plain or sorted; the search does not. */
    @Override
    public List<Sort.Entry> sorts(String url) {
        return isIndex(url) || listingRoot(url) != null ? Dle.MENU : Collections.<Sort.Entry>emptyList();
    }

    /** The index in the item's sort; "new" is the bare index, which {@link #fetch} POSTs in the date sort. */
    @Override
    public String sortUrl(Sort sort, String listingUrl) {
        Dle.Order order = Dle.Order.of(sort);
        return order == null ? null : order == Dle.Order.DATE ? INDEX : Dle.sorted(INDEX, order, "main");
    }

    /** The root of a genre, author, reader or series listing, plain or sorted: each sorts on the server. */
    @Override
    public String genreOf(String url) {
        return listingRoot(url);
    }

    /** The site-wide sort {@code siteWide} within {@code genre}; site-wide while a genre's id is unknown. */
    @Override
    public String inGenre(String siteWide, String genre) {
        HttpUrl parsed = HttpUrl.parse(siteWide);
        Dle.Order order = Dle.Order.ofField(parsed == null ? null : parsed.queryParameter(Dle.SORT_PARAM));
        if (order == null) {
            return genre;
        }
        String key = keyOf(genre);
        return key == null ? siteWide : Dle.sorted(genre, order, key);
    }

    @Override
    public ProviderPage<GenrePOJO> genres(Document page, String url, int pageNo) {
        return parseGenres(page);
    }

    @Override
    public String searchUrl(SearchUrls.Category category, String query, int page) {
        return category == SearchUrls.Category.BOOKS
                ? Dle.searchUrl(SERVER, query, page) + "&sortby=date&resorder=desc" : null;
    }

    @Override
    public ProviderPage<BookPOJO> searchResults(Document page, int pageNo) {
        return parseSearch(page);
    }

    @Override
    public DescriptionPOJO description(Document page, String bookUrl) {
        return parseDescription(page);
    }

    @Override
    public ArrayList<BookPOJO> recommendations(Document page, String bookUrl) {
        return parseRecommendations(page);
    }

    /** The inline playlist; else an archive.org embed, read through the item's metadata. */
    @Override
    public ArrayList<AudioPOJO> tracks(Document page, String base) throws IOException {
        String name = parseDescription(page).getTitle();
        String playlist = Players.findPlayerScript(page, "var playlist", '[');
        if (playlist != null) {
            return parseTracks(playlist, base, name);
        }
        String item = archiveItem(page, base);
        // the item's metadata can be over a megabyte and take archive.org several seconds
        return parseArchiveItem(SiteHttp.body(SiteHttp.connect("https://archive.org/metadata/" + item)
                .timeout(timeoutMs())), name, base);
    }

    /** The series listing linked from the book page, all its pages, by number. */
    @Override
    public ArrayList<SeriesPOJO> series(Document page, String bookUrl) throws IOException {
        ArrayList<SeriesPOJO> result = new ArrayList<>();
        String series = href(labelled(page, ROWS, "Цикл"), "a");
        for (int no = 1; !series.isEmpty() && no <= MAX_SERIES_PAGES; no++) {
            Document doc = fetch(pageUrl(series, no));
            result.addAll(parseSeries(doc));
            if (lastPage(doc) <= no) {
                break;
            }
        }
        sortByNumber(result);
        return result;
    }

    /** All comments are on the book page. */
    @Override
    public ArrayList<ComentsPOJO> comments(String bookUrl, BooleanSupplier cancelled) throws Exception {
        return parseComments(fetch(bookUrl));
    }

    // ---------------------------------------------------------------- URLs

    /** The session key of a listing's sort: {@code main}, {@code xfsearch} or {@code cat_<id>}; null elsewhere. */
    @Nullable
    private static String sortKey(String url) {
        if (isIndex(url)) {
            return "main";
        }
        String root = listingRoot(url);
        return root == null ? null : keyOf(root);
    }

    /** {@link #genreOf}: the root of a genre or xfsearch listing on this host, any page, plain or sorted. */
    @Nullable
    private static String listingRoot(String url) {
        HttpUrl parsed = HttpUrl.parse(url);
        if (parsed == null || !parsed.host().equals(HttpUrl.get(SERVER).host())) {
            return null;
        }
        Matcher m = LISTING_ROOT.matcher(parsed.encodedPath());
        return m.matches() ? SERVER + m.group(1) : null;
    }

    /** The key of a genre or xfsearch root; null for a genre whose id is not known yet. */
    @Nullable
    private static String keyOf(String root) {
        if (root.startsWith(SERVER + "/xfsearch/")) {
            return "xfsearch";
        }
        String id = CATEGORY_IDS.get(root);
        return id == null ? null : "cat_" + id;
    }

    /** The index, plain or sorted: {@code /} or {@code /page/N/}. */
    private static boolean isIndex(String url) {
        HttpUrl parsed = HttpUrl.parse(url);
        return parsed != null && parsed.host().equals(HttpUrl.get(SERVER).host())
                && INDEX_PATH.matcher(parsed.encodedPath()).matches();
    }

    // ---------------------------------------------------------------- listings

    /** Index, sorted, genre and author, reader or series pages. */
    public static ProviderPage<BookPOJO> parseListing(Document doc, int page) {
        Elements raw = doc.select("#dle-content div.short-item");
        return ProviderPage.of(Html.cards(raw, Bookish::card), lastPage(doc) > page, raw.size());
    }

    /** A search page: its pager stops at page 5 while the results go on, so a full page means more. */
    public static ProviderPage<BookPOJO> parseSearch(Document doc) {
        Elements raw = doc.select("#dle-content div.short-item");
        return ProviderPage.of(Html.cards(raw, Bookish::card), raw.size() >= PAGE_SIZE, raw.size());
    }

    private static int lastPage(Document doc) {
        return Html.lastPage(doc.select(".bottom-nav .navigation a, .bottom-nav .navigation span"));
    }

    @Nullable
    private static BookPOJO card(Element card) {
        Element title = card.selectFirst("a.short-title");
        if (title == null) {
            return null;
        }
        BookPOJO book = new BookPOJO();
        book.setUrl(title.absUrl("href"));
        book.setPhoto(Html.imgSrc(card.selectFirst("a.short-img img")));
        Element author = labelled(card, ROWS, "Автор");
        String authors = afterLabel(author); // "" without the row; the book keeps null then
        book.setAutor(authors);
        book.setUrlAutor(href(author, "a"));
        Element reader = labelled(card, ROWS, "Читает");
        book.setArtist(afterLabel(reader));
        book.setUrlArtist(href(reader, "a"));
        Element series = labelled(card, ROWS, "Цикл");
        book.setSeries(text(series, "a"));
        book.setUrlSeries(href(series, "a"));
        book.setTime(duration(card));
        book.setName(title(title.text(), authors));
        String percent = text(card, "li.current-rating");
        if (percent.matches("\\d+") && !percent.equals("0")) {
            book.setReting(String.format(Locale.ROOT, "%.1f", Integer.parseInt(percent) / 20f));
        }
        return book;
    }

    /** The "Время:" row as H:MM:SS, days counted in the hours; another spelling as printed. */
    static String duration(Element scope) {
        String time = afterLabel(labelled(scope, ROWS, "Время"));
        Matcher m = DURATION.matcher(time);
        if (!m.matches()) {
            return time;
        }
        int days = m.group(1) == null ? 0 : Integer.parseInt(m.group(1));
        return String.format(Locale.ROOT, "%d:%02d:%02d", days * 24 + Integer.parseInt(m.group(2)),
                Integer.parseInt(m.group(3)), Integer.parseInt(m.group(4)));
    }

    /**
     * {@code text} without its authors, whichever side of " - " they are on, in any name order ("Принц - Евгений
     * Щепетнов" by "Щепетнов Евгений"); the whole text when neither side is made of the authors' words.
     */
    static String title(String text, String authors) {
        String name = text.trim();
        Set<String> words = words(authors);
        int last = name.lastIndexOf(" - ");
        if (last > 0 && authorsOnly(name.substring(last + 3), words)) {
            return name.substring(0, last).trim();
        }
        int first = name.indexOf(" - ");
        if (first > 0 && authorsOnly(name.substring(0, first), words)) {
            return name.substring(first + 3).trim();
        }
        return name;
    }

    private static boolean authorsOnly(String part, Set<String> authorWords) {
        Set<String> words = words(part);
        return !words.isEmpty() && authorWords.containsAll(words);
    }

    private static Set<String> words(String text) {
        Set<String> words = new HashSet<>();
        for (String word : NON_WORD.split(text.toLowerCase(Locale.ROOT))) {
            if (!word.isEmpty()) {
                words.add(word);
            }
        }
        return words;
    }

    // ---------------------------------------------------------------- genres

    /** The sidebar menu, then {@link #HIDDEN_GENRES}. */
    public static ProviderPage<GenrePOJO> parseGenres(Document doc) {
        ArrayList<GenrePOJO> genres = Html.rows(doc.select(".side-nav ul.nav-menu li a"),
                a -> new GenrePOJO(a.text().trim(), a.absUrl("href")));
        for (String[] hidden : HIDDEN_GENRES) {
            genres.add(new GenrePOJO(hidden[0], SERVER + "/" + hidden[1] + "/"));
        }
        return ProviderPage.of(genres, false);
    }

    // ---------------------------------------------------------------- book page

    public static DescriptionPOJO parseDescription(Document doc) {
        DescriptionPOJO description = new DescriptionPOJO();
        Element article = doc.selectFirst("article.article");
        if (article == null) {
            return description;
        }
        Element author = labelled(article, ROWS, "Автор");
        description.setAutor(afterLabel(author));
        description.setAutorUrl(href(author, "a"));
        description.setTitle(title(text(article, "h1"), description.getAutor()));
        description.setPoster(Html.imgSrc(article.selectFirst(".fimg img")));
        Element reader = labelled(article, ROWS, "Читает");
        description.setArtist(afterLabel(reader));
        description.setArtistUrl(href(reader, "a"));
        Element series = labelled(article, ROWS, "Цикл");
        if (series != null) {
            description.setSeries(text(series, "a"));
            description.setSeriesUrl(href(series, "a"));
        }
        description.setTime(duration(article));
        // the breadcrumb: home, the book's one category, the title
        Elements crumbs = doc.select(".speedbar [itemprop=itemListElement]");
        if (crumbs.size() > 1) {
            description.setGenre(text(crumbs.get(1), "[itemprop=name]"));
            description.setGenreUrl(href(crumbs.get(1), "a"));
        }
        String percent = text(article, "li.current-rating");
        String votes = text(article, "[id^=vote-num-id-]");
        if (percent.matches("\\d+") && !percent.equals("0") && votes.matches("\\d+") && !votes.equals("0")) {
            description.setReiting(BookPOJO.formatRating(Integer.parseInt(percent) / 20f, votes));
        }
        Element text = article.selectFirst("div.ftext.full-text");
        if (text != null) {
            Element own = text.clone();
            own.select("script, noindex, .attachment_position, .fsubtitle").remove();
            description.setDescription(own.text());
        }
        return description;
    }

    /** "Ещё есть:", mostly the same author or series. */
    public static ArrayList<BookPOJO> parseRecommendations(Document doc) {
        return Html.cards(doc.select("#owl-rels a.popular-item-img"), a -> {
            BookPOJO book = new BookPOJO();
            book.setUrl(a.absUrl("href"));
            book.setName(text(a, ".popular-item-title"));
            book.setPhoto(Html.imgSrc(a.selectFirst("img")));
            return book;
        });
    }

    /** The cards of a series listing, each with its number ("№10", "№1.1"; none on some). */
    public static ArrayList<SeriesPOJO> parseSeries(Document doc) {
        return Html.each(doc.select("#dle-content div.short-item"), card -> {
            BookPOJO book = card(card);
            if (book == null || book.isNull()) {
                return null;
            }
            SeriesPOJO item = new SeriesPOJO();
            item.setUrl(book.getUrl());
            item.setName(book.getName());
            item.setPhoto(book.getPhoto());
            Matcher number = SERIES_NUMBER.matcher(afterLabel(labelled(card, ROWS, "Цикл")));
            if (number.find()) {
                item.setNumber(number.group(1));
            }
            return item;
        });
    }

    /** By number; books without one keep their order after the numbered ones. */
    static void sortByNumber(List<SeriesPOJO> series) {
        Collections.sort(series, (x, y) -> Double.compare(number(x), number(y)));
    }

    private static double number(SeriesPOJO item) {
        try {
            return Double.parseDouble(item.getNumber());
        } catch (NumberFormatException none) {
            return Double.MAX_VALUE;
        }
    }

    /** The comments in page order (oldest first); replies of any depth join their thread. */
    public static ArrayList<ComentsPOJO> parseComments(Document doc) {
        ArrayList<ComentsPOJO> result = new ArrayList<>();
        Map<Element, ComentsPOJO> rootOf = new IdentityHashMap<>();
        Map<Element, String> nameOf = new IdentityHashMap<>();
        for (Element li : doc.select("#dle-comments-list li.comments-tree-item")) {
            Element parent = li.parent() == null ? null : li.parent().closest("li.comments-tree-item");
            ComentsPOJO root = parent == null ? null : rootOf.get(parent);
            if (parent != null && root == null) {
                continue; // a reply of a skipped comment
            }
            ContentParentPOJO pojo = root == null ? new ComentsPOJO() : new SubComentsPOJO();
            // its own block comes before the list of its replies
            Element item = li.children().isEmpty() ? null : li.child(0).selectFirst(".comm-item");
            if (item != null) {
                pojo.setName(text(item, ".comm-author"));
                pojo.setDate(text(item, ".comm-one > span:not(.comm-author)"));
                pojo.setText(text(item, ".comm-two div[id^=comm-id-]"));
                pojo.setImage(Html.imgSrc(item.selectFirst(".comm-avatar img")));
            }
            nameOf.put(li, pojo.getName());
            if (pojo.isEmty()) {
                continue;
            }
            if (root == null) {
                rootOf.put(li, (ComentsPOJO) pojo);
                result.add((ComentsPOJO) pojo);
            } else {
                rootOf.put(li, root);
                ((SubComentsPOJO) pojo).setParentName(nameOf.get(parent));
                root.setChildComents((SubComentsPOJO) pojo);
            }
        }
        return result;
    }

    // ---------------------------------------------------------------- chapters

    /**
     * The inline playlist {@code [{title, file}]}: files direct, raw (encoded here) or behind {@code go.php}
     * (decoded here, so no redirect needs the book as Referer); a title is a path, its last part is the name.
     */
    public static ArrayList<AudioPOJO> parseTracks(String json, String base, String bookName) {
        Players.Chapters chapters = new Players.Chapters(bookName, base);
        chapters.each(Json.array(json), item -> {
            JsonObject o = item.getAsJsonObject();
            String title = Json.string(o, "title");
            return chapters.chapter(title.substring(title.lastIndexOf('/') + 1).trim(),
                    mediaUrl(base, Json.string(o, "file")), 0);
        });
        return chapters.done("bookish playlist");
    }

    /** A playlist file as a playable https URL; "" when it is none. */
    static String mediaUrl(String base, String file) {
        HttpUrl url = HttpUrl.parse(Players.resolve(base, Players.playerjsUrl(file.replace("&amp;", "&"))));
        if (url == null) {
            return "";
        }
        // go.php hides some http://archive.org links, which answer only with a redirect to https
        return !url.isHttps() && url.host().endsWith("archive.org")
                ? url.newBuilder().scheme("https").build().toString() : url.toString();
    }

    /**
     * The archive.org item of a page without a playlist. A LitRes trial widget (the book is sold there) or an empty
     * player is a {@link BookRemovedException}; any other page an {@link IllegalStateException}.
     */
    static String archiveItem(Document doc, String bookUrl) {
        Element player = doc.selectFirst(".fplayer .video-box");
        if (player == null) {
            throw new IllegalStateException("no player on " + bookUrl);
        }
        Element frame = player.selectFirst("iframe[src]");
        String src = frame == null ? "" : frame.attr("src");
        Matcher embed = ARCHIVE_EMBED.matcher(src);
        if (embed.find()) {
            return embed.group(1);
        }
        if (src.contains("litres.ru/") || player.children().isEmpty() && player.text().isEmpty()) {
            throw new BookRemovedException(bookUrl);
        }
        throw new IllegalStateException("unknown player on " + bookUrl);
    }

    /** archive.org {@code /metadata/<item>} of an embedded item: its MP3s; a dark (taken down) item has no files. */
    public static ArrayList<AudioPOJO> parseArchiveItem(String json, String bookName, String bookUrl) {
        if (!Json.object(json).has("files")) {
            throw new BookRemovedException(bookUrl);
        }
        return BazaKnig.parseArchiveOrgMetadata(json, bookName);
    }
}
