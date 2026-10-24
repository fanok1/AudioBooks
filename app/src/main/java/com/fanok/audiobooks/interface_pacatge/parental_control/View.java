package com.fanok.audiobooks.interface_pacatge.parental_control;

import androidx.annotation.NonNull;
import com.arellomobile.mvp.MvpView;
import com.arellomobile.mvp.viewstate.strategy.AddToEndSingleStrategy;
import com.arellomobile.mvp.viewstate.strategy.SkipStrategy;
import com.arellomobile.mvp.viewstate.strategy.StateStrategyType;
import com.fanok.audiobooks.pojo.ParentControlPOJO;
import java.util.ArrayList;
import org.jetbrains.annotations.NotNull;

public interface View extends MvpView {

    @StateStrategyType(AddToEndSingleStrategy.class)
    void showProgress(boolean b);

    @StateStrategyType(AddToEndSingleStrategy.class)
    void showData(@NotNull ArrayList<ParentControlPOJO> arrayList);

    @StateStrategyType(SkipStrategy.class)
    void showToast(int id);

    @StateStrategyType(SkipStrategy.class)
    void showToast(@NonNull String text);
}
