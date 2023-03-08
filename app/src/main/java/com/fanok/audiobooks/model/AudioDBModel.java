package com.fanok.audiobooks.model;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import androidx.annotation.NonNull;

import com.fanok.audiobooks.interface_pacatge.books.AudioDBHelperInterfase;
import com.fanok.audiobooks.interface_pacatge.books.BooksDBAbstract;
import com.fanok.audiobooks.pojo.TimeStartPOJO;

import java.util.ArrayList;

public class AudioDBModel extends BooksDBAbstract implements AudioDBHelperInterfase {
    private static final String TAG = "AudioDBModel";


    public AudioDBModel(Context context) {
        super(context);
    }


    private ContentValues getContentValues(@NonNull String bookUrl, @NonNull String name) {
        ContentValues contentValues = new ContentValues();
        contentValues.put("url_book", bookUrl);
        contentValues.put("name", name);
        contentValues.put("time", 0);
        return contentValues;
    }


    @Override
    public boolean isset(@NonNull String url) {
        String builder = "select id from " + "audio" + " where "
                + "url_book = '" + url + "'";
        SQLiteDatabase db = getDBHelper().getWritableDatabase();
        Cursor cursor = db.rawQuery(builder, null);
        int i = cursor.getCount();
        cursor.close();
        db.close();
        return i > 0;
    }

    @Override
    public void add(@NonNull String urlBook, @NonNull String name) {
        ContentValues values = getContentValues(urlBook, name);
        SQLiteDatabase db = getDBHelper().getWritableDatabase();
        long i = db.insert("audio", null, values);
        db.close();
    }

    @Override
    public void add(@NonNull TimeStartPOJO timeStartPOJO) {
        ContentValues values = new ContentValues();
        values.put("url_book", timeStartPOJO.getUrl());
        values.put("name", timeStartPOJO.getName());
        values.put("time", timeStartPOJO.getTime());
        SQLiteDatabase db = getDBHelper().getWritableDatabase();
        long i = db.insert("audio", null, values);
        db.close();
    }

    @Override
    public void remove(@NonNull String urlBook) {
        SQLiteDatabase db = getDBHelper().getWritableDatabase();
        long i = db.delete("audio", "url_book = ?", new String[]{urlBook});
        db.close();
    }

    @Override
    public void clearAll() {
        SQLiteDatabase db = getDBHelper().getWritableDatabase();
        db.delete("audio", null, null);
        db.close();
    }

    @Override
    public String getName(@NonNull String url) {

        SQLiteDatabase db = getDBHelper().getReadableDatabase();

        Cursor cursor = db.query("audio", new String[]{"name"}, "url_book" + "=?",
                new String[]{url}, null, null, null, null);

        String result = "";
        if (cursor != null) {
            if (cursor.getCount() > 0) {
                cursor.moveToFirst();
                result = cursor.getString(0);
            }
            cursor.close();

        }
        db.close();
        return result;
    }

    @Override
    public int getTime(@NonNull String url) {

        SQLiteDatabase db = getDBHelper().getReadableDatabase();

        Cursor cursor = db.query("audio", new String[]{"time"}, "url_book" + "=?",
                new String[]{url}, null, null, null, null);

        int result = 0;
        if (cursor != null) {
            if (cursor.getCount() != 0) {
                cursor.moveToFirst();
                result = cursor.getInt(0);
            }
            cursor.close();
        }
        db.close();
        return result;
    }

    @Override
    public int setTime(@NonNull String urlBook, int time) {
        if (time < 0 || urlBook.isEmpty()) throw new IllegalArgumentException();
        SQLiteDatabase db = getDBHelper().getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("time", time);

        return db.update("audio", values, "url_book" + " = ?",
                new String[]{urlBook});
    }

    @Override
    public ArrayList<TimeStartPOJO> getAll() {
        ArrayList<TimeStartPOJO> list = new ArrayList<>();
        String selectQuery = "SELECT  * FROM audio";

        SQLiteDatabase db = getDBHelper().getWritableDatabase();
        Cursor cursor = db.rawQuery(selectQuery, null);
        if (cursor.moveToLast()) {
            do {
                TimeStartPOJO audio = new TimeStartPOJO();
                audio.setUrl(cursor.getString(1));
                audio.setName(cursor.getString(2));
                audio.setTime(cursor.getInt(3));
                list.add(audio);
            } while (cursor.moveToPrevious());
        }
        cursor.close();
        db.close();
        return list;
    }


}