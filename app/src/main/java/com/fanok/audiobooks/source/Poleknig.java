package com.fanok.audiobooks.source;

import static com.fanok.audiobooks.source.Html.each;
import static com.fanok.audiobooks.source.Html.text;

import com.fanok.audiobooks.Url;
import com.fanok.audiobooks.model.OtherSourceModel;
import com.fanok.audiobooks.pojo.AudioPOJO;
import com.fanok.audiobooks.pojo.BookPOJO;
import com.fanok.audiobooks.pojo.DescriptionPOJO;
import com.fanok.audiobooks.pojo.GenrePOJO;
import com.fanok.audiobooks.pojo.SeriesPOJO;
import com.fanok.audiobooks.search.ProviderPage;
import com.fanok.audiobooks.search.SearchUrls;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.HttpUrl;

/**
 * poleknig.com: plain GETs. Listings ({@link Url#INDEX_POLEKNIG}, novelties, popular, a genre {@code /genres/<id>})
 * page with {@code ?p=N} and have no pager markup; a genre ranks on the server ({@code /genres/<id>/new}, {@code /best}).
 * Search {@code /?q=Q&p=N} (books only). Book pages carry a Playerjs whose {@code file} is a JSON playlist on the site
 * (mirrors shuffled per load, {@link Players#oneOf}); the series is a {@code /series/<id>} page. No comments, no other
 * narrations.
 */
public final class Poleknig implements SourceParser {

    private static final List<Sort.Entry> MENU = Sort.menu(Sort.NEW, Sort.POPULAR, Sort.TRENDING);
    /** A genre has no "popular new books" list. */
    private static final List<Sort.Entry> GENRE_MENU = Sort.menu(Sort.NEW, Sort.POPULAR);
    private static final Pattern GENRE =
            Pattern.compile("^" + Pattern.quote(Url.SERVER_POLEKNIG) + "/genres/\\d+(?=[/?#]|$)");

    // ---------------------------------------------------------------- SourceParser

    @Override
    public String referer() {
        return Url.SERVER_POLEKNIG + "/";
    }

    @Override
    public ProviderPage<BookPOJO> listing(Document page, String url, int pageNo) {
        return parseListing(page, pageNo);
    }

    @Override
    public String pageUrl(String url, int page) {
        return url.contains("?") ? url + page : url + "?p=" + page;
    }

    /** Author, reader, series, year and tag listings are not re-sorted. */
    @Override
    public List<Sort.Entry> sorts(String url) {
        boolean people = url.contains("reader") || url.contains("author") || url.contains("serie")
                || url.contains("cikl") || url.contains("/years/") || url.contains("/tags/");
        if (people) {
            return Collections.<Sort.Entry>emptyList();
        }
        return genreOf(url) == null ? MENU : GENRE_MENU;
    }

    @Override
    public String sortUrl(Sort sort, String listingUrl) {
        switch (sort) {
            case NEW: return Url.INDEX_POLEKNIG;
            case POPULAR: return Url.BEST_POLEKNIG;
            case TRENDING: return Url.NEW_BOOK_POLEKNIG;
            default: return null;
        }
    }

    /** The genre root {@code /genres/<id>} of a genre listing or ranking. */
    @Override
    public String genreOf(String url) {
        Matcher m = GENRE.matcher(url);
        return m.find() ? m.group() : null;
    }

    @Override
    public String inGenre(String siteWide, String genre) {
        if (siteWide.equals(Url.INDEX_POLEKNIG)) {
            return genre + "/new?p=";
        }
        return siteWide.replace(Url.SERVER_POLEKNIG + "/books/popular", genre + "/best");
    }

    @Override
    public ProviderPage<GenrePOJO> genres(Document page, String url, int pageNo) {
        return ProviderPage.of(Html.rows(page.select("div.genre > a.heading"), item -> {
            GenrePOJO genrePOJO = new GenrePOJO();
            if (!item.attr("href").isEmpty()) {
                genrePOJO.setUrl(item.attr("href") + "?p=");
            }
            genrePOJO.setName(item.text());
            return genrePOJO;
        }), false);
    }

