package com.fanok.audiobooks.presenter;


import android.content.Context;
import android.support.v7.widget.PopupMenu;
import android.view.Gravity;
import android.view.View;
import android.widget.Toast;

import com.arellomobile.mvp.InjectViewState;
import com.arellomobile.mvp.MvpPresenter;
import com.fanok.audiobooks.Consts;
import com.fanok.audiobooks.R;
import com.fanok.audiobooks.fragment.BooksFragment;
import com.fanok.audiobooks.interface_pacatge.favorite.FavoriteView;
import com.fanok.audiobooks.model.BooksDBModel;
import com.fanok.audiobooks.pojo.BookPOJO;

import java.util.ArrayList;

@InjectViewState
public class FavoritePresenter extends MvpPresenter<FavoriteView> implements
        com.fanok.audiobooks.interface_pacatge.favorite.FavoritePresenter {

    private static final String TAG = "FavoritePresenter";
    private ArrayList<BookPOJO> books;
    private BooksDBModel mBooksDBModel;
    private int table;

    @Override
    public void onCreate(Context context, int table) {
        mBooksDBModel = new BooksDBModel(context);
        books = new ArrayList<>();
        this.table = table;
    }

    @Override
    public void loadBooks() {
        switch (table) {
            case Consts.TABLE_FAVORITE:
                books = mBooksDBModel.getAllFavorite();
                break;
            case Consts.TABLE_HISTORY:
                books = mBooksDBModel.getAllHistory();
                break;
        }
        getViewState().showData(books);
    }

    @Override
    public void onDestroy() {
    }

    @Override
    public void onBookItemClick(View view, int position) {
        Toast.makeText(view.getContext(), "Short", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onBookItemLongClick(View view, int position) {
        PopupMenu popupMenu = new PopupMenu(view.getContext(), view, Gravity.END);
        popupMenu.inflate(R.menu.popup_favorite_item_menu);

        if (books.get(position).getSeries() == null || books.get(position).getUrlSeries() == null) {
            popupMenu.getMenu().findItem(R.id.series).setVisible(false);
        } else {
            popupMenu.getMenu().findItem(R.id.series).setVisible(true);
        }

        popupMenu.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case R.id.open:
                    Toast.makeText(view.getContext(),
                            "Вы выбрали PopupMenu 1",
                            Toast.LENGTH_SHORT).show();
                    return true;
                case R.id.remove:
                    if (table == Consts.TABLE_FAVORITE) {
                        mBooksDBModel.removeFavorite(books.get(position));
                    } else if (table == Consts.TABLE_HISTORY) {
                        mBooksDBModel.removeHistory(books.get(position));
                    }
                    books.remove(position);
                    getViewState().showData(books);
                    return true;
                case R.id.genre:
                    getViewState().showFragment(BooksFragment.newInstance(
                            books.get(position).getUrlGenre() + "/page/",
                            R.string.menu_audiobooks,
                            books.get(position).getGenre(), Consts.MODEL_BOOKS),
                            "genreBooks");
                    return true;
                case R.id.author:
                    getViewState().showFragment(BooksFragment.newInstance(
                            books.get(position).getUrlAutor() + "/page/",
                            R.string.menu_audiobooks,
                            books.get(position).getAutor(), Consts.MODEL_BOOKS),
                            "autorBooks");
                    return true;
                case R.id.artist:
                    getViewState().showFragment(BooksFragment.newInstance(
                            books.get(position).getUrlArtist() + "/page/",
                            R.string.menu_audiobooks,
                            books.get(position).getArtist(), Consts.MODEL_BOOKS),
                            "artistBooks");
                    return true;
                case R.id.series:
                    getViewState().showFragment(BooksFragment.newInstance(
                            books.get(position).getUrlSeries() + "/page/",
                            R.string.menu_audiobooks,
                            books.get(position).getSeries(), Consts.MODEL_BOOKS),
                            "seriesBooks");
                    return true;
                default:
                    return false;
            }
        });
        popupMenu.show();
    }
}
