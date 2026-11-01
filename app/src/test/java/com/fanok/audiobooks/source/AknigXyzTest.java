package com.fanok.audiobooks.source;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.fanok.audiobooks.BookRemovedException;
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

/** Pages saved from s2.aknig.xyz on 2026-10-25 (see src/test/resources/fixtures/aknig-xyz). */
public class AknigXyzTest {

    private static final String BOOK = AknigXyz.SERVER + "/14731-chetvertoe-krylo.html";

    private static InputStream open(String name) {
        InputStream in = AknigXyzTest.class.getResourceAsStream("/fixtures/aknig-xyz/" + name);
        assertNotNull("missing fixture " + name, in);
        return in;
    }

    private static Document fixture(String name, String baseUri) throws IOException {
        try (InputStream in = open(name)) {
            return Jsoup.parse(in, "UTF-8", baseUri);
        }
    }

    // ---------------------------------------------------------------- URLs

    private final AknigXyz parser = new AknigXyz();

    @Test
    public void pageOneIsTheRootAndLaterPagesAppendPageN() {
        assertEquals(AknigXyz.INDEX, parser.pageUrl(AknigXyz.INDEX, 1));
        assertEquals(AknigXyz.SERVER + "/page/3/", parser.pageUrl(AknigXyz.INDEX, 3));
        assertEquals(AknigXyz.SERVER + "/boeviki/", parser.pageUrl(AknigXyz.SERVER + "/boeviki/page/4/", 1));
        String author = AknigXyz.SERVER + "/xfsearch/author/%D0%B2%D0%B0%20%28%D0%B8%29/";
        assertEquals(author + "page/2/", parser.pageUrl(author, 2));
    }

    @Test
    public void sortItemsArePostedFormsAndPagesKeepThem() {
        String rating = parser.sortUrl(Sort.RATING, AknigXyz.INDEX);
        assertEquals(AknigXyz.SERVER + "/?dlenewssortby=rating&dledirection=desc"
                + "&set_new_sort=dle_sort_main&set_direction_sort=dle_direction_main", rating);
        assertEquals(AknigXyz.SERVER + "/page/2/?dlenewssortby=rating&dledirection=desc"
                + "&set_new_sort=dle_sort_main&set_direction_sort=dle_direction_main", parser.pageUrl(rating, 2));
        assertEquals(rating, parser.sortUrl(Sort.RATING, rating));
        assertEquals("the plain index is already newest first", AknigXyz.INDEX,
                parser.sortUrl(Sort.NEW, AknigXyz.INDEX));
        assertTrue(parser.sortUrl(Sort.TITLE, AknigXyz.INDEX).contains("dlenewssortby=title&dledirection=asc"));
        assertNull(parser.sortUrl(Sort.YEARS, AknigXyz.INDEX));
    }

    @Test
    public void aGenreSortStaysInTheGenreOnceItsIdIsKnown() throws IOException {
        String genre = AknigXyz.SERVER + "/ljubovnoe-fjentezi/";
        assertEquals(genre, parser.genreOf(genre + "page/3/"));
        assertNull(parser.genreOf(AknigXyz.INDEX));
        assertNull(parser.genreOf(AknigXyz.SERVER + "/page/2/"));
        assertNull(parser.genreOf(AknigXyz.SERVER + "/xfsearch/reader/x/"));
        assertNull(parser.genreOf(BOOK));
        String rating = parser.sortUrl(Sort.RATING, genre);
        AknigXyz.learnCategory(genre, fixture("genre-p1.html", genre));
        String inGenre = parser.inGenre(rating, genre);
        assertEquals(genre + "?dlenewssortby=rating&dledirection=desc"
                + "&set_new_sort=dle_sort_cat_20&set_direction_sort=dle_direction_cat_20", inGenre);
        assertEquals(genre, parser.genreOf(inGenre));
        assertTrue(parser.hasSort(inGenre));
    }

