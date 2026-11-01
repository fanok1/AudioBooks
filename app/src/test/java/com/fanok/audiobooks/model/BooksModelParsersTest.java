package com.fanok.audiobooks.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.fanok.audiobooks.source.Akniga;
import com.fanok.audiobooks.source.BazaKnig;
import com.fanok.audiobooks.source.Abmp3;
import com.fanok.audiobooks.source.Audioboo;
import com.fanok.audiobooks.source.Izibuk;
import com.fanok.audiobooks.source.Knigavuhe;
import com.fanok.audiobooks.Url;
import com.fanok.audiobooks.pojo.BookPOJO;
import com.fanok.audiobooks.search.ListingFilters;
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
        ProviderPage<BookPOJO> page = Knigavuhe.parseListing(
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
        ProviderPage<BookPOJO> page = Knigavuhe.parseListing(fixture("knigavuhe/search-1984-p1.html", url), url, 1);
        assertEquals("5 cards, 2 of them LitRes-only", 3, page.items.size());
        assertEquals("1984", page.items.get(0).getName());
        assertFalse(page.hasMore);
    }

    @Test
    public void aknigaIndexPageOneStripsAuthorFromTitle() throws IOException {
        ProviderPage<BookPOJO> page = Akniga.parseListing(fixture("akniga/listing-index-p1.html", Url.SERVER_AKNIGA), 1);
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
        ProviderPage<BookPOJO> p1 = Akniga.parseListing(fixture("akniga/author-king-p1.html", base + "1/"), 1);
        ProviderPage<BookPOJO> p2 = Akniga.parseListing(fixture("akniga/author-king-p2.html", base + "2/"), 2);
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
        ProviderPage<BookPOJO> first = Akniga.parseListing(fixture("akniga/performer-pankov-p1.html", base + "1/"), 1);
        ProviderPage<BookPOJO> last = Akniga.parseListing(fixture("akniga/performer-pankov-p12.html", base + "12/"), 12);
        assertEquals(12, first.items.size());
        assertTrue(first.hasMore);
        assertEquals(11, last.items.size());
        assertFalse(last.hasMore);
    }

    @Test
    public void audiobooIndexGenreAndSearchPages() throws IOException {
        ProviderPage<BookPOJO> index = Audioboo.parseListing(
                fixture("audioboo/listing-index-p1.html", Url.SERVER_AUDIOBOO + "/"), 1);
        assertEquals(27, index.items.size());
        assertTrue(index.hasMore);
        assertAllHaveNameAndUrl(index, Url.SERVER_AUDIOBOO);
        ProviderPage<BookPOJO> genre = Audioboo.parseListing(
                fixture("audioboo/listing-genre-p2.html", Url.SERVER_AUDIOBOO + "/didiktiva/page/2/"), 2);
        assertEquals(27, genre.items.size());
        assertTrue(genre.hasMore);
        ProviderPage<BookPOJO> search = Audioboo.parseSearchResults(
                fixture("audioboo/search-master-p2.html", Url.SERVER_AUDIOBOO + "/index.php"), 2);
        assertEquals(50, search.items.size());
        assertTrue("its pager only links back to page 1", search.hasMore);
    }

    @Test
    public void aknigaSearchPageHasNextButton() throws IOException {
        ProviderPage<BookPOJO> page = Akniga.parseListing(fixture("akniga/search-1984-p1.html", Url.SERVER_AKNIGA), 1);
        assertEquals(12, page.items.size());
        assertTrue(page.hasMore);
    }

    @Test
    public void bazaKnigPageTwoOfTheIndex() throws IOException {
        ProviderPage<BookPOJO> page = BazaKnig.parseListing(
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
        ProviderPage<BookPOJO> page = BazaKnig.parseListing(
                fixture("bazaknig/search-1984-p1.html", Url.SERVER_BAZA_KNIG), 1);
        assertEquals(4, page.items.size());
        assertEquals("Назад в СССР 1984", page.items.get(0).getName());
        assertEquals("Необыкновенный Икс. 1964 – 1984", page.items.get(3).getName());
        assertFalse(page.hasMore);
    }

    @Test
    public void izibukMobileGenrePageCarriesDurations() throws IOException {
        String url = "https://izib.uk/genre1?p=1/";
        ProviderPage<BookPOJO> page = Izibuk.parseListing(fixture("izibuk/listing-mobile-genre1-p1.html", url), url, 1);
        assertEquals(20, page.items.size());
        assertTrue("2239 pages", page.hasMore);
        assertAllHaveNameAndUrl(page, Url.SERVER_IZIBUK);
        BookPOJO first = page.items.get(0);
        assertEquals("Романов. Том 12", first.getName());
        assertEquals("Владимир Кощеев", first.getAutor());
        assertEquals("8 ч. 12 мин.", first.getTime());
        // mobile genre cards carry no genre: the breadcrumb names it
        assertEquals("Фантастика, фэнтези", first.getGenre());
        assertTrue(first.getDesc(), first.getDesc().startsWith("Балы, красавицы"));
        for (BookPOJO book : page.items) {
            assertTrue(book.getTime(), ListingFilters.durationMinutes(book.getTime()) > 0);
        }
    }

    @Test
    public void izibukMobileSeriesPageWithoutListContainerSkipsFragments() {
        String url = "https://izib.uk/serie703?p=1/";
        String card = "<div class=_ccb9b7><div class=_680f12> #%1$d </div>"
                + "<a class=_bce453 href=/art%1$d><img src=c.jpg></a><div class=_802db0><span></span>"
                + "<div class=_3dc935><a href=/art%1$d>Book %1$d</a>%2$s</div>"
                + "<div class=_eeab32><span class=_1d4de8></span><a href=/serie703>Мегрэ</a></div>"
                + "<div class=_eeab32><span class=_1f6354></span><span class=_08dd3a>4 ч. 2 мин.</span></div></div></div>";
        Document doc = Jsoup.parse("<div class=_6eb726> Серия «<b>Комиссар Мегрэ</b>» </div>"
                + String.format(card, 1, "") + String.format(card, 2, "<span class=_09ddb7>Ф</span>"), url);
        ProviderPage<BookPOJO> page = Izibuk.parseListing(doc, url, 1);
        assertEquals(1, page.items.size());
        assertEquals("Book 1", page.items.get(0).getName());
        assertEquals("4 ч. 2 мин.", page.items.get(0).getTime());
        assertEquals("Мегрэ", page.items.get(0).getSeries());
        assertFalse(page.hasMore);
    }

    @Test
    public void otherListingsParseAndDetectTheirPagers() throws IOException {
        ProviderPage<BookPOJO> izibuk = Izibuk.parseListing(
                fixture("izibuk/listing-index-p1.html", Url.INDEX_IZIBUK + "1"), Url.INDEX_IZIBUK + "1", 1);
        assertEquals(20, izibuk.items.size());
        assertTrue(izibuk.hasMore);
        assertAllHaveNameAndUrl(izibuk, Url.SERVER_IZIBUK);

        ProviderPage<BookPOJO> abmp3 = Abmp3.parseListing(fixture("abmp3/listing-index-p2.html", Url.SERVER_ABMP3), 2);
        assertEquals(12, abmp3.items.size());
        assertTrue(abmp3.hasMore);
        assertAllHaveNameAndUrl(abmp3, Url.SERVER_ABMP3);

        ProviderPage<BookPOJO> knigoblud = com.fanok.audiobooks.source.Knigoblud.parseListing(
                fixture("knigoblud/listing-today-p1.html", Url.SERVER_KNIGOBLUD), 1);
        assertEquals(21, knigoblud.items.size());
        assertTrue(knigoblud.hasMore);
        assertAllHaveNameAndUrl(knigoblud, Url.SERVER_KNIGOBLUD);

        ProviderPage<BookPOJO> otrub = com.fanok.audiobooks.source.Otrub.parseListing(fixture("otrub/listing-index-p1.html", Url.SERVER_OTRUB), 1);
        assertEquals(10, otrub.items.size());
        assertTrue(otrub.hasMore);
        assertAllHaveNameAndUrl(otrub, Url.SERVER_OTRUB);

        ProviderPage<BookPOJO> poleknig = com.fanok.audiobooks.source.Poleknig.parseListing(
                fixture("poleknig/listing-novelties-p1.html", Url.SERVER_POLEKNIG), 1);
        assertEquals(12, poleknig.items.size());
        // changed on purpose after the live check (2026-10-30): hasMore needs a .pagination a[rel=next] link and this novelties page has no pager (a one-page list)
        assertFalse(poleknig.hasMore);
        assertAllHaveNameAndUrl(poleknig, Url.SERVER_POLEKNIG);
    }

    // changed on purpose after the live check (2026-10-30): one-page lists (popular, novelties) have no pager, so hasMore needs a .pagination a[rel=next] link
    @Test
    public void poleknigListingHasMoreOnlyWithANextPageLink() {
        String card = "<div class='book-item'><a class='book-title' href='" + Url.SERVER_POLEKNIG + "/book/1'>One</a></div>";
        ProviderPage<BookPOJO> paged = com.fanok.audiobooks.source.Poleknig.parseListing(Jsoup.parse(card
                + "<ul class='pagination'><li><a href='/?p=1'>1</a></li><li><a rel='next' href='/?p=2'>2</a></li></ul>",
                Url.SERVER_POLEKNIG), 1);
        assertEquals(1, paged.items.size());
        assertTrue(paged.hasMore);
        // the last page links the previous one only
        ProviderPage<BookPOJO> last = com.fanok.audiobooks.source.Poleknig.parseListing(Jsoup.parse(card
                + "<ul class='pagination'><li><a rel='prev' href='/?p=1'>1</a></li></ul>", Url.SERVER_POLEKNIG), 2);
        assertEquals(1, last.items.size());
        assertFalse(last.hasMore);
        // a one-page list has no pager at all
        ProviderPage<BookPOJO> single = com.fanok.audiobooks.source.Poleknig.parseListing(
                Jsoup.parse(card, Url.SERVER_POLEKNIG), 1);
        assertEquals(1, single.items.size());
        assertFalse(single.hasMore);
        // a next link without a card on the page is not a reason to go on
        ProviderPage<BookPOJO> noCards = com.fanok.audiobooks.source.Poleknig.parseListing(Jsoup.parse(
                "<ul class='pagination'><li><a rel='next' href='/?p=2'>2</a></li></ul>", Url.SERVER_POLEKNIG), 1);
        assertTrue(noCards.items.isEmpty());
        assertFalse(noCards.hasMore);
    }

    // changed on purpose after the live check (2026-10-30): the site's series path is /series/ now (/serie/ redirects to it); both name the series from the page title
    @Test
    public void knigavuheSeriesListingTakesTheSeriesFromThePageTitle() {
        Document doc = Jsoup.parse("<div class='page_title'><h1>Цикл: <b>Тёмная башня</b></h1></div><div id='books_list'>"
                + "<div class='bookkitem'><div class='bookkitem_name'><a href='/book/1/'>One</a></div></div></div>",
                Url.SERVER);
        String series = Url.SERVER + "/series/123-temnaya-bashnya/?page=1";
        BookPOJO book = Knigavuhe.parseListing(doc, series, 1).items.get(0);
        assertEquals("Тёмная башня", book.getSeries());
        assertEquals(series, book.getUrlSeries());
        String legacy = Url.SERVER + "/serie/123-temnaya-bashnya/?page=1";
        BookPOJO legacyBook = Knigavuhe.parseListing(doc, legacy, 1).items.get(0);
        assertEquals("Тёмная башня", legacyBook.getSeries());
        assertEquals(legacy, legacyBook.getUrlSeries());
        // any other listing leaves the cards' series alone
        BookPOJO other = Knigavuhe.parseListing(doc, Url.INDEX + "1", 1).items.get(0);
        assertNull(other.getSeries());
        assertNull(other.getUrlSeries());
    }

    @Test
    public void abmp3SearchIsOnePageSlicedLocally() throws IOException {
        Document doc = fixture("abmp3/search-1984.html", Url.SERVER_ABMP3);
        ProviderPage<BookPOJO> first = Abmp3.parseSearch(doc, 1);
        assertEquals(4, first.items.size());
        assertEquals("1984", first.items.get(0).getName());
        assertEquals("Джордж Оруэлл", first.items.get(0).getAutor());
        assertNotNull(first.items.get(0).getPhoto());
        assertFalse(first.hasMore);
        assertEquals(ProviderPage.Status.EMPTY, Abmp3.parseSearch(doc, 2).status);
    }

    @Test
    public void emptyAndMalformedPagesAreEmptyNotErrors() {
        Document blank = Jsoup.parse("<html><body><div id='books_list'></div></body></html>", Url.SERVER);
        ProviderPage<BookPOJO> page = Knigavuhe.parseListing(blank, Url.INDEX + "1", 1);
        assertEquals(ProviderPage.Status.EMPTY, page.status);
        assertFalse(page.hasMore);
        // one card without a title is skipped, the other survives
        Document mixed = Jsoup.parse("<div id='books_list'>"
                + "<div class='bookkitem'><div class='bookkitem_name'><a href='/book/1/'></a></div></div>"
                + "<div class='bookkitem'><div class='bookkitem_name'><a href='/book/2/'>Two</a></div></div>"
                + "</div>", Url.SERVER);
        ProviderPage<BookPOJO> survivors = Knigavuhe.parseListing(mixed, Url.INDEX + "1", 1);
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
        ProviderPage<BookPOJO> page = Knigavuhe.parseListing(litresOnly, Url.INDEX + "1", 1);
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
        ProviderPage<BookPOJO> page = Audioboo.parseListing(doc, 1);
        assertEquals(ProviderPage.Status.EMPTY, page.status);
        assertTrue(page.items.isEmpty());
        assertFalse(page.hasMore);
    }
}
