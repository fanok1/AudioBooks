package com.fanok.audiobooks.room;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import java.util.List;

@Dao
public interface FavoriteDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(FavoriteEntity entity);

    @Delete
    void delete(FavoriteEntity entity);

    @Query("DELETE FROM favorite WHERE url_book = :url")
    void deleteByUrl(String url);

    @Query("DELETE FROM favorite")
    void deleteAll();

    @Query("SELECT * FROM favorite")
    List<FavoriteEntity> getAll();

    @Query("SELECT * FROM favorite ORDER BY id DESC LIMIT 1")
    FavoriteEntity getLast();

    @Query("SELECT * FROM favorite WHERE url_book = :url")
    FavoriteEntity getByUrl(String url);

    @Query("SELECT COUNT(id) FROM favorite WHERE url_book = :url")
    int count(String url);

    @Query("SELECT genre FROM favorite WHERE genre <> '' GROUP BY genre ORDER BY genre ASC")
    List<String> getGenres();

    @Query("SELECT author FROM favorite WHERE author <> '' GROUP BY author ORDER BY author ASC")
    List<String> getAuthors();

    @Query("SELECT artist FROM favorite WHERE artist <> '' GROUP BY artist ORDER BY artist ASC")
    List<String> getArtists();

    @Query("SELECT series FROM favorite WHERE series <> '' GROUP BY series ORDER BY series ASC")
    List<String> getSeries();
}
