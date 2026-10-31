package com.fanok.audiobooks.source;

import static com.fanok.audiobooks.source.Json.arrayOf;
import static com.fanok.audiobooks.source.Json.string;

import com.fanok.audiobooks.BookRemovedException;
import com.fanok.audiobooks.model.OtherSourceModel;
import com.fanok.audiobooks.net.JsonPage;
import com.fanok.audiobooks.net.SiteHttp;
import com.fanok.audiobooks.pojo.AudioPOJO;
import com.fanok.audiobooks.pojo.BookPOJO;
import com.fanok.audiobooks.pojo.ComentsPOJO;
import com.fanok.audiobooks.pojo.ContentParentPOJO;
import com.fanok.audiobooks.pojo.DescriptionPOJO;
import com.fanok.audiobooks.pojo.GenrePOJO;
import com.fanok.audiobooks.pojo.OtherArtistPOJO;
import com.fanok.audiobooks.pojo.SeriesPOJO;
import com.fanok.audiobooks.pojo.SubComentsPOJO;
import com.fanok.audiobooks.search.ListingFilters;
import com.fanok.audiobooks.search.ProviderPage;
import com.fanok.audiobooks.search.SearchUrls;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import io.reactivex.functions.BooleanSupplier;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.HttpUrl;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

/**
 * lis10book.com (probed live, 2026-10-31): every HTML page answers 200 with a Turnstile guard, so the app reads only
 * the site's open JSON API (no cookies, no Referer) and an HTML answer is an error. The API URLs are the app's listing
 * URLs; a book is its public page {@code /audio/<slug>/}, which {@link #fetch} reads as {@code /api/books/<slug>}.
 * <ul>
 * <li>books {@code /api/books?audio=full} ({@code audio=full} leaves out the books only premium donors hear),
 * {@code &genre=<slug>}, {@code &q=<words>} (a substring of the title only): {@code {items, total, page, limit}},
 * paged with {@code page=N}, 24 cards ({@code limit} takes up to 48).</li>
 * <li>sorts {@code sort=popular} (views), {@code rating} (livelib), {@code duration} (longest first), else newest; the
 * same on an author {@code /api/authors/<slug>} and a reader {@code /api/narrators/<slug>} (cards under
 * {@code books}). No periods. {@code dur=short|medium|long}: under 3 h, 3 to 10 h, 10 h and more.</li>
 * <li>a series {@code /api/series/<slug>} is one page, by position ({@code sort} and {@code audio} ignored).</li>
 * <li>genres {@code /api/genres} (a list); authors {@code /api/authors} and readers {@code /api/narrators} by book
 * count, 60 a page ({@code totalPages} says 1: the pages follow from {@code total}); their search {@code ?q=}.</li>
 * <li>book {@code /api/books/<slug>}: people, genres, series with their books, other narrations; similar books
 * {@code /similar}; comments {@code /comments?limit=L&offset=O}, newest first, replies nested. Chapters
 * {@code /api/p/<slug>}: an MP3 each on s1.fantbox.net or s2.knizhin.net, which answer 403 unless the Referer is on
 * the site: the default one, the book page, is (both hosts belong to other sites too, so they are no {@link #hosts}).
 * A book whose {@code donorTier} is "fragment" has a blocked playlist.</li>
 * </ul>
 */
public final class Lis10book implements SourceParser {

    public static final String SERVER = "https://lis10book.com";
    public static final String INDEX = SERVER + "/api/books?audio=full";
    public static final String GENRES = SERVER + "/api/genres";
    public static final String AUTHORS = SERVER + "/api/authors";
    public static final String READERS = SERVER + "/api/narrators";

    /** The most cards a page of the books API holds: the other-source lookup reads one page. */
    static final int MAX_LIMIT = 48;
    /** Comments asked for at once. */
    static final int COMMENTS_PAGE = 50;
    /** Bound of the comment pages read for one book. */
    static final int MAX_COMMENT_PAGES = 20;
    /** Where {@link #fetch} puts a book's similar books into its record. */
    static final String SIMILAR = "similar";
    /** Durations of the server's {@code dur} buckets, in minutes. */
    private static final int SHORT_BELOW = 180;
    private static final int LONG_FROM = 600;

