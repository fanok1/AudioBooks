package com.fanok.audiobooks.interface_pacatge.sleep_timer;


import android.view.View;

public interface SleepTimerPresenter {
    void numberClick(View view);

    void clear(View view);

    void start();

    void finish();
}
