package com.fanok.audiobooks.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.fanok.audiobooks.Url;
import com.fanok.audiobooks.pojo.BookPOJO;
import com.fanok.audiobooks.search.Provider;
import com.fanok.audiobooks.search.ProviderPage;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;

/** Listing and search pages saved from the providers (see src/test/resources/fixtures). */
public class BooksModelParsersTest {

    private static Document fixture(String name, String baseUri) throws IOException {
        try (InputStream in = BooksModelParsersTest.class.getResourceAsStream("/fixtures/" + name)) {
            assertNotNull("missing fixture " + name, in);
            return Jsoup.parse(in, "UTF-8", baseUri);
        }
    }

    private static void assertAllHaveNameAndUrl(ProviderPage<BookPOJO> page, String host) {
        for (BookPOJO book : page.items) {
            assertFalse(book.getName().isEmpty());
            assertTrue(book.getUrl(), Url.isHost(book.getUrl(), host));
        }
    }

    @Test
    public void knigavuheNewPageOne() throws IOException {
        ProviderPage<BookPOJO> page = BooksModel.parseKnigavuhe(
                fixture("knigavuhe/listing-new-p1.html", Url.INDEX + "1"), Url.INDEX + "1", 1);
        assertEquals(ProviderPage.Status.OK, page.status);
        assertEquals(10, page.items.size());
        assertEquals("Молчание", page.items.get(0).getName());
        assertEquals("Игра в прятки", page.items.get(9).getName());
        assertTrue("6508 pages", page.hasMore);
        assertAllHaveNameAndUrl(page, Url.SERVER);
    }

    @Test
    public void knigavuheSearchHasOnePage() throws IOException {
        String url = Url.SERVER + "/search/?q=1984&page=1";
        ProviderPage<BookPOJO> page = BooksModel.parseKnigavuhe(fixture("knigavuhe/search-1984-p1.html", url), url, 1);
        assertEquals("5 cards, 2 of them LitRes-only", 3, page.items.size());
        assertEquals("1984", page.items.get(0).getName());
        assertFalse(page.hasMore);
    }

    @Test
    public void aknigaIndexPageOneStripsAuthorFromTitle() throws IOException {
        ProviderPage<BookPOJO> page = BooksModel.parseAkniga(fixture("akniga/listing-index-p1.html", Url.SERVER_AKNIGA), 1);
        assertEquals(12, page.items.size());
        assertEquals("Солнце мёртвых", page.items.get(0).getName());
        assertEquals("Атеев Алексей", page.items.get(0).getAutor());
        assertEquals("Святой папочка", page.items.get(10).getName());
        assertEquals("Книги для бессонной ночи", page.items.get(11).getName()); // a card without an author
        assertTrue(page.hasMore);
        assertAllHaveNameAndUrl(page, Url.SERVER_AKNIGA);
    }

    @Test
    public void aknigaAuthorListingPagesOneAndTwo() throws IOException {
        String base = Url.SERVER_AKNIGA + "/author/Кинг Стивен/page";
        ProviderPage<BookPOJO> p1 = BooksModel.parseAkniga(fixture("akniga/author-king-p1.html", base + "1/"), 1);
        ProviderPage<BookPOJO> p2 = BooksModel.parseAkniga(fixture("akniga/author-king-p2.html", base + "2/"), 2);
        assertEquals(12, p1.items.size());
        assertEquals(12, p2.items.size());
        assertTrue(p1.hasMore);
        assertTrue(p2.hasMore);
        assertNotEquals(p1.items.get(0).getUrl(), p2.items.get(0).getUrl());
        assertAllHaveNameAndUrl(p1, Url.SERVER_AKNIGA);
        for (BookPOJO book : p1.items) {
            // the card links lead to listings this app can open again
            if (book.getUrlAutor() != null) {
                assertEquals(book.getUrlAutor(), Provider.AKNIGA,
                        Provider.ofUrl(book.getUrlAutor().replace("<page>", "1")));
            }
        }
    }

