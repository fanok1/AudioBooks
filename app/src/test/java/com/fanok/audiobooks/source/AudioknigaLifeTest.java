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
import com.fanok.audiobooks.search.ProviderPage;
import com.fanok.audiobooks.search.SearchUrls;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

/** Pages saved from audiokniga.life on 2026-10-25 (see src/test/resources/fixtures/audiokniga-life). */
public class AudioknigaLifeTest {

    private static final String S = AudioknigaLife.SERVER;
    private static final String BOOK = S + "/fantastika/6508-noldsapfir-2-s-t-i-k-s.html";
    private static final String AUTHOR_VITTE = S + "/xfsearch/avtor/%D0%98%D0%B3%D0%BE%D1%80%D1%8C%20%D0%92%D0%B8%D1%82%D1%82%D0%B5/";

    private static Document fixture(String name, String baseUri) throws IOException {
        try (InputStream in = AudioknigaLifeTest.class.getResourceAsStream("/fixtures/audiokniga-life/" + name)) {
            assertNotNull("missing fixture " + name, in);
            return Jsoup.parse(in, "UTF-8", baseUri);
        }
    }

    private static final AudioknigaLife PARSER = new AudioknigaLife();

    @Test
    public void pagesAndSearch() {
        assertEquals(S + "/page/2/", PARSER.pageUrl(AudioknigaLife.INDEX, 2));
        assertEquals(S + "/", PARSER.pageUrl(AudioknigaLife.INDEX, 1));
        assertEquals(S + "/f/sort=rating/order=desc/page/3/", PARSER.pageUrl(S + "/f/sort=rating/order=desc/", 3));
        assertEquals(S + "/xfsearch/avtor/X/page/2/", PARSER.pageUrl(S + "/xfsearch/avtor/X/", 2));
        assertEquals(S + "/search/%D0%B7%D0%BE%D1%80%D0%B8%20%D0%B7%D0%B4%D0%B5%D1%81%D1%8C/page/1/",
                PARSER.searchUrl(SearchUrls.Category.BOOKS, "зори здесь", 1));
        assertNull(PARSER.searchUrl(SearchUrls.Category.AUTHORS, "x", 1));
        assertFalse(PARSER.hasSort(AUTHOR_VITTE));
        assertTrue(PARSER.hasSort(S + "/fantastika/"));
    }

    @Test
    public void sortsStayInTheGenre() {
        String rating = PARSER.sortUrl(Sort.RATING, AudioknigaLife.INDEX);
        assertEquals(S + "/f/sort=rating/order=desc/", rating);
        assertEquals(AudioknigaLife.INDEX, PARSER.sortUrl(Sort.NEW, rating));
        assertEquals(S + "/f/sort=title/order=asc/", PARSER.sortUrl(Sort.TITLE, AudioknigaLife.INDEX));
        assertNull(PARSER.sortUrl(Sort.POPULAR_WEEK, AudioknigaLife.INDEX));
        // the genre key is the category id, read off the genre root or off a ranking inside it
        assertEquals("9", PARSER.genreOf(S + "/fantastika/"));
        assertEquals("26", PARSER.genreOf(S + "/fantastika/postapokalipsis/page/3/"));
        assertEquals("26", PARSER.genreOf(S + "/f/cat=26/sort=rating/order=desc/page/2/"));
        assertNull(PARSER.genreOf(AudioknigaLife.INDEX));
        assertNull(PARSER.genreOf(rating));
        assertNull(PARSER.genreOf(AUTHOR_VITTE));
        assertEquals(S + "/f/cat=9/sort=rating/order=desc/", PARSER.inGenre(rating, "9"));
        assertEquals(S + "/f/cat=9/sort=date/order=desc/", PARSER.inGenre(AudioknigaLife.INDEX, "9"));
        // a genre the map lacks keeps its own listing for every sort
        String unknown = S + "/novyj-zhanr/";
        assertEquals(unknown, PARSER.genreOf(unknown + "page/2/"));
        assertEquals(unknown, PARSER.inGenre(rating, PARSER.genreOf(unknown)));
    }

