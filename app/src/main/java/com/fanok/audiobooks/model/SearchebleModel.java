package com.fanok.audiobooks.model;


import android.content.SharedPreferences;
import androidx.annotation.NonNull;
import com.fanok.audiobooks.AutorsSearchABMP3;
import com.fanok.audiobooks.Url;
import com.fanok.audiobooks.interface_pacatge.searchable.SearchableModel;
import com.fanok.audiobooks.net.SiteHttp;
import com.fanok.audiobooks.pojo.GenrePOJO;
import com.fanok.audiobooks.pojo.SearcheblPOJO;
import com.fanok.audiobooks.pojo.SearchebleArrayPOJO;
import com.fanok.audiobooks.search.Provider;
import com.fanok.audiobooks.search.Sources;
import com.fanok.audiobooks.search.SearchUrls;
import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.schedulers.Schedulers;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

/** Author and series chips above the search results: every source independent, none blocks the others. */
public class SearchebleModel implements SearchableModel {

    private static final long TIMEOUT_SECONDS = 10;

    @Override
    public Observable<SearcheblPOJO> chips(SharedPreferences preferences, String query) {
        List<Single<SearcheblPOJO>> sources = new ArrayList<>();
        if (Sources.isActive(Provider.KNIGAVUHE) && preferences.getBoolean(Provider.KNIGAVUHE.prefKey, true)) {
            sources.add(Single.fromCallable(() -> getSearcheblPOJO(
                    SearchUrls.url(Provider.KNIGAVUHE, SearchUrls.Category.BOOKS, query, 1))));
        }
        if (Sources.isActive(Provider.ABMP3) && preferences.getBoolean(Provider.ABMP3.prefKey, true)) {
            sources.add(Single.fromCallable(() -> abmp3Authors(query)));
        }
        List<Observable<SearcheblPOJO>> bounded = new ArrayList<>();
        for (Single<SearcheblPOJO> source : sources) {
            bounded.add(source.subscribeOn(Schedulers.io())
                    .timeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .onErrorReturnItem(new SearcheblPOJO())
                    .toObservable());
        }
        return Observable.merge(bounded);
    }

    private static SearcheblPOJO abmp3Authors(String query) throws IOException {
        SearcheblPOJO pojo = new SearcheblPOJO();
        ArrayList<SearchebleArrayPOJO> authors = new ArrayList<>();
        String url = SearchUrls.url(Provider.ABMP3, SearchUrls.Category.AUTHORS, query, 1);
        for (GenrePOJO genrePOJO : new AutorsSearchABMP3().getAutors(url, 1).items) {
            add(authors, genrePOJO.getName(), genrePOJO.getUrl());
        }
        pojo.setAutorsList(authors);
        return pojo;
    }

    /** Adds a chip unless its name or URL is unusable (the POJO constructor throws on both). */
    private static void add(ArrayList<SearchebleArrayPOJO> list, String name, String url) {
        try {
            list.add(new SearchebleArrayPOJO(name, url));
        } catch (RuntimeException ignored) {
        }
    }

    public SearcheblPOJO getSearcheblPOJO(@NonNull String url) throws IOException {
        SearcheblPOJO searcheblPOJO = new SearcheblPOJO();
        Document doc = SiteHttp.get(url);
        Element root = doc.getElementById("search_blocks");
        if (root == null) {
            return searcheblPOJO;
        }
        for (Element block : root.getElementsByClass("search_block_wrap")) {
            Element titleElement = block.selectFirst(".search_block_title");
            if (titleElement == null) {
                continue;
            }
            String title = titleElement.text();
            ArrayList<SearchebleArrayPOJO> list = new ArrayList<>();
            Element allResults = block.selectFirst(".search_block_all_results_link");
            if (allResults == null) {
                for (Element item : block.getElementsByClass("search_block_item")) {
                    Element a = item.selectFirst("a");
                    if (a != null) {
                        add(list, item.text(), Url.SERVER + a.attr("href") + "?page=");
                    }
                }
            } else {
                Document searchDoc = SiteHttp.get(Url.SERVER + allResults.attr("href"));
                Elements items = searchDoc.select(".common_list .common_list_item");
                for (Element item : items) {
                    Element a = item.selectFirst(".author_item_name");
                    if (a == null) {
                        continue;
                    }
                    String name = a.text();
                    Element subName = item.selectFirst(".author_item_subname");
                    if (subName != null) {
                        name += " " + subName.text().trim();
                    }
                    add(list, name, Url.SERVER + a.attr("href") + "?page=");
                }
            }
            if (title.contains("автор")) {
                searcheblPOJO.setAutorsList(list);
            } else if (title.contains("цикл")) {
                searcheblPOJO.setSeriesList(list);
            }
        }
        return searcheblPOJO;
    }
}
