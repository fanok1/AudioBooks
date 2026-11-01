package com.fanok.audiobooks.source;

import static com.fanok.audiobooks.source.Html.cards;
import static com.fanok.audiobooks.source.Html.each;
import static com.fanok.audiobooks.source.Html.lastPage;
import static com.fanok.audiobooks.source.Html.rows;
import static com.fanok.audiobooks.source.Html.seriesItem;
import static com.fanok.audiobooks.source.Html.text;

import android.util.Log;

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

import io.reactivex.functions.BooleanSupplier;

import org.jsoup.Connection;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import okhttp3.HttpUrl;

/**
 * audioboo.org, a DataLife Engine site behind a Cloudflare that challenges every GET: {@link SiteHttp} POSTs all its
 * pages (host-based, {@code executePosted}). Listings (the index {@link Url#INDEX_AUDIOBOO}, a genre, an author
 * {@code /xfsearch/avtora/}, a reader {@code /tags/}, a series {@code /xfsearch/cikl/}) page with {@code page/N/} and
 * are sorted by the site's {@code xsort} form, POSTed with every listing page; a sort travels in the listing URL
 * ({@link #sortUrl}) and {@link #loadListing} moves it into that form, so the site gets the same request. Search is the DLE
 * search form POSTed ({@link #fetch}); the other-source and other-readers searches are plain links. Genres are the
 * index's navigation menu, authors a letter index, readers the tag cloud. Book pages carry an inline Playerjs list,
 * DLE comments, a cycle link and a related block.
 */
public final class Audioboo implements SourceParser {

    public static final String SERVER = Url.SERVER_AUDIOBOO;

    /** The rating and popularity sorts take 8-10 s on the server. */
    static final int TIMEOUT_MS = 30_000;

    /** Results per search page. */
    static final int SEARCH_PAGE_SIZE = 50;

    private static final String TAG = "ComentsModel";

    private static final List<Sort.Entry> MENU = Sort.menu(Sort.NEW, Sort.TITLE, Sort.RATING, Sort.POPULAR,
            Sort.COMMENTS, Sort.DURATION);

    /** The sort form's value of an unsorted listing. */
    static final String DEFAULT_SORT = "date";

    /** The site's session cookie, which also holds the last listing sort. */
    static final String SESSION_COOKIE = "PHPSESSID";

    /** The query parameter a sorted listing's URL carries its sort form value in; never sent ({@link #loadListing}). */
    static final String SORT_PARAM = "xs_value";

    // ---------------------------------------------------------------- SourceParser

    /** The book search goes out as the DLE search form (its query POSTed); every other page as {@link SiteHttp#get}. */
    @Override
    public Document fetch(String url) throws IOException {
        return isSearchForm(url) ? SiteHttp.postForm(url) : SiteHttp.get(url);
    }

    @Override
    public int timeoutMs() {
        return TIMEOUT_MS;
    }

    /** Its Cloudflare answers every GET with a challenge while a POST passes. */
    @Override
    public boolean postsPages() {
        return true;
    }

    @Override
    public ProviderPage<BookPOJO> listing(Document page, String url, int pageNo) {
        return parseListing(page, pageNo);
    }

    /** Every listing page is POSTed with the site's sort form: the URL's {@link #SORT_PARAM}, else the date sort. */
    @Override
    public ProviderPage<BookPOJO> loadListing(String url, int pageNo) throws IOException {
        return listing(fetchListing(withoutSort(url), sortOf(url)), url, pageNo);
    }

    /** The index, genres and the author, reader and series links of cards share one scheme. */
    @Override
    public String pageUrl(String url, int page) {
        return Url.audiobooListingPage(url, page);
    }

    /** Reader-, author- and series-named listings are not re-sorted (the site's own author/reader links are). */
    @Override
    public List<Sort.Entry> sorts(String url) {
        boolean people = url.contains("reader") || url.contains("author") || url.contains("serie")
                || url.contains("cikl");
        return people ? Collections.<Sort.Entry>emptyList() : MENU;
    }

