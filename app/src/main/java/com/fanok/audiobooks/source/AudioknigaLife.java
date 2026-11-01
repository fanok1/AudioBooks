package com.fanok.audiobooks.source;

import static com.fanok.audiobooks.source.Html.text;

import com.fanok.audiobooks.BookRemovedException;
import com.fanok.audiobooks.net.JsonSlice;
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
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import io.reactivex.functions.BooleanSupplier;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

/**
 * audiokniga.life, a DataLife Engine site with its own catalogue (probed live 2026-10-25). Every listing pages as
 * {@code <listing>page/N/} ({@link Dle#pageUrl}); 16 cards per listing page, 10 per search page. No protection, plain
 * GETs, no Referer or cookies.
 * <ul>
 * <li>index {@code /}; a genre {@code /<slug>/}, subgenres nested ({@code /fantastika/postapokalipsis/}). The genre
 * list sits in the sidebar of every page.</li>
 * <li>sorts: the DLE Filter plugin {@code /f/sort=<date|rating|news_read|comm_num|title>/order=<desc|asc>/}, inside a
 * genre {@code /f/cat=<id>/sort=…/}. No page shows a category's id, so {@link #CATEGORY_IDS} holds the slugs found by
 * probing {@code /f/cat=1..40}; a genre missing there sorts to its own listing. {@code cat=<id>} counts only books
 * filed under that category itself, not its subgenres'. No periods: popularity and rating are all-time.</li>
 * <li>search {@code /search/<query>/page/N/}.</li>
 * <li>author, reader, series {@code /xfsearch/avtor|ispolnitel|serie/<name>/}; the site has no index of all authors
 * or readers.</li>
 * <li>book {@code /<slug>/<id>-<name>.html}: chapters inline in {@code playerInit(id, 'title', 'json', [...])}, direct
 * MP3s with durations; all comments (a DLE tree) are on the page.</li>
 * </ul>
 */
public final class AudioknigaLife implements SourceParser {

    public static final String SERVER = "https://audiokniga.life";
    public static final String INDEX = SERVER + "/";
    /** Any page carries the genre list; the index is the lightest. */
    public static final String GENRES = SERVER + "/";

    /** DLE category ids by the last segment of the genre URL, probed live; {@code /f/cat=<id>/} ranks inside it. */
    static final Map<String, Integer> CATEGORY_IDS = new HashMap<>();

    static {
        String[] slugs = {"fantastika", "fjentezi", "litrpg", "detektivy", "uzhasy", "mistika", "prikljuchenija",
                "roman", "klassika", "audioskazki", "pro-derevnju", "pro-zombi", "strashnye-istorii",
                "russkie-detektivy", "popadancy", "stalker", "psihologija", "postapokalipsis", "ljubovnoe-fjentezi",
                "istorija", "biografija"};
        for (int i = 0; i < slugs.length; i++) {
            CATEGORY_IDS.put(slugs[i], 9 + i); // 9..29 in this order
        }
        CATEGORY_IDS.put("tru-krajm", 31);
    }

    private static final String FILTER = SERVER + "/f/";
    private static final Pattern NUMBER = Pattern.compile("(\\d+)");
    private static final Pattern FILTER_CAT = Pattern.compile("^" + Pattern.quote(FILTER) + "cat=(\\d+)/");
    /** A genre root, maybe paged; not the index, the filter, a search, an author/reader/series or tag page. */
    private static final Pattern GENRE = Pattern.compile("^(" + Pattern.quote(SERVER)
            + "/(?!(?:f|search|xfsearch|tags|favorites|page|user)/)(?:[\\w-]+/)+?)(?:page/\\d+/)?$");

    // ---------------------------------------------------------------- SourceParser

    @Override
    public ProviderPage<BookPOJO> listing(Document page, String url, int pageNo) {
        return parseBooks(page, pageNo);
    }

    @Override
    public String pageUrl(String url, int page) {
        return Dle.pageUrl(url, page);
    }

    /** Authors, readers, series and search results are ranked by the site alone. */
    @Override
    public List<Sort.Entry> sorts(String url) {
        boolean sortable = !url.startsWith(SERVER + "/xfsearch/") && !url.startsWith(SERVER + "/search/");
        return sortable ? Dle.MENU : Collections.<Sort.Entry>emptyList();
    }

