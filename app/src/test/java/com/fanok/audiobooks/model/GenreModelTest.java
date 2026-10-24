package com.fanok.audiobooks.model;

import static com.fanok.audiobooks.model.SeriesModelTest.fixture;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.fanok.audiobooks.Url;
import com.fanok.audiobooks.pojo.GenrePOJO;
import com.fanok.audiobooks.search.ProviderPage;
import java.io.IOException;
import org.junit.Test;

public class GenreModelTest {

    @Test
    public void audiobooGenresComeFromTheNavigationMenuWithTheSiteUrls() throws IOException {
        ProviderPage<GenrePOJO> page = GenreModel.parseAudiobooGenres(
                fixture("audioboo/listing-index-p1.html", Url.SERVER_AUDIOBOO + "/"));
        assertEquals(50, page.items.size());
        assertFalse(page.hasMore);
        GenrePOJO first = page.items.get(0);
        assertEquals("Альтернативная история", first.getName());
        assertEquals("https://audioboo.org/altist/", first.getUrl());
        assertEquals("953", first.getDescription());
        boolean mystic = false;
        for (GenrePOJO genre : page.items) {
            assertTrue(genre.getUrl(), Url.isHost(genre.getUrl(), Url.SERVER_AUDIOBOO));
            if (genre.getName().equals("Мистика")) {
                // the slug the site uses, not a guessed one ("mictika" is a 404)
                assertEquals("https://audioboo.org/mistic/", genre.getUrl());
                mystic = true;
            }
        }
        assertTrue(mystic);
    }

    @Test
    public void audiobooAuthorsAndReadersAreThoseOfTheListingCards() throws IOException {
        org.jsoup.nodes.Document index = fixture("audioboo/listing-index-p1.html", Url.SERVER_AUDIOBOO + "/");
        ProviderPage<GenrePOJO> authors = AutorsModel.parseAudiobooPeople(index, false, 1);
        ProviderPage<GenrePOJO> readers = AutorsModel.parseAudiobooPeople(index, true, 1);
        assertTrue(authors.items.size() > 10);
        assertTrue(readers.items.size() > 10);
        assertTrue(authors.hasMore);
        java.util.Set<String> names = new java.util.HashSet<>();
        for (GenrePOJO author : authors.items) {
            assertTrue(author.getName(), names.add(author.getName()));
            assertTrue(author.getUrl(), author.getUrl().startsWith(Url.SERVER_AUDIOBOO + "/xfsearch/avtora/"));
        }
        for (GenrePOJO reader : readers.items) {
            assertTrue(reader.getUrl(), reader.getUrl().startsWith(Url.SERVER_AUDIOBOO + "/tags/"));
        }
    }
}
