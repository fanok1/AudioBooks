package com.fanok.audiobooks.source;

import static com.fanok.audiobooks.pojo.BookPOJO.absUrl;
import static com.fanok.audiobooks.source.Html.cards;
import static com.fanok.audiobooks.source.Html.each;
import static com.fanok.audiobooks.source.Html.lastPage;
import static com.fanok.audiobooks.source.Html.ownPath;
import static com.fanok.audiobooks.source.Html.rows;
import static com.fanok.audiobooks.source.Html.text;

import android.util.Log;

import androidx.annotation.Nullable;

import com.fanok.audiobooks.AutorsSearchABMP3;
import com.fanok.audiobooks.Url;
import com.fanok.audiobooks.model.ComentsModel;
import com.fanok.audiobooks.model.OtherArtistModel;
import com.fanok.audiobooks.model.OtherSourceModel;
import com.fanok.audiobooks.pojo.AudioPOJO;
import com.fanok.audiobooks.pojo.BookPOJO;
import com.fanok.audiobooks.pojo.ComentsPOJO;
import com.fanok.audiobooks.pojo.DescriptionPOJO;
import com.fanok.audiobooks.pojo.GenrePOJO;
import com.fanok.audiobooks.pojo.OtherArtistPOJO;
import com.fanok.audiobooks.pojo.SeriesPOJO;
import com.fanok.audiobooks.search.ProviderPage;
import com.fanok.audiobooks.search.SearchUrls;
import com.fanok.audiobooks.util.DownloadUtil;

import io.reactivex.functions.BooleanSupplier;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import okhttp3.HttpUrl;

/**
 * audiobook-mp3.com: plain GETs, no Referer of its own. Listings (the index, {@link Url#ABMP3_TOP},
 * {@link Url#ABMP3_RANDOM}, a genre, author or reader page) page with the {@code page} query parameter alone
 * ({@link Url#abmp3ListingPage}); genre listings cannot be re-sorted. The authors' index is an alphabet whose letters
 * are its pages. The search {@code /search?text=Q} is one static page of every hit, sliced into pages of
 * {@link #SEARCH_PAGE_SIZE} here (no reader search; its author block is {@link AutorsSearchABMP3}). Book pages carry a
 * Playerjs whose {@code file} is a playlist URL; the media wants a desktop User-Agent and the site as Referer. No
 * series; other narrations are found by searching the title.
 */
public final class Abmp3 implements SourceParser {

    /** The search returns every hit on one page; it is sliced client-side. */
    static final int SEARCH_PAGE_SIZE = 40;

    private static final List<Sort.Entry> MENU = Sort.menu(Sort.NEW, Sort.TOP_100, Sort.RANDOM);

    private static final String TAG = "ComentsModel";

    // ---------------------------------------------------------------- SourceParser

    @Override
    public ProviderPage<BookPOJO> listing(Document page, String url, int pageNo) {
        return parseListing(page, pageNo);
    }

    @Override
    public String pageUrl(String url, int page) {
        return Url.abmp3ListingPage(url, page);
    }

    /** {@link Url#ABMP3_RANDOM} redirects to one random book's page: that book is the listing's one card. */
    @Override
    public ProviderPage<BookPOJO> loadListing(String url, int pageNo) throws IOException {
        Document page = fetch(url);
        if (!url.startsWith(Url.ABMP3_RANDOM)) {
            return listing(page, url, pageNo);
        }
        BookPOJO book = parseBook(page, page.location());
        return book.isNull() ? ProviderPage.<BookPOJO>empty()
                : ProviderPage.of(new ArrayList<>(Collections.singletonList(book)), false, 1);
    }

    /** Author ({@code /avtor-}), reader ({@code /ispolnitel-}), series and genre listings are not re-sorted. */
    @Override
    public List<Sort.Entry> sorts(String url) {
        boolean noSort = url.contains("reader") || url.contains("author") || url.contains("serie")
                || url.contains("cikl") || url.contains("genre") || url.contains("/avtor-")
                || url.contains("/ispolnitel-");
        return noSort ? Collections.<Sort.Entry>emptyList() : MENU;
    }

