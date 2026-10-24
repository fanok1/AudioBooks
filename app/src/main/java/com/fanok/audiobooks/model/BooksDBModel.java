package com.fanok.audiobooks.model;

import android.content.Context;
import android.util.Log;
import androidx.annotation.NonNull;
import com.fanok.audiobooks.Consts;
import com.fanok.audiobooks.interface_pacatge.books.BooksDBAbstract;
import com.fanok.audiobooks.interface_pacatge.books.BooksDBHelperInterfase;
import com.fanok.audiobooks.pojo.BookPOJO;
import com.fanok.audiobooks.room.BookBaseEntity;
import com.fanok.audiobooks.room.FavoriteEntity;
import com.fanok.audiobooks.room.HistoryEntity;
import com.fanok.audiobooks.room.SavedEntity;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.jetbrains.annotations.NotNull;

public class BooksDBModel extends BooksDBAbstract implements BooksDBHelperInterfase {
    private static final String TAG = "BookDBModel";

    private int mSkippedRows;


    public BooksDBModel(Context context) {
        super(context);
    }

    @Override
    public boolean inFavorite(@NonNull BookPOJO book) {
        return getDatabase().favoriteDao().count(book.getUrl()) > 0;
    }

    @Override
    public boolean inHistory(@NonNull BookPOJO book) {
        return getDatabase().historyDao().count(book.getUrl()) > 0;
    }

    @Override
    public boolean inSaved(@NonNull final BookPOJO book) {
        return getDatabase().savedDao().count(book.getUrl()) > 0;
    }

    @Override
    public boolean inFavorite(@NonNull String url) {
        return getDatabase().favoriteDao().count(url) > 0;
    }

    @Override
    public boolean inSaved(final String url) {
        return getDatabase().savedDao().count(url) > 0;
    }

    /** Adds or refreshes a favorite; an existing one keeps its id and therefore its place in the list. */
    @Override
    public void addFavorite(BookPOJO book) {
        FavoriteEntity entity = new FavoriteEntity();
        entity.fromPojo(book);
        FavoriteEntity existing = getDatabase().favoriteDao().getByUrl(entity.urlBook);
        if (existing != null) {
            entity.id = existing.id;
        }
        getDatabase().favoriteDao().insert(entity);
    }

    /** Adds all books in one transaction: either every one is stored or none is. */
    public void addFavorites(@NonNull List<BookPOJO> books) {
        getDatabase().runInTransaction(() -> {
            for (BookPOJO book : books) {
                addFavorite(book);
            }
        });
    }

    @Override
    public void removeFavorite(BookPOJO book) {
        getDatabase().favoriteDao().deleteByUrl(book.getUrl());
        getDatabase().audioDao().deleteByUrlIfUnreferenced(book.getUrl());
    }

    @Override
    public void clearFavorite() {
        getDatabase().favoriteDao().deleteAll();
        getDatabase().audioDao().deleteUnreferenced();
    }

    /** Moves the book to the top of the history: REPLACE drops the old row of the same URL in the same statement. */
    @Override
    public void addHistory(BookPOJO book) {
        HistoryEntity entity = new HistoryEntity();
        entity.fromPojo(book);
        getDatabase().historyDao().insert(entity);
    }

    @Override
    public void removeHistory(BookPOJO book) {
        getDatabase().historyDao().deleteByUrl(book.getUrl());
        getDatabase().audioDao().deleteByUrlIfUnreferenced(book.getUrl());
    }

    @Override
    public void clearHistory() {
        getDatabase().historyDao().deleteAll();
        getDatabase().audioDao().deleteUnreferenced();
    }

    @Override
    public void addSaved(final BookPOJO book) {
        if (!inSaved(book)) {
            SavedEntity entity = new SavedEntity();
            entity.fromPojo(book);
            getDatabase().savedDao().insert(entity);
        }
    }

    @Override
    public void removeSaved(final BookPOJO book) {
        if (book != null) {
            removeSavedByUrl(book.getUrl());
        }
    }

    public void removeSavedByUrl(final String url) {
        if (url != null) {
            getDatabase().savedDao().deleteByUrl(url);
            getDatabase().audioDao().deleteByUrlIfUnreferenced(url);
        }
    }

    @Override
    public void removeSavedById(final BookPOJO book) {
        removeSaved(book);
    }

    @Override
    public ArrayList<BookPOJO> getAllFavorite() {
        return toPojos(getDatabase().favoriteDao().getAll());
    }

