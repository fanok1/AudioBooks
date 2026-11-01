package com.fanok.audiobooks.source;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.fanok.audiobooks.BookRemovedException;
import com.fanok.audiobooks.model.OtherArtistModel;
import com.fanok.audiobooks.pojo.AudioPOJO;
import com.fanok.audiobooks.pojo.BookPOJO;
import com.fanok.audiobooks.pojo.DescriptionPOJO;
import com.fanok.audiobooks.pojo.GenrePOJO;
import com.fanok.audiobooks.pojo.OtherArtistPOJO;
import com.fanok.audiobooks.pojo.SeriesPOJO;
import com.fanok.audiobooks.search.Provider;
import com.fanok.audiobooks.search.ProviderPage;
import com.fanok.audiobooks.search.SearchUrls;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;

/** golosom.org pages saved on 2026-10-31 (see src/test/resources/fixtures/golosom). */
public class GolosomTest {

    private static final Golosom GOLOSOM = new Golosom();
    private static final String GENRE = Golosom.SERVER + "/genre/fantastika-fentezi";

    private static Document page(String name, String url) throws IOException {
        try (InputStream in = GolosomTest.class.getResourceAsStream("/fixtures/golosom/" + name)) {
            assertNotNull("missing fixture " + name, in);
            return Jsoup.parse(in, "UTF-8", url);
        }
    }

    private static ProviderPage<BookPOJO> listing(String name, String url, int page) throws IOException {
        return Golosom.parseListing(page(name, url), page);
    }

    @Test
    public void indexFirstPage() throws IOException {
        ProviderPage<BookPOJO> page = listing("listing-index-p1.html", Golosom.INDEX, 1);
        assertEquals(ProviderPage.Status.OK, page.status);
        assertEquals(21, page.items.size());
        assertTrue("4454 pages", page.hasMore);
        BookPOJO first = page.items.get(0);
        assertEquals("https://golosom.org/book/lovets-znanij-ekspeditsiya", first.getUrl());
        assertEquals("Ловец знаний. Экспедиция", first.getName());
        assertEquals("https://r7.audioknigi.xyz/a9bb584f4360acab/pic/db09d74bc59b46a8.jpg", first.getPhoto());
        assertEquals("Роман Романович", first.getAutor());
        assertEquals("https://golosom.org/author/roman-romanovich", first.getUrlAutor());
        assertEquals("Александр Чистяков", first.getArtist());
        assertEquals("https://golosom.org/performer/aleksandr-chistyakov", first.getUrlArtist());
        assertEquals("Фантастика, фэнтези", first.getGenre());
        assertEquals(GENRE, first.getUrlGenre());
        assertEquals("Ловец знаний", first.getSeries());
        assertEquals("https://golosom.org/series/lovets-znanij", first.getUrlSeries());
        assertEquals("9 ч. 43 мин.", first.getTime());
        BookPOJO twoAuthors = page.items.get(1);
        assertEquals("Сергей Карелин, Александр Вольт", twoAuthors.getAutor());
        assertEquals("https://golosom.org/author/sergej-karelin", twoAuthors.getUrlAutor());
        assertEquals("Сын помещика 13", page.items.get(20).getName());
    }

    @Test
    public void indexLaterPages() throws IOException {
        ProviderPage<BookPOJO> p2 = listing("listing-index-p2.html", GOLOSOM.pageUrl(Golosom.INDEX, 2), 2);
        assertEquals(21, p2.items.size());
        assertTrue(p2.hasMore);
        assertEquals("Призыватель нулевого ранга. Том 10", p2.items.get(0).getName());
        assertEquals("Последний человек из Атлантиды", p2.items.get(20).getName());

        ProviderPage<BookPOJO> last = listing("listing-index-last.html", GOLOSOM.pageUrl(Golosom.INDEX, 4454), 4454);
        assertEquals(17, last.items.size());
        assertFalse(last.hasMore);
        assertEquals("«Записки кавалериста» Избранная лирика", last.items.get(0).getName());
        assertEquals("Болтик", last.items.get(16).getName());
        BookPOJO noAuthor = null;
        for (BookPOJO book : last.items) {
            if (book.getName().startsWith("Сундук со сказками")) {
                noAuthor = book;
            }
        }
        assertNotNull("book 4 has a reader only", noAuthor);
        assertTrue(noAuthor.getAutor() == null || noAuthor.getAutor().isEmpty());
        assertFalse(noAuthor.getArtist().isEmpty());
    }

