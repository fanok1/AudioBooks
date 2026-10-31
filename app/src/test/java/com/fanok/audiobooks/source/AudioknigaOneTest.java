package com.fanok.audiobooks.source;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.fanok.audiobooks.BookPageFixtures;
import com.fanok.audiobooks.BookRemovedException;
import com.fanok.audiobooks.pojo.AudioPOJO;
import com.fanok.audiobooks.pojo.BookPOJO;
import com.fanok.audiobooks.pojo.ComentsPOJO;
import com.fanok.audiobooks.pojo.DescriptionPOJO;
import com.fanok.audiobooks.pojo.GenrePOJO;
import com.fanok.audiobooks.pojo.SeriesPOJO;
import com.fanok.audiobooks.search.ListingFilters;
import com.fanok.audiobooks.search.Provider;
import com.fanok.audiobooks.search.ProviderPage;
import com.fanok.audiobooks.search.SearchUrls;

import org.jsoup.nodes.Document;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Pages saved from audiokniga.one on 2026-10-31 (see src/test/resources/fixtures/audiokniga-one). */
public class AudioknigaOneTest {

    private static final String S = AudioknigaOne.SERVER;
    private static final AudioknigaOne SITE = new AudioknigaOne();
    private static final String GENRE = S + "/fantastika/";
    private static final String KING = S + "/xfsearch/avtor/%D1%81%D1%82%D0%B8%D0%B2%D0%B5%D0%BD%20%D0%BA%D0%B8%D0%BD%D0%B3/";
    private static final String BOOK = S + "/16149-idealnyj-mir-dlja-sociopata-1-13.html";
    private static final String BOOK_HLS = S + "/25450-gorod-tysjachi-bogov.html";
    private static final String BOOK_SERIES = S + "/25449-carskoe-delo.html";
    private static final String SORTED = "?dlenewssortby=%s&dledirection=%s&set_new_sort=dle_sort_main"
            + "&set_direction_sort=dle_direction_main";

    private static Document page(String name, String url) throws IOException {
        return BookPageFixtures.load("audiokniga-one/" + name, url);
    }

    private static ProviderPage<BookPOJO> listing(String name, String url, int pageNo) throws IOException {
        return SITE.listing(page(name, url), url, pageNo);
    }

