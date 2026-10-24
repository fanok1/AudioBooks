package com.fanok.audiobooks.model;

import com.fanok.audiobooks.AutorsSearchABMP3;
import com.fanok.audiobooks.Url;
import com.fanok.audiobooks.net.SiteHttp;
import com.fanok.audiobooks.pojo.GenrePOJO;
import com.fanok.audiobooks.search.Provider;
import com.fanok.audiobooks.search.ProviderPage;
import com.fanok.audiobooks.search.SearchUrls;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.util.ArrayList;
import okhttp3.HttpUrl;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

/** Author and performer listings and their search, one page per call; end of list comes from the pager. */
public class AutorsModel extends GenreModel {

    private boolean mPerformerMode = false;

    public void setPerformerMode(boolean mode) {
        mPerformerMode = mode;
    }

    /** One page of author/performer search on {@code provider}; EMPTY when the provider has no such search. */
    public ProviderPage<GenrePOJO> search(Provider provider, SearchUrls.Category category, String query, int page)
            throws IOException {
        String url = SearchUrls.url(provider, category, query, page);
        if (url == null) {
            return ProviderPage.empty();
        }
        switch (provider) {
            case KNIGAVUHE: return loadBooksList(url, page);
            case IZIBUK: return loadBooksListIzibuk(url, page);
            case ABMP3: return new AutorsSearchABMP3().getAutors(url, page);
            case AKNIGA: return page > 1 ? ProviderPage.<GenrePOJO>empty() : searchAbook(url);
            default: return ProviderPage.empty();
        }
    }

    @Override
    protected ProviderPage<GenrePOJO> loadBooksList(String url, int page) throws IOException {
        Document doc = SiteHttp.get(url);
        boolean hasMore = BooksModel.lastPage(doc.select(".pn_page_buttons a")) > page;
        return ProviderPage.of(rows(doc.getElementsByClass("author_item"), item -> {
            GenrePOJO genrePOJO = new GenrePOJO();
            Element a = item.selectFirst("a");
            if (a != null) {
                if (!a.attr("href").isEmpty()) {
                    genrePOJO.setUrl(Url.SERVER + a.attr("href"));
                }
                if (!a.text().isEmpty()) {
                    genrePOJO.setName(a.text());
                }
            }
            Element booksCount = item.selectFirst(".author_item_books_count");
            if (booksCount != null) {
                genrePOJO.setDescription(booksCount.text());
            }
            return genrePOJO;
        }), hasMore);
    }

    @Override
    protected ProviderPage<GenrePOJO> loadBooksListABMP3(String url, int page) throws IOException {
        Document doc = SiteHttp.get(url);
        if (doc.selectFirst(".authors_list") == null) {
            // a paged author listing
            Element parent = doc.selectFirst(".b-posts");
            if (parent == null) {
                return ProviderPage.empty();
            }
            boolean hasMore = BooksModel.lastPage(doc.select(".pagination li a")) > page;
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
        if (page > total) {
            return ProviderPage.empty();
        }
        Element letter = page <= rus.size() ? rus.get(page - 1) : eng.get(page - rus.size() - 1);
        Document letterDoc = SiteHttp.get(Url.SERVER_ABMP3 + letter.attr("href"));
        Element list = letterDoc.selectFirst(".authors_list");
        ArrayList<GenrePOJO> result = list == null ? new ArrayList<>() : authorsRows(list.children());
        return ProviderPage.of(result, page < total);
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

    @Override
    protected ProviderPage<GenrePOJO> loadBooksListAbook(final String url, final int page) throws IOException {
        Document doc = SiteHttp.get(url);
        boolean hasMore = !doc.select(".page__nav .page__nav--next").isEmpty();
        return ProviderPage.of(parseAbookTable(doc), hasMore);
    }

    /** akniga's ajax search: a security key and cookies from the listing page, then a POST; one page only (SRCH-07). */
    private ProviderPage<GenrePOJO> searchAbook(String url) throws IOException {
        HttpUrl parsed = HttpUrl.parse(url);
        if (parsed == null) {
            return ProviderPage.empty();
        }
        String query = parsed.queryParameter("q");
        String endpoint = parsed.newBuilder().query(null).build().toString();
        Document listing = SiteHttp.get(Url.SERVER_AKNIGA + "/authors/");
        String key = "";
        for (Element script : listing.getElementsByTag("script")) {
            String text = script.data();
            int at = text.indexOf("LIVESTREET_SECURITY_KEY");
            if (at >= 0) {
                text = text.substring(at).replace("LIVESTREET_SECURITY_KEY = '", "");
                key = text.substring(0, Math.max(0, text.indexOf("'")));
                break;
            }
        }
        Connection connection = SiteHttp.connect(endpoint)
                .method(Connection.Method.POST)
                .referrer(Url.SERVER_AKNIGA + "/authors/")
                .ignoreContentType(true)
                .data("security_ls_key", key)
                .data("sText", query == null ? "" : query);
        String body = SiteHttp.execute(connection).body();
        JsonElement json = JsonParser.parseString(body.replaceAll("\\n", ""));
        if (!json.isJsonObject() || !json.getAsJsonObject().has("html")) {
            return ProviderPage.empty();
        }
        String html = json.getAsJsonObject().get("html").getAsString();
        Document doc = Jsoup.parse("<html><head></head><body><table><tbody>" + html + "</tbody></table></body></html>");
        return ProviderPage.of(parseAbookTable(doc), false);
    }

    @Override
    protected ProviderPage<GenrePOJO> loadBooksListIzibuk(String url, int page) throws IOException {
        Document doc = SiteHttp.get(url);
        boolean hasMore = BooksModel.lastPage(doc.select(
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

    @Override
    protected ProviderPage<GenrePOJO> loadBooksListAudioboo(String url, int page) throws IOException {
        return parseAudiobooPeople(SiteHttp.get(url), mPerformerMode, page);
    }

    /**
     * audioboo has no index of authors or readers: those of the listing page's cards, once each, linking to their
     * book listings. The listing's pages are this list's pages.
     */
    static ProviderPage<GenrePOJO> parseAudiobooPeople(Document doc, boolean performers, int page) {
        String marker = performers ? "Исполнитель:" : "Автор:";
        ArrayList<GenrePOJO> result = new ArrayList<>();
        ArrayList<String> names = new ArrayList<>();
        Elements cards = doc.select("article.card");
        for (Element card : cards) {
            for (Element li : card.select(".card__list li")) {
                Element link = li.selectFirst("a[href]");
                if (link == null || !li.text().contains(marker)) {
                    continue;
                }
                String name = link.text().trim();
                String href = link.absUrl("href");
                if (name.isEmpty() || !Url.isHost(href, Url.SERVER_AUDIOBOO) || names.contains(name)) {
                    continue;
                }
                GenrePOJO person = new GenrePOJO();
                person.setUrl(href);
                person.setName(name);
                names.add(name);
                result.add(person);
            }
        }
        int last = BooksModel.lastPage(doc.select(".navigation a, .pagination a"));
        return ProviderPage.of(result, last > 0 ? last > page : !cards.isEmpty(), cards.size());
    }
}
