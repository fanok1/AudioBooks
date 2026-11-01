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
 * audiobook-mp3's Order menu pinned to git HEAD: the shared books_options_menu.xml with its onPrepareMenu toggles
 * (popular and rating submenus and knigoblud items hidden, top-100 and random shown), hidden on genre listings, the
 * presenter's abmp3 branch and {@code Url.abmp3ListingPage} paging.
 */
public class Abmp3MenuTest {

    private static final SourceParser PARSER = Provider.ABMP3.parser;

    private static final String INDEX = "https://audiobook-mp3.com/?page=";
    private static final String TOP = "https://audiobook-mp3.com/top";
    private static final String RANDOM = "https://audiobook-mp3.com/random-book";
    private static final String GENRE = "https://audiobook-mp3.com/genre-1-fantastika?page=";
    private static final String AUTHOR = "https://audiobook-mp3.com/avtor-80-juval-nojj-kharari?page=";
    private static final String READER = "https://audiobook-mp3.com/ispolnitel-76-levashev-vladimir?page=";

    // HEAD BooksFragment.java:253-262 (noSort: abmp3 genre) + 264-269 (Order hidden when noSort)
    @Test
    public void genreListingHasNoOrderMenu() {
        assertTrue(PARSER.sorts(GENRE).isEmpty());
        assertTrue(PARSER.sorts("https://audiobook-mp3.com/genre-12-detektivy-trillery?page=").isEmpty());
    }

    // HEAD BooksFragment.java:222-241,253-262,286-304 + books_options_menu.xml; BooksPresenter.java:580-587,696-701,837-851; BooksPresenter.java:359-360 + Url.java:234-245
    @Test
    public void index() {
        assertMenu(INDEX);
    }

    // HEAD BooksFragment.java:222-241,253-262,286-304 + books_options_menu.xml; BooksPresenter.java:580-587,696-701,837-851; BooksPresenter.java:359-360 + Url.java:234-245
    @Test
    public void topHundredListing() {
        assertMenu(TOP);
    }

    // HEAD BooksFragment.java:222-241,253-262,286-304 + books_options_menu.xml; BooksPresenter.java:580-587,696-701,837-851; BooksPresenter.java:359-360 + Url.java:234-245
    @Test
    public void randomListing() {
        assertMenu(RANDOM);
    }

    // changed on purpose after the live check (2026-10-30): an author listing (/avtor-N-slug) shows no Order menu (HEAD showed the site-wide one)
    @Test
    public void authorListingHasNoOrderMenu() {
        assertTrue(PARSER.sorts(AUTHOR).isEmpty());
    }

    // changed on purpose after the live check (2026-10-30): a reader listing (/ispolnitel-N-slug) shows no Order menu (HEAD showed the site-wide one)
    @Test
    public void readerListingHasNoOrderMenu() {
        assertTrue(PARSER.sorts(READER).isEmpty());
    }

    // HEAD BooksFragment.java:253-262 (noSort: "author"/"reader"/"serie"/"cikl" anywhere in the URL)
    @Test
    public void listingsNamedLikePeopleHaveNoOrderMenu() {
        assertTrue(PARSER.sorts("https://audiobook-mp3.com/author-5-x?page=").isEmpty());
        assertTrue(PARSER.sorts("https://audiobook-mp3.com/reader-5-x?page=").isEmpty());
        assertTrue(PARSER.sorts("https://audiobook-mp3.com/serie-5-x?page=").isEmpty());
        assertTrue(PARSER.sorts("https://audiobook-mp3.com/cikl-5-x?page=").isEmpty());
    }

    // HEAD BooksPresenter.java:580-587 (other ids of books_options_menu leave url "" and nothing opens)
    @Test
    public void hiddenItemsOpenNothing() {
        assertNull(ListingFilters.sorted(Provider.ABMP3, Sort.POPULAR_WEEK, INDEX));
        assertNull(ListingFilters.sorted(Provider.ABMP3, Sort.RATING_ALL_TIME, INDEX));
        assertNull(ListingFilters.sorted(Provider.ABMP3, Sort.TODAY, INDEX));
    }

    /** The visible menu of every non-genre abmp3 listing: new, top 100, random; each opens the same site-wide list. */
    private static void assertMenu(String url) {
        List<Sort.Entry> menu = PARSER.sorts(url);
        assertEquals(url, 3, menu.size());
        assertItem(menu.get(0), Sort.NEW, R.id.new_data, R.string.new_data);
        assertItem(menu.get(1), Sort.TOP_100, R.id.abmp3_top100, R.string.abmp3_top100);
        assertItem(menu.get(2), Sort.RANDOM, R.id.abmp3_random, R.string.abmp3_random);

        assertSorted(Sort.NEW, url, "https://audiobook-mp3.com/?page=", "https://audiobook-mp3.com/?page=2",
                R.string.order_new, "audioBooksOrederNew");
        assertSorted(Sort.TOP_100, url, "https://audiobook-mp3.com/top", "https://audiobook-mp3.com/top?page=2",
                R.string.abmp3_top100, "abmp3Top100");
        assertSorted(Sort.RANDOM, url, "https://audiobook-mp3.com/random-book",
                "https://audiobook-mp3.com/random-book?page=2", R.string.abmp3_random, "abmp3Random");
    }

    private static void assertItem(Sort.Entry entry, Sort sort, int menuId, int title) {
        assertEquals(sort, entry.sort);
        assertTrue(entry.items.isEmpty());
        assertEquals(menuId, entry.sort.menuId);
        assertEquals(title, entry.title);
    }

    private static void assertSorted(Sort sort, String url, String expected, String page2, int label, String tag) {
        String sorted = ListingFilters.sorted(Provider.ABMP3, sort, url);
        assertEquals(sort + " from " + url, expected, sorted);
        assertEquals(sort + " label", label, sort.label);
        assertEquals(sort + " tag", tag, sort.tag);
        assertEquals(sort + " page 2 from " + url, page2, PARSER.pageUrl(sorted, 2));
    }
}
