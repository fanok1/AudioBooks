package com.fanok.audiobooks.interface_pacatge.books;

import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;

import androidx.annotation.NonNull;

public interface BooksPresenter {

    void onDestroy();

    void loadBoks();

    void onRefresh();

    void onOptionItemSelected(int itemId);

    void onGenreItemClick(View view, int position);

    void onBookItemClick(View view, int position);

    void onBookItemLongClick(View view, int position, LayoutInflater layoutInflater);

    void onActivityResult(@NonNull Intent intent);

}