    @Override
    public String searchUrl(SearchUrls.Category category, String query, int page) {
        return category == SearchUrls.Category.BOOKS
                ? Url.SERVER_POLEKNIG + "/?q=" + SearchUrls.enc(query) + "&p=" + page : null;
    }

    @Override
    public ProviderPage<BookPOJO> searchResults(Document page, int pageNo) {
        return parseListing(page, pageNo);
    }

    @Override
    public DescriptionPOJO description(Document page, String bookUrl) {
        return parseDescription(page);
    }

    @Override
    public ArrayList<AudioPOJO> tracks(Document page, String base) throws IOException {
        return Players.loadPlayerjs(page, base, text(page.selectFirst("h1")));
    }

    @Override
    public ArrayList<SeriesPOJO> series(Document page, String bookUrl) throws IOException {
        String series = Html.href(page, "a[href*=/series/]");
        return series.isEmpty() ? new ArrayList<>() : parseSeriesPage(fetch(series));
    }

    /** The search page without a page number, as the other-source lookup has always asked it. */
    @Override
    public String otherSourceSearchUrl(String terms, int page) {
        return HttpUrl.get(Url.SERVER_POLEKNIG).newBuilder().addQueryParameter("q", terms).build().toString();
    }

    @Override
    public ArrayList<OtherSourceModel.Hit> otherSourceHits(Document searchPage) {
        return parseSearch(searchPage);
    }

    // ---------------------------------------------------------------- pure parsers

    /** Index, rankings, genres and search results. */
    public static ProviderPage<BookPOJO> parseListing(Document doc, int page) {
        Elements raw = doc.select("div.book-item");
        ArrayList<BookPOJO> result = Html.cards(raw, book -> {
            BookPOJO bookPOJO = new BookPOJO();
            Element img = book.selectFirst("img");
            if (img != null) {
                String src = img.attr("data-original");
                if (src.isEmpty()) {
                    src = img.attr("data-originl");
                }
                if (src.isEmpty()) {
                    src = img.attr("src");
                }
                if (!src.isEmpty()) {
                    bookPOJO.setPhoto(src.startsWith("http") ? src : Url.SERVER_POLEKNIG + src);
                }
            }
            Element titleEl = book.selectFirst("a.book-title");
            if (titleEl != null) {
                if (!titleEl.attr("href").isEmpty()) {
                    bookPOJO.setUrl(titleEl.attr("href"));
                }
                if (!titleEl.text().isEmpty()) {
                    bookPOJO.setName(titleEl.text());
                }
            }
            Element authorEl = book.selectFirst(".book-author a");
            if (authorEl != null) {
                if (!authorEl.attr("href").isEmpty()) {
                    bookPOJO.setUrlAutor(authorEl.attr("href"));
                }
                if (!authorEl.text().isEmpty()) {
                    bookPOJO.setAutor(authorEl.text());
                }
            }
            Element readerEl = book.selectFirst(".book-reader a");
            if (readerEl != null) {
                if (!readerEl.attr("href").isEmpty()) {
                    bookPOJO.setUrlArtist(readerEl.attr("href"));
                }
                if (!readerEl.text().isEmpty()) {
                    bookPOJO.setArtist(readerEl.text());
                }
            }
            Element genreEl = book.selectFirst(".book-genres a");
            if (genreEl != null) {
                if (!genreEl.attr("href").isEmpty()) {
                    bookPOJO.setUrlGenre(genreEl.attr("href"));
                }
                if (!genreEl.text().isEmpty()) {
                    bookPOJO.setGenre(genreEl.text());
                }
            }
            Element seriesEl = book.selectFirst("a[href*=/series/]");
            if (seriesEl == null) {
                seriesEl = book.selectFirst(".book-series a");
            }
            if (seriesEl != null) {
                String href = seriesEl.attr("href");
                if (!href.isEmpty()) {
                    bookPOJO.setUrlSeries(href.startsWith("/") ? Url.SERVER_POLEKNIG + href : href);
                }
                if (!seriesEl.text().isEmpty()) {
                    bookPOJO.setSeries(seriesEl.text());
                }
            }
            Element durationEl = book.selectFirst(".book-duration");
            if (durationEl != null && !durationEl.text().isEmpty()) {
                bookPOJO.setTime(durationEl.text());
            }
            return bookPOJO;
        });
        // a paged list links its next page; a one-page list (popular, novelties) has no pager
        return ProviderPage.of(result, !raw.isEmpty() && doc.selectFirst(".pagination a[rel=next]") != null,
                raw.size());
    }

