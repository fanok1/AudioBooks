package com.fanok.audiobooks.source;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.fanok.audiobooks.R;
import com.fanok.audiobooks.search.ListingFilters;
import com.fanok.audiobooks.search.Provider;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * poleknig's Order menu pinned to git HEAD: books_poleknig_options_menu.xml chosen in BooksFragment.onCreateMenu, the
 * generic no-sort rule, the poleknig branch of BooksPresenter.onOptionItemSelected, HEAD ListingFilters.genreOf/inGenre
 * (/genres/<id>/new and /best) and HEAD BooksPresenter.pageUrl. All expected values are HEAD literals.
 */
public class PoleknigMenuTest {

    private static final Provider P = Provider.POLEKNIG;
    private static final SourceParser PARSER = Provider.POLEKNIG.parser;

    // HEAD Url.java:98-102
    private static final String SERVER = "https://poleknig.com";
    private static final String INDEX = SERVER + "/?p=";
    private static final String NEW_BOOK = SERVER + "/books/novelties?p=";
    private static final String BEST = SERVER + "/books/popular?p=";

    // HEAD GenreModel.java:266-275: a genre of the genre list is its link + "?p="
    private static final String GENRE = SERVER + "/genres/1?p=";
    // HEAD BooksModel.java (poleknig cards): a card's genre link as is (fixtures/poleknig/listing-novelties-p1.html)
    private static final String CARD_GENRE = SERVER + "/genres/16";
    // HEAD ListingFilters.java:610-612: the genre's own rankings
    private static final String GENRE_NEW = SERVER + "/genres/1/new?p=";
    private static final String GENRE_BEST = SERVER + "/genres/1/best?p=";
    // HEAD BooksModel.java:924-934,1063-1097 link format; author, reader, series links of fixtures/poleknig/series.html,
    // series-book.html and other-source-search.html
    private static final String AUTHOR = SERVER + "/authors/123532";
    private static final String READER = SERVER + "/readers/131771";
    private static final String SERIES = SERVER + "/series/705";

    // ---------------------------------------------------------------- helpers

    private static void assertItem(Sort.Entry entry, Sort sort) {
        assertEquals(sort, entry.sort);
        assertEquals(sort.title, entry.title);
        assertTrue(entry.items.isEmpty());
    }

    /** HEAD books_poleknig_options_menu.xml: new_data, popular (plain items). */
    private static void assertPoleknigMenu(String url) {
        List<Sort.Entry> menu = PARSER.sorts(url);
        assertEquals(url, 2, menu.size());
        assertItem(menu.get(0), Sort.NEW);
        assertItem(menu.get(1), Sort.POPULAR);
        assertEquals(R.id.popular, menu.get(1).sort.menuId);
        assertEquals(R.string.popular, menu.get(1).title);
    }

    // changed on purpose after the live check (2026-10-30): the site-wide menu is new, popular and TRENDING (/books/novelties, the popular new books); a genre listing's stays new, popular
    private static void assertSiteWideMenu(String url) {
        List<Sort.Entry> menu = PARSER.sorts(url);
        assertEquals(url, 3, menu.size());
        assertItem(menu.get(0), Sort.NEW);
        assertItem(menu.get(1), Sort.POPULAR);
        assertItem(menu.get(2), Sort.TRENDING);
        assertEquals(R.id.popular, menu.get(1).sort.menuId);
        assertEquals(R.string.popular, menu.get(1).title);
        assertEquals(R.id.knigoblud_trending, menu.get(2).sort.menuId);
        assertEquals(R.string.knigoblud_trending, menu.get(2).title);
    }

    /** HEAD BooksPresenter.java:363-364: page N of a poleknig listing. */
    private static String headPage2(String url) {
        return url.contains("?") ? url + 2 : url + "?p=" + 2;
    }

