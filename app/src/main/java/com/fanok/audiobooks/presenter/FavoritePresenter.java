package com.fanok.audiobooks.presenter;


import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.appcompat.widget.PopupMenu;
import androidx.preference.PreferenceManager;
import com.arellomobile.mvp.InjectViewState;
import com.arellomobile.mvp.MvpPresenter;
import com.fanok.audiobooks.Consts;
import com.fanok.audiobooks.MyInterstitialAd;
import com.fanok.audiobooks.R;
import com.fanok.audiobooks.activity.PopupClearSaved;
import com.fanok.audiobooks.fragment.BooksFragment;
import com.fanok.audiobooks.interface_pacatge.favorite.FavoriteView;
import com.fanok.audiobooks.model.AudioDBModel;
import com.fanok.audiobooks.model.AudioListDBModel;
import com.fanok.audiobooks.model.BooksDBModel;
import com.fanok.audiobooks.model.FavoriteModel;
import com.fanok.audiobooks.pojo.AudioListPOJO;
import com.fanok.audiobooks.pojo.BookPOJO;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.squareup.picasso.Picasso;
import io.reactivex.Observer;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import org.jetbrains.annotations.NotNull;

@InjectViewState
public class FavoritePresenter extends MvpPresenter<FavoriteView> implements
        com.fanok.audiobooks.interface_pacatge.favorite.FavoritePresenter {

    public static final int NOT_SAVED = -1;

    public static final int SAVED = 0;

    public static final int SAVED_ALL = 1;


    private ArrayList<BookPOJO> books;

    private ArrayList<BookPOJO> flter = null;

    private AudioDBModel mAudioDBModel;

    private BooksDBModel mBooksDBModel;

    private FavoriteModel mFavoriteModel;

    private final ArrayList<BookPOJO> filterSearch;

    private final int table;

    private boolean isLoading = false;

    private String mQuery = "";

    private Context mContext;

    private boolean firstOpen;

    private AudioListDBModel mAudioListDBModel;

    private boolean isFilterSaved;


    public FavoritePresenter(int table) {
        filterSearch = new ArrayList<>();
        firstOpen = true;
        this.table = table;
    }


    @Override
    public void loadBooks() {
        if (!isLoading) {
            getViewState().showProgres(true);
            isLoading = true;
            filterSearch.clear();
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
                            if (flter == null) {
                                if (firstOpen) {
                                    firstOpen = false;
                                    if(mContext!=null) {
                                        if(table != Consts.TABLE_HISTORY) {
                                            SharedPreferences pref = PreferenceManager
                                                    .getDefaultSharedPreferences(mContext);
                                            String sort = pref.getString("pref_sort_favorite",
                                                    mContext.getString(R.string.sort_value_date));
                                            String sortSaved = mContext.getString(R.string.sort_value_saved);
                                            if (table == Consts.TABLE_SAVED && sortSaved.equals(sort)) {
                                                sort = mContext.getString(R.string.sort_value_date);
                                            }
                                            Comparator<BookPOJO> comparator = getComparator(sort);
                                            if (!sort.equals(
                                                    mContext.getString(R.string.sort_value_date))) {
                                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                                                    books.sort(comparator);
                                                } else {
                                                    Collections.sort(books, comparator);
                                                }
                                            }
                                        }
                                    }
                                }
                                getViewState().showData(books);
                            } else {
                                ArrayList<BookPOJO> newFilter = new ArrayList<>();
                                for (BookPOJO bookPOJO : books) {
                                    for (BookPOJO filterPojo : flter) {
                                        if (filterPojo.getUrl().equals(bookPOJO.getUrl())) {
                                            newFilter.add(bookPOJO);
                                        }
                                    }
                                }
                                flter = newFilter;
                                getViewState().showData(flter);
                            }
                            getViewState().showProgres(false);
                            getViewState().updateFilter();
                            isLoading = false;
                        }
                    });

        }
    }
    @Override
    public void onCreate(Context context) {
        mContext = context;
        mBooksDBModel = new BooksDBModel(context);
        mAudioDBModel = new AudioDBModel(context);
        mFavoriteModel = new FavoriteModel(context);
    }


    @Override
    public void onDestroy() {
        mAudioDBModel.closeDB();
        mBooksDBModel.closeDB();
        mFavoriteModel.closeDB();
        if (mAudioListDBModel != null) {
            mAudioListDBModel.closeDB();
        }
        mContext = null;
    }

    @Override
    public void onBookItemClick(View view, int position) {
        if (filterSearch.isEmpty()) {
            getViewState().showBooksActivity(books.get(position));
        } else {
            getViewState().showBooksActivity(filterSearch.get(position));
        }
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
        BookPOJO book;
        if (filterSearch.isEmpty()) {
            book = books.get(position);
        } else {
            book = filterSearch.get(position);
        }

        ImageView imageView = layout.findViewById(R.id.imageView);
        Picasso.get()
                .load(book.getPhoto())
                .error(R.drawable.image_placeholder)
                .placeholder(R.drawable.image_placeholder)
                .into(imageView);

        TextView title = layout.findViewById(R.id.title);
        title.setText(book.getName());

        TextView authorName = layout.findViewById(R.id.authorName);
        if (book.getAutor() != null) {
            authorName.setText(book.getAutor());
            authorName.setVisibility(View.VISIBLE);
        } else {
            authorName.setVisibility(View.GONE);
        }

        if (book.getSeries() == null || book.getUrlSeries() == null) {
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
            MyInterstitialAd.increase();
            getViewState().showBooksActivity(book);
        });

        remove.setOnClickListener(view1 -> {
            dialog.dismiss();
            onRemove(position);
        });

        if (book.getUrlGenre() != null) {
            genre.setVisibility(View.VISIBLE);
            genre.setOnClickListener(view1 -> {
                dialog.dismiss();
                getViewState().showFragment(BooksFragment.newInstance(
                        book.getUrlGenre(),
                        R.string.menu_audiobooks,
                        book.getGenre(), Consts.MODEL_BOOKS),
                        "genreBooks");
            });
        } else {
            genre.setVisibility(View.GONE);
        }

        if (book.getUrlAutor() != null) {
            author.setVisibility(View.VISIBLE);
            author.setOnClickListener(view1 -> {
                dialog.dismiss();
                if (!book.getUrlAutor().isEmpty()) {
                    getViewState().showFragment(BooksFragment.newInstance(
                            book.getUrlAutor(),
                            R.string.menu_audiobooks,
                            book.getAutor(), Consts.MODEL_BOOKS),
                            "autorBooks");
                }
            });
        } else {
            author.setVisibility(View.GONE);
        }

        if (book.getUrlArtist() != null) {
            artist.setVisibility(View.VISIBLE);
            artist.setOnClickListener(view1 -> {
                dialog.dismiss();
                getViewState().showFragment(BooksFragment.newInstance(
                        book.getUrlArtist(),
                        R.string.menu_audiobooks,
                        book.getArtist(), Consts.MODEL_BOOKS),
                        "artistBooks");
            });
        } else {
            artist.setVisibility(View.GONE);
        }

        series.setOnClickListener(view12 -> {
            dialog.dismiss();
            getViewState().showFragment(BooksFragment.newInstance(
                    book.getUrlSeries() + "?page=",
                    R.string.menu_audiobooks,
                    book.getSeries(), Consts.MODEL_BOOKS),
                    "seriesBooks");
        });
        dialog.show();
    }

    @Override
    public void onRemove(int position) {
        BookPOJO book;
        if (filterSearch.isEmpty()) {
            book = books.get(position);
        } else {
            book = filterSearch.get(position);
        }

        if (table == Consts.TABLE_FAVORITE) {
            mBooksDBModel.removeFavorite(book);
        } else if (table == Consts.TABLE_HISTORY) {
            mBooksDBModel.removeHistory(book);
            mAudioDBModel.remove(book.getUrl());
        } else if (table == Consts.TABLE_SAVED){
            mBooksDBModel.removeSaved(book);
            File[] folders = mContext.getExternalFilesDirs(null);
            for (File folder : folders) {
                if (folder != null) {
                    String source = Consts.getSorceName(mContext, book.getUrl());
                    String filePath = folder.getAbsolutePath() + "/" + source
                            + "/" + book.getAutor()
                            + "/" + book.getArtist()
                            + "/" + book.getName();
                    File dir = new File(filePath);
                    PopupClearSaved.delete(dir);
                }
            }
            for (final File filesDir : folders) {
                if (filesDir != null) {
                    File file = new File(filesDir.getAbsolutePath());
                    PopupClearSaved.deleteEmtyFolder(file);
                }
            }

        }
        if (filterSearch.isEmpty()) {
            books.remove(position);
            getViewState().showData(books);
        } else {
            books.remove(filterSearch.get(position));
            filterSearch.remove(position);
            getViewState().showData(filterSearch);
        }
    }

    @Override
    public void onSearch(String qery) {
        mQuery = qery;
        filterSearch.clear();
        if (flter != null) {
            for (BookPOJO book : flter) {
                if (book.getName().toLowerCase().contains(qery.toLowerCase())) {
                    filterSearch.add(book);
                }
            }
        } else if (books != null && books.size() > 0) {
            for (BookPOJO book : books) {
                if (book.getName().toLowerCase().contains(qery.toLowerCase())) {
                    filterSearch.add(book);
                }
            }
        }
        getViewState().showData(filterSearch);
    }

    @Override
    public void cealrData() {
        if (books != null) {
            books.clear();
            getViewState().showData(books);
        }
    }

    @Override
    public void onOptionsItemSelected(@NotNull View view, int id) {
        if (books != null && id != R.id.filter && id != R.id.order) {
            if (id == R.id.genre_filter || id == R.id.autor_filter ||
                    id == R.id.artist_filter || id == R.id.series_filter) {
                showPopupMenu(view, id);
            } else if (id == R.id.date) {
                loadBooks();
            } else if (id == R.id.saved_filter) {
                if (!isFilterSaved) {
                    File[] folders = mContext.getExternalFilesDirs(null);
                    flter = new ArrayList<>();
                    if (mAudioListDBModel == null) {
                        mAudioListDBModel = new AudioListDBModel(mContext);
                    }
                    for (BookPOJO bookPOJO : books) {
                        ArrayList<AudioListPOJO> arrayList = mAudioListDBModel.get(
                                bookPOJO.getUrl());
                        for (File folder : folders) {
                            if (folder != null) {
                                String source = Consts.getSorceName(mContext, bookPOJO.getUrl());
                                String filePath = folder.getAbsolutePath() + "/" + source
                                        + "/" + bookPOJO.getAutor()
                                        + "/" + bookPOJO.getArtist()
                                        + "/" + bookPOJO.getName();
                                File dir = new File(filePath);
                                if (dir.exists() && dir.isDirectory()) {
                                    for (AudioListPOJO pojo : arrayList) {
                                        String url = pojo.getAudioUrl();
                                        File file = new File(dir,
                                                url.substring(url.lastIndexOf("/") + 1));
                                        if (file.exists()) {
                                            flter.add(bookPOJO);
                                            break;
                                        }
                                    }
                                }
                            }
                        }
                    }
                    getViewState().setSubTitle(mContext.getString(R.string.menu_saved));
                } else {
                    getViewState().setSubTitle("");
                    flter = null;
                }
                isFilterSaved = !isFilterSaved;
                onSearch(mQuery);
            } else {
                Comparator<BookPOJO> comparator = getComparator(id);
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
                onSearch(mQuery);
            }

        }

    }

    private void showPopupMenu(@NotNull View view, int id) {
        ArrayList<String> arrayList;

        if (id == R.id.genre_filter) {
            arrayList = mBooksDBModel.getGenre(table);
        } else if (id == R.id.autor_filter){
            arrayList = mBooksDBModel.getAutors(table);
        } else if (id == R.id.artist_filter) {
            arrayList = mBooksDBModel.getArtists(table);
        } else if (id == R.id.series_filter) {
            arrayList = mBooksDBModel.getSeries(table);
        } else {
            arrayList = new ArrayList<>();
        }

        if (arrayList.isEmpty()) {
            return;
        } else {
            arrayList.add(0, "Все");
        }

        PopupMenu popupMenu = new PopupMenu(view.getContext(), view, Gravity.END);
        for (int i = 0; i < arrayList.size(); i++) {
            popupMenu.getMenu().add(1, i, 0, arrayList.get(i));
        }
        popupMenu.setOnMenuItemClickListener(item -> {
            if (books != null) {
                if (item.getTitle().equals("Все")) {
                    getViewState().setSubTitle("");
                    this.flter = null;
                    onSearch(mQuery);
                    return false;
                } else {
                    getViewState().setSubTitle(item.getTitle().toString());
                }
                ArrayList<BookPOJO> filter = new ArrayList<>();
                for (BookPOJO book : books) {
                    String text;
                    if (id == R.id.genre_filter) {
                        text = book.getGenre();
                    } else if (id == R.id.autor_filter) {
                        text = book.getAutor();
                    } else if (id == R.id.artist_filter) {
                        text = book.getArtist();
                    } else if (id == R.id.series_filter) {
                        text = book.getSeries();
                    } else {
                        return false;
                    }
                    if (item.getTitle().equals(text)) {
                        filter.add(book);
                    }
                }
                this.flter = filter;
                onSearch(mQuery);
            }
            return false;
        });
        popupMenu.show();

    }

    private Comparator<BookPOJO> getComparator(@NotNull String sort) {
        if (mContext.getString(R.string.sort_value_name).equals(sort)) {
            return getComparator(R.id.name);
        } else if (mContext.getString(R.string.sort_value_genre).equals(sort)) {
            return getComparator(R.id.genre);
        } else if (mContext.getString(R.string.sort_value_autor).equals(sort)) {
            return getComparator(R.id.autor);
        } else if (mContext.getString(R.string.sort_value_artist).equals(sort)) {
            return getComparator(R.id.artist);
        } else if (mContext.getString(R.string.sort_value_series).equals(sort)) {
            return getComparator(R.id.series);
        } else if (mContext.getString(R.string.sort_value_saved).equals(sort)) {
            return getComparator(R.id.saved);
        } else {
            return getComparator(R.id.date);
        }
    }



    private Comparator<BookPOJO> getComparator(int sort) {
        if (sort == R.id.name) {
            return (bookPOJO, t1) -> {
                if ((bookPOJO.getName() == null || bookPOJO.getName().isEmpty()) && (
                        t1.getName() == null || t1.getName().isEmpty())) {
                    return 0;
                } else if ((bookPOJO.getName() != null && !bookPOJO.getName().isEmpty()) && (
                        t1.getName() != null && !t1.getName().isEmpty())) {
                    return bookPOJO.getName().compareTo(t1.getName());
                } else if ((bookPOJO.getName() != null && !bookPOJO.getName().isEmpty()) &&
                        (t1.getName() == null || t1.getName().isEmpty())) {
                    return -11;
                } else if ((bookPOJO.getName() == null || bookPOJO.getName().isEmpty()) &&
                        (t1.getName() != null && !t1.getName().isEmpty())) {
                    return 11;
                } else {
                    return 0;
                }
            };
        } else if (sort == R.id.genre) {
            return (bookPOJO, t1) -> {
                if ((bookPOJO.getGenre() == null || bookPOJO.getGenre().isEmpty()) && (
                        t1.getGenre() == null || t1.getGenre().isEmpty())) {
                    return 0;
                } else if ((bookPOJO.getGenre() != null && !bookPOJO.getGenre().isEmpty()) && (
                        t1.getGenre() != null && !t1.getGenre().isEmpty())) {
                    return bookPOJO.getGenre().compareTo(t1.getGenre());
                } else if ((bookPOJO.getGenre() != null && !bookPOJO.getGenre().isEmpty()) &&
                        (t1.getGenre() == null || t1.getGenre().isEmpty())) {
                    return -11;
                } else if ((bookPOJO.getGenre() == null || bookPOJO.getGenre().isEmpty()) &&
                        (t1.getGenre() != null && !t1.getGenre().isEmpty())) {
                    return 11;
                } else {
                    return 0;
                }

            };
        } else if (sort == R.id.autor) {
            return (bookPOJO, t1) -> {
                if ((bookPOJO.getAutor() == null || bookPOJO.getAutor().isEmpty()) && (
                        t1.getAutor() == null || t1.getAutor().isEmpty())) {
                    return 0;
                } else if ((bookPOJO.getAutor() != null && !bookPOJO.getAutor().isEmpty()) && (
                        t1.getAutor() != null && !t1.getAutor().isEmpty())) {
                    return bookPOJO.getAutor().compareTo(t1.getAutor());
                } else if ((bookPOJO.getAutor() != null && !bookPOJO.getAutor().isEmpty()) &&
                        (t1.getAutor() == null || t1.getAutor().isEmpty())) {
                    return -11;
                } else if ((bookPOJO.getAutor() == null || bookPOJO.getAutor().isEmpty()) &&
                        (t1.getAutor() != null && !t1.getAutor().isEmpty())) {
                    return 11;
                } else {
                    return 0;
                }
            };
        } else if (sort == R.id.artist) {
            return (bookPOJO, t1) -> {
                if ((bookPOJO.getArtist() == null || bookPOJO.getArtist().isEmpty()) && (
                        t1.getArtist() == null || t1.getArtist().isEmpty())) {
                    return 0;
                } else if ((bookPOJO.getArtist() != null && !bookPOJO.getArtist().isEmpty()) && (
                        t1.getArtist() != null && !t1.getArtist().isEmpty())) {
                    return bookPOJO.getArtist().compareTo(t1.getArtist());
                } else if ((bookPOJO.getArtist() != null && !bookPOJO.getArtist().isEmpty()) &&
                        (t1.getArtist() == null || t1.getArtist().isEmpty())) {
                    return -11;
                } else if ((bookPOJO.getArtist() == null || bookPOJO.getArtist().isEmpty()) &&
                        (t1.getArtist() != null && !t1.getArtist().isEmpty())) {
                    return 11;
                } else {
                    return 0;
                }
            };
        } else if (sort == R.id.series) {
            return (bookPOJO, t1) -> {
                if ((bookPOJO.getSeries() == null || bookPOJO.getSeries().isEmpty()) && (
                        t1.getSeries() == null || t1.getSeries().isEmpty())) {
                    return 0;
                } else if ((bookPOJO.getSeries() != null && !bookPOJO.getSeries().isEmpty()) && (
                        t1.getSeries() != null && !t1.getSeries().isEmpty())) {
                    return bookPOJO.getSeries().compareTo(t1.getSeries());
                } else if ((bookPOJO.getSeries() != null && !bookPOJO.getSeries().isEmpty()) &&
                        (t1.getSeries() == null || t1.getSeries().isEmpty())) {
                    return -11;
                } else if ((bookPOJO.getSeries() == null || bookPOJO.getSeries().isEmpty()) &&
                        (t1.getSeries() != null && !t1.getSeries().isEmpty())) {
                    return 11;
                } else {
                    return 0;
                }
            };
        } else if (sort == R.id.saved) {

            return (bookPOJO, t1) -> {
                int number1 = getSavedAudioNumber(bookPOJO);
                int number2 = getSavedAudioNumber(t1);
                if (number1 > number2) {
                    return -11;
                } else if (number1 < number2) {
                    return 11;
                } else {
                    return 0;
                }
            };

        } else {
            return (bookPOJO, t1) -> 0;
        }
    }

    private int getSavedAudioNumber(BookPOJO book) {
        if (mAudioListDBModel == null) {
            mAudioListDBModel = new AudioListDBModel(mContext);
        }
        int size = 0;
        File[] folders = mContext.getExternalFilesDirs(null);
        ArrayList<AudioListPOJO> arrayList = mAudioListDBModel.get(book.getUrl());
        for (File folder : folders) {
            if (folder != null) {
                String source = Consts.getSorceName(mContext, book.getUrl());
                String filePath = folder.getAbsolutePath() + "/" + source
                        + "/" + book.getAutor()
                        + "/" + book.getArtist()
                        + "/" + book.getName();
                File dir = new File(filePath);
                if (dir.exists() && dir.isDirectory()) {
                    for (AudioListPOJO pojo : arrayList) {
                        String url = pojo.getAudioUrl();
                        File file = new File(dir, url.substring(url.lastIndexOf("/") + 1));
                        if (file.exists()) {
                            size++;
                        }
                    }
                }
            }
        }
        if (size == 0) {
            return NOT_SAVED;
        } else if (size >= arrayList.size()) {
            return SAVED_ALL;
        } else {
            return SAVED;
        }
    }

}