    @Override
    public String sortUrl(Sort sort, String listingUrl) {
        switch (sort) {
            case NEW: return Url.INDEX_ABMP3;
            case TOP_100: return Url.ABMP3_TOP;
            case RANDOM: return Url.ABMP3_RANDOM;
            default: return null;
        }
    }

    @Override
    public ProviderPage<GenrePOJO> genres(Document page, String url, int pageNo) {
        Element items = page.selectFirst(".b-posts");
        if (items == null) {
            return ProviderPage.empty();
        }
        return ProviderPage.of(rows(items.children(), item -> {
            GenrePOJO genrePOJO = new GenrePOJO();
            Element aGenre = item.selectFirst(".title a");
            if (aGenre != null) {
                if (!ownPath(aGenre.attr("href"))) {
                    return null; // a row linking to another site
                }
                genrePOJO.setUrl(Url.SERVER_ABMP3 + aGenre.attr("href") + "?page=");
                genrePOJO.setName(aGenre.text());
            }
            Element ratings = item.selectFirst(".rating");
            if (ratings != null) {
                genrePOJO.setDescription(ratings.text().trim().replace("кни", " кни"));
            }
            return genrePOJO;
        }), false);
    }

    /** A paged author or reader listing, or the authors' alphabet: page N is the N-th letter, Russian letters first. */
    @Override
    public ProviderPage<GenrePOJO> people(Document page, String url, int pageNo, boolean performers)
            throws IOException {
        Document doc = page;
        if (doc.selectFirst(".authors_list") == null) {
            // a paged author listing
            Element parent = doc.selectFirst(".b-posts");
            if (parent == null) {
                return ProviderPage.empty();
            }
            boolean hasMore = Html.lastPage(doc.select(".pagination li a")) > pageNo;
            return ProviderPage.of(authorsRows(parent.children()), hasMore);
        }
        // the alphabet index: page N is the N-th letter, Russian letters first
        Element chars = doc.selectFirst(".alphabet");
        if (chars == null || chars.childrenSize() == 0) {
            return ProviderPage.empty();
        }
        Elements rus = chars.child(chars.childrenSize() - 1).children();
        Elements eng = chars.child(0).children();
        int total = rus.size() + eng.size();
        if (pageNo > total) {
            return ProviderPage.empty();
        }
        Element letter = pageNo <= rus.size() ? rus.get(pageNo - 1) : eng.get(pageNo - rus.size() - 1);
        Document letterDoc = fetch(Url.SERVER_ABMP3 + letter.attr("href"));
        Element list = letterDoc.selectFirst(".authors_list");
        ArrayList<GenrePOJO> result = list == null ? new ArrayList<>() : authorsRows(list.children());
        return ProviderPage.of(result, pageNo < total);
    }

    /** One static results page; {@link #parseSearch} slices it into pages of {@link #SEARCH_PAGE_SIZE}. */
    @Override
    public String searchUrl(SearchUrls.Category category, String query, int page) {
        if (category == SearchUrls.Category.PERFORMERS) {
            return null;
        }
        return Url.SERVER_ABMP3 + "/search?text=" + SearchUrls.enc(query) + "&page=" + page;
    }

    @Override
    public ProviderPage<BookPOJO> searchResults(Document page, int pageNo) {
        return parseSearch(page, pageNo);
    }

    /** The author block of the same one-page search. */
    @Override
    public ProviderPage<GenrePOJO> searchPeople(Document page, int pageNo, boolean performers) {
        return AutorsSearchABMP3.parse(page, pageNo);
    }

