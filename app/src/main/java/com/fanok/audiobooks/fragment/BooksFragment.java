package com.fanok.audiobooks.fragment;

import static com.fanok.audiobooks.Consts.MODEL_ARTIST;
import static com.fanok.audiobooks.Consts.MODEL_AUTOR;
import static com.fanok.audiobooks.Consts.MODEL_BOOKS;
import static com.fanok.audiobooks.Consts.MODEL_GENRE;
import static com.fanok.audiobooks.Consts.setColorPrimeriTextInIconItemMenu;

import android.content.res.Configuration;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.NavigationView;
import android.support.v4.app.Fragment;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.arellomobile.mvp.MvpAppCompatFragment;
import com.arellomobile.mvp.presenter.InjectPresenter;
import com.fanok.audiobooks.Consts;
import com.fanok.audiobooks.GridSpacingItemDecoration;
import com.fanok.audiobooks.R;
import com.fanok.audiobooks.activity.MainActivity;
import com.fanok.audiobooks.adapter.BooksListAddapter;
import com.fanok.audiobooks.adapter.GenreListAddapter;
import com.fanok.audiobooks.interface_pacatge.books.BooksView;
import com.fanok.audiobooks.pojo.BookPOJO;
import com.fanok.audiobooks.pojo.GenrePOJO;
import com.fanok.audiobooks.presenter.BooksPresenter;

import java.util.ArrayList;
import java.util.Objects;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.Unbinder;

public class BooksFragment extends MvpAppCompatFragment implements BooksView {
    private static final String TAG = "BooksFragment";
    private static final String ARG_URL = "url";
    private static final String ARG_TITLE = "title";
    private static final String ARG_SUB_TITLE = "sub_title";
    private static final String ARG_SUB_TITLE_STRING = "sub_title_string";
    private static final String ARG_MODEL = "model_id";

    @BindView(R.id.list)
    RecyclerView mRecyclerView;
    @BindView(R.id.refresh)
    SwipeRefreshLayout mRefresh;
    @BindView(R.id.progressBar)
    LinearLayout mProgressBar;
    Unbinder unbinder;

    @InjectPresenter
    BooksPresenter mPresenter;

    private BooksListAddapter mAddapterBooks;
    private GenreListAddapter mAddapterGenre;

    private int titleId;
    private int subTitleId;
    private String subTitleString;
    private int modelID;

    public static BooksFragment newInstance(@NonNull String url, int title, int modelID) {
        BooksFragment fragment = new BooksFragment();
        if (!Consts.REGEXP_URL.matcher(url).matches()) {
            throw new IllegalArgumentException(
                    "Variable 'url' contains not url");
        }
        Bundle args = new Bundle();
        args.putString(ARG_URL, url);
        args.putInt(ARG_TITLE, title);
        args.putInt(ARG_MODEL, modelID);
        fragment.setArguments(args);
        return fragment;
    }

    public static BooksFragment newInstance(@NonNull String url, int title, int subTitle,
            int modelID) {
        BooksFragment fragment = new BooksFragment();
        if (!Consts.REGEXP_URL.matcher(url).matches()) {
            throw new IllegalArgumentException(
                    "Variable 'url' contains not url");
        }
        Bundle args = new Bundle();
        args.putString(ARG_URL, url);
        args.putInt(ARG_TITLE, title);
        args.putInt(ARG_SUB_TITLE, subTitle);
        args.putInt(ARG_MODEL, modelID);
        fragment.setArguments(args);
        return fragment;
    }

    public static BooksFragment newInstance(@NonNull String url, int title, String subTitle,
            int modelID) {
        BooksFragment fragment = new BooksFragment();
        if (!Consts.REGEXP_URL.matcher(url).matches()) {
            throw new IllegalArgumentException(
                    "Variable 'url' contains not url");
        }
        Bundle args = new Bundle();
        args.putString(ARG_URL, url);
        args.putInt(ARG_TITLE, title);
        args.putString(ARG_SUB_TITLE_STRING, subTitle);
        args.putInt(ARG_MODEL, modelID);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
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
        if (url.isEmpty()) throw new IllegalArgumentException("Variable 'url' contains not url");
        if (modelID == -1) throw new IllegalArgumentException("Illegal model id");
        if (savedInstanceState == null) {
            String subTitle = "";
            if (!subTitleString.isEmpty()) {
                subTitle = subTitleString;
            } else if (subTitleId != 0) subTitle = getResources().getString(subTitleId);
            getPresenter().onCreate(url, modelID, subTitle, getContext());
        } else {
            getPresenter().onChageOrintationScreen(url);
        }
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_books, container, false);
        unbinder = ButterKnife.bind(this, view);

