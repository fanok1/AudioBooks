package com.fanok.audiobooks.source;

import static com.fanok.audiobooks.search.ListingFilters.lastSegment;
import static com.fanok.audiobooks.search.ListingFilters.param;
import static com.fanok.audiobooks.search.ListingFilters.withParam;
import static com.fanok.audiobooks.source.Html.cards;
import static com.fanok.audiobooks.source.Html.each;
import static com.fanok.audiobooks.source.Html.href;
import static com.fanok.audiobooks.source.Html.lastPage;
import static com.fanok.audiobooks.source.Html.text;

import com.fanok.audiobooks.R;
import com.fanok.audiobooks.Url;
import com.fanok.audiobooks.model.OtherArtistModel;
import com.fanok.audiobooks.model.OtherSourceModel;
import com.fanok.audiobooks.pojo.AudioPOJO;
import com.fanok.audiobooks.pojo.BookPOJO;
import com.fanok.audiobooks.pojo.DescriptionPOJO;
import com.fanok.audiobooks.pojo.GenrePOJO;
import com.fanok.audiobooks.pojo.OtherArtistPOJO;
import com.fanok.audiobooks.pojo.SeriesPOJO;
import com.fanok.audiobooks.search.ListingFilters;
import com.fanok.audiobooks.search.ProviderPage;
import com.fanok.audiobooks.search.SearchUrls;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import okhttp3.HttpUrl;

/**
 * knigoblud.club: plain GETs. Genre, author, reader and series listings all look like {@code /<uuid>}; the genres are
 * a built-in list ({@link #GENRE_URLS} tells them apart), never fetched. Listings page with {@code /<page>} or, when
 * the URL has a query, an appended number; rankings are {@code /popular/?period=week|month}, {@code /today/},
 * {@code /trending/}, {@code /finished/}, none per genre, so a ranking opened from a genre carries {@code genre=<id>}
 * (ignored by the server) and its cards are narrowed on the device. Search {@code /search?q=Q&page=N} (books only).
 * Book pages carry a {@code KB.playerInit} XSPlayer playlist and list the series. No comments; other narrations are
 * found by searching the title and author.
 */
public final class Knigoblud implements SourceParser {

    private static final List<Sort.Entry> MENU = Collections.unmodifiableList(Arrays.asList(
            Sort.item(Sort.NEW),
            Sort.submenu(R.string.popular, Sort.POPULAR_MONTH, Sort.POPULAR_WEEK),
            Sort.item(Sort.TODAY), Sort.item(Sort.TRENDING), Sort.item(Sort.FINISHED)));
    private static final List<ListingFilters.Period> WEEK_MONTH =
            Arrays.asList(ListingFilters.Period.WEEK, ListingFilters.Period.MONTH);
    /** A {@code /<uuid>} listing, with or without www. */
    private static final Pattern UUID_LISTING = Pattern.compile("https://(www\\.)?knigoblud\\.club/[0-9a-f-]{36}.*");

    /** The URLs of the genres: the genre, author and series links look alike, so a sort knows them by this. */
    public static final Set<String> GENRE_URLS = new HashSet<>();

    static {
        for (GenrePOJO genre : genreList()) {
            GENRE_URLS.add(genre.getUrl());
        }
    }

    // ---------------------------------------------------------------- SourceParser

    @Override
    public ProviderPage<BookPOJO> listing(Document page, String url, int pageNo) {
        return parseListing(page, pageNo);
    }

    @Override
    public String pageUrl(String url, int page) {
        return url.contains("?") ? url + page : url + "/" + page;
    }

    /** Author, reader and series listings ({@code /<uuid>} like the genres, but not one of them) are not re-sorted. */
    @Override
    public List<Sort.Entry> sorts(String url) {
        boolean noSort = url.contains("reader") || url.contains("author") || url.contains("serie")
                || url.contains("cikl") || (UUID_LISTING.matcher(url).matches() && genreOf(url) == null);
        if (noSort) {
            return Collections.<Sort.Entry>emptyList();
        }
        return MENU;
    }

    /** The site ranks popularity by week and month only; "today" is its own list. */
    @Override
    public String sortUrl(Sort sort, String listingUrl) {
        switch (sort) {
            case NEW: return Url.INDEX_KNIGOBLUD;
            case POPULAR_WEEK: return Url.POPULAR_WEEK_KNIGOBLUD;
            case POPULAR_MONTH: return Url.POPULAR_MONTH_KNIGOBLUD;
            case TODAY: return Url.TODAY_KNIGOBLUD;
            case TRENDING: return Url.TRENDING_KNIGOBLUD;
            case FINISHED: return Url.FINISHED_KNIGOBLUD;
            default: return null;
        }
    }

