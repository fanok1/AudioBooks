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

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Proxy.Type;
import java.util.ArrayList;
import java.util.Objects;

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
        for (Element item : elements) {
            Elements title = item.getElementsByClass("b-statictop__title");
            if (!title.isEmpty()) {
                if (Objects.requireNonNull(title.first()).text().contains("в книгах")) {
                    Elements parent = item.getElementsByClass("b-statictop__items");
                    if (!parent.isEmpty()) {
                        Elements books = Objects.requireNonNull(parent.first()).children();
                        for (int i = 0; i < books.size(); i++) {
                            String autorName = "";
                            String readerName = "";
                            String bookTitle = "";
                            Element book = books.get(i);
                            OtherArtistPOJO otherArtistPOJO = new OtherArtistPOJO();
                            Elements aTag = book.getElementsByTag("a");
                            if (!aTag.isEmpty()) {
                                String href = Objects.requireNonNull(aTag.first()).attr("href");
                                if (!href.isEmpty()) {
                                    otherArtistPOJO.setUrl(Url.SERVER_ABMP3 + href);
                                }
                                String fullText = Objects.requireNonNull(aTag.first()).text();
                                if (!fullText.isEmpty()) {
                                    String text = fullText.substring(0, fullText.indexOf(" ("));
                                    if (!text.isEmpty()) {
                                        bookTitle = text.trim();
                                    }
                                }

                                Elements info = Objects.requireNonNull(aTag.first()).getElementsByClass("add_info");
                                if (!info.isEmpty()) {
                                    String infoText = Objects.requireNonNull(info.first()).text();
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
        for (Element book : root) {
            String autorName = "";
            String readerName = "";
            String bookTitle = "";
            OtherArtistPOJO otherArtistPOJO = new OtherArtistPOJO();
            Elements aTag = book.getElementsByClass("content__article-main-link tap-link");
            if (!aTag.isEmpty()) {
                String href = Objects.requireNonNull(aTag.first()).attr("href");
                if (!href.isEmpty()) {
                    otherArtistPOJO.setUrl(href);
                }
            }

            Elements title = book.getElementsByClass("caption__article-main");
            if (!title.isEmpty()) {
                String text = Objects.requireNonNull(title.first()).text();
                if (!text.isEmpty()) {
                    bookTitle = text.trim();
                }
            }

            Elements elements = book.getElementsByClass("link__action link__action--author");
            for (Element element : elements) {
                Elements use = element.getElementsByTag("use");
                if (!use.isEmpty()) {
                    String useHref = Objects.requireNonNull(use.first()).attr("xlink:href");
                    aTag = element.getElementsByTag("a");
                    if (!aTag.isEmpty()) {
                        Element a = aTag.first();
                        String name = Objects.requireNonNull(a).text();

                        if (!name.isEmpty()) {
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
            if (!bookList.isEmpty()) {
                for (Element book : bookList) {
                    String url = "";
                    Elements imgConteiner = book.getElementsByClass("short-img");
                    if (!imgConteiner.isEmpty()) {
                        Element element = imgConteiner.first();
                        if(element!=null) {
                            Elements aElement = element.getElementsByTag("a");
                            if (!aElement.isEmpty()) {
                                String aHref = Objects.requireNonNull(aElement.first()).attr("href");
                                if (!aHref.isEmpty()) {
                                    url = aHref;
                                } else {
                                    continue;
                                }
                            }
                        }
                    }

                    Elements cont = book.getElementsByClass("reset short-items");
                    if (!cont.isEmpty()) {
                        Elements liElem = Objects.requireNonNull(cont.first()).getElementsByTag("li");
                        if (!liElem.isEmpty()) {
                            for (Element li : liElem) {
                                String liText = li.text();
                                if (liText.contains("Читает")) {
                                    Elements artistElements = li.getElementsByTag("b");
                                    if (!artistElements.isEmpty()) {
                                        String artistName = liText.replace("Читает: ", "").trim();
                                        if (!artistName.isEmpty()) {
                                            if (artistName.contains("альтернативная озвучка")) {
                                                Connection conn = Jsoup.connect(url)
                                                        .userAgent(Consts.USER_AGENT)
                                                        .referrer("http://www.google.com")
                                                        .sslSocketFactory(Consts.socketFactory())
                                                        .maxBodySize(0)
                                                        .ignoreHttpErrors(true);

                                                if (App.useProxy) {
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
                                                    if (text.contains("Озвучивает:")) {
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
                                                    if (text.contains("Озвучивает:")) {
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

                                            String name = Objects.requireNonNull(artistElements.first()).text();
                                            if (!name.isEmpty() && !baseArtist.contains(name)) {
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
            if (!listElements.isEmpty()) {
                for (Element book : listElements) {
                    OtherArtistPOJO pojo = new OtherArtistPOJO();
                    String autorName = "";
                    String readerName = "";
                    String title = "";


                    Elements urlConteiner = book.getElementsByClass("bookListItemCover");
                    if (!urlConteiner.isEmpty()) {
                        String href = Objects.requireNonNull(urlConteiner.first()).attr("href");
                        if (!href.isEmpty()) {
                            pojo.setUrl(Url.SERVER_KNIGOBLUD + href);
                        }
                    }

                    Elements metaBloks = book.getElementsByClass("bookListItemMetaBlock");
                    for (Element metaBlock : metaBloks) {
                        String metaBlocktext = metaBlock.text();
                        if (metaBlocktext.contains("✍") && metaBlocktext.contains("\uD83C\uDF99")) {
                            Element element = metaBlock.child(1);
                            String text = element.text();
                            if (!text.isEmpty()) {
                                autorName = text;
                            }

                            element = metaBlock.child(3);
                            String t = element.text();
                            if (!t.isEmpty()) {
                                readerName = t;
                            }

                        } else if (metaBlocktext.contains("✍")) {
                            Element element = metaBlock.child(1);
                            String text = element.text();
                            if (!text.isEmpty()) {
                                autorName = text;
                            }
                        } else if (metaBlocktext.contains("\uD83C\uDF99")) {
                            Element element = metaBlock.child(1);
                            String text = element.text();
                            if (!text.isEmpty()) {
                                readerName = text;
                            }
                        }
                    }

                    Elements nameConteiner = book.getElementsByClass("bookListItemCoverNameText");
                    if(!nameConteiner.isEmpty()){
                        String text = Objects.requireNonNull(nameConteiner.first()).text();
                        if(!text.isEmpty()){
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
                if (!aTags.isEmpty()) {
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
            if (list.size() > 1) {
                for (Element book : list) {
                    OtherArtistPOJO pojo = new OtherArtistPOJO();
                    Elements nameCobteiners = book.getElementsByClass("_3dc935");
                    if (nameCobteiners.size() > 1) {
                        Element nameConteiner = nameCobteiners.get(1);
                        String href = nameConteiner.attr("href");
                        pojo.setUrl(Url.SERVER_IZIBUK + href);
                        Elements elements = book.getElementsByClass("_eeab32");
                        for (Element conteiner : elements) {
                            Elements aTag = conteiner.getElementsByTag("a");
                            if (!aTag.isEmpty()) {
                                String name = Objects.requireNonNull(aTag.first()).text();
                                String urlArtist = Objects.requireNonNull(aTag.first()).attr("href");
                                if (urlArtist.contains("reader")) {
                                    if (!name.equals(bookPOJO.getArtist())) {
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
            if (!title.isEmpty() && Objects.requireNonNull(title.first()).text().contains("Другие варианты озвучки")
                    || Objects.requireNonNull(title.first()).text().contains("Другие озвучки")) {
                Elements items = element.getElementsByClass("book_serie_block_item");
                for (Element item : items) {
                    OtherArtistPOJO otherArtistPOJO = new OtherArtistPOJO();
                    Elements a = item.getElementsByTag("a");
                    if (!a.isEmpty()) {
                        otherArtistPOJO.setUrl(Url.SERVER + Objects.requireNonNull(a.first()).attr("href"));
                        otherArtistPOJO.setName(item.text().replace(Objects.requireNonNull(a.first()).text() + " ", ""));
                    }

                    result.add(otherArtistPOJO);
                }
            }
        }

        return result;
    }
}
