package com.fanok.audiobooks.interface_pacatge.favorite;

import android.content.Context;
import android.view.View;

public interface FavoritePresenter {

    void onCreate(Context context, int table);

    void loadBooks();

    void onDestroy();

    void onBookItemClick(View view, int position);

    void onBookItemLongClick(View view, int position);

    void onSearch(String qery);

    void cealrData();




}
