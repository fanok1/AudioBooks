package com.fanok.audiobooks.adapter;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;

import com.fanok.audiobooks.R;
import com.fanok.audiobooks.fragment.ComentsBookFragment;
import com.fanok.audiobooks.fragment.DescriptionBookFragment;
import com.fanok.audiobooks.fragment.OtherArtistFragment;
import com.fanok.audiobooks.fragment.SeriesBookFragment;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;


public class SectionsPagerAdapter extends FragmentPagerAdapter {

    private final ArrayList<String> tabItems;
    private final String mUrl;
    private Context mContext;

    public SectionsPagerAdapter(@NonNull Context context, @NonNull FragmentManager fm,
            @NonNull String url) {
        super(fm, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT);
        mUrl = url;
        tabItems = new ArrayList<>();
        tabItems.add(context.getResources().getString(R.string.tab_text_1));
        tabItems.add(context.getResources().getString(R.string.tab_text_2));
        mContext = context;

    }

    @NotNull
    @Override
    public Fragment getItem(int position) {
        switch (position) {
            case 0:
                return DescriptionBookFragment.newInstance(mUrl);
            case 1:
                return ComentsBookFragment.newInstance(mUrl);

        }

        if (tabItems.get(position).equals(mContext.getResources().getString(R.string.tab_text_3))) {
            return SeriesBookFragment.newInstance(mUrl);
        }

        if (tabItems.get(position).equals(mContext.getResources().getString(R.string.tab_text_4))) {
            return OtherArtistFragment.newInstance(mUrl);
        }

        return DescriptionBookFragment.newInstance(mUrl);
    }

    @Nullable
    @Override
    public CharSequence getPageTitle(int position) {
        return tabItems.get(position);
    }

    @Override
    public int getCount() {
        return tabItems.size();
    }

    public void addTabPage(String title) {
        tabItems.add(title);
        notifyDataSetChanged();
    }
}