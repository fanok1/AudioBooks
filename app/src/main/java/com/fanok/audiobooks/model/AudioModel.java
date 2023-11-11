package com.fanok.audiobooks.model;




import static com.fanok.audiobooks.App.useProxy;
import static com.fanok.audiobooks.Consts.PROXY_HOST;
import static com.fanok.audiobooks.Consts.PROXY_PORT;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;
import com.fanok.audiobooks.App;
import com.fanok.audiobooks.Consts;
import com.fanok.audiobooks.EncodingExeption;
import com.fanok.audiobooks.Url;
import com.fanok.audiobooks.pojo.AudioPOJO;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.reactivex.Observable;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Proxy.Type;
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

    public static String runScript(Context context, String key, String functionName) {
        String resultStr = null;
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
            Object obj = scope.get(functionName, scope);

            if (obj instanceof Function) {
                Function function = (Function) obj;
                Object result = function.call(rhino, scope, scope, functionParams);
                resultStr = org.mozilla.javascript.Context.toString(result);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        } finally {
            // We must exit the Rhino VM
            org.mozilla.javascript.Context.exit();
        }

        return resultStr;
    }

    @Override
    public Observable<ArrayList<AudioPOJO>> getAudio(@NonNull String url) {

        return Observable.create(observableEmitter -> {
            //waitVpnConetion();
            ArrayList<AudioPOJO> articlesModels;
            try {
                if (url.contains(Url.SERVER)) {
                    articlesModels = loadSeriesList(url);
                } else if (url.contains(Url.SERVER_IZIBUK)) {
                    articlesModels = loadSeriesListIzibuk(url);
                } else if (url.contains(Url.SERVER_ABMP3)) {
                    articlesModels = loadSeriesListADMP3(url);
                } else if (url.contains(Url.SERVER_AKNIGA)) {
                    articlesModels = loadSeriesListAbook(url);
                } else if (url.contains(Url.SERVER_BAZA_KNIG)) {
                    articlesModels = loadSeriesListBazaKnig(url);
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
                .maxBodySize(0)
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

        Connection connection = Jsoup.connect(url)
                .method(Connection.Method.GET)
                .userAgent(Consts.USER_AGENT)
                .referrer(Url.SERVER_ABMP3 + "/")
                .sslSocketFactory(Consts.socketFactory())
                .maxBodySize(0);

        if(App.useProxy) {
            Proxy proxy = new Proxy(Type.SOCKS,
                    new InetSocketAddress(PROXY_HOST, PROXY_PORT));
            connection.proxy(proxy);
        }

        Connection.Response res = connection.execute();

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



                Connection connection1 = Jsoup.connect(urlJson)
                        .userAgent(Consts.USER_AGENT)
                        .referrer(Url.SERVER_ABMP3 + "/")
                        .sslSocketFactory(Consts.socketFactory())
                        //.cookies(res.cookies())
                        .maxBodySize(0);

                if(App.useProxy) {
                    Proxy proxy = new Proxy(Type.SOCKS,
                            new InetSocketAddress(PROXY_HOST, PROXY_PORT));
                    connection.proxy(proxy);
                }

                Document document = connection1.get();

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
                .referrer(Url.SERVER_AKNIGA + "/performers/")
                .sslSocketFactory(Consts.socketFactory())
                .maxBodySize(0)
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

        String hash = runScript(mContext, key, "getHash");

        String text = Jsoup.connect(Url.SERVER_AKNIGA + "/ajax/b/" + id)
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
            String srv = jsonObject.get("srv").getAsString();
            JsonElement keyElement = jsonObject.get("key");
            String audioUrl;
            if(keyElement!=null) {
                String key2 = keyElement.getAsString();
                String filename = jsonObject.get("slug").getAsString();
                audioUrl = srv + "b/" + id + "/" + key2 + "/" + filename + ".mp3";
            }else {

                String hres = jsonObject.get("hres").getAsString();
                audioUrl = runScript(mContext, hres, "myDecrypt");
            }


            JsonElement jElement = jsonObject.get("items");
            if (jElement.isJsonPrimitive()) {
                String array = jElement.getAsString();
                jElement = JsonParser.parseString(array);
            }
            if (jElement.isJsonArray()) {
                JsonArray jarray = jElement.getAsJsonArray();

                SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(mContext);
                if (preferences.getBoolean("cutting_file", true)) {
                    for (int i = 0; i < jarray.size(); i++) {
                        AudioPOJO audioPOJO = new AudioPOJO();
                        audioPOJO.setUrl(audioUrl);
                        audioPOJO.setBookName(jsonObject.get("titleonly").getAsString());
                        JsonElement jsonElement = jarray.get(i);
                        if (jsonElement.isJsonObject()) {
                            String name = jsonElement.getAsJsonObject().get("title").getAsString();
                            audioPOJO.setName(name);
                            int duration = jsonElement.getAsJsonObject().get("duration").getAsInt();
                            int timeStart = jsonElement.getAsJsonObject().get("time_from_start").getAsInt();
                            int timeFinish = jsonElement.getAsJsonObject().get("time_finish").getAsInt();
                            audioPOJO.setTime(duration);
                            audioPOJO.setTimeStart(timeStart);
                            audioPOJO.setTimeFinish(timeFinish);
                        }
                        result.add(audioPOJO);
                    }
                }else {
                    AudioPOJO audioPOJO = new AudioPOJO();
                    audioPOJO.setUrl(audioUrl);
                    audioPOJO.setBookName(jsonObject.get("titleonly").getAsString());
                    audioPOJO.setName(jsonObject.get("titleonly").getAsString());
                    int duration = 0;
                    for (int i = 0; i < jarray.size(); i++) {
                        JsonElement jsonElement = jarray.get(i);
                        if (jsonElement.isJsonObject()) {
                            duration += jsonElement.getAsJsonObject().get("duration").getAsInt();
                        }
                        audioPOJO.setTime(duration);
                    }
                    result.add(audioPOJO);
                }
            }
        }
        return result;
    }

    private ArrayList<AudioPOJO> getAudioSorceBazaKnig(Document doc, String id, String bookName) {
        ArrayList<AudioPOJO> result = new ArrayList<>();
        Elements sriptElements = doc.getElementsByTag("script");
        for (Element script : sriptElements) {
            String value = script.toString();
            if (value.contains(id)) {
                String key = value.substring(value.indexOf("["), value.indexOf("]")) + "]";
                if (value.contains("strDecode")) {
                    throw new EncodingExeption(key);
                }
                JsonElement jsonTree = JsonParser.parseString(key);
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
            }
        }
        return result;
    }

    private ArrayList<AudioPOJO> loadSeriesListBazaKnig(String url) throws IOException {

        int indexSorce = url.indexOf("?sorce");
        int sorce = 1;
        if (indexSorce != -1) {
            String substring = url.substring(indexSorce).replace("?sorce=", "");
            sorce = Integer.parseInt(substring);
        }

        ArrayList<AudioPOJO> result = new ArrayList<>();

        Connection connection = Jsoup.connect(url)
                .userAgent(Consts.USER_AGENT)
                .referrer("http://www.google.com")
                .sslSocketFactory(Consts.socketFactory())
                .maxBodySize(0)
                .ignoreHttpErrors(true);

        if (!Consts.getBazaKnigCookies().isEmpty()) {
            connection.cookie("PHPSESSID", Consts.getBazaKnigCookies());
        }
        if(App.useProxy) {
            Proxy proxy = new Proxy(Type.SOCKS,
                    new InetSocketAddress(PROXY_HOST, PROXY_PORT));
            connection.proxy(proxy);
        }


        Document doc = connection.get();

        String bookName = "";
        Elements title = doc.getElementsByTag("h1");
        if (title != null && title.size() > 0) {
            String text = title.first().ownText();
            bookName = text.substring(0, text.indexOf(" - "));
        }

        Element div = doc.getElementById("fr");
        if (div != null) {
            String data = div.attr("data");
            if (data != null) {
                data = data.substring(data.indexOf("src="));
                data = data.replace("src=\"", "");
                data = data.substring(0, data.indexOf("\""));
                Connection conPlaylist = Jsoup.connect(data)
                        .userAgent(Consts.USER_AGENT)
                        .referrer("http://www.google.com")
                        .maxBodySize(0)
                        .sslSocketFactory(Consts.socketFactory());
                Document playlist = conPlaylist.get();
                Elements player = playlist.getElementsByClass("js-play8-playlist");
                if (player != null && player.size() > 0) {
                    String value = player.first().attr("value");
                    JsonElement jsonArray = JsonParser.parseString(value);
                    if (jsonArray.isJsonArray()) {
                        for (int i = 0; i < jsonArray.getAsJsonArray().size(); i++) {
                            JsonElement element = jsonArray.getAsJsonArray().get(i);
                            if (element.isJsonObject()) {
                                JsonObject jsonObject = element.getAsJsonObject();
                                AudioPOJO audioPOJO = new AudioPOJO();
                                audioPOJO.setName(bookName + "_" + (i + 1));
                                audioPOJO.setTime((int) jsonObject.get("duration").getAsDouble());
                                audioPOJO.setBookName(bookName);
                                JsonArray array = jsonObject.getAsJsonArray("sources");
                                if (array != null) {
                                    JsonElement element1 = array.get(0);
                                    if (element1 != null && element1.isJsonObject()) {
                                        JsonObject object = element1.getAsJsonObject();
                                        audioPOJO.setUrl("https://archive.org" + object.get("file").getAsString());
                                    }
                                }

                                result.add(audioPOJO);
                            }
                        }

                    }

                }
            }
        } else {
            if (sorce == 1) {
                result = getAudioSorceBazaKnig(doc, "id:\"player\"", bookName);
            } else {
                result = getAudioSorceBazaKnig(doc, "id:\"player" + sorce + "\"", bookName);
            }
        }

        return result;
    }

    private ArrayList<AudioPOJO> loadSeriesListIzibuk(String url) throws IOException {
        ArrayList<AudioPOJO> result = new ArrayList<>();

        Connection connection = Jsoup.connect(url)
                .userAgent(Consts.USER_AGENT)
                .referrer("http://www.google.com")
                .maxBodySize(0)
                .sslSocketFactory(Consts.socketFactory());

        if(useProxy){
            Proxy proxy = new Proxy(Type.SOCKS,
                    new InetSocketAddress(PROXY_HOST, PROXY_PORT));
            connection.proxy(proxy);
        }

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
                // value = value.substring(0, value.indexOf("\n"));
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
