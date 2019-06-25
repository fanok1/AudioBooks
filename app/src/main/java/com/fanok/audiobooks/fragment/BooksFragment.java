package com.fanok.audiobooks.fragment;

import static com.fanok.audiobooks.Consts.setColorPrimeriTextInIconItemMenu;

import android.content.res.Configuration;
import android.os.Bundle;
import android.support.annotation.NonNull;
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
import com.fanok.audiobooks.interface_pacatge.books.BooksView;
import com.fanok.audiobooks.pojo.BookPOJO;
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

    @BindView(R.id.list)
    RecyclerView mRecyclerView;
    @BindView(R.id.refresh)
    SwipeRefreshLayout mRefresh;
    @BindView(R.id.progressBar)
    LinearLayout mProgressBar;
    Unbinder unbinder;

    @InjectPresenter
    BooksPresenter mPresenter;


    private BooksListAddapter mAddapter;

    private int titleId;
    private int subTitleId;

    public static BooksFragment newInstance(@NonNull String url, int title) {
        BooksFragment fragment = new BooksFragment();
        if (!Consts.REGEXP_URL.matcher(url).matches()) {
            throw new IllegalArgumentException(
                    "Variable 'url' contains not url");
        }
        Bundle args = new Bundle();
        args.putString(ARG_URL, url);
        args.putInt(ARG_TITLE, title);
        fragment.setArguments(args);
        return fragment;
    }

    public static BooksFragment newInstance(@NonNull String url, int title, int subTitle) {
        BooksFragment fragment = new BooksFragment();
        if (!Consts.REGEXP_URL.matcher(url).matches()) {
            throw new IllegalArgumentException(
                    "Variable 'url' contains not url");
        }
        Bundle args = new Bundle();
        args.putString(ARG_URL, url);
        args.putInt(ARG_TITLE, title);
        args.putInt(ARG_SUB_TITLE, subTitle);
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
        }
        if (url.isEmpty()) throw new IllegalArgumentException("Variable 'url' contains not url");
        if (savedInstanceState == null) {
            getPresenter().onCreate(url);
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
            if (subTitleId != 0) {
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
        setAddapter(new BooksListAddapter());
        mRecyclerView.setAdapter(getAddapter());
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
                if (lastCompletelyVisibleItemPosition > getAddapter().getItemCount() - 3
                        && dy > 0) {
                    getPresenter().loadBoks();
                }
            }
        });

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
    public void showData(ArrayList<BookPOJO> bookPOJOS) {
        try {
            getAddapter().setItem(bookPOJOS);
        } catch (NullPointerException e) {
            Log.e(TAG, "Data display error");
            showToast(R.string.error_display_data);
        }
    }

    @Override
    public void clearData() {
        getAddapter().clearItem();
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
        setColorPrimeriTextInIconItemMenu(menu.findItem(R.id.order),
                Objects.requireNonNull(getContext()));
        setColorPrimeriTextInIconItemMenu(menu.findItem(R.id.app_bar_search), getContext());
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

    public BooksListAddapter getAddapter() {
        return mAddapter;
    }

    public void setAddapter(@NonNull BooksListAddapter addapter) {
        mAddapter = addapter;
    }
}
