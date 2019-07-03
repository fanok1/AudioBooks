package com.fanok.audiobooks.interface_pacatge.searchable;

import android.support.annotation.NonNull;

import com.fanok.audiobooks.Consts;
import com.fanok.audiobooks.pojo.GenrePOJO;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;
import java.util.regex.Matcher;

import io.reactivex.Observable;

public abstract class SearchableModelAbstract implements SearchableModel {
    private Map<String, String> cookes;
    private String sicretKey = "";


    @Override
    public void setCookies() {
        try {
            Connection.Response res = Jsoup
                    .connect("https://audioknigi.club/authors/")
                    .referrer("https://audioknigi.club/")
                    .userAgent("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_12_6) AppleWebKit/537.36 "
                            + "(KHTML, like Gecko) Chrome/61.0.3163.100 Safari/537.36")
                    .method(Connection.Method.GET)
                    .execute();

            cookes = res.cookies();
        } catch (IOException e) {
            cookes = null;
        }
    }

    @Override
    public void setSikretKey() {
        if (getCookes() == null) setCookies();
        Document doc;
        try {
            doc = Jsoup.connect("https://audioknigi.club/authors/")
                    .userAgent(
                            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_12_6) AppleWebKit/537.36 "
                                    + "(KHTML, like Gecko) Chrome/61.0.3163.100 Safari/537.36")
                    .referrer("http://www.google.com")
                    .cookies(getCookes())
                    .get();
            Elements inputs = doc.getElementsByTag("input");
            Elements input_text = inputs.attr("type", "text");
            Elements input_login = input_text.attr("name", "login");
            String keyVel = input_login.attr("data-parsley-remote-options");
            Matcher matcher = Consts.REGEXP_SIKRET_KEY.matcher(keyVel);
            if (matcher.find()) {
                sicretKey = matcher.group(1);
            }
        } catch (Exception e) {
            e.printStackTrace();
            sicretKey = "";
        }

    }

    protected String getSicretKey() {
        return sicretKey;
    }

    protected Map<String, String> getCookes() {
        return cookes;
    }

    @Override
    public Observable<ArrayList<GenrePOJO>> getBooks(@NonNull String url, @NonNull String qery) {
        return Observable.create(observableEmitter -> {
            ArrayList<GenrePOJO> articlesModels;
            try {
                articlesModels = loadBooksList(url, qery);
                observableEmitter.onNext(articlesModels);
            } catch (Exception e) {
                observableEmitter.onError(e);
            } finally {
                observableEmitter.onComplete();
            }
        });
    }
}
