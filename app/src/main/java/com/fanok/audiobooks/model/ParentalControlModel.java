package com.fanok.audiobooks.model;


import androidx.annotation.NonNull;
import com.fanok.audiobooks.Url;
import com.fanok.audiobooks.net.SiteHttp;
import com.fanok.audiobooks.pojo.GenrePOJO;
import com.fanok.audiobooks.pojo.ParentControlPOJO;
import io.reactivex.Observable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

/**
 * Loads the category lists the allow-list editor offers. A source whose list cannot be loaded is reported in
 * {@link Loaded#failedSources}: its categories are then absent from the editor, so its books stay blocked
 * (unknown category) rather than silently allowed.
 */
public class ParentalControlModel {

    public static final class Loaded {
        public final ArrayList<ParentControlPOJO> items = new ArrayList<>();
        public final List<String> failedSources = new ArrayList<>();
    }

    private interface Loader extends Callable<ArrayList<ParentControlPOJO>> {
    }

    public Observable<Loaded> getBooks() {
        return Observable.fromCallable(() -> {
            Loaded loaded = new Loaded();
            load(loaded, "Akniga", this::loadBooksListAkniga);
            load(loaded, "Книга в ухе", this::loadBooksList);
            load(loaded, "izibuk", this::loadBooksListIziBuk);
            load(loaded, "audiobook-mp3.com", this::loadBooksListABMP3);
            load(loaded, "База Книг", this::loadBooksListBazaKnig);
            load(loaded, "Книгоблуд", this::loadBooksListKnigablud);
            load(loaded, "Audioboo", () -> loadGenres("Audioboo", Url.INDEX_AUDIOBOO));
            load(loaded, "Отрубин", () -> loadGenres("Отрубин", Url.SECTIONS_OTRUB));
            load(loaded, "Полекниг", () -> loadGenres("Полекниг", Url.SECTIONS_POLEKNIG));
            return loaded;
        });
    }

    private static void load(@NonNull Loaded loaded, @NonNull String source, @NonNull Loader loader) {
        try {
            ArrayList<ParentControlPOJO> items = loader.call();
            if (items.isEmpty()) {
                loaded.failedSources.add(source);
            } else {
                loaded.items.addAll(items);
            }
        } catch (Exception e) {
            android.util.Log.w("ParentalControlModel", "Categories of " + source + " not loaded", e);
            loaded.failedSources.add(source);
        }
    }

    /** The categories of a source through the genre-screen loader {@link GenreModel} already has for it. */
    private static ArrayList<ParentControlPOJO> loadGenres(@NonNull String source, @NonNull String sectionsUrl)
            throws IOException {
        ArrayList<ParentControlPOJO> result = new ArrayList<>();
        for (GenrePOJO genre : new GenreModel().load(sectionsUrl, 1).items) {
            if (genre.getUrl() != null && genre.getName() != null) {
                result.add(new ParentControlPOJO(source, genre.getName(), genre.getUrl()));
            }
        }
        return result;
    }

    ArrayList<ParentControlPOJO> loadBooksList() throws IOException {
        ArrayList<ParentControlPOJO> result = new ArrayList<>();
        Document doc = SiteHttp.get(Url.SECTIONS);

        Elements items = doc.getElementsByClass("genre2_item");
        for (Element item : items) {
            Elements name = item.getElementsByClass("genre2_item_name");
            if (name.size() != 0) {
                String href = Url.SERVER + name.first().attr("href") + "<page>/";
                String nameValue = name.first().text();
                result.add(new ParentControlPOJO("Книга в ухе", nameValue, href));
            }
        }

        return result;
    }

