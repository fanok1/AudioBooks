package com.fanok.audiobooks.source;

import androidx.annotation.Nullable;

import com.fanok.audiobooks.Url;
import com.fanok.audiobooks.net.SiteHttp;
import com.fanok.audiobooks.search.SearchUrls;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** What the DataLife Engine sites share: sorts POSTed as a form, {@code /page/N/} paging, the {@code do=search} URL. */
public final class Dle {

    /** The sort parameter a sorted listing URL carries; {@link #fetch} POSTs such a URL's query as the sort form. */
    public static final String SORT_PARAM = "dlenewssortby";

    /** The Order menu of a DLE listing: the five sorts of the engine's sort form, {@link Order}. */
    public static final List<Sort.Entry> MENU = Sort.menu(Sort.NEW, Sort.POPULAR, Sort.RATING, Sort.COMMENTS,
            Sort.TITLE);

    /** {@link #MENU} and the site's fixed top-100 page, for an index whose site has one. */
    public static final List<Sort.Entry> MENU_WITH_TOP = Sort.menu(Sort.NEW, Sort.POPULAR, Sort.RATING,
            Sort.COMMENTS, Sort.TITLE, Sort.TOP_100);

    /** The engine's sort form: field ({@code dlenewssortby}) and direction; the title sort runs A to Я. */
    public enum Order {
        DATE("date", "desc"), RATING("rating", "desc"), VIEWS("news_read", "desc"),
        COMMENTS("comm_num", "desc"), TITLE("title", "asc");

        public final String field;
        public final String direction;

        Order(String field, String direction) {
            this.field = field;
            this.direction = direction;
        }

        /** The form of a {@link #MENU} sort; null for any other. */
        @Nullable
        public static Order of(Sort sort) {
            switch (sort) {
                case NEW: return DATE;
                case POPULAR: return VIEWS;
                case RATING: return RATING;
                case COMMENTS: return COMMENTS;
                case TITLE: return TITLE;
                default: return null;
            }
        }

        /** The form whose field {@code field} is ({@code dlenewssortby} of a sorted URL); null for none. */
        @Nullable
        public static Order ofField(@Nullable String field) {
            for (Order order : values()) {
                if (order.field.equals(field)) {
                    return order;
                }
            }
            return null;
        }
    }

    private static final Pattern NEWS_ID = Pattern.compile("/(\\d+)[^/]*/?$");
    private static final Pattern CATEGORY = Pattern.compile("dle_sort_cat_(\\d+)");

    private Dle() {
    }

    /** GET, or the POST the site's sort buttons send when the URL carries {@link #SORT_PARAM}: the app URL stays the key. */
    public static Document fetch(String url) throws IOException {
        int query = url.indexOf('?');
        if (query >= 0 && url.indexOf(SORT_PARAM + "=", query) > 0) {
            return SiteHttp.postForm(url);
        }
        return SiteHttp.get(url);
    }

    /**
     * {@code listing} (a URL without query) sorted by {@code order}: the engine's sort form as the query, which
     * {@link #fetch} POSTs to the page itself. {@code key} names the listing's sort in the session: {@code main} for
     * the index, {@code cat_<id>} for a category ({@link #categoryId}), the template's own for others
     * ({@code xfsearch}, ...). Which page and which listings go out sorted is the site's policy.
     */
    public static String sorted(String listing, Order order, String key) {
        return listing + "?" + SORT_PARAM + "=" + order.field + "&dledirection=" + order.direction
                + "&set_new_sort=dle_sort_" + key + "&set_direction_sort=dle_direction_" + key;
    }

    /** The DLE id of the category a listing page shows (its sort form's {@code set_new_sort}); null elsewhere. */
    @Nullable
    public static String categoryId(Document page) {
        Element key = page.getElementById("set_new_sort");
        Matcher m = CATEGORY.matcher(key == null ? "" : key.val());
        return m.matches() ? m.group(1) : null;
    }

    /** Page {@code page} of a DLE listing: {@code page/N/} before the query; page 1 is the bare listing. */
    public static String pageUrl(String url, int page) {
        return Url.audiobooListingPage(url, page);
    }

    /** The site search; its parameters are read from a GET as well as from the form. */
    public static String searchUrl(String server, String query, int page) {
        return server + "/index.php?do=search&subaction=search&story=" + SearchUrls.enc(query) + "&search_start="
                + page + "&result_from=" + ((page - 1) * 10 + 1) + "&full_search=0";
    }

    /** The DLE news id of a book URL ({@code .../182353-name.html} → {@code 182353}); "" when the URL has none. */
    public static String newsId(String bookUrl) {
        String path = URI.create(bookUrl.trim()).getPath();
        Matcher matcher = NEWS_ID.matcher(path == null ? "" : path);
        return matcher.find() ? matcher.group(1) : "";
    }
}