    @Test
    public void genreListing() throws IOException {
        ProviderPage<BookPOJO> p1 = listing("genre-p1.html", GENRE, 1);
        assertEquals(21, p1.items.size());
        assertTrue(p1.hasMore);
        assertEquals("Злодейка в академии. Сбежать от куратора", p1.items.get(20).getName());
        ProviderPage<BookPOJO> p2 = listing("genre-p2.html", GENRE + "/2", 2);
        assertEquals("Научить любить", p2.items.get(0).getName());
        assertEquals("Последний конвой. Книга 3", p2.items.get(20).getName());
        assertTrue(p2.hasMore);
        ProviderPage<BookPOJO> last = listing("genre-last.html", GENRE + "/1756", 1756);
        assertEquals(1, last.items.size());
        assertEquals("Особый почтовый", last.items.get(0).getName());
        assertFalse(last.hasMore);
    }

    @Test
    public void peopleAndSeriesListings() throws IOException {
        ProviderPage<BookPOJO> author = listing("author.html", Golosom.SERVER + "/author/mihail-bulgakov", 1);
        assertEquals(21, author.items.size());
        assertTrue("5 pages", author.hasMore);
        assertEquals("Собачье сердце", author.items.get(0).getName());
        assertEquals("https://golosom.org/book/sobache-serdtse-13", author.items.get(0).getUrl());
        ProviderPage<BookPOJO> author2 = listing("author-p2.html", Golosom.SERVER + "/author/mihail-bulgakov/2", 2);
        assertEquals(21, author2.items.size());
        assertTrue(author2.hasMore);

        ProviderPage<BookPOJO> reader = listing("reader.html", Golosom.SERVER + "/performer/aleksandr-klyukvin", 1);
        assertEquals(21, reader.items.size());
        assertTrue(reader.hasMore);
        assertEquals("Свадебный переполох в Тенистой Лощине", reader.items.get(0).getName());

        ProviderPage<BookPOJO> series = listing("series.html", Golosom.SERVER + "/series/lovets-znanij", 1);
        assertEquals("the series page has no #BL", 7, series.items.size());
        assertFalse(series.hasMore);
        assertEquals("Ловец знаний", series.items.get(0).getName());
        assertEquals("Ловец знаний. Экспедиция", series.items.get(6).getName());
        ProviderPage<BookPOJO> longSeries = listing("series-long.html", Golosom.SERVER + "/series/x", 1);
        assertEquals(60, longSeries.items.size());
        assertFalse(longSeries.hasMore);
        assertEquals("Маникюр для покойника", longSeries.items.get(0).getName());
        assertEquals("Мамаша Бармалей", longSeries.items.get(59).getName());
    }

    @Test
    public void genres() throws IOException {
        ProviderPage<GenrePOJO> page = GOLOSOM.genres(page("genres.html", Golosom.GENRES), Golosom.GENRES, 1);
        assertEquals(22, page.items.size());
        assertFalse(page.hasMore);
        GenrePOJO first = page.items.get(0);
        assertEquals("Фантастика, фэнтези", first.getName());
        assertEquals(GENRE, first.getUrl());
        assertEquals(44876, first.getReting());
        GenrePOJO last = page.items.get(21);
        assertEquals("Юмор, сатира", last.getName());
        assertEquals("https://golosom.org/genre/yumor-satira", last.getUrl());
    }

