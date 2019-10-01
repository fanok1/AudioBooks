package com.fanok.audiobooks.presenter;


import android.content.Context;
import android.util.Log;
import android.view.Gravity;
import android.view.View;

import androidx.appcompat.widget.PopupMenu;

import com.arellomobile.mvp.InjectViewState;
import com.arellomobile.mvp.MvpPresenter;
import com.fanok.audiobooks.Consts;
import com.fanok.audiobooks.R;
import com.fanok.audiobooks.interface_pacatge.searchable.SearchableModel;
import com.fanok.audiobooks.interface_pacatge.searchable.SearchablePresenter;
import com.fanok.audiobooks.interface_pacatge.searchable.SearchableView;
import com.fanok.audiobooks.model.AutorsModel;
import com.fanok.audiobooks.model.BooksDBModel;
import com.fanok.audiobooks.model.BooksModel;
import com.fanok.audiobooks.model.SearchebleModel;
import com.fanok.audiobooks.pojo.BookPOJO;
import com.fanok.audiobooks.pojo.GenrePOJO;
import com.fanok.audiobooks.pojo.SearcheblPOJO;

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
    private BooksDBModel mBooksDBModel;
    private com.fanok.audiobooks.interface_pacatge.books.GenreModel mModelGenre;
    private SearchableModel mSearchableModel;
    private boolean isEnd;

    private SearcheblPOJO mSearcheblPOJO;


    private String mUrl;


    @Override
    public void onCreate(int modelId, Context context) {
        mModelId = modelId;
        switch (modelId) {
            case Consts.MODEL_BOOKS:
                mUrl = "https://knigavuhe.org/search/?q=<qery>&page=<page>";
                break;
            case Consts.MODEL_AUTOR:
                mUrl = "https://knigavuhe.org/search/authors/?q=<qery>&page=<page>";
                break;
            case Consts.MODEL_ARTIST:
                mUrl = "https://knigavuhe.org/search/readers/?q=<qery>&page=<page>";
                break;
        }
        isEnd = false;
        mBooksDBModel = new BooksDBModel(context);
        switch (mModelId) {
            case Consts.MODEL_BOOKS:
                books = new ArrayList<>();
                mModelBook = new BooksModel();
                mSearcheblPOJO = new SearcheblPOJO();
                mSearchableModel = new SearchebleModel();
                break;
            case Consts.MODEL_AUTOR:
            case Consts.MODEL_ARTIST:
                genre = new ArrayList<>();
                mModelGenre = new AutorsModel();
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

        mSearcheblPOJO = new SearcheblPOJO();
        getViewState().showSeriesAndAutors(mSearcheblPOJO);

        if (mModelId == Consts.MODEL_BOOKS) {
            getViewState().showProgresTop(true);
            getDataSeriesAndAutors(mUrl.replace("<qery>", qery).replace("<page>", "1"));
        }

        isEnd = false;
        page = 0;
        loadNext(qery);
        /*
        FirebaseLanguageIdentification languageIdentifier =
                FirebaseNaturalLanguage.getInstance().getLanguageIdentification();
        languageIdentifier.identifyLanguage(qery)
                .addOnSuccessListener(
                        languageCode -> {
                            if (!Objects.equals(languageCode, "und")) {
                                FirebaseTranslatorOptions options =
                                        new FirebaseTranslatorOptions.Builder()
                                                .setSourceLanguage(FirebaseTranslateLanguage
                                                .languageForLanguageCode(languageCode))
                                                .setTargetLanguage(FirebaseTranslateLanguage.RU)
                                                .build();
                                final FirebaseTranslator translator =
                                        FirebaseNaturalLanguage.getInstance().getTranslator
                                        (options);

                                FirebaseModelDownloadConditions conditions = new
                                FirebaseModelDownloadConditions.Builder()
                                        .requireWifi()
                                        .build();
                                translator.downloadModelIfNeeded(conditions)
                                        .addOnSuccessListener(
                                                v -> translator.translate(qery)
                                                        .addOnSuccessListener(
                                                                this::loadNext)
                                                        .addOnFailureListener(
                                                                e -> loadNext(qery)))
                                        .addOnFailureListener(
                                                e -> loadNext(qery));

                            } else {
                                loadNext(qery);
                            }
                        })
                .addOnFailureListener(
                        e -> loadNext(qery));

         */

    }


    @Override
    public void loadNext(String qery) {
        if (!isEnd && !isLoading) {
            getViewState().showProgres(true);
            page++;
            getData(mUrl.replace("<qery>", qery).replace("<page>", String.valueOf(page)));
        }
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
            String url = genre.get(position).getUrl();
            if (url.contains("genre")) url += "<page>/";
            getViewState().returnResult(url,
                    genre.get(position).getName(), Consts.MODEL_BOOKS, tag);
        }
    }

    @Override
    public void onBookItemClick(View view, int position) {
        getViewState().startBookActivity(books.get(position));
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
                    getViewState().startBookActivity(books.get(position));
                    return true;
                case R.id.addFavorite:
                    mBooksDBModel.addFavorite(books.get(position));
                    return true;
                case R.id.removeFavorite:
                    mBooksDBModel.removeFavorite(books.get(position));
                    return true;
                case R.id.genre:
                    getViewState().returnResult(books.get(position).getUrlGenre(),
                            books.get(position).getGenre(), Consts.MODEL_BOOKS, "searchebleBooks");
                    return true;
                case R.id.author:
                    getViewState().returnResult(books.get(position).getUrlAutor(),
                            books.get(position).getAutor(), Consts.MODEL_BOOKS, "searchebleBooks");
                    return true;
                case R.id.artist:
                    getViewState().returnResult(books.get(position).getUrlArtist(),
                            books.get(position).getArtist(), Consts.MODEL_BOOKS, "searchebleBooks");
                    return true;
                case R.id.series:
                    getViewState().returnResult(books.get(position).getUrlSeries() + "?page=",
                            books.get(position).getSeries(), Consts.MODEL_BOOKS, "searchebleBooks");
                    return true;
                default:
                    return false;
            }
        });
        popupMenu.show();
    }

    @Override
    public void onAutorsListItemClick(View view, int position) {
        getViewState().returnResult(mSearcheblPOJO.getAutorsList().get(position).getUrl(),
                mSearcheblPOJO.getAutorsList().get(position).getName(), Consts.MODEL_BOOKS,
                "searchebleBooks");
    }

    @Override
    public void onSeriesListItemClick(View view, int position) {
        getViewState().returnResult(
                mSearcheblPOJO.getSeriesList().get(position).getUrl() + "?page=",
                mSearcheblPOJO.getSeriesList().get(position).getName(), Consts.MODEL_BOOKS,
                "searchebleBooks");
    }


    private void getData(String url) {
        if (!isLoading) {
            isLoading = true;
            if (mModelId == Consts.MODEL_BOOKS) {
                mModelBook.getBooks(url, page)
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
            } else {
                mModelGenre.getBooks(url, page)
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
                                if (e.getClass() == NullPointerException.class) {
                                    isEnd = true;
                                } else {
                                    getViewState().showToast(R.string.error_load_data);
                                    page--;
                                }
                                onComplete();

                            }

                            @Override
                            public void onComplete() {
                                Log.d(TAG, "onComplete");
                                getViewState().showData(genre);
                                getViewState().showProgres(false);
                                isLoading = false;
                            }
                        });
            }
        }
    }

    private void getDataSeriesAndAutors(String url) {
        mSearchableModel.dowland(url)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Observer<SearcheblPOJO>() {
                    @Override
                    public void onSubscribe(Disposable d) {
                    }

                    @Override
                    public void onNext(SearcheblPOJO searcheblPOJO) {
                        mSearcheblPOJO = searcheblPOJO;
                    }

                    @Override
                    public void onError(Throwable e) {
                        getViewState().showToast(R.string.error_load_data);
                        onComplete();
                    }

                    @Override
                    public void onComplete() {
                        Log.d(TAG, "onComplete");
                        getViewState().showSeriesAndAutors(mSearcheblPOJO);
                        getViewState().showProgresTop(false);
                    }
                });
    }



}
