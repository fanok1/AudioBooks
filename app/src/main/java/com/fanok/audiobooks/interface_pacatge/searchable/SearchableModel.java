package com.fanok.audiobooks.interface_pacatge.searchable;

import android.support.annotation.NonNull;

import com.fanok.audiobooks.pojo.GenrePOJO;

import java.io.IOException;
import java.util.ArrayList;

import io.reactivex.Observable;

public interface SearchableModel {
    Observable<ArrayList<GenrePOJO>> getBooks(String url, @NonNull String qery) throws IOException;

    ArrayList<GenrePOJO> loadBooksList(@NonNull String url, @NonNull String qery)
            throws IOException;

    void setCookies();

    void setSikretKey();
}
