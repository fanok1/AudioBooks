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

    @Query("SELECT count(id) FROM books_audio WHERE url_book = :url AND deleted = 0")
    int count(String url);

    @Query("UPDATE books_audio SET deleted = 1, need_sync = 1, updated_at = :updatedAt WHERE url_book = :url")
    void deleteByUrl(String url, long updatedAt);

    @Query("UPDATE books_audio SET deleted = 1, need_sync = 1, updated_at = :updatedAt")
    void deleteAll(long updatedAt);

    @Query("SELECT * FROM books_audio WHERE url_book = :url AND deleted = 0")
    List<BooksAudioEntity> getByBookUrl(String url);

    @Query("SELECT * FROM books_audio WHERE url_book = :bookUrl AND url_audio LIKE :audioUrl AND deleted = 0 LIMIT 1")
    BooksAudioEntity getByUrlAndAudioUrl(String bookUrl, String audioUrl);

    @Query("SELECT * FROM books_audio WHERE deleted = 0")
    List<BooksAudioEntity> getAll();

    // --- Sync Methods ---
    @Query("SELECT * FROM books_audio WHERE url_book = :urlBook AND url_audio = :audioUrl")
    BooksAudioEntity getRawByUrlAndAudioUrl(String urlBook, String audioUrl);

    @Query("SELECT * FROM books_audio WHERE need_sync = 1")
    List<BooksAudioEntity> getEntitiesToSync();

    @Query("UPDATE books_audio SET need_sync = 0 WHERE url_book = :urlBook AND url_audio = :audioUrl AND updated_at = :updatedAt")
    void markAsSynced(String urlBook, String audioUrl, long updatedAt);

    @Query("UPDATE books_audio SET need_sync = 1 WHERE deleted = 0")
    void markAllAsNeedSync();

    // --- Logout / Physical Clear ---
    // Do not delete audio list for books that are downloaded (present in saved table)
    @Query("DELETE FROM books_audio WHERE url_book NOT IN (SELECT url_book FROM saved)")
    void clearTablePhysical();
}
