package com.fanok.audiobooks.interface_pacatge.book_content;

import com.arellomobile.mvp.MvpView;
import com.arellomobile.mvp.viewstate.strategy.SkipStrategy;
import com.arellomobile.mvp.viewstate.strategy.StateStrategyType;

public interface Activity extends MvpView {
    void setTabPostion(int postion);

    void refreshActivity();

    @StateStrategyType(SkipStrategy.class)
    void shareTextUrl();

    @StateStrategyType(SkipStrategy.class)
    void addToMainScreen();
}
