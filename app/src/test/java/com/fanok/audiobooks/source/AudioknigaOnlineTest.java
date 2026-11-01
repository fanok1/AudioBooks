package com.fanok.audiobooks.source;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.fanok.audiobooks.BookRemovedException;
import com.fanok.audiobooks.model.OtherSourceModel;
import com.fanok.audiobooks.pojo.AudioPOJO;
import com.fanok.audiobooks.pojo.BookPOJO;
import com.fanok.audiobooks.pojo.ComentsPOJO;
import com.fanok.audiobooks.pojo.DescriptionPOJO;
import com.fanok.audiobooks.pojo.GenrePOJO;
import com.fanok.audiobooks.pojo.SeriesPOJO;
import com.fanok.audiobooks.pojo.SubComentsPOJO;
import com.fanok.audiobooks.search.ProviderPage;
import com.fanok.audiobooks.search.SearchUrls;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Pages saved from audiokniga-online.ru on 2026-10-31 (see src/test/resources/fixtures/audiokniga-online). */
public class AudioknigaOnlineTest {

    private static final String S = AudioknigaOnline.SERVER;
    private static final String BOOK = S + "/klasika/247-vojna-i-mir.html";
    private static final String GENRE = S + "/fjentezi/";
    private static final String AUTHOR = S + "/xfsearch/avtor/%D0%A1%D1%82%D0%B8%D0%B2%D0%B5%D0%BD%20%D0%9A%D0%B8%D0%BD%D0%B3/";
    private static final String RATING_FORM = "dlenewssortby=rating&dledirection=desc";

    private final AudioknigaOnline parser = new AudioknigaOnline();

    private static InputStream open(String name) {
        InputStream in = AudioknigaOnlineTest.class.getResourceAsStream("/fixtures/audiokniga-online/" + name);
        assertNotNull("missing fixture " + name, in);
        return in;
    }

    private static Document fixture(String name, String baseUri) throws IOException {
        try (InputStream in = open(name)) {
            return Jsoup.parse(in, "UTF-8", baseUri);
        }
    }

