package com.fanok.audiobooks.source;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.fanok.audiobooks.BookRemovedException;
import com.fanok.audiobooks.model.BookPages;
import com.fanok.audiobooks.model.ComentsModel;
import com.fanok.audiobooks.model.OtherArtistModel;
import com.fanok.audiobooks.model.OtherSourceModel;
import com.fanok.audiobooks.net.JsonPage;
import com.fanok.audiobooks.pojo.AudioPOJO;
import com.fanok.audiobooks.pojo.BookPOJO;
import com.fanok.audiobooks.pojo.ComentsPOJO;
import com.fanok.audiobooks.pojo.DescriptionPOJO;
import com.fanok.audiobooks.pojo.GenrePOJO;
import com.fanok.audiobooks.pojo.OtherArtistPOJO;
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
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** aknigi24.com pages saved on 2026-10-31 (see src/test/resources/fixtures/aknigi24). */
public class Aknigi24Test {

    private static final Aknigi24 SITE = new Aknigi24();
    private static final String BOOK = Aknigi24.SERVER
            + "/book/garri-potter-i-metody-racionalnogo-mysleniia-iudkovski-eliezer-ali-wodan";
    private static final String GENRE = Aknigi24.SERVER + "/genre/religiia";

    private static InputStream open(String name) {
        InputStream in = Aknigi24Test.class.getResourceAsStream("/fixtures/aknigi24/" + name);
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

    private static ProviderPage<BookPOJO> listing(String name, String url) throws IOException {
        return SITE.listing(page(name, url), url, 1);
    }

    // ---------------------------------------------------------------- listings

    @Test
    public void indexCards() throws IOException {
        ProviderPage<BookPOJO> page = listing("listing-index-p1.html", Aknigi24.INDEX);
        assertEquals(ProviderPage.Status.OK, page.status);
        assertEquals("the sidebar's books are no cards", 21, page.items.size());
        assertTrue(page.hasMore);
        BookPOJO first = page.items.get(0);
        assertEquals(Aknigi24.SERVER + "/book/na-vsiakogo-mudreca-dovolno-prostoty-ostrovskii-aleksandr-velixov-evgenii"
                + "-carev-mixail-ryzova-varvara-ilinskii-igor-zubov-konstantin-turcaninova", first.getUrl());
        assertEquals("На всякого мудреца довольно простоты", first.getName());
        assertEquals(Aknigi24.SERVER + "/cover/audiobooks/covers/audiospektakli/na-vsiakogo-mudreca-dovolno-prostoty"
                + "-ostrovskii-aleksandr-velixov-evgenii-carev-mixail-ryzova-varvara-ilinskii-igor-zubov-konstantin"
                + "-turcaninova.jpg", first.getPhoto());
        assertEquals("Островский Александр", first.getAutor());
        assertEquals(Aknigi24.SERVER + "/author/ostrovskii-aleksandr", first.getUrlAutor());
        assertEquals("all ten readers of the \"и др.\" title", "Велихов Евгений, Царёв Михаил, Рыжова Варвара, "
                + "Ильинский Игорь, Зубов Константин, Турчанинова Евдокия, Садовский Михаил, Карнович Наталья, "
                + "Орлова Вера, Головин Владимир", first.getArtist());
        assertEquals(Aknigi24.SERVER + "/reader/velixov-evgenii", first.getUrlArtist());
        assertEquals("Классика", first.getGenre());
        assertEquals(Aknigi24.SERVER + "/genre/klassika?sort=new", first.getUrlGenre());
        assertEquals("2 ч. 45 мин.", first.getTime());
        assertEquals("4.33", first.getReting());

        BookPOJO second = page.items.get(1);
        assertEquals("Горбун", second.getName());
        assertEquals("Шерлок Холмс", second.getSeries());
        assertEquals(Aknigi24.SERVER + "/serie/serlok-xolms", second.getUrlSeries());
        assertEquals("Сушков Владимир", second.getArtist());
        assertEquals("51 мин.", second.getTime());

        BookPOJO last = page.items.get(20);
        assertEquals("Кукольный домик", last.getName());
        assertEquals("no rating badge", "", last.getReting());
    }

    @Test
    public void indexPagesOnAndEnds() throws IOException {
        ProviderPage<BookPOJO> p2 = listing("listing-index-p2.html", SITE.pageUrl(Aknigi24.INDEX, 2));
        assertEquals(21, p2.items.size());
        assertTrue(p2.hasMore);
        BookPOJO first = p2.items.get(0);
        assertEquals("Флибустьер. Вест-Индия", first.getName());
        assertEquals("12 ч.", first.getTime());
        assertEquals("3.48", first.getReting());
        assertEquals("Ахманов Михаил", first.getAutor());
        assertEquals("Ващенко Семён", first.getArtist());
        assertEquals("Белый какаду доктора Хазельмайера", p2.items.get(20).getName());

        ProviderPage<BookPOJO> last = listing("listing-index-last.html", SITE.pageUrl(Aknigi24.INDEX, 1343));
        assertEquals(15, last.items.size());
        assertFalse(last.hasMore);
        assertEquals("Девятый час", last.items.get(0).getName());
        assertEquals("Доспех судьбы", last.items.get(14).getName());
    }

    @Test
    public void sortedListings() throws IOException {
        ProviderPage<BookPOJO> rating = listing("listing-rating-p1.html", Aknigi24.INDEX + "?sort=rating");
        assertEquals("Последняя ночь", rating.items.get(0).getName());
        assertEquals("5.00", rating.items.get(0).getReting());
        assertEquals("the site writes й decomposed", "Маленький фермер",
                Normalizer.normalize(rating.items.get(20).getName(), Normalizer.Form.NFC));
        assertEquals("У серебряного озера",
                listing("listing-rating-p2.html", Aknigi24.INDEX + "?sort=rating&page=2").items.get(0).getName());

        ProviderPage<BookPOJO> title = listing("listing-title-p1.html", Aknigi24.INDEX + "?sort=title");
        assertEquals("... и Батиста был рожден", title.items.get(0).getName());
        assertEquals("\"Сделай сам\"", title.items.get(20).getName());

        ProviderPage<BookPOJO> duration = listing("listing-duration-p1.html", Aknigi24.INDEX + "?sort=duration");
        BookPOJO longest = duration.items.get(0);
        assertEquals("Боевой Континент Непревзойденный Клан Тан", longest.getName());
        assertEquals("379 ч. 19 мин.", longest.getTime());
        assertEquals("Tang Jia San Shao", longest.getAutor());
        assertEquals("82 ч. 31 мин.", duration.items.get(20).getTime());

        ProviderPage<BookPOJO> genreNew = listing("listing-genre-new-p1.html", GENRE + "?sort=new");
        assertEquals("Деяния святых апостолов", genreNew.items.get(0).getName());
        assertEquals("", genreNew.items.get(0).getReting());
        assertEquals("Взгляд за кулисы", genreNew.items.get(20).getName());
    }

    @Test
    public void genrePages() throws IOException {
        ProviderPage<BookPOJO> p1 = listing("genre-p1.html", GENRE);
        assertEquals(21, p1.items.size());
        assertTrue(p1.hasMore);
        BookPOJO bible = p1.items.get(0);
        assertEquals("Библия. Ветхий Завет", bible.getName());
        assertNull("no author row", bible.getAutor());
        assertEquals("Оргин Дмитрий", bible.getArtist());
        assertEquals("Библия", bible.getSeries());
        assertEquals("65 ч. 16 мин.", bible.getTime());
        assertEquals("5.00", bible.getReting());

        ProviderPage<BookPOJO> p2 = listing("genre-p2.html", GENRE + "?page=2");
        assertEquals("Николушка", p2.items.get(0).getName());
        assertEquals("38 мин.", p2.items.get(0).getTime());
        assertEquals("Почему Иисус любил падших женщин", p2.items.get(20).getName());

        ProviderPage<BookPOJO> last = listing("genre-last.html", GENRE + "?page=5");
        assertEquals(5, last.items.size());
        assertFalse(last.hasMore);
        assertEquals("Мастерство любви", last.items.get(0).getName());
        assertEquals("Блез Паскаль: Логика веры", last.items.get(4).getName());
    }

    @Test
    public void peopleAndSeriesListings() throws IOException {
        ProviderPage<BookPOJO> author = listing("author.html", Aknigi24.SERVER + "/author/king-stiven");
        assertEquals(21, author.items.size());
        assertTrue(author.hasMore);
        assertEquals("Салимов удел", author.items.get(0).getName());
        BookPOJO shining = listing("author-p2.html", Aknigi24.SERVER + "/author/king-stiven?page=2").items.get(0);
        assertEquals("Сияние", shining.getName());
        assertEquals("Дэнни Торранс", shining.getSeries());

        ProviderPage<BookPOJO> reader = listing("reader.html", Aknigi24.SERVER + "/reader/kniazev-igor");
        assertEquals(21, reader.items.size());
        assertEquals("Песочный человек", reader.items.get(0).getName());

        ProviderPage<BookPOJO> series = listing("series.html", Aknigi24.SERVER + "/serie/serlok-xolms");
        assertEquals(17, series.items.size());
        assertFalse("no pager at all", series.hasMore);
    }

    @Test
    public void genreList() throws IOException {
        List<GenrePOJO> genres = SITE.genres(page("genres.html", Aknigi24.GENRES), Aknigi24.GENRES, 1).items;
        assertEquals(25, genres.size());
        GenrePOJO first = genres.get(0);
        assertEquals("Фантастика, фэнтези", first.getName());
        assertEquals(Aknigi24.SERVER + "/genre/fantastika-fentezi?sort=new", first.getUrl());
        assertEquals("8917 книг", first.getDescription());
        assertEquals(8917, first.getReting());
        GenrePOJO last = genres.get(24);
        assertEquals("На иностранных языках", last.getName());
        assertEquals(24, last.getReting());
    }

    // ---------------------------------------------------------------- URLs and sorts

    @Test
    public void pagesAndSearchUrls() {
        assertEquals(Aknigi24.INDEX, SITE.pageUrl(Aknigi24.INDEX, 1));
        assertEquals("https://aknigi24.com/?page=2", SITE.pageUrl(Aknigi24.INDEX, 2));
        assertEquals("https://aknigi24.com/?sort=rating&page=3", SITE.pageUrl(Aknigi24.INDEX + "?sort=rating", 3));
        assertEquals("https://aknigi24.com/genre/religiia?sort=new&page=2", SITE.pageUrl(GENRE + "?sort=new", 2));
        assertEquals("https://aknigi24.com/author/king-stiven?page=2",
                SITE.pageUrl("https://aknigi24.com/author/king-stiven", 2));
        assertEquals("https://aknigi24.com/search?q=%D0%9A%D0%B8%D0%BD%D0%B3&page=2",
                SITE.searchUrl(SearchUrls.Category.BOOKS, "Кинг", 2));
        assertEquals("https://aknigi24.com/authors?search=%D0%BA%D0%B8%D0%BD%D0%B3&page=1",
                SITE.searchUrl(SearchUrls.Category.AUTHORS, "кинг", 1));
        assertEquals("https://aknigi24.com/readers?search=a+b&page=1",
                SITE.searchUrl(SearchUrls.Category.PERFORMERS, "a b", 1));
        assertEquals("https://aknigi24.com/authors?letter=%D0%9A&page=2", Aknigi24.letterUrl(false, 'К', 2));
        assertEquals("https://aknigi24.com/readers?letter=A", Aknigi24.letterUrl(true, 'A', 1));
    }

    /** As ListingFilters.sorted does it: the site-wide sort, then narrowed to the genre the listing is in. */
    private static String sorted(Sort sort, String listing) {
        String url = SITE.sortUrl(sort, listing);
        String genre = SITE.genreOf(listing);
        return url == null || url.equals(listing) || genre == null ? url : SITE.inGenre(url, genre);
    }

    @Test
    public void sortsSiteWideAndInGenre() {
        List<Sort.Entry> menu = SITE.sorts(Aknigi24.INDEX);
        assertEquals(4, menu.size());
        assertEquals(Sort.NEW, menu.get(0).sort);
        assertEquals(Sort.RATING, menu.get(1).sort);
        assertEquals(Sort.TITLE, menu.get(2).sort);
        assertEquals(Sort.DURATION, menu.get(3).sort);

        String home = Aknigi24.INDEX;
        assertEquals(home, sorted(Sort.NEW, home));
        assertEquals("https://aknigi24.com/?sort=rating", sorted(Sort.RATING, home));
        assertEquals("https://aknigi24.com/?sort=title", sorted(Sort.TITLE, home));
        assertEquals("https://aknigi24.com/?sort=duration", sorted(Sort.DURATION, home));
        assertEquals(home, sorted(Sort.NEW, "https://aknigi24.com/?sort=duration"));
        assertNull(SITE.sortUrl(Sort.POPULAR, home));
        assertNull(SITE.sortUrl(Sort.COMMENTS, home));
        assertEquals("a sorted listing keeps its menu", menu, SITE.sorts("https://aknigi24.com/?sort=title"));

        String genre = GENRE + "?sort=new";
        assertEquals(GENRE, SITE.genreOf(genre));
        assertEquals(GENRE, SITE.genreOf(GENRE + "?sort=duration&page=2"));
        assertNull(SITE.genreOf(home));
        assertEquals(menu, SITE.sorts(genre));
        assertEquals(genre, sorted(Sort.NEW, genre));
        assertEquals(GENRE + "?sort=rating", sorted(Sort.RATING, genre));
        assertEquals(GENRE + "?sort=title", sorted(Sort.TITLE, genre));
        assertEquals(GENRE + "?sort=duration", sorted(Sort.DURATION, genre));
        assertEquals(genre, sorted(Sort.NEW, GENRE + "?sort=duration"));
        assertEquals("the plain genre (rating) gets the explicit sort", GENRE + "?sort=rating",
                sorted(Sort.RATING, GENRE));

        assertTrue(SITE.sorts("https://aknigi24.com/author/king-stiven").isEmpty());
        assertTrue(SITE.sorts("https://aknigi24.com/reader/kniazev-igor?page=2").isEmpty());
        assertTrue(SITE.sorts("https://aknigi24.com/serie/serlok-xolms").isEmpty());
        assertTrue(SITE.sorts(SITE.searchUrl(SearchUrls.Category.BOOKS, "Кинг", 1)).isEmpty());
        assertTrue(SITE.listsDuration(home));
    }

    // ---------------------------------------------------------------- search

    @Test
    public void search() throws IOException {
        String url = SITE.searchUrl(SearchUrls.Category.BOOKS, "Кинг", 1);
        ProviderPage<BookPOJO> p1 = SITE.searchResults(page("search-king-p1.html", url), 1);
        assertEquals(21, p1.items.size());
        assertTrue(p1.hasMore);
        assertEquals("МногоГолосье. Стивен Кинг 2", p1.items.get(0).getName());
        assertEquals("МногоГолосье", p1.items.get(0).getSeries());
        assertEquals("Лавка дурных снов (сборник)", p1.items.get(20).getName());

        ProviderPage<BookPOJO> p2 = SITE.searchResults(page("search-king-p2.html",
                SITE.searchUrl(SearchUrls.Category.BOOKS, "Кинг", 2)), 2);
        assertEquals("Глубина. Погружение 20-е", p2.items.get(0).getName());
        assertTrue(p2.items.get(0).getAutor().startsWith("Слизар Генри, Кинг Стивен"));
        assertEquals("Глубина. Погружение 46-е", p2.items.get(20).getName());

        ProviderPage<BookPOJO> last = SITE.searchResults(page("search-king-last.html",
                SITE.searchUrl(SearchUrls.Category.BOOKS, "Кинг", 24)), 24);
        assertEquals(4, last.items.size());
        assertFalse(last.hasMore);
        assertEquals("Сборник радиоспектаклей №13", last.items.get(3).getName());

        ProviderPage<BookPOJO> none = SITE.searchResults(page("search-none.html",
                SITE.searchUrl(SearchUrls.Category.BOOKS, "zzzxqw", 1)), 1);
        assertEquals("the sidebar's new books are no results", ProviderPage.Status.EMPTY, none.status);
    }

    @Test
    public void otherSourceAndOtherReaders() throws IOException {
        Document search = page("other-source-search.html",
                SITE.otherSourceSearchUrl("Мастер и Маргарита Булгаков", 1));
        List<OtherSourceModel.Hit> hits = SITE.otherSourceHits(search);
        assertEquals("Мастер и Маргарита", hits.get(0).title);
        assertEquals("Булгаков Михаил", hits.get(0).author);
        assertEquals("Ефремов Олег", hits.get(0).reader);
        assertEquals("Манылов Вячеслав", hits.get(1).reader);

        String efremov = Aknigi24.SERVER + "/book/master-i-margarita-bulgakov-mixail-efremov-oleg";
        ArrayList<OtherArtistPOJO> others = OtherArtistModel.otherReaders(hits, "Мастер и Маргарита",
                "Булгаков Михаил", efremov, "Ефремов Олег");
        assertEquals("the same title of another author is none", 2, others.size());
        assertEquals("Исполнитель Манылов Вячеслав", others.get(0).getName());
        assertEquals(Aknigi24.SERVER + "/book/master-i-margarita-bulgakov-mixail-manylov-viaceslav",
                others.get(0).getUrl());
        assertEquals(Aknigi24.SERVER + "/book/master-i-margarita-bulgakov-mixail-aktery-teatrov",
                others.get(1).getUrl());
        assertTrue(SITE.findsOtherReaders());
    }

    // ---------------------------------------------------------------- people

    @Test
    public void people() throws IOException {
        ProviderPage<GenrePOJO> letter = Aknigi24.parsePeople(page("people-authors-letter-p1.html",
                Aknigi24.letterUrl(false, 'К', 1)));
        assertEquals(60, letter.items.size());
        assertTrue(letter.hasMore);
        GenrePOJO first = letter.items.get(0);
        assertEquals("Кабаков Александр", first.getName());
        assertEquals(Aknigi24.SERVER + "/author/kabakov-aleksandr", first.getUrl());
        assertEquals(1, first.getReting());
        ProviderPage<GenrePOJO> p2 = Aknigi24.parsePeople(page("people-authors-letter-p2.html",
                Aknigi24.letterUrl(false, 'К', 2)));
        assertEquals(60, p2.items.size());
        assertEquals("Камилл Сфез", p2.items.get(0).getName());
        assertEquals(0, p2.items.get(0).getReting());

        ProviderPage<GenrePOJO> found = SITE.searchPeople(page("people-authors-search.html",
                SITE.searchUrl(SearchUrls.Category.AUTHORS, "кинг", 1)), 1, false);
        assertEquals(14, found.items.size());
        assertFalse(found.hasMore);
        assertEquals("Кинг Стивен", found.items.get(0).getName());
        assertEquals(409, found.items.get(0).getReting());

        ProviderPage<GenrePOJO> readers = Aknigi24.parsePeople(page("people-readers-p1.html", Aknigi24.READERS));
        assertEquals(40, readers.items.size());
        assertEquals("Булдаков Олег", readers.items.get(0).getName());
        assertEquals(1484, readers.items.get(0).getReting());
    }

    /** The letter walk: a letter's last page moves on to the next letter, an empty letter is passed over. */
    @Test
    public void peopleWalkTheLetters() throws IOException {
        final Document full = page("people-authors-letter-p1.html", Aknigi24.AUTHORS);
        final Document last = page("people-authors-search.html", Aknigi24.AUTHORS);
        final Document empty = Jsoup.parse("<html><body></body></html>", Aknigi24.AUTHORS);
        final List<String> fetched = new ArrayList<>();
        BookPages.Fetch pages = url -> {
            fetched.add(url);
            if (url.equals(Aknigi24.letterUrl(false, 'А', 2))) {
                return last; // А has two pages
            }
            return url.contains("letter=%D0%91") ? empty : full; // Б has none
        };
        SourceParser.PeopleCursor cursor = new SourceParser.PeopleCursor();
        assertEquals(60, Aknigi24.walkLetters(pages, 1, false, cursor).items.size());
        ProviderPage<GenrePOJO> endOfA = Aknigi24.walkLetters(pages, 2, false, cursor);
        assertEquals(14, endOfA.items.size());
        assertTrue("more letters follow", endOfA.hasMore);
        assertEquals(60, Aknigi24.walkLetters(pages, 3, false, cursor).items.size());
        assertEquals(Arrays.asList(Aknigi24.letterUrl(false, 'А', 1), Aknigi24.letterUrl(false, 'А', 2),
                Aknigi24.letterUrl(false, 'Б', 1), Aknigi24.letterUrl(false, 'В', 1)), fetched);
        assertEquals(Aknigi24.letterUrl(false, 'В', 2), pagesAt(pages, 4, cursor, fetched));

        assertEquals("the list starts over", Aknigi24.letterUrl(false, 'А', 1), pagesAt(pages, 1, cursor, fetched));
    }

    private static String pagesAt(BookPages.Fetch pages, int pageNo, SourceParser.PeopleCursor cursor,
            List<String> fetched) throws IOException {
        Aknigi24.walkLetters(pages, pageNo, false, cursor);
        return fetched.get(fetched.size() - 1);
    }

    // ---------------------------------------------------------------- book page

    @Test
    public void bookPage() throws IOException {
        Document doc = page("book.html", BOOK);
        DescriptionPOJO d = SITE.description(doc, BOOK);
        assertEquals("Гарри Поттер и методы рационального мышления", d.getTitle());
        assertEquals(Aknigi24.SERVER + "/cover/audiobooks/covers/fantastika-fentezi/garri-potter-i-metody-racionalnogo"
                + "-mysleniia-iudkovski-eliezer-ali-wodan.jpg", d.getPoster());
        assertEquals("Юдковски Элиезер", d.getAutor());
        assertEquals(Aknigi24.SERVER + "/author/iudkovski-eliezer", d.getAutorUrl());
        assertEquals("Ali Wodan", d.getArtist());
        assertEquals(Aknigi24.SERVER + "/reader/ali-wodan", d.getArtistUrl());
        assertEquals("Фантастика, фэнтези", d.getGenre());
        assertEquals(Aknigi24.SERVER + "/genre/fantastika-fentezi?sort=new", d.getGenreUrl());
        assertEquals("", d.getSeries());
        assertEquals("82 ч. 31 мин.", d.getTime());
        assertEquals("4.13", d.getReiting());
        assertTrue(d.getDescription().length() > 100);
        String start = d.getDescription().substring(0, 40);
        assertEquals("one copy of the text", d.getDescription().indexOf(start), d.getDescription().lastIndexOf(start));

        BookPOJO card = SITE.book(doc, BOOK);
        assertEquals(BOOK, card.getUrl());
        assertEquals("Гарри Поттер и методы рационального мышления", card.getName());

        ArrayList<BookPOJO> more = SITE.recommendations(doc, BOOK);
        assertEquals("the author's other book, then six of the genre", 7, more.size());
        assertEquals("Гарри Поттер и Методы рационального мышления", more.get(0).getName());
        assertEquals("the author's card names its readers only", "Юдковски Элиезер", more.get(0).getAutor());
        assertEquals(Aknigi24.SERVER + "/author/iudkovski-eliezer", more.get(0).getUrlAutor());
        assertEquals("Николенко Александр, Омномном", more.get(0).getArtist());
        BookPOJO apollo = more.get(1);
        assertEquals("Аполлоша. Тайна бронзовой статуэтки", apollo.getName());
        assertEquals("Симанович Григорий", apollo.getAutor());
        assertEquals(Aknigi24.SERVER + "/cover/audiobooks/covers/fantastika-fentezi/apollosa-taina-bronzovoi-statuetki"
                + "-simanovic-grigorii-kravec-andrei.jpg", apollo.getPhoto());
    }

    @Test
    public void tracks() throws IOException {
        ArrayList<AudioPOJO> tracks = SITE.tracks(page("book.html", BOOK), BOOK);
        assertEquals(123, tracks.size());
        AudioPOJO first = tracks.get(0);
        assertEquals("Глава 1", first.getName());
        assertEquals("https://aknigi24.com/book/13337/chapter/1", first.getUrl());
        assertEquals(97, first.getTime());
        assertEquals("Гарри Поттер и методы рационального мышления", first.getBookName());
        assertEquals("Глава 123", tracks.get(122).getName());
        assertEquals("https://aknigi24.com/book/13337/chapter/123", tracks.get(122).getUrl());
        int total = 0;
        for (AudioPOJO track : tracks) {
            total += track.getTime();
        }
        assertEquals(297058, total);

        // the saved player-data is the page's own
        Document playlist = Jsoup.parse("<script type=\"application/json\" id=\"player-data\">"
                + text("playlist.json") + "</script>", BOOK);
        assertEquals(123, Aknigi24.parseTracks(playlist, BOOK).size());

        String gorbun = Aknigi24.SERVER + "/book/gorbun-doil-artur-konan-suskov-vladimir";
        ArrayList<AudioPOJO> two = SITE.tracks(page("book-series.html", gorbun), gorbun);
        assertEquals(2, two.size());
        assertEquals("https://aknigi24.com/book/31466/chapter/2", two.get(1).getUrl());
        assertEquals(1615, two.get(0).getTime());
        assertEquals(1445, two.get(1).getTime());
    }

    @Test
    public void missingBook() throws IOException {
        String url = Aknigi24.SERVER + "/book/no-such-book";
        try {
            SITE.tracks(page("book-404.html", url), url);
            fail("the 404 page has no player");
        } catch (BookRemovedException e) {
            fail("the 404 page carries no takedown notice");
        } catch (IllegalStateException expected) {
            // the site answers a removed book with HTTP 404, which SiteHttp raises before any parsing
        }
        Document removed = Jsoup.parse("<h1><span class=\"d-md-none\">Книга</span></h1>"
                + "<p>Аудиокнига удалена по требованию правообладателя</p>", url);
        try {
            SITE.tracks(removed, url);
            fail("a takedown notice without a player");
        } catch (BookRemovedException expected) {
            assertEquals(url, expected.getMessage());
        }
    }

    @Test
    public void seriesBook() throws IOException {
        String url = Aknigi24.SERVER + "/book/gorbun-doil-artur-konan-suskov-vladimir";
        DescriptionPOJO d = SITE.description(page("book-series.html", url), url);
        assertEquals("Горбун", d.getTitle());
        assertEquals("Шерлок Холмс", d.getSeries());
        assertEquals(Aknigi24.SERVER + "/serie/serlok-xolms", d.getSeriesUrl());
        assertEquals("Дойл Артур Конан", d.getAutor());
        assertEquals("Сушков Владимир", d.getArtist());
        assertEquals("the page's \"0 ч 51 мин\"", "51 мин.", d.getTime());
        assertEquals("4.22", d.getReiting());

        ArrayList<BookPOJO> more = SITE.recommendations(page("book-series.html", url), url);
        assertEquals("six of the author, six of the genre, one of them in both", 11, more.size());
        int adventures = 0;
        for (BookPOJO book : more) {
            if (book.getName().equals("Приключения Шерлока Холмса")) {
                adventures++;
                assertEquals("Дойл Артур Конан", book.getAutor());
                assertEquals("Яковлев-Суханов Юрий", book.getArtist());
            }
        }
        assertEquals(1, adventures);
        assertEquals("a card of the genre keeps its own author", "Кеплер Ларс", more.get(6).getAutor());

        ArrayList<SeriesPOJO> series = Aknigi24.parseSeries(page("series.html", d.getSeriesUrl()));
        assertEquals(17, series.size());
        assertEquals("Горбун", series.get(0).getName());
        assertEquals(url, series.get(0).getUrl());
        assertFalse(series.get(0).getPhoto().isEmpty());
        assertEquals("Забытые дела Шерлока Холмса", series.get(16).getName());
    }

    @Test
    public void multiAuthorBook() throws IOException {
        String url = Aknigi24.SERVER + "/book/multi";
        DescriptionPOJO d = SITE.description(page("book-multi.html", url), url);
        assertEquals("Классические ТЁМНЫЕ АЛЛЕИ. Выпуск 9", d.getTitle());
        assertTrue(d.getAutor(), d.getAutor().startsWith("Майринк Густав, "));
        assertTrue(d.getAutor(), d.getAutor().endsWith(", Кроуфорд Фрэнсис Марион"));
        assertEquals(6, d.getAutor().split(", ").length);
        assertEquals("Булдаков Олег", d.getArtist());
        assertEquals("Классические тёмные аллеи", d.getSeries());
        assertEquals("3 ч. 35 мин.", d.getTime());
        assertEquals("5", d.getReiting());
        assertEquals(6, SITE.tracks(page("book-multi.html", url), url).size());
    }

    @Test
    public void comments() throws IOException {
        String url = Aknigi24.SERVER + "/book/master-i-margarita-bulgakov-mixail-efremov-oleg";
        Document doc = page("book-comments.html", url);
        DescriptionPOJO d = SITE.description(doc, url);
        assertEquals("Мастер и Маргарита", d.getTitle());
        assertEquals("Роман, проза", d.getGenre());
        assertEquals("4.52", d.getReiting());
        assertEquals(10, SITE.tracks(doc, url).size());

        ComentsModel.Parsed inline = Aknigi24.parseComments(doc.select("#comments-list li[id^=comment-]"));
        assertEquals(0, inline.skipped);
        assertEquals(1, inline.comments.size());
        ComentsPOJO first = inline.comments.get(0);
        assertEquals("Исчезающий вид", first.getName());
        assertEquals("26.07.2026 03:26", first.getDate());
        assertEquals("Описание не совпадает... (((", first.getText());
        assertEquals("fallback avatar", "", first.getImage());
        assertEquals(1, first.getChildComents().size());
        assertEquals("Aknigi24", first.getChildComents().get(0).getName());
        assertEquals("03.08.2026 16:22", first.getChildComents().get(0).getDate());
        assertEquals("Спасибо, исправили описание.", first.getChildComents().get(0).getText());
        assertEquals("https://aknigi24.com/storage/users/tg_7325987985.jpg",
                first.getChildComents().get(0).getImage());

        Document api = JsonPage.parse(Aknigi24.SERVER + "/api/comment/list?page=1&audiobook_id=29314&channel=",
                text("comments-29314-p1.json"));
        ComentsModel.Parsed page1 = Aknigi24.parseCommentsPage(JsonPage.of(api).getAsJsonObject());
        assertEquals(1, page1.comments.size());
        assertEquals("Исчезающий вид", page1.comments.get(0).getName());
        assertEquals("https://aknigi24.com/storage/users/tg_7325987985.jpg",
                page1.comments.get(0).getChildComents().get(0).getImage());
        assertNull("one page only: no load-more button", doc.selectFirst("#comments-load-more"));
    }
}
