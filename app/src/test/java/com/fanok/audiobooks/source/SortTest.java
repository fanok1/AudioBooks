package com.fanok.audiobooks.source;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

/** The sort vocabulary: one menu item per sort, and every DLE menu sort has its form. */
public class SortTest {

    @Test
    public void everySortHasItsOwnMenuItem() {
        Set<Integer> ids = new HashSet<>();
        for (Sort sort : Sort.values()) {
            assertTrue(sort + " shares a menu id", ids.add(sort.menuId));
            assertSame(sort, Sort.ofMenuId(sort.menuId));
            assertTrue(sort.tag.length() > 0);
        }
        assertNull(Sort.ofMenuId(0));
    }

    @Test
    public void theDleMenuIsTheFormsOfTheEngine() {
        for (Sort.Entry entry : Dle.MENU) {
            assertNotNull(entry.sort);
            assertNotNull(entry.sort + " has no DLE form", Dle.Order.of(entry.sort));
            assertSame(Dle.Order.of(entry.sort), Dle.Order.ofField(Dle.Order.of(entry.sort).field));
        }
        assertNull(Dle.Order.of(Sort.POPULAR_WEEK));
        assertNull(Dle.Order.ofField("bogus"));
    }

    @Test
    public void aSubmenuKeepsItsItemsInOrder() {
        Sort.Entry popular = Sort.submenu(1, Sort.POPULAR_DAY, Sort.POPULAR_WEEK);
        assertNull(popular.sort);
        assertEquals(1, popular.title);
        assertEquals(Sort.POPULAR_WEEK, popular.items.get(1));
        assertEquals(Sort.NEW.title, Sort.item(Sort.NEW).title);
    }

    /**
     * A submenu header only opens its submenu: knigavuhe's rating header used to share the id of the plain rating sort
     * and, in a genre, also pushed the bare genre root (whose pages all repeat page 1).
     */
    @Test
    public void knigavuheRatingHeaderOnlyOpensItsPeriods() {
        Knigavuhe parser = new Knigavuhe();
        String genre = com.fanok.audiobooks.Url.SERVER + "/genre/fantastika/<page>/";
        assertNull(parser.sorts(genre).get(2).sort);
        assertNull(parser.sortUrl(Sort.RATING, genre));
    }
}
