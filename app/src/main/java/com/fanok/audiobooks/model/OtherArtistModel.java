package com.fanok.audiobooks.model;


import static com.fanok.audiobooks.model.OtherSourceModel.each;
import static com.fanok.audiobooks.model.OtherSourceModel.isBlank;
import static com.fanok.audiobooks.model.OtherSourceModel.query;
import static com.fanok.audiobooks.model.OtherSourceModel.sameBook;
import static com.fanok.audiobooks.model.OtherSourceModel.samePerson;
import static com.fanok.audiobooks.model.OtherSourceModel.text;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.fanok.audiobooks.Url;
import com.fanok.audiobooks.model.OtherSourceModel.Hit;
import com.fanok.audiobooks.model.OtherSourceModel.Site;
import com.fanok.audiobooks.net.SiteHttp;
import com.fanok.audiobooks.pojo.BookPOJO;
import com.fanok.audiobooks.pojo.OtherArtistPOJO;
import io.reactivex.Observable;
import okhttp3.HttpUrl;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

/** "Other readers" of a book: other pages of the same title and author with another reader. */
public class OtherArtistModel implements
        com.fanok.audiobooks.interface_pacatge.book_content.OtherArtistModel {

    /** Whether a provider can list other readings; {@code UNSUPPORTED} always yields an empty list, not an error. */
    public enum Support {
        SUPPORTED,
        UNSUPPORTED
    }

    /** otrub and poleknig have no way to find other readings; an unknown host throws IllegalArgumentException. */
    public static Support support(@Nullable String bookUrl) {
        switch (Site.of(Url.canonical(bookUrl))) {
            case OTRUB:
            case POLEKNIG:
                return Support.UNSUPPORTED;
            default:
                return Support.SUPPORTED;
        }
    }

    public static ArrayList<OtherArtistPOJO> loadOtherArtistABMP3(String bookName, String bookAuthor, String bookUrl,
            String bookReader) throws IOException {
        return otherReaders(Site.ABMP3, bookName, bookAuthor, bookUrl, bookReader);
    }

    public static ArrayList<OtherArtistPOJO> loadOtherArtistAbook(String bookName, String bookAuthor, String bookUrl,
            String bookReader) throws IOException {
        return otherReaders(Site.AKNIGA, bookName, bookAuthor, bookUrl, bookReader);
    }

    public static ArrayList<OtherArtistPOJO> loadOtherArtistBazaKnig(final String baseName, final String baseAutor,
            final String baseUrl, final String baseArtist) throws IOException {
        return otherReaders(Site.BAZA_KNIG, baseName, baseAutor, baseUrl, baseArtist);
    }

    public static ArrayList<OtherArtistPOJO> loadOtherArtistKnigoblud(String bookName, String bookAuthor,
            String bookUrl, String bookReader) throws IOException {
        return otherReaders(Site.KNIGOBLUD, bookName, bookAuthor, bookUrl, bookReader);
    }

    public static ArrayList<OtherArtistPOJO> loadOtherArtistAudioboo(String bookName, String bookAuthor,
            String bookUrl, String bookReader) throws IOException {
        return otherReaders(Site.AUDIOBOO, bookName, bookAuthor, bookUrl, bookReader);
    }

    @Override
    public Observable<ArrayList<OtherArtistPOJO>> getOtherArtist(@NonNull BookPOJO bookPOJO) {
        return Observable.create(emitter -> {
            try {
                ArrayList<OtherArtistPOJO> readers = load(bookPOJO);
                if (!emitter.isDisposed()) {
                    emitter.onNext(readers);
                    emitter.onComplete();
                }
            } catch (Exception e) {
                emitter.tryOnError(e); // disposed meanwhile: dropped, not an undeliverable crash
            }
        });
    }

    static ArrayList<OtherArtistPOJO> load(BookPOJO book) throws IOException {
        String url = Url.canonical(book.getUrl());
        Site site = Site.of(url);
        if (support(url) == Support.UNSUPPORTED) {
            return new ArrayList<>();
        }
        if (site == Site.KNIGAVUHE) {
            return parseKnigavuheOtherReaders(SiteHttp.get(url));
        }
        return otherReaders(site, book.getName(), book.getAutor(), url, book.getArtist());
    }

    /** Searches {@code site} by title (and author where the site's search copes with it) and keeps other readings. */
    private static ArrayList<OtherArtistPOJO> otherReaders(Site site, String name, String author, String url,
            String reader) throws IOException {
        if (isBlank(name)) {
            return new ArrayList<>();
        }
        String terms = site == Site.ABMP3 || site == Site.AUDIOBOO ? query(name) : query(name, author);
        List<Hit> hits = OtherSourceModel.parseSearch(site, SiteHttp.get(OtherSourceModel.searchUrl(site, terms, 1)));
        ArrayList<OtherArtistPOJO> result = otherReaders(hits, name, author, url, reader);
        if (site == Site.BAZA_KNIG) {
            for (Hit hit : hits) {
                if (hit.altVoices && sameBook(hit, name, author)) {
                    result.addAll(parseBazaKnigAltReaders(SiteHttp.get(hit.url), hit.url, reader));
                }
            }
        }
        return result;
    }

    /** Hits with our title and author on another page with another reader; one entry per page. */
    static ArrayList<OtherArtistPOJO> otherReaders(List<Hit> hits, String name, @Nullable String author,
            @Nullable String url, @Nullable String reader) {
        ArrayList<OtherArtistPOJO> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        String self = url == null ? "" : Url.canonical(url);
        for (Hit hit : hits) {
            if (hit.url.isEmpty() || hit.reader.isEmpty() || hit.url.equals(self) || !sameBook(hit, name, author)
                    || (!isBlank(reader) && samePerson(hit.reader, reader)) || !seen.add(hit.url)) {
                continue;
            }
            OtherArtistPOJO pojo = new OtherArtistPOJO();
            pojo.setName("Исполнитель " + hit.reader);
            pojo.setUrl(hit.url);
            result.add(pojo);
        }
        return result;
    }

    /** knigavuhe lists other readings on the book page itself ("Другие озвучки"). */
    static ArrayList<OtherArtistPOJO> parseKnigavuheOtherReaders(Document doc) {
        ArrayList<OtherArtistPOJO> result = new ArrayList<>();
        for (Element block : doc.getElementsByClass("book_serie_block")) {
            String title = text(block, ".book_serie_block_title");
            if (!title.contains("Другие варианты озвучки") && !title.contains("Другие озвучки")) {
                continue;
            }
            result.addAll(each(block.getElementsByClass("book_serie_block_item"), item -> {
                Element a = item.selectFirst("a");
                String link = a == null ? "" : a.absUrl("href");
                if (link.isEmpty()) {
                    return null;
                }
                OtherArtistPOJO pojo = new OtherArtistPOJO();
                pojo.setUrl(link);
                pojo.setName(item.text().replace(a.text() + " ", "").trim());
                return pojo;
            }));
        }
        return result;
    }

    /** baza-knig keeps alternative voice-overs in tabs 2, 3, … of one page; each opens as {@code ?sorce=N}. */
    static ArrayList<OtherArtistPOJO> parseBazaKnigAltReaders(Document doc, String bookUrl, @Nullable String reader) {
        return each(doc.select("section[id^=content-tab]"), tab -> {
            String number = tab.id().substring("content-tab".length());
            if (!number.matches("\\d+") || Integer.parseInt(number) < 2) {
                return null;
            }
            String name = text(tab, "a[href*=/ispolnitel/]");
            if (name.isEmpty()) {
                String own = tab.ownText();
                int at = own.indexOf("Озвучивает:");
                name = at < 0 ? "" : own.substring(at + "Озвучивает:".length()).trim();
            }
            if (name.isEmpty() || (!isBlank(reader) && samePerson(name, reader))) {
                return null;
            }
            OtherArtistPOJO pojo = new OtherArtistPOJO();
            pojo.setName(name);
            pojo.setUrl(HttpUrl.get(bookUrl).newBuilder().setQueryParameter("sorce", number).build().toString());
            return pojo;
        });
    }
}
