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

    private boolean importBooks() throws IOException {
        Document document = Jsoup.connect(getUserUrl() + "fav/")
                .cookies(getCookiesAuth())
                .userAgent(Consts.USER_AGENT)
                .sslSocketFactory(Consts.socketFactory())
                .referrer(getUserUrl())
                .maxBodySize(0)
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
            //waitVpnConetion();
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
                } else if (src == Consts.IMPORT_SITE_ABOOK) {
                    if (!loginAbook(username, password)) {
                        observableEmitter.onNext(R.string.incorect_login_or_password);
                    } else {
                        if (importBooksAbook()) {
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

    private boolean importBooksAbook() throws IOException {
        int page = 1;
        BooksDBModel booksDBModel = new BooksDBModel(mContext);
        boolean end = false;
        while (true) {
            Document document = Jsoup.connect(getUserUrl() + "/books/page" + page + "/")
                    .cookies(getCookiesAuth())
                    .userAgent(Consts.USER_AGENT)
                    .sslSocketFactory(Consts.socketFactory())
                    .referrer(Url.SERVER_AKNIGA)
                    .maxBodySize(0)
                    .get();
            Elements bootom = document.getElementsByClass("page__nav");
            if (bootom != null && bootom.size() != 0) {
                Elements nextButton = bootom.get(0).getElementsByClass("page__nav--next");
                if (!(nextButton != null && nextButton.size() != 0)) {
                    if (end) {
                        return true;
                    }
                    end = true;
                }
            } else if (page > 1) {
                return true;
            }

            Elements listElements = document.getElementsByClass("content__main__articles--item");
            if (listElements != null && listElements.size() != 0) {
                for (Element book : listElements) {
                    Elements paid = book.getElementsByAttributeValue("href", Url.SERVER_AKNIGA + "/paid/");
                    if (paid != null && paid.size() != 0) {
                        continue;
                    }
                    Elements fragment = book.getElementsByClass("caption__article-preview");
                    if (fragment != null && fragment.size() != 0) {
                        String text = fragment.first().text();
                        if (text != null && text.equals("Фрагмент")) {
                            continue;
                        }
                    }
                    Elements link = book.getElementsByClass("content__article-main-link");
                    if (link != null && link.size() != 0) {
                        String src = link.first().attr("href");
                        if (src != null && !src.isEmpty()) {
                            if (!booksDBModel.inFavorite(src)) {
                                booksDBModel.addFavorite(BookPOJO.getBookByUrlAbook(src));
                            }
                        }
                    }
                }
            }

            page++;
        }
    }

    private boolean login(@NotNull String username, @NotNull String password) throws IOException {
        if (getCookies() == null || getToken() == null) {
            setCookes();
        }
        Connection.Response res = Jsoup.connect(Url.SERVER + "/login/")
                .cookies(getCookies())
                .data("email", username)
                .data("password", password)
                .data("token", getToken())
                .method(Connection.Method.POST)
                .sslSocketFactory(Consts.socketFactory())
                .ignoreContentType(true)
                .userAgent(Consts.USER_AGENT)
                .referrer(Url.SERVER + "/login/")
                .maxBodySize(0)
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
                        userUrl = Url.SERVER + jsonObject.get("url").getAsString();
                        return true;
                    }
                }
            }
            return false;
        }
    }

    private boolean loginAbook(@NotNull String username, @NotNull String password) throws IOException {
        if (getCookies() == null || getToken() == null) {
            setCookesAbook();
        }

        Connection.Response res = Jsoup.connect(Url.SERVER_AKNIGA + "/auth/ajax-login")
                .cookies(getCookies())
                .data("login", username)
                .data("password", password)
                .data("security_ls_key", getToken())
                .data("remember", "1")
                .data("return-path", Url.SERVER_AKNIGA + "/")
                .method(Connection.Method.POST)
                .sslSocketFactory(Consts.socketFactory())
                .ignoreContentType(true)
                .followRedirects(true)
                .userAgent(Consts.USER_AGENT)
                .referrer(Url.SERVER_AKNIGA)
                .maxBodySize(0)
                .execute();

        String json = res.body();

        JsonElement root = JsonParser.parseString(json);
        if (root.isJsonObject()) {
            JsonElement sMsg = root.getAsJsonObject().get("sMsg");
            if (!sMsg.isJsonNull() && sMsg.isJsonPrimitive()) {
                String text = sMsg.getAsString();
                if (text == null || text.contains("Неправильно указан логин (e-mail) или пароль!")) {
                    return false;
                } else {
                    cookiesAuth = res.cookies();
                    Document document = Jsoup.connect(Url.SERVER_AKNIGA)
                            .userAgent(Consts.USER_AGENT)
                            .referrer("http://www.google.com")
                            .sslSocketFactory(Consts.socketFactory())
                            .cookies(cookiesAuth)
                            .maxBodySize(0)
                            .get();
                    Elements elements = document.getElementsByClass("menu__user--wrapper--wrapper");
                    if (elements != null && elements.size() != 0) {
                        Element a = elements.first().child(2);
                        if (a != null) {
                            String href = a.attr("href");
                            if (href != null && !href.isEmpty()) {
                                userUrl = href;
                                return true;
                            }
                        }
                    }
                }
            }
        }
        return false;
    }

    private void setCookes() throws IOException {
        Connection.Response res = Jsoup.connect(Url.SERVER + "/login/")
                .method(Connection.Method.GET)
                .userAgent(Consts.USER_AGENT)
                .referrer(Url.SERVER)
                .sslSocketFactory(Consts.socketFactory())
                .maxBodySize(0)
                .execute();
        Document doc = res.parse();
        cookies = res.cookies();
        Element form = doc.selectFirst("form[name='login_form']>input[name='token']");
        if (form != null) {
            token = form.attr("value");
        }

    }

    private void setCookesAbook() throws IOException {
        Connection.Response res = Jsoup.connect(Url.SERVER_AKNIGA)
                .method(Connection.Method.GET)
                .userAgent(Consts.USER_AGENT)
                .referrer("https://www.google.com.ua/")
                .sslSocketFactory(Consts.socketFactory())
                .maxBodySize(0)
                .execute();
        Document doc = res.parse();
        cookies = res.cookies();
        Elements sriptTag = doc.getElementsByTag("script");
        if (sriptTag != null) {
            for (Element script : sriptTag) {
                String text = script.data();
                if (text != null && text.contains("LIVESTREET_SECURITY_KEY")) {
                    text = text.substring(text.indexOf("LIVESTREET_SECURITY_KEY"));
                    text = text.replace("LIVESTREET_SECURITY_KEY = '", "");
                    token = text.substring(0, text.indexOf("'"));
                    break;
                }
            }
        }

    }
}
