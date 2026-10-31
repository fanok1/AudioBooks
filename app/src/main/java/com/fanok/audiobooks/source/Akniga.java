package com.fanok.audiobooks.source;

import static com.fanok.audiobooks.pojo.BookPOJO.absUrl;
import static com.fanok.audiobooks.source.Html.cards;
import static com.fanok.audiobooks.source.Html.each;
import static com.fanok.audiobooks.source.Html.href;
import static com.fanok.audiobooks.source.Html.imgSrc;
import static com.fanok.audiobooks.source.Html.rows;
import static com.fanok.audiobooks.source.Json.object;
import static com.fanok.audiobooks.source.Json.seconds;
import static com.fanok.audiobooks.source.Json.string;
import static com.fanok.audiobooks.source.Players.resolve;

import android.util.Log;

import androidx.annotation.Nullable;

import com.fanok.audiobooks.R;
import com.fanok.audiobooks.Url;
import com.fanok.audiobooks.model.ComentsModel;
import com.fanok.audiobooks.model.OtherArtistModel;
import com.fanok.audiobooks.model.OtherSourceModel;
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
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import io.reactivex.functions.BooleanSupplier;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.HttpUrl;

/**
 * akniga.org ({@link Url#SERVER_AKNIGA}): plain GETs with a {@code <page>} template ({@code /index/page<page>/},
 * {@code /section/<g>/page<page>/}, the rankings {@code top|discussed/page<page>/?period=1|7|30|365|all}, the
 * collections). The authors, readers and sections are one table shape. Search {@code /search/books/page<N>/?q=Q};
 * the author and reader searches are ajax endpoints POSTed with the page's security key ({@link #fetch}), one page
 * only. Book pages give a security key and a book id; the chapters come from {@code /ajax/player/token} and
 * {@code /ajax/b/<bid>} as one encrypted HLS URL clipped per chapter. The series is a block of the page or a
 * {@code /series/} page; the comments are on the page, the rest in its RSS feed. Other narrations are found by
 * searching the title and author.
 */
public final class Akniga implements SourceParser {

    private static final String TAG = "ComentsModel";

    /** Passphrase reverse-engineered from akniga's obfuscated player script. */
    private static final String PASSPHRASE = "EKxtcg46V";
    private static final Pattern SECURITY_KEY
            = Pattern.compile("LIVESTREET_SECURITY_KEY\\s*=\\s*['\"]([^'\"]+)['\"]");
    private static final Pattern FEED_ID = Pattern.compile("#comment(\\d+)$");
    private static final Pattern SECTION =
            Pattern.compile("^" + Pattern.quote(Url.SERVER_AKNIGA) + "/section/[^/?#]+/");
    private static final List<ListingFilters.Period> PERIODS = Arrays.asList(ListingFilters.Period.DAY,
            ListingFilters.Period.WEEK, ListingFilters.Period.MONTH, ListingFilters.Period.YEAR,
            ListingFilters.Period.ALL);

    private static final Sort.Entry NEW = Sort.item(Sort.NEW);
    private static final Sort.Entry POPULAR = Sort.submenu(R.string.popular, Sort.POPULAR_DAY, Sort.POPULAR_WEEK,
            Sort.POPULAR_MONTH, Sort.POPULAR_YEAR, Sort.POPULAR_ALL_TIME);
    private static final Sort.Entry DISCUSSED = Sort.submenu(R.string.order_discussed, Sort.RATING_DAY,
            Sort.RATING_WEEK, Sort.RATING_MONTH, Sort.RATING_YEAR, Sort.RATING_ALL_TIME);
    private static final Sort.Entry COLLECTIONS = Sort.submenu(R.string.order_collections, Sort.COLLECTIONS_NEW,
            Sort.COLLECTIONS_TOP, Sort.COLLECTIONS_DISCUSSED);
    private static final List<Sort.Entry> MENU =
            Collections.unmodifiableList(Arrays.asList(NEW, POPULAR, DISCUSSED, COLLECTIONS));
    /** The collections are site-wide: not offered within a genre. */
    private static final List<Sort.Entry> MENU_IN_GENRE =
            Collections.unmodifiableList(Arrays.asList(NEW, POPULAR, DISCUSSED));

    // ---------------------------------------------------------------- SourceParser

    /**
     * The author and reader searches ({@link #searchUrl}) are POSTed: q and page are read back from the URL, page 1
     * only; everything else is a GET.
     */
    @Override
    public Document fetch(String url) throws IOException {
        if (url.startsWith(Url.SERVER_AKNIGA + "/authors/ajax-search/")
                || url.startsWith(Url.SERVER_AKNIGA + "/performers/ajax-search/")) {
            return searchPeoplePage(url);
        }
        return SiteHttp.get(url);
    }

    @Override
    public String referer() {
        return Url.SERVER_AKNIGA + "/";
    }

    /** The HLS playlists and segments ({@code r1.akniga.club}): they follow the site's proxy route. */
    @Override
    public Set<String> hosts() {
        return Collections.singleton("akniga.club");
    }

    @Override
    public ProviderPage<BookPOJO> listing(Document page, String url, int pageNo) {
        return parseListing(page, pageNo);
    }

    @Override
    public String pageUrl(String url, int page) {
        return url.replace("<page>", Integer.toString(page));
    }

    /** A collection ("Подборка") card opens as the list of its books, all on one page in the listing's markup. */
    @Override
    public boolean opensAsListing(String url) {
        return url.contains("/collection/");
    }

