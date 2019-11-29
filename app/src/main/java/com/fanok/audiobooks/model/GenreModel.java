package com.fanok.audiobooks.model;

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
                .userAgent(
                        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_12_6) AppleWebKit/537.36 "
                                + "(KHTML, like Gecko) Chrome/61.0.3163.100 Safari/537.36")
                .referrer("http://www.google.com")
                .get();

        Elements items = doc.getElementsByClass("genre_item");
        for (Element item : items) {
            GenrePOJO genrePOJO = new GenrePOJO();
            Elements name = item.getElementsByClass("genre_item_name");
            if (name.size() != 0) {
                genrePOJO.setUrl(Url.SERVER + name.first().attr("href"));
                genrePOJO.setName(name.first().text());
            }

            Elements rating = item.getElementsByClass("subscribe_btn_label_count");
            if (rating.size() != 0) {
                genrePOJO.setReting(Integer.parseInt(rating.first().text()));
            }

            Elements description = item.getElementsByClass("genre_item_description");
            if (description.size() != 0) {
                genrePOJO.setDescription(description.first().text());
            }
            if (!genrePOJO.isNull()) result.add(genrePOJO);
        }
        return result;
    }

    @Override
    public Observable<ArrayList<GenrePOJO>> getBooks(String url, int page) {
        return Observable.create(observableEmitter -> {
            ArrayList<GenrePOJO> articlesModels;
            try {
                if (url.contains(String.valueOf(page))) {
                    for (int i = 1; i <= 4; i++) {
                        int temp = (page - 1) * 4 + i;
                        articlesModels = loadBooksList(
                                url.replace(String.valueOf(page), String.valueOf(temp)), temp);
                        observableEmitter.onNext(articlesModels);
                    }
                } else {
                    articlesModels = loadBooksList(url, page);
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
