package com.fanok.audiobooks.fragment;

import static com.fanok.audiobooks.Consts.TABLE_FAVORITE;
import static com.fanok.audiobooks.Consts.TABLE_HISTORY;

import android.content.res.Configuration;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.NavigationView;
import android.support.v4.app.Fragment;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.arellomobile.mvp.MvpAppCompatFragment;
import com.arellomobile.mvp.presenter.InjectPresenter;
import com.fanok.audiobooks.GridSpacingItemDecoration;
import com.fanok.audiobooks.R;
import com.fanok.audiobooks.activity.MainActivity;
import com.fanok.audiobooks.adapter.BooksListAddapter;
import com.fanok.audiobooks.interface_pacatge.favorite.FavoriteView;
import com.fanok.audiobooks.pojo.BookPOJO;
import com.fanok.audiobooks.presenter.FavoritePresenter;

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
    Unbinder unbinder;

    @InjectPresenter
    FavoritePresenter mPresenter;

    private BooksListAddapter mAddapterBooks;

    private int titleId;
    private int table;

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
        super.onCreate(savedInstanceState);
        Bundle arg = getArguments();
        if (arg != null) {
            titleId = arg.getInt(ARG_TITLE, 0);
            table = arg.getInt(ARG_TABLE, 0);
        }
        if (savedInstanceState == null) {
            getPresenter().onCreate(getContext(), table);
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
        getPresenter().loadBooks();
        setHasOptionsMenu(true);
        mAddapterBooks.setListener(
                (view12, position) -> mPresenter.onBookItemClick(view12, position));

        mAddapterBooks.setLongListener(
                (view13, position) -> mPresenter.onBookItemLongClick(view13, position));
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
}
