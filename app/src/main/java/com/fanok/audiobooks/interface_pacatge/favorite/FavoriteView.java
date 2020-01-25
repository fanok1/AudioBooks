package com.fanok.audiobooks.interface_pacatge.favorite;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.arellomobile.mvp.MvpView;
import com.arellomobile.mvp.viewstate.strategy.AddToEndSingleStrategy;
import com.arellomobile.mvp.viewstate.strategy.SkipStrategy;
import com.arellomobile.mvp.viewstate.strategy.StateStrategyType;
import com.fanok.audiobooks.pojo.BookPOJO;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

public interface FavoriteView extends MvpView {
    @StateStrategyType(AddToEndSingleStrategy.class)
    void setLayoutManager();

    @StateStrategyType(AddToEndSingleStrategy.class)
    void setLayoutManager(int count);

    @StateStrategyType(AddToEndSingleStrategy.class)
    void showData(ArrayList<BookPOJO> bookPOJOS);

    @StateStrategyType(AddToEndSingleStrategy.class)
    void clearData();

    @StateStrategyType(SkipStrategy.class)
    void showToast(int message);

    @StateStrategyType(SkipStrategy.class)
    void showToast(String message);

    @StateStrategyType(AddToEndSingleStrategy.class)
    void showProgres(boolean b);

    @StateStrategyType(AddToEndSingleStrategy.class)
    void setSubTitle(@NotNull String text);

    @StateStrategyType(SkipStrategy.class)
    void showFragment(Fragment fragment, String tag);

    @StateStrategyType(SkipStrategy.class)
    void showBooksActivity(@NonNull BookPOJO bookPOJO);

    @StateStrategyType(SkipStrategy.class)
    void showSearchActivity(int modelId);

}
