package com.fanok.audiobooks.search;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.Map;

/**
 * How often the user opened each genre, counted by the app's genre ({@link Genre#of}), so a pick of "Ужасы, мистика"
 * on one site counts for "Мистика и ужасы" on every other; a name no genre matches counts by its own words. The
 * "favorite genres" order ({@link GenreSort#FAVORITE}) reads it.
 */
public final class GenrePicks {

    static final String PREFERENCES = "genre_picks";

    private GenrePicks() {
    }

    /** Counts one pick of the genre named {@code name} on a site; a missing name is not counted. */
    public static void record(@NonNull Context context, @Nullable String name) {
        String key = key(name);
        if (key != null) {
            SharedPreferences prefs = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
            prefs.edit().putInt(key, prefs.getInt(key, 0) + 1).apply();
        }
    }

    /** Every count, for {@link GenreSort#sorted}. */
    @NonNull
    public static Map<String, ?> all(@NonNull Context context) {
        return context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).getAll();
    }

    static int count(@NonNull Map<String, ?> picks, @Nullable String name) {
        Object count = picks.get(key(name));
        return count instanceof Integer ? (Integer) count : 0;
    }

    /** The genre's {@link Genre#name()}, else "?" and the name's folded words; null for a name without words. */
    @Nullable
    static String key(@Nullable String name) {
        Genre genre = Genre.of(name);
        if (genre != null) {
            return genre.name();
        }
        String words = name == null ? "" : GenreMatcher.fold(name).replaceAll("[ ,]+", " ").trim();
        return words.isEmpty() ? null : "?" + words;
    }
}
