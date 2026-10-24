package com.fanok.audiobooks.model;

import static com.fanok.audiobooks.pojo.BookPOJO.absUrl;

import androidx.annotation.OptIn;
import androidx.media3.common.util.UnstableApi;

import com.fanok.audiobooks.App;
import com.fanok.audiobooks.Url;
import com.fanok.audiobooks.interface_pacatge.book_content.DescriptionModel;
import com.fanok.audiobooks.net.SiteHttp;
import com.fanok.audiobooks.pojo.BookPOJO;
import com.fanok.audiobooks.pojo.DescriptionPOJO;
import io.reactivex.Maybe;
import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.functions.Function;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import okhttp3.HttpUrl;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.select.Elements;

/**
 * Book page of any source: downloaded once per model through {@link SiteHttp} and shared by
 * {@link #getDescription()} and {@link #getBooks()}; the static {@code parse*} functions are pure.
 */
public class BookDescriptionModel implements DescriptionModel {

    /** The URL as stored (favorites, history and downloads are keyed by it). */
    private final String mUrl;

    /** {@link #mUrl} on the source's current host. */
    private final String mPageUrl;

    private final Function<String, Document> mFetch;

    /** The in-flight or downloaded page; a failed download is forgotten so the next load retries. Guarded by this. */
    private Single<Document> mPage;

    public BookDescriptionModel(String url) {
        this(url, SiteHttp::get);
    }

    BookDescriptionModel(String url, Function<String, Document> fetch) {
        mUrl = url;
        mPageUrl = Url.canonical(url);
        mFetch = fetch;
    }

    private synchronized Single<Document> page() {
        if (mPage == null) {
            mPage = Single.fromCallable(() -> mFetch.apply(BookPOJO.requireSupported(mPageUrl)))
                    .doOnError(e -> forgetPage())
                    .cache();
        }
        return mPage;
    }

    private synchronized void forgetPage() {
        mPage = null;
    }

    @OptIn(markerClass = UnstableApi.class)
    @Override
    public Observable<ArrayList<BookPOJO>> getBooks() {
        return Single.defer(this::page)
                .map(document -> parseBooks(document, mPageUrl))
                .toObservable();
    }

    @OptIn(markerClass = UnstableApi.class)
    @Override
    public Observable<DescriptionPOJO> getDescription() {
        return Single.defer(this::page)
                .map(document -> withOtherReader(parseDescription(document, mPageUrl)))
                .toObservable();
    }

    /** What favorites, history or downloads saved for this book; empty when nothing is saved. */
    @Override
    public Maybe<DescriptionPOJO> getSavedDescription() {
        return Maybe.fromCallable(() -> {
            if (App.getInstance() == null || mUrl == null) {
                return null;
            }
            BooksDBModel dbModel = new BooksDBModel(App.getInstance());
            try {
                BookPOJO savedBook = dbModel.getSomewhere(mUrl);
                return savedBook == null ? null : savedBook.getDescriptionPOJO();
            } finally {
                dbModel.closeDB();
            }
        });
    }

    /** Pure: picks the description parser by host; a page without a title is an {@link IOException}. */
    static DescriptionPOJO parseDescription(Document document, String bookUrl) throws IOException {
        String url = BookPOJO.requireSupported(bookUrl);
        DescriptionPOJO description;
        if (Url.isHost(url, Url.SERVER)) {
            description = parseDescriptionKnigavuhe(document, url);
        } else if (Url.isHost(url, Url.SERVER_IZIBUK)) {
            description = parseDescriptionIzibuk(document, url);
        } else if (Url.isHost(url, Url.SERVER_ABMP3)) {
            description = parseDescriptionABMP3(document, url);
        } else if (Url.isHost(url, Url.SERVER_AKNIGA)) {
            description = parseDescriptionAkniga(document, url);
        } else if (Url.isHost(url, Url.SERVER_BAZA_KNIG)) {
            description = parseDescriptionBazaKnig(document, url);
        } else if (Url.isHost(url, Url.SERVER_KNIGOBLUD)) {
            description = parseDescriptionKnigoblud(document, url);
        } else if (Url.isHost(url, Url.SERVER_AUDIOBOO)) {
            description = parseDescriptionAudioboo(document, url);
        } else if (Url.isHost(url, Url.SERVER_OTRUB)) {
            description = parseDescriptionOtrub(document, url);
        } else {
            description = parseDescriptionPoleknig(document, url);
        }
        if (description.getTitle().isEmpty()) {
            throw new IOException("no book on page: " + url);
        }
        return description;
    }

