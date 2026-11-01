package com.fanok.audiobooks.source;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.fanok.audiobooks.R;
import com.fanok.audiobooks.search.ListingFilters;
import com.fanok.audiobooks.search.Provider;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

/**
 * audiokniga.life's Order menu pinned to git HEAD: books_dle_options_menu.xml (via HEAD AudioknigaLife.optionsMenu),
 * the HEAD BooksFragment no-sort rule (HEAD AudioknigaLife.hasSort), HEAD AudioknigaLife.sortUrl(int, String) + the
 * presenter's generic parser branch and its inGenre narrowing (the /f/cat=<id>/ filter), HEAD pageUrl
 * (Dle.pageUrl = Url.audiobooListingPage).
 */
public class AudioknigaLifeMenuTest {

    private static final SourceParser P = Provider.AUDIOKNIGA_LIFE.parser;
    private static final String S = "https://audiokniga.life";

    /** HEAD's visible Order items, in menu order. */
    private static final List<Sort> HEAD_ITEMS = Arrays.asList(Sort.NEW, Sort.POPULAR, Sort.RATING, Sort.COMMENTS,
            Sort.TITLE);
    /** The /f/ filter path of HEAD's AudioknigaLife.sortUrl per item ("new" is the index; within a genre: date). */
    private static final String[] FILTERS = {"sort=date/order=desc/", "sort=news_read/order=desc/",
            "sort=rating/order=desc/", "sort=comm_num/order=desc/", "sort=title/order=asc/"};

    // HEAD res/menu/books_dle_options_menu.xml:19-41 (order submenu); HEAD BooksFragment.java:226 (parser menu),
    // :294-300 (reting stays visible off knigoblud/abmp3)
    private static void assertHeadMenu(String url) {
        List<Sort.Entry> menu = P.sorts(url);
        int[] titles = {R.string.new_data, R.string.popular, R.string.reting, R.string.comets, R.string.order_title};
        assertEquals(url, HEAD_ITEMS.size(), menu.size());
        for (int i = 0; i < HEAD_ITEMS.size(); i++) {
            assertEquals(url, HEAD_ITEMS.get(i), menu.get(i).sort);
            assertEquals(url, titles[i], menu.get(i).title);
            assertTrue(url, menu.get(i).items.isEmpty());
        }
    }

    /** {@code expected}: per HEAD item, the URL HEAD opened (null: nothing) and HEAD's page 2 of it. */
    private static void assertOpens(String url, String... expected) {
        assertHeadMenu(url);
        assertEquals(HEAD_ITEMS.size() * 2, expected.length);
        for (int i = 0; i < HEAD_ITEMS.size(); i++) {
            Sort sort = HEAD_ITEMS.get(i);
            String sorted = ListingFilters.sorted(Provider.AUDIOKNIGA_LIFE, sort, url);
            assertEquals(sort + " from " + url, expected[2 * i], sorted);
            if (sorted != null) {
                assertEquals(sort + " page 2 from " + url, expected[2 * i + 1], P.pageUrl(sorted, 2));
            }
        }
    }

    private static void assertNoOrder(String url) {
        assertTrue(url, P.sorts(url).isEmpty());
    }

    /** Every item through the filter {@code filter} ({@code /f/} or {@code /f/cat=<id>/}). */
    private static String[] filtered(String filter) {
        String[] rows = new String[HEAD_ITEMS.size() * 2];
        for (int i = 0; i < HEAD_ITEMS.size(); i++) {
            rows[2 * i] = filter + FILTERS[i];
            rows[2 * i + 1] = filter + FILTERS[i] + "page/2/";
        }
        return rows;
    }

    /** Site-wide: "new" is the bare index. */
    private static String[] siteWide() {
        String[] rows = filtered(S + "/f/");
        rows[0] = S + "/";
        rows[1] = S + "/page/2/";
        return rows;
    }

