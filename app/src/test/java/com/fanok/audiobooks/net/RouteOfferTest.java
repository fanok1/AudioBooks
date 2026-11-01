package com.fanok.audiobooks.net;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.fanok.audiobooks.net.RouteOffer.Kind;
import com.fanok.audiobooks.search.Provider;

import org.junit.After;
import org.junit.Test;

public class RouteOfferTest {

    @After
    public void tearDown() {
        RouteOffer.reset();
    }

    @Test
    public void claimsOncePerProvider() {
        assertTrue(RouteOffer.claim(Provider.AKNIGA));
        assertFalse(RouteOffer.claim(Provider.AKNIGA));
        assertTrue(RouteOffer.claim(Provider.KNIGAVUHE));
        assertFalse(RouteOffer.claim(Provider.KNIGAVUHE));
    }

    @Test
    public void resetAllowsANewOffer() {
        assertTrue(RouteOffer.claim(Provider.AKNIGA));
        RouteOffer.reset();
        assertTrue(RouteOffer.claim(Provider.AKNIGA));
    }

    @Test
    public void modeOffOffersToEnableTheProxy() {
        for (boolean wasDirect : new boolean[]{false, true}) {
            for (boolean usable : new boolean[]{false, true}) {
                assertEquals(Kind.ENABLE_PROXY, RouteOffer.of(true, wasDirect, usable));
            }
        }
    }

    @Test
    public void usableProxyOffersTheOtherRoute() {
        assertEquals(Kind.TO_PROXY, RouteOffer.of(false, true, true));
        assertEquals(Kind.TO_DIRECT, RouteOffer.of(false, false, true));
    }

    @Test
    public void noUsableProxyOffersNothing() {
        assertEquals(Kind.NONE, RouteOffer.of(false, true, false));
        assertEquals(Kind.NONE, RouteOffer.of(false, false, false));
    }
}
