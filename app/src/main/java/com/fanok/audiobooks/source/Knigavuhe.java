package com.fanok.audiobooks.source;

import static com.fanok.audiobooks.pojo.BookPOJO.absUrl;
import static com.fanok.audiobooks.source.Html.cards;
import static com.fanok.audiobooks.source.Html.each;
import static com.fanok.audiobooks.source.Html.lastPage;
import static com.fanok.audiobooks.source.Html.ownPath;
import static com.fanok.audiobooks.source.Html.rows;
import static com.fanok.audiobooks.source.Html.text;
import static com.fanok.audiobooks.source.Json.array;
import static com.fanok.audiobooks.source.Json.seconds;
import static com.fanok.audiobooks.source.Json.string;
import static com.fanok.audiobooks.source.Players.playerScript;

import android.util.Log;

import androidx.annotation.Nullable;

import com.fanok.audiobooks.Consts;
import com.fanok.audiobooks.R;
import com.fanok.audiobooks.Url;
import com.fanok.audiobooks.model.ComentsModel;
import com.fanok.audiobooks.model.OtherSourceModel;
import com.fanok.audiobooks.pojo.AudioPOJO;
import com.fanok.audiobooks.pojo.BookPOJO;
import com.fanok.audiobooks.pojo.ComentsPOJO;
import com.fanok.audiobooks.pojo.DescriptionPOJO;
import com.fanok.audiobooks.pojo.GenrePOJO;
import com.fanok.audiobooks.pojo.OtherArtistPOJO;
import com.fanok.audiobooks.pojo.SeriesPOJO;
import com.fanok.audiobooks.search.ListingFilters;
import com.fanok.audiobooks.search.ProviderPage;
import com.fanok.audiobooks.search.SearchUrls;
import com.fanok.audiobooks.source.Players.Chapters;
import com.google.gson.JsonObject;

import io.reactivex.functions.BooleanSupplier;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;

import okhttp3.HttpUrl;

/**
 * knigavuhe.org ({@link Url#SERVER}): plain GETs. Listings page with a number before the closing slash
 * ({@code /new/?page=N/}); a genre ({@code /genre/<g>/<page>/}) and its rankings ({@code rating|popular/<page>/?period=})
 * fill a {@code <page>} template. The site-wide rankings take {@code w=}, a genre's {@code period=}: today, week,
 * month, alltime. Search {@code /search/?q=Q&page=N} (books, authors, readers). Book pages carry the player JSON
 * ({@code new BookPlayer(...)}), the series and the other narrations as blocks of the page itself, and the comments.
 */
public final class Knigavuhe implements SourceParser {

    private static final String TAG = "ComentsModel";

    /** The books menu every listing has always shown; none of its sorts works on a listing without {@link Url#SERVER}. */
    private static final List<Sort.Entry> MENU = Collections.unmodifiableList(Arrays.asList(
            Sort.item(Sort.NEW),
            Sort.submenu(R.string.popular,
                    Sort.POPULAR_ALL_TIME, Sort.POPULAR_MONTH, Sort.POPULAR_WEEK, Sort.POPULAR_DAY),
            Sort.submenu(R.string.reting,
                    Sort.RATING_ALL_TIME, Sort.RATING_MONTH, Sort.RATING_WEEK, Sort.RATING_DAY)));

    private static final List<ListingFilters.Period> PERIODS = Arrays.asList(ListingFilters.Period.DAY,
            ListingFilters.Period.WEEK, ListingFilters.Period.MONTH, ListingFilters.Period.ALL);

    // ---------------------------------------------------------------- SourceParser

    @Override
    public ProviderPage<BookPOJO> listing(Document page, String url, int pageNo) {
        return parseListing(page, url, pageNo);
    }

    /** A genre and its rankings fill their {@code <page>} template; any other listing takes the number and a slash. */
    @Override
    public String pageUrl(String url, int page) {
        if (!url.contains("genre")) {
            return url + page + "/";
        }
        return url.replace("<page>", Integer.toString(page));
    }

    /** Author, reader and series listings are not re-sorted. */
    @Override
    public List<Sort.Entry> sorts(String url) {
        boolean noSort = url.contains("reader") || url.contains("author") || url.contains("serie")
                || url.contains("cikl");
        return noSort ? Collections.<Sort.Entry>emptyList() : MENU;
    }

