package com.fanok.audiobooks.source;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.fanok.audiobooks.pojo.BookPOJO;
import com.fanok.audiobooks.pojo.DescriptionPOJO;
import com.fanok.audiobooks.pojo.GenrePOJO;
import com.google.gson.Gson;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.regex.Pattern;

/**
 * otrub's class names are build hashes ({@code _cb0a41}, {@code _dad4fa}...) that a rebuild of the site renames. The
 * parsers must not depend on them: every fixture is parsed as saved and again with each such class renamed, and both
 * give the same results.
 */
public class OtrubSelectorsTest {

    private static final Pattern GENERATED = Pattern.compile("_[0-9a-f]{6}");

    /** Generated classes the adapter still selects by, for want of anything else: none. */
    private static final Set<String> KEPT = Collections.emptySet();

    private static final String[] FIXTURES = {"book-description.html", "book.html", "genres.html",
            "listing-index-p1.html", "other-source-search.html", "search-1984-p1.html", "series-book.html",
            "series.html"};

    private static final String BASE = "https://otrub.in/";
    private static final String BOOK_URL = BASE + "book.html";

    private static final Gson GSON = new Gson();

    private static Document fixture(String name) throws IOException {
        try (InputStream in = OtrubSelectorsTest.class.getResourceAsStream("/fixtures/otrub/" + name)) {
            assertNotNull("missing fixture " + name, in);
            return Jsoup.parse(in, "UTF-8", BASE);
        }
    }

    /** Every hex digit shifted by 7: distinct classes stay distinct and none keeps its name. */
    private static String rehash(String cls) {
        StringBuilder s = new StringBuilder("_");
        for (char c : cls.substring(1).toCharArray()) {
            s.append(Integer.toHexString((Character.digit(c, 16) + 7) % 16));
        }
        return s.toString();
    }

    /** Renames the generated classes of every element; returns the names renamed. */
    private static Set<String> renameGeneratedClasses(Document doc) {
        Set<String> renamed = new LinkedHashSet<>();
        for (Element e : doc.getAllElements()) {
            Set<String> classes = new LinkedHashSet<>();
            boolean changed = false;
            for (String cls : e.classNames()) {
                if (GENERATED.matcher(cls).matches() && !KEPT.contains(cls)) {
                    renamed.add(cls);
                    classes.add(rehash(cls));
                    changed = true;
                } else {
                    classes.add(cls);
                }
            }
            if (changed) {
                e.classNames(classes);
            }
        }
        return renamed;
    }

    private static String result(Callable<Object> parse) {
        try {
            return GSON.toJson(parse.call());
        } catch (Exception e) {
            return "throws " + e.getClass().getName() + ": " + e.getMessage();
        }
    }

    private static void assertSameResult(String what, Callable<Object> saved, Callable<Object> rebuilt) {
        assertEquals(what, result(saved), result(rebuilt));
    }

    @Test
    public void parsersDoNotDependOnGeneratedClassNames() throws IOException {
        Otrub otrub = new Otrub();
        for (String name : FIXTURES) {
            Document saved = fixture(name);
            Document rebuilt = fixture(name);
            Set<String> renamed = renameGeneratedClasses(rebuilt);
            assertTrue(name + " has generated classes", renamed.size() > 5);
            assertSameResult(name + " parseListing",
                    () -> Otrub.parseListing(saved, 1), () -> Otrub.parseListing(rebuilt, 1));
            assertSameResult(name + " genres", () -> otrub.genres(saved, BASE + "genres.html", 1),
                    () -> otrub.genres(rebuilt, BASE + "genres.html", 1));
            assertSameResult(name + " parseDescription",
                    () -> Otrub.parseDescription(saved), () -> Otrub.parseDescription(rebuilt));
            assertSameResult(name + " parseBook",
                    () -> Otrub.parseBook(saved, BOOK_URL), () -> Otrub.parseBook(rebuilt, BOOK_URL));
            assertSameResult(name + " parseTracks",
                    () -> Otrub.parseTracks(saved, BOOK_URL), () -> Otrub.parseTracks(rebuilt, BOOK_URL));
            assertSameResult(name + " parseSeriesPage",
                    () -> Otrub.parseSeriesPage(saved), () -> Otrub.parseSeriesPage(rebuilt));
            assertSameResult(name + " parseSearch", () -> Otrub.parseSearch(saved), () -> Otrub.parseSearch(rebuilt));
        }
    }

    /** The fixtures do exercise the parsers: a rename that broke them would not go unnoticed as "both empty". */
    @Test
    public void renamedFixturesStillParse() throws IOException {
        Document listing = fixture("listing-index-p1.html");
        renameGeneratedClasses(listing);
        List<BookPOJO> cards = Otrub.parseListing(listing, 1).items;
        assertEquals(10, cards.size());
        for (BookPOJO card : cards) {
            assertFalse(card.getName().isEmpty());
            assertFalse(card.getPhoto().isEmpty());
            assertFalse(card.getGenre().isEmpty());
            assertFalse(card.getAutor().isEmpty());
        }

        Document genres = fixture("genres.html");
        renameGeneratedClasses(genres);
        List<GenrePOJO> rows = new Otrub().genres(genres, BASE + "genres.html", 1).items;
        assertEquals(22, rows.size());
        assertFalse(rows.get(0).getName().isEmpty());
        assertTrue(rows.get(0).getDescription().contains("аудиокниг"));

        Document search = fixture("search-1984-p1.html");
        renameGeneratedClasses(search);
        assertTrue(Otrub.parseSearch(search).size() > 0);

        Document book = fixture("book.html");
        renameGeneratedClasses(book);
        DescriptionPOJO description = Otrub.parseDescription(book);
        assertFalse(description.getAutor().isEmpty());
        assertFalse(description.getGenre().isEmpty());
        assertFalse(description.getPoster().isEmpty());
        assertFalse(description.getDescription().isEmpty());

        Document series = fixture("series.html");
        renameGeneratedClasses(series);
        assertTrue(Otrub.parseSeriesPage(series).size() > 0);
    }

    /** Two readers: the row reads "Читают:" (live page otrub.in/ubijjca-v-domashnikh-tapochkakh.html, 2026-10-30). */
    @Test
    public void aBookWithSeveralReadersKeepsItsReader() throws IOException {
        Document page = fixture("book-several-readers.html");
        DescriptionPOJO description = Otrub.parseDescription(page);
        assertEquals("Аманда Эшби", description.getAutor());
        assertEquals("Юлия Рудина", description.getArtist());
        assertEquals("Юлия Рудина", Otrub.parseBook(page, BASE + "ubijjca-v-domashnikh-tapochkakh.html").getArtist());
    }

    /** The card's description is its own text block, not a people row or the title. */
    @Test
    public void aCardKeepsItsDescription() throws IOException {
        BookPOJO first = Otrub.parseListing(fixture("listing-index-p1.html"), 1).items.get(0);
        assertEquals("Последняя из рода. Невеста", first.getName());
        assertTrue(first.getDesc(), first.getDesc().startsWith("Теперь я – княгиня Ярослава Морозова"));
    }
}
