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
 * audioknigi.fun's Order menu pinned to git HEAD: books_dle_options_menu.xml (via HEAD AudioknigiFun.optionsMenu),
 * the HEAD BooksFragment no-sort rule (HEAD AudioknigiFun.hasSort = sortKey), HEAD AudioknigiFun.sortUrl(int, String)
 * + the presenter's generic parser branch and its inGenre narrowing, HEAD pageUrl (Dle.pageUrl =
 * Url.audiobooListingPage). Sorts are the DLE form POSTed from the URL's query.
 */
public class AudioknigiFunMenuTest {

    private static final SourceParser P = Provider.AUDIOKNIGI_FUN.parser;
    private static final String S = "https://audioknigi.fun";

    /** HEAD's visible Order items, in menu order. */
    private static final List<Sort> HEAD_ITEMS = Arrays.asList(Sort.NEW, Sort.POPULAR, Sort.RATING, Sort.COMMENTS,
            Sort.TITLE);
    /** The DLE form HEAD's AudioknigiFun.Sort sent per item: DATE, VIEWS, RATING, COMMENTS, TITLE. */
    private static final String[][] FORMS = {{"date", "desc"}, {"news_read", "desc"}, {"rating", "desc"},
            {"comm_num", "desc"}, {"title", "asc"}};

    // HEAD res/menu/books_dle_options_menu.xml:19-41 (order submenu); HEAD BooksFragment.java:226 (parser menu),
    // :294-300 (reting stays visible off knigoblud/abmp3)
    private static void assertHeadMenu(String url) {
        List<Sort.Entry> menu = P.sorts(url);
        int[] titles = {R.string.new_data, R.string.popular, R.string.reting, R.string.comets, R.string.order_title};
        // changed on purpose after the live check (2026-10-30): the index menu (plain or sorted) has a sixth item, the site's fixed top-100 page ({@code AudioknigiFun.TOP_100}); genre and other listings keep the five
        boolean index = url.equals(S + "/") || url.contains("set_new_sort=dle_sort_main");
        assertEquals(url, HEAD_ITEMS.size() + (index ? 1 : 0), menu.size());
        for (int i = 0; i < HEAD_ITEMS.size(); i++) {
            assertEquals(url, HEAD_ITEMS.get(i), menu.get(i).sort);
            assertEquals(url, titles[i], menu.get(i).title);
            assertTrue(url, menu.get(i).items.isEmpty());
        }
        if (index) {
            Sort.Entry top = menu.get(HEAD_ITEMS.size());
            assertEquals(url, Sort.TOP_100, top.sort);
            assertEquals(url, R.string.abmp3_top100, top.title);
            assertTrue(url, top.items.isEmpty());
            assertEquals(url, AudioknigiFun.TOP_100, ListingFilters.sorted(Provider.AUDIOKNIGI_FUN, Sort.TOP_100, url));
        }
    }

    /** {@code expected}: per HEAD item, the URL HEAD opened (null: nothing) and HEAD's page 2 of it. */
    private static void assertOpens(String url, String... expected) {
        assertHeadMenu(url);
        assertEquals(HEAD_ITEMS.size() * 2, expected.length);
        for (int i = 0; i < HEAD_ITEMS.size(); i++) {
            Sort sort = HEAD_ITEMS.get(i);
            String sorted = ListingFilters.sorted(Provider.AUDIOKNIGI_FUN, sort, url);
            assertEquals(sort + " from " + url, expected[2 * i], sorted);
            if (sorted != null) {
                assertEquals(sort + " page 2 from " + url, expected[2 * i + 1], P.pageUrl(sorted, 2));
            }
        }
    }

    private static void assertNoOrder(String url) {
        assertTrue(url, P.sorts(url).isEmpty());
    }

    /** HEAD AudioknigiFun.sorted's query (okhttp builds it in this order). */
    private static String form(String key, int item) {
        return "?dlenewssortby=" + FORMS[item][0] + "&dledirection=" + FORMS[item][1]
                + "&set_new_sort=dle_sort_" + key + "&set_direction_sort=dle_direction_" + key;
    }

    /** Every item within {@code root} (a genre, author, reader, series or collection), "new" included. */
    private static String[] within(String root, String key) {
        String[] rows = new String[HEAD_ITEMS.size() * 2];
        for (int i = 0; i < HEAD_ITEMS.size(); i++) {
            rows[2 * i] = root + form(key, i);
            rows[2 * i + 1] = root + "page/2/" + form(key, i);
        }
        return rows;
    }

