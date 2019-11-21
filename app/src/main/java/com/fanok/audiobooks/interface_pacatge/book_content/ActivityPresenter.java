package com.fanok.audiobooks.interface_pacatge.book_content;

import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.NonNull;

public interface ActivityPresenter {

    void onStop();

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

    void buttonSpeedClick(View view);

    void setImageDrawable(int id);

    void updateTime(int timeCurrent, int timeEnd);

    void setSelected(int pos, @NonNull String name);

    void showTitle(String title);

    void stateCollapsed();

    void stateExpanded();

    void stateElse();


}
