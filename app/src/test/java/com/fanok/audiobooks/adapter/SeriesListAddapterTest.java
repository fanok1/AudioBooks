package com.fanok.audiobooks.adapter;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SeriesListAddapterTest {

    private static final String BOOK = "https://knigavuhe.org/book/devjatiehtazhka-2/";

    @Test
    public void theEntryWithoutALinkIsTheOpenBook() {
        // knigavuhe, izibuk and knigoblud list the open book as plain text
        assertTrue(SeriesListAddapter.isCurrent("", BOOK));
    }

    @Test
    public void anEntryLinkingTheSamePageIsTheOpenBookWhateverItsSpelling() {
        assertTrue(SeriesListAddapter.isCurrent(BOOK, BOOK));
        assertTrue(SeriesListAddapter.isCurrent("http://www.knigavuhe.org/book/devjatiehtazhka-2", BOOK));
    }

    @Test
    public void anotherBookOfTheSeriesIsNot() {
        assertFalse(SeriesListAddapter.isCurrent("https://knigavuhe.org/book/devjatiehtazhka-3/", BOOK));
    }
}
