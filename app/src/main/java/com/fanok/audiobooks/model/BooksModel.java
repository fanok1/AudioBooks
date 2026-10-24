package com.fanok.audiobooks.model;


import android.content.Context;

import com.fanok.audiobooks.R;
import com.fanok.audiobooks.Url;
import com.fanok.audiobooks.net.SiteHttp;
import com.fanok.audiobooks.pojo.BookPOJO;
import com.fanok.audiobooks.search.Provider;
import com.fanok.audiobooks.search.ProviderPage;
import com.fanok.audiobooks.search.SearchUrls;
import io.reactivex.Single;
import okhttp3.HttpUrl;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import org.jsoup.Connection;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

/**
 * Book listings and book search: one fetch per page through {@link SiteHttp}, one parser per provider.
 * Parsers are static and take a {@link Document}, so they are tested from saved pages.
 */
public class BooksModel implements com.fanok.audiobooks.interface_pacatge.books.BooksModel {

    /** ABMP3 returns every hit on one page; it is sliced client-side. */
    static final int ABMP3_PAGE_SIZE = 40;

    /** Results per audioboo search page. */
    static final int AUDIOBOO_SEARCH_PAGE_SIZE = 50;

    @Override
    public Single<ProviderPage<BookPOJO>> getBooks(String url, int page, String nameModel, Context context) {
        return Single.fromCallable(() -> listing(url, page, nameModel, context));
    }

    @Override
    public ProviderPage<BookPOJO> search(Provider provider, String query, int page) throws IOException {
        String url = SearchUrls.url(provider, SearchUrls.Category.BOOKS, query, page);
        if (url == null) {
            return ProviderPage.empty();
        }
        switch (provider) {
            case KNIGAVUHE: return parseKnigavuhe(SiteHttp.get(url), url, page);
            case IZIBUK: return parseIzibuk(SiteHttp.get(url), url, page);
            case ABMP3: return parseAbmp3Search(SiteHttp.get(url), page);
            case AKNIGA: return parseAkniga(SiteHttp.get(url), page);
            case BAZA_KNIG: return parseBazaKnig(SiteHttp.get(url), page);
            case KNIGOBLUD: return parseKnigoblud(SiteHttp.get(url), page);
            case AUDIOBOO: return parseAudiobooSearch(postForm(url), page);
            case OTRUB: return parseOtrub(SiteHttp.get(url), page);
            case POLEKNIG: return parsePoleknig(SiteHttp.get(url), page);
        }
        return ProviderPage.empty();
    }

    ProviderPage<BookPOJO> listing(String url, int page, String nameModel, Context context) throws IOException {
        Provider provider = Provider.ofUrl(url);
        if (provider == null) {
            return ProviderPage.empty();
        }
        switch (provider) {
            case KNIGAVUHE: return parseKnigavuhe(SiteHttp.get(url), url, page);
            case IZIBUK: return izibukListing(url, page);
            case ABMP3: return parseAbmp3(SiteHttp.get(url), page);
            case AKNIGA: return parseAkniga(SiteHttp.get(url), page);
            case BAZA_KNIG: return parseBazaKnig(SiteHttp.get(url), page);
            case KNIGOBLUD: return parseKnigoblud(SiteHttp.get(url), page);
            case AUDIOBOO: return parseAudioboo(audiobooListing(url, nameModel, context), page);
            case OTRUB: return parseOtrub(SiteHttp.get(url), page);
            case POLEKNIG: return parsePoleknig(SiteHttp.get(url), page);
        }
        return ProviderPage.empty();
    }

    // ---------------------------------------------------------------- fetch helpers

    /** DLE search endpoints take their parameters as a form: POST the query string of {@code url}. */
    static Document postForm(String url) throws IOException {
        HttpUrl parsed = HttpUrl.parse(url);
        if (parsed == null) {
            throw new IOException("bad url " + url);
        }
        Connection connection = SiteHttp.connect(parsed.newBuilder().query(null).build().toString())
                .method(Connection.Method.POST);
        for (String name : parsed.queryParameterNames()) {
            connection.data(name, parsed.queryParameter(name));
        }
        return SiteHttp.parse(SiteHttp.execute(connection));
    }

