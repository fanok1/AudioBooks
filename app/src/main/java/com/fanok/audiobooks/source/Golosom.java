package com.fanok.audiobooks.source;

import static com.fanok.audiobooks.search.ListingFilters.withParam;
import static com.fanok.audiobooks.source.Html.href;
import static com.fanok.audiobooks.source.Html.labelled;
import static com.fanok.audiobooks.source.Html.text;

import com.fanok.audiobooks.BookRemovedException;
import com.fanok.audiobooks.model.OtherArtistModel;
import com.fanok.audiobooks.model.OtherSourceModel;
import com.fanok.audiobooks.pojo.AudioPOJO;
import com.fanok.audiobooks.pojo.BookPOJO;
import com.fanok.audiobooks.pojo.DescriptionPOJO;
import com.fanok.audiobooks.pojo.GenrePOJO;
import com.fanok.audiobooks.pojo.OtherArtistPOJO;
import com.fanok.audiobooks.pojo.SeriesPOJO;
import com.fanok.audiobooks.search.ProviderPage;
import com.fanok.audiobooks.search.SearchUrls;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;

/**
 * golosom.org (probed live, 2026-10-31): another front-end of Knigoblud's catalog and media, plain GETs, no Referer.
 * Every path 404s with a trailing slash. A visitor without a {@code lang} cookie whose Accept-Language prefers uk or en
 * is sent (302) to the {@code /uk/} or {@code /en/} copy of the page, so every request carries {@code lang=ru}.
 * <ul>
 * <li>listings, all newest first and paged with {@code /N}: the index {@code /}, {@code /genre/<slug>},
 * {@code /author/<slug>}, {@code /performer/<slug>}; {@code /series/<slug>} is one page. 21 {@code .bookListItem}
 * cards a page; a page past the end repeats the last one, so only the pager ({@code a[data-page]}) tells the end.
 * No sorts: {@code ?sort=} is ignored and there are no rankings. Genres {@code /genres}; no people indexes.</li>
 * <li>search {@code /search?q=Q&page=N} (books; a reader's name finds their books too).</li>
 * <li>book {@code /book/<slug>}: an inline {@code App.playerInit} XSPlayer playlist, one MP3 a chapter on
 * {@code rN.audioknigi.xyz} signed with {@code md5&expires} for 2–3 days (shared with Knigoblud, needs no
 * headers). A book sold through LitRes plays its trial ({@code litres.ru} src and a buy item) instead. The page lists
 * the series with numbers but does not link the series itself. No comments, ratings or recommendations; other
 * narrations are found by searching the title and author.</li>
 * </ul>
 */
public final class Golosom implements SourceParser {

    public static final String SERVER = "https://golosom.org";
    public static final String INDEX = SERVER;
    public static final String GENRES = SERVER + "/genres";

    @Override
    public Map<String, String> cookies() {
        return Collections.singletonMap("lang", "ru");
    }

    // ---------------------------------------------------------------- SourceParser

    @Override
    public ProviderPage<BookPOJO> listing(Document page, String url, int pageNo) {
        return parseListing(page, pageNo);
    }

    @Override
    public String pageUrl(String url, int page) {
        if (page <= 1) {
            return url;
        }
        return url.contains("?") ? withParam(url, "page", String.valueOf(page)) : url + "/" + page;
    }

    @Override
    public ProviderPage<GenrePOJO> genres(Document page, String url, int pageNo) {
        return ProviderPage.of(parseGenres(page), false);
    }

    @Override
    public String searchUrl(SearchUrls.Category category, String query, int page) {
        return category == SearchUrls.Category.BOOKS
                ? SERVER + "/search?q=" + SearchUrls.enc(query) + "&page=" + page : null;
    }

    @Override
    public DescriptionPOJO description(Document page, String bookUrl) {
        return parseDescription(page);
    }

    @Override
    public ArrayList<AudioPOJO> tracks(Document page, String base) {
        return parseTracks(page, base);
    }

    @Override
    public ArrayList<SeriesPOJO> series(Document page, String bookUrl) {
        return parseSeries(page);
    }

    @Override
    public boolean findsOtherReaders() {
        return true;
    }

    /** The book search for title and author: the hits of this book with another reader. */
    @Override
    public ArrayList<OtherArtistPOJO> otherReaders(String bookUrl, String title, String author, String reader)
            throws IOException {
        if (title == null || title.trim().isEmpty()) {
            return new ArrayList<>();
        }
        String terms = OtherSourceModel.query(title, author);
        return OtherArtistModel.otherReaders(otherSourceHits(fetch(otherSourceSearchUrl(terms, 1))), title, author,
                bookUrl, reader);
    }

    /** Title and author: every narration comes back and the reader is matched on the hits, never in the query. */
    @Override
    public String otherSourceTerms(BookPOJO book) {
        return OtherSourceModel.query(book.getName(), book.getAutor());
    }

    // ---------------------------------------------------------------- pure parsers

    /** Index, genre, author, performer, series and search pages (a series page has no {@code #BL}). */
    public static ProviderPage<BookPOJO> parseListing(Document doc, int page) {
        boolean hasMore = Html.lastPage(doc.select(".PageNavLine1 a[data-page]")) > page;
        return ProviderPage.of(Html.cards(doc.select(".BookList .bookListItem"), Golosom::card), hasMore);
    }

