package com.fanok.audiobooks.interface_pacatge.favorite;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.arellomobile.mvp.MvpView;
import com.arellomobile.mvp.viewstate.strategy.AddToEndSingleStrategy;
import com.arellomobile.mvp.viewstate.strategy.SkipStrategy;
import com.arellomobile.mvp.viewstate.strategy.StateStrategyType;
import com.fanok.audiobooks.pojo.BookPOJO;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

public interface FavoriteView extends MvpView {
    @StateStrategyType(AddToEndSingleStrategy.class)
    void setLayoutManager();

    @StateStrategyType(AddToEndSingleStrategy.class)
    void setLayoutManager(int count);

    @StateStrategyType(AddToEndSingleStrategy.class)
    void showData(ArrayList<BookPOJO> bookPOJOS);

    @StateStrategyType(AddToEndSingleStrategy.class)
    void updateFilter();

    @StateStrategyType(AddToEndSingleStrategy.class)
    void clearData();

    @StateStrategyType(SkipStrategy.class)
    void showToast(int message);

    @StateStrategyType(SkipStrategy.class)
    void showToast(String message);

    @StateStrategyType(AddToEndSingleStrategy.class)
    void showProgres(boolean b);

    /** The load failed: a message with a retry action instead of the list. */
    @StateStrategyType(AddToEndSingleStrategy.class)
    void showError(boolean b);

    @StateStrategyType(AddToEndSingleStrategy.class)
    void setSubTitle(@NotNull String text);

    @StateStrategyType(SkipStrategy.class)
    void showFragment(Fragment fragment, String tag);

    @StateStrategyType(SkipStrategy.class)
    void showBooksActivity(@NonNull BookPOJO bookPOJO);

    @StateStrategyType(SkipStrategy.class)
    void showSearchActivity(int modelId);

    /** The long-press menu for {@code book}; the view owns the dialog. */
    @StateStrategyType(SkipStrategy.class)
    void showBookMenu(@NonNull BookPOJO book);

    /** The values of one filter (genre, author, ...) for the view to offer. */
    @StateStrategyType(SkipStrategy.class)
    void showFilterValues(int filterId, @NonNull ArrayList<String> values);

    /** A book was hidden; the view offers undo and reports the outcome to the presenter. */
    @StateStrategyType(SkipStrategy.class)
    void showUndoRemove(@NonNull BookPOJO book);
}
