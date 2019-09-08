package com.fanok.audiobooks.interface_pacatge.books;


import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.arellomobile.mvp.MvpView;
import com.arellomobile.mvp.viewstate.strategy.SkipStrategy;
import com.arellomobile.mvp.viewstate.strategy.StateStrategyType;
import com.fanok.audiobooks.pojo.BookPOJO;

import java.util.ArrayList;

public interface BooksView extends MvpView {
    void setLayoutManager();

    void setLayoutManager(int count);

    void showData(ArrayList bookPOJOS);

    void clearData();

    void showProgres(boolean b);

    void showToast(int message);

    void showToast(String message);

    void showRefreshing(boolean b);

    void setPosition(int position);

    @StateStrategyType(SkipStrategy.class)
    void showFragment(Fragment fragment, String tag);

    @StateStrategyType(SkipStrategy.class)
    void showSearchActivity(int modelId);

    @StateStrategyType(SkipStrategy.class)
    void showBooksActivity(@NonNull BookPOJO bookPOJO);

}
