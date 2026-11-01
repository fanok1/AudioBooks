package com.fanok.audiobooks.fragment;

import static android.content.Context.UI_MODE_SERVICE;
import static com.fanok.audiobooks.Consts.MODEL_ARTIST;
import static com.fanok.audiobooks.Consts.MODEL_AUTOR;
import static com.fanok.audiobooks.Consts.MODEL_BOOKS;
import static com.fanok.audiobooks.Consts.MODEL_GENRE;
import static com.fanok.audiobooks.Consts.setColorPrimeriTextInIconItemMenu;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.UiModeManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.core.view.MenuCompat;
import androidx.core.view.MenuHost;
import androidx.core.view.MenuProvider;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Lifecycle;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.arellomobile.mvp.MvpAppCompatFragment;
import com.arellomobile.mvp.presenter.InjectPresenter;
import com.arellomobile.mvp.presenter.ProvidePresenter;
import com.fanok.audiobooks.Consts;
import com.fanok.audiobooks.GridSpacingItemDecoration;
import com.fanok.audiobooks.R;
import com.fanok.audiobooks.Url;
import com.fanok.audiobooks.activity.BookActivity;
import com.fanok.audiobooks.activity.MainActivity;
import com.fanok.audiobooks.activity.SearchableActivity;
import com.fanok.audiobooks.adapter.BooksListAddapter;
import com.fanok.audiobooks.adapter.GenreListAddapter;
import com.fanok.audiobooks.databinding.DialogDurationFilterBinding;
import com.fanok.audiobooks.databinding.FragmentBooksBinding;
import com.fanok.audiobooks.interface_pacatge.books.BooksView;
import com.fanok.audiobooks.pojo.BookPOJO;
import com.fanok.audiobooks.pojo.GenrePOJO;
import com.fanok.audiobooks.policy.ParentalControl;
import com.fanok.audiobooks.presenter.BooksPresenter;
import com.fanok.audiobooks.search.GenreSort;
import com.fanok.audiobooks.search.ListingFilters;
import com.fanok.audiobooks.search.ListingFilters.Duration;
import com.fanok.audiobooks.search.Provider;
import com.fanok.audiobooks.search.Sources;
import com.fanok.audiobooks.source.Sort;
import com.google.android.material.navigation.NavigationView;
import com.google.android.material.slider.RangeSlider;
import com.google.android.material.snackbar.Snackbar;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;


public class BooksFragment extends MvpAppCompatFragment implements BooksView {
    private static final String TAG = "BooksFragment";
    private static final String ARG_URL = "url";
    private static final String ARG_TITLE = "title";
    private static final String ARG_SUB_TITLE = "sub_title";
    private static final String ARG_SUB_TITLE_STRING = "sub_title_string";
    private static final String ARG_MODEL = "model_id";
    /** The filter-bar period, kept next to the URL it rewrote ({@link #rememberUrl}). */
    private static final String ARG_PERIOD = "period";
    /** The URL the listing was opened on, never rewritten: MainActivity tells a re-tapped root by it. */
    public static final String ARG_OPENED_URL = "opened_url";
    /** The labels of {@link Duration#PRESETS}, in that order. */
    private static final int[] DURATION_PRESET_LABELS = {R.string.duration_any, R.string.duration_under_30m,
            R.string.duration_30m_1h, R.string.duration_1h_2h, R.string.duration_over_2h};


    @InjectPresenter
    BooksPresenter mPresenter;

    private BooksListAddapter mAddapterBooks;
    /** The parental-control version the visible covers were bound with. */
    private long mParentalVersion;

    private GenreListAddapter mAddapterGenre;

    private FragmentBooksBinding binding;

    private int titleId;
    private int subTitleId;
    private String subTitleString;
    private int modelID;
    private String mUrl;

    private boolean isTelevision;

    private MenuProvider mMenuProvider;

    /** The filter state last shown: the toolbar's filter item is prepared from it. */
    private ListingFilters.Bar mBar = ListingFilters.Bar.HIDDEN;


    public static BooksFragment newInstance(@NonNull String url, int title, int modelID) {
        BooksFragment fragment = new BooksFragment();
        if (url == null || url.isEmpty()) {
            throw new IllegalArgumentException(
                    "Variable 'url' contains not url");
        }
        Bundle args = new Bundle();
        args.putString(ARG_URL, url);
        args.putString(ARG_OPENED_URL, url);
        args.putInt(ARG_TITLE, title);
        args.putInt(ARG_MODEL, modelID);
        fragment.setArguments(args);
        return fragment;
    }