    @Override
    public ArrayList<BookPOJO> getAllHistory() {
        return toPojos(getDatabase().historyDao().getAll());
    }

    @Override
    public ArrayList<BookPOJO> getAllSaved() {
        return toPojos(getDatabase().savedDao().getAll());
    }

    /** Rows the getAll* methods could not convert and left out since this model was created. */
    public int getSkippedRows() {
        return mSkippedRows;
    }

    /** Newest first; a row that cannot be converted (e.g. an empty name) is skipped and counted. */
    private ArrayList<BookPOJO> toPojos(List<? extends BookBaseEntity> entities) {
        ArrayList<BookPOJO> list = new ArrayList<>();
        for (BookBaseEntity entity : entities) {
            try {
                BookPOJO pojo = entity.toPojo();
                if (pojo.getUrl() == null || pojo.getUrl().trim().isEmpty()) {
                    throw new IllegalStateException("empty url");
                }
                list.add(pojo);
            } catch (RuntimeException e) {
                mSkippedRows++;
                Log.w(TAG, "Skipping unreadable book row " + entity.urlBook, e);
            }
        }
        Collections.reverse(list);
        return list;
    }

    @Override
    public BookPOJO getHistory() {
        HistoryEntity entity = getDatabase().historyDao().getLast();
        return entity != null ? entity.toPojo() : null;
    }

    @Override
    public BookPOJO getSaved(@NonNull String url) {
        SavedEntity entity = getDatabase().savedDao().getByUrl(url);
        return entity != null ? entity.toPojo() : null;
    }

    @Override
    public BookPOJO getSomewhere(@NonNull String url) {
        BookPOJO bookPOJO = getSaved(url);
        if (bookPOJO!=null && bookPOJO.getUrl()!=null) {
            return bookPOJO;
        }
        FavoriteEntity favoriteEntity = getDatabase().favoriteDao().getByUrl(url);
        if (favoriteEntity != null) {
            bookPOJO = favoriteEntity.toPojo();
        }
        if (bookPOJO!=null && bookPOJO.getUrl()!=null) {
            return bookPOJO;
        }
        HistoryEntity historyEntity = getDatabase().historyDao().getByUrl(url);
        return historyEntity != null ? historyEntity.toPojo() : null;
    }

    @Override
    public ArrayList<String> getGenre(int table) {
        List<String> list;
        if(table == Consts.TABLE_FAVORITE){
            list = getDatabase().favoriteDao().getGenres();
        }else if (table == Consts.TABLE_HISTORY){
            list = getDatabase().historyDao().getGenres();
        }else if (table == Consts.TABLE_SAVED){
            list = getDatabase().savedDao().getGenres();
        }else {
            throw new IllegalArgumentException("Incorect table id");
        }
        return new ArrayList<>(list);
    }


    @Override
    public ArrayList<String> getAutors(int table) {
        List<String> list;
        if(table == Consts.TABLE_FAVORITE){
            list = getDatabase().favoriteDao().getAuthors();
        }else if (table == Consts.TABLE_HISTORY){
            list = getDatabase().historyDao().getAuthors();
        }else if (table == Consts.TABLE_SAVED){
            list = getDatabase().savedDao().getAuthors();
        }else {
            throw new IllegalArgumentException("Incorect table id");
        }
        return new ArrayList<>(list);
    }

    @Override
    public ArrayList<String> getArtists(int table) {
        List<String> list;
        if(table == Consts.TABLE_FAVORITE){
            list = getDatabase().favoriteDao().getArtists();
        }else if (table == Consts.TABLE_HISTORY){
            list = getDatabase().historyDao().getArtists();
        }else if (table == Consts.TABLE_SAVED){
            list = getDatabase().savedDao().getArtists();
        }else {
            throw new IllegalArgumentException("Incorect table id");
        }
        return new ArrayList<>(list);
    }

    @Override
    public ArrayList<String> getSeries(int table) {
        List<String> list;
        if(table == Consts.TABLE_FAVORITE){
            list = getDatabase().favoriteDao().getSeries();
        }else if (table == Consts.TABLE_HISTORY){
            list = getDatabase().historyDao().getSeries();
        }else if (table == Consts.TABLE_SAVED){
            list = getDatabase().savedDao().getSeries();
        }else {
            throw new IllegalArgumentException("Incorect table id");
        }
        return new ArrayList<>(list);
    }
}
