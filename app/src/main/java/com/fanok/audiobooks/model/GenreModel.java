package com.fanok.audiobooks.model;

import com.fanok.audiobooks.Consts;
import com.fanok.audiobooks.Url;
import com.fanok.audiobooks.pojo.GenrePOJO;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.ArrayList;

import io.reactivex.Observable;

public class GenreModel implements com.fanok.audiobooks.interface_pacatge.books.GenreModel {


    protected ArrayList<GenrePOJO> loadBooksList(String url, int page) throws IOException {
        ArrayList<GenrePOJO> result = new ArrayList<>();
        Document doc = Jsoup.connect(url)
                .userAgent(Consts.USER_AGENT)
                .referrer("http://www.google.com")
                .get();

        Elements items = doc.getElementsByClass("genre2_item");
        for (Element item : items) {
            GenrePOJO genrePOJO = new GenrePOJO();
            Elements name = item.getElementsByClass("genre2_item_name");
            if (name.size() != 0) {
                genrePOJO.setUrl(Url.SERVER + name.first().attr("href"));
                genrePOJO.setName(name.first().text());
            }

            Elements rating = item.getElementsByClass("subscribe_btn_label_count");
            if (rating.size() != 0) {
                genrePOJO.setReting(Integer.parseInt(rating.first().text()));
            }

            Elements description = item.getElementsByClass("genre2_item_description");
            if (description.size() != 0) {
                genrePOJO.setDescription(description.first().text());
            }
            if (!genrePOJO.isNull()) result.add(genrePOJO);
        }
        return result;
    }

    protected ArrayList<GenrePOJO> loadBooksListIzibuk(String url, int page) throws IOException {
        ArrayList<GenrePOJO> result = new ArrayList<>();
        Document doc = Jsoup.connect(url + page)
                .userAgent(Consts.USER_AGENT)
                .referrer("http://www.google.com")
                .get();

        Elements items = doc.getElementsByClass("_e181af");
        if (items != null && items.size() != 0) {
            Elements list = items.first().children();
            if (list != null) {
                for (Element item : list) {
                    GenrePOJO genrePOJO = new GenrePOJO();

                    String src = item.attr("href");
                    if (src != null) {
                        genrePOJO.setUrl(Url.SERVER_IZIBUK + src + "?p=");
                    }


                    Elements children = item.children();
                    if (children != null && children.size() == 2) {
                        String name = children.first().text();
                        if (name != null) {
                            genrePOJO.setName(name);
                        }

                        String desc = children.last().text();
                        if (desc != null) {
                            genrePOJO.setDescription(desc);
                        }
                    }


                    if (!genrePOJO.isNull()) result.add(genrePOJO);
                }
            }
        }
        return result;
    }

    @Override
    public Observable<ArrayList<GenrePOJO>> getBooks(String url, int page) {
        int size = 4;

        return Observable.create(observableEmitter -> {
            ArrayList<GenrePOJO> articlesModels;
            try {
                if (url.contains(String.valueOf(page))) {
                    for (int i = 1; i <= size; i++) {
                        int temp = (page - 1) * size + i;
                        if (url.contains("knigavuhe.org")) {
                            articlesModels = loadBooksList(
                                    url.replace(String.valueOf(page), String.valueOf(temp)), temp);
                        } else if (url.contains("izibuk.ru/")) {
                            articlesModels = loadBooksListIzibuk(
                                    url.replace(String.valueOf(page), String.valueOf(temp)), temp);
                        } else {
                            articlesModels = new ArrayList<>();
                        }
                        observableEmitter.onNext(articlesModels);
                    }
                } else {
                    if (url.contains("knigavuhe.org")) {
                        articlesModels = loadBooksList(url, page);
                    } else if (url.contains("izibuk.ru/")) {
                        articlesModels = loadBooksListIzibuk(url, page);
                    } else {
                        articlesModels = new ArrayList<>();
                    }
                    observableEmitter.onNext(articlesModels);
                }
            } catch (Exception e) {
                observableEmitter.onError(e);
            } finally {
                observableEmitter.onComplete();
            }
        });
    }
}
