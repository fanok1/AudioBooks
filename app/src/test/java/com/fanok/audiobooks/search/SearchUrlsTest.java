package com.fanok.audiobooks.search;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.fanok.audiobooks.search.SearchUrls.Category;

import okhttp3.HttpUrl;

import org.junit.Test;

import java.net.URI;
import java.util.EnumMap;
import java.util.Map;

public class SearchUrlsTest {

    private static final String[] QUERIES = {"1984", "Приручитель женщин-монстров", "a&b", "100%", "c++"};
    private static final int[] PAGES = {1, 2, 10, 11};

    /** How each provider spells the page number in its own URLs. */
    private static final Map<Provider, String> PAGE_FORMAT = new EnumMap<>(Provider.class);

    static {
        PAGE_FORMAT.put(Provider.KNIGAVUHE, "&page=%d");
        PAGE_FORMAT.put(Provider.IZIBUK, "&p=%d");
        PAGE_FORMAT.put(Provider.ABMP3, "&page=%d");
        PAGE_FORMAT.put(Provider.AKNIGA, "page%d/");
        PAGE_FORMAT.put(Provider.BAZA_KNIG, "/page/%d/");
        PAGE_FORMAT.put(Provider.KNIGOBLUD, "&page=%d");
        PAGE_FORMAT.put(Provider.AUDIOBOO, "search_start=%d&");
        PAGE_FORMAT.put(Provider.OTRUB, "&p=%d");
        PAGE_FORMAT.put(Provider.POLEKNIG, "&p=%d");
    }

    private static String pageToken(Provider provider, Category category, int page) {
        // akniga's ajax author/performer search is a query-string endpoint, its book search is a path
        String format = provider == Provider.AKNIGA && category != Category.BOOKS
                ? "&page=%d" : PAGE_FORMAT.get(provider);
        return String.format(format, page);
    }

    private static int count(String haystack, String needle) {
        int n = 0;
        for (int i = haystack.indexOf(needle); i >= 0; i = haystack.indexOf(needle, i + needle.length())) {
            n++;
        }
        return n;
    }

    @Test
    public void everyProviderCategoryQueryAndPageIsWellFormedAndEncodedOnce() {
        for (Provider provider : Provider.values()) {
            for (Category category : Category.values()) {
                for (String query : QUERIES) {
                    for (int page : PAGES) {
                        String url = SearchUrls.url(provider, category, query, page);
                        if (url == null) {
                            // unsupported combination: skipped with a status, never routed elsewhere; every provider
                            // supports book search, so a null there is a regression, not a gap
                            assertNotEquals(provider + " lost its book search", Category.BOOKS, category);
                            continue;
                        }
                        String where = provider + "/" + category + " q=" + query + " p=" + page + " -> " + url;
                        assertNotNull(where, HttpUrl.parse(url));
                        assertEquals(where, provider.host(), HttpUrl.parse(url).host().replaceFirst("^www\\.", ""));
                        URI.create(url); // throws on anything left unencoded
                        String encoded = provider == Provider.BAZA_KNIG
                                ? SearchUrls.segment(query) : SearchUrls.enc(query);
                        assertEquals(where, 1, count(url, encoded));
                        assertFalse(where + " double-encoded", url.contains("%25") && !query.contains("%"));
                        assertEquals(where, 1, count(url, "%25") * (query.contains("%") ? 1 : 0)
                                + (query.contains("%") ? 0 : 1));
                        assertFalse(where, url.contains("%2525"));
                        String pageToken = pageToken(provider, category, page);
                        assertTrue(where + " lacks " + pageToken, url.contains(pageToken));
                    }
                }
            }
        }
    }

    @Test
    public void pagesTenAndElevenAreDistinctAndPageOneDoesNotRepeatTheQuery() {
        for (Provider provider : Provider.values()) {
            for (Category category : Category.values()) {
                String ten = SearchUrls.url(provider, category, "1984", 10);
                String eleven = SearchUrls.url(provider, category, "1984", 11);
                String one = SearchUrls.url(provider, category, "1984", 1);
                if (one == null) {
                    continue;
                }
                assertNotEquals(provider + "/" + category, ten, eleven);
                assertEquals(provider + "/" + category + " -> " + eleven,
                        ten.replace(pageToken(provider, category, 10), pageToken(provider, category, 11))
                                .replace("result_from=91", "result_from=101"), eleven);
                assertEquals(provider + "/" + category + " -> " + one, 1, count(one, "1984"));
            }
        }
    }

    @Test
    public void unsupportedCategoriesAreNullNotIzibuk() {
        assertNull(SearchUrls.url(Provider.ABMP3, Category.PERFORMERS, "x", 1));
        assertNull(SearchUrls.url(Provider.BAZA_KNIG, Category.AUTHORS, "x", 1));
        assertNull(SearchUrls.url(Provider.KNIGOBLUD, Category.PERFORMERS, "x", 1));
        assertNull(SearchUrls.url(Provider.AUDIOBOO, Category.AUTHORS, "x", 1));
        assertNull(SearchUrls.url(Provider.OTRUB, Category.AUTHORS, "x", 1));
        assertNull(SearchUrls.url(Provider.POLEKNIG, Category.PERFORMERS, "x", 1));
    }

    @Test
    public void aknigaAjaxSearchCarriesTheQueryOnceAndDecodesBack() {
        String url = SearchUrls.url(Provider.AKNIGA, Category.AUTHORS, "Толстой", 1);
        assertEquals("https://akniga.org/authors/ajax-search/?q=%D0%A2%D0%BE%D0%BB%D1%81%D1%82%D0%BE%D0%B9&page=1", url);
        assertEquals("Толстой", HttpUrl.parse(url).queryParameter("q"));
    }

    @Test
    public void bazaKnigKeepsAmpersandInsideTheQuery() {
        String url = SearchUrls.url(Provider.BAZA_KNIG, Category.BOOKS, "War & Peace", 2);
        assertEquals("https://baza-knig.top/f/j.x.autor;p.title=War%20%26%20Peace"
                + "/v.litres_buy=litres/sort=news_read/order=desc/page/2/", url);
    }

    @Test
    public void everyGeneratedUrlDecodesBackToTheOriginalQuery() {
        String[] queries = {"1984", "Приручитель женщин-монстров", "a&b", "100%", "c++", "War & Peace", "a b+c %20"};
        for (Provider provider : Provider.values()) {
            for (Category category : Category.values()) {
                for (String query : queries) {
                    String url = SearchUrls.url(provider, category, query, 2);
                    if (url == null) {
                        continue;
                    }
                    String decoded;
                    if (provider == Provider.BAZA_KNIG) {
                        String path = URI.create(url).getPath();
                        int from = path.indexOf("p.title=") + "p.title=".length();
                        decoded = path.substring(from, path.indexOf("/v.litres_buy", from));
                    } else {
                        String name = provider == Provider.ABMP3 ? "text" : provider == Provider.AUDIOBOO ? "story" : "q";
                        decoded = HttpUrl.parse(url).queryParameter(name);
                    }
                    assertEquals(provider + "/" + category + " -> " + url, query, decoded);
                }
            }
        }
    }
}
