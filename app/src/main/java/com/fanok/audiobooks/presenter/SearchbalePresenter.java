package com.fanok.audiobooks.presenter;


import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;
import com.arellomobile.mvp.InjectViewState;
import com.arellomobile.mvp.MvpPresenter;
import com.fanok.audiobooks.Consts;
import com.fanok.audiobooks.CookesExeption;
import com.fanok.audiobooks.R;
import com.fanok.audiobooks.Url;
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
import com.fanok.audiobooks.pojo.SearchebleArrayPOJO;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.squareup.picasso.Picasso;
import io.reactivex.Observer;
import io.reactivex.Scheduler;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;

@InjectViewState
public class SearchbalePresenter extends MvpPresenter<SearchableView> implements
        SearchablePresenter {

    private static final String TAG = "SearchablePresenter";

    private boolean isLoading = false;

    private boolean isLoadAutors = false;

    private int page = 0;

    private final BooksDBModel mBooksDBModel;

    private BooksModel mBooksModel;

    private ArrayList<BookPOJO> books;

    private ArrayList<BookPOJO> books_filter;

    private ArrayList<GenrePOJO> genre;

    private final int mModelId;

    private com.fanok.audiobooks.interface_pacatge.books.GenreModel mModelGenre;

    private SearchableModel mSearchableModel;

    private boolean isEnd;

    private int filter = -1;

    private SearcheblPOJO mSearcheblPOJOFilter;

    private SearcheblPOJO mSearcheblPOJO;

    private ArrayList<String> mUrls;

    private String query;

    private final SharedPreferences mPreferences;

    private final Hashtable<String, Boolean> hesSearchable;

    private int exit = 0;


    private String mUrl;

    public SearchbalePresenter(int modelId, @NonNull Context context) {

        mPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        hesSearchable = new Hashtable<>();

        mModelId = modelId;
        switch (modelId) {
            case Consts.MODEL_BOOKS:
                mUrls = new ArrayList<>();
                mUrls.add(Url.SERVER_AKNIGA + "/search/books/page<page>/?q=<qery>");
                mUrls.add(Url.SERVER + "/search/?q=<qery>&page=<page>");
                mUrls.add(Url.SERVER_IZIBUK + "/search?q=<qery>&p=<page>");
                mUrls.add(Url.SERVER_ABMP3 + "/search?text=<qery>");
                mUrls.add(Url.SERVER_BAZA_KNIG + "/index.php?do=search///qery=<qery>&page=<page>");
                mUrls.add(Url.SERVER_KNIGOBLUD + "/search?q=<qery>&page=<page>");
                mUrls.add(Url.SERVER_BOOKOOF + "/index.php?do=search&q=<qery>&page=<page>");
                break;
            case Consts.MODEL_AUTOR:
                if (Consts.SOURCE_KNIGA_V_UHE == Consts.getSOURCE()) {
                    mUrl = Url.SERVER + "/search/authors/?q=<qery>&page=<page>";
                } else if (Consts.SOURCE_AUDIO_BOOK_MP3 == Consts.getSOURCE()) {
                    mUrl = Url.SERVER_ABMP3 + "/search?text=<qery>";
                } else if (Consts.SOURCE_ABOOK == Consts.getSOURCE()) {
                    mUrl = Url.SERVER_AKNIGA + "/authors/ajax-search/";
                } else {
                    mUrl = Url.SERVER_IZIBUK + "/authors?p=<page>&q=<qery>";
                }
                break;
            case Consts.MODEL_ARTIST:
                if (Consts.SOURCE_KNIGA_V_UHE == Consts.getSOURCE()) {
                    mUrl = Url.SERVER + "/search/readers/?q=<qery>&page=<page>";
                } else if (Consts.SOURCE_ABOOK == Consts.getSOURCE()) {
                    mUrl = Url.SERVER_AKNIGA + "/performers/ajax-search/";
                } else {
                    mUrl = Url.SERVER_IZIBUK + "/readers?p=<page>&q=<qery>";
                }
                break;
        }
        isEnd = false;
        mBooksDBModel = new BooksDBModel(context);
        switch (mModelId) {
            case Consts.MODEL_BOOKS:
                books = new ArrayList<>();
                mSearcheblPOJO = new SearcheblPOJO();
                mSearchableModel = new SearchebleModel();
                mBooksModel = new BooksModel();
                break;
            case Consts.MODEL_AUTOR:
            case Consts.MODEL_ARTIST:
                genre = new ArrayList<>();
                mModelGenre = new AutorsModel();
                getViewState().showSeriesAndAutors(null);
                break;
        }
    }

    public void setQuery(@NonNull String query) {
        this.query = query;
    }

    public void setFilter(int filter) {
        this.filter = filter;
    }

    @Override
    public void onDestroy() {
        mBooksDBModel.closeDB();
    }

    @Override
    public void filterAutorsAndSeries() {
        if (filter == -1) {
            mSearcheblPOJOFilter = null;
            getViewState().showSeriesAndAutors(mSearcheblPOJO);
        } else {
            if (filter == Consts.SOURCE_KNIGA_V_UHE) {
                setAutorsAndSeriesFilter("knigavuhe.org");
            } else if (filter == Consts.SOURCE_IZI_BUK) {
                mSearcheblPOJOFilter = new SearcheblPOJO();
            } else if (filter == Consts.SOURCE_AUDIO_BOOK_MP3) {
                setAutorsAndSeriesFilter("audiobook-mp3.com");
            } else if (filter == Consts.SOURCE_ABOOK) {
                mSearcheblPOJOFilter = new SearcheblPOJO();
            } else if (filter == Consts.SOURCE_BAZA_KNIG) {
                mSearcheblPOJOFilter = new SearcheblPOJO();
            }else if (filter == Consts.SOURCE_KNIGOBLUD) {
                mSearcheblPOJOFilter = new SearcheblPOJO();
            }else if (filter == Consts.SOURCE_BOOKOOF) {
                mSearcheblPOJOFilter = new SearcheblPOJO();
            }
            getViewState().showSeriesAndAutors(mSearcheblPOJOFilter);
        }
    }

    @Override
    public void filterBooks() {
        if (filter == -1) {
            books_filter = null;
            getViewState().showDataBooks(books);
        } else if (filter == Consts.SOURCE_KNIGA_V_UHE) {
            setBooksFilter("knigavuhe.org");
            getViewState().showDataBooks(books_filter);
        } else if (filter == Consts.SOURCE_IZI_BUK) {
            setBooksFilter("izibuk.ru");
            getViewState().showDataBooks(books_filter);
        } else if (filter == Consts.SOURCE_AUDIO_BOOK_MP3) {
            setBooksFilter("audiobook-mp3.com");
            getViewState().showDataBooks(books_filter);
        } else if (filter == Consts.SOURCE_ABOOK) {
            setBooksFilter("akniga.org");
            getViewState().showDataBooks(books_filter);
        } else if (filter == Consts.SOURCE_BAZA_KNIG) {
            setBooksFilter("baza-knig.ink");
            getViewState().showDataBooks(books_filter);
        }else if (filter == Consts.SOURCE_KNIGOBLUD) {
            setBooksFilter("knigoblud.club");
            getViewState().showDataBooks(books_filter);
        }else if (filter == Consts.SOURCE_BOOKOOF) {
            setBooksFilter("bookoof.net");
            getViewState().showDataBooks(books_filter);
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
        if (books_filter == null) {
            getViewState().startBookActivity(books.get(position));
        } else {
            getViewState().startBookActivity(books_filter.get(position));
        }
    }

    @Override
    public void loadBoks() {
        if (query != null) {
            if (books != null) {
                books.clear();
                getViewState().showDataBooks(books);
            }
            if (books_filter != null) {
                books_filter = null;
            }
            if (genre != null) {
                genre.clear();
                getViewState().showDataGenres(genre);
            }

            mSearcheblPOJO = new SearcheblPOJO();
            if (mSearcheblPOJOFilter != null) {
                mSearcheblPOJOFilter = null;
            }
            getViewState().showSeriesAndAutors(mSearcheblPOJO);

            if (mModelId == Consts.MODEL_BOOKS) {
                getViewState().showProgresTop(true);
                getDataSeriesAndAutors(mUrls, query);
            }

            isEnd = false;

            hesSearchable.clear();
            hesSearchable.put(Url.SERVER, mPreferences.getBoolean("search_kniga_v_uhe", true));
            hesSearchable.put(Url.SERVER_IZIBUK, mPreferences.getBoolean("search_izibuc", true));
            hesSearchable.put(Url.SERVER_ABMP3, mPreferences.getBoolean("search_abmp3", true));
            hesSearchable.put(Url.SERVER_AKNIGA, mPreferences.getBoolean("search_abook", true));
            hesSearchable.put(Url.SERVER_BAZA_KNIG, mPreferences.getBoolean("search_baza_knig", true));
            hesSearchable.put(Url.SERVER_KNIGOBLUD, mPreferences.getBoolean("search_knigoblud", true));
            hesSearchable.put(Url.SERVER_BOOKOOF, mPreferences.getBoolean("search_bookoof", true));

            page = 0;
            loadNext();
        }
    }

    @Override
    public void loadNext() {
        if (query != null) {
            if (!isEnd && !isLoading) {
                getViewState().showProgres(true);
                page++;
                if (mUrls != null && !mUrls.isEmpty()) {
                    ArrayList<String> temp = new ArrayList<>();
                    for (String url : mUrls) {
                        temp.add(url.replace("<qery>", query).replace("<page>",
                                String.valueOf(page)));
                    }
                    getData(temp);
                } else if (mUrl != null) {
                    if (mUrl.contains(Url.SERVER_AKNIGA)) {
                        getData(mUrl + "?q=" + query);
                    } else {
                        getData(mUrl.replace("<qery>", query).replace("<page>", String.valueOf(page)));
                    }
                }
            }
        }
    }

    @Override
    public void onAutorsListItemClick(View view, int position) {
        SearchebleArrayPOJO arrayPOJO;
        if (mSearcheblPOJOFilter == null) {
            arrayPOJO = mSearcheblPOJO.getAutorsList().get(position);
        } else {
            arrayPOJO = mSearcheblPOJOFilter.getAutorsList().get(position);
        }

        getViewState().returnResult(arrayPOJO.getUrl(),
                arrayPOJO.getName(), Consts.MODEL_BOOKS,
                "searchebleBooks");
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

        BookPOJO bookPOJO;
        if (books_filter == null) {
            bookPOJO = books.get(position);
        } else {
            bookPOJO = books_filter.get(position);
        }

        ImageView imageView = layout.findViewById(R.id.imageView);
        Picasso.get()
                .load(bookPOJO.getPhoto())
                .error(android.R.drawable.ic_menu_gallery)
                .placeholder(android.R.drawable.ic_menu_gallery)
                .into(imageView);

        TextView title = layout.findViewById(R.id.title);
        title.setText(bookPOJO.getName());

        TextView authorName = layout.findViewById(R.id.authorName);
        if (bookPOJO.getAutor() != null) {
            authorName.setText(bookPOJO.getAutor());
            authorName.setVisibility(View.VISIBLE);
        } else {
            authorName.setVisibility(View.GONE);
        }

        if (bookPOJO.getSeries() == null || bookPOJO.getUrlSeries() == null) {
            series.setVisibility(View.GONE);
        } else {
            series.setVisibility(View.VISIBLE);
        }

        if (mBooksDBModel.inFavorite(bookPOJO)) {
            add.setVisibility(View.GONE);
            remove.setVisibility(View.VISIBLE);
        } else {
            add.setVisibility(View.VISIBLE);
            remove.setVisibility(View.GONE);
        }

        open.setOnClickListener(view1 -> {
            dialog.dismiss();
            getViewState().startBookActivity(bookPOJO);
        });

        add.setOnClickListener(view1 -> {
            dialog.dismiss();
            mBooksDBModel.addFavorite(bookPOJO);
        });

        remove.setOnClickListener(view1 -> {
            dialog.dismiss();
            mBooksDBModel.removeFavorite(bookPOJO);
        });

        if (bookPOJO.getUrlGenre() != null) {
            genre.setVisibility(View.VISIBLE);
            genre.setOnClickListener(view1 -> {
                dialog.dismiss();
                getViewState().returnResult(bookPOJO.getUrlGenre(),
                        bookPOJO.getGenre(), Consts.MODEL_BOOKS, "searchebleBooks");
            });
        } else {
            genre.setVisibility(View.GONE);
        }

        if (bookPOJO.getUrlAutor() != null) {
            author.setVisibility(View.VISIBLE);
            author.setOnClickListener(view1 -> {
                dialog.dismiss();
                getViewState().returnResult(bookPOJO.getUrlAutor(),
                        bookPOJO.getAutor(), Consts.MODEL_BOOKS, "searchebleBooks");
            });
        } else {
            author.setVisibility(View.GONE);
        }

        if (bookPOJO.getUrlArtist() != null) {
            artist.setVisibility(View.VISIBLE);
            artist.setOnClickListener(view1 -> {
                dialog.dismiss();
                getViewState().returnResult(bookPOJO.getUrlArtist(),
                        bookPOJO.getArtist(), Consts.MODEL_BOOKS, "searchebleBooks");
            });
        } else {
            artist.setVisibility(View.GONE);
        }

        series.setOnClickListener(view12 -> {
            dialog.dismiss();
            getViewState().returnResult(bookPOJO.getUrlSeries(),
                    bookPOJO.getSeries(), Consts.MODEL_BOOKS, "searchebleBooks");
        });
        dialog.show();
    }

    @Override
    public void onSeriesListItemClick(View view, int position) {
        SearchebleArrayPOJO arrayPOJO;
        if (mSearcheblPOJOFilter == null) {
            arrayPOJO = mSearcheblPOJO.getSeriesList().get(position);
        } else {
            arrayPOJO = mSearcheblPOJOFilter.getSeriesList().get(position);
        }

        getViewState().returnResult(
                arrayPOJO.getUrl(),
                arrayPOJO.getName(), Consts.MODEL_BOOKS,
                "searchebleBooks");
    }

    private void execute(boolean enabled, String url, boolean speedUp, Observer<ArrayList<BookPOJO>> observer,
            Scheduler scheduler) {
        if (!enabled) {
            exit++;
        } else {
            mBooksModel.getBooks(url, page, speedUp)
                    .subscribeOn(scheduler)
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(observer);
        }
    }

    private void getData(ArrayList<String> url) {
        exit = 0;
        Observer<ArrayList<BookPOJO>> observer = new Observer<ArrayList<BookPOJO>>() {
            @Override
            public void onComplete() {
                exit++;
                Log.d(TAG, "onComplete");
                if (exit == url.size()) {
                    books = sort(url, books); //при добавлении источников изменять не надо
                    filterBooks();
                    getViewState().showProgres(false);
                    isLoading = false;
                    getViewState().setNotFoundVisibile(
                            isLoadAutors && mSearcheblPOJO.getAutorsList().isEmpty() &&
                                    mSearcheblPOJO.getSeriesList().isEmpty() &&
                                    books.isEmpty());
                }
                boolean flag = false;
                for (Hashtable.Entry<String, Boolean> entry : hesSearchable.entrySet()) {
                    Boolean value = entry.getValue();
                    if (value) {
                        flag = true;
                        break;
                    }
                }
                if (!flag) {
                    isEnd = true;
                }
            }

            @Override
            public void onError(@NotNull Throwable e) {
                if (e.getClass() == CookesExeption.class) {
                    if (Objects.requireNonNull(e.getMessage()).contains(Url.SERVER_BAZA_KNIG)) {
                        getViewState().showToast(R.string.cookes_baza_knig_exeption);
                    }
                } else if (e.getClass() == NullPointerException.class) {
                    String url = e.getMessage();
                    if (url != null) {
                        for (Hashtable.Entry<String, Boolean> entry : hesSearchable.entrySet()) {
                            String key = entry.getKey();
                            if (url.contains(key)) {
                                entry.setValue(false);
                            }
                        }
                    }
                }
                onComplete();
            }

            @Override
            public void onNext(@NotNull ArrayList<BookPOJO> bookPOJOS) {
                books.addAll(bookPOJOS);
            }

            @Override
            public void onSubscribe(@NotNull Disposable d) {
            }
        };

        if (!isLoading) {
            isLoading = true;
            Scheduler scheduler;
            if (mPreferences.getBoolean("search_multithreading", true)) {
                scheduler = Schedulers.io();
            } else {
                scheduler = Schedulers.single();
            }

            for (Hashtable.Entry<String, Boolean> entry : hesSearchable.entrySet()) {
                String key = entry.getKey();
                Boolean value = entry.getValue();
                for (int i = 0; i < url.size(); i++) {
                    String s = url.get(i);
                    if (s.contains(key)) {
                        if (key.equals(Url.SERVER_ABMP3)) {
                            boolean speed = mPreferences.getBoolean("speed_up_search_abmp3", false);
                            execute(value, s, speed, observer, scheduler);
                        } else {
                            execute(value, s, false, observer, scheduler);
                        }
                    }
                }

            }
        }


    }

    private ArrayList<BookPOJO> sort(ArrayList<String> urls, ArrayList<BookPOJO> array) {
        //при добавлении источников изменять не надо
        ArrayList<BookPOJO> result = new ArrayList<>();
        while (!array.isEmpty()) {
            for (String url : urls) {
                String text = url.replace("https://", "");
                text = text.replace("http://", "");
                text = text.substring(0, text.indexOf("/"));
                for (BookPOJO bookPOJO : array) {
                    if (bookPOJO.getUrl().contains(text)) {
                        result.add(bookPOJO);
                        array.remove(bookPOJO);
                        break;
                    }
                }
            }
        }
        return result;
    }

    private void getData(String url) {
        if (!isLoading) {
            isLoading = true;
            mModelGenre.getBooks(url, page)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(new Observer<ArrayList<GenrePOJO>>() {
                        @Override
                        public void onError(@NotNull Throwable e) {
                            if (e.getClass() == NullPointerException.class) {
                                isEnd = true;
                            } else {
                                getViewState().showToast(R.string.error_load_data);
                            }
                            onComplete();

                        }

                        @Override
                        public void onNext(@NotNull ArrayList<GenrePOJO> bookPOJOS) {
                            genre.addAll(bookPOJOS);
                        }

                        @Override
                        public void onSubscribe(@NotNull Disposable d) {
                        }

                        @Override
                        public void onComplete() {
                            Log.d(TAG, "onComplete");
                            getViewState().showDataGenres(genre);
                            getViewState().showProgres(false);
                            isLoading = false;
                            getViewState().setNotFoundVisibile(genre.isEmpty());
                        }
                    });
        }
    }

    private void getDataSeriesAndAutors(ArrayList<String> urls, String query) {
        mSearchableModel.dowland(mPreferences, urls, query)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Observer<SearcheblPOJO>() {
                    @Override
                    public void onComplete() {
                        Log.d(TAG, "onComplete");
                        filterAutorsAndSeries();
                        getViewState().showProgresTop(false);
                        isLoadAutors = true;
                        getViewState().setNotFoundVisibile(
                                !isLoading && ((books != null && books.isEmpty()) || (genre != null
                                        && genre.isEmpty())) &&
                                        mSearcheblPOJO.getAutorsList().isEmpty()
                                        && mSearcheblPOJO.getSeriesList().isEmpty());
                    }

                    @Override
                    public void onError(@NotNull Throwable e) {
                        onComplete();
                    }

                    @Override
                    public void onNext(@NotNull SearcheblPOJO searcheblPOJO) {
                        mSearcheblPOJO = searcheblPOJO;
                    }

                    @Override
                    public void onSubscribe(@NotNull Disposable d) {
                    }
                });
    }

    private void setAutorsAndSeriesFilter(String src) {
        mSearcheblPOJOFilter = new SearcheblPOJO();
        ArrayList<SearchebleArrayPOJO> autorsList = new ArrayList<>();
        ArrayList<SearchebleArrayPOJO> seriesList = new ArrayList<>();
        for (SearchebleArrayPOJO arrayPOJO : mSearcheblPOJO.getAutorsList()) {
            if (arrayPOJO.getUrl().contains(src)) {
                autorsList.add(arrayPOJO);
            }
        }
        mSearcheblPOJOFilter.setAutorsList(autorsList);
        for (SearchebleArrayPOJO arrayPOJO : mSearcheblPOJO.getSeriesList()) {
            if (arrayPOJO.getUrl().contains(src)) {
                seriesList.add(arrayPOJO);
            }
        }
        mSearcheblPOJOFilter.setSeriesList(seriesList);
    }

    private void setBooksFilter(String src) {
        books_filter = new ArrayList<>();
        for (BookPOJO bookPOJO : books) {
            if (bookPOJO.getUrl().contains(src)) {
                books_filter.add(bookPOJO);
            }
        }
    }


}
