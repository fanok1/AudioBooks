package com.fanok.audiobooks.interface_pacatge.books;

import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;

import androidx.annotation.NonNull;

import com.fanok.audiobooks.search.ListingFilters;

public interface BooksPresenter {

    void onDestroy();

    void loadBoks();

    void onRefresh();

    void onOptionItemSelected(int itemId);

    void onGenreItemClick(View view, int position);

    void onBookItemClick(View view, int position);

    void onBookItemLongClick(View view, int position, LayoutInflater layoutInflater);

    void onActivityResult(@NonNull Intent intent);

    /** The duration filter's chip or menu item: opens the range dialog. */
    void onDurationChipClick();

    void onDurationSelected(@NonNull ListingFilters.Duration duration);

    void onPeriodSelected(@NonNull ListingFilters.Period period);

    /** "Search further": fetch the next pages of a filtered listing that hid most of what it fetched so far. */
    void onFilterMore();

}
