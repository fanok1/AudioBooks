package com.fanok.audiobooks.source;

import androidx.annotation.Nullable;

import com.fanok.audiobooks.R;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Every sort a book listing can offer, across all sites: the menu item, the label added to the listing's subtitle and
 * the back-stack tag of the sorted listing. A source declares which of them its listings take
 * ({@link SourceParser#sorts}) and the URL of each ({@link SourceParser#sortUrl}); the Order menu and the navigation
 * are built from that, so a new site needs no menu resource and no presenter branch. A sort of a new kind is one row
 * here (plus its strings and menu id).
 */
public enum Sort {
    NEW(R.id.new_data, R.string.new_data, R.string.order_new, "audioBooksOrederNew"),
    POPULAR(R.id.popular, R.string.popular, R.string.order_popular, "audioBooksOrederDiscussedAllTime"),
    POPULAR_DAY(R.id.popular_day, R.string.in_day, R.string.order_popular, "audioBooksOrederDiscussedDay"),
    POPULAR_WEEK(R.id.popular_week, R.string.in_week, R.string.order_popular, "audioBooksOrederDiscussedWeek"),
    POPULAR_MONTH(R.id.popular_month, R.string.in_month, R.string.order_popular, "audioBooksOrederDiscussedMonth"),
    POPULAR_YEAR(R.id.popular_year, R.string.in_year, R.string.order_popular, "audioBooksOrederDiscussedMonth"),
    POPULAR_ALL_TIME(R.id.popular_all_time, R.string.all_time, R.string.order_popular,
            "audioBooksOrederDiscussedAllTime"),
    RATING(R.id.reting, R.string.reting, R.string.order_reting, "audioBooksOrederBestAllTime"),
    RATING_DAY(R.id.reting_day, R.string.in_day, R.string.order_reting, "audioBooksOrederBestDay"),
    RATING_WEEK(R.id.reting_week, R.string.in_week, R.string.order_reting, "audioBooksOrederBestWeek"),
    RATING_MONTH(R.id.reting_month, R.string.in_month, R.string.order_reting, "audioBooksOrederBestMonth"),
    RATING_YEAR(R.id.reting_year, R.string.in_year, R.string.order_reting, "audioBooksOrederBestMonth"),
    RATING_ALL_TIME(R.id.reting_all_time, R.string.all_time, R.string.order_reting, "audioBooksOrederBestAllTime"),
    COMMENTS(R.id.coments, R.string.comets, R.string.order_coments, "audioBooksOrederComents"),
    YEARS(R.id.years, R.string.years, R.string.order_year, "audioBooksOrederYears"),
    TITLE(R.id.sort_title, R.string.order_title, R.string.order_title, "audioBooksOrderTitle"),
    DURATION(R.id.sort_duration, R.string.order_duration, R.string.order_duration, "audioBooksOrderDuration"),
    TODAY(R.id.knigoblud_today, R.string.knigoblud_today, R.string.knigoblud_today, "knigobludToday"),
    TRENDING(R.id.knigoblud_trending, R.string.knigoblud_trending, R.string.knigoblud_trending, "knigobludTrending"),
    FINISHED(R.id.knigoblud_finished, R.string.knigoblud_finished, R.string.knigoblud_finished, "knigobludFinished"),
    TOP_100(R.id.abmp3_top100, R.string.abmp3_top100, R.string.abmp3_top100, "abmp3Top100"),
    RANDOM(R.id.abmp3_random, R.string.abmp3_random, R.string.abmp3_random, "abmp3Random"),
    COLLECTIONS_NEW(R.id.collections_new, R.string.new_data, R.string.order_new, "audioBooksOrederNew"),
    COLLECTIONS_TOP(R.id.collections_top, R.string.popular, R.string.order_popular,
            "audioBooksOrederDiscussedAllTime"),
    COLLECTIONS_DISCUSSED(R.id.collections_discussed, R.string.order_discussed, R.string.order_discussed,
            "audioBooksOrederBestAllTime");

    /** The menu item id; stable within a build, never stored. */
    public final int menuId;
    /** The menu item's title (a period's name inside a submenu). */
    public final int title;
    /** What the sorted listing appends to its subtitle. */
    public final int label;
    /** The back-stack tag of the sorted listing. */
    public final String tag;

    Sort(int menuId, int title, int label, String tag) {
        this.menuId = menuId;
        this.title = title;
        this.label = label;
        this.tag = tag;
    }

    /** The sort of a menu item; null for any other item. */
    @Nullable
    public static Sort ofMenuId(int menuId) {
        for (Sort sort : values()) {
            if (sort.menuId == menuId) {
                return sort;
            }
        }
        return null;
    }

    /** One entry of the Order menu: a sort, or a titled submenu of sorts (the periods of one ranking). */
    public static final class Entry {
        /** The sort of a plain entry; null for a submenu. */
        @Nullable
        public final Sort sort;
        /** The title of a submenu, else the sort's own. */
        public final int title;
        public final List<Sort> items;

        private Entry(@Nullable Sort sort, int title, List<Sort> items) {
            this.sort = sort;
            this.title = title;
            this.items = items;
        }
    }

    public static Entry item(Sort sort) {
        return new Entry(sort, sort.title, Collections.<Sort>emptyList());
    }

    public static Entry submenu(int title, Sort... items) {
        return new Entry(null, title, Collections.unmodifiableList(Arrays.asList(items)));
    }

    /** A plain menu of {@code sorts}, in order. */
    public static List<Entry> menu(Sort... sorts) {
        Entry[] entries = new Entry[sorts.length];
        for (int i = 0; i < sorts.length; i++) {
            entries[i] = item(sorts[i]);
        }
        return Collections.unmodifiableList(Arrays.asList(entries));
    }
}