    private static final List<Sort.Entry> MENU = Sort.menu(Sort.NEW, Sort.POPULAR, Sort.RATING, Sort.DURATION);
    private static final Pattern BOOK = Pattern.compile("^https?://(?:www\\.)?lis10book\\.com/audio/([^/?#]+)");
    private static final Pattern PERSON_BOOKS = Pattern.compile("^/api/(authors|narrators)/[^/]+$");
    private static final Pattern DATE = Pattern.compile("^(\\d{4})-(\\d{2})-(\\d{2})");

    // ---------------------------------------------------------------- SourceParser

    /**
     * A book page is its record with its similar books added ({@link #withSimilar}), which the description shows
     * without them when that second request fails; any other URL is an API call.
     */
    @Override
    public Document fetch(String url) throws IOException {
        Matcher book = BOOK.matcher(url);
        if (!book.find()) {
            return SiteHttp.getJson(url);
        }
        String record = SERVER + "/api/books/" + book.group(1);
        JsonPage page = SiteHttp.getJson(record);
        try {
            withSimilar(page, SiteHttp.getJson(record + "/similar"));
        } catch (IOException | IllegalStateException noSimilar) {
            // the book without its similar books
        }
        return page;
    }

    @Override
    public ProviderPage<BookPOJO> listing(Document page, String url, int pageNo) {
        return parseListing(page);
    }

    @Override
    public String pageUrl(String url, int page) {
        return ListingFilters.withParam(url, "page", String.valueOf(page));
    }

    /** The index, a genre, an author and a reader re-sort on the server; a series does not, and a search gets no menu. */
    @Override
    public List<Sort.Entry> sorts(String url) {
        return sortable(url) ? MENU : Collections.<Sort.Entry>emptyList();
    }

    @Override
    public String sortUrl(Sort sort, String listingUrl) {
        String value = sortValue(sort);
        return value == null ? null : withSort(INDEX, value);
    }

    /** The books of a genre, an author or a reader without sort and page: the server ranks each of them itself. */
    @Override
    public String genreOf(String url) {
        return sortable(url) && !isIndex(url) ? withSort(url, "") : null;
    }

    @Override
    public String inGenre(String siteWide, String genre) {
        String sort = HttpUrl.get(siteWide).queryParameter("sort");
        return withSort(genre, sort == null ? "" : sort);
    }

    /**
     * The server's duration bucket that holds the whole range ({@code dur}), so its cards are only those that may
     * match; none for a range across buckets.
     */
    @Override
    public String withDuration(String url, ListingFilters.Duration duration) {
        if (!sortable(url)) {
            return url;
        }
        String bucket = bucket(duration);
        HttpUrl.Builder builder = HttpUrl.get(url).newBuilder();
        return (bucket == null ? builder.removeAllQueryParameters("dur") : builder.setQueryParameter("dur", bucket))
                .build().toString();
    }

    @Override
    public ProviderPage<GenrePOJO> genres(Document page, String url, int pageNo) {
        return ProviderPage.of(parseGenres(page), false);
    }

    @Override
    public ProviderPage<GenrePOJO> people(Document page, String url, int pageNo, boolean performers) {
        return parsePeople(page, performers);
    }

    @Override
    public String searchUrl(SearchUrls.Category category, String query, int page) {
        String base;
        switch (category) {
            case BOOKS: base = INDEX + "&q="; break;
            case AUTHORS: base = AUTHORS + "?q="; break;
            default: base = READERS + "?q="; break;
        }
        return base + SearchUrls.enc(query) + "&page=" + page;
    }

    @Override
    public ProviderPage<GenrePOJO> searchPeople(Document page, int pageNo, boolean performers) {
        return parsePeople(page, performers);
    }

    @Override
    public DescriptionPOJO description(Document page, String bookUrl) {
        return parseDescription(page);
    }

    @Override
    public ArrayList<BookPOJO> recommendations(Document page, String bookUrl) {
        return parseRecommendations(page);
    }

