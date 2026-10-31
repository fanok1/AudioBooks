package com.fanok.audiobooks.source;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.fanok.audiobooks.BookPageFixtures;
import com.fanok.audiobooks.BookRemovedException;
import com.fanok.audiobooks.model.OtherSourceModel;
import com.fanok.audiobooks.net.JsonPage;
import com.fanok.audiobooks.pojo.AudioPOJO;
import com.fanok.audiobooks.pojo.BookPOJO;
import com.fanok.audiobooks.pojo.ComentsPOJO;
import com.fanok.audiobooks.pojo.DescriptionPOJO;
import com.fanok.audiobooks.pojo.GenrePOJO;
import com.fanok.audiobooks.pojo.OtherArtistPOJO;
import com.fanok.audiobooks.pojo.SeriesPOJO;
import com.fanok.audiobooks.search.ListingFilters;
import com.fanok.audiobooks.search.ProviderPage;
import com.fanok.audiobooks.search.SearchUrls;

import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** lis10book.com API answers saved on 2026-10-31 (see src/test/resources/fixtures/lis10book). */
public class Lis10bookTest {

    private static final Lis10book SITE = new Lis10book();
    private static final String S = Lis10book.SERVER;
    private static final String API = S + "/api/";
    private static final String GENRE = Lis10book.INDEX + "&genre=fantastika";
    private static final String AUTHOR = API + "authors/stiven-king?audio=full";
    private static final String READER = API + "narrators/oleg-buldakov?audio=full";
    private static final String BOOK = S + "/audio/zheleznoe-plamya/";

    private static JsonPage json(String name, String url) throws IOException {
        return BookPageFixtures.json("lis10book/" + name, url);
    }

    private static ProviderPage<BookPOJO> listing(String name, String url) throws IOException {
        return Lis10book.parseListing(json(name, url));
    }

    private static JsonPage book(String name, String slug) throws IOException {
        return json(name, API + "books/" + slug);
    }

    // ---------------------------------------------------------------- listings

    @Test
    public void indexCardsAndPages() throws IOException {
        ProviderPage<BookPOJO> page = listing("listing-index-p1.json", Lis10book.INDEX);
        assertEquals(ProviderPage.Status.OK, page.status);
        assertEquals(24, page.items.size());
        assertTrue("3533 pages", page.hasMore);
        BookPOJO first = page.items.get(0);
        assertEquals(S + "/audio/zhenshhina-v-chyornom/", first.getUrl());
        assertEquals("Женщина в чёрном", first.getName());
        assertEquals(S + "/wp-content/uploads/covers/849577.jpg", first.getPhoto());
        assertEquals("Сьюзен Хилл", first.getAutor());
        assertEquals(API + "authors/syuzen-hill?audio=full", first.getUrlAutor());
        assertEquals("Олег Булдаков", first.getArtist());
        assertEquals(READER, first.getUrlArtist());
        assertEquals("18660 s", "5 ч. 11 мин.", first.getTime());
        assertEquals("uknigRating 0 is no rating", "0", first.getReting());
        assertEquals(S + "/audio/bes-devka/", page.items.get(23).getUrl());

        ProviderPage<BookPOJO> second = listing("listing-index-p2.json", SITE.pageUrl(Lis10book.INDEX, 2));
        assertEquals(24, second.items.size());
        assertTrue(second.hasMore);
        assertEquals("Чужой муж из прошлого", second.items.get(0).getName());

        ProviderPage<BookPOJO> last = listing("listing-index-last.json", SITE.pageUrl(Lis10book.INDEX, 3533));
        assertEquals(6, last.items.size());
        assertFalse(last.hasMore);
        BookPOJO fandorin = last.items.get(0);
        assertEquals(S + "/audio/fandorin-nikolas-3-f-m/", fandorin.getUrl());
        assertEquals(S + "/wp-content/uploads/2023/09/400704_cover-1.jpg", fandorin.getPhoto());
        assertEquals("Борис Акунин", fandorin.getAutor());
        assertEquals("16 ч. 20 мин.", fandorin.getTime());
        assertEquals(S + "/audio/metro-3-02-apokrify-konecz-dorogi/", last.items.get(5).getUrl());

        ProviderPage<BookPOJO> beyond = listing("listing-index-beyond.json", SITE.pageUrl(Lis10book.INDEX, 3534));
        assertEquals(ProviderPage.Status.EMPTY, beyond.status);
        assertFalse(beyond.hasMore);
    }

