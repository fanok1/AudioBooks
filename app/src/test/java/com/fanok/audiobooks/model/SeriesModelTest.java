package com.fanok.audiobooks.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.fanok.audiobooks.source.Akniga;
import com.fanok.audiobooks.source.BazaKnig;
import com.fanok.audiobooks.source.Audioboo;
import com.fanok.audiobooks.source.Izibuk;
import com.fanok.audiobooks.source.Knigavuhe;
import com.fanok.audiobooks.Url;
import com.fanok.audiobooks.pojo.SeriesPOJO;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.Test;

public class SeriesModelTest {

    /** A captured page from {@code src/test/resources/fixtures}, parsed as if fetched from {@code baseUri}. */
    static Document fixture(String path, String baseUri) throws IOException {
        try (InputStream in = SeriesModelTest.class.getResourceAsStream("/fixtures/" + path)) {
            if (in == null) {
                throw new FileNotFoundException(path);
            }
            return Jsoup.parse(in, "UTF-8", baseUri);
        }
    }

    /** Every entry has a name and links to an absolute https page on {@code server}. */
    private static void assertLinksOn(List<SeriesPOJO> series, String server) {
        for (SeriesPOJO s : series) {
            assertTrue(s.getUrl(), s.getUrl().startsWith("https://") && Url.isHost(s.getUrl(), server));
            assertFalse(s.getUrl(), s.getName().isEmpty());
        }
    }

    @Test
    public void knigavuheSeriesFromFixture() throws IOException {
        List<SeriesPOJO> series = Knigavuhe.parseSeries(
                fixture("knigavuhe/series.html", "https://knigavuhe.org/book/nochnojj-dozor-1/"));
        assertEquals(6, series.size());
        assertEquals("Ночной дозор", series.get(0).getName());
        assertEquals("", series.get(0).getUrl()); // the current book is listed without a link
        assertEquals("2.", series.get(1).getNumber());
        assertEquals("https://knigavuhe.org/book/dnevnojj-dozor/", series.get(1).getUrl());
        assertLinksOn(series.subList(1, 6), Url.SERVER);
    }

    @Test
    public void izibukSeriesFromFixture() throws IOException {
        List<SeriesPOJO> series = Izibuk.parseSeries(fixture("izibuk/series.html", "https://izib.uk/art1439"));
        assertEquals(10, series.size());
        assertEquals("", series.get(0).getUrl());
        assertEquals("https://izib.uk/art20481", series.get(1).getUrl());
        assertTrue(series.get(1).getName().startsWith("Дневной дозор"));
        assertLinksOn(series.subList(1, 10), Url.SERVER_IZIBUK);
    }

    @Test
    public void aknigaSeriesFromFixture() throws IOException {
        List<SeriesPOJO> series = Akniga.parseSeries(
                fixture("akniga/series.html", "https://akniga.org/sergey-lukyanenko-nochnoy-dozor-2011"));
        assertEquals(10, series.size()); // the "show all" toggle has no link and is not an entry
        assertEquals("1.", series.get(1).getNumber());
        assertEquals("Ночной дозор", series.get(1).getName());
        assertLinksOn(series, Url.SERVER_AKNIGA);
    }

    @Test
    public void bazaKnigSeriesFromFixtures() throws IOException {
        String series = BazaKnig.parseSeriesUrl(fixture("baza-knig/series-book.html",
                "https://baza-knig.top/fantastika-fentezii/31-lukyanenko-sergey-nochnoy-dozor.html"));
        assertEquals("https://baza-knig.top/xfsearch/cikl/%D0%B4%D0%BE%D0%B7%D0%BE%D1%80/", series);

        Document page = fixture("baza-knig/series.html", series);
        List<SeriesPOJO> books = BazaKnig.parseSeriesPage(page);
        assertEquals(10, books.size());
        assertEquals("1.", books.get(0).getNumber());
        assertTrue(books.get(0).getName(), books.get(0).getName().startsWith("Ночной Дозор Читает:"));
        assertTrue(books.get(0).getPhoto().startsWith("https://baza-knig.top/uploads/"));
        assertLinksOn(books, Url.SERVER_BAZA_KNIG);
        assertEquals(1, OtherSourceModel.lastPage(page));
    }

