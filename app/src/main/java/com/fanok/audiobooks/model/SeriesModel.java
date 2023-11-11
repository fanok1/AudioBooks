package com.fanok.audiobooks.model;




import static com.fanok.audiobooks.Consts.PROXY_HOST;
import static com.fanok.audiobooks.Consts.PROXY_PORT;

import androidx.annotation.NonNull;
import com.fanok.audiobooks.App;
import com.fanok.audiobooks.Consts;
import com.fanok.audiobooks.Url;
import com.fanok.audiobooks.pojo.SeriesPOJO;
import io.reactivex.Observable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Proxy.Type;
import java.util.ArrayList;
import java.util.Collections;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

public class SeriesModel implements
        com.fanok.audiobooks.interface_pacatge.book_content.SeriesModel {


    @Override
    public Observable<ArrayList<SeriesPOJO>> getSeries(@NonNull String url) {

        return Observable.create(observableEmitter -> {
            //waitVpnConetion();
            ArrayList<SeriesPOJO> articlesModels;
            try {
                if (url.contains(Url.SERVER)) {
                    articlesModels = loadSeriesList(url);
                } else if (url.contains(Url.SERVER_IZIBUK)) {
                    articlesModels = loadSeriesListIzibuk(url);
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

    private ArrayList<SeriesPOJO> loadSeriesList(String url) throws IOException {
        ArrayList<SeriesPOJO> result = new ArrayList<>();
        Document doc = Jsoup.connect(url)
                .userAgent(Consts.USER_AGENT)
                .referrer("http://www.google.com")
                .sslSocketFactory(Consts.socketFactory())
                .maxBodySize(0)
                .get();

        Elements seriesConteiner = doc.getElementsByClass("book_blue_block book_serie_block");
        for (int i = 0; i < seriesConteiner.size(); i++) {
            Elements elementsTitle = seriesConteiner.get(i).getElementsByClass(
                    "book_serie_block_title");
            if (elementsTitle.size() != 0 && elementsTitle.first().text().contains("Цикл")) {
                Elements series = seriesConteiner.get(i).getElementsByClass(
                        "book_serie_block_item");
                for (Element item : series) {
                    SeriesPOJO seriesPOJO = new SeriesPOJO();
                    Elements indexElements = item.getElementsByClass("book_serie_block_item_index");
                    if (indexElements.size() != 0) {
                        seriesPOJO.setNumber(indexElements.first().text());
                    }

                    Elements strongElements = item.getElementsByTag("strong");
                    if (strongElements.size() != 0) {
                        seriesPOJO.setName(strongElements.first().text());
                    }

                    Elements aElements = item.getElementsByTag("a");
                    if (aElements.size() != 0) {
                        seriesPOJO.setName(aElements.first().text());
                        seriesPOJO.setUrl(Url.SERVER + aElements.first().attr("href"));
                    }

                    result.add(seriesPOJO);
                }
            }
        }

        return result;
    }

    private ArrayList<SeriesPOJO> loadSeriesListAbook(String url) throws IOException {
        ArrayList<SeriesPOJO> result = new ArrayList<>();
        Document doc = Jsoup.connect(url)
                .userAgent(Consts.USER_AGENT)
                .referrer("http://www.google.com")
                .sslSocketFactory(Consts.socketFactory())
                .maxBodySize(0)
                .get();

        Elements parents = doc.getElementsByClass("content__main__book--item--series-list");
        if (parents != null && parents.size() != 0) {
            Elements series = parents.first().children();
            if (series != null) {
                for (Element serie : series) {
                    SeriesPOJO seriesPOJO = new SeriesPOJO();
                    Elements namberConteiner = serie.getElementsByTag("b");
                    if (namberConteiner != null && namberConteiner.size() != 0) {
                        String number = namberConteiner.first().text();
                        if (number != null) {
                            seriesPOJO.setNumber(number);
                        }
                    }

                    String href = serie.attr("href");
                    if (href != null && !href.isEmpty()) {
                        seriesPOJO.setUrl(href);
                    }
                    Elements stringTag = serie.getElementsByClass("caption");
                    if (stringTag != null && stringTag.size() != 0) {
                        String text = stringTag.first().text();
                        if (text != null && !text.isEmpty()) {
                            seriesPOJO.setName(text);
                        }
                    }
                    result.add(seriesPOJO);
                }
            }
        }

        return result;
    }

    private ArrayList<SeriesPOJO> loadSeriesListBazaKnig(String url) throws IOException {

        Connection connection = Jsoup.connect(url)
                .userAgent(Consts.USER_AGENT)
                .referrer("http://www.google.com")
                .sslSocketFactory(Consts.socketFactory())
                .maxBodySize(0)
                .ignoreHttpErrors(true);


        if(App.useProxy) {
            Proxy proxy = new Proxy(Type.SOCKS,
                    new InetSocketAddress(PROXY_HOST, PROXY_PORT));
            connection.proxy(proxy);
        }

        if (!Consts.getBazaKnigCookies().isEmpty()) {
            connection.cookie("PHPSESSID", Consts.getBazaKnigCookies());
        }

        Document document = connection.get();
        String urlSeries = "";

        Elements conteiners = document.getElementsByClass("reset full-items");
        if (conteiners != null && conteiners.size() > 0) {
            Elements liElem = conteiners.first().getElementsByTag("li");
            if (liElem != null && liElem.size() > 0) {
                for (Element li : liElem) {
                    String liText = li.text();
                    if (liText != null) {
                        if (liText.contains("Цикл")) {
                            Elements seriesConteiner = li.getElementsByTag("a");
                            if (seriesConteiner != null && seriesConteiner.size() > 0) {
                                Element a = seriesConteiner.first();
                                String href = a.attr("href");
                                if (href != null) {
                                    urlSeries = href;
                                }
                            }
                        }
                    }
                }
            }
        }

        ArrayList<SeriesPOJO> result = new ArrayList<>();
        int page = 0;
        String bookName = "";
        while (true) {
            page++;
            Document doc = Jsoup.connect(urlSeries + "/page/" + page + "/")
                    .userAgent(Consts.USER_AGENT)
                    .referrer("http://www.google.com")
                    .sslSocketFactory(Consts.socketFactory())
                    .ignoreHttpErrors(true)
                    .maxBodySize(0)
                    .get();

            Elements navConteiner = doc.getElementsByClass("page-nav");
            if (page > 1) {
                if (navConteiner != null && navConteiner.size() > 0) {
                    Elements navigainClass = navConteiner.first().getElementsByClass("navigation");
                    if (navigainClass != null && navigainClass.size() > 0) {
                        Elements children = navigainClass.first().children();
                        if (children != null && children.size() > 0) {
                            int last = Integer.parseInt(children.last().text());
                            if (page > last) {
                                break;
                            }
                        } else {
                            break;
                        }
                    } else {
                        break;
                    }
                } else {
                    break;
                }
            }

            Element conteiner = doc.getElementById("dle-content");
            if (conteiner != null) {
                Elements bookList = conteiner.getElementsByClass("short");
                if (bookList != null && bookList.size() > 0) {
                    for (Element book : bookList) {
                        SeriesPOJO seriesPOJO = new SeriesPOJO();
                        Elements imgConteiner = book.getElementsByClass("short-img");
                        if (imgConteiner != null && imgConteiner.size() > 0) {
                            Element element = imgConteiner.first();
                            Elements aElement = element.getElementsByTag("a");
                            if (aElement != null && aElement.size() > 0) {
                                String aHref = aElement.first().attr("href");
                                if (aHref != null && !aHref.isEmpty()) {
                                    seriesPOJO.setUrl(aHref);
                                } else {
                                    continue;
                                }
                            }
                        }

                        Elements nameConteiner = book.getElementsByClass("short-title");
                        if (nameConteiner != null && nameConteiner.size() > 0) {
                            Elements aTag = nameConteiner.first().getElementsByTag("a");
                            if (aTag != null && aTag.size() > 0) {
                                String name = aTag.first().ownText();
                                if (name != null && !name.isEmpty()) {
                                    bookName = name;
                                } else {
                                    continue;
                                }
                                Elements b = aTag.first().getElementsByTag("b");
                                if (b != null && b.size() != 0) {
                                    String number = b.first().text();
                                    if (number != null && !number.isEmpty()) {
                                        seriesPOJO.setNumber(number);
                                    }
                                }
                            }
                        }

                        Elements cont = book.getElementsByClass("reset short-items");
                        if (cont != null && cont.size() > 0) {
                            Elements liElem = cont.first().getElementsByTag("li");
                            if (liElem != null && liElem.size() > 0) {
                                for (Element li : liElem) {
                                    String liText = li.text();
                                    if (liText != null) {
                                        if (liText.contains("Читает")) {
                                            seriesPOJO.setName(bookName + " " + liText);
                                        }
                                    }
                                }
                            }
                        }

                        result.add(seriesPOJO);
                    }

                }
            }
            if (result.size() == 0) {
                throw new NullPointerException();
            }
        }

        Collections.reverse(result);
        return result;
    }

    private ArrayList<SeriesPOJO> loadSeriesListIzibuk(String url) throws IOException {
        ArrayList<SeriesPOJO> result = new ArrayList<>();
        Connection connection = Jsoup.connect(url)
                .userAgent(Consts.USER_AGENT)
                .referrer("http://www.google.com")
                .sslSocketFactory(Consts.socketFactory())
                .maxBodySize(0);


        if(App.useProxy) {
            Proxy proxy = new Proxy(Type.SOCKS,
                    new InetSocketAddress(PROXY_HOST, PROXY_PORT));
            connection.proxy(proxy);
        }

        Document doc = connection.get();

        Elements parents = doc.getElementsByClass("_b264b2 _49d1b4");
        if (parents != null && parents.size() != 0) {
            Elements series = parents.first().getElementsByClass("_f61db9");
            if (series != null) {
                for (Element serie : series) {
                    SeriesPOJO seriesPOJO = new SeriesPOJO();
                    Elements namberConteiner = serie.getElementsByClass("_bb8bca");
                    if (namberConteiner != null && namberConteiner.size() != 0) {
                        String number = namberConteiner.first().text();
                        if (number != null) {
                            seriesPOJO.setNumber(number);
                        }
                    }
                    Elements aTag = serie.getElementsByTag("a");
                    if (aTag != null && aTag.size() != 0) {
                        Element a = aTag.first();
                        String href = a.attr("href");
                        if (href != null) {
                            seriesPOJO.setUrl(Url.SERVER_IZIBUK + href);
                        }
                        String text = serie.text();
                        if (text != null) {
                            seriesPOJO.setName(text.replace(seriesPOJO.getNumber(), ""));
                        }
                    } else {
                        Elements stringTag = serie.getElementsByTag("strong");
                        if (stringTag != null && stringTag.size() != 0) {
                            String text = stringTag.first().text();
                            if (text != null) {
                                seriesPOJO.setName(text);
                            }
                        }
                    }
                    result.add(seriesPOJO);
                }
            }
        }

        return result;
    }
}
