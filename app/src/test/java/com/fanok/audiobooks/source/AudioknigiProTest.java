package com.fanok.audiobooks.source;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.fanok.audiobooks.BookRemovedException;
import com.fanok.audiobooks.pojo.AudioPOJO;
import com.fanok.audiobooks.pojo.BookPOJO;
import com.fanok.audiobooks.pojo.ComentsPOJO;
import com.fanok.audiobooks.pojo.DescriptionPOJO;
import com.fanok.audiobooks.pojo.GenrePOJO;
import com.fanok.audiobooks.pojo.SeriesPOJO;
import com.fanok.audiobooks.search.Genre;
import com.fanok.audiobooks.search.ProviderPage;
import com.fanok.audiobooks.search.SearchUrls;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;

/** audioknigi.pro pages saved live on 2026-10-31 (src/test/resources/fixtures/audioknigi-pro). */
public class AudioknigiProTest {

    private static final AudioknigiPro P = new AudioknigiPro();
    private static final String S = AudioknigiPro.SERVER;
    private static final String BOOK = S + "/audioknigi/22614-koleso-vremeni-11-nogh-snovideniy-robert-dghordan.html";
    private static final String GENRE = S + "/zhanr/fantastika/";
    private static final String AUTHOR = S + "/author/stiven-king/";
    private static final String SERIES = S + "/cikl/velikij-gusljar/";
    private static final String MAIN = "&set_new_sort=dle_sort_main&set_direction_sort=dle_direction_main";
    private static final String XFSEARCH = "&set_new_sort=dle_sort_xfsearch&set_direction_sort=dle_direction_xfsearch";

    private static Document fixture(String name, String baseUri) throws IOException {
        try (InputStream in = AudioknigiProTest.class.getResourceAsStream("/fixtures/audioknigi-pro/" + name)) {
            assertNotNull("missing fixture " + name, in);
            return Jsoup.parse(in, "UTF-8", baseUri);
        }
    }

    private static String text(String name) throws Exception {
        URL url = AudioknigiProTest.class.getResource("/fixtures/audioknigi-pro/" + name);
        assertNotNull("missing fixture " + name, url);
        return new String(Files.readAllBytes(Paths.get(url.toURI())), StandardCharsets.UTF_8);
    }

    private static ProviderPage<BookPOJO> listing(String name, String url) throws IOException {
        ProviderPage<BookPOJO> page = AudioknigiPro.parseListing(fixture(name, url));
        assertEquals(name, ProviderPage.Status.OK, page.status);
        for (BookPOJO book : page.items) {
            assertFalse(name, book.getName().isEmpty());
            assertTrue(book.getUrl(), book.getUrl().startsWith(S + "/audioknigi/"));
            assertTrue(book.getUrl() + " cover", book.getPhoto().startsWith(S + "/uploads/"));
        }
        return page;
    }

    private static String first(ProviderPage<BookPOJO> page) {
        return Dle.newsId(page.items.get(0).getUrl());
    }

    private static String last(ProviderPage<BookPOJO> page) {
        return Dle.newsId(page.items.get(page.items.size() - 1).getUrl());
    }

    // ---------------------------------------------------------------- listings

