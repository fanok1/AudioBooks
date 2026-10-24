package com.fanok.audiobooks.interface_pacatge.book_content;


import android.content.Intent;
import androidx.annotation.NonNull;
import com.arellomobile.mvp.MvpView;
import com.arellomobile.mvp.viewstate.strategy.AddToEndSingleStrategy;
import com.arellomobile.mvp.viewstate.strategy.OneExecutionStateStrategy;
import com.arellomobile.mvp.viewstate.strategy.SingleStateStrategy;
import com.arellomobile.mvp.viewstate.strategy.SkipStrategy;
import com.arellomobile.mvp.viewstate.strategy.StateStrategyType;
import com.fanok.audiobooks.pojo.AudioPOJO;
import com.fanok.audiobooks.pojo.BookPOJO;
import java.util.ArrayList;

public interface Activity extends MvpView {
    @StateStrategyType(SingleStateStrategy.class)
    void setTabPostion(String title);

    @StateStrategyType(SkipStrategy.class)
    void refreshActivity();

    @StateStrategyType(SkipStrategy.class)
    void shareTextUrl();

    @StateStrategyType(SkipStrategy.class)
    void addToMainScreen(BookPOJO bookPOJO);


    void showProgres(boolean b);

    /** {@code chaptersTotal}: chapters the book has, more than {@code data} when only downloaded ones are listed. */
    @StateStrategyType(AddToEndSingleStrategy.class)
    void showData(ArrayList<AudioPOJO> data, String url, int chaptersTotal);

    void showTitle(@NonNull String name);

    @StateStrategyType(AddToEndSingleStrategy.class)
    void updateTime(int timeCurent, int timeEnd, int buffered);

    @StateStrategyType(AddToEndSingleStrategy.class)
    void setTimeEnd(int timeEnd);

    @StateStrategyType(AddToEndSingleStrategy.class)
    void setImageDrawable(int id);

    @StateStrategyType(AddToEndSingleStrategy.class)
    void setSelected(int id, String name);

    @StateStrategyType(SkipStrategy.class)
    void broadcastSend(@NonNull Intent intent);

    @StateStrategyType(SkipStrategy.class)
    void activityStart(@NonNull Intent intent);

    @StateStrategyType(AddToEndSingleStrategy.class)
    void stateCollapsed();

    @StateStrategyType(AddToEndSingleStrategy.class)
    void stateExpanded();

    @StateStrategyType(AddToEndSingleStrategy.class)
    void showOtherSource();

    @StateStrategyType(AddToEndSingleStrategy.class)
    void stateElse();

    @StateStrategyType(SkipStrategy.class)
    void setIsFavorite(boolean b);


    @StateStrategyType(SkipStrategy.class)
    void showRatingDialog();

    @StateStrategyType(SkipStrategy.class)
    void showToast(int id);

    @StateStrategyType(SkipStrategy.class)
    void showToast(String s);

    /** A notice that must be read: shown long, and on the next attach when the screen was stopped. */
    @StateStrategyType(OneExecutionStateStrategy.class)
    void showLongToast(int id);

    @StateStrategyType(SkipStrategy.class)
    void updateAdapter();

    @StateStrategyType(SkipStrategy.class)
    void startMainActivity(int fragmentId);
}
