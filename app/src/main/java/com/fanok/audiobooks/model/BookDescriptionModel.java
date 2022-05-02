package com.fanok.audiobooks.model;

import com.fanok.audiobooks.Consts;
import com.fanok.audiobooks.Url;
import com.fanok.audiobooks.interface_pacatge.book_content.DescriptionModel;
import com.fanok.audiobooks.pojo.BookPOJO;
import com.fanok.audiobooks.pojo.DescriptionPOJO;
import com.fanok.audiobooks.pojo.OtherArtistPOJO;
import io.reactivex.Observable;
import java.io.IOException;
import java.util.ArrayList;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.select.Elements;

public class BookDescriptionModel implements DescriptionModel {

    private final String mUrl;

    private Document mDocument;

    public BookDescriptionModel(String url) {
        mUrl = url;
    }

    private Document getDocument() {
        return mDocument;
    }

    private void setDocument() throws IOException {
        mDocument = Jsoup.connect(mUrl)
                .userAgent(Consts.USER_AGENT)
                .referrer("http://www.google.com")
                .sslSocketFactory(Consts.socketFactory())
                .get();
    }

    @Override
    public Observable<ArrayList<BookPOJO>> getBooks() {
        return Observable.create(observableEmitter -> {
            ArrayList<BookPOJO> bookPOJOArrayList;
            try {
                if (mUrl.contains("knigavuhe.org")) {
                    bookPOJOArrayList = loadBooks();
                } else if (mUrl.contains("audiobook-mp3.com")) {
                    bookPOJOArrayList = loadBooksABMP3();
                } else if (mUrl.contains("akniga.org")) {
                    bookPOJOArrayList = loadBooksAbook();
                } else {
                    bookPOJOArrayList = new ArrayList<>();
                }
                observableEmitter.onNext(bookPOJOArrayList);
            } catch (Exception e) {
                observableEmitter.onError(e);
            } finally {
                observableEmitter.onComplete();
            }
        });
    }

    @Override
    public Observable<DescriptionPOJO> getDescription() {
        return Observable.create(observableEmitter -> {
            DescriptionPOJO articlesModels;
            try {
                if (mUrl.contains("knigavuhe.org")) {
                    articlesModels = loadDescription();
                } else if (mUrl.contains("izib.uk")) {
                    articlesModels = loadDescriptionIzibuk();
                } else if (mUrl.contains("audiobook-mp3.com")) {
                    articlesModels = loadDescriptionABMP3();
                } else if (mUrl.contains("akniga.org")) {
                    articlesModels = loadDescriptionAkniga();
                } else {
                    articlesModels = new DescriptionPOJO();
                }
                observableEmitter.onNext(articlesModels);
            } catch (Exception e) {
                observableEmitter.onError(e);
            } finally {
                observableEmitter.onComplete();
            }
        });
    }

    private ArrayList<BookPOJO> loadBooks() throws IOException {
        ArrayList<BookPOJO> books = new ArrayList<>();
        if (getDocument() == null) {
            setDocument();
        }
        Document document = getDocument();
        Elements root = document.getElementsByClass("suggested");
        if (root.size() > 0) {
            Elements booksConteiner = root.get(0).getElementsByTag("td");
            for (int i = 0; i < booksConteiner.size(); i++) {
                BookPOJO bookPOJO = new BookPOJO();
                Elements aTag = booksConteiner.get(i).getElementsByTag("a");
                if (aTag.size() > 0) {
                    Element a = aTag.first();
                    bookPOJO.setUrl(Url.SERVER + a.attr("href"));
                    Elements img = a.getElementsByTag("img");
                    if (img.size() > 0) {
                        String imgUrl = img.first().attr("src");
                        int lastPos = imgUrl.indexOf("?");
                        if (lastPos != -1) {
                            imgUrl = imgUrl.substring(0, lastPos);
                        }
                        bookPOJO.setPhoto(imgUrl);
                    }
                    Elements name = a.getElementsByClass("suggested_book_name");
                    if (name.size() > 0) {
                        bookPOJO.setName(name.first().text());
                    }
                }
                books.add(bookPOJO);
            }
        }

        return books;
    }

