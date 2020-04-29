package com.fanok.audiobooks.model;


import androidx.annotation.NonNull;

import com.fanok.audiobooks.Consts;
import com.fanok.audiobooks.Url;
import com.fanok.audiobooks.pojo.BookPOJO;
import com.fanok.audiobooks.pojo.OtherArtistPOJO;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.ArrayList;

import io.reactivex.Observable;

public class OtherArtistModel implements
        com.fanok.audiobooks.interface_pacatge.book_content.OtherArtistModel {


    private ArrayList<OtherArtistPOJO> loadSeriesList(String url) throws IOException {
        ArrayList<OtherArtistPOJO> result = new ArrayList<>();
        Document doc = Jsoup.connect(url)
                .userAgent(Consts.USER_AGENT)
                .referrer("http://www.google.com")
                .sslSocketFactory(Consts.socketFactory())
                .get();

        Elements elements = doc.getElementsByClass("book_serie_block");
        for (Element element : elements) {
            Elements title = element.getElementsByClass("book_serie_block_title");
            if (title.size() != 0 && title.first().text().contains("Другие варианты озвучки")) {
                Elements items = element.getElementsByClass("book_serie_block_item");
                for (Element item : items) {
                    OtherArtistPOJO otherArtistPOJO = new OtherArtistPOJO();
                    Elements a = item.getElementsByTag("a");
                    if (a.size() != 0) {
                        otherArtistPOJO.setUrl(Url.SERVER + a.first().attr("href"));
                        otherArtistPOJO.setName(item.text().replace(a.first().text() + " ", ""));
                    }

                    result.add(otherArtistPOJO);
                }
            }
        }

        return result;
    }

    private ArrayList<OtherArtistPOJO> loadOtherArtistIzibuk(BookPOJO bookPOJO) throws IOException {
        ArrayList<OtherArtistPOJO> result = new ArrayList<>();
        Document doc = Jsoup.connect(
                "https://izib.uk/search?q=" + bookPOJO.getName() + " " + bookPOJO.getAutor())
                .userAgent(Consts.USER_AGENT)
                .referrer("http://www.google.com")
                .sslSocketFactory(Consts.socketFactory())
                .get();

        Element element = doc.getElementById("books_list");
        if (element != null) {
            Elements list = element.getElementsByClass("_ccb9b7");
            if (list != null && list.size() > 1) {
                for (Element book : list) {
                    OtherArtistPOJO pojo = new OtherArtistPOJO();
                    Elements nameCobteiners = book.getElementsByClass("_3dc935");
                    if (nameCobteiners != null && nameCobteiners.size() > 1) {
                        Element nameConteiner = nameCobteiners.get(1);
                        String href = nameConteiner.attr("href");
                        if (href != null) {
                            pojo.setUrl(Url.SERVER_IZIBUK + href);
                            Elements elements = book.getElementsByClass("_eeab32");
                            if (elements != null) {
                                for (Element conteiner : elements) {
                                    Elements aTag = conteiner.getElementsByTag("a");
                                    if (aTag != null && aTag.size() != 0) {
                                        String name = aTag.first().text();
                                        String urlArtist = aTag.first().attr("href");
                                        if (urlArtist != null && urlArtist.contains("reader")) {
                                            if (name != null && !name.equals(
                                                    bookPOJO.getArtist())) {
                                                pojo.setName("Исполнитель " + name);
                                                result.add(pojo);
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        return result;
    }


    @Override
    public Observable<ArrayList<OtherArtistPOJO>> getOtherArtist(@NonNull BookPOJO bookPOJO) {

        return Observable.create(observableEmitter -> {
            ArrayList<OtherArtistPOJO> articlesModels;
            try {
                if (bookPOJO.getUrl().contains("knigavuhe.org")) {
                    articlesModels = loadSeriesList(bookPOJO.getUrl());
                } else if (bookPOJO.getUrl().contains("izib.uk")) {
                    articlesModels = loadOtherArtistIzibuk(bookPOJO);
                } else {
                    articlesModels = new ArrayList<>();
                }
                observableEmitter.onNext(articlesModels);
            } catch (Exception e) {
                observableEmitter.onError(e);
            } finally {
                observableEmitter.onComplete();
            }
        });
    }
}