    public static DescriptionPOJO parseDescription(Document document) {
        DescriptionPOJO descriptionPOJO = new DescriptionPOJO();
        Elements titleElement = document.select("h1[itemprop=name]");
        if (titleElement.size() > 0) {
            descriptionPOJO.setTitle(titleElement.first().text().trim());
        }

        Element img = document.select("img.cover, img[src*=\"/covers/\"], img[data-original*=\"/covers/\"]").first();
        if (img != null) {
            String src = img.absUrl("src");
            if (src.isEmpty() || src.contains("placeholder")) {
                src = img.absUrl("data-original");
            }
            descriptionPOJO.setPoster(src);
        }

        Element authorEl = document.select("h2.book-author > a").first();
        if (authorEl != null) {
            descriptionPOJO.setAutor(authorEl.text());
            descriptionPOJO.setAutorUrl(authorEl.absUrl("href"));
        }

        Element genre = document.select("div.book-genres > div > a").first();
        if (genre != null) {
            descriptionPOJO.setGenre(genre.text());
            descriptionPOJO.setGenreUrl(genre.absUrl("href"));
        }

        Element readerEl = document.select("div.book-reader > div > a").first();
        if (readerEl != null) {
            descriptionPOJO.setArtist(readerEl.text());
            descriptionPOJO.setArtistUrl(readerEl.absUrl("href"));
        }

        Elements seriesEl = document.select("a[href*=/series/]");
        if (seriesEl.isEmpty()) {
            seriesEl = document.select("div.book-series > div > a");
        }
        if (seriesEl.size() > 0) {
            descriptionPOJO.setSeries(seriesEl.first().text());
            descriptionPOJO.setSeriesUrl(seriesEl.first().absUrl("href"));
        }

        Elements durationEl = document.select("div.book-duration > div");
        if (durationEl.size() > 0) {
            descriptionPOJO.setTime(durationEl.first().text());
        }

        Elements desc = document.select("div#description[itemprop=description]");
        if (desc.size() > 0) {
            descriptionPOJO.setDescription(desc.first().text());
        }
        return descriptionPOJO;
    }

    /** The books of a {@code /series/<id>} page, in the site's order. */
    public static ArrayList<SeriesPOJO> parseSeriesPage(Document doc) {
        return each(doc.select(".media-body"), body -> {
            Element card = body.parent();
            return Html.seriesItem(body.selectFirst("a[href*=/books/]"), card == null ? null : card.selectFirst("img"));
        });
    }

    /** The other-source search: its book cards as hits. */
    public static ArrayList<OtherSourceModel.Hit> parseSearch(Document doc) {
        Element root = doc.selectFirst("div#content");
        return each((root == null ? doc : root).select("div.book-item"), book -> {
            Element a = book.selectFirst("a.book-title, .book-title a");
            if (a == null) {
                return null;
            }
            return new OtherSourceModel.Hit(a.absUrl("href"), a.text().trim(), text(book, ".book-author a"),
                    text(book, ".book-reader a"));
        });
    }
}