    @Override
    public DescriptionPOJO description(Document page, String bookUrl) {
        return parseDescription(page, bookUrl);
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
    public ArrayList<AudioPOJO> tracks(Document page, String base) throws IOException {
        return Players.loadPlayerjs(page, base, parseBookName(page));
    }

    @Override
    public Map<String, String> mediaHeaders(String bookUrl) {
        Map<String, String> headers = new HashMap<>();
        headers.put("User-Agent", DownloadUtil.DESKTOP_UA);
        headers.put("Referer", Url.SERVER_ABMP3 + "/");
        return headers;
    }

    /** The site has no series: nothing is fetched. */
    @Override
    public ArrayList<SeriesPOJO> loadSeries(String bookUrl) {
        return new ArrayList<>();
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

    /** The title alone: the site's search finds nothing with the author added. */
    @Override
    public ArrayList<OtherArtistPOJO> otherReaders(String bookUrl, String title, String author, String reader)
            throws IOException {
        if (title == null || title.trim().isEmpty()) {
            return new ArrayList<>();
        }
        String terms = OtherSourceModel.query(title);
        return OtherArtistModel.otherReaders(parseSearchHits(fetch(otherSourceSearchUrl(terms, 1))), title, author,
                bookUrl, reader);
    }

    @Override
    public String otherSourceTerms(BookPOJO book) {
        return OtherSourceModel.query(book.getName());
    }

    /** The search page without a page number, as the other-source lookup has always asked it. */
    @Override
    public String otherSourceSearchUrl(String terms, int page) {
        return HttpUrl.get(Url.SERVER_ABMP3).newBuilder().addPathSegment("search").addQueryParameter("text", terms)
                .build().toString();
    }

    @Override
    public ArrayList<OtherSourceModel.Hit> otherSourceHits(Document searchPage) {
        return parseSearchHits(searchPage);
    }

    // ---------------------------------------------------------------- pure parsers

    /** The index, rankings, genre, author and reader listings. */
    public static ProviderPage<BookPOJO> parseListing(Document doc, int page) {
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

    /** The search returns every hit at once: one request per page, sliced locally, no book-page fetches (SRCH-13). */
    public static ProviderPage<BookPOJO> parseSearch(Document doc, int page) {
        Elements hits = new Elements();
        for (Element item : doc.getElementsByClass("b-statictop-search")) {
            Element title = item.selectFirst(".b-statictop__title");
            Element parent = item.selectFirst(".b-statictop__items");
            if (title != null && title.text().contains("в книгах") && parent != null) {
                hits = parent.children();
                break;
            }
        }
        int from = Math.min((page - 1) * SEARCH_PAGE_SIZE, hits.size());
        int to = Math.min(page * SEARCH_PAGE_SIZE, hits.size());
        ArrayList<BookPOJO> result = cards(new Elements(hits.subList(from, to)), book -> {
            Element aTag = book.selectFirst("a");
            if (aTag == null) {
                return null;
            }
            BookPOJO bookPOJO = new BookPOJO();
            String href = aTag.attr("href");
            if (!href.isEmpty()) {
                bookPOJO.setUrl(Url.SERVER_ABMP3 + href);
                String cover = coverUrl(href);
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

    /** The CDN cover of a search hit, derived from its {@code /audio-<id>-<slug>} link; null for any other link. */
    static String coverUrl(String href) {
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

    private static ArrayList<GenrePOJO> authorsRows(Elements list) {
        return rows(list, item -> {
            GenrePOJO genrePOJO = new GenrePOJO();
            Element aGenre = item.selectFirst(".title a");
            if (aGenre != null) {
                genrePOJO.setUrl(Url.SERVER_ABMP3 + aGenre.attr("href") + "?page=");
                if (!aGenre.text().isEmpty()) {
                    genrePOJO.setName(aGenre.text());
                }
            }
            Element ratings = item.selectFirst(".rating");
            if (ratings != null) {
                genrePOJO.setDescription(ratings.text().trim().replace("кни", " кни"));
            }
            return genrePOJO;
        });
    }

    public static DescriptionPOJO parseDescription(Document document, String bookUrl) {

        String autor = "";

        DescriptionPOJO descriptionPOJO = new DescriptionPOJO();

        Elements img = document.getElementsByClass("abook_image");
        if (img.size() != 0) {
            descriptionPOJO.setPoster(img.first().absUrl("src"));
        }

        Elements desc = document.getElementsByClass("abook-desc");
        if (desc.size() != 0) {
            descriptionPOJO.setDescription(desc.first().ownText());
        }

        Elements infos = document.getElementsByClass("panel-item");
        for (Element info : infos) {
            if (info.text().contains("Автор")) {
                Elements element = info.getElementsByTag("a");
                if (element.size() != 0) {
                    autor = element.first().text();
                    descriptionPOJO.setAutor(autor);
                    descriptionPOJO.setAutorUrl(absUrl(element.first(), "href", "?page="));
                }
            } else if (info.text().contains("Читает")) {
                Elements element = info.getElementsByTag("a");
                if (element.size() != 0) {
                    descriptionPOJO.setArtist(element.first().text());
                    descriptionPOJO.setArtistUrl(absUrl(element.first(), "href", "?page="));
                }
            } else if (info.text().contains("Жанры")) {
                Elements element = info.getElementsByTag("a");
                if (element.size() != 0) {
                    descriptionPOJO.setGenre(element.first().text());
                    descriptionPOJO.setGenreUrl(absUrl(element.first(), "href", "?page="));
                }
            } else {
                Elements clock = info.getElementsByClass("fa-clock-o");
                if (clock.size() != 0) {
                    descriptionPOJO.setTime(info.text().trim());
                }

                Elements reting = info.getElementsByClass("fa-eye");
                if (reting.size() != 0) {
                    descriptionPOJO.setReiting(info.text().trim());
                }

            }
        }

        Elements titleElement = document.getElementsByTag("h1");
        if (titleElement.size() != 0) {
            String name = titleElement.first().text().trim();
            if (autor.isEmpty()) {
                descriptionPOJO.setTitle(name);
            } else {
                descriptionPOJO.setTitle(name.replace(autor + " - ", ""));
            }
        }

        Element like = document.getElementsByClass("vote-count").first();
        if (like != null) {
            descriptionPOJO.setLike(like.text());
        }

        return descriptionPOJO;
    }

    /** A book opened by its link. */
    public static BookPOJO parseBook(Document document, String url) {
        String autor = "";

        BookPOJO bookPOJO = new BookPOJO();

        bookPOJO.setUrl(url);

        Elements img = document.getElementsByClass("abook_image");
        if (img != null && !img.isEmpty()) {
            bookPOJO.setPhoto(img.first().absUrl("src"));
        }

        Elements desc = document.getElementsByClass("abook-desc");
        if (desc != null && !desc.isEmpty()) {
            bookPOJO.setDesc(desc.first().ownText());
        }

        Elements infos = document.getElementsByClass("panel-item");
        if (infos != null) {
            for (Element info : infos) {
                if (info.text().contains("Автор")) {
                    Elements element = info.getElementsByTag("a");
                    if (element != null && !element.isEmpty()) {
                        autor = element.first().text();
                        bookPOJO.setAutor(autor);
                        bookPOJO.setUrlAutor(absUrl(element.first(), "href", "?page="));
                    }
                } else if (info.text().contains("Читает")) {
                    Elements element = info.getElementsByTag("a");
                    if (element != null && !element.isEmpty()) {
                        bookPOJO.setArtist(element.first().text());
                        bookPOJO.setUrlArtist(absUrl(element.first(), "href", "?page="));
                    }
                } else if (info.text().contains("Жанры")) {
                    Elements element = info.getElementsByTag("a");
                    if (element != null && !element.isEmpty()) {
                        bookPOJO.setGenre(element.first().text());
                        bookPOJO.setUrlGenre(absUrl(element.first(), "href", "?page="));
                    }
                } else {
                    Elements clock = info.getElementsByClass("fa-clock-o");
                    if (clock != null && !clock.isEmpty()) {
                        bookPOJO.setTime(info.text().trim());
                    }

                    Elements reting = info.getElementsByClass("fa-eye");
                    if (reting != null && !reting.isEmpty()) {
                        bookPOJO.setReting(info.text().trim());
                    }

                }
            }
        }

        Elements titleElement = document.getElementsByTag("h1");
        if (!titleElement.isEmpty()) {
            String name = titleElement.first().text().trim();
            if (autor.isEmpty()) {
                bookPOJO.setName(name);
            } else {
                bookPOJO.setName(name.replace(autor + " - ", ""));
            }
        }

        return bookPOJO;
    }

    /** The "similar books" block of a book page. */
    public static ArrayList<BookPOJO> parseRecommendations(Document document) {
        ArrayList<BookPOJO> books = new ArrayList<>();

        Elements root = document.getElementsByClass("similar-abooks-block");
        if (root.size() > 0) {
            Elements booksConteiner = root.get(0).getElementsByClass("abook-similar-item");
            for (int i = 0; i < booksConteiner.size(); i++) {
                BookPOJO bookPOJO = new BookPOJO();
                Elements aTag = booksConteiner.get(i).getElementsByTag("a");
                if (aTag.size() > 0) {
                    Element a = aTag.first();
                    bookPOJO.setUrl(a.absUrl("href"));
                    Elements img = a.getElementsByTag("img");
                    if (img.size() > 0) {
                        bookPOJO.setPhoto(img.first().absUrl("src"));
                    }
                    Elements name = a.getElementsByTag("span");
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

    /** The chapters' book name: the heading without the "Author - " prefix. */
    public static String parseBookName(Document doc) {
        String author = "";
        for (Element info : doc.select(".panel-item")) {
            Element link = info.selectFirst("a");
            if (info.text().contains("Автор") && link != null) {
                author = link.text();
            }
        }
        String title = text(doc.selectFirst("h1"));
        return author.isEmpty() ? title : title.replace(author + " - ", "");
    }

    /** The comments of a book page; a reply sits in the {@code li.comment} it answers. */
    public static ComentsModel.Parsed parseComments(Document doc) {
        Element list = doc.getElementById("w0");
        if (list == null) {
            return new ComentsModel.Parsed();
        }
        return ComentsModel.nest(list.select("li.comment"), "li.comment", (comment, pojo) -> {
            Element content = comment.selectFirst("> .comment-content");
            pojo.setImage(commentUrl(content, ".comment-author-avatar img", "src"));
            pojo.setName(commentText(content, ".comment-author-name > span:not(.comment-date)"));
            pojo.setDate(commentText(content, ".comment-date"));
            pojo.setText(commentText(content, ".comment-body"));
        });
    }

    /** The other-source search: the hits of its books block, the author and reader read off their parentheses. */
    public static ArrayList<OtherSourceModel.Hit> parseSearchHits(Document doc) {
        ArrayList<OtherSourceModel.Hit> hits = new ArrayList<>();
        for (Element block : doc.getElementsByClass("b-statictop-search")) {
            Element items = block.selectFirst(".b-statictop__items");
            if (!text(block, ".b-statictop__title").contains("в книгах") || items == null) {
                continue;
            }
            hits.addAll(each(items.children(), item -> {
                Element a = item.selectFirst("a");
                if (a == null) {
                    return null;
                }
                String full = a.text();
                int cut = full.indexOf(" (");
                String title = (cut >= 0 ? full.substring(0, cut) : full).trim();
                String info = text(a, ".add_info");
                String author = firstParenthesis(info);
                String reader = lastParenthesis(info);
                return new OtherSourceModel.Hit(a.absUrl("href"), title, author, author.equals(reader) ? "" : reader);
            }));
        }
        return hits;
    }

    // ---------------------------------------------------------------- helpers

    private static String firstParenthesis(String s) {
        int open = s.indexOf('(');
        int close = open < 0 ? -1 : s.indexOf(')', open);
        return close > open ? s.substring(open + 1, close).trim() : "";
    }

    private static String lastParenthesis(String s) {
        int open = s.lastIndexOf('(');
        int close = s.lastIndexOf(')');
        return open >= 0 && close > open ? s.substring(open + 1, close).trim() : "";
    }

    /** Untrimmed text of the first match of {@code query} under {@code scope}; "" when absent. */
    private static String commentText(@Nullable Element scope, String query) {
        Element element = scope == null ? null : scope.selectFirst(query);
        return element == null ? "" : element.text();
    }

    private static String commentUrl(@Nullable Element scope, String query, String attribute) {
        Element element = scope == null ? null : scope.selectFirst(query);
        return element == null ? "" : element.absUrl(attribute);
    }
}