    /** The playlist of the book; a book kept for premium donors is a {@link BookRemovedException}. */
    @Override
    public ArrayList<AudioPOJO> tracks(Document page, String base) throws IOException {
        JsonObject book = object(page);
        String slug = string(book, "slug");
        if ("fragment".equals(string(book, "donorTier"))) {
            throw new BookRemovedException(bookUrl(slug));
        }
        return parseTracks(SiteHttp.getJson(SERVER + "/api/p/" + slug), bookUrl(slug));
    }

    @Override
    public ArrayList<SeriesPOJO> series(Document page, String bookUrl) {
        return parseSeries(page);
    }

    /** All pages of the comments API, newest first as the site lists them. */
    @Override
    public ArrayList<ComentsPOJO> comments(String bookUrl, BooleanSupplier cancelled) throws Exception {
        ArrayList<ComentsPOJO> all = new ArrayList<>();
        Matcher book = BOOK.matcher(bookUrl);
        if (!book.find()) {
            return all;
        }
        String comments = SERVER + "/api/books/" + book.group(1) + "/comments?limit=" + COMMENTS_PAGE + "&offset=";
        int offset = 0;
        for (int no = 0; no < MAX_COMMENT_PAGES && !cancelled.getAsBoolean(); no++) {
            Document page = fetch(comments + offset);
            all.addAll(parseComments(page));
            JsonObject answer = object(page);
            int read = arrayOf(answer, "comments").size();
            if (read == 0 || !flag(answer, "hasMore")) {
                break;
            }
            offset += read;
        }
        return all;
    }

    @Override
    public boolean findsOtherReaders() {
        return true;
    }

    /** The book record lists its other narrations itself ({@link #parseDescription}): no search of its own. */
    @Override
    public boolean probesOtherReaders() {
        return false;
    }

    @Override
    public ArrayList<OtherArtistPOJO> otherReaders(String bookUrl, String title, String author, String reader)
            throws IOException {
        return parseOtherReaders(fetch(bookUrl));
    }

    /** The title alone: the site searches titles only, and a name added to them finds nothing. */
    @Override
    public String otherSourceTerms(BookPOJO book) {
        return OtherSourceModel.query(book.getName());
    }

    /** The search with the most cards a page holds: the lookup reads one page. */
    @Override
    public String otherSourceSearchUrl(String terms, int page) {
        return searchUrl(SearchUrls.Category.BOOKS, terms, page) + "&limit=" + MAX_LIMIT;
    }

    // ---------------------------------------------------------------- URLs

    /** The public page of the book {@code slug}: what the app stores, shows and deep-links. */
    public static String bookUrl(String slug) {
        return SERVER + "/audio/" + slug + "/";
    }

    private static String genreUrl(String slug) {
        return INDEX + "&genre=" + slug;
    }

    /** The books of the author or reader {@code slug} ({@code kind} "authors" or "narrators"); "" without one. */
    private static String personUrl(String kind, String slug) {
        return slug.isEmpty() ? "" : SERVER + "/api/" + kind + "/" + slug + "?audio=full";
    }

    /** {@code /api/books} (not a search), an author's or a reader's books. */
    private static boolean sortable(String url) {
        HttpUrl parsed = HttpUrl.parse(url);
        if (parsed == null) {
            return false;
        }
        String path = parsed.encodedPath();
        return path.equals("/api/books") ? parsed.queryParameter("q") == null : PERSON_BOOKS.matcher(path).matches();
    }

    private static boolean isIndex(String url) {
        HttpUrl parsed = HttpUrl.get(url);
        return parsed.encodedPath().equals("/api/books") && parsed.queryParameter("genre") == null;
    }

    /** {@code url} from its first page in the sort {@code value} ("" for the newest first, the default). */
    private static String withSort(String url, String value) {
        HttpUrl.Builder builder = HttpUrl.get(url).newBuilder().removeAllQueryParameters("page");
        return (value.isEmpty() ? builder.removeAllQueryParameters("sort") : builder.setQueryParameter("sort", value))
                .build().toString();
    }

