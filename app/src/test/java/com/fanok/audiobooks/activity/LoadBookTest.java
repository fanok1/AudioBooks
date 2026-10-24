package com.fanok.audiobooks.activity;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class LoadBookTest {

    @Test
    public void deepLinkDataIsAcceptedAndMovedToTheCurrentHost() {
        assertEquals("https://akniga.org/book-1", LoadBook.bookUrl(null, "https://akniga.org/book-1"));
        assertEquals("https://baza-knig.top/x.html", LoadBook.bookUrl(null, "https://baza-knig.ink/x.html"));
        assertEquals("https://izib.uk/b/1", LoadBook.bookUrl(null, "http://izibuk.ru/b/1"));
    }

    @Test
    public void explicitExtraWinsAndUnknownHostsAreRejected() {
        assertEquals("https://knigavuhe.org/book/x/",
                LoadBook.bookUrl("https://knigavuhe.org/book/x/", "https://akniga.org/y"));
        assertNull(LoadBook.bookUrl(null, "https://example.com/book"));
        assertNull(LoadBook.bookUrl(null, null));
        assertNull(LoadBook.bookUrl("not a url", null));
    }
}