    /** Author, reader, series and collection listings are not re-sorted; the collections are not offered within a genre. */
    @Override
    public List<Sort.Entry> sorts(String url) {
        if (url.contains("reader") || url.contains("author") || url.contains("serie") || url.contains("cikl")
                || opensAsListing(url)) {
            return Collections.emptyList();
        }
        return genreOf(url) == null ? MENU : MENU_IN_GENRE;
    }

    @Override
    public String sortUrl(Sort sort, String listingUrl) {
        switch (sort) {
            case NEW: return Url.NEW_BOOK_AKNIGA;
            case POPULAR_DAY: return Url.BEST_TODAY_AKNIGA;
            case POPULAR_WEEK: return Url.BEST_WEEK_AKNIGA;
            case POPULAR_MONTH: return Url.BEST_MONTH_AKNIGA;
            case POPULAR_YEAR: return Url.BEST_YEAR_AKNIGA;
            case POPULAR_ALL_TIME:
            case POPULAR: return Url.BEST_ALL_TIME_AKNIGA;
            case RATING_DAY: return Url.RATING_TODATY_AKNIGA;
            case RATING_WEEK: return Url.RATING_WEEK_AKNIGA;
            case RATING_MONTH: return Url.RATING_MONTH_AKNIGA;
            case RATING_YEAR: return Url.RATING_YEAR_AKNIGA;
            case RATING_ALL_TIME:
            case RATING: return Url.RATING_ALL_TIME_AKNIGA;
            case COLLECTIONS_NEW: return Url.COLLECTIONS_AKNIGA;
            case COLLECTIONS_TOP: return Url.COLLECTIONS_TOP_AKNIGA;
            case COLLECTIONS_DISCUSSED: return Url.COLLECTIONS_DISCUSSED_AKNIGA;
            default: return listingUrl;
        }
    }

    /** The collections ("Подборки") list collections, not books, and carry no duration. */
    @Override
    public boolean listsDuration(String url) {
        return !url.contains("/collections/");
    }

    /** {@code top}/{@code discussed} take {@code period=} 1, 7, 30, 365, all. */
    @Override
    public List<ListingFilters.Period> periods(String url) {
        return (url.contains("/top/") || url.contains("/discussed/")) && url.contains("period=")
                ? PERIODS : Collections.<ListingFilters.Period>emptyList();
    }

    @Override
    public ListingFilters.Period periodOf(String url, Calendar today) {
        String p = ListingFilters.param(url, "period");
        if (p == null) {
            return null;
        }
        if (p.equals("all")) {
            return ListingFilters.Period.ALL;
        }
        for (ListingFilters.Period period : PERIODS) {
            if (Integer.toString(period.days).equals(p)) {
                return period;
            }
        }
        return ListingFilters.Period.WEEK; // what the site falls back to
    }

    @Override
    public String withPeriod(String url, ListingFilters.Period period, Calendar today) {
        return ListingFilters.withParam(url, "period",
                period == ListingFilters.Period.ALL ? "all" : Integer.toString(period.days));
    }

    /** The section root {@code /section/<g>/} of a section listing or ranking. */
    @Override
    public String genreOf(String url) {
        Matcher m = SECTION.matcher(url);
        return m.find() ? m.group() : null;
    }

    /** The section's own top/discussed ({@code /section/<g>/top/}). */
    @Override
    public String inGenre(String siteWide, String genre) {
        return siteWide.replace(Url.SERVER_AKNIGA + "/index/", genre);
    }

    @Override
    public ProviderPage<GenrePOJO> genres(Document page, String url, int pageNo) {
        return ProviderPage.of(parseTable(page), false);
    }

    @Override
    public ProviderPage<GenrePOJO> people(Document page, String url, int pageNo, boolean performers) {
        boolean hasMore = !page.select(".page__nav .page__nav--next").isEmpty();
        return ProviderPage.of(parseTable(page), hasMore);
    }

    @Override
    public String searchUrl(SearchUrls.Category category, String query, int page) {
        String q = SearchUrls.enc(query);
        switch (category) {
            case BOOKS: return Url.SERVER_AKNIGA + "/search/books/page" + page + "/?q=" + q;
            // the ajax endpoints are POSTed: the fetcher reads q and page back from this URL
            case AUTHORS: return Url.SERVER_AKNIGA + "/authors/ajax-search/?q=" + q + "&page=" + page;
            case PERFORMERS: return Url.SERVER_AKNIGA + "/performers/ajax-search/?q=" + q + "&page=" + page;
        }
        return null;
    }

    @Override
    public ProviderPage<BookPOJO> searchResults(Document page, int pageNo) {
        return parseListing(page, pageNo);
    }

    /** One page only (SRCH-07). */
    @Override
    public ProviderPage<GenrePOJO> searchPeople(Document page, int pageNo, boolean performers) {
        return pageNo > 1 ? ProviderPage.<GenrePOJO>empty() : ProviderPage.of(parseTable(page), false);
    }

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

    /** Page → token → book JSON with an encrypted HLS URL. */
    @Override
    public ArrayList<AudioPOJO> tracks(Document doc, String url) throws IOException {
        String key = securityKey(doc);
        String bid = bid(doc);
        String book = null;
        if (!key.isEmpty() && !bid.isEmpty()) {
            String token = string(object(SiteHttp.body(post(url, "/ajax/player/token")
                    .data("security_ls_key", key)
                    .data("bid", bid)
                    .data("ts", String.valueOf(System.currentTimeMillis())))), "token");
            book = SiteHttp.body(post(url, "/ajax/b/" + bid)
                    .data("security_ls_key", key)
                    .data("bid", bid)
                    .data("token", token)
                    .data("hls", "true"));
        }
        return parseTracks(doc, book, url);
    }

