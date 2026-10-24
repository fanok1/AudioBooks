package com.fanok.audiobooks.pojo;

import androidx.annotation.NonNull;

import com.fanok.audiobooks.Consts;
import com.fanok.audiobooks.Url;
import com.fanok.audiobooks.net.SiteHttp;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.reactivex.Observable;
import java.io.IOException;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.select.Elements;

public class BookPOJO {

    private String photo;
    private String autor;
    private String artist;
    private String urlArtist;
    private String urlAutor;
    private String time;
    private String series;
    private String urlSeries;
    private String genre;
    private String urlGenre;

    private String reting = "0";

    private int coments = 0;

    private String name;

    private String url;

    private String desc;

    public String getName() {
        return name;
    }

    /** The hosts a book page can be loaded from; anything else is rejected before any download. */
    private static final String[] SOURCES = {Url.SERVER, Url.SERVER_IZIBUK, Url.SERVER_ABMP3, Url.SERVER_AKNIGA,
            Url.SERVER_BAZA_KNIG, Url.SERVER_KNIGOBLUD, Url.SERVER_AUDIOBOO, Url.SERVER_OTRUB, Url.SERVER_POLEKNIG};

    /** {@link Url#canonical} of {@code url}; throws {@link IllegalArgumentException} for a host no parser handles. */
    public static String requireSupported(String url) {
        String canonical = Url.canonical(url);
        for (String source : SOURCES) {
            if (Url.isHost(canonical, source)) {
                return canonical;
            }
        }
        throw new IllegalArgumentException("unsupported host: " + url);
    }

    /** Absolute value of {@code attr} (resolved against the page URL) plus {@code suffix}; "" when absent. */
    public static String absUrl(Element element, String attr, String suffix) {
        String url = element.absUrl(attr);
        return url.isEmpty() ? "" : url + suffix;
    }

    public static BookPOJO getBookByUrl(String url) throws IOException {
        return parseBookKnigavuhe(SiteHttp.get(url), url);
    }

    static BookPOJO parseBookKnigavuhe(Document document, String url) {
        BookPOJO bookPOJO = new BookPOJO();

        Elements titleElement = document.getElementsByClass("book_title_elem book_title_name");
        if (!titleElement.isEmpty()) {
            bookPOJO.setName(titleElement.first().text().trim());
        }
        Elements retingElements = document.getElementsByClass("book_views_icon");
        if (!retingElements.isEmpty()) {
            Node retingNode = retingElements.first().nextSibling();
            if (retingNode != null) {
                bookPOJO.setReting(retingNode.toString());
            }
        }
        Elements posterElements = document.getElementsByClass("book_cover");
        if (!posterElements.isEmpty()) {
            Elements img = posterElements.first().getElementsByTag("img");
            if (!img.isEmpty()) {
                String imgUrl = img.first().absUrl("src");
                if (imgUrl != null) {
                    int lastPos = imgUrl.indexOf("?");
                    if (lastPos != -1) {
                        imgUrl = imgUrl.substring(0, lastPos);
                    }
                    bookPOJO.setPhoto(imgUrl);
                }
            }
        }

        Elements timeElements = document.getElementsByClass("book_info_label");

        if (!timeElements.isEmpty() && timeElements.first().text().contains("Время звучания:")) {
            Node timeNode = timeElements.first().nextSibling();
            if (timeNode != null) {
                bookPOJO.setTime(timeNode.toString());
            }
        }

        Elements autorElements = document.getElementsByAttributeValue("itemprop", "author");
        if (!autorElements.isEmpty()) {
            Elements aElements = autorElements.first().getElementsByTag("a");
            if (!aElements.isEmpty()) {
                bookPOJO.setAutor(aElements.first().text());
                bookPOJO.setUrlAutor(absUrl(aElements.first(), "href", ""));
            }
        }

        Elements artistElements = document.getElementsByClass("book_title_elem");

        for (int i = 0; i < artistElements.size(); i++) {
            Element element = artistElements.get(i);
            if (element.text().contains("читает")) {
                Elements aTag = element.getElementsByTag("a");
                if (!aTag.isEmpty()) {
                    bookPOJO.setArtist(aTag.first().text());
                    bookPOJO.setUrlArtist(absUrl(aTag.first(), "href", ""));
                }
            }
        }

        Elements seriesSisterElements = document.getElementsByClass("book_serie_block_title");
        for (int i = 0; i < seriesSisterElements.size(); i++) {
            Element element = seriesSisterElements.get(i);
            if (element.text().contains("Цикл")) {
                Elements seriesElement = element.getElementsByTag("a");
                if (!seriesElement.isEmpty()) {
                    bookPOJO.setSeries(seriesElement.first().text());
                    bookPOJO.setUrlSeries(absUrl(seriesElement.first(), "href", ""));
                }
            }

        }

        Elements genreConteiner = document.getElementsByClass("book_genre_pretitle");
        if (!genreConteiner.isEmpty()) {
            Elements aTag = genreConteiner.first().getElementsByTag("a");
            if (!aTag.isEmpty()) {
                Element a = aTag.first();
                bookPOJO.setGenre(a.text());
                bookPOJO.setUrlGenre(absUrl(a, "href", ""));
            }
        }

        bookPOJO.setUrl(url);

        Element comentsTitle = document.getElementById("comments_count");
        if (comentsTitle != null) {
            bookPOJO.setComents(comentsTitle.text());
        }

        Elements desc = document.getElementsByClass("book_description");
        if (desc != null && !desc.isEmpty()) {
            bookPOJO.setDesc(desc.first().text());
        }

        return bookPOJO;
    }

