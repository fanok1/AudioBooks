package com.fanok.audiobooks.model;

import static com.fanok.audiobooks.model.SeriesModelTest.fixture;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.fanok.audiobooks.source.Akniga;
import com.fanok.audiobooks.source.BazaKnig;
import com.fanok.audiobooks.source.Abmp3;
import com.fanok.audiobooks.source.Audioboo;
import com.fanok.audiobooks.source.Izibuk;
import com.fanok.audiobooks.source.Knigavuhe;
import com.fanok.audiobooks.Url;
import com.fanok.audiobooks.model.OtherSourceModel.Hit;
import com.fanok.audiobooks.pojo.BookPOJO;
import com.fanok.audiobooks.pojo.OtherArtistPOJO;
import java.io.IOException;
import java.util.List;
import org.jsoup.Jsoup;
import org.junit.Test;

public class OtherArtistModelTest {

    private static BookPOJO book(String url) {
        BookPOJO book = new BookPOJO();
        book.setUrl(url);
        book.setName("Ночной дозор");
        return book;
    }

    @Test
    public void knigavuheOtherReadersFromFixture() throws IOException {
        List<OtherArtistPOJO> readers = Knigavuhe.parseOtherReaders(fixture(
                "knigavuhe/other-readers.html", "https://knigavuhe.org/book/177-master-i-margarita/"));
        assertEquals(8, readers.size());
        assertEquals("https://knigavuhe.org/book/285-master-i-margarita-1/", readers.get(0).getUrl());
        assertEquals("В исполнении Киры Серовой", readers.get(0).getName());
        for (OtherArtistPOJO reader : readers) {
            assertTrue(reader.getUrl(), Url.isHost(reader.getUrl(), Url.SERVER));
        }
    }

    @Test
    public void knigavuheBlockWithoutTitleOrItemWithoutLinkIsNoNpe() {
        List<OtherArtistPOJO> readers = Knigavuhe.parseOtherReaders(Jsoup.parse(
                "<div class='book_serie_block'><div class='book_serie_block_item'><a href='/book/x/'>X</a></div></div>"
                        + "<div class='book_serie_block'><div class='book_serie_block_title'>Другие озвучки</div>"
                        + "<div class='book_serie_block_item'>без ссылки</div>"
                        + "<div class='book_serie_block_item'><a href='/book/y/'>Y</a> <span>в исполнении</span>"
                        + " <a href='/reader/z/'>Z</a></div></div>",
                "https://knigavuhe.org/book/1/"));
        assertEquals(1, readers.size());
        assertEquals("https://knigavuhe.org/book/y/", readers.get(0).getUrl());
        assertEquals("В исполнении Z", readers.get(0).getName());
    }

    @Test
    public void izibukListsOnlyTheSameBookOncePerPage() throws IOException {
        List<Hit> hits = Izibuk.parseSearch(
                fixture("izibuk/other-source-search.html", "https://izib.uk/search?q=x"));
        List<OtherArtistPOJO> readers = OtherArtistModel.otherReaders(hits, "Ночной дозор", "Сергей Лукьяненко",
                "https://izib.uk/art9999", "Другой Чтец");
        assertEquals(1, readers.size()); // Сара Уотерс' book with the same title is not another reading
        assertEquals("https://izib.uk/art1439", readers.get(0).getUrl());
        assertEquals("Исполнитель Александр Кокшаров", readers.get(0).getName());
        assertTrue(OtherArtistModel.otherReaders(hits, "Ночной дозор", "Сергей Лукьяненко", "https://izib.uk/art1439",
                "Другой Чтец").isEmpty()); // the current page is not "another" reading
    }

    @Test
    public void abmp3OtherReadersFromFixture() throws IOException {
        List<Hit> hits = Abmp3.parseSearchHits(
                fixture("abmp3/other-source-search.html", "https://audiobook-mp3.com/search?text=x"));
        List<OtherArtistPOJO> readers = OtherArtistModel.otherReaders(hits, "Дневной дозор", "Сергей Лукьяненко",
                "https://audiobook-mp3.com/audio-7938-dnevnojj-dozor-1", "Сергей Натапов");
        assertEquals(1, readers.size());
        assertEquals("https://audiobook-mp3.com/audio-12931-dnevnojj-dozor", readers.get(0).getUrl());
        assertEquals("Исполнитель Наталья Грачёва", readers.get(0).getName());
    }

    @Test
    public void aknigaOtherReadersFromFixture() throws IOException {
        List<Hit> hits = Akniga.parseSearch(
                fixture("akniga/other-source-search.html", "https://akniga.org/search/books?q=x"));
        List<OtherArtistPOJO> readers = OtherArtistModel.otherReaders(hits, "Ночной дозор", "Лукьяненко Сергей",
                "https://akniga.org/another-reading", "Другой Чтец");
        assertEquals(1, readers.size());
        assertEquals("https://akniga.org/sergey-lukyanenko-nochnoy-dozor-2011", readers.get(0).getUrl());
        assertEquals("Исполнитель Кокшаров Александр", readers.get(0).getName());
    }

