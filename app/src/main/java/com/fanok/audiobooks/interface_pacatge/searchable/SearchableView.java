package com.fanok.audiobooks.interface_pacatge.searchable;

import com.arellomobile.mvp.MvpView;

import java.util.ArrayList;

public interface SearchableView extends MvpView {

    void setLayoutManager();

    void setLayoutManager(int count);

    void showData(ArrayList arrayList);

    void clearData();

    void showToast(int message);

    void showToast(String message);

    void showProgres(boolean b);

    void returnResult(String url, String name, int modelId, String tag);

}
