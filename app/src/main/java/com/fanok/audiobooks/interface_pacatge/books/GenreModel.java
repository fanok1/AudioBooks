package com.fanok.audiobooks.interface_pacatge.books;

import com.fanok.audiobooks.pojo.GenrePOJO;

import java.util.ArrayList;

import io.reactivex.Observable;

public interface GenreModel {
    Observable<ArrayList<GenrePOJO>> getBooks(String url, int page);
}
