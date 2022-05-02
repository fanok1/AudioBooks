package com.fanok.audiobooks.model;


import androidx.annotation.NonNull;
import com.fanok.audiobooks.Consts;
import com.fanok.audiobooks.Url;
import com.fanok.audiobooks.pojo.BookPOJO;
import com.fanok.audiobooks.pojo.OtherArtistPOJO;
import io.reactivex.Observable;
import java.io.IOException;
import java.util.ArrayList;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

public class OtherArtistModel implements
        com.fanok.audiobooks.interface_pacatge.book_content.OtherArtistModel {

    public static ArrayList<OtherArtistPOJO> loadOtherArtistAbook(String bookName, String bookAuthor, String bookUrl,
            String bookReader) throws IOException {
        ArrayList<OtherArtistPOJO> result = new ArrayList<>();
        Document doc = Jsoup.connect(
                "https://akniga.org/search/books?q=" + bookAuthor + " - " + bookName)
                .userAgent(Consts.USER_AGENT)
                .referrer("https://akniga.org/")
                .sslSocketFactory(Consts.socketFactory())
                .get();

        Elements root = doc.getElementsByClass("content__main__articles--item");
        if (root != null) {
            for (Element book : root) {
                String autorName = "";
                String readerName = "";
                String bookTitle = "";
                OtherArtistPOJO otherArtistPOJO = new OtherArtistPOJO();
                Elements aTag = book.getElementsByClass("content__article-main-link tap-link");
                if (aTag != null && aTag.size() != 0) {
                    String href = aTag.first().attr("href");
                    if (href != null && !href.isEmpty()) {
                        otherArtistPOJO.setUrl(href);
                    }
                }

                Elements title = book.getElementsByClass("caption__article-main");
                if (title != null && title.size() != 0) {
                    String text = title.first().text();
                    if (text != null && !text.isEmpty()) {
                        bookTitle = text.trim();
                    }
                }

                Elements elements = book.getElementsByClass("link__action link__action--author");
                if (elements != null) {
                    for (Element element : elements) {
                        Elements use = element.getElementsByTag("use");
                        if (use != null && use.size() != 0) {
                            String useHref = use.first().attr("xlink:href");
                            if (useHref != null) {
                                aTag = element.getElementsByTag("a");
                                if (aTag != null && aTag.size() != 0) {
                                    Element a = aTag.first();
                                    String name = a.text();

                                    if (name != null && !name.isEmpty()) {
                                        switch (useHref) {
                                            case "#author":
                                                autorName = name;
                                                break;
                                            case "#performer":
                                                readerName = name;
                                                break;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                bookTitle = bookTitle.replace(autorName + " - ", "");
                otherArtistPOJO.setName("Исполнитель " + readerName);
                if (!readerName.isEmpty()
                        && bookName.equals(bookTitle)
                        && bookAuthor.equals(autorName)
                        && !bookUrl.equals(otherArtistPOJO.getUrl())
                        && !bookReader.equals(readerName)) {
                    result.add(otherArtistPOJO);
                }
            }
        }
        return result;
    }


    public static ArrayList<OtherArtistPOJO> loadOtherArtistABMP3(String bookName, String bookAuthor, String bookUrl,
            String bookReader) throws IOException {
        ArrayList<OtherArtistPOJO> result = new ArrayList<>();
        Document doc = Jsoup.connect(
                "https://audiobook-mp3.com/search?text=" + bookName)
                .userAgent(Consts.USER_AGENT)
                .referrer("https://audiobook-mp3.com/")
                .sslSocketFactory(Consts.socketFactory())
                .get();

        Elements elements = doc.getElementsByClass("b-statictop-search");
        if (elements != null) {
            for (Element item : elements) {
                Elements title = item.getElementsByClass("b-statictop__title");
                if (title != null && title.size() != 0) {
                    if (title.first().text().contains("в книгах")) {
                        Elements parent = item.getElementsByClass("b-statictop__items");
                        if (parent != null && parent.size() != 0) {
                            Elements books = parent.first().children();
                            for (int i = 0; i < books.size(); i++) {
                                String autorName = "";
                                String readerName = "";
                                String bookTitle = "";
                                Element book = books.get(i);
                                OtherArtistPOJO otherArtistPOJO = new OtherArtistPOJO();
                                Elements aTag = book.getElementsByTag("a");
                                if (aTag != null && aTag.size() != 0) {
                                    String href = aTag.first().attr("href");
                                    if (href != null && !href.isEmpty()) {
                                        otherArtistPOJO.setUrl(Url.SERVER_ABMP3 + href);
                                    }
                                    String fullText = aTag.first().text();
                                    if (fullText != null && !fullText.isEmpty()) {
                                        String text = fullText.substring(0, fullText.indexOf(" ("));
                                        if (!text.isEmpty()) {
                                            bookTitle = text.trim();
                                        }
                                    }

                                    Elements info = aTag.first().getElementsByClass("add_info");
                                    if (info != null && info.size() != 0) {
                                        String infoText = info.first().text();
                                        autorName = infoText
                                                .substring(infoText.indexOf("(") + 1, infoText.indexOf(")")).trim();
                                        readerName = infoText
                                                .substring(infoText.lastIndexOf("(") + 1, infoText.lastIndexOf(")"))
                                                .trim();
                                        if (autorName.equals(readerName)) {
                                            readerName = "";
                                        }
                                        otherArtistPOJO.setName("Исполнитель " + readerName);


                                    }

                                }

                                if (!readerName.isEmpty()
                                        && bookName.equals(bookTitle)
                                        && bookAuthor.equals(autorName)
                                        && !bookUrl.equals(otherArtistPOJO.getUrl())
                                        && !bookReader.equals(readerName)) {
                                    result.add(otherArtistPOJO);
                                }
                            }
                        }
                    }
                }
            }
        }
        return result;
    }

    @Override
    public Observable<ArrayList<OtherArtistPOJO>> getOtherArtist(@NonNull BookPOJO bookPOJO) {

        return Observable.create(observableEmitter -> {
            ArrayList<OtherArtistPOJO> articlesModels;
            try {
                if (bookPOJO.getUrl().contains("knigavuhe.org")) {
                    articlesModels = loadSeriesList(bookPOJO.getUrl());
                } else if (bookPOJO.getUrl().contains("izib.uk")) {
                    articlesModels = loadOtherArtistIzibuk(bookPOJO);
                } else if (bookPOJO.getUrl().contains("audiobook-mp3.com")) {
                    articlesModels = loadOtherArtistABMP3(bookPOJO.getName(), bookPOJO.getAutor(), bookPOJO.getUrl(),
                            bookPOJO.getArtist());
                } else if (bookPOJO.getUrl().contains("akniga.org")) {
                    articlesModels = loadOtherArtistAbook(bookPOJO.getName(), bookPOJO.getAutor(), bookPOJO.getUrl(),
                            bookPOJO.getArtist());
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

    private ArrayList<OtherArtistPOJO> loadSeriesList(String url) throws IOException {
        ArrayList<OtherArtistPOJO> result = new ArrayList<>();
        Document doc = Jsoup.connect(url)
                .userAgent(Consts.USER_AGENT)
                .referrer("http://www.google.com")
                .sslSocketFactory(Consts.socketFactory())
                .get();

        Elements elements = doc.getElementsByClass("book_serie_block");
        for (Element element : elements) {
            Elements title = element.getElementsByClass("book_serie_block_title");
            if (title.size() != 0 && title.first().text().contains("Другие варианты озвучки")) {
                Elements items = element.getElementsByClass("book_serie_block_item");
                for (Element item : items) {
                    OtherArtistPOJO otherArtistPOJO = new OtherArtistPOJO();
                    Elements a = item.getElementsByTag("a");
                    if (a.size() != 0) {
                        otherArtistPOJO.setUrl(Url.SERVER + a.first().attr("href"));
                        otherArtistPOJO.setName(item.text().replace(a.first().text() + " ", ""));
                    }

                    result.add(otherArtistPOJO);
                }
            }
        }

        return result;
    }

    private ArrayList<OtherArtistPOJO> loadOtherArtistIzibuk(BookPOJO bookPOJO) throws IOException {
        ArrayList<OtherArtistPOJO> result = new ArrayList<>();
        Document doc = Jsoup.connect(
                "https://izib.uk/search?q=" + bookPOJO.getName() + " " + bookPOJO.getAutor())
                .userAgent(Consts.USER_AGENT)
                .referrer("http://www.google.com")
                .sslSocketFactory(Consts.socketFactory())
                .get();

        Element element = doc.getElementById("books_list");
        if (element != null) {
            Elements list = element.getElementsByClass("_ccb9b7");
            if (list != null && list.size() > 1) {
                for (Element book : list) {
                    OtherArtistPOJO pojo = new OtherArtistPOJO();
                    Elements nameCobteiners = book.getElementsByClass("_3dc935");
                    if (nameCobteiners != null && nameCobteiners.size() > 1) {
                        Element nameConteiner = nameCobteiners.get(1);
                        String href = nameConteiner.attr("href");
                        if (href != null) {
                            pojo.setUrl(Url.SERVER_IZIBUK + href);
                            Elements elements = book.getElementsByClass("_eeab32");
                            if (elements != null) {
                                for (Element conteiner : elements) {
                                    Elements aTag = conteiner.getElementsByTag("a");
                                    if (aTag != null && aTag.size() != 0) {
                                        String name = aTag.first().text();
                                        String urlArtist = aTag.first().attr("href");
                                        if (urlArtist != null && urlArtist.contains("reader")) {
                                            if (name != null && !name.equals(
                                                    bookPOJO.getArtist())) {
                                                pojo.setName("Исполнитель " + name);
                                                result.add(pojo);
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        return result;
    }
}
