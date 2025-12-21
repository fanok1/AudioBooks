package com.fanok.audiobooks.model;




import static com.fanok.audiobooks.Consts.PROXY_HOST;
import static com.fanok.audiobooks.Consts.PROXY_PORT;

import androidx.annotation.NonNull;
import androidx.media3.common.util.UnstableApi;

import com.fanok.audiobooks.App;
import com.fanok.audiobooks.Consts;
import com.fanok.audiobooks.Url;
import com.fanok.audiobooks.pojo.BookPOJO;
import com.fanok.audiobooks.pojo.OtherArtistPOJO;
import io.reactivex.Observable;
import okhttp3.HttpUrl;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Proxy.Type;
import java.util.ArrayList;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

public class OtherArtistModel implements
        com.fanok.audiobooks.interface_pacatge.book_content.OtherArtistModel {

    @UnstableApi
    public static ArrayList<OtherArtistPOJO> loadOtherArtistABMP3(String bookName, String bookAuthor, String bookUrl,
            String bookReader) throws IOException {
        ArrayList<OtherArtistPOJO> result = new ArrayList<>();
        Connection connection = Jsoup.connect(
                        Url.SERVER_ABMP3 + "/search?text=" + bookName)
                .userAgent(Consts.USER_AGENT)
                .referrer(Url.SERVER_ABMP3 + "/")
                .sslSocketFactory(Consts.socketFactory())
                .maxBodySize(0);

        if(App.useProxy) {
            Proxy proxy = new Proxy(Type.SOCKS,
                    new InetSocketAddress(PROXY_HOST, PROXY_PORT));
            connection.proxy(proxy);
        }

        Document doc = connection.get();

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

    public static ArrayList<OtherArtistPOJO> loadOtherArtistAbook(String bookName, String bookAuthor, String bookUrl,
            String bookReader) throws IOException {
        ArrayList<OtherArtistPOJO> result = new ArrayList<>();
        Document doc = Jsoup.connect(
                        Url.SERVER_AKNIGA + "/search/books?q=" + bookAuthor + " - " + bookName)
                .userAgent(Consts.USER_AGENT)
                .referrer(Url.SERVER_AKNIGA)
                .sslSocketFactory(Consts.socketFactory())
                .maxBodySize(0)
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

    @UnstableApi
    public static ArrayList<OtherArtistPOJO> loadOtherArtistBazaKnig(final String baseName, final String baseAutor,
            final String baseUrl, final String baseArtist) throws IOException {
        ArrayList<OtherArtistPOJO> result = new ArrayList<>();
        Document doc;
        Connection connection = Jsoup.connect(Url.SERVER_BAZA_KNIG + "/index.php?do=search")
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
        doc = connection
                .data("do", "search")
                .data("subaction", "search")
                .data("search_start", "0")
                .data("full_search", "0")
                .data("result_from", "0")
                .data("story", baseName + " " + baseAutor)
                .post();

        Element conteiner = doc.getElementById("dle-content");
        if (conteiner != null) {
            Elements bookList = conteiner.getElementsByClass("short");
            if (bookList != null && bookList.size() > 0) {
                for (Element book : bookList) {
                    String url = "";
                    Elements imgConteiner = book.getElementsByClass("short-img");
                    if (imgConteiner != null && imgConteiner.size() > 0) {
                        Element element = imgConteiner.first();
                        Elements aElement = element.getElementsByTag("a");
                        if (aElement != null && aElement.size() > 0) {
                            String aHref = aElement.first().attr("href");
                            if (aHref != null && !aHref.isEmpty()) {
                                url = aHref;
                            } else {
                                continue;
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
                                        Elements artistElements = li.getElementsByTag("b");
                                        if (artistElements != null && artistElements.size() > 0) {
                                            String artistName = liText.replace("Читает: ", "").trim();
                                            if (!artistName.isEmpty()) {
                                                if (artistName.contains("альтернативная озвучка")) {
                                                    Connection conn = Jsoup.connect(url)
                                                            .userAgent(Consts.USER_AGENT)
                                                            .referrer("http://www.google.com")
                                                            .sslSocketFactory(Consts.socketFactory())
                                                            .maxBodySize(0)
                                                            .ignoreHttpErrors(true);

                                                    if(App.useProxy) {
                                                        Proxy proxy = new Proxy(Type.SOCKS,
                                                                new InetSocketAddress(PROXY_HOST, PROXY_PORT));
                                                        conn.proxy(proxy);
                                                    }
                                                    if (!Consts.getBazaKnigCookies().isEmpty()) {
                                                        conn.cookie("PHPSESSID", Consts.getBazaKnigCookies());
                                                    }
                                                    Document document = conn.get();

                                                    Element otherReader1 = document.getElementById("content-tab2");
                                                    Element otherReader2 = document.getElementById("content-tab3");
                                                    if (otherReader1 != null) {
                                                        String text = otherReader1.ownText();
                                                        if (text != null && text.contains("Озвучивает:")) {
                                                            String name = text.replace("Озвучивает:", "").trim();
                                                            if (!name.equals(baseArtist)) {
                                                                OtherArtistPOJO otherArtistPOJO
                                                                        = new OtherArtistPOJO();
                                                                otherArtistPOJO.setName(name);
                                                                otherArtistPOJO.setUrl(url + "?sorce=2");
                                                                result.add(otherArtistPOJO);
                                                            }
                                                        }
                                                    }
                                                    if (otherReader2 != null) {
                                                        String text = otherReader2.ownText();
                                                        if (text != null && text.contains("Озвучивает:")) {
                                                            String name = text.replace("Озвучивает:", "").trim();
                                                            if (!name.equals(baseArtist)) {
                                                                OtherArtistPOJO otherArtistPOJO
                                                                        = new OtherArtistPOJO();
                                                                otherArtistPOJO.setName(name);
                                                                otherArtistPOJO.setUrl(url + "?sorce=3");
                                                                result.add(otherArtistPOJO);
                                                            }
                                                        }
                                                    }

                                                }

                                                String name = artistElements.first().text();
                                                if (name != null && !name.isEmpty() && !baseArtist.contains(name)) {
                                                    OtherArtistPOJO otherArtistPOJO = new OtherArtistPOJO();
                                                    otherArtistPOJO.setName(name);
                                                    otherArtistPOJO.setUrl(url);
                                                    result.add(otherArtistPOJO);
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
        }
        return result;
    }

    public static ArrayList<OtherArtistPOJO> loadOtherArtistKnigoblud(String bookName, String bookAuthor, String bookUrl,
            String bookReader) throws IOException {
        ArrayList<OtherArtistPOJO> result = new ArrayList<>();
        Document doc = Jsoup.connect(
                        Url.SERVER_KNIGOBLUD + "/search?q=" + bookAuthor + " - " + bookName)
                .userAgent(Consts.USER_AGENT)
                .referrer(Url.SERVER_KNIGOBLUD)
                .sslSocketFactory(Consts.socketFactory())
                .maxBodySize(0)
                .ignoreHttpErrors(true)
                .get();



        Element listParent = doc.getElementById("BL");
        if (listParent != null) {
            Elements listElements = listParent.getElementsByClass("bookListItem");
            if (listElements != null && listElements.size() != 0) {
                for (Element book : listElements) {
                    OtherArtistPOJO pojo = new OtherArtistPOJO();
                    String autorName = "";
                    String readerName = "";
                    String title = "";


                    Elements urlConteiner = book.getElementsByClass("bookListItemCover");
                    if (urlConteiner != null && urlConteiner.size() != 0) {
                        String href = urlConteiner.first().attr("href");
                        if (href != null && !href.isEmpty()) {
                            pojo.setUrl(Url.SERVER_KNIGOBLUD + href);
                        }
                    }

                    Elements metaBloks = book.getElementsByClass("bookListItemMetaBlock");
                    if (metaBloks != null) {
                        for (Element metaBlock : metaBloks) {
                            String metaBlocktext = metaBlock.text();
                            if (metaBlocktext.contains("✍") && metaBlocktext.contains("\uD83C\uDF99")) {
                                Element element = metaBlock.child(1);
                                if (element != null) {
                                    String text = element.text();
                                    if (text != null && !text.isEmpty()) {
                                        autorName = text;
                                    }
                                }
                                element = metaBlock.child(3);
                                if (element != null) {
                                    String text = element.text();
                                    if (text != null && !text.isEmpty()) {
                                        readerName = text;
                                    }
                                }
                            } else if (metaBlocktext.contains("✍")) {
                                Element element = metaBlock.child(1);
                                if (element != null) {
                                    String text = element.text();
                                    if (text != null && !text.isEmpty()) {
                                        autorName = text;
                                    }
                                }
                            } else if (metaBlocktext.contains("\uD83C\uDF99")) {
                                Element element = metaBlock.child(1);
                                if (element != null) {
                                    String text = element.text();
                                    if (text != null && !text.isEmpty()) {
                                        readerName = text;
                                    }
                                }
                            }
                        }
                    }

                    Elements nameConteiner = book.getElementsByClass("bookListItemCoverNameText");
                    if(nameConteiner!=null&&nameConteiner.size()!=0){
                        String text = nameConteiner.first().text();
                        if(text!=null&&!text.isEmpty()){
                            title = text;
                        }
                    }

                    pojo.setName("Исполнитель " + readerName);
                    if (!readerName.isEmpty()
                            && bookName.equals(title)
                            && bookAuthor.equals(autorName)
                            && !bookUrl.equals(pojo.getUrl())
                            && !bookReader.equals(readerName)) {
                        result.add(pojo);
                    }

                }
            }
        }
        return result;
    }

    @UnstableApi
    public static ArrayList<OtherArtistPOJO> loadOtherArtistBookoof(String bookName, String bookAuthor, String bookUrl,
                                                                      String bookReader) throws IOException {
        ArrayList<OtherArtistPOJO> result = new ArrayList<>();
        Connection connection = Jsoup.connect("https://bookoof.net/index.php?do=search")
                .userAgent(Consts.USER_AGENT)
                .referrer("http://www.google.com")
                .sslSocketFactory(Consts.socketFactory())
                .data("do", "search")
                .data("subaction", "search")
                .data("search_start", String.valueOf(1))
                .data("full_search", "0")
                .data("result_from", String.valueOf(11))
                .data("story", bookName)
                .ignoreHttpErrors(true)
                .maxBodySize(0);

        if(App.useProxy) {
            Proxy proxy = new Proxy(Type.SOCKS,
                    new InetSocketAddress(PROXY_HOST, PROXY_PORT));
            connection.proxy(proxy);
        }

        Document doc = connection.post();


        Element bookList = doc.getElementById("dle-content");
        if (bookList != null) {
            Elements books = bookList.getElementsByClass("short-item");
            if (books.isEmpty()) return result;
            for (Element book : books) {
                OtherArtistPOJO pojo = new OtherArtistPOJO();
                String autorName = "";
                String readerName = "";
                String title = "";
                Elements aTags = book.getElementsByClass("short-title");
                if (aTags.size() != 0) {
                    Element a = aTags.first();
                    if (a != null) {
                        pojo.setUrl(a.attr("href"));
                        title = (a.text());
                    }
                }

                Elements cont = book.getElementsByClass("short-list");

                Element element =  cont.first();
                if (element != null){
                    Elements liTag = element.getElementsByTag("li");
                    for (Element li : liTag) {
                        Element span = li.getElementsByTag("span").first();
                        Element a = li.getElementsByTag("a").first();
                        if (span!=null) {
                            String spanText = span.text();
                            if (a != null) {
                                if (spanText.equals("Автор:")) {
                                    autorName = a.text();
                                } else if (spanText.equals("Читает:")) {
                                    readerName = a.text();
                                }
                            }
                        }

                    }

                }
                pojo.setName("Исполнитель " + readerName);
                if (!readerName.isEmpty()
                        && bookName.equals(title)
                        && bookAuthor.equals(autorName)
                        && !bookUrl.equals(pojo.getUrl())
                        && !bookReader.equals(readerName)) {
                    result.add(pojo);
                }
            }
        }
        return result;
    }

    @UnstableApi
    @Override
    public Observable<ArrayList<OtherArtistPOJO>> getOtherArtist(@NonNull BookPOJO bookPOJO) {
        return Observable.create(observableEmitter -> {
            //waitVpnConetion();
            ArrayList<OtherArtistPOJO> articlesModels;
            try {
                if (bookPOJO.getUrl().contains(Url.SERVER)) {
                    articlesModels = loadSeriesList(bookPOJO.getUrl());
                } else if (bookPOJO.getUrl().contains(Url.SERVER_IZIBUK)) {
                    articlesModels = loadOtherArtistIzibuk(bookPOJO);
                } else if (bookPOJO.getUrl().contains(Url.SERVER_ABMP3)) {
                    articlesModels = loadOtherArtistABMP3(bookPOJO.getName(), bookPOJO.getAutor(), bookPOJO.getUrl(),
                            bookPOJO.getArtist());
                } else if (bookPOJO.getUrl().contains(Url.SERVER_AKNIGA)) {
                    articlesModels = loadOtherArtistAbook(bookPOJO.getName(), bookPOJO.getAutor(), bookPOJO.getUrl(),
                            bookPOJO.getArtist());
                } else if (bookPOJO.getUrl().contains(Url.SERVER_BAZA_KNIG)) {
                    articlesModels = loadOtherArtistBazaKnig(bookPOJO.getName(), bookPOJO.getAutor(),
                            bookPOJO.getUrl(),
                            bookPOJO.getArtist());
                } else if (bookPOJO.getUrl().contains(Url.SERVER_KNIGOBLUD)){
                    articlesModels = loadOtherArtistKnigoblud(bookPOJO.getName(), bookPOJO.getAutor(),
                            bookPOJO.getUrl(),
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

    @UnstableApi
    private ArrayList<OtherArtistPOJO> loadOtherArtistIzibuk(BookPOJO bookPOJO) throws IOException {
        ArrayList<OtherArtistPOJO> result = new ArrayList<>();
        Connection connection = Jsoup.connect(
                        Url.SERVER_IZIBUK + "/search?q=" + bookPOJO.getName() + " " + bookPOJO.getAutor())
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

    private ArrayList<OtherArtistPOJO> loadSeriesList(String url) throws IOException {
        ArrayList<OtherArtistPOJO> result = new ArrayList<>();
        Document doc = Jsoup.connect(url)
                .userAgent(Consts.USER_AGENT)
                .referrer("http://www.google.com")
                .sslSocketFactory(Consts.socketFactory())
                .maxBodySize(0)
                .get();

        Elements elements = doc.getElementsByClass("book_serie_block");
        for (Element element : elements) {
            Elements title = element.getElementsByClass("book_serie_block_title");
            if (title.size() != 0 && title.first().text().contains("Другие варианты озвучки")
                    ||title.first().text().contains("Другие озвучки")) {
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
}
