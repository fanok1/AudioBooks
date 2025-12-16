package com.fanok.audiobooks.interface_pacatge.clearSeved;

import androidx.annotation.NonNull;

import com.arellomobile.mvp.MvpView;
import com.arellomobile.mvp.viewstate.strategy.AddToEndSingleStrategy;
import com.arellomobile.mvp.viewstate.strategy.SkipStrategy;
import com.arellomobile.mvp.viewstate.strategy.StateStrategyType;
import com.fanok.audiobooks.pojo.DownloadItem;

import java.util.ArrayList;

public interface ClearSavedView extends MvpView {

    @StateStrategyType(AddToEndSingleStrategy.class)
    void showData(@NonNull ArrayList<DownloadItem> downloads);

    @StateStrategyType(AddToEndSingleStrategy.class)
    void showProgress(boolean isProgress);

    @StateStrategyType(SkipStrategy.class)
    void updateAdapter();

    @StateStrategyType(SkipStrategy.class)
    void showToast(int message);

}