    /** {@code popular} takes {@code period=week|month} only. */
    @Override
    public List<ListingFilters.Period> periods(String url) {
        return url.contains("/popular/") ? WEEK_MONTH : Collections.<ListingFilters.Period>emptyList();
    }

    @Override
    public ListingFilters.Period periodOf(String url, Calendar today) {
        return "month".equals(param(url, "period")) ? ListingFilters.Period.MONTH : ListingFilters.Period.WEEK;
    }

    @Override
    public String withPeriod(String url, ListingFilters.Period period, Calendar today) {
        return withParam(url, "period", period == ListingFilters.Period.MONTH ? "month" : "week");
    }

    @Override
    public String cardGenre(String url) {
        return param(url, "genre");
    }

    /** The genre id of a genre listing ({@link #GENRE_URLS}), or of a ranking narrowed to one. */
    @Override
    public String genreOf(String url) {
        String narrowed = cardGenre(url);
        if (narrowed != null) {
            return narrowed;
        }
        return GENRE_URLS.contains(url) ? lastSegment(url) : null;
    }

    /** "New" is the genre listing itself; a ranking carries the genre for {@link #cardGenre}. */
    @Override
    public String inGenre(String siteWide, String genre) {
        return siteWide.equals(Url.INDEX_KNIGOBLUD) ? Url.SERVER_KNIGOBLUD + "/" + genre
                : siteWide.replace("?", "?genre=" + genre + "&");
    }

    /** The built-in genre list; {@code page} is not read. */
    @Override
    public ProviderPage<GenrePOJO> genres(Document page, String url, int pageNo) {
        return ProviderPage.of(genreList(), false);
    }

    /** The genre list is built in: nothing is fetched. */
    @Override
    public ProviderPage<GenrePOJO> loadGenres(String url, int pageNo) {
        return ProviderPage.of(genreList(), false);
    }

