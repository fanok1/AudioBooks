package com.fanok.audiobooks.model;

import static com.fanok.audiobooks.model.SeriesModelTest.fixture;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.fanok.audiobooks.model.OtherSourceModel.Hit;
import com.fanok.audiobooks.search.Provider;
import com.fanok.audiobooks.source.Abmp3;
import com.fanok.audiobooks.pojo.BookPOJO;
import com.fanok.audiobooks.pojo.OtherArtistPOJO;
import io.reactivex.Single;
import io.reactivex.observers.TestObserver;
import io.reactivex.schedulers.TestScheduler;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import okhttp3.HttpUrl;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.Test;

public class OtherSourceModelTest {

    private static BookPOJO book(String name, String author, String reader) {
        BookPOJO book = new BookPOJO();
        book.setUrl("https://knigavuhe.org/book/1/");
        book.setName(name);
        book.setAutor(author);
        book.setArtist(reader);
        return book;
    }

    private static Document search(Provider site) throws IOException {
        String dir = site == Provider.BAZA_KNIG ? "baza-knig" : site.name().toLowerCase(java.util.Locale.ROOT);
        return fixture(dir + "/other-source-search.html", site.server + "/search");
    }

    @Test
    public void missingAuthorIsLeftOutOfEveryQuery() {
        BookPOJO book = book("Ночной дозор", null, "Александр Кокшаров");
        for (Provider site : Provider.values()) {
            String url = OtherSourceModel.searchUrl(site, OtherSourceModel.terms(site, book), 1);
            assertFalse(url, url.toLowerCase(java.util.Locale.ROOT).contains("null"));
            assertTrue(url, url.startsWith(site.server));
        }
        String akniga = OtherSourceModel.searchUrl(Provider.AKNIGA, OtherSourceModel.terms(Provider.AKNIGA, book), 1);
        assertEquals("Ночной дозор Александр Кокшаров", HttpUrl.get(akniga).queryParameter("q"));
    }

    @Test
    public void titleIsEncodedAsOneQueryParameter() {
        String title = "Тени & свет #1 + ?";
        String url = OtherSourceModel.searchUrl(Provider.KNIGAVUHE, OtherSourceModel.query(title, " ", null), 1);
        assertTrue(url, url.startsWith("https://knigavuhe.org/search/?q="));
        assertEquals(title, HttpUrl.get(url).queryParameter("q"));
        String baza = OtherSourceModel.searchUrl(Provider.BAZA_KNIG, "x", 2);
        assertEquals("2", HttpUrl.get(baza).queryParameter("search_start"));
        assertEquals("https://poleknig.com/?q=x", OtherSourceModel.searchUrl(Provider.POLEKNIG, "x", 1));
    }

    @Test
    public void everySearchFixtureFindsTheSameBook() throws IOException {
        BookPOJO withReader = book("Ночной дозор", "Сергей Лукьяненко", "Александр Кокшаров");
        String[][] expected = {
                {"AKNIGA", "https://akniga.org/sergey-lukyanenko-nochnoy-dozor-2011"},
                {"IZIBUK", "https://izib.uk/art1439"},
                {"ABMP3", "https://audiobook-mp3.com/audio-3926-nochnojj-dozor-1"},
                {"BAZA_KNIG", "https://baza-knig.top/fantastika-fentezii/31-lukyanenko-sergey-nochnoy-dozor.html"},
                {"POLEKNIG", "https://poleknig.com/books/130543"},
        };
        for (String[] row : expected) {
            Provider site = Provider.valueOf(row[0]);
            OtherArtistPOJO found = OtherSourceModel.pickSameBook(site, OtherSourceModel.parseSearch(site, search(site)),
                    withReader);
            assertEquals(row[0], row[1], found == null ? null : found.getUrl());
            assertTrue(found.getName(), found.getName().equalsIgnoreCase(OtherSourceModel.label(site)));
        }
        // knigavuhe lists this reading only as a LitRes fragment, which is skipped
        assertNull(OtherSourceModel.pickSameBook(Provider.KNIGAVUHE,
                OtherSourceModel.parseSearch(Provider.KNIGAVUHE, search(Provider.KNIGAVUHE)), withReader));
        // knigoblud and otrub list this book without a reader: no match while we know ours, a match when we do not
        for (Provider site : new Provider[]{Provider.KNIGOBLUD, Provider.OTRUB}) {
            List<Hit> hits = OtherSourceModel.parseSearch(site, search(site));
            assertNull(site.name(), OtherSourceModel.pickSameBook(site, hits, withReader));
            OtherArtistPOJO found = OtherSourceModel.pickSameBook(site, hits,
                    book("Ночной дозор", "Сергей Лукьяненко", null));
            assertTrue(site.name(), found != null && found.getUrl().startsWith(site.server));
        }
    }

