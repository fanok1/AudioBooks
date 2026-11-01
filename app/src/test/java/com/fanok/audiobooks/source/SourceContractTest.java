package com.fanok.audiobooks.source;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.fanok.audiobooks.net.SiteHttp;
import com.fanok.audiobooks.search.ListingFilters;
import com.fanok.audiobooks.search.Provider;
import com.fanok.audiobooks.search.SearchUrls;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What every registered site must honour, checked without fixtures or network: a new {@link Provider} row gets these
 * checks for free, its own test keeps only what its pages show. Genre round trips are left to the sites' tests (a DLE
 * genre sorts only after its category id was learnt from a page).
 */
public class SourceContractTest {

    private static final Pattern HOST = Pattern.compile("android:host=\"([^\"]+)\"");

    /** A book link of the site opens in the app: its host is in the deep-link intent filter. */
    @Test
    public void everySiteHostIsADeepLink() throws IOException {
        File manifest = new File("src/main/AndroidManifest.xml");
        if (!manifest.exists()) {
            manifest = new File("app/src/main/AndroidManifest.xml");
        }
        Set<String> hosts = new HashSet<>();
        Matcher m = HOST.matcher(new String(Files.readAllBytes(manifest.toPath()), StandardCharsets.UTF_8));
        while (m.find()) {
            hosts.add(m.group(1));
        }
        for (Provider p : Provider.values()) {
            assertTrue(p + " is not in AndroidManifest.xml", hosts.contains(URI.create(p.server).getHost()));
        }
    }

    /** The index pages on, every Order item has a URL on the site, and a sorted listing keeps its menu or has none. */
    @Test
    public void listingsAndSortsStayOnTheSite() {
        for (Provider p : Provider.values()) {
            SourceParser site = p.parser;
            String second = site.pageUrl(p.index, 2);
            assertSame(p + " " + second, p, Provider.ofUrl(second));
            assertNotEquals(p + " page 2 is page 1", site.pageUrl(p.index, 1), second);
            for (Sort sort : sorts(site, p.index)) {
                String sorted = site.sortUrl(sort, p.index);
                assertNotNull(p + " " + sort + " has no URL", sorted);
                assertSame(p + " " + sort + " " + sorted, p, Provider.ofUrl(sorted));
                assertSame(p + " " + sort + " page 2", p, Provider.ofUrl(site.pageUrl(sorted, 2)));
                List<Sort> again = sorts(site, sorted);
                assertTrue(p + " " + sort + " loses its own menu item", again.isEmpty() || again.contains(sort));
            }
        }
    }

    /** Every period a listing offers is the one its URL then carries. */
    @Test
    public void periodsRoundTrip() {
        Calendar today = Calendar.getInstance();
        for (Provider p : Provider.values()) {
            SourceParser site = p.parser;
            List<String> listings = new ArrayList<>();
            listings.add(p.index);
            for (Sort sort : sorts(site, p.index)) {
                listings.add(site.sortUrl(sort, p.index));
            }
            for (String url : listings) {
                for (ListingFilters.Period period : site.periods(url)) {
                    String with = site.withPeriod(url, period, today);
                    assertEquals(p + " " + with, period, site.periodOf(with, today));
                    assertSame(p + " " + with, p, Provider.ofUrl(with));
                }
            }
        }
    }

    @Test
    public void bookSearchIsPagedOnTheSite() {
        for (Provider p : Provider.values()) {
            String first = p.parser.searchUrl(SearchUrls.Category.BOOKS, "мастер и маргарита", 1);
            String second = p.parser.searchUrl(SearchUrls.Category.BOOKS, "мастер и маргарита", 2);
            assertNotNull(p + " has no book search", first);
            assertSame(p + " " + first, p, Provider.ofUrl(first));
            assertNotEquals(p + " search page 2 is page 1", first, second);
        }
    }

    /**
     * Every host of a site follows that site's proxy route ({@code AppProxy} resolves a request's host through
     * {@link Provider#ofNetworkHost}); {@code hosts()} lists the other hosts only; a timeout is only ever raised.
     */
    @Test
    public void networkHooksAreConsistent() {
        Set<String> folders = new HashSet<>();
        for (Provider p : Provider.values()) {
            assertSame(p + " route", p, Provider.ofNetworkHost(p.host()));
            for (String own : p.parser.hosts()) {
                assertSame(p + " route of " + own, p, Provider.ofNetworkHost(own));
            }
            assertFalse(p + " lists its own host", p.parser.hosts().contains(p.host()));
            assertTrue(p + " timeout", p.parser.timeoutMs() >= SiteHttp.TIMEOUT_MS);
            assertTrue(p + " folder " + p.folder, p.folder.matches("\\w+") && folders.add(p.folder));
        }
    }

    /** The sorts of the Order menu of {@code url}, submenus flattened. */
    private static List<Sort> sorts(SourceParser site, String url) {
        List<Sort> out = new ArrayList<>();
        for (Sort.Entry entry : site.sorts(url)) {
            if (entry.sort != null) {
                out.add(entry.sort);
            }
            out.addAll(entry.items);
        }
        return out;
    }
}