    ArrayList<ParentControlPOJO> loadBooksListABMP3() throws IOException {
        ArrayList<ParentControlPOJO> result = new ArrayList<>();
        Document doc = SiteHttp.get(Url.SECTIONS_ABMP3);

        Elements items = doc.getElementsByClass("b-posts");
        if (items.size() != 0) {
            Elements list = items.first().children();
            for (Element item : list) {
                String name = "";
                String url = "";
                Elements titles = item.getElementsByClass("title");
                if (titles.size() != 0) {
                    Elements aGenre = titles.first().getElementsByTag("a");
                    if (aGenre.size() != 0) {
                        url = Url.SERVER_ABMP3 + aGenre.first().attr("href") + "?page=";
                        name = aGenre.first().text();
                    }
                }
                if (!url.isEmpty() && !name.isEmpty()) {
                    result.add(new ParentControlPOJO("audiobook-mp3.com", name, url));
                }
            }
        }
        return result;
    }

    ArrayList<ParentControlPOJO> loadBooksListAkniga() throws IOException {
        ArrayList<ParentControlPOJO> result = new ArrayList<>();
        Document doc = SiteHttp.get(Url.SECTIONS_AKNIGA);

        Elements items = doc.getElementsByClass("table-authors");
        if (items.size() != 0) {
            Elements tbody = items.first().getElementsByTag("tbody");
            if (tbody.size() != 0) {
                Elements list = tbody.first().children();
                for (Element item : list) {
                    String url = "";
                    String name = "";
                    Elements titles = item.getElementsByClass("name-obj");
                    if (titles.size() != 0) {
                        Elements aGenre = titles.first().getElementsByTag("a");
                        if (aGenre.size() != 0) {
                            url = aGenre.first().attr("href") + "page<page>/";
                            name = aGenre.first().text();
                        }
                    }

                    if (!url.isEmpty() && !name.isEmpty()) {
                        result.add(new ParentControlPOJO("Akniga", name, url));
                    }
                }
            }
        }
        return result;
    }

    ArrayList<ParentControlPOJO> loadBooksListBazaKnig() throws IOException {
        ArrayList<ParentControlPOJO> result = new ArrayList<>();
        Document doc = SiteHttp.get(Url.SECTIONS_BAZA_KNIG);

        Elements items = doc.getElementsByClass("reset left-menu-items");
        if (items.size() != 0) {
            Elements list = items.first().getElementsByTag("li");
            for (Element item : list) {
                String name = "";
                String url = "";
                Elements aGenre = item.getElementsByTag("a");
                if (aGenre.size() != 0) {
                    url = Url.SERVER_BAZA_KNIG + aGenre.first().attr("href") + "page/";
                    name = item.text();
                }
                if (!name.isEmpty() && !url.isEmpty()) {
                    result.add(new ParentControlPOJO("База Книг", name, url));
                }
            }
        }
        return result;
    }

    ArrayList<ParentControlPOJO> loadBooksListIziBuk() throws IOException {
        ArrayList<ParentControlPOJO> result = new ArrayList<>();
        Document doc = SiteHttp.get(Url.SECTIONS_IZIBUK + 1);

        Elements items = doc.getElementsByClass("_e181af");
        if (items.size() != 0) {
            Elements list = items.first().children();
            for (Element item : list) {
                String nameValue = "";
                String url = "";
                String src = item.attr("href");
                if (!src.isEmpty()) {
                    url = Url.SERVER_IZIBUK + src + "?p=";
                }

                Elements children = item.children();
                if (children.size() == 2) {
                    nameValue = children.first().text();
                }

                if (!nameValue.isEmpty() && !url.isEmpty()) {
                    result.add(new ParentControlPOJO("izibuk", nameValue, url));
                }
            }
        }
        return result;
    }

