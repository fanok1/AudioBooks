package com.fanok.audiobooks.model;

import android.support.annotation.NonNull;

import com.fanok.audiobooks.pojo.SeriesPOJO;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.ArrayList;

import io.reactivex.Observable;

public class SeriesModel implements
        com.fanok.audiobooks.interface_pacatge.book_content.SeriesModel {


    private ArrayList<SeriesPOJO> loadSeriesList(String url) throws IOException {
        ArrayList<SeriesPOJO> result = new ArrayList<>();
        Document doc = Jsoup.connect(url)
                .userAgent(
                        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_12_6) AppleWebKit/537.36 "
                                + "(KHTML, like Gecko) Chrome/61.0.3163.100 Safari/537.36")
                .referrer("http://www.google.com")
                .get();

        Elements seriesConteiner = doc.getElementsByClass("series-info");
        if (seriesConteiner.size() != 0) {
            Elements seriesList = seriesConteiner.first().getElementsByTag("li");
            for (Element book : seriesList) {
                SeriesPOJO seriesPOJO = new SeriesPOJO();
                Elements a = book.getElementsByTag("a");
                if (a.size() != 0) {
                    seriesPOJO.setName(a.first().text());
                    seriesPOJO.setUrl(a.first().attr("href"));
                }
                Elements elements = book.getElementsByClass("favourite-count");
                if (elements.size() != 0) {
                    seriesPOJO.setReting(elements.first().text());
                }
                if (elements.size() > 1) {
                    Elements comnetsConteiner = elements.get(1).getElementsByTag("a");
                    if (comnetsConteiner.size() != 0) {
                        seriesPOJO.setComents(comnetsConteiner.first().text());
                    }
                }
                result.add(seriesPOJO);
            }
        }

        return result;
    }


    @Override
    public Observable<ArrayList<SeriesPOJO>> getSeries(@NonNull String url) {

        return Observable.create(observableEmitter -> {
            ArrayList<SeriesPOJO> articlesModels;
            try {
                articlesModels = loadSeriesList(url);
                observableEmitter.onNext(articlesModels);
            } catch (Exception e) {
                observableEmitter.onError(e);
            } finally {
                observableEmitter.onComplete();
            }
        });
    }
}
