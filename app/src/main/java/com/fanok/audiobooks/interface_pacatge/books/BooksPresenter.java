package com.fanok.audiobooks.interface_pacatge.books;

import android.content.Context;
import android.content.Intent;
import android.support.annotation.NonNull;
import android.view.View;

public interface BooksPresenter {

    void onCreate(@NonNull String url, int modelID, String subTitle, Context context);

    void onDestroy();

    void loadBoks();

    void onRefresh();

    void onOptionItemSelected(int itemId);

    void onGenreItemClick(View view, int position);

    void onBookItemClick(View view, int position);

    void onBookItemLongClick(View view, int position);

    void onActivityResult(@NonNull Intent intent);

}
