package com.fanok.audiobooks.interface_pacatge.books;

import com.fanok.audiobooks.pojo.BookPOJO;

public interface BooksDBHelperInterfase {
    boolean inFavorite(BookPOJO book);

    boolean addFavorite(BookPOJO book);

    boolean removeFavorite(BookPOJO book);

    boolean addHistory(BookPOJO book);

    boolean removeHistory(BookPOJO book);
}
