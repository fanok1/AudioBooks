package com.fanok.audiobooks.interface_pacatge.book_content;

import com.arellomobile.mvp.MvpView;
import com.fanok.audiobooks.pojo.BookPOJO;
import com.fanok.audiobooks.pojo.DescriptionPOJO;

import java.util.ArrayList;

public interface Description extends MvpView {
    void showProgress(boolean b);

    void showOtherBooks(ArrayList<BookPOJO> data);

    void showDescription(DescriptionPOJO description);

    void showRefreshDialog();
}
