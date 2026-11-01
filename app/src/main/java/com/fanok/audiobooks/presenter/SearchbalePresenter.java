package com.fanok.audiobooks.presenter;


import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;
import com.arellomobile.mvp.InjectViewState;
import com.arellomobile.mvp.MvpPresenter;
import com.fanok.audiobooks.Consts;
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
import com.fanok.audiobooks.policy.DefaultCover;
import com.fanok.audiobooks.search.GenrePicks;
import com.fanok.audiobooks.search.Provider;
import com.fanok.audiobooks.search.ProviderPage;
import com.fanok.audiobooks.search.SearchSession;
import com.fanok.audiobooks.search.SearchUrls;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.squareup.picasso.Picasso;
import io.reactivex.Single;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@InjectViewState
public class SearchbalePresenter extends MvpPresenter<SearchableView> implements
        SearchablePresenter {

    /** A site's search may take this long; one that declares slow pages ({@code timeoutMs}) gets longer. */
    private static final long SOURCE_TIMEOUT_SECONDS = 20;

    private final int mModelId;
    private final SharedPreferences mPreferences;
    private final BooksDBModel mBooksDBModel;

    // MODEL_BOOKS: the multi-source book search plus the author/series chips
    private BooksModel mBooksModel;
    private SearchableModel mSearchableModel;
    private SearchSession<BookPOJO> mSession;
    private Disposable mChips;
    private boolean isLoadAutors = false;
    private ArrayList<BookPOJO> books;
    private ArrayList<BookPOJO> books_filter;
    private SearcheblPOJO mSearcheblPOJO;
    private SearcheblPOJO mSearcheblPOJOFilter;
    private int filter = -1;

    // MODEL_AUTOR / MODEL_ARTIST: one source, the current one
    private AutorsModel mModelGenre;
    private SearchSession<GenrePOJO> mGenreSession;
    private ArrayList<GenrePOJO> genre;

    private String query;
    /** A run was cancelled before it finished; the next attach starts it again when nothing was shown. */
    private boolean mInterrupted;

    public SearchbalePresenter(int modelId, @NonNull Context context) {
        mPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        mModelId = modelId;
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
        cancel();
        mBooksDBModel.closeDB();
    }

    /** Cancels every run and hides its progress in the same step, so no spinner stays stuck (SRCH-08). */
    private void cancel() {
        mInterrupted = (mSession != null && mSession.isRunning())
                || (mGenreSession != null && mGenreSession.isRunning())
                || (mChips != null && !mChips.isDisposed());
        if (mSession != null) {
            mSession.dispose();
        }
        if (mGenreSession != null) {
            mGenreSession.dispose();
        }
        if (mChips != null) {
            mChips.dispose();
        }
        getViewState().showProgres(false);
        getViewState().showProgresTop(false);
    }

    @Override
    public void attachView(SearchableView view) {
        super.attachView(view);
        if (mInterrupted && query != null
                && (mModelId == Consts.MODEL_BOOKS ? books.isEmpty() : genre == null || genre.isEmpty())) {
            mInterrupted = false;
            loadBoks();
        }
    }

    @Override
    public void filterAutorsAndSeries() {
        Provider provider = Provider.ofSource(filter);
        if (provider == null) {
            mSearcheblPOJOFilter = null;
            getViewState().showSeriesAndAutors(mSearcheblPOJO);
            return;
        }
        mSearcheblPOJOFilter = new SearcheblPOJO();
        ArrayList<SearchebleArrayPOJO> autorsList = new ArrayList<>();
        ArrayList<SearchebleArrayPOJO> seriesList = new ArrayList<>();
        for (SearchebleArrayPOJO arrayPOJO : mSearcheblPOJO.getAutorsList()) {
            if (Url.isHost(arrayPOJO.getUrl(), provider.server)) {
                autorsList.add(arrayPOJO);
            }
        }
        for (SearchebleArrayPOJO arrayPOJO : mSearcheblPOJO.getSeriesList()) {
            if (Url.isHost(arrayPOJO.getUrl(), provider.server)) {
                seriesList.add(arrayPOJO);
            }
        }
        mSearcheblPOJOFilter.setAutorsList(autorsList);
        mSearcheblPOJOFilter.setSeriesList(seriesList);
        getViewState().showSeriesAndAutors(mSearcheblPOJOFilter);
    }

    @Override
    public void filterBooks() {
        Provider provider = Provider.ofSource(filter);
        if (provider == null) {
            books_filter = null;
            getViewState().showDataBooks(books);
            return;
        }
        books_filter = new ArrayList<>();
        for (BookPOJO bookPOJO : books) {
            if (Url.isHost(bookPOJO.getUrl(), provider.server)) {
                books_filter.add(bookPOJO);
            }
        }
        getViewState().showDataBooks(books_filter);
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

    private BookPOJO bookAt(int position) {
        return books_filter == null ? books.get(position) : books_filter.get(position);
    }

    @Override
    public void onBookItemClick(View view, int position) {
        getViewState().startBookActivity(bookAt(position));
    }

    @Override
    public void loadBoks() {
        if (query == null) {
            return;
        }
        getViewState().setNotFoundVisibile(false);
        getViewState().showSourcesFailed(0);
        if (mModelId == Consts.MODEL_BOOKS) {
            books = new ArrayList<>();
            books_filter = null;
            getViewState().showDataBooks(books);
            loadChips(query);
            startBooksSearch(query);
        } else {
            genre = new ArrayList<>();
            getViewState().showDataGenres(genre);
            startGenreSearch(query);
        }
    }

    @Override
    public void loadNext() {
        SearchSession<?> session = mModelId == Consts.MODEL_BOOKS ? mSession : mGenreSession;
        if (session != null && !session.isRunning() && session.hasMore()) {
            getViewState().showProgres(true);
            session.loadNext();
        }
    }

    @Override
    public void retryFailed() {
        SearchSession<?> session = mModelId == Consts.MODEL_BOOKS ? mSession : mGenreSession;
        if (session != null && !session.isRunning() && session.failedCount() > 0) {
            getViewState().setNotFoundVisibile(false);
            getViewState().showSourcesFailed(0);
            getViewState().showProgres(true);
            session.retryFailed();
        }
    }

    private void startBooksSearch(String q) {
        if (mSession != null) {
            mSession.dispose();
        }
        List<SearchSession.Source<BookPOJO>> sources = new ArrayList<>();
        for (Provider provider : Provider.selectedFirst()) {
            if (mPreferences.getBoolean(provider.prefKey, true)) {
                sources.add(new SearchSession.Source<BookPOJO>() {
                    @Override
                    public String name() {
                        return provider.name();
                    }

                    @Override
                    public Single<ProviderPage<BookPOJO>> load(String query, int page) {
                        return Single.fromCallable(() -> mBooksModel.search(provider, query, page));
                    }

                    @Override
                    public long timeoutSeconds() {
                        return provider.requestSeconds();
                    }
                });
            }
        }
        mSession = new SearchSession<>(sources, book -> Url.canonical(book.getUrl()), Schedulers.io(),
                AndroidSchedulers.mainThread(), SOURCE_TIMEOUT_SECONDS, new SearchSession.Listener<BookPOJO>() {
            @Override
            public void onItems(List<BookPOJO> items) {
                books = new ArrayList<>(items);
                filterBooks();
            }

            @Override
            public void onFinished(List<BookPOJO> items, int failedSources) {
                books = new ArrayList<>(items);
                filterBooks();
                getViewState().showProgres(false);
                getViewState().showSourcesFailed(failedSources);
                updateNotFound();
            }
        });
        getViewState().showProgres(true);
        mSession.search(q);
    }

    private void startGenreSearch(String q) {
        if (mGenreSession != null) {
            mGenreSession.dispose();
        }
        final Provider provider = Provider.ofSource(Consts.getSOURCE());
        final SearchUrls.Category category = mModelId == Consts.MODEL_AUTOR
                ? SearchUrls.Category.AUTHORS : SearchUrls.Category.PERFORMERS;
        if (provider == null || SearchUrls.url(provider, category, q, 1) == null) {
            getViewState().showToast(R.string.section_not_available);
            getViewState().setNotFoundVisibile(true);
            return;
        }
        SearchSession.Source<GenrePOJO> source = new SearchSession.Source<GenrePOJO>() {
            @Override
            public String name() {
                return provider.name();
            }

            @Override
            public Single<ProviderPage<GenrePOJO>> load(String query, int page) {
                return Single.fromCallable(() -> mModelGenre.search(provider, category, query, page));
            }
        };
        mGenreSession = new SearchSession<>(Collections.singletonList(source), GenrePOJO::getUrl, Schedulers.io(),
                AndroidSchedulers.mainThread(), SOURCE_TIMEOUT_SECONDS, new SearchSession.Listener<GenrePOJO>() {
            @Override
            public void onItems(List<GenrePOJO> items) {
                genre = new ArrayList<>(items);
                getViewState().showDataGenres(genre);
            }

            @Override
            public void onFinished(List<GenrePOJO> items, int failedSources) {
                genre = new ArrayList<>(items);
                getViewState().showDataGenres(genre);
                getViewState().showProgres(false);
                getViewState().showSourcesFailed(failedSources);
                getViewState().setNotFoundVisibile(isNotFound(true, failedSources, genre.isEmpty()));
            }
        });
        getViewState().showProgres(true);
        mGenreSession.search(q);
    }

    /** Author and series chips: independent of the book list and of each other (SRCH-13). */
    private void loadChips(String q) {
        if (mChips != null) {
            mChips.dispose();
        }
        isLoadAutors = false;
        mSearcheblPOJO = new SearcheblPOJO();
        mSearcheblPOJOFilter = null;
        getViewState().showSeriesAndAutors(mSearcheblPOJO);
        getViewState().showProgresTop(true);
        mChips = mSearchableModel.chips(mPreferences, q)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(pojo -> {
                    mSearcheblPOJO = mSearcheblPOJO.concat(mSearcheblPOJO, pojo);
                    filterAutorsAndSeries();
                }, e -> finishChips(), this::finishChips);
    }

    private void finishChips() {
        isLoadAutors = true;
        getViewState().showProgresTop(false);
        updateNotFound();
    }

    private void updateNotFound() {
        boolean done = mSession != null && !mSession.isRunning() && isLoadAutors;
        getViewState().setNotFoundVisibile(isNotFound(done, mSession == null ? 0 : mSession.failedCount(),
                books.isEmpty() && mSearcheblPOJO.getAutorsList().isEmpty()
                        && mSearcheblPOJO.getSeriesList().isEmpty()));
    }

    /** "Not found" only for a finished search that found nothing while every source answered. */
    static boolean isNotFound(boolean finished, int failedSources, boolean nothingFound) {
        return finished && failedSources == 0 && nothingFound;
    }

    @Override
    public void onAutorsListItemClick(View view, int position) {
        SearchebleArrayPOJO arrayPOJO = (mSearcheblPOJOFilter == null ? mSearcheblPOJO : mSearcheblPOJOFilter)
                .getAutorsList().get(position);
        getViewState().returnResult(arrayPOJO.getUrl(), arrayPOJO.getName(), Consts.MODEL_BOOKS, "searchebleBooks");
    }

    @Override
    public void onSeriesListItemClick(View view, int position) {
        SearchebleArrayPOJO arrayPOJO = (mSearcheblPOJOFilter == null ? mSearcheblPOJO : mSearcheblPOJOFilter)
                .getSeriesList().get(position);
        getViewState().returnResult(arrayPOJO.getUrl(), arrayPOJO.getName(), Consts.MODEL_BOOKS, "searchebleBooks");
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

        BookPOJO bookPOJO = bookAt(position);

        ImageView imageView = layout.findViewById(R.id.imageView);
        if (DefaultCover.replaces(bookPOJO.getPhoto())) {
            imageView.setImageDrawable(DefaultCover.drawable(imageView.getContext(), bookPOJO.getUrl()));
        } else {
            Picasso.get()
                    .load(bookPOJO.getPhoto())
                    .error(android.R.drawable.ic_menu_gallery)
                    .placeholder(android.R.drawable.ic_menu_gallery)
                    .into(imageView);
        }

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
                GenrePicks.record(view1.getContext(), bookPOJO.getGenre());
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
}
