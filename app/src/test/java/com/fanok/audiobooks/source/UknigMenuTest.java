package com.fanok.audiobooks.source;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.fanok.audiobooks.R;
import com.fanok.audiobooks.search.ListingFilters;
import com.fanok.audiobooks.search.Provider;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

/**
 * uknig.com's Order menu pinned to git HEAD: books_uknig_options_menu.xml (via HEAD Uknig.optionsMenu), the
 * HEAD BooksFragment no-sort rule (HEAD Uknig.hasSort), HEAD Uknig.sortUrl(int, String) + the presenter's generic
 * parser branch and its inGenre narrowing, HEAD Uknig.pageUrl.
 */
public class UknigMenuTest {

    private static final SourceParser P = Provider.UKNIG.parser;
    private static final String S = "https://uknig.com";

    /** HEAD's visible Order items, submenu items flattened, in menu order. */
    private static final List<Sort> HEAD_ITEMS = Arrays.asList(Sort.NEW, Sort.POPULAR_DAY, Sort.POPULAR_WEEK,
            Sort.POPULAR_MONTH, Sort.POPULAR_ALL_TIME, Sort.RATING);

    // HEAD res/menu/books_uknig_options_menu.xml:19-48 (order submenu); HEAD BooksFragment.java:226 (parser menu),
    // :294-300 (reting stays visible off knigoblud/abmp3)
    private static void assertHeadMenu(String url) {
        List<Sort.Entry> menu = P.sorts(url);
        assertEquals(url, 3, menu.size());
        assertSame(url, Sort.NEW, menu.get(0).sort);
        assertEquals(R.string.new_data, menu.get(0).title);
        assertTrue(menu.get(0).items.isEmpty());
        // the "Популярное" submenu header has no id in HEAD (not a sort)
        assertNull(url, menu.get(1).sort);
        assertEquals(R.string.popular, menu.get(1).title);
        assertEquals(Arrays.asList(Sort.POPULAR_DAY, Sort.POPULAR_WEEK, Sort.POPULAR_MONTH, Sort.POPULAR_ALL_TIME),
                menu.get(1).items);
        assertSame(url, Sort.RATING, menu.get(2).sort);
        assertEquals(R.string.reting, menu.get(2).title);
        assertTrue(menu.get(2).items.isEmpty());
    }

    /** {@code expected}: per HEAD item, the URL HEAD opened (null: nothing) and HEAD's page 2 of it. */
    private static void assertOpens(String url, String... expected) {
        assertHeadMenu(url);
        assertEquals(HEAD_ITEMS.size() * 2, expected.length);
        for (int i = 0; i < HEAD_ITEMS.size(); i++) {
            Sort sort = HEAD_ITEMS.get(i);
            String sorted = ListingFilters.sorted(Provider.UKNIG, sort, url);
            assertEquals(sort + " from " + url, expected[2 * i], sorted);
            if (sorted != null) {
                assertEquals(sort + " page 2 from " + url, expected[2 * i + 1], P.pageUrl(sorted, 2));
            }
        }
    }

    private static void assertNoOrder(String url) {
        assertTrue(url, P.sorts(url).isEmpty());
    }

    private static String[] siteWide() {
        return new String[]{
                S + "/?p=", S + "/?p=2",
                S + "/popular-books/today", S + "/popular-books/today?p=2",
                S + "/popular-books", S + "/popular-books?p=2",
                S + "/popular-books/month", S + "/popular-books/month?p=2",
                S + "/popular-books/all", S + "/popular-books/all?p=2",
                S + "/best-books?p=", S + "/best-books?p=2"};
    }

    private static String[] inGenre(String genre) {
        return new String[]{
                genre + "?p=", genre + "?p=2",
                genre + "/popular/today?p=", genre + "/popular/today?p=2",
                genre + "/popular/week?p=", genre + "/popular/week?p=2",
                genre + "/popular/month?p=", genre + "/popular/month?p=2",
                genre + "/popular?p=", genre + "/popular?p=2",
                genre + "/best?p=", genre + "/best?p=2"};
    }

