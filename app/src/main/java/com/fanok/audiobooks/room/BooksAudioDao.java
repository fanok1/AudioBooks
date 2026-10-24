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

    @Query("DELETE FROM books_audio WHERE url_book = :url AND url_audio = :audioUrl")
    void deleteTrack(String url, String audioUrl);

    @Query("DELETE FROM books_audio WHERE id = :id")
    void deleteById(int id);

    @Query("DELETE FROM books_audio")
    void deleteAll();

    @Query("SELECT * FROM books_audio WHERE url_book = :url ORDER BY ordinal, id")
    List<BooksAudioEntity> getByBookUrl(String url);

    @Query("SELECT * FROM books_audio WHERE url_book = :bookUrl AND url_audio = :audioUrl ORDER BY ordinal, id LIMIT 1")
    BooksAudioEntity getByUrlAndAudioUrl(String bookUrl, String audioUrl);

    @Query("UPDATE books_audio SET local_uri = :localUri WHERE url_book = :bookUrl AND url_audio = :audioUrl")
    int updateLocalUri(String bookUrl, String audioUrl, String localUri);

    @Query("UPDATE books_audio SET local_uri = :localUri WHERE id = :id")
    int updateLocalUriById(int id, String localUri);

    @Query("SELECT * FROM books_audio")
    List<BooksAudioEntity> getAll();
}