    @Test
    public void indexPagesHaveFullCards() throws IOException {
        ProviderPage<BookPOJO> page = listing("listing-index-p1.html", AudioknigiPro.INDEX);
        assertEquals("the popular block's cards are left out", 20, page.items.size());
        assertTrue(page.hasMore);
        BookPOJO book = page.items.get(0);
        assertEquals("Осень в Российской империи", book.getName());
        assertEquals(S + "/audioknigi/72445-oseny-v-rossiyskoy-imperii.html", book.getUrl());
        assertEquals(S + "/uploads/posts/2026-10/602fe3c9_01a6ac91aefcab7a.jpg", book.getPhoto());
        assertEquals("Дарья Кузнецова, Тимур Суворкин, Игорь Евдокимов, Виктор Дашкевич, Лев Брусилов, Дарья Эпштейн, "
                + "Женя Гравис, Александра Лавалье", book.getAutor());
        assertEquals(S + "/author/darja-kuznecova/", book.getUrlAutor());
        assertEquals("Александр Клюквин", book.getArtist());
        assertEquals(S + "/read/aleksandr-kljukvin/", book.getUrlArtist());
        assertEquals("Детективы, триллеры, боевики", book.getGenre());
        assertEquals(S + "/zhanr/detektivy/", book.getUrlGenre());
        assertEquals("12 ч. 18 мин.", book.getTime());
        assertEquals("no votes", "0", book.getReting());
        assertEquals(0, book.getComents());
        assertNull(book.getSeries());
        assertTrue(book.getDesc(), book.getDesc().length() > 100 && book.getDesc().endsWith("..."));

        BookPOJO inSeries = page.items.get(1);
        assertEquals("Немаловажно но весьма значительно", inSeries.getSeries());
        assertEquals(S + "/cikl/nemalovazhno-no-vesma-znachitelno/", inSeries.getUrlSeries());
        assertEquals("38 мин.", inSeries.getTime());
        assertEquals("72404", last(page));

        ProviderPage<BookPOJO> second = listing("listing-index-p2.html", S + "/page/2/");
        assertEquals(20, second.items.size());
        assertTrue(second.hasMore);
        assertEquals("Идеальная жизнь оказалась иллюзией", second.items.get(0).getName());
        assertEquals("72365", last(second));

        ProviderPage<BookPOJO> end = listing("listing-index-last.html", S + "/page/2514/");
        assertEquals(8, end.items.size());
        assertFalse("the stale page numbers are not trusted", end.hasMore);
        assertEquals("Молот гнева", end.items.get(0).getName());
        assertEquals("Освобождение", end.items.get(7).getName());
    }

    @Test
    public void sortedIndexAndTop100() throws IOException {
        ProviderPage<BookPOJO> rating = listing("listing-rating-p1.html", S + "/");
        assertEquals(20, rating.items.size());
        assertTrue(rating.hasMore);
        assertEquals("Союз нерушимый?", rating.items.get(0).getName());
        assertEquals("9.97", rating.items.get(0).getReting());
        assertEquals("22028", last(rating));
        ProviderPage<BookPOJO> rating2 = listing("listing-rating-p2.html", S + "/page/2/");
        assertEquals("8906", first(rating2));
        assertEquals("8597", last(rating2));
        assertTrue(rating2.hasMore);

        assertEquals("Трансгуманизм Inc.", listing("listing-views-p1.html", S + "/").items.get(0).getName());
        BookPOJO commented = listing("listing-comments-p1.html", S + "/").items.get(0);
        assertEquals("Дети леса 3. Тайна Холли", commented.getName());
        assertEquals(14, commented.getComents());
        assertEquals("54684", first(listing("listing-title-p1.html", S + "/")));

        ProviderPage<BookPOJO> top = listing("top-100.html", AudioknigiPro.TOP_100);
        assertEquals(100, top.items.size());
        assertFalse(top.hasMore);
        assertEquals("7140", first(top));
        assertEquals("22090", last(top));
        assertEquals("Целитель", top.items.get(0).getSeries());
    }

    @Test
    public void genrePagesAndTheirSorts() throws IOException {
        ProviderPage<BookPOJO> page = listing("genre-p1.html", GENRE);
        assertEquals(20, page.items.size());
        assertTrue(page.hasMore);
        assertEquals("Полукровки на Венере", page.items.get(0).getName());
        assertEquals("Бездна", page.items.get(19).getName());
        ProviderPage<BookPOJO> second = listing("genre-p2.html", GENRE + "page/2/");
        assertEquals("72234", first(second));
        assertEquals("72093", last(second));
        ProviderPage<BookPOJO> end = listing("genre-last.html", GENRE + "page/602/");
        assertEquals(4, end.items.size());
        assertFalse(end.hasMore);
        assertEquals("Город и город", end.items.get(0).getName());
        assertEquals("Самозванец", end.items.get(3).getName());

        assertEquals("7140", first(listing("genre-rating-p1.html", GENRE)));
        ProviderPage<BookPOJO> rating2 = listing("genre-rating-p2.html", GENRE + "page/2/");
        assertEquals("7085", first(rating2));
        assertTrue(rating2.hasMore);
        assertEquals("1268", first(listing("genre-views-p1.html", GENRE)));
        assertEquals("21856", first(listing("genre-comments-p1.html", GENRE)));
        assertEquals("33684", first(listing("genre-title-asc-p1.html", GENRE)));
    }

