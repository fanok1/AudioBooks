package com.fanok.audiobooks.source;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

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
import com.fanok.audiobooks.search.Provider;
import com.fanok.audiobooks.search.ProviderPage;
import com.fanok.audiobooks.search.SearchUrls;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** biblus.org pages saved on 2026-10-31 (see src/test/resources/fixtures/biblus). */
public class BiblusTest {

    private static final Biblus BIBLUS = new Biblus();
    private static final String S = Biblus.SERVER;
    private static final String GENRE = S + "/genres/klassika.html";
    private static final String SUBGENRE = S + "/genres/fantastika.html?subcategory=2";
    private static final String SEARCH_KING = S + "/search.html?q=%D0%9A%D0%B8%D0%BD%D0%B3";
    private static final String BOOK = S + "/konec-vechnosti.html";
    private static final String SERIES_BOOK = S + "/zhatva-dush-5-bezymjannyjj-klan.html";

    private static InputStream open(String name) {
        InputStream in = BiblusTest.class.getResourceAsStream("/fixtures/biblus/" + name);
        assertNotNull("missing fixture " + name, in);
        return in;
    }

    private static Document page(String name, String url) throws IOException {
        try (InputStream in = open(name)) {
            return Jsoup.parse(in, "UTF-8", url);
        }
    }

