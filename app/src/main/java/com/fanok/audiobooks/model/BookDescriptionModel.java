package com.fanok.audiobooks.model;




import static com.fanok.audiobooks.App.useProxy;
import static com.fanok.audiobooks.Consts.PROXY_HOST;
import static com.fanok.audiobooks.Consts.PROXY_PORT;

import androidx.media3.common.util.UnstableApi;

import com.fanok.audiobooks.App;
import com.fanok.audiobooks.Consts;
import com.fanok.audiobooks.CookesExeption;
import com.fanok.audiobooks.Url;
import com.fanok.audiobooks.interface_pacatge.book_content.DescriptionModel;
import com.fanok.audiobooks.pojo.BookPOJO;
import com.fanok.audiobooks.pojo.DescriptionPOJO;
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

    @UnstableApi
    @Override
    public Observable<ArrayList<BookPOJO>> getBooks() {
        return Observable.create(observableEmitter -> {
            ArrayList<BookPOJO> bookPOJOArrayList;
            try {
                if (mUrl.contains(Url.SERVER)) {
                    bookPOJOArrayList = loadBooks();
                } else if (mUrl.contains(Url.SERVER_ABMP3)) {
                    bookPOJOArrayList = loadBooksABMP3();
                } else if (mUrl.contains(Url.SERVER_AKNIGA)) {
                    bookPOJOArrayList = loadBooksAbook();
                } else if (mUrl.contains(Url.SERVER_BAZA_KNIG)) {
                    bookPOJOArrayList = loadBooksBazaKnig();
                } else if (mUrl.contains(Url.SERVER_BOOKOOF)) {
                    bookPOJOArrayList = loadBooksBookoof();
                }else {
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

    @UnstableApi
    @Override
    public Observable<DescriptionPOJO> getDescription() {
        return Observable.create(observableEmitter -> {
            DescriptionPOJO articlesModels;
            //waitVpnConetion();
            try {
                if (mUrl.contains(Url.SERVER)) {
                    articlesModels = loadDescription();
                } else if (mUrl.contains(Url.SERVER_IZIBUK)) {
                    articlesModels = loadDescriptionIzibuk();
                } else if (mUrl.contains(Url.SERVER_ABMP3)) {
                    articlesModels = loadDescriptionABMP3();
                } else if (mUrl.contains(Url.SERVER_AKNIGA)) {
                    articlesModels = loadDescriptionAkniga();
                } else if (mUrl.contains(Url.SERVER_BAZA_KNIG)) {
                    articlesModels = loadDescriptionBazaKnig();
                } else if (mUrl.contains(Url.SERVER_KNIGOBLUD)){
                    articlesModels = loadDescriptionKnigoblud();
                }else if (mUrl.contains(Url.SERVER_BOOKOOF)){
                    articlesModels = loadDescriptionBookoof();
                }else {
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

    @UnstableApi
    private ArrayList<BookPOJO> loadBooksBazaKnig() throws IOException {
        ArrayList<BookPOJO> books = new ArrayList<>();
        if (getDocument() == null) {
            setDocument();
        }
        Document document = getDocument();

        Element root = document.getElementById("releative-slider");
        if (root != null) {
            Elements elements = root.getElementsByTag("a");
            if (elements != null && elements.size() > 0) {
                for (Element book : elements) {
                    BookPOJO bookPOJO = new BookPOJO();

                    String url = book.attr("href");
                    if (url != null && !url.isEmpty()) {
                        bookPOJO.setUrl(url);
                    }

                    Elements img = book.getElementsByTag("img");
                    if (img != null && img.size() > 0) {
                        String src = img.first().attr("src");
                        if (src != null&&!src.isEmpty()) {
                            if(src.contains(Url.SERVER_BAZA_KNIG)){
                                bookPOJO.setPhoto(src);
                            }else {
                                bookPOJO.setPhoto(Url.SERVER_BAZA_KNIG+src);
                            }
                        }
                        String name = img.first().attr("alt");
                        if (name != null && !name.isEmpty()) {
                            bookPOJO.setName(name);
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


    @UnstableApi
    private ArrayList<BookPOJO> loadBooksBookoof() throws IOException {
        ArrayList<BookPOJO> books = new ArrayList<>();
        if (getDocument() == null) {
            setDocument();
        }
        Document document = getDocument();

        Element root = document.getElementById("owl-rels");
        if (root != null) {
            Elements elements = root.getElementsByClass("popular-item");
            for (Element book : elements) {
                BookPOJO bookPOJO = new BookPOJO();
                Element a = book.getElementsByTag("a").first();
                if (a!=null){
                    String url = a.attr("href");
                    bookPOJO.setUrl(url);
                    Element name = a.getElementsByClass("popular-item-title anim").first();
                    if (name!=null) {
                        bookPOJO.setName(name.text());
                    }
                    Element img = a.getElementsByTag("img").first();
                    if (img!=null) {
                        String src = img.attr("data-src");
                        bookPOJO.setPhoto(Url.SERVER_BOOKOOF+src);
                    }
                }
                if (!bookPOJO.isNull()) {
                    books.add(bookPOJO);
                }
            }

        }
        return books;
    }

    @UnstableApi
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

    @UnstableApi
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
                            bookPOJO.setPhoto(imgUrl);
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

    @UnstableApi
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

    @UnstableApi
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
            } else if (element.text().contains("Другие варианты озвучки")||element.text().contains("Другие озвучки")) {
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
            descriptionPOJO.setFavorite(favorite.text());
        }

        Element like = document.getElementById("book_likes_count");
        if (like != null) {
            descriptionPOJO.setLike(like.text());
        }

        Element disLike = document.getElementById("book_dislikes_count");
        if (disLike != null) {
            descriptionPOJO.setDisLike(disLike.text());
        }

        return descriptionPOJO;
    }

    @UnstableApi
    private DescriptionPOJO loadDescriptionABMP3() throws IOException {

        String autor = "";

        DescriptionPOJO descriptionPOJO = new DescriptionPOJO();
        if (getDocument() == null) {
            setDocument();
        }
        Document document = getDocument();

        Elements img = document.getElementsByClass("abook_image");
        if (img != null && img.size() != 0) {
            descriptionPOJO.setPoster(img.first().attr("src"));
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
        if (!like.isEmpty()) {
            descriptionPOJO.setLike(Objects.requireNonNull(like.first()).text());
        }

        try {
            ArrayList<OtherArtistPOJO> arrayList = OtherArtistModel
                    .loadOtherArtistABMP3(descriptionPOJO.getTitle(), descriptionPOJO.getAutor(), mUrl,
                            descriptionPOJO.getArtist());
            descriptionPOJO.setOtherReader(!arrayList.isEmpty());
        } catch (Exception ignored) {
            descriptionPOJO.setOtherReader(false);
        }

        return descriptionPOJO;
    }

    @UnstableApi
    private DescriptionPOJO loadDescriptionKnigoblud() throws IOException {

        DescriptionPOJO descriptionPOJO = new DescriptionPOJO();
        if (getDocument() == null) {
            setDocument();
        }
        Document document = getDocument();

        Element img = document.getElementById("BookCoverImage");
        if (img != null) {
            descriptionPOJO.setPoster(img.attr("src"));
        }

        Elements desc = document.getElementsByClass("BookDescriptionContent");
        if (desc != null && desc.size() != 0) {
            descriptionPOJO.setDescription(desc.first().ownText());
        }

        Elements clock = document.getElementsByClass("PageTitle_Subtitle");
        if (clock != null && clock.size() != 0) {
            descriptionPOJO.setTime(clock.first().ownText());
        }

        Elements seriesConteiner = document.getElementsByClass("BookDescription BookSeries");
        if (seriesConteiner!=null&&seriesConteiner.size()!=0){
            Elements series = seriesConteiner.first().getElementsByTag("a");
            if (series!=null&&series.size()!=0){
                descriptionPOJO.setSeries(series.first().text());
                descriptionPOJO.setSeriesUrl(Url.SERVER_KNIGOBLUD + series.first().attr("href"));
            }
        }


        Elements elements = document.getElementsByClass("BookMetaBlock");
        if(!elements.isEmpty()) {
            Elements infos = Objects.requireNonNull(elements.first()).getElementsByClass("BookMetaBlockLine");
            for (Element info : infos) {
                if (info.text().contains("✍")) {
                    Elements element = info.getElementsByTag("a");
                    if (!element.isEmpty()) {
                        String autor = Objects.requireNonNull(element.first()).text();
                        descriptionPOJO.setAutor(autor);
                        descriptionPOJO.setAutorUrl(Url.SERVER_KNIGOBLUD + Objects.requireNonNull(element.first()).attr("href"));
                    }
                } else if (info.text().contains("\uD83C\uDF99")) {
                    Elements element = info.getElementsByTag("a");
                    if (!element.isEmpty()) {
                        descriptionPOJO.setArtist(Objects.requireNonNull(element.first()).text());
                        descriptionPOJO.setArtistUrl(Url.SERVER_KNIGOBLUD + Objects.requireNonNull(element.first()).attr("href"));
                    }
                } else if (info.text().contains("\uD83D\uDCD5")) {
                    Elements element = info.getElementsByTag("a");
                    if (!element.isEmpty()) {
                        descriptionPOJO.setGenre(Objects.requireNonNull(element.first()).text());
                        descriptionPOJO.setGenreUrl(Url.SERVER_KNIGOBLUD + Objects.requireNonNull(element.first()).attr("href"));
                    }
                }
            }
        }

        Elements titleElement = document.getElementsByTag("h1");
        if (!titleElement.isEmpty()) {
            String name = Objects.requireNonNull(titleElement.first()).text().trim();
            descriptionPOJO.setTitle(name);
        }

        try {
            ArrayList<OtherArtistPOJO> arrayList = OtherArtistModel
                    .loadOtherArtistKnigoblud(descriptionPOJO.getTitle(), descriptionPOJO.getAutor(), mUrl,
                            descriptionPOJO.getArtist());
            descriptionPOJO.setOtherReader(!arrayList.isEmpty());
        } catch (Exception ignored) {
            descriptionPOJO.setOtherReader(false);
        }

        return descriptionPOJO;
    }


    @UnstableApi
    private DescriptionPOJO loadDescriptionBookoof() throws IOException {

        DescriptionPOJO descriptionPOJO = new DescriptionPOJO();
        if (getDocument() == null) {
            setDocument();
        }
        Document document = getDocument();

        Element imgContainer = document.getElementsByClass("fimg img-wide").first();
        if (imgContainer != null) {
            Element img = imgContainer.getElementsByTag("img").first();
            if (img!=null){
                descriptionPOJO.setPoster(Url.SERVER_BOOKOOF+img.attr("data-src"));
            }
        }

        Element desc = document.getElementsByClass("ftext full-text cleasrfix").first();
        if (desc != null) {
            Element p = desc.getElementsByTag("p").first();
            if (p!=null){
                descriptionPOJO.setDescription(p.text());
            }
        }

        Element clock = document.getElementsByClass("xfvalue_duration").first();
        if (clock != null) {
            descriptionPOJO.setTime(clock.ownText());
        }

        Element seriesConteiner = document.getElementsByClass("xfvalue_series").first();
        if (seriesConteiner!=null){
            Element series = seriesConteiner.getElementsByTag("a").first();
            if (series!=null){
                descriptionPOJO.setSeries(series.text());
                descriptionPOJO.setSeriesUrl(series.attr("href"));
            }
        }

        Element authorConteiner = document.getElementsByClass("xfvalue_author").first();
        if (authorConteiner!=null){
            Element author = authorConteiner.getElementsByTag("a").first();
            if (author!=null){
                descriptionPOJO.setAutor(author.text());
                descriptionPOJO.setAutorUrl(author.attr("href"));
            }
        }

        Element artistConteiner = document.getElementsByClass("xfvalue_performer").first();
        if (artistConteiner!=null){
            Element artist = artistConteiner.getElementsByTag("a").first();
            if (artist!=null){
                descriptionPOJO.setArtist(artist.text());
                descriptionPOJO.setArtistUrl(artist.attr("href"));
            }
        }


        Element genreConteiner = document.getElementsByClass("genre").first();
        if (genreConteiner!=null){
            Element genre = genreConteiner.getElementsByTag("a").first();
            if (genre!=null){
                descriptionPOJO.setGenre(genre.text());
                descriptionPOJO.setGenreUrl(genre.attr("href"));
            }
        }


        Element titleElement = document.getElementsByClass("short-title fx-1").first();
        if (titleElement != null) {
            String name = titleElement.ownText().trim();
            descriptionPOJO.setTitle(name.substring(0, name.length() - 2));
        }

        Element ratingContainer = document.getElementsByClass("fal fa-eye").first();
        if (ratingContainer != null) {
            Element span = ratingContainer.parent();
            if (span != null) {
                descriptionPOJO.setReiting(span.ownText().trim());
            }
        }




        try {
            ArrayList<OtherArtistPOJO> arrayList = OtherArtistModel
                    .loadOtherArtistBookoof(descriptionPOJO.getTitle(), descriptionPOJO.getAutor(), mUrl,
                            descriptionPOJO.getArtist());
            descriptionPOJO.setOtherReader(!arrayList.isEmpty());
        } catch (Exception ignored) {
            descriptionPOJO.setOtherReader(false);
        }

        return descriptionPOJO;
    }

    @UnstableApi
    private DescriptionPOJO loadDescriptionAkniga() throws IOException {
        DescriptionPOJO descriptionPOJO = new DescriptionPOJO();
        if (getDocument() == null) {
            setDocument();
        }
        Document document = getDocument();
        String autor = "";

        Elements titleElement = document.getElementsByClass("caption__article-main");

        Elements autorElements = document.getElementsByClass("about-author");
        if (!autorElements.isEmpty()) {
            Elements aElements = Objects.requireNonNull(autorElements.first()).getElementsByTag("a");
            if (!aElements.isEmpty()) {
                autor = Objects.requireNonNull(aElements.first()).text();
                if (!autor.isEmpty()) {
                    descriptionPOJO.setAutor(autor);
                }
                String href = Objects.requireNonNull(aElements.first()).attr("href");
                if (!href.isEmpty()) {
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
                descriptionPOJO.setFavorite(favorite.first().text().trim());
            }

            Elements like = parent.getElementsByClass("js-vote-rating-up");
            if (like != null && like.size() != 0) {
                descriptionPOJO.setLike(like.first().text().trim());
            }

            Elements disLike = parent.getElementsByClass("js-vote-rating-down");
            if (disLike != null && disLike.size() != 0) {
                descriptionPOJO.setDisLike(disLike.first().text().trim());
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

    @UnstableApi
    private DescriptionPOJO loadDescriptionBazaKnig() throws IOException {

        int indexSorce = mUrl.indexOf("?sorce");
        int sorce = 1;
        if (indexSorce != -1) {
            String substring = mUrl.substring(indexSorce).replace("?sorce=", "");
            sorce = Integer.parseInt(substring);
        }

        DescriptionPOJO descriptionPOJO = new DescriptionPOJO();
        if (getDocument() == null) {
            setDocument();
        }
        Document document = getDocument();

        if (document.title().contains("Just a moment")) {
            throw new CookesExeption(document.location());
        }

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
                                String aHref = aTag.first().attr("href");
                                if (aHref != null) {
                                    descriptionPOJO.setAutorUrl(aHref + "page/");
                                }
                                String autorName = aTag.first().text();
                                if (autorName != null && !autorName.isEmpty()) {
                                    descriptionPOJO.setAutor(autorName);

                                }
                            }
                        }

                        if (liText.contains("Читает")) {

                            if (sorce == 1) {
                                Elements aTag = li.getElementsByTag("a");
                                if (aTag != null && aTag.size() > 0) {
                                    String aHref = aTag.first().attr("href");
                                    if (aHref != null) {
                                        descriptionPOJO.setArtistUrl(aHref + "page/");
                                    }
                                    String artistName = aTag.first().text();
                                    if (artistName != null && !artistName.isEmpty()) {
                                        descriptionPOJO.setArtist(artistName);

                                    }
                                }
                            } else if (sorce == 2) {
                                Element otherReader = document.getElementById("content-tab2");
                                if (otherReader != null) {
                                    String text = otherReader.ownText();
                                    if (text != null && text.contains("Озвучивает:")) {
                                        String name = text.replace("Озвучивает:", "").trim();
                                        descriptionPOJO.setArtist(name);
                                    }
                                }
                            } else if (sorce == 3) {
                                Element otherReader = document.getElementById("content-tab3");
                                if (otherReader != null) {
                                    String text = otherReader.ownText();
                                    if (text != null && text.contains("Озвучивает:")) {
                                        String name = text.replace("Озвучивает:", "").trim();
                                        descriptionPOJO.setArtist(name);
                                    }
                                }
                            }
                        }

                        if (liText.contains("Длительность")) {
                            Elements timeConteiner = li.getElementsByTag("b");
                            if (timeConteiner != null && timeConteiner.size() > 0) {
                                String time = timeConteiner.first().text();
                                if (time != null && !time.isEmpty()) {
                                    descriptionPOJO.setTime(time);
                                }
                            }
                        }

                        if (liText.contains("Цикл")) {
                            Elements seriesConteiner = li.getElementsByTag("a");
                            if (seriesConteiner != null && seriesConteiner.size() > 0) {
                                Element a = seriesConteiner.first();
                                String text = a.text();
                                if (text != null && !text.isEmpty()) {
                                    descriptionPOJO.setSeries(text);
                                    String href = a.attr("href");
                                    if (href != null) {
                                        descriptionPOJO.setSeriesUrl(href + "page/");
                                    }
                                }
                            }
                        }

                        if (liText.contains("Жанр")) {
                            Elements genreConteiner = li.getElementsByTag("a");
                            if (genreConteiner != null && genreConteiner.size() > 0) {
                                String text = genreConteiner.first().text();
                                if (text != null && !text.isEmpty()) {
                                    descriptionPOJO.setGenre(text);
                                    String href = genreConteiner.first().attr("href");
                                    if (href != null) {
                                        descriptionPOJO.setGenreUrl(href + "page/");
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
            if (img != null && img.size() > 0) {
                String src = img.first().attr("src");
                if (src != null&&!src.isEmpty()) {
                    if(src.contains(Url.SERVER_BAZA_KNIG)){
                        descriptionPOJO.setPoster(src);
                    }else {
                        descriptionPOJO.setPoster(Url.SERVER_BAZA_KNIG+src);
                    }
                }
            }
        }

        Elements title = document.getElementsByTag("h1");
        if (title != null && title.size() > 0) {
            String text = title.first().ownText();
            descriptionPOJO.setTitle(text.substring(0, text.indexOf(" - ")));
        }

        Elements desc = document.getElementsByClass("short-text");
        if (desc != null && desc.size() > 0) {
            String text = desc.first().ownText();
            descriptionPOJO.setDescription(text);
        }

        Elements comentsClass = document.getElementsByClass("short-rate main-sliders-rate ignore-select");
        if (comentsClass != null && comentsClass.size() > 0) {
            Elements elements = comentsClass.first().getElementsByTag("a");
            if (elements != null && elements.size() == 2) {
                String like = elements.first().text();
                if (like != null && !like.isEmpty()) {
                    descriptionPOJO.setLike(like);
                }

                String dislike = elements.last().text();
                if (dislike != null && !dislike.isEmpty()) {
                    descriptionPOJO.setDisLike(dislike);
                }
            }
        }

        try {
            ArrayList<OtherArtistPOJO> arrayList = OtherArtistModel
                    .loadOtherArtistBazaKnig(descriptionPOJO.getTitle(), descriptionPOJO.getAutor(), mUrl,
                            descriptionPOJO.getArtist());
            descriptionPOJO.setOtherReader(arrayList.size() != 0);
        } catch (Exception ignored) {
            descriptionPOJO.setOtherReader(false);
        }

        return descriptionPOJO;
    }

    @UnstableApi
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



            Connection connection = Jsoup.connect(
                            Url.SERVER_IZIBUK + "/search?q=" + descriptionPOJO.getTitle() + " "
                                    + descriptionPOJO.getAutor())
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
                    descriptionPOJO.setOtherReader(true);
                }
            }
        } catch (Exception ignored) {
            descriptionPOJO.setOtherReader(false);
        }

        return descriptionPOJO;
    }

    @UnstableApi
    private void setDocument() throws IOException {
        Connection connection = Jsoup.connect(mUrl)
                .userAgent(Consts.USER_AGENT)
                .referrer("http://www.google.com")
                .sslSocketFactory(Consts.socketFactory())
                .maxBodySize(0)
                .ignoreHttpErrors(true);
        if (!Consts.getBazaKnigCookies().isEmpty() && mUrl.contains(Url.SERVER_BAZA_KNIG)) {
            connection.cookie("PHPSESSID", Consts.getBazaKnigCookies());
        }

        if(useProxy && (mUrl.contains(Url.SERVER_IZIBUK)||mUrl.contains(Url.SERVER_ABMP3)||mUrl.contains(Url.SERVER_BAZA_KNIG)
                ||mUrl.contains(Url.SERVER_BOOKOOF))){
            Proxy proxy = new Proxy(Type.SOCKS,
                    new InetSocketAddress(PROXY_HOST, PROXY_PORT));
            connection.proxy(proxy);
        }
        mDocument = connection.get();
    }
}