    @Test
    public void search() throws IOException {
        String url = GOLOSOM.searchUrl(SearchUrls.Category.BOOKS, "Кинг", 1);
        assertEquals("https://golosom.org/search?q=%D0%9A%D0%B8%D0%BD%D0%B3&page=1", url);
        assertEquals("https://golosom.org/search?q=%D0%9A%D0%B8%D0%BD%D0%B3&page=2",
                GOLOSOM.searchUrl(SearchUrls.Category.BOOKS, "Кинг", 2));
        assertNull(GOLOSOM.searchUrl(SearchUrls.Category.AUTHORS, "Кинг", 1));
        assertNull(GOLOSOM.searchUrl(SearchUrls.Category.PERFORMERS, "Кинг", 1));

        ProviderPage<BookPOJO> p1 = GOLOSOM.searchResults(page("search-king-p1.html", url), 1);
        assertEquals(21, p1.items.size());
        assertTrue(p1.hasMore);
        assertEquals("highlight spans dropped", "Подземелье Кинга. Том I", p1.items.get(0).getName());
        assertEquals("https://golosom.org/book/podzemele-kinga-tom-i", p1.items.get(0).getUrl());
        ProviderPage<BookPOJO> p2 = GOLOSOM.searchResults(page("search-king-p2.html", url), 2);
        assertEquals(21, p2.items.size());
        assertTrue(p2.hasMore);
        assertEquals("Столп огненный", p2.items.get(0).getName());
        assertEquals("Плохой мальчишка", p2.items.get(20).getName());
        ProviderPage<BookPOJO> last = GOLOSOM.searchResults(page("search-king-last.html", url), 15);
        assertEquals(19, last.items.size());
        assertFalse(last.hasMore);
        assertEquals("Потаённый сад, потаённое окно", last.items.get(0).getName());

        ProviderPage<BookPOJO> none = GOLOSOM.searchResults(page("search-none.html", url), 1);
        assertEquals(ProviderPage.Status.EMPTY, none.status);
        assertFalse(none.hasMore);
    }

    @Test
    public void bookPage() throws IOException {
        DescriptionPOJO d = GOLOSOM.description(page("book.html", Golosom.SERVER + "/book/master-i-margarita"),
                Golosom.SERVER + "/book/master-i-margarita");
        assertEquals("Мастер и Маргарита", d.getTitle());
        assertEquals("18 ч. 50 мин.", d.getTime());
        assertEquals("https://r7.audioknigi.xyz/46fdafae8a536b6e/pic/fa108b82818ebaff.jpg", d.getPoster());
        assertEquals("Классика", d.getGenre());
        assertEquals("https://golosom.org/genre/klassika", d.getGenreUrl());
        assertEquals("Михаил Булгаков", d.getAutor());
        assertEquals("https://golosom.org/author/mihail-bulgakov", d.getAutorUrl());
        assertEquals("Александр Теренков", d.getArtist());
        assertTrue(d.getArtistUrl().startsWith("https://golosom.org/performer/"));
        assertTrue(d.getDescription(), d.getDescription().startsWith("Жанровая уникальность «Мастера и Маргариты»"));
        assertTrue(GOLOSOM.series(page("book.html", Golosom.SERVER), Golosom.SERVER).isEmpty());

        BookPOJO book = GOLOSOM.book(page("book.html", Golosom.SERVER + "/book/master-i-margarita"),
                Golosom.SERVER + "/book/master-i-margarita");
        assertFalse(book.isNull());
        assertEquals("Михаил Булгаков", book.getAutor());
    }