    private static String text(String name) throws IOException {
        try (InputStream in = open(name)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            for (int n; (n = in.read(buf)) != -1; ) {
                out.write(buf, 0, n);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private static ProviderPage<BookPOJO> listing(String name, String url, int page) throws IOException {
        return BIBLUS.listing(page(name, url), url, page);
    }

    private static List<String> names(ProviderPage<BookPOJO> page) {
        List<String> names = new ArrayList<>();
        for (BookPOJO book : page.items) {
            names.add(book.getName());
        }
        return names;
    }

    // ---------------------------------------------------------------- listings

    @Test
    public void indexPages() throws IOException {
        ProviderPage<BookPOJO> first = listing("listing-index-p1.html", Biblus.INDEX, 1);
        assertEquals(ProviderPage.Status.OK, first.status);
        assertEquals("page 1 has 7 cards", 7, first.items.size());
        assertTrue(first.hasMore);
        BookPOJO book = first.items.get(0);
        assertEquals(S + "/lovec-znanijj-ehkspedicija.html", book.getUrl());
        assertEquals("Ловец знаний. Экспедиция", book.getName());
        assertEquals("https://r7.audioknigi.xyz/a9bb584f4360acab/pic/014401fbe90edf21.jpg", book.getPhoto());
        assertEquals("Роман Романович", book.getAutor());
        assertEquals(S + "/authors/roman-romanovich.html", book.getUrlAutor());
        assertEquals("Александр Чистяков", book.getArtist());
        assertEquals(S + "/readers/aleksandr-chistjakov.html", book.getUrlArtist());
        assertEquals("Ловец знаний", book.getSeries());
        assertEquals(S + "/series/lovec-znanijj.html", book.getUrlSeries());
        assertEquals("Фантастика, фэнтези", book.getGenre());
        assertEquals(S + "/genres/fantastika.html", book.getUrlGenre());
        assertEquals("9 ч. 43 мин.", book.getTime());
        assertTrue(book.getDesc(), book.getDesc().startsWith("Бастард Эрано"));
        assertTrue(book.getDesc().endsWith("..."));
        BookPOJO last = first.items.get(6);
        assertEquals("Инспектор Иманиси расследует", last.getName());
        assertNull(last.getSeries());

        ProviderPage<BookPOJO> second = listing("listing-index-p2.html", S + "/?p=2", 2);
        assertEquals(10, second.items.size());
        assertTrue(second.hasMore);
        assertEquals("Странные картинки", second.items.get(0).getName());
        assertEquals("Долг Клана", second.items.get(9).getName());

        // the app's page 9404 is the site's last page 9405: 4 cards, the first without a cover
        ProviderPage<BookPOJO> end = listing("listing-index-last.html", BIBLUS.pageUrl(Biblus.INDEX, 9404), 9404);
        assertEquals(Arrays.asList("Я, Оперуполномоченный", end.items.get(1).getName(), end.items.get(2).getName(),
                "Болтик"), names(end));
        assertFalse(end.hasMore);
        assertNull("/images/no-poster.png is no cover", end.items.get(0).getPhoto());
    }

    @Test
    public void quotesInAnnotationsAreUnescapedOnce() throws IOException {
        for (String name : new String[]{"listing-index-p1.html", "listing-index-p2.html", "genre-p1.html",
                "listing-popular-week-p1.html", "search-king-p1.html"}) {
            for (BookPOJO book : listing(name, S + "/x", 1).items) {
                assertFalse(name + ": " + book.getDesc(), book.getDesc().contains("&quot;"));
            }
        }
    }

    @Test
    public void genreCardsGetTheGenreOfThePage() throws IOException {
        ProviderPage<BookPOJO> first = listing("genre-p1.html", GENRE, 1);
        assertEquals(10, first.items.size());
        assertTrue(first.hasMore);
        BookPOJO book = first.items.get(0);
        assertEquals("Будденброки. Упадок одной семьи", book.getName());
        assertEquals("Томас Манн", book.getAutor());
        assertEquals("23 ч. 55 мин.", book.getTime());
        assertEquals("Классика", book.getGenre());
        assertEquals(GENRE, book.getUrlGenre());
        assertEquals("Спартак", first.items.get(9).getName());

        ProviderPage<BookPOJO> second = listing("genre-p2.html", BIBLUS.pageUrl(GENRE, 2), 2);
        assertEquals(10, second.items.size());
        assertEquals("Поликушка", second.items.get(0).getName());
        assertEquals("Записки из мертвого дома", second.items.get(9).getName());
        assertTrue(second.hasMore);

        ProviderPage<BookPOJO> end = listing("genre-last.html", BIBLUS.pageUrl(GENRE, 371), 371);
        assertEquals(9, end.items.size());
        assertEquals("Собачье сердце", end.items.get(0).getName());
        assertEquals("Руслан и Людмила", end.items.get(8).getName());
        assertFalse(end.hasMore);

        ProviderPage<BookPOJO> sub = listing("subgenre-p1.html", SUBGENRE, 1);
        assertEquals(10, sub.items.size());
        assertTrue(sub.hasMore);
        assertEquals("Архитектура Переплетения: Ключники (Дрёма)", sub.items.get(0).getName());
        assertEquals("a sub-genre's cards are of its genre", "Фантастика, фэнтези", sub.items.get(0).getGenre());
        assertEquals(S + "/genres/fantastika.html", sub.items.get(0).getUrlGenre());
        ProviderPage<BookPOJO> sub2 = listing("subgenre-p2.html", BIBLUS.pageUrl(SUBGENRE, 2), 2);
        assertEquals("Сильный духом 2. Долой оковы", sub2.items.get(0).getName());
    }

    @Test
    public void rankings() throws IOException {
        ProviderPage<BookPOJO> week = listing("listing-popular-week-p1.html", Biblus.POPULAR_WEEK, 1);
        assertEquals(10, week.items.size());
        assertTrue(week.hasMore);
        assertEquals("Девятиэтажка", week.items.get(0).getName());
        assertEquals("Дух воина", week.items.get(9).getName());
        for (BookPOJO book : week.items) {
            assertNotNull("ranking cards link their genre: " + book.getName(), book.getUrlGenre());
        }
        ProviderPage<BookPOJO> week2 = listing("listing-popular-week-p2.html",
                BIBLUS.pageUrl(Biblus.POPULAR_WEEK, 2), 2);
        assertEquals("Чужак", week2.items.get(0).getName());
        assertTrue(week2.hasMore);
        ProviderPage<BookPOJO> weekEnd = listing("listing-popular-week-last.html",
                BIBLUS.pageUrl(Biblus.POPULAR_WEEK, 20), 20);
        assertEquals(10, weekEnd.items.size());
        assertFalse(weekEnd.hasMore);

        ProviderPage<BookPOJO> month = listing("listing-popular-month-p1.html", Biblus.POPULAR_MONTH, 1);
        assertEquals("Боярыня Марфа", month.items.get(9).getName());
        assertEquals("Ревизор: возвращение в СССР 65",
                listing("listing-trending-p1.html", Biblus.TRENDING, 1).items.get(0).getName());
        BookPOJO finished = listing("listing-finished-p1.html", Biblus.FINISHED, 1).items.get(0);
        assertEquals("Мой любимый тренер", finished.getName());
        assertEquals("Роман, проза", finished.getGenre());
        assertEquals("8 ч. 54 мин.", finished.getTime());
    }

    @Test
    public void peopleAndSeriesListings() throws IOException {
        ProviderPage<BookPOJO> author = listing("author.html", S + "/authors/stiven-king.html", 1);
        assertEquals(10, author.items.size());
        assertTrue(author.hasMore);
        assertEquals("Лангольеры. Один после полуночи", author.items.get(0).getName());
        assertEquals("Сказка", author.items.get(9).getName());

        ProviderPage<BookPOJO> reader = listing("reader.html", S + "/readers/amir-shakirov.html", 1);
        assertEquals("Жатва душ 5. Безымянный клан", reader.items.get(0).getName());
        assertEquals("Маршрут перестроен", reader.items.get(9).getName());
        assertEquals("Амир Шакиров", reader.items.get(0).getArtist());

        ProviderPage<BookPOJO> series = listing("series.html", S + "/series/zhatva-dush.html", 1);
        assertEquals(5, series.items.size());
        assertFalse(series.hasMore);
        assertEquals(S + "/zhatva-dush-ostrov-mjortvykh.html", series.items.get(0).getUrl());
        assertEquals(SERIES_BOOK, series.items.get(4).getUrl());
        for (BookPOJO book : series.items) {
            assertEquals("the series' first author, as cards show one", "Данияр Сугралинов", book.getAutor());
            assertEquals(S + "/authors/danijar-sugralinov.html", book.getUrlAutor());
            assertEquals("Жатва душ", book.getSeries());
        }
    }

    @Test
    public void genreList() throws IOException {
        ProviderPage<GenrePOJO> genres = BIBLUS.genres(page("genres.html", Biblus.GENRES), Biblus.GENRES, 1);
        assertFalse(genres.hasMore);
        assertEquals("22 genres and 7 sub-genres", 29, genres.items.size());
        GenrePOJO first = genres.items.get(0);
        assertEquals("Фантастика, фэнтези", first.getName());
        assertEquals(S + "/genres/fantastika.html", first.getUrl());
        assertEquals(44876, first.getReting());
        assertEquals("Юмор, сатира", genres.items.get(21).getName());
        assertEquals(1111, genres.items.get(21).getReting());
        assertEquals("Любовное Фэнтези", genres.items.get(22).getName());
        assertEquals(S + "/genres/fantastika.html?subcategory=3", genres.items.get(22).getUrl());
        assertEquals("Этногенез", genres.items.get(28).getName());
    }

    // ---------------------------------------------------------------- search

    @Test
    public void search() throws IOException {
        assertEquals(SEARCH_KING + "&p=1", BIBLUS.searchUrl(SearchUrls.Category.BOOKS, "Кинг", 1));
        assertEquals(SEARCH_KING + "&p=2", BIBLUS.searchUrl(SearchUrls.Category.BOOKS, "Кинг", 2));
        assertNull(BIBLUS.searchUrl(SearchUrls.Category.AUTHORS, "Кинг", 1));
        assertNull(BIBLUS.searchUrl(SearchUrls.Category.PERFORMERS, "Кинг", 1));

        ProviderPage<BookPOJO> first = BIBLUS.searchResults(page("search-king-p1.html", SEARCH_KING + "&p=1"), 1);
        assertEquals(10, first.items.size());
        assertTrue(first.hasMore);
        BookPOJO book = first.items.get(0);
        assertEquals("highlighted words keep the whole title", "Подземелье Кинга. Том I", book.getName());
        assertEquals("Подземелье Кинга", book.getSeries());
        assertEquals("Тайниковский", book.getAutor());
        assertEquals("4 ч. 32 мин.", book.getTime());
        ProviderPage<BookPOJO> second = BIBLUS.searchResults(page("search-king-p2.html", SEARCH_KING + "&p=2"), 2);
        assertEquals("Дьякон Кинг-Конг", second.items.get(0).getName());
        assertTrue(second.hasMore);
        ProviderPage<BookPOJO> end = BIBLUS.searchResults(page("search-king-last.html", SEARCH_KING + "&p=32"), 32);
        assertEquals(5, end.items.size());
        assertEquals("Коготь Рагнара", end.items.get(0).getName());
        assertEquals("Хороший человек", end.items.get(4).getName());
        assertFalse(end.hasMore);

        ProviderPage<BookPOJO> none = BIBLUS.searchResults(
                page("search-none.html", S + "/search.html?q=zzzxqw&p=1"), 1);
        assertEquals(ProviderPage.Status.EMPTY, none.status);

        ProviderPage<BookPOJO> byAuthor = BIBLUS.searchResults(page("search-stephen-king-p1.html",
                BIBLUS.searchUrl(SearchUrls.Category.BOOKS, "Стивен Кинг", 1)), 1);
        assertEquals(10, byAuthor.items.size());
        assertEquals("Стивен Кинг", byAuthor.items.get(1).getAutor());
    }

    @Test
    public void otherNarrationsAndOtherSources() throws IOException {
        String url = BIBLUS.searchUrl(SearchUrls.Category.BOOKS, "Мастер и Маргарита Булгаков", 1);
        ArrayList<OtherSourceModel.Hit> hits = BIBLUS.otherSourceHits(page("other-source-search.html", url));
        assertEquals(10, hits.size());
        assertEquals(S + "/master-i-margarita-2.html", hits.get(1).url);
        assertEquals("Мастер и Маргарита", hits.get(1).title);
        assertEquals("Михаил Булгаков", hits.get(1).author);
        assertEquals("Александр Теренков", hits.get(1).reader);

        ArrayList<OtherArtistPOJO> others = OtherArtistModel.otherReaders(hits, "Мастер и Маргарита",
                "Михаил Булгаков", S + "/master-i-margarita-2.html", "Александр Теренков");
        assertTrue(others.size() >= 5);
        for (OtherArtistPOJO other : others) {
            assertFalse(other.getUrl().endsWith("/master-i-margarita-2.html"));
        }
        assertTrue(BIBLUS.findsOtherReaders());

        // a book page names both authors, its search hits the first one only
        String authors = "Аркадий Стругацкий, Борис Стругацкий";
        ArrayList<OtherSourceModel.Hit> picnic = BIBLUS.otherSourceHits(page("search-two-authors.html",
                BIBLUS.searchUrl(SearchUrls.Category.BOOKS, "Пикник на обочине Аркадий Стругацкий", 1)));
        assertEquals(5, picnic.size());
        assertEquals("Аркадий Стругацкий", Biblus.firstAuthor(authors));
        ArrayList<OtherArtistPOJO> picnicOthers = OtherArtistModel.otherReaders(picnic, "Пикник на обочине",
                Biblus.firstAuthor(authors), S + "/piknik-na-obochine-3.html", "Александр Коврижных, Большова Анна");
        assertEquals(3, picnicOthers.size());
        assertEquals(S + "/strugackie-arkadijj-i-boris-piknik-na-obochine.html", picnicOthers.get(0).getUrl());
        assertEquals("Исполнитель Георгий Тараторкин", picnicOthers.get(0).getName());
        assertTrue("the joined authors match no hit", OtherArtistModel.otherReaders(picnic, "Пикник на обочине",
                authors, S + "/piknik-na-obochine-3.html", null).isEmpty());

        BookPOJO book = new BookPOJO();
        book.setName("Конец вечности");
        book.setAutor("Айзек Азимов");
        book.setArtist("Вячеслав Герасимов");
        assertEquals("the search ignores readers", "Конец вечности Айзек Азимов", BIBLUS.otherSourceTerms(book));
    }

    // ---------------------------------------------------------------- pages, sorts, genres

    @Test
    public void pageUrls() {
        assertEquals(Biblus.INDEX, BIBLUS.pageUrl(Biblus.INDEX, 1));
        assertEquals("page 1 holds the site's pages 1 and 2", S + "/?p=3", BIBLUS.pageUrl(Biblus.INDEX, 2));
        assertEquals(GENRE, BIBLUS.pageUrl(GENRE, 1));
        assertEquals(GENRE + "?p=2", BIBLUS.pageUrl(GENRE, 2));
        assertEquals(SUBGENRE + "&p=3", BIBLUS.pageUrl(SUBGENRE, 3));
        assertEquals(Biblus.POPULAR_WEEK + "&p=2", BIBLUS.pageUrl(Biblus.POPULAR_WEEK, 2));
        assertEquals(Biblus.TRENDING + "?p=2", BIBLUS.pageUrl(Biblus.TRENDING, 2));
        assertEquals(S + "/search.html?q=x&p=2", BIBLUS.pageUrl(S + "/search.html?q=x&p=1", 2));
        String narrowed = Biblus.POPULAR_MONTH + "&genre=klassika";
        assertEquals(narrowed + "&p=2", BIBLUS.pageUrl(narrowed, 2));
    }

    /** As BooksPresenter does it ({@link ListingFilters#sorted}): the site-wide sort, then narrowed to the genre. */
    private static String sorted(Sort sort, String listing) {
        String url = BIBLUS.sortUrl(sort, listing);
        String genre = BIBLUS.genreOf(listing);
        return url == null || url.equals(listing) || genre == null ? url : BIBLUS.inGenre(url, genre);
    }

    @Test
    public void sortsSiteWide() {
        List<Sort.Entry> menu = BIBLUS.sorts(Biblus.INDEX);
        assertEquals(4, menu.size());
        assertEquals(Sort.NEW, menu.get(0).sort);
        assertNull(menu.get(1).sort);
        assertEquals(R.string.popular, menu.get(1).title);
        assertEquals(Arrays.asList(Sort.POPULAR_WEEK, Sort.POPULAR_MONTH), menu.get(1).items);
        assertEquals(Sort.TRENDING, menu.get(2).sort);
        assertEquals(Sort.FINISHED, menu.get(3).sort);

        assertEquals(Biblus.POPULAR_WEEK, sorted(Sort.POPULAR_WEEK, Biblus.INDEX));
        assertEquals(Biblus.POPULAR_MONTH, sorted(Sort.POPULAR_MONTH, Biblus.POPULAR_WEEK));
        assertEquals(Biblus.TRENDING, sorted(Sort.TRENDING, Biblus.INDEX));
        assertEquals(Biblus.FINISHED, sorted(Sort.FINISHED, Biblus.TRENDING));
        assertEquals(Biblus.INDEX, sorted(Sort.NEW, Biblus.FINISHED));
        assertNull(BIBLUS.sortUrl(Sort.RATING, Biblus.INDEX));
        assertNull(BIBLUS.sortUrl(Sort.POPULAR_DAY, Biblus.INDEX));
        for (String sortedListing : new String[]{Biblus.POPULAR_WEEK, Biblus.POPULAR_MONTH, Biblus.TRENDING,
                Biblus.FINISHED, BIBLUS.pageUrl(Biblus.TRENDING, 2)}) {
            assertEquals("a sorted listing keeps its menu", menu, BIBLUS.sorts(sortedListing));
            assertNull(BIBLUS.genreOf(sortedListing));
        }
    }

    @Test
    public void rankingsFromAGenreAreNarrowedOnTheDevice() {
        assertEquals("klassika", BIBLUS.genreOf(GENRE));
        assertEquals(BIBLUS.sorts(Biblus.INDEX), BIBLUS.sorts(GENRE));
        String week = sorted(Sort.POPULAR_WEEK, GENRE);
        assertEquals(Biblus.POPULAR_WEEK + "&genre=klassika", week);
        assertEquals("klassika", BIBLUS.cardGenre(week));
        assertEquals("klassika", BIBLUS.genreOf(week));
        assertEquals(Biblus.TRENDING + "?genre=klassika", sorted(Sort.TRENDING, week));
        assertEquals(Biblus.FINISHED + "?genre=klassika", sorted(Sort.FINISHED, week));
        assertEquals("new is the genre itself", GENRE, sorted(Sort.NEW, week));
        assertEquals(BIBLUS.sorts(Biblus.INDEX), BIBLUS.sorts(week));
        assertNull(BIBLUS.cardGenre(GENRE));
        // a card's genre link is the key the narrowed ranking keeps
        assertEquals("klassika", ListingFilters.lastSegment(GENRE));
    }

    @Test
    public void periodsOfPopular() {
        List<ListingFilters.Period> weekMonth = Arrays.asList(ListingFilters.Period.WEEK, ListingFilters.Period.MONTH);
        assertEquals(weekMonth, BIBLUS.periods(Biblus.POPULAR_WEEK));
        assertEquals(Collections.emptyList(), BIBLUS.periods(Biblus.TRENDING));
        assertEquals(Collections.emptyList(), BIBLUS.periods(GENRE));
        assertEquals(ListingFilters.Period.WEEK, BIBLUS.periodOf(Biblus.POPULAR_WEEK, null));
        assertEquals(ListingFilters.Period.MONTH, BIBLUS.periodOf(Biblus.POPULAR_MONTH, null));
        assertEquals(Biblus.POPULAR_MONTH, BIBLUS.withPeriod(Biblus.POPULAR_WEEK, ListingFilters.Period.MONTH, null));
        assertEquals(Biblus.POPULAR_WEEK + "&genre=klassika",
                BIBLUS.withPeriod(Biblus.POPULAR_MONTH + "&genre=klassika", ListingFilters.Period.WEEK, null));
    }

    @Test
    public void listingsWithoutSorts() {
        for (String url : new String[]{SUBGENRE, S + "/authors/stiven-king.html", S + "/readers/amir-shakirov.html",
                S + "/series/zhatva-dush.html", SEARCH_KING + "&p=1", Biblus.GENRES}) {
            assertTrue(url, BIBLUS.sorts(url).isEmpty());
            assertNull(url, BIBLUS.genreOf(url));
        }
        assertTrue(BIBLUS.listsDuration(GENRE));
        assertSame(Provider.BIBLUS, Provider.ofUrl(BOOK));
    }

    // ---------------------------------------------------------------- book page

    @Test
    public void bookPage() throws IOException {
        DescriptionPOJO d = BIBLUS.description(page("book.html", BOOK), BOOK);
        assertEquals("Конец вечности", d.getTitle());
        assertEquals("https://r7.audioknigi.xyz/a3f1bcbff4894f44/pic/4d9044251898559e.jpg", d.getPoster());
        assertEquals("Фантастика, фэнтези", d.getGenre());
        assertEquals(S + "/genres/fantastika.html", d.getGenreUrl());
        assertEquals("Айзек Азимов", d.getAutor());
        assertEquals(S + "/authors/ajjzek-azimov.html", d.getAutorUrl());
        assertEquals("Вячеслав Герасимов", d.getArtist());
        assertEquals(S + "/readers/gerasimov-vjacheslav.html", d.getArtistUrl());
        assertEquals("9 ч. 39 мин.", d.getTime());
        assertTrue(d.getDescription(), d.getDescription().startsWith("Достигнув определенного уровня развития"));
        assertEquals("", d.getSeries());
        assertEquals("", d.getReiting());
        assertTrue(BIBLUS.series(page("book.html", BOOK), BOOK).isEmpty());

        BookPOJO card = BIBLUS.book(page("book.html", BOOK), BOOK);
        assertEquals(BOOK, card.getUrl());
        assertEquals("Конец вечности", card.getName());
        assertEquals("Айзек Азимов", card.getAutor());

        DescriptionPOJO other = BIBLUS.description(page("book-noseries.html", S + "/x.html"), S + "/x.html");
        assertEquals("Не в активном поиске. Книга для тех, кому руководства по отношениям не помогли",
                other.getTitle());
        assertEquals("Психология, философия", other.getGenre());
        assertEquals("Экель Сара", other.getAutor());
        assertEquals("Виктория Зайцева", other.getArtist());
        assertEquals("5 ч. 25 мин.", other.getTime());

        assertEquals("", BIBLUS.description(page("search-none.html", BOOK), BOOK).getTitle());
    }

    @Test
    public void seriesBook() throws IOException {
        Document doc = page("book-series.html", SERIES_BOOK);
        DescriptionPOJO d = BIBLUS.description(doc, SERIES_BOOK);
        assertEquals("Жатва душ 5. Безымянный клан", d.getTitle());
        assertEquals("both authors, the first one's link", "Данияр Сугралинов, Денис Ратманов", d.getAutor());
        assertEquals(S + "/authors/danijar-sugralinov.html", d.getAutorUrl());
        assertEquals("Амир Шакиров", d.getArtist());
        assertEquals("8 ч. 52 мин.", d.getTime());
        assertEquals("https://r7.audioknigi.xyz/0907858c3a7a59c9/pic/c287152935fbebd4.jpg", d.getPoster());
        assertEquals("Жатва душ", d.getSeries());
        assertEquals(S + "/series/zhatva-dush.html", d.getSeriesUrl());

        ArrayList<SeriesPOJO> series = BIBLUS.series(doc, SERIES_BOOK);
        assertEquals(5, series.size());
        assertEquals("1.", series.get(0).getNumber());
        assertEquals("Жатва душ. Остров мёртвых", series.get(0).getName());
        assertEquals(S + "/zhatva-dush-ostrov-mjortvykh.html", series.get(0).getUrl());
        assertEquals("Жатва душ 4. Город титанов", series.get(3).getName());
        assertEquals("5.", series.get(4).getNumber());
        assertEquals("the open book, without a link", "Жатва душ 5. Безымянный клан", series.get(4).getName());
        assertEquals("", series.get(4).getUrl());
    }

    @Test
    public void tracks() throws IOException {
        ArrayList<AudioPOJO> tracks = BIBLUS.tracks(page("book.html", BOOK), BOOK);
        JsonArray expected = Json.object(text("playlist-decoded.json")).getAsJsonArray("chapters");
        assertEquals(145, tracks.size());
        assertEquals(expected.size(), tracks.size());
        for (int i = 0; i < tracks.size(); i++) {
            JsonObject chapter = expected.get(i).getAsJsonObject();
            assertEquals(Json.string(chapter, "title"), tracks.get(i).getName());
            assertEquals(Json.string(chapter, "url"), tracks.get(i).getUrl());
            assertEquals(chapter.get("seconds").getAsInt(), tracks.get(i).getTime());
            assertEquals("Конец вечности", tracks.get(i).getBookName());
        }
        assertEquals("c001. Конец вечности", tracks.get(0).getName());
        assertEquals(247, tracks.get(0).getTime());
        assertEquals("https://r4.audioknigi.xyz/a3f1bcbff4894f44/audio/c001-konec-vechnosti.mp3"
                + "?md5=p5nKbpA74zG8aCMTL_IWtQ&expires=1793670303", tracks.get(0).getUrl());
        assertEquals("c145. Конец вечности", tracks.get(144).getName());
        assertEquals(7, tracks.get(144).getTime());

        ArrayList<AudioPOJO> series = BIBLUS.tracks(page("book-series.html", SERIES_BOOK), SERIES_BOOK);
        assertEquals(26, series.size());
        assertEquals("Жатва душ 5. Безымянный клан 01", series.get(0).getName());
        assertEquals(640, series.get(0).getTime());
        assertEquals(719, series.get(25).getTime());
        int total = 0;
        for (AudioPOJO track : series) {
            total += track.getTime();
        }
        assertEquals(31950, total);
        assertEquals(29, BIBLUS.tracks(page("book-noseries.html", S + "/x.html"), S + "/x.html").size());
    }

    private static Document player(String json) {
        return Jsoup.parse("<div itemscope itemtype=\"http://schema.org/Book\"><div itemprop=\"name\"> Книга </div>"
                + "</div><script>domReady(function() { var player = new XSPlayer(" + json + "); });</script>", BOOK);
    }

    @Test
    public void absoluteFilesAreNotSigned() {
        ArrayList<AudioPOJO> tracks = Biblus.parseTracks(player("{\"id\":1,\"blocked\":false,"
                + "\"mp3_url_prefix\":\"r4.audioknigi.xyz\\/h\\/audio\",\"sign\":\"?md5=a&expires=1\",\"tracks\":["
                + "[1,\"A\",10,0,\"a.mp3\"],[2,\"B\",20,0,\"https:\\/\\/cdn.example.org\\/b.mp3\"]]}"), BOOK);
        assertEquals("https://r4.audioknigi.xyz/h/audio/a.mp3?md5=a&expires=1", tracks.get(0).getUrl());
        assertEquals("https://cdn.example.org/b.mp3", tracks.get(1).getUrl());
        assertEquals("Книга", tracks.get(1).getBookName());
    }

    @Test
    public void removedOrBrokenBooks() {
        String blocked = "{\"id\":1,\"blocked\":true,\"mp3_url_prefix\":\"r4.audioknigi.xyz\\/h\\/audio\","
                + "\"sign\":\"?md5=a\",\"tracks\":[]}";
        assertThrows(BookRemovedException.class, () -> Biblus.parseTracks(player(blocked), BOOK));
        Document notice = Jsoup.parse("<div itemprop=\"name\">Книга</div>"
                + "<p>Аудиокнига удалена по требованию правообладателя</p>", BOOK);
        assertThrows(BookRemovedException.class, () -> Biblus.parseTracks(notice, BOOK));

        IllegalStateException noPlayer = assertThrows(IllegalStateException.class,
                () -> Biblus.parseTracks(Jsoup.parse("<div itemprop=\"name\">Книга</div>", BOOK), BOOK));
        assertFalse(noPlayer instanceof BookRemovedException);
        IllegalStateException empty = assertThrows(IllegalStateException.class,
                () -> Biblus.parseTracks(player(blocked.replace("true", "false")), BOOK));
        assertFalse(empty instanceof BookRemovedException);
    }
}
