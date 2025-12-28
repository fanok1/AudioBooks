package com.fanok.audiobooks.room;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "audio", indices = {@Index(value = {"url_book"}, unique = true)})
public class AudioEntity {
    @PrimaryKey(autoGenerate = true)
    public int id;

    @NonNull
    @ColumnInfo(name = "url_book")
    public String urlBook = "";

    @NonNull
    @ColumnInfo(name = "name")
    public String name = "";

    @ColumnInfo(name = "time", defaultValue = "0")
    public int time;

    @ColumnInfo(name = "updated_at", defaultValue = "0")
    public long updatedAt;

    @ColumnInfo(name = "need_sync", defaultValue = "0")
    public boolean needSync;

    @ColumnInfo(name = "deleted", defaultValue = "0")
    public boolean deleted;
}
