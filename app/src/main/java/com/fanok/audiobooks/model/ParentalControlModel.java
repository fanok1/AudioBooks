package com.fanok.audiobooks.model;

import com.fanok.audiobooks.Consts;

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
                .userAgent(Consts.USER_AGENT)
                .referrer("http://www.google.com")
                .sslSocketFactory(Consts.socketFactory())
                .get();

        Elements genreList = doc.getElementsByClass("genre2_item");
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
