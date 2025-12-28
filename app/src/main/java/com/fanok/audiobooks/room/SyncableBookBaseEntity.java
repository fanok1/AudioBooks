package com.fanok.audiobooks.room;

import androidx.room.ColumnInfo;

public class SyncableBookBaseEntity extends BookBaseEntity {
    @ColumnInfo(name = "updated_at", defaultValue = "0")
    public long updatedAt;

    @ColumnInfo(name = "need_sync", defaultValue = "0")
    public boolean needSync;

    @ColumnInfo(name = "deleted", defaultValue = "0")
    public boolean deleted;
}
