package com.fanok.audiobooks.interface_pacatge.favorite;

import android.view.LayoutInflater;
import android.view.View;

import org.jetbrains.annotations.NotNull;

public interface FavoritePresenter {

    void loadBooks();

    void onDestroy();

    void onBookItemClick(View view, int position);

    void onBookItemLongClick(View view, int position, LayoutInflater layoutInflater);

    void onSearch(String qery);

    void cealrData();

    void onOptionsItemSelected(int id);

    void setView(@NotNull View view);




}
