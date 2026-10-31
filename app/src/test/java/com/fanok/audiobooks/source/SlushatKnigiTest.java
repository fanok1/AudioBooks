package com.fanok.audiobooks.source;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.fanok.audiobooks.BookRemovedException;
import com.fanok.audiobooks.net.JsonPage;
import com.fanok.audiobooks.pojo.AudioPOJO;
import com.fanok.audiobooks.pojo.BookPOJO;
import com.fanok.audiobooks.pojo.ComentsPOJO;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Pages saved from slushat-knigi.com on 2026-10-31 (see src/test/resources/fixtures/slushat-knigi). */
public class SlushatKnigiTest {

    private static final String S = SlushatKnigi.SERVER;
    private static final String BOOK = S + "/34123-audiokniga-master-i-margarita-mihail-bulgakov.html";
    private static final String GENRE = S + "/fantastika-fentezi/";
    private static final String AUTHOR = S + "/tags/%D1%82%D0%B0%D0%B9%D0%BD%D0%B8%D0%BA%D0%BE%D0%B2%D1%81%D0%BA%D0%B8%D0%B9/";
    private static final String SERIES = S + "/xfsearch/series/%D0%B3%D0%B0%D1%80%D1%80%D0%B8%20%D0%BF%D0%BE%D1%82%D1%82%D0%B5%D1%80/";

    private final SlushatKnigi parser = new SlushatKnigi();

