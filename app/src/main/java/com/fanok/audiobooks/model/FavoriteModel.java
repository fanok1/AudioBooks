package com.fanok.audiobooks.model;

import android.content.Context;
import com.fanok.audiobooks.Consts;
import com.fanok.audiobooks.pojo.BookPOJO;
import io.reactivex.Observable;
import java.util.ArrayList;

public class FavoriteModel implements
        com.fanok.audiobooks.interface_pacatge.favorite.FavoriteModel {

    private final BooksDBModel mBooksDBModel;

    public FavoriteModel(Context context) {
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
                        break;
                    case Consts.TABLE_HISTORY:
                        books = mBooksDBModel.getAllHistory();
                        break;
                    case Consts.TABLE_SAVED:
                        books = mBooksDBModel.getAllSaved();
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

    public void closeDB() {
        mBooksDBModel.closeDB();
    }
}