    public static BooksFragment newInstance(@NonNull String url, int title, int subTitle,
            int modelID) {
        BooksFragment fragment = new BooksFragment();
        if (url == null || url.isEmpty()) {
            throw new IllegalArgumentException(
                    "Variable 'url' contains not url");
        }
        Bundle args = new Bundle();
        args.putString(ARG_URL, url);
        args.putString(ARG_OPENED_URL, url);
        args.putInt(ARG_TITLE, title);
        args.putInt(ARG_SUB_TITLE, subTitle);
        args.putInt(ARG_MODEL, modelID);
        fragment.setArguments(args);
        return fragment;
    }

    public static BooksFragment newInstance(@NonNull String url, int title, String subTitle,
            int modelID) {
        BooksFragment fragment = new BooksFragment();
        if (url == null || url.isEmpty()) {
            throw new IllegalArgumentException(
                    "Variable 'url' contains not url");
        }
        Bundle args = new Bundle();
        args.putString(ARG_URL, url);
        args.putString(ARG_OPENED_URL, url);
        args.putInt(ARG_TITLE, title);
        args.putString(ARG_SUB_TITLE_STRING, subTitle);
        args.putInt(ARG_MODEL, modelID);
        fragment.setArguments(args);
        return fragment;
    }

    @SuppressLint("UnsafeOptInUsageError")
    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate: called");
        super.onCreate(savedInstanceState);
        Bundle arg = getArguments();
        String url = "";
        if (arg != null) {
            url = arg.getString(ARG_URL, "");
            titleId = arg.getInt(ARG_TITLE, 0);
            subTitleId = arg.getInt(ARG_SUB_TITLE, 0);
            subTitleString = arg.getString(ARG_SUB_TITLE_STRING, "");
            modelID = arg.getInt(ARG_MODEL, -1);
        }
        if (url.isEmpty()) {
            throw new IllegalArgumentException("Variable 'url' contains not url");
        }
        if (modelID == -1) {
            throw new IllegalArgumentException("Illegal model id");
        }
        mUrl = url;


    }

    private final ActivityResultLauncher<Intent> searchActivityLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK) {
                    Intent data = result.getData();
                    if (data != null) {
                        mPresenter.onActivityResult(data);
                    }
                }
            });


    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {

        binding = FragmentBooksBinding.inflate(inflater, container, false);

        UiModeManager uiModeManager = (UiModeManager) requireContext().getSystemService(UI_MODE_SERVICE);
        isTelevision = (uiModeManager != null && uiModeManager.getCurrentModeType() == Configuration.UI_MODE_TYPE_TELEVISION);


        MenuHost menuHost = requireActivity();
        mMenuProvider = new MenuProvider() {
            @Override
            public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {


                menu.clear();

                Provider provider = Provider.ofUrl(mUrl); // null for an unknown host: no sorts
                String listing = Url.canonical(mUrl); // as the presenter lists it
                inflater.inflate(R.menu.books_source_options_menu, menu);
                if (provider != null) {
                    fillOrderMenu(menu, provider.parser.sorts(listing));
                }
                fillSourceMenu(menu);

                if (modelID != Consts.MODEL_BOOKS || mUrl.contains("reader") || mUrl.contains("author")
                        || mUrl.contains("genre") || mUrl.contains("serie") || mUrl.contains("performer")
                        || mUrl.contains("narrator") || mUrl.contains("xfsearch") || mUrl.contains("/tags/")
                        || mUrl.contains("zhanr")) {
                    MenuItem sourceItem = menu.findItem(R.id.source);
                    if (sourceItem != null) sourceItem.setVisible(false);
                } else {
                    setColorPrimeriTextInIconItemMenu(menu.findItem(R.id.source), requireContext());
                }

                boolean noSort = provider == null || !provider.parser.hasSort(listing);
                if (modelID == Consts.MODEL_GENRE) {
                    fillGenreSortMenu(menu);
                    setGenreSearch(menu.findItem(R.id.app_bar_search));
                } else if (modelID == Consts.MODEL_AUTOR || modelID == Consts.MODEL_ARTIST) {
                    fillGenreSortMenu(menu);
                } else if (modelID != Consts.MODEL_BOOKS || noSort) {
                    MenuItem orderItem = menu.findItem(R.id.order);
                    if (orderItem != null) orderItem.setVisible(false);
                } else {
                    setColorPrimeriTextInIconItemMenu(menu.findItem(R.id.order), requireContext());
                }



                setColorPrimeriTextInIconItemMenu(menu.findItem(R.id.app_bar_search), requireContext());
                fillFilterMenu(menu);
                            }

                            @Override
                            public void onPrepareMenu(@NonNull Menu menu) {
                MenuItem item = menu.findItem(SOURCE_ITEM_BASE + Consts.getSOURCE());
                if (item != null) {
                    item.setChecked(true);
                }
                prepareFilterMenu(menu);
                MenuItem genreSort = menu.findItem(GENRE_SORT_ITEM_BASE + getPresenter().getGenreSort().ordinal());
                if (genreSort != null) {
                    genreSort.setChecked(true);
                }
                            }

                            @Override
                            public boolean onMenuItemSelected(@NonNull MenuItem item) {
                if (item.getActionView() instanceof SearchView) {
                    return false; // a genre list's search opens in the toolbar
                }
                if (item.getGroupId() == R.id.genre_sort_group) {
                    item.setChecked(true);
                    getPresenter().onGenreSortSelected(GenreSort.values()[item.getItemId() - GENRE_SORT_ITEM_BASE]);
                } else if (!onFilterItemSelected(item)) {
                    getPresenter().onOptionItemSelected(item.getItemId());
                }
                return true;
            }
        };
        menuHost.addMenuProvider(mMenuProvider, getViewLifecycleOwner(), Lifecycle.State.RESUMED);


        if (titleId != 0) {
            requireActivity().setTitle(titleId);
        }
        ActionBar toolbar = ((AppCompatActivity) requireActivity()).getSupportActionBar();
        if (toolbar != null) {
            if (!subTitleString.isEmpty()) {
                toolbar.setSubtitle(subTitleString);
            } else if (subTitleId != 0) {
                toolbar.setSubtitle(subTitleId);
            } else {
                toolbar.setSubtitle("");
            }
        }

        switch (modelID) {
            case Consts.MODEL_BOOKS:
                mAddapterBooks = new BooksListAddapter();
                binding.list.setAdapter(mAddapterBooks);
                break;
            case Consts.MODEL_GENRE:
            case Consts.MODEL_AUTOR:
            case Consts.MODEL_ARTIST:
                mAddapterGenre = new GenreListAddapter();
                binding.list.setAdapter(mAddapterGenre);
                break;
        }


        if (isTelevision){
            binding.refresh.setEnabled(false);
        }else {
            binding.refresh.setOnRefreshListener(() -> getPresenter().onRefresh());
        }

        binding.durationChip.setOnClickListener(v -> getPresenter().onDurationChipClick());
        binding.durationChip.setCloseIconContentDescription(getString(R.string.filter_clear));
        binding.durationChip.setOnCloseIconClickListener(v -> getPresenter().onDurationSelected(Duration.ANY));
        binding.filterMore.setOnClickListener(v -> getPresenter().onFilterMore());

        binding.list.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                if (modelID == Consts.MODEL_GENRE) {
                    return;
                }
                if (dy > 0) {
                    int lastVisible = ((LinearLayoutManager) Objects.requireNonNull(
                            recyclerView.getLayoutManager())).findLastVisibleItemPosition();
                    if (lastVisible > getCount() - 15) {
                        getPresenter().loadBoks();
                    }
                } else if (dy == 0) {
                    fillScreen(); // a layout changed the visible range: a page came, the view came back, a resize
                }
            }
        });

        if (mAddapterGenre != null) {
            mAddapterGenre.setClickListner(
                    (view1, position) -> mPresenter.onGenreItemClick(view1, position));
        }

        if (mAddapterBooks != null) {
            mAddapterBooks.setListener(this::onItemSelected);

            mAddapterBooks.setLongListener(
                    (view13, position) -> mPresenter.onBookItemLongClick(view13, position,
                            getLayoutInflater()));
        }

        int orientation = this.getResources().getConfiguration().orientation;

        int isTablet = getResources().getInteger(R.integer.isTablet);

        if (isTablet == 0) {
            if (orientation == Configuration.ORIENTATION_PORTRAIT) {
                setLayoutManager();
            } else {
                setLayoutManager(2);
            }
        }
        if (isTablet > 0) {
            if (orientation == Configuration.ORIENTATION_PORTRAIT) {
                setLayoutManager(2);
            } else {
                setLayoutManager(3);
            }
        }

        return binding.getRoot();
    }








    @Override
    public void onDestroyView() {
        if (mRouteOffer != null) {
            mRouteOffer.dismiss(); // it would outlive the screen on the activity's layout
            mRouteOffer = null;
        }
        if (mAddapterBooks != null) {
            mAddapterBooks.close();
        }
        mAddapterBooks = null;
        mAddapterGenre = null;
        super.onDestroyView();
        binding = null;
    }

    @Override
    public void recreate() {
        if (getActivity() == null) return;
        Intent intent = new Intent(getActivity(), MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        getActivity().startActivity(intent);
        getActivity().finish();
    }

    @Override
    public void showBooksActivity(@NotNull @NonNull BookPOJO bookPOJO) {
        BookActivity.startNewActivity(requireContext(), bookPOJO);
    }

    @ProvidePresenter
    BooksPresenter provideBookPresenter() {
        Bundle arg = getArguments();
        String url = "";
        if (arg != null) {
            url = arg.getString(ARG_URL, "");
            titleId = arg.getInt(ARG_TITLE, 0);
            subTitleId = arg.getInt(ARG_SUB_TITLE, 0);
            subTitleString = arg.getString(ARG_SUB_TITLE_STRING, "");
            modelID = arg.getInt(ARG_MODEL, -1);
        }
        if (url.isEmpty()) {
            throw new IllegalArgumentException("Variable 'url' contains not url");
        }
        if (modelID == -1) {
            throw new IllegalArgumentException("Illegal model id");
        }
        mUrl = url;
        String subTitle = "";
        if (!subTitleString.isEmpty()) {
            subTitle = subTitleString;
        } else if (subTitleId != 0) {
            subTitle = getResources().getString(subTitleId);
        }
        return new BooksPresenter(url, modelID, subTitle,
                ListingFilters.Period.of(arg != null ? arg.getString(ARG_PERIOD) : null),
                requireContext().getApplicationContext());
    }

    @Override
    public void setLayoutManager() {
        binding.list.setLayoutManager(new LinearLayoutManager(this.getContext()));
        setItemDecoration(1);
    }

    @Override
    public void showDataBooks(@NonNull ArrayList<BookPOJO> books) {
        if (mAddapterBooks != null) {
            mAddapterBooks.setItem(books); // Используем setData, который мы ранее исправили
            // after the layout: a page that added no card (all filtered out, or an empty page) moves no visible
            // range, so no scroll event asks for the next one
            binding.list.post(this::fillScreen);
        } else {
            // Если адаптер еще не создан, это может быть ошибкой в логике,
            // но на всякий случай можно добавить обработку.
            showToast(R.string.error_display_data);
        }
    }

    @Override
    public void showDataGenres(@NonNull ArrayList<GenrePOJO> genres) {
        if (mAddapterGenre != null) {
            mAddapterGenre.setItem(genres);
        } else {
            showToast(R.string.error_display_data);
        }
    }


    @Override
    public void clearData() {
        if (mAddapterBooks != null) mAddapterBooks.clearItem();
        if (mAddapterGenre != null) mAddapterGenre.clearItem();
    }

    @Override
    public void setLayoutManager(int count) {
        binding.list.setLayoutManager(new GridLayoutManager(this.getContext(), count));
        setItemDecoration(count);
    }

    @Override
    public void showToast(int message) {
        Toast.makeText(this.getContext(), getResources().getText(message),
                Toast.LENGTH_LONG).show();
    }

    @Override
    public void showToast(String message) {
        Toast.makeText(this.getContext(), message, Toast.LENGTH_LONG).show();
    }

    /** Long enough to read the site's name and decide. */
    private static final int ROUTE_OFFER_MS = 8000;
    private Snackbar mRouteOffer;

    @Override
    public void showRouteOffer(int message, @NonNull String site, int action, boolean openProxySettings) {
        if (binding == null) {
            getPresenter().onRouteOfferDismissed();
            return;
        }
        mRouteOffer = Snackbar.make(binding.getRoot(), getString(message, site), ROUTE_OFFER_MS)
                .setAction(action, v -> {
                    if (!isAdded()) {
                        return;
                    }
                    if (openProxySettings) {
                        new ProxyDialogFragment().show(getChildFragmentManager(), ProxyDialogFragment.TAG);
                    } else {
                        getPresenter().onRouteOfferAccepted();
                    }
                })
                .addCallback(new Snackbar.Callback() {
                    @Override
                    public void onDismissed(Snackbar bar, int event) {
                        if (mRouteOffer == bar) {
                            mRouteOffer = null;
                        }
                        if (event != DISMISS_EVENT_ACTION) {
                            getPresenter().onRouteOfferDismissed();
                        }
                    }
                });
        mRouteOffer.show();
    }

    @Override
    public void setPosition(int position) {
        Objects.requireNonNull(binding.list.getLayoutManager()).scrollToPosition(position);
    }

    @Override
    public void showProgres(boolean b) {
        if (b) {
            binding.mProgressBarLayout.getRoot().setVisibility(View.VISIBLE);
        } else {
            binding.mProgressBarLayout.getRoot().setVisibility(View.GONE);
        }
    }

    @Override
    public void showFragment(@NonNull Fragment fragment, @NonNull String tag) {
        MainActivity activity = (MainActivity) getActivity();
        if (activity != null) {
            activity.showFragment(fragment, tag);
        }
    }

    @Override
    public void showSearchActivity(int modelId) {
        Intent intent = new Intent(getContext(), SearchableActivity.class);
        intent.putExtra(Consts.ARG_MODEL, modelId);
        searchActivityLauncher.launch(intent);
    }

    @Override
    public void showRefreshing(boolean b) {
        binding.refresh.setRefreshing(b);
    }

    @Override
    public void showFilterBar(@NonNull ListingFilters.Bar bar) {
        if (binding == null) {
            return;
        }
        boolean menuChanged = bar.visible != mBar.visible || bar.durationAvailable != mBar.durationAvailable
                || !bar.duration.equals(mBar.duration) || bar.period != mBar.period
                || !bar.periods.equals(mBar.periods);
        mBar = bar;
        if (menuChanged) {
            requireActivity().invalidateMenu();
        }
        // the strip is there only while a duration filter is on: nothing else takes room above the list
        boolean filtering = bar.visible && bar.isFiltering();
        binding.filterStrip.setVisibility(filtering ? View.VISIBLE : View.GONE);
        if (!filtering) {
            return;
        }
        binding.durationChip.setText(durationLabel(requireContext(), bar.duration));
        binding.filterStatus.setText(getString(
                bar.end && bar.scanned > 0 ? R.string.filter_status_end : R.string.filter_status,
                bar.shown, bar.scanned));
        // stays in place while a page loads (disabled, keeping the TV focus); gone only at the real end
        binding.filterMore.setVisibility(bar.end ? View.GONE : View.VISIBLE);
        binding.filterMore.setEnabled(bar.canSearchMore);
    }

    @Override
    public void rememberUrl(@NonNull String url, ListingFilters.Period period) {
        Bundle args = getArguments();
        if (args != null) {
            args.putString(ARG_URL, url);
            args.putString(ARG_PERIOD, period == null ? null : period.name());
        }
        mUrl = url;
    }

    /** Fills the filter item's submenu: the duration item, then one radio item per period. */
    private void fillFilterMenu(@NonNull Menu menu) {
        MenuItem filter = menu.findItem(R.id.filter);
        SubMenu sub = filter == null ? null : filter.getSubMenu();
        if (sub == null) {
            return;
        }
        sub.add(Menu.NONE, R.id.filter_duration, Menu.NONE, R.string.filter_duration_menu);
        for (ListingFilters.Period period : ListingFilters.Period.values()) {
            sub.add(R.id.filter_period_group, Menu.FIRST + period.ordinal(), Menu.NONE, periodLabel(period));
        }
        sub.setGroupCheckable(R.id.filter_period_group, true, true);
        MenuCompat.setGroupDividerEnabled(sub, true);
    }

    /**
     * The filter item where the listing can filter, with a dot while a duration filter is on. A ranking's item
     * opens a submenu (duration, then the periods the site takes); any other listing's item has no visible
     * submenu item, so a tap goes straight to the duration dialog ({@link #onFilterItemSelected}).
     */
    private void prepareFilterMenu(@NonNull Menu menu) {
        MenuItem filter = menu.findItem(R.id.filter);
        if (filter == null) {
            return;
        }
        filter.setVisible(modelID == Consts.MODEL_BOOKS && mBar.visible);
        if (!filter.isVisible()) {
            return;
        }
        if (mBar.isFiltering()) {
            filter.setIcon(R.drawable.ic_filter_active); // drawn in the menu color already: no tint over the dot
        } else {
            filter.setIcon(R.drawable.ic_filter);
            setColorPrimeriTextInIconItemMenu(filter, requireContext());
        }
        SubMenu sub = filter.getSubMenu();
        if (sub == null) {
            return;
        }
        MenuItem duration = sub.findItem(R.id.filter_duration);
        if (duration != null) {
            duration.setVisible(!mBar.periods.isEmpty() && mBar.durationAvailable);
            duration.setTitle(mBar.isFiltering()
                    ? getString(R.string.filter_duration_menu_on, durationLabel(requireContext(), mBar.duration))
                    : getString(R.string.filter_duration_menu));
        }
        for (ListingFilters.Period period : ListingFilters.Period.values()) {
            MenuItem item = sub.findItem(Menu.FIRST + period.ordinal());
            if (item != null) {
                item.setVisible(mBar.periods.contains(period));
                if (period == mBar.period) {
                    item.setChecked(true);
                }
            }
        }
    }

    /** Item ids of the generated source submenu: {@code SOURCE_ITEM_BASE + sourceId} (the period items use Menu.FIRST + n). */
    private static final int SOURCE_ITEM_BASE = Menu.FIRST + 100;

    /** The provider a source menu item selects, or null for any other item. */
    /** The Order submenu of a source's listing, from {@link com.fanok.audiobooks.source.SourceParser#sorts}. */
    private static void fillOrderMenu(Menu menu, List<Sort.Entry> sorts) {
        MenuItem order = menu.findItem(R.id.order);
        SubMenu items = order == null ? null : order.getSubMenu();
        if (items == null) {
            return;
        }
        for (Sort.Entry entry : sorts) {
            if (entry.items.isEmpty()) {
                items.add(Menu.NONE, entry.sort.menuId, Menu.NONE, entry.title);
            } else {
                SubMenu submenu = items.addSubMenu(entry.title);
                for (Sort sort : entry.items) {
                    submenu.add(Menu.NONE, sort.menuId, Menu.NONE, sort.title);
                }
            }
        }
    }

    public static Provider sourceOf(int itemId) {
        return itemId < SOURCE_ITEM_BASE ? null : Provider.ofSource(itemId - SOURCE_ITEM_BASE);
    }

    /** The "source" submenu: one checkable item per active source ({@link Sources}), in the user's order. */
    private static void fillSourceMenu(@NonNull Menu menu) {
        MenuItem source = menu.findItem(R.id.source);
        SubMenu sub = source == null ? null : source.getSubMenu();
        if (sub == null) {
            return;
        }
        for (Provider provider : Sources.active()) {
            sub.add(R.id.source_group, SOURCE_ITEM_BASE + provider.sourceId, Menu.NONE, provider.title);
        }
        sub.setGroupCheckable(R.id.source_group, true, true);
    }

    /** Item ids of the genre list's sort submenu: {@code GENRE_SORT_ITEM_BASE + GenreSort.ordinal()}. */
    private static final int GENRE_SORT_ITEM_BASE = Menu.FIRST + 200;

    /**
     * On a genre, author or performer list the "order" item sorts the loaded items locally: its site sorts give way
     * to {@link GenreSort}. People are listed by the site in order of popularity, and favorite genres are for genres.
     */
    private void fillGenreSortMenu(@NonNull Menu menu) {
        MenuItem order = menu.findItem(R.id.order);
        SubMenu sub = order == null ? null : order.getSubMenu();
        if (sub == null) {
            return;
        }
        sub.clear();
        boolean people = modelID != Consts.MODEL_GENRE;
        for (GenreSort sort : GenreSort.values()) {
            if (people && sort == GenreSort.FAVORITE) {
                continue;
            }
            int title = people && sort == GenreSort.NONE ? R.string.people_sort_popular : sort.title;
            sub.add(R.id.genre_sort_group, GENRE_SORT_ITEM_BASE + sort.ordinal(), Menu.NONE, title);
        }
        sub.setGroupCheckable(R.id.genre_sort_group, true, true);
        order.setVisible(true);
        setColorPrimeriTextInIconItemMenu(order, requireContext());
    }

    /**
     * A genre list's search narrows the loaded genres as one types (no site searches its genres). The query lives
     * in the presenter: it outlives the menu rebuilt on return from a genre, and is shown again there.
     */
    private void setGenreSearch(MenuItem item) {
        if (item == null) {
            return;
        }
        ActionBar actionBar = ((AppCompatActivity) requireActivity()).getSupportActionBar();
        SearchView searchView = new SearchView(actionBar != null ? actionBar.getThemedContext() : requireContext());
        searchView.setQueryHint(getString(R.string.search));
        item.setActionView(searchView);
        item.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS | MenuItem.SHOW_AS_ACTION_COLLAPSE_ACTION_VIEW);
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextChange(String s) {
                // leaving the screen clears the menu, which collapses the view: that is not the user's clearing
                if (isResumed()) {
                    getPresenter().onGenreQuery(s);
                }
                return true;
            }

            @Override
            public boolean onQueryTextSubmit(String s) {
                searchView.clearFocus();
                return true;
            }
        });
        String query = getPresenter().getGenreQuery();
        if (!query.isEmpty()) {
            // on the fragment's view: a collapsed action view is not attached, so it would never run its own posts
            requireView().post(() -> {
                if (isResumed()) {
                    item.expandActionView();
                    searchView.setQuery(query, false);
                    searchView.clearFocus();
                }
            });
        }
    }

    /** The filter item and its submenu items; false for any other item. */
    private boolean onFilterItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.filter) {
            // a ranking's item opens its submenu by itself; elsewhere the item is the duration filter
            if (mBar.periods.isEmpty()) {
                getPresenter().onDurationChipClick();
            }
            return true;
        }
        if (item.getItemId() == R.id.filter_duration) {
            getPresenter().onDurationChipClick();
            return true;
        }
        if (item.getGroupId() == R.id.filter_period_group) {
            getPresenter().onPeriodSelected(ListingFilters.Period.values()[item.getItemId() - Menu.FIRST]);
            return true;
        }
        return false;
    }

    /**
     * The duration dialog: presets in a dropdown over a two-thumb slider on {@link Duration#STOPS}. A preset moves
     * the thumbs; moved thumbs show the matching preset, or "custom range". On a TV the D-pad's centre opens the
     * presets, and the slider takes left/right as usual.
     */
    @Override
    public void showDurationPicker(@NonNull Duration current) {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        final Context context = builder.getContext();
        final DialogDurationFilterBinding dialog = DialogDurationFilterBinding.inflate(LayoutInflater.from(context));
        final List<Duration> presets = Duration.PRESETS;
        final String[] labels = new String[presets.size()];
        for (int i = 0; i < labels.length; i++) {
            labels[i] = context.getString(DURATION_PRESET_LABELS[i]);
        }
        dialog.durationPreset.setSimpleItems(labels);
        final RangeSlider slider = dialog.durationSlider;
        slider.setValueTo(Duration.STOPS.length - 1);
        slider.setMinSeparationValue(1);
        slider.setLabelFormatter(value -> minutesLabel(context, Duration.STOPS[Math.round(value)]));
        slider.setValues((float) current.fromStop(), (float) current.toStop());
        final Runnable sync = () -> {
            Duration picked = pickedDuration(slider);
            dialog.durationRange.setText(durationBounds(context, picked));
            int preset = presets.indexOf(picked);
            // no filtering: the list keeps every preset
            dialog.durationPreset.setText(preset >= 0 ? labels[preset] : context.getString(R.string.duration_custom),
                    false);
        };
        sync.run();
        slider.addOnChangeListener((s, value, fromUser) -> sync.run());
        dialog.durationPreset.setOnItemClickListener((parent, view, position, id) -> {
            Duration preset = presets.get(position);
            slider.setValues((float) preset.fromStop(), (float) preset.toStop());
        });
        dialog.durationPreset.setOnKeyListener((v, keyCode, event) -> {
            boolean confirm = keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER
                    || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER;
            if (!confirm || dialog.durationPreset.isPopupShowing()) {
                return false; // an open list takes the D-pad itself
            }
            if (event.getAction() == KeyEvent.ACTION_UP) {
                dialog.durationPreset.showDropDown();
            }
            return true;
        });
        builder.setTitle(R.string.filter_duration)
                .setView(dialog.getRoot())
                .setPositiveButton(R.string.apply,
                        (d, which) -> getPresenter().onDurationSelected(pickedDuration(slider)))
                .setNeutralButton(R.string.reset, (d, which) -> getPresenter().onDurationSelected(Duration.ANY))
                .show();
    }

    private static Duration pickedDuration(@NonNull RangeSlider slider) {
        List<Float> values = slider.getValues();
        return Duration.ofStops(Math.round(values.get(0)), Math.round(values.get(values.size() - 1)));
    }

    /** The range as the chip and the menu name it: a preset's own label, else its bounds, compactly. */
    private static String durationLabel(@NonNull Context context, @NonNull Duration duration) {
        int preset = Duration.PRESETS.indexOf(duration);
        if (preset >= 0) {
            return context.getString(DURATION_PRESET_LABELS[preset]);
        }
        if (duration.fromMinutes == 0) {
            return context.getString(R.string.duration_to, minutesLabel(context, duration.toMinutes));
        }
        if (duration.isUnbounded()) {
            return context.getString(R.string.duration_from, minutesLabel(context, duration.fromMinutes));
        }
        if (duration.toMinutes <= 60) {
            return context.getString(R.string.duration_minutes_range, duration.fromMinutes, duration.toMinutes);
        }
        if (duration.fromMinutes % 60 == 0 && duration.toMinutes % 60 == 0) {
            return context.getString(R.string.duration_hours_range, duration.fromMinutes / 60,
                    duration.toMinutes / 60);
        }
        return context.getString(R.string.duration_between, minutesLabel(context, duration.fromMinutes),
                minutesLabel(context, duration.toMinutes));
    }

    /** The range spelled out for the dialog's live label, e.g. "от 1 ч до 2 ч". */
    private static String durationBounds(@NonNull Context context, @NonNull Duration duration) {
        if (duration.isAny()) {
            return context.getString(R.string.duration_bounds_any);
        }
        if (duration.fromMinutes == 0) {
            return context.getString(R.string.duration_bounds_to, minutesLabel(context, duration.toMinutes));
        }
        if (duration.isUnbounded()) {
            return context.getString(R.string.duration_bounds_from, minutesLabel(context, duration.fromMinutes));
        }
        return context.getString(R.string.duration_bounds_between, minutesLabel(context, duration.fromMinutes),
                minutesLabel(context, duration.toMinutes));
    }

    /** "45 мин", "2 ч", "1 ч 30 мин"; the open end of the scale is "∞". */
    private static String minutesLabel(@NonNull Context context, int minutes) {
        if (minutes == Duration.UNBOUNDED) {
            return context.getString(R.string.duration_unbounded);
        }
        if (minutes < 60) {
            return context.getString(R.string.duration_minutes, minutes);
        }
        if (minutes % 60 == 0) {
            return context.getString(R.string.duration_hours, minutes / 60);
        }
        return context.getString(R.string.duration_hours_minutes, minutes / 60, minutes % 60);
    }

    private static int periodLabel(ListingFilters.Period period) {
        switch (period) {
            case DAY: return R.string.in_day;
            case THREE_DAYS: return R.string.in_3_days;
            case WEEK: return R.string.in_week;
            case TWO_WEEKS: return R.string.in_2_weeks;
            case MONTH: return R.string.in_month;
            case QUARTER: return R.string.in_quarter;
            case HALF_YEAR: return R.string.in_half_year;
            case YEAR: return R.string.in_year;
            default: return R.string.all_time;
        }
    }

    protected BooksPresenter getPresenter() {
        return mPresenter;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getPresenter().isInactiveSourceRoot()) {
            // its site was switched off (in the settings, maybe opened from a book): the app opens on the selected one
            recreate();
            return;
        }
        long parentalVersion = ParentalControl.version(requireContext());
        if (parentalVersion != mParentalVersion && mAddapterBooks != null) {
            mAddapterBooks.refreshItem();
        }
        mParentalVersion = parentalVersion;
        MainActivity activity = (MainActivity) getActivity();
        if (activity != null) {
            NavigationView navigationView = activity.getNavigationView();
            ArrayList<TextView> mTextViewArrayList = activity.getTextViewArrayList();
            final TypedValue SelectedValue = new TypedValue();
            activity.getTheme().resolveAttribute(R.attr.mySelectableItemBackground, SelectedValue,
                    true);
            switch (modelID) {
                case MODEL_BOOKS:
                    if (navigationView != null) {
                        navigationView.setCheckedItem(R.id.nav_audiobooks);
                    } else if (mTextViewArrayList != null && !mTextViewArrayList.isEmpty()) {
                        mTextViewArrayList.get(0).setBackgroundResource(SelectedValue.resourceId);
                    }
                    break;
                case MODEL_GENRE:
                    if (navigationView != null) {
                        navigationView.setCheckedItem(R.id.nav_genre);
                    } else if (mTextViewArrayList != null && mTextViewArrayList.size() > 1) {
                        mTextViewArrayList.get(1).setBackgroundResource(SelectedValue.resourceId);
                    }
                    break;
                case MODEL_AUTOR:
                    if (navigationView != null) {
                        navigationView.setCheckedItem(R.id.nav_autor);
                    } else if (mTextViewArrayList != null && mTextViewArrayList.size() > 2) {
                        mTextViewArrayList.get(2).setBackgroundResource(SelectedValue.resourceId);
                    }
                    break;
                case MODEL_ARTIST:
                    if (navigationView != null) {
                        navigationView.setCheckedItem(R.id.nav_artist);
                    } else if (mTextViewArrayList != null && mTextViewArrayList.size() > 3) {
                        mTextViewArrayList.get(3).setBackgroundResource(SelectedValue.resourceId);
                    }
                    break;
            }
        }
    }

    /** A list that ends on screen (a page fits a tablet's grid) leaves nothing to scroll for the next page. */
    private void fillScreen() {
        if (binding != null && modelID != Consts.MODEL_GENRE && !binding.list.canScrollVertically(1)) {
            getPresenter().loadToFill();
        }
    }

    private int getCount() {
        if (mAddapterBooks != null) {
            return mAddapterBooks.getItemCount();
        } else if (mAddapterGenre != null) {
            return mAddapterGenre.getItemCount();
        } else {
            return 0;
        }
    }

    private void setItemDecoration(int count) {
        int spacing = (int) getResources().getDimension(R.dimen.recycler_item_margin);
        boolean includeEdge = true;
        binding.list.addItemDecoration(new GridSpacingItemDecoration(count, spacing, includeEdge));
    }

    private void onItemSelected(View view12, int position) {
        mPresenter.onBookItemClick(view12, position);
    }
}