    @Test
    public void onlyTheIndexAndGenresSort() {
        assertTrue(parser.hasSort(AknigXyz.INDEX));
        assertTrue(parser.hasSort(parser.sortUrl(Sort.POPULAR, AknigXyz.INDEX)));
        assertTrue(parser.hasSort(AknigXyz.SERVER + "/boeviki/page/2/"));
        assertFalse(parser.hasSort(AknigXyz.SERVER + "/xfsearch/author/x/"));
        assertFalse(parser.hasSort(parser.searchUrl(SearchUrls.Category.BOOKS, "x", 1)));
    }

    @Test
    public void categoryIdComesFromTheGenrePageSortForm() throws IOException {
        assertEquals("20", Dle.categoryId(fixture("genre-p1.html", AknigXyz.SERVER + "/ljubovnoe-fjentezi/")));
        assertNull(Dle.categoryId(fixture("listing-index-p1.html", AknigXyz.INDEX)));
    }

    @Test
    public void searchIsBooksOnly() {
        assertTrue(parser.searchUrl(SearchUrls.Category.BOOKS, "дракон", 2).startsWith(AknigXyz.SERVER
                + "/index.php?do=search&subaction=search&story=%D0%B4%D1%80%D0%B0%D0%BA%D0%BE%D0%BD&search_start=2&"));
        assertNull(parser.searchUrl(SearchUrls.Category.AUTHORS, "x", 1));
    }

    // ---------------------------------------------------------------- listings

    @Test
    public void indexCardsCarryEveryField() throws IOException {
        ProviderPage<BookPOJO> page = AknigXyz.parseListing(fixture("listing-index-p1.html", AknigXyz.INDEX), 1);
        assertEquals(ProviderPage.Status.OK, page.status);
        assertEquals(12, page.items.size());
        assertTrue("2534 pages", page.hasMore);
        BookPOJO first = page.items.get(0);
        assertEquals("для короля драконов", first.getName());
        assertEquals(AknigXyz.SERVER + "/30739-dlja-korolja-drakonov.html", first.getUrl());
        assertEquals(AknigXyz.SERVER + "/uploads/posts/2026-10/68503-dlia-korolia-drakonov.webp", first.getPhoto());
        assertEquals("Боталова Мария", first.getAutor());
        assertTrue(first.getUrlAutor().startsWith(AknigXyz.SERVER + "/xfsearch/author/"));
        assertEquals("Морозова Ирина", first.getArtist());
        assertTrue(first.getUrlArtist().startsWith(AknigXyz.SERVER + "/xfsearch/reader/"));
        assertEquals("Любовное фэнтези", first.getGenre());
        assertEquals(AknigXyz.SERVER + "/ljubovnoe-fjentezi/", first.getUrlGenre());
        assertEquals("Последняя из фениксов", first.getSeries());
        assertTrue(first.getUrlSeries().startsWith(AknigXyz.SERVER + "/xfsearch/serie/"));
        assertEquals("10 ч. 18 мин.", first.getTime());
        assertTrue(first.getDesc().startsWith("Теперь все знают, что я феникс."));
        assertEquals("Ткачев Андрей, Измайлов Сергей", page.items.get(1).getAutor());
        for (BookPOJO book : page.items) {
            assertTrue(book.getUrl(), book.getUrl().startsWith(AknigXyz.SERVER + "/"));
        }
    }

    @Test
    public void lastIndexPageHasNoMore() throws IOException {
        String url = AknigXyz.SERVER + "/page/2534/";
        ProviderPage<BookPOJO> page = AknigXyz.parseListing(fixture("listing-index-last.html", url), 2534);
        assertEquals(8, page.items.size());
        assertFalse(page.hasMore);
    }

    @Test
    public void sortedSecondPageCarriesRatingAndComments() throws IOException {
        String url = AknigXyz.SERVER + "/page/2/";
        ProviderPage<BookPOJO> page = AknigXyz.parseListing(fixture("listing-rating-p2.html", url), 2);
        assertEquals(12, page.items.size());
        assertTrue(page.hasMore);
        assertEquals("Невидимые женщины", page.items.get(0).getName());
        assertEquals("+7", page.items.get(0).getReting());
    }

