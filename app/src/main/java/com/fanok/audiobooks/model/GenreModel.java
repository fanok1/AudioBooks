package com.fanok.audiobooks.model;


import static de.blinkt.openvpn.core.VpnStatus.waitVpnConetion;

import com.fanok.audiobooks.AutorsSearchABMP3;
import com.fanok.audiobooks.Consts;
import com.fanok.audiobooks.CookesExeption;
import com.fanok.audiobooks.Url;
import com.fanok.audiobooks.pojo.GenrePOJO;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.reactivex.Observable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;
import org.jsoup.Connection;
import org.jsoup.Connection.Method;
import org.jsoup.Connection.Response;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

public class GenreModel implements com.fanok.audiobooks.interface_pacatge.books.GenreModel {


    @Override
    public Observable<ArrayList<GenrePOJO>> getBooks(String url, int page) {
        int size = 4;
        return Observable.create(observableEmitter -> {
            waitVpnConetion();
            ArrayList<GenrePOJO> articlesModels;
            try {
                if (url.contains(Url.SERVER_AKNIGA) && url.contains("ajax-search")) {
                    if (page > 1) {
                        throw new NullPointerException();
                    }
                    String qery = url.substring(url.indexOf("?q="));
                    qery = qery.replace("?q=", "");
                    String url1 = url.substring(0, url.indexOf("?q="));
                    articlesModels = searchAutorAbook(url1, qery);
                    observableEmitter.onNext(articlesModels);
                } else if (url.contains(String.valueOf(page))) {
                    for (int i = 1; i <= size; i++) {
                        int temp = (page - 1) * size + i;
                        if (url.contains(Url.SERVER)) {
                            articlesModels = loadBooksList(
                                    url.replace(String.valueOf(page), String.valueOf(temp)), temp);
                        } else if (url.contains(Url.SERVER_IZIBUK)) {
                            articlesModels = loadBooksListIzibuk(
                                    url.replace(String.valueOf(page), String.valueOf(temp)), temp);
                        } else if (url.contains(Url.SERVER_ABMP3)) {
                            articlesModels = loadBooksListABMP3(
                                    url.replace("?page=" + page + "/", "?page=" + temp), temp);
                        } else if (url.contains(Url.SERVER_AKNIGA)) {
                            articlesModels = loadBooksListAbook(
                                    url.replace("page" + page + "/", "page" + temp), temp);
                        } else {
                            articlesModels = new ArrayList<>();
                        }
                        observableEmitter.onNext(articlesModels);
                    }
                } else {
                    if (url.contains(Url.SERVER)) {
                        articlesModels = loadBooksList(url, page);
                    } else if (url.contains(Url.SERVER_IZIBUK)) {
                        articlesModels = loadBooksListIzibuk(url, page);
                    } else if (url.contains(Url.SERVER_ABMP3)) {
                        if (url.contains("search")) {
                            articlesModels = new AutorsSearchABMP3().getAutors(url, page);
                        } else {
                            articlesModels = loadBooksListABMP3(url + "?page=", page);
                        }
                    } else if (url.contains(Url.SERVER_AKNIGA)) {
                        articlesModels = loadBooksListAbook(url, page);
                    } else if (url.contains("baza-knig")) {
                        articlesModels = loadBooksListBazaKnig(url, page);
                    } else {
                        articlesModels = new ArrayList<>();
                    }
                    observableEmitter.onNext(articlesModels);
                }
            } catch (Exception e) {
                observableEmitter.onError(e);
            } finally {
                observableEmitter.onComplete();
            }
        });
    }

    protected ArrayList<GenrePOJO> loadBooksList(String url, int page) throws IOException {
        ArrayList<GenrePOJO> result = new ArrayList<>();
        Document doc = Jsoup.connect(url)
                .userAgent(Consts.USER_AGENT)
                .referrer("http://www.google.com")
                .sslSocketFactory(Consts.socketFactory())
                .maxBodySize(0)
                .get();

        Elements items = doc.getElementsByClass("genre2_item");
        for (Element item : items) {
            GenrePOJO genrePOJO = new GenrePOJO();
            Elements name = item.getElementsByClass("genre2_item_name");
            if (name.size() != 0) {
                genrePOJO.setUrl(Url.SERVER + name.first().attr("href") + "<page>/");
                genrePOJO.setName(name.first().text());
            }

            Elements rating = item.getElementsByClass("subscribe_btn_label_count");
            if (rating.size() != 0) {
                genrePOJO.setReting(Integer.parseInt(rating.first().text()));
            }

            Elements description = item.getElementsByClass("genre2_item_description");
            if (description.size() != 0) {
                genrePOJO.setDescription(description.first().text());
            }
            if (!genrePOJO.isNull()) {
                result.add(genrePOJO);
            }
        }
        return result;
    }

