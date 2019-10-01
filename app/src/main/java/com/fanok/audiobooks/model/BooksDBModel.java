package com.fanok.audiobooks.model;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import androidx.annotation.NonNull;

import com.fanok.audiobooks.interface_pacatge.books.BooksDBAbstract;
import com.fanok.audiobooks.interface_pacatge.books.BooksDBHelperInterfase;
import com.fanok.audiobooks.pojo.BookPOJO;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

public class BooksDBModel extends BooksDBAbstract implements BooksDBHelperInterfase {
    private static final String TAG = "BookDBModel";


    public BooksDBModel(Context context) {
        super(context);
    }

    @Override
    public boolean inFavorite(@NonNull BookPOJO book) {
        return booksInTable(book, "favorite");
    }

    @Override
    public boolean inHistory(@NonNull BookPOJO book) {
        return booksInTable(book, "history");
    }

    private boolean booksInTable(@NonNull BookPOJO book, @NonNull String table) {
        String builder = "select id from " + table + " where "
                + "url_book = '" + book.getUrl() + "'";
        SQLiteDatabase db = getDBHelper().getWritableDatabase();
        Cursor cursor = db.rawQuery(builder, null);
        int i = cursor.getCount();
        cursor.close();
        db.close();
        return i > 0;
    }

    @Override
    public void addFavorite(BookPOJO book) {
        add(book, "favorite");
    }

    @Override
    public void removeFavorite(BookPOJO book) {
        remove(book, "favorite");
    }

    @Override
    public void clearFavorite() {
        clearAll("favorite");
    }

    @Override
    public void addHistory(BookPOJO book) {
        if (inHistory(book)) removeHistory(book);
        add(book, "history");
    }

    @Override
    public void removeHistory(BookPOJO book) {
        remove(book, "history");
    }

    @Override
    public void clearHistory() {
        clearAll("history");
    }

    @Override
    public ArrayList<BookPOJO> getAllFavorite() {
        return getAll("favorite");
    }

    @Override
    public ArrayList<BookPOJO> getAllHistory() {
        return getAll("history");
    }

    @Override
    public ArrayList<String> getGenre() {
        return getStringRow("genre");
    }


    @Override
    public ArrayList<String> getAutors() {
        return getStringRow("author");
    }

    @Override
    public ArrayList<String> getArtists() {
        return getStringRow("artist");
    }

    @Override
    public ArrayList<String> getSeries() {
        return getStringRow("series");
    }

    private ArrayList<String> getStringRow(@NotNull String row) {
        ArrayList<String> list = new ArrayList<>();

        SQLiteDatabase db = getDBHelper().getWritableDatabase();
        Cursor cursor = db.rawQuery(
                "SELECT " + row + " FROM favorite WHERE " + row + "<>\"\" GROUP BY " + row
                        + " ORDER BY " + row + " ASC", null);

        if (cursor.moveToFirst()) {
            do {
                list.add(cursor.getString(0));
            } while (cursor.moveToNext());
        }

        cursor.close();
        db.close();
        return list;
    }

    private void add(BookPOJO book, String table) {
        ContentValues values = getContentValues(book);
        SQLiteDatabase db = getDBHelper().getWritableDatabase();
        long i = db.insert(table, null, values);
        db.close();
    }

    private void remove(BookPOJO book, String table) {
        SQLiteDatabase db = getDBHelper().getWritableDatabase();
        long i = db.delete(table, "url_book = ?", new String[]{String.valueOf(book.getUrl())});
        db.close();
    }

    private ArrayList<BookPOJO> getAll(String table) {
        ArrayList<BookPOJO> contactList = new ArrayList<>();
        String selectQuery = "SELECT  * FROM " + table;

        SQLiteDatabase db = getDBHelper().getWritableDatabase();
        Cursor cursor = db.rawQuery(selectQuery, null);
        if (cursor.moveToLast()) {
            do {
                BookPOJO book = new BookPOJO();
                book.setName(cursor.getString(1));
                book.setUrl(cursor.getString(2));
                book.setPhoto(cursor.getString(3));
                book.setGenre(cursor.getString(4));
                book.setUrlGenre(cursor.getString(5));
                book.setAutor(cursor.getString(6));
                book.setUrlAutor(cursor.getString(7));
                book.setArtist(cursor.getString(8));
                book.setUrlArtist(cursor.getString(9));
                book.setSeries(cursor.getString(10));
                book.setUrlSeries(cursor.getString(11));
                book.setTime(cursor.getString(12));
                book.setReting(cursor.getString(13));
                book.setComents(cursor.getString(14));
                contactList.add(book);
            } while (cursor.moveToPrevious());
        }
        cursor.close();
        db.close();
        return contactList;
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
        contentValues.put("reting", book.getReting());
        contentValues.put("coments", book.getComents());
        return contentValues;
    }

    private void clearAll(String table) {
        SQLiteDatabase db = getDBHelper().getWritableDatabase();
        db.delete(table, null, null);
        db.close();
    }


}
