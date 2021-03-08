package com.fanok.audiobooks.model;


import android.content.Context;
import androidx.annotation.NonNull;
import com.fanok.audiobooks.Consts;
import com.fanok.audiobooks.pojo.AudioPOJO;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.reactivex.Observable;
import java.io.IOException;
import java.util.ArrayList;
import org.jetbrains.annotations.NotNull;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

public class AudioModel implements
        com.fanok.audiobooks.interface_pacatge.book_content.AudioModelInterfece {

    Context mContext;

    public AudioModel(@NotNull final Context context) {
        mContext = context;
    }

    private static final String TAG = "AudioModel";

    @Override
    public Observable<ArrayList<AudioPOJO>> getAudio(@NonNull String url) {

        return Observable.create(observableEmitter -> {
            ArrayList<AudioPOJO> articlesModels;

            try {
                if (url.contains("knigavuhe.org")) {
                    articlesModels = loadSeriesList(url);
                } else if (url.contains("izib.uk")) {
                    articlesModels = loadSeriesListIzibuk(url);
                } else if (url.contains("audiobook-mp3.com")) {
                    articlesModels = loadSeriesListADMP3(url);
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

    private ArrayList<AudioPOJO> loadSeriesList(String url) throws IOException {
        ArrayList<AudioPOJO> result = new ArrayList<>();
        Connection connection = Jsoup.connect(url)
                .userAgent(Consts.USER_AGENT)
                .referrer("http://www.google.com")
                .sslSocketFactory(Consts.socketFactory());

        Document doc = connection.get();


        Elements titleElement = doc.getElementsByClass("book_title_elem book_title_name");
        String bookName = "";
        if (titleElement.size() != 0) {
            bookName = titleElement.first().text().trim();
        }
        Elements sriptElements = doc.getElementsByTag("script");
        for (Element script : sriptElements) {
            String value = script.toString();
            if (value.contains("domReady")) {
                value = deleteComnets(value);
                value = value.substring(value.indexOf("var player = new BookPlayer"));
                value = value.substring(0, value.indexOf("\n"));
                String json = value.substring(value.indexOf("["), value.indexOf("], ") + 1);
                JsonElement jsonTree = JsonParser.parseString(json);
                if (jsonTree.isJsonArray()) {
                    JsonArray jsonArray = jsonTree.getAsJsonArray();
                    for (JsonElement element : jsonArray) {
                        if (element.isJsonObject()) {
                            JsonObject jsonObject = element.getAsJsonObject();
                            AudioPOJO audioPOJO = new AudioPOJO();
                            audioPOJO.setName(jsonObject.get("title").getAsString());
                            audioPOJO.setUrl(jsonObject.get("url").getAsString());
                            audioPOJO.setTime(jsonObject.get("duration").getAsInt());
                            audioPOJO.setBookName(bookName);
                            result.add(audioPOJO);
                        }
                    }
                }

            }
        }

        return result;
    }

    private ArrayList<AudioPOJO> loadSeriesListADMP3(String url) throws IOException {
        ArrayList<AudioPOJO> result = new ArrayList<>();
        String autor = "";
        String bookName = "";

        Connection.Response res = Jsoup.connect(url)
                .method(Connection.Method.GET)
                .userAgent(Consts.USER_AGENT)
                .referrer("https://audiobook-mp3.com")
                .sslSocketFactory(Consts.socketFactory())
                .execute();

        Document doc = res.parse();

        Elements infos = doc.getElementsByClass("panel-item");
        if (infos != null) {
            for (Element info : infos) {
                if (info.text().contains("Автор")) {
                    Elements element = info.getElementsByTag("a");
                    if (element != null && element.size() != 0) {
                        autor = element.first().text();
                    }
                }
            }
        }

        Elements titleElement = doc.getElementsByTag("h1");
        if (titleElement.size() != 0) {
            String name = titleElement.first().text().trim();
            if (autor.isEmpty()) {
                bookName = name;
            } else {
                bookName = name.replace(autor + " - ", "");
            }
        }

        Elements sriptElements = doc.getElementsByTag("script");
        for (Element script : sriptElements) {
            String value = script.toString();
            if (value.contains("var player = new Playerjs")) {
                value = value.substring(value.indexOf("file:"));
                String urlJson = value.substring(value.indexOf("\"") + 1, value.lastIndexOf("\""));

                Document document = Jsoup.connect(urlJson)
                        .userAgent(Consts.USER_AGENT)
                        .referrer(url)
                        .sslSocketFactory(Consts.socketFactory())
                        .cookies(res.cookies())
                        .get();

                String json = document.body().text();

                JsonElement jsonTree = JsonParser.parseString(json);
                if (jsonTree.isJsonArray()) {
                    JsonArray jsonArray = jsonTree.getAsJsonArray();
                    for (JsonElement element : jsonArray) {
                        if (element.isJsonObject()) {
                            JsonObject jsonObject = element.getAsJsonObject();
                            AudioPOJO audioPOJO = new AudioPOJO();
                            audioPOJO.setName(jsonObject.get("title").getAsString());
                            audioPOJO.setUrl(jsonObject.get("file").getAsString());
                            audioPOJO.setBookName(bookName);
                            result.add(audioPOJO);
                        }
                    }
                }

                break;

            }
        }

        return result;
    }

    private ArrayList<AudioPOJO> loadSeriesListIzibuk(String url) throws IOException {
        ArrayList<AudioPOJO> result = new ArrayList<>();

        Connection connection = Jsoup.connect(url)
                .userAgent(Consts.USER_AGENT)
                .referrer("http://www.google.com")
                .sslSocketFactory(Consts.socketFactory());

        Document doc = connection.get();

        Elements titleElement = doc.getElementsByAttributeValue("itemprop", "name");
        String bookName = "";
        if (titleElement.size() != 0) {
            bookName = titleElement.first().text().trim();
        }
        Elements sriptElements = doc.getElementsByTag("script");
        for (Element script : sriptElements) {
            String value = script.toString();
            if (value.contains("domReady")) {
                value = deleteComnets(value);
                value = value.substring(value.indexOf("var player = new XSPlayer("));
                value = value.substring(0, value.indexOf("\n"));
                String json = value.substring(value.indexOf("(") + 1, value.indexOf(");"));
                JsonElement jsonTree = JsonParser.parseString(json);
                if (jsonTree.isJsonObject()) {
                    JsonObject jsonObject = jsonTree.getAsJsonObject();
                    JsonElement url_pref_element = jsonObject.get("mp3_url_prefix");
                    if (url_pref_element.isJsonPrimitive()) {
                        String url_pref = "https://" + url_pref_element.getAsString();
                        JsonArray array = jsonObject.getAsJsonArray("tracks");
                        for (int i = 0; i < array.size(); i++) {
                            AudioPOJO audioPOJO = new AudioPOJO();
                            JsonArray elements = array.get(i).getAsJsonArray();
                            audioPOJO.setName(elements.get(1).getAsString());
                            audioPOJO.setTime(elements.get(2).getAsInt());
                            audioPOJO.setUrl(url_pref + "/" + elements.get(4).getAsString());
                            audioPOJO.setBookName(bookName);
                            result.add(audioPOJO);
                        }
                    }
                }

            }
        }

        return result;
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
