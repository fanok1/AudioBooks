package com.fanok.audiobooks.interface_pacatge.main;

import android.content.Intent;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;

import com.arellomobile.mvp.MvpView;

public interface MainView extends MvpView {
    void openActivity(@NonNull Intent intent);

    void showFragment(@NonNull Fragment fragment, String tag);
}
