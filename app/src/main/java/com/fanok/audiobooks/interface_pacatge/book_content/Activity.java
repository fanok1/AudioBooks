package com.fanok.audiobooks.interface_pacatge.book_content;


import android.content.Intent;
import android.content.ServiceConnection;

import androidx.annotation.NonNull;

import com.arellomobile.mvp.MvpView;
import com.arellomobile.mvp.viewstate.strategy.SingleStateStrategy;
import com.arellomobile.mvp.viewstate.strategy.SkipStrategy;
import com.arellomobile.mvp.viewstate.strategy.StateStrategyType;
import com.fanok.audiobooks.pojo.AudioPOJO;
import com.fanok.audiobooks.pojo.BookPOJO;

import java.util.ArrayList;

public interface Activity extends MvpView {
    @StateStrategyType(SingleStateStrategy.class)
    void setTabPostion(String title);

    @StateStrategyType(SkipStrategy.class)
    void refreshActivity();

    @StateStrategyType(SkipStrategy.class)
    void shareTextUrl();

    @StateStrategyType(SkipStrategy.class)
    void addToMainScreen(BookPOJO bookPOJO);

    void showProgres(boolean b);

    void showData(ArrayList<AudioPOJO> data);

    void showTitle(@NonNull String name);

    void updateTime(int timeCurent, int timeEnd);

    void setTimeEnd(int timeEnd);

    void setImageDrawable(int id);

    void setSelected(int id, String name);

    @StateStrategyType(SkipStrategy.class)
    void broadcastSend(@NonNull Intent intent);

    @StateStrategyType(SkipStrategy.class)
    void activityStart(@NonNull Intent intent);

    @StateStrategyType(SkipStrategy.class)
    void myUnbindService(@NonNull ServiceConnection serviceConnection);

    void stateCollapsed();

    void stateExpanded();

    void stateElse();

    @StateStrategyType(SkipStrategy.class)
    void setIsFavorite(boolean b);
}
