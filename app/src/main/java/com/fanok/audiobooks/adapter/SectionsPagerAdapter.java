package com.fanok.audiobooks.adapter;

import android.annotation.SuppressLint;
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

public class SectionsPagerAdapter extends FragmentStateAdapter {

    private final ArrayList<String> tabItems;
    private final BookPOJO mBookPOJO;
    private ArrayList<OtherArtistPOJO> mArtistPOJO;
    private final Context mContext;

    @SuppressLint("NotifyDataSetChanged")
    public void setArtistPOJO(ArrayList<OtherArtistPOJO> artistPOJO) {
        mArtistPOJO = artistPOJO;
        notifyDataSetChanged();
    }

    public SectionsPagerAdapter(@NonNull FragmentActivity activity, @NonNull BookPOJO bookPOJO) {
        super(activity);
        mContext = activity;
        mBookPOJO = bookPOJO;
        tabItems = new ArrayList<>();
        tabItems.add(mContext.getResources().getString(R.string.tab_text_1));
        tabItems.add(mContext.getResources().getString(R.string.tab_text_2));
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
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

    public CharSequence getPageTitle(int position) {
        return tabItems.get(position);
    }

    // 4. Меняем getCount на getItemCount
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
