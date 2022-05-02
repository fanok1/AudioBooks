package com.fanok.audiobooks.model;

import android.content.SharedPreferences;
import com.fanok.audiobooks.Consts;
import com.fanok.audiobooks.Url;
import com.fanok.audiobooks.pojo.BookPOJO;
import io.reactivex.Observable;
import java.io.IOException;
import java.util.ArrayList;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

public class BooksModel implements com.fanok.audiobooks.interface_pacatge.books.BooksModel {

    private String genreName;

    private String genreSrc;

    private String authorName;

    private String authorUrl;

    private boolean end = false;


    private void setSiries(String url) throws IOException {
        if (genreName == null || genreSrc == null || authorName == null || authorUrl == null) {
            Document doc = Jsoup.connect(url)
                    .userAgent(Consts.USER_AGENT)
                    .sslSocketFactory(Consts.socketFactory())
                    .referrer("http://www.google.com")
                    .get();

            Elements elements = doc.getElementsByClass("_7e215f");
            if (elements != null && elements.size() != 0) {
                Elements aTag = elements.first().getElementsByTag("a");
                if (aTag != null && aTag.size() != 0) {
                    Element a = aTag.first();
                    String urlGenre = a.attr("href");
                    if (urlGenre != null) {
                        genreSrc = Url.SERVER_IZIBUK + urlGenre + "?p=";
                    }
                    String genre = a.text();
                    if (genre != null) {
                        genreName = genre;
                    }

                }

                Elements autorConteiners = doc.getElementsByAttributeValue("itemprop", "author");
                if (autorConteiners != null && autorConteiners.size() != 0) {
                    Elements autor = autorConteiners.first().getElementsByTag("a");
                    if (autor != null && autor.size() != 0) {
                        Element a = autor.first();
                        String urlAutor = a.attr("href");
                        if (urlAutor != null) {
                            authorUrl = Url.SERVER_IZIBUK + urlAutor + "?p=";
                        }
                        String autorName = a.text();
                        if (autorName != null) {
                            authorName = autorName;
                        }

                    }
                }
            }
        }
    }

