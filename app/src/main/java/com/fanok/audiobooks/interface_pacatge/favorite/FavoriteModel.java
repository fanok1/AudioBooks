package com.fanok.audiobooks.interface_pacatge.favorite;

import androidx.annotation.NonNull;
import com.fanok.audiobooks.pojo.BookPOJO;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.reactivex.Observable;
import io.reactivex.Single;

public interface FavoriteModel {
    Observable<ArrayList<BookPOJO>> getBooks(int table);

    /** Book URL → NOT_SAVED / SAVED / SAVED_ALL, computed off the main thread. */
    Single<Map<String, Integer>> savedStates(@NonNull List<BookPOJO> books);
}
