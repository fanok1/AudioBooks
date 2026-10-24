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

/**
 * The book screen's tabs. Each tab has a stable id derived from which of the five known tabs it is, so a tab
 * inserted before an already-created one keeps the created fragment (UI-11); the "other source" tab's id also
 * carries a version that changes with its data, so a stale fragment is replaced rather than kept.
 */
public class SectionsPagerAdapter extends FragmentStateAdapter {

    private static final long OTHER_SOURCE_VERSION_STEP = 100;

    private final ArrayList<String> tabItems;
    private final BookPOJO mBookPOJO;
    private ArrayList<OtherArtistPOJO> mArtistPOJO;
    private final Context mContext;
    private final String[] knownTabs;
    private long otherSourceVersion = 0;

    public void setArtistPOJO(ArrayList<OtherArtistPOJO> artistPOJO) {
        mArtistPOJO = artistPOJO == null ? null : new ArrayList<>(artistPOJO);
        otherSourceVersion++;
        int position = tabItems.indexOf(knownTabs[4]);
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
        tabItems.add(knownTabs[0]);
        tabItems.add(knownTabs[1]);
    }

    private int kindOf(String title) {
        for (int i = 0; i < knownTabs.length; i++) {
            if (knownTabs[i].equals(title)) {
                return i;
            }
        }
        return 0;
    }

    private long idOf(String title) {
        int kind = kindOf(title);
        return kind == 4 ? kind + OTHER_SOURCE_VERSION_STEP * (otherSourceVersion + 1) : kind;
    }

    @Override
    public long getItemId(int position) {
        return idOf(tabItems.get(position));
    }

    @Override
    public boolean containsItem(long itemId) {
        for (String title : tabItems) {
            if (idOf(title) == itemId) {
                return true;
            }
        }
        return false;
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        switch (kindOf(tabItems.get(position))) {
            case 1:
                return ComentsBookFragment.newInstance(mBookPOJO.getUrl());
            case 2:
                return SeriesBookFragment.newInstance(mBookPOJO.getUrl());
            case 3:
                return OtherArtistFragment.newInstance(mBookPOJO);
            case 4:
                return OtherSourceFragment.newInstance(mArtistPOJO == null ? new ArrayList<>() : mArtistPOJO);
            default:
                return DescriptionBookFragment.newInstance(mBookPOJO);
        }
    }

    public CharSequence getPageTitle(int position) {
        return tabItems.get(position);
    }

    @Override
    public int getItemCount() {
        return tabItems.size();
    }

    public void addTabPage(String title) {
        int position = tabItems.size();
        tabItems.add(title);
        notifyItemInserted(position);
    }

    public void addTabPage(String title, int position) {
        tabItems.add(position, title);
        notifyItemInserted(position);
    }
}