    @Test
    public void multiPersonBooks() throws IOException {
        DescriptionPOJO two = Golosom.parseDescription(page("book-two-authors.html", Golosom.SERVER + "/book/lesnoj-dozor-4"));
        assertEquals("desktop row only, no mobile duplicates", "Сергей Карелин, Александр Вольт", two.getAutor());
        assertEquals("https://golosom.org/author/sergej-karelin", two.getAutorUrl());
        assertEquals("Александр Авгур", two.getArtist());

        DescriptionPOJO many = Golosom.parseDescription(
                page("book-many-readers.html", Golosom.SERVER + "/book/master-i-margarita-audiospektakl"));
        String[] readers = many.getArtist().split(", ");
        assertEquals(11, readers.length);
        assertEquals("Ефим Шифрин", readers[0]);
        assertEquals("Ксения Раппопорт", readers[10]);
    }

    @Test
    public void seriesBook() throws IOException {
        Document doc = page("book-series.html", Golosom.SERVER + "/book/lovets-znanij-ekspeditsiya");
        DescriptionPOJO d = Golosom.parseDescription(doc);
        assertEquals("Ловец знаний. Экспедиция", d.getTitle());
        assertEquals("9 ч. 43 мин.", d.getTime());
        assertEquals("https://r7.audioknigi.xyz/a9bb584f4360acab/pic/ecd625c898635efb.jpg", d.getPoster());
        assertEquals("Фантастика, фэнтези", d.getGenre());
        assertEquals("Роман Романович", d.getAutor());
        assertEquals("Александр Чистяков", d.getArtist());
        assertTrue(d.getDescription().startsWith("Бастард Эрано, красавец и ловелас"));

        ArrayList<SeriesPOJO> series = Golosom.parseSeries(doc);
        assertEquals(7, series.size());
        assertEquals("1", series.get(0).getNumber());
        assertEquals("Ловец знаний", series.get(0).getName());
        assertEquals("https://golosom.org/book/lovets-znanij", series.get(0).getUrl());
        assertEquals("7", series.get(6).getNumber());
        assertEquals("the open book, in bold", "Ловец знаний. Экспедиция", series.get(6).getName());
        assertEquals("https://golosom.org/book/lovets-znanij-ekspeditsiya", series.get(6).getUrl());

        assertEquals(4, Golosom.parseSeries(page("book-two-authors.html", Golosom.SERVER)).size());
    }

    @Test
    public void tracks() throws IOException {
        String bookUrl = Golosom.SERVER + "/book/master-i-margarita";
        ArrayList<AudioPOJO> tracks = GOLOSOM.tracks(page("book.html", bookUrl), bookUrl);
        assertEquals(99, tracks.size());
        AudioPOJO first = tracks.get(0);
        assertEquals("Мастер и Маргарита 1", first.getName());
        assertEquals("Мастер и Маргарита", first.getBookName());
        assertEquals("https://r7.audioknigi.xyz/46fdafae8a536b6e/audio/%D0%9C%D0%B0%D1%81%D1%82%D0%B5%D1%80%20"
                + "%D0%B8%20%D0%9C%D0%B0%D1%80%D0%B3%D0%B0%D1%80%D0%B8%D1%82%D0%B0%201.mp3"
                + "?md5=B5gw-Pj99vusonJ6AlXRPQ&expires=1793649102", first.getUrl());
        assertEquals(618, first.getTime());
        assertEquals("Мастер и Маргарита 101", tracks.get(98).getName());
        assertEquals(762, tracks.get(98).getTime());
        int total = 0;
        for (AudioPOJO track : tracks) {
            total += track.getTime();
        }
        assertEquals("the player's duration", 67800, total);

        String seriesUrl = Golosom.SERVER + "/book/lovets-znanij-ekspeditsiya";
        ArrayList<AudioPOJO> series = Golosom.parseTracks(page("book-series.html", seriesUrl), seriesUrl);
        assertEquals(25, series.size());
        assertEquals("Ловец знаний. Экспедиция 01", series.get(0).getName());
        assertEquals("Ловец знаний. Экспедиция 25", series.get(24).getName());
        assertEquals(231, series.get(24).getTime());
    }