    private static String sortValue(Sort sort) {
        switch (sort) {
            case NEW: return "";
            case POPULAR: return "popular";
            case RATING: return "rating";
            case DURATION: return "duration";
            default: return null;
        }
    }

    /** The {@code dur} bucket holding all of {@code duration}; null when none does. */
    static String bucket(ListingFilters.Duration duration) {
        if (duration.isAny()) {
            return null;
        }
        boolean bounded = !duration.isUnbounded();
        if (bounded && duration.toMinutes <= SHORT_BELOW) {
            return "short";
        }
        if (bounded && duration.fromMinutes >= SHORT_BELOW && duration.toMinutes <= LONG_FROM) {
            return "medium";
        }
        return duration.fromMinutes >= LONG_FROM ? "long" : null;
    }

    // ---------------------------------------------------------------- listings

    /** Index, sorts, genre, search, an author's, a reader's or a series' books. */
    public static ProviderPage<BookPOJO> parseListing(Document page) {
        JsonObject root = object(page);
        JsonElement books = root.get("books");
        JsonObject list = books != null && books.isJsonObject() ? books.getAsJsonObject() : root;
        return ProviderPage.of(cards(objects(list, "items")), hasMore(list), arrayOf(list, "items").size());
    }

    /** Page times page size below the total: the {@code totalPages} of the people indexes is wrong. */
    private static boolean hasMore(JsonObject list) {
        return (long) number(list, "page") * number(list, "limit") < number(list, "total");
    }

    private static ArrayList<BookPOJO> cards(List<JsonObject> items) {
        ArrayList<BookPOJO> books = new ArrayList<>();
        for (JsonObject item : items) {
            BookPOJO book = card(item);
            if (!book.isNull()) {
                books.add(book);
            }
        }
        return books;
    }

    private static BookPOJO card(JsonObject o) {
        BookPOJO book = new BookPOJO();
        String slug = string(o, "slug");
        if (!slug.isEmpty()) {
            book.setUrl(bookUrl(slug));
        }
        book.setName(string(o, "title"));
        book.setPhoto(cover(o));
        book.setAutor(names(o, "authors"));
        book.setUrlAutor(personUrl("authors", slugOf(first(o, "authors"))));
        book.setArtist(names(o, "narrators"));
        book.setUrlArtist(personUrl("narrators", slugOf(first(o, "narrators"))));
        book.setTime(time(o.get("totalDurationSec")));
        if (stars(o) > 0) {
            book.setReting(string(o, "uknigRating"));
        }
        return book;
    }

    /** The list of {@code /api/genres}, with each genre's book count. */
    public static ArrayList<GenrePOJO> parseGenres(Document page) {
        JsonElement root = JsonPage.of(page);
        if (!root.isJsonArray()) {
            throw new IllegalStateException("not a genre list: " + page.location());
        }
        ArrayList<GenrePOJO> genres = new ArrayList<>();
        for (JsonElement item : root.getAsJsonArray()) {
            JsonObject o = item.isJsonObject() ? item.getAsJsonObject() : new JsonObject();
            String name = string(o, "name");
            String slug = string(o, "slug");
            if (name.isEmpty() || slug.isEmpty()) {
                continue;
            }
            GenrePOJO genre = new GenrePOJO(name, genreUrl(slug));
            JsonElement count = o.get("_count");
            genre.setReting(count != null && count.isJsonObject() ? number(count.getAsJsonObject(), "books") : 0);
            genres.add(genre);
        }
        return genres;
    }

    /** A page of the authors or readers index or of their search: name, books and the book count. */
    public static ProviderPage<GenrePOJO> parsePeople(Document page, boolean performers) {
        JsonObject root = object(page);
        ArrayList<GenrePOJO> people = new ArrayList<>();
        for (JsonObject o : objects(root, "items")) {
            String name = string(o, "name");
            String url = personUrl(performers ? "narrators" : "authors", string(o, "slug"));
            if (!name.isEmpty() && !url.isEmpty()) {
                GenrePOJO person = new GenrePOJO(name, url);
                person.setReting(number(o, "count"));
                people.add(person);
            }
        }
        return ProviderPage.of(people, hasMore(root), arrayOf(root, "items").size());
    }

