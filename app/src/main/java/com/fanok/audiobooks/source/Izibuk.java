package com.fanok.audiobooks.source;

import static com.fanok.audiobooks.pojo.BookPOJO.absUrl;
import static com.fanok.audiobooks.source.Html.cards;
import static com.fanok.audiobooks.source.Html.each;
import static com.fanok.audiobooks.source.Html.lastPage;
import static com.fanok.audiobooks.source.Html.rows;
import static com.fanok.audiobooks.source.Html.text;
import static com.fanok.audiobooks.source.Json.object;
import static com.fanok.audiobooks.source.Players.playerScript;

import com.fanok.audiobooks.Url;
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
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import okhttp3.HttpUrl;

/**
 * izib.uk: plain GETs, every request with the site as Referer. Listings ({@code /?p=N}, {@code /genreX?p=},
 * {@code /authorX?p=}, {@code /readerX?p=}, {@code /serieX?p=}) are read from the mobile site
 * ({@link #MOBILE}), whose cards carry the duration; popularity is the listing with {@code sort_pop=1} (all time, the
 * genre kept). Search ({@code /search?q=Q&p=N}), the genre list and the author and reader indexes and searches use
 * the desktop site. Book pages carry an XSPlayer whose tracks are files under {@code mp3_url_prefix} signed with
 * {@code sign}, and list the series. No comments; other narrations are found by searching the title and author.
 */
public final class Izibuk implements SourceParser {

    /** izibuk's mobile site: the same listings, sorts and pages, but its cards carry the duration. */
    private static final String MOBILE = "https://pda.izib.uk";

    private static final List<Sort.Entry> MENU = Sort.menu(Sort.NEW, Sort.POPULAR);

    // The site's class names are build hashes (_ccb9b7, _802db0...) that a rebuild renames: elements are found by
    // their links, the schema.org Book markup, ids and labels instead.
    /** A book card: the block whose cover image links to the book. */
    private static final String CARD = "div:has(> a[href^=/art] > img)";
    /** Scopes a book-page selector to the book itself, past the genre links of the site menu. */
    private static final String BOOK = "[itemtype$=/Book] ";
    /** The genre list: its rows link to a genre and hold a name (the site menu's genre links hold none). */
    private static final String GENRE_LIST = "div:has(> a[href^=/genre] > span)";
    /** The book page's Автор(ы) / Читает (Читают) / Время rows. */
    private static final String INFO = "div:has(> div > b:matchesOwn(^(Авторы?|Чита(ет|ют)|Время):$))";

    // ---------------------------------------------------------------- SourceParser

    @Override
    public String referer() {
        return Url.SERVER_IZIBUK + "/";
    }

    @Override
    public ProviderPage<BookPOJO> listing(Document page, String url, int pageNo) {
        return parseListing(page, url, pageNo);
    }

    /**
     * The listing from the mobile site. Its index has 7 cards on page 1 and 20 from page 2 on: page n is its page
     * n + 1, page 1 with its page 1 in front, so a short first page never leaves a tablet grid without anything to
     * scroll. Series cards carry no genre or author: they are read once from the first book's page.
     */
    @Override
    public ProviderPage<BookPOJO> loadListing(String url, int pageNo) throws IOException {
        String mobile = url.replace(Url.SERVER_IZIBUK, MOBILE);
        ProviderPage<BookPOJO> result;
        if (mobile.startsWith(MOBILE + "/?")) {
            result = parseListing(fetch(mobile.replaceFirst("([?&]p=)\\d+", "$1" + (pageNo + 1))), url, pageNo + 1);
            if (pageNo <= 1) {
                ArrayList<BookPOJO> items = new ArrayList<>(parseListing(fetch(mobile), url, 1).items);
                items.addAll(result.items);
                result = ProviderPage.of(items, result.hasMore);
            }
        } else {
            result = parseListing(fetch(mobile), url, pageNo);
        }
        if (url.contains("serie") && !result.items.isEmpty()) {
            Document book = fetch(result.items.get(0).getUrl());
            Element genre = book.selectFirst(BOOK + "a[href^=/genre]");
            Element author = book.selectFirst("[itemprop=author] a");
            for (BookPOJO item : result.items) {
                try {
                    if (genre != null && item.getUrlGenre() == null) {
                        item.setGenre(genre.text());
                        item.setUrlGenre(Url.SERVER_IZIBUK + genre.attr("href") + "?p=");
                    }
                    if (author != null && item.getUrlAutor() == null) {
                        item.setAutor(author.text());
                        item.setUrlAutor(Url.SERVER_IZIBUK + author.attr("href") + "?p=");
                    }
                } catch (RuntimeException ignored) {
                    // an empty genre or author label is not worth losing the card
                }
            }
        }
        return result;
    }

