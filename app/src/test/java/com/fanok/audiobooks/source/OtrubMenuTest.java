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
 * otrub's Order menu pinned to git HEAD: books_otrub_options_menu.xml chosen in BooksFragment.onCreateMenu, the
 * generic no-sort rule, the otrub branch of BooksPresenter.onOptionItemSelected, HEAD ListingFilters.genreOf/inGenre
 * and HEAD BooksPresenter.pageUrl. All expected values are HEAD literals.
 */
public class OtrubMenuTest {

    private static final Provider P = Provider.OTRUB;
    private static final SourceParser PARSER = Provider.OTRUB.parser;

    // HEAD Url.java:90-95
    private static final String SERVER = "https://otrub.in";
    private static final String INDEX = SERVER + "/?p=";
    private static final String NEW_BOOK = SERVER + "/?p=";
    private static final String BEST_WEEK = SERVER + "/popular/?period=week&p=";
    private static final String BEST_MONTH = SERVER + "/popular/?period=month&p=";

    // HEAD GenreModel.java:252 and BooksModel.java:897: a genre listing is the genre page + "?p="
    private static final String GENRE = SERVER + "/genres/fantastika.html?p=";
    // HEAD ListingFilters.java:613-615: rankings opened from that genre
    private static final String GENRE_WEEK = SERVER + "/popular/?genre=fantastika&period=week&p=";
    private static final String GENRE_MONTH = SERVER + "/popular/?genre=fantastika&period=month&p=";
    // HEAD DescriptionBookFragment.java:396-399 + BookDescriptionModel.java:589: a description's genre link, no "?p="
    // (fixtures/otrub/book-description.html)
    private static final String GENRE_PAGE = SERVER + "/genres/fantastika.html";
    // HEAD BooksModel.java:924-934 link format; author: fixtures/otrub/search-1984-p1.html, reader:
    // listing-index-p1.html, series: series.html
    private static final String AUTHOR = SERVER + "/authors/dzhordzh-oruehll.html";
    private static final String READER = SERVER + "/readers/aleksandr-stepnojj.html";
    private static final String SERIES = SERVER + "/series/dozor.html";

    // ---------------------------------------------------------------- helpers

    private static void assertItem(Sort.Entry entry, Sort sort) {
        assertEquals(sort, entry.sort);
        assertEquals(sort.title, entry.title);
        assertTrue(entry.items.isEmpty());
    }

    private static void assertSubmenu(Sort.Entry entry, int title, Sort... items) {
        // intentional: a submenu header is never a sort (brief 4a); HEAD's header had no id and opened nothing
        assertNull(entry.sort);
        assertEquals(title, entry.title);
        assertEquals(Arrays.asList(items), entry.items);
    }

    /** HEAD books_otrub_options_menu.xml: new_data, "popular" submenu (popular_week, popular_month). */
    private static void assertOtrubMenu(String url) {
        List<Sort.Entry> menu = PARSER.sorts(url);
        assertEquals(url, 2, menu.size());
        assertItem(menu.get(0), Sort.NEW);
        assertSubmenu(menu.get(1), R.string.popular, Sort.POPULAR_WEEK, Sort.POPULAR_MONTH);
    }

    /** HEAD BooksPresenter.java:363-364: page N of an otrub listing. */
    private static String headPage2(String url) {
        return url.contains("?") ? url + 2 : url + "?p=" + 2;
    }

    /** HEAD BooksPresenter.java:696-751: the subtitle label and back-stack tag of each item id. */
    private static void assertLabelAndTag(Sort sort) {
        int label;
        String tag;
        if (sort.menuId == R.id.new_data) {
            label = R.string.order_new;
            tag = "audioBooksOrederNew";
        } else if (sort.menuId == R.id.popular_month) {
            label = R.string.order_popular;
            tag = "audioBooksOrederDiscussedMonth";
        } else if (sort.menuId == R.id.popular_week) {
            label = R.string.order_popular;
            tag = "audioBooksOrederDiscussedWeek";
        } else {
            throw new AssertionError("HEAD's otrub menu has no item " + sort);
        }
        assertEquals(sort.toString(), label, sort.label);
        assertEquals(sort.toString(), tag, sort.tag);
    }

    /** What HEAD opened for {@code sort} from {@code url}, its label/tag, and page 2 of it. */
    private static void assertOpens(String url, Sort sort, String expected) {
        String sorted = ListingFilters.sorted(P, sort, url);
        assertEquals(sort + " from " + url, expected, sorted);
        assertLabelAndTag(sort);
        assertEquals(sort + " page 2 from " + url, headPage2(expected), PARSER.pageUrl(sorted, 2));
    }

    // ---------------------------------------------------------------- tests