    @Test
    public void sortedListingsPageOn() throws IOException {
        String popular = Lis10book.INDEX + "&sort=popular";
        BookPOJO top = listing("listing-popular-p1.json", popular).items.get(0);
        assertEquals(S + "/audio/zheleznoe-plamya/", top.getUrl());
        assertEquals("uknigRating", "4.43", top.getReting());
        ProviderPage<BookPOJO> second = listing("listing-popular-p2.json", SITE.pageUrl(popular, 2));
        assertEquals(S + "/audio/a-smolin-vedmak-04-chas-polnoluniya/", second.items.get(0).getUrl());
        assertTrue(second.hasMore);
        ProviderPage<BookPOJO> longest = listing("listing-duration-p1.json", Lis10book.INDEX + "&sort=duration");
        assertEquals(S + "/audio/polnoe-sobranie-sochinenij-v-vosmi-tomah/", longest.items.get(0).getUrl());
        assertEquals("496800 s", "138 ч.", longest.items.get(0).getTime());
    }

    /** A genre, an author, a reader and a series wrap their cards in {@code books}. */
    @Test
    public void genreAuthorReaderAndSeriesListings() throws IOException {
        ProviderPage<BookPOJO> genre = listing("genre-p1.json", GENRE);
        assertEquals(24, genre.items.size());
        assertTrue("603 pages", genre.hasMore);
        assertEquals("Дух воина", genre.items.get(0).getName());
        assertEquals(S + "/audio/v-predelah-normy/", genre.items.get(23).getUrl());
        assertEquals(S + "/audio/gorkie-zvyozdy/",
                listing("genre-p2.json", SITE.pageUrl(GENRE, 2)).items.get(0).getUrl());

        ProviderPage<BookPOJO> author = listing("author.json", AUTHOR);
        assertEquals(24, author.items.size());
        assertTrue("474 books", author.hasMore);
        assertEquals(S + "/audio/kratchajshij-put-dlya-missis-todd-3/", author.items.get(0).getUrl());
        assertEquals(S + "/audio/tot-kto-hochet-vyzhit-ostavshijsya-v-zhivyh/",
                listing("author-p2.json", SITE.pageUrl(AUTHOR, 2)).items.get(0).getUrl());
        ProviderPage<BookPOJO> small = listing("author-small.json", API + "authors/mishel-pejver?audio=full");
        assertEquals(2, small.items.size());
        assertFalse(small.hasMore);

        ProviderPage<BookPOJO> reader = listing("reader.json", READER);
        assertTrue("61 pages", reader.hasMore);
        assertEquals("Женщина в чёрном", reader.items.get(0).getName());
        assertEquals("Лайон Спрэг де Камп, Лин Картер", reader.items.get(1).getAutor());

        ProviderPage<BookPOJO> series = listing("series.json", API + "series/vedmak");
        assertEquals("all in one page", 46, series.items.size());
        assertFalse(series.hasMore);
        assertEquals(S + "/audio/doroga-bez-vozvrata/", series.items.get(0).getUrl());
    }

