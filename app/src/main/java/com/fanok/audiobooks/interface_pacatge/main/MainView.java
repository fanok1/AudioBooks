package com.fanok.audiobooks.interface_pacatge.main;

import android.content.Intent;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import com.arellomobile.mvp.MvpView;
import com.arellomobile.mvp.viewstate.strategy.SkipStrategy;
import com.arellomobile.mvp.viewstate.strategy.StateStrategyType;
import com.fanok.audiobooks.pojo.BookPOJO;

public interface MainView extends MvpView {

    @StateStrategyType(SkipStrategy.class)
    void openActivity(@NonNull Intent intent);

    @StateStrategyType(SkipStrategy.class)
    void showFragment(@NonNull Fragment fragment, String tag);

    @StateStrategyType(SkipStrategy.class)
    void showToast(int id);

    @StateStrategyType(SkipStrategy.class)
    void setBattaryOptimizeDisenbled(boolean b);

    @StateStrategyType(SkipStrategy.class)
    void showBooksActivity(BookPOJO bookPOJO);

    @StateStrategyType(SkipStrategy.class)
    void updateUserInfo(String name, String email, String photo);

    @StateStrategyType(SkipStrategy.class)
    void updateIconLoginLogout(int id);
}
