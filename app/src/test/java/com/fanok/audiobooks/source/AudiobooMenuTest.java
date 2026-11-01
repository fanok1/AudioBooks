package com.fanok.audiobooks.source;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.fanok.audiobooks.R;
import com.fanok.audiobooks.search.ListingFilters;
import com.fanok.audiobooks.search.Provider;

import org.junit.Test;

import java.util.List;

/**
 * audioboo's Order menu pinned to git HEAD: books_audioboo_options_menu.xml (new, title, rating, popular, comments,
 * duration), hidden on series ("cikl") listings. HEAD re-opened the same URL and POSTed the sort chosen by the
 * subtitle label (BooksModel.audiobooListing); intentional: the sort now travels in the URL as
 * {@link Audioboo#SORT_PARAM}, so {@code withoutSort} must give HEAD's URL and {@code sortOf} HEAD's form value.
 */
public class AudiobooMenuTest {

    private static final SourceParser PARSER = Provider.AUDIOBOO.parser;

    private static final String SERVER = "https://audioboo.org";
    private static final String INDEX = SERVER + "/page/";
    private static final String GENRE = SERVER + "/fantastika/";
    private static final String AUTHOR = SERVER
            + "/xfsearch/avtora/%D0%91%D0%B0%D1%88%D0%BA%D0%BE%D0%B2%20%D0%90%D0%BB%D0%B5%D0%BA%D1%81%D0%B0%D0%BD%D0%B4%D1%80/";
    private static final String READER = SERVER
            + "/tags/%D0%91%D1%83%D0%BA%D1%87%D0%B8%D0%BD%20%D0%98%D0%B2%D0%B0%D0%BD/";
    private static final String SERIES = SERVER + "/xfsearch/cikl/%D0%B4%D0%BE%D0%B7%D0%BE%D1%80/";

    // HEAD BooksFragment.java:222-241,253-262 + books_audioboo_options_menu.xml; BooksPresenter.java:632-633,679,696-815; BooksModel.java:156-176; BooksPresenter.java:356-358 + Url.java:220-228
    @Test
    public void index() {
        assertMenu(INDEX, INDEX, SERVER + "/page/2/");
    }

    // HEAD BooksFragment.java:222-241,253-262 + books_audioboo_options_menu.xml; BooksPresenter.java:632-633,679,696-815; BooksModel.java:156-176; Url.java:220-228
    @Test
    public void genreListing() {
        assertMenu(GENRE, GENRE, GENRE + "page/2/");
    }

    // HEAD: a sorted listing was the same URL under another subtitle; intentional (brief 4d): now the URL carries xs_value
    // HEAD BooksPresenter.java:632-633 (url = mUrl), BooksModel.java:156-176 (the new label alone picks the form value); Url.java:220-228
    @Test
    public void sortedListing() {
        assertMenu(INDEX + "?" + Audioboo.SORT_PARAM + "=rating", INDEX, SERVER + "/page/2/");
        assertMenu(GENRE + "?" + Audioboo.SORT_PARAM + "=time", GENRE, GENRE + "page/2/");
        assertMenu(AUTHOR + "?" + Audioboo.SORT_PARAM + "=views", AUTHOR, AUTHOR + "page/2/");
        assertMenu(READER + "?" + Audioboo.SORT_PARAM + "=comm", READER, READER + "page/2/");
    }

    // HEAD BooksFragment.java:253-262 ("xfsearch/avtora/" holds no "author": not noSort); BooksPresenter.java:632-633; Url.java:220-228
    @Test
    public void authorListing() {
        assertMenu(AUTHOR, AUTHOR, AUTHOR + "page/2/");
    }

    // HEAD BooksFragment.java:253-262 ("tags/" holds no "reader": not noSort); BooksPresenter.java:632-633; Url.java:220-228
    @Test
    public void readerListing() {
        assertMenu(READER, READER, READER + "page/2/");
    }

    // HEAD BooksFragment.java:253-262 (noSort: "cikl") + 264-269
    @Test
    public void seriesListingHasNoOrderMenu() {
        assertTrue(PARSER.sorts(SERIES).isEmpty());
    }

    /**
     * HEAD's six items in order; each opens {@code plain} (HEAD's url = mUrl) with HEAD's form value, and its page 2
     * is HEAD's {@code Url.audiobooListingPage(plain, 2)} with the same value.
     */
    private static void assertMenu(String url, String plain, String page2) {
        List<Sort.Entry> menu = PARSER.sorts(url);
        assertEquals(url, 6, menu.size());
        assertItem(menu.get(0), Sort.NEW, R.id.new_data, R.string.new_data);
        assertItem(menu.get(1), Sort.TITLE, R.id.sort_title, R.string.order_title);
        assertItem(menu.get(2), Sort.RATING, R.id.reting, R.string.reting);
        assertItem(menu.get(3), Sort.POPULAR, R.id.popular, R.string.popular);
        assertItem(menu.get(4), Sort.COMMENTS, R.id.coments, R.string.comets);
        assertItem(menu.get(5), Sort.DURATION, R.id.sort_duration, R.string.order_duration);

        assertSorted(Sort.NEW, url, plain, page2, "date", R.string.order_new, "audioBooksOrederNew");
        assertSorted(Sort.TITLE, url, plain, page2, "title", R.string.order_title, "audioBooksOrderTitle");
        assertSorted(Sort.RATING, url, plain, page2, "rating", R.string.order_reting, "audioBooksOrederBestAllTime");
        assertSorted(Sort.POPULAR, url, plain, page2, "views", R.string.order_popular,
                "audioBooksOrederDiscussedAllTime");
        assertSorted(Sort.COMMENTS, url, plain, page2, "comm", R.string.order_coments, "audioBooksOrederComents");
        assertSorted(Sort.DURATION, url, plain, page2, "time", R.string.order_duration, "audioBooksOrderDuration");
    }

    private static void assertItem(Sort.Entry entry, Sort sort, int menuId, int title) {
        assertEquals(sort, entry.sort);
        assertTrue(entry.items.isEmpty());
        assertEquals(menuId, entry.sort.menuId);
        assertEquals(title, entry.title);
    }

    // intentional (brief 4d): the URL HEAD requested is withoutSort(sorted), the form value HEAD sent is sortOf(sorted)
    private static void assertSorted(Sort sort, String url, String plain, String page2, String formValue, int label,
            String tag) {
        String sorted = ListingFilters.sorted(Provider.AUDIOBOO, sort, url);
        assertEquals(sort + " from " + url, plain, Audioboo.withoutSort(sorted));
        assertEquals(sort + " form value from " + url, formValue, Audioboo.sortOf(sorted));
        assertEquals(sort + " label", label, sort.label);
        assertEquals(sort + " tag", tag, sort.tag);
        String next = PARSER.pageUrl(sorted, 2);
        assertEquals(sort + " page 2 from " + url, page2, Audioboo.withoutSort(next));
        assertEquals(sort + " page 2 form value from " + url, formValue, Audioboo.sortOf(next));
    }
}
