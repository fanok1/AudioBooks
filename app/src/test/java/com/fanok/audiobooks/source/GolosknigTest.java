package com.fanok.audiobooks.source;

import static org.junit.Assert.assertArrayEquals;
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
import com.fanok.audiobooks.pojo.OtherArtistPOJO;
import com.fanok.audiobooks.pojo.SeriesPOJO;
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
import java.util.List;

/** golosknig.com pages saved on 2026-10-31 (see src/test/resources/fixtures/golosknig). */
public class GolosknigTest {

    private static final Golosknig G = new Golosknig();
    private static final String S = Golosknig.SERVER;
    private static final String GENRE = S + "/genre/fantastic/";
    private static final String KING = "%D0%9A%D0%B8%D0%BD%D0%B3";
    private static final String BOOK = S + "/yacheyka-21-hellstrem-roslund";
    private static final String SERIES_BOOK = S + "/vlastelin-kolec-bratstvo-kolca-tolkin-dzhon";
    private static final String MP3 = "https://r1.akniga.club/s/b/92055/hellstrem-berge-roslund-anders-yacheyka-21.mp3"
            + "?res=8g9WdD9GOEy4hL7SRDYgPw&expires=2051222400";

    private static InputStream open(String name) {
        InputStream in = GolosknigTest.class.getResourceAsStream("/fixtures/golosknig/" + name);
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

    private static ProviderPage<BookPOJO> listing(String name, String url) throws IOException {
        return G.listing(page(name, url), url, 1);
    }

    private static void assertPage(ProviderPage<BookPOJO> page, int cards, boolean more, String firstSlug) {
        assertEquals(ProviderPage.Status.OK, page.status);
        assertEquals(cards, page.items.size());
        assertEquals(more, page.hasMore);
        assertEquals(S + "/" + firstSlug, page.items.get(0).getUrl());
    }

    @Test
    public void indexPages() throws IOException {
        ProviderPage<BookPOJO> p1 = listing("listing-index-p1.html", Golosknig.INDEX);
        assertPage(p1, 12, true, "skazka-pro-staryy-fotoalbom-bathen-nika");
        BookPOJO first = p1.items.get(0);
        assertEquals("Сказка про старый фотоальбом", first.getName());
        assertEquals("Батхен Ника", first.getAutor());
        assertEquals(S + "/author/%D0%91%D0%B0%D1%82%D1%85%D0%B5%D0%BD%20%D0%9D%D0%B8%D0%BA%D0%B0/",
                first.getUrlAutor());
        assertEquals("Елена Понеделина", first.getArtist());
        assertEquals(S + "/performer/%D0%9F%D0%BE%D0%BD%D0%B5%D0%B4%D0%B5%D0%BB%D0%B8%D0%BD%D0%B0%20"
                + "%D0%95%D0%BB%D0%B5%D0%BD%D0%B0/", first.getUrlArtist());
        assertEquals(S + "/uploads/topics/preview/00/00/00/00/ac14d46ccd_200crop.jpg", first.getPhoto());
        assertEquals("site text \"20 минут\", normalized by the POJO", "20 мин.", first.getTime());
        assertTrue(first.getDesc().endsWith("..."));
        assertNull("no genre in cards", first.getGenre());
        BookPOJO sixth = p1.items.get(5);
        assertEquals("Отпуск в раю", sixth.getName());
        assertEquals("де ла Мотт Андерс, Нильссон Монс", sixth.getAutor());
        assertEquals(S + "/author/%D0%B4%D0%B5%20%D0%BB%D0%B0%20%D0%9C%D0%BE%D1%82%D1%82%20"
                + "%D0%90%D0%BD%D0%B4%D0%B5%D1%80%D1%81/", sixth.getUrlAutor());
        assertEquals("10 ч. 22 мин.", sixth.getTime());

        assertPage(listing("listing-index-p2.html", S + "/index/page2/"), 12, true,
                "sredi-voskovyh-figur-berreydzh-alfred");

        ProviderPage<BookPOJO> last = listing("listing-index-last.html", S + "/index/page2491/");
        assertPage(last, 9, false, "sbornik-stihov-i-anekdotov-roman-trahtenberg");
        BookPOJO thrones = null;
        for (BookPOJO book : last.items) {
            if (book.getName().equals("Игра престолов")) {
                thrones = book;
            }
        }
        assertNotNull(thrones);
        assertEquals("Мартин Джордж", thrones.getAutor());
        assertEquals("Олег Булдаков и др.", thrones.getArtist());
        assertEquals("36 ч. 35 мин.", thrones.getTime());
    }

    @Test
    public void titlesWithADashKeepIt() throws IOException {
        // "Смерть — дело одинокое — Брэдбери Рэй" splits on the last dash
        ProviderPage<BookPOJO> views = listing("genre-views-p2.html", GENRE + "views/page2/?period=all");
        assertPage(views, 12, true, "vino-iz-oduvanchikov-bredberi-rey");
        assertEquals("Вино из одуванчиков", views.items.get(0).getName());
        assertEquals("Брэдбери Рэй", views.items.get(0).getAutor());
        Document doc = Jsoup.parse("<article class='topic'><h2 class='topic-title'><a href='/smert'>"
                + "Смерть — дело одинокое — Брэдбери Рэй</a></h2></article>", S + "/");
        BookPOJO book = Golosknig.parseListing(doc).items.get(0);
        assertEquals("Смерть — дело одинокое", book.getName());
        assertEquals("Брэдбери Рэй", book.getAutor());
    }

    @Test
    public void sortedAndGenreListings() throws IOException {
        assertPage(listing("listing-views-p1.html", Golosknig.VIEWS), 12, true, "dom-v-kotorom-petrosyan-mariam");
        assertPage(listing("listing-views-p2.html", S + "/index/views/page2/?period=all"), 12, true,
                "robinzon-kruzo-defo-daniel");
        assertPage(listing("genre-p1.html", GENRE), 12, true, "korablik-v-butylke-saymak-klifford_2");
        assertPage(listing("genre-p2.html", GENRE + "page2/"), 12, true, "zheltaya-pilyulya-fillips-rog_2");
        assertPage(listing("genre-views-p1.html", GENRE + "views/?period=all"), 12, true, "1984-oruell-dzhordzh");
    }

    @Test
    public void peopleAndSeriesListings() throws IOException {
        String king = S + "/author/" + KING + "%20%D0%A1%D1%82%D0%B8%D0%B2%D0%B5%D0%BD/";
        assertPage(listing("author.html", king), 12, true, "kratchayshiy-put-dlya-missis-todd-king-stiven_2");
        String reader = S + "/performer/%D0%95%D1%80%D0%B8%D1%81%D0%B0%D0%BD%D0%BE%D0%B2%D0%B0%20%D0%98%D1%80%D0%B8%D0%BD%D0%B0/";
        assertPage(listing("reader.html", reader), 12, true, "yacheyka-21-hellstrem-roslund");
        ProviderPage<BookPOJO> series = listing("series.html", S + "/series/%D0%92%D0%BB%D0%B0%D1%81%D1%82%D0%B5%D0%BB%D0%B8%D0%BD%20%D0%BA%D0%BE%D0%BB%D0%B5%D1%86/");
        assertPage(series, 6, false, "vlastelin-kolec-tolkin-dzhon");
        assertEquals("Властелин колец. Хранители", series.items.get(1).getName());

        // the last author (Батхен Ника) page of 2026-10-31: the anthology of 13 authors has none in its title link
        ProviderPage<BookPOJO> author = listing("author-many-authors.html",
                S + "/author/%D0%91%D0%B0%D1%82%D1%85%D0%B5%D0%BD%20%D0%9D%D0%B8%D0%BA%D0%B0/");
        assertPage(author, 10, false, "skazka-pro-staryy-fotoalbom-bathen-nika");
        BookPOJO anthology = author.items.get(9);
        assertEquals("Глубина. Погружение 18-е", anthology.getName());
        assertTrue(anthology.getAutor(), anthology.getAutor().startsWith("Верн Жюль, Марсель Габриэль, Уэллс Герберт"));
        assertTrue(anthology.getAutor().endsWith("Батхен Ника, Стрэнд Джефф"));
        assertEquals(S + "/author/%D0%92%D0%B5%D1%80%D0%BD%20%D0%96%D1%8E%D0%BB%D1%8C/", anthology.getUrlAutor());
        assertEquals("Артем Быков и др.", anthology.getArtist());
    }

    @Test
    public void search() throws IOException {
        assertEquals(S + "/search/books/?q=" + KING, G.searchUrl(SearchUrls.Category.BOOKS, "Кинг", 1));
        assertEquals(S + "/search/books/page2/?q=" + KING, G.searchUrl(SearchUrls.Category.BOOKS, "Кинг", 2));
        assertNull(G.searchUrl(SearchUrls.Category.AUTHORS, "Кинг", 1));
        assertNull(G.searchUrl(SearchUrls.Category.PERFORMERS, "Кинг", 1));

        Document p1 = page("search-king-p1.html", G.searchUrl(SearchUrls.Category.BOOKS, "Кинг", 1));
        assertPage(G.searchResults(p1, 1), 12, true, "semya-kingov-i-zlaya-koldunya-king-stiven");
        Document p2 = page("search-king-p2.html", G.searchUrl(SearchUrls.Category.BOOKS, "Кинг", 2));
        assertPage(G.searchResults(p2, 2), 12, true, "nochnaya-smena-sbornik-king-stiven");
        Document last = page("search-king-last.html", G.searchUrl(SearchUrls.Category.BOOKS, "Кинг", 36));
        assertPage(G.searchResults(last, 36), 3, false, "smirennye-sestry-elurii-king-stiven");

        ProviderPage<BookPOJO> none = G.searchResults(page("search-none.html", S + "/search/books/?q=zzzxqw"), 1);
        assertEquals(ProviderPage.Status.EMPTY, none.status);
        assertFalse(none.hasMore);

        BookPOJO master = new BookPOJO();
        master.setName("Мастер и Маргарита");
        master.setAutor("Булгаков Михаил");
        master.setArtist("Козий Владимир");
        assertEquals("Мастер и Маргарита Булгаков Михаил", G.otherSourceTerms(master));
        Document other = page("other-source-search.html", G.otherSourceSearchUrl("Мастер и Маргарита Булгаков", 1));
        assertPage(G.searchResults(other, 1), 12, false, "master-i-margarita-bulgakov-mihail_5");
        assertEquals("Булгаков Михаил", G.otherSourceHits(other).get(0).author);
    }

    @Test
    public void genres() throws IOException {
        ArrayList<GenrePOJO> p1 = Golosknig.parseGenres(page("genres.html", Golosknig.GENRES));
        assertEquals(20, p1.size());
        assertEquals("Фантастика, фэнтези", p1.get(0).getName());
        assertEquals(GENRE, p1.get(0).getUrl());
        ArrayList<GenrePOJO> p2 = Golosknig.parseGenres(
                page("genres-p2.html", S + "/genres/page2/?order=blog_count_topic&order_way=desc"));
        assertEquals(3, p2.size());
        assertEquals("На инстранном языке", p2.get(2).getName());
        assertEquals(S + "/genre/foreign/", p2.get(2).getUrl());
        assertFalse("the screen shows all pages at once", G.genres(page("genres.html", Golosknig.GENRES),
                Golosknig.GENRES, 1).hasMore);
    }

    @Test
    public void people() throws IOException {
        ProviderPage<GenrePOJO> authors = G.people(page("people-authors-p1.html", Golosknig.AUTHORS),
                Golosknig.AUTHORS, 1, false);
        assertEquals("the nameless first row is left out", 29, authors.items.size());
        assertTrue("287 pages", authors.hasMore);
        assertEquals("7_korov", authors.items.get(0).getName());
        assertEquals(S + "/author/7_korov/", authors.items.get(0).getUrl());
        assertEquals(S + "/authors/page2/", G.pageUrl(Golosknig.AUTHORS, 2));

        ProviderPage<GenrePOJO> readers = G.people(page("people-readers-p1.html", Golosknig.READERS),
                Golosknig.READERS, 1, true);
        assertEquals(29, readers.items.size());
        assertTrue(readers.hasMore);
        assertEquals("beckham48", readers.items.get(0).getName());
        assertEquals(S + "/performer/%20beckham48/", readers.items.get(0).getUrl());
        assertEquals(S + "/performers/page3/", G.pageUrl(Golosknig.READERS, 3));
    }

    @Test
    public void description() throws IOException {
        DescriptionPOJO d = G.description(page("book.html", BOOK), BOOK);
        assertEquals("Ячейка 21", d.getTitle());
        assertEquals(S + "/uploads/topics/preview/00/00/00/00/a887fc1c34.jpg", d.getPoster());
        assertEquals("Хелльстрём Бёрге, Рослунд Андерс", d.getAutor());
        assertEquals(S + "/author/%D0%A5%D0%B5%D0%BB%D0%BB%D1%8C%D1%81%D1%82%D1%80%D1%91%D0%BC%20"
                + "%D0%91%D1%91%D1%80%D0%B3%D0%B5/", d.getAutorUrl());
        assertEquals("Ерисанова Ирина", d.getArtist());
        assertEquals(S + "/performer/%D0%95%D1%80%D0%B8%D1%81%D0%B0%D0%BD%D0%BE%D0%B2%D0%B0%20"
                + "%D0%98%D1%80%D0%B8%D0%BD%D0%B0/", d.getArtistUrl());
        assertEquals("Детективы, боевики, триллеры", d.getGenre());
        assertEquals(S + "/genre/detective/", d.getGenreUrl());
        assertEquals("10 ч. 17 мин.", d.getTime());
        assertEquals("", d.getSeries());
        assertEquals("", d.getReiting());
        assertTrue(d.getDescription().startsWith("В стокгольмскую больницу после жестокого избиения"));
        assertTrue(d.getDescription().endsWith("Tекст читает Ирина Ерисанова."));
        assertFalse(d.isOtherReader());

        BookPOJO card = G.book(page("book.html", BOOK), BOOK);
        assertEquals("Ячейка 21", card.getName());
        assertEquals(BOOK, card.getUrl());
        assertEquals("10 ч. 17 мин.", card.getTime());
    }

    @Test
    public void seriesBook() throws IOException {
        Document doc = page("book-series.html", SERIES_BOOK);
        DescriptionPOJO d = G.description(doc, SERIES_BOOK);
        assertEquals("Властелин колец. Братство кольца", d.getTitle());
        assertEquals("Толкин Джон", d.getAutor());
        assertEquals("Евгений Косуха", d.getArtist());
        assertEquals("Фантастика, фэнтези", d.getGenre());
        assertEquals("8 ч. 47 мин.", d.getTime());
        assertEquals("Властелин колец", d.getSeries());
        assertEquals(S + "/series/%D0%92%D0%BB%D0%B0%D1%81%D1%82%D0%B5%D0%BB%D0%B8%D0%BD%20"
                + "%D0%BA%D0%BE%D0%BB%D0%B5%D1%86/", d.getSeriesUrl());
        assertTrue("the other narration is not the text", d.getDescription().startsWith("«Властелин Колец» является"));
        assertTrue(d.isOtherReader());

        ArrayList<SeriesPOJO> series = G.series(doc, SERIES_BOOK);
        assertEquals(6, series.size());
        assertEquals("Властелин колец", series.get(0).getName());
        assertEquals("the cycle as a whole has no number", "", series.get(0).getNumber());
        assertEquals(S + "/vlastelin-kolec-hraniteli-tolkin-dzhon", series.get(1).getUrl());
        assertEquals("1", series.get(1).getNumber());
        assertEquals(SERIES_BOOK, series.get(2).getUrl());
        assertEquals("Властелин колец. Возвращение короля", series.get(5).getName());
        assertEquals("3", series.get(5).getNumber());
        assertTrue(G.series(page("book.html", BOOK), BOOK).isEmpty());

        ArrayList<OtherArtistPOJO> others = Golosknig.parseOtherReaders(doc);
        assertEquals(1, others.size());
        assertEquals("Гребенщиков Кирилл (2007 год)", others.get(0).getName());
        assertEquals(SERIES_BOOK + "_2", others.get(0).getUrl());
        assertTrue(Golosknig.parseOtherReaders(page("book.html", BOOK)).isEmpty());
        assertTrue(G.findsOtherReaders());
        assertFalse("the page tells", G.probesOtherReaders());
    }

    /** "Исполняют:" several readers, "Другие озвучки:" several narrations (saved live on 2026-10-31). */
    @Test
    public void severalReadersAndNarrations() throws IOException {
        String url = S + "/igra-prestolov-martin-dzhordzh";
        Document doc = page("book-readers.html", url);
        DescriptionPOJO d = G.description(doc, url);
        assertEquals("Игра престолов", d.getTitle());
        assertEquals("Мартин Джордж", d.getAutor());
        assertEquals("Булдаков Олег, Игнатьев Дмитрий, Князев Игорь, Никифорова Светлана, Тихонов Алексей, "
                + "Юрьева Елена", d.getArtist());
        assertEquals(S + "/performer/%D0%91%D1%83%D0%BB%D0%B4%D0%B0%D0%BA%D0%BE%D0%B2%20%D0%9E%D0%BB%D0%B5%D0%B3/",
                d.getArtistUrl());
        assertEquals("Песнь Льда и Пламени", d.getSeries());
        assertTrue(d.isOtherReader());
        assertFalse(d.getDescription().contains("озвучки"));
        ArrayList<OtherArtistPOJO> others = Golosknig.parseOtherReaders(doc);
        assertEquals(3, others.size());
        assertEquals("Полонецкий Дмитрий,Полонецкая Елена (2015 год)", others.get(1).getName());
        assertEquals(url + "_3", others.get(1).getUrl());
        ArrayList<SeriesPOJO> series = G.series(doc, url);
        assertEquals(5, series.size());
        assertEquals("1", series.get(0).getNumber());
        assertEquals(url, series.get(0).getUrl());
        try {
            Golosknig.bid(doc, url);
            fail("unavailable");
        } catch (BookRemovedException expected) {
            assertEquals(url, expected.getMessage());
        }
    }

    @Test
    public void unavailableBookKeepsItsPage() throws IOException {
        DescriptionPOJO d = G.description(page("book-unavailable.html", S + "/otpusk-v-rayu-de-nilsson"),
                S + "/otpusk-v-rayu-de-nilsson");
        assertEquals("Отпуск в раю", d.getTitle());
        assertEquals("де ла Мотт Андерс, Нильссон Монс", d.getAutor());
        assertEquals("Кузнецов Андрей", d.getArtist());
        assertEquals("Чисто шведские убийства", d.getSeries());
    }

    @Test
    public void recommendations() throws IOException {
        ArrayList<BookPOJO> books = G.recommendations(page("book.html", BOOK), BOOK);
        assertEquals(8, books.size());
        assertEquals(S + "/poslednyaya-shvatka-makgil-gordon", books.get(0).getUrl());
        assertEquals("Последняя схватка", books.get(0).getName());
        assertEquals("Макгил Гордон", books.get(0).getAutor());
        assertEquals(S + "/uploads/topics/preview/00/00/00/00/3de524ef86_150crop.jpg", books.get(0).getPhoto());
        assertEquals("other works of the author, then the genre", 12,
                G.recommendations(page("book-series.html", SERIES_BOOK), SERIES_BOOK).size());
    }

    @Test
    public void tracks() throws IOException {
        assertEquals("92055", Golosknig.bid(page("book.html", BOOK), BOOK));
        assertEquals("74528", Golosknig.bid(page("book-series.html", SERIES_BOOK), SERIES_BOOK));

        ArrayList<AudioPOJO> tracks = Golosknig.parseTracks(json("playlist-encoded.json"), "Ячейка 21", BOOK);
        assertEquals(64, tracks.size());
        AudioPOJO first = tracks.get(0);
        assertEquals("01_01_1", first.getName());
        assertEquals("Ячейка 21", first.getBookName());
        assertEquals(MP3, first.getUrl());
        assertEquals(0, first.getTimeStart());
        assertEquals(704, first.getTimeFinish());
        assertEquals(704, first.getTime());
        AudioPOJO last = tracks.get(63);
        assertEquals("03_04_4", last.getName());
        assertEquals(MP3, last.getUrl());
        assertEquals(36815, last.getTimeStart());
        assertEquals(37060, last.getTimeFinish());
        assertEquals(245, last.getTime());
        assertDecoded("playlist-decoded.json", tracks);

        ArrayList<AudioPOJO> series = Golosknig.parseTracks(json("playlist-series-encoded.json"), "", SERIES_BOOK);
        assertEquals(13, series.size());
        assertEquals("1. Пролог. О хоббитах.", series.get(0).getName());
        assertEquals("13. Глава 8 _ Туман над курганами", series.get(12).getName());
        assertEquals(28509, series.get(12).getTimeStart());
        assertEquals(31622, series.get(12).getTimeFinish());
        assertDecoded("playlist-series-decoded.json", series);

        ArrayList<AudioPOJO> single = Golosknig.parseTracks(json("playlist-single-encoded.json"), "",
                S + "/platonov-andrey-ivan-velikiy");
        assertEquals(1, single.size());
        assertEquals("Иван Великий", single.get(0).getName());
        assertEquals(0, single.get(0).getTimeStart());
        assertEquals(1233, single.get(0).getTimeFinish());
        assertDecoded("playlist-single-decoded.json", single);
        assertTrue("one file for the book", G.downloadsSharedFiles());
    }

    /** The URL and the offsets of every chapter as the player decoded them. */
    private static void assertDecoded(String name, List<AudioPOJO> tracks) throws IOException {
        JsonObject decoded = Json.object(json(name));
        JsonArray items = decoded.getAsJsonArray("items");
        assertEquals(items.size(), tracks.size());
        for (int i = 0; i < items.size(); i++) {
            JsonObject item = items.get(i).getAsJsonObject();
            AudioPOJO track = tracks.get(i);
            assertEquals(Json.string(decoded, "mp3"), track.getUrl());
            assertEquals(Json.string(item, "title"), track.getName());
            assertEquals(item.get("time_from_start").getAsInt(), track.getTimeStart());
            assertEquals(item.get("time_finish").getAsInt(), track.getTimeFinish());
            assertEquals(item.get("duration").getAsInt(), track.getTime());
        }
    }

    @Test
    public void refusedPlaylistIsAnError() throws IOException {
        try {
            Golosknig.parseTracks(json("playlist-badhash.json"), "", BOOK);
            fail("no res, no items");
        } catch (IllegalStateException expected) {
            assertFalse(expected instanceof BookRemovedException);
        }
    }

    @Test
    public void closedLitresAndUnavailableBooksAreRemoved() throws IOException {
        for (String name : Arrays.asList("book-removed.html", "book-litres.html", "book-unavailable.html")) {
            String url = S + "/" + name;
            try {
                Golosknig.bid(page(name, url), url);
                fail(name + " has no player");
            } catch (BookRemovedException expected) {
                assertEquals(url, expected.getMessage());
            }
        }
        try {
            Golosknig.bid(page("listing-index-p1.html", Golosknig.INDEX), Golosknig.INDEX);
            fail("not a book page");
        } catch (IllegalStateException expected) {
            assertFalse(expected instanceof BookRemovedException);
        }
    }

    @Test
    public void cryptoJsVectors() {
        byte[] salt = {1, 2, 3, 4, 5, 6, 7, 8};
        byte[] keyIv = CryptoJs.evpBytesToKey("EKxtcg46V", salt);
        assertArrayEquals(CryptoJs.hexToBytes("842bf71dd3fee075dc46f5ddedbf3b8107e6a4a6e336c3bd7fc89d913beb4974"
                + "ab54694cd0373aec0e54f290546e1922"), keyIv);
        String hash = CryptoJs.encrypt("\"82adf60523fd5bc14603fe236f5911cb\"", "EKxtcg46V", salt);
        assertEquals("{\"ct\":\"uDjEyujqMS+LCJO6LEFXZmOzFhqaShfgum9fQMIvY8WlGU84iSuE9pZEKaTi84YX\","
                + "\"iv\":\"ab54694cd0373aec0e54f290546e1922\",\"s\":\"0102030405060708\"}", hash);
        assertEquals("\"82adf60523fd5bc14603fe236f5911cb\"", CryptoJs.decrypt(hash, "EKxtcg46V"));

        String random = Golosknig.hash("82adf60523fd5bc14603fe236f5911cb");
        assertEquals("the key as a JSON string", "\"82adf60523fd5bc14603fe236f5911cb\"",
                CryptoJs.decrypt(random, "EKxtcg46V"));
    }

    @Test
    public void pages() {
        assertEquals(Golosknig.INDEX, G.pageUrl(Golosknig.INDEX, 1));
        assertEquals(S + "/index/page2/", G.pageUrl(Golosknig.INDEX, 2));
        assertEquals("the root pages on as the index", S + "/index/page2/", G.pageUrl(S + "/", 2));
        assertEquals(S + "/index/page3/", G.pageUrl(S, 3));
        assertEquals(S + "/index/views/page2/?period=all", G.pageUrl(Golosknig.VIEWS, 2));
        assertEquals(GENRE + "page2/", G.pageUrl(GENRE, 2));
        assertEquals(GENRE + "views/page2/?period=all", G.pageUrl(GENRE + "views/?period=all", 2));
        assertEquals(S + "/genre/detective/page2/", G.pageUrl(S + "/genre/detective", 2));
        assertEquals(S + "/author/A.%20Poleff/page2/", G.pageUrl(S + "/author/A.%20Poleff/", 2));
    }

    /** As the presenter does it: the site-wide sort, then narrowed to the genre the listing is in. */
    private static String sorted(Sort sort, String listing) {
        String url = G.sortUrl(sort, listing);
        String genre = G.genreOf(listing);
        return url == null || url.equals(listing) || genre == null ? url : G.inGenre(url, genre);
    }

    @Test
    public void sorts() {
        List<Sort.Entry> menu = G.sorts(Golosknig.INDEX);
        assertEquals(2, menu.size());
        assertSame(Sort.NEW, menu.get(0).sort);
        assertSame(Sort.POPULAR, menu.get(1).sort);
        assertNull("new in 24 h is the start of \"new\"", G.sortUrl(Sort.TODAY, Golosknig.INDEX));
        for (String url : Arrays.asList(S + "/", Golosknig.VIEWS, GENRE, GENRE + "views/?period=all",
                S + "/genre/detective")) {
            assertEquals(url, menu, G.sorts(url));
        }
        for (String url : Arrays.asList(S + "/author/A.%20Poleff/", S + "/performer/%20beckham48/",
                S + "/series/%D0%92/", S + "/search/books/?q=" + KING)) {
            assertTrue(url, G.sorts(url).isEmpty());
        }

        String home = Golosknig.INDEX;
        assertEquals(home, sorted(Sort.NEW, home));
        assertEquals(Golosknig.VIEWS, sorted(Sort.POPULAR, home));
        assertEquals(home, sorted(Sort.NEW, Golosknig.VIEWS));
        assertEquals("already there", Golosknig.VIEWS, G.sortUrl(Sort.POPULAR, Golosknig.VIEWS));
        assertNull(G.sortUrl(Sort.POPULAR_WEEK, home));
        assertNull(G.sortUrl(Sort.RATING, home));

        assertEquals(GENRE, sorted(Sort.NEW, GENRE));
        String views = sorted(Sort.POPULAR, GENRE);
        assertEquals(GENRE + "views/?period=all", views);
        assertEquals(GENRE + "views/page2/?period=all", G.pageUrl(views, 2));
        assertEquals("already there", views, G.sortUrl(Sort.POPULAR, views));
        assertEquals(GENRE, sorted(Sort.NEW, views));

        assertEquals(GENRE, G.genreOf(GENRE + "views/page2/?period=all"));
        assertEquals("a card's link without the slash", S + "/genre/detective/", G.genreOf(S + "/genre/detective"));
        assertNull(G.genreOf(Golosknig.GENRES));
        assertNull(G.genreOf(Golosknig.VIEWS));
        assertTrue("cards carry the time", G.listsDuration(Golosknig.INDEX));
        assertTrue(G.periods(Golosknig.VIEWS).isEmpty());
    }
}