    /** HEAD BooksPresenter.java:696-738: the subtitle label and back-stack tag of each item id. */
    private static void assertLabelAndTag(Sort sort) {
        int label;
        String tag;
        if (sort.menuId == R.id.new_data) {
            label = R.string.order_new;
            tag = "audioBooksOrederNew";
        } else if (sort.menuId == R.id.popular) {
            label = R.string.order_popular;
            tag = "audioBooksOrederDiscussedAllTime";
        } else if (sort.menuId == R.id.knigoblud_trending) {
            // changed on purpose after the live check (2026-10-30): TRENDING is a site-wide poleknig item now
            label = R.string.knigoblud_trending;
            tag = "knigobludTrending";
        } else {
            throw new AssertionError("HEAD's poleknig menu has no item " + sort);
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

    // HEAD BooksFragment.java:238,253-268 (menu); BooksPresenter.java:644-655,678-682 (url)
    @Test
    public void index() {
        assertSiteWideMenu(INDEX);
        // changed on purpose after the live check (2026-10-30): "new" opens the index (the list by date); /books/novelties became one page of popular new books, now the TRENDING item
        assertOpens(INDEX, Sort.NEW, INDEX);
        assertOpens(INDEX, Sort.POPULAR, BEST);
        assertOpens(INDEX, Sort.TRENDING, NEW_BOOK);
    }

    // HEAD BooksPresenter.java:644-655,678-682: from a site-wide ranking its own item reopens it (url equals mUrl)
    @Test
    public void siteWideRankings() {
        assertSiteWideMenu(NEW_BOOK);
        // changed on purpose after the live check (2026-10-30): "new" opens the index, /books/novelties is the TRENDING item
        assertOpens(NEW_BOOK, Sort.NEW, INDEX);
        assertOpens(NEW_BOOK, Sort.POPULAR, BEST);
        assertOpens(NEW_BOOK, Sort.TRENDING, NEW_BOOK);
        assertSiteWideMenu(BEST);
        assertOpens(BEST, Sort.NEW, INDEX);
        assertOpens(BEST, Sort.POPULAR, BEST);
        assertOpens(BEST, Sort.TRENDING, NEW_BOOK);
    }

    // HEAD BooksPresenter.java:644-655,678-682 + ListingFilters.java:571-573,610-612: the genre ranks on the server
    @Test
    public void genreListing() {
        assertPoleknigMenu(GENRE);
        assertOpens(GENRE, Sort.NEW, GENRE_NEW);
        assertOpens(GENRE, Sort.POPULAR, GENRE_BEST);
    }

    // HEAD BooksPresenter.java:644-655,678-682 + ListingFilters.java:571-573,610-612: a card's genre link (no ?p=)
    @Test
    public void cardGenreListing() {
        assertPoleknigMenu(CARD_GENRE);
        assertOpens(CARD_GENRE, Sort.NEW, SERVER + "/genres/16/new?p=");
        assertOpens(CARD_GENRE, Sort.POPULAR, SERVER + "/genres/16/best?p=");
    }

    // HEAD BooksPresenter.java:644-655,678-682 + ListingFilters.java:571-573: a genre ranking stays in the genre
    @Test
    public void genreRankings() {
        assertPoleknigMenu(GENRE_NEW);
        assertOpens(GENRE_NEW, Sort.NEW, GENRE_NEW);
        assertOpens(GENRE_NEW, Sort.POPULAR, GENRE_BEST);
        assertPoleknigMenu(GENRE_BEST);
        assertOpens(GENRE_BEST, Sort.NEW, GENRE_NEW);
        assertOpens(GENRE_BEST, Sort.POPULAR, GENRE_BEST);
    }

    // HEAD BooksFragment.java:253-268: "author", "reader", "serie" in the URL hide the Order button
    @Test
    public void peopleListingsHaveNoOrderMenu() {
        assertTrue(PARSER.sorts(AUTHOR).isEmpty());
        assertTrue(PARSER.sorts(READER).isEmpty());
        assertTrue(PARSER.sorts(SERIES).isEmpty());
    }

    // changed on purpose after the live check (2026-10-30): year and tag listings keep no Order menu
    @Test
    public void yearAndTagListingsHaveNoOrderMenu() {
        assertTrue(PARSER.sorts(SERVER + "/years/2024?p=").isEmpty());
        assertTrue(PARSER.sorts(SERVER + "/tags/42?p=").isEmpty());
    }

    // changed on purpose after the live check (2026-10-30): only the site-wide menu has TRENDING (/books/novelties, the popular new books); a genre listing and a genre ranking keep new and popular
    @Test
    public void onlyTheSiteWideMenuHasTrending() {
        for (String url : new String[]{INDEX, NEW_BOOK, BEST}) {
            assertEquals(url, Arrays.asList(Sort.NEW, Sort.POPULAR, Sort.TRENDING), sortsOf(url));
        }
        for (String url : new String[]{GENRE, CARD_GENRE, GENRE_NEW, GENRE_BEST}) {
            assertEquals(url, Arrays.asList(Sort.NEW, Sort.POPULAR), sortsOf(url));
        }
        assertEquals(NEW_BOOK, ListingFilters.sorted(P, Sort.TRENDING, INDEX));
        assertEquals(SERVER + "/books/novelties?p=2", PARSER.pageUrl(NEW_BOOK, 2));
    }

    private static List<Sort> sortsOf(String url) {
        List<Sort> sorts = new ArrayList<>();
        for (Sort.Entry entry : PARSER.sorts(url)) {
            sorts.add(entry.sort);
        }
        return sorts;
    }

    // HEAD BooksPresenter.java:363-364: page 2 of the listings themselves
    @Test
    public void pagesOfListings() {
        assertEquals(SERVER + "/?p=2", PARSER.pageUrl(INDEX, 2));
        assertEquals(SERVER + "/genres/1?p=2", PARSER.pageUrl(GENRE, 2));
        assertEquals(SERVER + "/genres/16?p=2", PARSER.pageUrl(CARD_GENRE, 2));
        assertEquals(SERVER + "/authors/123532?p=2", PARSER.pageUrl(AUTHOR, 2));
        assertEquals(SERVER + "/genres/1/best?p=2", PARSER.pageUrl(GENRE_BEST, 2));
    }
}
