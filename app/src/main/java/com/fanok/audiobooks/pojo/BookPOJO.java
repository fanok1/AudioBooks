package com.fanok.audiobooks.pojo;

import android.support.annotation.NonNull;

import com.fanok.audiobooks.Consts;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;

import io.reactivex.Observable;

public class BookPOJO {
    private static final String TAG = "BookPOJO";

    private String photo;
    private String autor;
    private String artist;
    private String urlArtist;
    private String urlAutor;
    private String time = "";
    private String series = "";
    private String urlSeries;
    private String genre;
    private String urlGenre;
    private String reting = "0";
    private int favorite = 0;
    private int coments = 0;
    private String name;
    private String url;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        if (name.isEmpty()) throw new IllegalArgumentException("Value must be not empty");
        this.name = name;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        if (!Consts.REGEXP_URL.matcher(url).matches()) {
            throw new IllegalArgumentException(
                    "Value must be url");
        }
        this.url = url;
    }

    public String getPhoto() {
        return photo;
    }

    public void setPhoto(@NonNull String photo) {
        if (!Consts.REGEXP_URL_PHOTO.matcher(photo).matches()) {
            throw new IllegalArgumentException(
                    "Value must be url");
        }
        this.photo = photo;
    }

    public String getAutor() {
        return autor;
    }

    public void setAutor(@NonNull String autor) {
        if (autor.isEmpty()) throw new IllegalArgumentException("Value must be not empty");
        this.autor = autor;
    }

    public String getArtist() {
        return artist;
    }

    public void setArtist(@NonNull String artist) {
        if (artist.isEmpty()) throw new IllegalArgumentException("Value must be not empty");
        this.artist = artist;
    }

    public String getUrlArtist() {
        return urlArtist;
    }

    public void setUrlArtist(@NonNull String urlArtist) {
        if (!Consts.REGEXP_URL.matcher(urlArtist).matches()) {
            throw new IllegalArgumentException(
                    "Value must be url");
        }
        this.urlArtist = urlArtist;
    }

    public String getUrlAutor() {
        return urlAutor;
    }

    public void setUrlAutor(@NonNull String urlAutor) {
        if (!Consts.REGEXP_URL.matcher(urlAutor).matches()) {
            throw new IllegalArgumentException(
                    "Value must be url");
        }
        this.urlAutor = urlAutor;
    }

    public String getTime() {
        return time;
    }

    public void setTime(@NonNull String time) {
        if (time.isEmpty()) throw new IllegalArgumentException("Value must be not empty");
        this.time = time;
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
        if (!Consts.REGEXP_URL.matcher(urlSeries).matches()) {
            throw new IllegalArgumentException(
                    "Value must be url");
        }
        this.urlSeries = urlSeries;
    }

    public String getGenre() {
        return genre;
    }

    public void setGenre(@NonNull String genre) {
        if (genre.isEmpty()) throw new IllegalArgumentException("Value must be not empty");
        this.genre = genre;
    }

    public String getUrlGenre() {
        return urlGenre;
    }

    public void setUrlGenre(@NonNull String urlGenre) {
        if (!Consts.REGEXP_URL.matcher(urlGenre).matches()) {
            throw new IllegalArgumentException(
                    "Value must be url");
        }
        this.urlGenre = urlGenre;
    }

    public String getReting() {
        return reting;
    }

    public void setReting(@NonNull String reting) {
        if (!Consts.REGEXP_RETING.matcher(reting).matches()) {
            throw new IllegalArgumentException(
                    "Incorect value");
        }
        this.reting = reting;
    }

    public int getFavorite() {
        return favorite;
    }

    public void setFavorite(int favorite) {
        if (favorite < 0) throw new IllegalArgumentException("Value must be more 0");
        this.favorite = favorite;
    }

    public int getComents() {
        return coments;
    }

    public void setComents(@NonNull String coments) {
        coments = coments.replaceAll("[^0-9]", "");
        if (!coments.matches("^\\d+$")) {
            throw new IllegalArgumentException(
                    "Value not conteins integr");
        }
        int count = Integer.parseInt(coments);
        if (count < 0) throw new IllegalArgumentException("Value must be more 0");
        this.coments = count;
    }

    public boolean isNull() {
        return photo == null || autor == null || artist == null || urlArtist == null
                || urlAutor == null || genre == null || urlGenre == null
                || name == null || url == null;
    }


    public static BookPOJO parceJsonToBookPojo(String json) {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        return gson.fromJson(json, BookPOJO.class);
    }

    private static BookPOJO getBookByUrl(String url) throws IOException {
        BookPOJO bookPOJO = new BookPOJO();

        Document document = Jsoup.connect(url)
                .userAgent(
                        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_12_6) AppleWebKit/537.36 "
                                + "(KHTML, like Gecko) Chrome/61.0.3163.100 Safari/537.36")
                .referrer("https://audioknigi.club/")
                .get();
        Elements titleElement = document.getElementsByClass("ls-topic-title");
        if (titleElement.size() != 0) {
            bookPOJO.setName(titleElement.first().text().trim());
        }
        Elements retingElements = document.getElementsByClass("ls-vote-big-rating");
        if (retingElements.size() != 0) {
            bookPOJO.setReting(retingElements.first().text());
        }
        Elements posterElements = document.getElementsByClass("picture-side ");
        if (posterElements.size() != 0) {
            Elements img = posterElements.first().getElementsByTag("img");
            if (img.size() != 0) {
                bookPOJO.setPhoto(img.first().attr("src"));
            }
        }


        Elements hoursElements = document.getElementsByClass("hours");
        Elements minutesElements = document.getElementsByClass("minutes");

        String hours = "";
        String minutes = "";

        if (hoursElements.size() != 0) {
            hours = hoursElements.first().text();
        }

        if (minutesElements.size() != 0) {
            minutes = minutesElements.first().text();
        }
        bookPOJO.setTime((hours + " " + minutes).trim());

        Elements autorElements = document.getElementsByAttributeValue("itemprop", "author");
        if (autorElements.size() != 0) {
            bookPOJO.setAutor(autorElements.first().text());
        }

        Elements artistElements = document.getElementsByAttributeValue("rel", "performer");
        if (artistElements.size() != 0) {
            bookPOJO.setArtist(artistElements.first().text());
            bookPOJO.setUrlArtist(artistElements.first().attr("href"));
        }

        Elements seriesSisterElements = document.getElementsByClass("fa fa-book");
        if (seriesSisterElements.size() != 0) {
            Element parent = seriesSisterElements.first().parent();
            Elements seriesElement = parent.getElementsByTag("a");
            if (seriesElement.size() != 0) {
                bookPOJO.setSeries(seriesElement.first().text());
                bookPOJO.setUrlSeries(seriesElement.first().attr("href"));
            }
        }


        Elements genreConteiner = document.getElementsByClass("ls-topic-info-item");
        if (genreConteiner.size() != 0) {
            Elements aTag = genreConteiner.first().getElementsByTag("a");
            if (aTag.size() != 0) {
                Element a = aTag.first();
                bookPOJO.setGenre(a.text());
                bookPOJO.setUrlGenre(a.attr("href"));
            }
        }

        Elements autorUrlConteiner = document.getElementsByAttributeValue("rel", "author");
        if (autorUrlConteiner.size() != 0) {
            bookPOJO.setUrlAutor(autorUrlConteiner.first().attr("href"));
        }

        bookPOJO.setUrl(url);

        Elements comentsTitle = document.getElementsByClass("comments-title");
        if (comentsTitle.size() != 0) {
            bookPOJO.setComents(comentsTitle.first().text());
        }

        Elements favoriteConteiner = document.getElementsByClass("ls-favourite-count");
        if (favoriteConteiner.size() != 0) {
            bookPOJO.setFavorite(Integer.valueOf(favoriteConteiner.first().text()));
        }

        return bookPOJO;
    }

    public static Observable<BookPOJO> getDescription(String url) {
        return Observable.create(observableEmitter -> {
            BookPOJO articlesModels;
            try {
                articlesModels = getBookByUrl(url);
                observableEmitter.onNext(articlesModels);
            } catch (Exception e) {
                observableEmitter.onError(e);
            } finally {
                observableEmitter.onComplete();
            }
        });
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
                "\"favorite\":\"" + favorite + "\"" +
                "\"coments\":\"" + coments + "\"" +
                "\"name\":\"" + name + "\"" +
                "\"url\":\"" + url + "\"" +
                "}";
    }
}
