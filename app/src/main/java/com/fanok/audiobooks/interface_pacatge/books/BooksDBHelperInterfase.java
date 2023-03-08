package com.fanok.audiobooks.interface_pacatge.books;

import androidx.annotation.NonNull;
import com.fanok.audiobooks.pojo.BookPOJO;

import java.util.ArrayList;

public interface BooksDBHelperInterfase {
    boolean inFavorite(BookPOJO book);

    boolean inHistory(BookPOJO book);

    boolean inSaved(BookPOJO book);

    boolean inFavorite(String url);

    boolean inHistory(String url);

    boolean inSaved(String url);

    void addFavorite(BookPOJO book);

    void removeFavorite(BookPOJO book);

    void clearFavorite();

    void addHistory(BookPOJO book);

    void removeHistory(BookPOJO book);

    void clearHistory();

    void addSaved(BookPOJO book);

    void removeSaved(BookPOJO book);

    void clearSaved();

    int getHistoryCount();

    int getFavoriteCount();

    int getSavedCount();

    ArrayList<BookPOJO> getAllFavorite();

    ArrayList<BookPOJO> getAllHistory();

    ArrayList<BookPOJO> getAllSaved();

    BookPOJO getHistory();

    BookPOJO getSaved(@NonNull String url);

    ArrayList<String> getGenre(int table);

    ArrayList<String> getAutors(int table);

    ArrayList<String> getArtists(int table);

    ArrayList<String> getSeries(int table);




}