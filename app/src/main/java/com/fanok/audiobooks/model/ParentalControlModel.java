package com.fanok.audiobooks.model;

import org.jetbrains.annotations.NotNull;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.ArrayList;

import io.reactivex.Observable;

public class ParentalControlModel {

    ArrayList<String> loadBooksList(String url) throws IOException {
        ArrayList<String> result = new ArrayList<>();
        Document doc = Jsoup.connect(url)
                .userAgent(
                        "Mozilla / 5.0 (Windows NT 10.0; Win64; x64) AppleWebKit / 537.36 (KHTML,"
                                + " как Gecko) Chrome / 60.0.3112.78 Safari / 537.36")
                .referrer("http://www.google.com")
                .get();

        Elements genreList = doc.getElementsByClass("genre_item");
        for (int i = 0; i < genreList.size(); i++) {
            Element conteiner = genreList.get(i);
            Elements aTags = conteiner.getElementsByTag("a");
            if (aTags.size() != 0) {
                Element aTag = aTags.first();
                String text = aTag.text();
                if (text != null && !text.isEmpty()) {
                    result.add(text);
                }
            }
        }

        return result;
    }


    public Observable<ArrayList<String>> getBooks(@NotNull String url) {
        return Observable.create(observableEmitter -> {
            ArrayList<String> genres;
            try {
                genres = loadBooksList(url);
                observableEmitter.onNext(genres);
            } catch (Exception e) {
                observableEmitter.onError(e);
            } finally {
                observableEmitter.onComplete();
            }
        });
    }
}
