package com.fanok.audiobooks.activity;

import static com.fanok.audiobooks.Consts.handleUserInput;

import android.app.Activity;
import android.app.SearchManager;
import android.app.UiModeManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Bundle;
import android.provider.SearchRecentSuggestions;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageButton;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.widget.Toolbar;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.arellomobile.mvp.MvpAppCompatActivity;
import com.arellomobile.mvp.presenter.InjectPresenter;
import com.arellomobile.mvp.presenter.ProvidePresenter;
import com.fanok.audiobooks.Consts;
import com.fanok.audiobooks.GridSpacingItemDecoration;
import com.fanok.audiobooks.LocaleManager;
import com.fanok.audiobooks.MySuggestionProvider;
import com.fanok.audiobooks.R;
import com.fanok.audiobooks.Url;
import com.fanok.audiobooks.adapter.BooksListAddapter;
import com.fanok.audiobooks.adapter.FilterAdapter;
import com.fanok.audiobooks.adapter.GenreListAddapter;
import com.fanok.audiobooks.adapter.SearchebleAdapter;
import com.fanok.audiobooks.databinding.ActivitySearchableBinding;
import com.fanok.audiobooks.interface_pacatge.searchable.SearchableView;
import com.fanok.audiobooks.pojo.BookPOJO;
import com.fanok.audiobooks.pojo.GenrePOJO;
import com.fanok.audiobooks.pojo.SearcheblPOJO;
import com.fanok.audiobooks.presenter.SearchbalePresenter;
import java.util.ArrayList;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;

public class SearchableActivity extends MvpAppCompatActivity implements SearchableView {

    private static final String TAG = "SearchableActivity";

    @InjectPresenter
    SearchbalePresenter mPresenter;

    private int mModelId;

    private ActivitySearchableBinding binding;

    private BooksListAddapter mAddapterBooks;

    private GenreListAddapter mAddapterGenre;

    private SearchebleAdapter mAdapterAutors;

    private SearchebleAdapter mAdapterSeries;

    private FilterAdapter mFilterAdapter;


    @ProvidePresenter
    SearchbalePresenter provide() {
        Intent intent = getIntent();
        int model = intent.getIntExtra(Consts.ARG_MODEL, -1);
        if (model == -1) throw new IllegalArgumentException("ModelId require parameter");
        return new SearchbalePresenter(model, getApplicationContext());
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate: called");
        super.onCreate(savedInstanceState);
        binding = ActivitySearchableBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        SharedPreferences pref = PreferenceManager
                .getDefaultSharedPreferences(this);

        String themeName = pref.getString("pref_theme", getString(R.string.theme_dark_value));
        if (themeName.equals(getString(R.string.theme_dark_value))) {
            setTheme(R.style.AppTheme_SwipeOnClose);
        } else if (themeName.equals(getString(R.string.theme_light_value))) {
            setTheme(R.style.LightAppTheme_SwipeOnClose);
        } else if (themeName.equals(getString(R.string.theme_black_value))) {
            setTheme(R.style.AppThemeBlack_SwipeOnClose);
        }

        Intent intent = getIntent();
        mModelId = intent.getIntExtra(Consts.ARG_MODEL, -1);
        if (mModelId == -1) {
            throw new IllegalArgumentException("mModelId require parameter");
        }

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        binding.searchView.onActionViewExpanded();
        binding.searchView.setIconified(false);

        SearchManager searchManager = (SearchManager) getSystemService(Context.SEARCH_SERVICE);
        if (searchManager != null) {
            binding.searchView.setSearchableInfo(searchManager.getSearchableInfo(getComponentName()));
        }

        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);


        int orientation = this.getResources().getConfiguration().orientation;

        int isTablet = getResources().getInteger(R.integer.isTablet);

