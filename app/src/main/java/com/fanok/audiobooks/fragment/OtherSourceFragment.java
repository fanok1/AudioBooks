package com.fanok.audiobooks.fragment;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.fanok.audiobooks.R;
import com.fanok.audiobooks.activity.LoadBook;
import com.fanok.audiobooks.adapter.OtherArtistListAddapter;
import com.fanok.audiobooks.databinding.FragmentBookSeriasBinding;
import com.fanok.audiobooks.pojo.OtherArtistPOJO;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.util.ArrayList;



public class OtherSourceFragment extends Fragment {

    private static final String ARG_URL = "arg_url";

    private FragmentBookSeriasBinding binding;



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
        binding = FragmentBookSeriasBinding.inflate(inflater, container, false);

        ArrayList<OtherArtistPOJO> bookPOJO = new Gson().fromJson(
                requireArguments().getString(ARG_URL),
                new TypeToken<ArrayList<OtherArtistPOJO>>() {
                }.getType());

        if (bookPOJO == null) throw new NullPointerException();


        mOtherArtistListAddapter = new OtherArtistListAddapter();

        mOtherArtistListAddapter.setListener((view12, position) -> {
            OtherArtistPOJO otherArtistPOJO = mOtherArtistListAddapter.getItem(position);
            Intent intent = new Intent(getContext(), LoadBook.class);
            intent.putExtra("url", otherArtistPOJO.getUrl());
            requireActivity().startActivity(intent);
        });

        if (bookPOJO.size() == 0) {
            binding.placeholder.setText(R.string.error_load_data);
            binding.placeholder.setVisibility(View.VISIBLE);
        } else {
            binding.placeholder.setVisibility(View.GONE);
        }
        mOtherArtistListAddapter.setItem(bookPOJO);

        binding.list.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.list.setAdapter(mOtherArtistListAddapter);
        binding.list.addItemDecoration(
                new DividerItemDecoration(binding.list.getContext(), DividerItemDecoration.VERTICAL));

        binding.progressBar.setVisibility(View.GONE);

        return binding.getRoot();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
