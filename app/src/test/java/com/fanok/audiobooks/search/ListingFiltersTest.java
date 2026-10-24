package com.fanok.audiobooks.search;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.fanok.audiobooks.Url;
import com.fanok.audiobooks.pojo.BookPOJO;
import com.fanok.audiobooks.search.ListingFilters.Duration;
import com.fanok.audiobooks.search.ListingFilters.Period;

import org.junit.Test;

import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;

public class ListingFiltersTest {

    /** 2026-10-23, the day the sites were probed. */
    private static Calendar today() {
        Calendar c = Calendar.getInstance();
        c.set(2026, Calendar.OCTOBER, 23, 12, 0, 0);
        return c;
    }

    private static BookPOJO book(String time) {
        BookPOJO b = new BookPOJO();
        b.setTime(time);
        return b;
    }

    // ---------------------------------------------------------------- duration text

    @Test
    public void readsEveryDurationSpellingTheSitesUse() {
        assertEquals(960, ListingFilters.durationMinutes("16:00:57"));            // abmp3, baza-knig
        assertEquals(244, ListingFilters.durationMinutes("Время: 04:04:13"));     // audioboo
        assertEquals(402, ListingFilters.durationMinutes("6 часов 42 минуты"));   // knigavuhe, akniga, poleknig
        assertEquals(16, ListingFilters.durationMinutes("16 минут"));
        assertEquals(713, ListingFilters.durationMinutes("11 ч. 53 мин."));       // knigoblud, Consts.formatTimeDisplay
        assertEquals(960, ListingFilters.durationMinutes("16 ч."));
        assertEquals(1560, ListingFilters.durationMinutes("1 день 2 часа"));
        assertEquals(45, ListingFilters.durationMinutes("45:12"));                // mm:ss, as formatTimeDisplay reads it
    }

    @Test
    public void noDurationIsMinusOne() {
        assertEquals(-1, ListingFilters.durationMinutes(null));
        assertEquals(-1, ListingFilters.durationMinutes(""));
        assertEquals(-1, ListingFilters.durationMinutes("00:00:00"));           // baza-knig's "unknown"
        assertEquals(-1, ListingFilters.durationMinutes("0 ч. 0 мин."));
        assertEquals(-1, ListingFilters.durationMinutes("2024"));
    }

    @Test
    public void durationTextSurvivesThePojoFormatting() {
        // BookPOJO.setTime stores Consts.formatTimeDisplay's text: the filter reads that, not the raw site text
        assertEquals(960, ListingFilters.durationMinutes(book("16:00:57").getTime()));
        assertEquals(402, ListingFilters.durationMinutes(book("6 часов 42 минуты").getTime()));
    }

    @Test
    public void rangesAreHalfOpenAndUnknownPassesOnlyAny() {
        assertTrue(ListingFilters.matches(book("29 минут"), Duration.UNDER_30M));
        assertFalse(ListingFilters.matches(book("30 минут"), Duration.UNDER_30M));
        assertTrue(ListingFilters.matches(book("30 минут"), Duration.FROM_30M_TO_1H));
        assertTrue(ListingFilters.matches(book("1 час"), Duration.FROM_1H_TO_2H));
        assertFalse(ListingFilters.matches(book("1 час"), Duration.FROM_30M_TO_1H));
        assertTrue(ListingFilters.matches(book("2 часа"), Duration.OVER_2H));
        assertFalse(ListingFilters.matches(book("1 час 59 минут"), Duration.OVER_2H));
        assertTrue(ListingFilters.matches(book(""), Duration.ANY));
        assertFalse(ListingFilters.matches(book(""), Duration.UNDER_30M));
    }

