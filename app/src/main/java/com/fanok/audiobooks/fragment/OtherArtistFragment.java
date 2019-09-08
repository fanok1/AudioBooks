package com.fanok.audiobooks.fragment;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.arellomobile.mvp.MvpAppCompatFragment;
import com.arellomobile.mvp.presenter.InjectPresenter;
import com.fanok.audiobooks.R;
import com.fanok.audiobooks.activity.LoadBook;
import com.fanok.audiobooks.adapter.OtherArtistListAddapter;
import com.fanok.audiobooks.interface_pacatge.book_content.OtherArtist;
import com.fanok.audiobooks.pojo.OtherArtistPOJO;
import com.fanok.audiobooks.presenter.OtherArtistPresenter;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Objects;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.Unbinder;

public class OtherArtistFragment extends MvpAppCompatFragment implements OtherArtist {

    private static final String TAG = "SeriesBookFragment";
    private static final String ARG_URL = "arg_url";
    @BindView(R.id.list)
    RecyclerView mList;
    @BindView(R.id.progressBar)
    ProgressBar mProgressBar;
    Unbinder unbinder;

    @InjectPresenter
    OtherArtistPresenter mPresenter;
    @BindView(R.id.placeholder)
    TextView mPlaceholder;


    private String mUrl;
    private OtherArtistListAddapter mOtherArtistListAddapter;


    public static OtherArtistFragment newInstance(@NonNull String url) {
        Bundle args = new Bundle();
        args.putString(ARG_URL, url);
        OtherArtistFragment fragment = new OtherArtistFragment();
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

        mOtherArtistListAddapter = new OtherArtistListAddapter();

        mOtherArtistListAddapter.setListener((view12, position) -> {
            OtherArtistPOJO otherArtistPOJO = mOtherArtistListAddapter.getItem(position);
            showBook(otherArtistPOJO.getUrl());
        });

        mList.setLayoutManager(new LinearLayoutManager(getContext()));
        mList.setAdapter(mOtherArtistListAddapter);
        mList.addItemDecoration(
                new DividerItemDecoration(mList.getContext(), DividerItemDecoration.VERTICAL));


        if (savedInstanceState == null) {
            mPresenter.onCreate(mUrl);
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
    public void showSeries(ArrayList<OtherArtistPOJO> data) {
        if (data.size() == 0) {
            mPlaceholder.setText(R.string.error_load_data);
            mPlaceholder.setVisibility(View.VISIBLE);
        } else {
            mPlaceholder.setVisibility(View.GONE);
        }
        mOtherArtistListAddapter.setItem(data);
    }

    @Override
    public void showBook(@NotNull @NonNull String url) {
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
