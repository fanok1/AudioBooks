package com.fanok.audiobooks.search;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.fanok.audiobooks.Consts;
import com.fanok.audiobooks.net.FakePrefs;

import org.junit.After;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** The user's sources: the stored order and active count, read back safely; the selected source stays active. */
public class SourcesTest {

    @After
    public void restoreDefaults() {
        Sources.load(new FakePrefs());
        Consts.setSOURCE(Provider.AKNIGA.prefValue);
    }

    @Test
    public void nothingStoredIsTheDeclarationOrderWithTheFirstThirteenActive() {
        Sources.State state = Sources.parse(null);
        assertEquals(Arrays.asList(Provider.values()), state.order);
        assertEquals(Math.min(Sources.MAX_ACTIVE, Provider.values().length), state.active);
    }

    @Test
    public void theStoredTextIsReadBack() {
        List<Provider> order = new ArrayList<>(Arrays.asList(Provider.values()));
        java.util.Collections.reverse(order);
        Sources.State state = Sources.parse(Sources.format(order, 3));
        assertEquals(order, state.order);
        assertEquals(3, state.active);
        assertEquals("as many active as allowed", Math.min(Sources.MAX_ACTIVE, order.size()),
                Sources.parse(Sources.format(order, order.size())).active);
    }

    @Test
    public void unknownAndRepeatedIdsAreDroppedAndAMissingSiteStartsInactive() {
        Sources.State state = Sources.parse("uknig,gone_site,uknig,abook|izibuc");
        assertEquals(Arrays.asList(Provider.UKNIG, Provider.AKNIGA, Provider.IZIBUK),
                state.order.subList(0, 3));
        assertEquals(2, state.active);
        assertEquals(Provider.values().length, state.order.size());
    }

    @Test
    public void theActiveCountStaysBetweenOneAndTheMaximum() {
        assertEquals("none active: the first one is", 1, Sources.parse("|abook,uknig").active);
        assertEquals(1, Sources.parse(Sources.format(Arrays.asList(Provider.values()), 0)).active);
        assertEquals(1, Sources.parse("garbage").active);
        StringBuilder all = new StringBuilder();
        for (Provider provider : Provider.values()) {
            all.append(provider.prefValue).append(',');
        }
        assertTrue(Sources.parse(all.toString()).active <= Sources.MAX_ACTIVE);
    }

    @Test
    public void switchingOffTheSelectedSourceSelectsTheFirstActiveOne() {
        FakePrefs prefs = new FakePrefs();
        prefs.edit().putString("sorce_books", Provider.UKNIG.prefValue).apply();
        List<Provider> order = Arrays.asList(Provider.IZIBUK, Provider.AKNIGA, Provider.UKNIG);
        assertFalse("the selected one stays active", Sources.save(prefs, order, 3));
        assertTrue(Sources.save(prefs, order, 2));
        assertEquals(Provider.IZIBUK.prefValue, prefs.getString("sorce_books", null));
        assertEquals(Provider.IZIBUK.sourceId, Consts.getSOURCE());
        assertEquals(Arrays.asList(Provider.IZIBUK, Provider.AKNIGA), Sources.active());
        assertFalse(Sources.isActive(Provider.UKNIG));
        assertTrue("the rest follow, inactive", Sources.all().containsAll(Arrays.asList(Provider.values())));
    }

    @Test
    public void theListIsLoadedFromThePreferences() {
        FakePrefs prefs = new FakePrefs();
        prefs.edit().putString(Sources.PREF, "otrub,abook|uknig").apply();
        Sources.load(prefs);
        assertEquals(Arrays.asList(Provider.OTRUB, Provider.AKNIGA), Sources.active());
        Consts.setSOURCE(Provider.AKNIGA.prefValue);
        assertEquals("the selected one first, then the user's order", Arrays.asList(Provider.AKNIGA, Provider.OTRUB),
                Provider.selectedFirst());
        prefs.edit().putInt(Sources.PREF, 5).apply();
        Sources.load(prefs); // a wrongly typed value: the defaults
        assertEquals(Sources.parse(null).active, Sources.activeCount());
    }
}