        if (isTablet == 0) {
            if (orientation == Configuration.ORIENTATION_PORTRAIT) {
                setLayoutManager();
            } else {
                setLayoutManager(2);
            }
        }
        if (isTablet == 2) {
            if (orientation == Configuration.ORIENTATION_PORTRAIT) {
                setLayoutManager(2);
            } else {
                setLayoutManager(4);
            }
        } else if (isTablet == 1) {
            if (orientation == Configuration.ORIENTATION_PORTRAIT) {
                setLayoutManager(2);
            } else {
                setLayoutManager(3);
            }
        }

        switch (mModelId) {
            case Consts.MODEL_BOOKS:
                mAddapterBooks = new BooksListAddapter();
                binding.list.setAdapter(mAddapterBooks);
                mAdapterAutors = new SearchebleAdapter();
                mAdapterSeries = new SearchebleAdapter();
                binding.authorList.setLayoutManager(
                        new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
                binding.seriesList.setLayoutManager(
                        new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
                binding.authorList.setAdapter(mAdapterAutors);
                binding.seriesList.setAdapter(mAdapterSeries);
                mFilterAdapter = new FilterAdapter(
                        PreferenceManager.getDefaultSharedPreferences(getApplicationContext()));
                mFilterAdapter.setListener((view, position) -> {
                    mPresenter.setFilter(mFilterAdapter.getItem(position));
                    mPresenter.filterBooks();
                    mPresenter.filterAutorsAndSeries();
                });
                binding.filter.setLayoutManager(
                        new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
                binding.filter.setAdapter(mFilterAdapter);

                break;
            case Consts.MODEL_GENRE:
            case Consts.MODEL_AUTOR:
            case Consts.MODEL_ARTIST:
                mAddapterGenre = new GenreListAddapter();
                binding.list.setAdapter(mAddapterGenre);
                binding.filter.setVisibility(View.GONE);
                binding.progressBarTop.setVisibility(View.GONE);
                break;
        }

        binding.list.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                int lastCompletelyVisibleItemPosition =
                        ((LinearLayoutManager) Objects.requireNonNull(
                                recyclerView.getLayoutManager())).findLastVisibleItemPosition();
                if (mModelId != Consts.MODEL_GENRE
                        && lastCompletelyVisibleItemPosition > getCount() - 3 && dy > 0) {
                    mPresenter.loadNext();
                }
            }
        });

        if (mAddapterGenre != null) {
            mAddapterGenre.setClickListner(
                    (view1, position) -> mPresenter.onGenreItemClick(view1, position));
        }

        if (mAddapterBooks != null) {
            mAddapterBooks.setListener(
                    (view12, position) -> mPresenter.onBookItemClick(view12, position));

            mAddapterBooks.setLongListener(
                    (view13, position) -> mPresenter.onBookItemLongClick(view13, position,
                            getLayoutInflater()));
        }


        if (mAdapterSeries != null) {
            mAdapterSeries.setListener(
                    (view, position) -> mPresenter.onSeriesListItemClick(view, position));
        }


        if (mAdapterAutors != null) {
            mAdapterAutors.setListener(
                    (view, position) -> mPresenter.onAutorsListItemClick(view, position));
        }

