package com.fanok.audiobooks.model;

import com.fanok.audiobooks.pojo.GenrePOJO;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.ArrayList;

import io.reactivex.Observable;

public class GenreModel implements com.fanok.audiobooks.interface_pacatge.books.GenreModel {


    protected ArrayList<GenrePOJO> loadBooksList(String url) throws IOException {
        ArrayList<GenrePOJO> result = new ArrayList<>();
        Document doc = Jsoup.connect(url)
                .userAgent(
                        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_12_6) AppleWebKit/537.36 "
                                + "(KHTML, like Gecko) Chrome/61.0.3163.100 Safari/537.36")
                .referrer("http://www.google.com")
                .get();
        Elements table = doc.getElementsByTag("table");
        if (table.size() != 0) {
            Elements tr = table.first().getElementsByTag("tbody").first().getElementsByTag("tr");
            if (tr.size() == 0) return null;
            for (Element row : tr) {
                GenrePOJO genrePOJO = new GenrePOJO();
                Elements h4 = row.getElementsByTag("h4");
                if (h4.size() != 0) {
                    Element a = h4.first().child(0);
                    if (a != null) {
                        String src = a.attr("href");
                        if (src != null && !src.isEmpty()) genrePOJO.setUrl(src);
                        String name = a.text();
                        if (name != null && !name.isEmpty()) genrePOJO.setName(name);
                    }
                }
                Elements subsribesConteiner = row.getElementsByClass("cell-readers");
                if (subsribesConteiner.size() != 0) {
                    String subscribe = subsribesConteiner.first().text();
                    if (subscribe != null && !subscribe.isEmpty()) {
                        genrePOJO.setReting(
                                Integer.parseInt(subscribe));
                    }
                }
                Elements p = row.getElementsByTag("p");
                if (p.size() != 0) {
                    String desc = p.first().text();
                    if (desc != null && !desc.isEmpty()) genrePOJO.setDescription(desc);
                }
                if (!genrePOJO.isNull()) result.add(genrePOJO);
            }
        } else {
            return null;
        }
        return result;
    }

    @Override
    public Observable<ArrayList<GenrePOJO>> getBooks(String url) {
        return Observable.create(observableEmitter -> {
            ArrayList<GenrePOJO> articlesModels;
            try {
                articlesModels = loadBooksList(url);
                observableEmitter.onNext(articlesModels);
            } catch (Exception e) {
                observableEmitter.onError(e);
            } finally {
                observableEmitter.onComplete();
            }
        });
    }
}