    @Test
    public void litresTrialIsRemoved() throws IOException {
        for (String name : new String[]{"book-litres-trial.html", "book-litres-trial-2.html"}) {
            String url = Golosom.SERVER + "/book/" + name;
            try {
                GOLOSOM.tracks(page(name, url), url);
                fail(name + ": a LitRes trial is no book");
            } catch (BookRemovedException expected) {
                assertEquals(url, expected.getMessage());
            }
        }
    }

    @Test
    public void pageWithoutPlayer() throws IOException {
        try {
            Golosom.parseTracks(page("genres.html", Golosom.GENRES), Golosom.GENRES);
            fail("no player");
        } catch (BookRemovedException e) {
            fail("no notice on the page");
        } catch (IllegalStateException expected) {
            // no player: AudioModel turns it into BookRemovedException only when the page shows a notice
        }
    }

    @Test
    public void paging() {
        assertEquals(Golosom.INDEX, GOLOSOM.pageUrl(Golosom.INDEX, 1));
        assertEquals("https://golosom.org/2", GOLOSOM.pageUrl(Golosom.INDEX, 2));
        assertEquals(GENRE, GOLOSOM.pageUrl(GENRE, 1));
        assertEquals(GENRE + "/3", GOLOSOM.pageUrl(GENRE, 3));
        assertEquals("https://golosom.org/performer/aleksandr-klyukvin/2",
                GOLOSOM.pageUrl("https://golosom.org/performer/aleksandr-klyukvin", 2));
        assertEquals("https://golosom.org/search?q=x&page=2", GOLOSOM.pageUrl("https://golosom.org/search?q=x&page=1", 2));
        assertEquals("https://golosom.org/search?q=x&page=3", GOLOSOM.pageUrl("https://golosom.org/search?q=x", 3));
    }

    @Test
    public void noSorts() {
        for (String url : new String[]{Golosom.INDEX, GENRE, Golosom.SERVER + "/author/mihail-bulgakov",
                Golosom.SERVER + "/performer/aleksandr-klyukvin", Golosom.SERVER + "/series/lovets-znanij"}) {
            assertTrue(url, GOLOSOM.sorts(url).isEmpty());
            assertFalse(url, GOLOSOM.hasSort(url));
            assertNull(GOLOSOM.sortUrl(Sort.NEW, url));
            assertNull(GOLOSOM.genreOf(url));
            assertTrue(url, GOLOSOM.listsDuration(url));
        }
    }

    @Test
    public void siteSettings() {
        assertEquals(Collections.singletonMap("lang", "ru"), GOLOSOM.cookies());
        assertNull(GOLOSOM.referer());
        assertTrue("the media CDN is Knigoblud's too", GOLOSOM.hosts().isEmpty());
        assertSame(Provider.GOLOSOM, Provider.ofUrl("https://golosom.org/book/master-i-margarita"));
    }

    @Test
    public void otherReaders() throws IOException {
        BookPOJO book = new BookPOJO();
        book.setName("Мастер и Маргарита");
        book.setAutor("Михаил Булгаков");
        book.setArtist("Александр Теренков");
        assertEquals("Мастер и Маргарита Михаил Булгаков", GOLOSOM.otherSourceTerms(book));
        assertTrue(GOLOSOM.findsOtherReaders());

        ArrayList<OtherArtistPOJO> others = OtherArtistModel.otherReaders(
                GOLOSOM.otherSourceHits(page("other-source-search.html", Golosom.SERVER + "/search?q=x")),
                "Мастер и Маргарита", "Михаил Булгаков", Golosom.SERVER + "/book/master-i-margarita",
                "Александр Теренков");
        assertFalse(others.isEmpty());
        for (OtherArtistPOJO other : others) {
            assertFalse(other.getUrl(), other.getUrl().equals(Golosom.SERVER + "/book/master-i-margarita"));
            assertFalse(other.getName().contains("Теренков"));
        }
    }
}
