package com.fanok.audiobooks.source;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.fanok.audiobooks.pojo.BookPOJO;
import com.fanok.audiobooks.pojo.DescriptionPOJO;
import com.google.gson.Gson;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.regex.Pattern;

/**
 * izibuk's class names are build hashes ({@code _ccb9b7}, {@code _802db0}...) that a rebuild of the site renames. The
 * parsers must not depend on them: every fixture is parsed as saved and again with each such class renamed, and both
 * give the same results.
 */
public class IzibukSelectorsTest {

    private static final Pattern GENERATED = Pattern.compile("_[0-9a-f]{6}");

    /** Generated classes the adapter still selects by, for want of anything else: none. */
    private static final Set<String> KEPT = Collections.emptySet();

    private static final String[] FIXTURES = {"book-description.html", "book.html", "listing-index-p1.html",
            "listing-mobile-genre1-p1.html", "other-source-search.html", "search-1984-p1.html", "series.html"};

    private static final String[] LISTING_URLS = {"https://izib.uk/?p=1", "https://izib.uk/genre1?p=1/",
            "https://izib.uk/author1?p=1/", "https://izib.uk/reader1?p=1/", "https://izib.uk/serie1?p=1/",
            "https://izib.uk/search?q=1984&p=1"};

    private static final Gson GSON = new Gson();

    private static Document fixture(String name, String baseUri) throws IOException {
        try (InputStream in = IzibukSelectorsTest.class.getResourceAsStream("/fixtures/izibuk/" + name)) {
            assertNotNull("missing fixture " + name, in);
            return Jsoup.parse(in, "UTF-8", baseUri);
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
        for (String name : FIXTURES) {
            String base = name.contains("mobile") ? "https://pda.izib.uk/" : "https://izib.uk/";
            Document saved = fixture(name, base);
            Document rebuilt = fixture(name, base);
            Set<String> renamed = renameGeneratedClasses(rebuilt);
            assertTrue(name + " has generated classes", renamed.size() > 5);
            for (String url : LISTING_URLS) {
                assertSameResult(name + " parseListing " + url,
                        () -> Izibuk.parseListing(saved, url, 1), () -> Izibuk.parseListing(rebuilt, url, 1));
            }
            assertSameResult(name + " parseGenres", () -> Izibuk.parseGenres(saved), () -> Izibuk.parseGenres(rebuilt));
            assertSameResult(name + " parsePeople",
                    () -> Izibuk.parsePeople(saved, 1), () -> Izibuk.parsePeople(rebuilt, 1));
            assertSameResult(name + " parseDescription",
                    () -> Izibuk.parseDescription(saved), () -> Izibuk.parseDescription(rebuilt));
            String bookUrl = base + "art1";
            assertSameResult(name + " parseBook",
                    () -> Izibuk.parseBook(saved, bookUrl), () -> Izibuk.parseBook(rebuilt, bookUrl));
            assertSameResult(name + " parseTracks",
                    () -> Izibuk.parseTracks(saved, bookUrl), () -> Izibuk.parseTracks(rebuilt, bookUrl));
            assertSameResult(name + " parseSeries", () -> Izibuk.parseSeries(saved), () -> Izibuk.parseSeries(rebuilt));
            assertSameResult(name + " parseSearch", () -> Izibuk.parseSearch(saved), () -> Izibuk.parseSearch(rebuilt));
        }
    }

    /** The fixtures do exercise the parsers: a rename that broke them would not go unnoticed as "both empty". */
    @Test
    public void renamedFixturesStillParse() throws IOException {
        Document listing = fixture("listing-index-p1.html", "https://izib.uk/");
        renameGeneratedClasses(listing);
        assertEquals(20, Izibuk.parseListing(listing, "https://izib.uk/?p=1", 1).items.size());

        Document mobile = fixture("listing-mobile-genre1-p1.html", "https://pda.izib.uk/");
        renameGeneratedClasses(mobile);
        assertEquals("8 ч. 12 мин.",
                Izibuk.parseListing(mobile, "https://izib.uk/genre1?p=1/", 1).items.get(0).getTime());

        Document search = fixture("search-1984-p1.html", "https://izib.uk/");
        renameGeneratedClasses(search);
        assertTrue(Izibuk.parseSearch(search).size() > 0);

        Document book = fixture("book.html", "https://izib.uk/");
        renameGeneratedClasses(book);
        assertFalse(Izibuk.parseDescription(book).getAutor().isEmpty());
        assertFalse(Izibuk.parseDescription(book).getGenre().isEmpty());
        assertFalse(Izibuk.parseDescription(book).getPoster().isEmpty());

        Document series = fixture("series.html", "https://izib.uk/");
        renameGeneratedClasses(series);
        assertTrue(Izibuk.parseSeries(series).size() > 0);
    }

    /** Several authors and readers: the rows read "Авторы:" and "Читают:" (live page izib.uk/art20481, 2026-10-30). */
    @Test
    public void aBookWithSeveralReadersKeepsItsAuthorAndReader() throws IOException {
        Document page = fixture("book-several-readers.html", "https://izib.uk/art20481");
        DescriptionPOJO description = Izibuk.parseDescription(page);
        assertEquals("Сергей Лукьяненко", description.getAutor());
        assertEquals("Наталья Грачёва", description.getArtist());
        assertEquals("16 ч. 6 мин.", description.getTime());
        BookPOJO book = Izibuk.parseBook(page, "https://izib.uk/art20481");
        assertEquals("Сергей Лукьяненко", book.getAutor());
        assertEquals("Наталья Грачёва", book.getArtist());
    }
}
