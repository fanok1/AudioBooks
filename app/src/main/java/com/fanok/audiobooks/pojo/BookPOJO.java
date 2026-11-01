package com.fanok.audiobooks.pojo;

import androidx.annotation.NonNull;

import com.fanok.audiobooks.Consts;
import com.fanok.audiobooks.Url;
import com.fanok.audiobooks.net.SiteHttp;
import com.fanok.audiobooks.search.Provider;
import com.fanok.audiobooks.source.Akniga;
import com.fanok.audiobooks.source.Knigavuhe;
import com.fanok.audiobooks.source.SourceParser;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.reactivex.Observable;
import java.io.IOException;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

public class BookPOJO {

    private String photo;
    private String autor;
    private String artist;
    private String urlArtist;
    private String urlAutor;
    private String time;
    private String series;
    private String urlSeries;
    private String genre;
    private String urlGenre;

    private String reting = "0";

    private int coments = 0;

    private String name;

    private String url;

    private String desc;

    public String getName() {
        return name;
    }

    /** {@link Url#canonical} of {@code url}; throws {@link IllegalArgumentException} for a host no parser handles. */
    public static String requireSupported(String url) {
        String canonical = Url.canonical(url);
        for (Provider source : Provider.values()) {
            if (Url.isHost(canonical, source.server)) {
                return canonical;
            }
        }
        throw new IllegalArgumentException("unsupported host: " + url);
    }

    /**
     * The card of a book opened by its link, made of its page's description: name, cover, people, genre, series, time
     * and annotation (not the rating, whose scale differs between the card and the page on some sites).
     */
    public static BookPOJO of(DescriptionPOJO d, String url) {
        BookPOJO book = new BookPOJO();
        book.setUrl(url);
        book.setName(d.getTitle());
        book.setPhoto(d.getPoster());
        book.setAutor(d.getAutor());
        book.setUrlAutor(d.getAutorUrl());
        book.setArtist(d.getArtist());
        book.setUrlArtist(d.getArtistUrl());
        book.setGenre(d.getGenre());
        book.setUrlGenre(d.getGenreUrl());
        book.setSeries(d.getSeries());
        book.setUrlSeries(d.getSeriesUrl());
        book.setTime(d.getTime());
        book.setDesc(d.getDescription());
        return book;
    }

    /** Absolute value of {@code attr} (resolved against the page URL) plus {@code suffix}; "" when absent. */
    public static String absUrl(Element element, String attr, String suffix) {
        String url = element.absUrl(attr);
        return url.isEmpty() ? "" : url + suffix;
    }

    /** A knigavuhe book by its link (the favourites import). */
    public static BookPOJO getBookByUrl(String url) throws IOException {
        return Knigavuhe.parseBook(SiteHttp.get(url), url);
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        if (url == null || url.trim().isEmpty()) {
            return;
        }
        this.url = url;
    }

    public String getPhoto() {
        return photo;
    }

    /** An akniga book by its link (the favourites import). */
    public static BookPOJO getBookByUrlAbook(String url) throws IOException {
        return Akniga.parseBook(SiteHttp.get(url), url);
    }

    /** Loads the book behind {@code url}: unsupported host → {@link IllegalArgumentException}; fetch and page errors propagate. */
    public static Observable<BookPOJO> getDescription(String url) {
        return Observable.fromCallable(() -> {
            String pageUrl = requireSupported(url);
            SourceParser parser = Provider.parserOf(pageUrl);
            return parseBook(parser != null ? parser.fetch(pageUrl) : SiteHttp.get(pageUrl), pageUrl);
        });
    }

    /** Pure: picks the parser by host; a page without a book title is an {@link IOException}, never an empty book. */
    static BookPOJO parseBook(Document document, String bookUrl) throws IOException {
        String url = requireSupported(bookUrl);
        BookPOJO book;
        SourceParser parser = Provider.parserOf(url);
        if (parser != null) {
            book = parser.book(document, url);
        } else {
            throw new IllegalArgumentException("unsupported host: " + url);
        }
        if (book.isNull()) {
            throw new IOException("no book on page: " + url);
        }
        return book;
    }

    public String getAutor() {
        return autor;
    }

    public void setAutor(String autor) {
        if (autor == null || autor.isEmpty()) {
            return;
        }
        this.autor = autor.replaceAll("\\s*\\(https?://[^)]+\\)", "");
    }

    public void setPhoto(String photo) {
        if (photo == null || photo.isEmpty()) {
            return;
        }
        this.photo = photo;
    }

    public String getArtist() {
        return artist;
    }

    public void setArtist(String artist) {
        if (artist == null || artist.isEmpty()) return;
        this.artist = artist;
    }

    public String getUrlArtist() {
        return urlArtist;
    }

