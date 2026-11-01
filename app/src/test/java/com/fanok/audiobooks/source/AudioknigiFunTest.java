package com.fanok.audiobooks.source;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.fanok.audiobooks.pojo.AudioPOJO;
import com.fanok.audiobooks.pojo.BookPOJO;
import com.fanok.audiobooks.pojo.ComentsPOJO;
import com.fanok.audiobooks.pojo.DescriptionPOJO;
import com.fanok.audiobooks.pojo.GenrePOJO;
import com.fanok.audiobooks.pojo.SeriesPOJO;
import com.fanok.audiobooks.search.ProviderPage;
import com.fanok.audiobooks.search.SearchUrls;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/** audioknigi.fun pages saved live on 2026-10-25 (src/test/resources/fixtures/audioknigi-fun). */
public class AudioknigiFunTest {

    private static final AudioknigiFun P = new AudioknigiFun();
    private static final String BOOK = AudioknigiFun.SERVER + "/81692-na-vyruchku-yunomu-gassi.html";
    private static final String GENRE = AudioknigiFun.SERVER + "/zhanr/fantastika/";

    private static Document fixture(String name, String baseUri) throws IOException {
        try (InputStream in = AudioknigiFunTest.class.getResourceAsStream("/fixtures/audioknigi-fun/" + name)) {
            assertNotNull("missing fixture " + name, in);
            return Jsoup.parse(in, "UTF-8", baseUri);
        }
    }

    private static String json(String name) throws Exception {
        URL url = AudioknigiFunTest.class.getResource("/fixtures/audioknigi-fun/" + name);
        assertNotNull("missing fixture " + name, url);
        return new String(Files.readAllBytes(Paths.get(url.toURI())), StandardCharsets.UTF_8);
    }

    private static void assertOnSite(List<BookPOJO> books) {
        for (BookPOJO book : books) {
            assertFalse(book.getName().isEmpty());
            assertTrue(book.getUrl(), book.getUrl().startsWith(AudioknigiFun.SERVER + "/"));
        }
    }

    @Test
    public void indexPageOneHasFullCards() throws IOException {
        ProviderPage<BookPOJO> page = AudioknigiFun.parseListing(
                fixture("listing-index-p1.html", AudioknigiFun.INDEX), 1);
        assertEquals(ProviderPage.Status.OK, page.status);
        assertEquals(30, page.items.size());
        assertTrue("2056 pages", page.hasMore);
        BookPOJO first = page.items.get(0);
        assertEquals("Тёмная материя", first.getName());
        assertEquals(AudioknigiFun.SERVER + "/81715-temnaya-materiya.html", first.getUrl());
        assertEquals("Светоч", first.getAutor());
        assertEquals(AudioknigiFun.SERVER + "/author/svetoch/", first.getUrlAutor());
        assertEquals(AudioknigiFun.SERVER + "/read/svetoch/", first.getUrlArtist());
        assertEquals("Фантастика", first.getGenre());
        assertEquals(GENRE, first.getUrlGenre());
        assertEquals("33 мин.", first.getTime());
        assertEquals(AudioknigiFun.SERVER + "/uploads/posts/2026-10/7b88069c_1778189423_1-6.webp", first.getPhoto());
        assertOnSite(page.items);
    }

    @Test
    public void sortedGenrePageAndLastPage() throws IOException {
        ProviderPage<BookPOJO> sorted = AudioknigiFun.parseListing(
                fixture("listing-genre-rating-p2.html", GENRE + "page/2/"), 2);
        assertEquals(30, sorted.items.size());
        assertEquals("Сердце Дракона 4", sorted.items.get(0).getName());
        assertTrue(sorted.hasMore);

        ProviderPage<BookPOJO> last = AudioknigiFun.parseListing(fixture("listing-author-last.html",
                AudioknigiFun.SERVER + "/author/ajzek-azimov/page/7/"), 7);
        assertEquals(20, last.items.size());
        assertFalse(last.hasMore);
        assertEquals("Айзек Азимов", last.items.get(0).getAutor());
    }