    @Test
    public void personYearAndSeriesListings() throws IOException {
        ProviderPage<BookPOJO> author = listing("author.html", AUTHOR);
        assertEquals(20, author.items.size());
        assertTrue(author.hasMore);
        assertEquals("72203", first(author));
        assertEquals("Стивен Кинг", author.items.get(0).getAutor());
        assertEquals("68589", first(listing("author-p2.html", AUTHOR + "page/2/")));
        ProviderPage<BookPOJO> reader = listing("reader.html", S + "/read/vjacheslav-gerasimov/");
        assertEquals("Первая командировка", reader.items.get(0).getName());
        assertEquals("68929", last(reader));
        ProviderPage<BookPOJO> series = listing("series.html", SERIES);
        assertEquals("Как его узнать?", series.items.get(0).getName());
        assertEquals("Великий Гусляр", series.items.get(0).getSeries());
        assertEquals("67428", last(series));
        assertEquals(20, listing("year.html", S + "/year/2005/").items.size());
        ProviderPage<BookPOJO> collection = listing("collection.html", S + "/podborki/audioknigi-pro-zombi/");
        assertEquals(24, collection.items.size());
        assertTrue(collection.hasMore);
    }

    @Test
    public void genresAndPeopleIndexes() throws IOException {
        ProviderPage<GenrePOJO> genres = AudioknigiPro.parseIndex(fixture("genres.html", AudioknigiPro.GENRES));
        assertEquals(87, genres.items.size());
        assertFalse(genres.hasMore);
        assertEquals("Фантастика", genres.items.get(0).getName());
        assertEquals(GENRE, genres.items.get(0).getUrl());
        assertEquals("12023 аудиокниги", genres.items.get(0).getDescription());
        GenrePOJO lastGenre = genres.items.get(86);
        assertEquals("Романы", lastGenre.getName());
        assertEquals(S + "/zhanr/romany/", lastGenre.getUrl());
        assertEquals("4 аудиокниги", lastGenre.getDescription());
        // cards write some genres in lower case or plural
        assertEquals(Genre.DETECTIVE, Genre.of("триллеры"));
        assertEquals(Genre.ACTION, Genre.of("боевики"));
        assertEquals(Genre.SF_FANTASY, Genre.of("фэнтези"));
        assertEquals(Genre.PROSE, Genre.of("проза"));

        ProviderPage<GenrePOJO> authors = P.people(fixture("people-authors-p1.html", AudioknigiPro.AUTHORS),
                AudioknigiPro.AUTHORS, 1, false);
        assertEquals(150, authors.items.size());
        assertFalse(authors.hasMore);
        assertEquals("Антон Чехов", authors.items.get(0).getName());
        assertEquals(S + "/author/anton-chehov/", authors.items.get(0).getUrl());
        assertEquals("331 аудиокнига", authors.items.get(0).getDescription());
        ProviderPage<GenrePOJO> readers = P.people(fixture("people-readers-p1.html", AudioknigiPro.READERS),
                AudioknigiPro.READERS, 1, true);
        assertEquals("Вячеслав Герасимов", readers.items.get(0).getName());
        assertEquals(S + "/read/vjacheslav-gerasimov/", readers.items.get(0).getUrl());
        assertEquals("867 аудиокниг", readers.items.get(0).getDescription());
        assertEquals(AudioknigiPro.AUTHORS, P.pageUrl(AudioknigiPro.AUTHORS, 2));
    }

    // ---------------------------------------------------------------- search

