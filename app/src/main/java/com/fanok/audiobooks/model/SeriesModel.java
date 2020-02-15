package com.fanok.audiobooks.model;

import androidx.annotation.NonNull;

import com.fanok.audiobooks.Consts;
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
                .userAgent(Consts.USER_AGENT)
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

    private ArrayList<SeriesPOJO> loadSeriesListIzibuk(String url) throws IOException {
        ArrayList<SeriesPOJO> result = new ArrayList<>();
        Document doc = Jsoup.connect(url)
                .userAgent(Consts.USER_AGENT)
                .referrer("http://www.google.com")
                .get();

        Elements parents = doc.getElementsByClass("_b264b2 _49d1b4");
        if (parents != null && parents.size() != 0) {
            Elements series = parents.first().getElementsByClass("_f61db9");
            if (series != null) {
                for (Element serie : series) {
                    SeriesPOJO seriesPOJO = new SeriesPOJO();
                    Elements namberConteiner = serie.getElementsByClass("_bb8bca");
                    if (namberConteiner != null && namberConteiner.size() != 0) {
                        String number = namberConteiner.first().text();
                        if (number != null) {
                            seriesPOJO.setNumber(number);
                        }
                    }
                    Elements aTag = serie.getElementsByTag("a");
                    if (aTag != null && aTag.size() != 0) {
                        Element a = aTag.first();
                        String href = a.attr("href");
                        if (href != null) {
                            seriesPOJO.setUrl(Url.SERVER_IZIBUK + href);
                        }
                        String text = serie.text();
                        if (text != null) {
                            seriesPOJO.setName(text.replace(seriesPOJO.getNumber(), ""));
                        }
                    } else {
                        Elements stringTag = serie.getElementsByTag("strong");
                        if (stringTag != null && stringTag.size() != 0) {
                            String text = stringTag.first().text();
                            if (text != null) {
                                seriesPOJO.setName(text);
                            }
                        }
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
                if (url.contains("knigavuhe.org")) {
                    articlesModels = loadSeriesList(url);
                } else if (url.contains("izibuk.ru")) {
                    articlesModels = loadSeriesListIzibuk(url);
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
