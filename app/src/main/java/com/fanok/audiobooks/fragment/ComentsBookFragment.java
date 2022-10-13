package com.fanok.audiobooks.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.arellomobile.mvp.MvpAppCompatFragment;
import com.arellomobile.mvp.presenter.InjectPresenter;
import com.arellomobile.mvp.presenter.ProvidePresenter;
import com.fanok.audiobooks.adapter.AnswerListAddapter;
import com.fanok.audiobooks.adapter.ComentsListAddapter;
import com.fanok.audiobooks.adapter.ComentsListBazaKnigAddapter;
import com.fanok.audiobooks.databinding.FragmentBookComentsBinding;
import com.fanok.audiobooks.interface_pacatge.book_content.Coments;
import com.fanok.audiobooks.pojo.ComentsPOJO;
import com.fanok.audiobooks.pojo.SubComentsPOJO;
import com.fanok.audiobooks.presenter.BookComentsPresenter;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import java.util.ArrayList;
import org.jetbrains.annotations.NotNull;

public class ComentsBookFragment extends MvpAppCompatFragment implements Coments {

    private static final String TAG = "DescriptionBookFragment";

    private static final String ARG_URL = "arg_url";

    private FragmentBookComentsBinding binding;


    @InjectPresenter
    BookComentsPresenter mPresenter;

    private ComentsListAddapter mComentsListAddapter;

    private ComentsListBazaKnigAddapter mComentsListBazaKnigAddapter;

    private AnswerListAddapter mAnswerListAddapter;

    private String url;

    public static ComentsBookFragment newInstance(@NonNull String url) {
        Bundle args = new Bundle();
        args.putString(ARG_URL, url);
        ComentsBookFragment fragment = new ComentsBookFragment();
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {

        binding = FragmentBookComentsBinding.inflate(inflater, container, false);

        BottomSheetBehavior bottomSheetBehavior = BottomSheetBehavior.from(binding.answer.getRoot());
        binding.answer.close.setOnClickListener(view1 -> {
            if (bottomSheetBehavior.getState() == BottomSheetBehavior.STATE_EXPANDED) {
                bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
            }
        });

        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(getContext());
        binding.list.setLayoutManager(linearLayoutManager);
        if (!url.contains("baza-knig.ru")) {
            mComentsListAddapter = new ComentsListAddapter(getContext());
            mAnswerListAddapter = new AnswerListAddapter(getContext());
            mComentsListAddapter.setListener((view12, position) -> {
                ComentsPOJO comentsPOJO = mComentsListAddapter.getItem(position);
                if (bottomSheetBehavior.getState() == BottomSheetBehavior.STATE_COLLAPSED) {
                    bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
                    ArrayList<SubComentsPOJO> subComentsPOJOArrayList =
                            (ArrayList<SubComentsPOJO>) comentsPOJO.getChildComents().clone();
                    SubComentsPOJO subComentsPOJO = new SubComentsPOJO();
                    subComentsPOJO.setName(comentsPOJO.getName());
                    subComentsPOJO.setDate(comentsPOJO.getDate());
                    subComentsPOJO.setImage(comentsPOJO.getImage());
                    subComentsPOJO.setReting(comentsPOJO.getReting());
                    subComentsPOJO.setText(comentsPOJO.getText());
                    subComentsPOJOArrayList.add(0, subComentsPOJO);
                    mAnswerListAddapter.setItem(subComentsPOJOArrayList);
                }
            });

            binding.list.setAdapter(mComentsListAddapter);
            binding.answer.listAnswer.setLayoutManager(new LinearLayoutManager(getContext()));
            binding.answer.listAnswer.setAdapter(mAnswerListAddapter);
        } else {
            mComentsListBazaKnigAddapter = new ComentsListBazaKnigAddapter(getContext());
            mComentsListBazaKnigAddapter.setListener((view, position) -> {
                int pos = mComentsListBazaKnigAddapter.getParentQuoteId(position);
                if (pos != -1) {
                    linearLayoutManager.scrollToPositionWithOffset(pos, 0);
                }
            });
            binding.list.setAdapter(mComentsListBazaKnigAddapter);
        }

        binding.list.addItemDecoration(
                new DividerItemDecoration(binding.list.getContext(), DividerItemDecoration.VERTICAL));

        ViewCompat.setNestedScrollingEnabled(binding.list, false);
        return binding.getRoot();
    }

    @Override
    public void onDestroyView() {
        mComentsListAddapter = null;
        mAnswerListAddapter = null;
        mComentsListBazaKnigAddapter = null;
        mPresenter.onDestroy();
        super.onDestroyView();
        binding = null;
    }

    @Override
    public void setPlaceholder(int id) {
        binding.placeholder.setText(getString(id));
    }

    @Override
    public void setPlaceholder(@NotNull String text) {
        binding.placeholder.setText(text);
    }

    @Override
    public void showComents(ArrayList<ComentsPOJO> data) {
        if (data.size() == 0) {
            binding.placeholder.setVisibility(View.VISIBLE);
        } else {
            binding.placeholder.setVisibility(View.GONE);
        }
        if (!url.contains("baza-knig.ru")) {
            mComentsListAddapter.setItem(data);
        } else {
            mComentsListBazaKnigAddapter.setItem(data);
        }

    }

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String url = requireArguments().getString(ARG_URL);
        if (url == null || url.isEmpty()) {
            throw new NullPointerException();
        }
        this.url = url;
    }

    @ProvidePresenter
    BookComentsPresenter provide() {
        String url = requireArguments().getString(ARG_URL);
        if (url == null || url.isEmpty()) {
            throw new NullPointerException();
        }
        this.url = url;
        return new BookComentsPresenter(url);
    }

    @Override
    public void showProgress(boolean b) {
        if (b) {
            binding.progressBar.setVisibility(View.VISIBLE);
        } else {
            binding.progressBar.setVisibility(View.GONE);
        }

    }
}
