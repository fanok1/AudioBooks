package com.fanok.audiobooks.source;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.fanok.audiobooks.R;
import com.fanok.audiobooks.search.ListingFilters;
import com.fanok.audiobooks.search.Provider;

import org.junit.Test;

import java.util.List;

/**
 * baza-knig's Order menu pinned to git HEAD: books_baza_knig_options_menu.xml (new, popular, rating, comments, years),
 * hidden on reader ("ispolnitel") and series ("cikl") listings, the presenter's baza-knig branch narrowed to the
 * category of a category listing by HEAD ListingFilters.genreOf/inGenre, and HEAD pageUrl's {@code url + page + "/"}.
 */
public class BazaKnigMenuTest {

    private static final SourceParser PARSER = Provider.BAZA_KNIG.parser;

    private static final String SERVER = "https://baza-knig.top";
    private static final String INDEX = SERVER + "/page/";
    private static final String GENRE = SERVER + "/fantastika-fentezii/page/";
    private static final String SORTED = SERVER + "/f/sort=rating/order=desc/page/";
    private static final String GENRE_SORTED = SERVER + "/fantastika-fentezii/f/sort=comm_num/order=desc/page/";
    private static final String FILTER = SERVER
            + "/f/f.prodolzhitelnost=01:00:00/t.prodolzhitelnost=01:59:59/sort=date/order=desc/page/";
    private static final String GENRE_FILTER = SERVER
            + "/detektivy-trillery/f/f.pdate=2026-10-23/sort=news_read/order=desc/page/";
    private static final String PERIOD = SERVER + "/f/f.pdate=2026-10-23/sort=rating/order=desc/page/";
    /** A category with an underscore in its slug, as the genre menu of fixtures/bazaknig/listing-index-p1.html links it. */
    private static final String GENRE_UNDERSCORE = SERVER + "/tehnotima_/page/";
    private static final String AUTHOR = SERVER
            + "/xfsearch/autor/%D0%B1%D1%80%D0%B0%D0%B9%D0%B0%D0%BD%20%D0%BA%D0%B8%D1%80%D0%B0/page/";
    private static final String READER = SERVER
            + "/xfsearch/ispolnitel/%D0%BB%D0%B5%D0%B2%D0%B0%D1%88%D0%B5%D0%B2%20%D0%B2%D0%BB%D0%B0%D0%B4%D0%B8%D0%BC%D0%B8%D1%80/page/";
    private static final String SERIES = SERVER + "/xfsearch/cikl/%D0%B4%D0%BE%D0%B7%D0%BE%D1%80/page/";

    // HEAD BooksFragment.java:222-241,253-262,264-269 + books_baza_knig_options_menu.xml; BooksPresenter.java:620-631,674-682,696-801; BooksPresenter.java:365-367
    @Test
    public void index() {
        assertSiteWide(INDEX);
    }

    // HEAD BooksFragment.java:222-241,253-262 + books_baza_knig_options_menu.xml; BooksPresenter.java:620-631,679-682 + ListingFilters.java:545-546,568-570,608-609; BooksPresenter.java:365-367
    @Test
    public void genreListing() {
        assertInGenre(GENRE, SERVER + "/fantastika-fentezii/");
    }

    // HEAD BooksFragment.java:222-241,253-262 + books_baza_knig_options_menu.xml; BooksPresenter.java:620-631,679-682 (/f/ is no category); BooksPresenter.java:365-367
    @Test
    public void sortedListing() {
        assertSiteWide(SORTED);
    }

    // HEAD BooksFragment.java:222-241,253-262 + books_baza_knig_options_menu.xml; BooksPresenter.java:620-631,679-682 + ListingFilters.java:545-546,568-570,608-609; BooksPresenter.java:365-367
    @Test
    public void sortedGenreListing() {
        assertInGenre(GENRE_SORTED, SERVER + "/fantastika-fentezii/");
    }

    // HEAD BooksFragment.java:222-241,253-262 + books_baza_knig_options_menu.xml; BooksPresenter.java:620-631,679-682 + ListingFilters.java:356-360 (duration /f/ page of the index); BooksPresenter.java:365-367
    @Test
    public void durationFilterPage() {
        assertSiteWide(FILTER);
    }

    // HEAD BooksFragment.java:222-241,253-262 + books_baza_knig_options_menu.xml; BooksPresenter.java:620-631,679-682 + ListingFilters.java:545-546,568-570,608-609; BooksPresenter.java:365-367
    @Test
    public void periodFilterPageOfAGenre() {
        assertInGenre(GENRE_FILTER, SERVER + "/detektivy-trillery/");
    }

    // HEAD BooksFragment.java:222-241,253-262 + books_baza_knig_options_menu.xml; BooksPresenter.java:620-631,679-682 (ListingFilters.java:453-454 period page: BAZA_KNIG_CATEGORY 545-546 sees no category in /f/f.pdate, so site-wide, period dropped); BooksPresenter.java:365-367
    @Test
    public void sitePeriodRanking() {
        assertSiteWide(PERIOD);
    }