    @Test
    public void indexCards() throws IOException {
        ProviderPage<BookPOJO> page = AudioknigaLife.parseBooks(fixture("listing-index-p1.html", S + "/"), 1);
        assertEquals(ProviderPage.Status.OK, page.status);
        assertEquals(16, page.items.size());
        assertTrue("364 pages", page.hasMore);
        BookPOJO first = page.items.get(0);
        assertEquals("Борьба за огонь", first.getName());
        assertEquals(S + "/prikljuchenija/6509-borba-za-ogon.html", first.getUrl());
        assertEquals(S + "/uploads/posts/2026-09/logo_67402.webp", first.getPhoto());
        assertEquals("Приключения", first.getGenre()); // the sidebar calls it "Приключение"
        assertEquals(S + "/prikljuchenija/", first.getUrlGenre());
        assertEquals("Александр Абрамович", first.getArtist());
        assertEquals("6 ч. 37 мин.", first.getTime());
        BookPOJO second = page.items.get(1);
        assertEquals("Игорь Витте", second.getAutor());
        assertEquals(AUTHOR_VITTE, second.getUrlAutor());
        assertEquals("Пекло (Игорь Витте)", second.getSeries());
        assertTrue(second.getUrlSeries(), second.getUrlSeries().startsWith(S + "/xfsearch/serie/"));
    }

    @Test
    public void lastPagesEndTheListing() throws IOException {
        Document rating = fixture("listing-rating-p1.html", S + "/f/sort=rating/order=desc/");
        assertTrue(AudioknigaLife.parseBooks(rating, 363).hasMore);
        assertFalse(AudioknigaLife.parseBooks(rating, 364).hasMore);
        ProviderPage<BookPOJO> reader = AudioknigaLife.parseBooks(fixture("listing-reader-p1.html", S + "/"), 1);
        assertEquals(16, reader.items.size());
        assertTrue("6 pages", reader.hasMore);
    }

    @Test
    public void search() throws IOException {
        ProviderPage<BookPOJO> page = AudioknigaLife.parseBooks(fixture("search-klan-p1.html", S + "/"), 1);
        assertEquals(10, page.items.size());
        assertTrue("7 pages", page.hasMore);
        for (BookPOJO book : page.items) {
            assertFalse(book.getName().isEmpty());
            assertTrue(book.getUrl(), book.getUrl().startsWith(S + "/") && book.getUrl().endsWith(".html"));
        }
        // «Ночной дозор» is not in this catalogue
        Document none = fixture("other-source-search.html", S + "/");
        assertEquals(ProviderPage.Status.EMPTY, AudioknigaLife.parseBooks(none, 1).status);
        assertTrue(PARSER.otherSourceHits(none).isEmpty());
    }

    @Test
    public void genres() throws IOException {
        ArrayList<GenrePOJO> genres = AudioknigaLife.parseGenres(fixture("listing-index-p1.html", S + "/"));
        assertEquals(19, genres.size());
        assertEquals("Фантастика", genres.get(0).getName());
        assertEquals(S + "/fantastika/", genres.get(0).getUrl());
        assertEquals("1507", genres.get(0).getDescription());
        assertEquals("the genre list sorts by it", 1507, genres.get(0).getReting());
        boolean subgenre = false;
        for (GenrePOJO genre : genres) {
            assertTrue(genre.getName() + " has an id", PARSER.genreOf(genre.getUrl()).matches("\\d+"));
            subgenre |= genre.getUrl().equals(S + "/fjentezi/ljubovnoe-fjentezi/");
        }
        assertTrue("a.index_genre is a genre too", subgenre);
    }

    @Test
    public void description() throws IOException {
        DescriptionPOJO d = AudioknigaLife.parseDescription(fixture("book.html", BOOK));
        assertEquals("НОЛД.(Сапфир 2). S-T-I-K-S", d.getTitle());
        assertEquals(S + "/uploads/posts/2026-09/1769836759_logo.webp", d.getPoster());
        assertEquals("Игорь Витте", d.getAutor());
        assertEquals(AUTHOR_VITTE, d.getAutorUrl());
        assertEquals("Coyote", d.getArtist());
        assertEquals(S + "/xfsearch/ispolnitel/Coyote/", d.getArtistUrl());
        assertEquals("Пекло (Игорь Витте)", d.getSeries());
        assertEquals("Фантастика", d.getGenre());
        assertEquals(S + "/fantastika/", d.getGenreUrl());
        assertTrue(d.getTime(), d.getTime().startsWith("14 ч. 5"));
        assertTrue(d.getDescription().startsWith("Молодой ученый ведет двойную жизнь"));
        assertEquals("2", d.getLike());
        assertEquals("0", d.getDisLike());

        BookPOJO book = AudioknigaLife.parseBook(fixture("book.html", BOOK), BOOK);
        assertEquals(BOOK, book.getUrl());
        assertEquals("НОЛД.(Сапфир 2). S-T-I-K-S", book.getName());
        assertEquals("Coyote", book.getArtist());
        assertFalse(book.getDesc().isEmpty());
    }

