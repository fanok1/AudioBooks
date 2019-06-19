package com.fanok.audiobooks.interface_pacatge;

import android.os.Bundle;

import com.fanok.audiobooks.pojo.BookPOJO;

import java.util.ArrayList;

import io.reactivex.Observable;

public interface BooksContract {
    interface View {
        void setLayoutManager();

        void setLayoutManager(int count);

        Bundle getArg();

        void showData(ArrayList<BookPOJO> bookPOJOS);

        void clearData();

        void showProgres(boolean b);

        void showToast(int message);

        void showToast(String message);

        void showRefreshing(boolean b);

    }

    interface Presenter {
        void onCreate();

        void onCreateView();

        void onDestroy();

        void loadBoks();

        void onRefresh();
    }

    interface Model {
        Observable<ArrayList<BookPOJO>> getBooks(String url);
    }
}
