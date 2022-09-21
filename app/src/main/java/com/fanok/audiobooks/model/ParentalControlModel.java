package com.fanok.audiobooks.model;


import static de.blinkt.openvpn.core.VpnStatus.waitVpnConetion;

import com.fanok.audiobooks.Consts;
import com.fanok.audiobooks.Url;
import com.fanok.audiobooks.pojo.ParentControlPOJO;
import io.reactivex.Observable;
import java.io.IOException;
import java.util.ArrayList;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

public class ParentalControlModel {

    public Observable<ArrayList<ParentControlPOJO>> getBooks() {
        return Observable.create(observableEmitter -> {
            waitVpnConetion();
            ArrayList<ParentControlPOJO> arrayList = new ArrayList<>();

            try {
                arrayList.addAll(loadBooksListAkniga());
            } catch (Exception ignored) {
            }

            try {
                arrayList.addAll(loadBooksList());
            } catch (Exception ignored) {
            }

            try {
                arrayList.addAll(loadBooksListIziBuk());
            } catch (Exception ignored) {
            }

            try {
                arrayList.addAll(loadBooksListABMP3());
            } catch (Exception ignored) {
            }

            try {
                arrayList.addAll(loadBooksListBazaKnig());
            } catch (Exception ignored) {
            }

            observableEmitter.onNext(arrayList);
            observableEmitter.onComplete();
        });
    }

    ArrayList<ParentControlPOJO> loadBooksList() throws IOException {
        ArrayList<ParentControlPOJO> result = new ArrayList<>();
        Document doc = Jsoup.connect(Url.SECTIONS)
                .userAgent(Consts.USER_AGENT)
                .referrer("http://www.google.com")
                .sslSocketFactory(Consts.socketFactory())
                .get();

        Elements items = doc.getElementsByClass("genre2_item");
        for (Element item : items) {
            Elements name = item.getElementsByClass("genre2_item_name");
            if (name.size() != 0) {
                String href = Url.SERVER + name.first().attr("href") + "<page>/";
                String nameValue = name.first().text();
                result.add(new ParentControlPOJO("Книга в ухе", nameValue, href));
            }
        }

        return result;
    }

    ArrayList<ParentControlPOJO> loadBooksListABMP3() throws IOException {
        ArrayList<ParentControlPOJO> result = new ArrayList<>();
        Document doc = Jsoup.connect(Url.SECTIONS_ABMP3)
                .userAgent(Consts.USER_AGENT)
                .referrer("http://www.google.com")
                .sslSocketFactory(Consts.socketFactory())
                .get();

        Elements items = doc.getElementsByClass("b-posts");
        if (items != null && items.size() != 0) {
            Elements list = items.first().children();
            if (list != null && list.size() > 0) {
                for (Element item : list) {
                    String name = "";
                    String url = "";
                    Elements titles = item.getElementsByClass("title");
                    if (titles != null && titles.size() != 0) {
                        Elements aGenre = titles.first().getElementsByTag("a");
                        if (aGenre != null && aGenre.size() != 0) {
                            url = Url.SERVER_ABMP3 + aGenre.first().attr("href") + "?page=";
                            name = aGenre.first().text();
                        }
                    }
                    if (!url.isEmpty() && !name.isEmpty()) {
                        result.add(new ParentControlPOJO("audiobook-mp3.com", name, url));
                    }
                }
            }
        }
        return result;
    }

    ArrayList<ParentControlPOJO> loadBooksListAkniga() throws IOException {
        ArrayList<ParentControlPOJO> result = new ArrayList<>();
        Document doc = Jsoup.connect(Url.SECTIONS_AKNIGA)
                .userAgent(Consts.USER_AGENT)
                .referrer("http://www.google.com")
                .sslSocketFactory(Consts.socketFactory())
                .get();

        Elements items = doc.getElementsByClass("table-authors");
        if (items != null && items.size() != 0) {
            Elements tbody = items.first().getElementsByTag("tbody");
            if (tbody != null && tbody.size() != 0) {
                Elements list = tbody.first().children();
                if (list != null && list.size() > 0) {
                    for (Element item : list) {
                        String url = "";
                        String name = "";
                        Elements titles = item.getElementsByClass("name-obj");
                        if (titles != null && titles.size() != 0) {
                            Elements aGenre = titles.first().getElementsByTag("a");
                            if (aGenre != null && aGenre.size() != 0) {
                                url = aGenre.first().attr("href") + "page<page>/";
                                name = aGenre.first().text();
                            }
                        }

                        if (!url.isEmpty() && !name.isEmpty()) {
                            result.add(new ParentControlPOJO("Akniga", name, url));
                        }
                    }
                }
            }
        }
        return result;
    }

    ArrayList<ParentControlPOJO> loadBooksListBazaKnig() throws IOException {
        ArrayList<ParentControlPOJO> result = new ArrayList<>();
        Connection connection = Jsoup.connect(Url.SECTIONS_BAZA_KNIG)
                .userAgent(Consts.USER_AGENT)
                .referrer("http://www.google.com")
                .sslSocketFactory(Consts.socketFactory());

        if (!Consts.getBazaKnigCookies().isEmpty()) {
            connection.cookie("PHPSESSID", Consts.getBazaKnigCookies());
        }

        Document doc = connection.get();

        Elements items = doc.getElementsByClass("reset left-menu-items");
        if (items != null && items.size() != 0) {
            Elements list = items.first().getElementsByTag("li");
            if (list != null && list.size() > 0) {
                for (Element item : list) {
                    String name = "";
                    String url = "";
                    Elements aGenre = item.getElementsByTag("a");
                    if (aGenre != null && aGenre.size() != 0) {
                        url = Url.SERVER_BAZA_KNIG + aGenre.first().attr("href") + "page/";
                        name = item.text();
                    }
                    if (!name.isEmpty() && !url.isEmpty()) {
                        result.add(new ParentControlPOJO("База Книг", name, url));
                    }
                }
            }
        }
        return result;
    }

    ArrayList<ParentControlPOJO> loadBooksListIziBuk() throws IOException {
        ArrayList<ParentControlPOJO> result = new ArrayList<>();
        Document doc = Jsoup.connect(Url.SECTIONS_IZIBUK + 1)
                .userAgent(Consts.USER_AGENT)
                .referrer("http://www.google.com")
                .sslSocketFactory(Consts.socketFactory())
                .get();

        Elements items = doc.getElementsByClass("_e181af");
        if (items != null && items.size() != 0) {
            Elements list = items.first().children();
            if (list != null) {
                for (Element item : list) {
                    String nameValue = "";
                    String url = "";
                    String src = item.attr("href");
                    if (src != null) {
                        url = Url.SERVER_IZIBUK + src + "?p=";
                    }

                    Elements children = item.children();
                    if (children != null && children.size() == 2) {
                        String name = children.first().text();
                        if (name != null) {
                            nameValue = name;
                        }
                    }

                    if (!nameValue.isEmpty() && !url.isEmpty()) {
                        result.add(new ParentControlPOJO("izibuk", nameValue, url));
                    }
                }
            }
        }
        return result;
    }
}
