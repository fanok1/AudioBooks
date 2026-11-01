package com.fanok.audiobooks.search;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Maps a site's genre name onto a {@link Genre} by the {@link Genre#keys} of every genre.
 * <p>
 * The name is {@link #fold}ed and cut into phrases at commas, slashes and "и"/"или" ("Детективы, триллеры, боевики":
 * detective, action). A phrase names one genre: a two-word key first, then the first noun that names a genre
 * ("Детективное фэнтези" → fantasy: an adjective only qualifies), then a generic head refined by the nearest adjective
 * before it ("Исторический любовный роман" → romance), then the last adjective ("Военный"). A word matches the longest
 * stem it starts with (a stem of up to 3 letters only as the whole word), else the longest stem inside it of 5+
 * letters or ending it of 4 ("Аудиосказки", "Дизельпанк"), else, as a typo of a noun, a stem of 6+ letters within one
 * edit of its start (two for 9+ letters: "Хорор", "Фонтастика").
 */
final class GenreMatcher {

    static final GenreMatcher INSTANCE = new GenreMatcher(Genre.values());

    /** Wins wherever it appears in a name: a parent must never see it filed under a harmless genre. */
    private static final Genre DOMINANT = Genre.EROTICA;
    private static final String PHRASE_BREAKS = ",;/|&+:>";
    private static final Set<String> CONJUNCTIONS = new HashSet<>(Arrays.asList("и", "или", "and", "or"));
    private static final Set<String> STOP_WORDS = new HashSet<>(Arrays.asList("книги", "книга", "аудиокниги",
            "аудиокнига", "жанр", "жанры", "категория", "раздел", "для", "про", "на", "о", "об", "по", "из", "в", "с",
            "the", "of", "a", "an", "s", "book", "books", "audiobooks", "genre"));
    private static final String[] ADJECTIVE_ENDINGS = {"ый", "ий", "ой", "ая", "яя", "ое", "ее", "ые", "ие", "ых", "их",
            "ым", "им", "ую", "юю", "ого", "его", "ому", "ему", "ыми", "ими"};

    /** What a word names: its genre, as a generic head ("роман") or not, and whether it is an adjective. */
    private static final class Hit {
        final Genre genre;
        final boolean weak;
        final boolean adjective;

        Hit(Genre genre, boolean weak, boolean adjective) {
            this.genre = genre;
            this.weak = weak;
            this.adjective = adjective;
        }
    }

    private static final class Pair {
        final String first;
        final String second;
        final Genre genre;

        Pair(String first, String second, Genre genre) {
            this.first = first;
            this.second = second;
            this.genre = genre;
        }
    }

    /** Stem → its genre (adjective unknown, false); in the data's order, so ties resolve the same way every time. */
    private final Map<String, Hit> stems = new LinkedHashMap<>();
    private final List<Pair> pairs = new ArrayList<>();
    private int longestStem;

    GenreMatcher(@NonNull Genre[] genres) {
        for (Genre genre : genres) {
            for (String key : genre.keys.split(" ")) {
                int plus = key.indexOf('+');
                boolean weak = key.startsWith("~");
                String stem = fold(weak ? key.substring(1) : key);
                if (plus > 0) {
                    pairs.add(new Pair(fold(key.substring(0, plus)), fold(key.substring(plus + 1)), genre));
                } else if (stems.put(stem, new Hit(genre, weak, false)) != null) {
                    throw new IllegalStateException("Genre key twice: " + key);
                }
                longestStem = Math.max(longestStem, stem.length());
            }
        }
    }

    /** The genres the phrases of {@code name} name, in order; erotica first when any word names it. */
    @NonNull
    List<Genre> parts(@Nullable String name) {
        List<Genre> parts = new ArrayList<>();
        boolean dominant = false;
        for (List<String> phrase : phrases(name == null ? "" : fold(name))) {
            List<Hit> hits = hits(phrase);
            for (Hit hit : hits) {
                dominant |= hit.genre == DOMINANT;
            }
            Genre head = head(hits);
            if (head != null) {
                parts.add(head);
            }
        }
        if (dominant) {
            parts.add(0, DOMINANT);
        }
        return parts;
    }

