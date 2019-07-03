package com.fanok.audiobooks.model;

import android.support.annotation.NonNull;

import com.fanok.audiobooks.interface_pacatge.searchable.SearchableModelAbstract;
import com.fanok.audiobooks.pojo.GenrePOJO;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.ArrayList;

public class SearchableModel extends SearchableModelAbstract {


    @Override
    public ArrayList<GenrePOJO> loadBooksList(@NonNull String url, @NonNull String qery)
            throws IOException {
        ArrayList<GenrePOJO> result = new ArrayList<>();
        if (getCookes() == null) setCookies();
        if (getSicretKey().isEmpty()) setSikretKey();
        Connection.Response response = Jsoup.connect(url)
                .userAgent(
                        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_12_6) AppleWebKit/537.36 "
                                + "(KHTML, like Gecko) Chrome/61.0.3163.100 Safari/537.36")
                .header("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8")
                .referrer("https://audioknigi.club/authors/")
                .cookies(getCookes()).data()
                .data("security_ls_key", getSicretKey())
                .data("sText", qery)
                .data("isPrefix", "0")
                .ignoreContentType(true)
                .method(Connection.Method.POST)
                .execute();
        String jsonResponse = response.body();

        JsonElement root = new JsonParser().parse(jsonResponse);
        JsonObject jobject = root.getAsJsonObject();
        String html = "<html><head></head><body><table>" + jobject.get("html").getAsString()
                + "</table></body></html";
        Document doc = Jsoup.parse(html);
        Elements tr = doc.getElementsByTag("tr");
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
            Elements subsribesConteiner = row.getElementsByClass("cell-rating");
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


        return result;
    }
}