    @Test
    public void aknigaPerformerFirstAndLastPage() throws IOException {
        String base = Url.SERVER_AKNIGA + "/performer/Панков Роман/page";
        ProviderPage<BookPOJO> first = BooksModel.parseAkniga(fixture("akniga/performer-pankov-p1.html", base + "1/"), 1);
        ProviderPage<BookPOJO> last = BooksModel.parseAkniga(fixture("akniga/performer-pankov-p12.html", base + "12/"), 12);
        assertEquals(12, first.items.size());
        assertTrue(first.hasMore);
        assertEquals(11, last.items.size());
        assertFalse(last.hasMore);
    }

    @Test
    public void audiobooIndexGenreAndSearchPages() throws IOException {
        ProviderPage<BookPOJO> index = BooksModel.parseAudioboo(
                fixture("audioboo/listing-index-p1.html", Url.SERVER_AUDIOBOO + "/"), 1);
        assertEquals(27, index.items.size());
        assertTrue(index.hasMore);
        assertAllHaveNameAndUrl(index, Url.SERVER_AUDIOBOO);
        ProviderPage<BookPOJO> genre = BooksModel.parseAudioboo(
                fixture("audioboo/listing-genre-p2.html", Url.SERVER_AUDIOBOO + "/didiktiva/page/2/"), 2);
        assertEquals(27, genre.items.size());
        assertTrue(genre.hasMore);
        ProviderPage<BookPOJO> search = BooksModel.parseAudiobooSearch(
                fixture("audioboo/search-master-p2.html", Url.SERVER_AUDIOBOO + "/index.php"), 2);
        assertEquals(50, search.items.size());
        assertTrue("its pager only links back to page 1", search.hasMore);
    }

    @Test
    public void aknigaSearchPageHasNextButton() throws IOException {
        ProviderPage<BookPOJO> page = BooksModel.parseAkniga(fixture("akniga/search-1984-p1.html", Url.SERVER_AKNIGA), 1);
        assertEquals(12, page.items.size());
        assertTrue(page.hasMore);
    }

    @Test
    public void bazaKnigPageTwoOfTheIndex() throws IOException {
        ProviderPage<BookPOJO> page = BooksModel.parseBazaKnig(
                fixture("bazaknig/listing-index-p2.html", Url.SERVER_BAZA_KNIG + "/page/2/"), 2);
        assertEquals(11, page.items.size());
        assertEquals("Хозяин Оков 14", page.items.get(0).getName());
        assertEquals("Матисов Павел", page.items.get(0).getAutor());
        assertEquals("Хозяин оков", page.items.get(0).getSeries());
        assertEquals("Тайна Ночного шоссе", page.items.get(10).getName());
        assertTrue("9532 pages", page.hasMore);
        assertAllHaveNameAndUrl(page, Url.SERVER_BAZA_KNIG);
    }

    @Test
    public void bazaKnigSearchWithoutPagerEndsAfterOnePage() throws IOException {
        ProviderPage<BookPOJO> page = BooksModel.parseBazaKnig(
                fixture("bazaknig/search-1984-p1.html", Url.SERVER_BAZA_KNIG), 1);
        assertEquals(4, page.items.size());
        assertEquals("Назад в СССР 1984", page.items.get(0).getName());
        assertEquals("Необыкновенный Икс. 1964 – 1984", page.items.get(3).getName());
        assertFalse(page.hasMore);
    }

    @Test
    public void otherListingsParseAndDetectTheirPagers() throws IOException {
        ProviderPage<BookPOJO> izibuk = BooksModel.parseIzibuk(
                fixture("izibuk/listing-index-p1.html", Url.INDEX_IZIBUK + "1"), Url.INDEX_IZIBUK + "1", 1);
        assertEquals(20, izibuk.items.size());
        assertTrue(izibuk.hasMore);
        assertAllHaveNameAndUrl(izibuk, Url.SERVER_IZIBUK);

        ProviderPage<BookPOJO> abmp3 = BooksModel.parseAbmp3(fixture("abmp3/listing-index-p2.html", Url.SERVER_ABMP3), 2);
        assertEquals(12, abmp3.items.size());
        assertTrue(abmp3.hasMore);
        assertAllHaveNameAndUrl(abmp3, Url.SERVER_ABMP3);

        ProviderPage<BookPOJO> knigoblud = BooksModel.parseKnigoblud(
                fixture("knigoblud/listing-today-p1.html", Url.SERVER_KNIGOBLUD), 1);
        assertEquals(21, knigoblud.items.size());
        assertTrue(knigoblud.hasMore);
        assertAllHaveNameAndUrl(knigoblud, Url.SERVER_KNIGOBLUD);

        ProviderPage<BookPOJO> otrub = BooksModel.parseOtrub(fixture("otrub/listing-index-p1.html", Url.SERVER_OTRUB), 1);
        assertEquals(10, otrub.items.size());
        assertTrue(otrub.hasMore);
        assertAllHaveNameAndUrl(otrub, Url.SERVER_OTRUB);

        ProviderPage<BookPOJO> poleknig = BooksModel.parsePoleknig(
                fixture("poleknig/listing-novelties-p1.html", Url.SERVER_POLEKNIG), 1);
        assertEquals(12, poleknig.items.size());
        assertTrue(poleknig.hasMore);
        assertAllHaveNameAndUrl(poleknig, Url.SERVER_POLEKNIG);
    }

