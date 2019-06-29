package com.fanok.audiobooks.model;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.fanok.audiobooks.interface_pacatge.books.BooksDBAbstract;
import com.fanok.audiobooks.pojo.BookPOJO;

public class BooksDBModel extends BooksDBAbstract {


    public BooksDBModel(Context context) {
        super(context);
    }

    @Override
    public boolean inFavorite(BookPOJO book) {
        String builder = "select id from favorite where "
                + "url_book = '" + book.getUrl() + "'";
        SQLiteDatabase db = getDBHelper().getWritableDatabase();
        Cursor cursor = db.rawQuery(builder, null);
        int i = cursor.getCount();
        cursor.close();
        db.close();
        return i > 0;
    }

    @Override
    public boolean addFavorite(BookPOJO book) {
        return add(book, "favorite");
    }

    @Override
    public boolean removeFavorite(BookPOJO book) {
        return remove(book, "favorite");
    }

    @Override
    public boolean addHistory(BookPOJO book) {
        return add(book, "history");
    }

    @Override
    public boolean removeHistory(BookPOJO book) {
        return remove(book, "history");
    }

    private boolean add(BookPOJO book, String table) {
        ContentValues values = getContentValues(book);
        SQLiteDatabase db = getDBHelper().getWritableDatabase();
        long i = db.insert(table, null, values);
        db.close();
        return i == 0;
    }

    private boolean remove(BookPOJO book, String table) {
        SQLiteDatabase db = getDBHelper().getWritableDatabase();
        long i = db.delete(table, "url_book = ?", new String[]{String.valueOf(book.getUrl())});
        db.close();
        return i == 0;
    }

    private ContentValues getContentValues(BookPOJO book) {
        ContentValues contentValues = new ContentValues();
        contentValues.put("name", book.getName());
        contentValues.put("url_book", book.getUrl());
        contentValues.put("photo", book.getPhoto());
        contentValues.put("genre", book.getGenre());
        contentValues.put("url_genre", book.getUrlGenre());
        contentValues.put("author", book.getAutor());
        contentValues.put("url_author", book.getUrlAutor());
        contentValues.put("artist", book.getArtist());
        contentValues.put("url_artist", book.getUrlArtist());
        contentValues.put("series", book.getSeries());
        contentValues.put("url_series", book.getUrlSeries());
        contentValues.put("time", book.getTime());
        contentValues.put("favorite", book.getFavorite());
        contentValues.put("coments", book.getComents());
        return contentValues;
    }
}
