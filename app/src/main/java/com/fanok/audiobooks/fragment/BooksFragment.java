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
import com.fanok.audiobooks.search.ListingFilters;
import com.fanok.audiobooks.search.ListingFilters.Duration;
import com.google.android.material.navigation.NavigationView;
import com.google.android.material.slider.RangeSlider;
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

                int menuResId;
                if (mUrl.contains(Url.SERVER_IZIBUK)) {
                    menuResId = R.menu.books_izibuk_options_menu;
                } else if (mUrl.contains(Url.SERVER_BAZA_KNIG)) {
                    menuResId = R.menu.books_baza_knig_options_menu;
                } else if (mUrl.contains(Url.SERVER_AUDIOBOO)) {
                    menuResId = R.menu.books_audioboo_options_menu;
                } else if (mUrl.contains(Url.SERVER_AKNIGA)) {
                    menuResId = R.menu.books_akniga_options_menu;
                } else if (mUrl.contains(Url.SERVER_OTRUB)) {
                    menuResId = R.menu.books_otrub_options_menu;
                } else if (mUrl.contains(Url.SERVER_POLEKNIG)) {
                    menuResId = R.menu.books_poleknig_options_menu;
                } else {
                    menuResId = R.menu.books_options_menu;
                }
                inflater.inflate(menuResId, menu);

                if (modelID != Consts.MODEL_BOOKS || mUrl.contains("reader") || mUrl.contains("author")
                        || mUrl.contains("genre") || mUrl.contains("serie") || mUrl.contains("performer")) {
                    MenuItem sourceItem = menu.findItem(R.id.source);
                    if (sourceItem != null) sourceItem.setVisible(false);
                } else {
                    setColorPrimeriTextInIconItemMenu(menu.findItem(R.id.source), requireContext());
                }

                if (modelID != Consts.MODEL_BOOKS || mUrl.contains("reader") || mUrl.contains("author") ||
                        mUrl.contains("serie") || mUrl.contains("cikl") ||
                        (mUrl.contains(Url.SERVER_ABMP3) && mUrl.contains("genre")) ||
                        (mUrl.contains(Url.SERVER_IZIBUK) && mUrl.contains("genre")) ||
                        (mUrl.contains(Url.SERVER_BAZA_KNIG) && mUrl.contains("ispolnitel")) ||
                        false)  {
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
                MenuItem item = null;
                switch (Consts.getSOURCE()) {
                    case Consts.SOURCE_ABOOK:
                        item = menu.findItem(R.id.source_abook);
                        break;
                    case Consts.SOURCE_KNIGA_V_UHE:
                        item = menu.findItem(R.id.source_kniga_v_uhe);
                        break;
                    case Consts.SOURCE_IZI_BUK:
                        item = menu.findItem(R.id.source_izi_book);
                        break;
                    case Consts.SOURCE_AUDIO_BOOK_MP3:
                        item = menu.findItem(R.id.source_audio_book_mp3);
                        break;
                    case Consts.SOURCE_BAZA_KNIG:
                        item = menu.findItem(R.id.source_baza_knig);
                        break;
                    case Consts.SOURCE_KNIGOBLUD:
                        item = menu.findItem(R.id.source_knigoblud);
                        break;
                    case Consts.SOURCE_AUDIOBOO:
                        item = menu.findItem(R.id.source_audioboo);
                        break;
                    case Consts.SOURCE_OTRUB:
                        item = menu.findItem(R.id.source_otrub);
                        break;
                    case Consts.SOURCE_POLEKNIG:
                        item = menu.findItem(R.id.source_poleknig);
                        break;
                }
                if (item != null) {
                    item.setChecked(true);
                }
                // the listing's own site decides its sort items, not the selected source: an author, reader or
                // genre listing of another site opens from a book description
                boolean isKnigoblud = mUrl.contains(Url.SERVER_KNIGOBLUD);
                MenuItem todayItem = menu.findItem(R.id.knigoblud_today);
                if (todayItem != null) todayItem.setVisible(isKnigoblud);
                MenuItem trendingItem = menu.findItem(R.id.knigoblud_trending);
                if (trendingItem != null) trendingItem.setVisible(isKnigoblud);
                MenuItem finishedItem = menu.findItem(R.id.knigoblud_finished);
                if (finishedItem != null) finishedItem.setVisible(isKnigoblud);
                MenuItem retingParent = menu.findItem(R.id.reting);
                if (retingParent != null) retingParent.setVisible(!isKnigoblud);
                // only ever hidden here: onCreateMenu already hid Order where the listing takes no sort
                MenuItem orderItem = menu.findItem(R.id.order);
                if (orderItem != null && mUrl.contains(Url.SERVER_IZIBUK)) orderItem.setVisible(false);
                boolean isAbmp3 = mUrl.contains(Url.SERVER_ABMP3);
                MenuItem popularParent = menu.findItem(R.id.popular_parent);
                if (popularParent != null) popularParent.setVisible(!isAbmp3);
                MenuItem retingParent2 = menu.findItem(R.id.reting);
                if (retingParent2 != null) retingParent2.setVisible(!isAbmp3 && !isKnigoblud);
                MenuItem abmp3Top100 = menu.findItem(R.id.abmp3_top100);
                if (abmp3Top100 != null) abmp3Top100.setVisible(isAbmp3);
                MenuItem abmp3Random = menu.findItem(R.id.abmp3_random);
                if (abmp3Random != null) abmp3Random.setVisible(isAbmp3);
                prepareFilterMenu(menu);
                            }

                            @Override
                            public boolean onMenuItemSelected(@NonNull MenuItem item) {
                if (!onFilterItemSelected(item)) {
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
                int lastCompletelyVisibleItemPosition =
                        ((LinearLayoutManager) Objects.requireNonNull(
                                recyclerView.getLayoutManager())).findLastVisibleItemPosition();
                if (modelID != Consts.MODEL_GENRE
                        && lastCompletelyVisibleItemPosition > getCount() - 15 && dy > 0) {
                    getPresenter().loadBoks();
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