    public static BookPOJO getBookByUrlABMP3(String url) throws IOException {
        return parseBookABMP3(SiteHttp.get(url), url);
    }

    static BookPOJO parseBookABMP3(Document document, String url) {
        String autor = "";

        BookPOJO bookPOJO = new BookPOJO();

        bookPOJO.setUrl(url);

        Elements img = document.getElementsByClass("abook_image");
        if (img != null && !img.isEmpty()) {
            bookPOJO.setPhoto(img.first().absUrl("src"));
        }

        Elements desc = document.getElementsByClass("abook-desc");
        if (desc != null && !desc.isEmpty()) {
            bookPOJO.setDesc(desc.first().ownText());
        }

        Elements infos = document.getElementsByClass("panel-item");
        if (infos != null) {
            for (Element info : infos) {
                if (info.text().contains("Автор")) {
                    Elements element = info.getElementsByTag("a");
                    if (element != null && !element.isEmpty()) {
                        autor = element.first().text();
                        bookPOJO.setAutor(autor);
                        bookPOJO.setUrlAutor(absUrl(element.first(), "href", "?page="));
                    }
                } else if (info.text().contains("Читает")) {
                    Elements element = info.getElementsByTag("a");
                    if (element != null && !element.isEmpty()) {
                        bookPOJO.setArtist(element.first().text());
                        bookPOJO.setUrlArtist(absUrl(element.first(), "href", "?page="));
                    }
                } else if (info.text().contains("Жанры")) {
                    Elements element = info.getElementsByTag("a");
                    if (element != null && !element.isEmpty()) {
                        bookPOJO.setGenre(element.first().text());
                        bookPOJO.setUrlGenre(absUrl(element.first(), "href", "?page="));
                    }
                } else {
                    Elements clock = info.getElementsByClass("fa-clock-o");
                    if (clock != null && !clock.isEmpty()) {
                        bookPOJO.setTime(info.text().trim());
                    }

                    Elements reting = info.getElementsByClass("fa-eye");
                    if (reting != null && !reting.isEmpty()) {
                        bookPOJO.setReting(info.text().trim());
                    }

                }
            }
        }

        Elements titleElement = document.getElementsByTag("h1");
        if (!titleElement.isEmpty()) {
            String name = titleElement.first().text().trim();
            if (autor.isEmpty()) {
                bookPOJO.setName(name);
            } else {
                bookPOJO.setName(name.replace(autor + " - ", ""));
            }
        }

        return bookPOJO;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        if (url == null || url.trim().isEmpty()) {
            return;
        }
        this.url = url;
    }

    public String getPhoto() {
        return photo;
    }

    public static BookPOJO getBookByUrlAbook(String url) throws IOException {
        return parseBookAkniga(SiteHttp.get(url), url);
    }