    @Test
    public void audiobooSearchCardsGiveTitleWithoutAuthorAndTheReader() throws IOException {
        List<Hit> hits = OtherSourceModel.parseSearch(Provider.AUDIOBOO, search(Provider.AUDIOBOO));
        assertEquals(6, hits.size());
        // the site numbers cycles in its titles, so Lukyanenko's book is found under the site's own title
        assertEquals("https://audioboo.org/fenezi/63305-lukjanenko-sergej-dozor-01-nochnoj-dozor.html",
                OtherSourceModel.pickSameBook(Provider.AUDIOBOO, hits,
                        book("Дозор 01. Ночной Дозор", "Сергей Лукьяненко", "Александр Кокшаров")).getUrl());
        assertEquals("https://audioboo.org/roman/10385-uoters-sara-nochnoy-dozor.html",
                OtherSourceModel.pickSameBook(Provider.AUDIOBOO, hits, book("Ночной дозор", "Сара Уотерс", null)).getUrl());
    }

    @Test
    public void missingAuthorMatchesOnTitleAndReaderWithoutNpe() throws IOException {
        OtherArtistPOJO found = OtherSourceModel.pickSameBook(Provider.IZIBUK,
                OtherSourceModel.parseSearch(Provider.IZIBUK, search(Provider.IZIBUK)),
                book("Ночной дозор", null, "Александр Кокшаров"));
        assertEquals("https://izib.uk/art1439", found.getUrl());
    }

    @Test
    public void abmp3TitleWithoutParenthesesDoesNotThrow() {
        List<Hit> hits = Abmp3.parseSearchHits(Jsoup.parse("<div class='b-statictop-search'>"
                + "<h2 class='b-statictop__title'>Поиск в книгах:</h2><ul class='b-statictop__items'>"
                + "<li><a href='/audio-1-x'>Книга без скобок</a></li><li>нет ссылки</li></ul></div>",
                "https://audiobook-mp3.com/search"));
        assertEquals(1, hits.size());
        assertEquals("Книга без скобок", hits.get(0).title);
        assertEquals("", hits.get(0).author);
        assertEquals("https://audiobook-mp3.com/audio-1-x", hits.get(0).url);
    }

    @Test
    public void oneBadItemIsSkippedNotFatal() {
        Document doc = Jsoup.parse("<ul><li>a</li><li>boom</li><li>c</li></ul>");
        List<String> items = com.fanok.audiobooks.source.Html.each(doc.select("li"), li -> {
            if (li.text().equals("boom")) {
                throw new IndexOutOfBoundsException("child(3)");
            }
            return li.text();
        });
        assertEquals(Arrays.asList("a", "c"), items);
    }

    @Test
    public void sitesAreSelectedByHost() {
        assertEquals(Provider.KNIGOBLUD, Provider.require("https://www.knigoblud.club/e87cc048"));
        assertEquals(Provider.KNIGAVUHE, Provider.require("https://knigavuhe.org/book/x/"));
        assertEquals("baza-knig.top", OtherSourceModel.label(Provider.BAZA_KNIG));
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> Provider.require("https://knigavuhe.org.example.com/book/x/"));
        assertEquals("unsupported host: knigavuhe.org.example.com", e.getMessage());
    }

    private static ArrayList<OtherArtistPOJO> found(String url) {
        OtherArtistPOJO pojo = new OtherArtistPOJO();
        pojo.setName("site");
        pojo.setUrl(url);
        return new ArrayList<>(Collections.singletonList(pojo));
    }

    @Test
    public void slowSourceTimesOutAtItsLimitWhileTheOthersAreDelivered() {
        TestScheduler time = new TestScheduler();
        List<Single<ArrayList<OtherArtistPOJO>>> lookups = Arrays.asList(
                Single.just(found("https://akniga.org/a")),
                Single.timer(60, TimeUnit.SECONDS, time).map(t -> found("https://slow.example.com/")),
                Single.error(new IOException("connection reset")),
                Single.timer(5, TimeUnit.SECONDS, time).map(t -> found("https://izib.uk/b")),
                // a site that declares slow pages has its own, longer limit
                Single.timer(12, TimeUnit.SECONDS, time).map(t -> found("https://audioknigi.fun/c")));

        TestObserver<ArrayList<OtherArtistPOJO>> observer =
                OtherSourceModel.searchAll(lookups, Arrays.asList(10L, 10L, 10L, 10L, 30L), time).test();
        time.advanceTimeBy(11, TimeUnit.SECONDS);
        observer.assertNoValues().assertNotComplete();
        time.advanceTimeBy(1, TimeUnit.SECONDS);
        observer.assertComplete().assertNoErrors().assertValueCount(1);
        List<OtherArtistPOJO> all = observer.values().get(0);
        assertEquals(3, all.size());
        assertEquals("https://akniga.org/a", all.get(0).getUrl());
        assertEquals("https://izib.uk/b", all.get(1).getUrl());
        assertEquals("https://audioknigi.fun/c", all.get(2).getUrl());
    }

    @Test
    public void bookWithoutNameIsNotLookedUp() {
        BookPOJO book = new BookPOJO();
        book.setUrl("https://knigavuhe.org/book/1/");
        new OtherSourceModel().getOtherArtist(book).test().assertValue(List::isEmpty).assertComplete();
    }
}