    private static BookPOJO card(Element card) {
        BookPOJO book = new BookPOJO();
        book.setUrl(href(card, "a.bookListItemCover"));
        book.setName(text(card, ".bookListItemCoverNameText"));
        Element img = card.selectFirst(".bookListItemCoverImg");
        if (img != null) {
            book.setPhoto(img.absUrl("data-img"));
        }
        Element authors = labelled(card, ".bookListItemMetaBlock", "Автор");
        if (authors != null) {
            book.setAutor(names(authors));
            book.setUrlAutor(href(authors, "a"));
        }
        Element readers = labelled(card, ".bookListItemMetaBlock", "Чтец");
        if (readers != null) {
            book.setArtist(names(readers));
            book.setUrlArtist(href(readers, "a"));
        }
        Element genre = card.selectFirst("a.Tag[href^=/genre/]");
        if (genre != null) {
            book.setGenre(genre.text());
            book.setUrlGenre(genre.absUrl("href"));
        }
        Element series = card.selectFirst("a.Tag[href^=/series/]");
        if (series != null) {
            book.setSeries(series.text());
            book.setUrlSeries(series.absUrl("href"));
        }
        String time = text(card, "span.Tag");
        if (!time.isEmpty()) {
            book.setTime(time);
        }
        return book;
    }

    /** The people linked in a meta row, joined with ", ". */
    private static String names(Element row) {
        StringBuilder names = new StringBuilder();
        for (Element a : row.select("a")) {
            if (names.length() > 0) {
                names.append(", ");
            }
            names.append(a.text().trim());
        }
        return names.toString();
    }

    /** {@code /genres}: name, listing and the site's book count. */
    public static ArrayList<GenrePOJO> parseGenres(Document doc) {
        return Html.rows(doc.select("a.GenresListItem"), a -> {
            GenrePOJO genre = new GenrePOJO(text(a, ".GenresListItemName"), a.absUrl("href"));
            String count = text(a, ".GenresListItemCount");
            if (count.matches("\\d{1,9}")) {
                genre.setReting(Integer.parseInt(count));
            }
            return genre;
        });
    }

    /** The book page; its meta rows come twice (desktop: all people in one row, mobile: one row each). */
    public static DescriptionPOJO parseDescription(Document doc) {
        DescriptionPOJO description = new DescriptionPOJO();
        description.setTitle(text(doc, "h1[itemprop=name]"));
        description.setTime(text(doc, ".PageTitle .PageTitle_Subtitle"));
        Element img = doc.getElementById("BookCoverImage");
        if (img != null) {
            description.setPoster(img.absUrl("src"));
        }
        Element meta = doc.selectFirst(".BookMetaBlock");
        if (meta != null) {
            Element genre = meta.selectFirst(".BookMetaBlockLine > a[href^=/genre/]");
            if (genre != null) {
                description.setGenre(genre.text());
                description.setGenreUrl(genre.absUrl("href"));
            }
            Element authors = labelled(meta, ".BookMetaBlockLine.-desktop", "Автор");
            if (authors != null) {
                description.setAutor(names(authors));
                description.setAutorUrl(href(authors, "a"));
            }
            Element readers = labelled(meta, ".BookMetaBlockLine.-desktop", "Чтец");
            if (readers != null) {
                description.setArtist(names(readers));
                description.setArtistUrl(href(readers, "a"));
            }
        }
        description.setDescription(text(doc, "#BookDescriptionContent .BookDescriptionContentInner"));
        return description;
    }

    /**
     * The {@code App.playerInit} playlist. A LitRes trial (its buy item, or a {@code litres.ru} chapter) is a
     * {@link BookRemovedException}: the card does not tell it, and {@code "blocked"} stays false.
     */
    public static ArrayList<AudioPOJO> parseTracks(Document doc, String bookUrl) {
        ArrayList<AudioPOJO> tracks = Players.xsPlaylist(Json.object(Players.playerScript(doc, "App.playerInit(", '{')),
                bookUrl, text(doc, "h1[itemprop=name]"), "golosom player");
        if (doc.selectFirst(".PlayerPlaylistBuyItem") != null || tracks.get(0).getUrl().contains("litres.ru/")) {
            throw new BookRemovedException(bookUrl);
        }
        return tracks;
    }

    /** The "Цикл" tab: every book of the series with its number, the open one included. */
    public static ArrayList<SeriesPOJO> parseSeries(Document doc) {
        return Html.each(doc.select(".BookSeries a.BookDescriptionSeriesItem"), a -> {
            String url = a.absUrl("href");
            if (url.isEmpty()) {
                return null;
            }
            SeriesPOJO item = new SeriesPOJO();
            item.setUrl(url);
            String name = text(a, ".BookDescriptionSeriesItemName");
            item.setName(name.isEmpty() ? text(a, "b") : name);
            item.setNumber(text(a, ".BookDescriptionSeriesItemIndex").replace(".", "").trim());
            return item;
        });
    }
}