    @Test
    public void authorAndSeriesListingsAreCardPages() throws IOException {
        ProviderPage<BookPOJO> author = AknigXyz.parseListing(fixture("author.html", AknigXyz.SERVER + "/"), 1);
        assertEquals(8, author.items.size());
        assertFalse(author.hasMore);
        assertEquals("Поиски утраченного завтра", author.items.get(0).getName());
        ProviderPage<BookPOJO> series = AknigXyz.parseListing(fixture("series.html", AknigXyz.SERVER + "/"), 1);
        assertEquals(6, series.items.size());
        assertEquals("Невеста до востребования. Книга 2. Ведьма и Феникс", series.items.get(0).getName());
    }

    @Test
    public void genreListSkipsTheHiddenBanCategory() throws IOException {
        ProviderPage<GenrePOJO> page = AknigXyz.parseGenres(fixture("genres.html", AknigXyz.GENRES));
        assertEquals(35, page.items.size());
        assertEquals("Warhammer 40000", page.items.get(0).getName());
        assertEquals(AknigXyz.SERVER + "/warhammer-40000/", page.items.get(0).getUrl());
        for (GenrePOJO genre : page.items) {
            assertFalse(genre.getName(), genre.getUrl().contains("zablokirovannye"));
        }
    }

    // ---------------------------------------------------------------- search

    @Test
    public void searchGoesOnPastTheCappedCount() throws IOException {
        ProviderPage<BookPOJO> p1 = AknigXyz.parseSearch(fixture("search-drakon-p1.html", AknigXyz.SERVER), 1);
        assertEquals(10, p1.items.size());
        assertTrue(p1.hasMore);
        assertEquals("для короля драконов", p1.items.get(0).getName());
        ProviderPage<BookPOJO> p5 = AknigXyz.parseSearch(fixture("search-drakon-p5.html", AknigXyz.SERVER), 5);
        assertTrue("\"найдено 50\" is a cap, page 6 has hits", p5.hasMore);
    }

    @Test
    public void searchEndsAtAnExactCount() throws IOException {
        ProviderPage<BookPOJO> p2 = AknigXyz.parseSearch(fixture("search-lukyanenko-p2.html", AknigXyz.SERVER), 2);
        assertEquals(10, p2.items.size());
        assertFalse("20 found, 11-20 shown", p2.hasMore);
        ProviderPage<BookPOJO> none = AknigXyz.parseSearch(fixture("search-none.html", AknigXyz.SERVER), 1);
        assertEquals(ProviderPage.Status.EMPTY, none.status);
    }

    @Test
    public void otherSourceSearchHasNoNightWatch() throws IOException {
        ProviderPage<BookPOJO> page = AknigXyz.parseSearch(fixture("other-source-search.html", AknigXyz.SERVER), 1);
        assertEquals(1, page.items.size());
        BookPOJO hit = page.items.get(0);
        assertEquals("Ночная стража, или кто убил капитана Хассельбурга", hit.getName());
        assertFalse(hit.getAutor().isEmpty());
        assertFalse(hit.getArtist().isEmpty());
    }

    // ---------------------------------------------------------------- book page

    @Test
    public void description() throws IOException {
        DescriptionPOJO d = AknigXyz.parseDescription(fixture("book.html", BOOK));
        assertEquals("Четвертое крыло", d.getTitle());
        assertEquals(AknigXyz.SERVER + "/uploads/posts/2024-02/41876-chetvertoe-krylo.webp", d.getPoster());
        assertEquals("Яррос Ребекка", d.getAutor());
        assertTrue(d.getAutorUrl().startsWith(AknigXyz.SERVER + "/xfsearch/author/"));
        assertEquals("Актерский коллектив", d.getArtist());
        assertTrue(d.getArtistUrl().startsWith(AknigXyz.SERVER + "/xfsearch/reader/"));
        assertEquals("Любовное фэнтези", d.getGenre());
        assertEquals(AknigXyz.SERVER + "/ljubovnoe-fjentezi/", d.getGenreUrl());
        assertEquals("Эмпирей", d.getSeries());
        assertTrue(d.getSeriesUrl().startsWith(AknigXyz.SERVER + "/xfsearch/serie/"));
        assertEquals("22 ч. 20 мин.", d.getTime());
        assertEquals("-171", d.getReiting());
        assertTrue(d.getDescription().startsWith("Двадцатилетняя Вайолет Сорренгейл"));
    }