    @Test
    public void abmp3SearchIsOnePageSlicedLocally() throws IOException {
        Document doc = fixture("abmp3/search-1984.html", Url.SERVER_ABMP3);
        ProviderPage<BookPOJO> first = BooksModel.parseAbmp3Search(doc, 1);
        assertEquals(4, first.items.size());
        assertEquals("1984", first.items.get(0).getName());
        assertEquals("Джордж Оруэлл", first.items.get(0).getAutor());
        assertNotNull(first.items.get(0).getPhoto());
        assertFalse(first.hasMore);
        assertEquals(ProviderPage.Status.EMPTY, BooksModel.parseAbmp3Search(doc, 2).status);
    }

    @Test
    public void emptyAndMalformedPagesAreEmptyNotErrors() {
        Document blank = Jsoup.parse("<html><body><div id='books_list'></div></body></html>", Url.SERVER);
        ProviderPage<BookPOJO> page = BooksModel.parseKnigavuhe(blank, Url.INDEX + "1", 1);
        assertEquals(ProviderPage.Status.EMPTY, page.status);
        assertFalse(page.hasMore);
        // one card without a title is skipped, the other survives
        Document mixed = Jsoup.parse("<div id='books_list'>"
                + "<div class='bookkitem'><div class='bookkitem_name'><a href='/book/1/'></a></div></div>"
                + "<div class='bookkitem'><div class='bookkitem_name'><a href='/book/2/'>Two</a></div></div>"
                + "</div>", Url.SERVER);
        ProviderPage<BookPOJO> survivors = BooksModel.parseKnigavuhe(mixed, Url.INDEX + "1", 1);
        assertEquals(1, survivors.items.size());
        assertEquals("Two", survivors.items.get(0).getName());
    }

    @Test
    public void pageWhoseCardsWereAllFilteredKeepsTheNextLink() {
        // knigavuhe page 1 with only LitRes cards and a pager to page 2: nothing to show, but not the end
        Document litresOnly = Jsoup.parse("<div class='pn_page_buttons'><a href='/new/1/'>1</a><a href='/new/2/'>2</a></div>"
                + "<div id='books_list'>"
                + "<div class='bookkitem'><span class='bookkitem_litres_icon'></span>"
                + "<div class='bookkitem_name'><a href='/book/1/'>Paid</a></div></div>"
                + "</div>", Url.SERVER);
        ProviderPage<BookPOJO> page = BooksModel.parseKnigavuhe(litresOnly, Url.INDEX + "1", 1);
        assertTrue(page.items.isEmpty());
        assertTrue(page.hasMore);
        assertEquals(ProviderPage.Status.OK, page.status);
    }

    @Test
    public void audiobooChallengePageYieldsNoSentinelRows() throws IOException {
        // SiteHttp turns this page into a CookesExeption (BLOCKED) before any parser sees it;
        // should it ever get through, it must still be an empty page and never a fake book
        Document doc = fixture("audioboo/listing-challenge-p1.html", Url.SERVER_AUDIOBOO);
        assertTrue(doc.title().contains("Just a moment"));
        ProviderPage<BookPOJO> page = BooksModel.parseAudioboo(doc, 1);
        assertEquals(ProviderPage.Status.EMPTY, page.status);
        assertTrue(page.items.isEmpty());
        assertFalse(page.hasMore);
    }
}
