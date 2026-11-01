package com.fanok.audiobooks.source;

import static com.fanok.audiobooks.pojo.BookPOJO.absUrl;
import static com.fanok.audiobooks.search.ListingFilters.isoDate;
import static com.fanok.audiobooks.search.ListingFilters.nearest;
import static com.fanok.audiobooks.search.ListingFilters.segment;
import static com.fanok.audiobooks.search.ListingFilters.withSegment;
import static com.fanok.audiobooks.source.Html.cards;
import static com.fanok.audiobooks.source.Html.each;
import static com.fanok.audiobooks.source.Html.href;
import static com.fanok.audiobooks.source.Html.imgSrc;
import static com.fanok.audiobooks.source.Html.lastPage;
import static com.fanok.audiobooks.source.Html.rows;
import static com.fanok.audiobooks.source.Html.text;
import static com.fanok.audiobooks.source.Json.array;
import static com.fanok.audiobooks.source.Json.arrayOf;
import static com.fanok.audiobooks.source.Json.object;
import static com.fanok.audiobooks.source.Json.seconds;
import static com.fanok.audiobooks.source.Json.string;
import static com.fanok.audiobooks.source.Players.decodeBase64;
import static com.fanok.audiobooks.source.Players.stringLiteral;

import android.util.Log;

import androidx.annotation.Nullable;

import com.fanok.audiobooks.Consts;
import com.fanok.audiobooks.Url;
import com.fanok.audiobooks.model.ComentsModel;
import com.fanok.audiobooks.model.OtherArtistModel;
import com.fanok.audiobooks.model.OtherSourceModel;
import com.fanok.audiobooks.net.JsonSlice;
import com.fanok.audiobooks.net.SiteHttp;
import com.fanok.audiobooks.pojo.AudioPOJO;
import com.fanok.audiobooks.pojo.BookPOJO;
import com.fanok.audiobooks.pojo.ComentsPOJO;
import com.fanok.audiobooks.pojo.DescriptionPOJO;
import com.fanok.audiobooks.pojo.GenrePOJO;
import com.fanok.audiobooks.pojo.OtherArtistPOJO;
import com.fanok.audiobooks.pojo.SeriesPOJO;
import com.fanok.audiobooks.search.ListingFilters;
import com.fanok.audiobooks.search.ProviderPage;
import com.fanok.audiobooks.search.SearchUrls;
import com.fanok.audiobooks.source.Players.Chapters;
import com.fanok.audiobooks.util.DownloadUtil;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import io.reactivex.functions.BooleanSupplier;

import org.jsoup.Connection;
import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.HttpUrl;

/**
 * baza-knig.top (DataLife Engine): plain GETs that carry the session the user renewed in the settings
 * ({@link SiteHttp#connect}). Listings page with {@code page/N/}; rankings, the duration bound and the publication
 * date are the DLE filter {@code /f/} ({@code f.pdate}, {@code f.prodolzhitelnost}), which takes a category only as
 * {@code cat=<id>} ({@link #loadListing}). Search {@code /f/j.x.autor;p.title=Q/...} (books only). A book page keeps
 * each voice in a tab {@code #content-tabN}, opened as {@code ?sorce=N}; its chapters are the tab's player script, else
 * its {@code #fr} frame (archive.org or a js-play8 playlist). Comments are a JSON ajax answer; the series is a
 * "Цикл:" listing.
 */
public final class BazaKnig implements SourceParser {

    private static final String TAG = "BazaKnig";

    private static final List<Sort.Entry> MENU =
            Sort.menu(Sort.NEW, Sort.POPULAR, Sort.RATING, Sort.COMMENTS);

    /** baza-knig serves 30 comments per page; the bound stops the walk on a site that never sends an empty page. */
    public static final int MAX_COMMENT_PAGES = 50;

    /** Series listing pages read at most. */
    public static final int MAX_SERIES_PAGES = 10;

    /** Search result pages the other-source lookup reads at most before giving up. */
    public static final int MAX_SEARCH_PAGES = 3;

    private static final String BASE64 = "Z83UhncLHApBrM7GvdqT4tNWRjemgak9oVzwK1PXDfY5bQOSlsF26yi0JCIuxE+/";
    private static final Pattern STR_DECODE = Pattern.compile("strDecode\\(\\s*([\"'])");
    private static final Pattern VOICE = Pattern.compile("[?&]sorce=(\\d{1,3})");
    private static final Pattern NEWS_ID = Pattern.compile("/(\\d+)[^/]*/?$");

    /** Category roots as linked → {the root they lead to (a renamed one redirects), the DLE category id}. */
    private static final Map<String, String[]> CATEGORIES = new ConcurrentHashMap<>();
    private static final Pattern CATEGORY_PAGE =
            Pattern.compile("^(" + Pattern.quote(Url.SERVER_BAZA_KNIG) + "/[\\w-]+/)(page/.*|f/.*)$");
    private static final Pattern CATEGORY_ID = Pattern.compile("dle_sort_cat_(\\d+)");
    /** A category root followed by its pages or filter; not the index ({@code /page/2/}) nor {@code /f/} itself. */
    private static final Pattern CATEGORY =
            Pattern.compile("^(" + Pattern.quote(Url.SERVER_BAZA_KNIG) + "/[\\w-]+/)(?:page/|f/)");

    private static final List<ListingFilters.Period> ANY_PERIOD = Arrays.asList(ListingFilters.Period.values());

    /** One comments page (JSON) by its URL; a seam so the paging walk is testable without the network. */
    public interface CommentPages {
        String get(String url) throws IOException;
    }

    // ---------------------------------------------------------------- SourceParser: listings

