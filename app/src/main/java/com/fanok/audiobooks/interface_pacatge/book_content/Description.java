package com.fanok.audiobooks.interface_pacatge.book_content;

import com.arellomobile.mvp.MvpView;
import com.arellomobile.mvp.viewstate.strategy.AddToEndSingleStrategy;
import com.arellomobile.mvp.viewstate.strategy.SkipStrategy;
import com.arellomobile.mvp.viewstate.strategy.StateStrategyType;
import com.fanok.audiobooks.pojo.BookPOJO;
import com.fanok.audiobooks.pojo.DescriptionPOJO;
import java.util.ArrayList;

public interface Description extends MvpView {

    @StateStrategyType(AddToEndSingleStrategy.class)
    void showProgress(boolean b);

    @StateStrategyType(AddToEndSingleStrategy.class)
    void showOtherBooks(ArrayList<BookPOJO> data);

    @StateStrategyType(AddToEndSingleStrategy.class)
    void showDescription(DescriptionPOJO description);

    @StateStrategyType(AddToEndSingleStrategy.class)
    void showOtherBooksLine(boolean b);

    @StateStrategyType(SkipStrategy.class)
    void showToast(int id);
}
