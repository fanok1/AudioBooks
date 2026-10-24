package com.fanok.audiobooks.room;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "books_audio", indices = {@Index("url_book")})
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

    /** Exact URI (content:// or file://) of the exported copy this app wrote; null when not exported. */
    @ColumnInfo(name = "local_uri")
    public String localUri;

    /** Position of the chapter in the book's list as last parsed; -1 for rows written before this column existed. */
    @ColumnInfo(name = "ordinal", defaultValue = "-1")
    public int ordinal = -1;
}