    /** The same listing with the sort's form value in {@link #SORT_PARAM}; the date sort is the plain listing. */
    @Override
    public String sortUrl(Sort sort, String listingUrl) {
        String value = sortValue(sort);
        if (value == null) {
            return null;
        }
        String plain = withoutSort(listingUrl);
        return DEFAULT_SORT.equals(value) ? plain : ListingFilters.withParam(plain, SORT_PARAM, value);
    }

    @Override
    public ProviderPage<GenrePOJO> genres(Document page, String url, int pageNo) {
        return parseGenres(page);
    }

    /** One list for every page: the navigation menu of the index. */
    @Override
    public ProviderPage<GenrePOJO> loadGenres(String url, int pageNo) throws IOException {
        return genres(fetch(SERVER + "/"), url, pageNo);
    }

    /**
     * Authors: an index page per letter ("Все авторы на букву А"), linked from every listing page, so page N is the
     * N-th letter plus the empty letters passed so far ({@code cursor}). Readers are the site's tags (a card's reader
     * links to one), all on the tag cloud.
     */
    @Override
    public ProviderPage<GenrePOJO> loadPeople(String url, int pageNo, boolean performers, PeopleCursor cursor)
            throws IOException {
        if (performers) {
            // 17k tags: ~4 MB unpacked (0.3 MB sent), the bound SiteHttp sets for a page
            int limit = 4 * SiteHttp.MAX_BODY_BYTES;
            return parseReaders(SiteHttp.parse(SiteHttp.execute(SiteHttp.connect(
                    SERVER + "/tags/").maxBodySize(limit)), limit));
        }
        Elements letters = fetch(url).select("a.alfavit[href]");
        if (pageNo <= 1) {
            cursor.skipped = 0;
        }
        int index = pageNo - 1 + cursor.skipped;
        ArrayList<GenrePOJO> authors = new ArrayList<>();
        // an empty letter would end the list: the next one is read in its place
        for (; index < letters.size(); index++) {
            authors = parseAuthors(fetch(letters.get(index).absUrl("href")));
            if (!authors.isEmpty() || index == letters.size() - 1) {
                break;
            }
            cursor.skipped++;
        }
        return ProviderPage.of(authors, index < letters.size() - 1);
    }

    @Override
    public String searchUrl(SearchUrls.Category category, String query, int page) {
        return category == SearchUrls.Category.BOOKS ? Dle.searchUrl(SERVER, query, page) : null;
    }

