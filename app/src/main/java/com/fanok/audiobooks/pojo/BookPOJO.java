package com.fanok.audiobooks.pojo;

import androidx.annotation.NonNull;

import com.fanok.audiobooks.Consts;
import com.fanok.audiobooks.Url;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.select.Elements;

import java.io.IOException;

import io.reactivex.Observable;

public class BookPOJO {
    private static final String TAG = "BookPOJO";

    private String photo;
    private String autor;
    private String artist;
    private String urlArtist;
    private String urlAutor = "";
    private String time = "";
    private String series = "";
    private String urlSeries;
    private String genre;
    private String urlGenre;
    private String reting = "0";
    private int coments = 0;
    private String name;
    private String url;

    public String getName() {
        return name;
    }

    public static Observable<BookPOJO> getDescription(String url) {
        return Observable.create(observableEmitter -> {
            if (url != null) {
                BookPOJO articlesModels;
                try {
                    articlesModels = getBookByUrl(url);
                    observableEmitter.onNext(articlesModels);
                } catch (Exception e) {
                    observableEmitter.onError(e);
                } finally {
                    observableEmitter.onComplete();
                }
            }
        });
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

    public static BookPOJO getBookByUrl(String url) throws IOException {
        BookPOJO bookPOJO = new BookPOJO();

        Document document = Jsoup.connect(url)
                .userAgent(
                        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_12_6) AppleWebKit/537.36 "
                                + "(KHTML, like Gecko) Chrome/61.0.3163.100 Safari/537.36")
                .referrer("https://audioknigi.club/")
                .get();
        Elements titleElement = document.getElementsByClass("book_title_elem book_title_name");
        if (titleElement.size() != 0) {
            bookPOJO.setName(titleElement.first().text().trim());
        }
        Elements retingElements = document.getElementsByClass("book_views_icon");
        if (retingElements.size() != 0) {
            Node retingNode = retingElements.first().nextSibling();
            if (retingNode != null) {
                bookPOJO.setReting(retingNode.toString());
            }
        }
        Elements posterElements = document.getElementsByClass("book_cover");
        if (posterElements.size() != 0) {
            Elements img = posterElements.first().getElementsByTag("img");
            if (img.size() != 0) {
                String imgUrl = img.first().attr("src");
                if (imgUrl != null) {
                    int lastPos = imgUrl.indexOf("?");
                    if (lastPos != -1) {
                        imgUrl = imgUrl.substring(0, lastPos);
                    }
                    bookPOJO.setPhoto(imgUrl);
                }
            }
        }


        Elements timeElements = document.getElementsByClass("book_info_label");

        if (timeElements.size() != 0 && timeElements.first().text().contains("Время звучания:")) {
            Node timeNode = timeElements.first().nextSibling();
            if (timeNode != null) {
                bookPOJO.setTime(timeNode.toString());
            }
        }


        Elements autorElements = document.getElementsByAttributeValue("itemprop", "author");
        if (autorElements.size() != 0) {
            Elements aElements = autorElements.first().getElementsByTag("a");
            if (aElements.size() != 0) {
                bookPOJO.setAutor(aElements.first().text());
                bookPOJO.setUrlAutor(Url.SERVER + aElements.first().attr("href"));
            }
        }

        Elements artistElements = document.getElementsByClass("book_title_elem");

        for (int i = 0; i < artistElements.size(); i++) {
            Element element = artistElements.get(i);
            if (element.text().contains("читает")) {
                Elements aTag = element.getElementsByTag("a");
                if (aTag.size() != 0) {
                    bookPOJO.setArtist(aTag.first().text());
                    bookPOJO.setUrlArtist(Url.SERVER + aTag.first().attr("href"));
                }
            }
        }

        Elements seriesSisterElements = document.getElementsByClass("book_serie_block_title");
        for (int i = 0; i < seriesSisterElements.size(); i++) {
            Element element = seriesSisterElements.get(i);
            if (element.text().contains("Цикл")) {
                Elements seriesElement = element.getElementsByTag("a");
                if (seriesElement.size() != 0) {
                    bookPOJO.setSeries(seriesElement.first().text());
                    bookPOJO.setUrlSeries(Url.SERVER + seriesElement.first().attr("href"));
                }
            }

        }


        Elements genreConteiner = document.getElementsByClass("book_genre_pretitle");
        if (genreConteiner.size() != 0) {
            Elements aTag = genreConteiner.first().getElementsByTag("a");
            if (aTag.size() != 0) {
                Element a = aTag.first();
                bookPOJO.setGenre(a.text());
                bookPOJO.setUrlGenre(Url.SERVER + a.attr("href"));
            }
        }

        bookPOJO.setUrl(url);

        Element comentsTitle = document.getElementById("comments_count");
        if (comentsTitle != null) {
            bookPOJO.setComents(comentsTitle.text());
        }

        return bookPOJO;
    }

    public static BookPOJO parceJsonToBookPojo(String json) {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        return gson.fromJson(json, BookPOJO.class);
    }

    public int getComents() {
        return coments;
    }

    public void setComents(@NonNull String coments) {
        coments = coments.replaceAll("[^0-9]", "");
        if (!coments.matches("^\\d+$")) {
            this.coments = 0;
        } else {
            int count = Integer.parseInt(coments);
            if (count < 0) throw new IllegalArgumentException("Value must be more 0");
            this.coments = count;
        }
    }

    public void setReting(@NonNull String reting) {
        if (reting.isEmpty()) {
            throw new IllegalArgumentException(
                    "Incorect value");
        }
        this.reting = reting;
    }

    public boolean isNull() {
        return photo == null || autor == null || artist == null || urlArtist == null
                || genre == null || urlGenre == null
                || name == null || url == null;
    }

    public void setName(@NonNull String name) {
        if (name.isEmpty()) throw new IllegalArgumentException("Value must be not empty");
        this.name = name;
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
}
