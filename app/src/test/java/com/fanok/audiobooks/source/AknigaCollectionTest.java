package com.fanok.audiobooks.source;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.fanok.audiobooks.BookPageFixtures;
import com.fanok.audiobooks.pojo.BookPOJO;
import com.fanok.audiobooks.search.Provider;
import com.fanok.audiobooks.search.ProviderPage;

import org.junit.Test;

import java.io.IOException;

/** An akniga collection ("Подборка") card opens as the list of its books, not as a book page without a player. */
public class AknigaCollectionTest {

    private static final String COLLECTION = "https://akniga.org/collection/v-derevne-yavno-tvoritsya-kakaya-to-hren/";

    @Test
    public void aCollectionIsAOnePageListingOfItsBooks() throws IOException {
        ProviderPage<BookPOJO> page = Provider.AKNIGA.parser.listing(
                BookPageFixtures.load("akniga/collection.html", COLLECTION), COLLECTION, 1);
        assertEquals(ProviderPage.Status.OK, page.status);
        assertFalse("every book is on the one page", page.hasMore);
        assertEquals(16, page.items.size());
        assertEquals("https://akniga.org/lavkraft-govard-uzhas-danvicha", page.items.get(0).getUrl());
        for (BookPOJO book : page.items) {
            assertFalse(book.getUrl(), Provider.AKNIGA.parser.opensAsListing(book.getUrl()));
        }
    }

    @Test
    public void onlyACollectionOpensAsAListingAndItIsNotResorted() {
        SourceParser akniga = Provider.AKNIGA.parser;
        assertTrue(akniga.opensAsListing(COLLECTION));
        assertFalse(akniga.opensAsListing("https://akniga.org/lavkraft-govard-uzhas-danvicha"));
        assertFalse("the list of all collections", akniga.opensAsListing("https://akniga.org/collections/page1/"));
        assertTrue(akniga.sorts(COLLECTION).isEmpty());
        assertFalse("no other site has collections", Provider.UKNIG.parser.opensAsListing(COLLECTION));
    }
}
