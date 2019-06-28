package com.fanok.audiobooks.model;

import com.fanok.audiobooks.pojo.GenrePOJO;
import com.fanok.audiobooks.presenter.BooksPresenter;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.ArrayList;

public class AutorsModel extends GenreModel {

    @Override
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
            if (tr.size() == 0) BooksPresenter.isEnd = true;
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
                Elements subsribesConteiner = row.getElementsByClass("cell-rating");
                if (subsribesConteiner.size() != 0) {
                    String subscribe = subsribesConteiner.first().text();
                    if (subscribe != null && !subscribe.isEmpty()) {
                        genrePOJO.setReting(
                                Integer.parseInt(subscribe));
                    }
                }
                if (!genrePOJO.isNull()) result.add(genrePOJO);
            }
        } else {
            BooksPresenter.isEnd = true;
        }
        return result;
    }
}
