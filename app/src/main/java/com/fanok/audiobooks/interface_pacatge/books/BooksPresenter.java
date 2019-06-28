package com.fanok.audiobooks.interface_pacatge.books;

import android.support.annotation.NonNull;
import android.view.View;

public interface BooksPresenter {

    void onCreate(@NonNull String url, int modelID);

    void onDestroy();

    void loadBoks();

    void onRefresh();

    void onChageOrintationScreen(String url);

    void onOptionItemSelected(int itemId);

    void onGenreItemClick(View view, int position);

}