    protected ArrayList<GenrePOJO> loadBooksListABMP3(String url, int page) throws IOException {
        ArrayList<GenrePOJO> result = new ArrayList<>();
        Document doc = Jsoup.connect(url + page)
                .userAgent(Consts.USER_AGENT)
                .referrer("http://www.google.com")
                .sslSocketFactory(Consts.socketFactory())
                .maxBodySize(0)
                .get();

        Elements items = doc.getElementsByClass("b-posts");
        if (items != null && items.size() != 0) {
            Elements list = items.first().children();
            if (list != null && list.size() > 0) {
                for (Element item : list) {
                    GenrePOJO genrePOJO = new GenrePOJO();
                    Elements titles = item.getElementsByClass("title");
                    if (titles != null && titles.size() != 0) {
                        Elements aGenre = titles.first().getElementsByTag("a");
                        if (aGenre != null && aGenre.size() != 0) {
                            genrePOJO.setUrl(Url.SERVER_ABMP3 + aGenre.first().attr("href") + "?page=");
                            genrePOJO.setName(aGenre.first().text());
                        }
                    }
                    Elements ratings = item.getElementsByClass("rating");
                    if (ratings != null && ratings.size() != 0) {
                        genrePOJO.setDescription(ratings.first().text().trim().replace("кни", " кни"));
                    }

                    if (!genrePOJO.isNull()) {
                        result.add(genrePOJO);
                    }
                }
            }
        }
        return result;
    }

    protected ArrayList<GenrePOJO> loadBooksListAbook(String url, int page) throws IOException {
        ArrayList<GenrePOJO> result = new ArrayList<>();
        Document doc = Jsoup.connect(url)
                .userAgent(Consts.USER_AGENT)
                .referrer("http://www.google.com")
                .sslSocketFactory(Consts.socketFactory())
                .maxBodySize(0)
                .get();

        Elements items = doc.getElementsByClass("table-authors");
        if (items != null && items.size() != 0) {
            Elements tbody = items.first().getElementsByTag("tbody");
            if (tbody != null && tbody.size() != 0) {
                Elements list = tbody.first().children();
                if (list != null && list.size() > 0) {
                    for (Element item : list) {
                        GenrePOJO genrePOJO = new GenrePOJO();
                        Elements titles = item.getElementsByClass("name-obj");
                        if (titles != null && titles.size() != 0) {
                            Elements aGenre = titles.first().getElementsByTag("a");
                            if (aGenre != null && aGenre.size() != 0) {
                                genrePOJO.setUrl(aGenre.first().attr("href") + "page<page>/");
                                genrePOJO.setName(aGenre.first().text());
                            }
                        }
                        Elements description = item.getElementsByClass("description");
                        if (description != null && description.size() != 0) {
                            genrePOJO.setDescription(description.first().text().trim());
                        }

                        Elements reting = item.getElementsByClass("cell-rating");
                        if (reting != null && reting.size() != 0) {
                            genrePOJO.setReting(Integer.parseInt(reting.first().text().trim()));
                        }

                        if (!genrePOJO.isNull()) {
                            result.add(genrePOJO);
                        }
                    }
                }
            }
        }
        return result;
    }

