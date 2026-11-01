package com.fanok.audiobooks;

import com.fanok.audiobooks.net.JsonPage;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Proxy;
import java.net.URL;
import java.util.Scanner;

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
            {"uknig/book-series.html", "https://uknig.com/books/708261"},
            {"audiokniga-life/book.html", "https://audiokniga.life/fantastika/6508-noldsapfir-2-s-t-i-k-s.html"},
            {"audioknigi-fun/book.html", "https://audioknigi.fun/81692-na-vyruchku-yunomu-gassi.html"},
            {"aknig-xyz/book.html", "https://s2.aknig.xyz/14731-chetvertoe-krylo.html"},
            {"aknigi24/book.html",
                    "https://aknigi24.com/book/garri-potter-i-metody-racionalnogo-mysleniia-iudkovski-eliezer-ali-wodan"},
            {"lis10book/book.json", "https://lis10book.com/audio/zheleznoe-plamya/"},
            {"audiokniga-one/book.html", "https://audiokniga.one/16149-idealnyj-mir-dlja-sociopata-1-13.html"},
            {"biblus/book.html", "https://biblus.org/konec-vechnosti.html"},
            {"audiokniga-online/book.html", "https://audiokniga-online.ru/klasika/247-vojna-i-mir.html"},
            {"slushat-knigi/book.html",
                    "https://slushat-knigi.com/34123-audiokniga-master-i-margarita-mihail-bulgakov.html"},
            {"audioknigi-pro/book.html",
                    "https://audioknigi.pro/audioknigi/22614-koleso-vremeni-11-nogh-snovideniy-robert-dghordan.html"},
            {"bookish/book-series.html",
                    "https://bookish.site/145424-revizor-vozvrashchenie-v-sssr-65-vinterkei-serzh-shumilin-artem.html"},
            {"golosknig/book.html", "https://golosknig.com/yacheyka-21-hellstrem-roslund"},
            {"golosom/book.html", "https://golosom.org/book/master-i-margarita"},
    };

    private BookPageFixtures() {
    }

    /** The page as its site's {@code fetch} returns it: a {@code .json} fixture is a JSON site's {@link JsonPage}. */
    public static Document load(String path, String baseUrl) throws IOException {
        if (path.endsWith(".json")) {
            return json(path, baseUrl);
        }
        try (InputStream in = BookPageFixtures.class.getResourceAsStream("/fixtures/" + path)) {
            if (in == null) {
                throw new IOException("missing fixture " + path);
            }
            return Jsoup.parse(in, null, baseUrl);
        }
    }

    /** An empty page of the kind {@code path} is: an empty HTML page, or an empty JSON object for a JSON site. */
    public static Document empty(String path, String baseUrl) {
        return path.endsWith(".json") ? JsonPage.parse(baseUrl, "{}") : Jsoup.parse("", baseUrl);
    }

    /** A JSON fixture as the page {@code SiteHttp.getJson(location)} returns, for a JSON site's parse methods. */
    public static JsonPage json(String path, String location) throws IOException {
        try (InputStream in = BookPageFixtures.class.getResourceAsStream("/fixtures/" + path)) {
            if (in == null) {
                throw new IOException("missing fixture " + path);
            }
            return JsonPage.parse(location, new Scanner(in, "UTF-8").useDelimiter("\\A").next());
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
