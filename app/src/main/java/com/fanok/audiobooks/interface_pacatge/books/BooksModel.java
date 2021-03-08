package com.fanok.audiobooks.interface_pacatge.books;

import android.content.SharedPreferences;
import com.fanok.audiobooks.pojo.BookPOJO;
import io.reactivex.Observable;
import java.util.ArrayList;

public interface BooksModel {
    Observable<ArrayList<BookPOJO>> getBooks(String url, int page);

    Observable<ArrayList<BookPOJO>> getBooks(ArrayList<String> urls, int page, SharedPreferences preferences);
}
