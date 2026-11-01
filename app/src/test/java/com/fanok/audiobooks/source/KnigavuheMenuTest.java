package com.fanok.audiobooks.source;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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
 * knigavuhe's Order menu, pinned to what git HEAD did: the default books_options_menu.xml (BooksFragment.onCreateMenu
 * fallback) with onPrepareMenu hiding the knigoblud and abmp3 items, the {@code Url.SERVER} branch of
 * BooksPresenter.onOptionItemSelected (site-wide constants, or the genre root + rating|popular/<page>/?period=), and
 * HEAD's BooksPresenter.pageUrl.
 */
public class KnigavuheMenuTest {

    private static final Provider P = Provider.KNIGAVUHE;
    private static final SourceParser PARSER = Provider.KNIGAVUHE.parser;

    private static final String S = "https://knigavuhe.org";
    private static final String GENRE = S + "/genre/detektivy-trillery/";

    // ---------------------------------------------------------------- listings

    // HEAD BooksFragment.java:239-240 (default menu), BooksPresenter.java:529-550 (site-wide), 349-370 (pageUrl).
    @Test
    public void index() {
        siteWide(S + "/new/?page=");
    }

    // HEAD BooksFragment.java:253-256 (no sort rule misses /popular/, /rating/), BooksPresenter.java:529-550.
    @Test
    public void periodRankings() {
        siteWide(S + "/popular/?w=week&page=");
        siteWide(S + "/rating/?w=alltime&page=");
        siteWide(S + "/popular/?w=today&page=");
    }

    // HEAD BooksPresenter.java:551-572: the genre root (up to the 5th '/') + "<page>/" or rating|popular/<page>/?period=.
    @Test
    public void genre() {
        inGenre(GENRE + "<page>/");
    }

    // HEAD BooksPresenter.java:551-572: a genre ranking keeps the same genre root.
    @Test
    public void genreRankings() {
        inGenre(GENRE + "popular/<page>/?period=week");
        inGenre(GENRE + "rating/<page>/?period=alltime");
    }

    // HEAD BooksPresenter.java:551-570 + 701-706: the bare genre root (no <page>), the listing HEAD's "Рейтинг" header
    // pushed from a genre (book-page genre links carry "<page>/", see genre()), has the same root.
    @Test
    public void genreLinkOfABookPage() {
        inGenre(GENRE);
    }

    // HEAD BooksFragment.java:253-256 and 266-268: "author", "reader", "serie" listings hide the Order button.
    @Test
    public void authorReaderAndSeriesHaveNoSort() {
        assertTrue(PARSER.sorts(S + "/author/aleks-graf/").isEmpty());
        assertTrue(PARSER.sorts(S + "/reader/abdullaev-dzhakhangir/").isEmpty());
        assertTrue(PARSER.sorts(S + "/serie/dozory/?page=").isEmpty());
    }

    // ---------------------------------------------------------------- expectations

    // HEAD books_options_menu.xml + BooksFragment.java:287-304 (not knigoblud, not abmp3): new, the popular_parent
    // submenu (all time, month, week, day) and the reting submenu (all time, month, week, day).
    private static void assertMenu(String listing) {
        List<Sort.Entry> menu = PARSER.sorts(listing);
        assertEquals(listing, 3, menu.size());
        assertItem(menu.get(0), Sort.NEW, R.id.new_data, R.string.new_data);
        assertSubmenu(menu.get(1), R.string.popular, Sort.POPULAR_ALL_TIME, Sort.POPULAR_MONTH, Sort.POPULAR_WEEK,
                Sort.POPULAR_DAY);
        assertMenuIds(menu.get(1), R.id.popular_all_time, R.id.popular_month, R.id.popular_week, R.id.popular_day);
        // intentional: HEAD's "Рейтинг" header shared R.id.reting (and in a genre pushed the bare genre root); a
        // submenu header is now never a sort and only opens its periods
        assertSubmenu(menu.get(2), R.string.reting, Sort.RATING_ALL_TIME, Sort.RATING_MONTH, Sort.RATING_WEEK,
                Sort.RATING_DAY);
        assertMenuIds(menu.get(2), R.id.reting_all_time, R.id.reting_month, R.id.reting_week, R.id.reting_day);
        // HEAD books_options_menu.xml:25-26, 31-43, 50-61: the item titles.
        assertTitles(menu.get(1), R.string.all_time, R.string.in_month, R.string.in_week, R.string.in_day);
        assertTitles(menu.get(2), R.string.all_time, R.string.in_month, R.string.in_week, R.string.in_day);
    }

