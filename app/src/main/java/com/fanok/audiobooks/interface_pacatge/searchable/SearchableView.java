package com.fanok.audiobooks.interface_pacatge.searchable;


import androidx.annotation.NonNull;

import com.arellomobile.mvp.MvpView;
import com.arellomobile.mvp.viewstate.strategy.AddToEndSingleStrategy;
import com.arellomobile.mvp.viewstate.strategy.SkipStrategy;
import com.arellomobile.mvp.viewstate.strategy.StateStrategyType;
import com.fanok.audiobooks.pojo.BookPOJO;
import com.fanok.audiobooks.pojo.GenrePOJO;
import com.fanok.audiobooks.pojo.SearcheblPOJO;

import java.util.ArrayList;

public interface SearchableView extends MvpView {

    @StateStrategyType(AddToEndSingleStrategy.class)
    void setLayoutManager();

    @StateStrategyType(AddToEndSingleStrategy.class)
    void setLayoutManager(int count);

    @StateStrategyType(AddToEndSingleStrategy.class)
    void showDataBooks(ArrayList<BookPOJO> books);

    @StateStrategyType(AddToEndSingleStrategy.class)
    void showDataGenres(ArrayList<GenrePOJO> genres);

    @StateStrategyType(AddToEndSingleStrategy.class)
    void clearData();

    @StateStrategyType(AddToEndSingleStrategy.class)
    void showToast(int message);

    @StateStrategyType(AddToEndSingleStrategy.class)
    void showToast(String message);

    @StateStrategyType(AddToEndSingleStrategy.class)
    void showProgres(boolean b);

    @StateStrategyType(AddToEndSingleStrategy.class)
    void showProgresTop(boolean b);

    @StateStrategyType(SkipStrategy.class)
    void returnResult(String url, String name, int modelId, String tag);

    @StateStrategyType(AddToEndSingleStrategy.class)
    void showSeriesAndAutors(SearcheblPOJO searcheblPOJO);

    @StateStrategyType(SkipStrategy.class)
    void startBookActivity(@NonNull BookPOJO bookPOJO);

    @StateStrategyType(AddToEndSingleStrategy.class)
    void setNotFoundVisibile(boolean b);

}
