package com.fanok.audiobooks.search;

import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.fanok.audiobooks.Consts;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The user's sources ("Настройки → Источники"): every {@link Provider} in the user's order, the first
 * {@link #activeCount} of them active. Only active sources exist for the source menu, the settings, the searches and
 * the availability check; an inactive one is still known to links, the library and the network routes
 * ({@code Provider.ofUrl} and friends stay on every provider). Stored as one preference, {@link #PREF}:
 * {@code "abook,izibuc|otrub"}, the active ones before {@code |}. A site added later is not in it and starts inactive.
 */
public final class Sources {

    public static final String PREF = "pref_sources";
    /** The most sources that can be active at once. */
    public static final int MAX_ACTIVE = 13;
    private static final String SELECTED = "sorce_books";

    /** One consistent snapshot: the order and the active count change together. */
    static final class State {
        final List<Provider> order;
        final int active;

        State(List<Provider> order, int active) {
            this.order = Collections.unmodifiableList(order);
            this.active = active;
        }
    }

    private static volatile State state = parse(null);

    private Sources() {
    }

    /** The active sources, in the user's order. */
    @NonNull
    public static List<Provider> active() {
        State s = state;
        return s.order.subList(0, s.active);
    }

    /** Every source in the user's order, the active ones first. */
    @NonNull
    public static List<Provider> all() {
        return state.order;
    }

    public static int activeCount() {
        return state.active;
    }

    public static boolean isActive(@Nullable Provider provider) {
        return provider != null && active().contains(provider);
    }

    /** Reads the stored list; call once at the start, before anything asks for the sources. */
    public static synchronized void load(@NonNull SharedPreferences prefs) {
        String stored;
        try {
            stored = prefs.getString(PREF, null);
        } catch (ClassCastException e) {
            stored = null;
        }
        state = parse(stored);
        keepSelectedActive(prefs);
    }

    /**
     * Stores {@code order} with its first {@code active} sources active (normalized as {@link #parse} does) and makes
     * the selected source an active one. Returns true when that moved the selection.
     */
    public static synchronized boolean save(@NonNull SharedPreferences prefs, @NonNull List<Provider> order,
            int active) {
        State s = parse(format(order, active));
        state = s;
        prefs.edit().putString(PREF, format(s.order, s.active)).apply();
        return keepSelectedActive(prefs);
    }

    /** The selected source must be active: otherwise the first active one is selected. True when it moved. */
    private static boolean keepSelectedActive(@NonNull SharedPreferences prefs) {
        String selected;
        try {
            selected = prefs.getString(SELECTED, null);
        } catch (ClassCastException e) {
            selected = null;
        }
        for (Provider provider : active()) {
            if (provider.prefValue.equals(selected)) {
                return false;
            }
        }
        if (selected == null) {
            return false; // nothing chosen yet: the settings' default applies, and it is checked when chosen
        }
        Provider first = active().get(0);
        prefs.edit().putString(SELECTED, first.prefValue).apply();
        Consts.setSOURCE(first.prefValue);
        return true;
    }

    /**
     * Pure: the stored text read back. Unknown and repeated ids are dropped; a provider missing from it is appended
     * inactive; at least one and at most {@link #MAX_ACTIVE} are active (the overflow becomes the first inactive).
     * No text: the declaration order, the first {@link #MAX_ACTIVE} active.
     */
    @NonNull
    static State parse(@Nullable String text) {
        Set<Provider> order = new LinkedHashSet<>();
        int active;
        if (text == null) {
            order.addAll(Arrays.asList(Provider.values()));
            active = MAX_ACTIVE;
        } else {
            int bar = text.indexOf('|');
            addAll(order, bar < 0 ? text : text.substring(0, bar));
            active = order.size();
            if (bar >= 0) {
                addAll(order, text.substring(bar + 1));
            }
            order.addAll(Arrays.asList(Provider.values()));
        }
        active = Math.max(1, Math.min(Math.min(MAX_ACTIVE, order.size()), active));
        return new State(new ArrayList<>(order), active);
    }

    private static void addAll(@NonNull Set<Provider> order, @NonNull String ids) {
        for (String id : ids.split(",")) {
            for (Provider provider : Provider.values()) {
                if (provider.prefValue.equals(id.trim())) {
                    order.add(provider);
                }
            }
        }
    }

    /** Pure: the stored text of {@code order} with its first {@code active} sources active. */
    @NonNull
    static String format(@NonNull List<Provider> order, int active) {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < order.size(); i++) {
            if (i == active) {
                text.append('|');
            } else if (i > 0) {
                text.append(',');
            }
            text.append(order.get(i).prefValue);
        }
        if (active >= order.size()) {
            text.append('|');
        }
        return text.toString();
    }
}
