package com.fanok.audiobooks.model;

import static com.fanok.audiobooks.BookPageFixtures.BOOK_PAGES;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.fanok.audiobooks.BookPageFixtures;
import com.fanok.audiobooks.CookesExeption;
import com.fanok.audiobooks.Url;
import com.fanok.audiobooks.net.SiteHttp;
import com.fanok.audiobooks.pojo.BookPOJO;
import com.fanok.audiobooks.pojo.DescriptionPOJO;
import com.fanok.audiobooks.search.Provider;
import io.reactivex.observers.TestObserver;
import io.reactivex.schedulers.Schedulers;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.Test;

public class BookDescriptionModelTest {

    private static final String KNIGAVUHE = BOOK_PAGES[0][1];
    private static final String BAZA = BOOK_PAGES[4][1];

    @Test
    public void everySourceFixtureYieldsTitleAuthorReaderCoverAndDescription() throws IOException {
        for (String[] page : BOOK_PAGES) {
            DescriptionPOJO d = BookDescriptionModel.parseDescription(
                    BookPageFixtures.load(page[0], page[1]), page[1]);
            String where = page[0] + ": ";
            assertFalse(where + "title", d.getTitle().isEmpty());
            assertNotNull(where + "author", d.getAutor());
            assertFalse(where + "reader", d.getArtist().isEmpty());
            assertTrue(where + "cover " + d.getPoster(), d.getPoster().startsWith("https://"));
            assertFalse(where + "description", d.getDescription().trim().isEmpty());
        }
    }

    @Test
    public void challengePageFromSiteHttpPropagatesAsCookesExeption() throws IOException {
        String challenge = BookPageFixtures.text("audioboo/challenge-description.html");
        String url = "https://audioboo.org/detektivy/1000-tihiy-dom.html";
        BookDescriptionModel model = new BookDescriptionModel(url,
                u -> SiteHttp.parse(BookPageFixtures.response(u, 403, "challenge", challenge)));

        model.getDescription().test().assertNoValues().assertError(CookesExeption.class);
        model.getBooks().test().assertNoValues().assertError(CookesExeption.class);
    }

    @Test
    public void descriptionAndBooksShareOneDownload() throws Exception {
        Document page = BookPageFixtures.load(BOOK_PAGES[0][0], KNIGAVUHE);
        List<String> fetched = new CopyOnWriteArrayList<>();
        BookDescriptionModel model = new BookDescriptionModel(KNIGAVUHE, u -> {
            fetched.add(u);
            Thread.sleep(200);
            return page;
        });

        TestObserver<DescriptionPOJO> description = model.getDescription().subscribeOn(Schedulers.io()).test();
        TestObserver<ArrayList<BookPOJO>> books = model.getBooks().subscribeOn(Schedulers.io()).test();

        assertTrue(description.await(5, TimeUnit.SECONDS));
        assertTrue(books.await(5, TimeUnit.SECONDS));
        description.assertValueCount(1).assertComplete();
        books.assertValueCount(1).assertComplete();
        assertEquals(1, fetched.size());
    }

    @Test
    public void failedDownloadIsNotCachedForTheNextLoad() throws IOException {
        Document page = BookPageFixtures.load(BOOK_PAGES[0][0], KNIGAVUHE);
        List<String> fetched = new ArrayList<>();
        BookDescriptionModel model = new BookDescriptionModel(KNIGAVUHE, u -> {
            fetched.add(u);
            if (fetched.size() == 1) {
                throw new IOException("timeout");
            }
            return page;
        });

        model.getDescription().test().assertError(IOException.class);
        model.getDescription().test().assertValueCount(1).assertComplete();
        assertEquals(2, fetched.size());
    }

    @Test
    public void emptyPageIsATypedErrorNotAnEmptyDescription() {
        Document empty = Jsoup.parse("<html><head><title>404</title></head><body></body></html>", KNIGAVUHE);
        new BookDescriptionModel(KNIGAVUHE, u -> empty).getDescription().test()
                .assertNoValues().assertError(e -> e instanceof IOException && e.getMessage().contains("no book"));
        for (String[] page : BOOK_PAGES) {
            try {
                BookDescriptionModel.parseDescription(Jsoup.parse("", page[1]), page[1]);
                fail(page[0] + ": empty page parsed");
            } catch (IOException expected) {
                // typed error
            }
        }
    }

    @Test
    public void missingChildElementsAreTolerated() throws IOException {
        String url = BOOK_PAGES[1][1];
        Document izibuk = Jsoup.parse("<span itemprop=name>Книга</span><div class=_5e0b77></div>"
                + "<div class=_7e215f></div><div class=_b264b2></div><div class=_c337c7></div>", url);
        DescriptionPOJO d = BookDescriptionModel.parseDescription(izibuk, url);
        assertEquals("Книга", d.getTitle());
        assertEquals("", d.getPoster());
        assertEquals("", d.getGenre());
        Document baza = Jsoup.parse("<h1>Книга без тире</h1><ul class='reset full-items'><li>Автор:</li>"
                + "<li>Читает:</li><li>Цикл:</li></ul><div class=full-img></div>", BAZA);
        assertEquals("Книга без тире", BookDescriptionModel.parseDescription(baza, BAZA).getTitle());
    }