    /** The DLE sort form as a {@code /f/} filter path; "new" is the index. */
    @Override
    public String sortUrl(Sort sort, String listingUrl) {
        Dle.Order order = Dle.Order.of(sort);
        if (order == null) {
            return null;
        }
        return order == Dle.Order.DATE ? INDEX : FILTER + "sort=" + order.field + "/order=" + order.direction + "/";
    }

    /** The category id of a genre or of a ranking inside one; a genre without a known id is keyed by its root URL. */
    @Override
    public String genreOf(String url) {
        Matcher m = FILTER_CAT.matcher(url);
        if (m.find()) {
            return m.group(1);
        }
        m = GENRE.matcher(url);
        if (!m.matches()) {
            return null;
        }
        String root = m.group(1);
        String path = root.substring(0, root.length() - 1);
        Integer id = CATEGORY_IDS.get(path.substring(path.lastIndexOf('/') + 1));
        return id == null ? root : id.toString();
    }

    @Override
    public String inGenre(String siteWide, String genre) {
        if (genre.startsWith("http")) {
            return genre; // no id to rank it by: the genre's own listing
        }
        String cat = FILTER + "cat=" + genre + "/";
        return siteWide.equals(INDEX) ? cat + "sort=date/order=desc/" : siteWide.replace(FILTER, cat);
    }

    @Override
    public ProviderPage<GenrePOJO> genres(Document page, String url, int pageNo) {
        return ProviderPage.of(parseGenres(page), false);
    }

    @Override
    public String searchUrl(SearchUrls.Category category, String query, int page) {
        return category == SearchUrls.Category.BOOKS
                ? SERVER + "/search/" + SearchUrls.segment(query) + "/page/" + page + "/" : null;
    }

