package com.fanok.audiobooks.search;

import io.reactivex.functions.Function;

import java.util.ArrayList;
import java.util.HashSet;

/**
 * The page cursor of one paged listing: the items shown, the keys already seen, the last page applied and
 * whether the list has ended. Only a successful page moves it: a refresh (page 1) builds a new list and
 * swaps it in, so a failed refresh or a failed page leaves cursor, list and dedupe set as they were.
 */
public final class ListingCursor<T> {

    private final Function<T, String> key;
    private ArrayList<T> items = new ArrayList<>();
    private HashSet<String> seen = new HashSet<>();
    private int page;
    private boolean end;

    public ListingCursor(Function<T, String> key) {
        this.key = key;
    }

    /** The page to ask for when scrolling on. */
    public int nextPage() {
        return page + 1;
    }

    public int page() {
        return page;
    }

    public boolean isEnd() {
        return end;
    }

    /** The current list; a refresh replaces the instance. */
    public ArrayList<T> items() {
        return items;
    }

    /**
     * Applies a successful answer for {@code requested}: page 1 replaces the list, a later page appends.
     * The list ends when the provider has no next page, or when a page new to the cursor brought cards
     * that were all seen already (providers that repeat their last page past the end).
     */
    public void apply(int requested, ProviderPage<T> result) {
        boolean replace = requested <= 1;
        boolean fresh = replace || requested > page;
        ArrayList<T> list = replace ? new ArrayList<>() : items;
        HashSet<String> keys = replace ? new HashSet<>() : seen;
        boolean added = false;
        for (T item : result.items) {
            String k;
            try {
                k = key.apply(item);
            } catch (Exception e) {
                k = null;
            }
            if (k == null || keys.add(k)) {
                list.add(item);
                added = true;
            }
        }
        items = list;
        seen = keys;
        if (fresh) {
            page = requested;
        }
        end = !result.hasMore || (fresh && !added && !result.items.isEmpty());
    }
}
