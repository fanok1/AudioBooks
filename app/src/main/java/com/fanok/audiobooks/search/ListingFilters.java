package com.fanok.audiobooks.search;

import com.fanok.audiobooks.pojo.BookPOJO;
import com.fanok.audiobooks.source.Sort;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The duration and period filters of a book listing: what each site can do on its server (probed live,
 * 2026-10-23) and what the app does with the cards it already has. Pure and static, so every URL rewrite and
 * every decision is a unit test.
 * <ul>
 * <li>knigavuhe: {@code popular}/{@code rating} take {@code w=} (index) or {@code period=} (genre) with exactly
 * today, week, month, alltime; any other value falls back to today. Cards carry the duration.</li>
 * <li>akniga: {@code top}/{@code discussed} take {@code period=} 1, 7, 30, 365, all; any other number falls
 * back to 7. Cards carry the duration.</li>
 * <li>knigoblud, otrub: {@code popular} takes {@code period=week|month} only; otrub cards carry no duration.</li>
 * <li>baza-knig: the DLE filter {@code /f/} takes {@code f.pdate=YYYY-MM-DD} (any date) and
 * {@code f.prodolzhitelnost}/{@code t.prodolzhitelnost} as HH:MM:SS compared by whole hours; it ignores the
 * genre of the page it was opened from, so genre pages filter client-side. Cards carry the duration.</li>
 * <li>abmp3, poleknig, audioboo: no period at all (audioboo sorts by duration but cannot bound it); cards carry
 * the duration. izibuk: no period (its popularity sort is all-time); the cards of its mobile site, which
 * {@code Izibuk} lists from, carry the duration.</li>
 * </ul>
 * A sort chosen in a genre listing stays within the genre ({@link #inGenre}): akniga ({@code /section/<g>/top/}),
 * baza-knig ({@code /f/cat=<id>/}) and poleknig ({@code /genres/<id>/best}) rank a genre on the server; otrub and
 * knigoblud cannot, so their site-wide ranking is narrowed on the device to the cards of the genre.
 */
public final class ListingFilters {

    /**
     * A duration range in whole minutes, {@code [from, to)}; a {@code to} of {@link #UNBOUNDED} has no upper limit.
     * The five ranges older versions offered stay as named presets, and their stored enum names still read back.
     */
    public static final class Duration {
        /** The {@code to} of a range without an upper limit. */
        public static final int UNBOUNDED = Integer.MAX_VALUE;

        public static final Duration ANY = new Duration(0, UNBOUNDED);
        public static final Duration UNDER_30M = new Duration(0, 30);
        public static final Duration FROM_30M_TO_1H = new Duration(30, 60);
        public static final Duration FROM_1H_TO_2H = new Duration(60, 120);
        public static final Duration OVER_2H = new Duration(120, UNBOUNDED);

        /** The ready-made ranges, in menu order. */
        public static final List<Duration> PRESETS = Collections.unmodifiableList(
                Arrays.asList(ANY, UNDER_30M, FROM_30M_TO_1H, FROM_1H_TO_2H, OVER_2H));

        /**
         * The stops of the range slider in minutes: dense where books are short, sparse where they are long, so
         * both a 20-minute story and a 30-hour epic are a few steps away; the last stop has no limit.
         */
        public static final int[] STOPS = {0, 15, 30, 45, 60, 90, 120, 180, 240, 300, 360, 480, 600, 720, 900,
                1200, 1800, UNBOUNDED};

        /** The enum names older versions stored, in {@link #PRESETS} order. */
        private static final String[] LEGACY_NAMES = {"ANY", "UNDER_30M", "FROM_30M_TO_1H", "FROM_1H_TO_2H",
                "OVER_2H"};
        private static final Pattern KEY = Pattern.compile("(\\d+)-(\\d*)");

        public final int fromMinutes;
        public final int toMinutes;

        private Duration(int fromMinutes, int toMinutes) {
            this.fromMinutes = fromMinutes;
            this.toMinutes = toMinutes;
        }

        /** The range {@code [from, to)}; {@link #ANY} when it bounds nothing or is empty. */
        public static Duration of(int fromMinutes, int toMinutes) {
            int from = Math.max(0, fromMinutes);
            if (toMinutes <= from || (from == 0 && toMinutes == UNBOUNDED)) {
                return ANY;
            }
            return new Duration(from, toMinutes);
        }

        /** The range between slider stops {@code from} and {@code to} (indexes into {@link #STOPS}). */
        public static Duration ofStops(int from, int to) {
            return of(STOPS[clampStop(from)], STOPS[clampStop(to)]);
        }

        /** The stop at or below {@code fromMinutes}: where the slider's lower thumb sits. */
        public int fromStop() {
            int i = STOPS.length - 2;
            while (i > 0 && STOPS[i] > fromMinutes) {
                i--;
            }
            return i;
        }

        /** The stop at or above {@code toMinutes}: where the slider's upper thumb sits. */
        public int toStop() {
            int i = 1;
            while (i < STOPS.length - 1 && STOPS[i] < toMinutes) {
                i++;
            }
            return i;
        }

        private static int clampStop(int i) {
            return Math.max(0, Math.min(STOPS.length - 1, i));
        }

        public boolean isAny() {
            return fromMinutes == 0 && toMinutes == UNBOUNDED;
        }

        public boolean isUnbounded() {
            return toMinutes == UNBOUNDED;
        }

        public boolean contains(int minutes) {
            return minutes >= fromMinutes && (isUnbounded() || minutes < toMinutes);
        }

        /** The stored form: {@code from-to}, or {@code from-} without an upper limit. */
        public String key() {
            return fromMinutes + "-" + (isUnbounded() ? "" : Integer.toString(toMinutes));
        }

        /** {@link #key} read back, or an enum name an older version stored; {@link #ANY} for anything else. */
        public static Duration of(String stored) {
            if (stored == null) {
                return ANY;
            }
            for (int i = 0; i < LEGACY_NAMES.length; i++) {
                if (LEGACY_NAMES[i].equals(stored)) {
                    return PRESETS.get(i);
                }
            }
            Matcher m = KEY.matcher(stored);
            if (!m.matches()) {
                return ANY;
            }
            try {
                return of(Integer.parseInt(m.group(1)), m.group(2).isEmpty() ? UNBOUNDED : Integer.parseInt(m.group(2)));
            } catch (NumberFormatException e) {
                return ANY;
            }
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Duration)) {
                return false;
            }
            Duration d = (Duration) o;
            return fromMinutes == d.fromMinutes && toMinutes == d.toMinutes;
        }

        @Override
        public int hashCode() {
            return 31 * fromMinutes + toMinutes;
        }

        @Override
        public String toString() {
            return key();
        }
    }

    /** A ranking period in days back from today; {@link #ALL} is unbounded. */
    public enum Period {
        DAY(1), THREE_DAYS(3), WEEK(7), TWO_WEEKS(14), MONTH(30), QUARTER(90), HALF_YEAR(182), YEAR(365), ALL(0);

        public final int days;

        Period(int days) {
            this.days = days;
        }

        /** The stored name, or null for anything else (none kept, or garbled). */
        public static Period of(String name) {
            for (Period p : values()) {
                if (p.name().equals(name)) {
                    return p;
                }
            }
            return null;
        }
    }

    /** What the filter bar above a listing shows; built by {@link #bar} from the presenter's state. */
    public static final class Bar {
        public final boolean visible;
        public final Duration duration;
        /** False when the listing's cards carry no duration: the duration chip is then hidden. */
        public final boolean durationAvailable;
        /** The period the listing URL carries, or null when the listing takes none. */
        public final Period period;
        /** The periods the listing accepts, in menu order; empty when it takes none. */
        public final List<Period> periods;
        /** Cards that pass the filter, and cards fetched so far. */
        public final int shown;
        public final int scanned;
        /** The listing has no more pages: the whole list was scanned. */
        public final boolean end;
        /** A page is being fetched right now. */
        public final boolean loading;
        /** A site-wide ranking narrowed to one genre on the device ({@link #cardGenre}). */
        public final boolean genre;
        /** Filtering, more pages and no load running: "search further" can be pressed. */
        public final boolean canSearchMore;

        Bar(boolean visible, Duration duration, boolean durationAvailable, Period period, List<Period> periods,
                int shown, int scanned, boolean end, boolean loading, boolean genre) {
            this.visible = visible;
            this.duration = duration;
            this.durationAvailable = durationAvailable;
            this.period = period;
            this.periods = periods;
            this.shown = shown;
            this.scanned = scanned;
            this.end = end;
            this.loading = loading;
            this.genre = genre;
            this.canSearchMore = isFiltering() && !end && !loading;
        }

        public static final Bar HIDDEN = new Bar(false, Duration.ANY, false, null, Collections.<Period>emptyList(),
                0, 0, false, false, false);

        public boolean isFiltering() {
            return durationAvailable && !duration.isAny() || genre;
        }
    }

    /** Pages fetched on their own after one user action before the bar asks whether to go on. */
    static final int HUNT_MAX_PAGES = 4;
    /** Matching cards after which an automatic hunt stops and leaves scrolling to the user. */
    static final int HUNT_ENOUGH_MATCHES = 6;
    /** Cards shown in all below which a hunt goes on anyway: a tablet or TV grid (2-3 columns) is not full yet. */
    static final int HUNT_FILL = 21;

    private ListingFilters() {
    }

    // ---------------------------------------------------------------- duration

    private static final Pattern HMS = Pattern.compile("(\\d+):(\\d{2}):(\\d{2})");
    private static final Pattern MS = Pattern.compile("(\\d+):(\\d{2})");
    private static final Pattern WORDS = Pattern.compile("(\\d+)\\s*([а-яА-ЯёЁ]+)");

    /**
     * Whole minutes of a card's duration text in any spelling the sites use ({@code 16:00:57},
     * {@code 6 часов 42 минуты}, {@code 11 ч. 53 мин.}, {@code 1 день 2 часа}), or -1 when the text carries no
     * duration (null, empty, {@code 00:00:00}, prose).
     */
    public static int durationMinutes(String text) {
        if (text == null) {
            return -1;
        }
        Matcher m = HMS.matcher(text);
        if (m.find()) {
            return nonZero(Integer.parseInt(m.group(1)) * 60 + Integer.parseInt(m.group(2)));
        }
        m = MS.matcher(text);
        if (m.find()) {
            // mm:ss, as Consts.formatTimeDisplay reads it
            return nonZero(Integer.parseInt(m.group(1)));
        }
        m = WORDS.matcher(text);
        int minutes = 0;
        boolean unit = false;
        while (m.find()) {
            int n = Integer.parseInt(m.group(1));
            String word = m.group(2).toLowerCase(Locale.ROOT);
            if (word.startsWith("д") && !word.startsWith("до")) {
                minutes += n * 24 * 60;
                unit = true;
            } else if (word.startsWith("ч")) {
                minutes += n * 60;
                unit = true;
            } else if (word.startsWith("м")) {
                minutes += n;
                unit = true;
            } else if (word.startsWith("с")) {
                unit = true; // seconds: below the minute
            }
        }
        return unit ? nonZero(minutes) : -1;
    }

    private static int nonZero(int minutes) {
        return minutes > 0 ? minutes : -1;
    }

    /** True when {@code book} passes {@code duration}; a card without a readable duration passes only ANY. */
    public static boolean matches(BookPOJO book, Duration duration) {
        if (duration.isAny()) {
            return true;
        }
        int minutes = durationMinutes(book.getTime());
        return minutes >= 0 && duration.contains(minutes);
    }

    /** The books of {@code all} that pass {@code duration}; the same list when it is ANY. */
    public static ArrayList<BookPOJO> filter(ArrayList<BookPOJO> all, Duration duration) {
        return filter(all, duration, null);
    }

    /** The books of {@code all} that pass {@code duration} and are of {@code genre} ({@link #cardGenre}) unless null. */
    public static ArrayList<BookPOJO> filter(ArrayList<BookPOJO> all, Duration duration, String genre) {
        if (duration.isAny() && genre == null) {
            return all;
        }
        ArrayList<BookPOJO> out = new ArrayList<>();
        for (BookPOJO book : all) {
            if (matches(book, duration) && (genre == null || genre.equals(lastSegment(book.getUrlGenre())))) {
                out.add(book);
            }
        }
        return out;
    }

    /**
     * True when the cards of the listing at {@code url} carry a duration, so a duration filter can work at all;
     * akniga's collections ("Подборки") list collections, not books, and carry none.
     */
    public static boolean listsDuration(Provider provider, String url) {
        return provider != null && provider.parser.listsDuration(url);
    }

    /**
     * {@code url} with the server's own duration bound for {@code duration}, where the server has one
     * ({@link com.fanok.audiobooks.source.SourceParser#withDuration}); the exact range is still applied to the cards
     * afterwards.
     */
    public static String withDuration(Provider provider, String url, Duration duration) {
        return provider == null ? url : provider.parser.withDuration(url, duration);
    }

    // ---------------------------------------------------------------- period

    /** The periods the listing at {@code url} accepts, in menu order; empty when it is not a ranking. */
    public static List<Period> periods(Provider provider, String url) {
        if (provider == null || url == null) {
            return Collections.emptyList();
        }
        return provider.parser.periods(url);
    }

    /** The period {@code url} carries, or null when it carries none or the listing takes none. */
    public static Period periodOf(Provider provider, String url, Calendar today) {
        if (periods(provider, url).isEmpty()) {
            return null;
        }
        return provider.parser.periodOf(url, today);
    }

    /** {@code url} with its period replaced by {@code period}; unchanged when the listing takes no period. */
    public static String withPeriod(Provider provider, String url, Period period, Calendar today) {
        if (!periods(provider, url).contains(period)) {
            return url;
        }
        return provider.parser.withPeriod(url, period, today);
    }

    /** True when the listing's period is a date bound recounted from today ({@link com.fanok.audiobooks.source.SourceParser#periodsAreDates}). */
    public static boolean periodsAreDates(Provider provider) {
        return provider != null && provider.parser.periodsAreDates();
    }

    /** The period whose day count is closest to {@code days}; {@link Period#ALL} never, it has no days. */
    public static Period nearest(int days) {
        Period best = Period.DAY;
        for (Period p : Period.values()) {
            if (p != Period.ALL && Math.abs(p.days - days) < Math.abs(best.days - days)) {
                best = p;
            }
        }
        return best;
    }

    public static String isoDate(Calendar today, int daysBack) {
        Calendar c = (Calendar) today.clone();
        c.add(Calendar.DAY_OF_MONTH, -daysBack);
        return String.format(Locale.ROOT, "%04d-%02d-%02d", c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1,
                c.get(Calendar.DAY_OF_MONTH));
    }

    // ---------------------------------------------------------------- auto paging

    /**
     * Whether a filtered listing should fetch the next page on its own: it does until the user's action brought
     * {@link #HUNT_ENOUGH_MATCHES} cards and the list shows at least {@link #HUNT_FILL}, or until
     * {@link #HUNT_MAX_PAGES} pages, whichever first; the bar then offers "search further", so a filter that hides
     * most cards never walks a whole catalog.
     */
    public static boolean keepHunting(int matchesSinceStart, int shownTotal, int pagesSinceStart) {
        return (matchesSinceStart < HUNT_ENOUGH_MATCHES || shownTotal < HUNT_FILL) && pagesSinceStart < HUNT_MAX_PAGES;
    }

    /**
     * The bar for a book listing; genre and author lists pass {@code books = false} and get {@link Bar#HIDDEN}.
     * {@code period} is the one the listing is ranked by, or null when it takes none.
     */
    public static Bar bar(boolean books, Provider provider, String url, Duration duration, Period period, int shown,
            int scanned, boolean end, boolean loading) {
        if (!books || provider == null) {
            return Bar.HIDDEN;
        }
        boolean durationAvailable = listsDuration(provider, url);
        if (!durationAvailable && period == null) {
            return Bar.HIDDEN;
        }
        return new Bar(true, duration, durationAvailable, period, periods(provider, url), shown, scanned, end, loading,
                cardGenre(provider, url) != null);
    }

    // ---------------------------------------------------------------- genre

    /**
     * The genre of a genre listing or of a ranking opened from one, as {@link #inGenre} takes it: the genre's root
     * URL (akniga, baza-knig, poleknig) or its key ({@link com.fanok.audiobooks.source.SourceParser#genreOf}); null for
     * any other listing.
     */
    public static String genreOf(Provider provider, String url) {
        if (provider == null || url == null) {
            return null;
        }
        return provider.parser.genreOf(url);
    }

    /**
     * {@code siteWide}, a listing of the sort menu, within {@code genre} ({@link #genreOf}; null keeps it site-wide).
     * baza-knig's {@code /<category>/f/} gets the category id when loaded (the filter ignores the path). A site with no
     * genre rankings ({@link com.fanok.audiobooks.source.SourceParser#inGenre}) carries the genre in a parameter its
     * server ignores, and its cards are filtered by it ({@link #cardGenre}).
     */
    public static String inGenre(Provider provider, String siteWide, String genre) {
        return genre == null ? siteWide : provider.parser.inGenre(siteWide, genre);
    }

    /**
     * The listing the Order item {@code sort} opens from the listing {@code url} of {@code provider}: the site's
     * {@link com.fanok.audiobooks.source.SourceParser#sortUrl}, within the genre of a genre listing ({@link #inGenre});
     * null when the site has no listing for it.
     */
    public static String sorted(Provider provider, Sort sort, String url) {
        if (provider == null) {
            return null;
        }
        String sorted = provider.parser.sortUrl(sort, url);
        if (sorted == null || sorted.isEmpty()) {
            return null;
        }
        return sorted.equals(url) ? sorted : inGenre(provider, sorted, genreOf(provider, url));
    }

    /**
     * The genre a site-wide ranking is narrowed to on the device
     * ({@link com.fanok.audiobooks.source.SourceParser#cardGenre}), else null.
     */
    public static String cardGenre(Provider provider, String url) {
        return provider == null ? null : provider.parser.cardGenre(url);
    }

    /** The last path segment of {@code url} without {@code .html}: a card's genre link as a genre key. */
    public static String lastSegment(String url) {
        if (url == null) {
            return null;
        }
        String path = url.replaceFirst("[?#].*", "").replaceFirst("/+$", "");
        String last = path.substring(path.lastIndexOf('/') + 1);
        return last.endsWith(".html") ? last.substring(0, last.length() - 5) : last;
    }

    // ---------------------------------------------------------------- url helpers

    /** The value of query parameter {@code name} in {@code url}, or null. */
    public static String param(String url, String name) {
        Matcher m = Pattern.compile("[?&]" + Pattern.quote(name) + "=([^&#]*)").matcher(url);
        return m.find() ? m.group(1) : null;
    }

    /** {@code url} with query parameter {@code name} set to {@code value} (replaced in place, else appended). */
    public static String withParam(String url, String name, String value) {
        Matcher m = Pattern.compile("([?&]" + Pattern.quote(name) + "=)[^&#]*").matcher(url);
        if (m.find()) {
            return url.substring(0, m.end(1)) + value + url.substring(m.end());
        }
        return url + (url.contains("?") ? "&" : "?") + name + "=" + value;
    }

    /** The value of DLE filter segment {@code name} ({@code /f/.../name=value/...}), or null. */
    public static String segment(String url, String name) {
        Matcher m = Pattern.compile("/" + Pattern.quote(name) + "=([^/]*)").matcher(url);
        return m.find() ? m.group(1) : null;
    }

    /** {@code url} with DLE filter segment {@code name} set to {@code value}, or removed when it is null. */
    public static String withSegment(String url, String name, String value) {
        String without = url.replaceAll("/" + Pattern.quote(name) + "=[^/]*", "");
        if (value == null) {
            return without;
        }
        int f = without.indexOf("/f/");
        if (f < 0) {
            return url;
        }
        return without.substring(0, f + 3) + name + "=" + value + "/" + without.substring(f + 3);
    }
}
