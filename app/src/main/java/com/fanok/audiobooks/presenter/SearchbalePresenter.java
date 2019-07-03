package com.fanok.audiobooks.presenter;


import android.content.Context;
import android.support.v7.widget.PopupMenu;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.Toast;

import com.arellomobile.mvp.InjectViewState;
import com.arellomobile.mvp.MvpPresenter;
import com.fanok.audiobooks.Consts;
import com.fanok.audiobooks.R;
import com.fanok.audiobooks.interface_pacatge.searchable.SearchablePresenter;
import com.fanok.audiobooks.interface_pacatge.searchable.SearchableView;
import com.fanok.audiobooks.model.BooksDBModel;
import com.fanok.audiobooks.model.BooksModel;
import com.fanok.audiobooks.model.SearchableModel;
import com.fanok.audiobooks.pojo.BookPOJO;
import com.fanok.audiobooks.pojo.GenrePOJO;

import java.util.ArrayList;

import io.reactivex.Observer;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

@InjectViewState
public class SearchbalePresenter extends MvpPresenter<SearchableView> implements
        SearchablePresenter {

    private static final String TAG = "SearchablePresenter";
    private boolean isLoading = false;
    private int page = 0;
    private int mModelId;
    private ArrayList<BookPOJO> books;
    private ArrayList<GenrePOJO> genre;
    private com.fanok.audiobooks.interface_pacatge.books.BooksModel mModelBook;
    private SearchableModel mSearchableModel;
    private BooksDBModel mBooksDBModel;
    private boolean isEnd;


    private String mUrl;


    @Override
    public void onCreate(int modelId, Context context) {
        mModelId = modelId;
        switch (modelId) {
            case Consts.MODEL_BOOKS:
                mUrl = "https://audioknigi.club/search/books/page/?q=";
                break;
            case Consts.MODEL_AUTOR:
                mUrl = "https://audioknigi.club/authors/ajax-search/";
                break;
            case Consts.MODEL_ARTIST:
                mUrl = "https://audioknigi.club/performers/ajax-search/";
                break;
        }
        isEnd = false;
        mBooksDBModel = new BooksDBModel(context);
        switch (mModelId) {
            case Consts.MODEL_BOOKS:
                books = new ArrayList<>();
                mModelBook = new BooksModel();
                break;
            case Consts.MODEL_AUTOR:
            case Consts.MODEL_ARTIST:
                genre = new ArrayList<>();
                mSearchableModel = new SearchableModel();
                break;
        }
    }

    @Override
    public void onDestroy() {

    }

    @Override
    public void loadBoks(String qery) {
        if (books != null) {
            books.clear();
            getViewState().showData(books);
        }
        if (genre != null) {
            genre.clear();
            getViewState().showData(genre);
        }


        isEnd = false;
        page = 0;
        loadNext(qery);
    }

    @Override
    public void loadNext(String qery) {
        if (!isEnd) {
            getViewState().showProgres(true);
            page++;
            if (mModelId == Consts.MODEL_BOOKS) {
                getData(mUrl.replace("page", "page" + page) + qery);
            } else {
                getData(mUrl, qery);
            }
        }

    }

    @Override
    public void onChageOrintationScreen() {
        if ((books == null || books.size() == 0) && (genre == null || genre.size() == 0)
                && !isLoading) {
            getData(mUrl);
            isEnd = false;
        } else {
            switch (mModelId) {
                case Consts.MODEL_BOOKS:
                    getViewState().showData(books);
                    break;
                case Consts.MODEL_AUTOR:
                    getViewState().showData(genre);
                    break;
                case Consts.MODEL_ARTIST:
                    getViewState().showData(genre);
                    break;
            }
        }
        if (isLoading) getViewState().showProgres(true);
    }

    @Override
    public void onGenreItemClick(View view, int position) {
        String tag;
        switch (mModelId) {
            case Consts.MODEL_AUTOR:
                tag = "autorBooks";
                break;
            case Consts.MODEL_ARTIST:
                tag = "artistBooks";
                break;
            default:
                return;
        }

        if (genre != null && genre.size() - 1 >= position) {
            getViewState().returnResult(genre.get(position).getUrl() + "/page/",
                    genre.get(position).getName(), Consts.MODEL_BOOKS, tag);
        }
    }

    @Override
    public void onBookItemClick(View view, int position) {
        Toast.makeText(view.getContext(), "Short", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onBookItemLongClick(View view, int position) {
        PopupMenu popupMenu = new PopupMenu(view.getContext(), view, Gravity.END);
        popupMenu.inflate(R.menu.popup_books_item_menu);

        if (books.get(position).getSeries() == null || books.get(position).getUrlSeries() == null) {
            popupMenu.getMenu().findItem(R.id.series).setVisible(false);
        } else {
            popupMenu.getMenu().findItem(R.id.series).setVisible(true);
        }

        if (mBooksDBModel.inFavorite(books.get(position))) {
            popupMenu.getMenu().findItem(R.id.addFavorite).setVisible(false);
            popupMenu.getMenu().findItem(R.id.removeFavorite).setVisible(true);
        } else {
            popupMenu.getMenu().findItem(R.id.addFavorite).setVisible(true);
            popupMenu.getMenu().findItem(R.id.removeFavorite).setVisible(false);
        }

        popupMenu.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case R.id.open:
                    Toast.makeText(view.getContext(),
                            "Вы выбрали PopupMenu 1",
                            Toast.LENGTH_SHORT).show();
                    return true;
                case R.id.addFavorite:
                    mBooksDBModel.addFavorite(books.get(position));
                    return true;
                case R.id.removeFavorite:
                    mBooksDBModel.removeFavorite(books.get(position));
                    return true;
                case R.id.genre:
                    getViewState().returnResult(books.get(position).getUrlGenre() + "/page/",
                            books.get(position).getGenre(), Consts.MODEL_BOOKS, "genreBooks");
                    return true;
                case R.id.author:
                    getViewState().returnResult(books.get(position).getUrlAutor() + "/page/",
                            books.get(position).getAutor(), Consts.MODEL_BOOKS, "autorBooks");
                    return true;
                case R.id.artist:
                    getViewState().returnResult(books.get(position).getUrlArtist() + "/page/",
                            books.get(position).getArtist(), Consts.MODEL_BOOKS, "artistBooks");
                    return true;
                case R.id.series:
                    getViewState().returnResult(books.get(position).getUrlSeries() + "/page/",
                            books.get(position).getSeries(), Consts.MODEL_BOOKS, "seriesBooks");
                    return true;
                default:
                    return false;
            }
        });
        popupMenu.show();
    }


    private void getData(String url) {
        if (!isLoading) {
            isLoading = true;
            mModelBook.getBooks(url)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(new Observer<ArrayList<BookPOJO>>() {
                        @Override
                        public void onSubscribe(Disposable d) {
                        }

                        @Override
                        public void onNext(ArrayList<BookPOJO> bookPOJOS) {
                            books.addAll(bookPOJOS);
                        }

                        @Override
                        public void onError(Throwable e) {
                            e.printStackTrace();
                            Log.e(TAG, e.getMessage());
                            if (e.getClass() == NullPointerException.class) {
                                isEnd = true;
                            } else {
                                page--;
                            }
                            onComplete();

                        }

                        @Override
                        public void onComplete() {
                            Log.d(TAG, "onComplete");
                            getViewState().showData(books);
                            getViewState().showProgres(false);
                            isLoading = false;
                        }
                    });
        }
    }

    private void getData(String url, String query) {
        if (!isLoading) {
            isLoading = true;
            mSearchableModel.getBooks(url, query)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(new Observer<ArrayList<GenrePOJO>>() {
                        @Override
                        public void onSubscribe(Disposable d) {
                        }

                        @Override
                        public void onNext(ArrayList<GenrePOJO> bookPOJOS) {
                            genre.addAll(bookPOJOS);
                        }

                        @Override
                        public void onError(Throwable e) {
                            e.printStackTrace();
                            Log.e(TAG, e.getMessage());
                            onComplete();

                        }

                        @Override
                        public void onComplete() {
                            Log.d(TAG, "onComplete");
                            getViewState().showData(genre);
                            getViewState().showProgres(false);
                            isLoading = false;
                            isEnd = true;
                        }
                    });
        }
    }

}