    // ---------------------------------------------------------------- book

    /** The book record: the first genre and series, all authors and readers, the other narrations. */
    public static DescriptionPOJO parseDescription(Document page) {
        JsonObject o = object(page);
        DescriptionPOJO d = new DescriptionPOJO();
        d.setTitle(string(o, "title"));
        d.setPoster(cover(o));
        d.setAutor(names(o, "authors"));
        d.setAutorUrl(personUrl("authors", slugOf(first(o, "authors"))));
        d.setArtist(names(o, "narrators"));
        d.setArtistUrl(personUrl("narrators", slugOf(first(o, "narrators"))));
        JsonObject genre = first(o, "genres");
        if (genre != null) {
            d.setGenre(string(genre, "name"));
            d.setGenreUrl(genreUrl(string(genre, "slug")));
        }
        JsonObject series = first(o, "series");
        if (series != null) {
            d.setSeries(string(series, "title"));
            d.setSeriesUrl(SERVER + "/api/series/" + string(series, "slug"));
        }
        d.setTime(time(o.get("totalDurationSec")));
        double stars = stars(o);
        d.setReiting(stars > 0 ? BookPOJO.formatRating((float) stars, string(o, "uknigRatingsCount")) : "");
        d.setDescription(Jsoup.parseBodyFragment(string(o, "description")).text());
        ArrayList<OtherArtistPOJO> others = parseOtherReaders(page);
        d.setOtherReader(!others.isEmpty());
        if (!others.isEmpty()) {
            d.setOtherReaders(others);
        }
        return d;
    }

    /** {@code book} (a record) with the items of {@code similar} under {@link #SIMILAR}. */
    static JsonPage withSimilar(JsonPage book, Document similar) {
        object(book).add(SIMILAR, arrayOf(object(similar), "items"));
        return book;
    }

    /** The similar books {@link #fetch} added to the record; none when it could not. */
    public static ArrayList<BookPOJO> parseRecommendations(Document page) {
        return cards(objects(object(page), SIMILAR));
    }

    /** The books of the book's first series, with their numbers, in the site's order. */
    public static ArrayList<SeriesPOJO> parseSeries(Document page) {
        JsonObject o = object(page);
        String slug = slugOf(first(o, "series"));
        ArrayList<SeriesPOJO> out = new ArrayList<>();
        for (JsonObject group : objects(o, "seriesGroups")) {
            if (slug.isEmpty() || !slug.equals(string(group, "slug"))) {
                continue;
            }
            for (JsonObject b : objects(group, "books")) {
                if (string(b, "slug").isEmpty() || string(b, "title").isEmpty()) {
                    continue;
                }
                SeriesPOJO item = new SeriesPOJO();
                item.setUrl(bookUrl(string(b, "slug")));
                item.setName(string(b, "title"));
                item.setNumber(string(b, "seriesPosition"));
                item.setPhoto(cover(b));
                out.add(item);
            }
        }
        return out;
    }

    /** The other narrations of the record that play in full. */
    public static ArrayList<OtherArtistPOJO> parseOtherReaders(Document page) {
        ArrayList<OtherArtistPOJO> out = new ArrayList<>();
        for (JsonObject alt : objects(object(page), "altNarrations")) {
            if (string(alt, "slug").isEmpty() || flag(alt, "fragment")) {
                continue;
            }
            OtherArtistPOJO pojo = new OtherArtistPOJO();
            pojo.setName("Исполнитель " + names(alt, "narrators"));
            pojo.setUrl(bookUrl(string(alt, "slug")));
            out.add(pojo);
        }
        return out;
    }

    /**
     * The playlist of {@code bookUrl}: an MP3 per chapter, without durations. A playlist the site blocks (premium
     * only) or cuts to a fragment is a {@link BookRemovedException}.
     */
    public static ArrayList<AudioPOJO> parseTracks(Document playlist, String bookUrl) {
        JsonObject o = object(playlist);
        if (flag(o, "blocked") || flag(o, "fragment")) {
            throw new BookRemovedException(bookUrl);
        }
        Players.Chapters chapters = new Players.Chapters(string(o, "title"), bookUrl);
        chapters.each(arrayOf(o, "chapters"), item -> {
            JsonObject c = item.getAsJsonObject();
            return chapters.chapter(string(c, "title"), string(c, "src"), Json.seconds(c.get("durationSec")));
        });
        return chapters.done(bookUrl);
    }

