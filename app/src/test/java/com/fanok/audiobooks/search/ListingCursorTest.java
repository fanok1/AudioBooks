package com.fanok.audiobooks.search;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;

public class ListingCursorTest {

    private static ProviderPage<String> page(boolean hasMore, String... items) {
        return ProviderPage.of(Arrays.asList(items), hasMore);
    }

    private static ListingCursor<String> twoPagesLoaded() {
        ListingCursor<String> c = new ListingCursor<>(s -> s);
        c.apply(c.nextPage(), page(true, "a", "b"));
        c.apply(c.nextPage(), page(true, "c"));
        return c;
    }

    @Test
    public void aFailedRefreshKeepsCursorListAndDedupeSoScrollingGoesOn() {
        ListingCursor<String> c = twoPagesLoaded();
        // the refresh of page 1 failed: the presenter applies nothing, the cursor is untouched
        assertEquals(3, c.nextPage());
        c.apply(c.nextPage(), page(true, "d"));
        assertEquals(Arrays.asList("a", "b", "c", "d"), c.items());
        assertFalse(c.isEnd());
    }

    @Test
    public void aSuccessfulRefreshSwapsInPageOneAndItsOwnDedupeSet() {
        ListingCursor<String> c = twoPagesLoaded();
        c.apply(1, page(true, "a", "b"));
        assertEquals(Arrays.asList("a", "b"), c.items());
        assertEquals(2, c.nextPage());
        assertFalse("page 1 again is new to a refreshed list", c.isEnd());
        c.apply(c.nextPage(), page(true, "c"));
        assertEquals(Arrays.asList("a", "b", "c"), c.items());
    }

    @Test
    public void aRepeatedLastPageEndsTheListButAFilteredOutPageDoesNot() {
        ListingCursor<String> c = twoPagesLoaded();
        c.apply(c.nextPage(), ProviderPage.of(Arrays.<String>asList(), true, 3)); // all cards filtered out
        assertFalse(c.isEnd());
        assertEquals(4, c.nextPage());
        c.apply(c.nextPage(), page(true, "c")); // the provider repeats its last page
        assertTrue(c.isEnd());
        assertEquals(Arrays.asList("a", "b", "c"), c.items());
    }
}
