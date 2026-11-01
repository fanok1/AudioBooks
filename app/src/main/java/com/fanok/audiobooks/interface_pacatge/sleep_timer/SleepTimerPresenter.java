package com.fanok.audiobooks.interface_pacatge.sleep_timer;


public interface SleepTimerPresenter {

    void setMode(int mode);

    void setCountdown(int hours, int minutes);

    void setClock(int hour, int minute);

    void setWarn(boolean warn);

    void setShake(boolean shake);

    void start();

    void endOfChapter();

    void extend();

    void cancel();

    /** The service's state reply ({@code SleepTimerActivity.bradcast_UpdateTimer}). */
    void onTimerUpdate(long remainingMs, long endAt, boolean paused, int mode);

    /** The service's {@code SleepTimerActivity.bradcast_FinishTimer}: fired or cancelled. */
    void finish();
}
