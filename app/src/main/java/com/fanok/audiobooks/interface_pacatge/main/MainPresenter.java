package com.fanok.audiobooks.interface_pacatge.main;

import android.content.DialogInterface;
import androidx.annotation.NonNull;

public interface MainPresenter {
    void onItemSelected(int id);

    void onDestroy();

    void startFragment(int fragmentID, String url);

    void startFragment(int fragmentID, boolean b);

    void openSettingsOptimizeBattery(@NonNull DialogInterface dialogInterface);

    void onLoginLogoutClicked();

    void onResume();
}