    @Test
    public void searchPages() throws IOException {
        String p1 = P.searchUrl(SearchUrls.Category.BOOKS, "Кинг", 1);
        assertEquals(S + "/index.php?do=search&subaction=search&story=%D0%9A%D0%B8%D0%BD%D0%B3&search_start=1"
                + "&result_from=1&full_search=0", p1);
        assertNull(P.searchUrl(SearchUrls.Category.AUTHORS, "Кинг", 1));
        assertNull(P.searchUrl(SearchUrls.Category.PERFORMERS, "Кинг", 1));

        ProviderPage<BookPOJO> first = P.searchResults(fixture("search-king-p1.html", p1), 1);
        assertEquals(20, first.items.size());
        assertTrue(first.hasMore);
        assertEquals("Дом на повороте", first.items.get(0).getName());
        assertEquals("Папся", first.items.get(19).getName());
        assertFalse(first.items.get(0).getTime().isEmpty());

        String p2 = P.searchUrl(SearchUrls.Category.BOOKS, "Кинг", 2);
        assertTrue(p2, p2.contains("search_start=2"));
        ProviderPage<BookPOJO> second = P.searchResults(fixture("search-king-p2.html", p2), 2);
        assertEquals(20, second.items.size());
        assertTrue(second.hasMore);
        assertEquals("70063", first(second));
        assertEquals("68589", last(second));

        ProviderPage<BookPOJO> fifth = P.searchResults(fixture("search-king-last.html",
                P.searchUrl(SearchUrls.Category.BOOKS, "Кинг", 5)), 5);
        assertEquals(20, fifth.items.size());
        assertFalse("100 hits at most", fifth.hasMore);

        ProviderPage<BookPOJO> none = P.searchResults(fixture("search-none.html",
                P.searchUrl(SearchUrls.Category.BOOKS, "zzzxqw", 1)), 1);
        assertEquals(ProviderPage.Status.EMPTY, none.status);
        assertFalse(none.hasMore);
    }

    @Test
    public void otherSourceSearchFindsTheBook() throws IOException {
        Document doc = fixture("other-source-search.html",
                P.otherSourceSearchUrl("Мастер и Маргарита Булгаков", 1));
        List<BookPOJO> hits = P.searchResults(doc, 1).items;
        assertEquals(3, hits.size());
        BookPOJO book = hits.get(2);
        assertEquals("Мастер и Маргарита", book.getName());
        assertEquals("Михаил Булгаков", book.getAutor());
        assertEquals("Джахангир Абдуллаев", book.getArtist());
        assertEquals("18 ч. 19 мин.", book.getTime());
        assertEquals(3, P.otherSourceHits(doc).size());
        assertFalse(P.searchResults(doc, 1).hasMore);
    }

    // ---------------------------------------------------------------- sorts and URLs

    @Test
    public void ordersOfEachKindOfListing() {
        String rating = AudioknigiPro.sorted(AudioknigiPro.INDEX, Dle.Order.RATING);
        assertEquals(S + "/?dlenewssortby=rating&dledirection=desc" + MAIN, rating);
        assertEquals(Dle.MENU_WITH_TOP, P.sorts(AudioknigiPro.INDEX));
        assertEquals(Dle.MENU_WITH_TOP, P.sorts(S + "/page/7/"));
        assertEquals(Dle.MENU_WITH_TOP, P.sorts(rating));
        assertEquals("the top 100 keeps the index's menu", Dle.MENU_WITH_TOP, P.sorts(AudioknigiPro.TOP_100));
        assertEquals(Dle.MENU, P.sorts(GENRE));
        assertEquals(Dle.MENU, P.sorts(AudioknigiPro.sorted(GENRE + "page/3/", Dle.Order.TITLE)));
        assertEquals(Dle.MENU, P.sorts(AUTHOR + "page/2/"));
        assertEquals(Dle.MENU, P.sorts(S + "/read/vjacheslav-gerasimov/"));
        assertEquals(Dle.MENU, P.sorts(S + "/year/2005/"));
        List<Sort.Entry> none = Collections.emptyList();
        assertEquals("a series stays in the order of its numbers", none, P.sorts(SERIES));
        assertEquals(none, P.sorts(S + "/podborki/audioknigi-pro-zombi/"));
        assertEquals(none, P.sorts(P.searchUrl(SearchUrls.Category.BOOKS, "Кинг", 1)));
        assertEquals(none, P.sorts(BOOK));
        assertEquals(none, P.sorts(AudioknigiPro.GENRES));
    }

