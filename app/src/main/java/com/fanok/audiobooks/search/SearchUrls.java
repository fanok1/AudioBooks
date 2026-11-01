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
        return provider.parser.searchUrl(category, query, page);
    }

    /** Query-string encoding (space → {@code +}). */
    public static String enc(String query) {
        try {
            return URLEncoder.encode(query, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new AssertionError(e);
        }
    }

    /** Path-segment encoding (space → {@code %20}); a literal {@code +} stays {@code %2B}. */
    public static String segment(String query) {
        return enc(query).replace("+", "%20");
    }
}