    // HEAD BooksPresenter.java:530-550 with HEAD Url.java:15-23; genreOf is null for knigavuhe (HEAD
    // ListingFilters.java:556-590), so BooksPresenter.java:678-682 keeps them; pageUrl appends "<n>/" (no "genre").
    private static void siteWide(String listing) {
        assertMenu(listing);
        opens(listing, Sort.NEW, S + "/new/?page=", S + "/new/?page=2/");
        String[] words = {"alltime", "month", "week", "today"};
        Sort[] popular = {Sort.POPULAR_ALL_TIME, Sort.POPULAR_MONTH, Sort.POPULAR_WEEK, Sort.POPULAR_DAY};
        Sort[] rating = {Sort.RATING_ALL_TIME, Sort.RATING_MONTH, Sort.RATING_WEEK, Sort.RATING_DAY};
        for (int i = 0; i < words.length; i++) {
            opens(listing, popular[i], S + "/popular/?w=" + words[i] + "&page=",
                    S + "/popular/?w=" + words[i] + "&page=2/");
            opens(listing, rating[i], S + "/rating/?w=" + words[i] + "&page=",
                    S + "/rating/?w=" + words[i] + "&page=2/");
        }
    }

    // HEAD BooksPresenter.java:551-572; pageUrl (BooksPresenter.java:365-369): a "genre" URL fills its <page>.
    private static void inGenre(String listing) {
        assertMenu(listing);
        opens(listing, Sort.NEW, GENRE + "<page>/", GENRE + "2/");
        String[] words = {"alltime", "month", "week", "today"};
        Sort[] popular = {Sort.POPULAR_ALL_TIME, Sort.POPULAR_MONTH, Sort.POPULAR_WEEK, Sort.POPULAR_DAY};
        Sort[] rating = {Sort.RATING_ALL_TIME, Sort.RATING_MONTH, Sort.RATING_WEEK, Sort.RATING_DAY};
        for (int i = 0; i < words.length; i++) {
            opens(listing, popular[i], GENRE + "popular/<page>/?period=" + words[i],
                    GENRE + "popular/2/?period=" + words[i]);
            opens(listing, rating[i], GENRE + "rating/<page>/?period=" + words[i],
                    GENRE + "rating/2/?period=" + words[i]);
        }
    }

    // HEAD BooksPresenter.java:696-760 (label, tag per item id) and HEAD BooksPresenter.java:349-370 (pageUrl).
    private static void opens(String listing, Sort sort, String headUrl, String headPage2) {
        String sorted = ListingFilters.sorted(P, sort, listing);
        assertEquals(listing + " " + sort, headUrl, sorted);
        assertEquals(sort.toString(), headLabel(sort.menuId), sort.label);
        assertEquals(sort.toString(), headTag(sort.menuId), sort.tag);
        assertEquals(sorted, headPage2, PARSER.pageUrl(sorted, 2));
    }

    // HEAD BooksPresenter.java:696-760: the subtitle label each knigavuhe item id appended.
    private static int headLabel(int id) {
        if (id == R.id.new_data) return R.string.order_new;
        if (id == R.id.reting_day || id == R.id.reting_week || id == R.id.reting_month
                || id == R.id.reting_all_time) return R.string.order_reting;
        if (id == R.id.popular_day || id == R.id.popular_week || id == R.id.popular_month
                || id == R.id.popular_all_time) return R.string.order_popular;
        throw new AssertionError("no knigavuhe item " + id);
    }

    // HEAD BooksPresenter.java:696-760: the back-stack tag of each knigavuhe item id.
    private static String headTag(int id) {
        if (id == R.id.new_data) return "audioBooksOrederNew";
        if (id == R.id.popular_day) return "audioBooksOrederDiscussedDay";
        if (id == R.id.popular_week) return "audioBooksOrederDiscussedWeek";
        if (id == R.id.popular_month) return "audioBooksOrederDiscussedMonth";
        if (id == R.id.popular_all_time) return "audioBooksOrederDiscussedAllTime";
        if (id == R.id.reting_day) return "audioBooksOrederBestDay";
        if (id == R.id.reting_week) return "audioBooksOrederBestWeek";
        if (id == R.id.reting_month) return "audioBooksOrederBestMonth";
        if (id == R.id.reting_all_time) return "audioBooksOrederBestAllTime";
        throw new AssertionError("no knigavuhe item " + id);
    }

    // ---------------------------------------------------------------- helpers

    private static void assertItem(Sort.Entry entry, Sort sort, int headId, int headTitle) {
        assertSame(sort, entry.sort);
        assertEquals(headId, entry.sort.menuId);
        assertEquals(sort.toString(), headTitle, entry.sort.title);
        assertTrue(entry.items.isEmpty());
    }

    private static void assertSubmenu(Sort.Entry entry, int title, Sort... items) {
        // intentional: a submenu header is never a sort (Sort.Entry submenus have sort == null)
        assertNull(entry.sort);
        assertEquals(title, entry.title);
        assertEquals(Arrays.asList(items), entry.items);
    }

    private static void assertMenuIds(Sort.Entry submenu, int... headIds) {
        assertEquals(headIds.length, submenu.items.size());
        for (int i = 0; i < headIds.length; i++) {
            assertEquals(submenu.items.get(i).toString(), headIds[i], submenu.items.get(i).menuId);
        }
    }

    private static void assertTitles(Sort.Entry submenu, int... headTitles) {
        assertEquals(headTitles.length, submenu.items.size());
        for (int i = 0; i < headTitles.length; i++) {
            assertEquals(submenu.items.get(i).toString(), headTitles[i], submenu.items.get(i).title);
        }
    }
}
