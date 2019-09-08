package com.fanok.audiobooks.activity;

import android.app.Activity;
import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.provider.SearchRecentSuggestions;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.arellomobile.mvp.MvpAppCompatActivity;
import com.arellomobile.mvp.presenter.InjectPresenter;
import com.fanok.audiobooks.Consts;
import com.fanok.audiobooks.GridSpacingItemDecoration;
import com.fanok.audiobooks.MySuggestionProvider;
import com.fanok.audiobooks.R;
import com.fanok.audiobooks.adapter.BooksListAddapter;
import com.fanok.audiobooks.adapter.GenreListAddapter;
import com.fanok.audiobooks.adapter.SearchebleAdapter;
import com.fanok.audiobooks.interface_pacatge.searchable.SearchableView;
import com.fanok.audiobooks.pojo.BookPOJO;
import com.fanok.audiobooks.pojo.GenrePOJO;
import com.fanok.audiobooks.pojo.SearcheblPOJO;
import com.fanok.audiobooks.presenter.SearchbalePresenter;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Objects;

import butterknife.BindView;
import butterknife.ButterKnife;

public class SearchableActivity extends MvpAppCompatActivity implements SearchableView {
    private static final String TAG = "SearchableActivity";

    @BindView(R.id.searchView)
    SearchView mSearchView;
    @BindView(R.id.list)
    RecyclerView mRecyclerView;

    @InjectPresenter
    SearchbalePresenter mPresenter;
    @BindView(R.id.progressBar)
    LinearLayout mProgressBar;
    @BindView(R.id.autors)
    TextView mAutors;
    @BindView(R.id.authorList)
    RecyclerView mAuthorList;
    @BindView(R.id.series)
    TextView mSeries;
    @BindView(R.id.seriesList)
    RecyclerView mSeriesList;
    @BindView(R.id.progressBarTop)
    ProgressBar mProgressBarTop;
    @BindView(R.id.topList)
    LinearLayout mTopList;
    private int modelId;

    private BooksListAddapter mAddapterBooks;
    private GenreListAddapter mAddapterGenre;

    private SearchebleAdapter mAdapterAutors;
    private SearchebleAdapter mAdapterSeries;


    private String query;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate: called");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_searchable);
        ButterKnife.bind(this);
        Intent intent = getIntent();
        modelId = intent.getIntExtra(Consts.ARG_MODEL, -1);
        if (modelId == -1) throw new IllegalArgumentException("modelId require parameter");


        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        mSearchView.onActionViewExpanded();
        mSearchView.setIconified(false);


