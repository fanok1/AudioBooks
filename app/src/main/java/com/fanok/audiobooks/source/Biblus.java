package com.fanok.audiobooks.source;

import static com.fanok.audiobooks.search.ListingFilters.param;
import static com.fanok.audiobooks.search.ListingFilters.withParam;
import static com.fanok.audiobooks.source.Html.afterLabel;
import static com.fanok.audiobooks.source.Html.cards;
import static com.fanok.audiobooks.source.Html.each;
import static com.fanok.audiobooks.source.Html.href;
import static com.fanok.audiobooks.source.Html.labelled;
import static com.fanok.audiobooks.source.Html.lastPage;
import static com.fanok.audiobooks.source.Html.rows;
import static com.fanok.audiobooks.source.Html.text;

import com.fanok.audiobooks.BookRemovedException;
import com.fanok.audiobooks.R;
import com.fanok.audiobooks.model.OtherArtistModel;
import com.fanok.audiobooks.model.OtherSourceModel;
import com.fanok.audiobooks.pojo.AudioPOJO;
import com.fanok.audiobooks.pojo.BookPOJO;
import com.fanok.audiobooks.pojo.DescriptionPOJO;
import com.fanok.audiobooks.pojo.GenrePOJO;
import com.fanok.audiobooks.pojo.OtherArtistPOJO;
import com.fanok.audiobooks.pojo.SeriesPOJO;
import com.fanok.audiobooks.search.ListingFilters;
import com.fanok.audiobooks.search.ProviderPage;
import com.fanok.audiobooks.search.SearchUrls;
import com.google.gson.JsonObject;

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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * biblus.org (probed live, 2026-10-31): the engine of izib.uk ({@link Izibuk}) with one, mobile, layout; plain GETs, no
 * cookies, no Referer; m.biblus.org serves the same pages (www is another site). Every listing pages with
 * {@code ?p=N} ({@code &p=N} after a query) and an {@code a[data-page]} pager; a page past the end repeats the last.
 * <ul>
 * <li>new: the index {@code /}, 7 cards on page 1 and 10 from page 2 on; genres {@code /genres/<slug>.html} and the
 * sub-genres of Фантастика {@code /genres/fantastika.html?subcategory=K} (list: {@code /genres.html}); authors
 * {@code /authors/<slug>.html}, readers {@code /readers/<slug>.html}, series {@code /series/<slug>.html} (one page,
 * series order), no index of people.</li>
 * <li>rankings, site-wide only and 20 pages long: {@code /popular/?period=week|month}, {@code /trending/},
 * {@code /finished/}. A ranking opened from a genre carries {@code genre=<slug>}, which the server ignores, and its
 * cards (they link their genre) are narrowed on the device: the 200 books are mostly Фантастика, Роман and Детективы,
 * other genres keep a few or none. Nothing else sorts; a genre's own listing is "new".</li>
 * <li>search {@code /search.html?q=Q&p=N}: titles, series and authors, whole words; books only.</li>
 * <li>book {@code /<slug>.html}: schema.org Book markup, the series with numbers, and an XSPlayer whose tracks are
 * MP3 files under {@code mp3_url_prefix} signed with {@code sign} (expires in about two days). No rating, comments
 * or recommendations; other narrations are found by searching the title and the first author (cards name one).</li>
 * </ul>
 */
public final class Biblus implements SourceParser {

    public static final String SERVER = "https://biblus.org";
    public static final String INDEX = SERVER + "/";
    public static final String GENRES = SERVER + "/genres.html";
    public static final String POPULAR_WEEK = SERVER + "/popular/?period=week";
    public static final String POPULAR_MONTH = SERVER + "/popular/?period=month";
    public static final String TRENDING = SERVER + "/trending/";
    public static final String FINISHED = SERVER + "/finished/";

    private static final List<Sort.Entry> MENU = Collections.unmodifiableList(Arrays.asList(
            Sort.item(Sort.NEW),
            Sort.submenu(R.string.popular, Sort.POPULAR_WEEK, Sort.POPULAR_MONTH),
            Sort.item(Sort.TRENDING), Sort.item(Sort.FINISHED)));
    private static final List<ListingFilters.Period> WEEK_MONTH =
            Arrays.asList(ListingFilters.Period.WEEK, ListingFilters.Period.MONTH);
    /** The index and the rankings, with their query: the listings the menu re-sorts besides a genre. */
    private static final Pattern RANKED = Pattern.compile("^https?://[^/]+/((popular|trending|finished)/)?(\\?|$)");
    private static final Pattern GENRE = Pattern.compile("^https?://[^/]+/genres/([^/?#]+)\\.html");
    /** The index or one of its pages. */
    private static final Pattern INDEX_PAGE = Pattern.compile("^https?://[^/]+/(\\?p=\\d+)?$");