    // HEAD BooksFragment.java:236,253-268 (menu); BooksPresenter.java:634-643,678-682 (new_data == mUrl stays)
    @Test
    public void index() {
        assertOtrubMenu(INDEX);
        assertOpens(INDEX, Sort.NEW, NEW_BOOK);
        assertOpens(INDEX, Sort.POPULAR_WEEK, BEST_WEEK);
        assertOpens(INDEX, Sort.POPULAR_MONTH, BEST_MONTH);
    }

    // HEAD BooksPresenter.java:634-643,678-682 + ListingFilters.java:574-583,613-615: "new" is the genre listing,
    // a ranking carries genre=
    @Test
    public void genreListing() {
        assertOtrubMenu(GENRE);
        assertOpens(GENRE, Sort.NEW, GENRE);
        assertOpens(GENRE, Sort.POPULAR_WEEK, GENRE_WEEK);
        assertOpens(GENRE, Sort.POPULAR_MONTH, GENRE_MONTH);
    }

    // HEAD BooksPresenter.java:634-643,678-682: a site-wide ranking switches period or goes back to new
    @Test
    public void popularWeekListing() {
        assertOtrubMenu(BEST_WEEK);
        assertOpens(BEST_WEEK, Sort.NEW, NEW_BOOK);
        assertOpens(BEST_WEEK, Sort.POPULAR_WEEK, BEST_WEEK);
        assertOpens(BEST_WEEK, Sort.POPULAR_MONTH, BEST_MONTH);
    }

    // HEAD BooksPresenter.java:634-643,678-682 + ListingFilters.java:576-579 (cardGenre): a genre ranking stays in it
    @Test
    public void genreRankingListing() {
        assertOtrubMenu(GENRE_WEEK);
        assertOpens(GENRE_WEEK, Sort.NEW, GENRE);
        assertOpens(GENRE_WEEK, Sort.POPULAR_WEEK, GENRE_WEEK);
        assertOpens(GENRE_WEEK, Sort.POPULAR_MONTH, GENRE_MONTH);
    }

    // HEAD BooksPresenter.java:634-643,678-682 + ListingFilters.java:584-585,613-614: the description's genre link is
    // a genre too; "new" differs from it (no "?p="), so it is narrowed to the genre listing
    @Test
    public void genrePageListing() {
        assertOtrubMenu(GENRE_PAGE);
        assertOpens(GENRE_PAGE, Sort.NEW, GENRE);
        assertOpens(GENRE_PAGE, Sort.POPULAR_WEEK, GENRE_WEEK);
        assertOpens(GENRE_PAGE, Sort.POPULAR_MONTH, GENRE_MONTH);
    }

    // HEAD BooksPresenter.java:634-643,678-682 (Url.java:93-95): the month ranking as the starting listing
    @Test
    public void popularMonthListing() {
        assertOtrubMenu(BEST_MONTH);
        assertOpens(BEST_MONTH, Sort.NEW, NEW_BOOK);
        assertOpens(BEST_MONTH, Sort.POPULAR_WEEK, BEST_WEEK);
        assertOpens(BEST_MONTH, Sort.POPULAR_MONTH, BEST_MONTH);
    }

    // HEAD BooksPresenter.java:634-643,678-682 + ListingFilters.java:576,613-615: a genre month ranking stays in it
    @Test
    public void genreMonthRankingListing() {
        assertOtrubMenu(GENRE_MONTH);
        assertOpens(GENRE_MONTH, Sort.NEW, GENRE);
        assertOpens(GENRE_MONTH, Sort.POPULAR_WEEK, GENRE_WEEK);
        assertOpens(GENRE_MONTH, Sort.POPULAR_MONTH, GENRE_MONTH);
    }

    // HEAD BooksFragment.java:253-268: "author", "reader", "serie" in the URL hide the Order button
    @Test
    public void peopleListingsHaveNoOrderMenu() {
        assertTrue(PARSER.sorts(AUTHOR).isEmpty());
        assertTrue(PARSER.sorts(READER).isEmpty());
        assertTrue(PARSER.sorts(SERIES).isEmpty());
    }

    // HEAD BooksPresenter.java:363-364: page 2 of the listings themselves
    @Test
    public void pagesOfListings() {
        assertEquals(SERVER + "/?p=2", PARSER.pageUrl(INDEX, 2));
        assertEquals(SERVER + "/genres/fantastika.html?p=2", PARSER.pageUrl(GENRE, 2));
        assertEquals(SERVER + "/genres/fantastika.html?p=2", PARSER.pageUrl(GENRE_PAGE, 2));
        assertEquals(SERVER + "/authors/dzhordzh-oruehll.html?p=2", PARSER.pageUrl(AUTHOR, 2));
        assertEquals(SERVER + "/readers/aleksandr-stepnojj.html?p=2", PARSER.pageUrl(READER, 2));
        assertEquals(SERVER + "/series/dozor.html?p=2", PARSER.pageUrl(SERIES, 2));
        assertEquals(SERVER + "/popular/?genre=fantastika&period=week&p=2", PARSER.pageUrl(GENRE_WEEK, 2));
    }
}
