package com.fanok.audiobooks.presenter;


import android.content.Context;
import android.content.Intent;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.support.v7.widget.PopupMenu;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.Toast;

import com.arellomobile.mvp.InjectViewState;
import com.arellomobile.mvp.MvpPresenter;
import com.fanok.audiobooks.Consts;
import com.fanok.audiobooks.R;
import com.fanok.audiobooks.fragment.BooksFragment;
import com.fanok.audiobooks.interface_pacatge.books.BooksView;
import com.fanok.audiobooks.model.AutorsModel;
import com.fanok.audiobooks.model.BooksDBModel;
import com.fanok.audiobooks.model.BooksModel;
import com.fanok.audiobooks.model.GenreModel;
import com.fanok.audiobooks.pojo.BookPOJO;
import com.fanok.audiobooks.pojo.GenrePOJO;

import java.util.ArrayList;

import io.reactivex.Observer;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

@InjectViewState
public class BooksPresenter extends MvpPresenter<BooksView> implements
        com.fanok.audiobooks.interface_pacatge.books.BooksPresenter {

    private static final String TAG = "BooksPresenter";
    private boolean isLoading = false;
    private boolean isRefreshing = false;
    private int page = 0;
    private int mModelId;
    private String mSubTitle;
    private ArrayList<BookPOJO> books;
    private ArrayList<GenrePOJO> genre;
    private com.fanok.audiobooks.interface_pacatge.books.BooksModel mModelBook;
    private com.fanok.audiobooks.interface_pacatge.books.GenreModel mModelGenre;
    private BooksDBModel mBooksDBModel;

    private Context mContext;


    private String mUrl;
    private boolean isEnd;


    @Override
    public void onCreate(@NonNull String url, int modelId, @NonNull String subTitle,
            Context context) {
        mUrl = url;
        mModelId = modelId;
        mSubTitle = subTitle;
        isEnd = false;
        mContext = context;
        mBooksDBModel = new BooksDBModel(context);
        switch (mModelId) {
            case Consts.MODEL_BOOKS:
                books = new ArrayList<>();
                mModelBook = new BooksModel();
                break;
            case Consts.MODEL_GENRE:
                genre = new ArrayList<>();
                mModelGenre = new GenreModel();
                break;
            case Consts.MODEL_AUTOR:
                genre = new ArrayList<>();
                mModelGenre = new AutorsModel();
                break;
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
    public void loadBoks() {
        if (!isEnd && !isRefreshing) {
            getViewState().showProgres(true);
            page++;
            getData(mUrl.replace("page", "page" + page));
        }

    }

    @Override
    public void onRefresh() {
        if (!isLoading) {
            isRefreshing = true;
            isEnd = false;
            getViewState().showRefreshing(true);
            page = 1;
            getData(mUrl.replace("page", "page" + page));
        } else {
            getViewState().showRefreshing(false);
        }

    }

    @Override
    public void onChageOrintationScreen() {
        if ((books == null || books.size() == 0) && (genre == null || genre.size() == 0)
                && !isLoading) {
            page = 0;
            getData(mUrl);
            isEnd = false;
        } else {
            switch (mModelId) {
                case Consts.MODEL_BOOKS:
                    getViewState().showData(books);
                    break;
                case Consts.MODEL_GENRE:
                    getViewState().showData(genre);
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
    public void onOptionItemSelected(int itemId) {
        String url = mUrl.replace("top/", "");
        url = url.replace("discussed/", "");
        url = url.replaceAll("\\?period=.+", "");

        String subTitle = mSubTitle.replace(" " + getStringById(R.string.order_new), "");
        subTitle = subTitle.replace(" " + getStringById(R.string.order_reting), "");
        subTitle = subTitle.replace(" " + getStringById(R.string.order_discussed), "");


        switch (itemId) {
            case R.id.app_bar_search:
                getViewState().showSearchActivity(mModelId);
                break;
            case R.id.new_data:
                getViewState().showFragment(BooksFragment.newInstance(
                        url, R.string.menu_audiobooks,
                        subTitle + " " + getStringById(R.string.order_new), Consts.MODEL_BOOKS),
                        "audioBooksOrederNew");
                break;
            case R.id.reting_all_time:
                getViewState().showFragment(BooksFragment.newInstance(
                        url.replace("page", "top/page") + "?period=all",
                        R.string.menu_audiobooks,
                        subTitle + " " + getStringById(R.string.order_reting), Consts.MODEL_BOOKS),
                        "audioBooksOrederBestAllTime");
                break;
            case R.id.reting_month:
                getViewState().showFragment(BooksFragment.newInstance(
                        url.replace("page", "top/page") + "?period=30",
                        R.string.menu_audiobooks,
                        subTitle + " " + getStringById(R.string.order_reting), Consts.MODEL_BOOKS),
                        "audioBooksOrederBestMonth");
                break;
            case R.id.reting_week:
                getViewState().showFragment(BooksFragment.newInstance(
                        url.replace("page", "top/page") + "?period=7",
                        R.string.menu_audiobooks,
                        subTitle + " " + getStringById(R.string.order_reting), Consts.MODEL_BOOKS),
                        "audioBooksOrederBestWeek");
                break;
            case R.id.discussed_all_time:
                getViewState().showFragment(BooksFragment.newInstance(
                        url.replace("page", "discussed/page") + "?period=all",
                        R.string.menu_audiobooks,
                        subTitle + " " + getStringById(R.string.order_discussed),
                        Consts.MODEL_BOOKS),
                        "audioBooksOrederDiscussedAllTime");
                break;
            case R.id.discussed_month:
                getViewState().showFragment(BooksFragment.newInstance(
                        url.replace("page", "discussed/page") + "?period=30",
                        R.string.menu_audiobooks,
                        subTitle + " " + getStringById(R.string.order_discussed),
                        Consts.MODEL_BOOKS),
                        "audioBooksOrederDiscussedMonth");
                break;
            case R.id.discussed_week:
                getViewState().showFragment(BooksFragment.newInstance(
                        url.replace("page", "discussed/page") + "?period=7",
                        R.string.menu_audiobooks,
                        subTitle + " " + getStringById(R.string.order_discussed),
                        Consts.MODEL_BOOKS),
                        "audioBooksOrederDiscussedWeek");
                break;
        }
    }

    @Override
    public void onGenreItemClick(View view, int position) {
        String tag;
        switch (mModelId) {
            case Consts.MODEL_GENRE:
                tag = "genreBooks";
                break;
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
            getViewState().showFragment(BooksFragment.newInstance(
                    genre.get(position).getUrl() + "/page/",
                    R.string.menu_audiobooks,
                    genre.get(position).getName(), Consts.MODEL_BOOKS), tag);
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


    private void getData(String url) {
        if (!isLoading) {
            isLoading = true;
            if (mModelId == Consts.MODEL_BOOKS) {
                mModelBook.getBooks(url)
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(new Observer<ArrayList<BookPOJO>>() {
                            @Override
                            public void onSubscribe(Disposable d) {
                            }

                            @Override
                            public void onNext(ArrayList<BookPOJO> bookPOJOS) {
                                if (isRefreshing) {
                                    books.clear();
                                }
                                books.addAll(bookPOJOS);
                            }

                            @Override
                            public void onError(Throwable e) {
                                e.printStackTrace();
                                Log.e(TAG, e.getMessage());
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
                                getViewState().showData(books);
                                if (isRefreshing) getViewState().setPosition(0);
                                getViewState().showProgres(false);
                                getViewState().showRefreshing(false);
                                isLoading = false;
                                isRefreshing = false;
                            }
                        });
            } else {
                mModelGenre.getBooks(url)
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(new Observer<ArrayList<GenrePOJO>>() {
                            @Override
                            public void onSubscribe(Disposable d) {
                            }

                            @Override
                            public void onNext(ArrayList<GenrePOJO> bookPOJOS) {
                                if (isRefreshing) {
                                    genre.clear();
                                }
                                genre.addAll(bookPOJOS);
                            }

                            @Override
                            public void onError(Throwable e) {
                                e.printStackTrace();
                                if (e.getClass() == NullPointerException.class) {
                                    isEnd = true;
                                } else {
                                    getViewState().showToast(R.string.error_load_data);
                                    page--;
                                }
                                Log.e(TAG, e.getMessage());
                                onComplete();

                            }

                            @Override
                            public void onComplete() {
                                Log.d(TAG, "onComplete");
                                getViewState().showData(genre);
                                if (isRefreshing) getViewState().setPosition(0);
                                getViewState().showProgres(false);
                                getViewState().showRefreshing(false);
                                isLoading = false;
                                isRefreshing = false;
                            }
                        });
            }
        }
    }

    private String getStringById(int id) {
        return mContext.getResources().getString(id);
    }

    @Override
    public void onActivityResult(@NonNull Intent intent) {
        String url = intent.getStringExtra("url");
        String name = intent.getStringExtra("name");
        int modelId = intent.getIntExtra("modelId", 0);
        String tag = intent.getStringExtra("tag");

        Fragment fragment = BooksFragment.newInstance(url,
                R.string.menu_audiobooks, name, modelId);
        getViewState().showFragment(fragment, tag);
    }
}
