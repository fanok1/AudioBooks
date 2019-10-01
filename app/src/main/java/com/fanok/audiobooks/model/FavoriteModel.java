package com.fanok.audiobooks.model;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import androidx.preference.PreferenceManager;

import com.fanok.audiobooks.Consts;
import com.fanok.audiobooks.R;
import com.fanok.audiobooks.pojo.BookPOJO;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

import io.reactivex.Observable;

public class FavoriteModel implements
        com.fanok.audiobooks.interface_pacatge.favorite.FavoriteModel {

    private Context mContext;
    private BooksDBModel mBooksDBModel;

    public FavoriteModel(Context context) {
        mContext = context;
        mBooksDBModel = new BooksDBModel(context);
    }

    @Override
    public Observable<ArrayList<BookPOJO>> getBooks(int table) {
        return Observable.create(observableEmitter -> {
            try {
                ArrayList<BookPOJO> books;
                switch (table) {
                    case Consts.TABLE_FAVORITE:
                        books = mBooksDBModel.getAllFavorite();

                        SharedPreferences pref = PreferenceManager
                                .getDefaultSharedPreferences(mContext);

                        String sort = pref.getString("pref_sort_favorite",
                                getString(R.string.sort_value_date));
                        Comparator<BookPOJO> comparator = getComparator(sort);
                        if (!sort.equals(getString(R.string.sort_value_date))) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                                books.sort(comparator);
                            } else {
                                Collections.sort(books, comparator);
                            }
                        }

                        break;
                    case Consts.TABLE_HISTORY:
                        books = mBooksDBModel.getAllHistory();
                        break;
                    default:
                        books = new ArrayList<>();
                        break;
                }
                observableEmitter.onNext(books);
            } catch (Exception e) {
                observableEmitter.onError(e);
            } finally {
                observableEmitter.onComplete();
            }
        });
    }

    private String getString(int id) {
        return mContext.getString(id);
    }

    private Comparator<BookPOJO> getComparator(@NotNull String sort) {
        if (getString(R.string.sort_value_name).equals(sort)) {
            return (bookPOJO, t1) -> bookPOJO.getName().compareTo(t1.getName());
        } else if (getString(R.string.sort_value_genre).equals(sort)) {
            return (bookPOJO, t1) -> bookPOJO.getGenre().compareTo(t1.getGenre());
        } else if (getString(R.string.sort_value_autor).equals(sort)) {
            return (bookPOJO, t1) -> bookPOJO.getAutor().compareTo(t1.getAutor());
        } else if (getString(R.string.sort_value_artist).equals(sort)) {
            return (bookPOJO, t1) -> bookPOJO.getArtist().compareTo(t1.getArtist());
        } else if (getString(R.string.sort_value_series).equals(sort)) {
            return (bookPOJO, t1) -> bookPOJO.getSeries().compareTo(t1.getSeries());
        } else {
            return (bookPOJO, t1) -> 0;
        }
    }
}
