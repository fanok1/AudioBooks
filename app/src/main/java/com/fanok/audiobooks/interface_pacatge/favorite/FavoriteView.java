package com.fanok.audiobooks.interface_pacatge.favorite;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.arellomobile.mvp.MvpView;
import com.arellomobile.mvp.viewstate.strategy.SkipStrategy;
import com.arellomobile.mvp.viewstate.strategy.StateStrategyType;
import com.fanok.audiobooks.pojo.BookPOJO;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

public interface FavoriteView extends MvpView {
    void setLayoutManager();

    void setLayoutManager(int count);

    void showData(ArrayList<BookPOJO> bookPOJOS);

    void clearData();

    void showToast(int message);

    void showToast(String message);

    void showProgres(boolean b);

    void setSubTitle(@NotNull String text);

    @StateStrategyType(SkipStrategy.class)
    void showFragment(Fragment fragment, String tag);

    @StateStrategyType(SkipStrategy.class)
    void showBooksActivity(@NonNull BookPOJO bookPOJO);

    @StateStrategyType(SkipStrategy.class)
    void showSearchActivity(int modelId);

}
