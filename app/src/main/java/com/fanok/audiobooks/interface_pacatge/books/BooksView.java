package com.fanok.audiobooks.interface_pacatge.books;


import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;

import com.arellomobile.mvp.MvpView;
import com.arellomobile.mvp.viewstate.strategy.AddToEndSingleStrategy;
import com.arellomobile.mvp.viewstate.strategy.OneExecutionStateStrategy;
import com.arellomobile.mvp.viewstate.strategy.SkipStrategy;
import com.arellomobile.mvp.viewstate.strategy.StateStrategyType;
import com.fanok.audiobooks.pojo.BookPOJO;
import com.fanok.audiobooks.pojo.GenrePOJO;
import com.fanok.audiobooks.search.ListingFilters;

import java.util.ArrayList;

public interface BooksView extends MvpView {
    @StateStrategyType(AddToEndSingleStrategy.class)
    void setLayoutManager();

    @StateStrategyType(AddToEndSingleStrategy.class)
    void setLayoutManager(int count);

    @StateStrategyType(AddToEndSingleStrategy.class)
    void showDataBooks(@NonNull ArrayList<BookPOJO> genres);

    @StateStrategyType(AddToEndSingleStrategy.class)
    void showDataGenres(@NonNull ArrayList<GenrePOJO> genres);

    @StateStrategyType(AddToEndSingleStrategy.class)
    void clearData();

    @StateStrategyType(AddToEndSingleStrategy.class)
    void showProgres(boolean b);

    @StateStrategyType(OneExecutionStateStrategy.class)
    void showToast(int message);

    @StateStrategyType(OneExecutionStateStrategy.class)
    void showToast(String message);

    @StateStrategyType(AddToEndSingleStrategy.class)
    void showRefreshing(boolean b);

    @StateStrategyType(OneExecutionStateStrategy.class)
    void setPosition(int position);

    @StateStrategyType(SkipStrategy.class)
    void showFragment(Fragment fragment, String tag);

    @StateStrategyType(SkipStrategy.class)
    void showSearchActivity(int modelId);

    @StateStrategyType(SkipStrategy.class)
    void showBooksActivity(@NonNull BookPOJO bookPOJO);

    @StateStrategyType(SkipStrategy.class)
    void recreate();

    /**
     * The filter state: the toolbar's filter item (duration range, period choices) and, while a duration filter
     * is on, the strip above the list with the range, the match count and the "search further" offer.
     */
    @StateStrategyType(AddToEndSingleStrategy.class)
    void showFilterBar(@NonNull ListingFilters.Bar bar);

    /**
     * The listing URL as the filter bar rewrote it, and the period chosen (null when none): kept in the fragment
     * arguments for a restore.
     */
    @StateStrategyType(AddToEndSingleStrategy.class)
    void rememberUrl(@NonNull String url, ListingFilters.Period period);

    @StateStrategyType(SkipStrategy.class)
    void showDurationPicker(@NonNull ListingFilters.Duration current);

    /**
     * Offers another route to the site whose listing failed; the action opens the proxy settings or tells the
     * presenter it was accepted, any other dismissal declines it.
     */
    @StateStrategyType(OneExecutionStateStrategy.class)
    void showRouteOffer(@StringRes int message, @NonNull String site, @StringRes int action,
            boolean openProxySettings);

}
