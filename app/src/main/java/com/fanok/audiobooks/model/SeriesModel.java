package com.fanok.audiobooks.model;

import androidx.annotation.NonNull;

import com.fanok.audiobooks.Url;
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

        Elements seriesConteiner = doc.getElementsByClass("book_blue_block book_serie_block");
        for (int i = 0; i < seriesConteiner.size(); i++) {
            Elements elementsTitle = seriesConteiner.get(i).getElementsByClass(
                    "book_serie_block_title");
            if (elementsTitle.size() != 0 && elementsTitle.first().text().contains("Цикл")) {
                Elements series = seriesConteiner.get(i).getElementsByClass(
                        "book_serie_block_item");
                for (Element item : series) {
                    SeriesPOJO seriesPOJO = new SeriesPOJO();
                    Elements indexElements = item.getElementsByClass("book_serie_block_item_index");
                    if (indexElements.size() != 0) {
                        seriesPOJO.setNumber(indexElements.first().text());
                    }

                    Elements strongElements = item.getElementsByTag("strong");
                    if (strongElements.size() != 0) {
                        seriesPOJO.setName(strongElements.first().text());
                    }

                    Elements aElements = item.getElementsByTag("a");
                    if (aElements.size() != 0) {
                        seriesPOJO.setName(aElements.first().text());
                        seriesPOJO.setUrl(Url.SERVER + aElements.first().attr("href"));
                    }

                    result.add(seriesPOJO);
                }
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
