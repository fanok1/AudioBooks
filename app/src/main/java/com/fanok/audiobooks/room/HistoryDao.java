package com.fanok.audiobooks.room;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import java.util.List;

@Dao
public interface HistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(HistoryEntity entity);

    @Query("UPDATE history SET deleted = 1, need_sync = 1, updated_at = :updatedAt WHERE id = :id")
    void deleteById(int id, long updatedAt);

    @Query("UPDATE history SET deleted = 1, need_sync = 1, updated_at = :updatedAt WHERE url_book = :url")
    void deleteByUrl(String url, long updatedAt);

    @Query("UPDATE history SET deleted = 1, need_sync = 1, updated_at = :updatedAt")
    void deleteAll(long updatedAt);

    @Query("SELECT * FROM history WHERE deleted = 0")
    List<HistoryEntity> getAll();

    @Query("SELECT * FROM history WHERE deleted = 0 ORDER BY id DESC LIMIT 1")
    HistoryEntity getLast();

    @Query("SELECT * FROM history WHERE url_book = :url AND deleted = 0")
    HistoryEntity getByUrl(String url);

    @Query("SELECT COUNT(id) FROM history WHERE url_book = :url AND deleted = 0")
    int count(String url);

    @Query("SELECT genre FROM history WHERE genre <> '' AND deleted = 0 GROUP BY genre ORDER BY genre ASC")
    List<String> getGenres();

    @Query("SELECT author FROM history WHERE author <> '' AND deleted = 0 GROUP BY author ORDER BY author ASC")
    List<String> getAuthors();

    @Query("SELECT artist FROM history WHERE artist <> '' AND deleted = 0 GROUP BY artist ORDER BY artist ASC")
    List<String> getArtists();

    @Query("SELECT series FROM history WHERE series <> '' AND deleted = 0 GROUP BY series ORDER BY series ASC")
    List<String> getSeries();

    // --- Sync Methods ---
    @Query("SELECT * FROM history WHERE url_book = :url")
    HistoryEntity getRawByUrl(String url);

    @Query("SELECT * FROM history WHERE need_sync = 1")
    List<HistoryEntity> getEntitiesToSync();

    @Query("UPDATE history SET need_sync = 0 WHERE url_book = :urlBook AND updated_at = :updatedAt")
    void markAsSynced(String urlBook, long updatedAt);

    @Query("UPDATE history SET need_sync = 1 WHERE deleted = 0")
    void markAllAsNeedSync();

    // --- Logout / Physical Clear ---
    @Query("DELETE FROM history")
    void clearTablePhysical();
}
