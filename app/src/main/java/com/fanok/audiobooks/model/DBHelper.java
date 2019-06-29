package com.fanok.audiobooks.model;

import static com.fanok.audiobooks.Consts.DBName;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.support.annotation.NonNull;

public class DBHelper extends SQLiteOpenHelper {

    public DBHelper(@NonNull Context context) {
        super(context, DBName, null, 1);
    }

    @Override
    public void onCreate(SQLiteDatabase sqLiteDatabase) {
        sqLiteDatabase.execSQL("create table favorite ("
                + "id integer primary key autoincrement,"
                + "name text not null,"
                + "url_book text not null UNIQUE,"
                + "photo text,"
                + "genre text,"
                + "url_genre text,"
                + "author text,"
                + "url_author text,"
                + "artist text,"
                + "url_artist text,"
                + "series text,"
                + "url_series text,"
                + "time text,"
                + "reting text,"
                + "favorite integer,"
                + "coments integer" + ");");

        sqLiteDatabase.execSQL("create table history ("
                + "id integer primary key autoincrement,"
                + "name text not null,"
                + "url_book text not null UNIQUE,"
                + "photo text,"
                + "genre text,"
                + "url_genre text,"
                + "author text,"
                + "url_author text,"
                + "artist text,"
                + "url_artist text,"
                + "series text,"
                + "url_series text,"
                + "time text,"
                + "reting text,"
                + "favorite integer,"
                + "coments integer" + ");");
    }

    @Override
    public void onUpgrade(SQLiteDatabase sqLiteDatabase, int i, int i1) {
        sqLiteDatabase.execSQL("DROP TABLE IF EXISTS favorite");
        sqLiteDatabase.execSQL("DROP TABLE IF EXISTS history");
        onCreate(sqLiteDatabase);
    }
}
