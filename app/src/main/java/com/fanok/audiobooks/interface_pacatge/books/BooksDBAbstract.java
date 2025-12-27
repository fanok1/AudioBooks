package com.fanok.audiobooks.interface_pacatge.books;

import android.content.Context;
import com.fanok.audiobooks.room.AppDatabase;

public abstract class BooksDBAbstract {
    private final AppDatabase mDatabase;

    public BooksDBAbstract(Context context) {
        mDatabase = AppDatabase.getDatabase(context);
    }

    protected AppDatabase getDatabase() {
        return mDatabase;
    }

    public void closeDB() {
        // AppDatabase is a singleton and should generally remain open
    }
}
