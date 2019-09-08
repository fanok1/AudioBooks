package com.fanok.audiobooks.interface_pacatge.main;

import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.arellomobile.mvp.MvpView;
import com.arellomobile.mvp.viewstate.strategy.SkipStrategy;
import com.arellomobile.mvp.viewstate.strategy.StateStrategyType;

public interface MainView extends MvpView {

    @StateStrategyType(SkipStrategy.class)
    void openActivity(@NonNull Intent intent);

    @StateStrategyType(SkipStrategy.class)
    void showFragment(@NonNull Fragment fragment, String tag);

}
