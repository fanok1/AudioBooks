package com.fanok.audiobooks.model;


import com.fanok.audiobooks.Url;
import com.fanok.audiobooks.net.SiteHttp;
import com.fanok.audiobooks.pojo.GenrePOJO;
import com.fanok.audiobooks.search.Provider;
import com.fanok.audiobooks.search.ProviderPage;
import io.reactivex.Single;
import java.io.IOException;
import java.util.ArrayList;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

/** Genre lists per provider; {@link AutorsModel} overrides the per-provider loaders for authors and performers. */
public class GenreModel implements com.fanok.audiobooks.interface_pacatge.books.GenreModel {

    interface Row {
        GenrePOJO parse(Element row) throws Exception;
    }

    /** Parses every row on its own: one malformed row (empty name, bad URL) is skipped, not the page. */
    static ArrayList<GenrePOJO> rows(Elements rows, Row parser) {
        ArrayList<GenrePOJO> out = new ArrayList<>();
        for (Element row : rows) {
            try {
                GenrePOJO item = parser.parse(row);
                if (item != null && !item.isNull()) {
                    out.add(item);
                }
            } catch (Exception ignored) {
            }
        }
        return out;
    }

    @Override
    public Single<ProviderPage<GenrePOJO>> getBooks(String url, int page) {
        return Single.fromCallable(() -> load(url, page));
    }

    ProviderPage<GenrePOJO> load(String url, int page) throws IOException {
        Provider provider = Provider.ofUrl(url);
        if (provider == null) {
            return ProviderPage.empty();
        }
        switch (provider) {
            case KNIGAVUHE: return loadBooksList(url, page);
            case IZIBUK: return loadBooksListIzibuk(url, page);
            case ABMP3: return loadBooksListABMP3(url, page);
            case AKNIGA: return loadBooksListAbook(url, page);
            case BAZA_KNIG: return loadBooksListBazaKnig(url);
            case KNIGOBLUD: return loadBooksListKnigoblud();
            case AUDIOBOO: return loadBooksListAudioboo(url, page);
            case OTRUB: return loadBooksListOtrub(url);
            case POLEKNIG: return loadBooksListPoleknig(url);
        }
        return ProviderPage.empty();
    }

    protected ProviderPage<GenrePOJO> loadBooksList(String url, int page) throws IOException {
        Document doc = SiteHttp.get(url);
        return ProviderPage.of(rows(doc.getElementsByClass("genre2_item"), item -> {
            GenrePOJO genrePOJO = new GenrePOJO();
            Element name = item.selectFirst(".genre2_item_name");
            if (name != null) {
                genrePOJO.setUrl(Url.SERVER + name.attr("href") + "<page>/");
                genrePOJO.setName(name.text());
            }
            Element rating = item.selectFirst(".subscribe_btn_label_count");
            if (rating != null) {
                genrePOJO.setReting(Integer.parseInt(rating.text().trim()));
            }
            Element description = item.selectFirst(".genre2_item_description");
            if (description != null) {
                genrePOJO.setDescription(description.text());
            }
            return genrePOJO;
        }), false);
    }

    protected ProviderPage<GenrePOJO> loadBooksListABMP3(String url, int page) throws IOException {
        Document doc = SiteHttp.get(url);
        Element items = doc.selectFirst(".b-posts");
        if (items == null) {
            return ProviderPage.empty();
        }
        return ProviderPage.of(rows(items.children(), item -> {
            GenrePOJO genrePOJO = new GenrePOJO();
            Element aGenre = item.selectFirst(".title a");
            if (aGenre != null) {
                genrePOJO.setUrl(Url.SERVER_ABMP3 + aGenre.attr("href") + "?page=");
                genrePOJO.setName(aGenre.text());
            }
            Element ratings = item.selectFirst(".rating");
            if (ratings != null) {
                genrePOJO.setDescription(ratings.text().trim().replace("кни", " кни"));
            }
            return genrePOJO;
        }), false);
    }

    protected ProviderPage<GenrePOJO> loadBooksListAbook(String url, int page) throws IOException {
        Document doc = SiteHttp.get(url);
        return ProviderPage.of(parseAbookTable(doc), false);
    }

