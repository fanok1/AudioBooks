package com.fanok.audiobooks.adapter;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import com.fanok.audiobooks.R;
import com.fanok.audiobooks.fragment.ComentsBookFragment;
import com.fanok.audiobooks.fragment.DescriptionBookFragment;
import com.fanok.audiobooks.fragment.OtherArtistFragment;
import com.fanok.audiobooks.fragment.OtherSourceFragment;
import com.fanok.audiobooks.fragment.SeriesBookFragment;
import com.fanok.audiobooks.pojo.BookPOJO;
import com.fanok.audiobooks.pojo.OtherArtistPOJO;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * The book screen's tabs, kept by kind (one of the five known tabs) in that fixed order. Each tab has a stable id
 * derived from its kind, so a tab inserted before an already-created one keeps the created fragment (UI-11); the
 * "other source" and "other artist" tabs' ids also carry a version that changes with their data, so a stale fragment
 * is replaced rather than kept. Titles are for display only: the comments tab's carries the count.
 */
public class SectionsPagerAdapter extends FragmentStateAdapter {

    public static final int DESCRIPTION = 0;
    public static final int COMMENTS = 1;
    public static final int SERIES = 2;
    public static final int OTHER_ARTIST = 3;
    public static final int OTHER_SOURCE = 4;

    private static final long OTHER_SOURCE_VERSION_STEP = 100;

    /** The kinds of the shown tabs, in order. */
    private final ArrayList<Integer> tabItems;
    private final BookPOJO mBookPOJO;
    private ArrayList<OtherArtistPOJO> mArtistPOJO;
    private ArrayList<OtherArtistPOJO> mOtherArtists;
    private final Context mContext;
    private final String[] knownTabs;
    private long otherSourceVersion = 0;
    private long otherArtistVersion = 0;
    private int comentsCount;

    public void setArtistPOJO(ArrayList<OtherArtistPOJO> artistPOJO) {
        mArtistPOJO = artistPOJO == null ? null : new ArrayList<>(artistPOJO);
        otherSourceVersion++;
        int position = tabItems.indexOf(OTHER_SOURCE);
        if (position >= 0) {
            notifyItemChanged(position);
        }
    }

    /** The other narrators the "other artist" tab shows; set before the tab is shown. */
    public void setOtherArtists(ArrayList<OtherArtistPOJO> artists) {
        mOtherArtists = artists == null ? null : new ArrayList<>(artists);
        otherArtistVersion++;
        int position = tabItems.indexOf(OTHER_ARTIST);
        if (position >= 0) {
            notifyItemChanged(position);
        }
    }

    public SectionsPagerAdapter(@NonNull FragmentActivity activity, @NonNull BookPOJO bookPOJO) {
        super(activity);
        mContext = activity;
        mBookPOJO = bookPOJO;
        knownTabs = new String[]{
                mContext.getResources().getString(R.string.tab_text_1),
                mContext.getResources().getString(R.string.tab_text_2),
                mContext.getResources().getString(R.string.tab_text_3),
                mContext.getResources().getString(R.string.tab_text_4),
                mContext.getResources().getString(R.string.tab_text_5)};
        tabItems = new ArrayList<>();
        tabItems.add(DESCRIPTION);
    }

    private long idOf(int kind) {
        if (kind == OTHER_SOURCE) {
            return kind + OTHER_SOURCE_VERSION_STEP * (otherSourceVersion + 1);
        }
        if (kind == OTHER_ARTIST) {
            return kind + OTHER_SOURCE_VERSION_STEP * (otherArtistVersion + 1);
        }
        return kind;
    }

    @Override
    public long getItemId(int position) {
        return idOf(tabItems.get(position));
    }

    @Override
    public boolean containsItem(long itemId) {
        for (int kind : tabItems) {
            if (idOf(kind) == itemId) {
                return true;
            }
        }
        return false;
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        switch (tabItems.get(position)) {
            case COMMENTS:
                return ComentsBookFragment.newInstance(mBookPOJO.getUrl());
            case SERIES:
                return SeriesBookFragment.newInstance(mBookPOJO.getUrl());
            case OTHER_ARTIST:
                return OtherArtistFragment.newInstance(mBookPOJO,
                        mOtherArtists == null ? new ArrayList<>() : mOtherArtists);
            case OTHER_SOURCE:
                return OtherSourceFragment.newInstance(mArtistPOJO == null ? new ArrayList<>() : mArtistPOJO);
            default:
                return DescriptionBookFragment.newInstance(mBookPOJO);
        }
    }

    public CharSequence getPageTitle(int position) {
        int kind = tabItems.get(position);
        return kind == COMMENTS ? mContext.getString(R.string.tab_text_2_count, comentsCount) : knownTabs[kind];
    }

    @Override
    public int getItemCount() {
        return tabItems.size();
    }

    /** Shows the tab of {@code kind} at its place in the fixed order; a shown tab stays as it is. */
    public void showTab(int kind) {
        if (tabItems.contains(kind)) {
            return;
        }
        int position = 0;
        while (position < tabItems.size() && tabItems.get(position) < kind) {
            position++;
        }
        tabItems.add(position, kind);
        notifyItemInserted(position);
    }

    /** Shows the comments tab titled with their count. */
    public void showComents(int count) {
        comentsCount = count;
        showTab(COMMENTS);
    }

    /** The position of the tab a known title names, -1 when it is not shown. */
    public int positionOf(String title) {
        return tabItems.indexOf(Arrays.asList(knownTabs).indexOf(title));
    }
}
