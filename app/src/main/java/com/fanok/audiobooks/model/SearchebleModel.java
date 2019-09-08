package com.fanok.audiobooks.model;

import androidx.annotation.NonNull;

import com.fanok.audiobooks.Url;
import com.fanok.audiobooks.interface_pacatge.searchable.SearchableModel;
import com.fanok.audiobooks.pojo.SearcheblPOJO;
import com.fanok.audiobooks.pojo.SearchebleArrayPOJO;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.ArrayList;

import io.reactivex.Observable;

public class SearchebleModel implements SearchableModel {


    public SearcheblPOJO getSearcheblPOJO(@NonNull String url) throws IOException {
        SearcheblPOJO searcheblPOJO = new SearcheblPOJO();
        Document doc = Jsoup.connect(url)
                .userAgent(
                        "Mozilla / 5.0 (Windows NT 10.0; Win64; x64) AppleWebKit / 537.36 (KHTML,"
                                + " как Gecko) Chrome / 60.0.3112.78 Safari / 537.36")
                .referrer("http://www.google.com")
                .get();


        Element root = doc.getElementById("search_blocks");
        if (root != null) {
            Elements searchBlocks = root.getElementsByClass("search_block_wrap");
            for (int i = 0; i < searchBlocks.size(); i++) {
                Element block = searchBlocks.get(i);
                Elements titleConteiner = block.getElementsByClass("search_block_title");
                if (titleConteiner.size() != 0) {
                    Element titleElement = titleConteiner.first();
                    String title = titleElement.text();
                    ArrayList<SearchebleArrayPOJO> list = new ArrayList<>();

                    Elements allResults = block.getElementsByClass("search_block_all_results_link");
                    if (allResults.size() == 0) {
                        Elements items = block.getElementsByClass("search_block_item");

                        for (int j = 0; j < items.size(); j++) {
                            Element item = items.get(j);
                            Elements aElements = item.getElementsByTag("a");
                            if (aElements.size() != 0) {
                                Element a = aElements.first();
                                list.add(new SearchebleArrayPOJO(item.text(),
                                        Url.SERVER + a.attr("href")));
                            }
                        }
                    } else {
                        String link = Url.SERVER + allResults.first().attr("href");
                        Document searchDoc = Jsoup.connect(link)
                                .userAgent(
                                        "Mozilla / 5.0 (Windows NT 10.0; Win64; x64) AppleWebKit "
                                                + "/ 537.36 (KHTML,"
                                                + " как Gecko) Chrome / 60.0.3112.78 Safari / 537"
                                                + ".36")
                                .referrer("http://www.google.com")
                                .get();
                        Elements itemsConteiner = searchDoc.getElementsByClass("common_list");
                        if (itemsConteiner.size() != 0) {
                            Elements items = itemsConteiner.first().getElementsByClass(
                                    "common_list_item");
                            for (Element item : items) {
                                Elements aElements = item.getElementsByClass("author_item_name");
                                if (aElements.size() != 0) {
                                    String href = Url.SERVER + aElements.first().attr("href");
                                    String name = aElements.first().text();
                                    Elements subNameConteiner = item.getElementsByClass(
                                            "author_item_subname");
                                    if (subNameConteiner.size() != 0) {
                                        name += " " + subNameConteiner.first().text().trim();
                                    }
                                    list.add(new SearchebleArrayPOJO(name, href));
                                }
                            }
                        }
                    }
                    if (title.contains("автор")) {
                        searcheblPOJO.setAutorsCount(title);
                        searcheblPOJO.setAutorsList(list);

                    } else if (title.contains("цикл")) {
                        searcheblPOJO.setSeriesCount(title);
                        searcheblPOJO.setSeriesList(list);
                    }

                }
            }
        }
        return searcheblPOJO;
    }

    @Override
    public Observable<SearcheblPOJO> dowland(String url) {
        return Observable.create(observableEmitter -> {
            SearcheblPOJO searcheblPOJO;
            try {
                searcheblPOJO = getSearcheblPOJO(url);
                observableEmitter.onNext(searcheblPOJO);
            } catch (Exception e) {
                observableEmitter.onError(e);
            } finally {
                observableEmitter.onComplete();
            }
        });
    }
}
