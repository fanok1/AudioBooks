package com.fanok.audiobooks;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Proxy;
import java.net.URL;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

/** Book-page fixtures under {@code src/test/resources/fixtures/<source>/} and the page URL each was captured from. */
public final class BookPageFixtures {

    /** {fixture path, book URL}; audioboo's page was POSTed (the live site challenges every GET). */
    public static final String[][] BOOK_PAGES = {
            {"knigavuhe/book-description.html", "https://knigavuhe.org/book/uzhasy-po-chasam/"},
            {"izibuk/book-description.html", "https://izib.uk/art144181"},
            {"abmp3/book-description.html", "https://audiobook-mp3.com/audio-61536-45238-palec"},
            {"akniga/book-description.html", "https://akniga.org/grehem-yan-monument-chast-1"},
            {"bazaknig/book-description.html",
                    "https://baza-knig.top/roman-proza/182526-objali-menja-vody-do-dushi-moej-kjendzaburo-oje.html"},
            {"knigoblud/book-description.html", "https://www.knigoblud.club/099fb474-923c-4a06-8c01-a3f831109467"},
            {"audioboo/book-description.html", "https://audioboo.org/fantastika/123139-mahanenko-vasilij-solo-06.html"},
            {"otrub/book-description.html", "https://otrub.in/volnyjj-lekar-student-tom-7.html"},
            {"poleknig/book-description.html", "https://poleknig.com/books/269329"},
    };

    private BookPageFixtures() {
    }

    public static Document load(String path, String baseUrl) throws IOException {
        try (InputStream in = BookPageFixtures.class.getResourceAsStream("/fixtures/" + path)) {
            if (in == null) {
                throw new IOException("missing fixture " + path);
            }
            return Jsoup.parse(in, null, baseUrl);
        }
    }

    public static String text(String path) throws IOException {
        return load(path, "https://example.invalid/").outerHtml();
    }

    /** A response as {@code SiteHttp.parse} sees it, without a network. */
    public static Connection.Response response(String url, int status, String cfMitigated, String html) {
        return (Connection.Response) Proxy.newProxyInstance(BookPageFixtures.class.getClassLoader(),
                new Class<?>[]{Connection.Response.class}, (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "url":
                            return new URL(url);
                        case "statusCode":
                            return status;
                        case "header":
                            return "cf-mitigated".equalsIgnoreCase((String) args[0]) ? cfMitigated : null;
                        case "bodyAsBytes":
                            return html.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                        case "parse":
                            return Jsoup.parse(html, url);
                        default:
                            throw new UnsupportedOperationException(method.getName());
                    }
                });
    }
}
