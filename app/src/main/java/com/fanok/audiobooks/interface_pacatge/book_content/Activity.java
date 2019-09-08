package com.fanok.audiobooks.interface_pacatge.book_content;


import androidx.annotation.NonNull;

import com.arellomobile.mvp.MvpView;
import com.arellomobile.mvp.viewstate.strategy.SkipStrategy;
import com.arellomobile.mvp.viewstate.strategy.StateStrategyType;
import com.fanok.audiobooks.pojo.AudioPOJO;

import java.util.ArrayList;

public interface Activity extends MvpView {
    void setTabPostion(String title);

    void refreshActivity();

    @StateStrategyType(SkipStrategy.class)
    void shareTextUrl();

    @StateStrategyType(SkipStrategy.class)
    void addToMainScreen();

    void showProgres(boolean b);

    void showData(ArrayList<AudioPOJO> data);

    void showTitle(@NonNull String name);

    void updateTime(int timeCurent, int timeEnd);

    void setTimeEnd(int timeEnd);

    void setImageDrawable(int id);

    void setSelected(int id, String name);
}
