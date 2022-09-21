package com.fanok.audiobooks.model;


import static de.blinkt.openvpn.core.VpnStatus.waitVpnConetion;

import android.content.SharedPreferences;
import androidx.annotation.NonNull;
import com.fanok.audiobooks.AutorsSearchABMP3;
import com.fanok.audiobooks.Consts;
import com.fanok.audiobooks.Url;
import com.fanok.audiobooks.interface_pacatge.searchable.SearchableModel;
import com.fanok.audiobooks.pojo.GenrePOJO;
import com.fanok.audiobooks.pojo.SearcheblPOJO;
import com.fanok.audiobooks.pojo.SearchebleArrayPOJO;
import io.reactivex.Observable;
import java.io.IOException;
import java.util.ArrayList;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

public class SearchebleModel implements SearchableModel {


    public SearcheblPOJO getSearcheblPOJO(@NonNull String url) throws IOException {
        SearcheblPOJO searcheblPOJO = new SearcheblPOJO();
        Document doc = Jsoup.connect(url)
                .userAgent(Consts.USER_AGENT)
                .referrer("http://www.google.com")
                .sslSocketFactory(Consts.socketFactory())
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
                                        Url.SERVER + a.attr("href") + "?page="));
                            }
                        }
                    } else {
                        String link = Url.SERVER + allResults.first().attr("href");
                        Document searchDoc = Jsoup.connect(link)
                                .userAgent(Consts.USER_AGENT)
                                .referrer("http://www.google.com")
                                .sslSocketFactory(Consts.socketFactory())
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
                                    list.add(new SearchebleArrayPOJO(name, href + "?page="));
                                }
                            }
                        }
                    }
                    if (title.contains("автор")) {
                        searcheblPOJO.setAutorsList(list);

                    } else if (title.contains("цикл")) {
                        searcheblPOJO.setSeriesList(list);
                    }

                }
            }
        }
        return searcheblPOJO;
    }

    @Override
    public Observable<SearcheblPOJO> dowland(SharedPreferences preferences, ArrayList<String> urls, String query) {
        return Observable.create(observableEmitter -> {
            waitVpnConetion();
            boolean searchKnigaVUhe = preferences.getBoolean("search_kniga_v_uhe", true);
            boolean searchABMP3 = preferences.getBoolean("search_abmp3", true);

            SearcheblPOJO searcheblPOJO = new SearcheblPOJO();
            try {

                for (String url : urls) {
                    if (url.contains("knigavuhe.org") && searchKnigaVUhe) {
                        searcheblPOJO = searcheblPOJO
                                .concat(searcheblPOJO, getSearcheblPOJO(url.replace("<qery>", query).replace("<page>",
                                        "1")));
                    } else if (url.contains("audiobook-mp3.com") && searchABMP3) {
                        SearcheblPOJO pojo = new SearcheblPOJO();
                        ArrayList<GenrePOJO> genrePOJOs = new AutorsSearchABMP3()
                                .getAutors(url.replace("<qery>", query), 1);
                        ArrayList<SearchebleArrayPOJO> searchebleArrayPOJOS = new ArrayList<>();
                        for (GenrePOJO genrePOJO : genrePOJOs) {
                            searchebleArrayPOJOS
                                    .add(new SearchebleArrayPOJO(genrePOJO.getName(), genrePOJO.getUrl()));
                        }
                        pojo.setAutorsList(searchebleArrayPOJOS);
                        searcheblPOJO = searcheblPOJO.concat(searcheblPOJO, pojo);
                    }
                }
                observableEmitter.onNext(searcheblPOJO);
            } catch (Exception e) {
                observableEmitter.onError(e);
            } finally {
                observableEmitter.onComplete();
            }
        });
    }
}