    @Override
    public ProviderPage<BookPOJO> listing(Document page, String url, int pageNo) {
        return parseListing(page, pageNo);
    }

    /** A category's rankings and a renamed category are read from other pages ({@link #listingPage}). */
    @Override
    public ProviderPage<BookPOJO> loadListing(String url, int pageNo) throws IOException {
        return listing(listingPage(url), url, pageNo);
    }

    @Override
    public String pageUrl(String url, int page) {
        return url + page + "/";
    }

    /** Author, reader and series listings are not re-sorted. */
    @Override
    public List<Sort.Entry> sorts(String url) {
        boolean noSort = url.contains("reader") || url.contains("author") || url.contains("serie")
                || url.contains("cikl") || url.contains("ispolnitel") || url.contains("/xfsearch/autor/");
        return noSort ? Collections.<Sort.Entry>emptyList() : MENU;
    }

    @Override
    public String sortUrl(Sort sort, String listingUrl) {
        switch (sort) {
            case NEW: return Url.NEW_BOOK_BAZA_KNIG;
            case RATING: return Url.RATING_BAZA_KNIG;
            case POPULAR: return Url.BEST_BAZA_KNIG;
            case COMMENTS: return Url.COMENTS_BAZA_KNIG;
            default: return null;
        }
    }

    /** A date bound on the date-sorted list is just a shorter "new" list; rankings take any date. */
    @Override
    public List<ListingFilters.Period> periods(String url) {
        return url.contains("/f/") && !url.contains("sort=date")
                ? ANY_PERIOD : Collections.<ListingFilters.Period>emptyList();
    }

    @Override
    public ListingFilters.Period periodOf(String url, Calendar today) {
        String from = segment(url, "f.pdate");
        if (from == null) {
            return ListingFilters.Period.ALL;
        }
        return nearest(daysBetween(from, today));
    }

    @Override
    public String withPeriod(String url, ListingFilters.Period period, Calendar today) {
        return withSegment(url, "f.pdate", period == ListingFilters.Period.ALL ? null : isoDate(today, period.days));
    }

    /** {@code f.pdate} is an absolute date: the listing keeps the chosen period and recounts it from today. */
    @Override
    public boolean periodsAreDates() {
        return true;
    }

    /**
     * The server's own duration bound on the index and the {@code /f/} pages; the index becomes its date-sorted
     * {@code /f/} twin. The bound is whole hours (the site compares the integer part of HH:MM:SS): {@code from} rounds
     * down to its hour, {@code to} up to the end of the hour before it, so the server never drops a match and the
     * exact range is still applied to the cards afterwards.
     */
    @Override
    public String withDuration(String url, ListingFilters.Duration duration) {
        if (!(isIndex(url) || url.contains("/f/")) || (duration.isAny() && isIndex(url))) {
            return url;
        }
        String base = isIndex(url) ? Url.SERVER_BAZA_KNIG + "/f/sort=date/order=desc/page/" : url;
        int fromHours = duration.fromMinutes / 60;
        String from = fromHours > 0 ? String.format(Locale.ROOT, "%02d:00:00", fromHours) : null;
        String to = duration.isUnbounded() ? null
                : String.format(Locale.ROOT, "%02d:59:59", (duration.toMinutes + 59) / 60 - 1);
        return withSegment(withSegment(base, "t.prodolzhitelnost", to), "f.prodolzhitelnost", from);
    }

    /** The category root of a category listing or of a ranking opened from one (renamed ones end in "_"). */
    @Override
    public String genreOf(String url) {
        Matcher m = CATEGORY.matcher(url);
        return m.find() ? m.group(1) : null;
    }

    /** {@code /<category>/f/} gets the category id when loaded ({@link #listingPage}): the filter ignores the path. */
    @Override
    public String inGenre(String siteWide, String genre) {
        return siteWide.replace(Url.SERVER_BAZA_KNIG + "/", genre);
    }

    // ---------------------------------------------------------------- SourceParser: sections

    @Override
    public ProviderPage<GenrePOJO> genres(Document page, String url, int pageNo) {
        return ProviderPage.of(rows(page.select(".left-menu-items li"), item -> {
            GenrePOJO genrePOJO = new GenrePOJO();
            Element aGenre = item.selectFirst("a");
            if (aGenre != null) {
                genrePOJO.setUrl(Url.SERVER_BAZA_KNIG + aGenre.attr("href") + "page/");
                genrePOJO.setName(item.text());
            }
            return genrePOJO;
        }), false);
    }

    // ---------------------------------------------------------------- SourceParser: search

    @Override
    public String searchUrl(SearchUrls.Category category, String query, int page) {
        if (category != SearchUrls.Category.BOOKS) {
            return null;
        }
        return Url.SERVER_BAZA_KNIG + "/f/j.x.autor;p.title=" + SearchUrls.segment(query)
                + "/v.litres_buy=litres/sort=news_read/order=desc/page/" + page + "/";
    }

    @Override
    public ProviderPage<BookPOJO> searchResults(Document page, int pageNo) {
        return parseListing(page, pageNo);
    }

    // ---------------------------------------------------------------- SourceParser: book page

    @Override
    public DescriptionPOJO description(Document page, String bookUrl) {
        return parseDescription(page, bookUrl);
    }

    @Override
    public BookPOJO book(Document page, String bookUrl) {
        return parseBook(page, bookUrl);
    }

    @Override
    public ArrayList<BookPOJO> recommendations(Document page, String bookUrl) {
        return parseRecommendations(page);
    }

    @Override
    public ArrayList<AudioPOJO> tracks(Document page, String base) throws IOException {
        return tracks(page, base, base);
    }

