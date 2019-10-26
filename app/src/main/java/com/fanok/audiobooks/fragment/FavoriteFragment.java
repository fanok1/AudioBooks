package com.fanok.audiobooks.fragment;

import static com.fanok.audiobooks.Consts.REQEST_CODE_SEARCH;
import static com.fanok.audiobooks.Consts.TABLE_FAVORITE;
import static com.fanok.audiobooks.Consts.TABLE_HISTORY;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.SearchView;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.arellomobile.mvp.MvpAppCompatFragment;
import com.arellomobile.mvp.presenter.InjectPresenter;
import com.arellomobile.mvp.presenter.ProvidePresenter;
import com.fanok.audiobooks.Consts;
import com.fanok.audiobooks.GridSpacingItemDecoration;
import com.fanok.audiobooks.R;
import com.fanok.audiobooks.activity.BookActivity;
import com.fanok.audiobooks.activity.MainActivity;
import com.fanok.audiobooks.activity.SearchableActivity;
import com.fanok.audiobooks.adapter.BooksListAddapter;
import com.fanok.audiobooks.interface_pacatge.favorite.FavoriteView;
import com.fanok.audiobooks.pojo.BookPOJO;
import com.fanok.audiobooks.presenter.FavoritePresenter;
import com.google.android.material.navigation.NavigationView;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Objects;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.Unbinder;

public class FavoriteFragment extends MvpAppCompatFragment implements FavoriteView {
    private static final String TAG = "FavoriteFragment";
    private static final String ARG_TITLE = "title";
    private static final String ARG_TABLE = "table";

    @BindView(R.id.list)
    RecyclerView mRecyclerView;
    @BindView(R.id.progressBar)
    LinearLayout mProgressBar;
    Unbinder unbinder;

    @InjectPresenter
    FavoritePresenter mPresenter;
    @BindView(R.id.view)
    View mView;

    private BooksListAddapter mAddapterBooks;

    private int titleId;
    private int table;

    @ProvidePresenter
    FavoritePresenter provide() {
        Bundle arg = getArguments();
        if (arg != null) {
            table = arg.getInt(ARG_TABLE, 0);
        }
        return new FavoritePresenter(Objects.requireNonNull(getContext()), table);
    }