        SearchManager searchManager = (SearchManager) getSystemService(Context.SEARCH_SERVICE);
        mSearchView.setSearchableInfo(searchManager.getSearchableInfo(getComponentName()));


        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);


        int orientation = this.getResources().getConfiguration().orientation;
        if (orientation == Configuration.ORIENTATION_PORTRAIT) {
            setLayoutManager();
        } else {
            setLayoutManager(2);
        }

        switch (modelId) {
            case Consts.MODEL_BOOKS:
                mAddapterBooks = new BooksListAddapter();
                mRecyclerView.setAdapter(mAddapterBooks);
                mAdapterAutors = new SearchebleAdapter();
                mAdapterSeries = new SearchebleAdapter();
                mAuthorList.setLayoutManager(
                        new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
                mSeriesList.setLayoutManager(
                        new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
                mAuthorList.setAdapter(mAdapterAutors);
                mSeriesList.setAdapter(mAdapterSeries);
                break;
            case Consts.MODEL_GENRE:
            case Consts.MODEL_AUTOR:
            case Consts.MODEL_ARTIST:
                mAddapterGenre = new GenreListAddapter();
                mRecyclerView.setAdapter(mAddapterGenre);
                break;
        }

        if (savedInstanceState == null) {
            mPresenter.onCreate(modelId, this);
        }

        mRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                int lastCompletelyVisibleItemPosition =
                        ((LinearLayoutManager) Objects.requireNonNull(
                                recyclerView.getLayoutManager())).findLastVisibleItemPosition();
                if (modelId != Consts.MODEL_GENRE
                        && lastCompletelyVisibleItemPosition > getCount() - 3 && dy > 0) {
                    mPresenter.loadNext(query);
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
                    (view13, position) -> mPresenter.onBookItemLongClick(view13, position));
        }

        mAdapterSeries.setListener(
                (view, position) -> mPresenter.onSeriesListItemClick(view, position));

        mAdapterAutors.setListener(
                (view, position) -> mPresenter.onAutorsListItemClick(view, position));


        mRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
                int y = recyclerView.computeVerticalScrollOffset();
                if (y == 0 && mTopList.getVisibility() == View.GONE) {
                    mTopList.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                if (dy > 0 && mTopList.getVisibility() == View.VISIBLE) {
                    mTopList.setVisibility(View.GONE);
                }
            }
        });

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
        super.onDestroy();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) finish();
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (Intent.ACTION_SEARCH.equals(intent.getAction())) {
            query = intent.getStringExtra(SearchManager.QUERY);
            SearchRecentSuggestions suggestions = new SearchRecentSuggestions(this,
                    MySuggestionProvider.AUTHORITY, MySuggestionProvider.MODE);
            suggestions.saveRecentQuery(query, null);
            mSearchView.setQuery(query, false);
            mPresenter.loadBoks(query);
        }
        mRecyclerView.requestFocus();
    }

    @Override
    public void setLayoutManager() {
        mRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        setItemDecoration(1);
    }

    @Override
    public void setLayoutManager(int count) {
        mRecyclerView.setLayoutManager(new GridLayoutManager(this, count));
        setItemDecoration(count);
    }

    private void setItemDecoration(int count) {
        int spacing = (int) getResources().getDimension(R.dimen.recycler_item_margin);
        boolean includeEdge = true;
        mRecyclerView.addItemDecoration(new GridSpacingItemDecoration(count, spacing, includeEdge));
    }

    @Override
    public void showData(ArrayList arrayList) {
        try {
            if (arrayList.size() != 0) {
                if (arrayList.get(0) instanceof BookPOJO) {
                    mAddapterBooks.setItem(arrayList);
                } else if (arrayList.get(0) instanceof GenrePOJO) {
                    mAddapterGenre.setItem(arrayList);
                }
            }
        } catch (NullPointerException e) {
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
    public void showProgres(boolean b) {
        if (b) {
            mProgressBar.setVisibility(View.VISIBLE);
        } else {
            mProgressBar.setVisibility(View.GONE);
        }
    }

    @Override
    public void showProgresTop(boolean b) {
        if (b) {
            mProgressBarTop.setVisibility(View.VISIBLE);
        } else {
            mProgressBarTop.setVisibility(View.INVISIBLE);
        }
    }

    @Override
    public void returnResult(String url, String name, int modelId, String tag) {
        Intent intent = new Intent();
        intent.putExtra("url", url);
        intent.putExtra("name", name);
        intent.putExtra("modelId", modelId);
        intent.putExtra("tag", tag);
        setResult(Activity.RESULT_OK, intent);
        finish();
    }

    @Override
    public void showSeriesAndAutors(@NonNull SearcheblPOJO searcheblPOJO) {
        if (mAdapterSeries != null && mAdapterAutors != null) {
            if (searcheblPOJO.getAutorsList().size() == 0) {
                mAutors.setVisibility(View.GONE);
                mAuthorList.setVisibility(View.GONE);
            } else {
                mAutors.setVisibility(View.VISIBLE);
                mAuthorList.setVisibility(View.VISIBLE);
                mAutors.setText(searcheblPOJO.getAutorsCount());
                mAdapterAutors.setItem(searcheblPOJO.getAutorsList());
            }

            if (searcheblPOJO.getSeriesList().size() == 0) {
                mSeries.setVisibility(View.GONE);
                mSeriesList.setVisibility(View.GONE);
            } else {
                mSeries.setVisibility(View.VISIBLE);
                mSeriesList.setVisibility(View.VISIBLE);
                mSeries.setText(searcheblPOJO.getSeriesCount());
                mAdapterSeries.setItem(searcheblPOJO.getSeriesList());
            }
        }

    }

    @Override
    public void startBookActivity(@NotNull @NonNull BookPOJO bookPOJO) {
        BookActivity.startNewActivity(this, bookPOJO);
    }
}
