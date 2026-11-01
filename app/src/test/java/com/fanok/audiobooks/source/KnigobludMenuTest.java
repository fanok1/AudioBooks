package com.fanok.audiobooks.source;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.fanok.audiobooks.R;
import com.fanok.audiobooks.search.ListingFilters;
import com.fanok.audiobooks.search.Provider;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

/**
 * knigoblud's Order menu pinned to git HEAD: books_options_menu.xml with the knigoblud toggles of
 * BooksFragment.onPrepareMenu, the knigoblud branch of BooksPresenter.onOptionItemSelected, HEAD
 * ListingFilters.genreOf/inGenre and HEAD BooksPresenter.pageUrl. All expected values are HEAD literals.
 */
public class KnigobludMenuTest {

    private static final Provider P = Provider.KNIGOBLUD;
    private static final SourceParser PARSER = Provider.KNIGOBLUD.parser;

    // HEAD Url.java:76-84
    private static final String SERVER = "https://www.knigoblud.club";
    private static final String INDEX = SERVER;
    private static final String TODAY = SERVER + "/today/?page=";
    private static final String POPULAR_WEEK = SERVER + "/popular/?period=week&page=";
    private static final String POPULAR_MONTH = SERVER + "/popular/?period=month&page=";
    private static final String TRENDING = SERVER + "/trending/?page=";
    private static final String FINISHED = SERVER + "/finished/?page=";

    // HEAD GenreModel.java:191 (first of KNIGOBLUD_GENRE_URLS); a card's genre link (fixtures/knigoblud/listing-today-p1.html)
    private static final String GENRE_ID = "9d47c7aa-9e2e-4fe0-bcd5-14f7d4874198";
    private static final String GENRE = SERVER + "/" + GENRE_ID;
    // HEAD ListingFilters.java:616-618: a ranking opened from that genre
    private static final String GENRE_WEEK = SERVER + "/popular/?genre=" + GENRE_ID + "&period=week&page=";
    private static final String GENRE_MONTH = SERVER + "/popular/?genre=" + GENRE_ID + "&period=month&page=";
    private static final String GENRE_TODAY = SERVER + "/today/?genre=" + GENRE_ID + "&page=";
    private static final String GENRE_TRENDING = SERVER + "/trending/?genre=" + GENRE_ID + "&page=";
    private static final String GENRE_FINISHED = SERVER + "/finished/?genre=" + GENRE_ID + "&page=";
    // fixtures/knigoblud/listing-today-p1.html: ✍ author, 🎙 reader, 📚 series links (/<uuid>, not genres)
    private static final String AUTHOR = SERVER + "/e08742ad-0309-493d-9d55-d2b4058819eb";
    private static final String READER = SERVER + "/881e21c2-9a5e-4fc0-9dc3-cce8bbeea9d5";
    private static final String SERIES = SERVER + "/15a85a07-11bc-44ca-a424-0c3d6d1371b3";

    // HEAD GenreModel.java:191-212: every genre id of KNIGOBLUD_GENRE_URLS
    private static final List<String> HEAD_GENRE_IDS = Arrays.asList(
            "9d47c7aa-9e2e-4fe0-bcd5-14f7d4874198", "4daab267-3592-4604-9e34-8f3b6a1db9fe",
            "0efa0796-8734-4735-ac03-d4eca1ccb013", "8ea14af7-f600-4e30-a0eb-67b615cdb701",
            "760dd554-482b-4817-8283-5b9ae9e76431", "5982eae9-3868-4593-a9b0-b3378370345f",
            "31d39d95-7bf5-420b-9db4-66c448785a07", "0c6d4928-555a-4671-99e9-9bcf9a37d382",
            "f00832de-609c-4cc8-9017-2571caa4bfb2", "fb8d64ce-902d-44aa-9c21-77cb85a5b418",
            "9da19d35-32ee-41e7-ab8c-5ec554fcdd8a", "7445b383-418a-475a-8374-ef2822d0552c",
            "a1d4487a-12d6-4280-b1a2-4c204bd6908a", "e7d0d5bc-210a-4fc6-9598-19baf47c9b13",
            "09afc417-fa54-4b56-bb54-f950296a0b50", "697856ad-1d48-48d5-ad20-5f00cc169f75",
            "07bc7998-8f32-49a1-8048-07a2c2294a11", "4c37cad9-8ee6-4ce7-b62e-b52ef717952c",
            "61c37161-1899-4253-a4c3-9417f4b5c1c5", "120a1664-12e5-4ac6-853f-792f6a8007e9",
            "e4117eca-c027-4f80-8004-3851d9b562c7", "70bb89e8-65f7-414d-b98d-107878734c89");