    @Override
    public String searchUrl(SearchUrls.Category category, String query, int page) {
        return category == SearchUrls.Category.BOOKS
                ? Url.SERVER_KNIGOBLUD + "/search?q=" + SearchUrls.enc(query) + "&page=" + page : null;
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
    public BookPOJO book(Document page, String bookUrl) {
        return parseBook(page, bookUrl);
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
        return OtherArtistModel.otherReaders(parseSearch(fetch(otherSourceSearchUrl(terms, 1))), title, author,
                bookUrl, reader);
    }

    /** Title and author: the site's search finds nothing with the reader added. */
    @Override
    public String otherSourceTerms(BookPOJO book) {
        return OtherSourceModel.query(book.getName(), book.getAutor());
    }

    /** The search page without a page number, as the other-source lookup has always asked it. */
    @Override
    public String otherSourceSearchUrl(String terms, int page) {
        return HttpUrl.get(Url.SERVER_KNIGOBLUD).newBuilder().addPathSegment("search")
                .addQueryParameter("q", terms).build().toString();
    }

    @Override
    public ArrayList<OtherSourceModel.Hit> otherSourceHits(Document searchPage) {
        return parseSearch(searchPage);
    }

    // ---------------------------------------------------------------- pure parsers

    /** Index, genres, people, rankings and search results. */
    public static ProviderPage<BookPOJO> parseListing(Document document, int page) {
        boolean hasMore = lastPage(document.select(".PageNavLine1 a[data-page]")) > page;
        Element listParent = document.getElementById("BL");
        if (listParent == null) {
            return ProviderPage.empty();
        }
        ArrayList<BookPOJO> result = cards(listParent.getElementsByClass("bookListItem"), book -> {
            BookPOJO bookPOJO = new BookPOJO();
            Element image = book.selectFirst(".bookListItemCoverImg");
            if (image != null && !image.attr("data-img").isEmpty()) {
                bookPOJO.setPhoto(image.attr("data-img"));
            }
            Element cover = book.selectFirst(".bookListItemCover");
            if (cover != null && !cover.attr("href").isEmpty()) {
                bookPOJO.setUrl(Url.SERVER_KNIGOBLUD + cover.attr("href"));
            }
            Element name = book.selectFirst(".bookListItemCoverNameText");
            if (name != null && !name.text().isEmpty()) {
                bookPOJO.setName(name.text());
            }
            Element description = book.selectFirst(".bookListItemDescription");
            if (description != null && !description.text().isEmpty()) {
                bookPOJO.setDesc(description.text());
            }
            Element time = book.selectFirst(".bookListItemNameDur");
            if (time != null && !time.text().isEmpty()) {
                bookPOJO.setTime(time.text());
            }
            Element genre = book.selectFirst(".bookListItemGenreLink");
            if (genre != null) {
                if (!genre.attr("href").isEmpty()) {
                    bookPOJO.setUrlGenre(Url.SERVER_KNIGOBLUD + genre.attr("href"));
                }
                if (!genre.text().isEmpty()) {
                    bookPOJO.setGenre(genre.text());
                }
            }
            for (Element metaBlock : book.getElementsByClass("bookListItemMetaBlock")) {
                String metaBlocktext = metaBlock.text();
                if (metaBlocktext.contains("📚")) {
                    Element aTag = metaBlock.selectFirst("a");
                    if (aTag != null) {
                        if (!aTag.attr("href").isEmpty()) {
                            bookPOJO.setUrlSeries(Url.SERVER_KNIGOBLUD + aTag.attr("href"));
                        }
                        if (!aTag.text().isEmpty()) {
                            bookPOJO.setSeries(aTag.text());
                        }
                    }
                } else {
                    boolean author = metaBlocktext.contains("✍");
                    boolean reader = metaBlocktext.contains("🎙");
                    Element first = child(metaBlock, 1);
                    Element second = child(metaBlock, 3);
                    if (author && first != null) {
                        if (!first.attr("href").isEmpty()) {
                            bookPOJO.setUrlAutor(Url.SERVER_KNIGOBLUD + first.attr("href"));
                        }
                        if (!first.text().isEmpty()) {
                            bookPOJO.setAutor(first.text());
                        }
                    }
                    Element readerElement = author ? second : first;
                    if (reader && readerElement != null) {
                        if (!readerElement.attr("href").isEmpty()) {
                            bookPOJO.setUrlArtist(Url.SERVER_KNIGOBLUD + readerElement.attr("href"));
                        }
                        if (!readerElement.text().isEmpty()) {
                            bookPOJO.setArtist(readerElement.text());
                        }
                    }
                }
            }
            return bookPOJO;
        });
        return ProviderPage.of(result, hasMore);
    }

    private static Element child(Element parent, int index) {
        return parent != null && parent.childrenSize() > index ? parent.child(index) : null;
    }

    public static DescriptionPOJO parseDescription(Document document) {

        DescriptionPOJO descriptionPOJO = new DescriptionPOJO();

        Element img = document.getElementById("BookCoverImage");
        if (img != null) {
            descriptionPOJO.setPoster(img.absUrl("src"));
        }

        Elements desc = document.getElementsByClass("BookDescriptionContent");
        if (desc.size() != 0) {
            descriptionPOJO.setDescription(desc.first().ownText());
        }

        Elements clock = document.getElementsByClass("PageTitle_Subtitle");
        if (clock.size() != 0) {
            descriptionPOJO.setTime(clock.first().ownText());
        }

        Elements seriesConteiner = document.getElementsByClass("BookDescription BookSeries");
        if (seriesConteiner.size() != 0) {
            Elements series = seriesConteiner.first().getElementsByTag("a");
            if (series.size() != 0) {
                descriptionPOJO.setSeries(series.first().text());
                descriptionPOJO.setSeriesUrl(BookPOJO.absUrl(series.first(), "href", ""));
            }
        }


        Element metaBlock = document.getElementsByClass("BookMetaBlock").first();
        if (metaBlock != null) {
            for (Element info : metaBlock.getElementsByClass("BookMetaBlockLine")) {
                Element a = info.getElementsByTag("a").first();
                if (a == null) {
                    continue;
                }
                if (info.text().contains("✍")) {
                    descriptionPOJO.setAutor(a.text());
                    descriptionPOJO.setAutorUrl(BookPOJO.absUrl(a, "href", ""));
                } else if (info.text().contains("🎙")) {
                    descriptionPOJO.setArtist(a.text());
                    descriptionPOJO.setArtistUrl(BookPOJO.absUrl(a, "href", ""));
                } else if (info.text().contains("📕")) {
                    descriptionPOJO.setGenre(a.text());
                    descriptionPOJO.setGenreUrl(BookPOJO.absUrl(a, "href", ""));
                }
            }
        }

        Element titleElement = document.getElementsByTag("h1").first();
        if (titleElement != null) {
            descriptionPOJO.setTitle(titleElement.text().trim());
        }

        return descriptionPOJO;
    }

    /** A book opened by its link. */
    public static BookPOJO parseBook(Document document, String url) {
        BookPOJO bookPOJO = new BookPOJO();

        Element img = document.getElementById("BookCoverImage");
        if (img != null) {
            bookPOJO.setPhoto(img.absUrl("src"));
        }

        Elements desc = document.getElementsByClass("BookDescriptionContent");
        if (desc.size() != 0) {
            bookPOJO.setDesc(desc.first().ownText());
        }

        Elements clock = document.getElementsByClass("PageTitle_Subtitle");
        if (clock.size() != 0) {
            bookPOJO.setTime(clock.first().ownText());
        }

        Elements seriesConteiner = document.getElementsByClass("BookDescription BookSeries");
        if (seriesConteiner.size() != 0) {
            Elements series = seriesConteiner.first().getElementsByTag("a");
            if (series.size() != 0) {
                bookPOJO.setSeries(series.first().text());
                bookPOJO.setUrlSeries(BookPOJO.absUrl(series.first(), "href", ""));
            }
        }


        Elements elements = document.getElementsByClass("BookMetaBlock");
        if (elements.size() != 0) {
            Elements infos = elements.first().getElementsByClass("BookMetaBlockLine");
            for (Element info : infos) {
                if (info.text().contains("✍")) {
                    Elements element = info.getElementsByTag("a");
                    if (element.size() != 0) {
                        String autor = element.first().text();
                        bookPOJO.setAutor(autor);
                        bookPOJO.setUrlAutor(BookPOJO.absUrl(element.first(), "href", ""));
                    }
                } else if (info.text().contains("🎙")) {
                    Elements element = info.getElementsByTag("a");
                    if (element.size() != 0) {
                        bookPOJO.setArtist(element.first().text());
                        bookPOJO.setUrlArtist(BookPOJO.absUrl(element.first(), "href", ""));
                    }
                } else if (info.text().contains("📕")) {
                    Elements element = info.getElementsByTag("a");
                    if (element.size() != 0) {
                        bookPOJO.setGenre(element.first().text());
                        bookPOJO.setUrlGenre(BookPOJO.absUrl(element.first(), "href", ""));
                    }
                }
            }
        }

        Elements titleElement = document.getElementsByTag("h1");
        if (titleElement.size() != 0) {
            String name = titleElement.first().text().trim();
            bookPOJO.setName(name);
        }
        bookPOJO.setUrl(url);
        return bookPOJO;
    }

    /** The {@code KB.playerInit} playlist. */
    public static ArrayList<AudioPOJO> parseTracks(Document doc, String bookUrl) {
        return Players.xsPlaylist(Json.object(Players.playerScript(doc, "KB.playerInit(", '{')), bookUrl,
                text(doc.selectFirst("h1")), "knigoblud player");
    }

    /** The series block of the book page, the open book included (without a link). */
    public static ArrayList<SeriesPOJO> parseSeries(Document doc) {
        Element block = doc.selectFirst(".BookSeries");
        if (block == null) {
            return new ArrayList<>();
        }
        return each(block.getElementsByClass("BookDescriptionSeriesItem"), item -> {
            SeriesPOJO seriesPOJO = new SeriesPOJO();
            String number = text(item, ".BookDescriptionSeriesItemIndex");
            seriesPOJO.setNumber(number);
            seriesPOJO.setName(item.text().replace(number, "").trim());
            Element a = item.tagName().equals("a") ? item : item.selectFirst("a");
            if (a != null) {
                String url = a.absUrl("href");
                if (!url.isEmpty()) {
                    seriesPOJO.setUrl(url);
                }
            }
            return seriesPOJO;
        });
    }

    /** The search page: its book cards as hits. */
    public static ArrayList<OtherSourceModel.Hit> parseSearch(Document doc) {
        Element list = doc.getElementById("BL");
        if (list == null) {
            return new ArrayList<>();
        }
        return each(list.getElementsByClass("bookListItem"), book -> {
            String author = "";
            String reader = "";
            for (Element block : book.getElementsByClass("bookListItemMetaBlock")) {
                if (author.isEmpty()) {
                    author = linkAfterMarker(block, "✍");
                }
                if (reader.isEmpty()) {
                    reader = linkAfterMarker(block, "🎙");
                }
            }
            return new OtherSourceModel.Hit(href(book, "a.bookListItemCover"), text(book, ".bookListItemCoverNameText"),
                    author, reader);
        });
    }

    /** Text of the link right after the marker span (✍ author, 🎙 reader); "" when absent. */
    private static String linkAfterMarker(Element block, String marker) {
        for (Element span : block.getElementsByTag("span")) {
            Element next = span.nextElementSibling();
            if (span.text().contains(marker) && next != null && next.tagName().equals("a")) {
                return next.text().trim();
            }
        }
        return "";
    }

    /** The genres, in the site's order; a new list on each call. */
    public static ArrayList<GenrePOJO> genreList() {
        ArrayList<GenrePOJO> result = new ArrayList<>();
        result.add(new GenrePOJO("Фантастика, фэнтези", Url.SERVER_KNIGOBLUD+"/9d47c7aa-9e2e-4fe0-bcd5-14f7d4874198"));
        result.add(new GenrePOJO("Детективы, триллеры, боевики", Url.SERVER_KNIGOBLUD+"/4daab267-3592-4604-9e34-8f3b6a1db9fe"));
        result.add(new GenrePOJO("Аудиоспектакли, радиопостановки и литературные чтения", Url.SERVER_KNIGOBLUD+"/0efa0796-8734-4735-ac03-d4eca1ccb013"));
        result.add(new GenrePOJO("Бизнес, личностный рост", Url.SERVER_KNIGOBLUD+"/8ea14af7-f600-4e30-a0eb-67b615cdb701"));
        result.add(new GenrePOJO("Биографии, мемуары, ЖЗЛ", Url.SERVER_KNIGOBLUD+"/760dd554-482b-4817-8283-5b9ae9e76431"));
        result.add(new GenrePOJO("Для детей, аудиосказки, стишки", Url.SERVER_KNIGOBLUD+"/5982eae9-3868-4593-a9b0-b3378370345f"));
        result.add(new GenrePOJO("История, культурология", Url.SERVER_KNIGOBLUD+"/31d39d95-7bf5-420b-9db4-66c448785a07"));
        result.add(new GenrePOJO("Классика", Url.SERVER_KNIGOBLUD+"/0c6d4928-555a-4671-99e9-9bcf9a37d382"));
        result.add(new GenrePOJO("Медицина, здоровье", Url.SERVER_KNIGOBLUD+"/f00832de-609c-4cc8-9017-2571caa4bfb2"));
        result.add(new GenrePOJO("На иностранных языках", Url.SERVER_KNIGOBLUD+"/fb8d64ce-902d-44aa-9c21-77cb85a5b418"));
        result.add(new GenrePOJO("Научно-популярное", Url.SERVER_KNIGOBLUD+"/9da19d35-32ee-41e7-ab8c-5ec554fcdd8a"));
        result.add(new GenrePOJO("Обучение", Url.SERVER_KNIGOBLUD+"/7445b383-418a-475a-8374-ef2822d0552c"));
        result.add(new GenrePOJO("Поэзия", Url.SERVER_KNIGOBLUD+"/a1d4487a-12d6-4280-b1a2-4c204bd6908a"));
        result.add(new GenrePOJO("Приключения, военные приключения", Url.SERVER_KNIGOBLUD+"/e7d0d5bc-210a-4fc6-9598-19baf47c9b13"));
        result.add(new GenrePOJO("Психология, философия", Url.SERVER_KNIGOBLUD+"/09afc417-fa54-4b56-bb54-f950296a0b50"));
        result.add(new GenrePOJO("Разное", Url.SERVER_KNIGOBLUD+"/697856ad-1d48-48d5-ad20-5f00cc169f75"));
        result.add(new GenrePOJO("Ранобэ", Url.SERVER_KNIGOBLUD+"/07bc7998-8f32-49a1-8048-07a2c2294a11"));
        result.add(new GenrePOJO("Религия", Url.SERVER_KNIGOBLUD+"/4c37cad9-8ee6-4ce7-b62e-b52ef717952c"));
        result.add(new GenrePOJO("Роман, проза", Url.SERVER_KNIGOBLUD+"/61c37161-1899-4253-a4c3-9417f4b5c1c5"));
        result.add(new GenrePOJO("Ужасы, мистика, хоррор", Url.SERVER_KNIGOBLUD+"/120a1664-12e5-4ac6-853f-792f6a8007e9"));
        result.add(new GenrePOJO("Эзотерика, Нетрадиционные религиозно-философские учения", Url.SERVER_KNIGOBLUD+"/e4117eca-c027-4f80-8004-3851d9b562c7"));
        result.add(new GenrePOJO("Юмор, сатира", Url.SERVER_KNIGOBLUD+"/70bb89e8-65f7-414d-b98d-107878734c89"));
        return result;
    }
}