    private static Document audiobooListing(String url, String nameModel, Context context) throws IOException {
        String sortVal = "date";
        if (nameModel.contains(context.getString(R.string.order_reting))) {
            sortVal = "rating";
        } else if (nameModel.contains(context.getString(R.string.order_popular))) {
            sortVal = "views";
        } else if (nameModel.contains(context.getString(R.string.order_coments))) {
            sortVal = "comm";
        } else if (nameModel.contains(context.getString(R.string.order_title))) {
            sortVal = "title";
        } else if (nameModel.contains(context.getString(R.string.order_duration))) {
            sortVal = "time";
        }
        Connection connection = SiteHttp.connect(url)
                .method(Connection.Method.POST)
                .data("xsort", "1")
                .data("xs_field", "defaultsort")
                .data("xs_value", sortVal);
        return SiteHttp.parse(SiteHttp.execute(connection));
    }

    private static ProviderPage<BookPOJO> izibukListing(String url, int page) throws IOException {
        Connection connection = SiteHttp.connect(url)
                .referrer(Url.INDEX_IZIBUK + "1")
                .cookie("sort_pop", com.fanok.audiobooks.Consts.izibuk_reiting ? "1" : "0");
        ProviderPage<BookPOJO> result = parseIzibuk(SiteHttp.parse(SiteHttp.execute(connection)), url, page);
        if (url.contains("serie") && !result.items.isEmpty()) {
            // series cards carry no genre or author: read them once from the first book's page
            Document book = SiteHttp.get(result.items.get(0).getUrl());
            Element genre = book.selectFirst("._7e215f a");
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

    // ---------------------------------------------------------------- parse helpers

    interface Card {
        BookPOJO parse(Element card) throws Exception;
    }

    /** Parses every card on its own: one broken card is skipped, the page survives (SRCH-08). */
    static ArrayList<BookPOJO> cards(Elements cards, Card parser) {
        ArrayList<BookPOJO> out = new ArrayList<>();
        for (Element card : cards) {
            try {
                BookPOJO book = parser.parse(card);
                if (book != null && !book.isNull()) {
                    out.add(book);
                }
            } catch (Exception ignored) {
                // a malformed card (empty title, bad URL, missing block) is not a page failure
            }
        }
        return out;
    }

    /** The largest page number among pager links (text, else {@code data-page}); 0 when there is none. */
    static int lastPage(Elements links) {
        int last = 0;
        for (Element a : links) {
            last = Math.max(last, number(a.text()));
            if (a.hasAttr("data-page")) {
                last = Math.max(last, number(a.attr("data-page")));
            }
        }
        return last;
    }

    private static int number(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String nonEmpty(String s) {
        return s == null || s.trim().isEmpty() ? null : s.trim();
    }

    private static Element child(Element parent, int index) {
        return parent != null && parent.childrenSize() > index ? parent.child(index) : null;
    }

    // ---------------------------------------------------------------- parsers

    static ProviderPage<BookPOJO> parseKnigavuhe(Document doc, String url, int page) {
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
            if (url.contains("/serie/") && (bookPOJO.getSeries() == null || bookPOJO.getUrlSeries() == null)) {
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

    static ProviderPage<BookPOJO> parseAbmp3(Document doc, int page) {
        boolean hasMore = lastPage(doc.select(".pagination li a")) > page;
        Element bookList = doc.selectFirst(".b-posts");
        if (bookList == null) {
            return ProviderPage.empty();
        }
        ArrayList<BookPOJO> result = cards(bookList.getElementsByClass("abook-item"), book -> {
            BookPOJO bookPOJO = new BookPOJO();
            String autorName = "";
            Element img = book.selectFirst("img");
            if (img != null) {
                bookPOJO.setPhoto(img.attr("src"));
            }
            Element bookInfo = book.selectFirst(".content-abook-info");
            if (bookInfo != null && bookInfo.childrenSize() == 3) {
                Element aAutor = bookInfo.child(0).selectFirst("a");
                if (aAutor != null) {
                    autorName = aAutor.text();
                    bookPOJO.setAutor(autorName);
                    bookPOJO.setUrlAutor(Url.SERVER_ABMP3 + aAutor.attr("href") + "?page=");
                }
                Element aReader = bookInfo.child(1).selectFirst("a");
                if (aReader != null) {
                    bookPOJO.setArtist(aReader.text());
                    bookPOJO.setUrlArtist(Url.SERVER_ABMP3 + aReader.attr("href") + "?page=");
                }
                bookPOJO.setTime(bookInfo.child(2).text().trim());
            }
            Element a = book.selectFirst(".abook-title > *");
            if (a != null) {
                bookPOJO.setUrl(Url.SERVER_ABMP3 + a.attr("href"));
                bookPOJO.setName(a.text().replace(autorName + " - ", ""));
            }
            Element aGenre = book.selectFirst(".abook-genre > *");
            if (aGenre != null) {
                bookPOJO.setUrlGenre(Url.SERVER_ABMP3 + aGenre.attr("href") + "?page=");
                bookPOJO.setGenre(aGenre.text());
            }
            Element about = book.selectFirst(".abook-content");
            if (about != null) {
                bookPOJO.setDesc(about.text());
            }
            return bookPOJO;
        });
        return ProviderPage.of(result, hasMore);
    }

    static ProviderPage<BookPOJO> parseAkniga(Document document, int page) {
        boolean hasMore = !document.select(".page__nav .page__nav--next").isEmpty();
        Elements raw = document.getElementsByClass("content__main__articles--item");
        ArrayList<BookPOJO> result = cards(raw, book -> {
            if (!book.getElementsByAttributeValue("href", Url.SERVER_AKNIGA + "/paid/").isEmpty()) {
                return null;
            }
            Element fragment = book.selectFirst(".caption__article-preview");
            if (fragment != null && fragment.text().equals("Фрагмент")) {
                return null;
            }
            BookPOJO bookPOJO = new BookPOJO();
            Element genre = book.selectFirst(".section__title");
            if (genre != null) {
                bookPOJO.setGenre(genre.text());
                bookPOJO.setUrlGenre(genre.attr("href") + "page<page>/");
            }
            for (Element img : book.select("img")) {
                String src = img.attr("data-src");
                if (src.isEmpty()) {
                    src = img.attr("data-original");
                }
                if (src.isEmpty()) {
                    src = img.attr("src");
                }
                if (!src.isEmpty() && !src.contains("blank.gif") && !src.contains("pixel.gif")) {
                    bookPOJO.setPhoto(src);
                    break;
                }
            }
            if (bookPOJO.getPhoto() == null) {
                for (Element bgElem : book.select("[style*='background-image']")) {
                    String style = bgElem.attr("style");
                    int start = style.indexOf("url(");
                    int end = style.indexOf(")", start);
                    if (start >= 0 && end > start) {
                        String imgUrl = style.substring(start + 4, end).replace("'", "").replace("\"", "").trim();
                        if (!imgUrl.isEmpty()) {
                            if (imgUrl.startsWith("//")) {
                                imgUrl = "https:" + imgUrl;
                            } else if (imgUrl.startsWith("/")) {
                                imgUrl = Url.SERVER_AKNIGA + imgUrl;
                            }
                            bookPOJO.setPhoto(imgUrl);
                            break;
                        }
                    }
                }
            }
            for (Element element : book.getElementsByClass("link__action--author")) {
                Element a = element.selectFirst("a");
                if (a == null) {
                    continue;
                }
                String src = a.attr("href");
                String name = a.text();
                Element use = element.selectFirst("use");
                String typeStr = use == null ? "" : use.attr("xlink:href");
                if (typeStr.isEmpty()) {
                    typeStr = src;
                }
                if (typeStr.contains("author")) {
                    if (!src.isEmpty()) bookPOJO.setUrlAutor(src + "page<page>/");
                    if (!name.isEmpty()) bookPOJO.setAutor(name);
                } else if (typeStr.contains("performer") || typeStr.contains("reader")) {
                    if (!src.isEmpty()) bookPOJO.setUrlArtist(src + "page<page>/");
                    if (!name.isEmpty()) bookPOJO.setArtist(name);
                } else if (typeStr.contains("series") || typeStr.contains("cycle")) {
                    if (!src.isEmpty()) bookPOJO.setUrlSeries(src);
                    if (!name.isEmpty()) bookPOJO.setSeries(name);
                }
            }
            Element title = book.selectFirst(".caption__article-main");
            if (title != null && !title.text().isEmpty()) {
                // the card title is "Author – Title" (an en dash on the live site, a hyphen on older pages)
                bookPOJO.setName(title.text().replace(bookPOJO.getAutor() + " – ", "")
                        .replace(bookPOJO.getAutor() + " - ", ""));
            }
            Element link = book.selectFirst(".content__article-main-link");
            if (link != null && !link.attr("href").isEmpty()) {
                bookPOJO.setUrl(link.attr("href"));
            }
            Element desc = book.selectFirst(".description__article-main");
            if (desc != null && !desc.text().isEmpty()) {
                bookPOJO.setDesc(desc.text());
            }
            Element time = book.selectFirst(".link__action--label--time");
            if (time != null && !time.text().isEmpty()) {
                bookPOJO.setTime(time.text());
            }
            Element coments = book.selectFirst(".link__action--comment");
            if (coments != null && !coments.text().contains("нет комментариев")) {
                String count = coments.text().trim().split("\\s+")[0];
                if (!count.isEmpty()) {
                    bookPOJO.setComents(count);
                }
            }
            Elements reting = book.getElementsByClass("link__action--label--views");
            if (!reting.isEmpty() && !reting.text().trim().isEmpty()) {
                bookPOJO.setReting(reting.text().trim());
            }
            return bookPOJO;
        });
        return ProviderPage.of(result, hasMore, raw.size());
    }

    static ProviderPage<BookPOJO> parseBazaKnig(Document doc, int page) {
        boolean hasMore = lastPage(doc.select(".page-nav .navigation a, .page-nav .navigation span")) > page
                || !doc.select(".page-nav .pnext").isEmpty();
        Element conteiner = doc.getElementById("dle-content");
        if (conteiner == null) {
            return ProviderPage.empty();
        }
        Elements raw = conteiner.getElementsByClass("short");
        ArrayList<BookPOJO> result = cards(raw, book -> {
            BookPOJO bookPOJO = new BookPOJO();
            Element imgConteiner = book.selectFirst(".short-img");
            if (imgConteiner != null) {
                Element aElement = imgConteiner.selectFirst("a");
                if (aElement != null) {
                    if (aElement.attr("href").isEmpty()) {
                        return null;
                    }
                    bookPOJO.setUrl(aElement.attr("href"));
                    Element img = imgConteiner.selectFirst("img");
                    if (img != null) {
                        bookPOJO.setPhoto(Url.SERVER_BAZA_KNIG + img.attr("src"));
                    }
                }
            }
            Element nameTag = book.selectFirst(".short-title a");
            if (nameTag != null) {
                if (nameTag.text().isEmpty()) {
                    return null;
                }
                bookPOJO.setName(nameTag.text());
            }
            Element descCont = book.selectFirst(".short-text");
            if (descCont != null) {
                bookPOJO.setDesc(descCont.text());
            }
            for (Element li : book.select(".short-items li")) {
                String liText = li.text();
                Element b = li.selectFirst("b");
                Element a = li.selectFirst("a");
                if (liText.contains("Автор") && b != null && nonEmpty(b.text()) != null) {
                    bookPOJO.setAutor(b.text());
                    Element aTag = b.selectFirst("a");
                    if (aTag != null) {
                        bookPOJO.setUrlAutor(aTag.attr("href") + "page/");
                    }
                }
                if (liText.contains("Читает") && b != null) {
                    String artistName = liText.replace("Читает: ", "").trim();
                    if (!artistName.isEmpty()) {
                        bookPOJO.setArtist(artistName);
                        Element aTag = b.selectFirst("a");
                        if (aTag != null) {
                            bookPOJO.setUrlArtist(aTag.attr("href") + "page/");
                        }
                    }
                }
                if (liText.contains("Длительность") && b != null && nonEmpty(b.text()) != null) {
                    bookPOJO.setTime(b.text());
                }
                if (liText.contains("Цикл") && a != null && nonEmpty(a.text()) != null) {
                    bookPOJO.setSeries(a.text());
                    bookPOJO.setUrlSeries(a.attr("href") + "page/");
                }
                if (liText.contains("Жанр") && a != null && nonEmpty(a.text()) != null) {
                    bookPOJO.setGenre(a.text());
                    bookPOJO.setUrlGenre(a.attr("href") + "page/");
                }
            }
            Elements comentsClass = book.select(".short-bottom .comments");
            if (comentsClass.size() == 3) {
                bookPOJO.setComents(comentsClass.last().text());
            }
            return bookPOJO;
        });
        return ProviderPage.of(result, hasMore, raw.size());
    }

    static ProviderPage<BookPOJO> parseIzibuk(Document document, String url, int page) {
        boolean hasMore = lastPage(document.select(
                "#books_updates_list__pn a[data-page], #books_list__pn a[data-page]")) > page;
        Element listParent = document.getElementById("books_list");
        if (listParent == null) {
            listParent = document.getElementById("books_updates_list");
        }
        if (listParent == null) {
            return ProviderPage.empty();
        }
        Element h1 = document.selectFirst("h1");
        final String pageTitle = h1 == null ? null : nonEmpty(h1.text());
        Elements raw = listParent.getElementsByClass("_ccb9b7");
        ArrayList<BookPOJO> result = cards(raw, book -> {
            BookPOJO bookPOJO = new BookPOJO();
            Element genre = book.selectFirst("._680f12 > *");
            if (genre != null) {
                bookPOJO.setGenre(genre.text());
                bookPOJO.setUrlGenre(Url.SERVER_IZIBUK + genre.attr("href") + "?p=");
            } else if (url.contains("genre")) {
                bookPOJO.setUrlGenre(url);
                if (pageTitle != null) {
                    bookPOJO.setGenre(pageTitle);
                }
            }
            Element image = book.selectFirst("._bce453 > *");
            if (image != null) {
                bookPOJO.setPhoto(image.attr("src"));
            }
            Element content = book.selectFirst("._802db0");
            if (content == null) {
                return null;
            }
            Element titleParent = child(content, 1);
            if (titleParent != null) {
                if (!titleParent.getElementsByClass("_09ddb7").isEmpty()) {
                    return null; // fragment
                }
                Element title = child(titleParent, 0);
                if (title != null) {
                    bookPOJO.setUrl(Url.SERVER_IZIBUK + title.attr("href"));
                    bookPOJO.setName(title.text());
                }
            }
            Element meta = child(content, 2);
            if (meta != null) {
                for (Element element : meta.children()) {
                    Element link = child(element, 1);
                    if (link == null) {
                        continue;
                    }
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
            }
            if (bookPOJO.getAutor() == null && url.contains("author")) {
                bookPOJO.setUrlAutor(url + "?p=");
                if (pageTitle != null) {
                    bookPOJO.setAutor(pageTitle);
                }
            }
            if (bookPOJO.getArtist() == null && url.contains("reader")) {
                bookPOJO.setUrlArtist(url + "?p=");
                if (pageTitle != null) {
                    bookPOJO.setArtist(pageTitle);
                }
            }
            if (bookPOJO.getSeries() == null && url.contains("serie")) {
                bookPOJO.setUrlSeries(url + "?p=");
                if (pageTitle != null) {
                    bookPOJO.setSeries(pageTitle);
                }
            }
            Element description = child(content, 3);
            if (description != null) {
                bookPOJO.setDesc(description.text());
            }
            return bookPOJO;
        });
        return ProviderPage.of(result, hasMore, raw.size());
    }

    /** ABMP3's search returns every hit at once: one request per page, sliced locally, no book-page fetches (SRCH-13). */
    static ProviderPage<BookPOJO> parseAbmp3Search(Document doc, int page) {
        Elements hits = new Elements();
        for (Element item : doc.getElementsByClass("b-statictop-search")) {
            Element title = item.selectFirst(".b-statictop__title");
            Element parent = item.selectFirst(".b-statictop__items");
            if (title != null && title.text().contains("в книгах") && parent != null) {
                hits = parent.children();
                break;
            }
        }
        int from = Math.min((page - 1) * ABMP3_PAGE_SIZE, hits.size());
        int to = Math.min(page * ABMP3_PAGE_SIZE, hits.size());
        ArrayList<BookPOJO> result = cards(new Elements(hits.subList(from, to)), book -> {
            Element aTag = book.selectFirst("a");
            if (aTag == null) {
                return null;
            }
            BookPOJO bookPOJO = new BookPOJO();
            String href = aTag.attr("href");
            if (!href.isEmpty()) {
                bookPOJO.setUrl(Url.SERVER_ABMP3 + href);
                String cover = getAbmp3CoverUrl(href);
                if (cover != null) {
                    bookPOJO.setPhoto(cover);
                }
            }
            String text = aTag.text();
            int nameEnd = text.indexOf(" (");
            if (nameEnd > 0) {
                text = text.substring(0, nameEnd);
            }
            if (!text.trim().isEmpty()) {
                bookPOJO.setName(text.trim());
            }
            Element info = aTag.selectFirst(".add_info");
            if (info != null) {
                String infoText = info.text();
                int open = infoText.indexOf("(");
                int close = infoText.indexOf(")");
                if (open >= 0 && close > open) {
                    String autorName = infoText.substring(open + 1, close).trim();
                    if (!autorName.isEmpty()) {
                        bookPOJO.setAutor(autorName);
                    }
                    int lastOpen = infoText.lastIndexOf("(");
                    int lastClose = infoText.lastIndexOf(")");
                    if (lastOpen > open && lastClose > lastOpen) {
                        String readerName = infoText.substring(lastOpen + 1, lastClose).trim();
                        if (!readerName.isEmpty() && !readerName.equals(autorName)) {
                            bookPOJO.setArtist(readerName);
                        }
                    }
                }
            }
            return bookPOJO;
        });
        return ProviderPage.of(result, hits.size() > to, to - from);
    }

    static String getAbmp3CoverUrl(String href) {
        if (href == null || href.isEmpty()) {
            return null;
        }
        int idStart = href.indexOf("/audio-");
        if (idStart < 0) {
            return null;
        }
        String rest = href.substring(idStart + 7);
        int dash = rest.indexOf('-');
        if (dash <= 0) {
            return null;
        }
        String id = rest.substring(0, dash);
        String slug = rest.substring(dash + 1);
        if (id.isEmpty() || slug.isEmpty() || !id.matches("\\d+")) {
            return null;
        }
        StringBuilder digitsPath = new StringBuilder();
        for (char c : id.toCharArray()) {
            digitsPath.append(c).append('/');
        }
        return Url.CDN_ABMP3 + "/audiobooks/" + digitsPath + "audiobook-" + slug + ".jpg";
    }

    static ProviderPage<BookPOJO> parseKnigoblud(Document document, int page) {
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

    static ProviderPage<BookPOJO> parseOtrub(Document doc, int page) {
        boolean hasMore = lastPage(doc.select(
                "#books_updates_list__pn a[data-page], #books_list__pn a[data-page]")) > page;
        Element listParent = doc.getElementById("books_updates_list");
        if (listParent == null) {
            listParent = doc.getElementById("books_list");
        }
        if (listParent == null) {
            return ProviderPage.empty();
        }
        ArrayList<BookPOJO> result = cards(listParent.select("._cb0a41 > ._dad4fa"), book -> {
            BookPOJO bookPOJO = new BookPOJO();
            Element img = book.selectFirst("._bce453 img._76d12c");
            if (img != null) {
                bookPOJO.setPhoto(img.attr("src"));
            }
            Element genreLink = book.selectFirst("._eeab32 b a");
            if (genreLink != null) {
                if (!genreLink.attr("href").isEmpty()) {
                    bookPOJO.setUrlGenre(Url.SERVER_OTRUB + genreLink.attr("href") + "?p=");
                }
                if (!genreLink.text().isEmpty()) {
                    bookPOJO.setGenre(genreLink.text());
                }
            }
            Element titleLink = book.selectFirst("._3dc935 a");
            if (titleLink != null) {
                if (!titleLink.attr("href").isEmpty()) {
                    bookPOJO.setUrl(Url.SERVER_OTRUB + titleLink.attr("href"));
                }
                if (!titleLink.text().isEmpty()) {
                    bookPOJO.setName(titleLink.text());
                }
            }
            for (Element block : book.select("._5d10b1 ._eeab32")) {
                Element label = block.selectFirst("._a6370b");
                Element a = block.selectFirst("a");
                if (label == null || a == null) {
                    continue;
                }
                String labelText = label.text();
                String href = a.attr("href");
                String name = a.text();
                if (labelText.contains("Автор")) {
                    bookPOJO.setAutor(name);
                    if (!href.isEmpty()) {
                        bookPOJO.setUrlAutor(Url.SERVER_OTRUB + href);
                    }
                } else if (labelText.contains("Читает")) {
                    bookPOJO.setArtist(name);
                    if (!href.isEmpty()) {
                        bookPOJO.setUrlArtist(Url.SERVER_OTRUB + href);
                    }
                } else if (labelText.contains("Цикл") || labelText.contains("Серия")) {
                    bookPOJO.setSeries(name);
                    if (!href.isEmpty()) {
                        bookPOJO.setUrlSeries(href.startsWith("/") ? Url.SERVER_OTRUB + href : href);
                    }
                }
            }
            Element desc = book.selectFirst("._b1f6b9");
            if (desc != null) {
                bookPOJO.setDesc(desc.text());
            }
            return bookPOJO;
        });
        return ProviderPage.of(result, hasMore);
    }

    /** audioboo's search pager links only back to page 1: a full page means there may be another one. */
    static ProviderPage<BookPOJO> parseAudiobooSearch(Document doc, int page) {
        int cards = doc.select(".card.d-flex").size();
        return ProviderPage.of(parseAudioboo(doc, page).items, cards >= AUDIOBOO_SEARCH_PAGE_SIZE, cards);
    }

    static ProviderPage<BookPOJO> parseAudioboo(Document doc, int page) {
        ArrayList<BookPOJO> result = cards(doc.select(".card.d-flex"), book -> {
            BookPOJO bookPOJO = new BookPOJO();
            Element imgWrapper = book.selectFirst(".card__img");
            if (imgWrapper != null) {
                bookPOJO.setUrl(imgWrapper.attr("href"));
                Element img = imgWrapper.selectFirst("img");
                if (img != null) {
                    String imgUrl = img.attr("src");
                    bookPOJO.setPhoto(imgUrl.startsWith("/") ? Url.SERVER_AUDIOBOO + imgUrl : imgUrl);
                    if (!img.attr("alt").isEmpty()) {
                        bookPOJO.setName(img.attr("alt"));
                    }
                }
            }
            Element title = book.selectFirst(".card__title");
            if (title != null && !title.text().isEmpty()) {
                bookPOJO.setName(title.text());
            }
            for (Element li : book.getElementsByTag("li")) {
                String text = li.text();
                Element a = li.selectFirst("a");
                if (a != null) {
                    String href = a.attr("href");
                    if (href.startsWith("/")) {
                        href = Url.SERVER_AUDIOBOO + href;
                    }
                    if (text.contains("Жанр:")) {
                        bookPOJO.setGenre(a.text());
                        bookPOJO.setUrlGenre(href);
                    } else if (text.contains("Автор:")) {
                        bookPOJO.setAutor(a.text());
                        bookPOJO.setUrlAutor(href);
                    } else if (text.contains("Читает:") || text.contains("Исполнитель:")) {
                        bookPOJO.setArtist(a.text());
                        bookPOJO.setUrlArtist(href);
                    } else if (text.contains("Цикл:")) {
                        bookPOJO.setSeries(a.text());
                        bookPOJO.setUrlSeries(href);
                    }
                }
                if (text.contains("Время:") && !text.contains("Время звучания:")) {
                    bookPOJO.setTime(text.replace("Время:", "").trim());
                }
            }
            if (bookPOJO.getSeries() == null) {
                Element aTag = book.selectFirst("a[href*=/xfsearch/cikl/]");
                if (aTag != null) {
                    bookPOJO.setSeries(aTag.text());
                    String href = aTag.attr("href");
                    if (!href.isEmpty()) {
                        bookPOJO.setUrlSeries(href.startsWith("/") ? Url.SERVER_AUDIOBOO + href : href);
                    }
                }
            }
            Element rub = book.selectFirst(".card__ratings .rubbery");
            if (rub != null) {
                bookPOJO.setTime(rub.text().replace("Время звучания:", "").trim());
            }
            Element desc = book.selectFirst(".card__text");
            if (desc != null) {
                bookPOJO.setDesc(desc.text());
            }
            Element currentRating = book.selectFirst(".card__metas .rating .unit-rating .current-rating");
            if (currentRating != null && currentRating.attr("style").contains("width:")) {
                String widthStr = currentRating.attr("style").replaceAll(".*width:\\s*", "").replaceAll("[%\\s;].*", "");
                try {
                    float stars = Float.parseFloat(widthStr) / 20.0f;
                    Element voteSpan = book.selectFirst(".card__metas span[id^=\"vote-num-id-\"]");
                    String votes = voteSpan != null ? voteSpan.text().trim() : "0";
                    if (stars > 0) {
                        bookPOJO.setReting(BookPOJO.formatRating(stars, votes));
                    }
                } catch (NumberFormatException ignored) {
                }
            }
            return bookPOJO;
        });
        int last = lastPage(doc.select(".navigation a, .pagination a"));
        boolean hasMore = last > 0 ? last > page : !result.isEmpty();
        return ProviderPage.of(result, hasMore);
    }

    static ProviderPage<BookPOJO> parsePoleknig(Document doc, int page) {
        ArrayList<BookPOJO> result = cards(doc.select("div.book-item"), book -> {
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
        // poleknig has no pager markup; the presenter ends the list when a page brings nothing new
        return ProviderPage.of(result, !result.isEmpty());
    }
}
