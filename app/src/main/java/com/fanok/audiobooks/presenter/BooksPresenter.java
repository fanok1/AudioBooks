package com.fanok.audiobooks.presenter;


import static androidx.preference.PreferenceManager.getDefaultSharedPreferences;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import com.arellomobile.mvp.InjectViewState;
import com.arellomobile.mvp.MvpPresenter;
import com.fanok.audiobooks.Consts;
import com.fanok.audiobooks.CookesExeption;
import com.fanok.audiobooks.R;
import com.fanok.audiobooks.Url;
import com.fanok.audiobooks.fragment.BooksFragment;
import com.fanok.audiobooks.interface_pacatge.books.BooksView;
import com.fanok.audiobooks.model.AutorsModel;
import com.fanok.audiobooks.model.BooksDBModel;
import com.fanok.audiobooks.model.BooksModel;
import com.fanok.audiobooks.model.GenreModel;
import com.fanok.audiobooks.net.AppProxy;
import com.fanok.audiobooks.net.RouteOffer;
import com.fanok.audiobooks.pojo.BookPOJO;
import com.fanok.audiobooks.pojo.GenrePOJO;
import com.fanok.audiobooks.policy.DefaultCover;
import com.fanok.audiobooks.policy.ParentalControl;
import com.fanok.audiobooks.search.GenrePicks;
import com.fanok.audiobooks.search.GenreSort;
import com.fanok.audiobooks.search.ListingCursor;
import com.fanok.audiobooks.search.ListingFilters;
import com.fanok.audiobooks.search.Provider;
import com.fanok.audiobooks.search.Sources;
import com.fanok.audiobooks.source.Sort;
import com.fanok.audiobooks.search.ProviderPage;
import com.fanok.audiobooks.search.SearchUrls;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.squareup.picasso.Picasso;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@InjectViewState
public class BooksPresenter extends MvpPresenter<BooksView> implements
        com.fanok.audiobooks.interface_pacatge.books.BooksPresenter {

    /**
     * Preference holding the {@link ListingFilters.Duration#key} of the range the user filters book listings by
     * (older versions stored a preset's enum name; {@link ListingFilters.Duration#of(String)} reads both).
     */
    private static final String PREF_DURATION = "listing_duration_filter";
    private static final String PREF_GENRE_SORT = "genre_sort";
    private static final String PREF_AUTHOR_SORT = "author_sort";
    private static final String PREF_PERFORMER_SORT = "performer_sort";

    private boolean isLoading = false;
    private boolean isRefreshing = false;
    private final int mModelId;
    private final String mSubTitle;
    /** The cards shown: the cursor's cards that pass the duration filter. */
    private ArrayList<BookPOJO> books;
    private ArrayList<GenrePOJO> genre;
    private ListingCursor<BookPOJO> mBookCursor = new ListingCursor<>(b -> Url.canonical(b.getUrl()));
    private final ListingCursor<GenrePOJO> mGenreCursor = new ListingCursor<>(GenrePOJO::getUrl);
    private final CompositeDisposable mDisposables = new CompositeDisposable();
    private com.fanok.audiobooks.interface_pacatge.books.BooksModel mModelBook;
    private com.fanok.audiobooks.interface_pacatge.books.GenreModel mModelGenre;
    private final BooksDBModel mBooksDBModel;
    private final Context mContext;
    private final Provider mProvider;
    private ListingFilters.Duration mDuration = ListingFilters.Duration.ANY;
    /** The local order of a genre list ({@link #genre} is the cursor's genres in this order). */
    private GenreSort mGenreSort = GenreSort.NONE;
    /** The genre picks {@link #genre} was last sorted with: a pick made since re-sorts it on return. */
    private Map<String, ?> mPicks = Collections.emptyMap();
    /** The genre list's toolbar search: only the genres whose names contain it are shown. */
    private String mGenreQuery = "";
    /** Cards shown and pages fetched since the user's last action, bounding the automatic hunt for matches. */
    private int mHuntBaseShown;
    private int mHuntPages;
    /**
     * The period the listing is ranked by, as the user chose it; baza-knig's URL holds it only as an absolute
     * date, rebuilt from today for every page-1 request ({@link #refreshPeriodDate}).
     */
    private ListingFilters.Period mPeriod;


    /** The listing URL; a period or a server-side duration chosen in the filter bar rewrites it in place. */
    private String mUrl;

    /** @param period the period kept with a rewritten URL ({@link BooksView#rememberUrl}), or null */
    public BooksPresenter(@NonNull String url, int modelId, @NonNull String subTitle,
            ListingFilters.Period period, @NonNull Context context) {
        mUrl = Url.canonical(url); // a link on a former or mobile host lists from the current one
        mModelId = modelId;
        mSubTitle = subTitle;
        mBooksDBModel = new BooksDBModel(context);
        mProvider = Provider.ofUrl(url);
        switch (mModelId) {
            case Consts.MODEL_BOOKS:
                books = new ArrayList<>();
                mModelBook = new BooksModel();
                mDuration = ListingFilters.Duration.of(
                        getDefaultSharedPreferences(context).getString(PREF_DURATION, ""));
                // idempotent: a URL restored from the fragment arguments already carries the bound
                mUrl = ListingFilters.withDuration(mProvider, mUrl, mDuration);
                // baza-knig's URL holds only the absolute date: the kept choice, else one read back from it
                mPeriod = period != null && ListingFilters.periods(mProvider, mUrl).contains(period)
                        ? period : ListingFilters.periodOf(mProvider, mUrl, today());
                break;
            case Consts.MODEL_GENRE:
                genre = new ArrayList<>();
                mModelGenre = new GenreModel();
                mGenreSort = GenreSort.of(getDefaultSharedPreferences(context).getString(PREF_GENRE_SORT, ""));
                break;
            case Consts.MODEL_AUTOR:
            case Consts.MODEL_ARTIST:
                mGenreSort = GenreSort.of(getDefaultSharedPreferences(context).getString(sortPref(), ""));
                if (mGenreSort == GenreSort.FAVORITE) {
                    mGenreSort = GenreSort.NONE;
                }
                genre = new ArrayList<>();
                AutorsModel autorsModel = new AutorsModel();
                if (mModelId == Consts.MODEL_ARTIST) {
                    autorsModel.setPerformerMode(true);
                }
                mModelGenre = autorsModel;
                break;
        }
        mContext = context;

    }

    @Override
    public void onDestroy() {
        cancel();
        mBooksDBModel.closeDB();
    }

    /** Cancels the running load and resets the loading state with it, so no spinner or flag stays stuck. */
    private void cancel() {
        mDisposables.clear();
        isLoading = false;
        isRefreshing = false;
        getViewState().showProgres(false);
        getViewState().showRefreshing(false);
    }

    @Override
    protected void onFirstViewAttach() {
        super.onFirstViewAttach();
        loadBoks();
        showFilterBar();
    }

    @Override
    public void attachView(BooksView view) {
        super.attachView(view);
        // a listing whose load was cancelled before anything arrived starts again
        if (!isLoading && !cursor().isEnd() && cursor().items().isEmpty()) {
            loadBoks();
        } else if (mGenreSort == GenreSort.FAVORITE && !cursor().items().isEmpty()
                && !GenrePicks.all(mContext).equals(mPicks)) {
            showGenres();
        }
    }

    private ListingCursor<?> cursor() {
        return mModelId == Consts.MODEL_BOOKS ? mBookCursor : mGenreCursor;
    }

    @Override
    public void loadBoks() {
        if (!cursor().isEnd() && !isLoading) {
            startHunt(books == null ? 0 : books.size());
            if (cursor().nextPage() == 1) {
                refreshPeriodDate();
            }
            getViewState().showProgres(true);
            getData(cursor().nextPage());
            showFilterBar();
        } else {
            getViewState().showRefreshing(false);
        }
    }

    // ---------------------------------------------------------------- duration and period filters

    @NonNull
    public GenreSort getGenreSort() {
        return mGenreSort;
    }

    /** The setting that keeps the order of this list: genres, authors and performers each have their own. */
    @NonNull
    private String sortPref() {
        return mModelId == Consts.MODEL_AUTOR ? PREF_AUTHOR_SORT
                : mModelId == Consts.MODEL_ARTIST ? PREF_PERFORMER_SORT : PREF_GENRE_SORT;
    }

    /**
     * Re-sorts the loaded genres, authors or performers in place of a reload: the order is local, the pages stay as
     * fetched, and each page loaded later takes its place in it.
     */
    public void onGenreSortSelected(@NonNull GenreSort sort) {
        mGenreSort = sort;
        getDefaultSharedPreferences(mContext).edit().putString(sortPref(), sort.name()).apply();
        showGenres();
        getViewState().setPosition(0);
    }

    @NonNull
    public String getGenreQuery() {
        return mGenreQuery;
    }

    public void onGenreQuery(@NonNull String query) {
        String normalized = query.trim().toLowerCase(Locale.ROOT).replace('ё', 'е');
        if (normalized.equals(mGenreQuery)) {
            return;
        }
        mGenreQuery = normalized;
        showGenres();
        getViewState().setPosition(0);
    }

    /** The loaded genres in the chosen order, narrowed to the search. */
    private void showGenres() {
        mPicks = GenrePicks.all(mContext);
        genre = mGenreSort.sorted(mGenreCursor.items(), mPicks);
        if (!mGenreQuery.isEmpty()) {
            ArrayList<GenrePOJO> matching = new ArrayList<>();
            for (GenrePOJO item : genre) {
                String name = item.getName();
                if (name != null && name.toLowerCase(Locale.ROOT).replace('ё', 'е').contains(mGenreQuery)) {
                    matching.add(item);
                }
            }
            genre = matching;
        }
        getViewState().showDataGenres(genre);
    }

    @Override
    public void onDurationChipClick() {
        getViewState().showDurationPicker(mDuration);
    }

    @Override
    public void onDurationSelected(@NonNull ListingFilters.Duration duration) {
        if (mModelId != Consts.MODEL_BOOKS || duration.equals(mDuration)) {
            return;
        }
        mDuration = duration;
        getDefaultSharedPreferences(mContext).edit().putString(PREF_DURATION, duration.key()).apply();
        String url = ListingFilters.withDuration(mProvider, mUrl, duration);
        if (!url.equals(mUrl)) {
            // the server bounds the duration itself: a different listing. Not kept in the fragment arguments:
            // the constructor re-applies the stored duration on a restore, and the arguments stay those of the
            // screen as opened, so re-opening it from the menu is not taken for another listing
            mUrl = url;
            reload();
            return;
        }
        // the cards are already here: re-filter them, then hunt for more when the filter hid most of them;
        // a new filter counts every card it shows, not only the ones fetched from now on
        books = filtered(mBookCursor.items());
        getViewState().showDataBooks(books);
        startHunt(0);
        huntOn();
        showFilterBar();
    }

    @Override
    public void onPeriodSelected(@NonNull ListingFilters.Period period) {
        if (!ListingFilters.periods(mProvider, mUrl).contains(period)) {
            return;
        }
        mPeriod = period;
        if (setUrl(ListingFilters.withPeriod(mProvider, mUrl, period, today()))) {
            reload();
        }
    }

    /** Rewrites the listing URL and has the fragment keep it and the period for a restore; false when unchanged. */
    private boolean setUrl(String url) {
        if (url.equals(mUrl)) {
            return false;
        }
        mUrl = url;
        getViewState().rememberUrl(url, mPeriod);
        return true;
    }

    /** The period the filter menu shows checked: baza-knig's stored choice, else what the URL carries. */
    private ListingFilters.Period period() {
        if (ListingFilters.periodsAreDates(mProvider) && mPeriod != null
                && !ListingFilters.periods(mProvider, mUrl).isEmpty()) {
            return mPeriod;
        }
        return ListingFilters.periodOf(mProvider, mUrl, today());
    }

    /** Before a page-1 request: baza-knig's date bound counted back from today, never mid-paging. */
    private void refreshPeriodDate() {
        if (ListingFilters.periodsAreDates(mProvider) && mPeriod != null) {
            setUrl(ListingFilters.withPeriod(mProvider, mUrl, mPeriod, today()));
        }
    }

    @Override
    public void onFilterMore() {
        loadBoks();
    }

    /** Starts the listing over from page 1 of the (rewritten) URL with an empty list. */
    private void reload() {
        cancel();
        mBookCursor = new ListingCursor<>(b -> Url.canonical(b.getUrl()));
        books = new ArrayList<>();
        getViewState().showDataBooks(books);
        getViewState().setPosition(0);
        loadBoks();
        showFilterBar();
    }

    /** The stored duration filter where the cards carry a duration; ANY on a listing whose cards carry none. */
    private ListingFilters.Duration effectiveDuration() {
        return ListingFilters.listsDuration(mProvider, mUrl) ? mDuration : ListingFilters.Duration.ANY;
    }

    /** The cards that pass the duration filter and, on a ranking narrowed to a genre, are of that genre. */
    private ArrayList<BookPOJO> filtered(ArrayList<BookPOJO> items) {
        return ListingFilters.filter(items, effectiveDuration(), ListingFilters.cardGenre(mProvider, mUrl));
    }

    /**
     * The list ends on screen, so no scroll asks for more: the next page, unless a duration or genre filter is on —
     * its hunt fetches within its own budget and the filter strip offers the rest.
     */
    public void loadToFill() {
        boolean filtering = mModelId == Consts.MODEL_BOOKS
                && !(effectiveDuration().isAny() && ListingFilters.cardGenre(mProvider, mUrl) == null);
        if (filtering || isLoading || cursor().isEnd()) {
            return;
        }
        int count = cursor().items().size();
        // a site that keeps sending pages without a card: a few, then scrolling or a refresh decides
        mFillsWithoutCards = count == mFillCount ? mFillsWithoutCards + 1 : 0;
        mFillCount = count;
        if (mFillsWithoutCards <= MAX_FILLS_WITHOUT_CARDS) {
            loadBoks();
        }
    }

    private static final int MAX_FILLS_WITHOUT_CARDS = 3;
    /** The list size the last {@link #loadToFill} saw, and how many fills in a row found it unchanged. */
    private int mFillCount = -1;
    private int mFillsWithoutCards;

    /** Starts a hunt that counts the matches beyond {@code baseShown} cards. */
    private void startHunt(int baseShown) {
        mHuntBaseShown = baseShown;
        mHuntPages = 0;
    }

    /** After a page of a filtered book listing: fetches the next one while the hunt budget allows. */
    private void huntOn() {
        if (mModelId != Consts.MODEL_BOOKS || isLoading
                || effectiveDuration().isAny() && ListingFilters.cardGenre(mProvider, mUrl) == null
                || mBookCursor.isEnd()
                || !ListingFilters.keepHunting(books.size() - mHuntBaseShown, books.size(), mHuntPages)) {
            return;
        }
        getViewState().showProgres(true);
        getData(mBookCursor.nextPage());
    }

    private void showFilterBar() {
        getViewState().showFilterBar(ListingFilters.bar(mModelId == Consts.MODEL_BOOKS, mProvider, mUrl, mDuration,
                mModelId == Consts.MODEL_BOOKS ? period() : null, books == null ? 0 : books.size(),
                mBookCursor.items().size(), mBookCursor.isEnd(), isLoading));
    }

    private static Calendar today() {
        return Calendar.getInstance();
    }

    /** The listing URL of {@code page} in the provider's own scheme; genre lists have a single page. */
    private String pageUrl(int page) {
        if (mModelId == Consts.MODEL_GENRE) {
            return mUrl;
        }
        // an unknown host lists nothing
        return mProvider == null ? mUrl : mProvider.parser.pageUrl(mUrl, page);
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

        Drawable mask = ParentalControl.coverMask(view.getContext(), books.get(position));
        if (mask != null) {
            ImageView imageView = layout.findViewById(R.id.imageView);
            imageView.setImageDrawable(mask);
        } else if (DefaultCover.replaces(books.get(position).getPhoto())) {
            ImageView imageView = layout.findViewById(R.id.imageView);
            imageView.setImageDrawable(DefaultCover.drawable(imageView.getContext(), books.get(position).getUrl()));
        } else if (books.get(position).getPhoto() != null) {
            ImageView imageView = layout.findViewById(R.id.imageView);
            Picasso.get()
                    .load(books.get(position).getPhoto())
                    .error(android.R.drawable.ic_menu_gallery)
                    .placeholder(android.R.drawable.ic_menu_gallery)
                    .into(imageView);
        }

        TextView title = layout.findViewById(R.id.title);
        title.setText(books.get(position).getName());

        TextView authorName = layout.findViewById(R.id.authorName);
        if (books.get(position).getAutor() != null) {
            authorName.setText(books.get(position).getAutor());
            authorName.setVisibility(View.VISIBLE);
        } else {
            authorName.setVisibility(View.GONE);
        }

        if (books.get(position).getSeries() == null || books.get(position).getUrlSeries() == null) {
            series.setVisibility(View.GONE);
        } else {
            series.setVisibility(View.VISIBLE);
        }

        if (mBooksDBModel.inFavorite(books.get(position))) {
            add.setVisibility(View.GONE);
            remove.setVisibility(View.VISIBLE);
        } else {
            add.setVisibility(View.VISIBLE);
            remove.setVisibility(View.GONE);
        }

        open.setOnClickListener(view1 -> {
            dialog.dismiss();
            open(books.get(position));
        });

        add.setOnClickListener(view1 -> {
            dialog.dismiss();
            mBooksDBModel.addFavorite(books.get(position));
        });

        remove.setOnClickListener(view1 -> {
            dialog.dismiss();
            mBooksDBModel.removeFavorite(books.get(position));
        });

        if (books.get(position).getUrlGenre() != null) {
            genre.setVisibility(View.VISIBLE);
            genre.setOnClickListener(view1 -> {
                dialog.dismiss();
                GenrePicks.record(mContext, books.get(position).getGenre());
                getViewState().showFragment(BooksFragment.newInstance(
                        books.get(position).getUrlGenre(),
                        R.string.menu_audiobooks,
                        books.get(position).getGenre(), Consts.MODEL_BOOKS),
                        "genreBooks");
            });
        } else {
            genre.setVisibility(View.GONE);
        }

        if (books.get(position).getUrlAutor() != null) {
            author.setVisibility(View.VISIBLE);
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
        } else {
            author.setVisibility(View.GONE);
        }

        if (books.get(position).getUrlArtist() != null) {
            artist.setVisibility(View.VISIBLE);
            artist.setOnClickListener(view1 -> {
                dialog.dismiss();
                getViewState().showFragment(BooksFragment.newInstance(
                        books.get(position).getUrlArtist(),
                        R.string.menu_audiobooks,
                        books.get(position).getArtist(), Consts.MODEL_BOOKS),
                        "artistBooks");
            });
        } else {
            artist.setVisibility(View.GONE);
        }

        series.setOnClickListener(view12 -> {
            dialog.dismiss();
            getViewState().showFragment(BooksFragment.newInstance(
                    books.get(position).getUrlSeries(),
                    R.string.menu_audiobooks,
                    books.get(position).getSeries(), Consts.MODEL_BOOKS),
                    "seriesBooks");
        });
        dialog.show();
    }

    @SuppressLint("ApplySharedPref")
    @Override
    public void onOptionItemSelected(int itemId) {
        // the label of the sort the listing was opened with is replaced, not appended to
        String subTitle = mSubTitle;
        for (Sort label : Sort.values()) {
            subTitle = subTitle.replace(" " + getStringById(label.label), "");
        }
        Sort sort = Sort.ofMenuId(itemId);

        Provider source = BooksFragment.sourceOf(itemId);
        if (source != null) {
            SharedPreferences pref = getDefaultSharedPreferences(Objects.requireNonNull(mContext));
            SharedPreferences.Editor editor = pref.edit();
            editor.putString("sorce_books", source.prefValue);
            editor.apply();
            // Обновляем статическое поле сразу — процесс не перезапускается
            Consts.setSOURCE(source.prefValue);
            getViewState().recreate();
        }

        String url = sort == null ? null : ListingFilters.sorted(mProvider, sort, mUrl);
        if (url == null && itemId != R.id.app_bar_search) {
            // a source switch, a submenu parent or an item this site has no listing for
            return;
        }
        if (itemId == R.id.app_bar_search) {
            boolean noPerformerSearch = mModelId == Consts.MODEL_ARTIST && mProvider != null
                    && SearchUrls.url(mProvider, SearchUrls.Category.PERFORMERS, "q", 1) == null;
            if (mModelId == Consts.MODEL_GENRE || noPerformerSearch) {
                // no site searches its genres, some none of their performers: there the search is the book search
                getViewState().showSearchActivity(Consts.MODEL_BOOKS);
            } else {
                getViewState().showSearchActivity(mModelId);
            }
        } else if (sort != null) {
            getViewState().showFragment(BooksFragment.newInstance(url, R.string.menu_audiobooks,
                    subTitle + " " + getStringById(sort.label), Consts.MODEL_BOOKS), sort.tag);
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
            if (mModelId == Consts.MODEL_GENRE) {
                GenrePicks.record(mContext, genre.get(position).getName());
            }
            String url = genre.get(position).getUrl();
            getViewState().showFragment(BooksFragment.newInstance(
                    url,
                    R.string.menu_audiobooks,
                    genre.get(position).getName(), Consts.MODEL_BOOKS), tag);
        }
    }

    @Override
    public void onBookItemClick(View view, int position) {
        open(books.get(position));
    }

    /** A book opens its page; a card that is a list of books (a site's collection) opens as a listing. */
    private void open(BookPOJO book) {
        Provider provider = Provider.ofUrl(book.getUrl());
        if (provider != null && provider.parser.opensAsListing(book.getUrl())) {
            getViewState().showFragment(BooksFragment.newInstance(book.getUrl(), R.string.menu_audiobooks,
                    book.getName(), Consts.MODEL_BOOKS), "collectionBooks");
        } else {
            getViewState().showBooksActivity(book);
        }
    }

    @Override
    public void onRefresh() {
        if (!isLoading) {
            isRefreshing = true;
            startHunt(0); // page 1 replaces the list
            refreshPeriodDate();
            getViewState().showRefreshing(true);
            getData(1); // the cursor moves only when page 1 arrives
        } else {
            getViewState().showRefreshing(false);
        }
    }


    private void getData(int page) {
        if (isLoading) {
            return;
        }
        isLoading = true;
        int request = ++mRequest;
        mRequestDirect = mProvider != null && AppProxy.isDirect(mProvider);
        String url = pageUrl(page);
        if (mModelId == Consts.MODEL_BOOKS) {
            mDisposables.add(mModelBook.getBooks(url, page)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(result -> {
                                ListingCursor<BookPOJO> cursor = mBookCursor;
                                onPage(request, page, result, cursor, () -> {
                                    if (page <= 1) {
                                        mHuntBaseShown = 0; // page 1 replaced the list the hunt counted from
                                    }
                                    books = filtered(cursor.items());
                                    getViewState().showDataBooks(books);
                                });
                                if (!result.isFailure()) {
                                    mHuntPages++;
                                    huntOn();
                                }
                                showFilterBar();
                            },
                            e -> {
                                onPage(request, page, ProviderPage.<BookPOJO>failed(e), mBookCursor, null);
                                showFilterBar();
                            }));
        } else {
            mDisposables.add(mModelGenre.getBooks(url, page)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(result -> onPage(request, page, result, mGenreCursor, this::showGenres),
                            e -> onPage(request, page, ProviderPage.<GenrePOJO>failed(e), mGenreCursor, null)));
        }
    }

    /**
     * Applies one page: a failure (of a refresh too) keeps cursor and list and re-asks the same page next
     * time; a success moves the cursor, page 1 replacing the list and its dedupe set ({@link ListingCursor}).
     */
    private <T> void onPage(int request, int page, ProviderPage<T> result, ListingCursor<T> cursor, Runnable show) {
        try {
            RouteAttempt tried = takeRetry(request);
            if (result.isFailure()) {
                if (tried != null) {
                    routeFailed(tried, result);
                } else if (mRoute != null && mRoute.deferred) {
                    return; // the accepted offer retries below, on the other route
                } else if (!offerRoute(result)) {
                    showFailure(result);
                }
                return;
            }
            mRoute = null; // the site answers: an unanswered offer is moot
            if (tried != null) {
                getViewState().showToast(mContext.getString(
                        tried.wasDirect ? R.string.route_works_proxy : R.string.route_works_direct, mProvider.title));
            }
            cursor.apply(page, result);
            show.run();
            if (isRefreshing) {
                getViewState().setPosition(0);
            }
        } finally {
            isLoading = false;
            isRefreshing = false;
            getViewState().showProgres(false);
            getViewState().showRefreshing(false);
            RouteAttempt deferred = mRoute;
            if (deferred != null && deferred.deferred && request == mRequest) {
                mRoute = null;
                if (result.isFailure()) {
                    switchAndRetry(deferred);
                }
            }
        }
    }

    /**
     * True for a section root (catalog, genres, authors, readers) of a site that is no longer active: opened from the
     * source menu before the site was switched off. A listing reached from a book of such a site is not a root.
     */
    public boolean isInactiveSourceRoot() {
        if (mProvider == null || Sources.isActive(mProvider)) {
            return false;
        }
        for (String root : new String[]{mProvider.index, mProvider.sections, mProvider.authors, mProvider.performers}) {
            if (root != null && Url.canonical(root).equals(mUrl)) {
                return true;
            }
        }
        return false;
    }

    private void showFailure(ProviderPage<?> result) {
        if (result.status == ProviderPage.Status.BLOCKED) {
            getViewState().showToast(CookesExeption.messageFor(mUrl));
        } else {
            getViewState().showToast(R.string.error_load_data);
        }
    }

    // ---------------------------------------------------------------- route offer

    /**
     * A route offer for the listing's site: whether the failed page was a refresh, the site's route then, and the
     * retry's request once accepted (0 while the offer is unanswered).
     */
    private static final class RouteAttempt {
        final boolean refresh;
        final boolean wasDirect;
        int request;
        /** Accepted while another page was loading: that page decides (an answer drops the offer). */
        boolean deferred;

        RouteAttempt(boolean refresh, boolean wasDirect) {
            this.refresh = refresh;
            this.wasDirect = wasDirect;
        }
    }

    /** Numbers the page requests, so a retry's result is told apart from a newer request's. */
    private int mRequest;
    /** The route of the site when the latest request went out: a failure is offered the other route only. */
    private boolean mRequestDirect;
    private RouteAttempt mRoute;

    /** The accepted attempt whose retry this result answers; a newer request replaced any other, kept as it is. */
    private RouteAttempt takeRetry(int request) {
        RouteAttempt retry = mRoute;
        if (retry == null || retry.request == 0) {
            return null;
        }
        mRoute = null;
        return retry.request == request ? retry : null;
    }

    /**
     * A site that fails while the device is online: offers its other route once, in place of the error toast. A
     * blocked site with the proxy off keeps the cookie hint: a challenge, not a route, is the likelier cause.
     */
    private boolean offerRoute(ProviderPage<?> result) {
        if (mProvider == null || !Consts.isNetworkAvailable(mContext)) {
            return false;
        }
        boolean wasDirect = AppProxy.isDirect(mProvider);
        if (wasDirect != mRequestDirect) {
            return false; // the route changed while the page was loading: the failure says nothing of the new one
        }
        RouteOffer.Kind kind = RouteOffer.of(AppProxy.MODE_OFF.equals(AppProxy.mode()), wasDirect,
                AppProxy.proxyUsable());
        if (kind == RouteOffer.Kind.NONE || kind == RouteOffer.Kind.ENABLE_PROXY
                && result.status == ProviderPage.Status.BLOCKED || !RouteOffer.claim(mProvider)) {
            return false;
        }
        if (kind == RouteOffer.Kind.ENABLE_PROXY) {
            getViewState().showRouteOffer(R.string.route_offer_enable, mProvider.title,
                    R.string.route_offer_enable_action, true);
        } else {
            boolean toProxy = kind == RouteOffer.Kind.TO_PROXY;
            mRoute = new RouteAttempt(isRefreshing, wasDirect);
            getViewState().showRouteOffer(toProxy ? R.string.route_offer_proxy : R.string.route_offer_direct,
                    mProvider.title, toProxy ? R.string.route_offer_proxy_action : R.string.route_offer_direct_action,
                    false);
        }
        return true;
    }

    /** Moves the site to its other route and asks the failed page again the way it was asked: one try only. */
    public void onRouteOfferAccepted() {
        RouteAttempt offer = mRoute;
        if (offer == null || offer.request != 0 || mProvider == null) {
            return;
        }
        if (isLoading) {
            offer.deferred = true; // the page on its way decides (onPage)
            return;
        }
        mRoute = null;
        switchAndRetry(offer);
    }

    /** The site's other route, and the failed page asked again on it: the offer's one try. */
    private void switchAndRetry(RouteAttempt offer) {
        AppProxy.setDirect(getDefaultSharedPreferences(mContext), mProvider, !offer.wasDirect);
        int before = mRequest;
        if (offer.refresh) {
            onRefresh();
        } else {
            loadBoks();
        }
        if (mRequest != before) {
            offer.request = mRequest;
            mRoute = offer;
        }
    }

    /** A declined offer is not made again for the site in this process ({@link RouteOffer#claim}). */
    public void onRouteOfferDismissed() {
        if (mRoute != null && mRoute.request == 0 && !mRoute.deferred) {
            mRoute = null;
        }
    }

    /** Neither route answers: back to the user's route, unless the user changed it meanwhile. */
    private void routeFailed(RouteAttempt tried, ProviderPage<?> result) {
        if (AppProxy.isDirect(mProvider) != tried.wasDirect) {
            AppProxy.setDirect(getDefaultSharedPreferences(mContext), mProvider, tried.wasDirect);
        }
        if (result.status == ProviderPage.Status.BLOCKED) {
            getViewState().showToast(CookesExeption.messageFor(mUrl)); // the cookie hint is what can help
        } else {
            getViewState().showToast(mContext.getString(R.string.route_none, mProvider.title));
        }
    }

    private String getStringById(int id) {
        return mContext.getString(id);
    }

    @Override
    public void onActivityResult(@NonNull Intent intent) {
        String url = intent.getStringExtra("url");
        String name = intent.getStringExtra("name");
        int modelId = intent.getIntExtra("modelId", 0);
        String tag = intent.getStringExtra("tag");

        if (url != null) {
            Fragment fragment = BooksFragment.newInstance(url,
                    R.string.menu_audiobooks, name, modelId);
            getViewState().showFragment(fragment, tag);
        }

    }
}