    /** One page of the comments API; replies of any depth join their thread, naming whom they answer. */
    public static ArrayList<ComentsPOJO> parseComments(Document page) {
        ArrayList<ComentsPOJO> out = new ArrayList<>();
        for (JsonObject c : objects(object(page), "comments")) {
            ComentsPOJO comment = new ComentsPOJO();
            fill(comment, c);
            addReplies(comment, c, comment.getName());
            if (!comment.isEmty()) {
                out.add(comment);
            }
        }
        return out;
    }

    private static void addReplies(ComentsPOJO thread, JsonObject parent, String parentName) {
        for (JsonObject r : objects(parent, "replies")) {
            SubComentsPOJO reply = new SubComentsPOJO();
            fill(reply, r);
            reply.setParentName(parentName);
            if (!reply.isEmty()) {
                thread.setChildComents(reply);
            }
            addReplies(thread, r, reply.getName());
        }
    }

    private static void fill(ContentParentPOJO pojo, JsonObject c) {
        pojo.setName(string(c, "authorName"));
        Matcher date = DATE.matcher(string(c, "createdAt"));
        pojo.setDate(date.find() ? date.group(3) + "." + date.group(2) + "." + date.group(1) : "");
        pojo.setText(string(c, "body"));
        int votes = number(c, "upvotes") - number(c, "downvotes");
        pojo.setReting(votes == 0 ? "" : votes > 0 ? "+" + votes : String.valueOf(votes));
    }

    // ---------------------------------------------------------------- JSON fields

    private static JsonObject object(Document page) {
        JsonElement json = JsonPage.of(page);
        if (!json.isJsonObject()) {
            throw new IllegalStateException("JSON is not an object: " + page.location());
        }
        return json.getAsJsonObject();
    }

    /** The objects of the list under {@code key}; none when it is absent. */
    private static List<JsonObject> objects(JsonObject o, String key) {
        List<JsonObject> out = new ArrayList<>();
        JsonElement list = o.get(key);
        if (list != null && list.isJsonArray()) {
            for (JsonElement item : list.getAsJsonArray()) {
                if (item.isJsonObject()) {
                    out.add(item.getAsJsonObject());
                }
            }
        }
        return out;
    }

    private static JsonObject first(JsonObject o, String key) {
        List<JsonObject> all = objects(o, key);
        return all.isEmpty() ? null : all.get(0);
    }

    private static String slugOf(JsonObject o) {
        return o == null ? "" : string(o, "slug");
    }

    /** The names of the people under {@code key}, comma-separated. */
    private static String names(JsonObject o, String key) {
        StringBuilder out = new StringBuilder();
        for (JsonObject person : objects(o, key)) {
            String name = string(person, "name");
            if (!name.isEmpty()) {
                out.append(out.length() == 0 ? "" : ", ").append(name);
            }
        }
        return out.toString();
    }

    private static String cover(JsonObject o) {
        String path = string(o, "coverPath");
        return flag(o, "hasCover") && !path.isEmpty() ? SERVER + "/wp-content/uploads/" + path : "";
    }

    /** H:MM:SS of a number of seconds; "" for none. */
    private static String time(JsonElement seconds) {
        int s = Json.seconds(seconds);
        return s == 0 ? "" : String.format(Locale.ROOT, "%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60);
    }

    /** The 5-star rating the site shows (imported from uknig); 0 when unrated. */
    private static double stars(JsonObject o) {
        JsonElement e = o.get("uknigRating");
        return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber() ? e.getAsDouble() : 0;
    }

    private static int number(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber() ? e.getAsInt() : 0;
    }

    private static boolean flag(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonPrimitive() && e.getAsBoolean();
    }
}