    // ---------------------------------------------------------------- helpers

    private static void assertItem(Sort.Entry entry, Sort sort) {
        assertEquals(sort, entry.sort);
        assertEquals(sort.title, entry.title);
        assertTrue(entry.items.isEmpty());
    }

    private static void assertSubmenu(Sort.Entry entry, int title, Sort... items) {
        // intentional: a submenu header is never a sort (brief 4a)
        assertNull(entry.sort);
        assertEquals(title, entry.title);
        assertEquals(Arrays.asList(items), entry.items);
    }

    /**
     * HEAD books_options_menu.xml order items with BooksFragment.java:287-304 (isKnigoblud): new_data, popular_parent
     * (all_time, month, week, day), knigoblud_today, knigoblud_trending, knigoblud_finished; reting and abmp3 hidden.
     */
    // changed on purpose after the live check (2026-10-30): the Popular submenu has only month and week (the site has no all-time/day popularity; those items used to reopen the current listing)
    private static void assertKnigobludMenu(String url) {
        List<Sort.Entry> menu = PARSER.sorts(url);
        assertEquals(url, 5, menu.size());
        assertItem(menu.get(0), Sort.NEW);
        assertSubmenu(menu.get(1), R.string.popular, Sort.POPULAR_MONTH, Sort.POPULAR_WEEK);
        assertItem(menu.get(2), Sort.TODAY);
        assertItem(menu.get(3), Sort.TRENDING);
        assertItem(menu.get(4), Sort.FINISHED);
    }

    /** HEAD BooksPresenter.java:361-362: page N of a knigoblud listing. */
    private static String headPage2(String url) {
        return url.contains("?") ? url + 2 : url + "/" + 2;
    }

    /** HEAD BooksPresenter.java:696-836: the subtitle label and back-stack tag of each item id. */
    private static void assertLabelAndTag(Sort sort) {
        int label;
        String tag;
        if (sort.menuId == R.id.new_data) {
            label = R.string.order_new;
            tag = "audioBooksOrederNew";
        } else if (sort.menuId == R.id.popular_all_time) {
            label = R.string.order_popular;
            tag = "audioBooksOrederDiscussedAllTime";
        } else if (sort.menuId == R.id.popular_month) {
            label = R.string.order_popular;
            tag = "audioBooksOrederDiscussedMonth";
        } else if (sort.menuId == R.id.popular_week) {
            label = R.string.order_popular;
            tag = "audioBooksOrederDiscussedWeek";
        } else if (sort.menuId == R.id.popular_day) {
            label = R.string.order_popular;
            tag = "audioBooksOrederDiscussedDay";
        } else if (sort.menuId == R.id.knigoblud_today) {
            label = R.string.knigoblud_today;
            tag = "knigobludToday";
        } else if (sort.menuId == R.id.knigoblud_trending) {
            label = R.string.knigoblud_trending;
            tag = "knigobludTrending";
        } else if (sort.menuId == R.id.knigoblud_finished) {
            label = R.string.knigoblud_finished;
            tag = "knigobludFinished";
        } else {
            throw new AssertionError("HEAD's knigoblud menu has no item " + sort);
        }
        assertEquals(sort.toString(), label, sort.label);
        assertEquals(sort.toString(), tag, sort.tag);
    }

