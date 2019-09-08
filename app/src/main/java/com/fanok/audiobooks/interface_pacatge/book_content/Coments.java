package com.fanok.audiobooks.interface_pacatge.book_content;

import com.arellomobile.mvp.MvpView;
import com.fanok.audiobooks.pojo.ComentsPOJO;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

public interface Coments extends MvpView {

    void showProgress(boolean b);

    void showComents(ArrayList<ComentsPOJO> data);

    void setPlaceholder(int id);

    void setPlaceholder(@NotNull String text);
}
