package com.fanok.audiobooks.source;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.fanok.audiobooks.BookRemovedException;
import com.fanok.audiobooks.Consts;
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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Pages saved from bookish.site on 2026-10-31 (see src/test/resources/fixtures/bookish). */
public class BookishTest {

    private static final String S = Bookish.SERVER;
    private static final String BOOK = S + "/137312-igra-po-raspisaniju-baldachchi-djevid.html";
    private static final String SERIES_BOOK = S + "/145424-revizor-vozvraschenie-v-sssr-65.html";
    private static final String GENRE = S + "/fantastiikka/";
    private static final String AUTHOR = S + "/xfsearch/avtor/%D0%A8%D0%B8%D1%80%D0%BE%D0%BA%D0%BE%D0%B2"
            + "%20%D0%90%D0%BB%D0%B5%D0%BA%D1%81%D0%B5%D0%B9/";

    private final Bookish parser = new Bookish();

    private static InputStream open(String name) {
        InputStream in = BookishTest.class.getResourceAsStream("/fixtures/bookish/" + name);
        assertNotNull("missing fixture " + name, in);
        return in;
    }

    private static Document fixture(String name, String baseUri) throws IOException {
        try (InputStream in = open(name)) {
            return Jsoup.parse(in, "UTF-8", baseUri);
        }
    }

