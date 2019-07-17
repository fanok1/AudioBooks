package com.fanok.audiobooks.interface_pacatge.book_content;

import android.content.Context;
import android.support.annotation.NonNull;
import android.view.Menu;
import android.view.MenuItem;

import com.fanok.audiobooks.pojo.BookPOJO;

public interface ActivityPresenter {
    void onCreate(@NonNull BookPOJO bookPOJO, @NonNull Context context);

    void onDestroy();

    void onCreateOptionsMenu(Menu menu);

    void onOptionsMenuItemSelected(MenuItem item);
}