    private static String textFixture(String name) throws IOException {
        try (InputStream in = open(name)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            for (int n; (n = in.read(buffer)) > 0; ) {
                out.write(buffer, 0, n);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private static ProviderPage<BookPOJO> listing(String name, String url, int page) throws IOException {
        return AudioknigaOnline.parseListing(fixture(name, url), page);
    }

    // ---------------------------------------------------------------- URLs and sorts

    @Test
    public void pageOneIsTheRootAndLaterPagesAppendPageN() {
        assertEquals(AudioknigaOnline.INDEX, parser.pageUrl(AudioknigaOnline.INDEX, 1));
        assertEquals(S + "/page/2/", parser.pageUrl(AudioknigaOnline.INDEX, 2));
        assertEquals(GENRE, parser.pageUrl(GENRE + "page/4/", 1));
        assertEquals(AUTHOR + "page/2/", parser.pageUrl(AUTHOR, 2));
        assertEquals(S + "/collections/7-istorii-pro-derevnju/page/2/",
                parser.pageUrl(S + "/collections/7-istorii-pro-derevnju", 2));
        assertEquals(S + "/collections/page/3/", parser.pageUrl(AudioknigaOnline.COLLECTIONS, 3));
    }

    @Test
    public void sortItemsArePostedFormsAndPagesKeepThem() {
        String rating = parser.sortUrl(Sort.RATING, AudioknigaOnline.INDEX);
        assertEquals(S + "/?" + RATING_FORM + "&set_new_sort=dle_sort_main&set_direction_sort=dle_direction_main",
                rating);
        assertEquals(S + "/page/2/?" + RATING_FORM + "&set_new_sort=dle_sort_main&set_direction_sort=dle_direction_main",
                parser.pageUrl(rating, 2));
        assertEquals(rating, parser.sortUrl(Sort.RATING, rating));
        assertEquals("the plain index is newest first", AudioknigaOnline.INDEX,
                parser.sortUrl(Sort.NEW, AudioknigaOnline.INDEX));
        assertTrue(parser.sortUrl(Sort.POPULAR, AudioknigaOnline.INDEX).contains("dlenewssortby=news_read&dledirection=desc"));
        assertTrue(parser.sortUrl(Sort.COMMENTS, AudioknigaOnline.INDEX).contains("dlenewssortby=comm_num&dledirection=desc"));
        assertTrue(parser.sortUrl(Sort.TITLE, AudioknigaOnline.INDEX).contains("dlenewssortby=title&dledirection=asc"));
        assertNull(parser.sortUrl(Sort.YEARS, AudioknigaOnline.INDEX));
        assertNull(parser.sortUrl(Sort.POPULAR_WEEK, AudioknigaOnline.INDEX));
    }

    @Test
    public void onlyTheIndexAndGenresSortAndASortedListingKeepsItsMenu() {
        assertEquals(Dle.MENU, parser.sorts(AudioknigaOnline.INDEX));
        assertEquals(Dle.MENU, parser.sorts(S + "/page/5/"));
        for (Sort sort : new Sort[]{Sort.NEW, Sort.POPULAR, Sort.RATING, Sort.COMMENTS, Sort.TITLE}) {
            String sorted = parser.sortUrl(sort, AudioknigaOnline.INDEX);
            assertEquals(sort.name(), Dle.MENU, parser.sorts(sorted));
            assertEquals(sort.name(), Dle.MENU, parser.sorts(parser.pageUrl(sorted, 2)));
            String inGenre = parser.inGenre(sorted, GENRE);
            assertEquals(sort.name(), Dle.MENU, parser.sorts(inGenre));
            assertEquals(sort.name(), GENRE, parser.genreOf(parser.pageUrl(inGenre, 3)));
        }
        assertEquals(Dle.MENU, parser.sorts(GENRE + "page/2/"));
        assertEquals(Dle.MENU, parser.sorts(S + "/podborki/stalker/"));
        assertTrue(parser.sorts(AUTHOR).isEmpty());
        assertTrue(parser.sorts(S + "/xfsearch/serie/x/").isEmpty());
        assertTrue(parser.sorts(S + "/collections/7-istorii-pro-derevnju").isEmpty());
        assertTrue(parser.sorts(AudioknigaOnline.COLLECTIONS).isEmpty());
        assertTrue(parser.sorts(parser.searchUrl(SearchUrls.Category.BOOKS, "Кинг", 1)).isEmpty());
        assertTrue(parser.sorts(BOOK).isEmpty());
    }

    @Test
    public void aGenreSortsByItsCategoryId() {
        assertEquals(GENRE, parser.genreOf(GENRE + "page/3/"));
        assertEquals(S + "/fjentezi/litrpg/", parser.genreOf(S + "/fjentezi/litrpg/page/2/"));
        assertNull(parser.genreOf(AudioknigaOnline.INDEX));
        assertNull(parser.genreOf(S + "/page/2/"));
        assertNull(parser.genreOf(AUTHOR));
        assertNull(parser.genreOf(BOOK));
        assertNull(parser.genreOf(S + "/collections/11-postapokalipsis"));
        assertNull(parser.genreOf("https://s2.aknig.xyz/fjentezi/"));
        String rating = parser.sortUrl(Sort.RATING, GENRE);
        String inGenre = parser.inGenre(rating, GENRE);
        assertEquals(GENRE + "?" + RATING_FORM + "&set_new_sort=dle_sort_cat_43&set_direction_sort=dle_direction_cat_43",
                inGenre);
        assertEquals(GENRE + "page/2/?" + RATING_FORM + "&set_new_sort=dle_sort_cat_43"
                + "&set_direction_sort=dle_direction_cat_43", parser.pageUrl(inGenre, 2));
        assertEquals("new is the plain genre", GENRE, parser.inGenre(parser.sortUrl(Sort.NEW, GENRE), GENRE));
        assertTrue(parser.inGenre(rating, S + "/podborki/stalker/").contains("set_new_sort=dle_sort_cat_15&"));
    }

    @Test
    public void theBuiltInCategoryIdsAreTheOnesTheGenrePagesPost() throws IOException {
        assertEquals("43", Dle.categoryId(fixture("genre-p1.html", GENRE)));
        assertTrue(parser.inGenre(parser.sortUrl(Sort.TITLE, GENRE), GENRE).contains("dle_sort_cat_43&"));
        String stalker = S + "/podborki/stalker/";
        assertEquals("15", Dle.categoryId(fixture("genre-sub-stalker.html", stalker)));
        assertNull(Dle.categoryId(fixture("listing-index-p1.html", AudioknigaOnline.INDEX)));
    }

    @Test
    public void searchIsBooksOnlyAndPaged() {
        assertTrue(parser.searchUrl(SearchUrls.Category.BOOKS, "Кинг", 2).startsWith(S
                + "/index.php?do=search&subaction=search&story=%D0%9A%D0%B8%D0%BD%D0%B3&search_start=2&"));
        assertNull(parser.searchUrl(SearchUrls.Category.AUTHORS, "Кинг", 1));
        assertNull(parser.searchUrl(SearchUrls.Category.PERFORMERS, "Кинг", 1));
    }

    @Test
    public void collectionsOpenAsListingsAndTheirIndexHasNoTimes() {
        assertTrue(parser.opensAsListing(S + "/collections/25-25-popadanstvo-v-sssr"));
        assertFalse(parser.opensAsListing(BOOK));
        assertFalse(parser.listsDuration(AudioknigaOnline.COLLECTIONS));
        assertFalse(parser.listsDuration(S + "/collections/page/2/"));
        assertTrue(parser.listsDuration(S + "/collections/7-istorii-pro-derevnju"));
        assertTrue(parser.listsDuration(AudioknigaOnline.INDEX));
    }

    // ---------------------------------------------------------------- listings

    @Test
    public void indexCardsCarryEveryField() throws IOException {
        ProviderPage<BookPOJO> page = listing("listing-index-p1.html", AudioknigaOnline.INDEX, 1);
        assertEquals(ProviderPage.Status.OK, page.status);
        assertEquals(10, page.items.size());
        assertTrue("227 pages", page.hasMore);
        BookPOJO first = page.items.get(0);
        assertEquals("Учитель Особого Назначения 10", first.getName());
        assertEquals(S + "/popadancy/2287-uchitel-osobogo-naznachenija-10.html", first.getUrl());
        assertEquals(S + "/uploads/posts/2026-10/1793427694_11.jpg", first.getPhoto());
        assertEquals("Илья Савич, Виктор Молотов", first.getAutor());
        assertEquals(S + "/xfsearch/avtor/%D0%98%D0%BB%D1%8C%D1%8F%20%D0%A1%D0%B0%D0%B2%D0%B8%D1%87/",
                first.getUrlAutor());
        assertEquals("Григорий Андрианов", first.getArtist());
        assertEquals(S + "/xfsearch/ispolnitel/%D0%93%D1%80%D0%B8%D0%B3%D0%BE%D1%80%D0%B8%D0%B9%20"
                + "%D0%90%D0%BD%D0%B4%D1%80%D0%B8%D0%B0%D0%BD%D0%BE%D0%B2/", first.getUrlArtist());
        assertEquals("Попаданцы, Фантастика, Фэнтези", first.getGenre());
        assertNull("the label is no link", first.getUrlGenre());
        assertEquals("11 ч. 11 мин.", first.getTime());
        assertEquals("likes 0, dislikes 0", "0", first.getReting());
        assertTrue(first.getDesc().startsWith("Казалось бы, после череды испытаний"));
        BookPOJO last = page.items.get(9);
        assertEquals("Паутина Темного Бога", last.getName());
        assertEquals(S + "/fantastika/2278-pautina-temnogo-boga.html", last.getUrl());
        assertEquals("10 ч. 52 мин.", last.getTime());
    }

    @Test
    public void secondIndexPageMayComeWithoutVotesAndAnnotations() throws IOException {
        ProviderPage<BookPOJO> page = listing("listing-index-p2.html", S + "/page/2/", 2);
        assertEquals(10, page.items.size());
        assertTrue(page.hasMore);
        BookPOJO first = page.items.get(0);
        assertEquals("Альпийская охота", first.getName());
        assertEquals(S + "/detektivy/2277-alpijskaja-ohota.html", first.getUrl());
        assertEquals("7 ч. 17 мин.", first.getTime());
        assertEquals("Александр Тамоников", first.getAutor());
        assertEquals("0", first.getReting());
        assertEquals("", first.getDesc());
        assertEquals("Псы Господни 1", page.items.get(9).getName());
    }

    @Test
    public void lastIndexPageHasNoMoreAndItsTwoPartTimesAreMinutesAndSeconds() throws IOException {
        ProviderPage<BookPOJO> page = listing("listing-index-last.html", S + "/page/227/", 227);
        assertEquals(10, page.items.size());
        assertFalse(page.hasMore);
        assertEquals("Загадка старого кладбища", page.items.get(0).getName());
        assertEquals("04:24:36", "4 ч. 24 мин.", page.items.get(0).getTime());
        BookPOJO story = page.items.get(3);
        assertEquals("Лаз в углу двора", story.getName());
        assertEquals("48:36, a short story", "48 мин.", story.getTime());
        BookPOJO last = page.items.get(9);
        assertEquals("Сталкер Лунь", last.getName());
        assertEquals(S + "/fantastika/3-stalker-lun.html", last.getUrl());
    }

    @Test
    public void sortedPagesCarryTheNetVotes() throws IOException {
        ProviderPage<BookPOJO> p1 = listing("listing-rating-p1.html", AudioknigaOnline.INDEX, 1);
        assertEquals("Война и мир", p1.items.get(0).getName());
        assertEquals("3254 likes, 464 dislikes", "+2790", p1.items.get(0).getReting());
        assertEquals(S + "/fjentezi/218-garri-potter-i-filosofskij-kamen.html", p1.items.get(1).getUrl());
        ProviderPage<BookPOJO> p2 = listing("listing-rating-p2.html", S + "/page/2/", 2);
        assertEquals(10, p2.items.size());
        assertTrue(p2.hasMore);
        assertEquals("Гарри Поттер и принц-полукровка", p2.items.get(0).getName());
        ProviderPage<BookPOJO> views = listing("listing-views-p1.html", AudioknigaOnline.INDEX, 1);
        assertEquals("Война и мир", views.items.get(0).getName());
        assertTrue(views.items.get(1).getUrl().contains("/165-"));
        assertEquals("Гарри Поттер и Кубок Огня", listing("listing-comments-p1.html", AudioknigaOnline.INDEX, 1)
                .items.get(0).getName());
        assertEquals("«Бис»-исход", listing("listing-title-p1.html", AudioknigaOnline.INDEX, 1)
                .items.get(0).getName());
    }

    @Test
    public void genrePages() throws IOException {
        ProviderPage<BookPOJO> p1 = listing("genre-p1.html", GENRE, 1);
        assertEquals(10, p1.items.size());
        assertTrue("163 pages", p1.hasMore);
        assertEquals(S + "/popadancy/2287-uchitel-osobogo-naznachenija-10.html", p1.items.get(0).getUrl());
        ProviderPage<BookPOJO> p2 = listing("genre-p2.html", GENRE + "page/2/", 2);
        assertEquals(10, p2.items.size());
        assertTrue(p2.hasMore);
        assertEquals("Вторжение демонов", p2.items.get(0).getName());
        assertEquals(S + "/fjentezi/2264-vtorzhenie-demonov.html", p2.items.get(0).getUrl());
        ProviderPage<BookPOJO> rating = listing("genre-rating-p1.html", GENRE, 1);
        assertEquals("Гарри Поттер и философский камень", rating.items.get(0).getName());
        assertEquals("Темное Время", listing("genre-rating-p2.html", GENRE + "page/2/", 2).items.get(0).getName());
        assertEquals(10, listing("genre-sub-stalker.html", S + "/podborki/stalker/", 1).items.size());
    }

    @Test
    public void authorReaderAndSeriesListings() throws IOException {
        ProviderPage<BookPOJO> author = listing("author.html", AUTHOR, 1);
        assertEquals(10, author.items.size());
        assertTrue(author.hasMore);
        assertEquals("Страна Радости", author.items.get(0).getName());
        assertEquals(S + "/uzhasy-mistika/2261-strana-radosti.html", author.items.get(0).getUrl());
        ProviderPage<BookPOJO> reader = listing("reader-p2.html", S + "/xfsearch/ispolnitel/x/page/2/", 2);
        assertEquals(10, reader.items.size());
        assertTrue("3 pages", reader.hasMore);
        assertEquals("Учитель Особого Назначения 2", reader.items.get(0).getName());
        ProviderPage<BookPOJO> series = listing("series.html", S + "/xfsearch/serie/x/", 1);
        assertEquals(10, series.items.size());
        assertFalse("one page, no pager", series.hasMore);
    }

    @Test
    public void collectionsIndexListsCollectionsAndACollectionListsBooks() throws IOException {
        ProviderPage<BookPOJO> index = listing("collections-index.html", AudioknigaOnline.COLLECTIONS, 1);
        assertEquals(10, index.items.size());
        assertTrue("3 pages", index.hasMore);
        BookPOJO first = index.items.get(0);
        assertEquals("Попаданство в СССР", first.getName());
        assertEquals(S + "/collections/25-25-popadanstvo-v-sssr", first.getUrl());
        assertTrue(parser.opensAsListing(first.getUrl()));
        assertEquals(S + "/uploads/posts/2025-12/popadanstvo-v-sssr_25.jpg", first.getPhoto());
        assertTrue(first.getDesc().startsWith("Подборка книг и целых серий"));
        assertEquals("", first.getTime());

        String url = S + "/collections/7-istorii-pro-derevnju";
        ProviderPage<BookPOJO> collection = listing("collection.html", url, 1);
        assertEquals(10, collection.items.size());
        assertTrue("2 pages", collection.hasMore);
        BookPOJO book = collection.items.get(0);
        assertEquals("Хранительница пекарни Тётя Зина", book.getName());
        assertEquals(S + "/uzhasy-mistika/27-hranitelnica-pekarni-tetja-zina.html", book.getUrl());
        assertEquals("Андрей Смскин", book.getArtist());
        assertNull(book.getAutor());
        assertEquals("+28", book.getReting());
        assertEquals("30 мин.", book.getTime());
    }

    @Test
    public void genreListIsBuiltInAndCoversTheSiteMenu() throws IOException {
        ProviderPage<GenrePOJO> page = parser.loadGenres(AudioknigaOnline.GENRES, 1);
        assertFalse(page.hasMore);
        List<GenrePOJO> genres = page.items;
        assertEquals(20, genres.size());
        assertEquals("Фантастика", genres.get(0).getName());
        assertEquals(S + "/fantastika/", genres.get(0).getUrl());
        assertEquals("Подборки", genres.get(19).getName());
        assertEquals(AudioknigaOnline.COLLECTIONS, genres.get(19).getUrl());
        List<String> urls = new ArrayList<>();
        for (GenrePOJO genre : genres) {
            urls.add(genre.getUrl());
            assertTrue(genre.getName(), genre.getUrl().contains("/collections/")
                    || genre.getUrl().equals(parser.genreOf(genre.getUrl())));
        }
        Document index = fixture("listing-index-p1.html", AudioknigaOnline.INDEX);
        int menu = 0;
        for (Element a : index.select(".side-nav .nav-menu a[href]")) {
            menu++;
            assertTrue(a.text(), urls.contains(a.absUrl("href")));
        }
        assertEquals(14, menu);
    }

    // ---------------------------------------------------------------- search

    @Test
    public void searchPagesEndAtTheLastPagerNumber() throws IOException {
        ProviderPage<BookPOJO> p1 = parser.searchResults(fixture("search-king-p1.html", S + "/index.php"), 1);
        assertEquals(10, p1.items.size());
        assertTrue(p1.hasMore);
        assertEquals("Страна Радости", p1.items.get(0).getName());
        assertEquals("Под куполом", p1.items.get(9).getName());
        assertFalse(p1.items.get(0).getAutor().isEmpty());
        ProviderPage<BookPOJO> p2 = parser.searchResults(fixture("search-king-p2.html", S + "/index.php"), 2);
        assertEquals(10, p2.items.size());
        assertTrue(p2.hasMore);
        assertEquals("Громила", p2.items.get(0).getName());
        assertEquals("ОНО. Тень прошлого", p2.items.get(9).getName());
        ProviderPage<BookPOJO> last = parser.searchResults(fixture("search-king-last.html", S + "/index.php"), 3);
        assertEquals(3, last.items.size());
        assertFalse(last.hasMore);
        assertEquals("Девочка, которая любила Тома Гордона", last.items.get(2).getName());
    }

    @Test
    public void searchWithoutHitsOrWithAShortQueryIsEmpty() throws IOException {
        assertEquals(ProviderPage.Status.EMPTY,
                parser.searchResults(fixture("search-none.html", S + "/index.php"), 1).status);
        assertEquals(ProviderPage.Status.EMPTY,
                parser.searchResults(fixture("search-short.html", S + "/index.php"), 1).status);
    }

    @Test
    public void otherSourceSearchFindsTheBookWithItsPeople() throws IOException {
        ArrayList<OtherSourceModel.Hit> hits = parser.otherSourceHits(fixture("other-source-search.html",
                S + "/index.php"));
        assertEquals(1, hits.size());
        ProviderPage<BookPOJO> page = parser.searchResults(fixture("other-source-search.html", S + "/index.php"), 1);
        BookPOJO hit = page.items.get(0);
        assertEquals("Мастер и Маргарита", hit.getName());
        assertEquals(S + "/roman/212-master-i-margarita.html", hit.getUrl());
        assertEquals("Михаил Булгаков", hit.getAutor());
        assertEquals("Иван Литвинов", hit.getArtist());
        assertEquals("3 ч.", hit.getTime());
        assertFalse(page.hasMore);
    }

    /** Live: title + authors + reader found nothing; the plain title "Война и мир" 50 books, the novel not among the first 10. */
    @Test
    public void otherSourceSearchesTheTitleInTitlesOnly() throws IOException {
        BookPOJO card = parser.listing(fixture("listing-index-p1.html", AudioknigaOnline.INDEX),
                AudioknigaOnline.INDEX, 1).items.get(0);
        assertEquals("Илья Савич, Виктор Молотов", card.getAutor());
        String terms = parser.otherSourceTerms(card);
        assertEquals("Учитель Особого Назначения 10", terms);
        assertEquals(S + "/index.php?do=search&subaction=search&story="
                + "%D0%A3%D1%87%D0%B8%D1%82%D0%B5%D0%BB%D1%8C+%D0%9E%D1%81%D0%BE%D0%B1%D0%BE%D0%B3%D0%BE+"
                + "%D0%9D%D0%B0%D0%B7%D0%BD%D0%B0%D1%87%D0%B5%D0%BD%D0%B8%D1%8F+10"
                + "&search_start=1&result_from=1&full_search=1&titleonly=3", parser.otherSourceSearchUrl(terms, 1));
    }

    // ---------------------------------------------------------------- book page

    @Test
    public void description() throws IOException {
        DescriptionPOJO d = parser.description(fixture("book.html", BOOK), BOOK);
        assertEquals("Война и мир", d.getTitle());
        assertEquals(S + "/uploads/posts/2021-04/1619359726_1613200979_vojna-i-mir.jpg", d.getPoster());
        assertEquals("Лев Толстой", d.getAutor());
        assertEquals(S + "/xfsearch/avtor/%D0%9B%D0%B5%D0%B2%20%D0%A2%D0%BE%D0%BB%D1%81%D1%82%D0%BE%D0%B9/",
                d.getAutorUrl());
        assertEquals("Александр Клюквин", d.getArtist());
        assertTrue(d.getArtistUrl().startsWith(S + "/xfsearch/ispolnitel/"));
        assertEquals("Классика, Роман", d.getGenre());
        assertEquals(S + "/klasika/", d.getGenreUrl());
        assertEquals("", d.getSeries());
        assertEquals("60 ч. 14 мин.", d.getTime());
        assertEquals("+2790", d.getReiting());
        assertEquals("3254", d.getLike());
        assertEquals("464", d.getDisLike());
        assertTrue(d.getDescription().startsWith("Великий роман-эпопея Льва Толстого"));
        assertFalse(d.getDescription().contains("Аннотация"));
    }

    @Test
    public void bookByLinkCarriesVotesAndCommentCount() throws IOException {
        BookPOJO book = parser.book(fixture("book.html", BOOK), BOOK);
        assertEquals(BOOK, book.getUrl());
        assertEquals("Война и мир", book.getName());
        assertEquals("Лев Толстой", book.getAutor());
        assertEquals("60 ч. 14 мин.", book.getTime());
        assertEquals("+2790", book.getReting());
        assertEquals(32, book.getComents());
    }

    @Test
    public void seriesBookLinksItsCycleOrItsCollection() throws IOException {
        String url = S + "/popadancy/2287-uchitel-osobogo-naznachenija-10.html";
        DescriptionPOJO d = parser.description(fixture("book-series.html", url), url);
        assertEquals("Учитель Особого Назначения 10", d.getTitle());
        assertEquals("Илья Савич, Виктор Молотов", d.getAutor());
        assertEquals("Учитель Особого Назначения", d.getSeries());
        assertEquals(S + "/xfsearch/serie/%D0%A3%D1%87%D0%B8%D1%82%D0%B5%D0%BB%D1%8C%20%D0%9E%D1%81%D0%BE%D0%B1"
                + "%D0%BE%D0%B3%D0%BE%20%D0%9D%D0%B0%D0%B7%D0%BD%D0%B0%D1%87%D0%B5%D0%BD%D0%B8%D1%8F/", d.getSeriesUrl());
        assertEquals(S + "/popadancy/", d.getGenreUrl());
        assertEquals("0", d.getReiting());
        String hp = S + "/fjentezi/221-garri-potter-i-kubok-ognja.html";
        DescriptionPOJO collection = parser.description(fixture("book-comments.html", hp), hp);
        assertEquals("Гарри Поттер", collection.getSeries());
        assertEquals(S + "/collections/20-garri-potter", collection.getSeriesUrl());
        assertEquals("21 ч. 14 мин.", collection.getTime());
    }

    @Test
    public void aCategoryOutsideTheGenresIsNoGenreLink() {
        Document doc = Jsoup.parse("<div class=speedbar><a itemprop=item href=/>Home</a>"
                + "<a itemprop=item href=/avtory/stiven-king/>Стивен Кинг</a></div>"
                + "<ul class=short-list><li><span>Жанр:</span> Ужасы</li></ul>", S + "/avtory/stiven-king/473-x.html");
        DescriptionPOJO d = AudioknigaOnline.parseDescription(doc);
        assertEquals("Ужасы", d.getGenre());
        assertEquals("", d.getGenreUrl());
    }

    @Test
    public void recommendations() throws IOException {
        String url = S + "/fjentezi/221-garri-potter-i-kubok-ognja.html";
        ArrayList<BookPOJO> books = parser.recommendations(fixture("book-comments.html", url), url);
        assertEquals(5, books.size());
        BookPOJO first = books.get(0);
        assertEquals("Гарри Поттер и тайная комната", first.getName());
        assertEquals(S + "/fjentezi/219-garri-potter-i-tajnaja-komnata.html", first.getUrl());
        assertEquals(S + "/uploads/posts/2021-04/1618946277_4-4.jpg", first.getPhoto());
        assertEquals("Фэнтези, Фантастика", first.getGenre());
        assertEquals("Второго шанса не будет. Когда Завидуют мертвым", books.get(4).getName());
    }

    @Test
    public void seriesTabFirstBookFirst() throws IOException {
        ArrayList<SeriesPOJO> cycle = AudioknigaOnline.parseSeries(fixture("series.html", S + "/xfsearch/serie/x/"));
        assertEquals(10, cycle.size());
        assertEquals("Учитель Особого Назначения 10", cycle.get(0).getName());
        assertEquals("", cycle.get(0).getNumber());
        cycle = AudioknigaOnline.inSeriesOrder(cycle);
        assertEquals("Учитель Особого Назначения 1", cycle.get(0).getName());
        assertEquals(S + "/popadancy/2113-uchitel-osobogo-naznachenija-1.html", cycle.get(0).getUrl());
        assertEquals("Учитель Особого Назначения 10", cycle.get(9).getName());
        assertFalse(cycle.get(0).getPhoto().isEmpty());

        // a collection lists its titles from Я to А: "1. … философский камень", "3. … узник Азкабана", "2. …"
        ArrayList<SeriesPOJO> collection = AudioknigaOnline.parseSeries(
                fixture("collection-numbered.html", S + "/collections/20-garri-potter"));
        assertEquals("3", collection.get(1).getNumber());
        assertEquals("Гарри Поттер и узник Азкабана", collection.get(1).getName());
        collection = AudioknigaOnline.inSeriesOrder(collection);
        assertEquals(7, collection.size());
        for (int i = 0; i < 7; i++) {
            assertEquals(String.valueOf(i + 1), collection.get(i).getNumber());
        }
        assertEquals("Гарри Поттер и философский камень", collection.get(0).getName());
        assertEquals(S + "/fjentezi/221-garri-potter-i-kubok-ognja.html", collection.get(3).getUrl());

        // /collections/10-jankylma: "2. Янкылма 2 - все серии рассказов", then " Янкылма" without a number
        ArrayList<SeriesPOJO> partly = new ArrayList<>();
        partly.add(seriesItem("2", "Янкылма 2 - все серии рассказов"));
        partly.add(seriesItem("", "Янкылма"));
        partly = AudioknigaOnline.inSeriesOrder(partly);
        assertEquals("Янкылма", partly.get(0).getName());
        assertEquals("2", partly.get(1).getNumber());
    }

    private static SeriesPOJO seriesItem(String number, String name) {
        SeriesPOJO item = new SeriesPOJO();
        item.setNumber(number);
        item.setName(name);
        return item;
    }

    @Test
    public void commentsWithRepliesAndTheirPages() throws IOException {
        String url = S + "/fjentezi/221-garri-potter-i-kubok-ognja.html";
        Document page = fixture("book-comments.html", url);
        assertEquals(4, AudioknigaOnline.commentPages(page));
        assertEquals(1, AudioknigaOnline.commentPages(fixture("book.html", BOOK)));
        ArrayList<ComentsPOJO> comments = AudioknigaOnline.parseComments(page);
        assertEquals(59, count(comments));
        ComentsPOJO first = comments.get(0);
        assertEquals("Гость Фёдор", first.getName());
        assertEquals("1 ноября 2021 21:44", first.getDate());
        assertEquals("Очень круто Очень сильно люблю эту книгу", first.getText());
        assertEquals("+120", first.getReting());
        assertEquals(S + "/templates/coolserial-light-qv3/dleimages/noavatar.png", first.getImage());
        SubComentsPOJO reply = first.getChildComents().get(0);
        assertEquals("Неизвестный", reply.getName());
        assertEquals("Гость Фёдор", reply.getParentName());
        assertEquals("Эта книга просто легенда. Лучше этой книги ничего нет.", reply.getText());
        assertEquals("Гость Фёдор", comments.get(1).getName());
        assertEquals("1 ноября 2021 22:42", comments.get(1).getDate());

        ArrayList<ComentsPOJO> p2 = AudioknigaOnline.parseCommentsPage(textFixture("comments-p2.json"));
        assertEquals(38, count(p2));
        assertEquals("Гость Михаил", p2.get(0).getName());
        assertEquals("Короче говоря отлично", p2.get(0).getText());
        assertEquals("+31", p2.get(0).getReting());
        ArrayList<ComentsPOJO> p4 = AudioknigaOnline.parseCommentsPage(textFixture("comments-p4.json"));
        assertEquals(9, count(p4));
        assertEquals("Серёжа", p4.get(0).getName());
        assertTrue(AudioknigaOnline.parseCommentsPage("{\"navigation\":\"\",\"comments\":\"\"}").isEmpty());
        assertTrue(AudioknigaOnline.parseComments(fixture("book-series.html", url)).isEmpty());
    }

    private static int count(List<ComentsPOJO> comments) {
        int n = 0;
        for (ComentsPOJO c : comments) {
            n += 1 + c.getChildComents().size();
        }
        return n;
    }

    // ---------------------------------------------------------------- audio

    @Test
    public void nestedPlaylistIsFlattenedAndItsCyrillicEncoded() throws IOException {
        String playlist = S + "/f/audiobooks513925/lev-tolstoi/voina-i-mir/playlist.txt.php";
        ArrayList<AudioPOJO> tracks = AudioknigaOnline.encoded(
                Players.parsePlayerjsPlaylist(textFixture("playlist.json"), playlist, "Война и мир"));
        assertEquals(362, tracks.size());
        AudioPOJO first = tracks.get(0);
        assertEquals("0001", first.getName());
        assertEquals("Война и мир", first.getBookName());
        assertEquals(S + "/f/audiobooks513925/lev-tolstoi/voina-i-mir/%D0%A2%D0%BE%D0%BC_1/0001.mp3", first.getUrl());
        assertEquals(0, first.getTime());
        assertEquals(-1, first.getTimeStart());
        assertEquals("0102", tracks.get(361).getName());
        assertEquals(S + "/f/audiobooks513925/lev-tolstoi/voina-i-mir/%D0%A2%D0%BE%D0%BC_4/0102.mp3",
                tracks.get(361).getUrl());
    }

    @Test
    public void flatPlaylist() throws IOException {
        String playlist = S + "/f/audiobooks513925/savich-ilja-molotov-viktor-uchitel-osobogo-naznachenija-10/"
                + "playlist.txt.php";
        ArrayList<AudioPOJO> tracks = AudioknigaOnline.encoded(
                Players.parsePlayerjsPlaylist(textFixture("playlist-series.json"), playlist, "x"));
        assertEquals(29, tracks.size());
        assertEquals("0001", tracks.get(0).getName());
        assertEquals(S + "/f/audiobooks513925/savich-ilja-molotov-viktor-uchitel-osobogo-naznachenija-10/0029.mp3",
                tracks.get(28).getUrl());
    }

    @Test
    public void bookPagePointsAtItsPlaylist() throws IOException {
        assertEquals("/f/audiobooks513925/lev-tolstoi/voina-i-mir/playlist.txt.php",
                Players.playerjsFile(fixture("book.html", BOOK)));
    }

    @Test
    public void oldBooksCarryAnInlineListWithEmptyPlaceholders() throws IOException {
        String url = S + "/uzhasy-mistika/10-jankylma.html";
        ArrayList<AudioPOJO> tracks = parser.tracks(fixture("book-single-file.html", url), url);
        assertEquals(2, tracks.size());
        assertEquals("ЯНКЫЛМА. ЛЕГЕНДЫ И УЖАСЫ народов СЕВЕРА. Мистика", tracks.get(0).getName());
        assertEquals("Янкылма", tracks.get(0).getBookName());
        assertEquals(S + "/uploads/files/2021-02/1612724201_jankylma_-legendy-i-uzhasy-narodov-severa_-mistika.m4a",
                tracks.get(0).getUrl());
        assertEquals("ЯНКЫЛМА. Глава 2", tracks.get(1).getName());
        DescriptionPOJO d = parser.description(fixture("book-single-file.html", url), url);
        assertEquals("37 мин.", d.getTime());
        assertEquals("Янкылма", d.getSeries());
        assertEquals(S + "/collections/10-jankylma", d.getSeriesUrl());
        assertEquals(S + "/uzhasy-mistika/", d.getGenreUrl());
    }

    @Test
    public void booksTheSiteOnlySellsAreTakenDown() throws IOException {
        for (String name : new String[]{"book-unavailable.html", "book-fragment.html"}) {
            String url = S + "/x/1-" + name;
            Document doc = fixture(name, url);
            assertFalse(name, parser.description(doc, url).getTitle().isEmpty());
            try {
                parser.tracks(doc, url);
                fail(name + " has no player");
            } catch (BookRemovedException expected) {
                assertEquals(url, expected.getMessage());
            }
        }
        Document noPlayer = Jsoup.parse("<h1 class=short-title>x</h1>", BOOK);
        try {
            parser.tracks(noPlayer, BOOK);
            fail("no player");
        } catch (BookRemovedException wrong) {
            fail("a page without a player or a notice is not a takedown");
        } catch (IllegalStateException expected) {
            // the page is not what the site serves
        }
    }
}