    /** The voice is the requested one ({@code ?sorce=N}) even when a redirect drops the query. */
    @Override
    public ArrayList<AudioPOJO> tracks(Document page, String base, String requestedUrl) throws IOException {
        return loadTracks(page, base, requestedUrl);
    }

    @Override
    public Map<String, String> mediaHeaders(String bookUrl) {
        Map<String, String> headers = new HashMap<>();
        headers.put("User-Agent", DownloadUtil.DESKTOP_UA);
        headers.put("Referer", Url.SERVER_BAZA_KNIG + "/");
        return headers;
    }

    /** The session the user renewed in the settings, sent with the media requests to the site itself. */
    @Override
    public Map<String, String> mediaCookies(String bookUrl) {
        return cookies();
    }

    /** The session the user pasted in the settings ({@code ActivityCookies}). */
    @Override
    public Map<String, String> cookies() {
        String session = Consts.getBazaKnigCookies();
        if (session != null && !session.isEmpty()) {
            return Collections.singletonMap("PHPSESSID", session);
        }
        return Collections.emptyMap();
    }

    /** Book page → cycle link → listing pages, oldest first. */
    @Override
    public ArrayList<SeriesPOJO> series(Document page, String bookUrl) throws IOException {
        ArrayList<SeriesPOJO> result = new ArrayList<>();
        String series = parseSeriesUrl(page);
        if (series.isEmpty()) {
            return result;
        }
        for (int no = 1; no <= MAX_SERIES_PAGES; no++) {
            Document doc = fetch(seriesPageUrl(series, no));
            result.addAll(parseSeriesPage(doc));
            if (no >= OtherSourceModel.lastPage(doc)) {
                break;
            }
        }
        Collections.reverse(result);
        return result;
    }

    @Override
    public ArrayList<ComentsPOJO> comments(String bookUrl, BooleanSupplier cancelled) throws Exception {
        ComentsModel.Parsed parsed = loadComments(bookUrl, BazaKnig::getJson, cancelled);
        if (parsed.skipped > 0) {
            Log.w(TAG, "skipped " + parsed.skipped + " malformed comments on " + bookUrl);
        }
        return parsed.comments;
    }

    // ---------------------------------------------------------------- SourceParser: other readers, other sources

    @Override
    public boolean findsOtherReaders() {
        return true;
    }

    /** The search by title and author, plus the alternative voice-overs (tabs) of the pages of this book. */
    @Override
    public ArrayList<OtherArtistPOJO> otherReaders(String bookUrl, String title, String author, String reader)
            throws IOException {
        if (OtherSourceModel.isBlank(title)) {
            return new ArrayList<>();
        }
        String terms = OtherSourceModel.query(title, author);
        List<OtherSourceModel.Hit> hits = parseSearch(fetch(otherSourceSearchUrl(terms, 1)));
        ArrayList<OtherArtistPOJO> result = OtherArtistModel.otherReaders(hits, title, author, bookUrl, reader);
        for (OtherSourceModel.Hit hit : hits) {
            if (hit.altVoices && OtherSourceModel.sameBook(hit, title, author)) {
                result.addAll(parseAltReaders(fetch(hit.url), hit.url, reader));
            }
        }
        return result;
    }

    /** The title alone: the site's search finds nothing with the author or reader added. */
    @Override
    public String otherSourceTerms(BookPOJO book) {
        return OtherSourceModel.query(book.getName());
    }

    @Override
    public String otherSourceSearchUrl(String terms, int page) {
        HttpUrl.Builder url = HttpUrl.get(Url.SERVER_BAZA_KNIG).newBuilder();
        url.addPathSegment("index.php").addQueryParameter("do", "search")
                .addQueryParameter("subaction", "search").addQueryParameter("story", terms);
        if (page > 1) {
            url.addQueryParameter("search_start", String.valueOf(page));
        }
        return url.build().toString();
    }

    @Override
    public ArrayList<OtherSourceModel.Hit> otherSourceHits(Document searchPage) {
        return parseSearch(searchPage);
    }

    @Override
    public int otherSourcePages() {
        return MAX_SEARCH_PAGES;
    }

    // ---------------------------------------------------------------- fetch helpers

    /**
     * A page of a listing. A category's own rankings ({@code /<category>/f/...}, see {@link #inGenre}) go to the DLE
     * filter, which takes a category only as {@code cat=<id>}; a category the site renamed ("uzhasy-mistika" →
     * "uzhasy-mistika_") is still linked by its menu under the old name, whose root redirects but whose pages are 404.
     * Both are read off the category's own page, once.
     */
    static Document listingPage(String url) throws IOException {
        Matcher m = CATEGORY_PAGE.matcher(url);
        if (!m.matches()) {
            return SiteHttp.get(url);
        }
        String root = m.group(1);
        String rest = m.group(2);
        if (rest.startsWith("f/")) {
            String id = category(root)[1];
            return SiteHttp.get(id == null ? url : Url.SERVER_BAZA_KNIG + "/f/cat=" + id + "/" + rest.substring(2));
        }
        String[] known = CATEGORIES.get(root);
        try {
            return SiteHttp.get(known != null ? known[0] + rest : url);
        } catch (HttpStatusException e) {
            if (known != null || e.getStatusCode() != 404) {
                throw e;
            }
            String moved = category(root)[0];
            if (moved.equals(root)) {
                throw e;
            }
            return SiteHttp.get(moved + rest);
        }
    }

    private static String[] category(String root) throws IOException {
        String[] known = CATEGORIES.get(root);
        if (known == null) {
            Document doc = SiteHttp.get(root);
            Matcher id = CATEGORY_ID.matcher(doc.html());
            known = new String[]{doc.location(), id.find() ? id.group(1) : null};
            CATEGORIES.put(root, known);
        }
        return known;
    }

