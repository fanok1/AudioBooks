package com.fanok.audiobooks.interface_pacatge.sleep_timer;


import android.view.View;

public interface SleepTimerPresenter {

    void numberClick(View view);

    void endChapterClick(View view);

    void setEndCapterTime(long time);

    void clear(View view);

    void start();

    void finish();
}
