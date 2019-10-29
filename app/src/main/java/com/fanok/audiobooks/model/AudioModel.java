package com.fanok.audiobooks.model;


import androidx.annotation.NonNull;

import com.fanok.audiobooks.pojo.AudioPOJO;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.ArrayList;

import io.reactivex.Observable;

public class AudioModel implements
        com.fanok.audiobooks.interface_pacatge.book_content.AudioModelInterfece {


    private ArrayList<AudioPOJO> loadSeriesList(String url) throws IOException {
        ArrayList<AudioPOJO> result = new ArrayList<>();
        Document doc = Jsoup.connect(url)
                .userAgent(
                        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_12_6) AppleWebKit/537.36 "
                                + "(KHTML, like Gecko) Chrome/61.0.3163.100 Safari/537.36")
                .referrer("http://www.google.com")
                .get();

        Elements sriptElements = doc.getElementsByTag("script");
        for (Element script : sriptElements) {
            String value = script.toString();
            if (value.contains("domReady")) {
                value = deleteComnets(value);
                value = value.substring(value.indexOf("var player = new BookPlayer"));
                value = value.substring(0, value.indexOf("\n"));
                String json = value.substring(value.indexOf("["), value.indexOf("]") + 1);
                JsonElement jsonTree = new JsonParser().parse(json);
                if (jsonTree.isJsonArray()) {
                    JsonArray jsonArray = jsonTree.getAsJsonArray();
                    for (JsonElement element : jsonArray) {
                        if (element.isJsonObject()) {
                            JsonObject jsonObject = element.getAsJsonObject();
                            AudioPOJO audioPOJO = new AudioPOJO();
                            audioPOJO.setName(jsonObject.get("title").getAsString());
                            audioPOJO.setUrl(jsonObject.get("url").getAsString());
                            audioPOJO.setTime(jsonObject.get("duration").getAsInt());
                            result.add(audioPOJO);
                        }
                    }
                }

            }
        }

        return result;
    }


    @Override
    public Observable<ArrayList<AudioPOJO>> getAudio(@NonNull String url) {

        return Observable.create(observableEmitter -> {
            ArrayList<AudioPOJO> articlesModels;
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

    private String deleteComnets(@NonNull String str) {
        int firstIndex = str.indexOf("/*");
        if (firstIndex != -1) {
            int lastIndex = str.indexOf("*/");
            if (lastIndex == -1) lastIndex = str.length() - 1;
            String subString = str.substring(firstIndex, lastIndex + 2);
            str = str.replace(subString, "");
            return deleteComnets(str);
        }
        return str;
    }
}
