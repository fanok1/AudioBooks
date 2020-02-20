package com.fanok.audiobooks.fragment;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.fanok.audiobooks.R;
import com.fanok.audiobooks.activity.LoadBook;
import com.fanok.audiobooks.adapter.OtherArtistListAddapter;
import com.fanok.audiobooks.pojo.OtherArtistPOJO;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.util.ArrayList;
import java.util.Objects;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.Unbinder;

public class OtherSourceFragment extends Fragment {

    private static final String ARG_URL = "arg_url";
    @BindView(R.id.list)
    RecyclerView mList;
    @BindView(R.id.progressBar)
    ProgressBar mProgressBar;
    Unbinder unbinder;

    @BindView(R.id.placeholder)
    TextView mPlaceholder;


    private OtherArtistListAddapter mOtherArtistListAddapter;


    public static OtherSourceFragment newInstance(@NonNull ArrayList<OtherArtistPOJO> bookPOJO) {
        Bundle args = new Bundle();
        args.putString(ARG_URL, new Gson().toJson(bookPOJO));
        OtherSourceFragment fragment = new OtherSourceFragment();
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_book_serias, container, false);
        unbinder = ButterKnife.bind(this, view);

        ArrayList<OtherArtistPOJO> bookPOJO = new Gson().fromJson(
                Objects.requireNonNull(getArguments()).getString(ARG_URL),
                new TypeToken<ArrayList<OtherArtistPOJO>>() {
                }.getType());

        if (bookPOJO == null) throw new NullPointerException();


        mOtherArtistListAddapter = new OtherArtistListAddapter();

        mOtherArtistListAddapter.setListener((view12, position) -> {
            OtherArtistPOJO otherArtistPOJO = mOtherArtistListAddapter.getItem(position);
            Intent intent = new Intent(getContext(), LoadBook.class);
            intent.putExtra("url", otherArtistPOJO.getUrl());
            Objects.requireNonNull(getContext()).startActivity(intent);
        });

        if (bookPOJO.size() == 0) {
            mPlaceholder.setText(R.string.error_load_data);
            mPlaceholder.setVisibility(View.VISIBLE);
        } else {
            mPlaceholder.setVisibility(View.GONE);
        }
        mOtherArtistListAddapter.setItem(bookPOJO);

        mList.setLayoutManager(new LinearLayoutManager(getContext()));
        mList.setAdapter(mOtherArtistListAddapter);
        mList.addItemDecoration(
                new DividerItemDecoration(mList.getContext(), DividerItemDecoration.VERTICAL));

        mProgressBar.setVisibility(View.GONE);

        return view;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        unbinder.unbind();
    }
}