    public static FavoriteFragment newInstance(int title, int table) {
        FavoriteFragment fragment = new FavoriteFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_TITLE, title);
        args.putInt(ARG_TABLE, table);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate: called");
        super.onCreate(savedInstanceState);
        Bundle arg = getArguments();
        if (arg != null) {
            titleId = arg.getInt(ARG_TITLE, 0);
            table = arg.getInt(ARG_TABLE, 0);
        }
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_favorite, container, false);
        unbinder = ButterKnife.bind(this, view);

        if (titleId != 0) {
            Objects.requireNonNull(getActivity()).setTitle(titleId);
        }
        int orientation = this.getResources().getConfiguration().orientation;
        if (orientation == Configuration.ORIENTATION_PORTRAIT) {
            setLayoutManager();
        } else {
            setLayoutManager(2);
        }

        mAddapterBooks = new BooksListAddapter();
        mRecyclerView.setAdapter(mAddapterBooks);
        setHasOptionsMenu(true);
        mAddapterBooks.setListener(
                (view12, position) -> mPresenter.onBookItemClick(view12, position));

        mAddapterBooks.setLongListener(
                (view13, position) -> mPresenter.onBookItemLongClick(view13, position,
                        getLayoutInflater()));
        getPresenter().setView(mView);
        return view;
    }


    @Override
    public void onDestroyView() {
        getPresenter().onDestroy();
        super.onDestroyView();
        unbinder.unbind();
    }

    @Override
    public void setLayoutManager() {
        mRecyclerView.setLayoutManager(new LinearLayoutManager(this.getContext()));
        setItemDecoration(1);
    }

    @Override
    public void setLayoutManager(int count) {
        mRecyclerView.setLayoutManager(new GridLayoutManager(this.getContext(), count));
        setItemDecoration(count);
    }

    private void setItemDecoration(int count) {
        int spacing = (int) getResources().getDimension(R.dimen.recycler_item_margin);
        boolean includeEdge = true;
        mRecyclerView.addItemDecoration(new GridSpacingItemDecoration(count, spacing, includeEdge));
    }

    @Override
    public void showData(@NonNull ArrayList<BookPOJO> bookPOJOS) {
        if (mAddapterBooks != null) mAddapterBooks.setItem(bookPOJOS);
    }


    @Override
    public void clearData() {
        if (mAddapterBooks != null) mAddapterBooks.clearItem();
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
    public void showProgres(boolean b) {
        if (b) {
            mProgressBar.setVisibility(View.VISIBLE);
        } else {
            mProgressBar.setVisibility(View.GONE);
        }
    }

    @Override
    public void setSubTitle(@NotNull String text) {
        MainActivity mainActivity = (MainActivity) getActivity();
        if (mainActivity != null) {
            Objects.requireNonNull(mainActivity.getSupportActionBar()).setSubtitle(text);
        }
    }


    @Override
    public void showFragment(@NonNull Fragment fragment, @NonNull String tag) {
        MainActivity activity = (MainActivity) getActivity();
        if (activity != null) {
            activity.showFragment(fragment, tag);
        }
    }

    protected FavoritePresenter getPresenter() {
        return mPresenter;
    }

    @Override
    public void onResume() {
        super.onResume();
        MainActivity activity = (MainActivity) getActivity();
        if (activity != null) {
            NavigationView navigationView = activity.getNavigationView();
            switch (table) {
                case TABLE_FAVORITE:
                    navigationView.setCheckedItem(R.id.nav_favorite);
                    break;
                case TABLE_HISTORY:
                    navigationView.setCheckedItem(R.id.nav_history);
                    break;
            }
        }
    }

    @Override
    public void onCreateOptionsMenu(@NotNull Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.favorite_options_menu, menu);
        MenuItem item = menu.findItem(R.id.action_search);
        Consts.setColorPrimeriTextInIconItemMenu(item, Objects.requireNonNull(getContext()));

        SearchView searchView = (SearchView) item.getActionView();
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String s) {
                return false;
            }

            @Override
            public boolean onQueryTextChange(String s) {
                getPresenter().onSearch(s);
                return false;
            }
        });

        if (table == TABLE_FAVORITE) {
            menu.findItem(R.id.order).setVisible(true);
            menu.findItem(R.id.filter).setVisible(true);

            SharedPreferences pref = PreferenceManager
                    .getDefaultSharedPreferences(getContext());

            String sort = pref.getString("pref_sort_favorite", getString(R.string.sort_value_date));
            if (getString(R.string.sort_value_name).equals(sort)) {
                menu.findItem(R.id.name).setChecked(true);
            } else if (getString(R.string.sort_value_genre).equals(sort)) {
                menu.findItem(R.id.genre).setChecked(true);
            } else if (getString(R.string.sort_value_autor).equals(sort)) {
                menu.findItem(R.id.autor).setChecked(true);
            } else if (getString(R.string.sort_value_artist).equals(sort)) {
                menu.findItem(R.id.artist).setChecked(true);
            } else if (getString(R.string.sort_value_series).equals(sort)) {
                menu.findItem(R.id.series).setChecked(true);
            } else {
                menu.findItem(R.id.date).setChecked(true);
            }

        }


        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        mPresenter.onOptionsItemSelected(item.getItemId());
        item.setChecked(true);
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void showBooksActivity(@NotNull @NonNull BookPOJO bookPOJO) {
        BookActivity.startNewActivity(Objects.requireNonNull(getContext()), bookPOJO);
    }

    @Override
    public void showSearchActivity(int modelId) {
        Intent intent = new Intent(getContext(), SearchableActivity.class);
        intent.putExtra(Consts.ARG_MODEL, modelId);
        startActivityForResult(intent, REQEST_CODE_SEARCH);
    }
}