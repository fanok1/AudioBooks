package com.fanok.audiobooks.model;


import static com.fanok.audiobooks.model.OtherSourceModel.each;
import static com.fanok.audiobooks.model.OtherSourceModel.href;
import static com.fanok.audiobooks.model.OtherSourceModel.text;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.fanok.audiobooks.Url;
import com.fanok.audiobooks.model.OtherSourceModel.Site;
import com.fanok.audiobooks.net.SiteHttp;
import com.fanok.audiobooks.pojo.SeriesPOJO;
import io.reactivex.Observable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import okhttp3.HttpUrl;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

/** The series ("цикл") tab: the other books of the book's series, as each provider lists them. */
public class SeriesModel implements
        com.fanok.audiobooks.interface_pacatge.book_content.SeriesModel {

    /** baza-knig series listing pages read at most. */
    static final int MAX_SERIES_PAGES = 10;

    @Override
    public Observable<ArrayList<SeriesPOJO>> getSeries(@NonNull String url) {
        return Observable.create(emitter -> {
            try {
                ArrayList<SeriesPOJO> series = loadSeries(url);
                if (!emitter.isDisposed()) {
                    emitter.onNext(series);
                    emitter.onComplete();
                }
            } catch (Exception e) {
                emitter.tryOnError(e); // disposed meanwhile: dropped, not an undeliverable crash
            }
        });
    }

    /** Fetches and parses the series of the book at {@code bookUrl}; an unknown host throws IllegalArgumentException. */
    static ArrayList<SeriesPOJO> loadSeries(String bookUrl) throws IOException {
        String url = Url.canonical(bookUrl);
        switch (Site.of(url)) {
            case KNIGAVUHE:
                return parseKnigavuheSeries(SiteHttp.get(url));
            case IZIBUK:
                return parseIzibukSeries(SiteHttp.get(url));
            case AKNIGA:
                return loadAknigaSeries(url);
            case BAZA_KNIG:
                return loadBazaKnigSeries(url);
            case KNIGOBLUD:
                return parseKnigobludSeries(SiteHttp.get(url));
            case AUDIOBOO: {
                String series = parseAudiobooSeriesUrl(SiteHttp.get(url));
                return series.isEmpty() ? new ArrayList<>() : parseAudiobooSeriesPage(SiteHttp.get(series));
            }
            case OTRUB: {
                String series = parseSeriesLink(SiteHttp.get(url));
                return series.isEmpty() ? new ArrayList<>() : parseOtrubSeriesPage(SiteHttp.get(series));
            }
            case POLEKNIG: {
                String series = parseSeriesLink(SiteHttp.get(url));
                return series.isEmpty() ? new ArrayList<>() : parsePoleknigSeriesPage(SiteHttp.get(series));
            }
            default:
                return new ArrayList<>(); // audiobook-mp3 has no series
        }
    }

    static ArrayList<SeriesPOJO> parseKnigavuheSeries(Document doc) {
        ArrayList<SeriesPOJO> result = new ArrayList<>();
        for (Element block : doc.getElementsByClass("book_serie_block")) {
            if (!text(block, ".book_serie_block_title").contains("Цикл")) {
                continue;
            }
            result.addAll(each(block.getElementsByClass("book_serie_block_item"), item -> {
                SeriesPOJO seriesPOJO = new SeriesPOJO();
                seriesPOJO.setNumber(text(item, ".book_serie_block_item_index"));
                Element a = item.selectFirst("a");
                if (a != null) {
                    seriesPOJO.setName(a.text().trim());
                    setUrl(seriesPOJO, a.absUrl("href"));
                } else {
                    seriesPOJO.setName(text(item, "strong")); // the current book: no link
                }
                return seriesPOJO;
            }));
        }
        return result;
    }

    static ArrayList<SeriesPOJO> parseIzibukSeries(Document doc) {
        Element block = doc.selectFirst("._b264b2._49d1b4");
        if (block == null) {
            return new ArrayList<>();
        }
        return each(block.getElementsByClass("_f61db9"), item -> {
            SeriesPOJO seriesPOJO = new SeriesPOJO();
            String number = text(item, "._bb8bca");
            seriesPOJO.setNumber(number);
            Element a = item.selectFirst("a");
            if (a != null) {
                setUrl(seriesPOJO, a.absUrl("href"));
                seriesPOJO.setName(item.text().replace(number, "").trim());
            } else {
                seriesPOJO.setName(text(item, "strong"));
            }
            return seriesPOJO;
        });
    }

    private static ArrayList<SeriesPOJO> loadAknigaSeries(String url) throws IOException {
        Document doc = SiteHttp.get(url);
        ArrayList<SeriesPOJO> result = parseAknigaSeries(doc);
        if (!result.isEmpty()) {
            return result;
        }
        if (url.contains("/series/")) {
            return parseAknigaSeriesPage(doc);
        }
        String series = href(doc, "a[href*=/series/]");
        return series.isEmpty() ? result : parseAknigaSeriesPage(SiteHttp.get(series));
    }

    static ArrayList<SeriesPOJO> parseAknigaSeries(Document doc) {
        Element list = doc.selectFirst(".content__main__book--item--series-list");
        if (list == null) {
            return new ArrayList<>();
        }
        return each(list.children(), item -> {
            String link = item.absUrl("href");
            if (link.isEmpty()) {
                return null; // "Показать весь список" toggle
            }
            SeriesPOJO seriesPOJO = new SeriesPOJO();
            seriesPOJO.setUrl(link);
            seriesPOJO.setNumber(text(item, "b"));
            String caption = text(item, ".caption");
            seriesPOJO.setName(caption.isEmpty() ? item.text().trim() : caption);
            seriesPOJO.setPhoto(imgSrc(item.selectFirst("img")));
            return seriesPOJO;
        });
    }

    /** A series listing page (cards, like search results). */
    static ArrayList<SeriesPOJO> parseAknigaSeriesPage(Document doc) {
        return each(doc.select(".content__main__articles--item"), card -> {
            Element a = card.selectFirst(".caption__article-main a, h2 a, h3 a");
            if (a == null) {
                a = card.selectFirst("a[href]");
            }
            String link = a == null ? "" : a.absUrl("href");
            if (link.isEmpty() || link.contains("/author/") || link.contains("/performer/")
                    || link.contains("/section/")) {
                return null;
            }
            SeriesPOJO seriesPOJO = new SeriesPOJO();
            seriesPOJO.setUrl(link);
            String caption = text(card, ".caption__article-main");
            seriesPOJO.setName(caption.isEmpty() ? a.text().trim() : caption);
            seriesPOJO.setPhoto(imgSrc(card.selectFirst("img")));
            return seriesPOJO;
        });
    }

    /** Book page → cycle link → listing pages; every request goes through SiteHttp (PHPSESSID, cookie jar). */
    private static ArrayList<SeriesPOJO> loadBazaKnigSeries(String url) throws IOException {
        ArrayList<SeriesPOJO> result = new ArrayList<>();
        String series = parseBazaKnigSeriesUrl(SiteHttp.get(url));
        if (series.isEmpty()) {
            return result;
        }
        for (int page = 1; page <= MAX_SERIES_PAGES; page++) {
            Document doc = SiteHttp.get(bazaKnigPageUrl(series, page));
            result.addAll(parseBazaKnigSeriesPage(doc));
            if (page >= OtherSourceModel.lastPage(doc)) {
                break;
            }
        }
        Collections.reverse(result);
        return result;
    }

    /** The "Цикл:" link of a baza-knig book page; "" when the book is not part of a cycle. */
    static String parseBazaKnigSeriesUrl(Document doc) {
        for (Element li : doc.select(".full-items li")) {
            String link = href(li, "a");
            if (li.text().contains("Цикл") && !link.isEmpty()) {
                return link;
            }
        }
        return "";
    }

    /** Page {@code page} of a DLE listing: the listing itself for page 1, {@code …/page/N/} after it. */
    static String bazaKnigPageUrl(String seriesUrl, int page) {
        if (page <= 1) {
            return seriesUrl;
        }
        return HttpUrl.get(seriesUrl).newBuilder().addPathSegment("page").addPathSegment(String.valueOf(page))
                .addPathSegment("").build().toString();
    }

    static ArrayList<SeriesPOJO> parseBazaKnigSeriesPage(Document doc) {
        Element content = doc.getElementById("dle-content");
        if (content == null) {
            return new ArrayList<>();
        }
        return each(content.getElementsByClass("short"), book -> {
            String link = href(book, ".short-img a");
            Element title = book.selectFirst(".short-title a");
            String name = title == null ? "" : title.ownText().trim();
            if (link.isEmpty() || name.isEmpty()) {
                return null;
            }
            SeriesPOJO seriesPOJO = new SeriesPOJO();
            seriesPOJO.setUrl(link);
            seriesPOJO.setNumber(text(title, "b"));
            seriesPOJO.setName(name);
            for (Element li : book.select(".short-items li")) {
                if (li.text().contains("Читает")) {
                    seriesPOJO.setName(name + " " + li.text());
                }
            }
            seriesPOJO.setPhoto(imgSrc(book.selectFirst(".short-img img")));
            return seriesPOJO;
        });
    }

    static ArrayList<SeriesPOJO> parseKnigobludSeries(Document doc) {
        Element block = doc.selectFirst(".BookSeries");
        if (block == null) {
            return new ArrayList<>();
        }
        return each(block.getElementsByClass("BookDescriptionSeriesItem"), item -> {
            SeriesPOJO seriesPOJO = new SeriesPOJO();
            String number = text(item, ".BookDescriptionSeriesItemIndex");
            seriesPOJO.setNumber(number);
            seriesPOJO.setName(item.text().replace(number, "").trim());
            Element a = item.tagName().equals("a") ? item : item.selectFirst("a");
            if (a != null) {
                setUrl(seriesPOJO, a.absUrl("href"));
            }
            return seriesPOJO;
        });
    }

    /** The cycle link of an audioboo book page, resolved against audioboo itself; "" when there is none. */
    static String parseAudiobooSeriesUrl(Document doc) {
        Element a = doc.selectFirst("a[href*=/xfsearch/cikl/]");
        if (a == null) {
            a = doc.selectFirst(".xfvalue_series a");
        }
        return a == null ? "" : a.absUrl("href");
    }

    static ArrayList<SeriesPOJO> parseAudiobooSeriesPage(Document doc) {
        Elements cards = doc.select(".card");
        if (!cards.isEmpty()) {
            return each(cards, card -> {
                Element a = card.selectFirst(".card__title a");
                if (a == null) {
                    a = card.selectFirst("a[href*=.html]");
                }
                return bookLink(a, card.selectFirst("img"));
            });
        }
        Element list = doc.getElementById("dle-content");
        if (list == null) {
            return new ArrayList<>();
        }
        return each(list.getElementsByClass("short-item"),
                book -> bookLink(book.selectFirst("a.short-title, .short-title a"), book.selectFirst("img")));
    }

    /** The first series link of an otrub or poleknig book page; "" when there is none. */
    static String parseSeriesLink(Document doc) {
        return href(doc, "a[href*=/series/]");
    }

    static ArrayList<SeriesPOJO> parseOtrubSeriesPage(Document doc) {
        return each(doc.select("._3dc935 a"), a -> {
            Element card = a.closest("._dad4fa");
            return bookLink(a, card == null ? null : card.selectFirst("img"));
        });
    }

    static ArrayList<SeriesPOJO> parsePoleknigSeriesPage(Document doc) {
        return each(doc.select(".media-body"), body -> {
            Element card = body.parent();
            return bookLink(body.selectFirst("a[href*=/books/]"), card == null ? null : card.selectFirst("img"));
        });
    }

    /** A series entry from a book link and its cover; null when the link is missing. */
    @Nullable
    private static SeriesPOJO bookLink(@Nullable Element a, @Nullable Element img) {
        String link = a == null ? "" : a.absUrl("href");
        if (link.isEmpty()) {
            return null;
        }
        SeriesPOJO seriesPOJO = new SeriesPOJO();
        seriesPOJO.setUrl(link);
        seriesPOJO.setName(a.text().trim());
        seriesPOJO.setPhoto(imgSrc(img));
        return seriesPOJO;
    }

    /** Absolute cover of a lazy-loaded image (data-original, data-src, then src), skipping placeholders. */
    static String imgSrc(@Nullable Element img) {
        if (img == null) {
            return "";
        }
        for (String attr : new String[]{"data-original", "data-src", "src"}) {
            String src = img.absUrl(attr);
            if (!src.isEmpty() && !src.contains("placeholder")) {
                return src;
            }
        }
        return "";
    }

    private static void setUrl(SeriesPOJO seriesPOJO, String url) {
        if (!url.isEmpty()) {
            seriesPOJO.setUrl(url);
        }
    }
}
