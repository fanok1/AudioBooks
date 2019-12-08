package com.fanok.audiobooks.interface_pacatge.parental_control;

import com.arellomobile.mvp.MvpView;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

public interface View extends MvpView {

    void showProgress(boolean b);

    void showData(@NotNull ArrayList<String> arrayList);

    void showToast(int id);
}
