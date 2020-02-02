package com.fanok.audiobooks.interface_pacatge.parental_control;

import com.arellomobile.mvp.MvpView;
import com.arellomobile.mvp.viewstate.strategy.AddToEndSingleStrategy;
import com.arellomobile.mvp.viewstate.strategy.StateStrategyType;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

public interface View extends MvpView {

    @StateStrategyType(AddToEndSingleStrategy.class)
    void showProgress(boolean b);

    @StateStrategyType(AddToEndSingleStrategy.class)
    void showData(@NotNull ArrayList<String> arrayList);

    @StateStrategyType(AddToEndSingleStrategy.class)
    void showToast(int id);
}