    @Test
    public void htmlGuardIsAnErrorNotAnEmptyPage() throws IOException {
        try {
            Lis10book.parseListing(BookPageFixtures.load("lis10book/guard.html", S + "/"));
            fail("the Turnstile guard is not a listing");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("not a JSON page"));
        }
    }

    @Test
    public void genres() throws IOException {
        ArrayList<GenrePOJO> genres = Lis10book.parseGenres(json("genres.json", Lis10book.GENRES));
        assertEquals(144, genres.size());
        assertEquals("Фантастика", genres.get(0).getName());
        assertEquals(GENRE, genres.get(0).getUrl());
        assertEquals("the genre list sorts by it", 19534, genres.get(0).getReting());
        assertEquals("Ранобэ и новеллы", genres.get(143).getName());
        assertEquals(1, genres.get(143).getReting());
        assertFalse(SITE.genres(json("genres.json", Lis10book.GENRES), Lis10book.GENRES, 1).hasMore);
    }

    // ---------------------------------------------------------------- search

    @Test
    public void search() throws IOException {
        String mag = SITE.searchUrl(SearchUrls.Category.BOOKS, "маг", 1);
        assertEquals(Lis10book.INDEX + "&q=%D0%BC%D0%B0%D0%B3&page=1", mag);
        ProviderPage<BookPOJO> first = SITE.searchResults(json("search-mag-p1.json", mag), 1);
        assertEquals(24, first.items.size());
        assertTrue("33 pages", first.hasMore);
        assertEquals(S + "/audio/privet-magiya-pesochnicza-kniga-shestaya/", first.items.get(0).getUrl());
        assertEquals(S + "/audio/spontannaya-magiya/", first.items.get(23).getUrl());

        String two = SITE.searchUrl(SearchUrls.Category.BOOKS, "маг", 2);
        assertEquals(Lis10book.INDEX + "&q=%D0%BC%D0%B0%D0%B3&page=2", two);
        ProviderPage<BookPOJO> second = SITE.searchResults(json("search-mag-p2.json", two), 2);
        assertEquals(S + "/audio/magazinchik-nuzhnyh-slov/", second.items.get(0).getUrl());
        assertTrue(second.hasMore);

        ProviderPage<BookPOJO> none = SITE.searchResults(
                json("search-none.json", SITE.searchUrl(SearchUrls.Category.BOOKS, "zzzxqw", 1)), 1);
        assertEquals(ProviderPage.Status.EMPTY, none.status);
        assertFalse(none.hasMore);

        assertEquals("a search is not re-sorted", 0, SITE.sorts(mag).size());
        assertEquals(Lis10book.INDEX + "&q=%D0%9C%D0%B0%D1%81%D1%82%D0%B5%D1%80+%D0%B8+%D0%9C%D0%B0%D1%80%D0%B3%D0%B0"
                + "%D1%80%D0%B8%D1%82%D0%B0&page=1",
                SITE.searchUrl(SearchUrls.Category.BOOKS, "Мастер и Маргарита", 1));
    }

    /** The site searches titles only: "on other sites" asks for the title, 48 cards at once. */
    @Test
    public void otherSourceSearchByTitle() throws IOException {
        BookPOJO book = new BookPOJO();
        book.setName("Мастер и Маргарита");
        book.setAutor("Михаил Булгаков");
        book.setArtist("Олег Ефремов");
        assertEquals("Мастер и Маргарита", SITE.otherSourceTerms(book));
        String url = SITE.otherSourceSearchUrl(SITE.otherSourceTerms(book), 1);
        assertTrue(url, url.endsWith("&page=1&limit=48"));

        ArrayList<OtherSourceModel.Hit> hits = SITE.otherSourceHits(json("other-source-search-title.json", url));
        assertEquals(9, hits.size());
        OtherSourceModel.Hit hit = hits.get(1);
        assertEquals(S + "/audio/master-i-margarita-8/", hit.url);
        assertEquals("Мастер и Маргарита", hit.title);
        assertEquals("Михаил Афанасьевич Булгаков", hit.author);
        assertEquals("Медведь Обыкновенный", hit.reader);
        assertTrue("title and author find nothing",
                SITE.otherSourceHits(json("other-source-search.json", url)).isEmpty());
    }

    // ---------------------------------------------------------------- people

    @Test
    public void people() throws IOException {
        assertEquals(Lis10book.AUTHORS + "?page=2", SITE.pageUrl(Lis10book.AUTHORS, 2));
        ProviderPage<GenrePOJO> authors = SITE.people(json("people-authors-p1.json", Lis10book.AUTHORS),
                Lis10book.AUTHORS, 1, false);
        assertEquals(60, authors.items.size());
        assertTrue("totalPages says 1 of 29850 authors", authors.hasMore);
        assertEquals("Антон Павлович Чехов", authors.items.get(0).getName());
        assertEquals(API + "authors/anton-chehov?audio=full", authors.items.get(0).getUrl());
        assertEquals(738, authors.items.get(0).getReting());
        assertTrue(Lis10book.parsePeople(json("people-authors-p2.json", Lis10book.AUTHORS + "?page=2"), false).hasMore);

        ProviderPage<GenrePOJO> readers = SITE.people(json("people-readers-p1.json", Lis10book.READERS),
                Lis10book.READERS, 1, true);
        assertEquals("Вячеслав Герасимов", readers.items.get(0).getName());
        assertEquals(API + "narrators/vyacheslav-gerasimov?audio=full", readers.items.get(0).getUrl());

        String king = SITE.searchUrl(SearchUrls.Category.AUTHORS, "Кинг", 1);
        assertEquals(Lis10book.AUTHORS + "?q=%D0%9A%D0%B8%D0%BD%D0%B3&page=1", king);
        ProviderPage<GenrePOJO> found = SITE.searchPeople(json("people-authors-search.json", king), 1, false);
        assertEquals(39, found.items.size());
        assertFalse(found.hasMore);
        assertEquals(AUTHOR, found.items.get(0).getUrl());
        String kozij = SITE.searchUrl(SearchUrls.Category.PERFORMERS, "Козий", 1);
        assertTrue(kozij, kozij.startsWith(Lis10book.READERS + "?q="));
        assertEquals(API + "narrators/nikolaj-kozij?audio=full",
                SITE.searchPeople(json("people-readers-search.json", kozij), 1, true).items.get(0).getUrl());
    }

    // ---------------------------------------------------------------- sorts

    /** As BooksPresenter does it ({@code ListingFilters.sorted}): the site-wide sort, then narrowed to the listing. */
    private static String sorted(Sort sort, String listing) {
        String url = SITE.sortUrl(sort, listing);
        String genre = SITE.genreOf(listing);
        return url.equals(listing) || genre == null ? url : SITE.inGenre(url, genre);
    }

    @Test
    public void sortMenus() {
        List<Sort> menu = Arrays.asList(Sort.NEW, Sort.POPULAR, Sort.RATING, Sort.DURATION);
        for (String listing : new String[]{Lis10book.INDEX, GENRE, AUTHOR, READER}) {
            List<Sort.Entry> entries = SITE.sorts(listing);
            assertEquals(listing, menu.size(), entries.size());
            for (int i = 0; i < menu.size(); i++) {
                assertEquals(listing, menu.get(i), entries.get(i).sort);
            }
            for (Sort sort : menu) {
                String url = sorted(sort, listing);
                assertEquals(url + " keeps its menu", 4, SITE.sorts(url).size());
                assertEquals(url + " " + sort, url, sorted(sort, url));
            }
        }
        assertTrue("a series keeps its order", SITE.sorts(API + "series/vedmak").isEmpty());
        assertTrue(SITE.sorts(Lis10book.AUTHORS).isEmpty());
        assertTrue(SITE.sorts(Lis10book.GENRES).isEmpty());
        assertNull(SITE.sortUrl(Sort.COMMENTS, Lis10book.INDEX));
        assertNull(SITE.sortUrl(Sort.POPULAR_WEEK, Lis10book.INDEX));
    }

    @Test
    public void sortsSiteWideAndWithinTheListing() {
        String popular = Lis10book.INDEX + "&sort=popular";
        assertEquals(popular, sorted(Sort.POPULAR, Lis10book.INDEX));
        assertEquals(Lis10book.INDEX + "&sort=rating", sorted(Sort.RATING, popular));
        assertEquals(Lis10book.INDEX, sorted(Sort.NEW, SITE.pageUrl(Lis10book.INDEX + "&sort=duration", 3)));
        assertNull(SITE.genreOf(popular));

        assertEquals(GENRE, SITE.genreOf(GENRE + "&sort=rating&page=3"));
        assertEquals(GENRE + "&sort=duration", sorted(Sort.DURATION, GENRE + "&sort=popular"));
        assertEquals(GENRE, sorted(Sort.NEW, GENRE + "&sort=rating"));
        assertEquals(GENRE + "&sort=duration", SITE.inGenre(Lis10book.INDEX + "&sort=duration", GENRE));
        assertEquals(AUTHOR + "&sort=popular", sorted(Sort.POPULAR, AUTHOR));
        assertEquals(READER + "&sort=rating", sorted(Sort.RATING, READER + "&sort=duration"));

        assertEquals(Lis10book.INDEX + "&page=2", SITE.pageUrl(Lis10book.INDEX, 2));
        assertEquals(popular + "&page=2", SITE.pageUrl(popular, 2));
        assertEquals(popular + "&page=3", SITE.pageUrl(popular + "&page=2", 3));
        assertEquals(GENRE + "&sort=rating&page=2", SITE.pageUrl(sorted(Sort.RATING, GENRE), 2));
    }

    @Test
    public void durationBuckets() {
        assertEquals(Lis10book.INDEX + "&dur=short", SITE.withDuration(Lis10book.INDEX,
                ListingFilters.Duration.UNDER_30M));
        assertEquals("short", Lis10book.bucket(ListingFilters.Duration.FROM_1H_TO_2H));
        assertEquals("short", Lis10book.bucket(ListingFilters.Duration.of(0, 180)));
        assertEquals("medium", Lis10book.bucket(ListingFilters.Duration.of(180, 600)));
        assertEquals("medium", Lis10book.bucket(ListingFilters.Duration.of(240, 300)));
        assertEquals("long", Lis10book.bucket(ListingFilters.Duration.of(600, ListingFilters.Duration.UNBOUNDED)));
        assertEquals("long", Lis10book.bucket(ListingFilters.Duration.of(900, 1200)));
        assertNull("across short and medium", Lis10book.bucket(ListingFilters.Duration.OVER_2H));
        assertNull(Lis10book.bucket(ListingFilters.Duration.of(120, 240)));
        assertNull(Lis10book.bucket(ListingFilters.Duration.ANY));

        String genreLong = SITE.withDuration(GENRE + "&sort=rating", ListingFilters.Duration.of(600, 900));
        assertEquals(GENRE + "&sort=rating&dur=long", genreLong);
        assertEquals("idempotent", genreLong, SITE.withDuration(genreLong, ListingFilters.Duration.of(600, 900)));
        assertEquals(GENRE + "&sort=rating", SITE.withDuration(genreLong, ListingFilters.Duration.ANY));
        assertEquals(AUTHOR + "&dur=medium", SITE.withDuration(AUTHOR, ListingFilters.Duration.of(180, 360)));
        assertEquals("the series ignores it", API + "series/vedmak",
                SITE.withDuration(API + "series/vedmak", ListingFilters.Duration.UNDER_30M));
        assertEquals("the bound survives a sort", GENRE + "&dur=long&sort=popular",
                sorted(Sort.POPULAR, genreLong));
    }

    // ---------------------------------------------------------------- book

    @Test
    public void description() throws IOException {
        DescriptionPOJO d = SITE.description(book("book.json", "zheleznoe-plamya"), BOOK);
        assertEquals("Железное пламя", d.getTitle());
        assertEquals(S + "/wp-content/uploads/2025/08/824264_cover.jpg", d.getPoster());
        assertEquals("Ребекка Яррос", d.getAutor());
        assertEquals(API + "authors/rebekka-yarros?audio=full", d.getAutorUrl());
        assertEquals("Коллективный проект", d.getArtist());
        assertEquals(API + "narrators/kollektivnyj-proekt?audio=full", d.getArtistUrl());
        assertEquals("Героическое фэнтези", d.getGenre());
        assertEquals(Lis10book.INDEX + "&genre=geroicheskoe-fentezi", d.getGenreUrl());
        assertEquals("Эмпирей", d.getSeries());
        assertEquals(API + "series/empirej", d.getSeriesUrl());
        assertEquals("25200 s", "7 ч.", d.getTime());
        assertEquals(BookPOJO.formatRating(4.43f, "40"), d.getReiting());
        assertTrue(d.getDescription(), d.getDescription().startsWith("Никто не ожидал, что Вайолет Сорренгейл"));
        assertFalse(d.getDescription().contains("<p>"));
        assertFalse(d.isOtherReader());

        BookPOJO card = SITE.book(book("book.json", "zheleznoe-plamya"), BOOK);
        assertEquals(BOOK, card.getUrl());
        assertEquals("Железное пламя", card.getName());
        assertEquals("Ребекка Яррос", card.getAutor());
        assertEquals("Героическое фэнтези", card.getGenre());

        DescriptionPOJO fragment = Lis10book.parseDescription(book("book-fragment.json", "tajnaya-krasota"));
        assertEquals("Люсинда Райли, Гарри Уиттакер", fragment.getAutor());
        assertEquals(API + "authors/lyusinda-rajli?audio=full", fragment.getAutorUrl());
        assertEquals("", fragment.getReiting());
        assertEquals("", fragment.getSeries());
    }

    @Test
    public void series() throws IOException {
        DescriptionPOJO d = Lis10book.parseDescription(book("book-series.json", "a-smolin-vedmak-04-chas-polnoluniya"));
        assertEquals("А. Смолин, ведьмак: 04. Час полнолуния", d.getTitle());
        assertEquals("Андрей Васильев", d.getAutor());
        assertEquals("Сергей Горбунов", d.getArtist());
        assertEquals("the first of three", "Ведьмак", d.getSeries());
        assertEquals(API + "series/vedmak", d.getSeriesUrl());

        ArrayList<SeriesPOJO> vedmak = SITE.series(book("book-series.json", "a-smolin-vedmak-04-chas-polnoluniya"),
                S + "/audio/a-smolin-vedmak-04-chas-polnoluniya/");
        assertEquals(46, vedmak.size());
        assertEquals(S + "/audio/doroga-bez-vozvrata/", vedmak.get(0).getUrl());
        assertEquals("Дорога без возврата", vedmak.get(0).getName());
        assertEquals("1", vedmak.get(0).getNumber());
        assertEquals(S + "/wp-content/uploads/2023/09/407597_cover-1.jpg", vedmak.get(0).getPhoto());
        assertEquals(S + "/audio/perekrestok-voronov/", vedmak.get(45).getUrl());
        assertEquals("9", vedmak.get(45).getNumber());

        ArrayList<SeriesPOJO> empire = Lis10book.parseSeries(book("book.json", "zheleznoe-plamya"));
        assertEquals(3, empire.size());
        assertEquals("Четвертое крыло", empire.get(0).getName());
        assertEquals("2", empire.get(1).getNumber());
        assertEquals(BOOK, empire.get(1).getUrl());
        assertTrue(Lis10book.parseSeries(book("book-alt-narration.json", "master-i-margarita-7")).isEmpty());
    }

    @Test
    public void otherReaders() throws IOException {
        JsonPage page = book("book-alt-narration.json", "master-i-margarita-7");
        DescriptionPOJO d = Lis10book.parseDescription(page);
        assertEquals("Мастер и Маргарита", d.getTitle());
        assertEquals("10 ч. 8 мин.", d.getTime());
        assertEquals("Ужасы", d.getGenre());
        assertTrue(d.isOtherReader());
        assertEquals(1, d.getOtherReaders().size());
        ArrayList<OtherArtistPOJO> others = Lis10book.parseOtherReaders(page);
        assertEquals("Исполнитель Медведь Обыкновенный", others.get(0).getName());
        assertEquals(S + "/audio/master-i-margarita-8/", others.get(0).getUrl());
        assertTrue(SITE.findsOtherReaders());
        assertFalse("the record tells", SITE.probesOtherReaders());
    }

    @Test
    public void recommendations() throws IOException {
        JsonPage page = Lis10book.withSimilar(book("book.json", "zheleznoe-plamya"),
                json("similar.json", API + "books/zheleznoe-plamya/similar"));
        ArrayList<BookPOJO> similar = SITE.recommendations(page, BOOK);
        assertEquals(12, similar.size());
        assertEquals(S + "/audio/oniksovyj-shtorm/", similar.get(0).getUrl());
        assertEquals("Ониксовый шторм", similar.get(0).getName());
        assertEquals("Ребекка Яррос", similar.get(0).getAutor());
        assertEquals(S + "/wp-content/uploads/2025/08/824266_cover.jpg", similar.get(0).getPhoto());
        assertEquals("Железное пламя", Lis10book.parseDescription(page).getTitle());
        assertTrue("a record without them",
                Lis10book.parseRecommendations(book("book.json", "zheleznoe-plamya")).isEmpty());
    }

    @Test
    public void comments() throws IOException {
        ArrayList<ComentsPOJO> comments = Lis10book.parseComments(
                json("comments.json", API + "books/zheleznoe-plamya/comments?limit=50&offset=0"));
        assertEquals(10, comments.size());
        ComentsPOJO first = comments.get(0);
        assertEquals("Anastasia ✈️ Mikhaylova", first.getName());
        assertEquals("24.09.2026", first.getDate());
        assertEquals("+5", first.getReting());
        assertTrue(first.getText().startsWith("Книги классные, озвучка просто топ!"));
        assertEquals("2 up, 1 down", "+1", comments.get(1).getReting());
        ComentsPOJO olga = comments.get(2);
        assertEquals("Ольга", olga.getName());
        assertEquals(1, olga.getChildComents().size());
        assertEquals("Гость", olga.getChildComents().get(0).getName());
        assertEquals("Ольга", olga.getChildComents().get(0).getParentName());
        assertEquals(10, Lis10book.parseComments(
                json("comments-p2.json", API + "books/zheleznoe-plamya/comments?limit=50&offset=10")).size());
    }

    // ---------------------------------------------------------------- chapters

    @Test
    public void tracks() throws IOException {
        ArrayList<AudioPOJO> tracks = Lis10book.parseTracks(json("playlist.json", API + "p/zheleznoe-plamya"), BOOK);
        assertEquals(66, tracks.size());
        AudioPOJO first = tracks.get(0);
        assertEquals("1", first.getName());
        assertEquals("https://s1.fantbox.net/books/824264/4421444.mp3", first.getUrl());
        assertEquals("Железное пламя", first.getBookName());
        assertEquals("the playlist has no durations", 0, first.getTime());
        assertEquals("66", tracks.get(65).getName());
        assertEquals("https://s1.fantbox.net/books/824264/4421509.mp3", tracks.get(65).getUrl());

        ArrayList<AudioPOJO> uknig = Lis10book.parseTracks(
                json("playlist-uknig.json", API + "p/zhenshhina-v-chyornom"), S + "/audio/zhenshhina-v-chyornom/");
        assertEquals(12, uknig.size());
        assertEquals("sjuzen-khill-zhenshhina-v-chjornom-01", uknig.get(0).getName());
        assertEquals("https://s2.knizhin.net/books/849577/4990215.mp3", uknig.get(0).getUrl());

        ArrayList<AudioPOJO> single = Lis10book.parseTracks(
                json("playlist-single.json", API + "p/master-i-margarita-7"), S + "/audio/master-i-margarita-7/");
        assertEquals("one file for the whole book", 1, single.size());
        assertEquals("https://s1.fantbox.net/books/806894/3967018.mp3", single.get(0).getUrl());

        assertTrue("the media hosts serve other sites too", SITE.hosts().isEmpty());
        assertNull("the media hosts take the default Referer, the book page", SITE.mediaHeaders(BOOK));
        assertNull(SITE.referer());
    }

    @Test
    public void premiumBookHasNoTracks() throws IOException {
        String url = S + "/audio/tajnaya-krasota/";
        try {
            Lis10book.parseTracks(json("playlist-blocked.json", API + "p/tajnaya-krasota"), url);
            fail("a blocked playlist is not the book");
        } catch (BookRemovedException expected) {
            assertEquals(url, expected.getMessage());
        }
        try {
            // donorTier "fragment": refused before its playlist is asked for (no network here)
            SITE.tracks(book("book-fragment.json", "tajnaya-krasota"), API + "books/tajnaya-krasota");
            fail("a fragment is not the book");
        } catch (BookRemovedException expected) {
            assertEquals(url, expected.getMessage());
        }
        assertEquals("Тайная красота",
                Lis10book.parseDescription(book("book-fragment.json", "tajnaya-krasota")).getTitle());
    }
}
