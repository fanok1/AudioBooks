package com.fanok.audiobooks.source;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.fanok.audiobooks.BookRemovedException;
import com.fanok.audiobooks.pojo.AudioPOJO;
import com.fanok.audiobooks.pojo.BookPOJO;
import com.fanok.audiobooks.pojo.DescriptionPOJO;
import com.fanok.audiobooks.pojo.GenrePOJO;
import com.fanok.audiobooks.pojo.SeriesPOJO;
import com.fanok.audiobooks.search.Provider;
import com.fanok.audiobooks.search.ProviderPage;
import com.fanok.audiobooks.search.SearchUrls;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;

/** uknig.com pages saved on 2026-10-25 (see src/test/resources/fixtures/uknig). */
public class UknigTest {

    private static final Uknig UKNIG = new Uknig();

    private static InputStream open(String name) {
        InputStream in = UknigTest.class.getResourceAsStream("/fixtures/uknig/" + name);
        assertNotNull("missing fixture " + name, in);
        return in;
    }

    private static Document page(String name, String url) throws IOException {
        try (InputStream in = open(name)) {
            return Jsoup.parse(in, "UTF-8", url);
        }
    }

    private static String json(String name) throws IOException {
        try (InputStream in = open(name)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            for (int n; (n = in.read(buf)) != -1; ) {
                out.write(buf, 0, n);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    @Test
    public void indexCards() throws IOException {
        ProviderPage<BookPOJO> page = Uknig.parseListing(page("listing-index-p1.html", Uknig.SERVER + "/"));
        assertEquals(ProviderPage.Status.OK, page.status);
        assertEquals(24, page.items.size());
        assertTrue("5216 pages", page.hasMore);
        BookPOJO first = page.items.get(0);
        assertEquals("https://uknig.com/books/849525", first.getUrl());
        assertEquals("Я не должен был открывать ей дверь", first.getName());
        assertEquals("https://uknig.com/covers/849525_200x300.jpg?t=1792937871", first.getPhoto());
        assertEquals("Александр Гофман", first.getAutor());
        assertEquals("https://uknig.com/authors/aleksandr-gofman", first.getUrlAutor());
        assertEquals("https://uknig.com/readers/aleksandr-gofman", first.getUrlArtist());
        assertEquals("Мистика", first.getGenre());
        assertEquals("https://uknig.com/genres/mistika", first.getUrlGenre());
        assertEquals("site text \"23 минуты 4 секунды\", normalized by the POJO", "23 мин.", first.getTime());
        BookPOJO last = page.items.get(23);
        assertEquals("Отставник 5. Абордаж", last.getName());
        assertEquals("Отставник", last.getSeries());
        assertEquals("https://uknig.com/series/17565", last.getUrlSeries());
        assertEquals("Олег Троицкий", last.getArtist());
        assertEquals("8 ч. 7 мин.", last.getTime());
    }

    @Test
    public void genreSortPagesOnAndSearchEnds() throws IOException {
        ProviderPage<BookPOJO> best = Uknig.parseListing(
                page("listing-genre-best-p1.html", Uknig.SERVER + "/genres/fantastika/best"));
        assertEquals(18, best.items.size());
        assertEquals("Крылья Мальгуса. Ступень вторая", best.items.get(0).getName());
        assertTrue(best.hasMore);

        ProviderPage<BookPOJO> search = Uknig.parseListing(page("search-1984-p1.html", UKNIG.searchUrl(SearchUrls.Category.BOOKS, "1984", 1)));
        assertEquals(13, search.items.size());
        assertEquals("Здравствуй, 1984-й", search.items.get(0).getName());
        assertFalse(search.hasMore);

        ArrayList<BookPOJO> hits = new ArrayList<>(
                Uknig.parseListing(page("other-source-search.html", UKNIG.searchUrl(SearchUrls.Category.BOOKS, "Ночной дозор", 1))).items);
        assertEquals(3, hits.size());
        assertEquals("Ночной Дозор", hits.get(0).getName());
        assertFalse(hits.get(0).getAutor().isEmpty());
    }

    @Test
    public void pagesAndSearch() {
        assertEquals("https://uknig.com/?p=1", UKNIG.pageUrl(Uknig.INDEX, 1));
        assertEquals("https://uknig.com/?p=3", UKNIG.pageUrl(Uknig.INDEX, 3));
        assertEquals("https://uknig.com/authors/pavel-chuk", UKNIG.pageUrl("https://uknig.com/authors/pavel-chuk", 1));
        // a page of the authors or readers is PEOPLE_PAGES site pages of 10 rows, the first one fetched for it
        assertEquals(Uknig.AUTHORS, UKNIG.pageUrl(Uknig.AUTHORS, 1));
        assertEquals("https://uknig.com/authors?p=6", UKNIG.pageUrl(Uknig.AUTHORS, 2));
        assertEquals("https://uknig.com/readers?p=11", UKNIG.pageUrl(Uknig.READERS, 3));
        assertEquals("https://uknig.com/authors/pavel-chuk?p=2", UKNIG.pageUrl("https://uknig.com/authors/pavel-chuk", 2));
        assertEquals("https://uknig.com/?q=%D0%9D%D0%BE%D1%87%D0%BD%D0%BE%D0%B9+%D0%B4%D0%BE%D0%B7%D0%BE%D1%80&p=2",
                UKNIG.searchUrl(SearchUrls.Category.BOOKS, "Ночной дозор", 2));
        assertNull(UKNIG.searchUrl(SearchUrls.Category.AUTHORS, "Гофман", 1));
        assertFalse(UKNIG.hasSort("https://uknig.com/readers/oleg-isaev"));
        assertTrue(UKNIG.hasSort("https://uknig.com/genres/fantastika/best?p="));
    }

    /** As BooksPresenter does it: the site-wide sort, then narrowed to the genre the listing is in. */
    private static String sorted(Sort itemId, String listing) {
        String url = UKNIG.sortUrl(itemId, listing);
        String genre = UKNIG.genreOf(listing);
        return url.equals(listing) || genre == null ? url : UKNIG.inGenre(url, genre);
    }

    @Test
    public void sortsSiteWideAndInGenre() {
        String home = Uknig.INDEX;
        assertEquals(Uknig.POPULAR_TODAY, sorted(Sort.POPULAR_DAY, home));
        assertEquals(Uknig.POPULAR_WEEK, sorted(Sort.POPULAR_WEEK, home));
        assertEquals(Uknig.POPULAR_ALL_TIME, sorted(Sort.POPULAR_ALL_TIME, home));
        assertEquals(Uknig.BEST, sorted(Sort.RATING, home));
        assertEquals(home, sorted(Sort.NEW, Uknig.POPULAR_MONTH));
        assertNull(UKNIG.sortUrl(Sort.COMMENTS, home));

        String genre = "https://uknig.com/genres/fantastika?p=";
        assertEquals("https://uknig.com/genres/fantastika/popular/today?p=", sorted(Sort.POPULAR_DAY, genre));
        assertEquals("https://uknig.com/genres/fantastika/popular?p=", sorted(Sort.POPULAR_ALL_TIME, genre));
        String best = sorted(Sort.RATING, "https://uknig.com/genres/fantastika/popular/month?p=");
        assertEquals("https://uknig.com/genres/fantastika/best?p=", best);
        assertEquals("https://uknig.com/genres/fantastika/best?p=2", UKNIG.pageUrl(best, 2));
        assertEquals("already there", best, UKNIG.sortUrl(Sort.RATING, best));
        assertEquals(genre, sorted(Sort.NEW, best));
        assertNull(UKNIG.genreOf(Uknig.GENRES));
    }

    @Test
    public void people() throws IOException {
        // one site page (UKNIG.people reads PEOPLE_PAGES of them from the network)
        ProviderPage<GenrePOJO> page = Uknig.parsePeople(page("people-authors-p1.html", Uknig.AUTHORS));
        assertEquals(10, page.items.size());
        assertTrue("2891 pages", page.hasMore);
        assertEquals("Alex.S", page.items.get(0).getName());
        assertEquals("https://uknig.com/authors/allig-eri", page.items.get(1).getUrl());
        assertEquals(2, page.items.get(1).getReting());
    }

    @Test
    public void registered() {
        assertSame(Provider.UKNIG, Provider.ofUrl("https://uknig.com/books/708261"));
        assertTrue(Provider.UKNIG.parser instanceof Uknig);
    }

    @Test
    public void genres() throws IOException {
        ArrayList<GenrePOJO> genres = Uknig.parseGenres(page("genres.html", Uknig.GENRES));
        assertEquals(120, genres.size());
        assertEquals("Для детей", genres.get(0).getName());
        assertEquals("https://uknig.com/genres/dlya-detej?p=", genres.get(0).getUrl());
        assertEquals("4738 аудиокниг", genres.get(0).getDescription());
        assertEquals("the genre list sorts by it", 4738, genres.get(0).getReting());
        assertEquals("Юмор", genres.get(119).getName());
    }

    @Test
    public void description() throws IOException {
        String url = "https://uknig.com/books/708261";
        DescriptionPOJO d = Uknig.parseDescription(page("book-series.html", url));
        assertEquals("Сборник «Неведение отца Брауна»", d.getTitle());
        assertEquals("https://uknig.com/covers/708261.jpg?t=1791967896", d.getPoster());
        assertEquals("Гилберт Кит Честертон", d.getAutor());
        assertEquals("https://uknig.com/authors/gilbert-kit-chesterton", d.getAutorUrl());
        assertEquals("Олег Исаев", d.getArtist());
        assertEquals("https://uknig.com/readers/oleg-isaev", d.getArtistUrl());
        assertEquals("Рассказы", d.getGenre());
        assertEquals("https://uknig.com/genres/rasskazy", d.getGenreUrl());
        assertEquals("Отец Браун", d.getSeries());
        assertEquals("https://uknig.com/series/2452", d.getSeriesUrl());
        assertEquals("10 ч. 28 мин.", d.getTime());
        assertEquals("4.38", d.getReiting());
        assertFalse(d.getDescription().isEmpty());

        BookPOJO book = Uknig.parseBook(page("book.html", "https://uknig.com/books/849527"),
                "https://uknig.com/books/849527");
        assertEquals("Первый постулат", book.getName());
        assertEquals("Puffin Cafe", book.getArtist());
        assertEquals("1 ч. 3 мин.", book.getTime());
        assertTrue(book.getDesc().startsWith("Научно-фантастический рассказ"));
        assertEquals("", Uknig.seriesUrl(page("book.html", "https://uknig.com/books/849527")));
    }

    @Test
    public void recommendations() throws IOException {
        ArrayList<BookPOJO> books = Uknig.parseRecommendations(page("book.html", "https://uknig.com/books/849527"));
        assertEquals(6, books.size());
        assertEquals("https://uknig.com/books/848464", books.get(0).getUrl());
        assertEquals("Мальчик из Ямы 1: Начало", books.get(0).getName());
        assertEquals("Гизум Герко", books.get(0).getAutor());
        assertEquals("https://uknig.com/covers/848464_200x300.jpg?t=1792939134", books.get(0).getPhoto());
    }

    @Test
    public void series() throws IOException {
        Document book = page("book-series.html", "https://uknig.com/books/708261");
        assertEquals("https://uknig.com/series/2452", Uknig.seriesUrl(book));
        ArrayList<SeriesPOJO> series = Uknig.parseSeries(page("series.html", "https://uknig.com/series/2452"));
        assertEquals(24, series.size());
        assertEquals("https://uknig.com/books/848984", series.get(0).getUrl());
        assertEquals("1", series.get(0).getNumber());
        assertEquals("the site writes й decomposed", "Разбойничий рай",
                Normalizer.normalize(series.get(23).getName(), Normalizer.Form.NFC));
        assertEquals("14", series.get(23).getNumber());
        assertFalse(series.get(0).getPhoto().isEmpty());
    }

    @Test
    public void tracks() throws IOException {
        Document book = page("book-series.html", "https://uknig.com/books/708261");
        assertEquals("https://uknig.com/books/708261/playlist.txt?t=1792939722",
                Uknig.playlistUrl(book, "https://uknig.com/books/708261"));
        ArrayList<AudioPOJO> tracks = Uknig.parseTracks(json("playlist.json"), "Сборник «Неведение отца Брауна»");
        assertEquals(40, tracks.size());
        assertEquals("obr1-001-01", tracks.get(0).getName());
        assertEquals("https://uknig.com/files/1447686?d=1&h=86305aafeb4057e6f4bc4fea729fca63",
                tracks.get(39).getUrl());
        assertEquals("Сборник «Неведение отца Брауна»", tracks.get(39).getBookName());
    }

    @Test
    public void litresBookHasNoTracks() throws IOException {
        String url = "https://uknig.com/books/739402";
        Document blocked = page("book-blocked.html", url);
        assertEquals("Ещё один шанс", Uknig.parseDescription(blocked).getTitle());
        try {
            Uknig.playlistUrl(blocked, url);
            fail("a litres trial is not the book");
        } catch (BookRemovedException expected) {
            assertEquals(url, expected.getMessage());
        }
    }
}