    /** The site-wide rankings ({@link Url#NEW_BOOK} ...); a genre listing ranks its own genre root. */
    @Override
    public String sortUrl(Sort sort, String listingUrl) {
        if (!listingUrl.contains(Url.SERVER)) {
            return null;
        }
        if (!listingUrl.contains("genre")) {
            switch (sort) {
                case NEW: return Url.NEW_BOOK;
                case RATING_ALL_TIME: return Url.RATING_ALL_TIME;
                case RATING_MONTH: return Url.RATING_MONTH;
                case RATING_WEEK: return Url.RATING_WEEK;
                case RATING_DAY: return Url.RATING_TODATY;
                case POPULAR_ALL_TIME: return Url.BEST_ALL_TIME;
                case POPULAR_MONTH: return Url.BEST_MONTH;
                case POPULAR_WEEK: return Url.BEST_WEEK;
                case POPULAR_DAY: return Url.BEST_TODAY;
                default: return null;
            }
        }
        String url = listingUrl.substring(0, Consts.indexOfByNumber(listingUrl, '/', 5) + 1);
        switch (sort) {
            case NEW: return url + "<page>/";
            case RATING_ALL_TIME: return url + "rating/<page>/?period=alltime";
            case RATING_MONTH: return url + "rating/<page>/?period=month";
            case RATING_WEEK: return url + "rating/<page>/?period=week";
            case RATING_DAY: return url + "rating/<page>/?period=today";
            case POPULAR_ALL_TIME: return url + "popular/<page>/?period=alltime";
            case POPULAR_MONTH: return url + "popular/<page>/?period=month";
            case POPULAR_WEEK: return url + "popular/<page>/?period=week";
            case POPULAR_DAY: return url + "popular/<page>/?period=today";
            default: return null;
        }
    }

    /** {@code popular} and {@code rating} take today, week, month and alltime. */
    @Override
    public List<ListingFilters.Period> periods(String url) {
        return url.contains("/popular/") || url.contains("/rating/") ? PERIODS
                : Collections.<ListingFilters.Period>emptyList();
    }

    /** {@code w=} on the index, {@code period=} in a genre; any other word is the site's fallback, today. */
    @Override
    public ListingFilters.Period periodOf(String url, Calendar today) {
        String w = ListingFilters.param(url, "w");
        if (w == null) {
            w = ListingFilters.param(url, "period");
        }
        return fromWord(w);
    }

    @Override
    public String withPeriod(String url, ListingFilters.Period period, Calendar today) {
        return url.contains("w=") ? ListingFilters.withParam(url, "w", toWord(period))
                : ListingFilters.withParam(url, "period", toWord(period));
    }

    @Override
    public ProviderPage<GenrePOJO> genres(Document page, String url, int pageNo) {
        return parseGenres(page);
    }

    @Override
    public ProviderPage<GenrePOJO> people(Document page, String url, int pageNo, boolean performers) {
        return parsePeople(page, pageNo);
    }

    @Override
    public String searchUrl(SearchUrls.Category category, String query, int page) {
        String q = SearchUrls.enc(query);
        switch (category) {
            case BOOKS: return Url.SERVER + "/search/?q=" + q + "&page=" + page;
            case AUTHORS: return Url.SERVER + "/search/authors/?q=" + q + "&page=" + page;
            case PERFORMERS: return Url.SERVER + "/search/readers/?q=" + q + "&page=" + page;
        }
        return null;
    }

    /** The search page is a listing; its URL is the one it was served from. */
    @Override
    public ProviderPage<BookPOJO> searchResults(Document page, int pageNo) {
        return parseListing(page, page.location(), pageNo);
    }

    @Override
    public ProviderPage<GenrePOJO> searchPeople(Document page, int pageNo, boolean performers) {
        return parsePeople(page, pageNo);
    }

    @Override
    public DescriptionPOJO description(Document page, String bookUrl) {
        return parseDescription(page);
    }

    @Override
    public BookPOJO book(Document page, String bookUrl) {
        return parseBook(page, bookUrl);
    }