    static BookPOJO parseBookAkniga(Document document, String url) {
        String autor = "";

        BookPOJO bookPOJO = new BookPOJO();

        bookPOJO.setUrl(url);

        Elements titleElement = document.getElementsByClass("caption__article-main");

        Elements autorElements = document.getElementsByClass("about-author");
        if (autorElements != null && !autorElements.isEmpty()) {
            Elements aElements = autorElements.first().getElementsByTag("a");
            if (aElements != null && !aElements.isEmpty()) {
                autor = aElements.first().text();
                if (autor != null && !autor.isEmpty()) {
                    bookPOJO.setAutor(autor);
                }
                String href = aElements.first().absUrl("href");
                if (href != null && !href.isEmpty()) {
                    bookPOJO.setUrlAutor(href + "page<page>/");
                }
            }
        }

        if (titleElement != null && titleElement.size() != 0) {
            bookPOJO.setName(titleElement.first().text().trim().replace(autor + " - ", ""));
        }

        Elements posterElements = document.getElementsByClass("cover__wrapper--image");
        if (posterElements != null && !posterElements.isEmpty()) {
            Elements img = posterElements.first().getElementsByTag("img");
            if (img != null && img.size() != 0) {
                String imgUrl = img.first().absUrl("src");
                if (imgUrl != null && !imgUrl.isEmpty()) {
                    bookPOJO.setPhoto(imgUrl);
                }
            }
        }
        if (bookPOJO.getPhoto() == null || bookPOJO.getPhoto().isEmpty()) {
            Elements bgElems = document.getElementsByClass("collection-header-bg");
            if (bgElems == null || bgElems.isEmpty()) {
                bgElems = document.select("[style*='background-image']");
            }
            if (bgElems != null && !bgElems.isEmpty()) {
                for (Element bgElem : bgElems) {
                    String style = bgElem.attr("style");
                    if (style != null && style.contains("url(")) {
                        int start = style.indexOf("url(");
                        int end = style.indexOf(")", start);
                        if (start >= 0 && end > start) {
                            String imgUrl = style.substring(start + 4, end).replace("'", "").replace("\"", "").trim();
                            if (!imgUrl.isEmpty()) {
                                if (imgUrl.startsWith("//")) imgUrl = "https:" + imgUrl;
                                else if (imgUrl.startsWith("/")) imgUrl = Url.SERVER_AKNIGA + imgUrl;
                                bookPOJO.setPhoto(imgUrl);
                                break;
                            }
                        }
                    }
                }
            }
        }
        if (bookPOJO.getPhoto() == null || bookPOJO.getPhoto().isEmpty()) {
            Element metaImg = document.select("meta[property=og:image], meta[name=twitter:image]").first();
            if (metaImg != null) {
                bookPOJO.setPhoto(metaImg.absUrl("content"));
            }
        }

        String time = "";
        Elements timeElements = document.getElementsByClass("hours");
        if (timeElements != null && timeElements.size() != 0) {
            time = timeElements.first().text();
        }

        timeElements = document.getElementsByClass("minutes");
        if (timeElements != null && timeElements.size() != 0) {
            time += " " + timeElements.first().text();
        }
        if (!time.isEmpty()) {
            bookPOJO.setTime(time.trim());
        }

        Elements artistElements = document.getElementsByClass("link__reader");
        if (artistElements != null && artistElements.size() != 0) {
            String href = artistElements.first().absUrl("href");
            if (href != null && !href.isEmpty()) {
                bookPOJO.setUrlArtist(href + "page<page>/");
            }
            String name = artistElements.first().text();
            if (name != null && !name.isEmpty()) {
                bookPOJO.setArtist(name);
            }
        }

        Elements seriesElements = document.select("a[href*=/series/]");
        if (seriesElements != null && !seriesElements.isEmpty()) {
            Element aTag = seriesElements.first();
            String href = aTag.absUrl("href");
            if (href != null && !href.isEmpty()) {
                bookPOJO.setUrlSeries(href);
            }
            String title = aTag.text();
            if (title != null && !title.isEmpty()) {
                bookPOJO.setSeries(title);
            }
        }

        Elements descriptionElements = document.getElementsByClass("description__article-main");
        if (descriptionElements.size() != 0) {
            bookPOJO.setDesc(descriptionElements.first().text().trim());
        } else {
            descriptionElements = document.getElementsByAttributeValue("itemprop", "description");
            if (descriptionElements.size() != 0) {
                bookPOJO.setDesc(descriptionElements.first().ownText().trim());
            }
        }

        Elements genreConteiner = document.getElementsByClass("section__title");
        if (genreConteiner != null && genreConteiner.size() != 0) {
            Element a = genreConteiner.first();
            String href = a.absUrl("href");
            if (href != null && !href.isEmpty()) {
                bookPOJO.setUrlGenre(href + "page<page>/");
            }
            String text = a.text();
            if (text != null && !text.isEmpty()) {
                bookPOJO.setGenre(text);
            }
        }

        Elements retingElements = document
                .getElementsByClass("link__action--label link__action--label--views pull-right");
        if (retingElements != null && retingElements.size() != 0) {
            bookPOJO.setReting(retingElements.first().text().trim());
        }

        return bookPOJO;
    }

    public static BookPOJO getBookByUrlIziBuk(String url) throws IOException {
        return parseBookIzibuk(SiteHttp.get(url), url);
    }