    ArrayList<ParentControlPOJO> loadBooksListKnigablud() {
        ArrayList<ParentControlPOJO> result = new ArrayList<>();
        result.add(new ParentControlPOJO("Книгоблуд", "Фантастика, фэнтези", Url.SERVER_KNIGOBLUD+"/9d47c7aa-9e2e-4fe0-bcd5-14f7d4874198"));
        result.add(new ParentControlPOJO("Книгоблуд", "Детективы, триллеры, боевики", Url.SERVER_KNIGOBLUD+"/4daab267-3592-4604-9e34-8f3b6a1db9fe"));
        result.add(new ParentControlPOJO("Книгоблуд", "Аудиоспектакли, радиопостановки и литературные чтения", Url.SERVER_KNIGOBLUD+"/0efa0796-8734-4735-ac03-d4eca1ccb013"));
        result.add(new ParentControlPOJO("Книгоблуд", "Бизнес, личностный рост", Url.SERVER_KNIGOBLUD+"/8ea14af7-f600-4e30-a0eb-67b615cdb701"));
        result.add(new ParentControlPOJO("Книгоблуд", "Биографии, мемуары, ЖЗЛ", Url.SERVER_KNIGOBLUD+"/760dd554-482b-4817-8283-5b9ae9e76431"));
        result.add(new ParentControlPOJO("Книгоблуд", "Для детей, аудиосказки, стишки", Url.SERVER_KNIGOBLUD+"/5982eae9-3868-4593-a9b0-b3378370345f"));
        result.add(new ParentControlPOJO("Книгоблуд", "История, культурология", Url.SERVER_KNIGOBLUD+"/31d39d95-7bf5-420b-9db4-66c448785a07"));
        result.add(new ParentControlPOJO("Книгоблуд", "Классика", Url.SERVER_KNIGOBLUD+"/0c6d4928-555a-4671-99e9-9bcf9a37d382"));
        result.add(new ParentControlPOJO("Книгоблуд", "Медицина, здоровье", Url.SERVER_KNIGOBLUD+"/f00832de-609c-4cc8-9017-2571caa4bfb2"));
        result.add(new ParentControlPOJO("Книгоблуд", "На иностранных языках", Url.SERVER_KNIGOBLUD+"/fb8d64ce-902d-44aa-9c21-77cb85a5b418"));
        result.add(new ParentControlPOJO("Книгоблуд", "Научно-популярное", Url.SERVER_KNIGOBLUD+"/9da19d35-32ee-41e7-ab8c-5ec554fcdd8a"));
        result.add(new ParentControlPOJO("Книгоблуд", "Обучение", Url.SERVER_KNIGOBLUD+"/7445b383-418a-475a-8374-ef2822d0552c"));
        result.add(new ParentControlPOJO("Книгоблуд", "Поэзия", Url.SERVER_KNIGOBLUD+"/a1d4487a-12d6-4280-b1a2-4c204bd6908a"));
        result.add(new ParentControlPOJO("Книгоблуд", "Приключения, военные приключения", Url.SERVER_KNIGOBLUD+"/e7d0d5bc-210a-4fc6-9598-19baf47c9b13"));
        result.add(new ParentControlPOJO("Книгоблуд", "Психология, философия", Url.SERVER_KNIGOBLUD+"/09afc417-fa54-4b56-bb54-f950296a0b50"));
        result.add(new ParentControlPOJO("Книгоблуд", "Разное", Url.SERVER_KNIGOBLUD+"/697856ad-1d48-48d5-ad20-5f00cc169f75"));
        result.add(new ParentControlPOJO("Книгоблуд", "Ранобэ", Url.SERVER_KNIGOBLUD+"/07bc7998-8f32-49a1-8048-07a2c2294a11"));
        result.add(new ParentControlPOJO("Книгоблуд", "Религия", Url.SERVER_KNIGOBLUD+"/4c37cad9-8ee6-4ce7-b62e-b52ef717952c"));
        result.add(new ParentControlPOJO("Книгоблуд", "Роман, проза", Url.SERVER_KNIGOBLUD+"/61c37161-1899-4253-a4c3-9417f4b5c1c5"));
        result.add(new ParentControlPOJO("Книгоблуд", "Ужасы, мистика, хоррор", Url.SERVER_KNIGOBLUD+"/120a1664-12e5-4ac6-853f-792f6a8007e9"));
        result.add(new ParentControlPOJO("Книгоблуд", "Эзотерика, Нетрадиционные религиозно-философские учения", Url.SERVER_KNIGOBLUD+"/e4117eca-c027-4f80-8004-3851d9b562c7"));
        result.add(new ParentControlPOJO("Книгоблуд", "Юмор, сатира", Url.SERVER_KNIGOBLUD+"/70bb89e8-65f7-414d-b98d-107878734c89"));
        return result;
    }
}
