package com.fanok.audiobooks.net;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.fanok.audiobooks.Url;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import okhttp3.Cookie;
import okhttp3.CookieJar;
import okhttp3.HttpUrl;
import org.junit.Test;

public class MemoryCookieJarTest {

    private static final HttpUrl BAZA = HttpUrl.get("https://baza-knig.top/book/1");
    private static final HttpUrl BAZA_MEDIA = HttpUrl.get("https://baza-knig.top/media/1.mp3");
    private static final HttpUrl OTHER = HttpUrl.get("https://akniga.org/book/1");

    @Test
    public void storedCookieIsResentToItsHostOnly() {
        MemoryCookieJar jar = new MemoryCookieJar();
        jar.saveFromResponse(BAZA, Collections.singletonList(Cookie.parse(BAZA, "PHPSESSID=abc; Path=/")));

        List<Cookie> sent = jar.loadForRequest(BAZA_MEDIA);
        assertEquals(1, sent.size());
        assertEquals("abc", sent.get(0).value());
        assertTrue(jar.loadForRequest(OTHER).isEmpty());
    }

    @Test
    public void newerValueReplacesAndExpiredCookieIsDropped() {
        MemoryCookieJar jar = new MemoryCookieJar();
        jar.saveFromResponse(BAZA, Collections.singletonList(Cookie.parse(BAZA, "a=1; Path=/")));
        jar.saveFromResponse(BAZA, Collections.singletonList(Cookie.parse(BAZA, "a=2; Path=/")));
        assertEquals("2", jar.loadForRequest(BAZA).get(0).value());

        jar.saveFromResponse(BAZA, Collections.singletonList(Cookie.parse(BAZA, "a=3; Path=/; Max-Age=0")));
        assertTrue(jar.loadForRequest(BAZA).isEmpty());
    }

    @Test
    public void contextCookieGoesOnlyToTheSessionHostOverHttpsUnlessTheHostSetItsOwn() {
        MemoryCookieJar jar = new MemoryCookieJar();
        CookieJar scoped = jar.with(Url.SERVER_BAZA_KNIG, Collections.singletonMap("PHPSESSID", "ctx"));

        // A CDN or any other host of a baza-knig download never gets the session, nor does plain http.
        assertTrue(scoped.loadForRequest(OTHER).isEmpty());
        assertTrue(scoped.loadForRequest(HttpUrl.get("https://cdn.baza-knig.top/1.mp3")).isEmpty());
        assertTrue(scoped.loadForRequest(HttpUrl.get("http://baza-knig.top/book/1")).isEmpty());
        assertEquals("ctx", scoped.loadForRequest(BAZA).get(0).value());
        assertTrue(scoped.loadForRequest(BAZA).get(0).secure());

        scoped.saveFromResponse(BAZA, Collections.singletonList(Cookie.parse(BAZA, "PHPSESSID=srv; Path=/")));
        List<Cookie> sent = scoped.loadForRequest(BAZA);
        assertEquals(1, sent.size());
        assertEquals("srv", sent.get(0).value());
        // The context never leaks into the shared jar.
        assertTrue(jar.loadForRequest(OTHER).isEmpty());
    }

    @Test
    public void aRefreshedCookieSurvivesTheCleanupOfItsExpiredValue() {
        Cookie expired = new Cookie.Builder().name("a").value("1").domain("baza-knig.top").expiresAt(1).build();
        Cookie refreshed = Cookie.parse(BAZA, "a=2; Path=/");
        String key = "a;baza-knig.top;/";
        Map<String, Cookie> jar = new ConcurrentHashMap<>();
        // A reader saw the expired value; a response refreshed the cookie before the reader removed it.
        jar.put(key, refreshed);

        MemoryCookieJar.dropExpired(jar, key, expired);
        assertEquals(refreshed, jar.get(key));

        jar.put(key, expired);
        MemoryCookieJar.dropExpired(jar, key, expired);
        assertTrue(jar.isEmpty());
    }
}
