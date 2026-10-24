package com.fanok.audiobooks.net;

import androidx.annotation.NonNull;

import com.fanok.audiobooks.Url;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import okhttp3.Cookie;
import okhttp3.CookieJar;
import okhttp3.HttpUrl;

/**
 * Process-lifetime cookies of the shared OkHttp client: what a host sets is sent back to requests that
 * {@link Cookie#matches} (its host, path and scheme), never to other hosts. Expired cookies are dropped.
 */
public final class MemoryCookieJar implements CookieJar {

    /** Cookie domain → (name; domain; path) → cookie. */
    private final Map<String, Map<String, Cookie>> byDomain = new ConcurrentHashMap<>();

    @Override
    public void saveFromResponse(@NonNull HttpUrl url, @NonNull List<Cookie> cookies) {
        for (Cookie cookie : cookies) {
            Map<String, Cookie> jar = byDomain.computeIfAbsent(cookie.domain(), d -> new ConcurrentHashMap<>());
            String key = cookie.name() + ";" + cookie.domain() + ";" + cookie.path();
            if (cookie.expiresAt() < System.currentTimeMillis()) {
                jar.remove(key);
            } else {
                jar.put(key, cookie);
            }
        }
    }

    @NonNull
    @Override
    public List<Cookie> loadForRequest(@NonNull HttpUrl url) {
        long now = System.currentTimeMillis();
        List<Cookie> result = new ArrayList<>();
        for (Map<String, Cookie> jar : byDomain.values()) {
            for (Map.Entry<String, Cookie> e : jar.entrySet()) {
                Cookie cookie = e.getValue();
                if (cookie.expiresAt() < now) {
                    dropExpired(jar, e.getKey(), cookie);
                } else if (cookie.matches(url)) {
                    result.add(cookie);
                }
            }
        }
        return result;
    }

    /** Removes the expired {@code seen} only: a cookie saved under the same key meanwhile stays. */
    static void dropExpired(@NonNull Map<String, Cookie> jar, @NonNull String key, @NonNull Cookie seen) {
        jar.remove(key, seen);
    }

    /**
     * A view of this jar that also sends {@code extra} (name → value) with every https request to the host of
     * {@code server} whose stored cookies do not already carry that name: the immutable request context of one
     * download or playback (e.g. a session id). Requests to any other host or over http never get it.
     */
    @NonNull
    public CookieJar with(@NonNull String server, @NonNull Map<String, String> extra) {
        final Map<String, String> context = Collections.unmodifiableMap(new LinkedHashMap<>(extra));
        return new CookieJar() {
            @Override
            public void saveFromResponse(@NonNull HttpUrl url, @NonNull List<Cookie> cookies) {
                MemoryCookieJar.this.saveFromResponse(url, cookies);
            }

            @NonNull
            @Override
            public List<Cookie> loadForRequest(@NonNull HttpUrl url) {
                List<Cookie> cookies = MemoryCookieJar.this.loadForRequest(url);
                if (!url.isHttps() || !Url.isHost(url.toString(), server)) return cookies;
                List<Cookie> result = new ArrayList<>(cookies);
                for (Map.Entry<String, String> e : context.entrySet()) {
                    boolean present = false;
                    for (Cookie c : cookies) present |= c.name().equals(e.getKey());
                    if (!present) {
                        try {
                            result.add(new Cookie.Builder().name(e.getKey()).value(e.getValue())
                                    .hostOnlyDomain(url.host()).secure().build());
                        } catch (IllegalArgumentException invalid) {
                            // A malformed stored session value is not sent rather than failing the request.
                        }
                    }
                }
                return result;
            }
        };
    }
}