    @Test
    public void searchPagesAndOtherSourceHits() throws IOException {
        String p1 = P.searchUrl(SearchUrls.Category.BOOKS, "Ночной дозор", 1);
        ProviderPage<BookPOJO> first = P.searchResults(fixture("other-source-search.html", p1), 1);
        assertEquals(10, first.items.size());
        assertEquals("Тайна Ночного дозора", first.items.get(0).getName());
        assertTrue("15 hits over 2 pages", first.hasMore);
        assertOnSite(first.items);
        assertNull(P.searchUrl(SearchUrls.Category.AUTHORS, "x", 1));

        String p2 = P.searchUrl(SearchUrls.Category.BOOKS, "Ночной дозор", 2);
        ProviderPage<BookPOJO> second = P.searchResults(fixture("search-nochnoy-dozor-p2.html", p2), 2);
        assertEquals(5, second.items.size());
        assertFalse(second.hasMore);
    }

    @Test
    public void sortsArePostedWithTheListingsSessionKey() {
        String main = AudioknigiFun.sorted(AudioknigiFun.INDEX, Dle.Order.RATING);
        assertEquals(AudioknigiFun.SERVER + "/?dlenewssortby=rating&dledirection=desc"
                + "&set_new_sort=dle_sort_main&set_direction_sort=dle_direction_main", main);
        assertEquals(AudioknigiFun.SERVER + "/page/2/?dlenewssortby=rating&dledirection=desc"
                + "&set_new_sort=dle_sort_main&set_direction_sort=dle_direction_main", P.pageUrl(main, 2));
        assertEquals(AudioknigiFun.INDEX, P.pageUrl(AudioknigiFun.SERVER + "/page/3/", 1));
        String genre = AudioknigiFun.sorted(GENRE, Dle.Order.TITLE);
        assertTrue(genre, genre.startsWith(GENRE + "?dlenewssortby=title&dledirection=asc&set_new_sort=dle_sort_xfsearch"));
        assertTrue(AudioknigiFun.sorted(AudioknigiFun.SERVER + "/podborki/russkie-detektivy/page/3/", Dle.Order.VIEWS)
                .contains("dlenewssortby=news_read&dledirection=desc&set_new_sort=dle_sort_selection"));
        assertNull(AudioknigiFun.sorted(AudioknigiFun.TOP_100, Dle.Order.RATING));
        assertNull(AudioknigiFun.sorted(P.searchUrl(SearchUrls.Category.BOOKS, "ночь", 1), Dle.Order.RATING));
        assertFalse(P.hasSort(AudioknigiFun.TOP_100));
        assertTrue(P.hasSort(GENRE + "page/2/"));
    }

    @Test
    public void menuSortsAreSiteWideAndStayInTheGenreOrAuthor() {
        assertEquals(AudioknigiFun.INDEX, P.sortUrl(Sort.NEW, AudioknigiFun.INDEX));
        String popular = P.sortUrl(Sort.POPULAR, AudioknigiFun.INDEX);
        assertEquals(AudioknigiFun.sorted(AudioknigiFun.INDEX, Dle.Order.VIEWS), popular);
        assertEquals(AudioknigiFun.sorted(AudioknigiFun.INDEX, Dle.Order.RATING), P.sortUrl(Sort.RATING, GENRE));
        assertEquals(AudioknigiFun.sorted(AudioknigiFun.INDEX, Dle.Order.COMMENTS), P.sortUrl(Sort.COMMENTS, GENRE));
        assertEquals(AudioknigiFun.sorted(AudioknigiFun.INDEX, Dle.Order.TITLE), P.sortUrl(Sort.TITLE, GENRE));
        assertNull(P.sortUrl(Sort.YEARS, GENRE));

        String sortedGenre = AudioknigiFun.sorted(GENRE + "page/3/", Dle.Order.RATING);
        assertEquals(GENRE, P.genreOf(sortedGenre));
        assertEquals(AudioknigiFun.sorted(GENRE, Dle.Order.VIEWS), P.inGenre(popular, P.genreOf(sortedGenre)));
        assertEquals(AudioknigiFun.sorted(GENRE, Dle.Order.DATE), P.inGenre(AudioknigiFun.INDEX, GENRE));
        String author = AudioknigiFun.SERVER + "/author/ajzek-azimov/";
        assertEquals(author, P.genreOf(author + "page/2/"));
        assertNull(P.genreOf(AudioknigiFun.INDEX));
        assertNull(P.genreOf(popular));
        assertNull(P.genreOf(BOOK));
    }

