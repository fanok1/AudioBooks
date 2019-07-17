package com.fanok.audiobooks.model;

import com.fanok.audiobooks.interface_pacatge.book_content.DescriptionModel;
import com.fanok.audiobooks.pojo.BookPOJO;
import com.fanok.audiobooks.pojo.DescriptionPOJO;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.ArrayList;

import io.reactivex.Observable;

public class BookDescriptionModel implements DescriptionModel {

    private String mUrl;
    private Document mDocument;

    public BookDescriptionModel(String url) {
        mUrl = url;
    }

    private Document getDocument() {
        return mDocument;
    }

    private void setDocument() throws IOException {
        mDocument = Jsoup.connect(mUrl)
                .userAgent(
                        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_12_6) AppleWebKit/537.36 "
                                + "(KHTML, like Gecko) Chrome/61.0.3163.100 Safari/537.36")
                .referrer("http://www.google.com")
                .get();
    }

    private DescriptionPOJO loadDescription() throws IOException {
        DescriptionPOJO descriptionPOJO = new DescriptionPOJO();
        if (getDocument() == null) setDocument();
        Document document = getDocument();
        Elements titleElement = document.getElementsByClass("ls-topic-title");
        if (titleElement.size() != 0) {
            descriptionPOJO.setTitle(titleElement.first().text().trim());
        }
        Elements retingElements = document.getElementsByClass("ls-vote-big-rating");
        if (retingElements.size() != 0) {
            descriptionPOJO.setReiting(Integer.parseInt(retingElements.first().text()));
        }
        Elements posterElements = document.getElementsByClass("picture-side ");
        if (posterElements.size() != 0) {
            Elements img = posterElements.first().getElementsByTag("img");
            if (img.size() != 0) {
                descriptionPOJO.setPoster(img.first().attr("src"));
            }
        }

        Elements yearsElements = document.getElementsByAttributeValue("itemprop", "datePublished");
        if (yearsElements.size() != 0) {
            descriptionPOJO.setYear(Integer.parseInt(yearsElements.first().text()));
        }

        Elements hoursElements = document.getElementsByClass("hours");
        Elements minutesElements = document.getElementsByClass("minutes");

        String hours = "";
        String minutes = "";

        if (hoursElements.size() != 0) {
            hours = hoursElements.first().text();
        }

        if (minutesElements.size() != 0) {
            minutes = minutesElements.first().text();
        }
        descriptionPOJO.setTime((hours + " " + minutes).trim());

        Elements fanLabElements = document.getElementsByClass("flab-data");
        if (fanLabElements.size() != 0) {
            descriptionPOJO.setFanlab("Fantlab: " + fanLabElements.first().text());
        }

        Elements autorElements = document.getElementsByAttributeValue("itemprop", "author");
        if (autorElements.size() != 0) {
            descriptionPOJO.setAutor(autorElements.first().text());
        }

        Elements artistElements = document.getElementsByAttributeValue("rel", "performer");
        if (artistElements.size() != 0) {
            descriptionPOJO.setArtist(artistElements.first().text());
            descriptionPOJO.setArtistUrl(artistElements.first().attr("href"));
        }

        Elements seriesSisterElements = document.getElementsByClass("fa fa-book");
        if (seriesSisterElements.size() != 0) {
            Element parent = seriesSisterElements.first().parent();
            Elements seriesElement = parent.getElementsByTag("a");
            if (seriesElement.size() != 0) {
                descriptionPOJO.setSeries(seriesElement.first().text());
                descriptionPOJO.setSeriesUrl(seriesElement.first().attr("href"));
            }
        }

        Elements descriptionElements = document.getElementsByAttributeValue("itemprop",
                "description");
        if (descriptionElements.size() != 0) {
            descriptionPOJO.setDescription(descriptionElements.first().text().trim());
        }

        Elements genreConteiner = document.getElementsByClass("ls-topic-info-item");
        if (genreConteiner.size() != 0) {
            Elements aTag = genreConteiner.first().getElementsByTag("a");
            if (aTag.size() != 0) {
                Element a = aTag.first();
                descriptionPOJO.setGenre(a.text());
                descriptionPOJO.setGenreUrl(a.attr("href"));
            }
        }

        Elements autorUrlConteiner = document.getElementsByAttributeValue("rel", "author");
        if (autorUrlConteiner.size() != 0) {
            descriptionPOJO.setAutorUrl(autorUrlConteiner.first().attr("href"));
        }


        return descriptionPOJO;
    }

    private ArrayList<BookPOJO> loadBooks(int positoon) throws IOException {
        ArrayList<BookPOJO> books = new ArrayList<>();
        if (getDocument() == null) setDocument();
        Document document = getDocument();
        Elements root = document.getElementsByClass("books-block");
        int index;
        if (root.size() == 1 && positoon == 1) {
            index = 0;
        } else if (root.size() > 1) {
            index = positoon;
        } else {
            return books;
        }
        Elements booksConteiner = root.get(index).getElementsByClass("books-block-desc");
        if (booksConteiner.size() > 0) {
            for (int i = 0; i < booksConteiner.size(); i++) {
                BookPOJO bookPOJO = new BookPOJO();
                Elements aTag = booksConteiner.get(i).getElementsByTag("a");
                if (aTag.size() > 0) {
                    Element a = aTag.first();
                    bookPOJO.setUrl(a.attr("href"));
                    Elements img = a.getElementsByTag("img");
                    if (img.size() > 0) {
                        bookPOJO.setPhoto(img.first().attr("src"));
                    }
                    Elements strong = a.getElementsByTag("strong");
                    if (strong.size() > 0) {
                        bookPOJO.setName(strong.first().text());
                    }
                }
                books.add(bookPOJO);
            }
        }

        return books;
    }

    @Override
    public Observable<DescriptionPOJO> getDescription() {
        return Observable.create(observableEmitter -> {
            DescriptionPOJO articlesModels;
            try {
                articlesModels = loadDescription();
                observableEmitter.onNext(articlesModels);
            } catch (Exception e) {
                observableEmitter.onError(e);
            } finally {
                observableEmitter.onComplete();
            }
        });
    }

    @Override
    public Observable<ArrayList<BookPOJO>> getBooks(int itemPosition) {
        return Observable.create(observableEmitter -> {
            ArrayList<BookPOJO> bookPOJOArrayList;
            try {
                bookPOJOArrayList = loadBooks(itemPosition);
                observableEmitter.onNext(bookPOJOArrayList);
            } catch (Exception e) {
                observableEmitter.onError(e);
            } finally {
                observableEmitter.onComplete();
            }
        });
    }
}
