package com.fanok.audiobooks.search;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

/**
 * Search URL templates: every provider's own paging scheme, the query percent-encoded exactly once.
 * Pure and static, so a page is a parameter and never a digit substituted into a finished URL.
 */
public final class SearchUrls {

    public enum Category { BOOKS, AUTHORS, PERFORMERS }

    private SearchUrls() {
    }

    /** The URL for page {@code page} (1-based) of {@code category} on {@code provider}, or null when unsupported. */
    public static String url(Provider provider, Category category, String query, int page) {
        String q = enc(query);
        switch (provider) {
            case KNIGAVUHE:
                switch (category) {
                    case BOOKS: return provider.server + "/search/?q=" + q + "&page=" + page;
                    case AUTHORS: return provider.server + "/search/authors/?q=" + q + "&page=" + page;
                    case PERFORMERS: return provider.server + "/search/readers/?q=" + q + "&page=" + page;
                }
                return null;
            case IZIBUK:
                switch (category) {
                    case BOOKS: return provider.server + "/search?q=" + q + "&p=" + page;
                    case AUTHORS: return provider.server + "/authors?q=" + q + "&p=" + page;
                    case PERFORMERS: return provider.server + "/readers?q=" + q + "&p=" + page;
                }
                return null;
            case ABMP3:
                // one static results page; the parser slices it into pages of ABMP3_PAGE_SIZE
                if (category == Category.PERFORMERS) {
                    return null;
                }
                return provider.server + "/search?text=" + q + "&page=" + page;
            case AKNIGA:
                switch (category) {
                    case BOOKS: return provider.server + "/search/books/page" + page + "/?q=" + q;
                    // the ajax endpoints are POSTed: the fetcher reads q and page back from this URL
                    case AUTHORS: return provider.server + "/authors/ajax-search/?q=" + q + "&page=" + page;
                    case PERFORMERS: return provider.server + "/performers/ajax-search/?q=" + q + "&page=" + page;
                }
                return null;
            case BAZA_KNIG:
                if (category != Category.BOOKS) {
                    return null;
                }
                return provider.server + "/f/j.x.autor;p.title=" + segment(query)
                        + "/v.litres_buy=litres/sort=news_read/order=desc/page/" + page + "/";
            case KNIGOBLUD:
                return category == Category.BOOKS ? provider.server + "/search?q=" + q + "&page=" + page : null;
            case AUDIOBOO:
                // DLE search: the fetcher POSTs these parameters as a form
                return category == Category.BOOKS ? provider.server
                        + "/index.php?do=search&subaction=search&story=" + q + "&search_start=" + page
                        + "&result_from=" + ((page - 1) * 10 + 1) + "&full_search=0" : null;
            case OTRUB:
                return category == Category.BOOKS ? provider.server + "/search?q=" + q + "&p=" + page : null;
            case POLEKNIG:
                return category == Category.BOOKS ? provider.server + "/?q=" + q + "&p=" + page : null;
        }
        return null;
    }

    /** Query-string encoding (space → {@code +}). */
    static String enc(String query) {
        try {
            return URLEncoder.encode(query, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new AssertionError(e);
        }
    }

    /** Path-segment encoding (space → {@code %20}); a literal {@code +} stays {@code %2B}. */
    static String segment(String query) {
        return enc(query).replace("+", "%20");
    }
}
