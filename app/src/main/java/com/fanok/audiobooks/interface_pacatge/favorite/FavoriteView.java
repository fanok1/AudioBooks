package com.fanok.audiobooks.interface_pacatge.favorite;

import android.support.v4.app.Fragment;

import com.arellomobile.mvp.MvpView;
import com.arellomobile.mvp.viewstate.strategy.SkipStrategy;
import com.arellomobile.mvp.viewstate.strategy.StateStrategyType;
import com.fanok.audiobooks.pojo.BookPOJO;

import java.util.ArrayList;

public interface FavoriteView extends MvpView {
    void setLayoutManager();

    void setLayoutManager(int count);

    void showData(ArrayList<BookPOJO> bookPOJOS);

    void clearData();

    void showToast(int message);

    void showToast(String message);

    @StateStrategyType(SkipStrategy.class)
    void showFragment(Fragment fragment, String tag);

}
