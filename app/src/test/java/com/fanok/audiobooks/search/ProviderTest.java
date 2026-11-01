package com.fanok.audiobooks.search;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.fanok.audiobooks.Consts;

import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

/** The registry's rows are identity: stored preference values, search keys, filter ids and the display order. */
public class ProviderTest {

    /** {name, title, prefValue, prefKey, sourceId}: what older installs have stored, in the order the UI shows. */
    private static final String[][] ROWS = {
            {"AKNIGA", "Akniga", "abook", "search_abook", "3"},
            {"KNIGAVUHE", "Книга в ухе", "kniga_v_uhe", "search_kniga_v_uhe", "0"},
            {"IZIBUK", "ИзиБук", "izibuc", "search_izibuc", "1"},
            {"ABMP3", "AudioBook-mp3", "audiobook_mp3", "search_abmp3", "2"},
            {"BAZA_KNIG", "База Книг", "baza_knig", "search_baza_knig", "4"},
            {"KNIGOBLUD", "Книгоблуд", "knigoblud", "search_knigoblud", "5"},
            {"AUDIOBOO", "Audioboo", "audioboo", "search_audioboo", "6"},
            {"OTRUB", "Отрубин", "otrub", "search_otrub", "7"},
            {"POLEKNIG", "Полекниг", "poleknig", "search_poleknig", "8"},
    };

    @Test
    public void aNetworkHostBelongsToItsSourceButIsNoBookHost() {
        assertSame(Provider.UKNIG, Provider.ofNetworkHost("uknig.com"));
        assertSame("a subdomain (a CDN of the site)", Provider.UKNIG, Provider.ofNetworkHost("cdn.uknig.com"));
        assertNull(Provider.ofNetworkHost("archive.org"));
        assertNull(Provider.ofNetworkHost("notuknig.com"));
        assertNull(Provider.ofNetworkHost(""));
        assertNull("a CDN host never makes a link a book of the site", Provider.ofUrl("https://cdn.uknig.com/b/1"));
    }

    /** Requests to a former host and to a declared host's subdomains follow the site's proxy route and Referer. */
    @Test
    public void formerHostsAndDeclaredHostsBelongToTheirSource() {
        assertSame(Provider.IZIBUK, Provider.ofNetworkHost("izibuk.ru"));
        assertSame(Provider.BAZA_KNIG, Provider.ofNetworkHost("baza-knig.ink"));
        assertSame("akniga's HLS host", Provider.AKNIGA, Provider.ofNetworkHost("r1.akniga.club"));
        assertSame(Provider.AKNIGA, Provider.ofNetworkHost("akniga.club"));
        assertNull(Provider.ofNetworkHost("akniga.club.example.com"));
        assertNull(Provider.ofNetworkHost("notakniga.club"));
    }

    @Test
    public void theSelectedSourceIsSearchedFirst() {
        Provider before = Provider.current();
        Consts.setSOURCE(Provider.UKNIG.prefValue);
        try {
            assertSame(Provider.UKNIG, Provider.selectedFirst().get(0));
            assertEquals("the active sources", Sources.activeCount(), Provider.selectedFirst().size());
            assertEquals(Sources.activeCount(), new HashSet<>(Provider.selectedFirst()).size());
        } finally {
            if (before != null) {
                Consts.setSOURCE(before.prefValue);
            }
        }
    }

    @Test
    public void theFirstNineRowsKeepTheirStoredValuesAndOrder() {
        Provider[] providers = Provider.values();
        assertTrue(providers.length >= ROWS.length);
        for (int i = 0; i < ROWS.length; i++) {
            Provider p = providers[i];
            assertEquals(ROWS[i][0], p.name());
            assertEquals(p.name(), ROWS[i][1], p.title);
            assertEquals(p.name(), ROWS[i][2], p.prefValue);
            assertEquals(p.name(), ROWS[i][3], p.prefKey);
            assertEquals(p.name(), Integer.parseInt(ROWS[i][4]), p.sourceId);
        }
    }

    @Test
    public void everyRowIsCompleteAndUnique() {
        Set<String> values = new HashSet<>();
        Set<String> keys = new HashSet<>();
        Set<Integer> ids = new HashSet<>();
        Set<String> hosts = new HashSet<>();
        for (Provider p : Provider.values()) {
            assertTrue(p.name(), values.add(p.prefValue));
            assertTrue(p.name(), keys.add(p.prefKey));
            assertTrue(p.name(), ids.add(p.sourceId));
            assertTrue(p.name(), hosts.add(p.host()));
            assertTrue(p.name(), p.prefKey.startsWith("search_"));
            assertTrue(p.name(), p.server.startsWith("https://") && !p.server.endsWith("/"));
            assertTrue(p.name() + " has no index", p.index != null && !p.index.isEmpty());
            assertSame(p.name(), p, Provider.ofUrl(p.index));
            if (p.sections != null) {
                assertSame(p.name(), p, Provider.ofUrl(p.sections));
            }
            assertSame(p.name(), p, Provider.ofSource(p.sourceId));
            assertSame(p.name(), p, Provider.require(p.server + "/some/book/"));
            Consts.setSOURCE(p.prefValue);
            assertSame(p.name(), p, Provider.current());
        }
    }

    @Test
    public void unknownHostsAreRejectedOrNull() {
        assertNull(Provider.ofUrl("https://example.com/book"));
        assertNull(Provider.ofUrl(null));
        assertNull(Provider.ofSource(-1));
        assertThrows(IllegalArgumentException.class, () -> Provider.require("https://knigavuhe.org.example.com/x"));
        assertNull(Provider.parserOf("https://example.com/book"));
    }

    /** Every site is an adapter: the shared code has no per-site branches left to fall back on. */
    @Test
    public void everySiteHasItsAdapter() {
        for (Provider provider : Provider.values()) {
            assertSame(provider.parser, Provider.parserOf(provider.server + "/"));
            assertTrue(provider + " has no adapter", provider.parser != null);
        }
    }

    @Test
    public void movedHostsResolveToTheirProvider() {
        assertSame(Provider.IZIBUK, Provider.ofUrl("https://izibuk.ru/art1"));
        assertSame(Provider.BAZA_KNIG, Provider.ofUrl("https://baza-knig.ink/x/1-y.html"));
        assertSame(Provider.KNIGOBLUD, Provider.ofUrl("https://www.knigoblud.club/e87cc048"));
        assertArrayEquals(new String[]{"akniga.org", "knigavuhe.org", "izib.uk"},
                new String[]{Provider.AKNIGA.host(), Provider.KNIGAVUHE.host(), Provider.IZIBUK.host()});
    }

    // changed on purpose after the live check (2026-10-30): m.otrub.in and mob.knigoblud.club are the mobile hosts of Otrub and Knigoblud
    @Test
    public void mobileHostsResolveToTheirProvider() {
        assertSame(Provider.OTRUB, Provider.ofUrl("https://m.otrub.in/book/1"));
        assertSame(Provider.KNIGOBLUD, Provider.ofUrl("https://mob.knigoblud.club/e87cc048"));
        assertSame(Provider.KNIGOBLUD.parser, Provider.parserOf("https://mob.knigoblud.club/e87cc048"));
        assertSame(Provider.OTRUB.parser, Provider.parserOf("http://m.otrub.in/popular/?period=month&p=1"));
        assertNull(Provider.ofUrl("https://m.otrub.in.evil.com/book/1"));
        assertNull(Provider.ofUrl("https://mob.knigoblud.club.evil.com/e87cc048"));
    }
}