        UiModeManager uiModeManager = (UiModeManager) getSystemService(Context.UI_MODE_SERVICE);
        if (uiModeManager == null || uiModeManager.getCurrentModeType() != Configuration.UI_MODE_TYPE_TELEVISION) {
            binding.list.addOnScrollListener(new RecyclerView.OnScrollListener() {
                @Override
                public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                    super.onScrollStateChanged(recyclerView, newState);
                    int y = recyclerView.computeVerticalScrollOffset();
                    boolean b = mAdapterAutors != null && mAdapterAutors.getItemCount() != 0;
                    if (mAdapterSeries != null && mAdapterSeries.getItemCount() != 0) {
                        b = true;
                    }

                    if (y == 0 && binding.topList.getVisibility() == View.GONE && b) {
                        binding.topList.setVisibility(View.VISIBLE);
                    }
                }

                @Override
                public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                    super.onScrolled(recyclerView, dx, dy);
                    if (dy > 0 && binding.topList.getVisibility() == View.VISIBLE) {
                        binding.topList.setVisibility(View.GONE);
                    }
                }
            });
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

    @Override
    protected void onDestroy() {
        mPresenter.onDestroy();
        if (mAddapterBooks != null) {
            mAddapterBooks.close();
        }
        mAddapterBooks = null;
        mAddapterGenre = null;
        mAdapterAutors = null;
        mAdapterSeries = null;
        super.onDestroy();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) finish();
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onNewIntent(@NonNull Intent intent) {
        super.onNewIntent(intent);
        if (Intent.ACTION_SEARCH.equals(intent.getAction())) {
            String query = intent.getStringExtra(SearchManager.QUERY);
            if (query != null) {
                SearchRecentSuggestions suggestions = new SearchRecentSuggestions(this,
                        MySuggestionProvider.AUTHORITY, MySuggestionProvider.MODE);
                suggestions.saveRecentQuery(query, null);
                binding.searchView.setQuery(query, false);
                mPresenter.setQuery(query);
                mPresenter.loadBoks();
            }
        }
        binding.list.requestFocus();
    }

    @Override
    public Resources.Theme getTheme() {
        Resources.Theme theme = super.getTheme();

        SharedPreferences pref = PreferenceManager
                .getDefaultSharedPreferences(this);

        String themeName = pref.getString("pref_theme", getString(R.string.theme_dark_value));
        if (themeName.equals(getString(R.string.theme_dark_value))) {
            theme.applyStyle(R.style.AppTheme_SwipeOnClose, true);
        } else if (themeName.equals(getString(R.string.theme_light_value))) {
            theme.applyStyle(R.style.LightAppTheme_SwipeOnClose, true);
        } else if (themeName.equals(getString(R.string.theme_black_value))) {
            theme.applyStyle(R.style.AppThemeBlack_SwipeOnClose, true);
        }

        return theme;
    }

    @Override
    public void setLayoutManager(int count) {
        binding.list.setLayoutManager(new GridLayoutManager(this, count));
        setItemDecoration(count);
    }

    @Override
    public void setLayoutManager() {
        binding.list.setLayoutManager(new LinearLayoutManager(this));
        setItemDecoration(1);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        View currentFocus = getCurrentFocus();
        if (currentFocus!=null){
            if(keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                if (isUpButton(currentFocus)) {
                    Toolbar toolbar = findViewById(R.id.toolbar);
                    toolbar.requestFocus();
                }
                if (currentFocus.getId()==R.id.toolbar){
                    View view = findViewById(R.id.filter);
                    if(view!=null){
                        view.requestFocus();
                    }
                }
            }else if(keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                if (currentFocus.getId()==R.id.filter||currentFocus.getId()==R.id.toolbar) {
                    View menuItem = findViewById(R.id.searchView);
                    if (menuItem != null) {
                        menuItem.requestFocus();
                    }
                }
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    private boolean isUpButton(View view) {
        if (view instanceof ImageButton) {
            return "Перейти вверх".contentEquals(view.getContentDescription());
        }
        return false;
    }


    @Override
    public void showDataBooks(ArrayList<BookPOJO> books) {
        try {
            if (mAddapterBooks != null) {
                if (books != null && !books.isEmpty()) {
                    mAddapterBooks.setItem(books);
                } else {
                    // Если пришел пустой список, очищаем адаптер
                    mAddapterBooks.clearItem();
                }
            }
        } catch (Exception e) {
            Log.e("showData", "Error displaying book data", e);
            showToast(R.string.error_display_data);
        }
    }

    @Override
    public void showDataGenres(ArrayList<GenrePOJO> genres) {
        try {
            if (mAddapterGenre != null) {
                if (genres != null && !genres.isEmpty()) {
                    mAddapterGenre.setItem(genres);
                } else {
                    // Если пришел пустой список, очищаем адаптер
                    mAddapterGenre.clearItem();
                }
            }
        } catch (Exception e) {
            Log.e("showData", "Error displaying genre data", e);
            showToast(R.string.error_display_data);
        }
    }

    @Override
    public void clearData() {
        if (mAddapterBooks != null) mAddapterBooks.clearItem();
        if (mAddapterGenre != null) mAddapterGenre.clearItem();
    }

    @Override
    public void showToast(int message) {
        Toast.makeText(this, getResources().getText(message),
                Toast.LENGTH_LONG).show();
    }

    @Override
    public void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    @Override
    public void setNotFoundVisibile(boolean b) {
        if (b) {
            binding.booksNotFound.setVisibility(View.VISIBLE);
        } else {
            binding.booksNotFound.setVisibility(View.GONE);
        }
    }

    @Override
    public void showProgres(boolean b) {
        if (b) {
            binding.progressBarLayout.getRoot().setVisibility(View.VISIBLE);
        } else {
            binding.progressBarLayout.getRoot().setVisibility(View.GONE);
        }
    }

    @Override
    public void returnResult(String url, String name, int modelId, String tag) {
        Intent intent = new Intent();
        intent.putExtra("url", url);
        intent.putExtra("name", name);
        intent.putExtra("mModelId", modelId);
        intent.putExtra("tag", tag);
        setResult(Activity.RESULT_OK, intent);
        finish();
    }

    @Override
    public void showProgresTop(boolean b) {
        if (b) {
            binding.progressBarTop.setVisibility(View.VISIBLE);
        } else {
            binding.progressBarTop.setVisibility(View.INVISIBLE);
        }
    }

    @Override
    public void showSeriesAndAutors(SearcheblPOJO searcheblPOJO) {
        if (mAdapterSeries != null && mAdapterAutors != null && searcheblPOJO != null) {
            if (searcheblPOJO.getAutorsList().isEmpty()) {
                binding.autors.setVisibility(View.GONE);
                binding.authorList.setVisibility(View.GONE);
            } else {
                binding.autors.setVisibility(View.VISIBLE);
                binding.authorList.setVisibility(View.VISIBLE);
                binding.autors.setText(searcheblPOJO.getAutorsCount());
                mAdapterAutors.setItem(searcheblPOJO.getAutorsList());
            }

            if (searcheblPOJO.getSeriesList().isEmpty()) {
                binding.series.setVisibility(View.GONE);
                binding.seriesList.setVisibility(View.GONE);
            } else {
                binding.series.setVisibility(View.VISIBLE);
                binding.seriesList.setVisibility(View.VISIBLE);
                binding.series.setText(searcheblPOJO.getSeriesCount());
                mAdapterSeries.setItem(searcheblPOJO.getSeriesList());
            }
        } else {
            binding.topList.setVisibility(View.GONE);
        }

    }

    @Override
    public void startBookActivity(@NotNull @NonNull BookPOJO bookPOJO) {

        if (bookPOJO.getUrl().contains(Url.SERVER_ABMP3)) {
            SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
            if (preferences.getBoolean("speed_up_search_abmp3", false)) {
                Intent intent = new Intent(this, LoadBook.class);
                intent.putExtra("url", bookPOJO.getUrl());
                startActivity(intent);
            } else {
                BookActivity.startNewActivity(this, bookPOJO);
            }
        } else {
            BookActivity.startNewActivity(this, bookPOJO);
        }
    }

    private void setItemDecoration(int count) {
        int spacing = (int) getResources().getDimension(R.dimen.recycler_item_margin);
        boolean includeEdge = true;
        binding.list.addItemDecoration(new GridSpacingItemDecoration(count, spacing, includeEdge));
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(LocaleManager.onAttach(base));
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            if (handleUserInput(getApplicationContext(), event.getKeyCode())) {
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }


}
