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

    public static Observable<BookPOJO> getDescription(String url) {
        return Observable.create(observableEmitter -> {
            if (url != null) {
                BookPOJO articlesModels;
                try {
                    if (url.contains("knigavuhe.org")) {
                        articlesModels = getBookByUrl(url);
                    } else if (url.contains("izibuk.ru")) {
                        articlesModels = getBookByUrlIziBuk(url);
                    } else {
                        articlesModels = new BookPOJO();
                    }
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

    public static BookPOJO getBookByUrl(String url) throws IOException {
        BookPOJO bookPOJO = new BookPOJO();

        Document document = Jsoup.connect(url)
                .userAgent(Consts.USER_AGENT)
                .referrer("https://google.com/")
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

        Elements desc = document.getElementsByClass("book_description");
        if (desc != null && desc.size() != 0) {
            bookPOJO.setDesc(desc.first().text());
        }

        return bookPOJO;
    }

    public static BookPOJO getBookByUrlIziBuk(String url) throws IOException {
        BookPOJO bookPOJO = new BookPOJO();

        Document document = Jsoup.connect(url)
                .userAgent(Consts.USER_AGENT)
                .referrer("https://google.com/")
                .get();

        bookPOJO.setUrl(url);

        Elements elements = document.getElementsByAttributeValue("itemprop", "name");
        if (elements != null && elements.size() != 0) {
            String name = elements.first().text();
            if (name != null) {
                bookPOJO.setName(name);
            }
        }

        Elements imgParent = document.getElementsByClass("_5e0b77");
        if (imgParent != null && imgParent.size() != 0) {
            Element img = imgParent.first().child(0);
            if (img != null) {
                String src = img.attr("src");
                if (src != null) {
                    bookPOJO.setPhoto(src);
                }
            }
        }

        Elements infoParent = document.getElementsByClass("_b264b2");
        if (infoParent != null && infoParent.size() != 0) {
            Elements info = infoParent.first().children();
            if (info != null) {
                for (Element element : info) {
                    String text = element.text();
                    if (text != null) {
                        if (text.contains("Автор")) {
                            Elements aTag = element.getElementsByTag("a");
                            if (aTag != null && aTag.size() != 0) {
                                Element a = aTag.first();
                                String href = a.attr("href");
                                if (href != null) {
                                    bookPOJO.setUrlAutor(Url.SERVER_IZIBUK + href + "?p=");
                                }
                                String name = a.text();
                                if (name != null) {
                                    bookPOJO.setAutor(name);
                                }
                            }
                        } else if (text.contains("Читает")) {
                            Elements aTag = element.getElementsByTag("a");
                            if (aTag != null && aTag.size() != 0) {
                                Element a = aTag.first();
                                String href = a.attr("href");
                                if (href != null) {
                                    bookPOJO.setUrlArtist(Url.SERVER_IZIBUK + href + "?p=");
                                }
                                String name = a.text();
                                if (name != null) {
                                    bookPOJO.setArtist(name);
                                }
                            }
                        } else if (text.contains("Время")) {
                            String time = text.replace("Время:", "").trim();
                            bookPOJO.setTime(time);
                        }
                    }
                }
            }
        }

        Elements seriesParent = document.getElementsByClass("_c337c7");
        if (seriesParent != null && seriesParent.size() != 0) {
            Elements aTag = seriesParent.first().getElementsByTag("a");
            if (aTag != null && aTag.size() != 0) {
                Element a = aTag.first();
                String href = a.attr("href");
                if (href != null) {
                    bookPOJO.setUrlSeries(Url.SERVER_IZIBUK + href + "?p=");
                }
                String text = a.text();
                if (text != null) {
                    bookPOJO.setSeries(text);
                }
            }
        }

        Elements genreParent = document.getElementsByClass("_7e215f");
        if (genreParent != null && genreParent.size() != 0) {
            Element genreElement = genreParent.first().child(0);
            if (genreElement != null) {
                String href = genreElement.attr("href");
                if (href != null) {
                    bookPOJO.setUrlGenre(Url.SERVER_IZIBUK + href + "?p=");
                }
                String text = genreElement.text();
                if (text != null) {
                    bookPOJO.setGenre(text);
                }
            }
        }

        Elements descriptionList = document.getElementsByAttributeValue("itemprop", "description");
        if (descriptionList != null && descriptionList.size() != 0) {
            String text = descriptionList.first().text();
            if (text != null) {
                bookPOJO.setDesc(text);
            }
        }
        return bookPOJO;
    }

    public String getAutor() {
        return autor;
    }

    public void setAutor(String autor) {
        if (autor == null || autor.isEmpty()) return;
        this.autor = autor;
    }

    public void setPhoto(String photo) {
        if (photo == null || !Consts.REGEXP_URL_PHOTO.matcher(photo).matches()) {
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
        if (urlArtist == null || !Consts.REGEXP_URL.matcher(urlArtist).matches()) {
            return;
        }
        this.urlArtist = urlArtist;
    }

    public String getUrlAutor() {
        return urlAutor;
    }

    public void setUrlAutor(String urlAutor) {
        if (urlAutor == null || !Consts.REGEXP_URL.matcher(urlAutor).matches()) {
            return;
        }
        this.urlAutor = urlAutor;
    }

    public String getTime() {
        return time;
    }

    public void setTime(String time) {
        if (time == null || time.isEmpty()) time = "";
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
        if (urlGenre == null || !Consts.REGEXP_URL.matcher(urlGenre).matches()) {
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
            this.reting = "0";
        }
        this.reting = reting;
    }

    public boolean isNull() {
        return name == null || url == null;
    }

    public void setName(@NonNull String name) {
        if (name.isEmpty()) throw new IllegalArgumentException("Value must be not empty");
        this.name = name;
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
        descriptionPOJO.setTitle(this.name);
        descriptionPOJO.setPoster(this.photo);
        descriptionPOJO.setReiting(Integer.parseInt(this.reting.replaceAll(" ", "")));
        descriptionPOJO.setTime(this.time);
        descriptionPOJO.setAutor(this.autor);
        descriptionPOJO.setArtist(this.artist);
        descriptionPOJO.setArtistUrl(this.urlArtist);
        descriptionPOJO.setAutorUrl(this.urlAutor);
        descriptionPOJO.setGenre(this.genre);
        descriptionPOJO.setGenreUrl(this.urlGenre);
        descriptionPOJO.setDescription(this.desc);


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
}
