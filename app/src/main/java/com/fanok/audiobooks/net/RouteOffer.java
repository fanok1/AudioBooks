package com.fanok.audiobooks.net;

import androidx.annotation.NonNull;

import com.fanok.audiobooks.search.Provider;

import java.util.EnumSet;
import java.util.Set;

/** When a site's listing fails: which other route to offer, and only once per site per process so it never nags. */
public final class RouteOffer {

    /** The offer for a failed site: turn the proxy on, move the site to the proxy or off it, or nothing to offer. */
    public enum Kind { ENABLE_PROXY, TO_PROXY, TO_DIRECT, NONE }

    private static final Set<Provider> offered = EnumSet.noneOf(Provider.class);

    private RouteOffer() {
    }

    /** With no usable proxy the other route does not exist: a "proxied" site already goes direct. */
    @NonNull
    public static Kind of(boolean modeOff, boolean wasDirect, boolean proxyUsable) {
        if (modeOff) {
            return Kind.ENABLE_PROXY;
        }
        if (!proxyUsable) {
            return Kind.NONE;
        }
        return wasDirect ? Kind.TO_PROXY : Kind.TO_DIRECT;
    }

    /** True the first time only: a declined or tried offer is not made again for the site until the app restarts. */
    public static synchronized boolean claim(@NonNull Provider provider) {
        return offered.add(provider);
    }

    /** Tests start from a fresh process. */
    static synchronized void reset() {
        offered.clear();
    }
}