    @Test
    public void aCustomRangeKeepsItsLowerBoundAndDropsItsUpperOne() {
        Duration range = Duration.of(45, 90);
        assertTrue(ListingFilters.matches(book("00:45:00"), range));
        assertTrue(ListingFilters.matches(book("01:29:59"), range));
        assertFalse(ListingFilters.matches(book("01:30:00"), range));
        assertFalse(ListingFilters.matches(book("00:44:59"), range));
        Duration longBooks = Duration.of(600, Duration.UNBOUNDED);
        assertTrue(ListingFilters.matches(book("26:00:00"), longBooks));
        assertFalse(ListingFilters.matches(book("09:59:59"), longBooks));
        // a range that bounds nothing is ANY, and so is an empty one
        assertTrue(Duration.of(0, Duration.UNBOUNDED).isAny());
        assertEquals(Duration.ANY, Duration.of(90, 45));
        assertEquals(Duration.FROM_1H_TO_2H, Duration.of(60, 120));
    }

    @Test
    public void storedDurationReadsTheOldEnumNamesAndTheNewKeys() {
        // what versions with the five-value enum stored
        assertEquals(Duration.ANY, Duration.of("ANY"));
        assertEquals(Duration.UNDER_30M, Duration.of("UNDER_30M"));
        assertEquals(Duration.FROM_30M_TO_1H, Duration.of("FROM_30M_TO_1H"));
        assertEquals(Duration.FROM_1H_TO_2H, Duration.of("FROM_1H_TO_2H"));
        assertEquals(Duration.OVER_2H, Duration.of("OVER_2H"));
        // the range keys round-trip, an open end included
        assertEquals("45-90", Duration.of(45, 90).key());
        assertEquals("120-", Duration.OVER_2H.key());
        assertEquals(Duration.of(45, 90), Duration.of("45-90"));
        assertEquals(Duration.OVER_2H, Duration.of("120-"));
        for (Duration d : Duration.PRESETS) {
            assertEquals(d, Duration.of(d.key()));
        }
        // nothing stored or garbled: no filter
        assertEquals(Duration.ANY, Duration.of((String) null));
        assertEquals(Duration.ANY, Duration.of(""));
        assertEquals(Duration.ANY, Duration.of("FORTNIGHT"));
        assertEquals(Duration.ANY, Duration.of("90-45"));
        assertEquals(Duration.ANY, Duration.of("99999999999-"));
    }

    @Test
    public void everyPresetSitsOnSliderStopsAndTheLastStopIsOpen() {
        assertEquals(Duration.UNBOUNDED, Duration.STOPS[Duration.STOPS.length - 1]);
        for (Duration d : Duration.PRESETS) {
            assertEquals(d.key(), d, Duration.ofStops(d.fromStop(), d.toStop()));
        }
        assertEquals(0, Duration.ANY.fromStop());
        assertEquals(Duration.STOPS.length - 1, Duration.ANY.toStop());
        // a range between stops widens to the stops around it
        assertEquals(Duration.of(30, 60), Duration.ofStops(Duration.of(40, 50).fromStop(), Duration.of(40, 50).toStop()));
    }

    @Test
    public void onlySitesWhoseCardsCarryADurationCanFilterByIt() {
        assertFalse(ListingFilters.listsDuration(Provider.IZIBUK, Url.INDEX_IZIBUK));
        assertFalse(ListingFilters.listsDuration(Provider.OTRUB, Url.BEST_WEEK_OTRUB));
        assertFalse(ListingFilters.listsDuration(null, Url.INDEX));
        for (Provider p : Provider.values()) {
            if (p != Provider.IZIBUK && p != Provider.OTRUB) {
                assertTrue(p.name(), ListingFilters.listsDuration(p, Url.INDEX));
            }
        }
        // akniga's collections list collections, not books
        assertTrue(ListingFilters.listsDuration(Provider.AKNIGA, Url.BEST_WEEK_AKNIGA));
        assertFalse(ListingFilters.listsDuration(Provider.AKNIGA, Url.COLLECTIONS_AKNIGA));
        assertFalse(ListingFilters.listsDuration(Provider.AKNIGA, Url.COLLECTIONS_TOP_AKNIGA));
        assertFalse(ListingFilters.bar(true, Provider.AKNIGA, Url.COLLECTIONS_TOP_AKNIGA, Duration.UNDER_30M, null,
                0, 0, false, false).visible);
    }