    /**
     * Lower case with ё/э/є → е, і/ї → и, ъ → ь (one spelling for "фэнтези"/"фентези", Ukrainian letters), a
     * phrase break as ',' and every other non-letter, non-digit as ' '.
     */
    @NonNull
    static String fold(@NonNull String name) {
        StringBuilder folded = new StringBuilder(name.length());
        for (int i = 0; i < name.length(); i++) {
            char c = Character.toLowerCase(name.charAt(i));
            if (c == 'ё' || c == 'э' || c == 'є') {
                c = 'е';
            } else if (c == 'і' || c == 'ї') {
                c = 'и';
            } else if (c == 'ъ') {
                c = 'ь';
            }
            folded.append(Character.isLetterOrDigit(c) ? c : PHRASE_BREAKS.indexOf(c) >= 0 ? ',' : ' ');
        }
        return folded.toString();
    }

    /**
     * The phrases of a folded name as words, without stop words ("аудиокниги", "для") and empty phrases; a run of
     * single letters is one word ("S.T.A.L.K.E.R." → stalker).
     */
    @NonNull
    private static List<List<String>> phrases(@NonNull String folded) {
        List<List<String>> phrases = new ArrayList<>();
        for (String part : folded.split(",")) {
            List<String> words = new ArrayList<>();
            boolean letters = false;
            for (String word : part.split(" ")) {
                if (word.isEmpty()) {
                    continue;
                }
                if (CONJUNCTIONS.contains(word)) {
                    addPhrase(phrases, words);
                    words = new ArrayList<>();
                    letters = false;
                } else if (letters && word.length() == 1) {
                    words.set(words.size() - 1, words.get(words.size() - 1) + word);
                } else {
                    words.add(word);
                    letters = word.length() == 1;
                }
            }
            addPhrase(phrases, words);
        }
        return phrases;
    }

    private static void addPhrase(@NonNull List<List<String>> phrases, @NonNull List<String> words) {
        words.removeAll(STOP_WORDS);
        if (!words.isEmpty()) {
            phrases.add(words);
        }
    }

    @NonNull
    private List<Hit> hits(@NonNull List<String> words) {
        List<Hit> hits = new ArrayList<>();
        for (int i = 0; i < words.size(); i++) {
            String word = words.get(i);
            Genre pair = i + 1 < words.size() ? pair(word, words.get(i + 1)) : null;
            if (pair != null) {
                hits.add(new Hit(pair, false, false));
                i++;
            } else {
                Hit stem = stem(word);
                if (stem != null) {
                    hits.add(new Hit(stem.genre, stem.weak, adjective(word)));
                }
            }
        }
        return hits;
    }

    /** The genre a phrase's words name, or null: see the class comment. */
    @Nullable
    private static Genre head(@NonNull List<Hit> hits) {
        for (Hit hit : hits) {
            if (!hit.weak && !hit.adjective) {
                return hit.genre;
            }
        }
        for (int i = 0; i < hits.size(); i++) {
            if (hits.get(i).weak && !hits.get(i).adjective) {
                for (int j = i - 1; j >= 0; j--) {
                    if (hits.get(j).adjective) {
                        return hits.get(j).genre;
                    }
                }
                return hits.get(i).genre;
            }
        }
        return hits.isEmpty() ? null : hits.get(hits.size() - 1).genre;
    }

    @Nullable
    private Genre pair(@NonNull String word, @NonNull String next) {
        for (Pair pair : pairs) {
            if (starts(word, pair.first) && next.startsWith(pair.second)) {
                return pair.genre;
            }
        }
        return null;
    }

