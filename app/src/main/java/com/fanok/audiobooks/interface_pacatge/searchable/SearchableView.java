package com.fanok.audiobooks.interface_pacatge.searchable;

import android.support.annotation.NonNull;

import com.arellomobile.mvp.MvpView;
import com.arellomobile.mvp.viewstate.strategy.SkipStrategy;
import com.arellomobile.mvp.viewstate.strategy.StateStrategyType;
import com.fanok.audiobooks.pojo.BookPOJO;
import com.fanok.audiobooks.pojo.SearcheblPOJO;

import java.util.ArrayList;

public interface SearchableView extends MvpView {

    void setLayoutManager();

    void setLayoutManager(int count);

    void showData(ArrayList arrayList);

    void clearData();

    void showToast(int message);

    void showToast(String message);

    void showProgres(boolean b);

    void showProgresTop(boolean b);

    void returnResult(String url, String name, int modelId, String tag);

    void showSeriesAndAutors(SearcheblPOJO searcheblPOJO);

    @StateStrategyType(SkipStrategy.class)
    void startBookActivity(@NonNull BookPOJO bookPOJO);

}