    static BookPOJO parseBookIzibuk(Document document, String url) {
        BookPOJO bookPOJO = new BookPOJO();

        bookPOJO.setUrl(url);

        Elements elements = document.getElementsByAttributeValue("itemprop", "name");
        if (elements != null && elements.size() != 0) {
            String name = elements.first().text();
            if (name != null) {
                bookPOJO.setName(name);
            }
        }

        Elements imgParent = document.getElementsByClass("_5e0b77");
        if (imgParent != null && imgParent.size() != 0) {
            Element img = imgParent.first().firstElementChild();
            if (img != null) {
                String src = img.absUrl("src");
                if (src != null) {
                    bookPOJO.setPhoto(src);
                }
            }
        }

        Elements infoParent = document.getElementsByClass("_b264b2");
        if (infoParent != null && infoParent.size() != 0) {
            Elements info = infoParent.first().children();
            if (info != null) {
                for (Element element : info) {
                    String text = element.text();
                    if (text != null) {
                        if (text.contains("Автор")) {
                            Elements aTag = element.getElementsByTag("a");
                            if (aTag != null && aTag.size() != 0) {
                                Element a = aTag.first();
                                String href = a.absUrl("href");
                                if (!href.isEmpty()) {
                                    bookPOJO.setUrlAutor(href + "?p=");
                                }
                                String name = a.text();
                                if (name != null) {
                                    bookPOJO.setAutor(name);
                                }
                            }
                        } else if (text.contains("Читает")) {
                            Elements aTag = element.getElementsByTag("a");
                            if (aTag != null && aTag.size() != 0) {
                                Element a = aTag.first();
                                String href = a.absUrl("href");
                                if (!href.isEmpty()) {
                                    bookPOJO.setUrlArtist(href + "?p=");
                                }
                                String name = a.text();
                                if (name != null) {
                                    bookPOJO.setArtist(name);
                                }
                            }
                        } else if (text.contains("Время")) {
                            String time = text.replace("Время:", "").trim();
                            bookPOJO.setTime(time);
                        }
                    }
                }
            }
        }

        Elements seriesParent = document.getElementsByClass("_c337c7");
        if (seriesParent != null && seriesParent.size() != 0) {
            Elements aTag = seriesParent.first().getElementsByTag("a");
            if (aTag != null && aTag.size() != 0) {
                Element a = aTag.first();
                String href = a.absUrl("href");
                if (!href.isEmpty()) {
                    bookPOJO.setUrlSeries(href + "?p=");
                }
                String text = a.text();
                if (text != null) {
                    bookPOJO.setSeries(text);
                }
            }
        }

        Elements genreParent = document.getElementsByClass("_7e215f");
        if (genreParent != null && genreParent.size() != 0) {
            Element genreElement = genreParent.first().firstElementChild();
            if (genreElement != null) {
                String href = genreElement.absUrl("href");
                if (!href.isEmpty()) {
                    bookPOJO.setUrlGenre(href + "?p=");
                }
                String text = genreElement.text();
                if (text != null) {
                    bookPOJO.setGenre(text);
                }
            }
        }

        Elements descriptionList = document.getElementsByAttributeValue("itemprop", "description");
        if (descriptionList != null && descriptionList.size() != 0) {
            String text = descriptionList.first().text();
            if (text != null) {
                bookPOJO.setDesc(text);
            }
        }
        return bookPOJO;
    }

    /** Loads the book behind {@code url}: unsupported host → {@link IllegalArgumentException}; fetch and page errors propagate. */
    public static Observable<BookPOJO> getDescription(String url) {
        return Observable.fromCallable(() -> {
            String pageUrl = requireSupported(url);
            return parseBook(SiteHttp.get(pageUrl), pageUrl);
        });
    }

    /** Pure: picks the parser by host; a page without a book title is an {@link IOException}, never an empty book. */
    static BookPOJO parseBook(Document document, String bookUrl) throws IOException {
        String url = requireSupported(bookUrl);
        BookPOJO book;
        if (Url.isHost(url, Url.SERVER)) {
            book = parseBookKnigavuhe(document, url);
        } else if (Url.isHost(url, Url.SERVER_IZIBUK)) {
            book = parseBookIzibuk(document, url);
        } else if (Url.isHost(url, Url.SERVER_ABMP3)) {
            book = parseBookABMP3(document, url);
        } else if (Url.isHost(url, Url.SERVER_AKNIGA)) {
            book = parseBookAkniga(document, url);
        } else if (Url.isHost(url, Url.SERVER_BAZA_KNIG)) {
            book = parseBookBazaKnig(document, url);
        } else if (Url.isHost(url, Url.SERVER_KNIGOBLUD)) {
            book = parseBookKnigoblud(document, url);
        } else if (Url.isHost(url, Url.SERVER_AUDIOBOO)) {
            book = parseBookAudioboo(document, url);
        } else if (Url.isHost(url, Url.SERVER_OTRUB)) {
            book = parseBookOtrub(document, url);
        } else {
            book = parseBookPoleknig(document, url);
        }
        if (book.isNull()) {
            throw new IOException("no book on page: " + url);
        }
        return book;
    }

    static BookPOJO parseBookKnigoblud(Document document, String url) {
        BookPOJO bookPOJO = new BookPOJO();

        Element img = document.getElementById("BookCoverImage");
        if (img != null) {
            bookPOJO.setPhoto(img.absUrl("src"));
        }

        Elements desc = document.getElementsByClass("BookDescriptionContent");
        if (desc != null && desc.size() != 0) {
            bookPOJO.setDesc(desc.first().ownText());
        }

        Elements clock = document.getElementsByClass("PageTitle_Subtitle");
        if (clock != null && clock.size() != 0) {
            bookPOJO.setTime(clock.first().ownText());
        }

        Elements seriesConteiner = document.getElementsByClass("BookDescription BookSeries");
        if (seriesConteiner!=null&&seriesConteiner.size()!=0){
            Elements series = seriesConteiner.first().getElementsByTag("a");
            if (series!=null&&series.size()!=0){
                bookPOJO.setSeries(series.first().text());
                bookPOJO.setUrlSeries(absUrl(series.first(), "href", ""));
            }
        }


        Elements elements = document.getElementsByClass("BookMetaBlock");
        if(elements!=null&&elements.size()!=0) {
            Elements infos = elements.first().getElementsByClass("BookMetaBlockLine");
            if (infos != null) {
                for (Element info : infos) {
                    if (info.text().contains("✍")) {
                        Elements element = info.getElementsByTag("a");
                        if (element != null && element.size() != 0) {
                            String autor = element.first().text();
                            bookPOJO.setAutor(autor);
                            bookPOJO.setUrlAutor(absUrl(element.first(), "href", ""));
                        }
                    } else if (info.text().contains("\uD83C\uDF99")) {
                        Elements element = info.getElementsByTag("a");
                        if (element != null && element.size() != 0) {
                            bookPOJO.setArtist(element.first().text());
                            bookPOJO.setUrlArtist(absUrl(element.first(), "href", ""));
                        }
                    } else if (info.text().contains("\uD83D\uDCD5")) {
                        Elements element = info.getElementsByTag("a");
                        if (element != null && element.size() != 0) {
                            bookPOJO.setGenre(element.first().text());
                            bookPOJO.setUrlGenre(absUrl(element.first(), "href", ""));
                        }
                    }
                }
            }
        }

        Elements titleElement = document.getElementsByTag("h1");
        if (titleElement!=null&&titleElement.size() != 0) {
            String name = titleElement.first().text().trim();
            bookPOJO.setName(name);
        }
        bookPOJO.setUrl(url);
        return bookPOJO;
    }

