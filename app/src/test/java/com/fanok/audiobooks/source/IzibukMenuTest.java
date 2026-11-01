package com.fanok.audiobooks.source;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.fanok.audiobooks.R;
import com.fanok.audiobooks.search.ListingFilters;
import com.fanok.audiobooks.search.Provider;

import org.junit.Test;

import java.util.List;

/**
 * izibuk's Order menu, pinned to what git HEAD did: books_izibuk_options_menu.xml (new, popular) chosen in
 * BooksFragment.onCreateMenu, the izibuk branch of BooksPresenter.onOptionItemSelected (sort_pop=1 dropped for new,
 * added for popular, the genre kept) and HEAD's BooksPresenter.pageUrl (an izibuk URL appends "<n>/").
 */
public class IzibukMenuTest {

    private static final Provider P = Provider.IZIBUK;
    private static final SourceParser PARSER = Provider.IZIBUK.parser;

    private static final String S = "https://izib.uk";

    // ---------------------------------------------------------------- listings

    // HEAD BooksFragment.java:227-228 (izibuk menu), BooksPresenter.java:572-579, 349-370 (pageUrl).
    @Test
    public void index() {
        sorts(S + "/?p=", S + "/?p=", S + "/?sort_pop=1&p=");
    }

    // HEAD BooksPresenter.java:572-579: from the popular index, new drops sort_pop=1 and popular adds it once.
    @Test
    public void popularIndex() {
        sorts(S + "/?sort_pop=1&p=", S + "/?p=", S + "/?sort_pop=1&p=");
    }

    // HEAD BooksPresenter.java:572-579: a genre keeps its own path; genreOf is null for izibuk (HEAD
    // ListingFilters.java:556-590), so BooksPresenter.java:678-682 leaves it as is.
    @Test
    public void genre() {
        sorts(S + "/genre1?p=", S + "/genre1?p=", S + "/genre1?sort_pop=1&p=");
    }

    // HEAD BooksPresenter.java:572-579: a genre's popular listing re-sorts within the genre.
    @Test
    public void popularGenre() {
        sorts(S + "/genre1?sort_pop=1&p=", S + "/genre1?p=", S + "/genre1?sort_pop=1&p=");
    }

    // HEAD BooksFragment.java:253-256 and 266-268: "author", "reader", "serie" listings hide the Order button.
    @Test
    public void authorReaderAndSeriesHaveNoSort() {
        assertTrue(PARSER.sorts(S + "/author1108?p=").isEmpty());
        assertTrue(PARSER.sorts(S + "/reader4350?p=").isEmpty());
        assertTrue(PARSER.sorts(S + "/serie10166?p=").isEmpty());
    }

    // ---------------------------------------------------------------- expectations

    // HEAD books_izibuk_options_menu.xml: new_data, popular, both always visible; HEAD BooksPresenter.java:572-579
    // for the URLs, 696-700 / 731-737 for label and tag, 365-367 for page 2 (mUrl + n + "/").
    private static void sorts(String listing, String headNew, String headPopular) {
        List<Sort.Entry> menu = PARSER.sorts(listing);
        assertEquals(listing, 2, menu.size());
        // HEAD books_izibuk_options_menu.xml:25-29: ids and titles.
        assertItem(menu.get(0), Sort.NEW, R.id.new_data, R.string.new_data);
        assertItem(menu.get(1), Sort.POPULAR, R.id.popular, R.string.popular);

        opens(listing, Sort.NEW, headNew, R.string.order_new, "audioBooksOrederNew");
        opens(listing, Sort.POPULAR, headPopular, R.string.order_popular, "audioBooksOrederDiscussedAllTime");
    }

    private static void opens(String listing, Sort sort, String headUrl, int headLabel, String headTag) {
        String sorted = ListingFilters.sorted(P, sort, listing);
        assertEquals(listing + " " + sort, headUrl, sorted);
        assertEquals(sort.toString(), headLabel, sort.label);
        assertEquals(sort.toString(), headTag, sort.tag);
        assertEquals(sorted, headUrl + "2/", PARSER.pageUrl(sorted, 2));
    }

    // ---------------------------------------------------------------- helpers

    private static void assertItem(Sort.Entry entry, Sort sort, int headId, int headTitle) {
        assertSame(sort, entry.sort);
        assertEquals(headId, entry.sort.menuId);
        assertEquals(sort.toString(), headTitle, entry.sort.title);
        assertTrue(entry.items.isEmpty());
    }
}