    private static boolean isIndex(String url) {
        String base = Url.SERVER_BAZA_KNIG + "/";
        return url.equals(base) || url.equals(Url.INDEX_BAZA_KNIG) || url.equals(base + "page/1/");
    }

    private static int daysBetween(String isoFrom, Calendar today) {
        try {
            String[] p = isoFrom.split("-");
            Calendar from = (Calendar) today.clone();
            from.set(Integer.parseInt(p[0]), Integer.parseInt(p[1]) - 1, Integer.parseInt(p[2]));
            long ms = today.getTimeInMillis() - from.getTimeInMillis();
            return (int) Math.round(ms / (24.0 * 60 * 60 * 1000));
        } catch (RuntimeException e) {
            return ListingFilters.Period.WEEK.days;
        }
    }

    private static String nonEmpty(String s) {
        return s == null || s.trim().isEmpty() ? null : s.trim();
    }

    // ---------------------------------------------------------------- pure parsers: listings

    /** Index, categories, rankings and search results. */
    public static ProviderPage<BookPOJO> parseListing(Document doc, int page) {
        boolean hasMore = lastPage(doc.select(".page-nav .navigation a, .page-nav .navigation span")) > page
                || !doc.select(".page-nav .pnext").isEmpty();
        Element conteiner = doc.getElementById("dle-content");
        if (conteiner == null) {
            return ProviderPage.empty();
        }
        Elements raw = conteiner.getElementsByClass("short");
        ArrayList<BookPOJO> result = cards(raw, book -> {
            BookPOJO bookPOJO = new BookPOJO();
            Element imgConteiner = book.selectFirst(".short-img");
            if (imgConteiner != null) {
                Element aElement = imgConteiner.selectFirst("a");
                if (aElement != null) {
                    if (aElement.attr("href").isEmpty()) {
                        return null;
                    }
                    bookPOJO.setUrl(aElement.attr("href"));
                    Element img = imgConteiner.selectFirst("img");
                    if (img != null) {
                        bookPOJO.setPhoto(Url.SERVER_BAZA_KNIG + img.attr("src"));
                    }
                }
            }
            Element nameTag = book.selectFirst(".short-title a");
            if (nameTag != null) {
                if (nameTag.text().isEmpty()) {
                    return null;
                }
                bookPOJO.setName(nameTag.text());
            }
            Element descCont = book.selectFirst(".short-text");
            if (descCont != null) {
                bookPOJO.setDesc(descCont.text());
            }
            for (Element li : book.select(".short-items li")) {
                String liText = li.text();
                Element b = li.selectFirst("b");
                Element a = li.selectFirst("a");
                if (liText.contains("Автор") && b != null && nonEmpty(b.text()) != null) {
                    bookPOJO.setAutor(b.text());
                    Element aTag = b.selectFirst("a");
                    if (aTag != null) {
                        bookPOJO.setUrlAutor(aTag.attr("href") + "page/");
                    }
                }
                if (liText.contains("Читает") && b != null) {
                    String artistName = liText.replace("Читает: ", "").trim();
                    if (!artistName.isEmpty()) {
                        bookPOJO.setArtist(artistName);
                        Element aTag = b.selectFirst("a");
                        if (aTag != null) {
                            bookPOJO.setUrlArtist(aTag.attr("href") + "page/");
                        }
                    }
                }
                if (liText.contains("Длительность") && b != null && nonEmpty(b.text()) != null) {
                    bookPOJO.setTime(b.text());
                }
                if (liText.contains("Цикл") && a != null && nonEmpty(a.text()) != null) {
                    bookPOJO.setSeries(a.text());
                    bookPOJO.setUrlSeries(a.attr("href") + "page/");
                }
                if (liText.contains("Жанр") && a != null && nonEmpty(a.text()) != null) {
                    bookPOJO.setGenre(a.text());
                    bookPOJO.setUrlGenre(a.attr("href") + "page/");
                }
            }
            Elements comentsClass = book.select(".short-bottom .comments");
            if (comentsClass.size() == 3) {
                bookPOJO.setComents(comentsClass.last().text());
            }
            return bookPOJO;
        });
        return ProviderPage.of(result, hasMore, raw.size());
    }

    // ---------------------------------------------------------------- pure parsers: book page

