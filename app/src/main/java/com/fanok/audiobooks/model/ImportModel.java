package com.fanok.audiobooks.model;


import android.content.Context;
import androidx.annotation.NonNull;
import com.fanok.audiobooks.Consts;
import com.fanok.audiobooks.R;
import com.fanok.audiobooks.Url;
import com.fanok.audiobooks.net.SiteHttp;
import com.fanok.audiobooks.pojo.BookPOJO;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import io.reactivex.Single;
import io.reactivex.SingleEmitter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

public class ImportModel {

    /** Hard cap on akniga list pages requested by one import. */
    private static final int MAX_PAGES = 200;

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

    private boolean importBooks(SingleEmitter<?> emitter) throws IOException {
        Document document = SiteHttp.parse(SiteHttp.execute(SiteHttp.connect(getUserUrl() + "fav/")
                .cookies(getCookiesAuth())
                .referrer(getUserUrl())
                ));
        Element booksListParent = document.getElementById("books_list");
        if (booksListParent != null) {
            Elements booksList = booksListParent.getElementsByClass("books_carousel");
            if (booksList.size() != 0) {
                Set<String> urls = new LinkedHashSet<>();
                for (Element a : booksList.first().getElementsByTag("a")) {
                    urls.add(Url.SERVER + a.attr("href"));
                }
                addFavorites(urls, BookPOJO::getBookByUrl, emitter);
            }
            return true;
        }
        return false;
    }

    /**
     * Emits exactly one terminal signal: the message to show on success, or the error. Nothing is stored
     * unless every book was loaded, and then all are stored in one transaction.
     */
    public Single<Integer> importBooks(@NonNull String username, @NotNull String password) {
        return Single.create(emitter -> {
            int message;
            try {
                if (src == Consts.IMPORT_SITE_KNIGA_V_UHE) {
                    if (!login(username, password)) {
                        message = R.string.incorect_login_or_password;
                    } else {
                        message = importBooks(emitter) ? R.string.import_complite : R.string.error_import;
                    }
                } else if (src == Consts.IMPORT_SITE_ABOOK) {
                    if (!loginAbook(username, password)) {
                        message = R.string.incorect_login_or_password;
                    } else {
                        message = importBooksAbook(emitter);
                    }
                } else {
                    message = R.string.worong_data_sorce;
                }
            } catch (Exception e) {
                emitter.tryOnError(e);
                return;
            }
            emitter.onSuccess(message);
        });
    }

    private interface BookLoader {
        BookPOJO load(String url) throws IOException;
    }

    private void addFavorites(Collection<String> urls, BookLoader loader, SingleEmitter<?> emitter)
            throws IOException {
        BooksDBModel booksDBModel = new BooksDBModel(mContext);
        List<BookPOJO> books = new ArrayList<>();
        for (String url : urls) {
            if (emitter.isDisposed()) {
                return;
            }
            if (!booksDBModel.inFavorite(url)) {
                books.add(loader.load(url));
            }
        }
        if (emitter.isDisposed()) {
            return;
        }
        booksDBModel.addFavorites(books);
    }

    /** True when the akniga list page links to a further page. */
    static boolean hasNextPage(@NonNull Document page) {
        Elements nav = page.getElementsByClass("page__nav");
        return !nav.isEmpty() && !nav.first().getElementsByClass("page__nav--next").isEmpty();
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

    /** Returns the message to show: complete, or partial when the page cap was reached with pages left. */
    private int importBooksAbook(SingleEmitter<?> emitter) throws IOException {
        Set<String> urls = new LinkedHashSet<>();
        int page = 1;
        for (; page <= MAX_PAGES && !emitter.isDisposed(); page++) {
            Document document = SiteHttp.parse(SiteHttp.execute(SiteHttp.connect(getUserUrl() + "/books/page" + page + "/")
                    .cookies(getCookiesAuth())
                    .referrer(Url.SERVER_AKNIGA)
                    ));

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
                            urls.add(src);
                        }
                    }
                }
            }

            if (listElements.isEmpty() || !hasNextPage(document)) {
                break;
            }
        }
        boolean partial = page > MAX_PAGES;
        addFavorites(urls, BookPOJO::getBookByUrlAbook, emitter);
        return partial ? R.string.import_partial : R.string.import_complite;
    }

    private boolean login(@NotNull String username, @NotNull String password) throws IOException {
        if (getCookies() == null || getToken() == null) {
            setCookes();
        }
        Connection.Response res = SiteHttp.execute(SiteHttp.connect(Url.SERVER + "/login/")
                .cookies(getCookies())
                .data("email", username)
                .data("password", password)
                .data("token", getToken())
                .method(Connection.Method.POST)
                .ignoreContentType(true)
                .referrer(Url.SERVER + "/login/")
                );
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
                        JsonElement url = jsonArray.get(i).getAsJsonObject().get("url");
                        if (url == null || !url.isJsonPrimitive()) {
                            return false;
                        }
                        userUrl = Url.SERVER + url.getAsString();
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

        Connection.Response res = SiteHttp.execute(SiteHttp.connect(Url.SERVER_AKNIGA + "/auth/ajax-login")
                .cookies(getCookies())
                .data("login", username)
                .data("password", password)
                .data("security_ls_key", getToken())
                .data("remember", "1")
                .data("return-path", Url.SERVER_AKNIGA + "/")
                .method(Connection.Method.POST)
                .ignoreContentType(true)
                .followRedirects(true)
                .referrer(Url.SERVER_AKNIGA)
                );

        String json = res.body();

        JsonElement root = JsonParser.parseString(json);
        if (root.isJsonObject()) {
            JsonElement sMsg = root.getAsJsonObject().get("sMsg");
            if (sMsg != null && sMsg.isJsonPrimitive()) {
                String text = sMsg.getAsString();
                if (text == null || text.contains("Неправильно указан логин (e-mail) или пароль!")) {
                    return false;
                } else {
                    cookiesAuth = res.cookies();
                    Document document = SiteHttp.parse(SiteHttp.execute(SiteHttp.connect(Url.SERVER_AKNIGA)
                            .referrer("http://www.google.com")
                            .cookies(cookiesAuth)
                            ));
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
        Connection.Response res = SiteHttp.execute(SiteHttp.connect(Url.SERVER + "/login/")
                .method(Connection.Method.GET)
                .referrer(Url.SERVER)
                );
        Document doc = res.parse();
        cookies = res.cookies();
        Element form = doc.selectFirst("form[name='login_form']>input[name='token']");
        if (form != null) {
            token = form.attr("value");
        }

    }

    private void setCookesAbook() throws IOException {
        Connection.Response res = SiteHttp.execute(SiteHttp.connect(Url.SERVER_AKNIGA)
                .method(Connection.Method.GET)
                .referrer("https://www.google.com.ua/")
                );
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