    private static boolean starts(@NonNull String word, @NonNull String stem) {
        return stem.length() <= 3 ? word.equals(stem) : word.startsWith(stem);
    }

    /** The stem {@code word} starts with, contains or nearly starts with; see the class comment. */
    @Nullable
    private Hit stem(@NonNull String word) {
        for (int n = Math.min(word.length(), longestStem); n >= 1; n--) {
            Hit hit = stems.get(word.substring(0, n));
            if (hit != null && (n > 3 || n == word.length())) {
                return hit;
            }
        }
        if (word.length() < 4 || !Character.isLetter(word.charAt(0))) {
            return null;
        }
        boolean noun = !adjective(word);
        Hit best = null;
        int bestEdits = Integer.MAX_VALUE;
        int bestLength = 0;
        for (Map.Entry<String, Hit> entry : stems.entrySet()) {
            String stem = entry.getKey();
            // inside the word counts as no edit, a typo at its start as the edits it takes
            int edits = inside(word, stem) ? 0 : Integer.MAX_VALUE;
            if (edits > 0 && noun && stem.length() >= 6 && stem.charAt(0) == word.charAt(0)) {
                edits = prefixEdits(stem, word, stem.length() >= 9 ? 2 : 1);
            }
            if (edits < bestEdits || edits == bestEdits && edits != Integer.MAX_VALUE && stem.length() > bestLength) {
                best = entry.getValue();
                bestEdits = edits;
                bestLength = stem.length();
            }
        }
        return bestEdits <= 2 ? best : null;
    }

    /** A stem of 5+ letters inside {@code word} past its start, or of 4 at its end ("Аудиосказки", "Паропанк"). */
    private static boolean inside(@NonNull String word, @NonNull String stem) {
        return stem.length() >= 5 ? word.indexOf(stem, 1) > 0
                : stem.length() == 4 && word.length() > 4 && word.endsWith(stem);
    }

    /**
     * The fewest edits (insertion, deletion, substitution, swap of neighbours: optimal string alignment) that turn
     * {@code stem} into a prefix of {@code word} at most {@code max} letters shorter or longer than it; more than
     * {@code max} edits count as {@link Integer#MAX_VALUE}.
     */
    static int prefixEdits(@NonNull String stem, @NonNull String word, int max) {
        int m = stem.length();
        int n = Math.min(word.length(), m + max);
        if (n < m - max) {
            return Integer.MAX_VALUE;
        }
        int[][] d = new int[m + 1][n + 1];
        for (int j = 0; j <= n; j++) {
            d[0][j] = j;
        }
        for (int i = 1; i <= m; i++) {
            d[i][0] = i;
            for (int j = 1; j <= n; j++) {
                int edits = Math.min(Math.min(d[i - 1][j], d[i][j - 1]) + 1,
                        d[i - 1][j - 1] + (stem.charAt(i - 1) == word.charAt(j - 1) ? 0 : 1));
                if (i > 1 && j > 1 && stem.charAt(i - 1) == word.charAt(j - 2)
                        && stem.charAt(i - 2) == word.charAt(j - 1)) {
                    edits = Math.min(edits, d[i - 2][j - 2] + 1);
                }
                d[i][j] = edits;
            }
        }
        int best = Integer.MAX_VALUE;
        for (int j = Math.max(0, m - max); j <= n; j++) {
            best = Math.min(best, d[m][j]);
        }
        return best <= max ? best : Integer.MAX_VALUE;
    }

    /** A Russian adjective by its ending; a noun in -ние/-тие ("чтение", "саморазвитие") is not one. */
    private static boolean adjective(@NonNull String word) {
        if (word.length() < 5 || word.charAt(0) < 'а' || word.charAt(0) > 'я'
                || word.endsWith("ние") || word.endsWith("тие")) {
            return false;
        }
        for (String ending : ADJECTIVE_ENDINGS) {
            if (word.endsWith(ending)) {
                return true;
            }
        }
        return false;
    }
}
