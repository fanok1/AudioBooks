package com.fanok.audiobooks.fragment;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.BottomSheetBehavior;
import android.support.v4.view.ViewCompat;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.arellomobile.mvp.MvpAppCompatFragment;
import com.arellomobile.mvp.presenter.InjectPresenter;
import com.fanok.audiobooks.R;
import com.fanok.audiobooks.adapter.AnswerListAddapter;
import com.fanok.audiobooks.adapter.ComentsListAddapter;
import com.fanok.audiobooks.interface_pacatge.book_content.Coments;
import com.fanok.audiobooks.pojo.ComentsPOJO;
import com.fanok.audiobooks.pojo.SubComentsPOJO;
import com.fanok.audiobooks.presenter.BookComentsPresenter;

import java.util.ArrayList;

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
    private String mUrl;
    private ComentsListAddapter mComentsListAddapter;
    private AnswerListAddapter mAnswerListAddapter;

    public static ComentsBookFragment newInstance(@NonNull String url) {
        Bundle args = new Bundle();
        args.putString(ARG_URL, url);
        ComentsBookFragment fragment = new ComentsBookFragment();
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
                ArrayList<SubComentsPOJO> subComentsPOJOArrayList = comentsPOJO.getChildComents();
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
    public void showComents(ArrayList<ComentsPOJO> data) {
        mComentsListAddapter.setItem(data);

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
