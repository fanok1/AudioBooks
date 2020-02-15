package com.fanok.audiobooks.interface_pacatge.books;

import com.fanok.audiobooks.pojo.BookPOJO;

import java.util.ArrayList;

import io.reactivex.Observable;

public interface BooksModel {
    Observable<ArrayList<BookPOJO>> getBooks(String url, int page);

    Observable<ArrayList<BookPOJO>> getBooks(ArrayList<String> urls, int page);
}
