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
import com.fanok.audiobooks.fragment.OtherSourceFragment;
import com.fanok.audiobooks.fragment.SeriesBookFragment;
import com.fanok.audiobooks.pojo.BookPOJO;
import com.fanok.audiobooks.pojo.OtherArtistPOJO;
import java.util.ArrayList;
import org.jetbrains.annotations.NotNull;


public class SectionsPagerAdapter extends FragmentPagerAdapter {

    private final ArrayList<String> tabItems;
    private final BookPOJO mBookPOJO;

    private ArrayList<OtherArtistPOJO> mArtistPOJO;

    private final Context mContext;

    public void setArtistPOJO(ArrayList<OtherArtistPOJO> artistPOJO) {
        mArtistPOJO = artistPOJO;
        notifyDataSetChanged();
    }

    public SectionsPagerAdapter(@NonNull Context context, @NonNull FragmentManager fm,
            @NonNull BookPOJO bookPOJO) {

        super(fm, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT);
        mBookPOJO = bookPOJO;
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
                return DescriptionBookFragment.newInstance(mBookPOJO);
            case 1:
                return ComentsBookFragment.newInstance(mBookPOJO.getUrl());

        }

        if (tabItems.get(position).equals(mContext.getResources().getString(R.string.tab_text_3))) {
            return SeriesBookFragment.newInstance(mBookPOJO.getUrl());
        }

        if (tabItems.get(position).equals(mContext.getResources().getString(R.string.tab_text_4))) {
            return OtherArtistFragment.newInstance(mBookPOJO);
        }

        if (tabItems.get(position).equals(mContext.getResources().getString(R.string.tab_text_5))) {
            return OtherSourceFragment.newInstance(mArtistPOJO);
        }

        return DescriptionBookFragment.newInstance(mBookPOJO);
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

    public void addTabPage(String title, int postion) {
        tabItems.add(postion, title);
        notifyDataSetChanged();
    }
}