package com.fanok.audiobooks.source;

import static com.fanok.audiobooks.source.Html.text;

import com.fanok.audiobooks.BookRemovedException;
import com.fanok.audiobooks.model.BookPages;
import com.fanok.audiobooks.model.ComentsModel;
import com.fanok.audiobooks.model.OtherArtistModel;
import com.fanok.audiobooks.model.OtherSourceModel;
import com.fanok.audiobooks.net.JsonPage;
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
import com.google.gson.JsonObject;

import io.reactivex.functions.BooleanSupplier;

import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * aknigi24.com (probed live, 2026-10-31): a Laravel site, plain GETs, no cookies, Referer or challenge. Every listing
 * is 21 {@code .books-grid .card} cards paged with {@code ?page=N} ({@code &page=N} after a query) and a
 * {@code rel=next} pager link; a page past the end is an empty grid.
 * <ul>
 * <li>sorts {@code ?sort=new|rating|title|duration}, stateless; the index {@code /} is {@code new}. A genre
 * {@code /genre/<slug>} takes the same sorts but is ranked by rating without one, so it is opened as
 * {@code ?sort=new}. Genres: {@code /genres} (25, flat). No periods, no popularity or comment sorts.</li>
 * <li>search {@code /search?q=Q&page=N}; {@code /author/<slug>}, {@code /reader/<slug>}, {@code /serie/<slug>} (no
 * numbers) are listings of the same cards. Authors and readers: {@code /authors?letter=X&page=N} (60 a page per
 * letter, {@link #loadPeople} walks the letters) and {@code /authors?search=Q&page=N}; {@code /authors} alone is a
 * top 40. These directory pages answer 429 to more than about one request a second after a burst of four.</li>
 * <li>book {@code /book/<slug>}: chapters in {@code <script id="player-data">}, each a whole MP3
 * {@code chapterBase + n} with its seconds {@code d} and no title; ranges, no Referer needed, no expiry. Comments are
 * on the page, further pages from {@code /api/comment/list} (JSON with their HTML). A removed book is a 404.</li>
 * </ul>
 */
public final class Aknigi24 implements SourceParser {

    public static final String SERVER = "https://aknigi24.com";
    public static final String INDEX = SERVER + "/";
    public static final String GENRES = SERVER + "/genres";
    public static final String AUTHORS = SERVER + "/authors";
    public static final String READERS = SERVER + "/readers";

    /** The index letters of the authors and readers, Latin ones too (the site links only the Cyrillic). */
    static final String LETTERS = "АБВГДЕЖЗИЙКЛМНОПРСТУФХЦЧШЩЭЮЯABCDEFGHIJKLMNOPQRSTUVWXYZ";
    /** Pages one letter may take in the walk of {@link #loadPeople}; past its last page the walk skips to the next. */
    static final int LETTER_PAGES = 1000;
    /** Bound of the pages read for a book's series tab or its comments. */
    static final int MAX_PAGES = 10;
    /** The first wait after a 429; the second waits twice as long. */
    private static final int THROTTLE_MS = 1500;

    private static final List<Sort.Entry> MENU = Sort.menu(Sort.NEW, Sort.RATING, Sort.TITLE, Sort.DURATION);
    private static final Pattern GENRE = Pattern.compile("^https?://[^/]+/genre/[^/?#]+");
    /** Listings of one author, reader or series and the search: ranked by the site alone. */
    private static final Pattern UNSORTED = Pattern.compile("^https?://[^/]+/(author|reader|serie|search)[/?]");
    private static final String COMMENT = "li[id^=comment-]";

    // ---------------------------------------------------------------- SourceParser

    /** The site answers a burst (about a dozen quick requests) with 429: wait and ask again, twice at most. */
    @Override
    public Document fetch(String url) throws IOException {
        for (int attempt = 1; ; attempt++) {
            try {
                return SiteHttp.get(url);
            } catch (HttpStatusException e) {
                if (e.getStatusCode() != 429 || attempt > 2) {
                    throw e;
                }
                try {
                    Thread.sleep(attempt * THROTTLE_MS);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new InterruptedIOException("cancelled while throttled");
                }
            }
        }
    }

    @Override
    public ProviderPage<BookPOJO> listing(Document page, String url, int pageNo) {
        return parseListing(page);
    }

    @Override
    public String pageUrl(String url, int page) {
        return page <= 1 ? url : ListingFilters.withParam(url, "page", String.valueOf(page));
    }

    @Override
    public List<Sort.Entry> sorts(String url) {
        return UNSORTED.matcher(url).find() ? Collections.<Sort.Entry>emptyList() : MENU;
    }

    @Override
    public String sortUrl(Sort sort, String listingUrl) {
        String value = sortValue(sort);
        if (value == null) {
            return null;
        }
        String siteWide = value.equals("new") ? INDEX : INDEX + "?sort=" + value;
        String genre = genreOf(listingUrl);
        boolean same = genre == null ? siteWide.equals(listingUrl) : inGenre(siteWide, genre).equals(listingUrl);
        return same ? listingUrl : siteWide;
    }

    @Override
    public String genreOf(String url) {
        Matcher m = GENRE.matcher(url);
        return m.find() ? m.group() : null;
    }

    /** The genre ranked by the same {@code sort}; "new" is spelled out, the genre's own default being rating. */
    @Override
    public String inGenre(String siteWide, String genre) {
        String sort = ListingFilters.param(siteWide, "sort");
        return genre + "?sort=" + (sort == null ? "new" : sort);
    }

    @Override
    public ProviderPage<GenrePOJO> genres(Document page, String url, int pageNo) {
        return ProviderPage.of(parseGenres(page), false);
    }

    @Override
    public ProviderPage<GenrePOJO> loadPeople(String url, int pageNo, boolean performers, PeopleCursor cursor)
            throws IOException {
        return walkLetters(this::fetch, pageNo, performers, cursor);
    }

    @Override
    public String searchUrl(SearchUrls.Category category, String query, int page) {
        String q = SearchUrls.enc(query) + "&page=" + page;
        switch (category) {
            case BOOKS: return SERVER + "/search?q=" + q;
            case AUTHORS: return AUTHORS + "?search=" + q;
            case PERFORMERS: return READERS + "?search=" + q;
            default: return null;
        }
    }

    @Override
    public ProviderPage<BookPOJO> searchResults(Document page, int pageNo) {
        return parseListing(page);
    }

    @Override
    public ProviderPage<GenrePOJO> searchPeople(Document page, int pageNo, boolean performers) {
        return parsePeople(page);
    }

    @Override
    public DescriptionPOJO description(Document page, String bookUrl) {
        return parseDescription(page);
    }

    @Override
    public ArrayList<BookPOJO> recommendations(Document page, String bookUrl) {
        return parseRecommendations(page);
    }

    @Override
    public ArrayList<AudioPOJO> tracks(Document page, String base) {
        return parseTracks(page, base);
    }

    /** The series linked from the book page, read from its own listing. */
    @Override
    public ArrayList<SeriesPOJO> series(Document page, String bookUrl) throws IOException {
        ArrayList<SeriesPOJO> result = new ArrayList<>();
        String series = Html.href(labelled(page, "Серия"), "a[href*=/serie/]");
        for (int no = 1; !series.isEmpty() && no <= MAX_PAGES; no++) {
            Document doc = fetch(pageUrl(series, no));
            result.addAll(parseSeries(doc));
            if (!hasNext(doc)) {
                break;
            }
        }
        return result;
    }

    /** The comments on the book page, then the further pages its "load more" button names. */
    @Override
    public ArrayList<ComentsPOJO> comments(String bookUrl, BooleanSupplier cancelled) throws Exception {
        Document page = fetch(bookUrl);
        ArrayList<ComentsPOJO> result = parseComments(page.select("#comments-list " + COMMENT)).comments;
        Element more = page.selectFirst("#comments-load-more");
        // the site's script reads page 2 when the button names none
        int next = more == null ? 0 : more.attr("data-page").isEmpty() ? 2 : number(more.attr("data-page"));
        for (int no = 0; next > 0 && no < MAX_PAGES && !cancelled.getAsBoolean(); no++) {
            JsonObject json = JsonPage.of(SiteHttp.getJson(SERVER + "/api/comment/list?page=" + next
                    + "&audiobook_id=" + more.attr("data-audiobook-id") + "&channel="
                    + SearchUrls.enc(more.attr("data-channel")))).getAsJsonObject();
            result.addAll(parseCommentsPage(json).comments);
            next = number(Json.string(json, "next_page"));
        }
        return result;
    }

    @Override
    public boolean findsOtherReaders() {
        return true;
    }

    /** The search by title and author: each narration is a book of its own. */
    @Override
    public ArrayList<OtherArtistPOJO> otherReaders(String bookUrl, String title, String author, String reader)
            throws IOException {
        if (OtherSourceModel.isBlank(title)) {
            return new ArrayList<>();
        }
        Document search = fetch(searchUrl(SearchUrls.Category.BOOKS, OtherSourceModel.query(title, author), 1));
        return OtherArtistModel.otherReaders(otherSourceHits(search), title, author, bookUrl, reader);
    }

    // ---------------------------------------------------------------- requests and URLs

    /** The {@code ?sort=} value of a menu sort; null for a sort the site has not. */
    static String sortValue(Sort sort) {
        switch (sort) {
            case NEW: return "new";
            case RATING: return "rating";
            case TITLE: return "title";
            case DURATION: return "duration";
            default: return null;
        }
    }

    /**
     * Page N of all authors or readers: the site's letter pages one after another. Page N is slot
     * {@code N - 1 + skipped} of a walk that gives each letter {@link #LETTER_PAGES} slots; a letter's last page
     * skips the rest of its slots, an empty letter is passed over.
     */
    static ProviderPage<GenrePOJO> walkLetters(BookPages.Fetch pages, int pageNo, boolean performers,
            PeopleCursor cursor) throws IOException {
        if (pageNo <= 1) {
            cursor.skipped = 0;
        }
        while (true) {
            int slot = pageNo - 1 + cursor.skipped;
            int letter = slot / LETTER_PAGES;
            int page = slot % LETTER_PAGES + 1;
            if (letter >= LETTERS.length()) {
                return ProviderPage.empty();
            }
            String url = letterUrl(performers, LETTERS.charAt(letter), page);
            ProviderPage<GenrePOJO> rows = parsePeople(pages.get(url));
            boolean lastLetter = letter == LETTERS.length() - 1;
            boolean empty = rows.items.isEmpty() && !lastLetter;
            if (!rows.hasMore) {
                // the rest of the letter's slots; this very one too for an empty letter, read again as the next
                cursor.skipped += LETTER_PAGES - page + (empty ? 1 : 0);
            }
            if (!empty) {
                return ProviderPage.of(rows.items, rows.hasMore || !lastLetter);
            }
        }
    }

    /** Page {@code page} of the authors (or readers) under {@code letter}. */
    static String letterUrl(boolean performers, char letter, int page) {
        String url = (performers ? READERS : AUTHORS) + "?letter=" + SearchUrls.enc(String.valueOf(letter));
        return page <= 1 ? url : url + "&page=" + page;
    }

    /** A genre link as the newest-first listing, like the index. */
    static String genreUrl(String href) {
        return href.isEmpty() ? "" : href + "?sort=new";
    }

    // ---------------------------------------------------------------- pure parsers

    /** Any listing: index, sorts, genre, search (also the other-source search), author, reader, series. */
    public static ProviderPage<BookPOJO> parseListing(Document doc) {
        Elements cards = doc.select(".books-grid .card");
        return ProviderPage.of(Html.cards(cards, Aknigi24::card), hasNext(doc), cards.size());
    }

    private static boolean hasNext(Document doc) {
        return !doc.select("ul.pagination a[rel=next]").isEmpty();
    }

    private static BookPOJO card(Element card) {
        BookPOJO book = new BookPOJO();
        Element title = card.selectFirst("h5.card-title a");
        if (title != null) {
            book.setUrl(title.absUrl("href"));
            book.setName(title.text().trim());
        }
        book.setPhoto(Html.imgSrc(card.selectFirst(".book-cover img")));
        book.setReting(text(card, ".cover-badge-rating"));
        book.setTime(text(card, ".cover-badge-duration"));
        Element authors = Html.labelled(card, "p.mb-1", "Автор");
        book.setAutor(names(authors, "a[href*=/author/]"));
        book.setUrlAutor(Html.href(authors, "a[href*=/author/]"));
        Element readers = Html.labelled(card, "p.mb-1", "Исполнитель");
        book.setArtist(names(readers, "a[href*=/reader/]"));
        book.setUrlArtist(Html.href(readers, "a[href*=/reader/]"));
        Element series = card.selectFirst(".book-meta a[href*=/serie/]");
        if (series != null) {
            book.setSeries(series.text().trim());
            book.setUrlSeries(series.absUrl("href"));
        }
        Element genre = card.selectFirst(".book-meta a[href*=/genre/]");
        if (genre != null) {
            book.setGenre(genre.text().trim());
            book.setUrlGenre(genreUrl(genre.absUrl("href")));
        }
        return book;
    }

    /**
     * The people of a "Автор:" or "Исполнитель:" row: a card links two and names all of them in the title of its
     * "и др."; "" for no row.
     */
    private static String names(Element row, String linkCss) {
        if (row == null) {
            return "";
        }
        Element all = row.selectFirst("span[title]");
        return all != null ? all.attr("title").trim() : join(row.select(linkCss).eachText(), ", ");
    }

    /** {@code /genres}: name, the newest-first listing and the book count ("8917 книг", also as description). */
    public static ArrayList<GenrePOJO> parseGenres(Document doc) {
        return Html.rows(doc.select("div.border.rounded:has(> h5 > a[href*=/genre/])"), row -> {
            Element a = row.selectFirst("h5 > a");
            GenrePOJO genre = new GenrePOJO(a.text().trim(), genreUrl(a.absUrl("href")));
            String count = text(row, "div.text-muted");
            genre.setDescription(count);
            genre.setReting(number(count.replaceAll("\\D", "")));
            return genre;
        });
    }

    /** A page of a letter or a search of the authors or readers: name, the person's listing, their book count. */
    public static ProviderPage<GenrePOJO> parsePeople(Document doc) {
        Elements rows = doc.select("a.directory-card");
        ArrayList<GenrePOJO> people = Html.rows(rows, a -> {
            GenrePOJO person = new GenrePOJO(text(a, ".directory-card__name"), a.absUrl("href"));
            person.setReting(number(text(a, ".directory-card__count")));
            return person;
        });
        return ProviderPage.of(people, hasNext(doc), rows.size());
    }

    public static DescriptionPOJO parseDescription(Document doc) {
        DescriptionPOJO description = new DescriptionPOJO();
        description.setTitle(text(doc, "h1 span.d-md-none"));
        description.setPoster(Html.imgSrc(doc.selectFirst("img.book-page-cover")));
        Elements authors = doc.select("h1 a[href*=/author/]");
        description.setAutor(join(authors.eachText(), ", "));
        description.setAutorUrl(authors.isEmpty() ? "" : authors.first().absUrl("href"));
        Element readers = labelled(doc, "Исполнитель");
        description.setArtist(names(readers, "a[href*=/reader/]"));
        description.setArtistUrl(Html.href(readers, "a[href*=/reader/]"));
        Element genre = labelled(doc, "Жанр");
        description.setGenre(text(genre, "a[href*=/genre/]"));
        description.setGenreUrl(genreUrl(Html.href(genre, "a[href*=/genre/]")));
        Element series = labelled(doc, "Серия");
        description.setSeries(text(series, "a[href*=/serie/]"));
        description.setSeriesUrl(Html.href(series, "a[href*=/serie/]"));
        // "⏱ 0 ч 51 мин": the hours are always printed
        description.setTime(text(doc, "h1 + div > span").replace("⏱", "").replaceFirst("^\\s*0 ч", "").trim());
        Element rating = doc.selectFirst("#rating-stars");
        description.setReiting(rating == null ? "" : rating.attr("data-initial-rating"));
        // the text is on the page twice (desktop and mobile)
        Element about = doc.selectFirst("h5.section-title:containsOwn(Описание)");
        description.setDescription(about == null ? "" : join(about.parent().select("> p").eachText(), "\n"));
        return description;
    }

    /** A "Label:" row of the book page; null when there is none. */
    private static Element labelled(Document doc, String label) {
        return Html.labelled(doc, "p.mb-2", label);
    }

    /**
     * The other books of the (first) author, then the popular ones of the genre; a book in both blocks once. The
     * author's cards name only their readers: the author is the book's own.
     */
    public static ArrayList<BookPOJO> parseRecommendations(Document doc) {
        Element author = doc.selectFirst("h1 a[href*=/author/]");
        Elements byAuthor = doc.select("h5.section-title:containsOwn(Другие книги) ~ div .book-mini-card");
        Set<String> seen = new HashSet<>();
        return Html.cards(doc.select(".book-mini-card"), card -> {
            BookPOJO book = new BookPOJO();
            Element a = card.selectFirst(".fw-semibold a");
            if (a == null || !seen.add(a.absUrl("href"))) {
                return null;
            }
            book.setUrl(a.absUrl("href"));
            book.setName(a.text().trim());
            book.setPhoto(Html.imgSrc(card.selectFirst("img")));
            Elements authors = card.select("a[href*=/author/]");
            if (authors.isEmpty() && author != null && byAuthor.contains(card)) {
                authors = new Elements(author);
            }
            book.setAutor(join(authors.eachText(), ", "));
            book.setUrlAutor(authors.isEmpty() ? "" : authors.first().absUrl("href"));
            Elements readers = card.select("a[href*=/reader/]");
            book.setArtist(join(readers.eachText(), ", "));
            book.setUrlArtist(Html.href(card, "a[href*=/reader/]"));
            return book;
        });
    }

    /** A series listing page (no numbers on the site). */
    public static ArrayList<SeriesPOJO> parseSeries(Document doc) {
        return Html.each(doc.select(".books-grid .card"),
                card -> Html.seriesItem(card.selectFirst("h5.card-title a"), card.selectFirst(".book-cover img")));
    }

    /**
     * The chapters of {@code <script id="player-data">}: {@code chapterBase + n}, {@code d} seconds, named "Глава n"
     * as on the site. A page without it is a {@link BookRemovedException} when it says so, else not a book page.
     */
    public static ArrayList<AudioPOJO> parseTracks(Document doc, String bookUrl) {
        Element data = doc.selectFirst("script#player-data");
        if (data == null) {
            if (BookRemovedException.isNotice(doc.text())) {
                throw new BookRemovedException(bookUrl);
            }
            throw new IllegalStateException("no player on " + bookUrl);
        }
        JsonObject player = Json.object(data.data());
        String base = Json.string(player, "chapterBase");
        Players.Chapters chapters = new Players.Chapters(Json.string(player, "title"), bookUrl);
        chapters.each(Json.arrayOf(player, "chapters"), item -> {
            JsonObject o = item.getAsJsonObject();
            String n = Json.string(o, "n");
            return chapters.chapter("Глава " + n, base.isEmpty() || n.isEmpty() ? "" : base + n,
                    Json.seconds(o.get("d")));
        });
        return chapters.done("aknigi24 player-data");
    }

    /** Comments with their replies (nested {@code li}s), from the page or from a page of the comments API. */
    public static ComentsModel.Parsed parseComments(Elements comments) {
        return ComentsModel.nest(comments, COMMENT, (comment, pojo) -> {
            Element head = comment.selectFirst("div.d-flex");
            pojo.setImage(head == null ? "" : Html.imgSrc(head.selectFirst("> img")));
            pojo.setName(text(comment, "strong"));
            pojo.setDate(text(comment, "small.text-muted"));
            pojo.setText(text(comment, "div.mt-2"));
        });
    }

    /** A page of {@code /api/comment/list}: {@code {"html": "<li …>", "next_page": N|null}}. */
    public static ComentsModel.Parsed parseCommentsPage(JsonObject json) {
        return parseComments(Jsoup.parseBodyFragment(Json.string(json, "html"), SERVER).select(COMMENT));
    }

    private static String join(List<String> parts, String separator) {
        StringBuilder s = new StringBuilder();
        for (String part : parts) {
            if (s.length() > 0) {
                s.append(separator);
            }
            s.append(part);
        }
        return s.toString();
    }

    private static int number(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
