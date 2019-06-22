package com.fanok.audiobooks.interface_pacatge.books;

import com.arellomobile.mvp.MvpView;
import com.fanok.audiobooks.pojo.BookPOJO;

import java.util.ArrayList;

public interface BooksView extends MvpView {
    void setLayoutManager();

    void setLayoutManager(int count);

    void showData(ArrayList<BookPOJO> bookPOJOS);

    void clearData();

    void showProgres(boolean b);

    void showToast(int message);

    void showToast(String message);

    void showRefreshing(boolean b);
}
