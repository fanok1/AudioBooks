package com.fanok.audiobooks.source;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.fanok.audiobooks.model.OtherSourceModel;
import com.fanok.audiobooks.pojo.AudioPOJO;
import com.fanok.audiobooks.pojo.BookPOJO;
import com.fanok.audiobooks.pojo.DescriptionPOJO;
import com.fanok.audiobooks.pojo.GenrePOJO;
import com.fanok.audiobooks.search.ProviderPage;
import com.fanok.audiobooks.search.SearchUrls;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** A site that implements only the required methods has no optional feature, and its other-source lookup is its book search. */
public class SourceParserDefaultsTest {

    /** The smallest site: six methods; its search page is a listing ({@link SourceParser#searchResults}). */
    private static final class Minimal implements SourceParser {
        @Override public ProviderPage<BookPOJO> listing(Document page, String url, int pageNo) {
            BookPOJO book = new BookPOJO();
            book.setUrl("https://site.test/book/1");
            book.setName(page.title());
            book.setAutor("Автор");
            return ProviderPage.of(Collections.singletonList(book), false);
        }
        @Override public String pageUrl(String url, int page) { return url + "?p=" + page; }
        @Override public ProviderPage<GenrePOJO> genres(Document page, String url, int pageNo) { return ProviderPage.empty(); }
        @Override public String searchUrl(SearchUrls.Category category, String query, int page) {
            return category == SearchUrls.Category.BOOKS ? "https://site.test/search?q=" + SearchUrls.enc(query) + "&p=" + page : null;
        }
        @Override public DescriptionPOJO description(Document page, String bookUrl) { return new DescriptionPOJO(); }
        @Override public ArrayList<AudioPOJO> tracks(Document page, String base) { throw new IllegalStateException("no player"); }
    }

    private final SourceParser parser = new Minimal();
    private final Document page = Jsoup.parse("<html><head><title>Ночной дозор</title></head><body></body></html>");

    @Test
    public void optionalFeaturesAreOffByDefault() throws Exception {
        assertFalse(parser.hasSort("https://site.test/"));
        assertTrue(parser.sorts("https://site.test/").isEmpty());
        assertNull(parser.sortUrl(Sort.RATING, "https://site.test/"));
        assertTrue(parser.listsDuration("https://site.test/"));
        assertNull(parser.genreOf("https://site.test/genre/1"));
        assertEquals("https://site.test/new", parser.inGenre("https://site.test/new", "1"));
        assertTrue(parser.people(page, "https://site.test/authors", 1, false).items.isEmpty());
        assertTrue(parser.searchPeople(page, 1, true).items.isEmpty());
        assertTrue(parser.recommendations(page, "https://site.test/book/1").isEmpty());
        assertNull(parser.mediaHeaders("https://site.test/book/1"));
        assertTrue(parser.mediaCookies("https://site.test/book/1").isEmpty());
        assertTrue(parser.series(page, "https://site.test/book/1").isEmpty());
        assertTrue(parser.comments("https://site.test/book/1", () -> false).isEmpty());
        assertFalse(parser.findsOtherReaders());
        assertFalse(parser.probesOtherReaders());
        assertEquals(1, parser.otherSourcePages());
        assertTrue(parser.otherReaders("https://site.test/book/1", "t", "a", "r").isEmpty());
        assertNull(parser.referer());
    }

    @Test
    public void otherSourceLookupDerivesFromTheBookSearch() {
        BookPOJO book = new BookPOJO();
        book.setName("Ночной дозор");
        book.setAutor("Лукьяненко");
        book.setArtist(null);
        assertEquals("Ночной дозор Лукьяненко", parser.otherSourceTerms(book));
        assertEquals(parser.searchUrl(SearchUrls.Category.BOOKS, "a b", 2), parser.otherSourceSearchUrl("a b", 2));
        List<OtherSourceModel.Hit> hits = parser.otherSourceHits(page);
        assertEquals(1, hits.size());
        assertEquals("https://site.test/book/1", hits.get(0).url);
        assertEquals("Ночной дозор", hits.get(0).title);
        assertEquals("Автор", hits.get(0).author);
        assertSame("a missing reader is \"\", never null", "", hits.get(0).reader);
    }
}
