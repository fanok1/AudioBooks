package com.fanok.audiobooks.room;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;
import java.util.List;

@Dao
public interface AudioDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(AudioEntity entity);

    @Query("UPDATE audio SET deleted = 1, need_sync = 1, updated_at = :updatedAt WHERE url_book = :urlBook")
    void deleteByUrl(String urlBook, long updatedAt);

    @Query("UPDATE audio SET deleted = 1, need_sync = 1, updated_at = :updatedAt")
    void deleteAll(long updatedAt);

    @Query("SELECT COUNT(id) FROM audio WHERE url_book = :urlBook AND deleted = 0")
    int count(String urlBook);

    @Query("SELECT name FROM audio WHERE url_book = :urlBook AND deleted = 0")
    String getName(String urlBook);

    @Query("SELECT time FROM audio WHERE url_book = :urlBook AND deleted = 0")
    int getTime(String urlBook);

    @Query("UPDATE audio SET time = :time, updated_at = :updatedAt, need_sync = :needSync WHERE url_book = :urlBook")
    int setTime(String urlBook, int time, long updatedAt, boolean needSync);

    @Query("SELECT * FROM audio WHERE deleted = 0")
    List<AudioEntity> getAll();

    // --- Sync Methods ---

    @Query("SELECT * FROM audio WHERE url_book = :urlBook")
    AudioEntity getRawByUrl(String urlBook);

    @Query("SELECT * FROM audio WHERE need_sync = 1")
    List<AudioEntity> getEntitiesToSync();

    @Query("UPDATE audio SET need_sync = 0 WHERE url_book = :urlBook AND updated_at = :updatedAt")
    void markAsSynced(String urlBook, long updatedAt);

    @Query("UPDATE audio SET need_sync = 1 WHERE deleted = 0")
    void markAllAsNeedSync();

    // --- Logout / Physical Clear ---
    // Do not delete audio progress for books that are downloaded (present in saved table)
    @Query("DELETE FROM audio WHERE url_book NOT IN (SELECT url_book FROM saved)")
    void clearTablePhysical();
}