    private ArrayList<BookPOJO> loadBooksABMP3() throws IOException {
        ArrayList<BookPOJO> books = new ArrayList<>();
        if (getDocument() == null) {
            setDocument();
        }
        Document document = getDocument();

        Elements root = document.getElementsByClass("similar-abooks-block");
        if (root != null && root.size() > 0) {
            Elements booksConteiner = root.get(0).getElementsByClass("abook-similar-item");
            if (booksConteiner != null) {
                for (int i = 0; i < booksConteiner.size(); i++) {
                    BookPOJO bookPOJO = new BookPOJO();
                    Elements aTag = booksConteiner.get(i).getElementsByTag("a");
                    if (aTag != null && aTag.size() > 0) {
                        Element a = aTag.first();
                        bookPOJO.setUrl(Url.SERVER_ABMP3 + a.attr("href"));
                        Elements img = a.getElementsByTag("img");
                        if (img != null && img.size() > 0) {
                            String imgUrl = img.first().attr("src");
                            bookPOJO.setPhoto(Url.SERVER_ABMP3 + imgUrl);
                        }
                        Elements name = a.getElementsByTag("span");
                        if (name != null && name.size() > 0) {
                            bookPOJO.setName(name.first().text());
                        }
                    }
                    if (!bookPOJO.isNull()) {
                        books.add(bookPOJO);
                    }
                }
            }
        }

        return books;
    }

    private ArrayList<BookPOJO> loadBooksAbook() throws IOException {
        ArrayList<BookPOJO> books = new ArrayList<>();
        if (getDocument() == null) {
            setDocument();
        }
        Document document = getDocument();

        Elements root = document.getElementsByClass("block-container");
        if (root != null) {
            Elements booksConteiner = new Elements();
            for (Element element : root) {
                booksConteiner.addAll(element.children());
            }
            for (Element element : booksConteiner) {
                BookPOJO bookPOJO = new BookPOJO();
                Elements aTag = element.getElementsByTag("a");
                if (aTag != null && aTag.size() > 0) {
                    Element a = aTag.first();
                    bookPOJO.setUrl(a.attr("href"));
                    Elements img = a.getElementsByTag("img");
                    if (img != null && img.size() > 0) {
                        String imgUrl = img.first().attr("data-src");
                        bookPOJO.setPhoto(imgUrl);
                    }

                    Elements name = a.getElementsByClass("caption");
                    if (name != null && name.size() > 0) {
                        bookPOJO.setName(name.first().text());
                    }
                }
                if (!bookPOJO.isNull()) {
                    books.add(bookPOJO);
                }
            }

        }
        return books;
    }

    private DescriptionPOJO loadDescription() throws IOException {
        DescriptionPOJO descriptionPOJO = new DescriptionPOJO();
        if (getDocument() == null) {
            setDocument();
        }
        Document document = getDocument();
        Elements titleElement = document.getElementsByClass("book_title_elem book_title_name");
        if (titleElement.size() != 0) {
            descriptionPOJO.setTitle(titleElement.first().text().trim());
        }
        Elements retingElements = document.getElementsByClass("book_actions_plays");
        if (retingElements.size() != 0) {
            descriptionPOJO.setReiting(retingElements.first().text().replaceAll(" ", ""));
        }
        Elements posterElements = document.getElementsByClass("book_cover");
        if (posterElements.size() != 0) {
            Elements img = posterElements.first().getElementsByTag("img");
            if (img.size() != 0) {
                String imgUrl = img.first().attr("src");
                int lastPos = imgUrl.indexOf("?");
                if (lastPos != -1) {
                    imgUrl = imgUrl.substring(0, lastPos);
                }

                descriptionPOJO.setPoster(imgUrl);
            }
        }

        Elements timeElements = document.getElementsByClass("book_info_label");
        if (timeElements.size() != 0) {
            Node time = timeElements.first().nextSibling();
            if (time != null) {
                descriptionPOJO.setTime(time.toString().trim());
            }
        }

        Elements autorElements = document.getElementsByAttributeValue("itemprop", "author");
        if (autorElements.size() != 0) {
            Elements aElements = autorElements.first().getElementsByTag("a");
            if (aElements.size() != 0) {
                descriptionPOJO.setAutor(aElements.first().text());
                descriptionPOJO.setAutorUrl(Url.SERVER + aElements.first().attr("href"));
            }
        }

        Elements artistElements = document.getElementsByClass("book_title_elem");

        for (int i = 0; i < artistElements.size(); i++) {
            Element element = artistElements.get(i);
            if (element.text().contains("читает") || element.text().contains("читают")) {
                Elements aTag = element.getElementsByTag("a");
                if (aTag.size() != 0) {
                    descriptionPOJO.setArtist(aTag.first().text());
                    descriptionPOJO.setArtistUrl(Url.SERVER + aTag.first().attr("href"));
                }
            }
        }

        Elements seriesSisterElements = document.getElementsByClass("book_serie_block_title");

        for (int i = 0; i < seriesSisterElements.size(); i++) {
            Element element = seriesSisterElements.get(i);
            if (element.text().contains("Цикл")) {
                Elements seriesElement = element.getElementsByTag("a");
                if (seriesElement.size() != 0) {
                    descriptionPOJO.setSeries(seriesElement.first().text());
                    descriptionPOJO.setSeriesUrl(Url.SERVER + seriesElement.first().attr("href"));
                }
            } else if (element.text().contains("Другие варианты озвучки")) {
                descriptionPOJO.setOtherReader(true);
            }
        }

        Elements descriptionElements = document.getElementsByAttributeValue("itemprop",
                "description");
        if (descriptionElements.size() != 0) {
            descriptionPOJO.setDescription(descriptionElements.first().text().trim());
        }

        Elements genreConteiner = document.getElementsByClass("book_genre_pretitle");
        if (genreConteiner.size() != 0) {
            Elements aTag = genreConteiner.first().getElementsByTag("a");
            if (aTag.size() != 0) {
                Element a = aTag.first();
                descriptionPOJO.setGenre(a.text());
                descriptionPOJO.setGenreUrl(Url.SERVER + a.attr("href") + "<page>/");
            }
        }

        Element favorite = document.getElementById("book_fave_count");
        if (favorite != null) {
            descriptionPOJO.setFavorite(Integer.parseInt(favorite.text()));
        }

        Element like = document.getElementById("book_likes_count");
        if (like != null) {
            descriptionPOJO.setLike(Integer.parseInt(like.text()));
        }

        Element disLike = document.getElementById("book_dislikes_count");
        if (disLike != null) {
            descriptionPOJO.setDisLike(Integer.parseInt(disLike.text()));
        }

        return descriptionPOJO;
    }

