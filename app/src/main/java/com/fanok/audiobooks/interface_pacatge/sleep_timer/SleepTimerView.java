package com.fanok.audiobooks.interface_pacatge.sleep_timer;

import android.content.Intent;
import com.arellomobile.mvp.MvpView;
import com.arellomobile.mvp.viewstate.strategy.AddToEndSingleStrategy;
import com.arellomobile.mvp.viewstate.strategy.SkipStrategy;
import com.arellomobile.mvp.viewstate.strategy.StateStrategyType;
import java.util.ArrayList;

public interface SleepTimerView extends MvpView {


    @StateStrategyType(AddToEndSingleStrategy.class)
    void updateTime(ArrayList<Character> time);

    @StateStrategyType(AddToEndSingleStrategy.class)
    void setSrcToStartButton(boolean started);

    @StateStrategyType(SkipStrategy.class)
    void broadcastStartTimer(Intent intent);

    @StateStrategyType(SkipStrategy.class)
    void brodcastSend(Intent intent);

    @StateStrategyType(SkipStrategy.class)
    void showToast(int id);

}
