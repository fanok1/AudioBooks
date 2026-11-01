package com.fanok.audiobooks.model;

import static com.fanok.audiobooks.model.SeriesModelTest.fixture;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.fanok.audiobooks.Url;
import com.fanok.audiobooks.pojo.GenrePOJO;
import com.fanok.audiobooks.search.Provider;
import com.fanok.audiobooks.search.ProviderPage;
import com.fanok.audiobooks.source.Audioboo;
import com.fanok.audiobooks.source.Knigavuhe;
import java.io.IOException;
import org.junit.Test;

public class GenreModelTest {

    @Test
    public void audiobooGenresComeFromTheNavigationMenuWithTheSiteUrls() throws IOException {
        ProviderPage<GenrePOJO> page = Audioboo.parseGenres(
                fixture("audioboo/listing-index-p1.html", Url.SERVER_AUDIOBOO + "/"));
        assertEquals(50, page.items.size());
        assertFalse(page.hasMore);
        GenrePOJO first = page.items.get(0);
        assertEquals("Альтернативная история", first.getName());
        assertEquals("https://audioboo.org/altist/", first.getUrl());
        assertEquals("953", first.getDescription());
        assertEquals("the genre list sorts by it", 953, first.getReting());
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
    public void audiobooAuthorsComeFromTheLetterIndexAndReadersFromTheTagCloud() throws IOException {
        // every listing page links the letters of the authors' index
        assertEquals(29, fixture("audioboo/listing-index-p1.html", Url.SERVER_AUDIOBOO + "/")
                .select("a.alfavit[href]").size());
        // the shapes of /a30-aa.html and /tags/, cut down
        java.util.List<GenrePOJO> authors = Audioboo.parseAuthors(org.jsoup.Jsoup.parse(
                "<div id=\"dle-content\"><h1>Все авторы на букву А</h1><table>"
                + "<tr><td><span class=\"fas fa-user\"></span>&ensp;Автор:&ensp;<a href='/xfsearch/avtora/А Фонд'>А Фонд</a>"
                + "</td><td><span class=\"fas fa-book\"></span>&ensp;Кол-во книг в базе:&ensp;"
                + "<a href='/xfsearch/А Фонд'>5</a></td></tr>"
                + "<tr><td>&ensp;Автор:&ensp;<a href='/xfsearch/avtora/Абдуллаев Чингиз'>Абдуллаев Чингиз</a></td>"
                + "<td>&ensp;Кол-во книг в базе:&ensp;<a href='/xfsearch/Абдуллаев Чингиз'>117</a></td></tr>"
                + "</table></div>", Url.SERVER_AUDIOBOO + "/a30-aa.html"));
        assertEquals(2, authors.size());
        assertEquals("А Фонд", authors.get(0).getName());
        assertEquals("as a card links it", Url.SERVER_AUDIOBOO + "/xfsearch/avtora/%D0%90%20%D0%A4%D0%BE%D0%BD%D0%B4/",
                authors.get(0).getUrl());
        assertEquals(5, authors.get(0).getReting());
        assertEquals(117, authors.get(1).getReting());
        ProviderPage<GenrePOJO> readers = Audioboo.parseReaders(org.jsoup.Jsoup.parse(
                "<div class=\"clearfix cloud-tags\"><h3>A</h3><span class=\"clouds_xsmall\">"
                + "<a href=\"https://audioboo.org/tags/A.Tim/\" title=\"Найдено публикаций: 12\">A.Tim</a></span>, "
                + "<span class=\"clouds_medium\"><a href=\"https://audioboo.org/tags/%D0%9A%D0%B8%D0%BC%20%D0%98%D0%B3%D0%BE%D1%80%D1%8C/\""
                + " title=\"Найдено публикаций: 1530\">Ким Игорь</a></span></div>", Url.SERVER_AUDIOBOO + "/tags/"));
        assertEquals(2, readers.items.size());
        assertFalse("one page", readers.hasMore);
        assertEquals("A.Tim", readers.items.get(0).getName());
        assertEquals(Url.SERVER_AUDIOBOO + "/tags/A.Tim/", readers.items.get(0).getUrl());
        assertEquals(12, readers.items.get(0).getReting());
        assertEquals(1530, readers.items.get(1).getReting());
    }

    @Test
    public void knigavuhePeopleComeFromListingRowsAndSearchItems() {
        // the shapes of /authors/2/ (legacy_people_row) and /search/authors/ (author_item), cut down
        org.jsoup.nodes.Document doc = org.jsoup.Jsoup.parse("<div id=\"authors_list\">"
                + "<div class=\"legacy_people_row\"><div class=\"legacy_people_identity\">"
                + "<a href=\"/author/donna-tartt/\" class=\"legacy_people_avatar -pseudo\">ДТ</a>"
                + "<div class=\"legacy_people_identity_body\"><div class=\"legacy_people_name\">"
                + "<a href=\"/author/donna-tartt/\">Донна <span>Тартт</span></a></div>"
                + "<div class=\"legacy_people_books\">6 книг</div></div></div>"
                + "<div class=\"legacy_people_metric -plays\">43K</div></div>"
                + "<div class=\"common_list_item author_item\"><span class=\"author_item_books_count\">586 книг</span>"
                + "<a href=\"/author/stiven-king/\" class=\"author_item_name\"><span>Стивен Кинг</span></a></div>"
                + "</div><div class=\"pn_page_buttons\"><a href=\"/authors/\" data-page=\"1\">1</a>"
                + "<a href=\"/authors/2/\" data-page=\"2\">2</a><a href=\"/authors/330/\" data-page=\"330\">330</a></div>");
        ProviderPage<GenrePOJO> page = Knigavuhe.parsePeople(doc, 2);
        assertEquals(2, page.items.size());
        assertTrue(page.hasMore);
        assertEquals("Донна Тартт", page.items.get(0).getName());
        assertEquals(Url.SERVER + "/author/donna-tartt/", page.items.get(0).getUrl());
        assertEquals("6 книг", page.items.get(0).getDescription());
        assertEquals("Стивен Кинг", page.items.get(1).getName());
        assertEquals("586 книг", page.items.get(1).getDescription());
        assertFalse(Knigavuhe.parsePeople(doc, 330).hasMore);
    }

    // changed on purpose after the live check (2026-10-30): a genre row that links to another site is skipped
    @Test
    public void knigavuheGenresAndCategoriesSkipARowLinkingToAnotherSite() {
        org.jsoup.nodes.Document doc = org.jsoup.Jsoup.parse("<div>"
                + "<div class=\"genre2_item\"><a class=\"genre2_item_name\" href=\"/genre/fantasy/\">Фантастика</a>"
                + "<div class=\"genre2_item_description\">Миры</div><span class=\"subscribe_btn_label_count\">12</span></div>"
                + "<div class=\"genre2_item\"><a class=\"genre2_item_name\" href=\"https://www.litres.ru/popular/\">Платные книги</a>"
                + "<div class=\"genre2_item_description\">Реклама</div></div>"
                + "<div class=\"genre2_item\"><a class=\"genre2_item_name\" href=\"/genre/detective/\">Детективы</a></div>"
                + "</div>", Url.SERVER);
        ProviderPage<GenrePOJO> genres = Knigavuhe.parseGenres(doc);
        assertEquals(2, genres.items.size());
        assertEquals("Фантастика", genres.items.get(0).getName());
        assertEquals(Url.SERVER + "/genre/fantasy/<page>/", genres.items.get(0).getUrl());
        assertEquals("Миры", genres.items.get(0).getDescription());
        assertEquals(12, genres.items.get(0).getReting());
        assertEquals("Детективы", genres.items.get(1).getName());
        assertEquals(Url.SERVER + "/genre/detective/<page>/", genres.items.get(1).getUrl());
    }

    // changed on purpose after the live check (2026-10-30): a genre row that links to another site is skipped
    @Test
    public void abmp3GenresSkipARowLinkingToAnotherSite() {
        org.jsoup.nodes.Document doc = org.jsoup.Jsoup.parse("<div class=\"b-posts\">"
                + "<div class=\"b-post\"><div class=\"title\"><a href=\"/genre-1-fantastika\">Фантастика</a></div>"
                + "<div class=\"rating\">120книг</div></div>"
                + "<div class=\"b-post\"><div class=\"title\"><a href=\"https://www.litres.ru/\">Платные книги</a></div></div>"
                + "<div class=\"b-post\"><div class=\"title\"><a href=\"/genre-12-detektivy-trillery\">Детективы</a></div></div>"
                + "</div>", Url.SERVER_ABMP3);
        ProviderPage<GenrePOJO> page = Provider.ABMP3.parser.genres(doc, Url.SECTIONS_ABMP3, 1);
        assertEquals(2, page.items.size());
        assertEquals("Фантастика", page.items.get(0).getName());
        assertEquals(Url.SERVER_ABMP3 + "/genre-1-fantastika?page=", page.items.get(0).getUrl());
        assertEquals("120 книг", page.items.get(0).getDescription());
        assertEquals("Детективы", page.items.get(1).getName());
        assertEquals(Url.SERVER_ABMP3 + "/genre-12-detektivy-trillery?page=", page.items.get(1).getUrl());
    }
}