    @Test
    public void plainListingsAreFetchedInTheDateSort() {
        String author = AudioknigiFun.SERVER + "/author/ajzek-azimov/page/2/";
        assertEquals(AudioknigiFun.sorted(author, Dle.Order.DATE), AudioknigiFun.fetchUrl(author));
        String sorted = AudioknigiFun.sorted(author, Dle.Order.COMMENTS);
        assertEquals(sorted, AudioknigiFun.fetchUrl(sorted));
        String search = P.searchUrl(SearchUrls.Category.BOOKS, "ночь", 1);
        assertEquals(search, AudioknigiFun.fetchUrl(search));
        assertEquals(AudioknigiFun.TOP_100, AudioknigiFun.fetchUrl(AudioknigiFun.TOP_100));
        assertEquals(BOOK, AudioknigiFun.fetchUrl(BOOK));
    }

    @Test
    public void genreAndCollectionIndexes() throws IOException {
        ProviderPage<GenrePOJO> genres = AudioknigiFun.parseIndex(fixture("genres.html", AudioknigiFun.GENRES));
        assertEquals(57, genres.items.size());
        assertFalse(genres.hasMore);
        assertEquals("Фантастика", genres.items.get(0).getName());
        assertEquals(GENRE, genres.items.get(0).getUrl());
        assertEquals("16880 книг", genres.items.get(0).getDescription());

        ProviderPage<GenrePOJO> collections = AudioknigiFun.parseIndex(
                fixture("collections.html", AudioknigiFun.COLLECTIONS));
        assertEquals(48, collections.items.size());
        assertEquals("Аудиокниги про зомби", collections.items.get(0).getName());
        assertEquals(AudioknigiFun.SERVER + "/podborki/audioknigi-pro-zombi/", collections.items.get(0).getUrl());
        assertEquals("822 книг", collections.items.get(0).getDescription());
    }

    @Test
    public void descriptionAndDeepLinkBook() throws IOException {
        Document doc = fixture("book.html", BOOK);
        DescriptionPOJO description = AudioknigiFun.parseDescription(doc);
        assertEquals("На выручку юному Гасси", description.getTitle());
        assertEquals("Пэлем Грэнвил Вудхауз", description.getAutor());
        assertEquals(AudioknigiFun.SERVER + "/author/pjelem-grjenvil-vudhauz/", description.getAutorUrl());
        assertEquals("Анна Прудникова", description.getArtist());
        assertEquals(AudioknigiFun.SERVER + "/read/anna-prudnikova/", description.getArtistUrl());
        assertEquals("Юмор", description.getGenre());
        assertEquals(AudioknigiFun.SERVER + "/zhanr/jumor/", description.getGenreUrl());
        assertEquals("Дживс и Вустер", description.getSeries());
        assertEquals(AudioknigiFun.SERVER + "/cikl/dzhivs-i-vuster/", description.getSeriesUrl());
        assertEquals("41 мин.", description.getTime());
        assertEquals(AudioknigiFun.SERVER + "/uploads/posts/2026-10/b40a13ad_1713821937_1-22x.webp",
                description.getPoster());
        assertTrue(description.getDescription(), description.getDescription().startsWith("Ваш кузен Огастус"));

        BookPOJO book = AudioknigiFun.parseBook(doc, BOOK);
        assertEquals(BOOK, book.getUrl());
        assertEquals("На выручку юному Гасси", book.getName());
        assertEquals(AudioknigiFun.SERVER + "/cikl/dzhivs-i-vuster/", book.getUrlSeries());
        assertEquals("41 мин.", book.getTime());
        assertEquals(description.getDescription(), book.getDesc());
    }