    // ---------------------------------------------------------------- server-side duration (baza-knig)

    @Test
    public void bazaKnigBoundsTheDurationOnTheServerByWholeHours() {
        String ranking = Url.BEST_BAZA_KNIG;
        assertEquals(Url.SERVER_BAZA_KNIG + "/f/t.prodolzhitelnost=00:59:59/sort=news_read/order=desc/page/",
                ListingFilters.withDuration(Provider.BAZA_KNIG, ranking, Duration.UNDER_30M));
        assertEquals(Url.SERVER_BAZA_KNIG + "/f/f.prodolzhitelnost=01:00:00/t.prodolzhitelnost=01:59:59/sort=news_read/order=desc/page/",
                ListingFilters.withDuration(Provider.BAZA_KNIG, ranking, Duration.FROM_1H_TO_2H));
        assertEquals(Url.SERVER_BAZA_KNIG + "/f/f.prodolzhitelnost=02:00:00/sort=news_read/order=desc/page/",
                ListingFilters.withDuration(Provider.BAZA_KNIG, ranking, Duration.OVER_2H));
        // a new bound replaces the old one, ANY removes it
        String bounded = ListingFilters.withDuration(Provider.BAZA_KNIG, ranking, Duration.FROM_1H_TO_2H);
        assertEquals(Url.SERVER_BAZA_KNIG + "/f/f.prodolzhitelnost=02:00:00/sort=news_read/order=desc/page/",
                ListingFilters.withDuration(Provider.BAZA_KNIG, bounded, Duration.OVER_2H));
        assertEquals(ranking, ListingFilters.withDuration(Provider.BAZA_KNIG, bounded, Duration.ANY));
    }

    @Test
    public void bazaKnigWidensACustomRangeToWholeHours() {
        String ranking = Url.BEST_BAZA_KNIG;
        // from rounds down (0 h is no bound), to rounds up to the end of the hour before it
        assertEquals(Url.SERVER_BAZA_KNIG + "/f/t.prodolzhitelnost=01:59:59/sort=news_read/order=desc/page/",
                ListingFilters.withDuration(Provider.BAZA_KNIG, ranking, Duration.of(45, 90)));
        assertEquals(Url.SERVER_BAZA_KNIG + "/f/f.prodolzhitelnost=01:00:00/t.prodolzhitelnost=02:59:59/sort=news_read/order=desc/page/",
                ListingFilters.withDuration(Provider.BAZA_KNIG, ranking, Duration.of(90, 180)));
        assertEquals(Url.SERVER_BAZA_KNIG + "/f/f.prodolzhitelnost=10:00:00/t.prodolzhitelnost=29:59:59/sort=news_read/order=desc/page/",
                ListingFilters.withDuration(Provider.BAZA_KNIG, ranking, Duration.of(600, 1800)));
        assertEquals(Url.SERVER_BAZA_KNIG + "/f/f.prodolzhitelnost=20:00:00/sort=news_read/order=desc/page/",
                ListingFilters.withDuration(Provider.BAZA_KNIG, ranking, Duration.of(1200, Duration.UNBOUNDED)));
    }

    @Test
    public void bazaKnigIndexBecomesItsDateSortedFilterTwin() {
        assertEquals(Url.SERVER_BAZA_KNIG + "/f/f.prodolzhitelnost=02:00:00/sort=date/order=desc/page/",
                ListingFilters.withDuration(Provider.BAZA_KNIG, Url.INDEX_BAZA_KNIG, Duration.OVER_2H));
        // ANY on the index stays the index
        assertEquals(Url.INDEX_BAZA_KNIG, ListingFilters.withDuration(Provider.BAZA_KNIG, Url.INDEX_BAZA_KNIG, Duration.ANY));
    }

