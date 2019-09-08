package com.fanok.audiobooks.interface_pacatge.book_content;

import androidx.annotation.NonNull;

import com.fanok.audiobooks.pojo.SeriesPOJO;

import java.util.ArrayList;

import io.reactivex.Observable;

public interface SeriesModel {
    Observable<ArrayList<SeriesPOJO>> getSeries(@NonNull String url);
}
