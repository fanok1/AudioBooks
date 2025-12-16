package com.fanok.audiobooks.model;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import androidx.annotation.NonNull;
import com.fanok.audiobooks.interface_pacatge.books.AudioListDBHelperInterfase;
import com.fanok.audiobooks.interface_pacatge.books.BooksDBAbstract;
import com.fanok.audiobooks.pojo.AudioListPOJO;
import java.util.ArrayList;

public class AudioListDBModel extends BooksDBAbstract implements AudioListDBHelperInterfase {

    public AudioListDBModel(Context context) {
        super(context);
    }


    private ContentValues getContentValues(@NonNull AudioListPOJO pojo) {
        ContentValues contentValues = new ContentValues();
        contentValues.put("url_book", pojo.getBookUrl());
        contentValues.put("books_name", pojo.getBookName());
        contentValues.put("name_audio", pojo.getAudioName());
        contentValues.put("url_audio", pojo.getAudioUrl());
        contentValues.put("time", pojo.getTime());
        contentValues.put("time_start", pojo.getTimeStart());
        contentValues.put("time_end", pojo.getTimeEnd());
        return contentValues;
    }

    @Override
    public boolean isset(@NonNull String url) {
        String builder = "select id from " + "books_audio" + " where "
                + "url_book = '" + url + "'";
        SQLiteDatabase db = getDBHelper().getWritableDatabase();
        Cursor cursor = db.rawQuery(builder, null);
        int i = cursor.getCount();
        cursor.close();
        db.close();
        return i > 0;
    }

    @Override
    public void add(@NonNull AudioListPOJO audioListPOJO) {
        ContentValues values = new ContentValues();
        values.put("url_book", audioListPOJO.getBookUrl());
        values.put("books_name", audioListPOJO.getBookName());
        values.put("name_audio", audioListPOJO.getAudioName());
        values.put("url_audio", audioListPOJO.getAudioUrl());
        values.put("time", audioListPOJO.getTime());
        values.put("time_start", audioListPOJO.getTimeStart());
        values.put("time_end", audioListPOJO.getTimeEnd());
        SQLiteDatabase db = getDBHelper().getWritableDatabase();
        long i = db.insert("books_audio", null, values);
        db.close();
    }

    @Override
    public void remove(@NonNull String urlBook) {
        SQLiteDatabase db = getDBHelper().getWritableDatabase();
        long i = db.delete("books_audio", "url_book = ?", new String[]{urlBook});
        db.close();
    }

    @Override
    public void clearAll() {
        SQLiteDatabase db = getDBHelper().getWritableDatabase();
        db.delete("books_audio", null, null);
        db.close();
    }

    @Override
    public ArrayList<AudioListPOJO> get(@NonNull String url) {
        ArrayList<AudioListPOJO> list = new ArrayList<>();
        String selectQuery = "SELECT  * FROM books_audio WHERE url_book = ?";

        SQLiteDatabase db = getDBHelper().getWritableDatabase();
        Cursor cursor = db.rawQuery(selectQuery, new String[]{url});
        if (cursor.moveToFirst()) {
            do {
                AudioListPOJO audio = new AudioListPOJO();
                audio.setBookUrl(cursor.getString(1));
                audio.setBookName(cursor.getString(2));
                audio.setAudioName(cursor.getString(3));
                audio.setAudioUrl(cursor.getString(4));
                audio.setTime(cursor.getInt(5));
                audio.setTimeStart(cursor.getInt(6));
                audio.setTimeEnd(cursor.getInt(7));
                list.add(audio);
            } while (cursor.moveToNext());
        }
        cursor.close();
        db.close();
        return list;
    }

    @Override
    public AudioListPOJO get(@NonNull String url, @NonNull String audioUrl) {
        ArrayList<AudioListPOJO> list = new ArrayList<>();
        String selectQuery = "SELECT  * FROM books_audio WHERE url_book = ? And url_audio LIKE ?";

        SQLiteDatabase db = getDBHelper().getWritableDatabase();
        Cursor cursor = db.rawQuery(selectQuery, new String[]{url, audioUrl});
        AudioListPOJO audio = new AudioListPOJO();
        if (cursor.moveToFirst()) {
            do {
                audio.setBookUrl(cursor.getString(1));
                audio.setBookName(cursor.getString(2));
                audio.setAudioName(cursor.getString(3));
                audio.setAudioUrl(cursor.getString(4));
                audio.setTime(cursor.getInt(5));
                audio.setTimeStart(cursor.getInt(6));
                audio.setTimeEnd(cursor.getInt(7));
            } while (cursor.moveToNext());
        }
        cursor.close();
        db.close();
        return audio;
    }

    @Override
    public ArrayList<AudioListPOJO> getAll() {
        ArrayList<AudioListPOJO> list = new ArrayList<>();
        String selectQuery = "SELECT * FROM books_audio";

        SQLiteDatabase db = getDBHelper().getWritableDatabase();
        Cursor cursor = db.rawQuery(selectQuery, null);
        if (cursor.moveToFirst()) {
            do {
                AudioListPOJO audio = new AudioListPOJO();
                audio.setBookUrl(cursor.getString(1));
                audio.setBookName(cursor.getString(2));
                audio.setAudioName(cursor.getString(3));
                audio.setAudioUrl(cursor.getString(4));
                audio.setTime(cursor.getInt(5));
                audio.setTimeStart(cursor.getInt(6));
                audio.setTimeEnd(cursor.getInt(7));
                list.add(audio);
            } while (cursor.moveToNext());
        }
        cursor.close();
        db.close();
        return list;
    }
}
