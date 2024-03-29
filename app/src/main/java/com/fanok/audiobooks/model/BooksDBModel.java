package com.fanok.audiobooks.model;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import androidx.annotation.NonNull;
import com.fanok.audiobooks.Consts;
import com.fanok.audiobooks.interface_pacatge.books.BooksDBAbstract;
import com.fanok.audiobooks.interface_pacatge.books.BooksDBHelperInterfase;
import com.fanok.audiobooks.pojo.BookPOJO;
import java.util.ArrayList;
import org.jetbrains.annotations.NotNull;

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

    @Override
    public boolean inSaved(@NonNull final BookPOJO book) {
        return booksInTable(book, "saved");
    }

    @Override
    public boolean inFavorite(@NonNull String url) {
        return booksInTable(url, "favorite");
    }

    @Override
    public boolean inHistory(@NonNull String url) {
        return booksInTable(url, "history");
    }

    @Override
    public boolean inSaved(final String url) {
        return booksInTable(url, "saved");
    }

    private boolean booksInTable(@NonNull BookPOJO book, @NonNull String table) {
        return booksInTable(book.getUrl(), table);
    }

    private boolean booksInTable(@NonNull String url, @NonNull String table) {
        String builder = "select id from " + table + " where "
                + "url_book = '" + url + "'";
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
    public void addSaved(final BookPOJO book) {
        if (!inSaved(book)) {
            add(book, "saved");
        }
    }

    @Override
    public void removeSaved(final BookPOJO book) {
        remove(book, "saved");
    }

    @Override
    public void clearSaved() {
        clearAll("saved");
    }

    @Override
    public int getHistoryCount() {
        return getCount("history");
    }

    @Override
    public int getFavoriteCount() {
        return getCount("favorite");
    }

    @Override
    public int getSavedCount() {
        return getCount("saved");
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
    public ArrayList<BookPOJO> getAllSaved() {
        return getAll("saved");
    }

    @Override
    public BookPOJO getHistory() {
        return getLast("history");
    }

    @Override
    public BookPOJO getSaved(@NonNull String url) {
        return getByUrl(url, "saved");
    }

    private BookPOJO getByUrl(@NonNull final String url, @NonNull final String table) {
        SQLiteDatabase db = getDBHelper().getWritableDatabase();

        String selectQuery = "SELECT * FROM " + table + " WHERE "
                + "url_book = '" + url + "'";

        BookPOJO book = null;
        Cursor cursor = db.rawQuery(selectQuery, null);
        if (cursor.moveToLast()) {
            book = new BookPOJO();
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
            book.setDesc(cursor.getString(15));
        }
        cursor.close();
        db.close();
        return book;
    }

    @Override
    public ArrayList<String> getGenre(int table) {
        return getStringRow("genre", table);
    }


    @Override
    public ArrayList<String> getAutors(int table) {
        return getStringRow("author", table);
    }

    @Override
    public ArrayList<String> getArtists(int table) {
        return getStringRow("artist", table);
    }

    @Override
    public ArrayList<String> getSeries(int table) {
        return getStringRow("series", table);
    }

    private ArrayList<String> getStringRow(@NotNull String row, int table) {
        String tableName;
        if(table == Consts.TABLE_FAVORITE){
            tableName = "favorite";
        }else if (table == Consts.TABLE_HISTORY){
            tableName = "history";
        }else if (table == Consts.TABLE_SAVED){
            tableName = "saved";
        }else {
            throw new IllegalArgumentException("Incorect table id");
        }
        ArrayList<String> list = new ArrayList<>();

        SQLiteDatabase db = getDBHelper().getWritableDatabase();
        Cursor cursor = db.rawQuery(
                "SELECT " + row + " FROM "+tableName+" WHERE " + row + "<>\"\" GROUP BY " + row
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
                book.setDesc(cursor.getString(15));
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
        contentValues.put("description", book.getDesc());
        return contentValues;
    }

    private void clearAll(String table) {
        SQLiteDatabase db = getDBHelper().getWritableDatabase();
        db.delete(table, null, null);
        db.close();
    }

    private BookPOJO getLast(String table) {
        SQLiteDatabase db = getDBHelper().getWritableDatabase();

        String selectQuery = "SELECT * FROM " + table + " ORDER BY id DESC LIMIT 1";

        BookPOJO book = null;
        Cursor cursor = db.rawQuery(selectQuery, null);
        if (cursor.moveToLast()) {
            book = new BookPOJO();
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
            book.setDesc(cursor.getString(15));
        }
        cursor.close();
        db.close();
        return book;
    }

    private int getCount(String table) {
        String countQuery = "SELECT  * FROM " + table;
        SQLiteDatabase db = getDBHelper().getWritableDatabase();
        Cursor cursor = db.rawQuery(countQuery, null);

        int result = 0;
        if (cursor != null) {
            result = cursor.getCount();
            cursor.close();
        }
        db.close();
        return result;
    }


}