    @Override
    public ArrayList<BookPOJO> recommendations(Document page, String bookUrl) {
        return parseRecommendations(page);
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
    public ArrayList<ComentsPOJO> comments(String bookUrl, BooleanSupplier cancelled) throws Exception {
        ComentsModel.Parsed parsed = parseComments(fetch(bookUrl));
        if (parsed.skipped > 0) {
            Log.w(TAG, "skipped " + parsed.skipped + " malformed comments on " + bookUrl);
        }
        return parsed.comments;
    }

    @Override
    public boolean findsOtherReaders() {
        return true;
    }

    /** The book page itself tells whether there are other narrations ({@link #parseDescription}): no search of its own. */
    @Override
    public boolean probesOtherReaders() {
        return false;
    }

    /** The "Другие озвучки" block of the book page. */
    @Override
    public ArrayList<OtherArtistPOJO> otherReaders(String bookUrl, String title, String author, String reader)
            throws IOException {
        return parseOtherReaders(fetch(bookUrl));
    }

    /** Title and author. */
    @Override
    public String otherSourceTerms(BookPOJO book) {
        return OtherSourceModel.query(book.getName(), book.getAutor());
    }

    /** The search page without a page number, as the other-source lookup has always asked it. */
    @Override
    public String otherSourceSearchUrl(String terms, int page) {
        return HttpUrl.get(Url.SERVER).newBuilder().addPathSegments("search/").addQueryParameter("q", terms)
                .build().toString();
    }

    @Override
    public ArrayList<OtherSourceModel.Hit> otherSourceHits(Document searchPage) {
        return parseSearch(searchPage);
    }

    // ---------------------------------------------------------------- pure parsers

    /** Index, rankings, genre, author, reader and series listings and search results. */
    public static ProviderPage<BookPOJO> parseListing(Document doc, String url, int page) {
        boolean hasMore = lastPage(doc.select(".pn_page_buttons a")) > page;
        String pageAutor = "";
        String pageAutorUrl = "";
        Element pageTitle = doc.selectFirst(".page_title");
        if (pageTitle != null) {
            if (pageTitle.text().contains("Цикл")) {
                Element a = pageTitle.selectFirst("a");
                if (a != null) {
                    pageAutor = a.text();
                    pageAutorUrl = Url.SERVER + a.attr("href");
                }
            } else if (pageTitle.text().contains("Все авторы")) {
                pageAutorUrl = url;
                Element h1 = pageTitle.selectFirst("h1");
                pageAutor = h1 == null ? "" : h1.text();
            }
        }
        final String autor = pageAutor;
        final String autorUrl = pageAutorUrl;
        Element bookList = doc.getElementById("books_updates_list");
        if (bookList == null) {
            bookList = doc.getElementById("books_list");
        }
        if (bookList == null) {
            return ProviderPage.empty();
        }
        Elements raw = bookList.getElementsByClass("bookkitem");
        ArrayList<BookPOJO> result = cards(raw, book -> {
            if (!book.getElementsByClass("bookkitem_litres_icon").isEmpty()) {
                return null;
            }
            BookPOJO bookPOJO = new BookPOJO();
            Element img = book.selectFirst("img");
            if (img != null) {
                String src = img.attr("src");
                int lastPos = src.indexOf("?");
                bookPOJO.setPhoto(lastPos == -1 ? src : src.substring(0, lastPos));
            }
            Element a = book.selectFirst(".bookkitem_name > *");
            if (a != null) {
                bookPOJO.setUrl(Url.SERVER + a.attr("href"));
                bookPOJO.setName(a.text());
            }
            Element aGenre = book.selectFirst(".bookkitem_genre > *");
            if (aGenre != null) {
                bookPOJO.setUrlGenre(Url.SERVER + aGenre.attr("href") + "<page>/");
                bookPOJO.setGenre(aGenre.text());
            }
            Element rating = book.selectFirst(".bookkitem_icon.-views");
            if (rating != null && rating.nextElementSibling() != null) {
                bookPOJO.setReting(rating.nextElementSibling().text());
            }
            Element coments = book.selectFirst(".bookkitem_icon.-comments");
            if (coments != null && coments.nextElementSibling() != null) {
                bookPOJO.setComents(coments.nextElementSibling().text());
            }
            Element aAutor = book.selectFirst(".bookkitem_author a");
            if (aAutor != null) {
                bookPOJO.setAutor(aAutor.text());
                bookPOJO.setUrlAutor(Url.SERVER + aAutor.attr("href"));
            } else {
                if (!autor.isEmpty()) {
                    bookPOJO.setAutor(autor);
                }
                if (!autorUrl.isEmpty()) {
                    bookPOJO.setUrlAutor(autorUrl);
                }
            }
            Element reader = book.selectFirst(".bookkitem_icon.-reader");
            if (reader != null && reader.parent() != null) {
                Element artist = reader.parent().selectFirst("a");
                if (artist != null) {
                    bookPOJO.setArtist(artist.text());
                    bookPOJO.setUrlArtist(Url.SERVER + artist.attr("href"));
                }
            }
            Element time = book.selectFirst(".bookkitem_meta_time");
            if (time != null) {
                bookPOJO.setTime(time.text());
            }
            Element serie = book.selectFirst(".bookkitem_icon.-serie");
            if (serie != null && serie.parent() != null) {
                Element series = serie.parent().selectFirst("a");
                if (series != null) {
                    bookPOJO.setSeries(series.text());
                    bookPOJO.setUrlSeries(Url.SERVER + series.attr("href") + "?page=");
                }
            }
            if ((url.contains("/serie/") || url.contains("/series/")) && (bookPOJO.getSeries() == null || bookPOJO.getUrlSeries() == null)) {
                bookPOJO.setUrlSeries(url);
                Element b = doc.selectFirst(".page_title b");
                if (b != null && nonEmpty(b.text()) != null) {
                    bookPOJO.setSeries(b.text());
                }
            }
            Element about = book.selectFirst(".bookkitem_about");
            if (about != null) {
                bookPOJO.setDesc(about.text());
            }
            return bookPOJO;
        });
        return ProviderPage.of(result, hasMore, raw.size());
    }

    private static String nonEmpty(String s) {
        return s == null || s.trim().isEmpty() ? null : s.trim();
    }

    /** The genre list: one page. */
    public static ProviderPage<GenrePOJO> parseGenres(Document doc) {
        return ProviderPage.of(rows(doc.getElementsByClass("genre2_item"), item -> {
            GenrePOJO genrePOJO = new GenrePOJO();
            Element name = item.selectFirst(".genre2_item_name");
            if (name != null && !ownPath(name.attr("href"))) {
                return null; // a row linking to another site
            }
            if (name != null) {
                genrePOJO.setUrl(Url.SERVER + name.attr("href") + "<page>/");
                genrePOJO.setName(name.text());
            }
            Element rating = item.selectFirst(".subscribe_btn_label_count");
            if (rating != null) {
                genrePOJO.setReting(Integer.parseInt(rating.text().trim()));
            }
            Element description = item.selectFirst(".genre2_item_description");
            if (description != null) {
                genrePOJO.setDescription(description.text());
            }
            return genrePOJO;
        }), false);
    }

    /** The author/reader rows: legacy_people_row on the listings, author_item in the search. */
    public static ProviderPage<GenrePOJO> parsePeople(Document doc, int page) {
        boolean hasMore = lastPage(doc.select(".pn_page_buttons a")) > page;
        return ProviderPage.of(rows(doc.select(".author_item, .legacy_people_row"), item -> {
            GenrePOJO genrePOJO = new GenrePOJO();
            // a listing row opens with the avatar link, whose text is the initials
            Element a = item.selectFirst(".legacy_people_name a");
            if (a == null) {
                a = item.selectFirst("a");
            }
            if (a != null) {
                if (!a.attr("href").isEmpty()) {
                    genrePOJO.setUrl(Url.SERVER + a.attr("href"));
                }
                if (!a.text().isEmpty()) {
                    genrePOJO.setName(a.text());
                }
            }
            Element booksCount = item.selectFirst(".author_item_books_count, .legacy_people_books");
            if (booksCount != null) {
                genrePOJO.setDescription(booksCount.text());
            }
            return genrePOJO;
        }), hasMore);
    }

    public static DescriptionPOJO parseDescription(Document document) {
        DescriptionPOJO descriptionPOJO = new DescriptionPOJO();
        Elements titleElement = document.getElementsByClass("book_title_elem book_title_name");
        if (titleElement.size() != 0) {
            descriptionPOJO.setTitle(titleElement.first().text().trim());
        }
        Elements retingElements = document.getElementsByClass("book_actions_plays");
        if (retingElements.size() != 0) {
            descriptionPOJO.setReiting(retingElements.first().text().replaceAll(" ", ""));
        }
        Elements posterElements = document.getElementsByClass("book_cover");
        if (posterElements.size() != 0) {
            Elements img = posterElements.first().getElementsByTag("img");
            if (img.size() != 0) {
                descriptionPOJO.setPoster(withoutQuery(img.first().absUrl("src")));
            }
        }

        Elements timeElements = document.getElementsByClass("book_info_label");
        if (timeElements.size() != 0) {
            Node time = timeElements.first().nextSibling();
            if (time != null) {
                descriptionPOJO.setTime(time.toString().trim());
            }
        }

        Elements autorElements = document.getElementsByAttributeValue("itemprop", "author");
        if (autorElements.size() != 0) {
            Elements aElements = autorElements.first().getElementsByTag("a");
            if (aElements.size() != 0) {
                descriptionPOJO.setAutor(aElements.first().text());
                descriptionPOJO.setAutorUrl(absUrl(aElements.first(), "href", ""));
            }
        }

        Elements artistElements = document.getElementsByClass("book_title_elem");

        for (int i = 0; i < artistElements.size(); i++) {
            Element element = artistElements.get(i);
            if (element.text().contains("читает") || element.text().contains("читают")) {
                Elements aTag = element.getElementsByTag("a");
                if (aTag.size() != 0) {
                    descriptionPOJO.setArtist(aTag.first().text());
                    descriptionPOJO.setArtistUrl(absUrl(aTag.first(), "href", ""));
                }
            }
        }

        Elements seriesSisterElements = document.getElementsByClass("book_serie_block_title");

        for (int i = 0; i < seriesSisterElements.size(); i++) {
            Element element = seriesSisterElements.get(i);
            if (element.text().contains("Цикл")) {
                Elements seriesElement = element.getElementsByTag("a");
                if (seriesElement.size() != 0) {
                    descriptionPOJO.setSeries(seriesElement.first().text());
                    descriptionPOJO.setSeriesUrl(absUrl(seriesElement.first(), "href", ""));
                }
            } else if (element.text().contains("Другие варианты озвучки")||element.text().contains("Другие озвучки")) {
                descriptionPOJO.setOtherReader(true);
            }
        }

        Elements descriptionElements = document.getElementsByAttributeValue("itemprop",
                "description");
        if (descriptionElements.size() != 0) {
            descriptionPOJO.setDescription(descriptionElements.first().text().trim());
        }

        Elements genreConteiner = document.getElementsByClass("book_genre_pretitle");
        if (genreConteiner.size() != 0) {
            Elements aTag = genreConteiner.first().getElementsByTag("a");
            if (aTag.size() != 0) {
                Element a = aTag.first();
                descriptionPOJO.setGenre(a.text());
                descriptionPOJO.setGenreUrl(absUrl(a, "href", "<page>/"));
            }
        }

        Element favorite = document.getElementById("book_fave_count");
        if (favorite != null) {
            descriptionPOJO.setFavorite(favorite.text());
        }

        Element like = document.getElementById("book_likes_count");
        if (like != null) {
            descriptionPOJO.setLike(like.text());
        }

        Element disLike = document.getElementById("book_dislikes_count");
        if (disLike != null) {
            descriptionPOJO.setDisLike(disLike.text());
        }

        return descriptionPOJO;
    }

    /** Cover URLs carry cache-busting queries ({@code ?v=1}); the app stores them without. */
    private static String withoutQuery(String url) {
        int query = url.indexOf('?');
        return query == -1 ? url : url.substring(0, query);
    }

    /** The "other books" block of the book page. */
    public static ArrayList<BookPOJO> parseRecommendations(Document document) {
        ArrayList<BookPOJO> books = new ArrayList<>();
        Elements root = document.getElementsByClass("suggested");
        if (root.size() > 0) {
            Elements booksConteiner = root.get(0).getElementsByTag("td");
            for (int i = 0; i < booksConteiner.size(); i++) {
                BookPOJO bookPOJO = new BookPOJO();
                Elements aTag = booksConteiner.get(i).getElementsByTag("a");
                if (aTag.size() > 0) {
                    Element a = aTag.first();
                    bookPOJO.setUrl(a.absUrl("href"));
                    Elements img = a.getElementsByTag("img");
                    if (img.size() > 0) {
                        bookPOJO.setPhoto(withoutQuery(img.first().absUrl("src")));
                    }
                    Elements name = a.getElementsByClass("suggested_book_name");
                    if (name.size() > 0) {
                        bookPOJO.setName(name.first().text());
                    }
                }
                if (!bookPOJO.isNull()) {
                    books.add(bookPOJO);
                }
            }
        }

        return books;
    }

    /** A book opened by its link. */
    public static BookPOJO parseBook(Document document, String url) {
        BookPOJO bookPOJO = new BookPOJO();

        Elements titleElement = document.getElementsByClass("book_title_elem book_title_name");
        if (!titleElement.isEmpty()) {
            bookPOJO.setName(titleElement.first().text().trim());
        }
        Elements retingElements = document.getElementsByClass("book_views_icon");
        if (!retingElements.isEmpty()) {
            Node retingNode = retingElements.first().nextSibling();
            if (retingNode != null) {
                bookPOJO.setReting(retingNode.toString());
            }
        }
        Elements posterElements = document.getElementsByClass("book_cover");
        if (!posterElements.isEmpty()) {
            Elements img = posterElements.first().getElementsByTag("img");
            if (!img.isEmpty()) {
                String imgUrl = img.first().absUrl("src");
                if (imgUrl != null) {
                    int lastPos = imgUrl.indexOf("?");
                    if (lastPos != -1) {
                        imgUrl = imgUrl.substring(0, lastPos);
                    }
                    bookPOJO.setPhoto(imgUrl);
                }
            }
        }

        Elements timeElements = document.getElementsByClass("book_info_label");

        if (!timeElements.isEmpty() && timeElements.first().text().contains("Время звучания:")) {
            Node timeNode = timeElements.first().nextSibling();
            if (timeNode != null) {
                bookPOJO.setTime(timeNode.toString());
            }
        }

        Elements autorElements = document.getElementsByAttributeValue("itemprop", "author");
        if (!autorElements.isEmpty()) {
            Elements aElements = autorElements.first().getElementsByTag("a");
            if (!aElements.isEmpty()) {
                bookPOJO.setAutor(aElements.first().text());
                bookPOJO.setUrlAutor(absUrl(aElements.first(), "href", ""));
            }
        }

        Elements artistElements = document.getElementsByClass("book_title_elem");

        for (int i = 0; i < artistElements.size(); i++) {
            Element element = artistElements.get(i);
            if (element.text().contains("читает")) {
                Elements aTag = element.getElementsByTag("a");
                if (!aTag.isEmpty()) {
                    bookPOJO.setArtist(aTag.first().text());
                    bookPOJO.setUrlArtist(absUrl(aTag.first(), "href", ""));
                }
            }
        }

        Elements seriesSisterElements = document.getElementsByClass("book_serie_block_title");
        for (int i = 0; i < seriesSisterElements.size(); i++) {
            Element element = seriesSisterElements.get(i);
            if (element.text().contains("Цикл")) {
                Elements seriesElement = element.getElementsByTag("a");
                if (!seriesElement.isEmpty()) {
                    bookPOJO.setSeries(seriesElement.first().text());
                    bookPOJO.setUrlSeries(absUrl(seriesElement.first(), "href", ""));
                }
            }

        }

        Elements genreConteiner = document.getElementsByClass("book_genre_pretitle");
        if (!genreConteiner.isEmpty()) {
            Elements aTag = genreConteiner.first().getElementsByTag("a");
            if (!aTag.isEmpty()) {
                Element a = aTag.first();
                bookPOJO.setGenre(a.text());
                bookPOJO.setUrlGenre(absUrl(a, "href", ""));
            }
        }

        bookPOJO.setUrl(url);

        Element comentsTitle = document.getElementById("comments_count");
        if (comentsTitle != null) {
            bookPOJO.setComents(comentsTitle.text());
        }

        Elements desc = document.getElementsByClass("book_description");
        if (desc != null && !desc.isEmpty()) {
            bookPOJO.setDesc(desc.first().text());
        }

        return bookPOJO;
    }

    /** The player JSON inside a page script ({@code new BookPlayer(id, [...])}). */
    public static ArrayList<AudioPOJO> parseTracks(Document doc, String bookUrl) {
        Chapters chapters = new Chapters(text(doc.selectFirst(".book_title_name")), bookUrl);
        chapters.each(array(playerScript(doc, "new BookPlayer(", '[')), item -> {
            JsonObject o = item.getAsJsonObject();
            return chapters.chapter(string(o, "title"), string(o, "url"), seconds(o.get("duration")));
        });
        return chapters.done("knigavuhe player");
    }

    /** The "Цикл" block of the book page, the open book included (without a link). */
    public static ArrayList<SeriesPOJO> parseSeries(Document doc) {
        ArrayList<SeriesPOJO> result = new ArrayList<>();
        for (Element block : doc.getElementsByClass("book_serie_block")) {
            if (!text(block, ".book_serie_block_title").contains("Цикл")) {
                continue;
            }
            result.addAll(each(block.getElementsByClass("book_serie_block_item"), item -> {
                SeriesPOJO seriesPOJO = new SeriesPOJO();
                seriesPOJO.setNumber(text(item, ".book_serie_block_item_index"));
                Element a = item.selectFirst("a");
                if (a != null) {
                    seriesPOJO.setName(a.text().trim());
                    String url = a.absUrl("href");
                    if (!url.isEmpty()) {
                        seriesPOJO.setUrl(url);
                    }
                } else {
                    seriesPOJO.setName(text(item, "strong")); // the current book: no link
                }
                return seriesPOJO;
            }));
        }
        return result;
    }

    /** The comments of a book page; a reply sits in the {@code div.comment} it answers. */
    public static ComentsModel.Parsed parseComments(Document doc) {
        Element list = doc.getElementById("comments_list");
        if (list == null) {
            return new ComentsModel.Parsed();
        }
        return ComentsModel.nest(list.select("div.comment"), "div.comment", (comment, pojo) -> {
            Element head = comment.selectFirst("> .comment_head");
            pojo.setName(commentText(head, ".comment_head_user"));
            pojo.setDate(commentText(head, ".comment_head_time"));
            pojo.setReting(commentText(head, ".comment_head_votes_count"));
            pojo.setImage(commentUrl(head, ".comment_head_userpic img", "src"));
            pojo.setText(commentText(comment, "> .comment_body_wrap > .comment_body"));
        });
    }

    private static String commentText(@Nullable Element scope, String query) {
        Element element = scope == null ? null : scope.selectFirst(query);
        return element == null ? "" : element.text();
    }

    private static String commentUrl(@Nullable Element scope, String query, String attribute) {
        Element element = scope == null ? null : scope.selectFirst(query);
        return element == null ? "" : element.absUrl(attribute);
    }

    /** The other readings the book page lists ("Другие озвучки"). */
    public static ArrayList<OtherArtistPOJO> parseOtherReaders(Document doc) {
        ArrayList<OtherArtistPOJO> result = new ArrayList<>();
        for (Element block : doc.getElementsByClass("book_serie_block")) {
            String title = text(block, ".book_serie_block_title");
            if (!title.contains("Другие варианты озвучки") && !title.contains("Другие озвучки")) {
                continue;
            }
            result.addAll(each(block.getElementsByClass("book_serie_block_item"), item -> {
                Element a = item.selectFirst("a");
                String link = a == null ? "" : a.absUrl("href");
                if (link.isEmpty()) {
                    return null;
                }
                OtherArtistPOJO pojo = new OtherArtistPOJO();
                pojo.setUrl(link);
                pojo.setName(item.text().replace(a.text() + " ", "").trim());
                return pojo;
            }));
        }
        return result;
    }

    /** The other-source search: its book cards as hits (litres cards left out). */
    public static ArrayList<OtherSourceModel.Hit> parseSearch(Document doc) {
        Element list = doc.getElementById("books_updates_list");
        if (list == null) {
            list = doc.getElementById("books_list");
        }
        if (list == null) {
            return new ArrayList<>();
        }
        return each(list.getElementsByClass("bookkitem"), book -> {
            if (!book.getElementsByClass("bookkitem_litres_icon").isEmpty()) {
                return null;
            }
            Element a = book.selectFirst("a.bookkitem_name, .bookkitem_name a");
            if (a == null) {
                return null;
            }
            Element icon = book.selectFirst(".bookkitem_icon.-reader");
            Element block = icon == null ? null : icon.parent();
            String reader = block == null ? "" : text(block, "a");
            return new OtherSourceModel.Hit(a.absUrl("href"), a.text().trim(), text(book, ".bookkitem_author a"),
                    reader);
        });
    }

    // ---------------------------------------------------------------- periods

    private static ListingFilters.Period fromWord(String w) {
        if (w == null) {
            return null;
        }
        switch (w) {
            case "week": return ListingFilters.Period.WEEK;
            case "month": return ListingFilters.Period.MONTH;
            case "alltime": return ListingFilters.Period.ALL;
            default: return ListingFilters.Period.DAY; // "today" and the site's fallback for anything else
        }
    }

    private static String toWord(ListingFilters.Period period) {
        switch (period) {
            case WEEK: return "week";
            case MONTH: return "month";
            case ALL: return "alltime";
            default: return "today";
        }
    }
}