    private static String ids(List<BookPOJO> books, int count) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < count && i < books.size(); i++) {
            String url = books.get(i).getUrl();
            out.append(i == 0 ? "" : " ").append(url.substring(S.length() + 1, url.indexOf('-')));
        }
        return out.toString();
    }

    // ---------------------------------------------------------------- listings

    @Test
    public void indexCards() throws IOException {
        ProviderPage<BookPOJO> p1 = listing("listing-index-p1.html", AudioknigaOne.INDEX, 1);
        assertEquals(16, p1.items.size());
        assertTrue(p1.hasMore);
        BookPOJO first = p1.items.get(0);
        assertEquals(S + "/25475-predsedatel-1988.html", first.getUrl());
        assertEquals("Председатель 1988", first.getName());
        assertEquals(S + "/uploads/posts/2026-10/1793468607_1-37.webp", first.getPhoto());
        assertEquals("Павел Смолин", first.getAutor());
        assertEquals(S + "/xfsearch/avtor/%D0%BF%D0%B0%D0%B2%D0%B5%D0%BB%20%D1%81%D0%BC%D0%BE%D0%BB%D0%B8%D0%BD/",
                first.getUrlAutor());
        assertEquals("Илья Кочетков", first.getArtist());
        assertEquals(S + "/xfsearch/ispolnitel/%D0%B8%D0%BB%D1%8C%D1%8F%20%D0%BA%D0%BE%D1%87%D0%B5%D1%82%D0%BA%D0%BE%D0%B2/",
                first.getUrlArtist());
        assertEquals("Фантастика, Попаданцы", first.getGenre());
        assertEquals(GENRE, first.getUrlGenre());
        assertEquals("9 ч. 31 мин.", first.getTime());
        assertEquals(571, ListingFilters.durationMinutes(first.getTime()));
        assertEquals("0", first.getReting());
        assertTrue(first.getDesc(), first.getDesc().startsWith("1988 год. В колхоз прибывает"));
        BookPOJO last = p1.items.get(15);
        assertEquals("Стужа", last.getName());
        assertEquals("6", last.getReting());

        ProviderPage<BookPOJO> p2 = listing("listing-index-p2.html", SITE.pageUrl(AudioknigaOne.INDEX, 2), 2);
        assertEquals(16, p2.items.size());
        assertTrue(p2.hasMore);
        assertEquals("Хэллоуин в Ок-Крик", p2.items.get(0).getName());
        assertEquals("27 мин.", p2.items.get(0).getTime());
        assertEquals(S + "/25444", p2.items.get(15).getUrl().substring(0, S.length() + 6));

        ProviderPage<BookPOJO> last1336 = listing("listing-index-last.html", SITE.pageUrl(AudioknigaOne.INDEX, 1336),
                1336);
        assertEquals(14, last1336.items.size());
        assertFalse(last1336.hasMore);
        assertEquals("107", ids(last1336.items, 1));
        assertEquals("Счастья не будет", last1336.items.get(13).getName());
    }

    @Test
    public void seriesOnCardsAndPagesOfFewerCards() throws IOException {
        ProviderPage<BookPOJO> author = listing("author.html", KING, 1);
        assertEquals(16, author.items.size());
        assertTrue(author.hasMore);
        assertEquals("Почти как «бьюик»", author.items.get(0).getName());
        // 15 cards on a page that is not the last
        ProviderPage<BookPOJO> p2 = listing("author-p2.html", SITE.pageUrl(KING, 2), 2);
        assertEquals(15, p2.items.size());
        assertTrue(p2.hasMore);

        BookPOJO king = SITE.searchResults(page("search-king-p1.html", S + "/index.php"), 1).items.get(0);
        assertEquals("Шон Кинг и Мишель Максвелл", king.getSeries());
        assertEquals(S + "/xfsearch/serie/%D1%88%D0%BE%D0%BD%20%D0%BA%D0%B8%D0%BD%D0%B3%20%D0%B8%20%D0%BC%D0%B8%D1%88"
                + "%D0%B5%D0%BB%D1%8C%20%D0%BC%D0%B0%D0%BA%D1%81%D0%B2%D0%B5%D0%BB%D0%BB/", king.getUrlSeries());
    }

    @Test
    public void genreListingAndRankings() throws IOException {
        ProviderPage<BookPOJO> p1 = listing("genre-p1.html", GENRE, 1);
        assertEquals(16, p1.items.size());
        assertTrue(p1.hasMore);
        assertEquals("25475", ids(p1.items, 1));
        assertEquals("Мутант", p1.items.get(15).getName());
        ProviderPage<BookPOJO> p2 = listing("genre-p2.html", SITE.pageUrl(GENRE, 2), 2);
        assertEquals("25415 25414 25413", ids(p2.items, 3));

        assertEquals("16149 20380 4633", ids(listing("listing-genre-popular-p1.html", GENRE + "popular/", 1).items, 3));
        assertEquals("25359 25397 25360",
                ids(listing("listing-genre-popular-day-p1.html", GENRE + "popular/day/", 1).items, 3));
        assertEquals("21258 24211 21275",
                ids(listing("listing-genre-popular-week-p2.html", GENRE + "popular/week/page/2/", 2).items, 3));
        assertEquals("647 25090 16149",
                ids(listing("listing-genre-popular-month-p1.html", GENRE + "popular/month/", 1).items, 3));
        ProviderPage<BookPOJO> rating = listing("listing-genre-rating-p1.html", GENRE + "rating/", 1);
        assertEquals("16238 16149 12514", ids(rating.items, 3));
        assertTrue(rating.hasMore);
        ProviderPage<BookPOJO> longOnes = listing("listing-genre-fd-l-p1.html", GENRE + "?fd=l", 1);
        assertEquals("ГЛУБИНА. Погружение 39-е", longOnes.items.get(0).getName());
        assertTrue(longOnes.hasMore);
    }

    @Test
    public void siteWideSortsAndTopPages() throws IOException {
        String views = SITE.sortUrl(Sort.POPULAR, AudioknigaOne.INDEX);
        assertEquals("16149 221 18454", ids(listing("listing-views-p1.html", views, 1).items, 3));
        assertEquals("4895 9469 528", ids(listing("listing-views-p2.html", SITE.pageUrl(views, 2), 2).items, 3));
        String rating = SITE.sortUrl(Sort.RATING_ALL_TIME, AudioknigaOne.INDEX);
        assertEquals("218 16149 4832", ids(listing("listing-dlerating-p1.html", rating, 1).items, 3));
        assertEquals("17643 219 528",
                ids(listing("listing-dlerating-p2.html", SITE.pageUrl(rating, 2), 2).items, 3));
        String comments = SITE.sortUrl(Sort.COMMENTS, AudioknigaOne.INDEX);
        assertEquals("11434 12776 10758", ids(listing("listing-comments-p1.html", comments, 1).items, 3));
        assertEquals("17643 18454 22800",
                ids(listing("listing-comments-p2.html", SITE.pageUrl(comments, 2), 2).items, 3));
        ProviderPage<BookPOJO> title = listing("listing-title-p1.html", SITE.sortUrl(Sort.TITLE, AudioknigaOne.INDEX), 1);
        assertEquals("#Girlboss", title.items.get(0).getName());
        assertTrue(title.hasMore);

        ProviderPage<BookPOJO> top = listing("listing-top100-p1.html", AudioknigaOne.TOP_100, 1);
        assertEquals(100, top.items.size());
        assertFalse(top.hasMore);
        assertEquals("647 24111 24644", ids(top.items, 3));
        assertEquals("20 ч. 14 мин.", top.items.get(0).getTime());
        ProviderPage<BookPOJO> week = listing("listing-week-p1.html", AudioknigaOne.TOP_WEEK, 1);
        assertEquals(20, week.items.size());
        assertFalse(week.hasMore);
        assertEquals("25397 25392 25385", ids(week.items, 3));
        ProviderPage<BookPOJO> month = listing("listing-month-p1.html", AudioknigaOne.TOP_MONTH, 1);
        assertEquals(30, month.items.size());
        assertFalse(month.hasMore);
        assertEquals("647 25188 25090", ids(month.items, 3));
    }

    // ---------------------------------------------------------------- URLs and sorts

    @Test
    public void pageUrls() {
        assertEquals(S + "/", SITE.pageUrl(AudioknigaOne.INDEX, 1));
        assertEquals(S + "/page/2/", SITE.pageUrl(AudioknigaOne.INDEX, 2));
        String views = SITE.sortUrl(Sort.POPULAR, AudioknigaOne.INDEX);
        assertEquals(S + "/page/3/" + String.format(SORTED, "news_read", "desc"), SITE.pageUrl(views, 3));
        assertEquals(S + "/fantastika/popular/week/page/2/", SITE.pageUrl(GENRE + "popular/week/", 2));
        // the pager drops the duration bound; the next page keeps it
        assertEquals(S + "/fantastika/page/2/?fd=l", SITE.pageUrl(GENRE + "?fd=l", 2));
        assertEquals(AudioknigaOne.AUTHORS + "page/2/", SITE.pageUrl(AudioknigaOne.AUTHORS, 2));
        assertEquals(KING + "page/2/", SITE.pageUrl(KING, 2));
    }

    @Test
    public void siteWideMenu() {
        List<Sort.Entry> menu = SITE.sorts(AudioknigaOne.INDEX);
        assertEquals(6, menu.size());
        assertEquals(Sort.NEW, menu.get(0).sort);
        assertEquals(Sort.POPULAR, menu.get(1).sort);
        assertEquals(java.util.Arrays.asList(Sort.RATING_WEEK, Sort.RATING_MONTH, Sort.RATING_ALL_TIME),
                menu.get(2).items);
        assertEquals(Sort.COMMENTS, menu.get(3).sort);
        assertEquals(Sort.TITLE, menu.get(4).sort);
        assertEquals(Sort.TOP_100, menu.get(5).sort);

        assertEquals(AudioknigaOne.INDEX, SITE.sortUrl(Sort.NEW, AudioknigaOne.INDEX));
        String views = S + "/" + String.format(SORTED, "news_read", "desc");
        assertEquals(views, SITE.sortUrl(Sort.POPULAR, AudioknigaOne.INDEX));
        assertEquals(views, SITE.sortUrl(Sort.POPULAR, views));
        assertEquals(AudioknigaOne.INDEX, SITE.sortUrl(Sort.NEW, views));
        assertEquals(S + "/" + String.format(SORTED, "rating", "desc"),
                SITE.sortUrl(Sort.RATING_ALL_TIME, AudioknigaOne.INDEX));
        assertEquals(S + "/" + String.format(SORTED, "comm_num", "desc"),
                SITE.sortUrl(Sort.COMMENTS, AudioknigaOne.INDEX));
        assertEquals(S + "/" + String.format(SORTED, "title", "asc"), SITE.sortUrl(Sort.TITLE, AudioknigaOne.INDEX));
        assertEquals(AudioknigaOne.TOP_100, SITE.sortUrl(Sort.TOP_100, AudioknigaOne.INDEX));
        assertEquals(AudioknigaOne.TOP_WEEK, SITE.sortUrl(Sort.RATING_WEEK, AudioknigaOne.TOP_100));
        assertEquals(AudioknigaOne.TOP_MONTH, SITE.sortUrl(Sort.RATING_MONTH, AudioknigaOne.INDEX));
        assertNull(SITE.sortUrl(Sort.POPULAR_WEEK, AudioknigaOne.INDEX));
        assertNull(SITE.sortUrl(Sort.RATING, AudioknigaOne.INDEX));
        // a sorted listing and the top pages keep the menu
        assertEquals(menu, SITE.sorts(views));
        assertEquals(menu, SITE.sorts(SITE.pageUrl(views, 2)));
        assertEquals(menu, SITE.sorts(AudioknigaOne.TOP_WEEK));
        assertEquals(menu, SITE.sorts(SITE.pageUrl(AudioknigaOne.INDEX, 5)));
    }

    @Test
    public void genreAndPeopleRankThemselves() {
        List<Sort.Entry> menu = SITE.sorts(GENRE);
        assertEquals(3, menu.size());
        assertEquals(Sort.NEW, menu.get(0).sort);
        assertEquals(java.util.Arrays.asList(Sort.POPULAR_DAY, Sort.POPULAR_WEEK, Sort.POPULAR_MONTH,
                Sort.POPULAR_ALL_TIME), menu.get(1).items);
        assertEquals(Sort.RATING, menu.get(2).sort);
        assertEquals(menu, SITE.sorts(GENRE + "popular/week/page/3/"));
        assertEquals(menu, SITE.sorts(GENRE + "rating/?fd=l"));
        assertEquals(menu, SITE.sorts(S + "/fjentezi/litrpg/"));
        assertEquals(menu, SITE.sorts(KING));
        assertEquals(menu, SITE.sorts(S + "/xfsearch/ispolnitel/%D0%BC%D0%B0%D1%8F%D0%BA/rating/"));

        assertEquals(GENRE + "popular/day/", SITE.sortUrl(Sort.POPULAR_DAY, GENRE));
        assertEquals(GENRE + "popular/week/", SITE.sortUrl(Sort.POPULAR_WEEK, GENRE + "rating/page/2/"));
        assertEquals(GENRE + "popular/month/", SITE.sortUrl(Sort.POPULAR_MONTH, GENRE));
        assertEquals(GENRE + "popular/", SITE.sortUrl(Sort.POPULAR_ALL_TIME, GENRE + "popular/day/"));
        assertEquals(GENRE + "rating/", SITE.sortUrl(Sort.RATING, GENRE));
        assertEquals(GENRE, SITE.sortUrl(Sort.NEW, GENRE + "rating/"));
        assertEquals(GENRE + "popular/?fd=l", SITE.sortUrl(Sort.POPULAR_ALL_TIME, GENRE + "popular/?fd=l"));
        assertEquals(S + "/fjentezi/litrpg/rating/", SITE.sortUrl(Sort.RATING, S + "/fjentezi/litrpg/popular/"));
        assertEquals(KING + "popular/week/", SITE.sortUrl(Sort.POPULAR_WEEK, KING));
        assertNull(SITE.sortUrl(Sort.COMMENTS, GENRE));
        assertNull(SITE.sortUrl(Sort.TOP_100, KING));

        assertEquals(GENRE, AudioknigaOne.rankedRoot(GENRE + "popular/week/page/2/?fd=s"));
        assertEquals(S + "/fjentezi/litrpg/", AudioknigaOne.rankedRoot(S + "/fjentezi/litrpg/rating/"));
        assertEquals(KING, AudioknigaOne.rankedRoot(KING + "popular/page/4/"));
        assertNull(AudioknigaOne.rankedRoot(GENRE + "best/"));
        assertNull(AudioknigaOne.rankedRoot(AudioknigaOne.INDEX));
        assertNull(AudioknigaOne.rankedRoot(AudioknigaOne.AUTHORS));
        assertNull(AudioknigaOne.rankedRoot(AudioknigaOne.AUTHORS + "page/2/"));
        assertNull(AudioknigaOne.rankedRoot(S + "/page/2/"));
        assertNull(AudioknigaOne.rankedRoot(BOOK));
        assertNull(AudioknigaOne.rankedRoot(AudioknigaOne.TOP_100));
        assertNull(AudioknigaOne.rankedRoot("https://audiokniga.life/fantastika/"));
        // the LitRes section answers popular/ with its plain listing
        assertNull(AudioknigaOne.rankedRoot(S + "/paid/fantastika-i-fjentezi/"));
        assertTrue(SITE.sorts(S + "/paid/fantastika-i-fjentezi/").isEmpty());
    }

    /** Every Order item of the index, a genre and an author opens a listing of the site that keeps the item. */
    @Test
    public void everyMenuItemKeepsItsMenu() {
        for (String listing : new String[]{AudioknigaOne.INDEX, GENRE, S + "/fjentezi/litrpg/page/3/", KING}) {
            for (Sort.Entry entry : SITE.sorts(listing)) {
                List<Sort> sorts = entry.sort != null ? java.util.Collections.singletonList(entry.sort) : entry.items;
                for (Sort sort : sorts) {
                    String sorted = SITE.sortUrl(sort, listing);
                    assertTrue(listing + " " + sort + " " + sorted, sorted != null && sorted.startsWith(S + "/"));
                    assertTrue(listing + " " + sort, SITE.pageUrl(sorted, 2).startsWith(S + "/"));
                    boolean kept = false;
                    for (Sort.Entry again : SITE.sorts(sorted)) {
                        kept |= again.sort == sort || again.items.contains(sort);
                    }
                    assertTrue(listing + " " + sort + " loses its menu item", kept);
                }
            }
        }
    }

    @Test
    public void unsortedListings() {
        assertTrue(SITE.sorts(S + "/xfsearch/serie/%D0%BA%D0%BE%D1%82%D1%8B-%D0%B2%D0%BE%D0%B8%D1%82%D0%B5%D0%BB%D0%B8/")
                .isEmpty());
        assertTrue(SITE.sorts(SITE.searchUrl(SearchUrls.Category.BOOKS, "кинг", 1)).isEmpty());
        assertTrue(SITE.sorts(S + "/tags/%D0%BC%D0%B0%D0%B3/").isEmpty());
        assertTrue(SITE.sorts(AudioknigaOne.AUTHORS).isEmpty());
        assertFalse(SITE.hasSort(AudioknigaOne.READERS));
        assertSame(Provider.AUDIOKNIGA_ONE, Provider.ofUrl(AudioknigaOne.INDEX));
    }

    @Test
    public void durationBoundOnRankedListings() {
        assertEquals(GENRE + "?fd=x", SITE.withDuration(GENRE, ListingFilters.Duration.UNDER_30M));
        assertEquals(GENRE + "?fd=x", SITE.withDuration(GENRE + "?fd=l", ListingFilters.Duration.FROM_1H_TO_2H));
        assertEquals(GENRE + "popular/week/?fd=s",
                SITE.withDuration(GENRE + "popular/week/", ListingFilters.Duration.of(150, 300)));
        assertEquals(KING + "?fd=m", SITE.withDuration(KING, ListingFilters.Duration.of(300, 600)));
        assertEquals(GENRE + "rating/?fd=l", SITE.withDuration(GENRE + "rating/",
                ListingFilters.Duration.of(600, ListingFilters.Duration.UNBOUNDED)));
        // a range across the server's bounds, or none at all, goes without one
        assertEquals(GENRE, SITE.withDuration(GENRE + "?fd=s", ListingFilters.Duration.OVER_2H));
        assertEquals(GENRE, SITE.withDuration(GENRE + "?fd=s", ListingFilters.Duration.ANY));
        // the index ignores fd
        assertEquals(AudioknigaOne.INDEX, SITE.withDuration(AudioknigaOne.INDEX, ListingFilters.Duration.UNDER_30M));
        assertTrue(SITE.listsDuration(GENRE));
        assertTrue(SITE.listsDuration(AudioknigaOne.TOP_100));
        // LitRes books show no time
        assertFalse(SITE.listsDuration(S + "/paid/fantastika-i-fjentezi/"));
    }

    // ---------------------------------------------------------------- genres, people

    @Test
    public void genres() throws IOException {
        ProviderPage<GenrePOJO> genres = SITE.genres(page("genres.html", AudioknigaOne.GENRES), AudioknigaOne.GENRES, 1);
        assertEquals(35, genres.items.size());
        assertFalse(genres.hasMore);
        GenrePOJO first = genres.items.get(0);
        assertEquals("Фантастика", first.getName());
        assertEquals(GENRE, first.getUrl());
        assertEquals(6824, first.getReting());
        assertEquals("Бояръ-Аниме", genres.items.get(2).getName());
        assertEquals(S + "/fjentezi/bojar-anime/", genres.items.get(2).getUrl());
        assertEquals("Про деревню", genres.items.get(34).getName());
        assertEquals(226, genres.items.get(34).getReting());
    }

    @Test
    public void peopleIndexes() throws IOException {
        ProviderPage<GenrePOJO> authors = SITE.people(page("people-authors-p1.html", AudioknigaOne.AUTHORS),
                AudioknigaOne.AUTHORS, 1, false);
        assertEquals(16, authors.items.size());
        assertTrue(authors.hasMore);
        assertEquals("Агата Кристи", authors.items.get(0).getName());
        assertEquals(S + "/xfsearch/avtor/%D0%B0%D0%B3%D0%B0%D1%82%D0%B0%20%D0%BA%D1%80%D0%B8%D1%81%D1%82%D0%B8/",
                authors.items.get(0).getUrl());
        assertEquals(277, authors.items.get(0).getReting());
        String p2 = SITE.pageUrl(AudioknigaOne.AUTHORS, 2);
        assertEquals("Николай Гоголь", SITE.people(page("people-authors-p2.html", p2), p2, 2, false).items.get(0)
                .getName());
        ProviderPage<GenrePOJO> readers = SITE.people(page("people-readers-p1.html", AudioknigaOne.READERS),
                AudioknigaOne.READERS, 1, true);
        assertEquals("Олег Булдаков", readers.items.get(0).getName());
        assertEquals(589, readers.items.get(0).getReting());
        assertTrue(readers.hasMore);
    }

    @Test
    public void peopleSearch() throws IOException {
        assertEquals(S + "/engine/ajax/controller.php?mod=site_search&ctx=page&t=author&q=%D0%9A%D0%B8%D0%BD%D0%B3&off=30",
                SITE.searchUrl(SearchUrls.Category.AUTHORS, "Кинг", 2));
        assertEquals(S + "/engine/ajax/controller.php?mod=site_search&ctx=page&t=reader&q=%D0%9A%D0%B8%D0%BD%D0%B3&off=0",
                SITE.searchUrl(SearchUrls.Category.PERFORMERS, "Кинг", 1));
        String url = SITE.searchUrl(SearchUrls.Category.AUTHORS, "Кинг", 1);
        ProviderPage<GenrePOJO> authors = SITE.searchPeople(
                BookPageFixtures.json("audiokniga-one/ajax-site-search-author.json", url), 1, false);
        assertEquals(10, authors.items.size());
        assertFalse(authors.hasMore);
        assertEquals("Стивен Кинг", authors.items.get(0).getName());
        assertEquals(KING, authors.items.get(0).getUrl());
        assertEquals(218, authors.items.get(0).getReting());
        ProviderPage<GenrePOJO> readers = SITE.searchPeople(
                BookPageFixtures.json("audiokniga-one/ajax-site-search-reader.json", url), 1, true);
        assertEquals(2, readers.items.size());
        assertEquals("Илья Кочетков", readers.items.get(0).getName());
        // a one-letter query: only the counts
        assertEquals(ProviderPage.Status.EMPTY, SITE.searchPeople(
                BookPageFixtures.json("audiokniga-one/ajax-site-search-short.json", url), 1, false).status);
    }

    // ---------------------------------------------------------------- search

    @Test
    public void bookSearch() throws IOException {
        assertEquals(S + "/index.php?do=search&subaction=search&story=%D0%BA%D0%B8%D0%BD%D0%B3+%D0%BE%D0%BD%D0%BE"
                + "&search_start=0&full_search=0", SITE.searchUrl(SearchUrls.Category.BOOKS, "кинг оно", 1));
        String second = SITE.searchUrl(SearchUrls.Category.BOOKS, "Кинг", 2);
        assertTrue(second, second.contains("&search_start=16&"));

        ProviderPage<BookPOJO> p1 = SITE.searchResults(page("search-king-p1.html", S + "/index.php"), 1);
        assertEquals(16, p1.items.size());
        assertTrue(p1.hasMore);
        BookPOJO first = p1.items.get(0);
        assertEquals("Игра по расписанию", first.getName());
        assertEquals("19 ч. 3 мин.", first.getTime());
        assertEquals("Дэвид Бальдаччи", first.getAutor());
        assertEquals("4", first.getReting());
        ProviderPage<BookPOJO> p2 = SITE.searchResults(page("search-king-p2.html", second), 2);
        assertEquals("Полицейский из библиотеки", p2.items.get(0).getName());
        assertTrue(p2.hasMore);
        assertEquals("8650 8643 8171", ids(SITE.searchResults(page("search-mag-p2.html", second), 2).items, 3));

        ProviderPage<BookPOJO> one = SITE.searchResults(page("other-source-search.html", S + "/index.php"), 1);
        assertEquals(12, one.items.size());
        assertFalse(one.hasMore);
        assertEquals("6778", ids(one.items, 1));
        assertEquals(12, SITE.otherSourceHits(page("other-source-search.html", S + "/index.php")).size());

        ProviderPage<BookPOJO> none = SITE.searchResults(page("search-none.html", S + "/index.php"), 1);
        assertEquals(ProviderPage.Status.EMPTY, none.status);
        assertFalse(none.hasMore);
    }

    // ---------------------------------------------------------------- book page

    @Test
    public void bookPage() throws IOException {
        Document doc = page("book.html", BOOK);
        DescriptionPOJO d = SITE.description(doc, BOOK);
        assertEquals("Идеальный мир для Социопата (1-13)", d.getTitle());
        assertEquals(S + "/uploads/posts/2024-11/1731603735_1.webp", d.getPoster());
        assertEquals("Фантастика, Постапокалипсис", d.getGenre());
        assertEquals(GENRE, d.getGenreUrl());
        assertEquals("Олег Сапфир", d.getAutor());
        // the page writes a raw space in the link
        assertEquals(S + "/xfsearch/avtor/%D0%BE%D0%BB%D0%B5%D0%B3%20%D1%81%D0%B0%D0%BF%D1%84%D0%B8%D1%80/",
                d.getAutorUrl());
        assertEquals("Александр Башков", d.getArtist());
        assertEquals(S + "/xfsearch/ispolnitel/%D0%B0%D0%BB%D0%B5%D0%BA%D1%81%D0%B0%D0%BD%D0%B4%D1%80%20%D0%B1%D0%B0%D1"
                + "%88%D0%BA%D0%BE%D0%B2/", d.getArtistUrl());
        assertEquals("104 ч. 21 мин.", d.getTime());
        assertEquals("", d.getSeries());
        assertEquals("1538", d.getLike());
        assertEquals("101", d.getDisLike());
        assertEquals(BookPOJO.formatRating(4.8f, "1639"), d.getReiting());
        assertTrue(d.getDescription(), d.getDescription().startsWith("Ты социопат и тебя не устраивает твоя жизнь?"));
        assertEquals(4, d.getDescription().split("\n").length);

        BookPOJO card = SITE.book(doc, BOOK);
        assertEquals("Идеальный мир для Социопата (1-13)", card.getName());
        assertEquals("1538", card.getReting());
        assertEquals(112, card.getComents());
        assertEquals("Олег Сапфир", card.getAutor());

        ArrayList<BookPOJO> recommended = SITE.recommendations(doc, BOOK);
        assertEquals(12, recommended.size());
        // the author's other books name their reader
        assertEquals(S + "/11682-kodeks-ohotnika-7-15.html", recommended.get(0).getUrl());
        assertEquals("Кодекс охотника 7-15", recommended.get(0).getName());
        assertEquals("Александр Башков", recommended.get(0).getArtist());
        assertEquals("656", recommended.get(0).getReting());
        assertEquals(41, recommended.get(0).getComents());
        assertTrue(recommended.get(0).getPhoto().startsWith(S + "/uploads/posts/2023-11/"));
        assertEquals("Идеальный мир для Лекаря 11", recommended.get(1).getName());
        // the similar ones their author
        assertEquals("4052", ids(recommended.subList(6, 7), 1));
        assertEquals("Андрей Плеханов", recommended.get(6).getAutor());
    }

    @Test
    public void seriesBook() throws IOException {
        Document doc = page("book-series.html", BOOK_SERIES);
        DescriptionPOJO d = SITE.description(doc, BOOK_SERIES);
        assertEquals("Царское дело", d.getTitle());
        assertEquals("Русские детективы, Приключения", d.getGenre());
        assertEquals(S + "/detektiv/russkie-detektivy/", d.getGenreUrl());
        assertEquals("Сергей Булыга", d.getAutor());
        assertEquals("Ирина Воробьёва", d.getArtist());
        assertEquals("9 ч. 19 мин.", d.getTime());
        assertEquals("Дела Разбойного Приказа", d.getSeries());
        String series = S + "/xfsearch/serie/%D0%B4%D0%B5%D0%BB%D0%B0%20%D1%80%D0%B0%D0%B7%D0%B1%D0%BE%D0%B9%D0%BD%D0%BE"
                + "%D0%B3%D0%BE%20%D0%BF%D1%80%D0%B8%D0%BA%D0%B0%D0%B7%D0%B0/";
        assertEquals(series, d.getSeriesUrl());
        assertEquals(series, AudioknigaOne.seriesUrl(doc));
        assertEquals(BookPOJO.formatRating(5.0f, "2"), d.getReiting());
        assertTrue(d.getDescription().startsWith("Царь Иван Грозный скончался 18 марта 1584 года"));
        assertEquals("", AudioknigaOne.seriesUrl(page("book.html", BOOK)));
        assertTrue(SITE.sorts(series).isEmpty());

        ArrayList<SeriesPOJO> two = AudioknigaOne.parseSeries(page("series.html", series));
        assertEquals(2, two.size());
        assertEquals("Углицкое дело", two.get(0).getName());
        assertEquals(S + "/25343-uglickoe-delo.html", two.get(0).getUrl());
        assertEquals("1", two.get(0).getNumber());
        assertEquals("2", two.get(1).getNumber());
        assertTrue(two.get(0).getPhoto().startsWith(S + "/uploads/"));
        ArrayList<SeriesPOJO> cats = AudioknigaOne.parseSeries(page("series-big.html", series));
        assertEquals(13, cats.size());
        for (int i = 0; i < cats.size(); i++) {
            assertEquals(String.valueOf(i + 1), cats.get(i).getNumber());
        }
        assertEquals(S + "/7600", cats.get(0).getUrl().substring(0, S.length() + 5));
    }

    @Test
    public void mp3Chapters() throws IOException {
        Document doc = page("book.html", BOOK);
        ArrayList<AudioPOJO> tracks = SITE.tracks(doc, BOOK);
        assertEquals(307, tracks.size());
        AudioPOJO first = tracks.get(0);
        assertEquals("01. Книга 1_001", first.getName());
        assertEquals("Идеальный мир для Социопата (1-13)", first.getBookName());
        assertEquals("https://s3.regru.cloud/s-3.readsound.ru/lib1/public_files/2024-11/kniga-1_001.mp3", first.getUrl());
        assertEquals(1306, first.getTime());
        assertEquals(-1, first.getTimeStart());
        assertEquals("0307. Книга 13_024", tracks.get(306).getName());
        assertEquals(1112, tracks.get(306).getTime());
        int total = 0;
        for (AudioPOJO track : tracks) {
            total += track.getTime();
        }
        assertEquals(375688, total);
    }

    @Test
    public void hlsChapters() throws IOException {
        ArrayList<AudioPOJO> tracks = SITE.tracks(page("book-hls.html", BOOK_HLS), BOOK_HLS);
        assertEquals(19, tracks.size());
        assertEquals("https://s3.regru.cloud/s-4.audiokniga.one/public_files/2026-10/hls/1793213364_glava-01/index.m3u8",
                tracks.get(0).getUrl());
        assertEquals(341, tracks.get(0).getTime());
        assertEquals("019. Глава 19", tracks.get(18).getName());
        assertEquals(4741, tracks.get(18).getTime());
        int total = 0;
        for (AudioPOJO track : tracks) {
            total += track.getTime();
        }
        assertEquals(36998, total);
        ArrayList<AudioPOJO> series = SITE.tracks(page("book-series.html", BOOK_SERIES), BOOK_SERIES);
        assertEquals(57, series.size());
        assertEquals("057. Глава 57", series.get(56).getName());
    }

    @Test
    public void litresAndRemovedBooks() throws IOException {
        String paid = S + "/25253-tovarisch-voenvrach-2.html";
        Document doc = page("book-paid.html", paid);
        DescriptionPOJO d = SITE.description(doc, paid);
        assertEquals("Товарищ военврач 2", d.getTitle());
        assertEquals("Валерий Гуров, Олег Дмитриев", d.getAutor());
        assertEquals("", d.getTime());
        // its first genre is the LitRes section, a listing without times or rankings
        assertEquals(S + "/paid/", d.getGenreUrl());
        assertFalse(SITE.listsDuration(d.getGenreUrl()));
        assertTrue(SITE.sorts(d.getGenreUrl()).isEmpty());
        try {
            SITE.tracks(doc, paid);
            fail("a LitRes fragment is no book to play");
        } catch (BookRemovedException expected) {
            // the fragment only
        }
        try {
            SITE.tracks(page("book-missing.html", S + "/99999999-x.html"), S + "/99999999-x.html");
            fail("a removed book has no chapters");
        } catch (BookRemovedException expected) {
            // "Данная аудиокнига удалена"
        }
        try {
            SITE.tracks(page("genres.html", BOOK), BOOK);
            fail("a page without a player");
        } catch (IllegalStateException expected) {
            // not a book page
        }
    }

    @Test
    public void comments() throws IOException {
        Document doc = page("book.html", BOOK);
        assertEquals(9, AudioknigaOne.commentPages(doc));
        ArrayList<ComentsPOJO> first = AudioknigaOne.parseComments(doc);
        assertEquals(10, first.size());
        ComentsPOJO newest = first.get(0);
        assertEquals("Гость Гость", newest.getName());
        assertEquals("В среду в 08:24", newest.getDate());
        assertTrue(newest.getText().startsWith("Сюжет вроде хороший"));
        assertEquals(S + "/templates/tema-23/dleimages/noavatar.png", newest.getImage());
        assertEquals("Sova", first.get(2).getName());
        assertEquals("2", first.get(2).getReting());

        assertEquals(S + "/engine/ajax/controller.php?mod=comments&cstart=2&news_id=16149&skin=tema-23&massact=disable",
                AudioknigaOne.commentsUrl(BOOK, 2));
        ArrayList<ComentsPOJO> second = AudioknigaOne.parseCommentsPage(
                BookPageFixtures.json("audiokniga-one/comments-p2.json", AudioknigaOne.commentsUrl(BOOK, 2)));
        assertEquals(10, second.size());
        ComentsPOJO thread = second.get(0);
        assertEquals("Гость Пётр", thread.getName());
        assertEquals("19 сентября 2026 в 11:33", thread.getDate());
        assertEquals(1, thread.getChildComents().size());
        assertEquals("Sova", thread.getChildComents().get(0).getName());
        assertEquals("Гость Пётр", thread.getChildComents().get(0).getParentName());
        int replies = 0;
        for (ComentsPOJO c : second) {
            replies += c.getChildComents().size();
        }
        assertEquals(4, replies);

        Document hls = page("book-hls.html", BOOK_HLS);
        assertEquals(1, AudioknigaOne.commentPages(hls));
        assertTrue(AudioknigaOne.parseComments(hls).isEmpty());
    }
}
