package com.fanok.audiobooks.source;

import static com.fanok.audiobooks.source.Html.text;

import com.fanok.audiobooks.BookRemovedException;
import com.fanok.audiobooks.R;
import com.fanok.audiobooks.net.SiteHttp;
import com.fanok.audiobooks.pojo.AudioPOJO;
import com.fanok.audiobooks.pojo.BookPOJO;
import com.fanok.audiobooks.pojo.DescriptionPOJO;
import com.fanok.audiobooks.pojo.GenrePOJO;
import com.fanok.audiobooks.pojo.SeriesPOJO;
import com.fanok.audiobooks.search.ProviderPage;
import com.fanok.audiobooks.search.SearchUrls;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * uknig.com (probed live, 2026-10-25): plain GETs, no cookies, no Referer. Every listing is 18–24
 * {@code .book-item} cards paged with {@code ?p=N} ({@code &p=N} after a query) and a {@code rel=next} pager link.
 * <ul>
 * <li>new: the index {@code /?p=N}; inside a genre {@code /genres/<slug>?p=N}. Genres: {@code /genres}.</li>
 * <li>popular: {@code /popular-books} (week), {@code /today}, {@code /month}, {@code /all} — one page of 48 cards,
 * {@code p} ignored. Inside a genre, paged: {@code /genres/<slug>/popular} (all time), {@code /popular/today},
 * {@code /popular/week}, {@code /popular/month}.</li>
 * <li>best (rating, no period): {@code /best-books?p=N}, {@code /genres/<slug>/best?p=N}.</li>
 * <li>search {@code /?q=Q&p=N} (books only); {@code /authors/<slug>}, {@code /readers/<slug>}, {@code /series/<id>},
 * {@code /years/YYYY}, all paged with {@code ?p=N}; all authors {@code /authors?p=N}, all readers
 * {@code /readers?p=N} (10 {@code .author-item} rows a page, too few to scroll: {@link #PEOPLE_PAGES} of them make
 * one page of the app's list).</li>
 * <li>book {@code /books/<id>}: tracks are the JSON of its {@code createPlayer("…/playlist.txt?t=…")}, whose
 * {@code file} is "{@code /files/<fid>?h=… or /files/<fid>?d=1&h=…}"; the {@code d=1} one redirects to an mp3
 * that serves ranges ({@code h} is the file's hash and does not expire). A book the site sells through litres
 * has a trial fragment instead of {@code createPlayer}. No comments.</li>
 * </ul>
 */
public final class Uknig implements SourceParser {

    public static final String SERVER = "https://uknig.com";
    public static final String INDEX = SERVER + "/?p=";
    public static final String GENRES = SERVER + "/genres";
    public static final String AUTHORS = SERVER + "/authors";
    public static final String READERS = SERVER + "/readers";
    public static final String BEST = SERVER + "/best-books?p=";
    public static final String POPULAR_TODAY = SERVER + "/popular-books/today";
    public static final String POPULAR_WEEK = SERVER + "/popular-books";
    public static final String POPULAR_MONTH = SERVER + "/popular-books/month";
    public static final String POPULAR_ALL_TIME = SERVER + "/popular-books/all";

    /** Bound of the pages read for a book's series tab. */
    static final int MAX_SERIES_PAGES = 10;
    /** Site pages of {@code /authors} or {@code /readers} per page of the list: one short of a screen never scrolls on. */
    static final int PEOPLE_PAGES = 5;
    private static final Pattern PEOPLE = Pattern.compile("^(https?://[^/]+/(?:authors|readers))(?:\\?p=\\d+)?$");
    private static final Pattern GENRE = Pattern.compile("^https?://[^/]+/genres/[^/?#]+");
    /** Listings of one author, reader, series or year: nothing to re-sort. */
    private static final Pattern UNSORTED = Pattern.compile("^https?://[^/]+/(authors|readers|series|years)/");
    private static final List<Sort.Entry> MENU = Collections.unmodifiableList(Arrays.asList(Sort.item(Sort.NEW),
            Sort.submenu(R.string.popular, Sort.POPULAR_DAY, Sort.POPULAR_WEEK, Sort.POPULAR_MONTH,
                    Sort.POPULAR_ALL_TIME),
            Sort.item(Sort.RATING)));
    private static final Pattern PLAYLIST = Pattern.compile("createPlayer\\(\\s*[\"']([^\"']+playlist\\.txt[^\"']*)");

    // ---------------------------------------------------------------- SourceParser

    @Override
    public ProviderPage<BookPOJO> listing(Document page, String url, int pageNo) {
        return parseListing(page);
    }

    /**
     * Page 1 is the listing itself, or {@code ?p=1} for a URL that ends in {@code p=}. Page N of the authors or readers
     * is the first of its {@link #PEOPLE_PAGES} site pages ({@link #people} reads the others).
     */
    @Override
    public String pageUrl(String url, int page) {
        if (url.endsWith("p=")) {
            return url + page;
        }
        Matcher people = PEOPLE.matcher(url);
        if (people.matches()) {
            return sitePage(people.group(1), (page - 1) * PEOPLE_PAGES + 1);
        }
        return sitePage(url, page);
    }

    private static String sitePage(String url, int page) {
        if (page <= 1) {
            return url;
        }
        return url + (url.contains("?") ? "&p=" : "?p=") + page;
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
            case POPULAR_DAY: siteWide = POPULAR_TODAY; break;
            case POPULAR_WEEK: siteWide = POPULAR_WEEK; break;
            case POPULAR_MONTH: siteWide = POPULAR_MONTH; break;
            case POPULAR_ALL_TIME: siteWide = POPULAR_ALL_TIME; break;
            case RATING: siteWide = BEST; break;
            default: return null;
        }
        String genre = genreOf(listingUrl);
        boolean same = genre == null ? siteWide.equals(listingUrl) : inGenre(siteWide, genre).equals(listingUrl);
        return same ? listingUrl : siteWide;
    }

    @Override
    public String genreOf(String url) {
        Matcher m = GENRE.matcher(url);
        return m.find() ? m.group() : null;
    }

    /** The genre's own ranking: {@code /best}, {@code /popular[/today|week|month]}; "new" is the genre listing. */
    @Override
    public String inGenre(String siteWide, String genre) {
        String sort;
        switch (siteWide) {
            case INDEX: sort = ""; break;
            case BEST: sort = "/best"; break;
            case POPULAR_TODAY: sort = "/popular/today"; break;
            case POPULAR_WEEK: sort = "/popular/week"; break;
            case POPULAR_MONTH: sort = "/popular/month"; break;
            case POPULAR_ALL_TIME: sort = "/popular"; break;
            default: return siteWide;
        }
        return genre + sort + "?p=";
    }

    @Override
    public ProviderPage<GenrePOJO> genres(Document page, String url, int pageNo) {
        return ProviderPage.of(parseGenres(page), false);
    }

    /** {@code page} is the first site page of the list's page {@code pageNo}; the rest of its site pages are read here. */
    @Override
    public ProviderPage<GenrePOJO> people(Document page, String url, int pageNo, boolean performers)
            throws IOException {
        ProviderPage<GenrePOJO> result = parsePeople(page);
        Matcher index = PEOPLE.matcher(url);
        if (!index.matches()) {
            return result;
        }
        ArrayList<GenrePOJO> people = new ArrayList<>(result.items);
        int first = (pageNo - 1) * PEOPLE_PAGES + 1;
        for (int no = first + 1; no < first + PEOPLE_PAGES && result.hasMore; no++) {
            result = parsePeople(fetch(sitePage(index.group(1), no)));
            people.addAll(result.items);
        }
        return ProviderPage.of(people, result.hasMore);
    }

    @Override
    public String searchUrl(SearchUrls.Category category, String query, int page) {
        return category == SearchUrls.Category.BOOKS ? SERVER + "/?q=" + SearchUrls.enc(query) + "&p=" + page : null;
    }

    @Override
    public ProviderPage<BookPOJO> searchResults(Document page, int pageNo) {
        return parseListing(page);
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
    public ArrayList<AudioPOJO> tracks(Document page, String base) throws IOException {
        return parseTracks(SiteHttp.body(SiteHttp.connect(playlistUrl(page, base))), text(page, "h1[itemprop=name]"));
    }

    @Override
    public ArrayList<SeriesPOJO> series(Document page, String bookUrl) throws IOException {
        ArrayList<SeriesPOJO> result = new ArrayList<>();
        String series = seriesUrl(page);
        // a series pages on (24 a page) like any listing
        for (int no = 1; !series.isEmpty() && no <= MAX_SERIES_PAGES; no++) {
            Document doc = fetch(pageUrl(series, no));
            result.addAll(parseSeries(doc));
            if (!hasNext(doc)) {
                break;
            }
        }
        return result;
    }

    // ---------------------------------------------------------------- pure parsers

    /** Any listing: index, sorts, genre, search (also the other-source search), author, reader, series, year. */
    public static ProviderPage<BookPOJO> parseListing(Document doc) {
        Elements cards = doc.select("div.book-item");
        ArrayList<BookPOJO> books = new ArrayList<>();
        for (Element card : cards) {
            BookPOJO book = card(card);
            if (!book.isNull()) {
                books.add(book);
            }
        }
        return ProviderPage.of(books, hasNext(doc), cards.size());
    }

    private static boolean hasNext(Document doc) {
        return !doc.select(".pagination a[rel=next]").isEmpty();
    }

    private static BookPOJO card(Element card) {
        BookPOJO book = new BookPOJO();
        Element title = card.selectFirst("a.book-title");
        if (title != null) {
            book.setUrl(title.absUrl("href"));
            book.setName(title.text().trim());
        }
        Element img = card.selectFirst("img.cover");
        if (img != null) {
            book.setPhoto(img.absUrl("data-original"));
        }
        Element author = card.selectFirst(".book-author a[href*=/authors/]");
        if (author != null) {
            book.setAutor(author.text());
            book.setUrlAutor(author.absUrl("href"));
        }
        Element reader = card.selectFirst(".book-reader a[href*=/readers/]");
        if (reader != null) {
            book.setArtist(reader.text());
            book.setUrlArtist(reader.absUrl("href"));
        }
        Element genre = card.selectFirst("a[href*=/genres/]");
        if (genre != null) {
            book.setGenre(genre.text());
            book.setUrlGenre(genre.absUrl("href"));
        }
        Element series = card.selectFirst("a[href*=/series/]");
        if (series != null) {
            book.setSeries(series.text());
            book.setUrlSeries(series.absUrl("href"));
        }
        String time = text(card, ".book-duration");
        if (!time.isEmpty()) {
            book.setTime(time);
        }
        String desc = text(card, ".description");
        if (!desc.isEmpty()) {
            book.setDesc(desc);
        }
        return book;
    }

    /** The {@code /genres} page: name, listing URL ending in {@code ?p=}, and the book count (also as description). */
    public static ArrayList<GenrePOJO> parseGenres(Document doc) {
        ArrayList<GenrePOJO> genres = new ArrayList<>();
        for (Element row : doc.select("div.genre")) {
            Element a = row.selectFirst("a.heading");
            if (a == null || a.absUrl("href").isEmpty() || a.text().trim().isEmpty()) {
                continue;
            }
            GenrePOJO genre = new GenrePOJO(a.text().trim(), a.absUrl("href") + "?p=");
            String count = text(row, ".books-count");
            genre.setDescription(count);
            String books = count.replaceAll("\\D", ""); // "4738 аудиокниг"
            if (!books.isEmpty() && books.length() < 10) {
                genre.setReting(Integer.parseInt(books));
            }
            genres.add(genre);
        }
        return genres;
    }

    /** A page of {@code /authors} or {@code /readers}: name, the person's listing, and their book count. */
    public static ProviderPage<GenrePOJO> parsePeople(Document doc) {
        Elements rows = doc.select("div.author-item");
        ArrayList<GenrePOJO> people = new ArrayList<>();
        for (Element row : rows) {
            Element a = row.selectFirst("a.author-name");
            if (a == null || a.absUrl("href").isEmpty() || a.text().trim().isEmpty()) {
                continue;
            }
            GenrePOJO person = new GenrePOJO(a.text().trim(), a.absUrl("href"));
            String count = text(row, ".name-line .info");
            if (count.matches("\\d+")) {
                person.setReting(Integer.parseInt(count));
            }
            people.add(person);
        }
        return ProviderPage.of(people, hasNext(doc), rows.size());
    }

    public static DescriptionPOJO parseDescription(Document doc) {
        DescriptionPOJO description = new DescriptionPOJO();
        Element info = info(doc);
        description.setTitle(text(doc, "h1[itemprop=name]"));
        description.setPoster(cover(doc));
        Element a = info.selectFirst("a[href*=/authors/]");
        if (a != null) {
            description.setAutor(a.text());
            description.setAutorUrl(a.absUrl("href"));
        }
        a = info.selectFirst("a[href*=/readers/]");
        if (a != null) {
            description.setArtist(a.text());
            description.setArtistUrl(a.absUrl("href"));
        }
        a = info.selectFirst("a[href*=/genres/]");
        if (a != null) {
            description.setGenre(a.text());
            description.setGenreUrl(a.absUrl("href"));
        }
        a = info.selectFirst("a[href*=/series/]");
        if (a != null) {
            description.setSeries(a.text());
            description.setSeriesUrl(a.absUrl("href"));
        }
        description.setTime(text(info, ".book-duration"));
        description.setReiting(text(doc, ".book-details .rating-number"));
        description.setDescription(text(doc, ".book-details .description"));
        return description;
    }

    /** A book page opened from a link: the card fields of the book itself. */
    public static BookPOJO parseBook(Document doc, String url) {
        return BookPOJO.of(parseDescription(doc), url);
    }

    /** The page's "what next" books (same author or genre); the next of the series is left to {@link #parseSeries}. */
    public static ArrayList<BookPOJO> parseRecommendations(Document doc) {
        ArrayList<BookPOJO> books = new ArrayList<>();
        for (Element a : doc.select("a.whats-next__book")) {
            BookPOJO book = new BookPOJO();
            book.setUrl(a.absUrl("href"));
            book.setName(text(a, ".whats-next__title"));
            Element img = a.selectFirst("img.whats-next__cover");
            if (img != null) {
                book.setPhoto(img.absUrl("src"));
            }
            String author = text(a, ".whats-next__author");
            if (!author.isEmpty()) {
                book.setAutor(author);
            }
            if (!book.isNull()) {
                books.add(book);
            }
        }
        return books;
    }

    /** The series link of a book page; "" when the book is in none. */
    public static String seriesUrl(Document bookPage) {
        Element a = info(bookPage).selectFirst("a[href*=/series/]");
        return a == null ? "" : a.absUrl("href");
    }

    /** The first page of {@code /series/<id>} (24 books), with each book's number. */
    public static ArrayList<SeriesPOJO> parseSeries(Document doc) {
        ArrayList<SeriesPOJO> series = new ArrayList<>();
        for (Element card : doc.select("div.book-item")) {
            Element a = card.selectFirst("a.book-title");
            if (a == null || a.absUrl("href").isEmpty()) {
                continue;
            }
            SeriesPOJO item = new SeriesPOJO();
            item.setUrl(a.absUrl("href"));
            item.setName(a.text().trim());
            Element img = card.selectFirst("img.cover");
            if (img != null) {
                item.setPhoto(img.absUrl("data-original"));
            }
            item.setNumber(text(card, ".number-in-series").replace("#", ""));
            series.add(item);
        }
        return series;
    }

    /**
     * The playlist URL of a book page. A book the site only sells through litres (a trial fragment, no
     * {@code createPlayer}) is a {@link BookRemovedException}.
     */
    public static String playlistUrl(Document doc, String bookUrl) {
        for (Element script : doc.select("script")) {
            Matcher m = PLAYLIST.matcher(script.data());
            if (m.find()) {
                return m.group(1).startsWith("/") ? SERVER + m.group(1) : m.group(1);
            }
        }
        if (!doc.select(".fragment-player").isEmpty()) {
            throw new BookRemovedException(bookUrl);
        }
        throw new IllegalStateException("no player on " + bookUrl);
    }

    /** The playlist JSON {@code [{title, file: "a or b", id, size}]}; picks the direct ({@code d=1}) mirror. */
    public static ArrayList<AudioPOJO> parseTracks(String json, String bookName) {
        ArrayList<AudioPOJO> tracks = new ArrayList<>();
        for (JsonElement item : JsonParser.parseString(json).getAsJsonArray()) {
            JsonObject o = item.getAsJsonObject();
            String url = "";
            for (String file : o.get("file").getAsString().split("\\s+or\\s+")) {
                if (url.isEmpty() || file.contains("d=1")) {
                    url = file.trim();
                }
            }
            AudioPOJO audio = new AudioPOJO();
            audio.setBookName(bookName);
            audio.setName(o.has("title") ? o.get("title").getAsString() : "");
            audio.setUrl(url);
            if (!audio.getUrl().isEmpty()) {
                tracks.add(audio);
            }
        }
        if (tracks.isEmpty()) {
            throw new IllegalStateException("empty uknig playlist");
        }
        return tracks;
    }

    /** The "details" block of a book page (the one under the description, falling back to the page). */
    private static Element info(Document doc) {
        Element info = doc.selectFirst(".book-details .book-info .book-main-info");
        return info != null ? info : doc;
    }

    private static String cover(Document doc) {
        Element img = doc.selectFirst(".book-details .cover img");
        return img == null ? "" : img.absUrl("src");
    }
}
