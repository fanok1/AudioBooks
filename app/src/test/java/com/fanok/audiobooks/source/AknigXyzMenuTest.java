package com.fanok.audiobooks.source;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.fanok.audiobooks.R;
import com.fanok.audiobooks.search.ListingFilters;
import com.fanok.audiobooks.search.Provider;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * s2.aknig.xyz's Order menu pinned to git HEAD: books_dle_options_menu.xml (via HEAD AknigXyz.optionsMenu), the
 * HEAD BooksFragment no-sort rule (HEAD AknigXyz.hasSort), HEAD AknigXyz.sortUrl(int, String) + the presenter's
 * generic parser branch and its inGenre narrowing (by the genre's learnt DLE category id), HEAD pageUrl
 * (Dle.pageUrl = Url.audiobooListingPage). Sorts are the DLE form POSTed from the URL's query.
 */
public class AknigXyzMenuTest {

    private static final SourceParser P = Provider.AKNIG_XYZ.parser;
    private static final String S = "https://s2.aknig.xyz";

    /** HEAD's visible Order items, in menu order. */
    private static final List<Sort> HEAD_ITEMS = Arrays.asList(Sort.NEW, Sort.POPULAR, Sort.RATING, Sort.COMMENTS,
            Sort.TITLE);
    /** The DLE form of HEAD's AknigXyz.sortOf per item: NEW, POPULAR, RATING, COMMENTS, TITLE. */
    private static final String[][] FORMS = {{"date", "desc"}, {"news_read", "desc"}, {"rating", "desc"},
            {"comm_num", "desc"}, {"title", "asc"}};

    private static Document fixture(String name, String baseUri) throws IOException {
        try (InputStream in = AknigXyzMenuTest.class.getResourceAsStream("/fixtures/aknig-xyz/" + name)) {
            assertNotNull("missing fixture " + name, in);
            return Jsoup.parse(in, "UTF-8", baseUri);
        }
    }

    // HEAD res/menu/books_dle_options_menu.xml:19-41 (order submenu); HEAD BooksFragment.java:226 (parser menu),
    // :294-300 (reting stays visible off knigoblud/abmp3)
    private static void assertHeadMenu(String url) {
        List<Sort.Entry> menu = P.sorts(url);
        int[] titles = {R.string.new_data, R.string.popular, R.string.reting, R.string.comets, R.string.order_title};
        // changed on purpose after the live check (2026-10-30): the index menu (plain or sorted) has a sixth item, the site's fixed top-100 page ({@code AknigXyz.TOP_100}); genre and other listings keep the five
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
            assertEquals(url, AknigXyz.TOP_100, ListingFilters.sorted(Provider.AKNIG_XYZ, Sort.TOP_100, url));
        }
    }

    /** {@code expected}: per HEAD item, the URL HEAD opened (null: nothing) and HEAD's page 2 of it. */
    private static void assertOpens(String url, String... expected) {
        assertHeadMenu(url);
        assertEquals(HEAD_ITEMS.size() * 2, expected.length);
        for (int i = 0; i < HEAD_ITEMS.size(); i++) {
            Sort sort = HEAD_ITEMS.get(i);
            String sorted = ListingFilters.sorted(Provider.AKNIG_XYZ, sort, url);
            assertEquals(sort + " from " + url, expected[2 * i], sorted);
            if (sorted != null) {
                assertEquals(sort + " page 2 from " + url, expected[2 * i + 1], P.pageUrl(sorted, 2));
            }
        }
    }

    private static void assertNoOrder(String url) {
        assertTrue(url, P.sorts(url).isEmpty());
    }

    /** HEAD AknigXyz.sortedUrl's query; {@code key} is "main" or "cat_<id>". */
    private static String form(String key, int item) {
        return "?dlenewssortby=" + FORMS[item][0] + "&dledirection=" + FORMS[item][1]
                + "&set_new_sort=dle_sort_" + key + "&set_direction_sort=dle_direction_" + key;
    }

    /** Every item sorted within {@code root}, "new" (the date form) included. */
    private static String[] within(String root, String key) {
        String[] rows = new String[HEAD_ITEMS.size() * 2];
        for (int i = 0; i < HEAD_ITEMS.size(); i++) {
            rows[2 * i] = root + form(key, i);
            rows[2 * i + 1] = root + "page/2/" + form(key, i);
        }
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

    // HEAD AknigXyz.java:130-132 (pageUrl), :140-150 ("new" on the index is the index itself), :235-241 (sortedUrl);
    // HEAD BooksPresenter.java:526-528, :674-682
    @Test
    public void theIndex() {
        String[] rows = within(S + "/", "main");
        rows[0] = S + "/";
        rows[1] = S + "/page/2/";
        assertOpens(S + "/", rows);
    }

    // HEAD AknigXyz.java:153-156 (hasSort: set_new_sort=dle_sort_main), :140-150 ("new" from a sorted index is the
    // date form, not the bare index), :235-241; HEAD BooksPresenter.java:674-682
    @Test
    public void aSortedIndex() {
        assertOpens(S + "/" + form("main", 2), within(S + "/", "main"));
        assertOpens(S + "/" + form("main", 1), within(S + "/", "main"));
    }

    // HEAD AknigXyz.java:148-149 ("new" is the date-sorted index itself: url.equals(listingUrl)), :155 (hasSort:
    // set_new_sort=dle_sort_main), :268-272 (isIndex: no query); HEAD BooksPresenter.java:674-682
    @Test
    public void theDateSortedIndex() {
        assertOpens(S + "/" + form("main", 0), within(S + "/", "main"));
    }

    // HEAD AknigXyz.java:116-122 (learnCategory), :159-180 (genreOf, inGenre by the category id), :235-241;
    // HEAD BooksPresenter.java:678-682
    @Test
    public void aGenreWhoseIdIsKnown() throws IOException {
        String genre = S + "/ljubovnoe-fjentezi/";
        AknigXyz.learnCategory(genre, fixture("genre-p1.html", genre)); // its sort form: dle_sort_cat_20
        assertOpens(genre, within(genre, "cat_20"));
        assertOpens(genre + form("cat_20", 2), within(genre, "cat_20"));
        assertOpens(genre + form("cat_20", 4), within(genre, "cat_20"));
    }

    // HEAD AknigXyz.java:169-180 (inGenre: site-wide while the genre's id is unknown), :140-150;
    // HEAD BooksPresenter.java:678-682
    @Test
    public void aGenreWhoseIdIsNotKnownYet() {
        assertOpens(S + "/eve-online/", within(S + "/", "main"));
    }

    // HEAD AknigXyz.java:153-156 (hasSort: index and genres only) + HEAD BooksFragment.java:253-268 (Order hidden)
    @Test
    public void authorReaderSeriesYearAndTop100HaveNoOrder() {
        assertNoOrder(S + "/xfsearch/author/%D1%8F%D1%80%D1%80%D0%BE%D1%81%20%D1%80%D0%B5%D0%B1%D0%B5%D0%BA%D0%BA%D0%B0/");
        assertNoOrder(S + "/xfsearch/reader/%D0%B0%D0%BA%D1%82%D0%B5%D1%80%D1%81%D0%BA%D0%B8%D0%B9"
                + "%20%D0%BA%D0%BE%D0%BB%D0%BB%D0%B5%D0%BA%D1%82%D0%B8%D0%B2/");
        assertNoOrder(S + "/xfsearch/serie/%D1%8D%D0%BC%D0%BF%D0%B8%D1%80%D0%B5%D0%B9/");
        assertNoOrder(S + "/xfsearch/god/2024/");
        assertNoOrder(S + "/top100.html");
    }

    // HEAD FavoritePresenter.java:237-239 (url + "?page="); HEAD AknigXyz.java:84 (GENRE_PATH: one segment), :155
    // (neither index, genre nor dle_sort_main) + HEAD BooksFragment.java:253-268 (Order hidden)
    @Test
    public void aSeriesOpenedFromFavoritesHasNoOrder() {
        assertNoOrder(S + "/xfsearch/serie/%D1%8D%D0%BC%D0%BF%D0%B8%D1%80%D0%B5%D0%B9/?page=");
    }

    // changed on purpose after the live check (2026-10-30): the fixed top-100 page (AknigXyz.TOP_100) is an Order item of the index only
    @Test
    public void theTop100IsAnItemOfTheIndexMenuOnly() {
        assertEquals("https://s2.aknig.xyz/top100.html", AknigXyz.TOP_100);
        List<Sort> withTop = new ArrayList<>(HEAD_ITEMS);
        withTop.add(Sort.TOP_100);
        assertEquals(withTop, sortsOf(S + "/"));
        assertEquals(withTop, sortsOf(S + "/" + form("main", 2)));
        assertEquals(S + "/eve-online/", HEAD_ITEMS, sortsOf(S + "/eve-online/"));
    }

    private static List<Sort> sortsOf(String url) {
        List<Sort> sorts = new ArrayList<>();
        for (Sort.Entry entry : P.sorts(url)) {
            sorts.add(entry.sort);
        }
        return sorts;
    }
}
