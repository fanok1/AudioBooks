package com.fanok.audiobooks.model;


import android.content.Context;
import androidx.annotation.NonNull;
import com.fanok.audiobooks.Consts;
import com.fanok.audiobooks.R;
import com.fanok.audiobooks.Url;
import com.fanok.audiobooks.pojo.BookPOJO;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.reactivex.Observable;
import java.io.IOException;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

public class ImportModel {

    private final Context mContext;

    private final int src;

    private Map<String, String> cookies;

    private Map<String, String> cookiesAuth;

    private String token;

    private String userUrl;


    public ImportModel(@NotNull Context context, int src) {
        mContext = context;
        this.src = src;
    }

    private void setCookes() throws IOException {
        Connection.Response res = Jsoup.connect("https://knigavuhe.org/login/")
                .method(Connection.Method.GET)
                .userAgent(Consts.USER_AGENT)
                .referrer("https://knigavuhe.org")
                .sslSocketFactory(Consts.socketFactory())
                .execute();
        Document doc = res.parse();
        cookies = res.cookies();
        Element form = doc.selectFirst("form[name='login_form']>input[name='token']");
        if (form != null) {
            token = form.attr("value");
        }

    }

    private Map<String, String> getCookies() {
        return cookies;
    }

    private String getToken() {
        return token;
    }

    private Map<String, String> getCookiesAuth() {
        return cookiesAuth;
    }

    private String getUserUrl() {
        return userUrl;
    }

    private boolean login(@NotNull String username, @NotNull String password) throws IOException {
        if (getCookies() == null || getToken() == null) setCookes();
        Connection.Response res = Jsoup.connect("https://knigavuhe.org/login/")
                .cookies(getCookies())
                .data("email", username)
                .data("password", password)
                .data("token", getToken())
                .method(Connection.Method.POST)
                .sslSocketFactory(Consts.socketFactory())
                .ignoreContentType(true)
                .userAgent(Consts.USER_AGENT)
                .referrer("https://knigavuhe.org/login/")
                .execute();
        if (res.url().toString().contains("error")) {
            return false;
        } else {
            cookiesAuth = res.cookies();
            String json = res.body();
            JsonElement root = JsonParser.parseString(json);
            if (root.isJsonArray()) {
                JsonArray jsonArray = root.getAsJsonArray();
                for (int i = 0; i < jsonArray.size(); i++) {
                    if (jsonArray.get(i).isJsonObject()) {
                        JsonObject jsonObject = jsonArray.get(i).getAsJsonObject();
                        userUrl = "https://knigavuhe.org" + jsonObject.get("url").getAsString();
                        return true;
                    }
                }
            }
            return false;
        }
    }

    private boolean importBooks() throws IOException {
        Document document = Jsoup.connect(getUserUrl() + "fav/")
                .cookies(getCookiesAuth())
                .userAgent(Consts.USER_AGENT)
                .sslSocketFactory(Consts.socketFactory())
                .referrer(getUserUrl())
                .get();
        Element booksListParent = document.getElementById("books_list");
        if (booksListParent != null) {
            Elements booksList = booksListParent.getElementsByClass("books_carousel");
            if (booksList.size() != 0) {
                Elements aList = booksList.first().getElementsByTag("a");
                BooksDBModel booksDBModel = new BooksDBModel(mContext);
                for (Element a : aList) {
                    String url = Url.SERVER + a.attr("href");
                    if (!booksDBModel.inFavorite(url)) {
                        booksDBModel.addFavorite(BookPOJO.getBookByUrl(url));
                    }

                }
                booksDBModel.closeDB();
            }
            return true;
        }
        return false;
    }

    public Observable<Integer> importBooks(@NonNull String username, @NotNull String password) {
        return Observable.create(observableEmitter -> {
            try {
                if (src == Consts.IMPORT_SITE_KNIGA_V_UHE) {
                    if (!login(username, password)) {
                        observableEmitter.onNext(R.string.incorect_login_or_password);
                    } else {
                        if (importBooks()) {
                            observableEmitter.onNext(R.string.import_complite);
                        } else {
                            observableEmitter.onNext(R.string.error_import);
                        }
                    }
                } else {
                    observableEmitter.onNext(R.string.worong_data_sorce);
                }
            } catch (Exception e) {
                observableEmitter.onError(e);
            } finally {
                observableEmitter.onComplete();
            }
        });
    }
}
