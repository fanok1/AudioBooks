package com.fanok.audiobooks.search;

import androidx.annotation.NonNull;
import com.fanok.audiobooks.R;
import com.fanok.audiobooks.pojo.GenrePOJO;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** The local order of a loaded genre, author or performer list, chosen in its "order" menu. */
public enum GenreSort {
    /** The site's order. */
    NONE(R.string.genre_sort_none),
    NAME(R.string.genre_sort_name),
    NAME_DESC(R.string.genre_sort_name_desc),
    /** Most books first ({@link #count}); a genre without a count (0) goes last. */
    COUNT(R.string.genre_sort_count),
    /** The genres the user opens most on any site first ({@link GenrePicks}); genre lists only. */
    FAVORITE(R.string.genre_sort_favorite);

    public final int title;

    /** A description that is only a book count, as some sites give it: "123 книги", "[953]", "4 738". */
    private static final Pattern COUNT_ONLY = Pattern.compile("^\\D{0,3}(\\d[\\d \u00a0]{0,10})\\D{0,16}$");
    /** A book count named as such within a description: "153K \u2022 515 \u0430\u0443\u0434\u0438\u043e\u043a\u043d\u0438\u0433", "42 \u043a\u043d\u0438\u0433\u0438". */
    private static final Pattern BOOKS = Pattern.compile("(\\d[\\d \u00a0]{0,10})\\s*(?:\u0430\u0443\u0434\u0438\u043e)?\u043a\u043d\u0438\u0433",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    GenreSort(int title) {
        this.title = title;
    }

    /** The choice kept under {@link #name()}, or {@link #NONE}. */
    @NonNull
    public static GenreSort of(String key) {
        for (GenreSort sort : values()) {
            if (sort.name().equals(key)) {
                return sort;
            }
        }
        return NONE;
    }

    /**
     * A sorted copy: {@code genres} itself is left as it is, and equal ones keep the site's order. {@code picks} are
     * the {@link GenrePicks#all} counts {@link #FAVORITE} sorts by.
     */
    @NonNull
    public ArrayList<GenrePOJO> sorted(@NonNull List<GenrePOJO> genres, @NonNull Map<String, ?> picks) {
        ArrayList<GenrePOJO> list = new ArrayList<>(genres);
        if (this == NAME || this == NAME_DESC) {
            Collator collator = Collator.getInstance(new Locale("ru"));
            int sign = this == NAME ? 1 : -1;
            Collections.sort(list, (a, b) -> sign * collator.compare(a.getName(), b.getName()));
        } else if (this == COUNT) {
            Collections.sort(list, (a, b) -> Integer.compare(count(b), count(a)));
        } else if (this == FAVORITE) {
            Map<GenrePOJO, Integer> counts = new IdentityHashMap<>();
            for (GenrePOJO genre : list) {
                counts.put(genre, GenrePicks.count(picks, genre.getName()));
            }
            Collections.sort(list, (a, b) -> Integer.compare(counts.get(b), counts.get(a)));
        }
        return list;
    }

    /**
     * The book count: one the description names as books (akniga's rating column is not one), else the rating, else
     * a description that is only a count; 0 when there is none.
     */
    static int count(@NonNull GenrePOJO genre) {
        String description = genre.getDescription() == null ? "" : genre.getDescription().trim();
        Matcher books = BOOKS.matcher(description);
        if (books.find() && number(books.group(1)) > 0) {
            return number(books.group(1));
        }
        if (genre.getReting() > 0) {
            return genre.getReting();
        }
        Matcher m = COUNT_ONLY.matcher(description);
        return m.matches() ? number(m.group(1)) : 0;
    }

    private static int number(@NonNull String digits) {
        String d = digits.replaceAll("\\D", "");
        return d.isEmpty() || d.length() > 9 ? 0 : Integer.parseInt(d);
    }
}
