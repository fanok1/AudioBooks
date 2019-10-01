package com.fanok.audiobooks.interface_pacatge.book_content;

import android.content.Context;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.NonNull;

import com.fanok.audiobooks.pojo.BookPOJO;

public interface ActivityPresenter {
    void onCreate(@NonNull BookPOJO bookPOJO, @NonNull Context context);

    void onDestroy();

    void onCreateOptionsMenu(Menu menu);

    void onOptionsMenuItemSelected(MenuItem item);

    void onItemSelected(View view, int position);

    void getAudio();

    void buttomPlayClick(View view);

    void buttomPreviousClick(View view);

    void buttomNextClick(View view);

    void buttomRewindClick(View view);

    void buttomForwardClick(View view);

    void seekChange(View view);

    void onOrintationChangeListner(BookPOJO bookPOJO);

    void buttonSpeedClick(View view);
}
