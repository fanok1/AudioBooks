package com.fanok.audiobooks.interface_pacatge.books;

import android.content.Context;

import com.fanok.audiobooks.model.DBHelper;

public abstract class BooksDBAbstract implements BooksDBHelperInterfase {
    private DBHelper mDBHelper;

    public BooksDBAbstract(Context context) {
        setDBHelper(new DBHelper(context));
    }

    protected DBHelper getDBHelper() {
        return mDBHelper;
    }

    private void setDBHelper(DBHelper DBHelper) {
        mDBHelper = DBHelper;
    }

}