    // The site's class names are build hashes (_aab9b7, _802db0...) that a rebuild renames: elements are found by
    // their ids, links, the schema.org Book markup and labels instead.
    /** A book card, {@code div#book<id>} (its hidden {@code div#book<id>_desc} is not one). */
    private static final String CARD = "div[id~=^book\\d+$]";
    /** A book's own path; the people, genre and series links are in folders. */
    private static final Pattern BOOK_PATH = Pattern.compile("^/[^/]+\\.html$");
    /** Scopes a book-page selector to the book itself, past the site menu. */
    private static final String BOOK = "[itemtype$=/Book]";
    /** The book page's series block (past the Book markup): its heading links the series. */
    private static final String SERIES = "div:has(> div > a[href^=/series/])";

    // ---------------------------------------------------------------- SourceParser

    @Override
    public ProviderPage<BookPOJO> listing(Document page, String url, int pageNo) {
        String p = param(url, "p");
        return parseListing(page, p == null ? pageNo : Integer.parseInt(p));
    }

    /**
     * The index's page 1 is read with its page 2: 7 cards alone may not fill a tablet's grid, which then never asks
     * for more ({@link #pageUrl} makes page n the site's page n + 1).
     */
    @Override
    public ProviderPage<BookPOJO> loadListing(String url, int pageNo) throws IOException {
        ProviderPage<BookPOJO> page = listing(fetch(url), url, pageNo);
        if (!url.equals(INDEX)) {
            return page;
        }
        String second = withParam(INDEX, "p", "2");
        ProviderPage<BookPOJO> next = listing(fetch(second), second, 2);
        ArrayList<BookPOJO> items = new ArrayList<>(page.items);
        items.addAll(next.items);
        return ProviderPage.of(items, next.hasMore);
    }

    /** {@code p=N} added or replaced; page n of the index is the site's page n + 1 ({@link #loadListing}). */
    @Override
    public String pageUrl(String url, int page) {
        int sitePage = page > 1 && INDEX_PAGE.matcher(url).matches() ? page + 1 : page;
        return sitePage <= 1 ? url : withParam(url, "p", Integer.toString(sitePage));
    }

    /** The index, the rankings and a genre; an author, reader, series, sub-genre or search is not re-sorted. */
    @Override
    public List<Sort.Entry> sorts(String url) {
        return RANKED.matcher(url).find() || genreOf(url) != null ? MENU : Collections.<Sort.Entry>emptyList();
    }

    @Override
    public String sortUrl(Sort sort, String listingUrl) {
        switch (sort) {
            case NEW: return INDEX;
            case POPULAR_WEEK: return POPULAR_WEEK;
            case POPULAR_MONTH: return POPULAR_MONTH;
            case TRENDING: return TRENDING;
            case FINISHED: return FINISHED;
            default: return null;
        }
    }

    /** {@code popular} takes {@code period=week|month} only. */
    @Override
    public List<ListingFilters.Period> periods(String url) {
        return url.contains("/popular/") ? WEEK_MONTH : Collections.<ListingFilters.Period>emptyList();
    }

    @Override
    public ListingFilters.Period periodOf(String url, Calendar today) {
        return "month".equals(param(url, "period")) ? ListingFilters.Period.MONTH : ListingFilters.Period.WEEK;
    }

    @Override
    public String withPeriod(String url, ListingFilters.Period period, Calendar today) {
        return withParam(url, "period", period == ListingFilters.Period.MONTH ? "month" : "week");
    }

    @Override
    public String cardGenre(String url) {
        return param(url, "genre");
    }

    /** The slug of a genre listing (not a sub-genre: cards link only the genre), or of a ranking narrowed to one. */
    @Override
    public String genreOf(String url) {
        String narrowed = cardGenre(url);
        if (narrowed != null) {
            return narrowed;
        }
        Matcher m = GENRE.matcher(url);
        return m.find() && param(url, "subcategory") == null ? m.group(1) : null;
    }

    /** "New" is the genre listing itself; a ranking carries the genre for {@link #cardGenre}. */
    @Override
    public String inGenre(String siteWide, String genre) {
        return siteWide.equals(INDEX) ? SERVER + "/genres/" + genre + ".html" : withParam(siteWide, "genre", genre);
    }

