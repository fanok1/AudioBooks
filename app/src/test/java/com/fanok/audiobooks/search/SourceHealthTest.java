package com.fanok.audiobooks.search;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;

/** The kept results of the availability check and when the automatic check is due. */
public class SourceHealthTest {

    @Test
    public void resultsAreKeptPerSiteAndReadBack() {
        Map<Provider, SourceProbe.Result> back = SourceHealth.decode(SourceHealth.encode(Arrays.asList(
                new SourceProbe.Result(Provider.AKNIGA, ProviderPage.Status.OK, 800, null),
                new SourceProbe.Result(Provider.AUDIOBOO, ProviderPage.Status.BLOCKED, 300, "challenge"),
                new SourceProbe.Result(Provider.UKNIG, ProviderPage.Status.TIMEOUT, 15_000, "no answer"))));
        assertEquals(3, back.size());
        assertEquals(ProviderPage.Status.OK, back.get(Provider.AKNIGA).status);
        assertEquals(800, back.get(Provider.AKNIGA).millis);
        assertEquals(ProviderPage.Status.BLOCKED, back.get(Provider.AUDIOBOO).status);
        assertEquals(ProviderPage.Status.TIMEOUT, back.get(Provider.UKNIG).status);
        assertEquals(15_000, back.get(Provider.UKNIG).millis);
    }

    @Test
    public void anUnknownSiteOrStatusIsSkipped() {
        Map<Provider, SourceProbe.Result> back = SourceHealth.decode(new HashSet<>(Arrays.asList(
                "abook=OK", "gone_site=OK=10", "uknig=SOMETHING_NEW=10", "izibuc=OK=fast", "garbage")));
        assertEquals("an older version's entry has no time", 1, back.size());
        assertEquals(ProviderPage.Status.OK, back.get(Provider.AKNIGA).status);
        assertEquals(0, back.get(Provider.AKNIGA).millis);
    }

    @Test
    public void theAutomaticCheckRunsOnceADay() {
        long now = 10 * SourceHealth.DAY_MS;
        assertTrue("never checked", SourceHealth.due(0, now));
        assertFalse(SourceHealth.due(now - SourceHealth.DAY_MS + 1, now));
        assertTrue(SourceHealth.due(now - SourceHealth.DAY_MS, now));
        assertTrue("the clock went back", SourceHealth.due(now + 1000, now));
    }
}