    private static String text(String name) throws IOException {
        try (InputStream in = open(name)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            for (int n; (n = in.read(buffer)) > 0; ) {
                out.write(buffer, 0, n);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    /** The site's duration as the app shows it. */
    private static String time(String printed) {
        return Consts.formatTimeDisplay(printed);
    }

    private static String search(int page) {
        return new Bookish().searchUrl(SearchUrls.Category.BOOKS, "Кинг", page);
    }

    // ---------------------------------------------------------------- URLs and sorts

    @Test
    public void pageOneIsTheRootAndLaterPagesAppendPageN() {
        assertEquals(Bookish.INDEX, parser.pageUrl(Bookish.INDEX, 1));
        assertEquals(S + "/page/2/", parser.pageUrl(Bookish.INDEX, 2));
        assertEquals(GENRE + "page/3/", parser.pageUrl(GENRE, 3));
        assertEquals(GENRE, parser.pageUrl(GENRE + "page/4/", 1));
        assertEquals(AUTHOR + "page/2/", parser.pageUrl(AUTHOR, 2));
    }

    @Test
    public void sortItemsArePostedFormsAndPagesKeepThem() {
        String rating = parser.sortUrl(Sort.RATING, Bookish.INDEX);
        assertEquals(S + "/?dlenewssortby=rating&dledirection=desc"
                + "&set_new_sort=dle_sort_main&set_direction_sort=dle_direction_main", rating);
        assertEquals(S + "/page/2/?dlenewssortby=rating&dledirection=desc"
                + "&set_new_sort=dle_sort_main&set_direction_sort=dle_direction_main", parser.pageUrl(rating, 2));
        assertEquals(rating, parser.sortUrl(Sort.RATING, rating));
        assertTrue(parser.sortUrl(Sort.POPULAR, Bookish.INDEX).contains("dlenewssortby=news_read&dledirection=desc"));
        assertTrue(parser.sortUrl(Sort.COMMENTS, Bookish.INDEX).contains("dlenewssortby=comm_num&dledirection=desc"));
        assertTrue(parser.sortUrl(Sort.TITLE, Bookish.INDEX).contains("dlenewssortby=title&dledirection=asc"));
        assertEquals("new is the bare index, POSTed in the date sort", Bookish.INDEX,
                parser.sortUrl(Sort.NEW, rating));
        assertNull(parser.sortUrl(Sort.YEARS, Bookish.INDEX));
        assertNull(parser.sortUrl(Sort.POPULAR_WEEK, Bookish.INDEX));
    }

    @Test
    public void indexGenresAndPeopleSortTheSearchDoesNot() {
        List<Sort> menu = Arrays.asList(Sort.NEW, Sort.POPULAR, Sort.RATING, Sort.COMMENTS, Sort.TITLE);
        for (String url : new String[]{Bookish.INDEX, S + "/page/7/", parser.sortUrl(Sort.TITLE, Bookish.INDEX),
                GENRE, GENRE + "page/2/", AUTHOR, S + "/xfsearch/cikl/%D0%98%D0%B3%D0%BD%D0%B8%D1%81/"}) {
            List<Sort.Entry> entries = parser.sorts(url);
            assertEquals(url, menu.size(), entries.size());
            for (int i = 0; i < menu.size(); i++) {
                assertEquals(url, menu.get(i), entries.get(i).sort);
            }
        }
        assertFalse(parser.hasSort(search(1)));
        assertFalse(parser.hasSort(BOOK));
        assertTrue(parser.periods(Bookish.INDEX).isEmpty());
    }

    @Test
    public void aGenreSortStaysInTheGenreOnceItsIdIsKnown() throws IOException {
        assertEquals(GENRE, parser.genreOf(GENRE + "page/3/"));
        assertNull(parser.genreOf(Bookish.INDEX));
        assertNull(parser.genreOf(S + "/page/2/"));
        assertNull(parser.genreOf(BOOK));
        assertNull(parser.genreOf(search(1)));
        assertNull(parser.genreOf("https://other.site/fantastiikka/"));
        assertEquals("42", Dle.categoryId(fixture("genre-p1.html", GENRE)));
        assertNull(Dle.categoryId(fixture("listing-index-p1.html", Bookish.INDEX)));
        String rating = parser.sortUrl(Sort.RATING, GENRE);
        Bookish.learnCategory(GENRE, fixture("genre-p1.html", GENRE));
        String inGenre = parser.inGenre(rating, GENRE);
        assertEquals(GENRE + "?dlenewssortby=rating&dledirection=desc"
                + "&set_new_sort=dle_sort_cat_42&set_direction_sort=dle_direction_cat_42", inGenre);
        assertEquals(GENRE, parser.genreOf(inGenre));
        assertEquals(GENRE + "page/2/?dlenewssortby=rating&dledirection=desc"
                + "&set_new_sort=dle_sort_cat_42&set_direction_sort=dle_direction_cat_42", parser.pageUrl(inGenre, 2));
        assertEquals(GENRE, parser.genreOf(parser.pageUrl(inGenre, 2)));
        assertTrue(parser.hasSort(inGenre));
        assertEquals("new in a genre is the genre itself", GENRE,
                parser.inGenre(parser.sortUrl(Sort.NEW, GENRE), GENRE));
        String unknown = S + "/proza/";
        assertEquals("site-wide until the genre's id is learnt", rating, parser.inGenre(rating, unknown));
    }

    @Test
    public void peoplePagesSortUnderTheXfsearchKey() {
        assertEquals(AUTHOR, parser.genreOf(AUTHOR + "page/2/"));
        String comments = parser.inGenre(parser.sortUrl(Sort.COMMENTS, AUTHOR), AUTHOR);
        assertEquals(AUTHOR + "?dlenewssortby=comm_num&dledirection=desc"
                + "&set_new_sort=dle_sort_xfsearch&set_direction_sort=dle_direction_xfsearch", comments);
        assertEquals(AUTHOR, parser.genreOf(comments));
        assertEquals(AUTHOR, parser.inGenre(parser.sortUrl(Sort.NEW, AUTHOR), AUTHOR));
    }

    @Test
    public void searchIsBooksOnlyNewestFirst() {
        assertEquals(S + "/index.php?do=search&subaction=search&story=%D0%9A%D0%B8%D0%BD%D0%B3&search_start=2"
                + "&result_from=11&full_search=0&sortby=date&resorder=desc", search(2));
        assertNull(parser.searchUrl(SearchUrls.Category.AUTHORS, "x", 1));
        assertNull(parser.searchUrl(SearchUrls.Category.PERFORMERS, "x", 1));
    }

    // ---------------------------------------------------------------- listings

    @Test
    public void indexCardsCarryEveryField() throws IOException {
        ProviderPage<BookPOJO> page = Bookish.parseListing(fixture("listing-index-p1.html", Bookish.INDEX), 1);
        assertEquals(ProviderPage.Status.OK, page.status);
        assertEquals(16, page.items.size());
        assertTrue("5620 pages", page.hasMore);
        BookPOJO first = page.items.get(0);
        assertEquals(S + "/145849-dolg-klana-shirokov-aleksei.html", first.getUrl());
        assertEquals("Долг Клана", first.getName());
        assertEquals(S + "/uploads/posts/2026-10/1793426289_3368e0c3711000f5.jpg", first.getPhoto());
        assertEquals("Широков Алексей", first.getAutor());
        assertEquals(AUTHOR, first.getUrlAutor());
        assertEquals("Обухов Михаил", first.getArtist());
        assertEquals(S + "/xfsearch/chitaet/%D0%9E%D0%B1%D1%83%D1%85%D0%BE%D0%B2%20%D0%9C%D0%B8%D1%85%D0%B0%D0%B8%D0%BB/",
                first.getUrlArtist());
        assertEquals(time("06:24:33"), first.getTime());
        assertEquals("Игнис", first.getSeries());
        assertEquals(S + "/xfsearch/cikl/%D0%98%D0%B3%D0%BD%D0%B8%D1%81/", first.getUrlSeries());
        assertEquals("new books have no votes", "0", first.getReting());
        BookPOJO second = page.items.get(1);
        assertEquals("Война клана 2", second.getName());
        assertEquals("Шапочкин Александр, Широков Алексей", second.getAutor());
        BookPOJO last = page.items.get(15);
        assertTrue(last.getUrl().startsWith(S + "/145822-"));
        assertEquals("Просвещенный наследник 2", last.getName());
        assertEquals(time("09:45:21"), last.getTime());
    }

    @Test
    public void laterAndLastPages() throws IOException {
        ProviderPage<BookPOJO> p2 = Bookish.parseListing(fixture("listing-index-p2.html", S + "/page/2/"), 2);
        assertEquals(16, p2.items.size());
        assertTrue(p2.hasMore);
        assertTrue(p2.items.get(0).getUrl().startsWith(S + "/145821-"));
        assertEquals("Жизнь сурка 8. Джанго перерождённый. Том 2", p2.items.get(0).getName());
        assertEquals("noslnosl", p2.items.get(0).getAutor());
        assertTrue(p2.items.get(15).getUrl().startsWith(S + "/145806-"));

        ProviderPage<BookPOJO> last = Bookish.parseListing(fixture("listing-index-last.html", S + "/page/5620/"),
                5620);
        assertEquals(11, last.items.size());
        assertFalse(last.hasMore);
        assertTrue(last.items.get(0).getUrl().startsWith(S + "/29-"));
        assertEquals("Author - Title order", "Принц и маг", last.items.get(0).getName());
        assertEquals("Фаулз Джон", last.items.get(0).getAutor());
        assertTrue(last.items.get(10).getUrl().startsWith(S + "/7-"));
        assertEquals("Домино", last.items.get(10).getName());
    }

    @Test
    public void sortedListingsShowTheirOrder() throws IOException {
        ProviderPage<BookPOJO> rating = Bookish.parseListing(fixture("listing-rating-p1.html", Bookish.INDEX), 1);
        assertEquals(16, rating.items.size());
        assertTrue(rating.items.get(0).getUrl().startsWith(S + "/28420-"));
        assertEquals("Бандит", rating.items.get(0).getName());
        assertEquals("4.0", rating.items.get(0).getReting());
        assertTrue(rating.items.get(15).getUrl().startsWith(S + "/39289-"));
        assertTrue(Bookish.parseListing(fixture("listing-views-p1.html", Bookish.INDEX), 1).items.get(0).getUrl()
                .startsWith(S + "/39290-"));
        assertTrue(Bookish.parseListing(fixture("listing-comments-p1.html", Bookish.INDEX), 1).items.get(0)
                .getUrl().startsWith(S + "/39285-"));
        ProviderPage<BookPOJO> comments2 = Bookish.parseListing(fixture("listing-comments-p2.html", S + "/page/2/"),
                2);
        assertTrue(comments2.hasMore);
        assertTrue(comments2.items.get(0).getUrl().startsWith(S + "/28607-"));
        BookPOJO title = Bookish.parseListing(fixture("listing-title-p1.html", Bookish.INDEX), 1).items.get(0);
        assertTrue(title.getUrl().startsWith(S + "/32613-"));
        assertEquals("a leading no-break space is dropped", "Перстень Веневитинова", title.getName());
    }

    @Test
    public void genrePages() throws IOException {
        ProviderPage<BookPOJO> p1 = Bookish.parseListing(fixture("genre-p1.html", GENRE), 1);
        assertEquals(16, p1.items.size());
        assertTrue(p1.hasMore);
        assertTrue(p1.items.get(0).getUrl().startsWith(S + "/145849-"));
        assertTrue(p1.items.get(15).getUrl().startsWith(S + "/145805-"));
        assertEquals("Машина времени", p1.items.get(15).getName());
        ProviderPage<BookPOJO> p2 = Bookish.parseListing(fixture("genre-p2.html", GENRE + "page/2/"), 2);
        assertTrue(p2.hasMore);
        assertEquals("Марсианин", p2.items.get(0).getName());
        assertEquals("Брэдбери Рэй", p2.items.get(0).getAutor());
        assertEquals(time("00:35:32"), p2.items.get(0).getTime());
        ProviderPage<BookPOJO> rating = Bookish.parseListing(fixture("genre-rating-p1.html", GENRE), 1);
        assertTrue(rating.items.get(0).getUrl().startsWith(S + "/28420-"));
        assertTrue(rating.items.get(15).getUrl().startsWith(S + "/24265-"));
        // /audiospektakl/ by views: a card without an "Автор:" row, a "00:41::47" duration
        List<BookPOJO> views = Bookish.parseListing(fixture("genre-views-p1.html", S + "/audiospektakl/"), 1).items;
        assertEquals(16, views.size());
        assertEquals("Отечественный дамский детектив", views.get(7).getName());
        assertNull(views.get(7).getAutor());
        assertEquals("Мёртвое пианино", views.get(8).getName());
        assertEquals(time("0:41:47"), views.get(8).getTime());
    }

    @Test
    public void peopleAndSeriesPages() throws IOException {
        ProviderPage<BookPOJO> author = Bookish.parseListing(fixture("author.html", AUTHOR), 1);
        assertEquals(16, author.items.size());
        assertTrue("3 pages", author.hasMore);
        assertTrue(author.items.get(0).getUrl().startsWith(S + "/145849-"));
        assertTrue(author.items.get(15).getUrl().startsWith(S + "/104297-"));
        assertEquals("Сонный воин 2", author.items.get(15).getName());
        ProviderPage<BookPOJO> reader = Bookish.parseListing(fixture("reader.html", S + "/xfsearch/chitaet/x/"), 1);
        assertEquals(16, reader.items.size());
        assertTrue("13 pages", reader.hasMore);
        ProviderPage<BookPOJO> series = Bookish.parseListing(fixture("series.html", S + "/xfsearch/cikl/x/"), 1);
        assertEquals(10, series.items.size());
        assertFalse(series.hasMore);
    }

    @Test
    public void genreListIsTheMenuAndTheHiddenCategories() throws IOException {
        ProviderPage<GenrePOJO> genres = Bookish.parseGenres(fixture("listing-index-p1.html", Bookish.INDEX));
        assertFalse(genres.hasMore);
        assertEquals(32 + 13, genres.items.size());
        assertEquals("Аудиоспектакль", genres.items.get(0).getName());
        assertEquals(S + "/audiospektakl/", genres.items.get(0).getUrl());
        assertEquals("S.T.A.L.K.E.R.", genres.items.get(12).getName());
        assertEquals("Фантастика", genres.items.get(28).getName());
        assertEquals(GENRE, genres.items.get(28).getUrl());
        assertEquals("Искусственный голос", genres.items.get(31).getName());
        assertEquals("Античность", genres.items.get(32).getName());
        assertEquals("Проза", genres.items.get(40).getName());
        assertEquals(S + "/proza/", genres.items.get(40).getUrl());
        assertEquals("Хоррор", genres.items.get(44).getName());
        for (GenrePOJO genre : genres.items) {
            assertEquals(genre.getUrl(), genre.getUrl(), parser.genreOf(genre.getUrl()));
        }
    }

    // ---------------------------------------------------------------- search

    @Test
    public void searchPagesGoOnPastTheCappedPager() throws IOException {
        ProviderPage<BookPOJO> p1 = parser.searchResults(fixture("search-king-p1.html", search(1)), 1);
        assertEquals(16, p1.items.size());
        assertTrue(p1.hasMore);
        assertTrue(p1.items.get(0).getUrl().startsWith(S + "/145663-"));
        assertEquals("Поле боя", p1.items.get(0).getName());
        assertEquals("Кинг Стивен", p1.items.get(0).getAutor());
        assertEquals(time("00:24:44"), p1.items.get(0).getTime());
        assertTrue(p1.items.get(15).getUrl().startsWith(S + "/137654-"));
        ProviderPage<BookPOJO> p2 = parser.searchResults(fixture("search-king-p2.html", search(2)), 2);
        assertEquals(16, p2.items.size());
        assertTrue(p2.hasMore);
        assertTrue(p2.items.get(0).getUrl().startsWith(S + "/137653-"));
        assertEquals(time("05:40:12"), p2.items.get(0).getTime());
        ProviderPage<BookPOJO> last = parser.searchResults(fixture("search-king-last.html", search(22)), 22);
        assertEquals(11, last.items.size());
        assertFalse(last.hasMore);
        assertTrue(last.items.get(0).getUrl().startsWith(S + "/1275-"));
        assertEquals("Очень тесное место", last.items.get(0).getName());
        assertTrue(last.items.get(10).getUrl().startsWith(S + "/139-"));
        assertEquals(time("57 мин."), last.items.get(10).getTime());
        assertEquals(ProviderPage.Status.EMPTY,
                parser.searchResults(fixture("search-king-beyond.html", search(23)), 23).status);
    }

    @Test
    public void searchWithoutResultsIsEmpty() throws IOException {
        ProviderPage<BookPOJO> none = parser.searchResults(fixture("search-none.html",
                parser.searchUrl(SearchUrls.Category.BOOKS, "zzzxqw", 1)), 1);
        assertEquals(ProviderPage.Status.EMPTY, none.status);
        assertFalse(none.hasMore);
    }

    @Test
    public void otherSourceHitsCarryTitleAndAuthor() throws IOException {
        ProviderPage<BookPOJO> hits = parser.searchResults(fixture("other-source-search.html",
                parser.otherSourceSearchUrl("Мастер и Маргарита Булгаков", 1)), 1);
        assertEquals(16, hits.items.size());
        assertTrue(hits.items.get(0).getUrl().startsWith(S + "/138823-"));
        BookPOJO last = hits.items.get(15);
        assertTrue(last.getUrl().startsWith(S + "/12852-"));
        assertEquals("Булгаков Михаил", last.getAutor());
        assertEquals("Две ранние редакции", last.getName());
        boolean days = false;
        for (BookPOJO book : hits.items) {
            days |= time("255:50:44").equals(book.getTime());
        }
        assertTrue("10 дн. 15:50:44 in hours", days);
    }

    // ---------------------------------------------------------------- book page

    @Test
    public void seriesBookDescription() throws IOException {
        DescriptionPOJO d = Bookish.parseDescription(fixture("book-series.html", SERIES_BOOK));
        assertEquals("Ревизор: возвращение в СССР 65", d.getTitle());
        assertEquals(S + "/uploads/posts/2026-10/1793076928_rv4k867o93.jpg", d.getPoster());
        assertEquals("Винтеркей Серж, Шумилин Артем", d.getAutor());
        assertTrue(d.getAutorUrl().startsWith(S + "/xfsearch/avtor/%D0%92"));
        assertEquals("Уделов Сергей", d.getArtist());
        assertTrue(d.getArtistUrl().startsWith(S + "/xfsearch/chitaet/"));
        assertEquals(time("07:48:56"), d.getTime());
        assertEquals("Ревизор: возвращение в СССР", d.getSeries());
        assertTrue(d.getSeriesUrl().startsWith(S + "/xfsearch/cikl/%D0%A0"));
        assertEquals("Попаданцы", d.getGenre());
        assertEquals(S + "/popadanciiy/", d.getGenreUrl());
        assertEquals(BookPOJO.formatRating(4f, "28"), d.getReiting());
        assertTrue(d.getDescription().startsWith("Продолжение приключений московского аудитора, попавшего из нашего"
                + " времени в 1971 год."));
    }

    @Test
    public void bookDescription() throws IOException {
        DescriptionPOJO d = Bookish.parseDescription(fixture("book.html", BOOK));
        assertEquals("Игра по расписанию", d.getTitle());
        assertEquals("Балдаччи Дэвид", d.getAutor());
        assertEquals("Тулупов Михаил", d.getArtist());
        assertEquals(time("19:03:52"), d.getTime());
        assertEquals("Шон Кинг и Мишель Максвелл", d.getSeries());
        assertEquals("the breadcrumb names the category, not the menu", "Детектив", d.getGenre());
        assertEquals(S + "/detektiv/", d.getGenreUrl());
        assertEquals(BookPOJO.formatRating(5f, "4"), d.getReiting());
        assertFalse(d.getDescription().isEmpty());
        BookPOJO card = parser.book(fixture("book.html", BOOK), BOOK);
        assertEquals(BOOK, card.getUrl());
        assertEquals("Игра по расписанию", card.getName());
        assertEquals("Шон Кинг и Мишель Максвелл", card.getSeries());
    }

    @Test
    public void bookWithoutSeriesOrVotesInAHiddenCategory() throws IOException {
        DescriptionPOJO d = Bookish.parseDescription(fixture("book-relative.html", S + "/95855-x.html"));
        assertEquals("...Где мудро все и справедливо...", d.getTitle());
        assertEquals("Шоу Ирвин", d.getAutor());
        assertEquals("YoWayYo", d.getArtist());
        assertEquals(time("00:51:00"), d.getTime());
        assertTrue(d.getSeries() == null || d.getSeries().isEmpty());
        assertEquals("Проза", d.getGenre());
        assertEquals(S + "/proza/", d.getGenreUrl());
        assertTrue(d.getReiting() == null || d.getReiting().isEmpty() || d.getReiting().equals("0"));
        DescriptionPOJO many = Bookish.parseDescription(fixture("book-long.html", S + "/18655-x.html"));
        assertEquals("Театр у микрофона (143 альбома)", many.getTitle());
        assertTrue(many.getAutor().endsWith(", Другие авторы"));
        assertEquals("days are counted in the hours", time("255:50:44"), many.getTime());
        assertEquals("Аудиоспектакль", many.getGenre());
        assertTrue(many.getDescription().startsWith("Александр Блок - Незнакомка Александр Грибоедов"));
    }

    @Test
    public void recommendations() throws IOException {
        ArrayList<BookPOJO> books = Bookish.parseRecommendations(fixture("book-series.html", SERIES_BOOK));
        assertEquals(5, books.size());
        BookPOJO first = books.get(0);
        assertTrue(first.getUrl().startsWith(S + "/51216-"));
        assertEquals("Ревизор: возвращение в СССР 7 - Винтеркей Серж, Шумилин Артем", first.getName());
        assertEquals(S + "/uploads/posts/2023-10/652df17b211e5.jpg", first.getPhoto());
    }

    @Test
    public void seriesCardsCarryTheirNumbers() throws IOException {
        ArrayList<SeriesPOJO> series = Bookish.parseSeries(fixture("series.html",
                S + "/xfsearch/cikl/%D0%98%D0%B3%D0%BD%D0%B8%D1%81/"));
        assertEquals(10, series.size());
        assertTrue(series.get(0).getUrl().startsWith(S + "/145849-"));
        assertEquals("Долг Клана", series.get(0).getName());
        assertEquals("10", series.get(0).getNumber());
        assertFalse(series.get(0).getPhoto().isEmpty());
        assertTrue(series.get(9).getUrl().startsWith(S + "/40034-"));
        Bookish.sortByNumber(series);
        assertEquals("1", series.get(0).getNumber());
        assertEquals("10", series.get(9).getNumber());
    }

    @Test
    public void commentsAreThreads() throws IOException {
        ArrayList<ComentsPOJO> one = Bookish.parseComments(fixture("book-series.html", SERIES_BOOK));
        assertEquals(1, one.size());
        assertEquals("истаблишмент", one.get(0).getName());
        assertEquals("27 октября 2026 11:40", one.get(0).getDate());
        assertEquals("Наметился некий оживляж.", one.get(0).getText());
        assertTrue(one.get(0).getChildComents().isEmpty());

        ArrayList<ComentsPOJO> threads = Bookish.parseComments(fixture("book-litres.html", S + "/39285-x.html"));
        int total = 0;
        for (ComentsPOJO thread : threads) {
            total += 1 + thread.getChildComents().size();
        }
        assertEquals(59, total);
        ComentsPOJO first = threads.get(0);
        assertEquals("stvoloch", first.getName());
        assertEquals("7 февраля 2023 22:02", first.getDate());
        assertEquals("Kali", first.getChildComents().get(0).getName());
        assertEquals("stvoloch", first.getChildComents().get(0).getParentName());
        assertTrue(Bookish.parseComments(fixture("book-removed.html", S + "/11-x.html")).isEmpty());
    }

    // ---------------------------------------------------------------- chapters

    @Test
    public void tracksFromTheInlinePlaylist() throws IOException {
        Document page = fixture("book-series.html", SERIES_BOOK);
        ArrayList<AudioPOJO> tracks = parser.tracks(page, SERIES_BOOK);
        assertEquals(22, tracks.size());
        assertEquals("01", tracks.get(0).getName());
        assertEquals("https://archive.org/download/11_20261026_20261026_1834/01.mp3", tracks.get(0).getUrl());
        assertEquals("Ревизор: возвращение в СССР 65", tracks.get(0).getBookName());
        assertEquals("22", tracks.get(21).getName());
        assertEquals("https://archive.org/download/11_20261026_20261026_1834/22.mp3", tracks.get(21).getUrl());
        assertEquals(0, tracks.get(0).getTime());

        ArrayList<AudioPOJO> saved = Bookish.parseTracks(text("playlist.json"), SERIES_BOOK, "x");
        assertEquals(22, saved.size());
        assertEquals(tracks.get(5).getUrl(), saved.get(5).getUrl());

        ArrayList<AudioPOJO> book = parser.tracks(fixture("book.html", BOOK), BOOK);
        assertEquals(101, book.size());
        assertEquals("0001", book.get(0).getName());
        assertEquals("https://archive.org/download/0007_20260602_202606/0001.mp3", book.get(0).getUrl());
        assertEquals("0101", book.get(100).getName());
    }

    @Test
    public void goPhpFilesAreDecodedAndUpgradedToHttps() throws IOException {
        String base = S + "/113276-x.html";
        ArrayList<AudioPOJO> encoded = Bookish.parseTracks(text("playlist-encoded.json"), base, "x");
        ArrayList<AudioPOJO> decoded = Bookish.parseTracks(text("playlist-decoded.json"), base, "x");
        assertEquals(25, encoded.size());
        assertEquals(decoded.size(), encoded.size());
        for (int i = 0; i < encoded.size(); i++) {
            assertEquals(decoded.get(i).getUrl(), encoded.get(i).getUrl());
        }
        assertEquals("https://archive.org/download/22_20250725_20250725_2036/01.mp3", encoded.get(0).getUrl());
        assertEquals("https://archive.org/download/22_20250725_20250725_2036/25.mp3", encoded.get(24).getUrl());

        String relative = S + "/95855-x.html";
        ArrayList<AudioPOJO> http = parser.tracks(fixture("book-relative.html", relative), relative);
        assertEquals(1, http.size());
        assertEquals("1", http.get(0).getName());
        assertEquals("https://archive.org/download/1_20250210_20250210_0620/1.mp3", http.get(0).getUrl());
    }

    @Test
    public void theNewestPostsHaveASingleQuotedPlaylistOnTheirOwnHost() throws IOException {
        String url = S + "/145849-dolg-klana-shirokov-aleksei.html";
        ArrayList<AudioPOJO> tracks = parser.tracks(fixture("book-knizhulki.html", url), url);
        assertEquals(21, tracks.size());
        assertEquals("01", tracks.get(0).getName());
        assertEquals("https://a1.knizhulki.xyz/dolg-klana/001.mp3", tracks.get(0).getUrl());
        assertEquals("https://a1.knizhulki.xyz/dolg-klana/021.mp3", tracks.get(20).getUrl());
        assertNull("the host wants the book page as Referer, the default", parser.mediaHeaders(url));
    }

    @Test
    public void titlesLoseTheirAuthorsInAnyNameOrder() {
        String[][] cases = {
                {"Долг Клана - Широков Алексей", "Широков Алексей", "Долг Клана"},
                {"Кинг Стивен - Очень тесное место", "Кинг Стивен", "Очень тесное место"},
                {"Бандит-5. Принц - Евгений Щепетнов", "Щепетнов Евгений", "Бандит-5. Принц"},
                {"Зыгарь Михаил, Панюшкин Валерий - Новое русское оружие",
                        "Зыгарь Михаил, Зыгарь Михаил Панюшкин Валерий", "Новое русское оружие"},
                {"Ревизор: возвращение в СССР 27 - Винтеркей Серж,Шумилин Артем", "Винтеркей Серж, Шумилин Артем",
                        "Ревизор: возвращение в СССР 27"},
                {"Театр у микрофона (143 альбома)", "Пушкин Александр, Другие авторы",
                        "Театр у микрофона (143 альбома)"},
                {"Алиса - Зазеркалье", "Кэрролл Льюис", "Алиса - Зазеркалье"},
                {"Долг Клана - Широков Алексей", "", "Долг Клана - Широков Алексей"}};
        for (String[] c : cases) {
            assertEquals(c[0], c[2], Bookish.title(c[0], c[1]));
        }
    }

    @Test
    public void durationsCountDaysInTheHours() {
        String[][] cases = {{"06:24:33", "6:24:33"}, {"10:9:27", "10:09:27"}, {"57:10:00.", "57:10:00"},
                {"10 дн. 15:50:44", "255:50:44"}, {"57 мин.", "57 мин."}, {"", ""}};
        for (String[] c : cases) {
            Document doc = Jsoup.parse("<ul class=short-list><li><span>Время:</span> " + c[0] + "</li></ul>");
            assertEquals(c[0], c[1], Bookish.duration(doc));
        }
    }

    @Test
    public void rawFileNamesAreEncodedAndTitlesLoseTheirAlbum() throws IOException {
        String url = S + "/18655-x.html";
        ArrayList<AudioPOJO> tracks = parser.tracks(fixture("book-long.html", url), url);
        assertEquals(1260, tracks.size());
        assertEquals("01. Незнакомка, часть 1", tracks.get(0).getName());
        assertEquals("https://archive.org/download/08.-2/01.%2020000%20%D0%BB%D1%8C%D0%B5%20%D0%BF%D0%BE%D0%B4%20"
                + "%D0%B2%D0%BE%D0%B4%D0%BE%D0%B9,%20%D1%87%D0%B0%D1%81%D1%82%D1%8C%201.mp3", tracks.get(0).getUrl());
    }

    @Test
    public void anEmbeddedArchiveItemPlaysItsMp3s() throws IOException {
        ArrayList<AudioPOJO> tracks = Bookish.parseArchiveItem(text("archive-metadata.json"), "x", SERIES_BOOK);
        assertEquals(22, tracks.size());
        assertEquals("Глава 1", tracks.get(0).getName());
        assertEquals("https://archive.org/download/11_20261026_20261026_1834/01.mp3", tracks.get(0).getUrl());
        assertEquals(1388, tracks.get(0).getTime());
        assertEquals("PrinceAndMagRu_201903",
                Bookish.archiveItem(fixture("book-iframe.html", S + "/29-x.html"), S + "/29-x.html"));
        try {
            Bookish.parseArchiveItem(text("archive-metadata-dark.json"), "x", S + "/29-x.html");
            fail("a dark item is taken down");
        } catch (BookRemovedException expected) {
            assertEquals(S + "/29-x.html", expected.getMessage());
        }
    }

    @Test
    public void litresAndEmptyPlayersAreTakenDown() throws IOException {
        for (String name : new String[]{"book-litres.html", "book-removed.html"}) {
            String url = S + "/1-" + name;
            try {
                parser.tracks(fixture(name, url), url);
                fail(name + " has no audio");
            } catch (BookRemovedException expected) {
                assertEquals(url, expected.getMessage());
            }
        }
        try {
            parser.tracks(fixture("listing-index-p1.html", Bookish.INDEX), Bookish.INDEX);
            fail("a page without a player");
        } catch (BookRemovedException wrong) {
            fail("not a takedown");
        } catch (IllegalStateException expected) {
            // no player at all
        }
    }
}