    @Test
    public void menuSortsAreSiteWideAndStayInTheGenre() {
        assertEquals(AudioknigiPro.INDEX, P.sortUrl(Sort.NEW, AudioknigiPro.INDEX));
        String popular = P.sortUrl(Sort.POPULAR, AudioknigiPro.INDEX);
        assertEquals(S + "/?dlenewssortby=news_read&dledirection=desc" + MAIN, popular);
        assertEquals(S + "/?dlenewssortby=rating&dledirection=desc" + MAIN, P.sortUrl(Sort.RATING, GENRE));
        assertEquals(S + "/?dlenewssortby=comm_num&dledirection=desc" + MAIN, P.sortUrl(Sort.COMMENTS, GENRE));
        assertEquals(S + "/?dlenewssortby=title&dledirection=asc" + MAIN, P.sortUrl(Sort.TITLE, GENRE));
        assertEquals(AudioknigiPro.TOP_100, P.sortUrl(Sort.TOP_100, AudioknigiPro.INDEX));
        assertNull(P.sortUrl(Sort.YEARS, AudioknigiPro.INDEX));
        assertNull(P.sortUrl(Sort.POPULAR_WEEK, AudioknigiPro.INDEX));

        String sortedGenre = AudioknigiPro.sorted(GENRE + "page/3/", Dle.Order.RATING);
        assertEquals(GENRE + "page/3/?dlenewssortby=rating&dledirection=desc" + XFSEARCH, sortedGenre);
        assertEquals(GENRE, P.genreOf(sortedGenre));
        assertEquals(GENRE, P.genreOf(GENRE + "page/2/"));
        assertEquals(AUTHOR, P.genreOf(AUTHOR));
        assertEquals(GENRE + "?dlenewssortby=news_read&dledirection=desc" + XFSEARCH, P.inGenre(popular, GENRE));
        assertEquals("new is the plain genre", GENRE, P.inGenre(AudioknigiPro.INDEX, GENRE));
        assertNull(P.genreOf(SERIES));
        assertNull(P.genreOf(AudioknigiPro.INDEX));
        assertNull(P.genreOf(popular));
        assertNull(P.genreOf(AudioknigiPro.TOP_100));
        assertNull(P.genreOf(BOOK));
    }

    @Test
    public void pagesKeepTheirSort() {
        assertEquals(GENRE + "page/2/", P.pageUrl(GENRE, 2));
        assertEquals(GENRE, P.pageUrl(GENRE + "page/5/", 1));
        assertEquals(S + "/page/2/", P.pageUrl(AudioknigiPro.INDEX, 2));
        String rating = AudioknigiPro.sorted(AudioknigiPro.INDEX, Dle.Order.RATING);
        assertEquals(S + "/page/2/?dlenewssortby=rating&dledirection=desc" + MAIN, P.pageUrl(rating, 2));
        assertEquals(rating, P.pageUrl(P.pageUrl(rating, 3), 1));
        assertEquals(AudioknigiPro.TOP_100, P.pageUrl(AudioknigiPro.TOP_100, 2));
        assertEquals(S + "/podborki/audioknigi-pro-zombi/page/2/", P.pageUrl(S + "/podborki/audioknigi-pro-zombi/", 2));
    }

    @Test
    public void plainListingsAreFetchedInTheDateSort() {
        assertEquals(S + "/?dlenewssortby=date&dledirection=desc" + MAIN, AudioknigiPro.fetchUrl(AudioknigiPro.INDEX));
        assertEquals(S + "/page/4/?dlenewssortby=date&dledirection=desc" + MAIN,
                AudioknigiPro.fetchUrl(S + "/page/4/"));
        assertEquals(GENRE + "page/2/?dlenewssortby=date&dledirection=desc" + XFSEARCH,
                AudioknigiPro.fetchUrl(GENRE + "page/2/"));
        assertEquals(SERIES + "?dlenewssortby=date&dledirection=desc" + XFSEARCH, AudioknigiPro.fetchUrl(SERIES));
        String sorted = AudioknigiPro.sorted(AUTHOR, Dle.Order.COMMENTS);
        assertEquals(sorted, AudioknigiPro.fetchUrl(sorted));
        String search = P.searchUrl(SearchUrls.Category.BOOKS, "Кинг", 1);
        assertEquals(search, AudioknigiPro.fetchUrl(search));
        assertEquals(AudioknigiPro.TOP_100, AudioknigiPro.fetchUrl(AudioknigiPro.TOP_100));
        assertEquals(AudioknigiPro.GENRES, AudioknigiPro.fetchUrl(AudioknigiPro.GENRES));
        assertEquals(BOOK, AudioknigiPro.fetchUrl(BOOK));
        assertEquals(S + "/podborki/audioknigi-pro-zombi/",
                AudioknigiPro.fetchUrl(S + "/podborki/audioknigi-pro-zombi/"));
    }