    // HEAD BooksPresenter.java:696-808 (label and tag per item id); books_dle_options_menu.xml:25-39 (titles)
    @Test
    public void everyItemCarriesHeadsTitleLabelAndTag() {
        int[][] head = {
                {R.id.new_data, R.string.new_data, R.string.order_new},
                {R.id.popular, R.string.popular, R.string.order_popular},
                {R.id.reting, R.string.reting, R.string.order_reting},
                {R.id.coments, R.string.comets, R.string.order_coments},
                {R.id.sort_title, R.string.order_title, R.string.order_title}};
        String[] tags = {"audioBooksOrederNew", "audioBooksOrederDiscussedAllTime", "audioBooksOrederBestAllTime",
                "audioBooksOrederComents", "audioBooksOrderTitle"};
        for (int i = 0; i < HEAD_ITEMS.size(); i++) {
            Sort sort = HEAD_ITEMS.get(i);
            assertEquals(sort.name(), head[i][0], sort.menuId);
            assertEquals(sort.name(), head[i][1], sort.title);
            assertEquals(sort.name(), head[i][2], sort.label);
            assertEquals(sort.name(), tags[i], sort.tag);
        }
    }

    // HEAD AudioknigaLife.java:90-92 (pageUrl), :100-113 (sortUrl), :123-136 (the index is no genre);
    // HEAD BooksPresenter.java:526-528, :674-682
    @Test
    public void theIndex() {
        assertOpens(S + "/", siteWide());
    }

    // HEAD AudioknigaLife.java:79-80 (GENRE: /f/ is no genre), :100-113; HEAD BooksPresenter.java:674-682
    @Test
    public void aSiteWideRanking() {
        assertOpens(S + "/f/sort=rating/order=desc/", siteWide());
        assertOpens(S + "/f/sort=title/order=asc/", siteWide());
    }

    // HEAD AudioknigaLife.java:62-72 (fantastika = 9, ljubovnoe-fjentezi = 27, tru-krajm = 31), :123-145 (genreOf,
    // inGenre: /f/cat=<id>/, "new" the date sort); HEAD BooksPresenter.java:678-682
    @Test
    public void aGenreWithAKnownId() {
        assertOpens(S + "/fantastika/", filtered(S + "/f/cat=9/"));
        assertOpens(S + "/fjentezi/ljubovnoe-fjentezi/", filtered(S + "/f/cat=27/"));
        assertOpens(S + "/tru-krajm/", filtered(S + "/f/cat=31/"));
    }

    // HEAD AudioknigaLife.java:77 (FILTER_CAT), :123-145; HEAD BooksPresenter.java:674-682
    @Test
    public void aGenreRanking() {
        assertOpens(S + "/f/cat=9/sort=rating/order=desc/", filtered(S + "/f/cat=9/"));
        assertOpens(S + "/f/cat=9/sort=date/order=desc/", filtered(S + "/f/cat=9/"));
    }

    // HEAD AudioknigaLife.java:123-145 (no id: every item opens the genre's own listing); HEAD BooksPresenter.java:678-682
    @Test
    public void aGenreWithoutAKnownId() {
        String genre = S + "/fantastika/kiberpank/"; // a subgenre slug missing from CATEGORY_IDS
        String[] rows = new String[HEAD_ITEMS.size() * 2];
        for (int i = 0; i < HEAD_ITEMS.size(); i++) {
            rows[2 * i] = genre;
            rows[2 * i + 1] = genre + "page/2/";
        }
        assertOpens(genre, rows);
    }

    // HEAD AudioknigaLife.java:116-119 (hasSort: not /xfsearch/, not /search/) + HEAD BooksFragment.java:253-268
    @Test
    public void authorReaderSeriesAndSearchHaveNoOrder() {
        assertNoOrder(S + "/xfsearch/avtor/%D0%98%D0%B3%D0%BE%D1%80%D1%8C%20%D0%92%D0%B8%D1%82%D1%82%D0%B5/");
        assertNoOrder(S + "/xfsearch/ispolnitel/Coyote/");
        assertNoOrder(S + "/xfsearch/serie/%D0%9F%D0%B5%D0%BA%D0%BB%D0%BE%20%28%D0%98%D0%B3%D0%BE%D1%80%D1%8C"
                + "%20%D0%92%D0%B8%D1%82%D1%82%D0%B5%29/");
        assertNoOrder(S + "/search/klan/page/1/");
    }
}