    /** Pure: the "other books" block of the page; sources without one give an empty list. */
    static ArrayList<BookPOJO> parseBooks(Document document, String bookUrl) {
        String url = BookPOJO.requireSupported(bookUrl);
        if (Url.isHost(url, Url.SERVER)) {
            return parseBooksKnigavuhe(document);
        } else if (Url.isHost(url, Url.SERVER_ABMP3)) {
            return parseBooksABMP3(document);
        } else if (Url.isHost(url, Url.SERVER_AKNIGA)) {
            return parseBooksAkniga(document);
        } else if (Url.isHost(url, Url.SERVER_BAZA_KNIG)) {
            return parseBooksBazaKnig(document);
        } else if (Url.isHost(url, Url.SERVER_AUDIOBOO)) {
            return parseBooksAudioboo(document);
        }
        return new ArrayList<>();
    }

    /** Network step after parsing: whether the site has other narrations; best effort, never fails the load. */
    @OptIn(markerClass = UnstableApi.class)
    private DescriptionPOJO withOtherReader(DescriptionPOJO description) {
        String title = description.getTitle();
        String autor = description.getAutor();
        String artist = description.getArtist();
        try {
            if (Url.isHost(mPageUrl, Url.SERVER_IZIBUK)) {
                HttpUrl search = Objects.requireNonNull(HttpUrl.parse(Url.SERVER_IZIBUK + "/search")).newBuilder()
                        .addQueryParameter("q", title + " " + autor)
                        .build();
                Element list = SiteHttp.get(search.toString()).getElementById("books_list");
                description.setOtherReader(list != null && list.getElementsByClass("_ccb9b7").size() > 1);
            } else if (Url.isHost(mPageUrl, Url.SERVER_ABMP3)) {
                description.setOtherReader(
                        !OtherArtistModel.loadOtherArtistABMP3(title, autor, mPageUrl, artist).isEmpty());
            } else if (Url.isHost(mPageUrl, Url.SERVER_AKNIGA)) {
                description.setOtherReader(
                        !OtherArtistModel.loadOtherArtistAbook(title, autor, mPageUrl, artist).isEmpty());
            } else if (Url.isHost(mPageUrl, Url.SERVER_BAZA_KNIG)) {
                description.setOtherReader(
                        !OtherArtistModel.loadOtherArtistBazaKnig(title, autor, mPageUrl, artist).isEmpty());
            } else if (Url.isHost(mPageUrl, Url.SERVER_KNIGOBLUD)) {
                description.setOtherReader(
                        !OtherArtistModel.loadOtherArtistKnigoblud(title, autor, mPageUrl, artist).isEmpty());
            } else if (Url.isHost(mPageUrl, Url.SERVER_AUDIOBOO)) {
                description.setOtherReader(
                        !OtherArtistModel.loadOtherArtistAudioboo(title, autor, mPageUrl, artist).isEmpty());
            }
        } catch (Exception ignored) {
            description.setOtherReader(false);
        }
        return description;
    }

    static ArrayList<BookPOJO> parseBooksBazaKnig(Document document) {
        ArrayList<BookPOJO> books = new ArrayList<>();

        Element root = document.getElementById("releative-slider");
        if (root != null) {
            Elements elements = root.getElementsByTag("a");
            for (Element book : elements) {
                BookPOJO bookPOJO = new BookPOJO();
                bookPOJO.setUrl(book.absUrl("href"));

                Element img = book.getElementsByTag("img").first();
                if (img != null) {
                    bookPOJO.setPhoto(img.absUrl("src"));
                    bookPOJO.setName(img.attr("alt"));
                }

                if (!bookPOJO.isNull()) {
                    books.add(bookPOJO);
                }
            }
        }

        return books;
    }

    static ArrayList<BookPOJO> parseBooksAudioboo(Document document) {
        ArrayList<BookPOJO> books = new ArrayList<>();

        //the related block: one cell per book, a cover link and a title link to the same page
        Element root = document.getElementById("somids");
        if (root != null) {
            Set<String> seen = new HashSet<>();
            for (Element cell : root.getElementsByTag("td")) {
                Element img = cell.selectFirst("a[href$=.html] img");
                Element title = cell.selectFirst("a[href$=.html]:not(:has(img))");
                if (title == null || title.absUrl("href").equals(document.location())
                        || !seen.add(title.absUrl("href"))) {
                    continue;
                }
                BookPOJO bookPOJO = new BookPOJO();
                bookPOJO.setUrl(title.absUrl("href"));
                bookPOJO.setName(title.text().trim());
                if (img != null) {
                    bookPOJO.setPhoto(img.absUrl("src"));
                }
                if (!bookPOJO.isNull()) {
                    books.add(bookPOJO);
                }
            }
        }
        return books;
    }

