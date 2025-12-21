package com.fanok.audiobooks.interface_pacatge.books;

import android.content.Context;

import com.fanok.audiobooks.pojo.BookPOJO;
import io.reactivex.Observable;
import java.util.ArrayList;

public interface BooksModel {
    Observable<ArrayList<BookPOJO>> getBooks(String url, int page, String nameModel, Context context);

    Observable<ArrayList<BookPOJO>> getBooks(String url, int page, boolean speedUp);
}