    @Test
    public void genrePagesAndOtherSitesFilterDurationOnTheDevice() {
        String genre = Url.SERVER_BAZA_KNIG + "/litrpg/page/";
        assertEquals(genre, ListingFilters.withDuration(Provider.BAZA_KNIG, genre, Duration.UNDER_30M));
        assertFalse(ListingFilters.filtersDurationOnServer(Provider.BAZA_KNIG, genre));
        assertEquals(Url.BEST_WEEK, ListingFilters.withDuration(Provider.KNIGAVUHE, Url.BEST_WEEK, Duration.UNDER_30M));
        assertEquals(Url.BEST_WEEK_AKNIGA, ListingFilters.withDuration(Provider.AKNIGA, Url.BEST_WEEK_AKNIGA, Duration.OVER_2H));
    }

    // ---------------------------------------------------------------- periods

    @Test
    public void eachSiteOffersOnlyThePeriodsItsServerHonours() {
        assertEquals(Arrays.asList(Period.DAY, Period.WEEK, Period.MONTH, Period.ALL),
                ListingFilters.periods(Provider.KNIGAVUHE, Url.BEST_WEEK));
        assertEquals(Arrays.asList(Period.DAY, Period.WEEK, Period.MONTH, Period.YEAR, Period.ALL),
                ListingFilters.periods(Provider.AKNIGA, Url.RATING_WEEK_AKNIGA));
        assertEquals(Arrays.asList(Period.WEEK, Period.MONTH), ListingFilters.periods(Provider.KNIGOBLUD, Url.POPULAR_WEEK_KNIGOBLUD));
        assertEquals(Arrays.asList(Period.WEEK, Period.MONTH), ListingFilters.periods(Provider.OTRUB, Url.BEST_MONTH_OTRUB));
        assertEquals(Arrays.asList(Period.values()), ListingFilters.periods(Provider.BAZA_KNIG, Url.RATING_BAZA_KNIG));
        // no ranking, no period
        assertEquals(Collections.emptyList(), ListingFilters.periods(Provider.KNIGAVUHE, Url.NEW_BOOK));
        assertEquals(Collections.emptyList(), ListingFilters.periods(Provider.AKNIGA, Url.COLLECTIONS_TOP_AKNIGA));
        assertEquals(Collections.emptyList(), ListingFilters.periods(Provider.BAZA_KNIG, Url.INDEX_BAZA_KNIG));
        assertEquals(Collections.emptyList(), ListingFilters.periods(Provider.ABMP3, Url.ABMP3_TOP));
        assertEquals(Collections.emptyList(), ListingFilters.periods(Provider.POLEKNIG, Url.BEST_POLEKNIG));
        assertEquals(Collections.emptyList(), ListingFilters.periods(Provider.IZIBUK, Url.INDEX_IZIBUK));
        assertEquals(Collections.emptyList(), ListingFilters.periods(null, Url.INDEX));
    }

    @Test
    public void knigavuheIndexUsesWAndGenresUsePeriod() {
        assertEquals(Period.WEEK, ListingFilters.periodOf(Provider.KNIGAVUHE, Url.BEST_WEEK, today()));
        assertEquals(Url.BEST_MONTH, ListingFilters.withPeriod(Provider.KNIGAVUHE, Url.BEST_WEEK, Period.MONTH, today()));
        assertEquals(Url.RATING_ALL_TIME, ListingFilters.withPeriod(Provider.KNIGAVUHE, Url.RATING_TODATY, Period.ALL, today()));
        String genre = Url.SERVER + "/genre/fantastika/popular/<page>/?period=alltime";
        assertEquals(Period.ALL, ListingFilters.periodOf(Provider.KNIGAVUHE, genre, today()));
        assertEquals(Url.SERVER + "/genre/fantastika/popular/<page>/?period=today",
                ListingFilters.withPeriod(Provider.KNIGAVUHE, genre, Period.DAY, today()));
        // a period the site has no word for is not offered and leaves the URL alone
        assertEquals(genre, ListingFilters.withPeriod(Provider.KNIGAVUHE, genre, Period.YEAR, today()));
    }