    @Override
    public Observable<ArrayList<BookPOJO>> getBooks(String url, int page) {
        genreName = null;
        genreSrc = null;
        authorName = null;
        authorUrl = null;
        int size = 4;
        return Observable.create(observableEmitter -> {
            ArrayList<BookPOJO> articlesModels;
            try {
                for (int i = 1; i <= size; i++) {

                    int temp = (page - 1) * size + i;
                    if (url.contains("knigavuhe.org")) {
                        articlesModels = loadBooksList(
                                url.replace(String.valueOf(page), String.valueOf(temp)), temp);
                    } else if (url.contains("izib.uk/")) {
                        articlesModels = loadBooksListIzibuk(
                                url.replace("?p=" + page, "?p=" + temp), temp);
                    } else if (url.contains("audiobook-mp3.com")) {
                        articlesModels = loadBooksListABMP3(
                                url.replace("?page=" + page + "/", "?page=" + temp), temp);
                    } else if (url.contains("akniga.org")) {
                        articlesModels = loadBooksListAbook(
                                url.replace("/page" + page + "/", "/page" + temp + "/"), temp);
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

    @Override
    public Observable<ArrayList<BookPOJO>> getBooks(ArrayList<String> urls, int page, SharedPreferences preferences) {
        //for search
        genreName = null;
        genreSrc = null;
        authorName = null;
        authorUrl = null;
        return Observable.create(observableEmitter -> {
            int size = 4;
            int exit = 0;
            boolean endKnigaVUhe = false;
            boolean endIziBuk = false;
            boolean endABMP3 = false;
            boolean endAbook = false;
            boolean searchKnigaVUhe = preferences.getBoolean("search_kniga_v_uhe", true);
            boolean searchIzibuk = preferences.getBoolean("search_izibuc", true);
            boolean searchABMP3 = preferences.getBoolean("search_abmp3", true);
            boolean searchAbook = preferences.getBoolean("search_abook", true);
            boolean speedUpABMP3 = preferences.getBoolean("speed_up_search_abmp3", false);

            if (!searchKnigaVUhe) {
                exit++;
            }
            if (!searchIzibuk) {
                exit++;
            }
            if (!searchABMP3) {
                exit++;
            }

            if (!searchAbook) {
                exit++;
            }

            try {
                for (int i = 1; i <= size; i++) {
                    ArrayList<BookPOJO> articlesModels = new ArrayList<>();
                    int temp = (page - 1) * size + i;
                    try {
                        if (!endKnigaVUhe && searchKnigaVUhe) {
                            articlesModels.addAll(loadBooksList(
                                    urls.get(1).replace(String.valueOf(page), String.valueOf(temp)),
                                    temp));
                        }
                    } catch (NullPointerException e) {
                        endKnigaVUhe = true;
                        exit++;
                        if (exit >= urls.size()) {
                            observableEmitter.onError(e);
                        }
                    }

                    try {
                        if (!endIziBuk && searchIzibuk) {
                            articlesModels.addAll(loadBooksListIzibuk(
                                    urls.get(2).replace("p=" + page, "p=" + temp), temp));
                        }
                    } catch (NullPointerException e) {
                        endIziBuk = true;
                        exit++;
                        if (exit >= urls.size()) {
                            observableEmitter.onError(e);
                        }
                    }

                    try {
                        if (!endABMP3 && searchABMP3) {
                            if (temp % size == 0) {
                                if (!speedUpABMP3) {
                                    articlesModels.addAll(loadBooksListSearchABMP3(urls.get(3), temp / size));
                                } else {
                                    articlesModels.addAll(loadBooksListSearchABMP3SpeedUp(urls.get(3), temp / size));
                                }
                            }
                        }
                    } catch (NullPointerException e) {
                        endABMP3 = true;
                        exit++;
                        if (exit >= urls.size()) {
                            observableEmitter.onError(e);
                        }
                    }

                    try {
                        if (!endAbook && searchAbook) {
                            articlesModels.addAll(loadBooksListAbook(
                                    urls.get(0).replace("/page" + page + "/", "/page" + temp + "/"), temp));
                        }
                    } catch (NullPointerException e) {
                        endAbook = true;
                        exit++;
                        if (exit >= urls.size()) {
                            observableEmitter.onError(e);
                        }
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


    private ArrayList<BookPOJO> loadBooksList(String url, int page) throws IOException {
        ArrayList<BookPOJO> result = new ArrayList<>();
        String autor = "";
        String autorUrl = "";
        Document doc = Jsoup.connect(url)
                .userAgent(Consts.USER_AGENT)
                .referrer("http://www.google.com")
                .sslSocketFactory(Consts.socketFactory())
                .get();

        Elements pagesConteiner = doc.getElementsByClass("pn_page_buttons");
        if (pagesConteiner.size() != 0) {
            Elements pagesElements = pagesConteiner.first().children();
            if (pagesElements.size() != 0) {
                Element lastPageElement = pagesElements.last();
                try {
                    int lastPage = Integer.parseInt(lastPageElement.text());
                    if (lastPage < page) {
                        throw new NullPointerException();
                    }
                } catch (NumberFormatException e) {
                    throw new NullPointerException();
                }
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
                autorUrl = url;
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
            Elements books = bookList.getElementsByClass("bookkitem");
            if (books.size() == 0) return result;
            for (Element book : books) {
                Elements litRes = book.getElementsByClass("bookkitem_litres_icon");
                if (litRes.size() != 0) continue;
                BookPOJO bookPOJO = new BookPOJO();
                String img = book.getElementsByTag("img").get(0).attr("src");
                if (img != null) {
                    int lastPos = img.indexOf("?");
                    if (lastPos != -1) {
                        img = img.substring(0, lastPos);
                    }
                    bookPOJO.setPhoto(img);
                }
                Elements aTags = book.getElementsByClass("bookkitem_name");
                if (aTags.size() != 0) {
                    Element a = aTags.first().child(0);
                    if (a != null) {
                        bookPOJO.setUrl(Url.SERVER + a.attr("href"));
                        bookPOJO.setName(a.text());
                    }
                }

                Element aGenre = book.getElementsByClass("bookkitem_genre").first().child(0);
                if (aGenre != null) {
                    bookPOJO.setUrlGenre(Url.SERVER + aGenre.attr("href") + "<page>/");
                    bookPOJO.setGenre(aGenre.text());
                }

                Elements ratingConteiner = book.getElementsByClass("bookkitem_icon -views");
                if (ratingConteiner.size() != 0) {
                    bookPOJO.setReting(ratingConteiner.first().nextElementSibling().text());
                }

                Elements comentsConteiner = book.getElementsByClass("bookkitem_icon -comments");
                if (comentsConteiner.size() != 0) {
                    bookPOJO.setComents(comentsConteiner.first().nextElementSibling().text());
                }

                Elements autorConteiner = book.getElementsByClass("bookkitem_author");
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

                Elements artistConteiner = book.getElementsByClass("bookkitem_icon -reader");
                if (artistConteiner.size() != 0) {
                    Element parent = artistConteiner.first().parent();
                    Elements artist = parent.getElementsByTag("a");
                    if (artist.size() != 0) {
                        bookPOJO.setArtist(artist.first().text());
                        bookPOJO.setUrlArtist(Url.SERVER + artist.first().attr("href"));
                    }
                }

                Elements timeConteinr = book.getElementsByClass("bookkitem_meta_time");
                if (timeConteinr.size() != 0) {
                    bookPOJO.setTime(timeConteinr.first().text());
                }

                Elements seriesConteiner = book.getElementsByClass("bookkitem_icon -serie");
                if (seriesConteiner.size() != 0) {
                    Element parent = seriesConteiner.first().parent();
                    Elements series = parent.getElementsByTag("a");
                    if (series.size() != 0) {
                        bookPOJO.setSeries(series.first().text());
                        bookPOJO.setUrlSeries(Url.SERVER + series.first().attr("href") + "?page=");
                    }
                }

                if (url.contains("/serie/")) {
                    if (bookPOJO.getSeries() == null || bookPOJO.getUrlSeries() == null
                            || bookPOJO.getSeries().isEmpty()
                            || bookPOJO.getUrlSeries().isEmpty()) {
                        bookPOJO.setUrlSeries(url);
                        Elements elements = doc.getElementsByClass("page_title");
                        if (elements != null && elements.size() != 0) {
                            Elements b = elements.first().getElementsByTag("b");
                            if (b != null && b.size() != 0) {
                                String text = b.first().text();
                                if (text != null && !text.isEmpty()) {
                                    bookPOJO.setSeries(text);
                                }
                            }
                        }
                    }
                }

                Elements about = book.getElementsByClass("bookkitem_about");
                if (about != null && about.size() != 0) {
                    bookPOJO.setDesc(about.first().text());
                }

                if (bookPOJO.isNull()) {
                    continue;
                }
                result.add(bookPOJO);
            }
        }
        if (result.size() == 0) {
            throw new NullPointerException();
        }
        return result;
    }

    private ArrayList<BookPOJO> loadBooksListABMP3(String url, int page) throws IOException {
        ArrayList<BookPOJO> result = new ArrayList<>();
        Document doc = Jsoup.connect(url)
                .userAgent(Consts.USER_AGENT)
                .referrer("http://www.google.com")
                .sslSocketFactory(Consts.socketFactory())
                .get();

        Elements pagesConteiner = doc.getElementsByClass("pagination");
        if (pagesConteiner.size() != 0) {
            Elements pagesElements = pagesConteiner.first().children();
            if (pagesElements.size() != 0) {
                Element lastPageElement = pagesElements.get(pagesElements.size() - 2);
                Element aLastPage = lastPageElement.child(0);
                if (aLastPage != null) {
                    int lastPage = Integer.parseInt(aLastPage.text());
                    if (lastPage < page) {
                        throw new NullPointerException();
                    }
                } else {
                    throw new NullPointerException();
                }
            } else {
                throw new NullPointerException();
            }
        } else if (page > 1) {
            throw new NullPointerException();
        }

        Elements bookList = doc.getElementsByClass("b-posts");
        if (bookList != null && bookList.size() > 0) {
            Elements books = bookList.first().getElementsByClass("abook-item");
            if (books.size() == 0) {
                return result;
            }
            for (Element book : books) {
                BookPOJO bookPOJO = new BookPOJO();
                String autorName = "";

                String img = book.getElementsByTag("img").get(0).attr("src");
                if (img != null) {
                    bookPOJO.setPhoto(Url.SERVER_ABMP3 + img);
                }

                Elements bookInfo = book.getElementsByClass("content-abook-info");
                if (bookInfo != null && bookInfo.size() != 0) {
                    Elements cont = bookInfo.first().children();
                    if (cont != null && cont.size() == 3) {
                        Elements aAutor = cont.get(0).getElementsByTag("a");
                        if (aAutor != null && aAutor.size() > 0) {
                            autorName = aAutor.first().text();
                            bookPOJO.setAutor(autorName);
                            bookPOJO.setUrlAutor(Url.SERVER_ABMP3 + aAutor.first().attr("href") + "?page=");
                        }
                        Elements aReader = cont.get(1).getElementsByTag("a");
                        if (aReader != null && aReader.size() > 0) {
                            bookPOJO.setArtist(aReader.first().text());
                            bookPOJO.setUrlArtist(Url.SERVER_ABMP3 + aReader.first().attr("href") + "?page=");
                        }
                        bookPOJO.setTime(cont.get(2).text().trim());
                    }
                }

                Elements aTags = book.getElementsByClass("abook-title");
                if (aTags != null && aTags.size() != 0) {
                    Element a = aTags.first().child(0);
                    if (a != null) {
                        bookPOJO.setUrl(Url.SERVER_ABMP3 + a.attr("href"));
                        bookPOJO.setName(a.text().replace(autorName + " - ", ""));
                    }
                }

                Element aGenre = book.getElementsByClass("abook-genre").first().child(0);
                if (aGenre != null) {
                    bookPOJO.setUrlGenre(Url.SERVER_ABMP3 + aGenre.attr("href") + "?page=");
                    bookPOJO.setGenre(aGenre.text());
                }

                Elements about = book.getElementsByClass("abook-content");
                if (about != null && about.size() != 0) {
                    bookPOJO.setDesc(about.first().text());
                }

                if (bookPOJO.isNull()) {
                    continue;
                }
                result.add(bookPOJO);
            }
        }
        return result;
    }

    private ArrayList<BookPOJO> loadBooksListAbook(String url, int page) throws IOException {
        ArrayList<BookPOJO> result = new ArrayList<>();
        Document document = Jsoup.connect(url)
                .userAgent(Consts.USER_AGENT)
                .referrer("http://www.google.com")
                .sslSocketFactory(Consts.socketFactory())
                .get();

        Elements bootom = document.getElementsByClass("page__nav");
        if (bootom != null && bootom.size() != 0) {
            Elements nextButton = bootom.get(0).getElementsByClass("page__nav--next");
            if (!(nextButton != null && nextButton.size() != 0)) {
                if (end) {
                    throw new NullPointerException();
                }
                end = true;
            }
        } else if (page > 1) {
            throw new NullPointerException();
        }

        Elements listElements = document.getElementsByClass("content__main__articles--item");
        if (listElements != null && listElements.size() != 0) {
            for (Element book : listElements) {
                Elements paid = book.getElementsByAttributeValue("href", "https://akniga.org/paid/");
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

                BookPOJO bookPOJO = new BookPOJO();
                Elements genreParent = book.getElementsByClass("section__title");
                if (genreParent != null && genreParent.size() != 0) {
                    Element element = genreParent.first();
                    String genreName = element.text();
                    if (genreName != null) {
                        bookPOJO.setGenre(genreName);
                    }
                    String genreUrl = element.attr("href");
                    if (genreUrl != null) {
                        bookPOJO.setUrlGenre(genreUrl + "page<page>/");
                    }
                }
                Elements imageParent = book.getElementsByTag("img");
                if (imageParent != null && imageParent.size() != 0) {
                    Element image = imageParent.first();
                    String imageSrc = image.attr("src");
                    if (imageSrc != null) {
                        bookPOJO.setPhoto(imageSrc);
                    }
                }

                Elements elements = book.getElementsByClass("link__action link__action--author");
                if (elements != null) {
                    for (Element element : elements) {
                        Elements use = element.getElementsByTag("use");
                        if (use != null && use.size() != 0) {
                            String useHref = use.first().attr("xlink:href");
                            if (useHref != null) {
                                Elements aTag = element.getElementsByTag("a");
                                if (aTag != null && aTag.size() != 0) {
                                    Element a = aTag.first();
                                    String src = a.attr("href");
                                    String name = a.text();
                                    if (src != null && !src.isEmpty()) {
                                        switch (useHref) {
                                            case "#author":
                                                bookPOJO.setUrlAutor(src + "page<page>/");
                                                break;
                                            case "#performer":
                                                bookPOJO.setUrlArtist(src + "page<page>/");
                                                break;
                                            case "#series":
                                                bookPOJO.setUrlSeries(src);
                                                break;
                                        }
                                    }

                                    if (name != null && !name.isEmpty()) {
                                        switch (useHref) {
                                            case "#author":
                                                bookPOJO.setAutor(name);
                                                break;
                                            case "#performer":
                                                bookPOJO.setArtist(name);
                                                break;
                                            case "#series":
                                                bookPOJO.setSeries(name);
                                                break;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Elements titleElements = book.getElementsByClass("caption__article-main");
                if (titleElements != null && titleElements.size() != 0) {
                    String title = titleElements.first().text();
                    if (title != null && !title.isEmpty()) {
                        bookPOJO.setName(title.replace(bookPOJO.getAutor() + " - ", ""));
                    }
                }

                Elements link = book.getElementsByClass("content__article-main-link");
                if (link != null && link.size() != 0) {
                    String src = link.first().attr("href");
                    if (src != null && !src.isEmpty()) {
                        bookPOJO.setUrl(src);
                    }
                }

                Elements desc = book.getElementsByClass("description__article-main");
                if (desc != null && desc.size() != 0) {
                    String text = desc.first().text();
                    if (text != null && !text.isEmpty()) {
                        bookPOJO.setDesc(text);
                    }
                }

                Elements time = book.getElementsByClass("link__action link__action--label link__action--label--time");
                if (time != null && time.size() != 0) {
                    String text = time.first().text();
                    if (text != null && !text.isEmpty()) {
                        bookPOJO.setTime(text);
                    }
                }

                Elements coments = book.getElementsByClass("link__action link__action--comment");
                if (coments != null && coments.size() != 0) {
                    String text = coments.first().text();
                    if (text != null && !text.contains("нет комментариев")) {
                        String count = text.trim().substring(0, text.trim().indexOf(" "));
                        bookPOJO.setComents(count);
                    }
                }

                Elements reting = book.getElementsByClass("link__action--label--views");
                if (reting != null && reting.size() != 0) {
                    String text = reting.text();
                    if (text != null && !text.isEmpty()) {
                        bookPOJO.setReting(text.trim());
                    }
                }

                if (bookPOJO.isNull()) {
                    continue;
                }
                result.add(bookPOJO);
            }

        }

        if (result.size() == 0) {
            throw new NullPointerException();
        }
        return result;
    }

    private ArrayList<BookPOJO> loadBooksListIzibuk(String url, int page) throws IOException {
        ArrayList<BookPOJO> result = new ArrayList<>();
        int cookie;
        if (Consts.izibuk_reiting) {
            cookie = 1;
        } else {
            cookie = 0;
        }
        Document document = Jsoup.connect(url).userAgent(Consts.USER_AGENT).sslSocketFactory(
                Consts.socketFactory()).referrer(
                Url.INDEX_IZIBUK + "1").cookie("sort_pop", String.valueOf(cookie)).get();

        Element bootom = document.getElementById("books_updates_list__pn");
        if (bootom == null) {
            bootom = document.getElementById("books_list__pn");
        }
        if (bootom != null) {
            Element bootomChild = bootom.child(0);
            if (bootomChild != null) {
                Elements list = bootomChild.children();
                if (list != null && list.size() > 1) {
                    Element pagesParent = list.last();
                    Elements pages = pagesParent.children();
                    if (pages != null && pages.size() > 0) {
                        Element lastPage = pages.last();
                        if (lastPage != null) {
                            String last = lastPage.text();
                            if (last != null) {
                                try {
                                    if (page > Integer.parseInt(last)) {
                                        throw new NullPointerException();
                                    }
                                } catch (NumberFormatException e) {
                                    throw new NullPointerException();
                                }

                            } else {
                                throw new NullPointerException();
                            }
                        } else {
                            throw new NullPointerException();
                        }
                    } else {
                        throw new NullPointerException();
                    }
                } else {
                    throw new NullPointerException();
                }
            } else {
                throw new NullPointerException();
            }
        } else if (page > 1) {
            throw new NullPointerException();
        }

        Element listParent = document.getElementById("books_list");
        if (listParent == null) {
            listParent = document.getElementById("books_updates_list");
        }
        if (listParent != null) {
            Elements listElements = listParent.getElementsByClass("_ccb9b7");
            if (listElements != null && listElements.size() != 0) {
                for (Element book : listElements) {
                    BookPOJO bookPOJO = new BookPOJO();
                    Elements genreParent = book.getElementsByClass("_680f12");
                    if (genreParent != null && genreParent.size() != 0) {
                        Element element = genreParent.first();
                        if (element != null && element.children().size() != 0) {
                            Element genre = element.child(0);
                            if (genre != null) {
                                String genreName = genre.text();
                                if (genreName != null) {
                                    bookPOJO.setGenre(genreName);
                                }
                                String genreUrl = genre.attr("href");
                                if (genreUrl != null) {
                                    bookPOJO.setUrlGenre(Url.SERVER_IZIBUK + genreUrl + "?p=");
                                }
                            }
                        }
                    } else {
                        if (url.contains("genre")) {
                            bookPOJO.setUrlGenre(url);
                            Elements elements = document.getElementsByTag("h1");
                            if (elements != null && elements.size() != 0) {
                                String genre = elements.first().text();
                                if (genre != null) {
                                    bookPOJO.setGenre(genre);
                                }
                            }
                        }
                    }
                    Elements imageParent = book.getElementsByClass("_bce453");
                    if (imageParent != null && imageParent.size() != 0) {
                        Element image = imageParent.first().child(0);
                        if (image != null) {
                            String imageSrc = image.attr("src");
                            if (imageSrc != null) {
                                bookPOJO.setPhoto(imageSrc);
                            }
                        }
                    }
                    Elements contentlist = book.getElementsByClass("_802db0");
                    if (contentlist != null && contentlist.size() != 0) {
                        Element content = contentlist.first();
                        if (content != null) {
                            Element titleParent = content.child(1);
                            if (titleParent != null) {
                                Elements fragments = titleParent.getElementsByClass("_09ddb7");
                                if (fragments != null && fragments.size() > 0) continue;
                                Element title = titleParent.child(0);
                                if (title != null) {
                                    String bookUrl = title.attr("href");
                                    if (bookUrl != null) {
                                        bookPOJO.setUrl(Url.SERVER_IZIBUK + bookUrl);
                                        if (url.contains("serie")
                                                && bookPOJO.getUrlGenre() == null) {
                                            setSiries(Url.SERVER_IZIBUK + bookUrl);
                                            bookPOJO.setGenre(genreName);
                                            bookPOJO.setUrlGenre(genreSrc);
                                            bookPOJO.setAutor(authorName);
                                            bookPOJO.setUrlAutor(authorUrl);
                                        }
                                    }
                                    String name = title.text();
                                    if (name != null) {
                                        bookPOJO.setName(name);
                                    }
                                }
                            }
                            Element child = content.child(2);
                            if (child != null) {
                                Elements elements = child.children();
                                if (elements != null) {
                                    for (Element element : elements) {
                                        if (element.childrenSize() > 1) {
                                            Element conteinter = element.child(1);
                                            if (conteinter != null) {
                                                String href = conteinter.attr("href");
                                                if (href != null) {
                                                    String name = conteinter.text();
                                                    if (href.contains("author")) {
                                                        bookPOJO.setUrlAutor(
                                                                Url.SERVER_IZIBUK + href + "?p=");
                                                        if (name != null) {
                                                            bookPOJO.setAutor(name);
                                                        }
                                                    } else if (href.contains("reader")) {
                                                        bookPOJO.setUrlArtist(
                                                                Url.SERVER_IZIBUK + href + "?p=");
                                                        if (name != null) {
                                                            bookPOJO.setArtist(name);
                                                        }
                                                    } else if (href.contains("serie")) {
                                                        bookPOJO.setUrlSeries(
                                                                Url.SERVER_IZIBUK + href + "?p=");
                                                        if (name != null) {
                                                            bookPOJO.setSeries(name);
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            if (bookPOJO.getAutor() == null || bookPOJO.getAutor().isEmpty()) {
                                if (url.contains("author")) {
                                    bookPOJO.setUrlAutor(url + "?p=");
                                    Elements elements = document.getElementsByTag("h1");
                                    if (elements != null && elements.size() != 0) {
                                        String genre = elements.first().text();
                                        if (genre != null) {
                                            bookPOJO.setAutor(genre);
                                        }
                                    }
                                }
                            }

                            if (bookPOJO.getArtist() == null || bookPOJO.getArtist().isEmpty()) {
                                if (url.contains("reader")) {
                                    bookPOJO.setUrlArtist(url + "?p=");
                                    Elements elements = document.getElementsByTag("h1");
                                    if (elements != null && elements.size() != 0) {
                                        String genre = elements.first().text();
                                        if (genre != null) {
                                            bookPOJO.setArtist(genre);
                                        }
                                    }
                                }
                            }

                            if (bookPOJO.getSeries() == null || bookPOJO.getSeries().isEmpty()) {
                                if (url.contains("serie")) {
                                    bookPOJO.setUrlSeries(url + "?p=");
                                    Elements elements = document.getElementsByTag("h1");
                                    if (elements != null && elements.size() != 0) {
                                        String genre = elements.first().text();
                                        if (genre != null) {
                                            bookPOJO.setSeries(genre);
                                        }
                                    }
                                }
                            }

                            Element description = content.child(3);
                            if (description != null) {
                                String text = description.text();
                                if (text != null) {
                                    bookPOJO.setDesc(text);
                                }
                            }
                        }
                    }
                    if (bookPOJO.isNull()) {
                        continue;
                    }
                    result.add(bookPOJO);
                }

            }
        }

        if (result.size() == 0) {
            throw new NullPointerException();
        }
        return result;
    }

    private ArrayList<BookPOJO> loadBooksListSearchABMP3(String url, int page) throws IOException {
        ArrayList<BookPOJO> result = new ArrayList<>();
        Document doc = Jsoup.connect(url)
                .userAgent(Consts.USER_AGENT)
                .referrer("http://www.google.com")
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
                            for (int i = (page - 1) * 5; i < page * 5; i++) {
                                if (i >= books.size()) {
                                    break;
                                }
                                Element book = books.get(i);
                                BookPOJO bookPOJO = new BookPOJO();
                                Elements aTag = book.getElementsByTag("a");
                                if (aTag != null && aTag.size() != 0) {
                                    bookPOJO.setUrl(Url.SERVER_ABMP3 + aTag.first().attr("href"));
                                    String name = aTag.first().text();
                                    bookPOJO.setName(name.substring(0, name.indexOf(" (")));
                                    Document bookPage = Jsoup.connect(bookPOJO.getUrl())
                                            .userAgent(Consts.USER_AGENT)
                                            .referrer("http://www.google.com")
                                            .sslSocketFactory(Consts.socketFactory())
                                            .get();
                                    Elements img = bookPage.getElementsByClass("abook_image");
                                    if (img != null && img.size() != 0) {
                                        bookPOJO.setPhoto(Url.SERVER_ABMP3 + img.first().attr("src"));
                                    }

                                    Elements desc = bookPage.getElementsByClass("abook-desc");
                                    if (desc != null && desc.size() != 0) {
                                        bookPOJO.setDesc(desc.first().text());
                                    }

                                    Elements infos = bookPage.getElementsByClass("panel-item");
                                    if (infos != null) {
                                        for (Element info : infos) {
                                            if (info.text().contains("Автор")) {
                                                Elements element = info.getElementsByTag("a");
                                                if (element != null && element.size() != 0) {
                                                    bookPOJO.setAutor(element.first().text());
                                                    bookPOJO.setUrlAutor(
                                                            Url.SERVER_ABMP3 + element.first().attr("href")
                                                                    + "?page=");
                                                }
                                            } else if (info.text().contains("Читает")) {
                                                Elements element = info.getElementsByTag("a");
                                                if (element != null && element.size() != 0) {
                                                    bookPOJO.setArtist(element.first().text());
                                                    bookPOJO.setUrlArtist(
                                                            Url.SERVER_ABMP3 + element.first().attr("href")
                                                                    + "?page=");
                                                }
                                            } else if (info.text().contains("Жанры")) {
                                                Elements element = info.getElementsByTag("a");
                                                if (element != null && element.size() != 0) {
                                                    bookPOJO.setGenre(element.first().text());
                                                    bookPOJO.setUrlGenre(
                                                            Url.SERVER_ABMP3 + element.first().attr("href")
                                                                    + "?page=");
                                                }
                                            } else {
                                                Elements clock = info.getElementsByClass("fa-clock-o");
                                                if (clock != null && clock.size() != 0) {
                                                    bookPOJO.setTime(info.text().trim());
                                                }

                                                Elements reting = info.getElementsByClass("fa-eye");
                                                if (reting != null && reting.size() != 0) {
                                                    bookPOJO.setReting(info.text().trim());
                                                }

                                            }
                                        }
                                    }

                                }
                                if (!bookPOJO.isNull()) {
                                    result.add(bookPOJO);
                                }
                            }
                        }
                    }
                }
            }
        }

        if (result.size() == 0) {
            throw new NullPointerException();
        }
        return result;
    }

    private ArrayList<BookPOJO> loadBooksListSearchABMP3SpeedUp(String url, int page) throws IOException {
        ArrayList<BookPOJO> result = new ArrayList<>();
        Document doc = Jsoup.connect(url)
                .userAgent(Consts.USER_AGENT)
                .referrer("http://www.google.com")
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
                            for (int i = (page - 1) * 40; i < page * 40; i++) {
                                if (i >= books.size()) {
                                    break;
                                }
                                Element book = books.get(i);
                                BookPOJO bookPOJO = new BookPOJO();
                                Elements aTag = book.getElementsByTag("a");
                                if (aTag != null && aTag.size() != 0) {
                                    String href = aTag.first().attr("href");
                                    if (href != null && !href.isEmpty()) {
                                        bookPOJO.setUrl(Url.SERVER_ABMP3 + href);
                                    }
                                    String fullText = aTag.first().text();
                                    if (fullText != null && !fullText.isEmpty()) {
                                        String text = fullText.substring(0, fullText.indexOf(" ("));
                                        if (!text.isEmpty()) {
                                            bookPOJO.setName(text.trim());
                                        }
                                    }

                                    Elements info = aTag.first().getElementsByClass("add_info");
                                    if (info != null && info.size() != 0) {
                                        String infoText = info.first().text();
                                        String autorName = infoText
                                                .substring(infoText.indexOf("(") + 1, infoText.indexOf(")")).trim();
                                        bookPOJO.setAutor(autorName);
                                        String readerName = infoText
                                                .substring(infoText.lastIndexOf("(") + 1, infoText.lastIndexOf(")"))
                                                .trim();
                                        if (!autorName.equals(readerName)) {
                                            bookPOJO.setArtist(readerName);
                                        }

                                    }

                                }

                                if (!bookPOJO.isNull()) {
                                    result.add(bookPOJO);
                                }
                            }
                        }
                    }
                }
            }
        }

        if (result.size() == 0) {
            throw new NullPointerException();
        }
        return result;
    }

}
