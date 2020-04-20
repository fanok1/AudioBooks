package com.fanok.audiobooks.model;


import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.fanok.audiobooks.Consts;
import com.fanok.audiobooks.Url;
import com.fanok.audiobooks.pojo.BookPOJO;
import com.fanok.audiobooks.pojo.OtherArtistPOJO;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.ArrayList;

import io.reactivex.Observable;

public class OtherSourceModel implements
        com.fanok.audiobooks.interface_pacatge.book_content.OtherArtistModel {


    @Nullable
    private OtherArtistPOJO getKnigaVuhe(BookPOJO bookPOJO) throws IOException {


        Document doc = Jsoup.connect("https://knigavuhe.org/search/?q=" + bookPOJO.getName()
                + " " + bookPOJO.getAutor())
                .userAgent(Consts.USER_AGENT)
                .referrer("http://www.google.com")
                .sslSocketFactory(Consts.socketFactory())
                .get();

        Element bookList = doc.getElementById("books_updates_list");
        if (bookList == null) bookList = doc.getElementById("books_list");
        if (bookList != null) {
            Elements books = bookList.getElementsByClass("bookkitem");
            if (books.size() == 0) return null;
            for (Element book : books) {

                String title = "";
                String author = "";
                String reader = "";
                String url = "";

                Elements litRes = book.getElementsByClass("bookkitem_litres_icon");
                if (litRes.size() != 0) continue;
                Elements aTags = book.getElementsByClass("bookkitem_name");
                if (aTags.size() != 0) {
                    Element a = aTags.first().child(0);
                    if (a != null) {
                        url = Url.SERVER + a.attr("href");
                        title = a.text();
                    }
                }

                Elements autorConteiner = book.getElementsByClass("bookkitem_author");
                if (autorConteiner.size() != 0) {
                    Elements aAutor = autorConteiner.first().getElementsByTag("a");
                    if (aAutor.size() != 0) {
                        author = aAutor.first().text();
                    }
                }


                Elements artistConteiner = book.getElementsByClass("bookkitem_icon -reader");
                if (artistConteiner.size() != 0) {
                    Element parent = artistConteiner.first().parent();
                    Elements artist = parent.getElementsByTag("a");
                    if (artist.size() != 0) {
                        reader = artist.first().text();
                    }
                }


                if (!title.isEmpty() && !url.isEmpty() &&
                        title.toLowerCase().equals(bookPOJO.getName().toLowerCase()) &&
                        author.toLowerCase().equals(bookPOJO.getAutor().toLowerCase()) &&
                        reader.toLowerCase().equals(bookPOJO.getArtist().toLowerCase())) {

                    OtherArtistPOJO otherArtistPOJO = new OtherArtistPOJO();
                    otherArtistPOJO.setName("knigavuhe.org");
                    otherArtistPOJO.setUrl(url);
                    return otherArtistPOJO;
                }
            }
        }

        return null;

    }

    @Nullable
    private OtherArtistPOJO getIzibuk(BookPOJO bookPOJO) throws IOException {

        Document doc = Jsoup.connect("https://izibuk.ru/search?q=" + bookPOJO.getName() + " "
                + bookPOJO.getAutor() + " " + bookPOJO.getArtist())
                .userAgent(Consts.USER_AGENT)
                .referrer("http://www.google.com")
                .sslSocketFactory(Consts.socketFactory())
                .get();

        Element listParent = doc.getElementById("books_list");
        if (listParent == null) {
            listParent = doc.getElementById("books_updates_list");
        }
        if (listParent != null) {
            Elements listElements = listParent.getElementsByClass("_ccb9b7");
            if (listElements != null && listElements.size() != 0) {
                for (Element book : listElements) {

                    String url = "";
                    String titleResulr = "";
                    String author = "";
                    String reader = "";

                    Elements contentlist = book.getElementsByClass("_802db0");
                    if (contentlist != null && contentlist.size() != 0) {
                        Element content = contentlist.first();
                        if (content != null) {
                            Element titleParent = content.child(1);
                            if (titleParent != null) {
                                Element title = titleParent.child(0);
                                if (title != null) {
                                    String bookUrl = title.attr("href");
                                    if (bookUrl != null) {
                                        url = Url.SERVER_IZIBUK + bookUrl;
                                    }
                                    String name = title.text();
                                    if (name != null) {
                                        titleResulr = name;
                                    }
                                }
                            }
                            Element child = content.child(2);
                            if (child != null) {
                                Elements elements = child.children();
                                if (elements != null) {
                                    for (Element element : elements) {
                                        Element conteinter = element.child(1);
                                        if (conteinter != null) {
                                            String href = conteinter.attr("href");
                                            if (href != null) {
                                                String name = conteinter.text();
                                                if (href.contains("author")) {
                                                    if (name != null) {
                                                        author = name;
                                                    }
                                                } else if (href.contains("reader")) {
                                                    if (name != null) {
                                                        reader = name;
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (!titleResulr.isEmpty() && !url.isEmpty() &&
                            titleResulr.toLowerCase().equals(bookPOJO.getName().toLowerCase()) &&
                            author.toLowerCase().equals(bookPOJO.getAutor().toLowerCase()) &&
                            reader.toLowerCase().equals(bookPOJO.getArtist().toLowerCase())) {

                        OtherArtistPOJO otherArtistPOJO = new OtherArtistPOJO();
                        otherArtistPOJO.setName("izibuk.ru");
                        otherArtistPOJO.setUrl(url);
                        return otherArtistPOJO;
                    }

                }

            }
        }

        return null;
    }


    @Override
    public Observable<ArrayList<OtherArtistPOJO>> getOtherArtist(@NonNull BookPOJO bookPOJO) {

        return Observable.create(observableEmitter -> {
            ArrayList<OtherArtistPOJO> articlesModels = new ArrayList<>();
            try {
                if (!bookPOJO.getUrl().contains("knigavuhe.org")) {
                    OtherArtistPOJO artistPOJO = getKnigaVuhe(bookPOJO);
                    if (artistPOJO != null) articlesModels.add(artistPOJO);
                } else if (!bookPOJO.getUrl().contains("izibuk.ru")) {
                    OtherArtistPOJO artistPOJO = getIzibuk(bookPOJO);
                    if (artistPOJO != null) articlesModels.add(artistPOJO);
                }
                observableEmitter.onNext(articlesModels);
            } catch (Exception e) {
                observableEmitter.onError(e);
            } finally {
                observableEmitter.onComplete();
            }
        });
    }
}