    @Test
    public void bazaKnigOtherReadersFromFixture() throws IOException {
        List<Hit> hits = BazaKnig.parseSearch(fixture("baza-knig/other-source-search.html",
                "https://baza-knig.top/index.php?do=search"));
        List<OtherArtistPOJO> readers = OtherArtistModel.otherReaders(hits, "Ночной дозор", "Лукьяненко Сергей",
                "https://baza-knig.top/fantastika-fentezii/31-lukyanenko-sergey-nochnoy-dozor.html",
                "Кокшаров Александр");
        assertEquals(2, readers.size());
        for (OtherArtistPOJO reader : readers) {
            assertTrue(reader.getUrl(), Url.isHost(reader.getUrl(), Url.SERVER_BAZA_KNIG));
            assertTrue(reader.getUrl(), reader.getUrl().contains("nochnoj-dozor-sergej-lukjanenko"));
        }
    }

    @Test
    public void bazaKnigAlternativeVoiceOversOpenAsSourceTabs() {
        List<OtherArtistPOJO> readers = BazaKnig.parseAltReaders(Jsoup.parse(
                "<section id='content-tab1'>Озвучивает: <a href='/xfsearch/ispolnitel/a/'>Кокшаров Александр</a></section>"
                        + "<section id='content-tab2'>Озвучивает: <a href='/xfsearch/ispolnitel/b/'>Иван Петров</a></section>"
                        + "<section id='content-tab3'>Озвучивает: Анна Смирнова</section>"
                        + "<section id='content-tab4'>Озвучивает: <a href='/xfsearch/ispolnitel/a/'>Кокшаров Александр</a></section>",
                "https://baza-knig.top/a/31-x.html"), "https://baza-knig.top/a/31-x.html", "Кокшаров Александр");
        assertEquals(2, readers.size());
        assertEquals("https://baza-knig.top/a/31-x.html?sorce=2", readers.get(0).getUrl());
        assertEquals("Иван Петров", readers.get(0).getName());
        assertEquals("https://baza-knig.top/a/31-x.html?sorce=3", readers.get(1).getUrl());
        assertEquals("Анна Смирнова", readers.get(1).getName());
    }

    @Test
    public void knigobludOtherReadersFromFixture() throws IOException {
        List<Hit> hits = com.fanok.audiobooks.source.Knigoblud.parseSearch(
                fixture("knigoblud/other-source-search.html", "https://www.knigoblud.club/search?q=x"));
        List<OtherArtistPOJO> readers = OtherArtistModel.otherReaders(hits, "Ночной дозор", "Сара Уотерс",
                "https://www.knigoblud.club/another", "Другой Чтец");
        assertEquals(1, readers.size());
        assertEquals("https://www.knigoblud.club/ad147025-443b-4b14-84c0-bed9ed36d99d", readers.get(0).getUrl());
    }

    @Test
    public void audiobooOtherReadersFromTheLiveSearch() throws IOException {
        List<Hit> hits = Audioboo.parseSearch(
                fixture("audioboo/other-source-search.html", "https://audioboo.org/index.php?do=search"));
        List<OtherArtistPOJO> readers = OtherArtistModel.otherReaders(hits, "Дозор 01. Ночной Дозор",
                "Сергей Лукьяненко", "https://audioboo.org/fenezi/110294-lukjanenko-sergej-dozor-01-nochnoj-dozor.html",
                "Иван Солонинкин");
        assertEquals(2, readers.size());
        assertEquals("https://audioboo.org/fenezi/63305-lukjanenko-sergej-dozor-01-nochnoj-dozor.html",
                readers.get(0).getUrl());
        assertEquals("Исполнитель Кокшаров Александр", readers.get(0).getName());
        assertEquals("https://audioboo.org/fenezi/63296-lukjanenko-sergej-dozory-01-nochnoj-dozor.html",
                readers.get(1).getUrl());
    }

    @Test
    public void missingAuthorAndReaderAreNotCompared() throws IOException {
        List<Hit> hits = Audioboo.parseSearch(
                fixture("audioboo/other-source-search.html", "https://audioboo.org/index.php?do=search"));
        assertEquals(3, OtherArtistModel.otherReaders(hits, "Дозор 01. Ночной Дозор", null, null, null).size());
    }

    @Test
    public void unsupportedSourcesGiveAnEmptyListWithoutError() {
        for (String url : new String[]{"https://otrub.in/nochnojj-dozor-2.html", "https://poleknig.com/books/130543"}) {
            assertEquals(OtherArtistModel.Support.UNSUPPORTED, OtherArtistModel.support(url));
            new OtherArtistModel().getOtherArtist(book(url)).test()
                    .assertValue(List::isEmpty).assertNoErrors().assertComplete();
        }
        assertEquals(OtherArtistModel.Support.SUPPORTED, OtherArtistModel.support("https://audioboo.org/x.html"));
    }

    @Test
    public void unknownHostIsAnErrorNotAnEmptyTab() {
        new OtherArtistModel().getOtherArtist(book("https://example.com/book/1")).test()
                .assertError(IllegalArgumentException.class).assertNoValues().assertNotComplete();
    }
}