    static BookPOJO parseBookBazaKnig(Document document, String url) {

        int indexSorce = url.indexOf("?sorce");
        int sorce = 1;
        if (indexSorce != -1) {
            String substring = url.substring(indexSorce).replace("?sorce=", "");
            sorce = Integer.parseInt(substring);
        }

        BookPOJO bookPOJO = new BookPOJO();

        bookPOJO.setUrl(url);

        Elements cont = document.getElementsByClass("reset full-items");
        if (cont != null && cont.size() > 0) {
            Elements liElem = cont.first().getElementsByTag("li");
            if (liElem != null && liElem.size() > 0) {
                for (Element li : liElem) {
                    String liText = li.text();
                    if (liText != null) {
                        if (liText.contains("Автор")) {
                            Elements aTag = li.getElementsByTag("a");
                            if (aTag != null && aTag.size() > 0) {
                                String aHref = aTag.first().absUrl("href");
                                if (!aHref.isEmpty()) {
                                    bookPOJO.setUrlAutor(aHref + "page/");
                                }
                                String autorName = aTag.first().text();
                                if (autorName != null && !autorName.isEmpty()) {
                                    bookPOJO.setAutor(autorName);

                                }
                            }
                        }

                        if (liText.contains("Читает")) {

                            if (sorce == 1) {
                                Elements aTag = li.getElementsByTag("a");
                                if (aTag != null && aTag.size() > 0) {
                                    String aHref = aTag.first().absUrl("href");
                                    if (!aHref.isEmpty()) {
                                        bookPOJO.setUrlArtist(aHref + "page/");
                                    }
                                    String artistName = aTag.first().text();
                                    if (artistName != null && !artistName.isEmpty()) {
                                        bookPOJO.setArtist(artistName);

                                    }
                                }
                            } else if (sorce == 2) {
                                Element otherReader = document.getElementById("content-tab2");
                                if (otherReader != null) {
                                    String text = otherReader.ownText();
                                    if (text != null && text.contains("Озвучивает:")) {
                                        String name = text.replace("Озвучивает:", "").trim();
                                        bookPOJO.setArtist(name);
                                    }
                                }
                            } else if (sorce == 3) {
                                Element otherReader = document.getElementById("content-tab3");
                                if (otherReader != null) {
                                    String text = otherReader.ownText();
                                    if (text != null && text.contains("Озвучивает:")) {
                                        String name = text.replace("Озвучивает:", "").trim();
                                        bookPOJO.setArtist(name);
                                    }
                                }
                            }
                        }

                        if (liText.contains("Длительность")) {
                            Elements timeConteiner = li.getElementsByTag("b");
                            if (timeConteiner != null && timeConteiner.size() > 0) {
                                String time = timeConteiner.first().text();
                                if (time != null && !time.isEmpty()) {
                                    bookPOJO.setTime(time);
                                }
                            }
                        }

                        if (liText.contains("Цикл")) {
                            Elements seriesConteiner = li.getElementsByTag("a");
                            if (seriesConteiner != null && seriesConteiner.size() > 0) {
                                Element a = seriesConteiner.first();
                                String text = a.text();
                                if (text != null && !text.isEmpty()) {
                                    bookPOJO.setSeries(text);
                                    String href = a.absUrl("href");
                                    if (!href.isEmpty()) {
                                        bookPOJO.setUrlSeries(href + "page/");
                                    }
                                }
                            }
                        }

                        if (liText.contains("Жанр")) {
                            Elements genreConteiner = li.getElementsByTag("a");
                            if (genreConteiner != null && genreConteiner.size() > 0) {
                                String text = genreConteiner.first().text();
                                if (text != null && !text.isEmpty()) {
                                    bookPOJO.setGenre(text);
                                    String href = genreConteiner.first().absUrl("href");
                                    if (!href.isEmpty()) {
                                        bookPOJO.setUrlGenre(href + "page/");
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Elements imgConteiner = document.getElementsByClass("full-img");
        if (imgConteiner != null && imgConteiner.size() > 0) {
            Element element = imgConteiner.first();
            Elements img = element.getElementsByTag("img");
            if (img != null && !img.isEmpty()) {
                String src = img.first().absUrl("src");
                if (src != null) {
                    bookPOJO.setPhoto(src);
                }
            }
        }

        Elements title = document.getElementsByTag("h1");
        if (title != null && !title.isEmpty()) {
            String text = title.first().ownText();
            int dash = text.indexOf(" - ");
            bookPOJO.setName((dash == -1 ? text : text.substring(0, dash)).trim());
        }

        Elements desc = document.getElementsByClass("short-text");
        if (desc != null && !desc.isEmpty()) {
            String text = desc.first().ownText();
            bookPOJO.setDesc(text);
        }

        Elements coments = document.getElementsByClass("comments");
        if (coments != null && !coments.isEmpty()) {
            String text = coments.first().text();
            if (text != null && !text.isEmpty()) {
                bookPOJO.setComents(text);
            }
        }
        return bookPOJO;
    }


    public static BookPOJO getBookByUrlAudioboo(String url) throws IOException {
        return parseBookAudioboo(SiteHttp.get(url), url);
    }

    static BookPOJO parseBookAudioboo(Document document, String url) {
        BookPOJO bookPOJO = new BookPOJO();
        bookPOJO.setUrl(url);

        Element descContainer = document.getElementsByClass("page__text").first();
        if (descContainer != null) {
            Element img = descContainer.getElementsByTag("img").first();
            if (img != null) {
                bookPOJO.setPhoto(img.absUrl("data-src"));
            }
            String descText = descContainer.text();
            bookPOJO.setDesc(descText);
            
            if (descText.contains("Продолжительность:")) {
                String timeStr = descText.substring(descText.indexOf("Продолжительность:") + "Продолжительность:".length()).trim();
                if (timeStr.contains(" ")) {
                    timeStr = timeStr.substring(0, timeStr.indexOf(" "));
                }
                bookPOJO.setTime(timeStr);
            }
        }

        Elements infos = document.getElementsByClass("duration list");
        if (infos != null) {
            for (Element info : infos) {
                String text = info.text();
                Element a = info.getElementsByTag("a").first();
                if (a != null) {
                    if (text.contains("Автор")) {
                        bookPOJO.setAutor(a.text());
                        bookPOJO.setUrlAutor(a.absUrl("href"));
                    } else if (text.contains("Исполнитель")) {
                        bookPOJO.setArtist(a.text());
                        bookPOJO.setUrlArtist(a.absUrl("href"));
                    } else if ((text.contains("Цикл") || text.contains("Серия")) && a != null) {
                        bookPOJO.setSeries(a.text());
                        String href = a.absUrl("href");
                        if (href != null && !href.isEmpty()) {
                            bookPOJO.setUrlSeries(href);
                        }
                    } else if (text.contains("Жанр")) {
                        bookPOJO.setGenre(a.text());
                        bookPOJO.setUrlGenre(a.absUrl("href"));
                    }
                }
            }
        }

        if (bookPOJO.getSeries() == null || bookPOJO.getSeries().isEmpty()) {
            Elements seriesElements = document.select("a[href*=/series/]");
            if (seriesElements != null && !seriesElements.isEmpty()) {
                Element a = seriesElements.first();
                bookPOJO.setSeries(a.text());
                String href = a.absUrl("href");
                if (href != null && !href.isEmpty()) {
                    bookPOJO.setUrlSeries(href);
                }
            }
        }

        Element titleElement = document.getElementsByTag("h1").first();
        if (titleElement != null) {
            String name = titleElement.text().trim();
            String author = bookPOJO.getAutor();
            if (author != null && !author.isEmpty() && name.contains(author + " - ")) {
                name = name.replace(author + " - ", "");
            }
            bookPOJO.setName(name);
        }

        Element ratingContainer = document.getElementsByClass("rating").first();
        if (ratingContainer != null) {
            Element currentRating = ratingContainer.selectFirst(".unit-rating .current-rating");
            if (currentRating != null) {
                String style = currentRating.attr("style");
                if (style != null && style.contains("width:")) {
                    String widthStr = style.replaceAll(".*width:\\s*", "").replaceAll("[%\\s;].*", "");
                    try {
                        float width = Float.parseFloat(widthStr);
                        float stars = width / 20.0f;
                        Element voteSpan = document.selectFirst("span[id^=\"vote-num-id-\"]");
                        String votes = voteSpan != null ? voteSpan.text().trim() : "0";
                        if (stars > 0) {
                            bookPOJO.setReting(BookPOJO.formatRating(stars, votes));
                        }
                                            } catch (NumberFormatException ignored) {
                                            }
                                        }
                                    }
                                }

                                Element comentsConteiner = document.getElementsByClass("fa-comment-alt-dots").first();
        if (comentsConteiner != null) {
            Element span = comentsConteiner.parent();
            if (span != null) {
                bookPOJO.setComents(span.ownText().trim());
            }
        }
        return bookPOJO;
    }

    public static BookPOJO getBookByUrlOtrub(String url) throws IOException {
        return parseBookOtrub(SiteHttp.get(url), url);
    }

    static BookPOJO parseBookOtrub(Document document, String url) {
        BookPOJO bookPOJO = new BookPOJO();

        bookPOJO.setUrl(url);

        Elements titleElement = document.select("h1 span[itemprop=name]");
        if (titleElement != null && titleElement.size() > 0) {
            bookPOJO.setName(titleElement.first().text().trim());
        }

        Elements img = document.select("._5e0b77 img");
        if (img != null && img.size() > 0) {
            bookPOJO.setPhoto(img.first().absUrl("src"));
        }

        Elements infoBlocks = document.select("._72ce76 ._5bef20");
        if (infoBlocks != null) {
            for (Element block : infoBlocks) {
                String text = block.text();
                Element a = block.select("a").first();
                if (text.contains("Жанр") && a != null) {
                    bookPOJO.setGenre(a.text());
                    String href = a.absUrl("href");
                    if (href != null && !href.isEmpty()) {
                        bookPOJO.setUrlGenre(href + "?p=");
                    }
                } else if (text.contains("Автор") && a != null) {
                    bookPOJO.setAutor(a.text());
                    String href = a.absUrl("href");
                    if (href != null && !href.isEmpty()) {
                        bookPOJO.setUrlAutor(href);
                    }
                } else if (text.contains("Читает") && a != null) {
                    bookPOJO.setArtist(a.text());
                    String href = a.absUrl("href");
                    if (href != null && !href.isEmpty()) {
                        bookPOJO.setUrlArtist(href);
                    }
                } else if (text.contains("Длительность")) {
                    String time = text.replace("Длительность:", "").trim();
                    if (!time.isEmpty()) {
                        bookPOJO.setTime(time);
                    }
                }
            }
        }

        Elements desc = document.select("._034fb7[itemprop=description]");
        if (desc != null && desc.size() > 0) {
            bookPOJO.setDesc(desc.first().text());
        }

        return bookPOJO;
    }

    public String getAutor() {
        return autor;
    }

    public void setAutor(String autor) {
        if (autor == null || autor.isEmpty()) {
            return;
        }
        this.autor = autor.replaceAll("\\s*\\(https?://[^)]+\\)", "");
    }

    public void setPhoto(String photo) {
        if (photo == null || photo.isEmpty()) {
            return;
        }
        this.photo = photo;
    }

    public String getArtist() {
        return artist;
    }

    public void setArtist(String artist) {
        if (artist == null || artist.isEmpty()) return;
        this.artist = artist;
    }

    public String getUrlArtist() {
        return urlArtist;
    }

    public void setUrlArtist(String urlArtist) {
        if (urlArtist == null || urlArtist.isEmpty()) {
            return;
        }
        this.urlArtist = urlArtist;
    }

    public String getUrlAutor() {
        return urlAutor;
    }

    public void setUrlAutor(String urlAutor) {
        if (urlAutor == null || urlAutor.isEmpty()) {
            return;
        }
        this.urlAutor = urlAutor;
    }

    public String getTime() {
        return time;
    }

    public void setTime(String time) {
        if (time == null || time.isEmpty()) {
            this.time = "";
        } else {
            this.time = Consts.formatTimeDisplay(time);
        }
    }

    public String getSeries() {
        return series;
    }

    public void setSeries(String series) {
        if (series == null || series.isEmpty()) return;
        this.series = series;
    }

    public String getUrlSeries() {
        return urlSeries;
    }

    public void setUrlSeries(String urlSeries) {
        if (urlSeries == null || urlSeries.isEmpty()) return;
        this.urlSeries = urlSeries;
    }

    public String getGenre() {
        return genre;
    }

    public void setGenre(String genre) {
        if (genre == null || genre.isEmpty()) {
            return;
        }
        this.genre = genre;
    }

    public String getUrlGenre() {
        return urlGenre;
    }

    public void setUrlGenre(String urlGenre) {
        if (urlGenre == null || urlGenre.isEmpty()) {
            return;
        }
        this.urlGenre = urlGenre;
    }

    public String getReting() {
        return reting;
    }

    public static BookPOJO parceJsonToBookPojo(String json) {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        return gson.fromJson(json, BookPOJO.class);
    }

    public int getComents() {
        return coments;
    }

    public void setComents(String coments) {
        String digits = coments == null ? "" : coments.replaceAll("[^0-9]", "");
        try {
            this.coments = digits.isEmpty() ? 0 : Integer.parseInt(digits);
        } catch (NumberFormatException e) {
            this.coments = 0;
        }
    }

    public void setReting(String reting) {
        this.reting = reting == null ? "" : reting;
    }

    /** True when the item cannot be shown: no URL or no (non-empty) name. */
    public boolean isNull() {
        return name == null || name.isEmpty() || url == null;
    }

    public void setName(String name) {
        this.name = name == null ? "" : name;
    }

    public String getDesc() {
        return desc;
    }

    public void setDesc(String desc) {
        if (desc == null || desc.isEmpty()) {
            this.desc = "";
        } else {
            this.desc = desc;
        }
    }

    public DescriptionPOJO getDescriptionPOJO() {
        DescriptionPOJO descriptionPOJO = new DescriptionPOJO();
        descriptionPOJO.setTitle(this.name != null ? this.name : "");
        descriptionPOJO.setPoster(this.photo != null ? this.photo : "");
        descriptionPOJO.setReiting(this.reting != null ? this.reting.replaceAll(" ", "") : "0");
        descriptionPOJO.setTime(this.time != null ? this.time : "");
        descriptionPOJO.setAutor(this.autor != null ? this.autor : "");
        descriptionPOJO.setArtist(this.artist != null ? this.artist : "");
        descriptionPOJO.setArtistUrl(this.urlArtist != null ? this.urlArtist : "");
        descriptionPOJO.setAutorUrl(this.urlAutor != null ? this.urlAutor : "");
        descriptionPOJO.setGenre(this.genre != null ? this.genre : "");
        descriptionPOJO.setGenreUrl(this.urlGenre != null ? this.urlGenre : "");
        descriptionPOJO.setSeries(this.series);
        descriptionPOJO.setSeriesUrl(this.urlSeries);
        descriptionPOJO.setDescription(this.desc != null ? this.desc : "");

        return descriptionPOJO;
    }

    public static BookPOJO getBookByUrlPoleknig(String url) throws IOException {
        return parseBookPoleknig(SiteHttp.get(url), url);
    }

    static BookPOJO parseBookPoleknig(Document document, String url) {
        BookPOJO bookPOJO = new BookPOJO();

        bookPOJO.setUrl(url);

        Elements titleElement = document.select("h1[itemprop=name]");
        if (titleElement != null && titleElement.size() > 0) {
            bookPOJO.setName(titleElement.first().text().trim());
        }

        Elements img = document.select("img[src*=/covers/]");
        if (img != null && img.size() > 0) {
            bookPOJO.setPhoto(img.first().absUrl("src"));
        }

        Elements authorEl = document.select("h2.book-author > a");
        if (authorEl != null && authorEl.size() > 0) {
            bookPOJO.setAutor(authorEl.first().text());
            String href = authorEl.first().absUrl("href");
            if (href != null && !href.isEmpty()) {
                bookPOJO.setUrlAutor(href);
            }
        }

        Elements genreBlocks = document.select("div.book-genres > div > a");
        if (genreBlocks != null && genreBlocks.size() > 0) {
            bookPOJO.setGenre(genreBlocks.first().text());
            String href = genreBlocks.first().absUrl("href");
            if (href != null && !href.isEmpty()) {
                bookPOJO.setUrlGenre(href);
            }
        }

        Elements readerEl = document.select("div.book-reader > div > a");
        if (readerEl != null && readerEl.size() > 0) {
            bookPOJO.setArtist(readerEl.first().text());
            String href = readerEl.first().absUrl("href");
            if (href != null && !href.isEmpty()) {
                bookPOJO.setUrlArtist(href);
            }
        }

        Elements seriesEl = document.select("a[href*=/series/]");
        if (seriesEl == null || seriesEl.isEmpty()) {
            seriesEl = document.select("div.book-series > div > a");
        }
        if (seriesEl != null && seriesEl.size() > 0) {
            bookPOJO.setSeries(seriesEl.first().text());
            String href = seriesEl.first().absUrl("href");
            if (href != null && !href.isEmpty()) {
                bookPOJO.setUrlSeries(href);
            }
        }

        Elements durationEl = document.select("div.book-duration > div");
        if (durationEl != null && durationEl.size() > 0) {
            bookPOJO.setTime(durationEl.first().text());
        }

        Elements desc = document.select("div#description[itemprop=description]");
        if (desc != null && desc.size() > 0) {
            bookPOJO.setDesc(desc.first().text());
        }

        return bookPOJO;
    }

    @NonNull
    @Override
    public String toString() {
        return "{" +
                "\"photo\":\"" + photo + "\"" +
                "\"autor\":\"" + autor + "\"" +
                "\"artist\":\"" + artist + "\"" +
                "\"urlArtist\":\"" + urlArtist + "\"" +
                "\"urlAutor\":\"" + urlAutor + "\"" +
                "\"time\":\"" + time + "\"" +
                "\"series\":\"" + series + "\"" +
                "\"urlSeries\":\"" + urlSeries + "\"" +
                "\"genre\":\"" + genre + "\"" +
                "\"urlGenre\":\"" + urlGenre + "\"" +
                "\"reting\":\"" + reting + "\"" +
                "\"coments\":\"" + coments + "\"" +
                "\"name\":\"" + name + "\"" +
                "\"url\":\"" + url + "\"" +
                "}";
    }

    public static String formatRating(float stars, String votes) {
        String starChar = stars >= 4.99f ? "\u2605" : "\u2606";
        return String.format("%s %.1f (%s)", starChar, stars, votes);
    }
    }
