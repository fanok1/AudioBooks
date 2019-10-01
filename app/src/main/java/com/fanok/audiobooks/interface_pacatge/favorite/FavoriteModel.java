package com.fanok.audiobooks.interface_pacatge.favorite;

import com.fanok.audiobooks.pojo.BookPOJO;

import java.util.ArrayList;

import io.reactivex.Observable;

public interface FavoriteModel {
    Observable<ArrayList<BookPOJO>> getBooks(int table);
}