    /** An item of a Sort the site does not have: nothing opens (its label and tag stay HEAD's). */
    // changed on purpose after the live check (2026-10-30): POPULAR_ALL_TIME and POPULAR_DAY have no listing; sortUrl returns null (HEAD reopened the current listing)
    private static void assertOpensNothing(String url, Sort sort) {
        assertNull(sort + " from " + url, ListingFilters.sorted(P, sort, url));
        assertLabelAndTag(sort);
    }

    /** What HEAD opened for {@code sort} from {@code url}, its label/tag, and page 2 of it. */
    private static void assertOpens(String url, Sort sort, String expected) {
        String sorted = ListingFilters.sorted(P, sort, url);
        assertEquals(sort + " from " + url, expected, sorted);
        assertLabelAndTag(sort);
        assertEquals(sort + " page 2 from " + url, headPage2(expected), PARSER.pageUrl(sorted, 2));
    }

    // ---------------------------------------------------------------- tests

    // HEAD BooksFragment.java:240,287-304 (menu); BooksPresenter.java:656-671,678-682 (url; new_data == mUrl stays)
    @Test
    public void index() {
        assertKnigobludMenu(INDEX);
        assertOpens(INDEX, Sort.NEW, INDEX);
        // changed on purpose after the live check (2026-10-30): the site has no all-time/day popularity; those items are gone from the submenu and open nothing (they used to reopen the listing)
        assertOpensNothing(INDEX, Sort.POPULAR_ALL_TIME);
        assertOpens(INDEX, Sort.POPULAR_MONTH, POPULAR_MONTH);
        assertOpens(INDEX, Sort.POPULAR_WEEK, POPULAR_WEEK);
        assertOpensNothing(INDEX, Sort.POPULAR_DAY);
        assertOpens(INDEX, Sort.TODAY, TODAY);
        assertOpens(INDEX, Sort.TRENDING, TRENDING);
        assertOpens(INDEX, Sort.FINISHED, FINISHED);
    }

    // HEAD BooksPresenter.java:656-682 + ListingFilters.java:575-583,616-618: rankings carry genre=, "new" is the genre
    @Test
    public void genreListing() {
        assertKnigobludMenu(GENRE);
        assertOpens(GENRE, Sort.NEW, GENRE);
        // changed on purpose after the live check (2026-10-30): the site has no all-time/day popularity; those items are gone from the submenu and open nothing (they used to reopen the listing)
        assertOpensNothing(GENRE, Sort.POPULAR_ALL_TIME);
        assertOpens(GENRE, Sort.POPULAR_MONTH,
                SERVER + "/popular/?genre=" + GENRE_ID + "&period=month&page=");
        assertOpens(GENRE, Sort.POPULAR_WEEK, GENRE_WEEK);
        assertOpensNothing(GENRE, Sort.POPULAR_DAY);
        assertOpens(GENRE, Sort.TODAY, SERVER + "/today/?genre=" + GENRE_ID + "&page=");
        assertOpens(GENRE, Sort.TRENDING, SERVER + "/trending/?genre=" + GENRE_ID + "&page=");
        assertOpens(GENRE, Sort.FINISHED, SERVER + "/finished/?genre=" + GENRE_ID + "&page=");
    }

    // HEAD BooksFragment.java:253-268 + GenreModel.java:191-212: every built-in genre keeps its Order menu
    @Test
    public void everyHeadGenreHasTheMenu() {
        for (String id : HEAD_GENRE_IDS) {
            assertKnigobludMenu(SERVER + "/" + id);
            assertEquals(id, SERVER + "/popular/?genre=" + id + "&period=week&page=",
                    ListingFilters.sorted(P, Sort.POPULAR_WEEK, SERVER + "/" + id));
        }
    }