    private DescriptionPOJO loadDescriptionABMP3() throws IOException {

        String autor = "";

        DescriptionPOJO descriptionPOJO = new DescriptionPOJO();
        if (getDocument() == null) {
            setDocument();
        }
        Document document = getDocument();

        Elements img = document.getElementsByClass("abook_image");
        if (img != null && img.size() != 0) {
            descriptionPOJO.setPoster(Url.SERVER_ABMP3 + img.first().attr("src"));
        }

        Elements desc = document.getElementsByClass("abook-desc");
        if (desc != null && desc.size() != 0) {
            descriptionPOJO.setDescription(desc.first().ownText());
        }

        Elements infos = document.getElementsByClass("panel-item");
        if (infos != null) {
            for (Element info : infos) {
                if (info.text().contains("Автор")) {
                    Elements element = info.getElementsByTag("a");
                    if (element != null && element.size() != 0) {
                        autor = element.first().text();
                        descriptionPOJO.setAutor(autor);
                        descriptionPOJO.setAutorUrl(Url.SERVER_ABMP3 + element.first().attr("href") + "?page=");
                    }
                } else if (info.text().contains("Читает")) {
                    Elements element = info.getElementsByTag("a");
                    if (element != null && element.size() != 0) {
                        descriptionPOJO.setArtist(element.first().text());
                        descriptionPOJO.setArtistUrl(Url.SERVER_ABMP3 + element.first().attr("href") + "?page=");
                    }
                } else if (info.text().contains("Жанры")) {
                    Elements element = info.getElementsByTag("a");
                    if (element != null && element.size() != 0) {
                        descriptionPOJO.setGenre(element.first().text());
                        descriptionPOJO.setGenreUrl(Url.SERVER_ABMP3 + element.first().attr("href") + "?page=");
                    }
                } else {
                    Elements clock = info.getElementsByClass("fa-clock-o");
                    if (clock != null && clock.size() != 0) {
                        descriptionPOJO.setTime(info.text().trim());
                    }

                    Elements reting = info.getElementsByClass("fa-eye");
                    if (reting != null && reting.size() != 0) {
                        descriptionPOJO.setReiting(info.text().trim());
                    }

                }
            }
        }

        Elements titleElement = document.getElementsByTag("h1");
        if (titleElement.size() != 0) {
            String name = titleElement.first().text().trim();
            if (autor.isEmpty()) {
                descriptionPOJO.setTitle(name);
            } else {
                descriptionPOJO.setTitle(name.replace(autor + " - ", ""));
            }
        }

        Elements like = document.getElementsByClass("vote-count");
        if (like != null && like.size() != 0) {
            descriptionPOJO.setLike(Integer.parseInt(like.first().text()));
        }

        try {
            ArrayList<OtherArtistPOJO> arrayList = OtherArtistModel
                    .loadOtherArtistABMP3(descriptionPOJO.getTitle(), descriptionPOJO.getAutor(), mUrl,
                            descriptionPOJO.getArtist());
            descriptionPOJO.setOtherReader(arrayList.size() != 0);
        } catch (Exception ignored) {
            descriptionPOJO.setOtherReader(false);
        }

        return descriptionPOJO;
    }

