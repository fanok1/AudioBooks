package com.fanok.audiobooks.interface_pacatge.books;

import com.fanok.audiobooks.pojo.BookPOJO;

import java.util.ArrayList;

public interface BooksDBHelperInterfase {
    boolean inFavorite(BookPOJO book);

    void addFavorite(BookPOJO book);

    void removeFavorite(BookPOJO book);

    void addHistory(BookPOJO book);

    void removeHistory(BookPOJO book);

    ArrayList<BookPOJO> getAllFavorite();

    ArrayList<BookPOJO> getAllHistory();


}
