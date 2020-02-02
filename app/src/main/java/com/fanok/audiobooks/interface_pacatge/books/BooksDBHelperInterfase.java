package com.fanok.audiobooks.interface_pacatge.books;

import com.fanok.audiobooks.pojo.BookPOJO;

import java.util.ArrayList;

public interface BooksDBHelperInterfase {
    boolean inFavorite(BookPOJO book);

    boolean inHistory(BookPOJO book);

    boolean inFavorite(String url);

    boolean inHistory(String url);

    void addFavorite(BookPOJO book);

    void removeFavorite(BookPOJO book);

    void clearFavorite();

    void addHistory(BookPOJO book);

    void removeHistory(BookPOJO book);

    void clearHistory();

    int getHistoryCount();

    int getFavoriteCount();

    ArrayList<BookPOJO> getAllFavorite();

    ArrayList<BookPOJO> getAllHistory();

    BookPOJO getHistory();

    BookPOJO getFavorite();

    ArrayList<String> getGenre();

    ArrayList<String> getAutors();

    ArrayList<String> getArtists();

    ArrayList<String> getSeries();




}