    @Override
    public ProviderPage<GenrePOJO> genres(Document page, String url, int pageNo) {
        return parseGenres(page);
    }

    @Override
    public String searchUrl(SearchUrls.Category category, String query, int page) {
        return category == SearchUrls.Category.BOOKS
                ? SERVER + "/search.html?q=" + SearchUrls.enc(query) + "&p=" + page : null;
    }

    @Override
    public DescriptionPOJO description(Document page, String bookUrl) {
        return parseDescription(page);
    }

    @Override
    public ArrayList<AudioPOJO> tracks(Document page, String base) {
        return parseTracks(page, base);
    }

    @Override
    public ArrayList<SeriesPOJO> series(Document page, String bookUrl) {
        return parseSeries(page);
    }

    @Override
    public boolean findsOtherReaders() {
        return true;
    }

    /** The book search for title and first author (the one cards name): the hits of this book with another reader. */
    @Override
    public ArrayList<OtherArtistPOJO> otherReaders(String bookUrl, String title, String author, String reader)
            throws IOException {
        if (title == null || title.trim().isEmpty()) {
            return new ArrayList<>();
        }
        String first = firstAuthor(author);
        Document search = fetch(searchUrl(SearchUrls.Category.BOOKS, OtherSourceModel.query(title, first), 1));
        return OtherArtistModel.otherReaders(otherSourceHits(search), title, first, bookUrl, reader);
    }

    /** "A" of the book page's "A, B": a card names the first author only. */
    static String firstAuthor(String author) {
        return author == null ? null : author.split(",")[0].trim();
    }

    /** Title and author: the search does not look at readers and wants every word. */
    @Override
    public String otherSourceTerms(BookPOJO book) {
        return OtherSourceModel.query(book.getName(), book.getAutor());
    }

    // ---------------------------------------------------------------- pure parsers

    /**
     * Any listing, search included ({@code page} is the site's). The cards of a genre carry no genre and those of a
     * series no author: they get the page's.
     */
    public static ProviderPage<BookPOJO> parseListing(Document doc, int page) {
        boolean hasMore = lastPage(doc.select("a[data-page]")) > page;
        Matcher genre = GENRE.matcher(doc.location());
        final String genreUrl = genre.find() ? SERVER + "/genres/" + genre.group(1) + ".html" : null;
        final String genreName = genreUrl == null ? null : heading(doc);
        final Element seriesAuthor = doc.location().contains("/series/") ? doc.selectFirst("a[href^=/authors/]") : null;
        Elements raw = doc.select(CARD);
        ArrayList<BookPOJO> items = cards(raw, card -> {
            Element cover = card.selectFirst("a[href] > img");
            String path = cover == null ? "" : cover.parent().attr("href");
            if (!BOOK_PATH.matcher(path).matches()) {
                return null;
            }
            BookPOJO book = new BookPOJO();
            book.setUrl(cover.parent().absUrl("href"));
            book.setName(card.getElementsByAttributeValue("href", path).last().text().trim());
            String photo = Html.imgSrc(cover);
            if (!photo.contains("/no-poster.")) {
                book.setPhoto(photo);
            }
            for (Element a : card.select(
                    "a[href^=/genres/], a[href^=/authors/], a[href^=/readers/], a[href^=/series/]")) {
                String href = a.attr("href");
                String url = a.absUrl("href");
                String name = a.text().trim();
                if (href.startsWith("/genres/")) {
                    book.setGenre(name);
                    book.setUrlGenre(url);
                } else if (href.startsWith("/authors/")) {
                    book.setAutor(name);
                    book.setUrlAutor(url);
                } else if (href.startsWith("/readers/")) {
                    book.setArtist(name);
                    book.setUrlArtist(url);
                } else {
                    book.setSeries(name);
                    book.setUrlSeries(url);
                }
            }
            if (book.getUrlGenre() == null && genreUrl != null) {
                book.setGenre(genreName);
                book.setUrlGenre(genreUrl);
            }
            if (book.getUrlAutor() == null && seriesAuthor != null) {
                book.setAutor(seriesAuthor.text().trim());
                book.setUrlAutor(seriesAuthor.absUrl("href"));
            }
            book.setTime(afterLabel(labelled(card, "div", "Длительность")));
            // the hidden annotation is escaped twice ("&amp;quot;")
            book.setDesc(Parser.unescapeEntities(text(card, "[id$=_desc]"), false));
            return book;
        });
        return ProviderPage.of(items, hasMore, raw.size());
    }

