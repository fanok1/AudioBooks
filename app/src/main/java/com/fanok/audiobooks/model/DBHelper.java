package com.fanok.audiobooks.model;

import static com.fanok.audiobooks.Consts.DBName;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;

import androidx.annotation.NonNull;

public class DBHelper extends SQLiteOpenHelper {

    public DBHelper(@NonNull Context context) {
        super(context, DBName, null, 12);
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
                + "coments integer,"
                + "description text" + ");");

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
                + "coments integer,"
                + "description text" + ");");

        sqLiteDatabase.execSQL("create table audio ("
                + "id integer primary key autoincrement,"
                + "url_book text not null UNIQUE,"
                + "name text not null,"
                + "time integer DEFAULT 0" + ");");

        sqLiteDatabase.execSQL("create table books_audio ("
                + "id integer primary key autoincrement,"
                + "url_book text not null,"
                + "books_name text not null,"
                + "name_audio text,"
                + "url_audio text,"
                + "time integer DEFAULT 0" + ");");
    }

    @Override
    public void onUpgrade(SQLiteDatabase sqLiteDatabase, int i, int i1) {
        if (i == 8) {
            sqLiteDatabase.execSQL("ALTER TABLE history ADD description text");
            sqLiteDatabase.execSQL("ALTER TABLE favorite ADD description text");
            sqLiteDatabase.execSQL("create table books_audio ("
                    + "id integer primary key autoincrement,"
                    + "url_book text not null,"
                    + "books_name text not null,"
                    + "name_audio text,"
                    + "url_audio text,"
                    + "time integer DEFAULT 0" + ");");
        }

        if (i == 11) {
            try {
                sqLiteDatabase.execSQL("ALTER TABLE books_audio ADD time integer DEFAULT 0");
            } catch (SQLiteException ignored) {
            }
        }



    }
}