    // ---------------------------------------------------------------- book page

    @Test
    public void bookPage() throws IOException {
        Document doc = fixture("book.html", BOOK);
        DescriptionPOJO d = P.description(doc, BOOK);
        assertEquals("Колесо Времени 11. Нож сновидений", d.getTitle());
        assertEquals(S + "/uploads/posts/2024-05/nozh-snovidenij-robert-dzhordan.jpg", d.getPoster());
        assertEquals("Роберт Джордан", d.getAutor());
        assertEquals(S + "/author/robert-dzhordan/", d.getAutorUrl());
        assertEquals("Akhn", d.getArtist());
        assertEquals(S + "/read/akhn/", d.getArtistUrl());
        assertEquals("Зарубежное фэнтези, Фэнтези, Героическое фэнтези, Боевое фэнтези", d.getGenre());
        assertEquals(S + "/zhanr/zarubezhnoe-fjentezi/", d.getGenreUrl());
        assertEquals("Колесо Времени", d.getSeries());
        assertEquals(S + "/cikl/koleso-vremeni/", d.getSeriesUrl());
        assertEquals("41 ч. 14 мин.", d.getTime());
        assertEquals("10", d.getReiting());
        assertTrue(d.getDescription(), d.getDescription().startsWith("Джордан Роберт - Нож мечты."));

        BookPOJO book = P.book(doc, BOOK);
        assertEquals(BOOK, book.getUrl());
        assertEquals("Колесо Времени 11. Нож сновидений", book.getName());
        assertEquals("10", book.getReting());
        assertEquals(0, book.getComents());
        assertEquals(S + "/cikl/koleso-vremeni/", book.getUrlSeries());

        String other = S + "/audioknigi/6212-kraduschiysya-v-teni.html";
        DescriptionPOJO series = AudioknigiPro.parseDescription(fixture("book-series.html", other));
        assertEquals("Крадущийся в тени", series.getTitle());
        assertEquals("Хроники Сиалы", series.getSeries());
        assertEquals("Алексей Пехов", series.getAutor());
        assertEquals("Владимир Рыбальченко", series.getArtist());
        assertEquals("Приключения, Фэнтези", series.getGenre());
        assertEquals("17 ч. 28 мин.", series.getTime());
        assertEquals("9.12", series.getReiting());
        assertEquals(2, AudioknigiPro.parseBook(fixture("book-series.html", other), other).getComents());

        String noSeries = S + "/audioknigi/72445-oseny-v-rossiyskoy-imperii.html";
        DescriptionPOJO plain = AudioknigiPro.parseDescription(fixture("book-noseries.html", noSeries));
        assertEquals("Осень в Российской империи", plain.getTitle());
        assertEquals("", plain.getSeries());
        assertEquals("", plain.getReiting());
        assertTrue(plain.getAutor(), plain.getAutor().startsWith("Дарья Кузнецова, Тимур Суворкин,"));
        assertEquals("0", AudioknigiPro.parseBook(fixture("book-noseries.html", noSeries), noSeries).getReting());
    }