    @Test
    public void aknigaTakesDayCountsAndAll() {
        assertEquals(Period.YEAR, ListingFilters.periodOf(Provider.AKNIGA, Url.BEST_YEAR_AKNIGA, today()));
        assertEquals(Period.ALL, ListingFilters.periodOf(Provider.AKNIGA, Url.RATING_ALL_TIME_AKNIGA, today()));
        assertEquals(Url.BEST_YEAR_AKNIGA, ListingFilters.withPeriod(Provider.AKNIGA, Url.BEST_TODAY_AKNIGA, Period.YEAR, today()));
        assertEquals(Url.RATING_ALL_TIME_AKNIGA, ListingFilters.withPeriod(Provider.AKNIGA, Url.RATING_MONTH_AKNIGA, Period.ALL, today()));
        assertEquals(Url.BEST_TODAY_AKNIGA, ListingFilters.withPeriod(Provider.AKNIGA, Url.BEST_ALL_TIME_AKNIGA, Period.DAY, today()));
    }

    @Test
    public void knigobludAndOtrubKnowWeekAndMonthOnly() {
        assertEquals(Url.POPULAR_MONTH_KNIGOBLUD,
                ListingFilters.withPeriod(Provider.KNIGOBLUD, Url.POPULAR_WEEK_KNIGOBLUD, Period.MONTH, today()));
        assertEquals(Url.BEST_WEEK_OTRUB, ListingFilters.withPeriod(Provider.OTRUB, Url.BEST_MONTH_OTRUB, Period.WEEK, today()));
        assertEquals(Period.MONTH, ListingFilters.periodOf(Provider.OTRUB, Url.BEST_MONTH_OTRUB, today()));
        assertEquals(Url.BEST_WEEK_OTRUB, ListingFilters.withPeriod(Provider.OTRUB, Url.BEST_WEEK_OTRUB, Period.YEAR, today()));
    }

    @Test
    public void bazaKnigTakesAnyDateBoundAndReadsItBack() {
        String twoWeeks = ListingFilters.withPeriod(Provider.BAZA_KNIG, Url.BEST_BAZA_KNIG, Period.TWO_WEEKS, today());
        assertEquals(Url.SERVER_BAZA_KNIG + "/f/f.pdate=2026-10-09/sort=news_read/order=desc/page/", twoWeeks);
        assertEquals(Period.TWO_WEEKS, ListingFilters.periodOf(Provider.BAZA_KNIG, twoWeeks, today()));
        assertEquals(Period.ALL, ListingFilters.periodOf(Provider.BAZA_KNIG, Url.BEST_BAZA_KNIG, today()));
        // a new date replaces the old one, ALL removes it; the duration bound stays
        String bounded = ListingFilters.withDuration(Provider.BAZA_KNIG, twoWeeks, Duration.OVER_2H);
        assertEquals(Url.SERVER_BAZA_KNIG + "/f/f.pdate=2026-07-25/f.prodolzhitelnost=02:00:00/sort=news_read/order=desc/page/",
                ListingFilters.withPeriod(Provider.BAZA_KNIG, bounded, Period.QUARTER, today()));
        assertEquals(Url.SERVER_BAZA_KNIG + "/f/f.prodolzhitelnost=02:00:00/sort=news_read/order=desc/page/",
                ListingFilters.withPeriod(Provider.BAZA_KNIG, bounded, Period.ALL, today()));
        // a date someone else wrote snaps to the nearest offered period
        assertEquals(Period.MONTH, ListingFilters.periodOf(Provider.BAZA_KNIG,
                Url.SERVER_BAZA_KNIG + "/f/f.pdate=2026-09-20/sort=rating/order=desc/page/", today()));
    }

