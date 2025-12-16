package com.fanok.audiobooks.interface_pacatge.favorite;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import org.jetbrains.annotations.NotNull;

public interface FavoritePresenter {

    void loadBooks();

    void onCreate(Context context);

    void onDestroy();

    void onBookItemClick(View view, int position);

    void onBookItemLongClick(View view, int position, LayoutInflater layoutInflater);

    void onRemove(int position);

    void onSearch(String qery);

    void onOptionsItemSelected(@NotNull View view, int id);




}
