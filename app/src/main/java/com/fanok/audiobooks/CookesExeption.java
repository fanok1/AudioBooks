package com.fanok.audiobooks;

/** A challenge page (Cloudflare, DDoS-Guard) instead of content; the message is the URL that was asked for. */
public class CookesExeption extends IllegalArgumentException {

    public CookesExeption(final String s) {
        super(s);
    }

    /** What to tell the user: only baza-knig has a session the user can renew in the settings. */
    public static int messageFor(String url) {
        return Url.isHost(url, Url.SERVER_BAZA_KNIG) ? R.string.cookes_baza_knig_exeption : R.string.site_challenge;
    }
}
