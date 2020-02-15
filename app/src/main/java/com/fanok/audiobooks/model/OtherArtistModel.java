package com.fanok.audiobooks.model;


import androidx.annotation.NonNull;

import com.fanok.audiobooks.Consts;
import com.fanok.audiobooks.Url;
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


    @Override
    public Observable<ArrayList<OtherArtistPOJO>> getOtherArtist(@NonNull String url) {

        return Observable.create(observableEmitter -> {
            ArrayList<OtherArtistPOJO> articlesModels;
            try {
                if (url.contains("knigavuhe.org")) {
                    articlesModels = loadSeriesList(url);
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