    /** The heading before the list ("Классика"), past a sub-genre's tabs to the heading that links its genre. */
    private static String heading(Document doc) {
        Element anchor = doc.getElementById("books_list__pn_anchor");
        for (Element e = anchor == null ? null : anchor.previousElementSibling(); e != null;
                e = e.previousElementSibling()) {
            if (e.selectFirst("a[href*=subcategory]") == null) {
                return e.text().trim();
            }
        }
        return null;
    }

    /** The genre list with each genre's count, then the sub-genres of Фантастика from the site menu. */
    public static ProviderPage<GenrePOJO> parseGenres(Document doc) {
        ArrayList<GenrePOJO> genres = rows(doc.select("a[href*=/genres/]:has(span)"), a -> {
            Elements spans = a.select("span");
            GenrePOJO genre = new GenrePOJO(spans.first().text().trim(), SERVER + a.attr("href").trim());
            String count = spans.last().text().replaceAll("\\D", "");
            if (spans.size() > 1 && !count.isEmpty()) {
                genre.setReting(Integer.parseInt(count));
            }
            return genre;
        });
        genres.addAll(rows(doc.select("#topmenu a[href*=subcategory]"),
                a -> new GenrePOJO(a.text().trim(), SERVER + a.attr("href").trim())));
        return ProviderPage.of(genres, false);
    }

    public static DescriptionPOJO parseDescription(Document doc) {
        DescriptionPOJO description = new DescriptionPOJO();
        Element book = doc.selectFirst(BOOK);
        if (book == null) {
            return description;
        }
        description.setTitle(text(book, "[itemprop=name]"));
        description.setPoster(Html.imgSrc(book.selectFirst("img[src*=/pic/]")));
        Element genre = book.selectFirst("a[href^=/genres/]");
        if (genre != null) {
            description.setGenre(genre.text().trim());
            description.setGenreUrl(genre.absUrl("href"));
        }
        // "Авторы:" lists them all; the link is the first one's
        Element authors = book.selectFirst("[itemprop=author]");
        description.setAutor(text(authors));
        description.setAutorUrl(href(authors, "a[href^=/authors/]"));
        Element reader = labelled(book, "div", "Чита");
        description.setArtist(afterLabel(reader));
        description.setArtistUrl(href(reader, "a[href^=/readers/]"));
        description.setTime(afterLabel(labelled(book, "div", "Длительность")));
        Element series = doc.selectFirst(SERIES + " a[href^=/series/]");
        if (series != null) {
            description.setSeries(series.text().trim());
            description.setSeriesUrl(series.absUrl("href"));
        }
        description.setDescription(text(book, "[itemprop=description]"));
        return description;
    }

    /**
     * The XSPlayer tracks. A page without the player that shows a takedown notice, or a player the site flags as
     * blocked without a track, is a {@link BookRemovedException}.
     */
    public static ArrayList<AudioPOJO> parseTracks(Document doc, String bookUrl) {
        String json = Players.findPlayerScript(doc, "new XSPlayer(", '{');
        if (json == null) {
            if (BookRemovedException.isNotice(doc.text())) {
                throw new BookRemovedException(bookUrl);
            }
            throw new IllegalStateException("biblus page has no player: " + bookUrl);
        }
        JsonObject player = Json.object(json);
        try {
            return Players.xsTracks(player, bookUrl, text(doc, BOOK + " [itemprop=name]"), "biblus player");
        } catch (IllegalStateException nothingToPlay) {
            if ("true".equals(Json.string(player, "blocked"))) {
                throw new BookRemovedException(bookUrl);
            }
            throw nothingToPlay;
        }
    }

    /** The series block of the book page: a numbered row per book, the open book's in bold without a link. */
    public static ArrayList<SeriesPOJO> parseSeries(Document doc) {
        Element block = doc.selectFirst(SERIES);
        if (block == null) {
            return new ArrayList<>();
        }
        return each(block.select("> div:has(> span)"), row -> {
            SeriesPOJO series = new SeriesPOJO();
            series.setNumber(text(row, "> span"));
            Element a = row.selectFirst("> a:not([href^=/readers/])");
            if (a != null) {
                series.setUrl(a.absUrl("href"));
                series.setName(a.text().trim());
            } else {
                series.setName(text(row, "> strong"));
            }
            return series;
        });
    }
}