    private DescriptionPOJO loadDescriptionAkniga() throws IOException {
        DescriptionPOJO descriptionPOJO = new DescriptionPOJO();
        if (getDocument() == null) {
            setDocument();
        }
        Document document = getDocument();
        String autor = "";

        Elements titleElement = document.getElementsByClass("caption__article-main");

        Elements autorElements = document.getElementsByClass("about-author");
        if (autorElements != null && autorElements.size() != 0) {
            Elements aElements = autorElements.first().getElementsByTag("a");
            if (aElements != null && aElements.size() != 0) {
                autor = aElements.first().text();
                if (autor != null && !autor.isEmpty()) {
                    descriptionPOJO.setAutor(autor);
                }
                String href = aElements.first().attr("href");
                if (href != null && !href.isEmpty()) {
                    descriptionPOJO.setAutorUrl(href + "page<page>/");
                }
            }
        }

        if (titleElement != null && titleElement.size() != 0) {
            descriptionPOJO.setTitle(titleElement.first().text().trim().replace(autor + " - ", ""));
        }

        Elements posterElements = document.getElementsByClass("cover__wrapper--image");
        if (posterElements != null && posterElements.size() != 0) {
            Elements img = posterElements.first().getElementsByTag("img");
            if (img != null && img.size() != 0) {
                String imgUrl = img.first().attr("src");
                if (imgUrl != null && !imgUrl.isEmpty()) {
                    descriptionPOJO.setPoster(imgUrl);
                }
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
            descriptionPOJO.setTime(time.trim());
        }

        Elements artistElements = document.getElementsByClass("link__reader");
        if (artistElements != null && artistElements.size() != 0) {
            String href = artistElements.first().attr("href");
            if (href != null && !href.isEmpty()) {
                descriptionPOJO.setArtistUrl(href + "page<page>/");
            }
            String name = artistElements.first().text();
            if (name != null && !name.isEmpty()) {
                descriptionPOJO.setArtist(name);
            }
        }

        Elements seriesElements = document.getElementsByClass("content__main__book--item--series-list");
        if (seriesElements != null && seriesElements.size() != 0) {
            Elements aTag = seriesElements.first().parent().getElementsByTag("a");
            if (aTag != null && aTag.size() != 0) {
                String href = aTag.first().attr("href");
                if (href != null && !href.isEmpty()) {
                    descriptionPOJO.setSeriesUrl(href);
                }
                String title = aTag.first().text();
                if (title != null && !title.isEmpty()) {
                    descriptionPOJO.setSeries(title);
                }
            }
        }

        Elements descriptionElements = document.getElementsByAttributeValue("itemprop",
                "description");
        if (descriptionElements.size() != 0) {
            descriptionPOJO.setDescription(descriptionElements.first().ownText().trim());
        }

        Elements genreConteiner = document.getElementsByClass("section__title");
        if (genreConteiner != null && genreConteiner.size() != 0) {
            Element a = genreConteiner.first();
            String href = a.attr("href");
            if (href != null && !href.isEmpty()) {
                descriptionPOJO.setGenreUrl(href + "page<page>/");
            }
            String text = a.text();
            if (text != null && !text.isEmpty()) {
                descriptionPOJO.setGenre(text);
            }
        }

        Elements retingElements = document
                .getElementsByClass("link__action--label link__action--label--views pull-right");
        if (retingElements != null && retingElements.size() != 0) {
            descriptionPOJO.setReiting(retingElements.first().text().trim());
        }

        Elements parents = document.getElementsByClass("cover__wrapper--buttons");
        if (parents != null && parents.size() != 0) {
            Element parent = parents.first();

            Elements favorite = parent.getElementsByClass("ls-favourite-count");
            if (favorite != null && favorite.size() != 0) {
                descriptionPOJO.setFavorite(Integer.parseInt(favorite.first().text().trim()));
            }

            Elements like = parent.getElementsByClass("js-vote-rating-up");
            if (like != null && like.size() != 0) {
                descriptionPOJO.setLike(Integer.parseInt(like.first().text().trim()));
            }

            Elements disLike = parent.getElementsByClass("js-vote-rating-down");
            if (disLike != null && disLike.size() != 0) {
                descriptionPOJO.setDisLike(Integer.parseInt(disLike.first().text().trim()));
            }

        }

        try {
            ArrayList<OtherArtistPOJO> arrayList = OtherArtistModel
                    .loadOtherArtistAbook(descriptionPOJO.getTitle(), descriptionPOJO.getAutor(), mUrl,
                            descriptionPOJO.getArtist());
            descriptionPOJO.setOtherReader(arrayList.size() != 0);
        } catch (Exception ignored) {
            descriptionPOJO.setOtherReader(false);
        }

        return descriptionPOJO;
    }

    private DescriptionPOJO loadDescriptionIzibuk() throws IOException {
        DescriptionPOJO descriptionPOJO = new DescriptionPOJO();
        if (getDocument() == null) {
            setDocument();
        }
        Document document = getDocument();

        Elements elements = document.getElementsByAttributeValue("itemprop", "name");
        if (elements != null && elements.size() != 0) {
            String name = elements.first().text();
            if (name != null) {
                descriptionPOJO.setTitle(name);
            }
        }

        Elements imgParent = document.getElementsByClass("_5e0b77");
        if (imgParent != null && imgParent.size() != 0) {
            Element img = imgParent.first().child(0);
            if (img != null) {
                String src = img.attr("src");
                if (src != null) {
                    descriptionPOJO.setPoster(src);
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
                                String href = a.attr("href");
                                if (href != null) {
                                    descriptionPOJO.setAutorUrl(Url.SERVER_IZIBUK + href + "?p=");
                                }
                                String name = a.text();
                                if (name != null) {
                                    descriptionPOJO.setAutor(name);
                                }
                            }
                        } else if (text.contains("Читает")) {
                            Elements aTag = element.getElementsByTag("a");
                            if (aTag != null && aTag.size() != 0) {
                                Element a = aTag.first();
                                String href = a.attr("href");
                                if (href != null) {
                                    descriptionPOJO.setArtistUrl(Url.SERVER_IZIBUK + href + "?p=");
                                }
                                String name = a.text();
                                if (name != null) {
                                    descriptionPOJO.setArtist(name);
                                }
                            }
                        } else if (text.contains("Время")) {
                            String time = text.replace("Время:", "").trim();
                            descriptionPOJO.setTime(time);
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
                String href = a.attr("href");
                if (href != null) {
                    descriptionPOJO.setSeriesUrl(Url.SERVER_IZIBUK + href + "?p=");
                }
                String text = a.text();
                if (text != null) {
                    descriptionPOJO.setSeries(text);
                }
            }
        }

        Elements genreParent = document.getElementsByClass("_7e215f");
        if (genreParent != null && genreParent.size() != 0) {
            Element genreElement = genreParent.first().child(0);
            if (genreElement != null) {
                String href = genreElement.attr("href");
                if (href != null) {
                    descriptionPOJO.setGenreUrl(Url.SERVER_IZIBUK + href + "?p=");
                }
                String text = genreElement.text();
                if (text != null) {
                    descriptionPOJO.setGenre(text);
                }
            }
        }

        Elements descriptionList = document.getElementsByAttributeValue("itemprop", "description");
        if (descriptionList != null && descriptionList.size() != 0) {
            String text = descriptionList.first().text();
            if (text != null) {
                descriptionPOJO.setDescription(text);
            }
        }


        try {
            Document doc = Jsoup.connect(
                    "https://izib.uk/search?q=" + descriptionPOJO.getTitle() + " "
                            + descriptionPOJO.getAutor())
                    .userAgent(Consts.USER_AGENT)
                    .referrer("http://www.google.com")
                    .sslSocketFactory(Consts.socketFactory())
                    .get();

            Element element = doc.getElementById("books_list");
            if (element != null) {
                Elements list = element.getElementsByClass("_ccb9b7");
                if (list != null && list.size() > 1) {
                    descriptionPOJO.setOtherReader(true);
                }
            }
        } catch (Exception ignored) {
            descriptionPOJO.setOtherReader(false);
        }

        return descriptionPOJO;
    }
}
