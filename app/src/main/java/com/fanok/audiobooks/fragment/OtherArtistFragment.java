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
import com.fanok.audiobooks.activity.LoadBook;
import com.fanok.audiobooks.adapter.OtherArtistListAddapter;
import com.fanok.audiobooks.databinding.FragmentBookSeriasBinding;
import com.fanok.audiobooks.interface_pacatge.book_content.OtherArtist;
import com.fanok.audiobooks.pojo.BookPOJO;
import com.fanok.audiobooks.pojo.OtherArtistPOJO;
import com.fanok.audiobooks.presenter.OtherArtistPresenter;
import com.google.gson.Gson;
import java.util.ArrayList;
import org.jetbrains.annotations.NotNull;


public class OtherArtistFragment extends MvpAppCompatFragment implements OtherArtist {

    private static final String TAG = "SeriesBookFragment";

    private static final String ARG_URL = "arg_url";

    private FragmentBookSeriasBinding binding;

    @InjectPresenter
    OtherArtistPresenter mPresenter;


    private OtherArtistListAddapter mOtherArtistListAddapter;

    public static OtherArtistFragment newInstance(@NonNull BookPOJO bookPOJO) {
        Bundle args = new Bundle();
        args.putString(ARG_URL, new Gson().toJson(bookPOJO));
        OtherArtistFragment fragment = new OtherArtistFragment();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void showBook(@NotNull @NonNull String url) {
        Intent intent = new Intent(getContext(), LoadBook.class);
        intent.putExtra("url", url);
        requireContext().startActivity(intent);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate: called");
        super.onCreate(savedInstanceState);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        binding = FragmentBookSeriasBinding.inflate(inflater, container, false);

        mOtherArtistListAddapter = new OtherArtistListAddapter();

        mOtherArtistListAddapter.setListener((view12, position) -> {
            OtherArtistPOJO otherArtistPOJO = mOtherArtistListAddapter.getItem(position);
            showBook(otherArtistPOJO.getUrl());
        });

        binding.list.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.list.setAdapter(mOtherArtistListAddapter);
        binding.list.addItemDecoration(
                new DividerItemDecoration(binding.list.getContext(), DividerItemDecoration.VERTICAL));

        return binding.getRoot();
    }

    @Override
    public void onDestroyView() {
        mPresenter.onDestroy();
        super.onDestroyView();
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

    @ProvidePresenter
    OtherArtistPresenter provide() {
        BookPOJO bookPOJO = new Gson().fromJson(
                requireArguments().getString(ARG_URL), BookPOJO.class);
        if (bookPOJO == null) {
            throw new NullPointerException();
        }
        return new OtherArtistPresenter(bookPOJO);
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
    public void showSeries(ArrayList<OtherArtistPOJO> data) {
        if (data.isEmpty()) {
            binding.placeholder.setText(R.string.error_load_data);
            binding.placeholder.setVisibility(View.VISIBLE);
        } else {
            binding.placeholder.setVisibility(View.GONE);
        }
        mOtherArtistListAddapter.setItem(data);
    }
}