    static ArrayList<BookPOJO> parseBooksKnigavuhe(Document document) {
        ArrayList<BookPOJO> books = new ArrayList<>();
        Elements root = document.getElementsByClass("suggested");
        if (root.size() > 0) {
            Elements booksConteiner = root.get(0).getElementsByTag("td");
            for (int i = 0; i < booksConteiner.size(); i++) {
                BookPOJO bookPOJO = new BookPOJO();
                Elements aTag = booksConteiner.get(i).getElementsByTag("a");
                if (aTag.size() > 0) {
                    Element a = aTag.first();
                    bookPOJO.setUrl(a.absUrl("href"));
                    Elements img = a.getElementsByTag("img");
                    if (img.size() > 0) {
                        bookPOJO.setPhoto(withoutQuery(img.first().absUrl("src")));
                    }
                    Elements name = a.getElementsByClass("suggested_book_name");
                    if (name.size() > 0) {
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

    static ArrayList<BookPOJO> parseBooksABMP3(Document document) {
        ArrayList<BookPOJO> books = new ArrayList<>();

        Elements root = document.getElementsByClass("similar-abooks-block");
        if (root.size() > 0) {
            Elements booksConteiner = root.get(0).getElementsByClass("abook-similar-item");
            for (int i = 0; i < booksConteiner.size(); i++) {
                BookPOJO bookPOJO = new BookPOJO();
                Elements aTag = booksConteiner.get(i).getElementsByTag("a");
                if (aTag.size() > 0) {
                    Element a = aTag.first();
                    bookPOJO.setUrl(a.absUrl("href"));
                    Elements img = a.getElementsByTag("img");
                    if (img.size() > 0) {
                        bookPOJO.setPhoto(img.first().absUrl("src"));
                    }
                    Elements name = a.getElementsByTag("span");
                    if (name.size() > 0) {
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

    static ArrayList<BookPOJO> parseBooksAkniga(Document document) {
        ArrayList<BookPOJO> books = new ArrayList<>();

        Elements booksConteiner = new Elements();
        for (Element element : document.getElementsByClass("block-container")) {
            booksConteiner.addAll(element.children());
        }
        for (Element element : booksConteiner) {
            BookPOJO bookPOJO = new BookPOJO();
            Elements aTag = element.getElementsByTag("a");
            if (aTag.size() > 0) {
                Element a = aTag.first();
                bookPOJO.setUrl(a.absUrl("href"));
                Elements img = a.getElementsByTag("img");
                if (img.size() > 0) {
                    String imgUrl = img.first().absUrl("data-src");
                    if (imgUrl.isEmpty()) {
                        imgUrl = img.first().absUrl("src");
                    }
                    bookPOJO.setPhoto(imgUrl);
                }

                Elements name = a.getElementsByClass("caption");
                if (name.size() > 0) {
                    bookPOJO.setName(name.first().text());
                }
            }
            if (!bookPOJO.isNull()) {
                books.add(bookPOJO);
            }
        }
        return books;
    }

    static DescriptionPOJO parseDescriptionKnigavuhe(Document document, String bookUrl) {
        DescriptionPOJO descriptionPOJO = new DescriptionPOJO();
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
                descriptionPOJO.setPoster(withoutQuery(img.first().absUrl("src")));
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
                descriptionPOJO.setAutorUrl(absUrl(aElements.first(), "href", ""));
            }
        }

        Elements artistElements = document.getElementsByClass("book_title_elem");

        for (int i = 0; i < artistElements.size(); i++) {
            Element element = artistElements.get(i);
            if (element.text().contains("читает") || element.text().contains("читают")) {
                Elements aTag = element.getElementsByTag("a");
                if (aTag.size() != 0) {
                    descriptionPOJO.setArtist(aTag.first().text());
                    descriptionPOJO.setArtistUrl(absUrl(aTag.first(), "href", ""));
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
                    descriptionPOJO.setSeriesUrl(absUrl(seriesElement.first(), "href", ""));
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
                descriptionPOJO.setGenreUrl(absUrl(a, "href", "<page>/"));
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

    static DescriptionPOJO parseDescriptionABMP3(Document document, String bookUrl) {

        String autor = "";

        DescriptionPOJO descriptionPOJO = new DescriptionPOJO();

        Elements img = document.getElementsByClass("abook_image");
        if (img.size() != 0) {
            descriptionPOJO.setPoster(img.first().absUrl("src"));
        }

        Elements desc = document.getElementsByClass("abook-desc");
        if (desc.size() != 0) {
            descriptionPOJO.setDescription(desc.first().ownText());
        }

        Elements infos = document.getElementsByClass("panel-item");
        for (Element info : infos) {
            if (info.text().contains("Автор")) {
                Elements element = info.getElementsByTag("a");
                if (element.size() != 0) {
                    autor = element.first().text();
                    descriptionPOJO.setAutor(autor);
                    descriptionPOJO.setAutorUrl(absUrl(element.first(), "href", "?page="));
                }
            } else if (info.text().contains("Читает")) {
                Elements element = info.getElementsByTag("a");
                if (element.size() != 0) {
                    descriptionPOJO.setArtist(element.first().text());
                    descriptionPOJO.setArtistUrl(absUrl(element.first(), "href", "?page="));
                }
            } else if (info.text().contains("Жанры")) {
                Elements element = info.getElementsByTag("a");
                if (element.size() != 0) {
                    descriptionPOJO.setGenre(element.first().text());
                    descriptionPOJO.setGenreUrl(absUrl(element.first(), "href", "?page="));
                }
            } else {
                Elements clock = info.getElementsByClass("fa-clock-o");
                if (clock.size() != 0) {
                    descriptionPOJO.setTime(info.text().trim());
                }

                Elements reting = info.getElementsByClass("fa-eye");
                if (reting.size() != 0) {
                    descriptionPOJO.setReiting(info.text().trim());
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

        Element like = document.getElementsByClass("vote-count").first();
        if (like != null) {
            descriptionPOJO.setLike(like.text());
        }

        return descriptionPOJO;
    }

    static DescriptionPOJO parseDescriptionKnigoblud(Document document, String bookUrl) {

        DescriptionPOJO descriptionPOJO = new DescriptionPOJO();

        Element img = document.getElementById("BookCoverImage");
        if (img != null) {
            descriptionPOJO.setPoster(img.absUrl("src"));
        }

        Elements desc = document.getElementsByClass("BookDescriptionContent");
        if (desc.size() != 0) {
            descriptionPOJO.setDescription(desc.first().ownText());
        }

        Elements clock = document.getElementsByClass("PageTitle_Subtitle");
        if (clock.size() != 0) {
            descriptionPOJO.setTime(clock.first().ownText());
        }

        Elements seriesConteiner = document.getElementsByClass("BookDescription BookSeries");
        if (seriesConteiner.size() != 0) {
            Elements series = seriesConteiner.first().getElementsByTag("a");
            if (series.size() != 0) {
                descriptionPOJO.setSeries(series.first().text());
                descriptionPOJO.setSeriesUrl(absUrl(series.first(), "href", ""));
            }
        }


        Element metaBlock = document.getElementsByClass("BookMetaBlock").first();
        if (metaBlock != null) {
            for (Element info : metaBlock.getElementsByClass("BookMetaBlockLine")) {
                Element a = info.getElementsByTag("a").first();
                if (a == null) {
                    continue;
                }
                if (info.text().contains("✍")) {
                    descriptionPOJO.setAutor(a.text());
                    descriptionPOJO.setAutorUrl(absUrl(a, "href", ""));
                } else if (info.text().contains("🎙")) {
                    descriptionPOJO.setArtist(a.text());
                    descriptionPOJO.setArtistUrl(absUrl(a, "href", ""));
                } else if (info.text().contains("📕")) {
                    descriptionPOJO.setGenre(a.text());
                    descriptionPOJO.setGenreUrl(absUrl(a, "href", ""));
                }
            }
        }

        Element titleElement = document.getElementsByTag("h1").first();
        if (titleElement != null) {
            descriptionPOJO.setTitle(titleElement.text().trim());
        }

        return descriptionPOJO;
    }

    static DescriptionPOJO parseDescriptionOtrub(Document document, String bookUrl) {
        DescriptionPOJO descriptionPOJO = new DescriptionPOJO();
        Elements titleElement = document.select("h1 span[itemprop=name]");
        if (titleElement.size() > 0) {
            descriptionPOJO.setTitle(titleElement.first().text().trim());
        }

        Elements img = document.select("._5e0b77 img");
        if (img.size() > 0) {
            descriptionPOJO.setPoster(img.first().absUrl("src"));
        }

        Elements infoBlocks = document.select("._72ce76 ._5bef20");
        for (Element block : infoBlocks) {
            String text = block.text();
            Element a = block.select("a").first();
            if (text.contains("Жанр") && a != null) {
                descriptionPOJO.setGenre(a.text());
                descriptionPOJO.setGenreUrl(a.absUrl("href"));
            } else if (text.contains("Автор") && a != null) {
                descriptionPOJO.setAutor(a.text());
                descriptionPOJO.setAutorUrl(a.absUrl("href"));
            } else if (text.contains("Читает") && a != null) {
                descriptionPOJO.setArtist(a.text());
                descriptionPOJO.setArtistUrl(a.absUrl("href"));
            } else if ((text.contains("Цикл") || text.contains("Серия")) && a != null) {
                descriptionPOJO.setSeries(a.text());
                descriptionPOJO.setSeriesUrl(a.absUrl("href"));
            } else if (text.contains("Длительность")) {
                String time = text.replace("Длительность:", "").trim();
                if (!time.isEmpty()) {
                    descriptionPOJO.setTime(time);
                }
            }
        }

        if (descriptionPOJO.getSeries().isEmpty()) {
            Element a = document.select("a[href*=/series/]").first();
            if (a != null) {
                descriptionPOJO.setSeries(a.text());
                descriptionPOJO.setSeriesUrl(a.absUrl("href"));
            }
        }

        Elements desc = document.select("._034fb7[itemprop=description]");
        if (desc.size() > 0) {
            descriptionPOJO.setDescription(desc.first().text());
        }
        return descriptionPOJO;
    }

    static DescriptionPOJO parseDescriptionAudioboo(Document document, String bookUrl) {

        DescriptionPOJO descriptionPOJO = new DescriptionPOJO();

        Element descContainer = document.getElementsByClass("page__text").first();
        if (descContainer != null) {
            Element img = descContainer.getElementsByTag("img").first();
            if (img != null) {
                descriptionPOJO.setPoster(img.absUrl("data-src"));
            }
            String descText = descContainer.text();
            descriptionPOJO.setDescription(descText);

            if (descText.contains("Продолжительность:")) {
                String timeStr = descText.substring(descText.indexOf("Продолжительность:") + "Продолжительность:".length()).trim();
                if (timeStr.contains(" ")) {
                    timeStr = timeStr.substring(0, timeStr.indexOf(" "));
                }
                descriptionPOJO.setTime(timeStr);
            }
        }

        Elements infos = document.getElementsByClass("duration list");
        for (Element info : infos) {
            String text = info.text();
            Element a = info.getElementsByTag("a").first();
            if (a != null) {
                if (text.contains("Автор")) {
                    descriptionPOJO.setAutor(a.text());
                    descriptionPOJO.setAutorUrl(a.absUrl("href"));
                } else if (text.contains("Исполнитель")) {
                    descriptionPOJO.setArtist(a.text());
                    descriptionPOJO.setArtistUrl(a.absUrl("href"));
                } else if (text.contains("Цикл")) {
                    descriptionPOJO.setSeries(a.text());
                    descriptionPOJO.setSeriesUrl(a.absUrl("href"));
                } else if (text.contains("Жанр")) {
                    descriptionPOJO.setGenre(a.text());
                    descriptionPOJO.setGenreUrl(a.absUrl("href"));
                }
            }
        }

        Element titleElement = document.getElementsByTag("h1").first();
        if (titleElement != null) {
            String name = titleElement.text().trim();
            String author = descriptionPOJO.getAutor();
            if (!author.isEmpty() && name.contains(author + " - ")) {
                name = name.replace(author + " - ", "");
            }
            descriptionPOJO.setTitle(name);
        }

        //stars are the width of the rating bar; .figstar is the vote count
        Element stars = document.selectFirst(".card__metas .current-rating");
        Element votes = document.selectFirst(".card__metas .figstar");
        if (stars != null && votes != null) {
            try {
                float value = Float.parseFloat(stars.attr("style").replaceAll(".*width:\\s*", "")
                        .replaceAll("[%\\s;].*", "")) / 20.0f;
                String count = votes.text().trim();
                if (value > 0 && !count.isEmpty() && !count.equals("0")) {
                    descriptionPOJO.setReiting(BookPOJO.formatRating(value, count));
                }
            } catch (NumberFormatException ignored) {
                // no rating rather than a wrong one
            }
        }

        return descriptionPOJO;
    }

    static DescriptionPOJO parseDescriptionAkniga(Document document, String bookUrl) {
        DescriptionPOJO descriptionPOJO = new DescriptionPOJO();
        String autor = "";

        Elements titleElement = document.getElementsByClass("caption__article-main");

        Elements autorElements = document.getElementsByClass("about-author");
        if (!autorElements.isEmpty()) {
            Elements aElements = autorElements.first().getElementsByTag("a");
            if (!aElements.isEmpty()) {
                autor = aElements.first().text();
                descriptionPOJO.setAutor(autor);
                descriptionPOJO.setAutorUrl(absUrl(aElements.first(), "href", "page<page>/"));
            }
        }

        if (titleElement.size() != 0) {
            descriptionPOJO.setTitle(titleElement.first().text().trim().replace(autor + " - ", ""));
        }

        Elements posterElements = document.getElementsByClass("cover__wrapper--image");
        if (posterElements.size() != 0) {
            Elements img = posterElements.first().getElementsByTag("img");
            if (img.size() != 0) {
                descriptionPOJO.setPoster(img.first().absUrl("src"));
            }
        }
        if (descriptionPOJO.getPoster().isEmpty()) {
            Elements bgElems = document.getElementsByClass("collection-header-bg");
            if (bgElems.isEmpty()) {
                bgElems = document.select("[style*='background-image']");
            }
            for (Element bgElem : bgElems) {
                String style = bgElem.attr("style");
                if (style.contains("url(")) {
                    int start = style.indexOf("url(");
                    int end = style.indexOf(")", start);
                    if (start >= 0 && end > start) {
                        String imgUrl = style.substring(start + 4, end).replace("'", "").replace("\"", "").trim();
                        if (!imgUrl.isEmpty()) {
                            if (imgUrl.startsWith("//")) imgUrl = "https:" + imgUrl;
                            else if (imgUrl.startsWith("/")) imgUrl = Url.SERVER_AKNIGA + imgUrl;
                            descriptionPOJO.setPoster(imgUrl);
                            break;
                        }
                    }
                }
            }
        }
        if (descriptionPOJO.getPoster().isEmpty()) {
            Element metaImg = document.select("meta[property=og:image], meta[name=twitter:image]").first();
            if (metaImg != null) {
                descriptionPOJO.setPoster(metaImg.absUrl("content"));
            }
        }

        String time = "";
        Elements timeElements = document.getElementsByClass("hours");
        if (timeElements.size() != 0) {
            time = timeElements.first().text();
        }

        timeElements = document.getElementsByClass("minutes");
        if (timeElements.size() != 0) {
            time += " " + timeElements.first().text();
        }
        if (!time.isEmpty()) {
            descriptionPOJO.setTime(time.trim());
        }

        Elements artistElements = document.getElementsByClass("link__reader");
        if (artistElements.size() != 0) {
            descriptionPOJO.setArtistUrl(absUrl(artistElements.first(), "href", "page<page>/"));
            descriptionPOJO.setArtist(artistElements.first().text());
        }

        Element seriesElement = document.select("a[href*=/series/]").first();
        if (seriesElement != null) {
            descriptionPOJO.setSeriesUrl(seriesElement.absUrl("href"));
            descriptionPOJO.setSeries(seriesElement.text());
        }

        Elements descriptionElements = document.getElementsByClass("description__article-main");
        if (descriptionElements.size() != 0) {
            StringBuilder descBuilder = new StringBuilder();
            for (Element element : descriptionElements) {
                String t = element.text().trim();
                if (!t.isEmpty()) {
                    descBuilder.append(t).append("\n");
                }
            }
            descriptionPOJO.setDescription(descBuilder.toString().trim());
        } else {
            Elements scripts = document.getElementsByTag("script");
            for (Element script : scripts) {
                if ("application/ld+json".equals(script.attr("type"))) {
                    String json = script.html();
                    if (json.contains("\"description\":\"")) {
                        String desc = json.substring(json.indexOf("\"description\":\"") + 15);
                        int end = desc.indexOf("\"");
                        if (end > 0) {
                            descriptionPOJO.setDescription(desc.substring(0, end));
                            break;
                        }
                    }
                }
            }
        }

        Elements genreConteiner = document.getElementsByClass("section__title");
        if (genreConteiner.size() != 0) {
            Element a = genreConteiner.first();
            descriptionPOJO.setGenreUrl(absUrl(a, "href", "page<page>/"));
            descriptionPOJO.setGenre(a.text());
        }

        Elements retingElements = document
                .getElementsByClass("link__action--label link__action--label--views pull-right");
        if (retingElements.size() != 0) {
            descriptionPOJO.setReiting(retingElements.first().text().trim());
        }

        Elements parents = document.getElementsByClass("cover__wrapper--buttons");
        if (parents.size() != 0) {
            Element parent = parents.first();

            Elements favorite = parent.getElementsByClass("ls-favourite-count");
            if (favorite.size() != 0) {
                descriptionPOJO.setFavorite(favorite.first().text().trim());
            }

            Elements like = parent.getElementsByClass("js-vote-rating-up");
            if (like.size() != 0) {
                descriptionPOJO.setLike(like.first().text().trim());
            }

            Elements disLike = parent.getElementsByClass("js-vote-rating-down");
            if (disLike.size() != 0) {
                descriptionPOJO.setDisLike(disLike.first().text().trim());
            }

        }

        return descriptionPOJO;
    }

    static DescriptionPOJO parseDescriptionBazaKnig(Document document, String bookUrl) {

        int indexSorce = bookUrl.indexOf("?sorce");
        int sorce = 1;
        if (indexSorce != -1) {
            String substring = bookUrl.substring(indexSorce).replace("?sorce=", "");
            sorce = Integer.parseInt(substring);
        }

        DescriptionPOJO descriptionPOJO = new DescriptionPOJO();

        Elements cont = document.getElementsByClass("reset full-items");
        if (cont.size() > 0) {
            for (Element li : cont.first().getElementsByTag("li")) {
                String liText = li.text();
                Element a = li.getElementsByTag("a").first();

                if (liText.contains("Автор") && a != null) {
                    descriptionPOJO.setAutorUrl(absUrl(a, "href", "page/"));
                    descriptionPOJO.setAutor(a.text());
                }

                if (liText.contains("Читает")) {
                    if (sorce == 1) {
                        if (a != null) {
                            descriptionPOJO.setArtistUrl(absUrl(a, "href", "page/"));
                            descriptionPOJO.setArtist(a.text());
                        }
                    } else if (sorce == 2 || sorce == 3) {
                        Element otherReader = document.getElementById("content-tab" + sorce);
                        if (otherReader != null) {
                            String text = otherReader.ownText();
                            if (text.contains("Озвучивает:")) {
                                descriptionPOJO.setArtist(text.replace("Озвучивает:", "").trim());
                            }
                        }
                    }
                }

                if (liText.contains("Длительность")) {
                    Element timeConteiner = li.getElementsByTag("b").first();
                    if (timeConteiner != null) {
                        descriptionPOJO.setTime(timeConteiner.text());
                    }
                }

                if (liText.contains("Цикл") && a != null && !a.text().isEmpty()) {
                    descriptionPOJO.setSeries(a.text());
                    descriptionPOJO.setSeriesUrl(absUrl(a, "href", "page/"));
                }

                if (liText.contains("Жанр") && a != null && !a.text().isEmpty()) {
                    descriptionPOJO.setGenre(a.text());
                    descriptionPOJO.setGenreUrl(absUrl(a, "href", "page/"));
                }
            }
        }

        Elements imgConteiner = document.getElementsByClass("full-img");
        if (imgConteiner.size() > 0) {
            Element img = imgConteiner.first().getElementsByTag("img").first();
            if (img != null) {
                descriptionPOJO.setPoster(img.absUrl("src"));
            }
        }

        Element title = document.getElementsByTag("h1").first();
        if (title != null) {
            String text = title.ownText();
            int dash = text.indexOf(" - ");
            descriptionPOJO.setTitle((dash == -1 ? text : text.substring(0, dash)).trim());
        }

        Elements desc = document.getElementsByClass("short-text");
        if (desc.size() > 0) {
            descriptionPOJO.setDescription(desc.first().ownText());
        }

        Elements comentsClass = document.getElementsByClass("short-rate main-sliders-rate ignore-select");
        if (comentsClass.size() > 0) {
            Elements elements = comentsClass.first().getElementsByTag("a");
            if (elements.size() == 2) {
                descriptionPOJO.setLike(elements.first().text());
                descriptionPOJO.setDisLike(elements.last().text());
            }
        }

        return descriptionPOJO;
    }

    static DescriptionPOJO parseDescriptionIzibuk(Document document, String bookUrl) {
        DescriptionPOJO descriptionPOJO = new DescriptionPOJO();

        Elements elements = document.getElementsByAttributeValue("itemprop", "name");
        if (elements.size() != 0) {
            descriptionPOJO.setTitle(elements.first().text());
        }

        Elements imgParent = document.getElementsByClass("_5e0b77");
        if (imgParent.size() != 0) {
            Element img = imgParent.first().firstElementChild();
            if (img != null) {
                descriptionPOJO.setPoster(img.absUrl("src"));
            }
        }

        Elements infoParent = document.getElementsByClass("_b264b2");
        if (infoParent.size() != 0) {
            for (Element element : infoParent.first().children()) {
                String text = element.text();
                Element a = element.getElementsByTag("a").first();
                if (text.contains("Автор")) {
                    if (a != null) {
                        descriptionPOJO.setAutorUrl(absUrl(a, "href", "?p="));
                        descriptionPOJO.setAutor(a.text());
                    }
                } else if (text.contains("Читает")) {
                    if (a != null) {
                        descriptionPOJO.setArtistUrl(absUrl(a, "href", "?p="));
                        descriptionPOJO.setArtist(a.text());
                    }
                } else if (text.contains("Время")) {
                    descriptionPOJO.setTime(text.replace("Время:", "").trim());
                }
            }
        }

        Elements seriesParent = document.getElementsByClass("_c337c7");
        if (seriesParent.size() != 0) {
            Element a = seriesParent.first().getElementsByTag("a").first();
            if (a != null) {
                descriptionPOJO.setSeriesUrl(absUrl(a, "href", "?p="));
                descriptionPOJO.setSeries(a.text());
            }
        }

        Elements genreParent = document.getElementsByClass("_7e215f");
        if (genreParent.size() != 0) {
            Element genreElement = genreParent.first().firstElementChild();
            if (genreElement != null) {
                descriptionPOJO.setGenreUrl(absUrl(genreElement, "href", "?p="));
                descriptionPOJO.setGenre(genreElement.text());
            }
        }

        Elements descriptionList = document.getElementsByAttributeValue("itemprop", "description");
        if (descriptionList.size() != 0) {
            descriptionPOJO.setDescription(descriptionList.first().text());
        }

        return descriptionPOJO;
    }

    static DescriptionPOJO parseDescriptionPoleknig(Document document, String bookUrl) {
        DescriptionPOJO descriptionPOJO = new DescriptionPOJO();
        Elements titleElement = document.select("h1[itemprop=name]");
        if (titleElement.size() > 0) {
            descriptionPOJO.setTitle(titleElement.first().text().trim());
        }

        Element img = document.select("img.cover, img[src*=\"/covers/\"], img[data-original*=\"/covers/\"]").first();
        if (img != null) {
            String src = img.absUrl("src");
            if (src.isEmpty() || src.contains("placeholder")) {
                src = img.absUrl("data-original");
            }
            descriptionPOJO.setPoster(src);
        }

        Element authorEl = document.select("h2.book-author > a").first();
        if (authorEl != null) {
            descriptionPOJO.setAutor(authorEl.text());
            descriptionPOJO.setAutorUrl(authorEl.absUrl("href"));
        }

        Element genre = document.select("div.book-genres > div > a").first();
        if (genre != null) {
            descriptionPOJO.setGenre(genre.text());
            descriptionPOJO.setGenreUrl(genre.absUrl("href"));
        }

        Element readerEl = document.select("div.book-reader > div > a").first();
        if (readerEl != null) {
            descriptionPOJO.setArtist(readerEl.text());
            descriptionPOJO.setArtistUrl(readerEl.absUrl("href"));
        }

        Elements seriesEl = document.select("a[href*=/series/]");
        if (seriesEl.isEmpty()) {
            seriesEl = document.select("div.book-series > div > a");
        }
        if (seriesEl.size() > 0) {
            descriptionPOJO.setSeries(seriesEl.first().text());
            descriptionPOJO.setSeriesUrl(seriesEl.first().absUrl("href"));
        }

        Elements durationEl = document.select("div.book-duration > div");
        if (durationEl.size() > 0) {
            descriptionPOJO.setTime(durationEl.first().text());
        }

        Elements desc = document.select("div#description[itemprop=description]");
        if (desc.size() > 0) {
            descriptionPOJO.setDescription(desc.first().text());
        }
        return descriptionPOJO;
    }

    /** Cover URLs carry cache-busting queries ({@code ?v=1}); the app stores them without. */
    private static String withoutQuery(String url) {
        int query = url.indexOf('?');
        return query == -1 ? url : url.substring(0, query);
    }
}
