package com.fanok.audiobooks.interface_pacatge.books;

import android.support.annotation.NonNull;

public interface BooksPresenter {

    void onCreate(@NonNull String url);

    void onDestroy();

    void loadBoks();

    void onRefresh();

    void onChageOrintationScreen();
}
