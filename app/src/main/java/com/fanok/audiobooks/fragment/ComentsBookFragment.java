package com.fanok.audiobooks.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.arellomobile.mvp.MvpAppCompatFragment;
import com.arellomobile.mvp.presenter.InjectPresenter;
import com.arellomobile.mvp.presenter.ProvidePresenter;
import com.fanok.audiobooks.R;
import com.fanok.audiobooks.adapter.AnswerListAddapter;
import com.fanok.audiobooks.adapter.ComentsListAddapter;
import com.fanok.audiobooks.interface_pacatge.book_content.Coments;
import com.fanok.audiobooks.pojo.ComentsPOJO;
import com.fanok.audiobooks.pojo.SubComentsPOJO;
import com.fanok.audiobooks.presenter.BookComentsPresenter;
import com.google.android.material.bottomsheet.BottomSheetBehavior;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Objects;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.Unbinder;

public class ComentsBookFragment extends MvpAppCompatFragment implements Coments {

    private static final String TAG = "DescriptionBookFragment";
    private static final String ARG_URL = "arg_url";


    @InjectPresenter
    BookComentsPresenter mPresenter;
    @BindView(R.id.list)
    RecyclerView mList;
    @BindView(R.id.progressBar)
    ProgressBar mProgressBar;
    @BindView(R.id.close)
    ImageButton mClose;
    @BindView(R.id.listAnswer)
    RecyclerView mListAnswer;
    Unbinder unbinder;
    @BindView(R.id.placeholder)
    TextView mPlaceholder;
    private ComentsListAddapter mComentsListAddapter;
    private AnswerListAddapter mAnswerListAddapter;

    @ProvidePresenter
    BookComentsPresenter provide() {
        String url = Objects.requireNonNull(getArguments()).getString(ARG_URL);
        if (url == null || url.isEmpty()) throw new NullPointerException();
        return new BookComentsPresenter(url);
    }

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
        View view = inflater.inflate(R.layout.fragment_book_coments, container, false);
        unbinder = ButterKnife.bind(this, view);


        BottomSheetBehavior bottomSheetBehavior = BottomSheetBehavior.from(
                view.findViewById(R.id.answer));
        mClose.setOnClickListener(view1 -> {
            if (bottomSheetBehavior.getState() == BottomSheetBehavior.STATE_EXPANDED) {
                bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
            }
        });


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

        mList.setLayoutManager(new LinearLayoutManager(getContext()));
        mList.setAdapter(mComentsListAddapter);
        mList.addItemDecoration(
                new DividerItemDecoration(mList.getContext(), DividerItemDecoration.VERTICAL));

        mListAnswer.setLayoutManager(new LinearLayoutManager(getContext()));
        mListAnswer.setAdapter(mAnswerListAddapter);

        ViewCompat.setNestedScrollingEnabled(mList, false);

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
    public void showComents(ArrayList<ComentsPOJO> data) {
        if (data.size() == 0) {
            mPlaceholder.setVisibility(View.VISIBLE);
        } else {
            mPlaceholder.setVisibility(View.GONE);
        }
        mComentsListAddapter.setItem(data);

    }

    @Override
    public void onDestroyView() {
        unbinder.unbind();
        mComentsListAddapter = null;
        mAnswerListAddapter = null;
        mPresenter.onDestroy();
        super.onDestroyView();
    }


    @Override
    public void setPlaceholder(int id) {
        mPlaceholder.setText(getString(id));
    }

    @Override
    public void setPlaceholder(@NotNull String text) {
        mPlaceholder.setText(text);
    }
}