    public static DescriptionPOJO parseDescription(Document document, String bookUrl) {

        int indexSorce = bookUrl.indexOf("?sorce");
        int sorce = 1;
        if (indexSorce != -1) {
            String substring = bookUrl.substring(indexSorce).replace("?sorce=", "");
            sorce = Integer.parseInt(substring);
        }

        DescriptionPOJO descriptionPOJO = new DescriptionPOJO();

        Elements cont = document.getElementsByClass("reset full-items");
        if (cont.size() > 0) {
            for (Element li : cont.first().getElementsByTag("li")) {
                String liText = li.text();
                Element a = li.getElementsByTag("a").first();

                if (liText.contains("Автор") && a != null) {
                    descriptionPOJO.setAutorUrl(absUrl(a, "href", "page/"));
                    descriptionPOJO.setAutor(a.text());
                }

                if (liText.contains("Читает")) {
                    if (sorce == 1) {
                        if (a != null) {
                            descriptionPOJO.setArtistUrl(absUrl(a, "href", "page/"));
                            descriptionPOJO.setArtist(a.text());
                        }
                    } else if (sorce == 2 || sorce == 3) {
                        Element otherReader = document.getElementById("content-tab" + sorce);
                        if (otherReader != null) {
                            String text = otherReader.ownText();
                            if (text.contains("Озвучивает:")) {
                                descriptionPOJO.setArtist(text.replace("Озвучивает:", "").trim());
                            }
                        }
                    }
                }

                if (liText.contains("Длительность")) {
                    Element timeConteiner = li.getElementsByTag("b").first();
                    if (timeConteiner != null) {
                        descriptionPOJO.setTime(timeConteiner.text());
                    }
                }

                if (liText.contains("Цикл") && a != null && !a.text().isEmpty()) {
                    descriptionPOJO.setSeries(a.text());
                    descriptionPOJO.setSeriesUrl(absUrl(a, "href", "page/"));
                }

                if (liText.contains("Жанр") && a != null && !a.text().isEmpty()) {
                    descriptionPOJO.setGenre(a.text());
                    descriptionPOJO.setGenreUrl(absUrl(a, "href", "page/"));
                }
            }
        }

        Elements imgConteiner = document.getElementsByClass("full-img");
        if (imgConteiner.size() > 0) {
            Element img = imgConteiner.first().getElementsByTag("img").first();
            if (img != null) {
                descriptionPOJO.setPoster(img.absUrl("src"));
            }
        }

        Element title = document.getElementsByTag("h1").first();
        if (title != null) {
            String text = title.ownText();
            int dash = text.indexOf(" - ");
            descriptionPOJO.setTitle((dash == -1 ? text : text.substring(0, dash)).trim());
        }

        Elements desc = document.getElementsByClass("short-text");
        if (desc.size() > 0) {
            descriptionPOJO.setDescription(desc.first().ownText());
        }

        Elements comentsClass = document.getElementsByClass("short-rate main-sliders-rate ignore-select");
        if (comentsClass.size() > 0) {
            Elements elements = comentsClass.first().getElementsByTag("a");
            if (elements.size() == 2) {
                descriptionPOJO.setLike(elements.first().text());
                descriptionPOJO.setDisLike(elements.last().text());
            }
        }

        return descriptionPOJO;
    }

