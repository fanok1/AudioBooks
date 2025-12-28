package com.fanok.audiobooks.model;

import android.content.Context;
import androidx.annotation.NonNull;
import com.fanok.audiobooks.Consts;
import com.fanok.audiobooks.interface_pacatge.books.BooksDBAbstract;
import com.fanok.audiobooks.interface_pacatge.books.BooksDBHelperInterfase;
import com.fanok.audiobooks.pojo.BookPOJO;
import com.fanok.audiobooks.room.FavoriteEntity;
import com.fanok.audiobooks.room.HistoryEntity;
import com.fanok.audiobooks.room.SavedEntity;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.jetbrains.annotations.NotNull;

public class BooksDBModel extends BooksDBAbstract implements BooksDBHelperInterfase {
    private static final String TAG = "BookDBModel";


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

    @Override
    public void addFavorite(BookPOJO book) {
        FavoriteEntity entity = new FavoriteEntity();
        entity.fromPojo(book);
        getDatabase().favoriteDao().insert(entity);
    }

    @Override
    public void removeFavorite(BookPOJO book) {
        getDatabase().favoriteDao().deleteByUrl(book.getUrl());
    }

    @Override
    public void clearFavorite() {
        getDatabase().favoriteDao().deleteAll();
    }

    @Override
    public void addHistory(BookPOJO book) {
        if (inHistory(book)) removeHistory(book);
        HistoryEntity entity = new HistoryEntity();
        entity.fromPojo(book);
        getDatabase().historyDao().insert(entity);
    }

    @Override
    public void removeHistory(BookPOJO book) {
        getDatabase().historyDao().deleteByUrl(book.getUrl());
    }

    @Override
    public void clearHistory() {
        getDatabase().historyDao().deleteAll();
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
        getDatabase().savedDao().deleteByUrl(book.getUrl());
    }

    @Override
    public void removeSavedById(final BookPOJO book) {
        removeSaved(book);
    }

    @Override
    public ArrayList<BookPOJO> getAllFavorite() {
        List<FavoriteEntity> entities = getDatabase().favoriteDao().getAll();
        ArrayList<BookPOJO> list = new ArrayList<>();
        for (FavoriteEntity entity : entities) {
            list.add(entity.toPojo());
        }
        Collections.reverse(list);
        return list;
    }

    @Override
    public ArrayList<BookPOJO> getAllHistory() {
        List<HistoryEntity> entities = getDatabase().historyDao().getAll();
        ArrayList<BookPOJO> list = new ArrayList<>();
        for (HistoryEntity entity : entities) {
            list.add(entity.toPojo());
        }
        Collections.reverse(list);
        return list;
    }

    @Override
    public ArrayList<BookPOJO> getAllSaved() {
        List<SavedEntity> entities = getDatabase().savedDao().getAll();
        ArrayList<BookPOJO> list = new ArrayList<>();
        for (SavedEntity entity : entities) {
            list.add(entity.toPojo());
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
