package com.fanok.audiobooks.source;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.fanok.audiobooks.Url;
import com.fanok.audiobooks.search.Provider;
import com.fanok.audiobooks.search.SearchUrls;

import org.junit.Test;

/** How audioboo's listings are sorted and paged, and which requests go out as a form. */
public class AudiobooTest {

    private final Audioboo parser = new Audioboo();
    private static final String GENRE = Url.SERVER_AUDIOBOO + "/fantastika/";

    /** The sort form values the legacy code POSTed for each menu sort (it read them from the subtitle). */
    @Test
    public void aSortTravelsInTheUrlAndIsPostedAsTheLegacyForm() {
        String[][] sorts = {{"NEW", "date"}, {"TITLE", "title"}, {"RATING", "rating"}, {"POPULAR", "views"},
                {"COMMENTS", "comm"}, {"DURATION", "time"}};
        for (String[] sort : sorts) {
            String sorted = parser.sortUrl(Sort.valueOf(sort[0]), GENRE);
            assertEquals(sort[0], sort[1], Audioboo.sortOf(sorted));
            assertEquals(sort[0], GENRE, Audioboo.withoutSort(sorted));
            // page 2 keeps the sort; the site is asked for the plain page 2
            assertEquals(sort[1], Audioboo.sortOf(parser.pageUrl(sorted, 2)));
            assertEquals(Url.audiobooListingPage(GENRE, 2), Audioboo.withoutSort(parser.pageUrl(sorted, 2)));
            // a re-sort replaces the sort
            assertEquals("comm", Audioboo.sortOf(parser.sortUrl(Sort.COMMENTS, sorted)));
        }
        assertEquals(GENRE, parser.sortUrl(Sort.NEW, GENRE));
        assertEquals("date", Audioboo.sortOf(GENRE));
        assertNull(parser.sortUrl(Sort.POPULAR_WEEK, GENRE));
        assertEquals(GENRE + "?p=1", Audioboo.withoutSort(GENRE + "?xs_value=rating&p=1"));
        assertEquals(GENRE + "?p=1", Audioboo.withoutSort(GENRE + "?p=1&xs_value=rating"));
        assertEquals(GENRE + "?p=1&q=2", Audioboo.withoutSort(GENRE + "?p=1&xs_value=rating&q=2"));
        assertEquals(Audioboo.TIMEOUT_MS, parser.timeoutMs());
    }

    @Test
    public void theMenuAndWhereItIsHidden() {
        assertEquals(6, parser.sorts(Url.INDEX_AUDIOBOO).size());
        assertEquals(Sort.TITLE, parser.sorts(GENRE).get(1).sort);
        assertEquals(Sort.DURATION, parser.sorts(GENRE).get(5).sort);
        // the site's author and reader links are not named so: they keep the menu
        assertFalse(parser.sorts(Url.SERVER_AUDIOBOO + "/xfsearch/avtora/X/").isEmpty());
        assertFalse(parser.sorts(Url.SERVER_AUDIOBOO + "/tags/X/").isEmpty());
        assertTrue(parser.sorts(Url.SERVER_AUDIOBOO + "/xfsearch/cikl/X/").isEmpty());
    }

    @Test
    public void onlyTheBookSearchIsPostedAsAForm() {
        assertTrue(Audioboo.isSearchForm(SearchUrls.url(Provider.AUDIOBOO, SearchUrls.Category.BOOKS, "мастер", 2)));
        assertFalse(Audioboo.isSearchForm(parser.otherSourceSearchUrl("мастер", 1)));
        assertFalse(Audioboo.isSearchForm(parser.otherSourceSearchUrl("мастер", 2)));
        assertFalse(Audioboo.isSearchForm(GENRE));
        assertEquals(Url.SERVER_AUDIOBOO + "/index.php?do=search&subaction=search&story=a+b&search_start=2"
                + "&result_from=11&full_search=0", SearchUrls.url(Provider.AUDIOBOO, SearchUrls.Category.BOOKS, "a b", 2));
    }
}