    @Override
    public ProviderPage<BookPOJO> searchResults(Document page, int pageNo) {
        return parseSearchResults(page, pageNo);
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

    @Override
    public ArrayList<AudioPOJO> tracks(Document page, String base) {
        return parseTracks(page, base);
    }

    /** The cycle linked from the book page, read from its own listing page. */
    @Override
    public ArrayList<SeriesPOJO> series(Document page, String bookUrl) throws IOException {
        String series = parseSeriesUrl(page);
        return series.isEmpty() ? new ArrayList<>() : parseSeriesPage(fetch(series));
    }

    @Override
    public ArrayList<ComentsPOJO> comments(String bookUrl, BooleanSupplier cancelled) throws Exception {
        ComentsModel.Parsed parsed = parseComments(fetch(bookUrl));
        if (parsed.skipped > 0) {
            Log.w(TAG, "skipped " + parsed.skipped + " malformed comments on " + bookUrl);
        }
        return parsed.comments;
    }

    @Override
    public boolean findsOtherReaders() {
        return true;
    }

    /** The title alone: the site's search copes with no more. */
    @Override
    public ArrayList<OtherArtistPOJO> otherReaders(String bookUrl, String title, String author, String reader)
            throws IOException {
        if (title == null || title.trim().isEmpty()) {
            return new ArrayList<>();
        }
        String terms = OtherSourceModel.query(title);
        return OtherArtistModel.otherReaders(parseSearch(fetch(otherSourceSearchUrl(terms, 1))), title, author,
                bookUrl, reader);
    }

    @Override
    public String otherSourceTerms(BookPOJO book) {
        return OtherSourceModel.query(book.getName());
    }

    /** The DLE search as a plain link (GET), {@code search_start} only after page 1. */
    @Override
    public String otherSourceSearchUrl(String terms, int page) {
        HttpUrl.Builder url = HttpUrl.get(SERVER).newBuilder();
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

    // ---------------------------------------------------------------- requests and URLs

    /** True for the book search ({@link #searchUrl}): the form's {@code full_search} field marks it. */
    static boolean isSearchForm(String url) {
        return url.startsWith(SERVER + "/index.php?do=search&") && url.contains("&full_search=");
    }

    /**
     * The listing page {@code url} POSTed with the site's sort form for {@code sort}, without the session: the site keeps
     * the sort in its session and reverses the direction when the same sort comes again, so in one session every
     * other page came back in the opposite order (checked live, 2026-10-30).
     */
    static Document fetchListing(String url, String sort) throws IOException {
        Connection connection = SiteHttp.connect(url)
                .method(Connection.Method.POST)
                .data("xsort", "1")
                .data("xs_field", "defaultsort")
                .data("xs_value", sort);
        connection.request().removeCookie(SESSION_COOKIE);
        return SiteHttp.parse(SiteHttp.execute(connection));
    }

    /** The sort form's value of a menu sort; null for a sort the site has not. */
    static String sortValue(Sort sort) {
        switch (sort) {
            case NEW: return DEFAULT_SORT;
            case RATING: return "rating";
            case POPULAR: return "views";
            case COMMENTS: return "comm";
            case TITLE: return "title";
            case DURATION: return "time";
            default: return null;
        }
    }

    /** The sort form value the listing {@code url} carries ({@link #sortUrl}); {@link #DEFAULT_SORT} when none. */
    static String sortOf(String url) {
        String value = ListingFilters.param(url, SORT_PARAM);
        return value == null ? DEFAULT_SORT : value;
    }

    /** {@code url} without its {@link #SORT_PARAM}: the page the site is asked for. */
    static String withoutSort(String url) {
        return url.replaceAll("([?&])" + SORT_PARAM + "=[^&#]*&?", "$1").replaceFirst("[?&](#|$)", "$1");
    }

    // ---------------------------------------------------------------- pure parsers

    /** The search pager links only back to page 1: a full page means there may be another one. */
    public static ProviderPage<BookPOJO> parseSearchResults(Document doc, int page) {
        int cards = doc.select(".card.d-flex").size();
        return ProviderPage.of(parseListing(doc, page).items, cards >= SEARCH_PAGE_SIZE, cards);
    }

    public static ProviderPage<BookPOJO> parseListing(Document doc, int page) {
        ArrayList<BookPOJO> result = cards(doc.select(".card.d-flex"), book -> {
            BookPOJO bookPOJO = new BookPOJO();
            Element imgWrapper = book.selectFirst(".card__img");
            if (imgWrapper != null) {
                bookPOJO.setUrl(imgWrapper.attr("href"));
                Element img = imgWrapper.selectFirst("img");
                if (img != null) {
                    String imgUrl = img.attr("src");
                    bookPOJO.setPhoto(imgUrl.startsWith("/") ? Url.SERVER_AUDIOBOO + imgUrl : imgUrl);
                    if (!img.attr("alt").isEmpty()) {
                        bookPOJO.setName(img.attr("alt"));
                    }
                }
            }
            Element title = book.selectFirst(".card__title");
            if (title != null && !title.text().isEmpty()) {
                bookPOJO.setName(title.text());
            }
            for (Element li : book.getElementsByTag("li")) {
                String text = li.text();
                Element a = li.selectFirst("a");
                if (a != null) {
                    String href = a.attr("href");
                    if (href.startsWith("/")) {
                        href = Url.SERVER_AUDIOBOO + href;
                    }
                    if (text.contains("Жанр:")) {
                        bookPOJO.setGenre(a.text());
                        bookPOJO.setUrlGenre(href);
                    } else if (text.contains("Автор:")) {
                        bookPOJO.setAutor(a.text());
                        bookPOJO.setUrlAutor(href);
                    } else if (text.contains("Читает:") || text.contains("Исполнитель:")) {
                        bookPOJO.setArtist(a.text());
                        bookPOJO.setUrlArtist(href);
                    } else if (text.contains("Цикл:")) {
                        bookPOJO.setSeries(a.text());
                        bookPOJO.setUrlSeries(href);
                    }
                }
                if (text.contains("Время:") && !text.contains("Время звучания:")) {
                    bookPOJO.setTime(text.replace("Время:", "").trim());
                }
            }
            if (bookPOJO.getSeries() == null) {
                Element aTag = book.selectFirst("a[href*=/xfsearch/cikl/]");
                if (aTag != null) {
                    bookPOJO.setSeries(aTag.text());
                    String href = aTag.attr("href");
                    if (!href.isEmpty()) {
                        bookPOJO.setUrlSeries(href.startsWith("/") ? Url.SERVER_AUDIOBOO + href : href);
                    }
                }
            }
            Element rub = book.selectFirst(".card__ratings .rubbery");
            if (rub != null) {
                bookPOJO.setTime(rub.text().replace("Время звучания:", "").trim());
            }
            Element desc = book.selectFirst(".card__text");
            if (desc != null) {
                bookPOJO.setDesc(desc.text());
            }
            Element currentRating = book.selectFirst(".card__metas .rating .unit-rating .current-rating");
            if (currentRating != null && currentRating.attr("style").contains("width:")) {
                String widthStr = currentRating.attr("style").replaceAll(".*width:\\s*", "").replaceAll("[%\\s;].*", "");
                try {
                    float stars = Float.parseFloat(widthStr) / 20.0f;
                    Element voteSpan = book.selectFirst(".card__metas span[id^=\"vote-num-id-\"]");
                    String votes = voteSpan != null ? voteSpan.text().trim() : "0";
                    if (stars > 0) {
                        bookPOJO.setReting(BookPOJO.formatRating(stars, votes));
                    }
                } catch (NumberFormatException ignored) {
                }
            }
            return bookPOJO;
        });
        int last = lastPage(doc.select(".navigation a, .pagination a"));
        boolean hasMore = last > 0 ? last > page : !result.isEmpty();
        return ProviderPage.of(result, hasMore);
    }

    /** The number in a count text ("[953]", "Найдено публикаций: 12"); 0 when there is none. */
    static int countOf(String text) {
        String digits = text.replaceAll("\\D", "");
        return digits.isEmpty() || digits.length() > 9 ? 0 : Integer.parseInt(digits);
    }

    /** The genres of the navigation menu, with the site's own URLs and the book count (also as the description). */
    public static ProviderPage<GenrePOJO> parseGenres(Document doc) {
        return ProviderPage.of(rows(doc.select("ul.navmenu li > a[href]"), item -> {
            String name = item.ownText().trim();
            String href = item.absUrl("href");
            if (name.isEmpty() || !Url.isHost(href, Url.SERVER_AUDIOBOO)) {
                return null;
            }
            GenrePOJO genrePOJO = new GenrePOJO();
            genrePOJO.setUrl(href);
            genrePOJO.setName(name);
            Element count = item.selectFirst("span");
            if (count != null) {
                genrePOJO.setDescription(count.text().replaceAll("[\\[\\]]", "").trim());
                genrePOJO.setReting(countOf(count.text()));
            }
            return genrePOJO;
        }), false);
    }

    /** A letter of the authors' index: a table row per author, with the number of their books. */
    public static ArrayList<GenrePOJO> parseAuthors(Document doc) {
        return rows(doc.select("#dle-content tr"), row -> {
            Element a = row.selectFirst("a[href*=/xfsearch/avtora/]");
            // the index links "/xfsearch/avtora/А Фонд": encoded and closed by "/" as the cards link it
            HttpUrl href = a == null ? null : HttpUrl.parse(a.absUrl("href"));
            if (href == null) {
                return null;
            }
            GenrePOJO author = new GenrePOJO();
            String link = href.toString();
            author.setUrl(link.endsWith("/") ? link : link + "/");
            author.setName(a.text().trim());
            Elements cells = row.select("td");
            author.setReting(countOf(cells.last().text()));
            return author;
        });
    }

    /** The tag cloud: every reader with the number of their books. */
    public static ProviderPage<GenrePOJO> parseReaders(Document doc) {
        return ProviderPage.of(rows(doc.select(".cloud-tags a[href*=/tags/]"), a -> {
            GenrePOJO reader = new GenrePOJO();
            reader.setUrl(a.absUrl("href"));
            reader.setName(a.text().trim());
            reader.setReting(countOf(a.attr("title"))); // "Найдено публикаций: 12"
            return reader;
        }), false);
    }

    public static DescriptionPOJO parseDescription(Document document) {

        DescriptionPOJO descriptionPOJO = new DescriptionPOJO();

        Element descContainer = document.getElementsByClass("page__text").first();
        if (descContainer != null) {
            Element img = descContainer.getElementsByTag("img").first();
            if (img != null) {
                descriptionPOJO.setPoster(img.absUrl("data-src"));
            }
            String descText = descContainer.text();
            descriptionPOJO.setDescription(descText);

            if (descText.contains("Продолжительность:")) {
                String timeStr = descText.substring(descText.indexOf("Продолжительность:") + "Продолжительность:".length()).trim();
                if (timeStr.contains(" ")) {
                    timeStr = timeStr.substring(0, timeStr.indexOf(" "));
                }
                descriptionPOJO.setTime(timeStr);
            }
        }

        Elements infos = document.getElementsByClass("duration list");
        for (Element info : infos) {
            String text = info.text();
            Element a = info.getElementsByTag("a").first();
            if (a != null) {
                if (text.contains("Автор")) {
                    descriptionPOJO.setAutor(a.text());
                    descriptionPOJO.setAutorUrl(a.absUrl("href"));
                } else if (text.contains("Исполнитель")) {
                    descriptionPOJO.setArtist(a.text());
                    descriptionPOJO.setArtistUrl(a.absUrl("href"));
                } else if (text.contains("Цикл")) {
                    descriptionPOJO.setSeries(a.text());
                    descriptionPOJO.setSeriesUrl(a.absUrl("href"));
                } else if (text.contains("Жанр")) {
                    descriptionPOJO.setGenre(a.text());
                    descriptionPOJO.setGenreUrl(a.absUrl("href"));
                }
            }
        }

        Element titleElement = document.getElementsByTag("h1").first();
        if (titleElement != null) {
            String name = titleElement.text().trim();
            String author = descriptionPOJO.getAutor();
            if (!author.isEmpty() && name.contains(author + " - ")) {
                name = name.replace(author + " - ", "");
            }
            descriptionPOJO.setTitle(name);
        }

        //stars are the width of the rating bar; .figstar is the vote count
        Element stars = document.selectFirst(".card__metas .current-rating");
        Element votes = document.selectFirst(".card__metas .figstar");
        if (stars != null && votes != null) {
            try {
                float value = Float.parseFloat(stars.attr("style").replaceAll(".*width:\\s*", "")
                        .replaceAll("[%\\s;].*", "")) / 20.0f;
                String count = votes.text().trim();
                if (value > 0 && !count.isEmpty() && !count.equals("0")) {
                    descriptionPOJO.setReiting(BookPOJO.formatRating(value, count));
                }
            } catch (NumberFormatException ignored) {
                // no rating rather than a wrong one
            }
        }

        return descriptionPOJO;
    }

    /** A book opened by its link. */
    public static BookPOJO parseBook(Document document, String url) {
        BookPOJO bookPOJO = new BookPOJO();
        bookPOJO.setUrl(url);

        Element descContainer = document.getElementsByClass("page__text").first();
        if (descContainer != null) {
            Element img = descContainer.getElementsByTag("img").first();
            if (img != null) {
                bookPOJO.setPhoto(img.absUrl("data-src"));
            }
            String descText = descContainer.text();
            bookPOJO.setDesc(descText);

            if (descText.contains("Продолжительность:")) {
                String timeStr = descText.substring(descText.indexOf("Продолжительность:") + "Продолжительность:".length()).trim();
                if (timeStr.contains(" ")) {
                    timeStr = timeStr.substring(0, timeStr.indexOf(" "));
                }
                bookPOJO.setTime(timeStr);
            }
        }

        Elements infos = document.getElementsByClass("duration list");
        if (infos != null) {
            for (Element info : infos) {
                String text = info.text();
                Element a = info.getElementsByTag("a").first();
                if (a != null) {
                    if (text.contains("Автор")) {
                        bookPOJO.setAutor(a.text());
                        bookPOJO.setUrlAutor(a.absUrl("href"));
                    } else if (text.contains("Исполнитель")) {
                        bookPOJO.setArtist(a.text());
                        bookPOJO.setUrlArtist(a.absUrl("href"));
                    } else if ((text.contains("Цикл") || text.contains("Серия")) && a != null) {
                        bookPOJO.setSeries(a.text());
                        String href = a.absUrl("href");
                        if (href != null && !href.isEmpty()) {
                            bookPOJO.setUrlSeries(href);
                        }
                    } else if (text.contains("Жанр")) {
                        bookPOJO.setGenre(a.text());
                        bookPOJO.setUrlGenre(a.absUrl("href"));
                    }
                }
            }
        }

        if (bookPOJO.getSeries() == null || bookPOJO.getSeries().isEmpty()) {
            Elements seriesElements = document.select("a[href*=/series/]");
            if (seriesElements != null && !seriesElements.isEmpty()) {
                Element a = seriesElements.first();
                bookPOJO.setSeries(a.text());
                String href = a.absUrl("href");
                if (href != null && !href.isEmpty()) {
                    bookPOJO.setUrlSeries(href);
                }
            }
        }

        Element titleElement = document.getElementsByTag("h1").first();
        if (titleElement != null) {
            String name = titleElement.text().trim();
            String author = bookPOJO.getAutor();
            if (author != null && !author.isEmpty() && name.contains(author + " - ")) {
                name = name.replace(author + " - ", "");
            }
            bookPOJO.setName(name);
        }

        Element ratingContainer = document.getElementsByClass("rating").first();
        if (ratingContainer != null) {
            Element currentRating = ratingContainer.selectFirst(".unit-rating .current-rating");
            if (currentRating != null) {
                String style = currentRating.attr("style");
                if (style != null && style.contains("width:")) {
                    String widthStr = style.replaceAll(".*width:\\s*", "").replaceAll("[%\\s;].*", "");
                    try {
                        float width = Float.parseFloat(widthStr);
                        float stars = width / 20.0f;
                        Element voteSpan = document.selectFirst("span[id^=\"vote-num-id-\"]");
                        String votes = voteSpan != null ? voteSpan.text().trim() : "0";
                        if (stars > 0) {
                            bookPOJO.setReting(BookPOJO.formatRating(stars, votes));
                        }
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }

        Element comentsConteiner = document.getElementsByClass("fa-comment-alt-dots").first();
        if (comentsConteiner != null) {
            Element span = comentsConteiner.parent();
            if (span != null) {
                bookPOJO.setComents(span.ownText().trim());
            }
        }
        return bookPOJO;
    }

    /** The related block: one cell per book, a cover link and a title link to the same page. */
    public static ArrayList<BookPOJO> parseRecommendations(Document document) {
        ArrayList<BookPOJO> books = new ArrayList<>();

        //the related block: one cell per book, a cover link and a title link to the same page
        Element root = document.getElementById("somids");
        if (root != null) {
            Set<String> seen = new HashSet<>();
            for (Element cell : root.getElementsByTag("td")) {
                Element img = cell.selectFirst("a[href$=.html] img");
                Element title = cell.selectFirst("a[href$=.html]:not(:has(img))");
                if (title == null || title.absUrl("href").equals(document.location())
                        || !seen.add(title.absUrl("href"))) {
                    continue;
                }
                BookPOJO bookPOJO = new BookPOJO();
                bookPOJO.setUrl(title.absUrl("href"));
                bookPOJO.setName(title.text().trim());
                if (img != null) {
                    bookPOJO.setPhoto(img.absUrl("src"));
                }
                if (!bookPOJO.isNull()) {
                    books.add(bookPOJO);
                }
            }
        }
        return books;
    }

    /** The inline Playerjs list of the book page. */
    public static ArrayList<AudioPOJO> parseTracks(Document doc, String bookUrl) {
        return Players.inlinePlayerjs(doc, bookUrl, text(doc.selectFirst("h1")));
    }

    /** The cycle link of a book page, resolved against audioboo itself; "" when there is none. */
    public static String parseSeriesUrl(Document doc) {
        Element a = doc.selectFirst("a[href*=/xfsearch/cikl/]");
        if (a == null) {
            a = doc.selectFirst(".xfvalue_series a");
        }
        return a == null ? "" : a.absUrl("href");
    }

    public static ArrayList<SeriesPOJO> parseSeriesPage(Document doc) {
        Elements cards = doc.select(".card");
        if (!cards.isEmpty()) {
            return each(cards, card -> {
                Element a = card.selectFirst(".card__title a");
                if (a == null) {
                    a = card.selectFirst("a[href*=.html]");
                }
                return seriesItem(a, card.selectFirst("img"));
            });
        }
        Element list = doc.getElementById("dle-content");
        if (list == null) {
            return new ArrayList<>();
        }
        return each(list.getElementsByClass("short-item"),
                book -> seriesItem(book.selectFirst("a.short-title, .short-title a"), book.selectFirst("img")));
    }

    /** DLE comments; a reply sits inside the block of the comment it answers. */
    public static ComentsModel.Parsed parseComments(Document doc) {
        Element list = doc.getElementById("dle-comments-list");
        if (list == null) {
            return new ComentsModel.Parsed();
        }
        //a reply sits inside the block of the comment it answers
        String block = "div[id^=comment-id-]";
        return ComentsModel.nest(list.select(block), block, (comment, pojo) -> {
            pojo.setName(text(comment, ".comment-item__author"));
            pojo.setDate(text(comment, ".comment-item__date"));
            pojo.setText(text(comment, ".comment-item__main"));
        });
    }

    /** Search cards: "Author - Title" headings, the author and reader ("Исполнитель:") in the card's list. */
    public static ArrayList<OtherSourceModel.Hit> parseSearch(Document doc) {
        return each(doc.select("article.card"), book -> {
            Element a = book.selectFirst(".card__title a");
            if (a == null) {
                return null;
            }
            String author = "";
            String reader = "";
            for (Element li : book.select(".card__list li")) {
                String label = li.text();
                if (label.contains("Автор:")) {
                    author = text(li, "a");
                } else if (label.contains("Исполнитель:") || label.contains("Читает:")) {
                    reader = text(li, "a");
                }
            }
            return new OtherSourceModel.Hit(a.absUrl("href"), OtherSourceModel.withoutAuthor(a.text().trim(), author),
                    author, reader);
        });
    }
}