    /** The index: "new" is the bare index, the rest its main sort. */
    private static String[] siteWide() {
        String[] rows = within(S + "/", "main");
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

    // HEAD AudioknigiFun.java:97-100 (pageUrl), :109-116 (sortUrl), :124-128 (the index is no genre), :219-230;
    // HEAD BooksPresenter.java:526-528, :674-682
    @Test
    public void theIndex() {
        assertOpens(S + "/", siteWide());
    }

    // HEAD AudioknigiFun.java:109-116, :124-128, :219-230; HEAD BooksPresenter.java:674-682 (url == mUrl: no narrowing)
    @Test
    public void aSortedIndex() {
        assertOpens(S + "/" + form("main", 2), siteWide());
        assertOpens(S + "/" + form("main", 4), siteWide());
    }

    // HEAD AudioknigiFun.java:124-138 (genreOf root, inGenre keeps the item's field), :219-252 (xfsearch key);
    // HEAD BooksPresenter.java:678-682
    @Test
    public void aGenre() {
        assertOpens(S + "/zhanr/fantastika/", within(S + "/zhanr/fantastika/", "xfsearch"));
    }

    // HEAD AudioknigiFun.java:124-138, :219-252; HEAD BooksPresenter.java:678-682
    @Test
    public void aSortedGenre() {
        String genre = S + "/zhanr/jumor/";
        assertOpens(genre + form("xfsearch", 2), within(genre, "xfsearch"));
    }

    // HEAD AudioknigiFun.java:118-121 (hasSort: author/read/cikl are xfsearch listings), :124-138, :239-251;
    // HEAD BooksPresenter.java:678-682
    @Test
    public void authorReaderAndSeriesSortWithinThemselves() {
        for (String root : new String[]{S + "/author/ajzek-azimov/", S + "/read/anna-prudnikova/",
                S + "/cikl/dzhivs-i-vuster/"}) {
            assertOpens(root, within(root, "xfsearch"));
        }
    }

    // HEAD AudioknigiFun.java:118-121, :124-138, :239-251 (podborki: the selection key); HEAD BooksPresenter.java:678-682
    @Test
    public void aCollection() {
        String root = S + "/podborki/audioknigi-pro-zombi/";
        assertOpens(root, within(root, "selection"));
    }

    // HEAD FavoritePresenter.java:237-239 (url + "?page="); HEAD AudioknigiFun.java:239-249 (path only: xfsearch),
    // :124-138 (genreOf drops the query); HEAD BooksPresenter.java:678-682
    @Test
    public void aSeriesOpenedFromFavorites() {
        String root = S + "/cikl/dzhivs-i-vuster/";
        assertOpens(root + "?page=", within(root, "xfsearch"));
    }

    // HEAD AudioknigiFun.java:124-138, :219-230, :239-251; HEAD BooksPresenter.java:678-682 (sorted mUrl: same root)
    @Test
    public void aSortedAuthorReaderSeriesAndCollection() {
        for (String root : new String[]{S + "/author/ajzek-azimov/", S + "/read/anna-prudnikova/",
                S + "/cikl/dzhivs-i-vuster/"}) {
            assertOpens(root + form("xfsearch", 2), within(root, "xfsearch"));
        }
        String collection = S + "/podborki/audioknigi-pro-zombi/";
        assertOpens(collection + form("selection", 2), within(collection, "selection"));
    }

    // HEAD AudioknigiFun.java:118-121 + :239-251 (no sort key) + HEAD BooksFragment.java:253-268 (Order hidden)
    @Test
    public void theTop100HasNoOrder() {
        assertNoOrder(S + "/top-100.html");
    }

    // changed on purpose after the live check (2026-10-30): the fixed top-100 page (AudioknigiFun.TOP_100) is an Order item of the index only
    @Test
    public void theTop100IsAnItemOfTheIndexMenuOnly() {
        assertEquals("https://audioknigi.fun/top-100.html", AudioknigiFun.TOP_100);
        List<Sort> withTop = new ArrayList<>(HEAD_ITEMS);
        withTop.add(Sort.TOP_100);
        assertEquals(withTop, sortsOf(S + "/"));
        assertEquals(withTop, sortsOf(S + "/" + form("main", 2)));
        assertEquals(S + "/zhanr/fantastika/", HEAD_ITEMS, sortsOf(S + "/zhanr/fantastika/"));
        assertEquals(S + "/author/ajzek-azimov/", HEAD_ITEMS, sortsOf(S + "/author/ajzek-azimov/"));
        assertEquals(S + "/podborki/audioknigi-pro-zombi/", HEAD_ITEMS, sortsOf(S + "/podborki/audioknigi-pro-zombi/"));
    }

    private static List<Sort> sortsOf(String url) {
        List<Sort> sorts = new ArrayList<>();
        for (Sort.Entry entry : P.sorts(url)) {
            sorts.add(entry.sort);
        }
        return sorts;
    }
}