    @Test
    public void listingsWithoutAPeriodReportNone() {
        assertNull(ListingFilters.periodOf(Provider.KNIGAVUHE, Url.NEW_BOOK, today()));
        assertNull(ListingFilters.periodOf(Provider.BAZA_KNIG, Url.INDEX_BAZA_KNIG, today()));
        assertNull(ListingFilters.periodOf(Provider.ABMP3, Url.INDEX_ABMP3, today()));
    }

    // ---------------------------------------------------------------- auto paging and the bar

    @Test
    public void aHuntStopsAtEnoughMatchesInAFullGridOrEnoughPages() {
        assertTrue(ListingFilters.keepHunting(0, 0, 0));
        assertTrue(ListingFilters.keepHunting(5, 30, 3));
        assertFalse(ListingFilters.keepHunting(6, 21, 1));
        // 6 new matches but a 3-column grid is not full yet
        assertTrue(ListingFilters.keepHunting(6, 12, 1));
        assertFalse(ListingFilters.keepHunting(0, 0, 4));
        assertFalse(ListingFilters.keepHunting(6, 12, 4));
    }

    @Test
    public void theBarShowsOnlyWhatTheListingCanDo() {
        ListingFilters.Bar bar = ListingFilters.bar(true, Provider.KNIGAVUHE, Url.BEST_WEEK, Duration.UNDER_30M, Period.WEEK, 2, 30, false, false);
        assertTrue(bar.visible);
        assertTrue(bar.isFiltering());
        assertEquals(Period.WEEK, bar.period);
        assertEquals(Arrays.asList(Period.DAY, Period.WEEK, Period.MONTH, Period.ALL), bar.periods);
        assertFalse(bar.end);
        assertTrue("more pages, nothing loading: offer to search further", bar.canSearchMore);
        ListingFilters.Bar end = ListingFilters.bar(true, Provider.KNIGAVUHE, Url.BEST_WEEK, Duration.UNDER_30M, Period.WEEK, 2, 30, true, false);
        assertTrue(end.end);
        assertFalse(end.canSearchMore);
        ListingFilters.Bar loading = ListingFilters.bar(true, Provider.KNIGAVUHE, Url.BEST_WEEK, Duration.UNDER_30M, Period.WEEK, 2, 30, false, true);
        assertTrue(loading.loading);
        assertFalse("a load running is not the end of the list", loading.end);
        assertFalse(loading.canSearchMore);
        assertFalse(ListingFilters.bar(true, Provider.KNIGAVUHE, Url.BEST_WEEK, Duration.ANY, Period.WEEK, 30, 30, false, false).canSearchMore);
        // otrub's popular list: a period chip but no duration chip
        ListingFilters.Bar otrub = ListingFilters.bar(true, Provider.OTRUB, Url.BEST_WEEK_OTRUB, Duration.UNDER_30M, Period.WEEK, 0, 10, false, false);
        assertTrue(otrub.visible);
        assertFalse(otrub.durationAvailable);
        assertFalse("a stored duration filter must not hide an otrub list", otrub.isFiltering());
        assertEquals(Period.WEEK, otrub.period);
        // izibuk offers nothing; genre and author lists never show the bar
        assertFalse(ListingFilters.bar(true, Provider.IZIBUK, Url.INDEX_IZIBUK, Duration.UNDER_30M, null, 0, 0, false, false).visible);
        assertFalse(ListingFilters.bar(false, Provider.KNIGAVUHE, Url.SECTIONS, Duration.UNDER_30M, null, 0, 0, false, false).visible);
    }

    @Test
    public void periodKeptByNameRoundTrips() {
        for (Period p : Period.values()) {
            assertEquals(p, Period.of(p.name()));
        }
        assertNull(Period.of(null));
        assertNull(Period.of("FORTNIGHT"));
    }
}
