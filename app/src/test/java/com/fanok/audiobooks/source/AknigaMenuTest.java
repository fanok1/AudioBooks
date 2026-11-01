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

import java.util.List;

/**
 * akniga's Order menu, pinned to what git HEAD did: books_akniga_options_menu.xml chosen in BooksFragment.onCreateMenu,
 * the collections hidden within a section (onPrepareMenu), the akniga branch of BooksPresenter.onOptionItemSelected
 * narrowed by ListingFilters.inGenre, and HEAD's BooksPresenter.pageUrl ({@code <page>} replaced on akniga).
 */
public class AknigaMenuTest {

    private static final Provider P = Provider.AKNIGA;
    private static final SourceParser PARSER = Provider.AKNIGA.parser;

    private static final String S = "https://akniga.org";
    private static final String SECTION = S + "/section/biography/";
    /** HEAD books_akniga_options_menu.xml:30-43 and 49-62: day, week, month, year, all time. */
    private static final int[] PERIOD_TITLES = {R.string.in_day, R.string.in_week, R.string.in_month,
            R.string.in_year, R.string.all_time};

    // ---------------------------------------------------------------- listings

    // HEAD BooksFragment.java:233-234 (akniga menu), BooksPresenter.java:588-619 (site-wide URLs), 349-370 (pageUrl).
    @Test
    public void index() {
        siteWide(S + "/index/page<page>/");
    }

    // HEAD BooksFragment.java:253-256 (no sort rule misses /top/, /discussed/), BooksPresenter.java:588-619.
    @Test
    public void periodRankings() {
        siteWide(S + "/index/top/page<page>/?period=7");
        siteWide(S + "/index/discussed/page<page>/?period=all");
    }

    // HEAD BooksFragment.java:306-308 (collections shown outside a section), BooksPresenter.java:588-619.
    @Test
    public void collections() {
        siteWide(S + "/collections/page<page>/");
        siteWide(S + "/collections/top/page<page>/");
        siteWide(S + "/collections/discussed/page<page>/");
    }

    // HEAD BooksFragment.java:253-256: "performer" is not in the no-sort rule, so a reader page kept the full menu
    // (collections too: HEAD BooksFragment.java:306-308, no section) and HEAD BooksPresenter.java:588-619 opened the
    // site-wide listings (ListingFilters.genreOf is null there).
    @Test
    public void performerPageKeepsTheSiteWideMenu() {
        siteWide(S + "/performer/Булдаков Олег/page<page>/");
        siteWide(S + "/performer/%D0%9F%D0%B0%D0%BD%D0%BA%D0%BE%D0%B2%20%D0%A0%D0%BE%D0%BC%D0%B0%D0%BD/page<page>/");
    }

    // HEAD BooksFragment.java:306-308 (no collections), BooksPresenter.java:678-682 + ListingFilters.java:606-607.
    @Test
    public void section() {
        inSection(SECTION + "page<page>/");
    }

    // HEAD ListingFilters.java:542-543 (a ranking keeps /section/<g>/), BooksPresenter.java:678-682.
    @Test
    public void sectionRankings() {
        inSection(SECTION + "top/page<page>/?period=30");
        inSection(SECTION + "discussed/page<page>/?period=365");
    }

    // HEAD BooksFragment.java:253-256 and 266-268: "author", "serie" listings hide the Order button.
    @Test
    public void authorAndSeriesHaveNoSort() {
        assertTrue(PARSER.sorts(S + "/author/Голотвина Ольга/page<page>/").isEmpty());
        assertTrue(PARSER.sorts(S + "/author/%D0%93%D1%80%D1%8D%D1%85%D0%B5%D0%BC%20%D0%99%D0%B0%D0%BD/page<page>/")
                .isEmpty());
        assertTrue(PARSER.sorts(S + "/series/%D0%94%D0%BE%D0%B7%D0%BE%D1%80%D1%8B/").isEmpty());
    }

    // ---------------------------------------------------------------- expectations

    // HEAD books_akniga_options_menu.xml (new, popular x5, discussed x5, collections x3) with all items visible
    // (BooksFragment.java:306-308 shows collections outside a section); URLs from BooksPresenter.java:588-619 with the
    // Url.*_AKNIGA constants, unchanged since inGenre is a no-op without a section (BooksPresenter.java:678-682).
    private static void siteWide(String listing) {
        List<Sort.Entry> menu = PARSER.sorts(listing);
        assertEquals(listing, 4, menu.size());
        assertItem(menu.get(0), Sort.NEW, R.id.new_data, R.string.new_data);
        assertSubmenu(menu.get(1), R.string.popular, Sort.POPULAR_DAY, Sort.POPULAR_WEEK, Sort.POPULAR_MONTH,
                Sort.POPULAR_YEAR, Sort.POPULAR_ALL_TIME);
        assertSubmenu(menu.get(2), R.string.order_discussed, Sort.RATING_DAY, Sort.RATING_WEEK, Sort.RATING_MONTH,
                Sort.RATING_YEAR, Sort.RATING_ALL_TIME);
        assertSubmenu(menu.get(3), R.string.order_collections, Sort.COLLECTIONS_NEW, Sort.COLLECTIONS_TOP,
                Sort.COLLECTIONS_DISCUSSED);
        assertMenuIds(menu.get(1), R.id.popular_day, R.id.popular_week, R.id.popular_month, R.id.popular_year,
                R.id.popular_all_time);
        assertMenuIds(menu.get(2), R.id.reting_day, R.id.reting_week, R.id.reting_month, R.id.reting_year,
                R.id.reting_all_time);
        assertMenuIds(menu.get(3), R.id.collections_new, R.id.collections_top, R.id.collections_discussed);
        // HEAD books_akniga_options_menu.xml:25-26, 30-43, 49-62, 70-77: the item titles.
        assertTitles(menu.get(1), PERIOD_TITLES);
        assertTitles(menu.get(2), PERIOD_TITLES);
        assertTitles(menu.get(3), R.string.new_data, R.string.popular, R.string.order_discussed);

        String root = S + "/index/";
        rankings(listing, root);
        opens(listing, Sort.COLLECTIONS_NEW, S + "/collections/page<page>/", S + "/collections/page2/");
        opens(listing, Sort.COLLECTIONS_TOP, S + "/collections/top/page<page>/", S + "/collections/top/page2/");
        opens(listing, Sort.COLLECTIONS_DISCUSSED, S + "/collections/discussed/page<page>/",
                S + "/collections/discussed/page2/");
    }

