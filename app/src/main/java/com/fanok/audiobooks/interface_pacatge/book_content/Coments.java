package com.fanok.audiobooks.interface_pacatge.book_content;

import com.arellomobile.mvp.MvpView;
import com.fanok.audiobooks.pojo.ComentsPOJO;

import java.util.ArrayList;

public interface Coments extends MvpView {

    void showProgress(boolean b);

    void showComents(ArrayList<ComentsPOJO> data);

    void showToast(int message);

    void showToast(String message);
}
