package com.fanok.audiobooks.interface_pacatge.searchable;

import android.content.Context;
import android.view.View;

public interface SearchablePresenter {

    void onCreate(int modelID, Context context);

    void onDestroy();

    void loadBoks(String qery);

    void loadNext(String qery);

    void onGenreItemClick(View view, int position);

    void onBookItemClick(View view, int position);

    void onBookItemLongClick(View view, int position);

    void onAutorsListItemClick(View view, int position);

    void onSeriesListItemClick(View view, int position);

}