    @Test
    public void recommendationsAndSeries() throws IOException {
        Document doc = fixture("book.html", BOOK);
        ArrayList<BookPOJO> books = P.recommendations(doc, BOOK);
        assertEquals("author's 6, genre's 6, mix 6", 18, books.size());
        assertEquals("Чёрный камень Аманара", books.get(0).getName());
        assertEquals(S + "/audioknigi/66874-chernyy-kameny-amanara.html", books.get(0).getUrl());
        assertEquals(S + "/uploads/posts/2026-03/68f78a8a27b53.jpg", books.get(0).getPhoto());
        assertEquals("Роберт Джордан", books.get(0).getAutor());
        assertEquals("Ведьма Агнета 1", books.get(17).getName());

        ArrayList<SeriesPOJO> series = P.series(doc, BOOK);
        assertEquals(7, series.size());
        assertEquals("Колесо Времени 1 Око Мира", series.get(0).getName());
        assertEquals(S + "/audioknigi/17902-koleso-vremeni-1-oko-mira-robert-dghordan.html", series.get(0).getUrl());
        assertEquals("", series.get(0).getNumber());
        assertEquals("7", series.get(1).getNumber());
        assertEquals("the open book", BOOK, series.get(5).getUrl());
        assertEquals("11", series.get(5).getNumber());
        assertEquals("Колесо Времени 12. Грядущая буря", series.get(6).getName());

        String other = S + "/audioknigi/6212-kraduschiysya-v-teni.html";
        ArrayList<SeriesPOJO> sial = P.series(fixture("book-series.html", other), other);
        assertEquals(4, sial.size());
        assertEquals("0.1", sial.get(0).getNumber());
        assertEquals("Змейка", sial.get(0).getName());
        assertEquals(other, sial.get(1).getUrl());
        assertTrue(P.series(fixture("book-noseries.html", BOOK), BOOK).isEmpty());
    }

    @Test
    public void inlineComments() throws IOException {
        ArrayList<ComentsPOJO> comments = AudioknigiPro.parseComments(fixture("book-comments.html",
                S + "/audioknigi/16764-deti-lesa-3-tayna-holli.html"));
        assertEquals(14, comments.size());
        ComentsPOJO first = comments.get(0);
        assertEquals("Илья", first.getName());
        assertEquals("14 май 2024", first.getDate());
        assertEquals("+4", first.getReting());
        assertEquals("книга имба очень интересная", first.getText());
        assertEquals(S + "/templates/default/dleimages/noavatar.png", first.getImage());
        assertEquals("Гость Ольга", comments.get(1).getName());
        assertTrue(AudioknigiPro.parseComments(fixture("book.html", BOOK)).isEmpty());
        assertEquals(2, AudioknigiPro.parseComments(fixture("book-series.html", BOOK)).size());
    }

    // ---------------------------------------------------------------- chapters

    @Test
    public void playlistIsDecodedWithTheSitesCodec() throws Exception {
        assertEquals(64, AudioknigiPro.ALPHABET.length());
        String encoded = JsonParser.parseString(text("playlist-encoded.json")).getAsJsonObject()
                .get("encoded").getAsString();
        assertEquals(text("playlist-decoded.txt").trim(), AudioknigiPro.decode(encoded, "22614").trim());

        ArrayList<AudioPOJO> chapters = AudioknigiPro.parseTracks(text("playlist-encoded.json"),
                "Колесо Времени 11. Нож сновидений");
        JsonArray expected = JsonParser.parseString(text("playlist-decoded.json")).getAsJsonArray();
        assertEquals(67, chapters.size());
        for (int i = 0; i < chapters.size(); i++) {
            assertEquals(expected.get(i).getAsJsonObject().get("file").getAsString(), chapters.get(i).getUrl());
        }
        assertEquals("Глава 1", chapters.get(0).getName());
        assertEquals("Колесо Времени 11. Нож сновидений", chapters.get(0).getBookName());
        assertEquals("https://s5.vozknig.com/files/koleso-vremeni-11-nogh-snovideniy-robert-dghordan/00-1.mp3",
                chapters.get(0).getUrl());
        assertEquals("Глава 67", chapters.get(66).getName());
        assertEquals("https://s5.vozknig.com/files/koleso-vremeni-11-nogh-snovideniy-robert-dghordan/39.mp3",
                chapters.get(66).getUrl());
        assertEquals(0, chapters.get(0).getTime());
    }

    @Test
    public void flatSignedPlaylist() throws Exception {
        String answer = text("playlist-flat-signed-encoded.json");
        String encoded = JsonParser.parseString(answer).getAsJsonObject().get("encoded").getAsString();
        assertEquals(text("playlist-flat-signed-decoded.txt").trim(), AudioknigiPro.decode(encoded, "72445").trim());
        ArrayList<AudioPOJO> chapters = AudioknigiPro.parseTracks(answer, "Осень в Российской империи");
        assertEquals(9, chapters.size());
        assertEquals("Глава 1", chapters.get(0).getName());
        assertEquals("https://s5.vozknig.com/uploads/files/2026-10/osen-v-rossijskoj-imperii/01_97534.mp3"
                + "?site=pro&md5=JDGCKY2R3uJnqf9H3hgYeA&expires=1793559737", chapters.get(0).getUrl());
        assertEquals("Глава 9", chapters.get(8).getName());
        assertTrue(chapters.get(8).getUrl().contains("/09_97534.mp3?site=pro&md5="));
    }

