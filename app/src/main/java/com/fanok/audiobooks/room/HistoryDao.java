package com.fanok.audiobooks.room;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import java.util.List;

@Dao
public interface HistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(HistoryEntity entity);

    @Delete
    void delete(HistoryEntity entity);

    @Query("DELETE FROM history WHERE url_book = :url")
    void deleteByUrl(String url);

    @Query("DELETE FROM history")
    void deleteAll();

    @Query("SELECT * FROM history")
    List<HistoryEntity> getAll();

    @Query("SELECT * FROM history ORDER BY id DESC LIMIT 1")
    HistoryEntity getLast();

    @Query("SELECT * FROM history WHERE url_book = :url")
    HistoryEntity getByUrl(String url);

    @Query("SELECT COUNT(id) FROM history WHERE url_book = :url")
    int count(String url);

    @Query("SELECT genre FROM history WHERE genre <> '' GROUP BY genre ORDER BY genre ASC")
    List<String> getGenres();

    @Query("SELECT author FROM history WHERE author <> '' GROUP BY author ORDER BY author ASC")
    List<String> getAuthors();

    @Query("SELECT artist FROM history WHERE artist <> '' GROUP BY artist ORDER BY artist ASC")
    List<String> getArtists();

    @Query("SELECT series FROM history WHERE series <> '' GROUP BY series ORDER BY series ASC")
    List<String> getSeries();
}