    /** The series block of the page, else the page itself when it is a series, else the linked series page. */
    @Override
    public ArrayList<SeriesPOJO> series(Document doc, String url) throws IOException {
        ArrayList<SeriesPOJO> result = parseSeries(doc);
        if (!result.isEmpty()) {
            return result;
        }
        if (url.contains("/series/")) {
            return parseSeriesPage(doc);
        }
        String series = href(doc, "a[href*=/series/]");
        return series.isEmpty() ? result : parseSeriesPage(fetch(series));
    }

    /** The comments on the page, plus those only its RSS feed holds (best effort). */
    @Override
    public ArrayList<ComentsPOJO> comments(String url, BooleanSupplier cancelled) throws Exception {
        Document page = fetch(url);
        ComentsModel.Parsed parsed = parseComments(page, url);
        String feedUrl = feedUrl(page);
        if (feedUrl != null) {
            try {
                Connection feed = SiteHttp.connect(feedUrl).parser(Parser.xmlParser());
                ComentsModel.Parsed more = parseCommentsFeed(SiteHttp.parse(SiteHttp.execute(feed)), page);
                parsed.comments.addAll(more.comments);
                parsed.skipped += more.skipped;
            } catch (IOException e) {
                Log.w(TAG, "akniga comments feed failed; showing the comments on the page", e);
            }
        }
        if (parsed.skipped > 0) {
            Log.w(TAG, "skipped " + parsed.skipped + " malformed comments on " + url);
        }
        return parsed.comments;
    }

    @Override
    public boolean findsOtherReaders() {
        return true;
    }

    /** Searches the title and author and keeps the other readings. */
    @Override
    public ArrayList<OtherArtistPOJO> otherReaders(String bookUrl, String title, String author, String reader)
            throws IOException {
        if (OtherSourceModel.isBlank(title)) {
            return new ArrayList<>();
        }
        String terms = OtherSourceModel.query(title, author);
        return OtherArtistModel.otherReaders(parseSearch(fetch(otherSourceSearchUrl(terms, 1))), title, author,
                bookUrl, reader);
    }

    /** The search page without a page number, as the other-source lookup has always asked it. */
    @Override
    public String otherSourceSearchUrl(String terms, int page) {
        return HttpUrl.get(Url.SERVER_AKNIGA).newBuilder().addPathSegments("search/books")
                .addQueryParameter("q", terms).build().toString();
    }

    @Override
    public ArrayList<OtherSourceModel.Hit> otherSourceHits(Document searchPage) {
        return parseSearch(searchPage);
    }

    // ---------------------------------------------------------------- requests

    /**
     * akniga's ajax search: a security key and cookies from the listing page, then a POST; an empty page for any page
     * after the first and for an answer without rows.
     */
    private static Document searchPeoplePage(String url) throws IOException {
        HttpUrl parsed = HttpUrl.parse(url);
        if (parsed == null || pageOf(parsed) > 1) {
            return Jsoup.parse("");
        }
        String query = parsed.queryParameter("q");
        String endpoint = parsed.newBuilder().query(null).build().toString();
        Document listing = SiteHttp.get(Url.SERVER_AKNIGA + "/authors/");
        String key = "";
        for (Element script : listing.getElementsByTag("script")) {
            String text = script.data();
            int at = text.indexOf("LIVESTREET_SECURITY_KEY");
            if (at >= 0) {
                text = text.substring(at).replace("LIVESTREET_SECURITY_KEY = '", "");
                key = text.substring(0, Math.max(0, text.indexOf("'")));
                break;
            }
        }
        Connection connection = SiteHttp.connect(endpoint)
                .method(Connection.Method.POST)
                .referrer(Url.SERVER_AKNIGA + "/authors/")
                .ignoreContentType(true)
                .data("security_ls_key", key)
                .data("sText", query == null ? "" : query);
        String body = SiteHttp.execute(connection).body();
        JsonElement json = JsonParser.parseString(body.replaceAll("\\n", ""));
        if (!json.isJsonObject() || !json.getAsJsonObject().has("html")) {
            return Jsoup.parse("");
        }
        String html = json.getAsJsonObject().get("html").getAsString();
        return Jsoup.parse("<html><head></head><body><table><tbody>" + html + "</tbody></table></body></html>");
    }