    /** Rows of akniga's authors/performers/sections table (also the shape of its ajax search answer). */
    static ArrayList<GenrePOJO> parseAbookTable(Document doc) {
        return rows(doc.select(".table-authors tbody > tr, table > tbody > tr"), item -> {
            GenrePOJO genrePOJO = new GenrePOJO();
            Element aGenre = item.selectFirst(".name-obj a");
            if (aGenre != null) {
                genrePOJO.setUrl(aGenre.attr("href") + "page<page>/");
                genrePOJO.setName(aGenre.text());
            }
            Element description = item.selectFirst(".description");
            if (description != null) {
                genrePOJO.setDescription(description.text().trim());
            }
            Element reting = item.selectFirst(".cell-rating");
            if (reting != null) {
                String text = reting.text().trim().split("\n")[0].trim();
                text = text.replace("K", "000").replace(".", "").replaceAll("[^0-9]", "");
                if (!text.isEmpty()) {
                    genrePOJO.setReting(Integer.parseInt(text));
                }
            }
            return genrePOJO;
        });
    }

    protected ProviderPage<GenrePOJO> loadBooksListIzibuk(String url, int page) throws IOException {
        Document doc = SiteHttp.get(url);
        Element items = doc.selectFirst("._e181af");
        if (items == null) {
            return ProviderPage.empty();
        }
        return ProviderPage.of(rows(items.children(), item -> {
            GenrePOJO genrePOJO = new GenrePOJO();
            genrePOJO.setUrl(Url.SERVER_IZIBUK + item.attr("href") + "?p=");
            Elements children = item.children();
            if (children.size() == 2) {
                genrePOJO.setName(children.first().text());
                genrePOJO.setDescription(children.last().text());
            }
            return genrePOJO;
        }), false);
    }

    private ProviderPage<GenrePOJO> loadBooksListBazaKnig(String url) throws IOException {
        Document doc = SiteHttp.get(url);
        return ProviderPage.of(rows(doc.select(".left-menu-items li"), item -> {
            GenrePOJO genrePOJO = new GenrePOJO();
            Element aGenre = item.selectFirst("a");
            if (aGenre != null) {
                genrePOJO.setUrl(Url.SERVER_BAZA_KNIG + aGenre.attr("href") + "page/");
                genrePOJO.setName(item.text());
            }
            return genrePOJO;
        }), false);
    }

    private ProviderPage<GenrePOJO> loadBooksListKnigoblud() {
        ArrayList<GenrePOJO> result = new ArrayList<>();
        result.add(new GenrePOJO("Фантастика, фэнтези", Url.SERVER_KNIGOBLUD+"/9d47c7aa-9e2e-4fe0-bcd5-14f7d4874198"));
        result.add(new GenrePOJO("Детективы, триллеры, боевики", Url.SERVER_KNIGOBLUD+"/4daab267-3592-4604-9e34-8f3b6a1db9fe"));
        result.add(new GenrePOJO("Аудиоспектакли, радиопостановки и литературные чтения", Url.SERVER_KNIGOBLUD+"/0efa0796-8734-4735-ac03-d4eca1ccb013"));
        result.add(new GenrePOJO("Бизнес, личностный рост", Url.SERVER_KNIGOBLUD+"/8ea14af7-f600-4e30-a0eb-67b615cdb701"));
        result.add(new GenrePOJO("Биографии, мемуары, ЖЗЛ", Url.SERVER_KNIGOBLUD+"/760dd554-482b-4817-8283-5b9ae9e76431"));
        result.add(new GenrePOJO("Для детей, аудиосказки, стишки", Url.SERVER_KNIGOBLUD+"/5982eae9-3868-4593-a9b0-b3378370345f"));
        result.add(new GenrePOJO("История, культурология", Url.SERVER_KNIGOBLUD+"/31d39d95-7bf5-420b-9db4-66c448785a07"));
        result.add(new GenrePOJO("Классика", Url.SERVER_KNIGOBLUD+"/0c6d4928-555a-4671-99e9-9bcf9a37d382"));
        result.add(new GenrePOJO("Медицина, здоровье", Url.SERVER_KNIGOBLUD+"/f00832de-609c-4cc8-9017-2571caa4bfb2"));
        result.add(new GenrePOJO("На иностранных языках", Url.SERVER_KNIGOBLUD+"/fb8d64ce-902d-44aa-9c21-77cb85a5b418"));
        result.add(new GenrePOJO("Научно-популярное", Url.SERVER_KNIGOBLUD+"/9da19d35-32ee-41e7-ab8c-5ec554fcdd8a"));
        result.add(new GenrePOJO("Обучение", Url.SERVER_KNIGOBLUD+"/7445b383-418a-475a-8374-ef2822d0552c"));
        result.add(new GenrePOJO("Поэзия", Url.SERVER_KNIGOBLUD+"/a1d4487a-12d6-4280-b1a2-4c204bd6908a"));
        result.add(new GenrePOJO("Приключения, военные приключения", Url.SERVER_KNIGOBLUD+"/e7d0d5bc-210a-4fc6-9598-19baf47c9b13"));
        result.add(new GenrePOJO("Психология, философия", Url.SERVER_KNIGOBLUD+"/09afc417-fa54-4b56-bb54-f950296a0b50"));
        result.add(new GenrePOJO("Разное", Url.SERVER_KNIGOBLUD+"/697856ad-1d48-48d5-ad20-5f00cc169f75"));
        result.add(new GenrePOJO("Ранобэ", Url.SERVER_KNIGOBLUD+"/07bc7998-8f32-49a1-8048-07a2c2294a11"));
        result.add(new GenrePOJO("Религия", Url.SERVER_KNIGOBLUD+"/4c37cad9-8ee6-4ce7-b62e-b52ef717952c"));
        result.add(new GenrePOJO("Роман, проза", Url.SERVER_KNIGOBLUD+"/61c37161-1899-4253-a4c3-9417f4b5c1c5"));
        result.add(new GenrePOJO("Ужасы, мистика, хоррор", Url.SERVER_KNIGOBLUD+"/120a1664-12e5-4ac6-853f-792f6a8007e9"));
        result.add(new GenrePOJO("Эзотерика, Нетрадиционные религиозно-философские учения", Url.SERVER_KNIGOBLUD+"/e4117eca-c027-4f80-8004-3851d9b562c7"));
        result.add(new GenrePOJO("Юмор, сатира", Url.SERVER_KNIGOBLUD+"/70bb89e8-65f7-414d-b98d-107878734c89"));
        return ProviderPage.of(result, false);
    }

