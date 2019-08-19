package com.fanok.audiobooks.presenter;

import android.content.Context;
import android.support.annotation.NonNull;
import android.view.Menu;
import android.view.MenuItem;

import com.arellomobile.mvp.InjectViewState;
import com.arellomobile.mvp.MvpPresenter;
import com.fanok.audiobooks.R;
import com.fanok.audiobooks.interface_pacatge.book_content.Activity;
import com.fanok.audiobooks.interface_pacatge.book_content.ActivityPresenter;
import com.fanok.audiobooks.model.BooksDBModel;
import com.fanok.audiobooks.pojo.BookPOJO;


@InjectViewState
public class BookPresenter extends MvpPresenter<Activity> implements ActivityPresenter {
    private static final String TAG = "BookPresenter";

    private BookPOJO mBookPOJO;
    private BooksDBModel mBooksDBModel;
    private MenuItem mAddFavorite;
    private MenuItem mRemoveFavorite;


    @Override
    public void onCreate(@NonNull BookPOJO bookPOJO, @NonNull Context context) {
        mBookPOJO = bookPOJO;
        mBooksDBModel = new BooksDBModel(context);
        mBooksDBModel.addHistory(mBookPOJO);
    }

    @Override
    public void onDestroy() {

    }

    @Override
    public void onCreateOptionsMenu(Menu menu) {
        mAddFavorite = menu.findItem(R.id.addFavorite);
        mRemoveFavorite = menu.findItem(R.id.removeFavorite);
        if (mBooksDBModel.inFavorite(mBookPOJO)) {
            mAddFavorite.setVisible(false);
            mRemoveFavorite.setVisible(true);
        } else {
            mAddFavorite.setVisible(true);
            mRemoveFavorite.setVisible(false);
        }
    }


    @Override
    public void onOptionsMenuItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.addFavorite:
                if (!mBooksDBModel.inFavorite(mBookPOJO)) {
                    mBooksDBModel.addFavorite(mBookPOJO);
                    mAddFavorite.setVisible(false);
                    mRemoveFavorite.setVisible(true);
                }
                break;
            case R.id.removeFavorite:
                if (mBooksDBModel.inFavorite(mBookPOJO)) {
                    mBooksDBModel.removeFavorite(mBookPOJO);
                    mAddFavorite.setVisible(true);
                    mRemoveFavorite.setVisible(false);
                }
                break;
            case R.id.refresh:
                getViewState().refreshActivity();
                break;
            case R.id.share:
                getViewState().shareTextUrl();
                break;
            case R.id.addMainScreen:
                getViewState().addToMainScreen();
                break;
        }
    }
}
