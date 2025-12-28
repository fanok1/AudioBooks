package com.fanok.audiobooks.room;

import androidx.room.Entity;
import androidx.room.Index;

@Entity(tableName = "history", indices = {@Index(value = {"url_book"}, unique = true)})
public class HistoryEntity extends SyncableBookBaseEntity {
}
