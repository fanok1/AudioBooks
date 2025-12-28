package com.fanok.audiobooks.room;

import androidx.room.Entity;
import androidx.room.Index;

@Entity(tableName = "favorite", indices = {@Index(value = {"url_book"}, unique = true)})
public class FavoriteEntity extends SyncableBookBaseEntity {
}