    // HEAD BooksPresenter.java:656-682: from a site-wide ranking, its own item reopens it (url equals mUrl)
    @Test
    public void todayListing() {
        assertKnigobludMenu(TODAY);
        assertOpens(TODAY, Sort.NEW, INDEX);
        // changed on purpose after the live check (2026-10-30): the site has no all-time/day popularity; those items are gone from the submenu and open nothing (they used to reopen the listing)
        assertOpensNothing(TODAY, Sort.POPULAR_ALL_TIME);
        assertOpens(TODAY, Sort.POPULAR_MONTH, POPULAR_MONTH);
        assertOpens(TODAY, Sort.POPULAR_WEEK, POPULAR_WEEK);
        assertOpensNothing(TODAY, Sort.POPULAR_DAY);
        assertOpens(TODAY, Sort.TODAY, TODAY);
        assertOpens(TODAY, Sort.TRENDING, TRENDING);
        assertOpens(TODAY, Sort.FINISHED, FINISHED);
    }

    // HEAD BooksPresenter.java:656-682: a popular period ranking switches to the other period
    @Test
    public void popularWeekListing() {
        assertKnigobludMenu(POPULAR_WEEK);
        assertOpens(POPULAR_WEEK, Sort.NEW, INDEX);
        // changed on purpose after the live check (2026-10-30): the site has no all-time/day popularity; those items are gone from the submenu and open nothing (they used to reopen the listing)
        assertOpensNothing(POPULAR_WEEK, Sort.POPULAR_ALL_TIME);
        assertOpens(POPULAR_WEEK, Sort.POPULAR_MONTH, POPULAR_MONTH);
        assertOpens(POPULAR_WEEK, Sort.POPULAR_WEEK, POPULAR_WEEK);
        assertOpensNothing(POPULAR_WEEK, Sort.POPULAR_DAY);
        assertOpens(POPULAR_WEEK, Sort.TODAY, TODAY);
        assertOpens(POPULAR_WEEK, Sort.TRENDING, TRENDING);
        assertOpens(POPULAR_WEEK, Sort.FINISHED, FINISHED);
    }

    // HEAD BooksPresenter.java:656-682 + ListingFilters.java:576-579 (cardGenre): a genre ranking stays in the genre
    @Test
    public void genreRankingListing() {
        assertKnigobludMenu(GENRE_WEEK);
        assertOpens(GENRE_WEEK, Sort.NEW, GENRE);
        // changed on purpose after the live check (2026-10-30): the site has no all-time/day popularity; those items are gone from the submenu and open nothing (they used to reopen the listing)
        assertOpensNothing(GENRE_WEEK, Sort.POPULAR_ALL_TIME);
        assertOpens(GENRE_WEEK, Sort.POPULAR_MONTH,
                SERVER + "/popular/?genre=" + GENRE_ID + "&period=month&page=");
        assertOpens(GENRE_WEEK, Sort.POPULAR_WEEK, GENRE_WEEK);
        assertOpensNothing(GENRE_WEEK, Sort.POPULAR_DAY);
        assertOpens(GENRE_WEEK, Sort.TODAY, SERVER + "/today/?genre=" + GENRE_ID + "&page=");
        assertOpens(GENRE_WEEK, Sort.TRENDING, SERVER + "/trending/?genre=" + GENRE_ID + "&page=");
        assertOpens(GENRE_WEEK, Sort.FINISHED, SERVER + "/finished/?genre=" + GENRE_ID + "&page=");
    }

    // HEAD BooksPresenter.java:656-682 (Url.java:80-84): the other site-wide rankings as the starting listing; the
    // item of the listing itself reopens it, the rest open site-wide
    @Test
    public void popularMonthListing() {
        assertSiteWideRanking(POPULAR_MONTH);
    }

    @Test
    public void trendingListing() {
        assertSiteWideRanking(TRENDING);
    }

    @Test
    public void finishedListing() {
        assertSiteWideRanking(FINISHED);
    }

    private static void assertSiteWideRanking(String url) {
        assertKnigobludMenu(url);
        assertOpens(url, Sort.NEW, INDEX);
        // changed on purpose after the live check (2026-10-30): the site has no all-time/day popularity; those items are gone from the submenu and open nothing (they used to reopen the listing)
        assertOpensNothing(url, Sort.POPULAR_ALL_TIME);
        assertOpens(url, Sort.POPULAR_MONTH, POPULAR_MONTH);
        assertOpens(url, Sort.POPULAR_WEEK, POPULAR_WEEK);
        assertOpensNothing(url, Sort.POPULAR_DAY);
        assertOpens(url, Sort.TODAY, TODAY);
        assertOpens(url, Sort.TRENDING, TRENDING);
        assertOpens(url, Sort.FINISHED, FINISHED);
    }