    @Override
    public ProviderPage<BookPOJO> searchResults(Document page, int pageNo) {
        return parseBooks(page, pageNo);
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
    public ArrayList<AudioPOJO> tracks(Document page, String base) {
        return parseTracks(page, base);
    }

    @Override
    public ArrayList<SeriesPOJO> series(Document page, String bookUrl) throws IOException {
        String series = parseSeriesUrl(page);
        return series.isEmpty() ? new ArrayList<>() : parseSeries(fetch(series));
    }

    @Override
    public ArrayList<ComentsPOJO> comments(String bookUrl, BooleanSupplier cancelled) throws Exception {
        return parseComments(fetch(bookUrl));
    }

    // ---------------------------------------------------------------- listings

    /** A listing, filter, search, author, reader or series page; more pages while the pager goes past {@code page}. */
    public static ProviderPage<BookPOJO> parseBooks(Document doc, int page) {
        Elements cards = doc.select("#dle-content .short-item.bookitem");
        ArrayList<BookPOJO> result = new ArrayList<>();
        for (Element card : cards) {
            BookPOJO book = card(card);
            if (book != null && !book.isNull()) {
                result.add(book);
            }
        }
        int last = 0;
        for (Element a : doc.select(".pagination a")) {
            last = Math.max(last, number(a.text(), 0)); // "»" counts nothing
        }
        return ProviderPage.of(result, last > page, cards.size());
    }

    private static BookPOJO card(Element card) {
        Element a = card.selectFirst(".bookitem_name a");
        if (a == null) {
            return null;
        }
        BookPOJO book = new BookPOJO();
        book.setUrl(a.absUrl("href"));
        book.setName(a.text().trim());
        book.setPhoto(imgSrc(card.selectFirst("a.short-img img")));
        Element genre = card.selectFirst(".bookitem_genre a");
        if (genre != null) {
            book.setGenre(genre.text());
            book.setUrlGenre(listing(genre));
        }
        Elements author = card.select(".icon_author a");
        if (!author.isEmpty()) {
            book.setAutor(names(author));
            book.setUrlAutor(listing(author.first()));
        }
        Elements reader = card.select(".icon_reader a");
        if (!reader.isEmpty()) {
            book.setArtist(names(reader));
            book.setUrlArtist(listing(reader.first()));
        }
        Element series = card.selectFirst(".icon_serie a[href*=/xfsearch/serie/]");
        if (series != null) {
            book.setSeries(series.text());
            book.setUrlSeries(listing(series));
        }
        Element time = card.selectFirst(".icon_time .bookitem_meta_block_content");
        if (time != null && !time.text().trim().isEmpty()) {
            book.setTime(time.text().trim());
        }
        return book;
    }

    /** The genres of the sidebar with their book counts, as listings to page through. */
    public static ArrayList<GenrePOJO> parseGenres(Document doc) {
        ArrayList<GenrePOJO> result = new ArrayList<>();
        Elements blocks = doc.select(".spisok-ganrov");
        // the first block is the site menu (home, takedown, bookmarks)
        for (int i = 1; i < blocks.size(); i++) {
            for (Element a : blocks.get(i).select("a")) {
                String name = text(a, ".genre_name");
                if (name.isEmpty() || a.absUrl("href").isEmpty()) {
                    continue;
                }
                GenrePOJO genre = new GenrePOJO();
                genre.setName(name);
                genre.setUrl(listing(a));
                genre.setDescription(text(a, ".genre_kol"));
                genre.setReting(number(text(a, ".genre_kol"), 0)); // the genre list sorts by it
                result.add(genre);
            }
        }
        return result;
    }

    // ---------------------------------------------------------------- book page

    public static DescriptionPOJO parseDescription(Document doc) {
        DescriptionPOJO description = new DescriptionPOJO();
        description.setTitle(text(doc, "h1.title_item"));
        description.setPoster(imgSrc(doc.selectFirst(".poster_item img")));
        Element a = stat(doc, "Автор");
        if (a != null) {
            description.setAutor(a.text());
            description.setAutorUrl(listing(a));
        }
        a = stat(doc, "Читает");
        if (a != null) {
            description.setArtist(a.text());
            description.setArtistUrl(listing(a));
        }
        a = stat(doc, "Серия");
        if (a != null) {
            description.setSeries(a.text());
            description.setSeriesUrl(listing(a));
        }
        a = stat(doc, "Жанр");
        if (a != null) {
            description.setGenre(a.text());
            description.setGenreUrl(listing(a));
        }
        String time = totalTime(doc);
        if (!time.isEmpty()) {
            description.setTime(time);
        }
        description.setDescription(annotation(doc));
        description.setLike(text(doc, ".full-news-rating [id^=likes-id-]"));
        description.setDisLike(text(doc, ".full-news-rating [id^=dislikes-id-]"));
        return description;
    }

    /** The book of a deep link. */
    public static BookPOJO parseBook(Document doc, String url) {
        BookPOJO book = BookPOJO.of(parseDescription(doc), url);
        book.setTime(totalTime(doc));
        return book;
    }

    /**
     * The chapters of {@code playerInit}: direct MP3s with their durations. A book the site blocked, or one it only
     * links to a LitRes fragment of, is a {@link BookRemovedException}.
     */
    public static ArrayList<AudioPOJO> parseTracks(Document doc, String bookUrl) {
        String list = playlist(doc);
        if (list == null) {
            if (doc.selectFirst(".sukanah, .llitres") != null) {
                throw new BookRemovedException(bookUrl);
            }
            throw new IllegalStateException("playerInit not found on " + bookUrl);
        }
        String bookName = text(doc, "h1.title_item");
        ArrayList<AudioPOJO> result = new ArrayList<>();
        for (JsonElement item : JsonParser.parseString(list).getAsJsonArray()) {
            JsonObject o = item.getAsJsonObject();
            String url = string(o, "url");
            if (url.isEmpty()) {
                continue;
            }
            AudioPOJO audio = new AudioPOJO();
            audio.setBookName(bookName);
            audio.setName(string(o, "title").trim());
            audio.setUrl(url);
            audio.setTime(o.has("duration") ? Math.max(0, o.get("duration").getAsInt()) : 0);
            result.add(audio);
        }
        if (result.isEmpty()) {
            throw new IllegalStateException("no playable chapters on " + bookUrl);
        }
        return result;
    }

    /** The series listing of a book page ({@code /xfsearch/serie/<name>/}); "" when it has none. */
    public static String parseSeriesUrl(Document doc) {
        Element a = stat(doc, "Серия");
        return a == null ? "" : listing(a);
    }

    /** The books of a series listing, by their number in the series. */
    public static ArrayList<SeriesPOJO> parseSeries(Document doc) {
        ArrayList<SeriesPOJO> result = new ArrayList<>();
        for (Element card : doc.select("#dle-content .short-item.bookitem")) {
            Element a = card.selectFirst(".bookitem_name a");
            if (a == null || a.absUrl("href").isEmpty()) {
                continue;
            }
            SeriesPOJO series = new SeriesPOJO();
            series.setUrl(a.absUrl("href"));
            series.setName(a.text().trim());
            series.setPhoto(imgSrc(card.selectFirst("a.short-img img")));
            Element link = card.selectFirst(".icon_serie .bookitem_meta_link");
            Matcher n = NUMBER.matcher(link == null ? "" : link.ownText()); // "(3)" after the series link
            if (n.find()) {
                series.setNumber(n.group(1));
            }
            result.add(series);
        }
        Collections.sort(result, (x, y) -> Integer.compare(number(x.getNumber(), Integer.MAX_VALUE),
                number(y.getNumber(), Integer.MAX_VALUE)));
        return result;
    }

    /** The comments of a book page in page order (newest first); replies of any depth join their thread. */
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
            Element item = li.selectFirst(".comments_item"); // its own block comes before its replies
            if (item != null) {
                comment(item, pojo);
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

    private static void comment(Element item, ContentParentPOJO pojo) {
        Element user = item.selectFirst(".comments_user");
        if (user != null) {
            Element a = user.selectFirst("a");
            pojo.setName(a != null ? a.text().trim() : user.ownText().trim());
            pojo.setDate(text(user, "span"));
        }
        pojo.setImage(imgSrc(item.selectFirst(".comments_ava img")));
        String rating = text(item, ".rate .ratingtypeplusminus");
        pojo.setReting(rating.equals("0") ? "" : rating);
        Element body = item.selectFirst(".comments_descr > div");
        if (body == null) {
            return;
        }
        Element quoteTitle = body.selectFirst(".title_quote");
        if (quoteTitle != null) {
            pojo.setQuoteName(quoteTitle.text().replace("Цитата:", "").trim());
        }
        Element quote = body.selectFirst(".quote");
        if (quote != null) {
            pojo.setQuoteText(quote.text());
        }
        Element own = body.clone();
        own.select(".title_quote, .quote").remove();
        pojo.setText(own.text());
    }

    // ---------------------------------------------------------------- helpers

    /** The {@code […]} of the page's {@code playerInit(…)}; null when there is none. */
    private static String playlist(Document doc) {
        for (Element script : doc.select("script")) {
            String data = script.data();
            if (data.contains("playerInit(")) {
                return JsonSlice.after(data, "playerInit(", '[');
            }
        }
        return null;
    }

    /** The sum of the chapter durations as H:MM:SS (the page fills its own "Время" by script); "" without chapters. */
    private static String totalTime(Document doc) {
        String list = playlist(doc);
        if (list == null) {
            return "";
        }
        int seconds = 0;
        try {
            for (JsonElement item : JsonParser.parseString(list).getAsJsonArray()) {
                JsonElement d = item.getAsJsonObject().get("duration");
                seconds += d == null ? 0 : d.getAsInt();
            }
        } catch (RuntimeException e) {
            return "";
        }
        return seconds <= 0 ? ""
                : String.format(Locale.ROOT, "%d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60);
    }

    /** The annotation without the channel promo paragraph and the tags. */
    private static String annotation(Document doc) {
        StringBuilder out = new StringBuilder();
        for (Element p : doc.select(".full_descr > p")) {
            if (p.ownText().trim().isEmpty() && p.selectFirst("a[href*=t.me]") != null) {
                continue;
            }
            String text = p.text().trim();
            if (!text.isEmpty()) {
                out.append(out.length() == 0 ? "" : "\n").append(text);
            }
        }
        return out.toString();
    }

    /** The first link of the "Автор:"/"Читает:"/"Серия:"/"Жанр:" row of the book's stats. */
    private static Element stat(Document doc, String label) {
        for (Element row : doc.select(".full-news-stats .fstat-item")) {
            if (text(row, ".fstat-item-title").startsWith(label)) {
                return row.selectFirst("a[href]");
            }
        }
        return null;
    }

    /** A listing link as {@link Dle#pageUrl} pages it: absolute, with its trailing slash. */
    private static String listing(Element a) {
        String href = a.absUrl("href");
        return href.isEmpty() || href.endsWith("/") ? href : href + "/";
    }

    private static String names(Elements links) {
        StringBuilder out = new StringBuilder();
        for (Element a : links) {
            out.append(out.length() == 0 ? "" : ", ").append(a.text().trim());
        }
        return out.toString();
    }

    private static String imgSrc(Element img) {
        if (img == null) {
            return "";
        }
        String src = img.absUrl("data-src");
        return src.isEmpty() ? img.absUrl("src") : src;
    }

    private static String string(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e == null || e.isJsonNull() ? "" : e.getAsString();
    }

    private static int number(String s, int none) {
        Matcher m = NUMBER.matcher(s == null ? "" : s);
        return m.find() ? Integer.parseInt(m.group(1)) : none;
    }
}