    @Test
    public void unknownHostIsRejectedBeforeAnyDownload() {
        List<String> fetched = new ArrayList<>();
        BookDescriptionModel model = new BookDescriptionModel("https://example.com/book/1", u -> {
            fetched.add(u);
            return Jsoup.parse("");
        });
        model.getDescription().test().assertError(
                e -> e instanceof IllegalArgumentException && e.getMessage().startsWith("unsupported host"));
        model.getBooks().test().assertError(IllegalArgumentException.class);
        assertTrue(fetched.isEmpty());
    }

    @Test
    public void oldBazaKnigInkUrlIsFetchedAndParsedAsBazaKnig() throws IOException {
        String ink = BAZA.replace("baza-knig.top", "baza-knig.ink");
        Document page = BookPageFixtures.load(BOOK_PAGES[4][0], BAZA);
        List<String> fetched = new ArrayList<>();
        new BookDescriptionModel(ink, u -> {
            fetched.add(u);
            return page;
        }).getBooks().test().assertComplete();

        assertEquals(1, fetched.size());
        assertTrue(Url.isHost(fetched.get(0), Url.SERVER_BAZA_KNIG));
        assertEquals(BookDescriptionModel.parseDescription(page, BAZA).getTitle(),
                BookDescriptionModel.parseDescription(page, ink).getTitle());
    }

    @Test
    public void audiobooRatingIsTheStarBarNotTheVoteCount() throws IOException {
        String url = "https://audioboo.org/roman/40195-ross-yan-basad.html";
        DescriptionPOJO d = BookDescriptionModel.parseDescription(BookPageFixtures.load("audioboo/comments.html", url), url);
        assertEquals(BookPOJO.formatRating(5.0f, "288"), d.getReiting());
        // no votes: no rating rather than the vote count "0"
        String solo = BOOK_PAGES[6][1];
        assertEquals("", BookDescriptionModel.parseDescription(BookPageFixtures.load(BOOK_PAGES[6][0], solo), solo)
                .getReiting());
    }

    @Test
    public void aknigaAuthorAndReaderFiltersRouteToAkniga() throws IOException {
        String akniga = BOOK_PAGES[3][1];
        DescriptionPOJO d = BookDescriptionModel.parseDescription(
                BookPageFixtures.load(BOOK_PAGES[3][0], akniga), akniga);
        assertEquals("https://akniga.org/author/Грэхем Йан/page<page>/", d.getAutorUrl());
        assertEquals("https://akniga.org/performer/Ибраев Шад/page<page>/", d.getArtistUrl());
        // the listing is chosen by host: a URL this app cannot route opens an empty page
        assertEquals(Provider.AKNIGA, Provider.ofUrl(d.getAutorUrl().replace("<page>", "1")));
        assertEquals(Provider.AKNIGA, Provider.ofUrl(d.getArtistUrl().replace("<page>", "1")));
    }

    @Test
    public void linksAreAbsoluteOnTheBookHost() throws IOException {
        String akniga = BOOK_PAGES[3][1];
        DescriptionPOJO d = BookDescriptionModel.parseDescription(
                BookPageFixtures.load(BOOK_PAGES[3][0], akniga), akniga);
        assertTrue(d.getSeriesUrl(), d.getSeriesUrl().startsWith(Url.SERVER_AKNIGA + "/series/"));
        String audioboo = BOOK_PAGES[6][1];
        Document boo = BookPageFixtures.load(BOOK_PAGES[6][0], audioboo);
        DescriptionPOJO a = BookDescriptionModel.parseDescription(boo, audioboo);
        assertEquals("https://audioboo.org/xfsearch/cikl/%D0%A1%D0%BE%D0%BB%D0%BE/", a.getSeriesUrl());
        assertEquals("https://audioboo.org/xfsearch/avtora/%D0%9C%D0%B0%D1%85%D0%B0%D0%BD%D0%B5%D0%BD%D0%BA%D0%BE"
                + "%20%D0%92%D0%B0%D1%81%D0%B8%D0%BB%D0%B8%D0%B9/", a.getAutorUrl());
        // the related block lists the series, this book included: it is left out
        ArrayList<BookPOJO> books = BookDescriptionModel.parseBooks(boo, audioboo);
        assertEquals(5, books.size());
        assertEquals("https://audioboo.org/fantastika/107329-mahanenko-vasilij-solo-01.html", books.get(0).getUrl());
        assertEquals("Маханенко Василий - Соло 01", books.get(0).getName());
        assertTrue(books.get(0).getPhoto(), books.get(0).getPhoto().startsWith("https://audioboo.org/uploads/"));
    }
}
