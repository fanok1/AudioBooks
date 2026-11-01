package com.fanok.audiobooks.interface_pacatge.sleep_timer;

import android.content.Intent;
import androidx.annotation.NonNull;
import com.arellomobile.mvp.MvpView;
import com.arellomobile.mvp.viewstate.strategy.AddToEndSingleStrategy;
import com.arellomobile.mvp.viewstate.strategy.SkipStrategy;
import com.arellomobile.mvp.viewstate.strategy.StateStrategyType;

public interface SleepTimerView extends MvpView {

    /** The inputs; {@code mode} is {@code SleepTimerPresenter.MODE_COUNTDOWN} or {@code MODE_CLOCK}. */
    @StateStrategyType(AddToEndSingleStrategy.class)
    void showSetup(int mode, int hours, int minutes, int clockHour, int clockMinute, boolean warn, boolean shake);

    /**
     * The running timer; {@code remainingMs} below 0 while the service has not told it yet, {@code endAt} the
     * epoch ms of the stop (0 for end-of-chapter), {@code mode} a {@code SleepTimerPresenter.MODE_*} or -1.
     */
    @StateStrategyType(AddToEndSingleStrategy.class)
    void showRunning(long remainingMs, long endAt, boolean paused, int mode);

    @StateStrategyType(AddToEndSingleStrategy.class)
    void setStartEnabled(boolean enabled);

    @StateStrategyType(SkipStrategy.class)
    void brodcastSend(Intent intent);

    @StateStrategyType(SkipStrategy.class)
    void showToast(@NonNull String text);

}
