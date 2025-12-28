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

    @Query("DELETE FROM audio WHERE url_book = :urlBook")
    void deleteByUrl(String urlBook);

    @Query("DELETE FROM audio")
    void deleteAll();

    @Query("SELECT COUNT(id) FROM audio WHERE url_book = :urlBook")
    int count(String urlBook);

    @Query("SELECT name FROM audio WHERE url_book = :urlBook")
    String getName(String urlBook);

    @Query("SELECT time FROM audio WHERE url_book = :urlBook")
    int getTime(String urlBook);

    @Query("UPDATE audio SET time = :time WHERE url_book = :urlBook")
    int setTime(String urlBook, int time);

    @Query("SELECT * FROM audio")
    List<AudioEntity> getAll();
}