    @Test
    public void bazaKnigPagesAreBuiltFromTheCycleUrl() {
        String series = "https://baza-knig.top/xfsearch/cikl/%D0%B4%D0%BE%D0%B7%D0%BE%D1%80/";
        assertEquals(series, BazaKnig.seriesPageUrl(series, 1));
        assertEquals(series + "page/2/", BazaKnig.seriesPageUrl(series, 2));
        assertEquals("https://baza-knig.top/xfsearch/cikl/x/page/3/",
                BazaKnig.seriesPageUrl("https://baza-knig.top/xfsearch/cikl/x", 3));

        Document nav = Jsoup.parse("<div class='page-nav'><div class='navigation'><span>1</span>"
                + "<a href='/page/2/'>2</a><span class='nav_ext'>...</span><a href='/page/12/'>12</a>"
                + "<a href='/page/2/'>Далее</a></div></div>", series);
        assertEquals(12, OtherSourceModel.lastPage(nav)); // the text label no longer reaches parseInt
    }

    @Test
    public void knigobludSeriesLinksStayOnKnigoblud() throws IOException {
        List<SeriesPOJO> series = com.fanok.audiobooks.source.Knigoblud.parseSeries(fixture("knigoblud/series.html",
                "https://www.knigoblud.club/e87cc048-e59a-4b31-af9c-06abc64025cb"));
        assertEquals(11, series.size());
        assertEquals("Ночной Дозор", series.get(0).getName());
        assertEquals("https://www.knigoblud.club/727d7590-69b0-4899-ae02-fe814cf05a1f", series.get(1).getUrl());
        assertLinksOn(series, Url.SERVER_KNIGOBLUD);
        for (SeriesPOJO s : series) {
            assertFalse(s.getUrl(), Url.isHost(s.getUrl(), Url.SERVER));
        }
    }

    @Test
    public void audiobooLiveSeriesFromTheBookPage() throws IOException {
        String book = "https://audioboo.org/fantastika/123139-mahanenko-vasilij-solo-06.html";
        String series = Audioboo.parseSeriesUrl(fixture("audioboo/book-description.html", book));
        assertEquals("https://audioboo.org/xfsearch/cikl/%D0%A1%D0%BE%D0%BB%D0%BE/", series);
        List<SeriesPOJO> books = Audioboo.parseSeriesPage(fixture("audioboo/series-solo.html", series));
        assertEquals(6, books.size());
        assertLinksOn(books, Url.SERVER_AUDIOBOO);
    }

    @Test
    public void audiobooSeriesLinksStayOnAudioboo() throws IOException {
        String series = Audioboo.parseSeriesUrl(
                fixture("audioboo/series-book.html", "https://audioboo.org/fantastika/1001-nochnoj-dozor.html"));
        assertEquals("https://audioboo.org/xfsearch/cikl/%D0%94%D0%BE%D0%B7%D0%BE%D1%80/", series);

        List<SeriesPOJO> books = Audioboo.parseSeriesPage(fixture("audioboo/series.html", series));
        assertEquals(3, books.size()); // the card without a link is skipped
        assertEquals("https://audioboo.org/uploads/posts/1001.jpg", books.get(0).getPhoto()); // placeholder skipped
        assertLinksOn(books, Url.SERVER_AUDIOBOO);
        for (SeriesPOJO s : books) {
            assertFalse(s.getUrl(), Url.isHost(s.getUrl(), Url.SERVER_OTRUB));
        }
    }

    @Test
    public void otrubSeriesFromFixtures() throws IOException {
        String series = com.fanok.audiobooks.source.Html.href(
                fixture("otrub/series-book.html", "https://otrub.in/nochnojj-dozor-2.html"), "a[href*=/series/]");
        assertEquals("https://otrub.in/series/dozor.html", series);
        List<SeriesPOJO> books = com.fanok.audiobooks.source.Otrub.parseSeriesPage(fixture("otrub/series.html", series));
        assertEquals(10, books.size());
        assertFalse(books.get(0).getPhoto().isEmpty());
        assertLinksOn(books, Url.SERVER_OTRUB);
    }

    @Test
    public void poleknigSeriesFromFixtures() throws IOException {
        String series = com.fanok.audiobooks.source.Html.href(
                fixture("poleknig/series-book.html", "https://poleknig.com/books/130543"), "a[href*=/series/]");
        assertEquals("https://poleknig.com/series/705", series);
        List<SeriesPOJO> books = com.fanok.audiobooks.source.Poleknig.parseSeriesPage(fixture("poleknig/series.html", series));
        assertEquals(9, books.size());
        assertEquals("Ночной дозор", books.get(0).getName());
        assertLinksOn(books, Url.SERVER_POLEKNIG);
    }

    @Test
    public void unknownHostIsRejected() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> SeriesModel.loadSeries("https://example.com/book/1"));
        assertEquals("unsupported host: example.com", e.getMessage());
        new SeriesModel().getSeries("https://example.com/book/1").test()
                .assertError(IllegalArgumentException.class).assertNotComplete(); // no onComplete after onError
    }
}