    /** The {@code page} of a {@link #searchUrl} ajax URL; 1 when it carries none. */
    private static int pageOf(HttpUrl url) {
        try {
            String page = url.queryParameter("page");
            return page == null ? 1 : Integer.parseInt(page);
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    /** Session cookies of the book page come from the {@link SiteHttp} jar. */
    private static Connection post(String bookUrl, String path) {
        return SiteHttp.connect(Url.SERVER_AKNIGA + path)
                .method(Connection.Method.POST)
                .referrer(bookUrl)
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Origin", Url.SERVER_AKNIGA)
                .header("Accept-Language", SiteHttp.ACCEPT_LANGUAGE);
    }

    // ---------------------------------------------------------------- pure parsers

    /** Index, sections, rankings, author/reader/series pages and search results; paid books and fragments are left out. */
    public static ProviderPage<BookPOJO> parseListing(Document document, int page) {
        boolean hasMore = !document.select(".page__nav .page__nav--next").isEmpty();
        Elements raw = document.getElementsByClass("content__main__articles--item");
        ArrayList<BookPOJO> result = cards(raw, book -> {
            if (!book.getElementsByAttributeValue("href", Url.SERVER_AKNIGA + "/paid/").isEmpty()) {
                return null;
            }
            Element fragment = book.selectFirst(".caption__article-preview");
            if (fragment != null && fragment.text().equals("Фрагмент")) {
                return null;
            }
            BookPOJO bookPOJO = new BookPOJO();
            Element genre = book.selectFirst(".section__title");
            if (genre != null) {
                bookPOJO.setGenre(genre.text());
                bookPOJO.setUrlGenre(genre.attr("href") + "page<page>/");
            }
            for (Element img : book.select("img")) {
                String src = img.attr("data-src");
                if (src.isEmpty()) {
                    src = img.attr("data-original");
                }
                if (src.isEmpty()) {
                    src = img.attr("src");
                }
                if (!src.isEmpty() && !src.contains("blank.gif") && !src.contains("pixel.gif")) {
                    bookPOJO.setPhoto(src);
                    break;
                }
            }
            if (bookPOJO.getPhoto() == null) {
                for (Element bgElem : book.select("[style*='background-image']")) {
                    String style = bgElem.attr("style");
                    int start = style.indexOf("url(");
                    int end = style.indexOf(")", start);
                    if (start >= 0 && end > start) {
                        String imgUrl = style.substring(start + 4, end).replace("'", "").replace("\"", "").trim();
                        if (!imgUrl.isEmpty()) {
                            if (imgUrl.startsWith("//")) {
                                imgUrl = "https:" + imgUrl;
                            } else if (imgUrl.startsWith("/")) {
                                imgUrl = Url.SERVER_AKNIGA + imgUrl;
                            }
                            bookPOJO.setPhoto(imgUrl);
                            break;
                        }
                    }
                }
            }
            for (Element element : book.getElementsByClass("link__action--author")) {
                Element a = element.selectFirst("a");
                if (a == null) {
                    continue;
                }
                String src = a.attr("href");
                String name = a.text();
                Element use = element.selectFirst("use");
                String typeStr = use == null ? "" : use.attr("xlink:href");
                if (typeStr.isEmpty()) {
                    typeStr = src;
                }
                if (typeStr.contains("author")) {
                    if (!src.isEmpty()) bookPOJO.setUrlAutor(src + "page<page>/");
                    if (!name.isEmpty()) bookPOJO.setAutor(name);
                } else if (typeStr.contains("performer") || typeStr.contains("reader")) {
                    if (!src.isEmpty()) bookPOJO.setUrlArtist(src + "page<page>/");
                    if (!name.isEmpty()) bookPOJO.setArtist(name);
                } else if (typeStr.contains("series") || typeStr.contains("cycle")) {
                    if (!src.isEmpty()) bookPOJO.setUrlSeries(src);
                    if (!name.isEmpty()) bookPOJO.setSeries(name);
                }
            }
            Element title = book.selectFirst(".caption__article-main");
            if (title != null && !title.text().isEmpty()) {
                // the card title is "Author – Title" (an en dash on the live site, a hyphen on older pages)
                bookPOJO.setName(title.text().replace(bookPOJO.getAutor() + " – ", "")
                        .replace(bookPOJO.getAutor() + " - ", ""));
            }
            Element link = book.selectFirst(".content__article-main-link");
            if (link != null && !link.attr("href").isEmpty()) {
                bookPOJO.setUrl(link.attr("href"));
            }
            Element desc = book.selectFirst(".description__article-main");
            if (desc != null && !desc.text().isEmpty()) {
                bookPOJO.setDesc(desc.text());
            }
            Element time = book.selectFirst(".link__action--label--time");
            if (time != null && !time.text().isEmpty()) {
                bookPOJO.setTime(time.text());
            }
            Element coments = book.selectFirst(".link__action--comment");
            if (coments != null && !coments.text().contains("нет комментариев")) {
                String count = coments.text().trim().split("\\s+")[0];
                if (!count.isEmpty()) {
                    bookPOJO.setComents(count);
                }
            }
            Elements reting = book.getElementsByClass("link__action--label--views");
            if (!reting.isEmpty() && !reting.text().trim().isEmpty()) {
                bookPOJO.setReting(reting.text().trim());
            }
            return bookPOJO;
        });
        return ProviderPage.of(result, hasMore, raw.size());
    }

    /** Rows of the authors/performers/sections table (also the shape of the ajax search answer). */
    public static ArrayList<GenrePOJO> parseTable(Document doc) {
        return rows(doc.select(".table-authors tbody > tr, table > tbody > tr"), item -> {
            GenrePOJO genrePOJO = new GenrePOJO();
            Element aGenre = item.selectFirst(".name-obj a");
            if (aGenre != null) {
                genrePOJO.setUrl(aGenre.attr("href") + "page<page>/");
                genrePOJO.setName(aGenre.text());
            }
            Element description = item.selectFirst(".description");
            if (description != null) {
                genrePOJO.setDescription(description.text().trim());
            }
            Element reting = item.selectFirst(".cell-rating");
            if (reting != null) {
                String text = reting.text().trim().split("\n")[0].trim();
                text = text.replace("K", "000").replace(".", "").replaceAll("[^0-9]", "");
                if (!text.isEmpty()) {
                    genrePOJO.setReting(Integer.parseInt(text));
                }
            }
            return genrePOJO;
        });
    }

    /** The "other books" block of the page. */
    public static ArrayList<BookPOJO> parseRecommendations(Document document) {
        ArrayList<BookPOJO> books = new ArrayList<>();

        Elements booksConteiner = new Elements();
        for (Element element : document.getElementsByClass("block-container")) {
            booksConteiner.addAll(element.children());
        }
        for (Element element : booksConteiner) {
            BookPOJO bookPOJO = new BookPOJO();
            Elements aTag = element.getElementsByTag("a");
            if (aTag.size() > 0) {
                Element a = aTag.first();
                bookPOJO.setUrl(a.absUrl("href"));
                Elements img = a.getElementsByTag("img");
                if (img.size() > 0) {
                    String imgUrl = img.first().absUrl("data-src");
                    if (imgUrl.isEmpty()) {
                        imgUrl = img.first().absUrl("src");
                    }
                    bookPOJO.setPhoto(imgUrl);
                }

                Elements name = a.getElementsByClass("caption");
                if (name.size() > 0) {
                    bookPOJO.setName(name.first().text());
                }
            }
            if (!bookPOJO.isNull()) {
                books.add(bookPOJO);
            }
        }
        return books;
    }

    public static DescriptionPOJO parseDescription(Document document, String bookUrl) {
        DescriptionPOJO descriptionPOJO = new DescriptionPOJO();
        String autor = "";

        Elements titleElement = document.getElementsByClass("caption__article-main");

        Elements autorElements = document.getElementsByClass("about-author");
        if (!autorElements.isEmpty()) {
            Elements aElements = autorElements.first().getElementsByTag("a");
            if (!aElements.isEmpty()) {
                autor = aElements.first().text();
                descriptionPOJO.setAutor(autor);
                descriptionPOJO.setAutorUrl(absUrl(aElements.first(), "href", "page<page>/"));
            }
        }

        if (titleElement.size() != 0) {
            descriptionPOJO.setTitle(titleElement.first().text().trim().replace(autor + " - ", ""));
        }

        Elements posterElements = document.getElementsByClass("cover__wrapper--image");
        if (posterElements.size() != 0) {
            Elements img = posterElements.first().getElementsByTag("img");
            if (img.size() != 0) {
                descriptionPOJO.setPoster(img.first().absUrl("src"));
            }
        }
        if (descriptionPOJO.getPoster().isEmpty()) {
            Elements bgElems = document.getElementsByClass("collection-header-bg");
            if (bgElems.isEmpty()) {
                bgElems = document.select("[style*='background-image']");
            }
            for (Element bgElem : bgElems) {
                String style = bgElem.attr("style");
                if (style.contains("url(")) {
                    int start = style.indexOf("url(");
                    int end = style.indexOf(")", start);
                    if (start >= 0 && end > start) {
                        String imgUrl = style.substring(start + 4, end).replace("'", "").replace("\"", "").trim();
                        if (!imgUrl.isEmpty()) {
                            if (imgUrl.startsWith("//")) imgUrl = "https:" + imgUrl;
                            else if (imgUrl.startsWith("/")) imgUrl = Url.SERVER_AKNIGA + imgUrl;
                            descriptionPOJO.setPoster(imgUrl);
                            break;
                        }
                    }
                }
            }
        }
        if (descriptionPOJO.getPoster().isEmpty()) {
            Element metaImg = document.select("meta[property=og:image], meta[name=twitter:image]").first();
            if (metaImg != null) {
                descriptionPOJO.setPoster(metaImg.absUrl("content"));
            }
        }

        String time = "";
        Elements timeElements = document.getElementsByClass("hours");
        if (timeElements.size() != 0) {
            time = timeElements.first().text();
        }

        timeElements = document.getElementsByClass("minutes");
        if (timeElements.size() != 0) {
            time += " " + timeElements.first().text();
        }
        if (!time.isEmpty()) {
            descriptionPOJO.setTime(time.trim());
        }

        Elements artistElements = document.getElementsByClass("link__reader");
        if (artistElements.size() != 0) {
            descriptionPOJO.setArtistUrl(absUrl(artistElements.first(), "href", "page<page>/"));
            descriptionPOJO.setArtist(artistElements.first().text());
        }

        Element seriesElement = document.select("a[href*=/series/]").first();
        if (seriesElement != null) {
            descriptionPOJO.setSeriesUrl(seriesElement.absUrl("href"));
            descriptionPOJO.setSeries(seriesElement.text());
        }

        Elements descriptionElements = document.getElementsByClass("description__article-main");
        if (descriptionElements.size() != 0) {
            StringBuilder descBuilder = new StringBuilder();
            for (Element element : descriptionElements) {
                String t = element.text().trim();
                if (!t.isEmpty()) {
                    descBuilder.append(t).append("\n");
                }
            }
            descriptionPOJO.setDescription(descBuilder.toString().trim());
        } else {
            Elements scripts = document.getElementsByTag("script");
            for (Element script : scripts) {
                if ("application/ld+json".equals(script.attr("type"))) {
                    String json = script.html();
                    if (json.contains("\"description\":\"")) {
                        String desc = json.substring(json.indexOf("\"description\":\"") + 15);
                        int end = desc.indexOf("\"");
                        if (end > 0) {
                            descriptionPOJO.setDescription(desc.substring(0, end));
                            break;
                        }
                    }
                }
            }
        }

        Elements genreConteiner = document.getElementsByClass("section__title");
        if (genreConteiner.size() != 0) {
            Element a = genreConteiner.first();
            descriptionPOJO.setGenreUrl(absUrl(a, "href", "page<page>/"));
            descriptionPOJO.setGenre(a.text());
        }

        Elements retingElements = document
                .getElementsByClass("link__action--label link__action--label--views pull-right");
        if (retingElements.size() != 0) {
            descriptionPOJO.setReiting(retingElements.first().text().trim());
        }

        Elements parents = document.getElementsByClass("cover__wrapper--buttons");
        if (parents.size() != 0) {
            Element parent = parents.first();

            Elements favorite = parent.getElementsByClass("ls-favourite-count");
            if (favorite.size() != 0) {
                descriptionPOJO.setFavorite(favorite.first().text().trim());
            }

            Elements like = parent.getElementsByClass("js-vote-rating-up");
            if (like.size() != 0) {
                descriptionPOJO.setLike(like.first().text().trim());
            }

            Elements disLike = parent.getElementsByClass("js-vote-rating-down");
            if (disLike.size() != 0) {
                descriptionPOJO.setDisLike(disLike.first().text().trim());
            }

        }

        return descriptionPOJO;
    }

    /** A book opened by its link (deep links and the favourites import). */
    public static BookPOJO parseBook(Document document, String url) {
        String autor = "";

        BookPOJO bookPOJO = new BookPOJO();

        bookPOJO.setUrl(url);

        Elements titleElement = document.getElementsByClass("caption__article-main");

        Elements autorElements = document.getElementsByClass("about-author");
        if (autorElements != null && !autorElements.isEmpty()) {
            Elements aElements = autorElements.first().getElementsByTag("a");
            if (aElements != null && !aElements.isEmpty()) {
                autor = aElements.first().text();
                if (autor != null && !autor.isEmpty()) {
                    bookPOJO.setAutor(autor);
                }
                String href = aElements.first().absUrl("href");
                if (href != null && !href.isEmpty()) {
                    bookPOJO.setUrlAutor(href + "page<page>/");
                }
            }
        }

        if (titleElement != null && titleElement.size() != 0) {
            bookPOJO.setName(titleElement.first().text().trim().replace(autor + " - ", ""));
        }

        Elements posterElements = document.getElementsByClass("cover__wrapper--image");
        if (posterElements != null && !posterElements.isEmpty()) {
            Elements img = posterElements.first().getElementsByTag("img");
            if (img != null && img.size() != 0) {
                String imgUrl = img.first().absUrl("src");
                if (imgUrl != null && !imgUrl.isEmpty()) {
                    bookPOJO.setPhoto(imgUrl);
                }
            }
        }
        if (bookPOJO.getPhoto() == null || bookPOJO.getPhoto().isEmpty()) {
            Elements bgElems = document.getElementsByClass("collection-header-bg");
            if (bgElems == null || bgElems.isEmpty()) {
                bgElems = document.select("[style*='background-image']");
            }
            if (bgElems != null && !bgElems.isEmpty()) {
                for (Element bgElem : bgElems) {
                    String style = bgElem.attr("style");
                    if (style != null && style.contains("url(")) {
                        int start = style.indexOf("url(");
                        int end = style.indexOf(")", start);
                        if (start >= 0 && end > start) {
                            String imgUrl = style.substring(start + 4, end).replace("'", "").replace("\"", "").trim();
                            if (!imgUrl.isEmpty()) {
                                if (imgUrl.startsWith("//")) imgUrl = "https:" + imgUrl;
                                else if (imgUrl.startsWith("/")) imgUrl = Url.SERVER_AKNIGA + imgUrl;
                                bookPOJO.setPhoto(imgUrl);
                                break;
                            }
                        }
                    }
                }
            }
        }
        if (bookPOJO.getPhoto() == null || bookPOJO.getPhoto().isEmpty()) {
            Element metaImg = document.select("meta[property=og:image], meta[name=twitter:image]").first();
            if (metaImg != null) {
                bookPOJO.setPhoto(metaImg.absUrl("content"));
            }
        }

        String time = "";
        Elements timeElements = document.getElementsByClass("hours");
        if (timeElements != null && timeElements.size() != 0) {
            time = timeElements.first().text();
        }

        timeElements = document.getElementsByClass("minutes");
        if (timeElements != null && timeElements.size() != 0) {
            time += " " + timeElements.first().text();
        }
        if (!time.isEmpty()) {
            bookPOJO.setTime(time.trim());
        }

        Elements artistElements = document.getElementsByClass("link__reader");
        if (artistElements != null && artistElements.size() != 0) {
            String href = artistElements.first().absUrl("href");
            if (href != null && !href.isEmpty()) {
                bookPOJO.setUrlArtist(href + "page<page>/");
            }
            String name = artistElements.first().text();
            if (name != null && !name.isEmpty()) {
                bookPOJO.setArtist(name);
            }
        }

        Elements seriesElements = document.select("a[href*=/series/]");
        if (seriesElements != null && !seriesElements.isEmpty()) {
            Element aTag = seriesElements.first();
            String href = aTag.absUrl("href");
            if (href != null && !href.isEmpty()) {
                bookPOJO.setUrlSeries(href);
            }
            String title = aTag.text();
            if (title != null && !title.isEmpty()) {
                bookPOJO.setSeries(title);
            }
        }

        Elements descriptionElements = document.getElementsByClass("description__article-main");
        if (descriptionElements.size() != 0) {
            bookPOJO.setDesc(descriptionElements.first().text().trim());
        } else {
            descriptionElements = document.getElementsByAttributeValue("itemprop", "description");
            if (descriptionElements.size() != 0) {
                bookPOJO.setDesc(descriptionElements.first().ownText().trim());
            }
        }

        Elements genreConteiner = document.getElementsByClass("section__title");
        if (genreConteiner != null && genreConteiner.size() != 0) {
            Element a = genreConteiner.first();
            String href = a.absUrl("href");
            if (href != null && !href.isEmpty()) {
                bookPOJO.setUrlGenre(href + "page<page>/");
            }
            String text = a.text();
            if (text != null && !text.isEmpty()) {
                bookPOJO.setGenre(text);
            }
        }

        Elements retingElements = document
                .getElementsByClass("link__action--label link__action--label--views pull-right");
        if (retingElements != null && retingElements.size() != 0) {
            bookPOJO.setReting(retingElements.first().text().trim());
        }

        return bookPOJO;
    }

    // ---------------------------------------------------------------- chapters

    public static String securityKey(Document doc) {
        for (Element script : doc.select("script")) {
            Matcher m = SECURITY_KEY.matcher(script.data());
            if (m.find()) {
                return m.group(1);
            }
        }
        return "";
    }

    /** The current book's id, from its own page or player container (related books carry theirs too). */
    public static String bid(Document doc) {
        Element el = doc.selectFirst("article.book--page[data-bid~=^\\d+$], .player--chapters[data-bid~=^\\d+$]");
        return el == null ? "" : el.attr("data-bid");
    }

    /**
     * {@code bookJson} is the {@code /ajax/b/<bid>} answer (null when the page had no key): one HLS
     * URL shared by all chapters, each clipped by its start/finish time.
     */
    public static ArrayList<AudioPOJO> parseTracks(Document doc, String bookJson, String bookUrl) {
        String bookName = Html.text(doc.selectFirst(".caption__article-main"));
        Chapters chapters = new Chapters(bookName, bookUrl);
        if (bookJson != null) {
            JsonObject book = object(bookJson);
            JsonElement hres = book.get("hres");
            String hls = hres == null ? "" : decryptHres(hres.isJsonPrimitive() ? hres.getAsString() : hres.toString());
            if (!hls.isEmpty()) {
                JsonElement items = book.get("items");
                if (items != null && items.isJsonPrimitive()) {
                    try {
                        items = JsonParser.parseString(items.getAsString());
                    } catch (JsonParseException e) {
                        items = null;
                    }
                }
                if (items != null && items.isJsonArray()) {
                    addClips(chapters, items.getAsJsonArray(), hls);
                }
                if (chapters.isEmpty()) {
                    AudioPOJO whole = chapters.chapter("Аудиокнига (полная)", hls, 0);
                    if (whole != null) {
                        whole.setTimeStart(0);
                    }
                    chapters.add(whole);
                }
            }
            JsonElement legacy = book.get("aItems");
            if (chapters.isEmpty() && legacy != null && legacy.isJsonArray()) {
                int[] accumulated = {0};
                chapters.each(legacy.getAsJsonArray(), item -> {
                    JsonObject o = item.getAsJsonObject();
                    String file = o.has("file") ? string(o, "file") : string(o, "file_url");
                    AudioPOJO audio = chapters.chapter(string(o, "title"), file, 0);
                    if (audio != null && o.has("duration")) {
                        int duration = seconds(o.get("duration"));
                        audio.setTime(duration);
                        audio.setTimeStart(accumulated[0]);
                        accumulated[0] += duration;
                        audio.setTimeFinish(accumulated[0]);
                    }
                    return audio;
                });
            }
        }
        if (chapters.isEmpty()) {
            for (Element audio : doc.select("audio[src]")) {
                if (chapters.add(chapters.chapter(bookName, audio.absUrl("src"), 0))) {
                    break;
                }
            }
        }
        return chapters.done("akniga book");
    }

    /**
     * Chapters clipped out of the one shared HLS stream, as complete monotonic intervals: a start
     * that goes backwards is clamped to the previous end; an item without its own end (no
     * {@code time_finish}/{@code duration}) ends where the next item explicitly starts, else it is
     * skipped. Only the last chapter may stay open-ended (plays to the end of the stream). Golosknig's whole-book MP3
     * comes with the same items.
     */
    static void addClips(Chapters chapters, JsonArray items, String hls) {
        List<AudioPOJO> clips = new ArrayList<>();
        List<JsonObject> times = new ArrayList<>();
        chapters.each(items, item -> {
            JsonObject o = item.getAsJsonObject();
            AudioPOJO audio = chapters.chapter(string(o, "title"), hls, 0);
            if (audio != null) {
                clips.add(audio);
                times.add(o);
            }
            return null;
        });
        int end = 0;
        for (int i = 0; i < clips.size(); i++) {
            JsonObject o = times.get(i);
            int start = Math.max(end, seconds(o.get("time_from_start")));
            int finish = seconds(o.get("time_finish"));
            if (finish <= start && o.has("duration")) {
                finish = start + seconds(o.get("duration"));
            }
            if (finish <= start && i + 1 < times.size()) {
                finish = seconds(times.get(i + 1).get("time_from_start"));
            }
            AudioPOJO clip = clips.get(i);
            clip.setTimeStart(start);
            if (finish > start) {
                clip.setTime(finish - start);
                clip.setTimeFinish(finish);
                end = finish;
                chapters.add(clip);
            } else if (i == clips.size() - 1) {
                chapters.add(clip);
            } else {
                chapters.skipped++;
            }
        }
    }

    /**
     * Decrypts akniga's {@code hres} (the HLS playlist URL), a {@link CryptoJs} JSON. Returns "" when it cannot be
     * decrypted.
     */
    public static String decryptHres(String hresJson) {
        try {
            String result = CryptoJs.decrypt(hresJson, PASSPHRASE).trim();
            // a JSON string like "https:\/\/r1.akniga.club\/b\/111286\/pl.m3u8?…", a list of them or {url}
            try {
                JsonElement value = JsonParser.parseString(result);
                if (value.isJsonArray() && value.getAsJsonArray().size() > 0) {
                    value = value.getAsJsonArray().get(0);
                } else if (value.isJsonObject()) {
                    value = value.getAsJsonObject().get("url");
                }
                result = string(value);
            } catch (JsonParseException notJson) {
                result = result.replace("\\/", "/");
            }
            return resolve(Url.SERVER_AKNIGA + "/", result);
        } catch (Exception e) {
            return "";
        }
    }

    // ---------------------------------------------------------------- series

    /** The series block of a book page. */
    public static ArrayList<SeriesPOJO> parseSeries(Document doc) {
        Element list = doc.selectFirst(".content__main__book--item--series-list");
        if (list == null) {
            return new ArrayList<>();
        }
        return each(list.children(), item -> {
            String link = item.absUrl("href");
            if (link.isEmpty()) {
                return null; // "Показать весь список" toggle
            }
            SeriesPOJO seriesPOJO = new SeriesPOJO();
            seriesPOJO.setUrl(link);
            seriesPOJO.setNumber(Html.text(item, "b"));
            String caption = Html.text(item, ".caption");
            seriesPOJO.setName(caption.isEmpty() ? item.text().trim() : caption);
            seriesPOJO.setPhoto(imgSrc(item.selectFirst("img")));
            return seriesPOJO;
        });
    }

    /** A series listing page (cards, like search results). */
    public static ArrayList<SeriesPOJO> parseSeriesPage(Document doc) {
        return each(doc.select(".content__main__articles--item"), card -> {
            Element a = card.selectFirst(".caption__article-main a, h2 a, h3 a");
            if (a == null) {
                a = card.selectFirst("a[href]");
            }
            String link = a == null ? "" : a.absUrl("href");
            if (link.isEmpty() || link.contains("/author/") || link.contains("/performer/")
                    || link.contains("/section/")) {
                return null;
            }
            SeriesPOJO seriesPOJO = new SeriesPOJO();
            seriesPOJO.setUrl(link);
            String caption = Html.text(card, ".caption__article-main");
            seriesPOJO.setName(caption.isEmpty() ? a.text().trim() : caption);
            seriesPOJO.setPhoto(imgSrc(card.selectFirst("img")));
            return seriesPOJO;
        });
    }

    // ---------------------------------------------------------------- comments

    public static ComentsModel.Parsed parseComments(Document doc, String bookUrl) {
        Element list = doc.getElementById("comments");
        if (list == null) {
            return new ComentsModel.Parsed();
        }
        return ComentsModel.nest(list.select(".js-comment-wrapper"), ".js-comment-wrapper", (wrapper, pojo) -> {
            Element section = wrapper.selectFirst("> section");
            Element name = section == null ? null : section.selectFirst(".comments__block--item--name");
            pojo.setName(name == null ? "" : name.ownText());
            String image = commentAbsUrl(section, "img.comments__block--item--avatar", "src");
            pojo.setImage(image.isEmpty()
                    ? commentAbsUrl(section, "img.comments__block--item--avatar", "data-src") : image);
            pojo.setDate(commentText(section, "time"));
            pojo.setText(commentText(section, ".comments__block--item--comment"));
            String rating = wrapper.attr("data-rating");
            pojo.setReting(rating.equals("0") ? "" : rating);
        });
    }

    /** The comments feed when the page holds only part of the comments; null when it holds them all. */
    @Nullable
    public static String feedUrl(Document doc) {
        Element more = doc.selectFirst(".js-comments-load-more");
        Element list = doc.getElementById("comments");
        if (more == null || list == null) {
            return null;
        }
        String target = list.attr("data-target-id");
        if (toInt(more.attr("data-total")) <= toInt(more.attr("data-loaded")) || !target.matches("\\d+")) {
            return null;
        }
        return Url.SERVER_AKNIGA + "/rss/comments/" + target + "/";
    }

    /** Feed items that are not on {@code page}, as top-level comments (the feed carries no reply structure). */
    public static ComentsModel.Parsed parseCommentsFeed(Document feed, Document page) {
        ComentsModel.Parsed parsed = new ComentsModel.Parsed();
        for (Element item : feed.select("item")) {
            Matcher id = FEED_ID.matcher(commentText(item, "guid"));
            if (id.find() && page.getElementById("comment" + id.group(1)) != null) {
                continue;
            }
            ComentsPOJO pojo = new ComentsPOJO();
            String creator = commentText(item, "dc|creator");
            pojo.setName(creator.isEmpty() ? commentText(item, "author") : creator);
            // the XML parser keeps the CDATA description, which is HTML, as text
            pojo.setText(Jsoup.parseBodyFragment(commentText(item, "description")).text());
            pojo.setDate(commentText(item, "pubDate").replaceAll(" [+-]\\d{4}$", ""));
            if (pojo.isEmty()) {
                parsed.skipped++;
            } else {
                parsed.comments.add(pojo);
            }
        }
        return parsed;
    }

    private static String commentText(@Nullable Element scope, String query) {
        Element element = scope == null ? null : scope.selectFirst(query);
        return element == null ? "" : element.text();
    }

    private static String commentAbsUrl(@Nullable Element scope, String query, String attribute) {
        Element element = scope == null ? null : scope.selectFirst(query);
        return element == null ? "" : element.absUrl(attribute);
    }

    private static int toInt(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    // ---------------------------------------------------------------- other sources

    /** The book search as hits: paid books and fragments left out, the title without its author. */
    public static ArrayList<OtherSourceModel.Hit> parseSearch(Document doc) {
        return each(doc.select(".content__main__articles--item"), book -> {
            if (book.selectFirst("a[href$=/paid/]") != null
                    || "Фрагмент".equals(Html.text(book, ".caption__article-preview"))) {
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
            String title = OtherSourceModel.withoutAuthor(Html.text(book, ".caption__article-main"), author);
            return new OtherSourceModel.Hit(href(book, "a.content__article-main-link"), title, author, reader);
        });
    }
}