        if (titleId != 0) {
            Objects.requireNonNull(getActivity()).setTitle(titleId);
        }
        ActionBar toolbar = ((AppCompatActivity) Objects.requireNonNull(
                getActivity())).getSupportActionBar();
        if (toolbar != null) {
            if (!subTitleString.isEmpty()) {
                toolbar.setSubtitle(subTitleString);
            } else if (subTitleId != 0) {
                toolbar.setSubtitle(subTitleId);
            } else {
                toolbar.setSubtitle("");
            }
        }

        int orientation = this.getResources().getConfiguration().orientation;
        if (orientation == Configuration.ORIENTATION_PORTRAIT) {
            setLayoutManager();
        } else {
            setLayoutManager(2);
        }

        switch (modelID) {
            case Consts.MODEL_BOOKS:
                mAddapterBooks = new BooksListAddapter();
                mRecyclerView.setAdapter(mAddapterBooks);
                break;
            case Consts.MODEL_GENRE:
            case Consts.MODEL_AUTOR:
            case Consts.MODEL_ARTIST:
                mAddapterGenre = new GenreListAddapter();
                mRecyclerView.setAdapter(mAddapterGenre);
                break;
        }
        getPresenter().loadBoks();
        setHasOptionsMenu(true);

        mRefresh.setOnRefreshListener(() -> getPresenter().onRefresh());

        mRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                int lastCompletelyVisibleItemPosition =
                        ((LinearLayoutManager) Objects.requireNonNull(
                                recyclerView.getLayoutManager())).findLastVisibleItemPosition();
                if (modelID != Consts.MODEL_GENRE
                        && lastCompletelyVisibleItemPosition > getCount() - 3 && dy > 0) {
                    getPresenter().loadBoks();
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

        return view;
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
    public void showData(@NonNull ArrayList bookPOJOS) {
        try {
            if (bookPOJOS.size() != 0) {
                if (bookPOJOS.get(0) instanceof BookPOJO) {
                    mAddapterBooks.setItem(bookPOJOS);
                } else if (bookPOJOS.get(0) instanceof GenrePOJO) {
                    mAddapterGenre.setItem(bookPOJOS);
                }
            }
        } catch (NullPointerException e) {
            Log.e(TAG, "Data display error");
            showToast(R.string.error_display_data);
        }
    }


    @Override
    public void clearData() {
        if (mAddapterBooks != null) mAddapterBooks.clearItem();
        if (mAddapterGenre != null) mAddapterGenre.clearItem();
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
    public void showToast(int message) {
        Toast.makeText(this.getContext(), getResources().getText(message),
                Toast.LENGTH_LONG).show();
    }

    @Override
    public void showToast(String message) {
        Toast.makeText(this.getContext(), message, Toast.LENGTH_LONG).show();
    }

    @Override
    public void showRefreshing(boolean b) {
        mRefresh.setRefreshing(b);
    }

    @Override
    public void setPosition(int position) {
        Objects.requireNonNull(mRecyclerView.getLayoutManager()).scrollToPosition(position);
    }

    @Override
    public void showFragment(@NonNull Fragment fragment, @NonNull String tag) {
        MainActivity activity = (MainActivity) getActivity();
        if (activity != null) {
            activity.showFragment(fragment, tag);
        }
    }


    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.books_options_menu, menu);
        if (modelID == Consts.MODEL_BOOKS) {
            setColorPrimeriTextInIconItemMenu(
                    menu.findItem(R.id.order), Objects.requireNonNull(getContext()));
        } else {
            menu.findItem(R.id.order).setVisible(false);
        }
        setColorPrimeriTextInIconItemMenu(menu.findItem(R.id.app_bar_search),
                Objects.requireNonNull(getContext()));

        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        getPresenter().onOptionItemSelected(item.getItemId());
        return true;
    }

    protected BooksPresenter getPresenter() {
        return mPresenter;
    }

    @Override
    public void onResume() {
        super.onResume();
        MainActivity activity = (MainActivity) getActivity();
        if (activity != null) {
            NavigationView navigationView = activity.getNavigationView();
            switch (modelID) {
                case MODEL_BOOKS:
                    navigationView.setCheckedItem(R.id.nav_audiobooks);
                    break;
                case MODEL_GENRE:
                    navigationView.setCheckedItem(R.id.nav_genre);
                    break;
                case MODEL_AUTOR:
                    navigationView.setCheckedItem(R.id.nav_autor);
                    break;
                case MODEL_ARTIST:
                    navigationView.setCheckedItem(R.id.nav_artist);
                    break;
            }
        }
    }
}