    @Test
    public void bookByLink() throws IOException {
        BookPOJO book = AknigXyz.parseBook(fixture("book.html", BOOK), BOOK);
        assertEquals(BOOK, book.getUrl());
        assertEquals("Четвертое крыло", book.getName());
        assertEquals("Яррос Ребекка", book.getAutor());
        assertEquals("22 ч. 20 мин.", book.getTime());
        assertEquals("Эмпирей", book.getSeries());
    }

    @Test
    public void bookWithoutSeriesHasNoSeriesFields() throws IOException {
        Document doc = fixture("book-removed.html", AknigXyz.SERVER + "/4696-x.html");
        assertEquals("", AknigXyz.parseDescription(doc).getSeries());
        assertTrue(AknigXyz.parseSeries(doc).isEmpty());
    }

    @Test
    public void recommendations() throws IOException {
        ArrayList<BookPOJO> books = AknigXyz.parseRecommendations(fixture("book.html", BOOK));
        assertEquals(5, books.size());
        assertEquals("Требуется муж, или Охота на темного магистра", books.get(0).getName());
        assertEquals(AknigXyz.SERVER + "/2050-trebuetsja-muzh-ili-ohota-na-temnogo-magistra.html",
                books.get(0).getUrl());
        assertEquals("+1", books.get(0).getReting());
    }

    @Test
    public void seriesTabInSiteOrder() throws IOException {
        String url = AknigXyz.SERVER + "/30728-nevesta-do-vostrebovanija-kniga-2-vedma-i-feniks.html";
        ArrayList<SeriesPOJO> series = AknigXyz.parseSeries(fixture("book-series.html", url));
        assertEquals(6, series.size());
        assertEquals("1", series.get(0).getNumber());
        assertEquals("Змеиная невеста", series.get(0).getName());
        assertEquals(AknigXyz.SERVER + "/27392-zmeinaja-nevesta.html", series.get(0).getUrl());
        assertEquals("4", series.get(1).getNumber());
        assertEquals(url, series.get(5).getUrl());
        assertEquals(1, AknigXyz.parseSeries(fixture("book.html", BOOK)).size());
    }

    @Test
    public void commentsOldestFirstWithQuotes() throws IOException {
        ArrayList<ComentsPOJO> comments = AknigXyz.parseComments(fixture("book.html", BOOK));
        assertEquals(10, comments.size());
        assertEquals("Джису", comments.get(0).getName());
        assertEquals("9 июля 2025 11:26", comments.get(0).getDate());
        assertEquals("Лира", comments.get(9).getName());
        assertEquals("Очень понравилось)", comments.get(9).getText());
        boolean quoted = false;
        for (ComentsPOJO c : comments) {
            assertFalse("the book's author block is not a comment", c.getName().contains("Яррос"));
            if (c.getName().equals("Егор")) {
                quoted = true;
                assertEquals("Пëтр", c.getQuoteName());
                assertEquals("Прекрасная книга", c.getQuoteText());
                assertFalse(c.getText().isEmpty());
            }
        }
        assertTrue(quoted);
    }

    // ---------------------------------------------------------------- audio

    @Test
    public void removedBookHasNoPlayerAndCarriesTheNotice() throws IOException {
        Document doc = fixture("book-removed.html", AknigXyz.SERVER + "/4696-x.html");
        try {
            parser.tracks(doc, doc.location());
            fail("no player on a removed book");
        } catch (IllegalStateException expected) {
            assertTrue("AudioModel.load turns this into BookRemovedException", BookRemovedException.isNotice(doc.text()));
        }
    }
}