    public void setUrlArtist(String urlArtist) {
        if (urlArtist == null || urlArtist.isEmpty()) {
            return;
        }
        this.urlArtist = urlArtist;
    }

    public String getUrlAutor() {
        return urlAutor;
    }

    public void setUrlAutor(String urlAutor) {
        if (urlAutor == null || urlAutor.isEmpty()) {
            return;
        }
        this.urlAutor = urlAutor;
    }

    public String getTime() {
        return time;
    }

    public void setTime(String time) {
        if (time == null || time.isEmpty()) {
            this.time = "";
        } else {
            this.time = Consts.formatTimeDisplay(time);
        }
    }

    public String getSeries() {
        return series;
    }

    public void setSeries(String series) {
        if (series == null || series.isEmpty()) return;
        this.series = series;
    }

    public String getUrlSeries() {
        return urlSeries;
    }

    public void setUrlSeries(String urlSeries) {
        if (urlSeries == null || urlSeries.isEmpty()) return;
        this.urlSeries = urlSeries;
    }

    public String getGenre() {
        return genre;
    }

    public void setGenre(String genre) {
        if (genre == null || genre.isEmpty()) {
            return;
        }
        this.genre = genre;
    }

    public String getUrlGenre() {
        return urlGenre;
    }

    public void setUrlGenre(String urlGenre) {
        if (urlGenre == null || urlGenre.isEmpty()) {
            return;
        }
        this.urlGenre = urlGenre;
    }

    public String getReting() {
        return reting;
    }

    public static BookPOJO parceJsonToBookPojo(String json) {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        return gson.fromJson(json, BookPOJO.class);
    }

    public int getComents() {
        return coments;
    }

    public void setComents(String coments) {
        String digits = coments == null ? "" : coments.replaceAll("[^0-9]", "");
        try {
            this.coments = digits.isEmpty() ? 0 : Integer.parseInt(digits);
        } catch (NumberFormatException e) {
            this.coments = 0;
        }
    }

    public void setReting(String reting) {
        this.reting = reting == null ? "" : reting;
    }

    /** True when the item cannot be shown: no URL or no (non-empty) name. */
    public boolean isNull() {
        return name == null || name.isEmpty() || url == null;
    }

    public void setName(String name) {
        this.name = name == null ? "" : name;
    }

    public String getDesc() {
        return desc;
    }

    public void setDesc(String desc) {
        if (desc == null || desc.isEmpty()) {
            this.desc = "";
        } else {
            this.desc = desc;
        }
    }

    public DescriptionPOJO getDescriptionPOJO() {
        DescriptionPOJO descriptionPOJO = new DescriptionPOJO();
        descriptionPOJO.setTitle(this.name != null ? this.name : "");
        descriptionPOJO.setPoster(this.photo != null ? this.photo : "");
        descriptionPOJO.setReiting(this.reting != null ? this.reting.replaceAll(" ", "") : "0");
        descriptionPOJO.setTime(this.time != null ? this.time : "");
        descriptionPOJO.setAutor(this.autor != null ? this.autor : "");
        descriptionPOJO.setArtist(this.artist != null ? this.artist : "");
        descriptionPOJO.setArtistUrl(this.urlArtist != null ? this.urlArtist : "");
        descriptionPOJO.setAutorUrl(this.urlAutor != null ? this.urlAutor : "");
        descriptionPOJO.setGenre(this.genre != null ? this.genre : "");
        descriptionPOJO.setGenreUrl(this.urlGenre != null ? this.urlGenre : "");
        descriptionPOJO.setSeries(this.series);
        descriptionPOJO.setSeriesUrl(this.urlSeries);
        descriptionPOJO.setDescription(this.desc != null ? this.desc : "");

        return descriptionPOJO;
    }

    @NonNull
    @Override
    public String toString() {
        return "{" +
                "\"photo\":\"" + photo + "\"" +
                "\"autor\":\"" + autor + "\"" +
                "\"artist\":\"" + artist + "\"" +
                "\"urlArtist\":\"" + urlArtist + "\"" +
                "\"urlAutor\":\"" + urlAutor + "\"" +
                "\"time\":\"" + time + "\"" +
                "\"series\":\"" + series + "\"" +
                "\"urlSeries\":\"" + urlSeries + "\"" +
                "\"genre\":\"" + genre + "\"" +
                "\"urlGenre\":\"" + urlGenre + "\"" +
                "\"reting\":\"" + reting + "\"" +
                "\"coments\":\"" + coments + "\"" +
                "\"name\":\"" + name + "\"" +
                "\"url\":\"" + url + "\"" +
                "}";
    }

    public static String formatRating(float stars, String votes) {
        String starChar = stars >= 4.99f ? "\u2605" : "\u2606";
        return String.format("%s %.1f (%s)", starChar, stars, votes);
    }
    }
