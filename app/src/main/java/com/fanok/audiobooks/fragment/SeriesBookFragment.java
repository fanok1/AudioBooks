package com.fanok.audiobooks.fragment;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.arellomobile.mvp.MvpAppCompatFragment;
import com.arellomobile.mvp.presenter.InjectPresenter;
import com.fanok.audiobooks.R;
import com.fanok.audiobooks.activity.LoadBook;
import com.fanok.audiobooks.adapter.SeriesListAddapter;
import com.fanok.audiobooks.interface_pacatge.book_content.Series;
import com.fanok.audiobooks.pojo.SeriesPOJO;
import com.fanok.audiobooks.presenter.BookSeriesPresenter;

import java.util.ArrayList;
import java.util.Objects;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.Unbinder;

public class SeriesBookFragment extends MvpAppCompatFragment implements Series {

    private static final String TAG = "SeriesBookFragment";
    private static final String ARG_URL = "arg_url";
    @BindView(R.id.list)
    RecyclerView mList;
    @BindView(R.id.progressBar)
    ProgressBar mProgressBar;
    Unbinder unbinder;

    @InjectPresenter
    BookSeriesPresenter mPresenter;


    private String mUrl;
    private SeriesListAddapter mSeriesListAddapter;


    public static SeriesBookFragment newInstance(@NonNull String url) {
        Bundle args = new Bundle();
        args.putString(ARG_URL, url);
        SeriesBookFragment fragment = new SeriesBookFragment();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate: called");
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            mUrl = getArguments().getString(ARG_URL);
            if (mUrl == null || mUrl.isEmpty()) throw new NullPointerException();
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_book_serias, container, false);
        unbinder = ButterKnife.bind(this, view);

        mSeriesListAddapter = new SeriesListAddapter(mUrl);

        mSeriesListAddapter.setListener((view12, position) -> {
            SeriesPOJO seriesPOJO = mSeriesListAddapter.getItem(position);
            if (!mUrl.equals(seriesPOJO.getUrl())) {
                showBook(seriesPOJO.getUrl());
            }
        });

        mList.setLayoutManager(new LinearLayoutManager(getContext()));
        mList.setAdapter(mSeriesListAddapter);
        mList.addItemDecoration(
                new DividerItemDecoration(mList.getContext(), DividerItemDecoration.VERTICAL));


        if (savedInstanceState == null) {
            mPresenter.onCreate(mUrl);
        } else {
            mPresenter.onChageOrintationScreen();
        }


        return view;
    }


    @Override
    public void showProgress(boolean b) {
        if (b) {
            mProgressBar.setVisibility(View.VISIBLE);
        } else {
            mProgressBar.setVisibility(View.GONE);
        }

    }

    @Override
    public void showSeries(ArrayList<SeriesPOJO> data) {
        mSeriesListAddapter.setItem(data);
    }

    @Override
    public void showBook(@NonNull String url) {
        Intent intent = new Intent(getContext(), LoadBook.class);
        intent.putExtra("url", url);
        Objects.requireNonNull(getContext()).startActivity(intent);
    }

    @Override
    public void showToast(int message) {
        Toast.makeText(getContext(), getText(message), Toast.LENGTH_SHORT).show();
    }

    @Override
    public void showToast(String message) {
        Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        unbinder.unbind();
    }
}