    @Test
    public void recommendations() throws IOException {
        ArrayList<BookPOJO> books = AudioknigiFun.parseRecommendations(fixture("book.html", BOOK));
        assertEquals(5, books.size());
        assertEquals("Сборник радиоспектаклей №10. Агата Кристи", books.get(0).getName());
        assertEquals("Агата Кристи", books.get(0).getAutor());
        assertEquals(AudioknigiFun.SERVER + "/75028-sbornik-radiospektakley-10-agata-kristi.html", books.get(0).getUrl());
        assertOnSite(books);
    }

    @Test
    public void seriesOfTheBook() throws IOException {
        String series = AudioknigiFun.seriesUrl(fixture("book.html", BOOK));
        assertEquals(AudioknigiFun.SERVER + "/cikl/dzhivs-i-vuster/", series);
        ArrayList<SeriesPOJO> books = AudioknigiFun.parseSeries(fixture("series.html", series));
        assertEquals(22, books.size());
        assertEquals("На выручку юному Гасси", books.get(0).getName());
        assertEquals(BOOK, books.get(0).getUrl());
        assertFalse(books.get(0).getPhoto().isEmpty());
        assertEquals("", AudioknigiFun.seriesUrl(Jsoup.parse("<h1>x</h1>", AudioknigiFun.SERVER)));
    }

    @Test
    public void inlineComments() throws IOException {
        ArrayList<ComentsPOJO> comments = AudioknigiFun.parseComments(
                fixture("comments.html", AudioknigiFun.SERVER + "/36759-zadacha-vyghity.html"));
        assertEquals(5, comments.size());
        assertEquals("Нина Сединина", comments.get(0).getName());
        assertEquals("5 декабря 2024 20:12", comments.get(0).getDate());
        assertTrue(comments.get(0).getText(), comments.get(0).getText().startsWith("Не логичные поступки гг."));
        assertEquals(AudioknigiFun.SERVER + "/templates/audiobooks/dleimages/noavatar.png", comments.get(0).getImage());
        assertTrue(AudioknigiFun.parseComments(fixture("book.html", BOOK)).isEmpty());
    }

    @Test
    public void signedPlaylistIsUnescapedOnce() throws Exception {
        assertEquals(AudioknigiFun.SERVER + "/engine/ajax/controller.php?mod=audioplaylist&newsid=81692",
                AudioknigiFun.playlistUrl(fixture("book.html", BOOK)));
        ArrayList<AudioPOJO> chapters = AudioknigiFun.parseTracks(json("playlist.json"), "На выручку юному Гасси");
        assertEquals(1, chapters.size());
        assertEquals("Глава 1", chapters.get(0).getName());
        assertEquals("На выручку юному Гасси", chapters.get(0).getBookName());
        assertEquals("https://s5.vozknig.com/uploads/files/2026-10/13604-na-vyruchku-junomu-gassi-html/01_97414.mp3"
                + "?site=fun&md5=HD3UbgecGUVmlSH0yltS-g&expires=1793026222", chapters.get(0).getUrl());
    }

    @Test
    public void plainPlaylistAndErrors() throws Exception {
        ArrayList<AudioPOJO> chapters = AudioknigiFun.parseTracks(json("playlist-plain.json"), "Корректировщик");
        assertEquals(8, chapters.size());
        assertEquals("Глава 1", chapters.get(0).getName());
        assertEquals("Глава 8", chapters.get(7).getName());
        assertEquals("https://s5.vozknig.com/korrektirovschik-1-blickriga-ne-budet/01.mp3", chapters.get(0).getUrl());
        assertEquals("https://s5.vozknig.com/korrektirovschik-1-blickriga-ne-budet/08.mp3", chapters.get(7).getUrl());
        try {
            AudioknigiFun.parseTracks("{\"ok\":false,\"error\":\"not_found\"}", "x");
            throw new AssertionError("an error answer has no chapters");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("not_found"));
        }
        assertEquals("", AudioknigiFun.playlistUrl(Jsoup.parse("<h1>x</h1>", AudioknigiFun.SERVER)));
    }
}