    // HEAD GenreModel.java:169 (genre URL = href + "page/") + BooksFragment.java:222-241,253-262; BooksPresenter.java:620-631,679-682 + ListingFilters.java:545-546 ([\w-]+ takes '_'),608-609; BooksPresenter.java:365-367
    @Test
    public void genreListingWithUnderscore() {
        assertInGenre(GENRE_UNDERSCORE, SERVER + "/tehnotima_/");
    }

    // HEAD BooksPresenter.java:620-631 (ids outside baza-knig's menu leave url "") + 674-677 (nothing opens)
    @Test
    public void otherSortsOpenNothing() {
        assertNull(ListingFilters.sorted(Provider.BAZA_KNIG, Sort.TITLE, INDEX));
        assertNull(ListingFilters.sorted(Provider.BAZA_KNIG, Sort.RATING_ALL_TIME, INDEX));
        assertNull(ListingFilters.sorted(Provider.BAZA_KNIG, Sort.TOP_100, INDEX));
        // changed on purpose after the live check (2026-10-30): "по годам" (Sort.YEARS) is gone, the site answers it with a MySQL error
        assertNull(ListingFilters.sorted(Provider.BAZA_KNIG, Sort.YEARS, INDEX));
    }

    // changed on purpose after the live check (2026-10-30): an author listing (/xfsearch/autor/) keeps no Order menu, as reader and series listings (HEAD showed the site-wide one)
    @Test
    public void authorListingHasNoOrderMenu() {
        assertTrue(PARSER.sorts(AUTHOR).isEmpty());
    }

    // HEAD BooksFragment.java:253-262 (noSort: baza-knig "ispolnitel") + 264-269
    @Test
    public void readerListingHasNoOrderMenu() {
        assertTrue(PARSER.sorts(READER).isEmpty());
    }

    // HEAD BooksFragment.java:253-262 (noSort: "cikl") + 264-269
    @Test
    public void seriesListingHasNoOrderMenu() {
        assertTrue(PARSER.sorts(SERIES).isEmpty());
    }

    private static void assertSiteWide(String url) {
        assertMenu(url, SERVER + "/");
    }

    private static void assertInGenre(String url, String genreRoot) {
        assertMenu(url, genreRoot);
    }

    /** The four items in HEAD's order; each opens HEAD's site-wide listing with the server root replaced by {@code root}. */
    // changed on purpose after the live check (2026-10-30): no "по годам" item any more (the site answers it with a MySQL error); the rankings carry v.litres_buy=litres/ (no paid LitRes trial books)
    private static void assertMenu(String url, String root) {
        List<Sort.Entry> menu = PARSER.sorts(url);
        assertEquals(url, 4, menu.size());
        assertItem(menu.get(0), Sort.NEW, R.id.new_data, R.string.new_data);
        assertItem(menu.get(1), Sort.POPULAR, R.id.popular, R.string.popular);
        assertItem(menu.get(2), Sort.RATING, R.id.reting, R.string.reting);
        assertItem(menu.get(3), Sort.COMMENTS, R.id.coments, R.string.comets);

        assertSorted(Sort.NEW, url, root + "page/", R.string.order_new, "audioBooksOrederNew");
        assertSorted(Sort.POPULAR, url, root + "f/v.litres_buy=litres/sort=news_read/order=desc/page/",
                R.string.order_popular, "audioBooksOrederDiscussedAllTime");
        assertSorted(Sort.RATING, url, root + "f/v.litres_buy=litres/sort=rating/order=desc/page/",
                R.string.order_reting, "audioBooksOrederBestAllTime");
        assertSorted(Sort.COMMENTS, url, root + "f/v.litres_buy=litres/sort=comm_num/order=desc/page/",
                R.string.order_coments, "audioBooksOrederComents");
    }

    private static void assertItem(Sort.Entry entry, Sort sort, int menuId, int title) {
        assertEquals(sort, entry.sort);
        assertTrue(entry.items.isEmpty());
        assertEquals(menuId, entry.sort.menuId);
        assertEquals(title, entry.title);
    }

    /** HEAD BooksPresenter.pageUrl's generic branch (365-367): {@code url + page + "/"}. */
    private static void assertSorted(Sort sort, String url, String expected, int label, String tag) {
        String sorted = ListingFilters.sorted(Provider.BAZA_KNIG, sort, url);
        assertEquals(sort + " from " + url, expected, sorted);
        assertEquals(sort + " label", label, sort.label);
        assertEquals(sort + " tag", tag, sort.tag);
        assertEquals(sort + " page 2 from " + url, expected + "2/", PARSER.pageUrl(sorted, 2));
    }
}