    private static InputStream open(String name) {
        InputStream in = SlushatKnigiTest.class.getResourceAsStream("/fixtures/slushat-knigi/" + name);
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

    private static List<Sort> flat(List<Sort.Entry> menu) {
        List<Sort> sorts = new ArrayList<>();
        for (Sort.Entry entry : menu) {
            sorts.add(entry.sort);
        }
        return sorts;
    }

    // ---------------------------------------------------------------- URLs and sorts

    @Test
    public void pageOneIsTheListingAndLaterPagesAppendPageN() {
        assertEquals(SlushatKnigi.INDEX, parser.pageUrl(SlushatKnigi.INDEX, 1));
        assertEquals(S + "/lastnews/page/2/", parser.pageUrl(SlushatKnigi.INDEX, 2));
        assertEquals(GENRE + "page/3/", parser.pageUrl(GENRE, 3));
        assertEquals(GENRE, parser.pageUrl(GENRE + "page/4/", 1));
        assertEquals(AUTHOR + "page/2/", parser.pageUrl(AUTHOR, 2));
        assertEquals("the tops are one page", SlushatKnigi.TOP_100, parser.pageUrl(SlushatKnigi.TOP_100, 2));
        assertEquals(SlushatKnigi.RATING, parser.pageUrl(SlushatKnigi.RATING, 2));
    }

    @Test
    public void theIndexAndTheTopsShareTheSiteWideMenu() {
        List<Sort> menu = Arrays.asList(Sort.NEW, Sort.TOP_100, Sort.RATING);
        for (String url : new String[]{SlushatKnigi.INDEX, S + "/lastnews/page/5/", SlushatKnigi.TOP_100,
                SlushatKnigi.RATING}) {
            assertEquals(url, menu, flat(parser.sorts(url)));
        }
        for (String url : new String[]{GENRE, GENRE + "page/2/", AUTHOR, SERIES, S + "/", BOOK,
                parser.searchUrl(SearchUrls.Category.BOOKS, "Кинг", 1)}) {
            assertTrue(url, parser.sorts(url).isEmpty());
        }
        assertEquals(SlushatKnigi.INDEX, parser.sortUrl(Sort.NEW, SlushatKnigi.RATING));
        assertEquals(SlushatKnigi.TOP_100, parser.sortUrl(Sort.TOP_100, SlushatKnigi.INDEX));
        assertEquals(SlushatKnigi.RATING, parser.sortUrl(Sort.RATING, SlushatKnigi.TOP_100));
        assertEquals(SlushatKnigi.RATING, parser.sortUrl(Sort.RATING, SlushatKnigi.RATING));
        assertNull(parser.sortUrl(Sort.POPULAR, SlushatKnigi.INDEX));
        assertNull(parser.sortUrl(Sort.TITLE, SlushatKnigi.INDEX));
        assertNull("no ranking inside a genre", parser.genreOf(GENRE));
        assertNull(parser.cardGenre(SlushatKnigi.TOP_100));
        assertSame(Provider.SLUSHAT_KNIGI, Provider.ofUrl(SlushatKnigi.INDEX));
    }

    @Test
    public void searchIsAGetPagedBySearchStart() {
        assertEquals(S + "/index.php?do=search&subaction=search&story=%D0%9A%D0%B8%D0%BD%D0%B3&search_start=2",
                parser.searchUrl(SearchUrls.Category.BOOKS, "Кинг", 2));
        assertEquals(S + "/index.php?do=search&subaction=search&story=%D0%BC%D0%B0%D1%81%D1%82%D0%B5%D1%80+"
                        + "%D0%B1%D1%83%D0%BB%D0%B3%D0%B0%D0%BA%D0%BE%D0%B2&search_start=1",
                parser.otherSourceSearchUrl("мастер булгаков", 1));
        assertNull(parser.searchUrl(SearchUrls.Category.AUTHORS, "Кинг", 1));
        assertNull(parser.searchUrl(SearchUrls.Category.PERFORMERS, "Кинг", 1));
    }

    @Test
    public void commentsPagesAreTheAjaxController() {
        assertEquals(S + "/engine/ajax/controller.php?mod=comments&cstart=2&news_id=1613&skin=audiobooksdark"
                + "&massact=disable", SlushatKnigi.commentsUrl(Dle.newsId(
                S + "/1613-audiokniga-leto-v-pionerskom-galstuke-katerina-silvanova.html"), 2));
    }

    // ---------------------------------------------------------------- listings

    @Test
    public void indexPagesHaveTwelveCardsAndThePagerTellsTheEnd() throws IOException {
        ProviderPage<BookPOJO> first = parser.listing(fixture("listing-index-p1.html", SlushatKnigi.INDEX),
                SlushatKnigi.INDEX, 1);
        assertEquals(12, first.items.size());
        assertTrue(first.hasMore);
        BookPOJO book = first.items.get(0);
        assertEquals(S + "/75579-audiokniga-brat-vjacheslav-ili-podzemele-bliz-kasimova-pavlov-aleksandr.html",
                book.getUrl());
        assertEquals("Брат Вячеслав, или Подземелье близ Касимова", book.getName());
        assertEquals("Павлов Александр", book.getAutor());
        assertEquals(S + "/uploads/posts/books/75579/75579.jpg", book.getPhoto());
        assertEquals("2 ч. 30 мин.", book.getTime());
        assertEquals("История", book.getGenre());
        assertEquals(S + "/istorija/", book.getUrlGenre());
        BookPOJO last = first.items.get(11);
        assertEquals("Последний человек из Атлантиды", last.getName());
        assertEquals("Беляев Александр", last.getAutor());
        assertEquals("4 ч. 32 мин.", last.getTime());

        ProviderPage<BookPOJO> second = parser.listing(fixture("listing-index-p2.html", S + "/lastnews/page/2/"),
                S + "/lastnews/page/2/", 2);
        assertEquals(12, second.items.size());
        assertTrue(second.hasMore);
        assertEquals("Злодейка в академии. Сбежать от куратора", second.items.get(0).getName());
        assertEquals("Лисина Василиса", second.items.get(0).getAutor());
        assertEquals("8 ч. 32 мин.", second.items.get(0).getTime());
        BookPOJO noAuthor = second.items.get(11);
        assertEquals("Пожар скрыл убийство", noAuthor.getName());
        assertNull(noAuthor.getAutor());
        assertEquals("29 мин.", noAuthor.getTime());
    }

    @Test
    public void theLastPageHasNoMoreAndOldBooksNoDuration() throws IOException {
        ProviderPage<BookPOJO> last = SlushatKnigi.parseListing(
                fixture("listing-index-last.html", S + "/lastnews/page/6292/"), 6292);
        assertEquals(11, last.items.size());
        assertFalse(last.hasMore);
        BookPOJO oldest = last.items.get(10);
        assertEquals(S + "/4-audiokniga-istorija-moskovskih-ban-anatolij-rubinov-vladimir-giljarovskij.html",
                oldest.getUrl());
        assertEquals("История московских бань", oldest.getName());
        assertEquals("Анатолий Рубинов, Владимир Гиляровский", oldest.getAutor());
        assertNull(oldest.getTime());
        assertEquals("Познавательная литература / Разное", oldest.getGenre());
        assertEquals(S + "/poznavatelnaja-literatura/", oldest.getUrlGenre());
    }

    @Test
    public void postsThatAreNoAudiobooksAreDropped() throws IOException {
        ProviderPage<BookPOJO> page = SlushatKnigi.parseListing(
                fixture("listing-index-p215-blog.html", S + "/lastnews/page/215/"), 215);
        assertEquals(11, page.items.size());
        assertTrue(page.hasMore);
        for (BookPOJO book : page.items) {
            assertFalse(book.getUrl(), book.getUrl().contains("/73002-"));
            assertTrue(book.getUrl(), book.getUrl().contains("-audiokniga-"));
        }
    }

    @Test
    public void aGenrePageSkipsItsBestOfBlock() throws IOException {
        ProviderPage<BookPOJO> first = parser.listing(fixture("genre-p1.html", GENRE), GENRE, 1);
        assertEquals(12, first.items.size());
        assertTrue(first.hasMore);
        assertEquals(S + "/75574-audiokniga-chelovek-nevidimka-uells-gerbert.html", first.items.get(0).getUrl());
        assertEquals("Человек-невидимка", first.items.get(0).getName());
        assertEquals("Уэллс Герберт", first.items.get(0).getAutor());
        assertEquals("Город тысячи богов", first.items.get(11).getName());
        assertEquals("Кожин Олег", first.items.get(11).getAutor());

        ProviderPage<BookPOJO> second = parser.listing(fixture("genre-p2.html", GENRE + "page/2/"), GENRE + "page/2/", 2);
        assertEquals(12, second.items.size());
        assertTrue(second.hasMore);
        assertTrue(second.items.get(0).getUrl(), second.items.get(0).getUrl().startsWith(S + "/75536-"));
    }

    @Test
    public void theTopsAreOnePageWithTheAuthorAsMeta() throws IOException {
        ProviderPage<BookPOJO> top = parser.listing(fixture("listing-top100.html", SlushatKnigi.TOP_100),
                SlushatKnigi.TOP_100, 1);
        assertEquals(100, top.items.size());
        assertFalse(top.hasMore);
        BookPOJO first = top.items.get(0);
        assertEquals(S + "/1613-audiokniga-leto-v-pionerskom-galstuke-katerina-silvanova.html", first.getUrl());
        assertEquals("Лето в пионерском галстуке", first.getName());
        assertEquals("Катерина Сильванова", first.getAutor());
        assertEquals("14 ч. 19 мин.", first.getTime());
        assertNull("the meta line is the author here", first.getGenre());
        BookPOJO last = top.items.get(99);
        assertEquals("Бегущая могила", last.getName());
        assertEquals("40 ч. 19 мин.", last.getTime());
        // "Все ради игры. Король Воронов - Нора Сакавич (2)": the volume is not the author's
        for (BookPOJO book : top.items) {
            if (book.getUrl().equals(S + "/922-audiokniga-vse-radi-igry-korol-voronov-nora-sakavich-2.html")) {
                assertEquals("Все ради игры. Король Воронов (2)", book.getName());
                assertEquals("Нора Сакавич", book.getAutor());
            }
            assertFalse(book.getAutor(), String.valueOf(book.getAutor()).endsWith(")"));
        }

        ProviderPage<BookPOJO> rating = parser.listing(fixture("listing-rating.html", SlushatKnigi.RATING),
                SlushatKnigi.RATING, 1);
        assertEquals(30, rating.items.size());
        assertFalse(rating.hasMore);
        assertTrue(rating.items.get(0).getUrl().startsWith(S + "/1613-"));
        assertEquals("Еще один шанс", rating.items.get(29).getName());
        assertEquals("Ерофей Трофимов", rating.items.get(29).getAutor());
        assertEquals("10 ч. 26 мин.", rating.items.get(29).getTime());
    }

    @Test
    public void authorAndSeriesListings() throws IOException {
        ProviderPage<BookPOJO> author = parser.listing(fixture("author.html", AUTHOR), AUTHOR, 1);
        assertEquals(12, author.items.size());
        assertTrue(author.hasMore);

        ProviderPage<BookPOJO> series = parser.listing(fixture("series.html", SERIES), SERIES, 1);
        assertEquals(9, series.items.size());
        assertFalse(series.hasMore);
        assertTrue(series.items.get(0).getUrl().startsWith(S + "/43193-"));
        assertEquals("Гарри Поттер и Дары Смерти", series.items.get(0).getName());
        assertEquals("Роулинг Джоан", series.items.get(0).getAutor());
        assertEquals("23 ч. 36 мин.", series.items.get(0).getTime());
    }

    @Test
    public void theGenreListIsTheHeaderMenuWithoutBlogAndDuplicates() throws IOException {
        ProviderPage<GenrePOJO> page = parser.genres(fixture("genres.html", SlushatKnigi.GENRES),
                SlushatKnigi.GENRES, 1);
        List<GenrePOJO> genres = page.items;
        assertFalse(page.hasMore);
        assertEquals(48, genres.size());
        assertEquals("Любовные романы", genres.get(0).getName());
        assertEquals(S + "/ljubovnye-romany/", genres.get(0).getUrl());
        assertEquals("Разное", genres.get(genres.size() - 1).getName());
        List<String> names = new ArrayList<>();
        for (GenrePOJO genre : genres) {
            names.add(genre.getName());
            assertFalse(genre.getUrl(), genre.getUrl().contains("/blog/") || genre.getUrl().contains("/tsitatu/")
                    || genre.getUrl().contains("chto-chitayut"));
        }
        assertTrue(names.contains("Сталкер"));
        assertFalse("same URL as Сталкер", names.contains("S.T.A.L.K.E.R."));
        assertTrue(names.contains("LitRPG"));
        assertFalse("same URL as LitRPG", names.contains("ЛитРПГ"));
        assertTrue(names.contains("Сталкер (STALKER)"));
    }

    // ---------------------------------------------------------------- search

    @Test
    public void searchPagesDropNonBooksAndKeepPaging() throws IOException {
        String p1 = parser.searchUrl(SearchUrls.Category.BOOKS, "Кинг", 1);
        ProviderPage<BookPOJO> first = parser.searchResults(fixture("search-king-p1.html", p1), 1);
        assertEquals(10, first.items.size());
        assertTrue(first.hasMore);
        assertTrue(first.items.get(0).getUrl().startsWith(S + "/31894-"));

        String p2 = parser.searchUrl(SearchUrls.Category.BOOKS, "Кинг", 2);
        ProviderPage<BookPOJO> second = parser.searchResults(fixture("search-king-p2.html", p2), 2);
        assertEquals("all twelve are books", 12, second.items.size());
        assertTrue(second.hasMore);
        BookPOJO book = second.items.get(0);
        assertEquals(S + "/1460-audiokniga-mobilnik-stiven-king.html", book.getUrl());
        assertEquals("Мобильник", book.getName());
        assertEquals("Стивен Кинг", book.getAutor());
        assertEquals("15 ч. 47 мин.", book.getTime());
        assertEquals("Ужасы, мистика / Фантастика, фэнтези", book.getGenre());
        assertEquals(S + "/uzhasy-mistika/", book.getUrlGenre());

        String p6 = parser.searchUrl(SearchUrls.Category.BOOKS, "Кинг", 6);
        ProviderPage<BookPOJO> sixth = parser.searchResults(fixture("search-king-p6.html", p6), 6);
        assertTrue("the pager ends at 72 hits, the pages do not", sixth.hasMore);
        assertEquals(12, sixth.items.size());

        String p33 = parser.searchUrl(SearchUrls.Category.BOOKS, "Кинг", 33);
        ProviderPage<BookPOJO> last = parser.searchResults(fixture("search-king-p33.html", p33), 33);
        assertFalse("ten cards: the last page", last.hasMore);
        assertEquals("three of ten are articles", 7, last.items.size());
        assertEquals(S + "/10557-audiokniga-adamovo-jabloko-sbornik-rasskazov.html", last.items.get(0).getUrl());

        String p34 = parser.searchUrl(SearchUrls.Category.BOOKS, "Кинг", 34);
        ProviderPage<BookPOJO> past = parser.searchResults(fixture("search-king-p34.html", p34), 34);
        assertEquals(ProviderPage.Status.EMPTY, past.status);
        assertFalse(past.hasMore);
    }

    @Test
    public void aSearchWithoutResultsIsEmpty() throws IOException {
        String url = parser.searchUrl(SearchUrls.Category.BOOKS, "zzzxqw", 1);
        ProviderPage<BookPOJO> page = parser.searchResults(fixture("search-none.html", url), 1);
        assertEquals(ProviderPage.Status.EMPTY, page.status);
        assertFalse(page.hasMore);
    }

    @Test
    public void otherSourceSearchFindsTheBook() throws IOException {
        String url = parser.otherSourceSearchUrl("Мастер и Маргарита Булгаков", 1);
        boolean found = false;
        for (com.fanok.audiobooks.model.OtherSourceModel.Hit hit
                : parser.otherSourceHits(fixture("other-source-search.html", url))) {
            assertTrue(hit.url, hit.url.contains("-audiokniga-"));
            found |= hit.url.equals(BOOK);
        }
        assertTrue(found);
    }

    // ---------------------------------------------------------------- book page

    @Test
    public void theBookPageHasEveryField() throws IOException {
        Document page = fixture("book.html", BOOK);
        DescriptionPOJO d = parser.description(page, BOOK);
        assertEquals("Мастер и Маргарита", d.getTitle());
        assertEquals(S + "/uploads/posts/books/34123/34123.jpg", d.getPoster());
        assertEquals("Булгаков Михаил", d.getAutor());
        assertEquals(S + "/tags/%D0%B1%D1%83%D0%BB%D0%B3%D0%B0%D0%BA%D0%BE%D0%B2%20%D0%BC%D0%B8%D1%85%D0%B0%D0%B8%D0%BB/",
                d.getAutorUrl());
        assertEquals("Клюквин Александр", d.getArtist());
        assertEquals("Классика / Роман, проза", d.getGenre());
        assertEquals(S + "/klassika/", d.getGenreUrl());
        assertEquals("16 ч. 53 мин.", d.getTime());
        assertEquals("4.6", d.getReiting());
        assertTrue(d.getDescription(), d.getDescription().startsWith("🔍 Добро пожаловать на slushat-knigi"));
        assertFalse("the review is not the annotation", d.getDescription().contains("Рецензия"));

        BookPOJO card = parser.book(page, BOOK);
        assertEquals(BOOK, card.getUrl());
        assertEquals("Мастер и Маргарита", card.getName());
        assertEquals("16 ч. 53 мин.", card.getTime());

        ArrayList<BookPOJO> recommendations = parser.recommendations(page, BOOK);
        assertEquals("the genre's top three without the open book", 2, recommendations.size());
        assertTrue(recommendations.get(0).getUrl().startsWith(S + "/8116-"));
        assertTrue(recommendations.get(1).getUrl().startsWith(S + "/25831-"));
        assertEquals(S + "/uploads/posts/books/8116/8116.jpg", recommendations.get(0).getPhoto());
        assertFalse(recommendations.get(0).getName().isEmpty());
        assertFalse(recommendations.get(0).getAutor().isEmpty());
    }

    @Test
    public void aSeriesBookLinksItsSeries() throws IOException {
        String url = S + "/75569-audiokniga-moja-geroicheskaja-ferma-tom-4-tajnikovskij.html";
        DescriptionPOJO d = parser.description(fixture("book-series.html", url), url);
        assertEquals("Моя героическая ферма. Том 4", d.getTitle());
        assertEquals("Тайниковский", d.getAutor());
        assertEquals(AUTHOR, d.getAutorUrl());
        assertEquals("Ломакин Игорь", d.getArtist());
        assertEquals("Моя героическая ферма", d.getSeries());
        assertEquals(S + "/xfsearch/series/%D0%BC%D0%BE%D1%8F%20%D0%B3%D0%B5%D1%80%D0%BE%D0%B8%D1%87%D0%B5%D1%81%D0%BA"
                + "%D0%B0%D1%8F%20%D1%84%D0%B5%D1%80%D0%BC%D0%B0/", d.getSeriesUrl());
        assertEquals("7 ч. 32 мин.", d.getTime());
        assertEquals(S + "/fantastika-fentezi/", d.getGenreUrl());

        String series = d.getSeriesUrl();
        ArrayList<SeriesPOJO> items = SlushatKnigi.parseSeries(fixture("series-small.html", series));
        assertEquals(2, items.size());
        assertTrue(items.get(0).getUrl().startsWith(S + "/65228-"));
        assertTrue(items.get(1).getUrl().startsWith(S + "/63925-"));
        assertFalse(items.get(0).getName().isEmpty());
        assertEquals(S + "/uploads/posts/books/65228/65228.jpg", items.get(0).getPhoto());
    }

    @Test
    public void anOldBookHasNoDurationAndOneLinkedAuthor() throws IOException {
        String url = S + "/4-audiokniga-istorija-moskovskih-ban-anatolij-rubinov-vladimir-giljarovskij.html";
        DescriptionPOJO d = parser.description(fixture("book-old-noduration.html", url), url);
        assertEquals("История московских бань - Анатолий Рубинов, Владимир Гиляровский", d.getTitle());
        assertEquals("Рубинов Анатолий", d.getAutor());
        assertEquals("Кирсанов Сергей", d.getArtist());
        assertEquals("", d.getTime());
        assertEquals("3", d.getReiting());
        assertEquals("", d.getSeries());
    }

    // ---------------------------------------------------------------- tracks

    @Test
    public void tracksAreThePlayerjsPlaylistOneMp3PerChapter() throws IOException {
        String playlist = SlushatKnigi.playlistUrl(fixture("book.html", BOOK), BOOK);
        assertEquals("https://9giiu0g54k8c.redirectto.cc/s01/5/4/8/5/5485.pl.txt", playlist);
        ArrayList<AudioPOJO> tracks = SlushatKnigi.parseTracks(text("playlist.json"), playlist, "Мастер и Маргарита");
        assertEquals(206, tracks.size());
        assertEquals("0001", tracks.get(0).getName());
        assertEquals("https://j3wccg4mgjcw.redirectto.cc/s01/5/4/8/5/0.mp3", tracks.get(0).getUrl());
        assertEquals("Мастер и Маргарита", tracks.get(0).getBookName());
        assertEquals(0, tracks.get(0).getTime());
        assertEquals("0206", tracks.get(205).getName());
        assertEquals("https://j3wccg4mgjcw.redirectto.cc/s01/5/4/8/5/205.mp3", tracks.get(205).getUrl());

        String popular = S + "/1613-audiokniga-leto-v-pionerskom-galstuke-katerina-silvanova.html";
        assertEquals("https://9giiu0g54k8c.redirectto.cc/s01/4/5/8/2/7/45827.pl.txt",
                SlushatKnigi.playlistUrl(fixture("book-comments.html", popular), popular));
        ArrayList<AudioPOJO> chapters = SlushatKnigi.parseTracks(text("playlist-1613.json"), popular, "x");
        assertEquals(21, chapters.size());
        assertEquals("Глава 01", chapters.get(0).getName());
        assertEquals("Глава 21", chapters.get(20).getName());
    }

    @Test
    public void albumTitlesKeepTheirSlash() throws IOException {
        String url = S + "/10382-audiokniga-teatr-u-mikrofona.html";
        String playlist = SlushatKnigi.playlistUrl(fixture("book-albums.html", url), url);
        assertEquals("https://9giiu0g54k8c.redirectto.cc/s01/2/1/7/7/4/21774.pl.txt", playlist);
        ArrayList<AudioPOJO> tracks = SlushatKnigi.parseTracks(text("playlist-albums.json"), playlist, "x");
        assertEquals(1260, tracks.size());
        assertTrue(tracks.get(0).getName(), tracks.get(0).getName().contains("/"));
        assertEquals("255 ч. 50 мин.", parser.description(fixture("book-albums.html", url), url).getTime());
    }

    @Test
    public void aPageWithoutPlayerIsAnErrorOrATakedown() throws IOException {
        Document page = fixture("book.html", BOOK);
        page.select("script:containsData(new Playerjs)").remove();
        try {
            SlushatKnigi.playlistUrl(page, BOOK);
            fail("no player");
        } catch (BookRemovedException e) {
            fail("no takedown notice on the page");
        } catch (IllegalStateException expected) {
            // a page without a player and without a notice
        }
        // no takedown was found on the site (2026-10-31); the app's notice wording stands in for one
        page.selectFirst(".page__text").text("Аудиокнига удалена по требованию правообладателя");
        try {
            SlushatKnigi.playlistUrl(page, BOOK);
            fail("removed");
        } catch (BookRemovedException expected) {
            assertEquals(BOOK, expected.getMessage());
        }
    }

    // ---------------------------------------------------------------- comments

    @Test
    public void commentsAreInlineThenByAjax() throws IOException {
        String url = S + "/1613-audiokniga-leto-v-pionerskom-galstuke-katerina-silvanova.html";
        Document page = fixture("book-comments.html", url);
        assertEquals("4.5", parser.description(page, url).getReiting());
        assertEquals(2, SlushatKnigi.commentPages(page));
        assertEquals(1, SlushatKnigi.commentPages(fixture("book.html", BOOK)));
        assertTrue(SlushatKnigi.parseComments(fixture("book.html", BOOK)).isEmpty());

        ArrayList<ComentsPOJO> inline = SlushatKnigi.parseComments(page);
        assertEquals(30, inline.size());
        ComentsPOJO first = inline.get(0);
        assertEquals("Гость Наталия", first.getName());
        assertEquals("8 октября 2023 17:43", first.getDate());
        assertTrue(first.getText(), first.getText().startsWith("Книга \"Лето в пионерском галстуке\""));
        assertEquals("no avatar", "", first.getImage());
        ComentsPOJO reply = null;
        for (ComentsPOJO comment : inline) {
            if (comment.getText().startsWith("о господи. Книга шикарна,очень понравилась!!!")) {
                reply = comment;
            }
        }
        assertNotNull(reply);
        assertEquals("Гость кириешка", reply.getQuoteName());
        assertTrue(reply.getQuoteText(), reply.getQuoteText().contains("разревелась"));
        assertFalse(reply.getText(), reply.getText().contains("разревелась"));

        String ajax = SlushatKnigi.commentsUrl("1613", 2);
        ArrayList<ComentsPOJO> second = SlushatKnigi.parseCommentsPage(JsonPage.parse(ajax, text("comments-p2.json")));
        assertEquals(27, second.size());
        assertEquals("Гость Лина", second.get(0).getName());
        assertEquals("5 сентября 2025 00:36", second.get(0).getDate());
        assertTrue(second.get(0).getText().startsWith("Очень классная книга."));
    }
}
