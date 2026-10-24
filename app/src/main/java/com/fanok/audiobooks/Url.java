package com.fanok.audiobooks;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class Url {
    public static final String SERVER = "https://knigavuhe.org";
    public static final String INDEX = SERVER + "/new/?page=";
    public static final String SECTIONS = SERVER + "/genres/";
    public static final String AUTHORS = SERVER + "/authors/";
    public static final String PERFORMERS = SERVER + "/readers/";
    public static final String NEW_BOOK = SERVER + "/new/?page=";
    public static final String BEST_TODAY = SERVER + "/popular/?w=today&page=";
    public static final String BEST_WEEK = SERVER + "/popular/?w=week&page=";
    public static final String BEST_MONTH = SERVER + "/popular/?w=month&page=";
    public static final String BEST_ALL_TIME = SERVER + "/popular/?w=alltime&page=";
    public static final String RATING_TODATY = SERVER + "/rating/?w=today&page=";
    public static final String RATING_WEEK = SERVER + "/rating/?w=week&page=";
    public static final String RATING_MONTH = SERVER + "/rating/?w=month&page=";
    public static final String RATING_ALL_TIME = SERVER + "/rating/?w=alltime&page=";

    public static final String SERVER_IZIBUK = "https://izib.uk";

    public static final String INDEX_IZIBUK = SERVER_IZIBUK + "/?p=";
    public static final String SECTIONS_IZIBUK = SERVER_IZIBUK + "/genres?p=";
    public static final String AUTHORS_IZIBUK = SERVER_IZIBUK + "/authors?p=";
    public static final String PERFORMERS_IZIBUK = SERVER_IZIBUK + "/readers?p=";


    public static final String SERVER_ABMP3 = "https://audiobook-mp3.com";
    public static final String CDN_ABMP3 = "https://cdn.audiobook-mp3.com";
    public static final String INDEX_ABMP3 = SERVER_ABMP3 + "/?page=";
    public static final String SECTIONS_ABMP3 = SERVER_ABMP3 + "/genres";
    public static final String AUTHORS_ABMP3 = SERVER_ABMP3 + "/authors?page=";
    public static final String PERFORMERS_ABMP3 = SERVER_ABMP3 + "/performers?page=";
    public static final String ABMP3_TOP = SERVER_ABMP3 + "/top";
    public static final String ABMP3_RANDOM = SERVER_ABMP3 + "/random-book";


    public static final String SERVER_AKNIGA = "https://akniga.org";

    public static final String INDEX_AKNIGA = SERVER_AKNIGA + "/index/page<page>/";
    public static final String SECTIONS_AKNIGA = SERVER_AKNIGA + "/sections/";
    public static final String AUTHORS_AKNIGA = SERVER_AKNIGA + "/authors/page<page>/";
    public static final String PERFORMERS_AKNIGA = SERVER_AKNIGA + "/performers/page<page>/";

    public static final String NEW_BOOK_AKNIGA = SERVER_AKNIGA + "/index/page<page>/";
    public static final String BEST_TODAY_AKNIGA = SERVER_AKNIGA + "/index/top/page<page>/?period=1";
    public static final String BEST_WEEK_AKNIGA = SERVER_AKNIGA + "/index/top/page<page>/?period=7";
    public static final String BEST_MONTH_AKNIGA = SERVER_AKNIGA + "/index/top/page<page>/?period=30";
    public static final String BEST_ALL_TIME_AKNIGA = SERVER_AKNIGA + "/index/top/page<page>/?period=all";
    public static final String RATING_TODATY_AKNIGA = SERVER_AKNIGA + "/index/discussed/page<page>/?period=1";
    public static final String RATING_WEEK_AKNIGA = SERVER_AKNIGA + "/index/discussed/page<page>/?period=7";
    public static final String RATING_MONTH_AKNIGA = SERVER_AKNIGA + "/index/discussed/page<page>/?period=30";
    public static final String RATING_ALL_TIME_AKNIGA = SERVER_AKNIGA + "/index/discussed/page<page>/?period=all";
    public static final String BEST_YEAR_AKNIGA = SERVER_AKNIGA + "/index/top/page<page>/?period=365";
    public static final String RATING_YEAR_AKNIGA = SERVER_AKNIGA + "/index/discussed/page<page>/?period=365";
    public static final String COLLECTIONS_AKNIGA = SERVER_AKNIGA + "/collections/page<page>/";
    public static final String COLLECTIONS_TOP_AKNIGA = SERVER_AKNIGA + "/collections/top/page<page>/";
    public static final String COLLECTIONS_DISCUSSED_AKNIGA = SERVER_AKNIGA + "/collections/discussed/page<page>/";


    public static final String SERVER_BAZA_KNIG = "https://baza-knig.top";
    public static final String INDEX_BAZA_KNIG = SERVER_BAZA_KNIG + "/page/";
    public static final String SECTIONS_BAZA_KNIG = SERVER_BAZA_KNIG;
    public static final String NEW_BOOK_BAZA_KNIG = SERVER_BAZA_KNIG + "/page/";
    public static final String BEST_BAZA_KNIG = SERVER_BAZA_KNIG + "/f/sort=news_read/order=desc/page/";
    public static final String RATING_BAZA_KNIG = SERVER_BAZA_KNIG + "/f/sort=rating/order=desc/page/";
    public static final String COMENTS_BAZA_KNIG = SERVER_BAZA_KNIG + "/f/sort=comm_num/order=desc/page/";
    public static final String YEARS_BAZA_KNIG = SERVER_BAZA_KNIG + "/f/sort=d.god/order=desc/page/";


    public static final String SERVER_KNIGOBLUD = "https://www.knigoblud.club";
    public static final String INDEX_KNIGOBLUD = SERVER_KNIGOBLUD;
    public static final String SECTIONS_KNIGOBLUD = SERVER_KNIGOBLUD + "/genres";

    public static final String TODAY_KNIGOBLUD = SERVER_KNIGOBLUD + "/today/?page=";
    public static final String POPULAR_WEEK_KNIGOBLUD = SERVER_KNIGOBLUD + "/popular/?period=week&page=";
    public static final String POPULAR_MONTH_KNIGOBLUD = SERVER_KNIGOBLUD + "/popular/?period=month&page=";
    public static final String TRENDING_KNIGOBLUD = SERVER_KNIGOBLUD + "/trending/?page=";
    public static final String FINISHED_KNIGOBLUD = SERVER_KNIGOBLUD + "/finished/?page=";

    public static final String SERVER_AUDIOBOO = "https://audioboo.org";
    public static final String INDEX_AUDIOBOO = SERVER_AUDIOBOO + "/page/";


    public static final String SERVER_OTRUB = "https://otrub.in";
    public static final String INDEX_OTRUB = SERVER_OTRUB + "/?p=";
    public static final String SECTIONS_OTRUB = SERVER_OTRUB + "/genres.html";
    public static final String NEW_BOOK_OTRUB = SERVER_OTRUB + "/?p=";
    public static final String BEST_WEEK_OTRUB = SERVER_OTRUB + "/popular/?period=week&p=";
    public static final String BEST_MONTH_OTRUB = SERVER_OTRUB + "/popular/?period=month&p=";


    public static final String SERVER_POLEKNIG = "https://poleknig.com";
    public static final String INDEX_POLEKNIG = SERVER_POLEKNIG + "/?p=";
    public static final String SECTIONS_POLEKNIG = SERVER_POLEKNIG + "/genres";
    public static final String NEW_BOOK_POLEKNIG = SERVER_POLEKNIG + "/books/novelties?p=";
    public static final String BEST_POLEKNIG = SERVER_POLEKNIG + "/books/popular?p=";

    /** Hosts the providers used earlier → host they use now (data, not code: extend when a site moves). */
    private static final Map<String, String> HOST_ALIASES = new HashMap<>();

    static {
        HOST_ALIASES.put("baza-knig.ink", "baza-knig.top");
        HOST_ALIASES.put("izibuk.ru", "izib.uk");
        HOST_ALIASES.put("audioboo.ru", "audioboo.org");
        HOST_ALIASES.put("m.knigavuhe.org", "knigavuhe.org");
        HOST_ALIASES.put("pda.izibuk.ru", "izib.uk");
    }

    /** Lower-case host of {@code url} without a leading {@code www.}; empty when unparsable. */
    public static String hostOf(String url) {
        URI uri = parse(url);
        String host = uri == null ? null : uri.getHost();
        if (host == null) {
            return "";
        }
        host = host.toLowerCase(Locale.ROOT);
        return host.startsWith("www.") ? host.substring(4) : host;
    }

    /**
     * {@code url} as a {@link URI}, tolerating what browsers and Jsoup tolerate (akniga links authors as
     * {@code /author/Кинг Стивен/}); null when it is still not a URI.
     */
    static URI parse(String url) {
        if (url == null) {
            return null;
        }
        try {
            return URI.create(url.trim());
        } catch (IllegalArgumentException strict) {
            try {
                return URI.create(uriSafe(url));
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
    }

    /**
     * {@code url} trimmed, with what a browser percent-encodes but {@link URI} rejects encoded as UTF-8: spaces and
     * control characters, {@code " < > ^ ` { | }} and a '%' that starts no escape. Structural characters
     * ({@code : / ? # @ [ ] \}) are kept, so the authority splits exactly as before and a spoof stays unparsable.
     */
    private static String uriSafe(String url) {
        String s = url.trim();
        StringBuilder b = null;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            boolean bad = c <= 0x20 || c == 0x7f || "\"<>^`{|}".indexOf(c) >= 0
                    || (c > 0x7f && (Character.isSpaceChar(c) || Character.isISOControl(c)))
                    || (c == '%' && !(i + 2 < s.length() && isHex(s.charAt(i + 1)) && isHex(s.charAt(i + 2))));
            if (bad && b == null) {
                b = new StringBuilder(s.length() + 16).append(s, 0, i);
            }
            if (b != null) {
                if (bad) {
                    for (byte x : String.valueOf(c).getBytes(StandardCharsets.UTF_8)) {
                        b.append('%').append(HEX.charAt((x >> 4) & 0xf)).append(HEX.charAt(x & 0xf));
                    }
                } else {
                    b.append(c);
                }
            }
        }
        return b == null ? s : b.toString();
    }

    private static final String HEX = "0123456789ABCDEF";

    private static boolean isHex(char c) {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
    }

    /** True when {@code url} is served by {@code server} (a {@code SERVER_*} constant or any URL); compares parsed hosts. */
    public static boolean isHost(String url, String server) {
        String host = hostOf(url);
        return !host.isEmpty() && host.equals(hostOf(server));
    }

    /** Drops a first-page marker ({@code /page/1/}, {@code /page/1}) that DataLife Engine sites redirect away. */
    public static String withoutFirstPage(String url) {
        int end = pathEnd(url);
        return url.substring(0, end).replaceFirst("/page/1/?$", "/") + url.substring(end);
    }

    /**
     * Page {@code page} of an audioboo listing: the index, a genre, an author, reader, tag or series link, with or
     * without a trailing {@code page/} or {@code page/N/}. Page 1 is the bare listing, page N adds {@code page/N/}.
     */
    public static String audiobooListingPage(String url, int page) {
        String trimmed = url.trim();
        int end = pathEnd(trimmed);
        String base = trimmed.substring(0, end).replaceFirst("/page(/\\d+)?/?$", "/");
        if (!base.endsWith("/")) {
            base += "/";
        }
        return (page <= 1 ? base : base + "page/" + page + "/") + trimmed.substring(end);
    }

    /**
     * Page {@code page} of an audiobook-mp3 listing (the index, top, a genre, author, reader or the authors' list):
     * the {@code page} query parameter only. The site answers {@code ?page=2/} with HTTP 500.
     */
    public static String abmp3ListingPage(String url, int page) {
        okhttp3.HttpUrl parsed = okhttp3.HttpUrl.parse(url.trim());
        if (parsed == null) {
            return url;
        }
        okhttp3.HttpUrl.Builder builder = parsed.newBuilder().removeAllQueryParameters("page");
        if (page > 1) {
            builder.addQueryParameter("page", Integer.toString(page));
        }
        return builder.build().toString();
    }

    /** Where the path of {@code url} ends: at its query or fragment, else at its end. */
    private static int pathEnd(String url) {
        int scheme = url.indexOf("://");
        for (int i = scheme < 0 ? 0 : scheme + 3; i < url.length(); i++) {
            if (url.charAt(i) == '?' || url.charAt(i) == '#') {
                return i;
            }
        }
        return url.length();
    }

    /** Rewrites a URL on a host that has since moved to the current host; other URLs are returned unchanged. */
    public static String canonical(String url) {
        String current = HOST_ALIASES.get(hostOf(url));
        if (current == null) {
            return url;
        }
        //non-null: hostOf found a host
        URI uri = parse(url);
        String s = url.trim();
        String raw = uri.getRawAuthority();
        int start = s.indexOf("://") + 3;
        if (start < 3 || !s.startsWith(raw, start)) {
            //the authority itself needed encoding: leave such a URL alone
            return url;
        }
        String www = uri.getHost().toLowerCase(Locale.ROOT).startsWith("www.") ? "www." : "";
        String authority = (uri.getRawUserInfo() != null ? uri.getRawUserInfo() + "@" : "") + www + current
                + (uri.getPort() >= 0 ? ":" + uri.getPort() : "");
        //path, query and fragment stay byte-identical: they are identity keys of saved books and downloads
        return (s.substring(0, start) + authority + s.substring(start + raw.length()))
                .replaceFirst("^(?i)http://", "https://");
    }

}
