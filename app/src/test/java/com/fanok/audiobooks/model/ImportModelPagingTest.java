package com.fanok.audiobooks.model;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.jsoup.Jsoup;
import org.junit.Test;

public class ImportModelPagingTest {

    @Test
    public void nextPageOnlyWhenTheNavigationLinksOne() {
        assertTrue(ImportModel.hasNextPage(Jsoup.parse(
                "<div class='page__nav'><a class='page__nav--prev'></a><a class='page__nav--next'></a></div>")));
        assertFalse(ImportModel.hasNextPage(Jsoup.parse(
                "<div class='page__nav'><a class='page__nav--prev'></a></div>")));
        assertFalse(ImportModel.hasNextPage(Jsoup.parse("<div class='content__main__articles--item'></div>")));
    }
}
