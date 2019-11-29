package com.fanok.audiobooks.interface_pacatge.main;

import android.content.DialogInterface;

public interface MainPresenter {
    void onItemSelected(int id);

    void onDestroy();

    void startFragment(int fragment, String url);

    void startFragment(int fragment);

    void openSettingsOptimizeBattery(DialogInterface dialogInterface);
}
