package com.fanok.audiobooks.interface_pacatge.main;

import android.support.annotation.NonNull;
import android.view.MenuItem;

public interface MainPresenter {
    void onItemSelected(@NonNull MenuItem item);

    void onDestroy();

    void onCreate();
}
