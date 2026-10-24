package com.fanok.audiobooks.interface_pacatge.clearSeved;

import androidx.annotation.NonNull;

import com.arellomobile.mvp.MvpView;
import com.arellomobile.mvp.viewstate.strategy.AddToEndSingleStrategy;
import com.arellomobile.mvp.viewstate.strategy.SkipStrategy;
import com.arellomobile.mvp.viewstate.strategy.StateStrategyType;
import com.fanok.audiobooks.pojo.DownloadItem;

import java.util.List;

public interface ClearSavedView extends MvpView {

    @StateStrategyType(AddToEndSingleStrategy.class)
    void showData(@NonNull List<DownloadItem> downloads);

    @StateStrategyType(AddToEndSingleStrategy.class)
    void showProgress(boolean isProgress);

    @StateStrategyType(SkipStrategy.class)
    void updateAdapter();

    @StateStrategyType(SkipStrategy.class)
    void showToast(int message);

    @StateStrategyType(SkipStrategy.class)
    void showMessage(@NonNull String message);

    @StateStrategyType(SkipStrategy.class)
    void showLogsDialog(String logs);

}
