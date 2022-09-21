package com.fanok.audiobooks.model;


import static de.blinkt.openvpn.core.VpnStatus.waitVpnConetion;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.fanok.audiobooks.Consts;
import com.fanok.audiobooks.Url;
import com.fanok.audiobooks.pojo.BookPOJO;
import com.fanok.audiobooks.pojo.OtherArtistPOJO;
import io.reactivex.Observable;
import java.io.IOException;
import java.util.ArrayList;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

public class OtherSourceModel implements
        com.fanok.audiobooks.interface_pacatge.book_content.OtherArtistModel {


    @Override
    public Observable<ArrayList<OtherArtistPOJO>> getOtherArtist(@NonNull BookPOJO bookPOJO) {
        return Observable.create(observableEmitter -> {
            waitVpnConetion();
            ArrayList<OtherArtistPOJO> articlesModels = new ArrayList<>();
            try {

                if (!bookPOJO.getUrl().contains("akniga.org")) {
                    OtherArtistPOJO artistPOJO = getAbook(bookPOJO);
                    if (artistPOJO != null) {
                        articlesModels.add(artistPOJO);
                    }
                }

                if (!bookPOJO.getUrl().contains("knigavuhe.org")) {
                    OtherArtistPOJO artistPOJO = getKnigaVuhe(bookPOJO);
                    if (artistPOJO != null) {
                        articlesModels.add(artistPOJO);
                    }
                }
                if (!bookPOJO.getUrl().contains("izib.uk")) {
                    OtherArtistPOJO artistPOJO = getIzibuk(bookPOJO);
                    if (artistPOJO != null) {
                        articlesModels.add(artistPOJO);
                    }
                }

                if (!bookPOJO.getUrl().contains("audiobook-mp3.com")) {
                    OtherArtistPOJO artistPOJO = getABMP3(bookPOJO);
                    if (artistPOJO != null) {
                        articlesModels.add(artistPOJO);
                    }
                }

                if (!bookPOJO.getUrl().contains("baza-knig.ru")) {
                    OtherArtistPOJO artistPOJO = getBazaKnig(bookPOJO);
                    if (artistPOJO != null) {
                        articlesModels.add(artistPOJO);
                    }
                }

                observableEmitter.onNext(articlesModels);
            } catch (Exception e) {
                observableEmitter.onError(e);
            } finally {
                observableEmitter.onComplete();
            }
        });
    }

    private OtherArtistPOJO getBazaKnig(BookPOJO bookPOJO) throws IOException {
        int page = 0;
        while (true) {
            page++;
            Connection connection = Jsoup.connect("https://baza-knig.ru/index.php?do=search")
                    .userAgent(Consts.USER_AGENT)
                    .referrer("http://www.google.com")
                    .sslSocketFactory(Consts.socketFactory())
                    .data("do", "search")
                    .data("subaction", "search")
                    .data("search_start", String.valueOf(page))
                    .data("full_search", "0")
                    .data("result_from", "0")
                    .data("story", bookPOJO.getName())
                    .ignoreHttpErrors(true);

            if (!Consts.getBazaKnigCookies().isEmpty()) {
                connection.cookie("PHPSESSID", Consts.getBazaKnigCookies());
            }

            Document doc = connection.post();

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
                        OtherArtistPOJO otherArtistPOJO = new OtherArtistPOJO();
                        String titleResult = "";
                        String author = "";
                        String reader = "";

                        Elements imgConteiner = book.getElementsByClass("short-img");
                        if (imgConteiner != null && imgConteiner.size() > 0) {
                            Element element = imgConteiner.first();
                            Elements aElement = element.getElementsByTag("a");
                            if (aElement != null && aElement.size() > 0) {
                                String aHref = aElement.first().attr("href");
                                if (aHref != null && !aHref.isEmpty()) {
                                    otherArtistPOJO.setUrl(aHref);
                                    otherArtistPOJO.setName("baza-knig.ru");
                                } else {
                                    continue;
                                }
                            }
                        }

                        Elements nameConteiner = book.getElementsByClass("short-title");
                        if (nameConteiner != null && nameConteiner.size() > 0) {
                            Elements aTag = nameConteiner.first().getElementsByTag("a");
                            if (aTag != null && aTag.size() > 0) {
                                String name = aTag.first().text();
                                if (name != null && !name.isEmpty()) {
                                    titleResult = name;
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
                                        if (liText.contains("Автор")) {
                                            Elements autorElements = li.getElementsByTag("b");
                                            if (autorElements != null && autorElements.size() > 0) {
                                                String autorName = autorElements.first().text();
                                                if (autorName != null && !autorName.isEmpty()) {
                                                    author = autorName;
                                                }
                                            }
                                        }

                                        if (liText.contains("Читает")) {
                                            Elements artistElements = li.getElementsByTag("b");
                                            if (artistElements != null && artistElements.size() > 0) {
                                                String artistName = artistElements.first().text();
                                                if (artistName != null && !artistName.isEmpty()) {
                                                    reader = artistName;
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        String[] name = author.split(" ");
                        boolean authorFlag = false;
                        if (author.length() == bookPOJO.getAutor().length()) {
                            authorFlag = true;
                            for (final String s : name) {
                                if (!bookPOJO.getAutor().toLowerCase().contains(s.toLowerCase())) {
                                    authorFlag = false;
                                    break;
                                }

                            }
                        }

                        String[] artist = reader.split(" ");
                        boolean readerFlag = false;
                        if (reader.length() == bookPOJO.getArtist().length()) {
                            readerFlag = true;
                            for (final String s : artist) {
                                if (!bookPOJO.getArtist().toLowerCase().contains(s.toLowerCase())) {
                                    readerFlag = false;
                                    break;
                                }

                            }
                        }

                        if (!reader.isEmpty()
                                && bookPOJO.getName().toLowerCase().equals(titleResult.toLowerCase()) &&
                                authorFlag && readerFlag) {
                            return otherArtistPOJO;
                        }


                    }

                }
            }
        }
        return null;
    }

    private OtherArtistPOJO getABMP3(BookPOJO bookPOJO) throws IOException {
        Document doc = Jsoup.connect(
                "https://audiobook-mp3.com/search?text=" + bookPOJO.getName())
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
                                if (i >= books.size()) {
                                    break;
                                }
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
                                        otherArtistPOJO.setName("audiobook-mp3.com");


                                    }

                                }

                                String[] name = autorName.split(" ");
                                boolean authorFlag = false;
                                if (autorName.length() == bookPOJO.getAutor().length()) {
                                    authorFlag = true;
                                    for (final String s : name) {
                                        if (!bookPOJO.getAutor().toLowerCase().contains(s.toLowerCase())) {
                                            authorFlag = false;
                                            break;
                                        }

                                    }
                                }

                                String[] artist = readerName.split(" ");
                                boolean readerFlag = false;
                                if (readerName.length() == bookPOJO.getArtist().length()) {
                                    readerFlag = true;
                                    for (final String s : artist) {
                                        if (!bookPOJO.getArtist().toLowerCase().contains(s.toLowerCase())) {
                                            readerFlag = false;
                                            break;
                                        }

                                    }
                                }

                                if (!readerName.isEmpty()
                                        && bookPOJO.getName().toLowerCase().equals(bookTitle.toLowerCase()) &&
                                        authorFlag && readerFlag) {
                                    return otherArtistPOJO;
                                }
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    @Nullable
    private OtherArtistPOJO getAbook(BookPOJO bookPOJO) throws IOException {

        Document doc = Jsoup.connect("https://akniga.org/search/books?q=" + bookPOJO.getName() + " "
                + bookPOJO.getAutor() + " " + bookPOJO.getArtist())
                .userAgent(Consts.USER_AGENT)
                .referrer("https://akniga.org/")
                .sslSocketFactory(Consts.socketFactory())
                .get();

        Elements listElements = doc.getElementsByClass("content__main__articles--item");
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

                String url = "";
                String titleResulr = "";
                String author = "";
                String reader = "";

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
                                    String name = a.text();

                                    if (name != null && !name.isEmpty()) {
                                        switch (useHref) {
                                            case "#author":
                                                author = name;
                                                break;
                                            case "#performer":
                                                reader = name;
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
                        titleResulr = title.replace(author + " - ", "");
                    }
                }

                Elements link = book.getElementsByClass("content__article-main-link");
                if (link != null && link.size() != 0) {
                    String src = link.first().attr("href");
                    if (src != null && !src.isEmpty()) {
                        url = src;
                    }
                }

                String[] name = author.split(" ");
                boolean authorFlag = false;
                if (author.length() == bookPOJO.getAutor().length()) {
                    authorFlag = true;
                    for (final String s : name) {
                        if (!bookPOJO.getAutor().toLowerCase().contains(s.toLowerCase())) {
                            authorFlag = false;
                            break;
                        }

                    }
                }

                String[] artist = reader.split(" ");
                boolean readerFlag = false;
                if (reader.length() == bookPOJO.getArtist().length()) {
                    readerFlag = true;
                    for (final String s : artist) {
                        if (!bookPOJO.getArtist().toLowerCase().contains(s.toLowerCase())) {
                            readerFlag = false;
                            break;
                        }

                    }
                }

                if (!titleResulr.isEmpty() && !url.isEmpty() &&
                        titleResulr.toLowerCase().equals(bookPOJO.getName().toLowerCase()) &&
                        authorFlag && readerFlag) {
                    OtherArtistPOJO otherArtistPOJO = new OtherArtistPOJO();
                    otherArtistPOJO.setName("akniga.org");
                    otherArtistPOJO.setUrl(url);
                    return otherArtistPOJO;
                }

            }

        }

        return null;
    }

    @Nullable
    private OtherArtistPOJO getIzibuk(BookPOJO bookPOJO) throws IOException {

        Document doc = Jsoup.connect("https://izib.uk/search?q=" + bookPOJO.getName() + " "
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

                    String[] name = author.split(" ");
                    boolean authorFlag = false;
                    if (author.length() == bookPOJO.getAutor().length()) {
                        authorFlag = true;
                        for (final String s : name) {
                            if (!bookPOJO.getAutor().toLowerCase().contains(s.toLowerCase())) {
                                authorFlag = false;
                                break;
                            }

                        }
                    }

                    String[] artist = reader.split(" ");
                    boolean readerFlag = false;
                    if (reader.length() == bookPOJO.getArtist().length()) {
                        readerFlag = true;
                        for (final String s : artist) {
                            if (!bookPOJO.getArtist().toLowerCase().contains(s.toLowerCase())) {
                                readerFlag = false;
                                break;
                            }

                        }
                    }

                    if (!titleResulr.isEmpty() && !url.isEmpty() &&
                            titleResulr.toLowerCase().equals(bookPOJO.getName().toLowerCase()) &&
                            authorFlag && readerFlag) {

                        OtherArtistPOJO otherArtistPOJO = new OtherArtistPOJO();
                        otherArtistPOJO.setName("izib.uk");
                        otherArtistPOJO.setUrl(url);
                        return otherArtistPOJO;
                    }

                }

            }
        }

        return null;
    }

    @Nullable
    private OtherArtistPOJO getKnigaVuhe(BookPOJO bookPOJO) throws IOException {

        Document doc = Jsoup.connect("https://knigavuhe.org/search/?q=" + bookPOJO.getName()
                + " " + bookPOJO.getAutor())
                .userAgent(Consts.USER_AGENT)
                .referrer("http://www.google.com")
                .sslSocketFactory(Consts.socketFactory())
                .get();

        Element bookList = doc.getElementById("books_updates_list");
        if (bookList == null) {
            bookList = doc.getElementById("books_list");
        }
        if (bookList != null) {
            Elements books = bookList.getElementsByClass("bookkitem");
            if (books.size() == 0) {
                return null;
            }
            for (Element book : books) {

                String title = "";
                String author = "";
                String reader = "";
                String url = "";

                Elements litRes = book.getElementsByClass("bookkitem_litres_icon");
                if (litRes.size() != 0) {
                    continue;
                }
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

                String[] name = author.split(" ");
                boolean authorFlag = false;
                if (author.length() == bookPOJO.getAutor().length()) {
                    authorFlag = true;
                    for (final String s : name) {
                        if (!bookPOJO.getAutor().toLowerCase().contains(s.toLowerCase())) {
                            authorFlag = false;
                            break;
                        }

                    }
                }

                String[] artist = reader.split(" ");
                boolean readerFlag = false;
                if (reader.length() == bookPOJO.getArtist().length()) {
                    readerFlag = true;
                    for (final String s : artist) {
                        if (!bookPOJO.getArtist().toLowerCase().contains(s.toLowerCase())) {
                            readerFlag = false;
                            break;
                        }

                    }
                }

                if (!title.isEmpty() && !url.isEmpty() &&
                        title.toLowerCase().equals(bookPOJO.getName().toLowerCase()) &&
                        authorFlag && readerFlag) {

                    OtherArtistPOJO otherArtistPOJO = new OtherArtistPOJO();
                    otherArtistPOJO.setName("knigavuhe.org");
                    otherArtistPOJO.setUrl(url);
                    return otherArtistPOJO;
                }
            }
        }

        return null;

    }
}