    // HEAD BooksPresenter.java:696-808 (label and tag per item id); books_uknig_options_menu.xml:25-46 (titles)
    @Test
    public void everyItemCarriesHeadsTitleLabelAndTag() {
        int[][] head = {
                {R.id.new_data, R.string.new_data, R.string.order_new},
                {R.id.popular_day, R.string.in_day, R.string.order_popular},
                {R.id.popular_week, R.string.in_week, R.string.order_popular},
                {R.id.popular_month, R.string.in_month, R.string.order_popular},
                {R.id.popular_all_time, R.string.all_time, R.string.order_popular},
                {R.id.reting, R.string.reting, R.string.order_reting}};
        String[] tags = {"audioBooksOrederNew", "audioBooksOrederDiscussedDay", "audioBooksOrederDiscussedWeek",
                "audioBooksOrederDiscussedMonth", "audioBooksOrederDiscussedAllTime", "audioBooksOrederBestAllTime"};
        for (int i = 0; i < HEAD_ITEMS.size(); i++) {
            Sort sort = HEAD_ITEMS.get(i);
            assertEquals(sort.name(), head[i][0], sort.menuId);
            assertEquals(sort.name(), head[i][1], sort.title);
            assertEquals(sort.name(), head[i][2], sort.label);
            assertEquals(sort.name(), tags[i], sort.tag);
        }
    }

    // HEAD Uknig.java:81-98 (pageUrl), :105-125 (sortUrl: the index's "new" is the index itself);
    // HEAD BooksPresenter.java:526-528, :674-682
    @Test
    public void theIndex() {
        assertOpens(Uknig.INDEX, siteWide());
    }

    // HEAD Uknig.java:105-125 (a site-wide ranking re-sorts site-wide); HEAD BooksPresenter.java:526-528, :674-682
    @Test
    public void aSiteWideRanking() {
        assertOpens(S + "/popular-books/today", siteWide());
        assertOpens(S + "/best-books?p=", siteWide());
        assertOpens(S + "/popular-books", siteWide());
        assertOpens(S + "/popular-books/month", siteWide());
        assertOpens(S + "/popular-books/all", siteWide());
    }

    // HEAD Uknig.java:133-152 (genreOf, inGenre), :105-125; HEAD BooksPresenter.java:678-682 (narrowed to the genre)
    @Test
    public void aGenreFromTheGenreList() {
        assertOpens(S + "/genres/fantastika?p=", inGenre(S + "/genres/fantastika"));
    }

    // HEAD Uknig.java:133-152, :105-125; HEAD BooksPresenter.java:678-682 (a card's genre link has no ?p=)
    @Test
    public void aGenreFromACardLink() {
        assertOpens(S + "/genres/rasskazy", inGenre(S + "/genres/rasskazy"));
    }

    // HEAD Uknig.java:105-125 (the same ranking is the listing itself), :140-152; HEAD BooksPresenter.java:678-682
    @Test
    public void aGenreRanking() {
        assertOpens(S + "/genres/fantastika/best?p=", inGenre(S + "/genres/fantastika"));
        assertOpens(S + "/genres/fantastika/popular/today?p=", inGenre(S + "/genres/fantastika"));
        assertOpens(S + "/genres/fantastika/popular/week?p=", inGenre(S + "/genres/fantastika"));
        assertOpens(S + "/genres/fantastika/popular/month?p=", inGenre(S + "/genres/fantastika"));
        assertOpens(S + "/genres/fantastika/popular?p=", inGenre(S + "/genres/fantastika"));
    }

    // HEAD Uknig.java:66, :127-130 (hasSort) + HEAD BooksFragment.java:253-268 (noSort hides the Order button)
    @Test
    public void authorReaderSeriesAndYearHaveNoOrder() {
        assertNoOrder(S + "/authors/pavel-chuk");
        assertNoOrder(S + "/readers/oleg-isaev");
        assertNoOrder(S + "/series/2452");
        assertNoOrder(S + "/years/2026");
    }

    // HEAD FavoritePresenter.java:237-239 (url + "?page="); HEAD Uknig.java:66, :127-130 + BooksFragment.java:253-268
    @Test
    public void aSeriesOpenedFromFavoritesHasNoOrder() {
        assertNoOrder(S + "/series/2452?page=");
    }
}