    /** A book opened by its link. */
    public static BookPOJO parseBook(Document document, String url) {

        int indexSorce = url.indexOf("?sorce");
        int sorce = 1;
        if (indexSorce != -1) {
            String substring = url.substring(indexSorce).replace("?sorce=", "");
            sorce = Integer.parseInt(substring);
        }

        BookPOJO bookPOJO = new BookPOJO();

        bookPOJO.setUrl(url);

        Elements cont = document.getElementsByClass("reset full-items");
        if (cont != null && cont.size() > 0) {
            Elements liElem = cont.first().getElementsByTag("li");
            if (liElem != null && liElem.size() > 0) {
                for (Element li : liElem) {
                    String liText = li.text();
                    if (liText != null) {
                        if (liText.contains("Автор")) {
                            Elements aTag = li.getElementsByTag("a");
                            if (aTag != null && aTag.size() > 0) {
                                String aHref = aTag.first().absUrl("href");
                                if (!aHref.isEmpty()) {
                                    bookPOJO.setUrlAutor(aHref + "page/");
                                }
                                String autorName = aTag.first().text();
                                if (autorName != null && !autorName.isEmpty()) {
                                    bookPOJO.setAutor(autorName);

                                }
                            }
                        }

                        if (liText.contains("Читает")) {

                            if (sorce == 1) {
                                Elements aTag = li.getElementsByTag("a");
                                if (aTag != null && aTag.size() > 0) {
                                    String aHref = aTag.first().absUrl("href");
                                    if (!aHref.isEmpty()) {
                                        bookPOJO.setUrlArtist(aHref + "page/");
                                    }
                                    String artistName = aTag.first().text();
                                    if (artistName != null && !artistName.isEmpty()) {
                                        bookPOJO.setArtist(artistName);

                                    }
                                }
                            } else if (sorce == 2) {
                                Element otherReader = document.getElementById("content-tab2");
                                if (otherReader != null) {
                                    String text = otherReader.ownText();
                                    if (text != null && text.contains("Озвучивает:")) {
                                        String name = text.replace("Озвучивает:", "").trim();
                                        bookPOJO.setArtist(name);
                                    }
                                }
                            } else if (sorce == 3) {
                                Element otherReader = document.getElementById("content-tab3");
                                if (otherReader != null) {
                                    String text = otherReader.ownText();
                                    if (text != null && text.contains("Озвучивает:")) {
                                        String name = text.replace("Озвучивает:", "").trim();
                                        bookPOJO.setArtist(name);
                                    }
                                }
                            }
                        }

                        if (liText.contains("Длительность")) {
                            Elements timeConteiner = li.getElementsByTag("b");
                            if (timeConteiner != null && timeConteiner.size() > 0) {
                                String time = timeConteiner.first().text();
                                if (time != null && !time.isEmpty()) {
                                    bookPOJO.setTime(time);
                                }
                            }
                        }

                        if (liText.contains("Цикл")) {
                            Elements seriesConteiner = li.getElementsByTag("a");
                            if (seriesConteiner != null && seriesConteiner.size() > 0) {
                                Element a = seriesConteiner.first();
                                String text = a.text();
                                if (text != null && !text.isEmpty()) {
                                    bookPOJO.setSeries(text);
                                    String href = a.absUrl("href");
                                    if (!href.isEmpty()) {
                                        bookPOJO.setUrlSeries(href + "page/");
                                    }
                                }
                            }
                        }

                        if (liText.contains("Жанр")) {
                            Elements genreConteiner = li.getElementsByTag("a");
                            if (genreConteiner != null && genreConteiner.size() > 0) {
                                String text = genreConteiner.first().text();
                                if (text != null && !text.isEmpty()) {
                                    bookPOJO.setGenre(text);
                                    String href = genreConteiner.first().absUrl("href");
                                    if (!href.isEmpty()) {
                                        bookPOJO.setUrlGenre(href + "page/");
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Elements imgConteiner = document.getElementsByClass("full-img");
        if (imgConteiner != null && imgConteiner.size() > 0) {
            Element element = imgConteiner.first();
            Elements img = element.getElementsByTag("img");
            if (img != null && !img.isEmpty()) {
                String src = img.first().absUrl("src");
                if (src != null) {
                    bookPOJO.setPhoto(src);
                }
            }
        }

        Elements title = document.getElementsByTag("h1");
        if (title != null && !title.isEmpty()) {
            String text = title.first().ownText();
            int dash = text.indexOf(" - ");
            bookPOJO.setName((dash == -1 ? text : text.substring(0, dash)).trim());
        }

        Elements desc = document.getElementsByClass("short-text");
        if (desc != null && !desc.isEmpty()) {
            String text = desc.first().ownText();
            bookPOJO.setDesc(text);
        }

        Elements coments = document.getElementsByClass("comments");
        if (coments != null && !coments.isEmpty()) {
            String text = coments.first().text();
            if (text != null && !text.isEmpty()) {
                bookPOJO.setComents(text);
            }
        }
        return bookPOJO;
    }

    /** The "other books" slider of the book page. */
    public static ArrayList<BookPOJO> parseRecommendations(Document document) {
        ArrayList<BookPOJO> books = new ArrayList<>();

        Element root = document.getElementById("releative-slider");
        if (root != null) {
            Elements elements = root.getElementsByTag("a");
            for (Element book : elements) {
                BookPOJO bookPOJO = new BookPOJO();
                bookPOJO.setUrl(book.absUrl("href"));

                Element img = book.getElementsByTag("img").first();
                if (img != null) {
                    bookPOJO.setPhoto(img.absUrl("src"));
                    bookPOJO.setName(img.attr("alt"));
                }

                if (!bookPOJO.isNull()) {
                    books.add(bookPOJO);
                }
            }
        }

        return books;
    }

    // ---------------------------------------------------------------- tracks: per-voice strDecode or plain player
    // scripts, else the #fr frame (archive.org)

    private static ArrayList<AudioPOJO> loadTracks(Document doc, String base, String url) throws IOException {
        Element scope = scope(doc, url);
        Chapters chapters = scripts(doc, scope, base);
        if (!chapters.isEmpty()) {
            return chapters.done("baza-knig player");
        }
        Element fr = scope.selectFirst("#fr");
        if (fr == null && voice(url) == 1) {
            fr = doc.getElementById("fr");
        }
        String frame = frameUrl(fr, base);
        if (frame == null) {
            return chapters.done("baza-knig player");
        }
        String bookName = bookName(doc);
        if (frame.contains("archive.org")) {
            String metadata = frame.replace("/embed/", "/metadata/").replaceFirst("[?&#].*$", "");
            return parseArchiveOrgMetadata(SiteHttp.body(SiteHttp.connect(metadata)), bookName);
        }
        return parsePlay8(SiteHttp.get(frame), bookName);
    }

    /** Chapters of the voice selected by {@code ?sorce=N} from the page's own scripts. */
    public static ArrayList<AudioPOJO> parseTracks(Document doc, String bookUrl) {
        return scripts(doc, scope(doc, bookUrl), bookUrl).done("baza-knig player");
    }

    /**
     * Voice N lives in {@code #content-tabN}; pages without tabs have one voice. A voice requested
     * by {@code ?sorce=N} whose tab is missing is an error: the whole page would be another voice.
     */
    private static Element scope(Document doc, String bookUrl) {
        String id = "content-tab" + voice(bookUrl);
        Element tab = doc.getElementById(id);
        if (tab == null && VOICE.matcher(bookUrl).find()) {
            throw new IllegalStateException("baza-knig voice tab not found: #" + id);
        }
        return tab != null ? tab : doc;
    }

    private static int voice(String bookUrl) {
        Matcher m = VOICE.matcher(bookUrl);
        return m.find() ? Integer.parseInt(m.group(1)) : 1;
    }

    private static Chapters scripts(Document doc, Element scope, String bookUrl) {
        Chapters chapters = new Chapters(bookName(doc), bookUrl);
        Chapters.ItemParser item = element -> {
            JsonObject o = element.getAsJsonObject();
            return chapters.chapter(string(o, "title"), string(o, "file"), 0);
        };
        for (Element script : scope.select("script")) {
            String data = script.data();
            Matcher m = STR_DECODE.matcher(data);
            try {
                if (m.find()) {
                    String decoded = decode(stringLiteral(data, m.start(1)));
                    chapters.each(array(JsonSlice.from(decoded, 0, '[')), item);
                } else if (data.contains("id:\"player")) {
                    chapters.each(array(JsonSlice.from(data, 0, '[')), item);
                }
            } catch (IllegalStateException notThisScript) {
                // keep looking
            }
            if (!chapters.isEmpty()) {
                break;
            }
        }
        return chapters;
    }

    /** baza-knig's {@code strDecode}: base64 with a shuffled alphabet around UTF-8 JSON. */
    public static String decode(String key) {
        return new String(decodeBase64(key, BASE64), StandardCharsets.UTF_8);
    }

    public static String bookName(Document doc) {
        Element h1 = doc.selectFirst("h1");
        String text = h1 == null ? "" : h1.ownText();
        int dash = text.indexOf(" - ");
        return (dash > 0 ? text.substring(0, dash) : text).trim();
    }

    /** The player frame kept as an iframe snippet in the {@code data} attribute of {@code #fr}, or null. */
    public static String frameUrl(Element fr, String baseUrl) {
        Element frame = fr == null ? null : Jsoup.parseBodyFragment(fr.attr("data"), baseUrl).selectFirst("[src]");
        String src = frame == null ? "" : frame.absUrl("src");
        return src.isEmpty() ? null : src;
    }

    /**
     * archive.org {@code /metadata/<id>}: one MP3 per recording, by name — its original MP3, else
     * one derivative (VBR over fixed bitrate). A file without a name is skipped.
     */
    public static ArrayList<AudioPOJO> parseArchiveOrgMetadata(String json, String bookName) {
        JsonObject root = object(json);
        JsonElement meta = root.get("metadata");
        String id = meta != null && meta.isJsonObject() ? string(meta.getAsJsonObject(), "identifier") : "";
        if (id.isEmpty()) {
            throw new IllegalStateException("archive.org item has no identifier (dark or removed)");
        }
        Chapters chapters = new Chapters(bookName, "https://archive.org/");
        Map<String, JsonObject> recordings = new TreeMap<>();
        for (JsonElement file : arrayOf(root, "files")) {
            JsonObject o = file.isJsonObject() ? file.getAsJsonObject() : null;
            if (o == null || !string(o, "format").contains("MP3")) {
                continue;
            }
            if (string(o, "name").isEmpty()) {
                chapters.skipped++;
                continue;
            }
            String recording = archiveOrgRecording(o);
            JsonObject best = recordings.get(recording);
            if (best == null || archiveOrgRank(o) < archiveOrgRank(best)) {
                recordings.put(recording, o);
            }
        }
        JsonArray files = new JsonArray();
        for (JsonObject file : recordings.values()) {
            files.add(file);
        }
        chapters.each(files, item -> {
            JsonObject o = item.getAsJsonObject();
            String name = string(o, "name");
            String title = string(o, "title");
            try {
                String url = new URI("https", "archive.org", "/download/" + id + "/" + name, null).toASCIIString();
                return chapters.chapter(title.isEmpty() ? name : title, url, seconds(o.get("length")));
            } catch (URISyntaxException e) {
                throw new IllegalStateException(e);
            }
        });
        return chapters.done("archive.org item " + id);
    }

    /** The recording a file belongs to: a derivative's {@code original}, without extension or bitrate suffix. */
    private static String archiveOrgRecording(JsonObject file) {
        String original = string(file, "original");
        String name = original.isEmpty() || "original".equals(string(file, "source")) ? string(file, "name") : original;
        return name.replaceFirst("\\.[^./]*$", "").replaceFirst("(?i)_(\\d+kb|vbr)$", "");
    }

    private static int archiveOrgRank(JsonObject file) {
        return "original".equals(string(file, "source")) ? 0 : "VBR MP3".equals(string(file, "format")) ? 1 : 2;
    }

    /** The frame's own playlist; its relative URLs resolve against the frame's (final) URL. */
    public static ArrayList<AudioPOJO> parsePlay8(Document playlist, String bookName) {
        Element player = playlist.selectFirst(".js-play8-playlist");
        if (player == null) {
            throw new IllegalStateException("js-play8 playlist not found");
        }
        Chapters chapters = new Chapters(bookName, location(playlist, ""));
        int[] index = {0};
        chapters.each(array(player.attr("value")), item -> {
            index[0]++;
            JsonArray sources = arrayOf(item.getAsJsonObject(), "sources");
            String file = sources.size() > 0 && sources.get(0).isJsonObject()
                    ? string(sources.get(0).getAsJsonObject(), "file") : "";
            return chapters.chapter(bookName + "_" + index[0], file, seconds(item.getAsJsonObject().get("duration")));
        });
        return chapters.done("baza-knig js-play8 playlist");
    }

    /** Where {@code doc} was served from after redirects ({@code fallback} when unknown). */
    private static String location(Document doc, String fallback) {
        String location = doc.location();
        return location == null || location.isEmpty() ? fallback : location;
    }

    // ---------------------------------------------------------------- series

    /** The "Цикл:" link of a book page; "" when the book is not part of a cycle. */
    public static String parseSeriesUrl(Document doc) {
        for (Element li : doc.select(".full-items li")) {
            String link = href(li, "a");
            if (li.text().contains("Цикл") && !link.isEmpty()) {
                return link;
            }
        }
        return "";
    }

    /** Page {@code page} of a DLE listing: the listing itself for page 1, {@code …/page/N/} after it. */
    public static String seriesPageUrl(String seriesUrl, int page) {
        if (page <= 1) {
            return seriesUrl;
        }
        return HttpUrl.get(seriesUrl).newBuilder().addPathSegment("page").addPathSegment(String.valueOf(page))
                .addPathSegment("").build().toString();
    }

    public static ArrayList<SeriesPOJO> parseSeriesPage(Document doc) {
        Element content = doc.getElementById("dle-content");
        if (content == null) {
            return new ArrayList<>();
        }
        return each(content.getElementsByClass("short"), book -> {
            String link = href(book, ".short-img a");
            Element title = book.selectFirst(".short-title a");
            String name = title == null ? "" : title.ownText().trim();
            if (link.isEmpty() || name.isEmpty()) {
                return null;
            }
            SeriesPOJO seriesPOJO = new SeriesPOJO();
            seriesPOJO.setUrl(link);
            seriesPOJO.setNumber(text(title, "b"));
            seriesPOJO.setName(name);
            for (Element li : book.select(".short-items li")) {
                if (li.text().contains("Читает")) {
                    seriesPOJO.setName(name + " " + li.text());
                }
            }
            seriesPOJO.setPhoto(imgSrc(book.selectFirst(".short-img img")));
            return seriesPOJO;
        });
    }

    // ---------------------------------------------------------------- comments

    /** One page of comments: flat, newest first; a reply quotes the comment it answers. */
    public static ComentsModel.Parsed parseComments(Document doc, String bookUrl) {
        return ComentsModel.nest(doc.select("div.comm"), null, (comment, pojo) -> {
            pojo.setImage(commentAbsUrl(comment, ".comm-ava img", "src"));
            Element info = comment.selectFirst(".comm-info");
            pojo.setName(commentText(info, "b"));
            pojo.setDate(info == null ? "" : info.ownText());
            Element body = comment.selectFirst(".comm-text > div");
            if (body == null) {
                return;
            }
            Element quoteTitle = body.selectFirst(".title_quote");
            if (quoteTitle != null) {
                String user = quoteTitle.attr("data-commentuser");
                pojo.setQuoteName(user.isEmpty() ? quoteTitle.text().replace("Цитата: ", "") : user);
            }
            Element quote = body.selectFirst(".quote");
            if (quote != null) {
                pojo.setQuoteText(withoutQuotes(quote));
            }
            pojo.setText(withoutQuotes(body));
        });
    }

    /** The HTML of one comments page from the site's JSON answer; empty past the last page. */
    public static String commentsHtml(String json) {
        JsonElement root = JsonParser.parseString(json);
        if (!root.isJsonObject()) {
            return "";
        }
        JsonElement comments = root.getAsJsonObject().get("comments");
        return comments != null && comments.isJsonPrimitive() ? comments.getAsString() : "";
    }

    /** The DLE news id of a book URL ({@code .../182353-name.html} → {@code 182353}). */
    public static String newsId(String url) {
        String path = URI.create(url.trim()).getPath();
        Matcher matcher = NEWS_ID.matcher(path == null ? "" : path);
        if (!matcher.find()) {
            throw new IllegalArgumentException("no book id in " + url);
        }
        return matcher.group(1);
    }

    /** Walks the comment pages until an empty or repeated page, at most {@link #MAX_COMMENT_PAGES}; oldest first. */
    public static ComentsModel.Parsed loadComments(String url, CommentPages source, BooleanSupplier cancelled)
            throws Exception {
        String newsId = newsId(url);
        ComentsModel.Parsed all = new ComentsModel.Parsed();
        String previous = "";
        for (int page = 1; page <= MAX_COMMENT_PAGES && !cancelled.getAsBoolean(); page++) {
            String pageUrl = Url.SERVER_BAZA_KNIG + "/engine/ajax/controller.php?mod=comments&cstart=" + page
                    + "&news_id=" + newsId + "&skin=knigi-pk&massact=disable";
            String html = commentsHtml(source.get(pageUrl));
            if (html.isEmpty() || html.equals(previous)) {
                break;
            }
            previous = html;
            ComentsModel.Parsed more = parseComments(Jsoup.parseBodyFragment(html, pageUrl), url);
            all.comments.addAll(more.comments);
            all.skipped += more.skipped;
        }
        Collections.reverse(all.comments);
        return all;
    }

    private static String getJson(String url) throws IOException {
        Connection.Response response = SiteHttp.execute(SiteHttp.connect(url).ignoreContentType(true));
        String body = response.body();
        if (response.statusCode() >= 400 || !body.trim().startsWith("{")) {
            SiteHttp.parse(response); // throws for a challenge or an HTTP error page
            throw new IOException("not a comments page: " + url);
        }
        return body;
    }

    /** Text of a comment body (or quote) without the quotes nested in it. */
    private static String withoutQuotes(Element body) {
        Element copy = body.clone();
        for (Element nested : copy.select(".quote_block, .title_quote, .quote")) {
            if (nested != copy) {
                nested.remove();
            }
        }
        return copy.text();
    }

    private static String commentText(@Nullable Element scope, String query) {
        Element element = scope == null ? null : scope.selectFirst(query);
        return element == null ? "" : element.text();
    }

    private static String commentAbsUrl(@Nullable Element scope, String query, String attribute) {
        Element element = scope == null ? null : scope.selectFirst(query);
        return element == null ? "" : element.absUrl(attribute);
    }

    // ---------------------------------------------------------------- search, other readers

    /** The other-source search: its book cards as hits, flagged when they advertise alternative voice-overs. */
    public static ArrayList<OtherSourceModel.Hit> parseSearch(Document doc) {
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
            return new OtherSourceModel.Hit(url, title == null ? "" : title.ownText().trim(), author, reader, alt);
        });
    }

    /** Alternative voice-overs are tabs 2, 3, … of one page; each opens as {@code ?sorce=N}. */
    public static ArrayList<OtherArtistPOJO> parseAltReaders(Document doc, String bookUrl, @Nullable String reader) {
        return each(doc.select("section[id^=content-tab]"), tab -> {
            String number = tab.id().substring("content-tab".length());
            if (!number.matches("\\d+") || Integer.parseInt(number) < 2) {
                return null;
            }
            String name = text(tab, "a[href*=/ispolnitel/]");
            if (name.isEmpty()) {
                String own = tab.ownText();
                int at = own.indexOf("Озвучивает:");
                name = at < 0 ? "" : own.substring(at + "Озвучивает:".length()).trim();
            }
            if (name.isEmpty() || (!OtherSourceModel.isBlank(reader) && OtherSourceModel.samePerson(name, reader))) {
                return null;
            }
            OtherArtistPOJO pojo = new OtherArtistPOJO();
            pojo.setName(name);
            pojo.setUrl(HttpUrl.get(bookUrl).newBuilder().setQueryParameter("sorce", number).build().toString());
            return pojo;
        });
    }
}
