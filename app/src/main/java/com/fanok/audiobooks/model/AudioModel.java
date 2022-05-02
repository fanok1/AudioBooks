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
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jsoup.Connection;
import org.jsoup.Connection.Method;
import org.jsoup.Connection.Response;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.Scriptable;

public class AudioModel implements
        com.fanok.audiobooks.interface_pacatge.book_content.AudioModelInterfece {

    Context mContext;

    public AudioModel(@NotNull final Context context) {
        mContext = context;
    }

    private static final String TAG = "AudioModel";

    public static String runScript(Context context, String key) {
        // Get the JavaScript in previous section
        try {

            InputStream inputStream = context.getResources().openRawResource(
                    context.getResources().getIdentifier("config",
                            "raw", context.getPackageName()));
            String data = convertStreamToString(inputStream);

            Object[] functionParams = new Object[]{key};

            org.mozilla.javascript.Context rhino = org.mozilla.javascript.Context.enter();
            rhino.setOptimizationLevel(-1);
            Scriptable scope = rhino.initStandardObjects();
            rhino.evaluateString(scope, data, "JavaScript", 0, null);
            Object obj = scope.get("getHash", scope);

            if (obj instanceof Function) {
                Function function = (Function) obj;
                Object result = function.call(rhino, scope, scope, functionParams);
                return org.mozilla.javascript.Context.toString(result);
            } else {
                return null;
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        } finally {
            // We must exit the Rhino VM
            org.mozilla.javascript.Context.exit();
        }

        return null;
    }

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
                } else if (url.contains("akniga.org")) {
                    articlesModels = loadSeriesListAbook(url);
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

    private String deleteComnets(@NonNull String str) {
        int firstIndex = str.indexOf("/*");
        if (firstIndex != -1) {
            int lastIndex = str.indexOf("*/");
            if (lastIndex == -1) {
                lastIndex = str.length() - 1;
            }
            String subString = str.substring(firstIndex, lastIndex + 2);
            str = str.replace(subString, "");
            return deleteComnets(str);
        }
        return str;
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
                        .referrer("https://audiobook-mp3.com/")
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

    private ArrayList<AudioPOJO> loadSeriesListAbook(String url) throws IOException {
        ArrayList<AudioPOJO> result = new ArrayList<>();

        Response response = Jsoup.connect(url)
                .userAgent(Consts.USER_AGENT)
                .referrer("https://akniga.org/performers/")
                .sslSocketFactory(Consts.socketFactory())
                .execute();
        Map<String, String> cookies = response.cookies();

        Document doc = response.parse();

        Elements sriptTag = doc.getElementsByTag("script");
        String key = "";
        if (sriptTag != null) {
            for (Element script : sriptTag) {
                String text = script.data();
                if (text != null && text.contains("LIVESTREET_SECURITY_KEY")) {
                    text = text.substring(text.indexOf("LIVESTREET_SECURITY_KEY"));
                    text = text.replace("LIVESTREET_SECURITY_KEY = '", "");
                    key = text.substring(0, text.indexOf("'"));
                    break;
                }
            }
        }

        String id = "";
        Elements elements = doc.getElementsByTag("article");
        if (elements != null && elements.size() != 0) {
            id = elements.first().attr("data-bid");
        }

        String title = "";
        elements = doc.getElementsByClass("caption__article-main");
        if (elements != null && elements.size() != 0) {
            title = elements.first().text();
        }

        String autor = "";

        Elements autorElements = doc.getElementsByClass("about-author");
        if (autorElements != null && autorElements.size() != 0) {
            Elements aElements = autorElements.first().getElementsByTag("a");
            if (aElements != null && aElements.size() != 0) {
                autor = aElements.first().text();
            }
        }

        String hash = runScript(mContext, key);

        String text = Jsoup.connect("https://akniga.org/ajax/b/" + id)
                .userAgent(Consts.USER_AGENT)
                .method(Method.POST)
                .referrer(url)
                .sslSocketFactory(Consts.socketFactory())
                .data("bid", id)
                .data("hash", hash)
                .data("security_ls_key", key)
                .maxBodySize(0)
                .ignoreContentType(true)
                .cookies(cookies)
                .execute()
                .body();
        JsonElement json = JsonParser.parseString(text.replaceAll("\\n", ""));
        if (json.isJsonObject()) {
            JsonObject jsonObject = json.getAsJsonObject();
            String key2 = jsonObject.get("key").getAsString();
            String srv = jsonObject.get("srv").getAsString();
            String titleName = jsonObject.get("title").getAsString();

            JsonElement jElement = jsonObject.get("items");
            if (jElement.isJsonPrimitive()) {
                String array = jElement.getAsString();
                jElement = JsonParser.parseString(array);
            }

            if (jElement.isJsonArray()) {
                JsonArray jarray = jElement.getAsJsonArray();

                String lastFIle = "";

                for (int i = 0; i < jarray.size(); i++) {
                    AudioPOJO audioPOJO = new AudioPOJO();
                    JsonElement jsonElement = jarray.get(i);
                    if (jsonElement.isJsonObject()) {
                        String file = jsonElement.getAsJsonObject().get("file").getAsString();
                        if (Integer.parseInt(file) < 10) {
                            file = "0" + file;
                        }
                        int duration = jsonElement.getAsJsonObject().get("duration").getAsInt();

                        if (!file.equals(lastFIle)) {
                            audioPOJO.setBookName(title.replace(autor + " - ", ""));
                            audioPOJO.setName(file + " " + title);

                            //replaceAll("\\?","").replaceAll("!","").replaceAll("\"", "")
                            String audioUrl = srv + "b/" + id + "/" + key2 + "/" + file + ". " + titleName + ".mp3";
                            audioPOJO.setUrl(audioUrl);
                            audioPOJO.setTime(duration);
                            result.add(audioPOJO);
                        } else {
                            result.get(result.size() - 1).setTime(result.get(result.size() - 1).getTime() + duration);
                        }
                        lastFIle = file;
                    }
                }
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

    static String convertStreamToString(java.io.InputStream is) {
        java.util.Scanner s = new java.util.Scanner(is).useDelimiter("\\A");
        return s.hasNext() ? s.next() : "";
    }
}