    // HEAD BooksPresenter.java:656-682 + ListingFilters.java:576-579,616-618 (cardGenre): the genre-narrowed
    // today/trending/finished listings stay in the genre; "new" is the genre listing
    @Test
    public void genreTodayListing() {
        assertGenreRanking(GENRE_TODAY);
    }

    @Test
    public void genreTrendingListing() {
        assertGenreRanking(GENRE_TRENDING);
    }

    @Test
    public void genreFinishedListing() {
        assertGenreRanking(GENRE_FINISHED);
    }

    @Test
    public void genreMonthRankingListing() {
        assertGenreRanking(GENRE_MONTH);
    }

    private static void assertGenreRanking(String url) {
        assertKnigobludMenu(url);
        assertOpens(url, Sort.NEW, GENRE);
        // changed on purpose after the live check (2026-10-30): the site has no all-time/day popularity; those items are gone from the submenu and open nothing (they used to reopen the listing)
        assertOpensNothing(url, Sort.POPULAR_ALL_TIME);
        assertOpens(url, Sort.POPULAR_MONTH, GENRE_MONTH);
        assertOpens(url, Sort.POPULAR_WEEK, GENRE_WEEK);
        assertOpensNothing(url, Sort.POPULAR_DAY);
        assertOpens(url, Sort.TODAY, GENRE_TODAY);
        assertOpens(url, Sort.TRENDING, GENRE_TRENDING);
        assertOpens(url, Sort.FINISHED, GENRE_FINISHED);
    }

    // changed on purpose after the live check (2026-10-30): the site has no all-time/day popularity, so the Popular submenu lists month and week only
    @Test
    public void thePopularSubmenuHasOnlyMonthAndWeek() {
        Sort.Entry popular = PARSER.sorts(INDEX).get(1);
        assertNull(popular.sort);
        assertEquals(R.string.popular, popular.title);
        assertEquals(Arrays.asList(Sort.POPULAR_MONTH, Sort.POPULAR_WEEK), popular.items);
        assertEquals(POPULAR_MONTH, PARSER.sortUrl(Sort.POPULAR_MONTH, INDEX));
        assertEquals(POPULAR_WEEK, PARSER.sortUrl(Sort.POPULAR_WEEK, INDEX));
        assertNull(PARSER.sortUrl(Sort.POPULAR_ALL_TIME, INDEX));
        assertNull(PARSER.sortUrl(Sort.POPULAR_DAY, INDEX));
    }

    // HEAD BooksFragment.java:253-268: /<uuid> listings that are not genres (author, reader, series) hide Order
    @Test
    public void peopleListingsHaveNoOrderMenu() {
        assertTrue(PARSER.sorts(AUTHOR).isEmpty());
        assertTrue(PARSER.sorts(READER).isEmpty());
        assertTrue(PARSER.sorts(SERIES).isEmpty());
    }

    // HEAD BooksPresenter.java:361-362: page 2 of the listings themselves
    @Test
    public void pagesOfListings() {
        assertEquals(SERVER + "/2", PARSER.pageUrl(INDEX, 2));
        assertEquals(GENRE + "/2", PARSER.pageUrl(GENRE, 2));
        assertEquals(AUTHOR + "/2", PARSER.pageUrl(AUTHOR, 2));
        assertEquals(READER + "/2", PARSER.pageUrl(READER, 2));
        assertEquals(SERIES + "/2", PARSER.pageUrl(SERIES, 2));
        assertEquals(SERVER + "/today/?page=2", PARSER.pageUrl(TODAY, 2));
        assertEquals(SERVER + "/popular/?genre=" + GENRE_ID + "&period=week&page=2", PARSER.pageUrl(GENRE_WEEK, 2));
    }
}