    @Test
    public void playlistShapesAndErrors() throws Exception {
        String flat = encode("\"title\":\"Пролог\",\"file\":\"https://54txtbjq4y.a.trbcdn.net/a/1.mp3\"", "5");
        ArrayList<AudioPOJO> one = AudioknigiPro.parseTracks("{\"id\":5,\"encoded\":\"" + flat + "\"}", "x");
        assertEquals(1, one.size());
        assertEquals("Пролог", one.get(0).getName());
        assertEquals("the player's mirror", "https://s4.slovushko.com/a/1.mp3", one.get(0).getUrl());
        String list = encode("[{\"title\":\"1\",\"file\":\"https://h/1.mp3\"},{\"title\":\"2\",\"file\":\"\"}]", "6");
        assertEquals(1, AudioknigiPro.parseTracks("{\"id\":6,\"encoded\":\"" + list + "\"}", "x").size());
        try {
            AudioknigiPro.parseTracks("{\"id\":7,\"encoded\":\"\"}", "x");
            fail("an empty playlist has no chapters");
        } catch (IllegalStateException expected) {
            // expected
        }
    }

    @Test
    public void playerOrLitresFragment() throws IOException {
        assertEquals(S + "/engine/ajax/controller.php?mod=audio_secure_playlist&id=22614",
                AudioknigiPro.playlistUrl(fixture("book.html", BOOK), BOOK));
        String fragment = S + "/audioknigi/1268-eda-i-patrony-4.html";
        try {
            AudioknigiPro.playlistUrl(fixture("book-fragment.html", fragment), fragment);
            fail("a LitRes fragment is no book of the site");
        } catch (BookRemovedException expected) {
            assertEquals(fragment, expected.getMessage());
        }
        try {
            AudioknigiPro.playlistUrl(Jsoup.parse("<h1 class=title>x</h1>", S), BOOK);
            fail("no player");
        } catch (BookRemovedException wrong) {
            fail("a page without a player and without a notice is no takedown");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains(BOOK));
        }
    }

    /** The site's encoder: the plain text XORed with the key stream, in base64 over the site's alphabet. */
    private static String encode(String plain, String newsId) throws Exception {
        byte[] data = plain.getBytes(StandardCharsets.UTF_8);
        MessageDigest md5 = MessageDigest.getInstance("MD5");
        for (int i = 0; i < data.length; i++) {
            data[i] ^= md5.digest((newsId + ":" + AudioknigiPro.SALT + ":" + i / 16)
                    .getBytes(StandardCharsets.UTF_8))[i % 16];
        }
        StringBuilder out = new StringBuilder();
        for (char c : Base64.getEncoder().withoutPadding().encodeToString(data).toCharArray()) {
            out.append(AudioknigiPro.ALPHABET.charAt(Players.BASE64.indexOf(c)));
        }
        return out.toString();
    }

    @Test
    public void aValidPlaylistKeepsItsEscapesAndSkipsAnOddItem() {
        ArrayList<AudioPOJO> tracks = AudioknigiPro.parsePlaylist("[{\"title\":\"Глава \\\"Начало\\\"\","
                + "\"file\":\"https://s5.vozknig.com/files/a/01.mp3\"},null,{\"title\":\"2\",\"file\":"
                + "\"https://s5.vozknig.com/files/a/02.mp3\"}]", "Книга");
        assertEquals(2, tracks.size());
        assertEquals("Глава \"Начало\"", tracks.get(0).getName());
        assertEquals("Глава 2", tracks.get(1).getName());
        assertEquals("https://s5.vozknig.com/files/a/02.mp3", tracks.get(1).getUrl());
        // the older form escapes every quote of the list
        assertEquals(1, AudioknigiPro.parsePlaylist("[{\\\"title\\\":\\\"Пролог\\\",\\\"file\\\":"
                + "\\\"https://s5.vozknig.com/files/a/00.mp3\\\"}]", "Книга").size());
    }
}