    protected ArrayList<GenrePOJO> loadBooksListIzibuk(String url, int page) throws IOException {
        ArrayList<GenrePOJO> result = new ArrayList<>();
        Document doc = Jsoup.connect(url + page)
                .userAgent(Consts.USER_AGENT)
                .referrer("http://www.google.com")
                .sslSocketFactory(Consts.socketFactory())
                .maxBodySize(0)
                .get();

        Elements items = doc.getElementsByClass("_e181af");
        if (items != null && items.size() != 0) {
            Elements list = items.first().children();
            if (list != null) {
                for (Element item : list) {
                    GenrePOJO genrePOJO = new GenrePOJO();

                    String src = item.attr("href");
                    if (src != null) {
                        genrePOJO.setUrl(Url.SERVER_IZIBUK + src + "?p=");
                    }

                    Elements children = item.children();
                    if (children != null && children.size() == 2) {
                        String name = children.first().text();
                        if (name != null) {
                            genrePOJO.setName(name);
                        }

                        String desc = children.last().text();
                        if (desc != null) {
                            genrePOJO.setDescription(desc);
                        }
                    }

                    if (!genrePOJO.isNull()) {
                        result.add(genrePOJO);
                    }
                }
            }
        }
        return result;
    }

    private ArrayList<GenrePOJO> loadBooksListBazaKnig(final String url, final int page) throws IOException {
        ArrayList<GenrePOJO> result = new ArrayList<>();
        Connection connection = Jsoup.connect(url)
                .userAgent(Consts.USER_AGENT)
                .referrer("http://www.google.com")
                .maxBodySize(0)
                .sslSocketFactory(Consts.socketFactory());

        if (!Consts.getBazaKnigCookies().isEmpty()) {
            connection.cookie("PHPSESSID", Consts.getBazaKnigCookies());
        }

        Document doc = connection.get();

        if (doc.title().contains("Just a moment")) {
            throw new CookesExeption(url);
        }

        Elements items = doc.getElementsByClass("reset left-menu-items");
        if (items != null && items.size() != 0) {
            Elements list = items.first().getElementsByTag("li");
            if (list != null && list.size() > 0) {
                for (Element item : list) {
                    GenrePOJO genrePOJO = new GenrePOJO();
                    Elements aGenre = item.getElementsByTag("a");
                    if (aGenre != null && aGenre.size() != 0) {
                        genrePOJO.setUrl(Url.SERVER_BAZA_KNIG + aGenre.first().attr("href") + "page/");
                        genrePOJO.setName(item.text());
                    }
                    if (!genrePOJO.isNull()) {
                        result.add(genrePOJO);
                    }
                }
            }
        }
        return result;
    }

    private ArrayList<GenrePOJO> searchAutorAbook(String url, String qery) throws IOException {
        ArrayList<GenrePOJO> result = new ArrayList<>();
        Response response = Jsoup.connect(Url.SERVER_AKNIGA + "/authors/")
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

        String text = Jsoup.connect(url)
                .userAgent(Consts.USER_AGENT)
                .method(Method.POST)
                .referrer(Url.SERVER_AKNIGA + "/authors/")
                .sslSocketFactory(Consts.socketFactory())
                .data("security_ls_key", key)
                .data("sText", qery)
                .maxBodySize(0)
                .ignoreContentType(true)
                .cookies(cookies)
                .maxBodySize(0)
                .execute()
                .body();
        JsonElement json = JsonParser.parseString(text.replaceAll("\\n", ""));
        if (json.isJsonObject()) {
            JsonObject jsonObject = json.getAsJsonObject();
            String html = jsonObject.get("html").getAsString();
            doc = Jsoup.parse("<html><head></head><body><table>" + html + "</table></body></html>");
            Elements tr = doc.getElementsByTag("tr");
            if (tr != null && tr.size() != 0) {
                for (Element item : tr) {
                    GenrePOJO genrePOJO = new GenrePOJO();
                    Elements titles = item.getElementsByClass("name-obj");
                    if (titles != null && titles.size() != 0) {
                        Elements aGenre = titles.first().getElementsByTag("a");
                        if (aGenre != null && aGenre.size() != 0) {
                            genrePOJO.setUrl(aGenre.first().attr("href") + "/page<page>/");
                            genrePOJO.setName(aGenre.first().text());
                        }
                    }
                    Elements description = item.getElementsByClass("description");
                    if (description != null && description.size() != 0) {
                        genrePOJO.setDescription(description.first().text().trim());
                    }

                    Elements reting = item.getElementsByClass("cell-rating");
                    if (reting != null && reting.size() != 0) {
                        genrePOJO.setReting(Integer.parseInt(reting.first().text().trim()));
                    }

                    if (!genrePOJO.isNull()) {
                        result.add(genrePOJO);
                    }
                }
            }

        }

        return result;
    }


}
