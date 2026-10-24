package com.fanok.audiobooks.pojo;

import static com.fanok.audiobooks.BookPageFixtures.BOOK_PAGES;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.fanok.audiobooks.BookPageFixtures;
import java.io.IOException;
import org.jsoup.Jsoup;
import org.junit.Test;

public class BookPOJOTest {

    @Test
    public void everySourceFixtureYieldsANamedBookWithAbsoluteCover() throws IOException {
        for (String[] page : BOOK_PAGES) {
            BookPOJO book = BookPOJO.parseBook(BookPageFixtures.load(page[0], page[1]), page[1]);
            assertFalse(page[0], book.isNull());
            assertEquals(page[0], page[1], book.getUrl());
            assertTrue(page[0] + " cover " + book.getPhoto(),
                    book.getPhoto() != null && book.getPhoto().startsWith("https://"));
        }
    }

    @Test
    public void seriesLinksPointToTheBooksOwnSite() throws IOException {
        String akniga = BOOK_PAGES[3][1];
        BookPOJO book = BookPOJO.parseBookAkniga(BookPageFixtures.load(BOOK_PAGES[3][0], akniga), akniga);
        assertTrue(book.getUrlSeries(), book.getUrlSeries().startsWith("https://akniga.org/series/"));
        String audioboo = BOOK_PAGES[6][1];
        BookPOJO boo = BookPOJO.parseBookAudioboo(BookPageFixtures.load(BOOK_PAGES[6][0], audioboo), audioboo);
        assertEquals("https://audioboo.org/xfsearch/cikl/%D0%A1%D0%BE%D0%BB%D0%BE/", boo.getUrlSeries());
    }

    @Test
    public void emptyPageIsATypedErrorAndUnknownHostIsRejected() {
        for (String[] page : BOOK_PAGES) {
            try {
                BookPOJO.parseBook(Jsoup.parse("", page[1]), page[1]);
                fail(page[0] + ": empty page parsed");
            } catch (IOException expected) {
                // typed error
            }
        }
        try {
            BookPOJO.requireSupported("https://example.com/book/1");
            fail("unknown host accepted");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().startsWith("unsupported host"));
        }
        assertEquals("https://baza-knig.top/a/1.html", BookPOJO.requireSupported("https://baza-knig.ink/a/1.html"));
    }

    @Test
    public void bazaKnigTitleWithoutDashAndMissingChildrenAreTolerated() throws IOException {
        String baza = BOOK_PAGES[4][1];
        assertEquals("Книга", BookPOJO.parseBook(Jsoup.parse("<h1>Книга</h1>", baza), baza).getName());
        String izibuk = BOOK_PAGES[1][1];
        BookPOJO book = BookPOJO.parseBook(Jsoup.parse(
                "<span itemprop=name>Книга</span><div class=_5e0b77></div><div class=_7e215f></div>", izibuk), izibuk);
        assertNull(book.getPhoto());
        assertNull(book.getGenre());
    }

    @Test
    public void settersTolerateNullAndEmptyInput() {
        BookPOJO book = new BookPOJO();
        book.setName(null);
        assertEquals("", book.getName());
        book.setName("");
        book.setUrl(null);
        book.setUrl("");
        book.setPhoto(null);
        book.setAutor(null);
        book.setArtist("");
        book.setTime(null);
        book.setSeries(null);
        book.setGenre("");
        book.setDesc(null);
        book.setReting(null);
        assertEquals("", book.getReting());
        book.setComents(null);
        book.setComents("");
        book.setComents("99999999999 комментариев");
        assertEquals(0, book.getComents());
        book.setComents("12 комментариев");
        assertEquals(12, book.getComents());
        assertTrue(book.isNull());
        book.setUrl("https://knigavuhe.org/book/x/");
        assertTrue("an empty name is not a book", book.isNull());

        DescriptionPOJO description = new DescriptionPOJO();
        description.setTitle(null);
        description.setPoster(null);
        description.setReiting(null);
        description.setTime(null);
        description.setAutor(null);
        description.setArtist(null);
        description.setSeries(null);
        description.setSeriesUrl(null);
        description.setDescription(null);
        description.setAutorUrl(null);
        description.setArtistUrl(null);
        description.setGenre(null);
        description.setGenreUrl(null);
        description.setLike(null);
        description.setDisLike("");
        description.setFavorite(null);
        assertEquals("", description.getReiting());
        assertEquals("", description.getTitle());
        assertEquals("0", description.getFavorite());
    }
}