    @Override
    public String pageUrl(String url, int page) {
        if (url.contains("genre") && !url.contains(Url.SERVER_IZIBUK)) {
            return url.replace("<page>", Integer.toString(page));
        }
        return url + page + "/";
    }

    /** Author, reader and series listings are not re-sorted. */
    @Override
    public List<Sort.Entry> sorts(String url) {
        boolean noSort = url.contains("reader") || url.contains("author") || url.contains("serie")
                || url.contains("cikl");
        if (noSort) {
            return Collections.<Sort.Entry>emptyList();
        }
        return MENU;
    }

    /** The index and a genre rank by popularity (all time, no period) with {@code sort_pop=1}, the genre kept. */
    @Override
    public String sortUrl(Sort sort, String listingUrl) {
        String newest = listingUrl.replace("sort_pop=1&", "");
        switch (sort) {
            case NEW: return newest;
            case POPULAR: return newest.replaceFirst("\\?", "?sort_pop=1&");
            default: return null;
        }
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
            case BOOKS: return Url.SERVER_IZIBUK + "/search?q=" + q + "&p=" + page;
            case AUTHORS: return Url.SERVER_IZIBUK + "/authors?q=" + q + "&p=" + page;
            case PERFORMERS: return Url.SERVER_IZIBUK + "/readers?q=" + q + "&p=" + page;
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

    /**
     * The search for title and author: more than one card means other narrations. Only whether there are any is
     * decided here; the tab searches again ({@link #otherReaders}).
     */
    @Override
    public void probeOtherReaders(DescriptionPOJO description, String bookUrl) throws IOException {
        HttpUrl search = Objects.requireNonNull(HttpUrl.parse(Url.SERVER_IZIBUK + "/search")).newBuilder()
                .addQueryParameter("q", description.getTitle() + " " + description.getAutor())
                .build();
        Element list = fetch(search.toString()).getElementById("books_list");
        description.setOtherReader(list != null && list.select(CARD).size() > 1);
    }

    /** The search for title and author: the hits of this book with another reader. */
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

    /** The search page without a page number, as the other-source lookup has always asked it. */
    @Override
    public String otherSourceSearchUrl(String terms, int page) {
        return HttpUrl.get(Url.SERVER_IZIBUK).newBuilder().addPathSegment("search")
                .addQueryParameter("q", terms).build().toString();
    }

    @Override
    public ArrayList<OtherSourceModel.Hit> otherSourceHits(Document searchPage) {
        return parseSearch(searchPage);
    }

    // ---------------------------------------------------------------- pure parsers

    /** Index, genre, author, reader and series listings (desktop or mobile) and search results. */
    public static ProviderPage<BookPOJO> parseListing(Document document, String url, int page) {
        boolean hasMore = lastPage(document.select(
                "#books_updates_list__pn a[data-page], #books_list__pn a[data-page]")) > page;
        Element listParent = document.getElementById("books_list");
        if (listParent == null) {
            listParent = document.getElementById("books_updates_list");
        }
        if (listParent == null) {
            listParent = document.body(); // the mobile series page has no list container
        }
        final String pageTitle = title(document);
        // the listing itself as a link of its cards: without the page (and sort) of this request
        final String listing = url.replaceFirst("[?#].*", "") + "?p=";
        Elements raw = listParent.select(CARD);
        ArrayList<BookPOJO> result = cards(raw, book -> {
            BookPOJO bookPOJO = new BookPOJO();
            Element genre = book.selectFirst("a[href^=/genre]");
            if (genre != null) {
                bookPOJO.setGenre(genre.text());
                bookPOJO.setUrlGenre(Url.SERVER_IZIBUK + genre.attr("href") + "?p=");
            } else if (url.contains("genre")) {
                bookPOJO.setUrlGenre(listing);
                if (pageTitle != null) {
                    bookPOJO.setGenre(pageTitle);
                }
            }
            Element image = book.selectFirst("a[href^=/art] > img");
            if (image != null) {
                bookPOJO.setPhoto(image.attr("src"));
            }
            Element content = book.selectFirst("div:has(> div > a[href^=/art])");
            if (content == null) {
                return null;
            }
            Element titleParent = content.selectFirst("> div:has(> a[href^=/art])");
            if (titleParent != null) {
                if (!titleParent.select("> span:matchesOwn(^(Ф|Фрагмент)$)").isEmpty()) {
                    return null; // fragment: "Фрагмент" on the desktop site, "Ф" on the mobile one
                }
                Element title = titleParent.selectFirst("> a[href^=/art]");
                if (title != null) {
                    bookPOJO.setUrl(Url.SERVER_IZIBUK + title.attr("href"));
                    bookPOJO.setName(title.text());
                }
            }
            // desktop cards group these rows in a block, mobile ones list them directly and add the duration
            for (Element link : content.select("a[href^=/author], a[href^=/reader], a[href^=/serie]")) {
                String href = link.attr("href");
                String name = link.text();
                if (href.contains("author")) {
                    bookPOJO.setUrlAutor(Url.SERVER_IZIBUK + href + "?p=");
                    bookPOJO.setAutor(name);
                } else if (href.contains("reader")) {
                    bookPOJO.setUrlArtist(Url.SERVER_IZIBUK + href + "?p=");
                    bookPOJO.setArtist(name);
                } else if (href.contains("serie")) {
                    bookPOJO.setUrlSeries(Url.SERVER_IZIBUK + href + "?p=");
                    bookPOJO.setSeries(name);
                }
            }
            // "8 ч. 12 мин." or "41 мин." (mobile only); the hidden "(41:31)" next to the title does not match
            Element time = content.selectFirst("span:matchesOwn(^\\d+ (ч|мин)\\.)");
            if (time != null && nonEmpty(time.text()) != null) {
                bookPOJO.setTime(time.text());
            }
            if (bookPOJO.getAutor() == null && url.contains("author")) {
                bookPOJO.setUrlAutor(listing);
                if (pageTitle != null) {
                    bookPOJO.setAutor(pageTitle);
                }
            }
            if (bookPOJO.getArtist() == null && url.contains("reader")) {
                bookPOJO.setUrlArtist(listing);
                if (pageTitle != null) {
                    bookPOJO.setArtist(pageTitle);
                }
            }
            if (bookPOJO.getSeries() == null && url.contains("serie")) {
                bookPOJO.setUrlSeries(listing);
                if (pageTitle != null) {
                    bookPOJO.setSeries(pageTitle);
                }
            }
            // mobile: the hidden "book<N>_desc"; desktop: the text-only block after the title's
            Element description = content.selectFirst(
                    "[id$=_desc], div:has(> a[href^=/art]) ~ div:not(:has(a)):matchesOwn(\\S)");
            if (description != null) {
                bookPOJO.setDesc(description.text());
            }
            return bookPOJO;
        });
        return ProviderPage.of(result, hasMore, raw.size());
    }

    /** The listing's name: the desktop h1; on the mobile page the bold series name, else the breadcrumb after its link. */
    private static String title(Document document) {
        Element title = document.selectFirst("h1, div:containsOwn(Серия) > b");
        if (title != null) {
            return nonEmpty(title.text());
        }
        // the breadcrumb starts at the genre, author or reader index (the site menu has the same links)
        Element crumbs = document.selectFirst(
                "div:not(#topmenu *):has(> a[href=/genres], > a[href=/authors], > a[href=/readers])");
        if (crumbs == null) {
            return null;
        }
        String text = crumbs.text();
        Element first = crumbs.selectFirst("a");
        return nonEmpty(first != null && text.startsWith(first.text()) ? text.substring(first.text().length()) : text);
    }

    private static String nonEmpty(String s) {
        return s == null || s.trim().isEmpty() ? null : s.trim();
    }

    /** The genre list: one page. */
    public static ProviderPage<GenrePOJO> parseGenres(Document doc) {
        Element items = doc.selectFirst(GENRE_LIST);
        if (items == null) {
            return ProviderPage.empty();
        }
        return ProviderPage.of(rows(items.children(), item -> {
            GenrePOJO genrePOJO = new GenrePOJO();
            genrePOJO.setUrl(Url.SERVER_IZIBUK + item.attr("href") + "?p=");
            Elements children = item.children();
            if (children.size() == 2) {
                genrePOJO.setName(children.first().text());
                genrePOJO.setDescription(children.last().text());
            }
            return genrePOJO;
        }), false);
    }

    /** The author or reader index and their search. */
    public static ProviderPage<GenrePOJO> parsePeople(Document doc, int page) {
        boolean hasMore = lastPage(doc.select(
                "#authors_list__pn a[data-page], #readers_list__pn a[data-page]")) > page;
        Element itemParent = doc.getElementById("authors_list");
        if (itemParent == null) {
            itemParent = doc.getElementById("readers_list");
        }
        if (itemParent == null) {
            return ProviderPage.empty();
        }
        return ProviderPage.of(rows(itemParent.children(), item -> {
            GenrePOJO genrePOJO = new GenrePOJO();
            genrePOJO.setUrl(Url.SERVER_IZIBUK + item.attr("href") + "?p=");
            Elements children = item.children();
            if (children.size() == 3) {
                genrePOJO.setName(children.get(1).text());
                genrePOJO.setDescription(children.last().text());
            }
            return genrePOJO;
        }), hasMore);
    }

    public static DescriptionPOJO parseDescription(Document document) {
        DescriptionPOJO descriptionPOJO = new DescriptionPOJO();

        Elements elements = document.getElementsByAttributeValue("itemprop", "name");
        if (elements.size() != 0) {
            descriptionPOJO.setTitle(elements.first().text());
        }

        Element img = document.selectFirst(BOOK + "img");
        if (img != null) {
            descriptionPOJO.setPoster(img.absUrl("src"));
        }

        Element infoParent = document.selectFirst(INFO);
        if (infoParent != null) {
            for (Element element : infoParent.children()) {
                String text = element.text();
                Element a = element.getElementsByTag("a").first();
                if (text.contains("Автор")) {
                    if (a != null) {
                        descriptionPOJO.setAutorUrl(absUrl(a, "href", "?p="));
                        descriptionPOJO.setAutor(a.text());
                    }
                } else if (text.startsWith("Чита")) {
                    if (a != null) {
                        descriptionPOJO.setArtistUrl(absUrl(a, "href", "?p="));
                        descriptionPOJO.setArtist(a.text());
                    }
                } else if (text.contains("Время")) {
                    descriptionPOJO.setTime(text.replace("Время:", "").trim());
                }
            }
        }

        Element a = document.selectFirst(BOOK + "a[href^=/serie]");
        if (a != null) {
            descriptionPOJO.setSeriesUrl(absUrl(a, "href", "?p="));
            descriptionPOJO.setSeries(a.text());
        }

        Element genreElement = document.selectFirst(BOOK + "a[href^=/genre]");
        if (genreElement != null) {
            descriptionPOJO.setGenreUrl(absUrl(genreElement, "href", "?p="));
            descriptionPOJO.setGenre(genreElement.text());
        }

        Elements descriptionList = document.getElementsByAttributeValue("itemprop", "description");
        if (descriptionList.size() != 0) {
            descriptionPOJO.setDescription(descriptionList.first().text());
        }

        return descriptionPOJO;
    }

    /** A book opened by its link. */
    public static BookPOJO parseBook(Document document, String url) {
        BookPOJO bookPOJO = new BookPOJO();

        bookPOJO.setUrl(url);

        Elements elements = document.getElementsByAttributeValue("itemprop", "name");
        if (elements != null && elements.size() != 0) {
            String name = elements.first().text();
            if (name != null) {
                bookPOJO.setName(name);
            }
        }

        Element img = document.selectFirst(BOOK + "img");
        if (img != null) {
            String src = img.absUrl("src");
            if (src != null) {
                bookPOJO.setPhoto(src);
            }
        }

        Element infoParent = document.selectFirst(INFO);
        if (infoParent != null) {
            Elements info = infoParent.children();
            if (info != null) {
                for (Element element : info) {
                    String text = element.text();
                    if (text != null) {
                        if (text.contains("Автор")) {
                            Elements aTag = element.getElementsByTag("a");
                            if (aTag != null && aTag.size() != 0) {
                                Element a = aTag.first();
                                String href = a.absUrl("href");
                                if (!href.isEmpty()) {
                                    bookPOJO.setUrlAutor(href + "?p=");
                                }
                                String name = a.text();
                                if (name != null) {
                                    bookPOJO.setAutor(name);
                                }
                            }
                        } else if (text.startsWith("Чита")) {
                            Elements aTag = element.getElementsByTag("a");
                            if (aTag != null && aTag.size() != 0) {
                                Element a = aTag.first();
                                String href = a.absUrl("href");
                                if (!href.isEmpty()) {
                                    bookPOJO.setUrlArtist(href + "?p=");
                                }
                                String name = a.text();
                                if (name != null) {
                                    bookPOJO.setArtist(name);
                                }
                            }
                        } else if (text.contains("Время")) {
                            String time = text.replace("Время:", "").trim();
                            bookPOJO.setTime(time);
                        }
                    }
                }
            }
        }

        Element a = document.selectFirst(BOOK + "a[href^=/serie]");
        if (a != null) {
            String href = a.absUrl("href");
            if (!href.isEmpty()) {
                bookPOJO.setUrlSeries(href + "?p=");
            }
            String text = a.text();
            if (text != null) {
                bookPOJO.setSeries(text);
            }
        }

        Element genreElement = document.selectFirst(BOOK + "a[href^=/genre]");
        if (genreElement != null) {
            String href = genreElement.absUrl("href");
            if (!href.isEmpty()) {
                bookPOJO.setUrlGenre(href + "?p=");
            }
            String text = genreElement.text();
            if (text != null) {
                bookPOJO.setGenre(text);
            }
        }

        Elements descriptionList = document.getElementsByAttributeValue("itemprop", "description");
        if (descriptionList != null && descriptionList.size() != 0) {
            String text = descriptionList.first().text();
            if (text != null) {
                bookPOJO.setDesc(text);
            }
        }
        return bookPOJO;
    }

    /** The {@code new XSPlayer({...})} playlist: files under {@code mp3_url_prefix}, signed with {@code sign}. */
    public static ArrayList<AudioPOJO> parseTracks(Document doc, String bookUrl) {
        return Players.xsTracks(object(playerScript(doc, "new XSPlayer(", '{')), bookUrl,
                text(doc.selectFirst("[itemprop=name]")), "izibuk player");
    }

    /** The series block of the book page, the open book included (without a link). */
    public static ArrayList<SeriesPOJO> parseSeries(Document doc) {
        Element block = doc.selectFirst(BOOK + "div:has(> div > a[href^=/serie])");
        if (block == null) {
            return new ArrayList<>();
        }
        // a row per book: its number, then a link, or the open book's name in bold
        return each(block.select("> div:has(> a[href^=/art], > strong)"), item -> {
            SeriesPOJO seriesPOJO = new SeriesPOJO();
            String number = text(item, "> span");
            seriesPOJO.setNumber(number);
            Element a = item.selectFirst("a");
            if (a != null) {
                String url = a.absUrl("href");
                if (!url.isEmpty()) {
                    seriesPOJO.setUrl(url);
                }
                seriesPOJO.setName(item.text().replace(number, "").trim());
            } else {
                seriesPOJO.setName(text(item, "strong"));
            }
            return seriesPOJO;
        });
    }

    /** The search page: its book cards as hits, the readers of a card joined. */
    public static ArrayList<OtherSourceModel.Hit> parseSearch(Document doc) {
        Element list = doc.getElementById("books_list");
        if (list == null) {
            list = doc.getElementById("books_updates_list");
        }
        if (list == null) {
            return new ArrayList<>();
        }
        return each(list.select(CARD), book -> {
            Element a = book.selectFirst("a[href^=/art]:not(:has(img))");
            if (a == null) {
                return null;
            }
            String author = "";
            List<String> readers = new ArrayList<>();
            for (Element person : book.select("a[href^=/author], a[href^=/reader]")) {
                String link = person.attr("href");
                if (link.contains("author") && author.isEmpty()) {
                    author = person.text().trim();
                } else if (link.contains("reader")) {
                    readers.add(person.text().trim());
                }
            }
            return new OtherSourceModel.Hit(a.absUrl("href"), a.text().trim(), author, join(readers));
        });
    }

    private static String join(List<String> parts) {
        StringBuilder s = new StringBuilder();
        for (String part : parts) {
            if (!part.isEmpty()) {
                s.append(s.length() == 0 ? "" : ", ").append(part);
            }
        }
        return s.toString();
    }
}
