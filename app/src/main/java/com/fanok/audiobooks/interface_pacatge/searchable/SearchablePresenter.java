package com.fanok.audiobooks.interface_pacatge.searchable;

import android.view.LayoutInflater;
import android.view.View;

public interface SearchablePresenter {

    void onDestroy();

    void loadBoks();

    void loadNext();

    void onGenreItemClick(View view, int position);

    void onBookItemClick(View view, int position);

    void onBookItemLongClick(View view, int position, LayoutInflater layoutInflater);

    void onAutorsListItemClick(View view, int position);

    void onSeriesListItemClick(View view, int position);

}
