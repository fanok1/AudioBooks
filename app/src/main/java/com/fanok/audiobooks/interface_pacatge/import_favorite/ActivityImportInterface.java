package com.fanok.audiobooks.interface_pacatge.import_favorite;

import com.arellomobile.mvp.MvpView;
import com.arellomobile.mvp.viewstate.strategy.SkipStrategy;
import com.arellomobile.mvp.viewstate.strategy.StateStrategyType;

public interface ActivityImportInterface extends MvpView {

    @StateStrategyType(SkipStrategy.class)
    void showToast(int id);

    @StateStrategyType(SkipStrategy.class)
    void showToast(String message);

    @StateStrategyType(SkipStrategy.class)
    void close();

    void showProgress(boolean b);

}
