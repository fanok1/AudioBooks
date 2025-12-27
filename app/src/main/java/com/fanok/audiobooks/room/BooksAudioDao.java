package com.fanok.audiobooks.room;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import java.util.List;

@Dao
public interface BooksAudioDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(BooksAudioEntity entity);

    @Query("SELECT count(id) FROM books_audio WHERE url_book = :url")
    int count(String url);

    @Query("DELETE FROM books_audio WHERE url_book = :url")
    void deleteByUrl(String url);

    @Query("DELETE FROM books_audio")
    void deleteAll();

    @Query("SELECT * FROM books_audio WHERE url_book = :url")
    List<BooksAudioEntity> getByBookUrl(String url);

    @Query("SELECT * FROM books_audio WHERE url_book = :bookUrl AND url_audio LIKE :audioUrl LIMIT 1")
    BooksAudioEntity getByUrlAndAudioUrl(String bookUrl, String audioUrl);

    @Query("SELECT * FROM books_audio")
    List<BooksAudioEntity> getAll();
}
