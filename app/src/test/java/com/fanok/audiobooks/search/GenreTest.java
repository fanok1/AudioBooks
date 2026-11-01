package com.fanok.audiobooks.search;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.junit.Test;

public class GenreTest {

    /** The reviewed names of {@code genres/names.tsv}: name, {@link Genre#of}, {@link Genre#allOf}. */
    private static List<String[]> names() throws IOException {
        List<String[]> rows = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                GenreTest.class.getClassLoader().getResourceAsStream("genres/names.tsv"), StandardCharsets.UTF_8))) {
            for (String line; (line = reader.readLine()) != null; ) {
                if (!line.startsWith("#")) {
                    rows.add(line.split("\t"));
                }
            }
        }
        return rows;
    }

    private static String allOf(String name) {
        StringBuilder genres = new StringBuilder();
        for (Genre genre : Genre.allOf(name)) {
            genres.append(genres.length() == 0 ? "" : ",").append(genre.name());
        }
        return genres.length() == 0 ? "-" : genres.toString();
    }

    @Test
    public void everyKnownSiteGenreMapsAsReviewed() throws IOException {
        List<String> wrong = new ArrayList<>();
        List<String[]> rows = names();
        for (String[] row : rows) {
            Genre of = Genre.of(row[0]);
            String actual = (of == null ? "-" : of.name()) + "\t" + allOf(row[0]);
            if (!actual.equals(row[1] + "\t" + row[2])) {
                wrong.add(row[0] + "\t" + actual + " (expected " + row[1] + "\t" + row[2] + ")");
            }
        }
        assertTrue(rows.size() > 400);
        assertEquals("", String.join("\n", wrong));
    }

    @Test
    public void aPhraseIsNamedByItsHeadNoun() {
        assertEquals(Genre.SF_FANTASY, Genre.of("Детективное фэнтези"));
        assertEquals(Genre.DETECTIVE, Genre.of("Иронический детектив"));
        assertEquals(Genre.SF_FANTASY, Genre.of("Классика фантастики"));
        assertEquals(Genre.HEALTH, Genre.of("Здоровье детей"));
        // a generic head takes the nearest adjective before it, else stays itself
        assertEquals(Genre.ROMANCE, Genre.of("Исторический любовный роман"));
        assertEquals(Genre.KIDS, Genre.of("Детская литература"));
        assertEquals(Genre.SF_FANTASY, Genre.of("Фантастические рассказы"));
        assertEquals(Genre.PROSE, Genre.of("Современная литература"));
        assertEquals(Genre.WAR, Genre.of("Военный"));
        assertEquals(Genre.ROMANTIC_FANTASY, Genre.of("Любовно-фантастический роман"));
    }

    @Test
    public void spellingsAndPunctuationFoldAway() {
        for (String name : Arrays.asList("Фэнтези", "фентези", "ФЭНТЭЗИ", "Фантастика / фэнтези", "«Фэнтези»")) {
            assertEquals(name, Genre.SF_FANTASY, Genre.of(name));
        }
        assertEquals(Genre.POSTAPOCALYPSE, Genre.of("S.T.A.L.K.E.R."));
        assertEquals(Genre.POSTAPOCALYPSE, Genre.of("S-T-I-K-S"));
        assertEquals(Genre.LITRPG, Genre.of("Лит-РПГ"));
        assertEquals(Genre.KIDS, Genre.of("Аудиокниги для детей"));
        assertEquals(Genre.SF_FANTASY, Genre.of("Дизельпанк"));
        assertEquals(Genre.PERFORMANCE, Genre.of("Радиоспектакли"));
    }

    @Test
    public void aTypoOfANounIsForgivenButNotAnAdjectivesLookAlike() {
        assertEquals(Genre.HORROR, Genre.of("Хорор"));
        assertEquals(Genre.SF_FANTASY, Genre.of("Фонтастика"));
        assertEquals(Genre.DETECTIVE, Genre.of("Детектифы"));
        // "истерический" is one letter from "истор", but an adjective is never guessed
        assertEquals(Genre.PROSE, Genre.of("Истерический роман"));
        assertEquals(2, GenreMatcher.prefixEdits("постапок", "постопакалипсис", 2));
        assertEquals(Integer.MAX_VALUE, GenreMatcher.prefixEdits("детектив", "детство", 1));
    }

    @Test
    public void everyPhraseCountsForPermissionsAndEroticaAnywhere() {
        assertEquals(EnumSet.of(Genre.DETECTIVE, Genre.ACTION), Genre.allOf("Детективы, триллеры, боевики"));
        assertEquals(Genre.DETECTIVE, Genre.of("Детективы, триллеры, боевики"));
        assertEquals(EnumSet.of(Genre.PSYCHOLOGY), Genre.allOf("Психология и философия"));
        assertEquals(Genre.EROTICA, Genre.of("Сказки для взрослых"));
        assertEquals(EnumSet.of(Genre.KIDS, Genre.EROTICA), Genre.allOf("Сказки для взрослых"));
        assertEquals(EnumSet.of(Genre.HISTORY, Genre.EROTICA), Genre.allOf("История 18+"));
    }

    @Test
    public void aNameWithoutAKnownGenreHasNone() {
        for (String name : Arrays.asList(null, "", "  ", "Аудиокниги", "Современный", "12345")) {
            assertNull(name, Genre.of(name));
            assertTrue(name, Genre.allOf(name).isEmpty());
        }
    }

    /**
     * Speed: a site genre name maps in microseconds, typos included, so binding cards and sorting need no cache. The
     * bound is generous for a slow CI machine; the measured figures are printed.
     */
    @Test
    public void mappingIsFast() throws IOException {
        List<String> names = new ArrayList<>();
        for (String[] row : names()) {
            names.add(row[0]);
        }
        List<String> typos = Arrays.asList("Фонтастика", "Хорор", "Анималистическое", "Современная стилизация",
                "Что читают известные люди", "Periodicals & Magazines");
        int sink = 0;
        for (int i = 0; i < 20; i++) { // warm-up
            sink += map(names) + map(typos);
        }
        int rounds = 200;
        long start = System.nanoTime();
        for (int i = 0; i < rounds; i++) {
            sink += map(names);
        }
        double known = (System.nanoTime() - start) / 1000.0 / rounds / names.size();
        start = System.nanoTime();
        for (int i = 0; i < rounds; i++) {
            sink += map(typos);
        }
        double unknown = (System.nanoTime() - start) / 1000.0 / rounds / typos.size();
        System.out.printf("Genre mapping: %.2f us per site genre name (%d names), %.2f us per typo or unknown name%n",
                known, names.size(), unknown);
        assertTrue(sink > 0);
        assertTrue("known " + known, known < 50);
        assertTrue("unknown " + unknown, unknown < 500);
    }

    private static int map(List<String> names) {
        int found = 0;
        for (String name : names) {
            Set<Genre> genres = Genre.allOf(name);
            found += genres.size() + (Genre.of(name) == null ? 0 : 1);
        }
        return found;
    }
}
