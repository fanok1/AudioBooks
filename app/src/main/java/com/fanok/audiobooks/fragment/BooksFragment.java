package com.fanok.audiobooks.fragment;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.fanok.audiobooks.R;
import com.fanok.audiobooks.adapter.BooksListAddapter;
import com.fanok.audiobooks.interface_pacatge.BooksContract;
import com.fanok.audiobooks.pojo.BookPOJO;
import com.fanok.audiobooks.presenter.BooksPresenter;

import java.util.ArrayList;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.Unbinder;

public class BooksFragment extends Fragment implements BooksContract.View {
    private static final String TAG = "BooksFragment";

    @BindView(R.id.list)
    RecyclerView mRecyclerView;
    @BindView(R.id.refresh)
    SwipeRefreshLayout mRefresh;
    @BindView(R.id.progressBar)
    LinearLayout mProgressBar;
    Unbinder unbinder;
    private BooksContract.Presenter mPresenter;
    private BooksListAddapter mAddapter;

    public BooksFragment() {
        mPresenter = new BooksPresenter(this);
    }

    public static BooksFragment newInstance(@NonNull String url, int columnCount) {
        BooksFragment fragment = new BooksFragment();
        fragment.setArguments(BooksPresenter.getArg(url, columnCount));
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mPresenter.onCreate();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_books, container, false);
        unbinder = ButterKnife.bind(this, view);
        mPresenter.onCreateView();
        mAddapter = new BooksListAddapter();
        mRecyclerView.setAdapter(mAddapter);
        mPresenter.loadBoks();

        mRefresh.setOnRefreshListener(() -> mPresenter.onRefresh());
        mRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
                if (!recyclerView.canScrollVertically(1)) {
                    mPresenter.loadBoks();
                }
            }
        });

        return view;
    }

    @Override
    public void onDestroyView() {
        mPresenter.onDestroy();
        super.onDestroyView();
        unbinder.unbind();
    }

    @Override
    public void setLayoutManager() {
        mRecyclerView.setLayoutManager(new LinearLayoutManager(this.getContext()));
    }

    @Override
    public void setLayoutManager(int count) {
        mRecyclerView.setLayoutManager(new GridLayoutManager(this.getContext(), count));
    }

    @Override
    public Bundle getArg() {
        return getArguments();
    }

    @Override
    public void showData(ArrayList<BookPOJO> bookPOJOS) {
        try {
            mAddapter.setItem(bookPOJOS);
        } catch (NullPointerException e) {
            Log.e(TAG, "Data display error");
            showToast(R.string.error_display_data);
        }
    }

    @Override
    public void clearData() {
        mAddapter.clearItem();
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
}
