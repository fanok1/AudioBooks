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
        assertFalse(ListingFilters.listsDuration(Provider.OTRUB, Url.BEST_WEEK_OTRUB));
        assertFalse(ListingFilters.listsDuration(null, Url.INDEX));
        for (Provider p : Provider.values()) {
            if (p != Provider.OTRUB) {
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
        // changed on purpose after the live check (2026-10-30): the baza-knig rankings carry v.litres_buy=litres/ before the sort segment (no paid LitRes trial books)
        String ranking = Url.BEST_BAZA_KNIG;
        assertEquals(Url.SERVER_BAZA_KNIG + "/f/t.prodolzhitelnost=00:59:59/v.litres_buy=litres/sort=news_read/order=desc/page/",
                ListingFilters.withDuration(Provider.BAZA_KNIG, ranking, Duration.UNDER_30M));
        assertEquals(Url.SERVER_BAZA_KNIG + "/f/f.prodolzhitelnost=01:00:00/t.prodolzhitelnost=01:59:59/v.litres_buy=litres/sort=news_read/order=desc/page/",
                ListingFilters.withDuration(Provider.BAZA_KNIG, ranking, Duration.FROM_1H_TO_2H));
        assertEquals(Url.SERVER_BAZA_KNIG + "/f/f.prodolzhitelnost=02:00:00/v.litres_buy=litres/sort=news_read/order=desc/page/",
                ListingFilters.withDuration(Provider.BAZA_KNIG, ranking, Duration.OVER_2H));
        // a new bound replaces the old one, ANY removes it
        String bounded = ListingFilters.withDuration(Provider.BAZA_KNIG, ranking, Duration.FROM_1H_TO_2H);
        assertEquals(Url.SERVER_BAZA_KNIG + "/f/f.prodolzhitelnost=02:00:00/v.litres_buy=litres/sort=news_read/order=desc/page/",
                ListingFilters.withDuration(Provider.BAZA_KNIG, bounded, Duration.OVER_2H));
        assertEquals(ranking, ListingFilters.withDuration(Provider.BAZA_KNIG, bounded, Duration.ANY));
    }

    @Test
    public void bazaKnigWidensACustomRangeToWholeHours() {
        // changed on purpose after the live check (2026-10-30): the baza-knig rankings carry v.litres_buy=litres/ before the sort segment (no paid LitRes trial books)
        String ranking = Url.BEST_BAZA_KNIG;
        // from rounds down (0 h is no bound), to rounds up to the end of the hour before it
        assertEquals(Url.SERVER_BAZA_KNIG + "/f/t.prodolzhitelnost=01:59:59/v.litres_buy=litres/sort=news_read/order=desc/page/",
                ListingFilters.withDuration(Provider.BAZA_KNIG, ranking, Duration.of(45, 90)));
        assertEquals(Url.SERVER_BAZA_KNIG + "/f/f.prodolzhitelnost=01:00:00/t.prodolzhitelnost=02:59:59/v.litres_buy=litres/sort=news_read/order=desc/page/",
                ListingFilters.withDuration(Provider.BAZA_KNIG, ranking, Duration.of(90, 180)));
        assertEquals(Url.SERVER_BAZA_KNIG + "/f/f.prodolzhitelnost=10:00:00/t.prodolzhitelnost=29:59:59/v.litres_buy=litres/sort=news_read/order=desc/page/",
                ListingFilters.withDuration(Provider.BAZA_KNIG, ranking, Duration.of(600, 1800)));
        assertEquals(Url.SERVER_BAZA_KNIG + "/f/f.prodolzhitelnost=20:00:00/v.litres_buy=litres/sort=news_read/order=desc/page/",
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
        assertEquals(genre, ListingFilters.withDuration(Provider.BAZA_KNIG, genre, Duration.of(90, 180)));
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
        // changed on purpose after the live check (2026-10-30): the baza-knig rankings carry v.litres_buy=litres/ before the sort segment (no paid LitRes trial books)
        String twoWeeks = ListingFilters.withPeriod(Provider.BAZA_KNIG, Url.BEST_BAZA_KNIG, Period.TWO_WEEKS, today());
        assertEquals(Url.SERVER_BAZA_KNIG + "/f/f.pdate=2026-10-09/v.litres_buy=litres/sort=news_read/order=desc/page/", twoWeeks);
        assertEquals(Period.TWO_WEEKS, ListingFilters.periodOf(Provider.BAZA_KNIG, twoWeeks, today()));
        assertEquals(Period.ALL, ListingFilters.periodOf(Provider.BAZA_KNIG, Url.BEST_BAZA_KNIG, today()));
        // a new date replaces the old one, ALL removes it; the duration bound stays
        String bounded = ListingFilters.withDuration(Provider.BAZA_KNIG, twoWeeks, Duration.OVER_2H);
        assertEquals(Url.SERVER_BAZA_KNIG + "/f/f.pdate=2026-07-25/f.prodolzhitelnost=02:00:00/v.litres_buy=litres/sort=news_read/order=desc/page/",
                ListingFilters.withPeriod(Provider.BAZA_KNIG, bounded, Period.QUARTER, today()));
        assertEquals(Url.SERVER_BAZA_KNIG + "/f/f.prodolzhitelnost=02:00:00/v.litres_buy=litres/sort=news_read/order=desc/page/",
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
        // izibuk: the duration chip without any period; genre and author lists never show the bar
        ListingFilters.Bar izibuk = ListingFilters.bar(true, Provider.IZIBUK, Url.INDEX_IZIBUK, Duration.UNDER_30M, null, 0, 0, false, false);
        assertTrue(izibuk.isFiltering());
        assertTrue(izibuk.periods.isEmpty());
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

    @Test
    public void aSortInAGenreListingStaysInTheGenre() {
        java.util.Set<String> kbGenres = Collections.singleton(Url.SERVER_KNIGOBLUD + "/0c6d4928-555a-4671-99e9-9bcf9a37d382");
        // akniga: the section's own top/discussed, also when re-sorting a section ranking
        String section = ListingFilters.genreOf(Provider.AKNIGA, Url.SERVER_AKNIGA + "/section/classic/page<page>/");
        assertEquals(Url.SERVER_AKNIGA + "/section/classic/top/page<page>/?period=7",
                ListingFilters.inGenre(Provider.AKNIGA, Url.BEST_WEEK_AKNIGA, section));
        assertEquals(section, ListingFilters.genreOf(Provider.AKNIGA, Url.SERVER_AKNIGA + "/section/classic/top/page<page>/?period=7"));
        assertNull(ListingFilters.genreOf(Provider.AKNIGA, Url.BEST_WEEK_AKNIGA));
        // baza-knig: the category root (renamed ones end in "_"); the index and the site-wide filter are no genre
        String category = ListingFilters.genreOf(Provider.BAZA_KNIG, Url.SERVER_BAZA_KNIG + "/uzhasy-mistika_/page/");
        // changed on purpose after the live check (2026-10-30): the baza-knig rankings carry v.litres_buy=litres/ before the sort segment (no paid LitRes trial books)
        assertEquals(Url.SERVER_BAZA_KNIG + "/uzhasy-mistika_/f/v.litres_buy=litres/sort=rating/order=desc/page/",
                ListingFilters.inGenre(Provider.BAZA_KNIG, Url.RATING_BAZA_KNIG, category));
        assertEquals(Url.SERVER_BAZA_KNIG + "/uzhasy-mistika_/page/",
                ListingFilters.inGenre(Provider.BAZA_KNIG, Url.NEW_BOOK_BAZA_KNIG, category));
        assertNull(ListingFilters.genreOf(Provider.BAZA_KNIG, Url.INDEX_BAZA_KNIG));
        assertNull(ListingFilters.genreOf(Provider.BAZA_KNIG, Url.RATING_BAZA_KNIG));
        // poleknig: /genres/<id>/new and /best
        String genre = ListingFilters.genreOf(Provider.POLEKNIG, Url.SERVER_POLEKNIG + "/genres/12?p=");
        assertEquals(Url.SERVER_POLEKNIG + "/genres/12/best?p=", ListingFilters.inGenre(Provider.POLEKNIG, Url.BEST_POLEKNIG, genre));
        assertEquals(genre, ListingFilters.genreOf(Provider.POLEKNIG, Url.SERVER_POLEKNIG + "/genres/12/best?p="));
        assertNull(ListingFilters.genreOf(Provider.POLEKNIG, Url.SERVER_POLEKNIG + "/genres?p="));
        // otrub, knigoblud: the genre listing for "new", the site-wide ranking narrowed on the device
        String slug = ListingFilters.genreOf(Provider.OTRUB, Url.SERVER_OTRUB + "/genres/fantastika.html?p=");
        assertEquals(Url.SERVER_OTRUB + "/genres/fantastika.html?p=", ListingFilters.inGenre(Provider.OTRUB, Url.NEW_BOOK_OTRUB, slug));
        String ranking = ListingFilters.inGenre(Provider.OTRUB, Url.BEST_WEEK_OTRUB, slug);
        assertEquals(Url.SERVER_OTRUB + "/popular/?genre=fantastika&period=week&p=", ranking);
        assertEquals("fantastika", ListingFilters.cardGenre(Provider.OTRUB, ranking));
        assertEquals(slug, ListingFilters.genreOf(Provider.OTRUB, ranking));
        assertEquals(Url.SERVER_OTRUB + "/popular/?genre=fantastika&period=month&p=",
                ListingFilters.withPeriod(Provider.OTRUB, ranking, Period.MONTH, today()));
        String id = ListingFilters.genreOf(Provider.KNIGOBLUD, kbGenres.iterator().next());
        assertEquals(Url.SERVER_KNIGOBLUD + "/today/?genre=" + id + "&page=",
                ListingFilters.inGenre(Provider.KNIGOBLUD, Url.TODAY_KNIGOBLUD, id));
        assertEquals(kbGenres.iterator().next(), ListingFilters.inGenre(Provider.KNIGOBLUD, Url.INDEX_KNIGOBLUD, id));
        // an author listing looks like a genre one: only the known genres count
        assertNull(ListingFilters.genreOf(Provider.KNIGOBLUD, Url.SERVER_KNIGOBLUD + "/426b52d3-cd88-4dc3-9cd1-487bcbb0ff30"));
        assertNull(ListingFilters.cardGenre(Provider.OTRUB, Url.BEST_WEEK_OTRUB));
    }

    @Test
    public void aNarrowedRankingShowsOnlyTheCardsOfItsGenre() {
        BookPOJO fantasy = book("1:00:00");
        fantasy.setUrlGenre(Url.SERVER_OTRUB + "/genres/fantastika.html?p=");
        BookPOJO novel = book("1:00:00");
        novel.setUrlGenre(Url.SERVER_OTRUB + "/genres/roman-proza.html?p=");
        BookPOJO none = book("1:00:00");
        java.util.ArrayList<BookPOJO> all = new java.util.ArrayList<>(Arrays.asList(fantasy, novel, none));
        assertEquals(Collections.singletonList(fantasy), ListingFilters.filter(all, Duration.ANY, "fantastika"));
        assertEquals(0, ListingFilters.filter(all, Duration.UNDER_30M, "fantastika").size());
        assertEquals(all, ListingFilters.filter(all, Duration.ANY, null));
        String ranking = Url.SERVER_OTRUB + "/popular/?genre=fantastika&period=week&p=";
        ListingFilters.Bar bar = ListingFilters.bar(true, Provider.OTRUB, ranking, Duration.ANY, Period.WEEK, 1, 3, false, false);
        assertTrue(bar.isFiltering());
        assertTrue(bar.canSearchMore);
    }
}
