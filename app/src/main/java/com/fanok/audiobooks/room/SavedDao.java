package com.fanok.audiobooks.room;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import java.util.List;

@Dao
public interface SavedDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(SavedEntity entity);

    @Delete
    void delete(SavedEntity entity);

    @Query("DELETE FROM saved WHERE url_book = :url")
    void deleteByUrl(String url);

    @Query("DELETE FROM saved")
    void deleteAll();

    @Query("SELECT * FROM saved")
    List<SavedEntity> getAll();

    @Query("SELECT * FROM saved ORDER BY id DESC LIMIT 1")
    SavedEntity getLast();

    @Query("SELECT * FROM saved WHERE url_book = :url")
    SavedEntity getByUrl(String url);

    @Query("SELECT COUNT(id) FROM saved WHERE url_book = :url")
    int count(String url);

    @Query("SELECT genre FROM saved WHERE genre <> '' GROUP BY genre ORDER BY genre ASC")
    List<String> getGenres();

    @Query("SELECT author FROM saved WHERE author <> '' GROUP BY author ORDER BY author ASC")
    List<String> getAuthors();

    @Query("SELECT artist FROM saved WHERE artist <> '' GROUP BY artist ORDER BY artist ASC")
    List<String> getArtists();

    @Query("SELECT series FROM saved WHERE series <> '' GROUP BY series ORDER BY series ASC")
    List<String> getSeries();
}
