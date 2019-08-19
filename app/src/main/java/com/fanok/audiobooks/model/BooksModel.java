package com.fanok.audiobooks.model;

import com.fanok.audiobooks.Url;
import com.fanok.audiobooks.pojo.BookPOJO;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.ArrayList;

import io.reactivex.Observable;

public class BooksModel implements com.fanok.audiobooks.interface_pacatge.books.BooksModel {


    private ArrayList<BookPOJO> loadBooksList(String url, int page) throws IOException {
        ArrayList<BookPOJO> result = new ArrayList<>();
        String autor = "";
        String autorUrl = "";
        Document doc = Jsoup.connect(url)
                .userAgent(
                        "Mozilla / 5.0 (Windows NT 10.0; Win64; x64) AppleWebKit / 537.36 (KHTML,"
                                + " как Gecko) Chrome / 60.0.3112.78 Safari / 537.36")
                .referrer("http://www.google.com")
                .get();


        Elements pagesConteiner = doc.getElementsByClass("pn_page_buttons");
        if (pagesConteiner.size() != 0) {
            Elements pagesElements = pagesConteiner.first().children();
            if (pagesElements.size() != 0) {
                Element lastPageElement = pagesElements.last();
                int lastPage = Integer.parseInt(lastPageElement.text());
                if (lastPage < page) throw new NullPointerException();
            }
        } else if (page > 1) throw new NullPointerException();

        Elements pageTitle = doc.getElementsByClass("page_title");
        if (pageTitle.size() != 0) {
            if (pageTitle.first().text().contains("Цикл")) {
                Elements aList = pageTitle.first().getElementsByTag("a");
                if (aList.size() != 0) {
                    autor = aList.first().text();
                    autorUrl = Url.SERVER + aList.first().attr("href");
                }
            } else if (pageTitle.first().text().contains("Все авторы")) {
                autorUrl = "";
                Elements h1 = pageTitle.first().getElementsByTag("h1");
                if (h1.size() != 0) {
                    autor = h1.first().text();
                } else {
                    autor = "";
                }
            }
        }
        Element bookList = doc.getElementById("books_updates_list");
        if (bookList == null) bookList = doc.getElementById("books_list");
        if (bookList != null) {
            Elements books = bookList.getElementsByClass("bookitem");
            if (books.size() == 0) return null;
            for (Element book : books) {
                Elements litRes = book.getElementsByClass("bookitem_litres_icon");
                if (litRes.size() != 0) continue;
                BookPOJO bookPOJO = new BookPOJO();
                String img = book.getElementsByTag("img").get(0).attr("src");
                if (img != null) bookPOJO.setPhoto(img);
                Elements aTags = book.getElementsByClass("bookitem_name");
                if (aTags.size() != 0) {
                    Element a = aTags.first().child(0);
                    if (a != null) {
                        bookPOJO.setUrl(Url.SERVER + a.attr("href"));
                        bookPOJO.setName(a.text());
                    }
                }

                Element aGenre = book.getElementsByClass("bookitem_genre").first().child(0);
                if (aGenre != null) {
                    bookPOJO.setUrlGenre(Url.SERVER + aGenre.attr("href"));
                    bookPOJO.setGenre(aGenre.text());
                }

                Elements ratingConteiner = book.getElementsByClass("bookitem_icon -views");
                if (ratingConteiner.size() != 0) {
                    bookPOJO.setReting(ratingConteiner.first().nextElementSibling().text());
                }

                Elements comentsConteiner = book.getElementsByClass("bookitem_icon -comments");
                if (comentsConteiner.size() != 0) {
                    bookPOJO.setComents(comentsConteiner.first().nextElementSibling().text());
                }

                Elements autorConteiner = book.getElementsByClass("bookitem_author");
                if (autorConteiner.size() != 0) {
                    Elements aAutor = autorConteiner.first().getElementsByTag("a");
                    if (aAutor.size() != 0) {
                        bookPOJO.setAutor(aAutor.first().text());
                        bookPOJO.setUrlAutor(Url.SERVER + aAutor.first().attr("href"));
                    }
                } else {
                    if (!autor.isEmpty()) {
                        bookPOJO.setAutor(autor);
                    }
                    if (!autorUrl.isEmpty()) {
                        bookPOJO.setUrlAutor(autorUrl);
                    }
                }


                Elements artistConteiner = book.getElementsByClass("bookitem_icon -reader");
                if (artistConteiner.size() != 0) {
                    Element parent = artistConteiner.first().parent();
                    Elements artist = parent.getElementsByTag("a");
                    if (artist.size() != 0) {
                        bookPOJO.setArtist(artist.first().text());
                        bookPOJO.setUrlArtist(Url.SERVER + artist.first().attr("href"));
                    }
                }

                Elements timeConteinr = book.getElementsByClass("bookitem_meta_time");
                if (timeConteinr.size() != 0) {
                    bookPOJO.setTime(timeConteinr.first().text());
                }


                Elements seriesConteiner = book.getElementsByClass("bookitem_icon -serie");
                if (seriesConteiner.size() != 0) {
                    Element parent = seriesConteiner.first().parent();
                    Elements series = parent.getElementsByTag("a");
                    if (series.size() != 0) {
                        bookPOJO.setSeries(series.first().text());
                        bookPOJO.setUrlSeries(Url.SERVER + series.first().attr("href"));
                    }
                }

                if (bookPOJO.isNull()) continue;
                result.add(bookPOJO);
            }
        }
        return result;
    }

    @Override
    public Observable<ArrayList<BookPOJO>> getBooks(String url, int page) {
        return Observable.create(observableEmitter -> {
            ArrayList<BookPOJO> articlesModels;
            try {
                articlesModels = loadBooksList(url, page);
                observableEmitter.onNext(articlesModels);
            } catch (Exception e) {
                observableEmitter.onError(e);
            } finally {
                observableEmitter.onComplete();
            }
        });
    }
}
