package com.fanok.audiobooks.model;

import com.fanok.audiobooks.pojo.BookPOJO;
import com.fanok.audiobooks.presenter.BooksPresenter;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.ArrayList;

import io.reactivex.Observable;

public class BooksModel implements com.fanok.audiobooks.interface_pacatge.books.BooksModel {


    ArrayList<BookPOJO> loadBooksList(String url) throws IOException {
        ArrayList<BookPOJO> result = new ArrayList<>();
        Document doc = Jsoup.connect(url)
                .userAgent(
                        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_12_6) AppleWebKit/537.36 "
                                + "(KHTML, like Gecko) Chrome/61.0.3163.100 Safari/537.36")
                .referrer("http://www.google.com")
                .get();
        Elements books = doc.getElementsByTag("article");
        if (books.size() == 0) BooksPresenter.isEnd = true;
        for (Element book : books) {
            BookPOJO bookPOJO = new BookPOJO();
            String img = book.getElementsByTag("img").get(0).attr("src");
            if (img != null) bookPOJO.setPhoto(img);
            Element a = book.getElementsByClass("ls-topic-title").first().child(0);
            if (a != null) {
                bookPOJO.setUrl(a.attr("href"));
                bookPOJO.setName(a.text());
            } else {
                continue;
            }
            Elements topLineElements = book.getElementsByClass("ls-topic-info");
            if (topLineElements.size() != 0) {
                Element topLine = topLineElements.first();
                Elements genreConteiner = topLine.getElementsByClass("topic-blog");
                if (genreConteiner.size() != 0) {
                    Element genre = genreConteiner.first();
                    if (genre != null) {
                        genre = genre.child(0);
                        if (genre != null) {
                            bookPOJO.setGenre(genre.text());
                            bookPOJO.setUrlGenre(genre.attr("href"));
                        }
                    }
                }

                Elements retingConteiner = topLine.getElementsByClass("favourite-count");
                if (retingConteiner.size() != 0) {
                    String reting = retingConteiner.first().text();
                    if (reting != null) bookPOJO.setReting(reting);
                }
                Elements favoriteConteiner = book.getElementsByClass("ls-favourite-count");
                if (favoriteConteiner.size() != 0) {
                    String favorite = favoriteConteiner.first().text();
                    if (favorite != null && favorite.matches("^\\d+$")) {
                        bookPOJO.setFavorite(Integer.parseInt(favorite));
                    }
                }

                Elements comentsConteiner = topLine.getElementsByClass("fa-comments-o");
                try {
                    if (comentsConteiner.size() != 0) {
                        Element comentsConteinerChild = comentsConteiner.first().parent();
                        if (comentsConteinerChild != null) {
                            String countComents = comentsConteinerChild.text();
                            if (countComents != null) {
                                bookPOJO.setComents(countComents);
                            }
                        }
                    }
                } catch (NullPointerException e) {
                    bookPOJO.setComents("0");
                }

            }
            Elements bottomLineElements = book.getElementsByClass("topic-a-info");
            if (bottomLineElements.size() != 0) {
                Element bottomLine = bottomLineElements.first();
                Elements autorElements = bottomLine.getElementsByClass("fa-user");
                if (autorElements.size() == 0) {
                    autorElements = bottomLine.getElementsByClass(
                            "fa-users");
                }
                if (autorElements.size() != 0) {
                    Element autor = autorElements.first();
                    Element aElement = autor.nextElementSibling();
                    String autorName = aElement.text();
                    String autorUrl = aElement.attr("href");
                    if (autorName != null && autorUrl != null) {
                        bookPOJO.setAutor(autorName);
                        bookPOJO.setUrlAutor(autorUrl);
                    }
                }

                Elements artistElements = bottomLine.getElementsByClass("fa-microphone");
                if (artistElements.size() != 0) {
                    Element artist = artistElements.first();
                    Element aElement = artist.nextElementSibling();
                    String artistName = aElement.text();
                    String artistUrl = aElement.attr("href");
                    if (artistName != null && artistUrl != null) {
                        bookPOJO.setArtist(artistName);
                        bookPOJO.setUrlArtist(artistUrl);
                    }
                }

                Elements seriesConteiner = bottomLine.getElementsByClass("fa-book");
                if (seriesConteiner.size() != 0) {
                    Element element = seriesConteiner.first();
                    if (element != null) {
                        Element aElement = element.nextElementSibling();
                        String urlSeries = aElement.attr("href");
                        String nameSeries = aElement.text();
                        if (urlSeries != null && nameSeries != null) {
                            bookPOJO.setSeries(nameSeries);
                            bookPOJO.setUrlSeries(urlSeries);
                        }
                    }
                }

                Elements hoursEl = bottomLine.getElementsByClass("hours");
                String hours = "";
                if (hoursEl.size() != 0) {
                    hours = hoursEl.first().text();
                }
                Elements minetsEl = bottomLine.getElementsByClass("minutes");
                String minets = "";
                if (minetsEl.size() != 0) {
                    minets = minetsEl.first().text();
                }
                if (!hours.isEmpty() || !minets.isEmpty()) {
                    String temp = hours + " " + minets;
                    bookPOJO.setTime(temp.trim());
                }
            }
            if (bookPOJO.isNull()) continue;
            result.add(bookPOJO);
        }
        return result;
    }

    @Override
    public Observable<ArrayList<BookPOJO>> getBooks(String url) {
        return Observable.create(observableEmitter -> {
            ArrayList<BookPOJO> articlesModels;
            try {
                articlesModels = loadBooksList(url);
                observableEmitter.onNext(articlesModels);
            } catch (Exception e) {
                observableEmitter.onError(e);
            } finally {
                observableEmitter.onComplete();
            }
        });
    }
}