    // HEAD BooksFragment.java:306-308: the collections submenu is hidden when ListingFilters.genreOf(AKNIGA) finds a
    // /section/<g>/; HEAD BooksPresenter.java:678-682 + ListingFilters.inGenre (HEAD ListingFilters.java:606-607)
    // replace "https://akniga.org/index/" by the section root.
    private static void inSection(String listing) {
        List<Sort.Entry> menu = PARSER.sorts(listing);
        assertEquals(listing, 3, menu.size());
        assertItem(menu.get(0), Sort.NEW, R.id.new_data, R.string.new_data);
        assertSubmenu(menu.get(1), R.string.popular, Sort.POPULAR_DAY, Sort.POPULAR_WEEK, Sort.POPULAR_MONTH,
                Sort.POPULAR_YEAR, Sort.POPULAR_ALL_TIME);
        assertSubmenu(menu.get(2), R.string.order_discussed, Sort.RATING_DAY, Sort.RATING_WEEK, Sort.RATING_MONTH,
                Sort.RATING_YEAR, Sort.RATING_ALL_TIME);
        // HEAD books_akniga_options_menu.xml:30-43, 49-62: the period titles.
        assertTitles(menu.get(1), PERIOD_TITLES);
        assertTitles(menu.get(2), PERIOD_TITLES);
        rankings(listing, SECTION);
    }

    /** new, top and discussed under {@code root} ("…/index/" or a section root). */
    private static void rankings(String listing, String root) {
        opens(listing, Sort.NEW, root + "page<page>/", root + "page2/");
        String[] periods = {"1", "7", "30", "365", "all"};
        Sort[] popular = {Sort.POPULAR_DAY, Sort.POPULAR_WEEK, Sort.POPULAR_MONTH, Sort.POPULAR_YEAR,
                Sort.POPULAR_ALL_TIME};
        Sort[] discussed = {Sort.RATING_DAY, Sort.RATING_WEEK, Sort.RATING_MONTH, Sort.RATING_YEAR,
                Sort.RATING_ALL_TIME};
        for (int i = 0; i < periods.length; i++) {
            opens(listing, popular[i], root + "top/page<page>/?period=" + periods[i],
                    root + "top/page2/?period=" + periods[i]);
            opens(listing, discussed[i], root + "discussed/page<page>/?period=" + periods[i],
                    root + "discussed/page2/?period=" + periods[i]);
        }
    }

    // HEAD BooksPresenter.java:696-851 (label, tag per item id) and HEAD BooksPresenter.java:349-370 (pageUrl: an
    // akniga URL takes the last branch, "<page>" replaced).
    private static void opens(String listing, Sort sort, String headUrl, String headPage2) {
        String sorted = ListingFilters.sorted(P, sort, listing);
        assertEquals(listing + " " + sort, headUrl, sorted);
        assertEquals(sort.toString(), headLabel(sort.menuId), sort.label);
        assertEquals(sort.toString(), headTag(sort.menuId), sort.tag);
        assertEquals(sorted, headPage2, PARSER.pageUrl(sorted, 2));
    }

    // HEAD BooksPresenter.java:696-851: the subtitle label each akniga item id appended.
    private static int headLabel(int id) {
        if (id == R.id.new_data || id == R.id.collections_new) return R.string.order_new;
        if (id == R.id.collections_discussed) return R.string.order_discussed;
        if (id == R.id.reting_day || id == R.id.reting_week || id == R.id.reting_month || id == R.id.reting_year
                || id == R.id.reting_all_time) return R.string.order_reting;
        return R.string.order_popular; // popular_* and collections_top
    }

    // HEAD BooksPresenter.java:696-851: the back-stack tag of each akniga item id.
    private static String headTag(int id) {
        if (id == R.id.new_data || id == R.id.collections_new) return "audioBooksOrederNew";
        if (id == R.id.popular_day) return "audioBooksOrederDiscussedDay";
        if (id == R.id.popular_week) return "audioBooksOrederDiscussedWeek";
        if (id == R.id.popular_month || id == R.id.popular_year) return "audioBooksOrederDiscussedMonth";
        if (id == R.id.popular_all_time || id == R.id.collections_top) return "audioBooksOrederDiscussedAllTime";
        if (id == R.id.reting_day) return "audioBooksOrederBestDay";
        if (id == R.id.reting_week) return "audioBooksOrederBestWeek";
        if (id == R.id.reting_month || id == R.id.reting_year) return "audioBooksOrederBestMonth";
        if (id == R.id.reting_all_time || id == R.id.collections_discussed) return "audioBooksOrederBestAllTime";
        throw new AssertionError("no akniga item " + id);
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
        assertEquals(java.util.Arrays.asList(items), entry.items);
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
