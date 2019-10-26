package com.fanok.audiobooks.presenter;


import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Build;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.widget.PopupMenu;

import com.arellomobile.mvp.InjectViewState;
import com.arellomobile.mvp.MvpPresenter;
import com.fanok.audiobooks.Consts;
import com.fanok.audiobooks.R;
import com.fanok.audiobooks.fragment.BooksFragment;
import com.fanok.audiobooks.interface_pacatge.favorite.FavoriteView;
import com.fanok.audiobooks.model.AudioDBModel;
import com.fanok.audiobooks.model.BooksDBModel;
import com.fanok.audiobooks.model.FavoriteModel;
import com.fanok.audiobooks.pojo.BookPOJO;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.squareup.picasso.Picasso;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

import io.reactivex.Observer;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

@InjectViewState
public class FavoritePresenter extends MvpPresenter<FavoriteView> implements
        com.fanok.audiobooks.interface_pacatge.favorite.FavoritePresenter {

    private ArrayList<BookPOJO> books;
    private ArrayList<BookPOJO> flter = null;
    private BooksDBModel mBooksDBModel;
    private AudioDBModel mAudioDBModel;
    private FavoriteModel mFavoriteModel;
    private int table;
    private boolean isLoading = false;
    private Context mContext;
    private View mView;


    public FavoritePresenter(@NotNull Context context, int table) {
        mBooksDBModel = new BooksDBModel(context);
        mAudioDBModel = new AudioDBModel(context);
        mFavoriteModel = new FavoriteModel(context);
        this.table = table;
        mContext = context;
    }


    @Override
    public void setView(@NotNull View view) {
        mView = view;
    }

    @Override
    protected void onFirstViewAttach() {
        super.onFirstViewAttach();
        loadBooks();
    }

    @Override
    public void loadBooks() {
        if (!isLoading) {
            isLoading = true;
            mFavoriteModel.getBooks(table)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(new Observer<ArrayList<BookPOJO>>() {
                        @Override
                        public void onSubscribe(Disposable d) {
                        }

                        @Override
                        public void onNext(ArrayList<BookPOJO> bookPOJOS) {
                            books = bookPOJOS;
                        }

                        @Override
                        public void onError(Throwable e) {
                            getViewState().showToast(R.string.error_load_data);
                            onComplete();
                        }

                        @Override
                        public void onComplete() {
                            getViewState().showData(books);
                            getViewState().showProgres(false);
                            isLoading = false;
                        }
                    });

        }
        getViewState().showData(books);
    }


    @Override
    public void onDestroy() {
    }

    @Override
    public void onBookItemClick(View view, int position) {
        getViewState().showBooksActivity(books.get(position));
    }

    @Override
    public void onBookItemLongClick(View view, int position, LayoutInflater layoutInflater) {
        @SuppressLint("InflateParams") View layout = layoutInflater.inflate(
                R.layout.bootom_sheet_books_menu, null);
        final BottomSheetDialog dialog = new BottomSheetDialog(view.getContext());
        dialog.setContentView(layout);

        TextView open = layout.findViewById(R.id.open);
        TextView add = layout.findViewById(R.id.addFavorite);
        TextView remove = layout.findViewById(R.id.removeFavorite);
        TextView genre = layout.findViewById(R.id.genre);
        TextView author = layout.findViewById(R.id.author);
        TextView artist = layout.findViewById(R.id.artist);
        TextView series = layout.findViewById(R.id.series);

        ImageView imageView = layout.findViewById(R.id.imageView);
        Picasso.get()
                .load(books.get(position).getPhoto())
                .error(android.R.drawable.ic_menu_camera)
                .placeholder(android.R.drawable.ic_menu_camera)
                .into(imageView);

        TextView title = layout.findViewById(R.id.title);
        title.setText(books.get(position).getName());

        TextView authorName = layout.findViewById(R.id.authorName);
        authorName.setText(books.get(position).getAutor());

        if (books.get(position).getSeries() == null || books.get(position).getUrlSeries() == null) {
            series.setVisibility(View.GONE);
        } else {
            series.setVisibility(View.VISIBLE);
        }

        add.setVisibility(View.GONE);
        remove.setVisibility(View.VISIBLE);
        remove.setText(R.string.remove);
        remove.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_minus_circle, 0, 0, 0);

        open.setOnClickListener(view1 -> {
            dialog.dismiss();
            getViewState().showBooksActivity(books.get(position));
        });

        remove.setOnClickListener(view1 -> {
            dialog.dismiss();
            if (table == Consts.TABLE_FAVORITE) {
                mBooksDBModel.removeFavorite(books.get(position));
            } else if (table == Consts.TABLE_HISTORY) {
                mBooksDBModel.removeHistory(books.get(position));
                mAudioDBModel.remove(books.get(position).getUrl());
            }
            books.remove(position);
            getViewState().showData(books);
        });

        genre.setOnClickListener(view1 -> {
            dialog.dismiss();
            getViewState().showFragment(BooksFragment.newInstance(
                    books.get(position).getUrlGenre(),
                    R.string.menu_audiobooks,
                    books.get(position).getGenre(), Consts.MODEL_BOOKS),
                    "genreBooks");
        });

        author.setOnClickListener(view1 -> {
            dialog.dismiss();
            if (!books.get(position).getUrlAutor().isEmpty()) {
                getViewState().showFragment(BooksFragment.newInstance(
                        books.get(position).getUrlAutor(),
                        R.string.menu_audiobooks,
                        books.get(position).getAutor(), Consts.MODEL_BOOKS),
                        "autorBooks");
            }
        });

        artist.setOnClickListener(view1 -> {
            dialog.dismiss();
            getViewState().showFragment(BooksFragment.newInstance(
                    books.get(position).getUrlArtist(),
                    R.string.menu_audiobooks,
                    books.get(position).getArtist(), Consts.MODEL_BOOKS),
                    "artistBooks");
        });

        series.setOnClickListener(view12 -> {
            dialog.dismiss();
            getViewState().showFragment(BooksFragment.newInstance(
                    books.get(position).getUrlSeries() + "?page=",
                    R.string.menu_audiobooks,
                    books.get(position).getSeries(), Consts.MODEL_BOOKS),
                    "seriesBooks");
        });
        dialog.show();
    }

    @Override
    public void onSearch(String qery) {
        ArrayList<BookPOJO> filter = new ArrayList<>();
        for (BookPOJO book : books) {
            if (book.getName().toLowerCase().contains(qery.toLowerCase())) {
                filter.add(book);
            }
        }
        getViewState().showData(filter);
    }

    @Override
    public void cealrData() {
        if (books != null) {
            books.clear();
            getViewState().showData(books);
        }
    }

    @Override
    public void onOptionsItemSelected(int id) {
        if (books != null && id != R.id.filter && id != R.id.order) {
            Comparator<BookPOJO> comparator = getComparator(id);
            if (id == R.id.genre_filter || id == R.id.autor_filter ||
                    id == R.id.artist_filter || id == R.id.series_filter) {
                showPopupMenu(id);
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    if (flter == null) {
                        books.sort(comparator);
                    } else {
                        flter.sort(comparator);
                    }
                } else {
                    if (flter == null) {
                        Collections.sort(books, comparator);
                    } else {
                        Collections.sort(flter, comparator);
                    }
                }
                if (flter == null) {
                    getViewState().showData(books);
                } else {
                    getViewState().showData(flter);
                }
            }

        }

    }

    private void showPopupMenu(int id) {
        if (mView != null) {
            ArrayList<String> arrayList;
            switch (id) {
                case R.id.genre_filter:
                    arrayList = mBooksDBModel.getGenre();
                    break;
                case R.id.autor_filter:
                    arrayList = mBooksDBModel.getAutors();
                    break;
                case R.id.artist_filter:
                    arrayList = mBooksDBModel.getArtists();
                    break;
                case R.id.series_filter:
                    arrayList = mBooksDBModel.getSeries();
                    break;
                default:
                    arrayList = new ArrayList<>();
            }
            if (arrayList.isEmpty()) {
                return;
            } else {
                arrayList.add(0, "Все");
            }

            PopupMenu popupMenu = new PopupMenu(mContext, mView, Gravity.END);
            for (int i = 0; i < arrayList.size(); i++) {
                popupMenu.getMenu().add(1, i, 0, arrayList.get(i));
            }
            popupMenu.setOnMenuItemClickListener(item -> {
                if (books != null) {
                    if (item.getTitle().equals("Все")) {
                        this.flter = null;
                        getViewState().showData(books);
                        return false;
                    }
                    if (item.getTitle().equals("Все")) {
                        getViewState().setSubTitle("");
                    } else {
                        getViewState().setSubTitle(item.getTitle().toString());
                    }
                    ArrayList<BookPOJO> filter = new ArrayList<>();
                    for (BookPOJO book : books) {
                        String text;
                        switch (id) {
                            case R.id.genre_filter:
                                text = book.getGenre();
                                break;
                            case R.id.autor_filter:
                                text = book.getAutor();
                                break;
                            case R.id.artist_filter:
                                text = book.getArtist();
                                break;
                            case R.id.series_filter:
                                text = book.getSeries();
                                break;
                            default:
                                return false;
                        }
                        if (item.getTitle().equals(text)) {
                            filter.add(book);
                        }
                    }
                    this.flter = filter;
                    getViewState().showData(filter);
                }
                return false;
            });
            popupMenu.show();
        }
    }

    private Comparator<BookPOJO> getComparator(int sort) {
        if (sort == R.id.name) {
            return (bookPOJO, t1) -> bookPOJO.getName().compareTo(t1.getName());
        } else if (sort == R.id.genre) {
            return (bookPOJO, t1) -> bookPOJO.getGenre().compareTo(t1.getGenre());
        } else if (sort == R.id.autor) {
            return (bookPOJO, t1) -> bookPOJO.getAutor().compareTo(t1.getAutor());
        } else if (sort == R.id.artist) {
            return (bookPOJO, t1) -> bookPOJO.getArtist().compareTo(t1.getArtist());
        } else if (sort == R.id.series) {
            return (bookPOJO, t1) -> bookPOJO.getSeries().compareTo(t1.getSeries());
        } else {
            return (bookPOJO, t1) -> 0;
        }
    }

}