    @Test
    public void annotationDropsTheChannelPromo() throws IOException {
        DescriptionPOJO d = AudioknigaLife.parseDescription(fixture("book-litres.html", S + "/"));
        assertFalse(d.getTitle().isEmpty());
        assertFalse(d.getDescription(), d.getDescription().contains("телеграмм"));
    }

    @Test
    public void tracks() throws IOException {
        ArrayList<AudioPOJO> tracks = AudioknigaLife.parseTracks(fixture("book.html", BOOK), BOOK);
        assertEquals(26, tracks.size());
        AudioPOJO first = tracks.get(0);
        assertEquals("https://lib4.audiokniga.life/6508/0001.mp3", first.getUrl());
        assertEquals("0001", first.getName());
        assertEquals(344, first.getTime());
        assertEquals("НОЛД.(Сапфир 2). S-T-I-K-S", first.getBookName());
        ArrayList<AudioPOJO> unicode = AudioknigaLife.parseTracks(fixture("comments.html", S + "/"), S + "/");
        assertEquals("Фрагмент 0", unicode.get(0).getName());
    }

    @Test
    public void blockedAndLitresOnlyBooksAreRemoved() throws IOException {
        for (String name : new String[]{"book-blocked.html", "book-litres.html"}) {
            try {
                AudioknigaLife.parseTracks(fixture(name, S + "/"), S + "/" + name);
                fail(name);
            } catch (BookRemovedException expected) {
                // no free audio
            }
        }
    }

    @Test
    public void series() throws IOException {
        assertTrue(AudioknigaLife.parseSeriesUrl(fixture("book.html", BOOK)).startsWith(S + "/xfsearch/serie/"));
        assertEquals("", AudioknigaLife.parseSeriesUrl(fixture("book-litres.html", S + "/")));
        ArrayList<SeriesPOJO> series = AudioknigaLife.parseSeries(fixture("series.html", S + "/"));
        assertEquals(3, series.size());
        assertEquals("1", series.get(0).getNumber());
        assertEquals(S + "/fantastika/6262-s-t-i-k-s-skitalec.html", series.get(0).getUrl());
        assertEquals("3", series.get(2).getNumber());
        assertEquals(BOOK, series.get(2).getUrl());
        assertFalse(series.get(2).getPhoto().isEmpty());
    }

    @Test
    public void commentsNestRepliesOfAnyDepth() throws IOException {
        ArrayList<ComentsPOJO> comments = AudioknigaLife.parseComments(fixture("comments.html", S + "/"));
        assertEquals(2, comments.size());
        ComentsPOJO quoting = comments.get(0);
        assertEquals("Torian", quoting.getName());
        assertEquals("31.03.2026 - 17:29", quoting.getDate());
        assertEquals("Ториан", quoting.getQuoteName());
        assertTrue(quoting.getQuoteText().startsWith("Глава 11 7:07"));
        assertTrue(quoting.getText().startsWith("Лабиринт отражений"));
        assertEquals(S + "/uploads/fotos/foto_2350.webp", quoting.getImage());
        ComentsPOJO thread = comments.get(1);
        assertEquals("Ториан", thread.getName());
        assertEquals(4, thread.getChildComents().size());
        assertEquals("Гость Саня", thread.getChildComents().get(0).getName());
        assertEquals("Ториан", thread.getChildComents().get(0).getParentName());
        assertEquals("он ошибся. десятая глава", thread.getChildComents().get(1).getText());
        assertEquals("Гость Саня", thread.getChildComents().get(1).getParentName());
        assertEquals("Ториан", thread.getChildComents().get(3).getParentName());
    }
}
