package com.fanok.audiobooks.fragment;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.arellomobile.mvp.MvpAppCompatFragment;
import com.arellomobile.mvp.presenter.InjectPresenter;
import com.arellomobile.mvp.presenter.ProvidePresenter;
import com.fanok.audiobooks.R;
import com.fanok.audiobooks.activity.BookActivity;
import com.fanok.audiobooks.activity.LoadBook;
import com.fanok.audiobooks.adapter.SeriesListAddapter;
import com.fanok.audiobooks.databinding.FragmentBookSeriasBinding;
import com.fanok.audiobooks.interface_pacatge.book_content.Series;
import com.fanok.audiobooks.pojo.SeriesPOJO;
import com.fanok.audiobooks.presenter.BookSeriesPresenter;
import java.util.ArrayList;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;


public class SeriesBookFragment extends MvpAppCompatFragment implements Series {

    private static final String TAG = "SeriesBookFragment";

    private static final String ARG_URL = "arg_url";

    private FragmentBookSeriasBinding binding;

    @InjectPresenter
    BookSeriesPresenter mPresenter;

    @ProvidePresenter
    BookSeriesPresenter provide() {
        mUrl = Objects.requireNonNull(getArguments()).getString(ARG_URL);
        if (mUrl == null || mUrl.isEmpty()) {
            throw new NullPointerException();
        }
        return new BookSeriesPresenter(mUrl);
    }


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
        binding = FragmentBookSeriasBinding.inflate(inflater, container, false);

        mSeriesListAddapter = new SeriesListAddapter(mUrl);

        mSeriesListAddapter.setListener((view12, position) -> {
            SeriesPOJO seriesPOJO = mSeriesListAddapter.getItem(position);
            if (!seriesPOJO.getUrl().isEmpty()) {
                showBook(seriesPOJO.getUrl());
            } else {
                BookActivity activity = (BookActivity) getActivity();
                if (activity != null) {
                    activity.setTabPostion(getResources().getString(R.string.tab_text_1));
                }
            }
        });

        binding.list.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.list.setAdapter(mSeriesListAddapter);
        binding.list.addItemDecoration(
                new DividerItemDecoration(binding.list.getContext(), DividerItemDecoration.VERTICAL));

        return binding.getRoot();
    }

    @Override
    public void onDestroyView() {
        mPresenter.onDestroy();
        super.onDestroyView();
        mSeriesListAddapter = null;
        binding = null;
    }

    @Override
    public void showProgress(boolean b) {
        if (b) {
            binding.progressBar.setVisibility(View.VISIBLE);
        } else {
            binding.progressBar.setVisibility(View.GONE);
        }

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
    public void showSeries(ArrayList<SeriesPOJO> data) {
        if (data.size() == 0) {
            binding.placeholder.setVisibility(View.VISIBLE);
        } else {
            binding.placeholder.setVisibility(View.GONE);
        }
        mSeriesListAddapter.setItem(data);
    }

}
