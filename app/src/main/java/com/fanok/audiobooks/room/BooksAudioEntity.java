package com.fanok.audiobooks.room;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "books_audio")
public class BooksAudioEntity {
    @PrimaryKey(autoGenerate = true)
    public int id;

    @NonNull
    @ColumnInfo(name = "url_book")
    public String urlBook = "";

    @NonNull
    @ColumnInfo(name = "books_name")
    public String booksName = "";

    @ColumnInfo(name = "name_audio")
    public String nameAudio;

    @ColumnInfo(name = "url_audio")
    public String urlAudio;

    @ColumnInfo(name = "time", defaultValue = "0")
    public int time;

    @ColumnInfo(name = "time_start", defaultValue = "-1")
    public int timeStart;

    @ColumnInfo(name = "time_end", defaultValue = "-1")
    public int timeEnd;

    @ColumnInfo(name = "updated_at", defaultValue = "0")
    public long updatedAt;

    @ColumnInfo(name = "need_sync", defaultValue = "0")
    public boolean needSync;
}
