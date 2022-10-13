package com.fanok.audiobooks.interface_pacatge.books;

import com.fanok.audiobooks.pojo.BookPOJO;
import io.reactivex.Observable;
import java.util.ArrayList;

public interface BooksModel {
    Observable<ArrayList<BookPOJO>> getBooks(String url, int page);

    Observable<ArrayList<BookPOJO>> getBooks(String url, int page, boolean speedUp);
}
