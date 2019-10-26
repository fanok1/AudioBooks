package com.fanok.audiobooks.interface_pacatge.main;

import android.view.MenuItem;

import androidx.annotation.NonNull;

public interface MainPresenter {
    void onItemSelected(@NonNull MenuItem item);

    void onDestroy();

    void startFragment(int fragment, String url);

    void startFragment(int fragment);
}
