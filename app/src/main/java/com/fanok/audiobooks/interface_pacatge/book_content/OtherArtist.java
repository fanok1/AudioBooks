package com.fanok.audiobooks.interface_pacatge.book_content;

import androidx.annotation.NonNull;

import com.arellomobile.mvp.MvpView;
import com.arellomobile.mvp.viewstate.strategy.SkipStrategy;
import com.arellomobile.mvp.viewstate.strategy.StateStrategyType;
import com.fanok.audiobooks.pojo.OtherArtistPOJO;

import java.util.ArrayList;

public interface OtherArtist extends MvpView {

    void showProgress(boolean b);

    void showSeries(ArrayList<OtherArtistPOJO> data);

    @StateStrategyType(SkipStrategy.class)
    void showBook(@NonNull String url);

    void showToast(int message);

    void showToast(String message);
}