    protected ProviderPage<GenrePOJO> loadBooksListAudioboo(String url, int page) throws IOException {
        //one list for every page: the navigation menu of the index
        return parseAudiobooGenres(SiteHttp.get(Url.SERVER_AUDIOBOO + "/"));
    }

    /** The genres of the navigation menu, with the site's own URLs and the book count as the description. */
    static ProviderPage<GenrePOJO> parseAudiobooGenres(Document doc) {
        return ProviderPage.of(rows(doc.select("ul.navmenu li > a[href]"), item -> {
            String name = item.ownText().trim();
            String href = item.absUrl("href");
            if (name.isEmpty() || !Url.isHost(href, Url.SERVER_AUDIOBOO)) {
                return null;
            }
            GenrePOJO genrePOJO = new GenrePOJO();
            genrePOJO.setUrl(href);
            genrePOJO.setName(name);
            Element count = item.selectFirst("span");
            if (count != null) {
                genrePOJO.setDescription(count.text().replaceAll("[\\[\\]]", "").trim());
            }
            return genrePOJO;
        }), false);
    }

    private ProviderPage<GenrePOJO> loadBooksListOtrub(String url) throws IOException {
        Document doc = SiteHttp.get(url);
        return ProviderPage.of(rows(doc.select("a._b8c4a1"), item -> {
            GenrePOJO genrePOJO = new GenrePOJO();
            if (!item.attr("href").isEmpty()) {
                genrePOJO.setUrl(Url.SERVER_OTRUB + item.attr("href") + "?p=");
            }
            Element nameEl = item.selectFirst("._28f7ab");
            if (nameEl != null) {
                genrePOJO.setName(nameEl.text());
            }
            Element countEl = item.selectFirst("._fb36fb");
            if (countEl != null) {
                genrePOJO.setDescription(countEl.text());
            }
            return genrePOJO;
        }), false);
    }

    private ProviderPage<GenrePOJO> loadBooksListPoleknig(String url) throws IOException {
        Document doc = SiteHttp.get(url);
        return ProviderPage.of(rows(doc.select("div.genre > a.heading"), item -> {
            GenrePOJO genrePOJO = new GenrePOJO();
            if (!item.attr("href").isEmpty()) {
                genrePOJO.setUrl(item.attr("href") + "?p=");
            }
            genrePOJO.setName(item.text());
            return genrePOJO;
        }), false);
    }
}
