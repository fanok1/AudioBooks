package com.fanok.audiobooks.fragment;

import static java.lang.Integer.MAX_VALUE;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.arellomobile.mvp.MvpAppCompatFragment;
import com.arellomobile.mvp.presenter.InjectPresenter;
import com.fanok.audiobooks.Consts;
import com.fanok.audiobooks.MarginItemDecoration;
import com.fanok.audiobooks.R;
import com.fanok.audiobooks.activity.BookActivity;
import com.fanok.audiobooks.activity.MainActivity;
import com.fanok.audiobooks.adapter.BooksOtherAdapter;
import com.fanok.audiobooks.interface_pacatge.book_content.Description;
import com.fanok.audiobooks.pojo.BookPOJO;
import com.fanok.audiobooks.pojo.DescriptionPOJO;
import com.fanok.audiobooks.presenter.BookDescriptionPresenter;
import com.squareup.picasso.Picasso;

import java.util.ArrayList;
import java.util.Objects;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.Unbinder;

public class DescriptionBookFragment extends MvpAppCompatFragment implements Description {

    private static final String TAG = "DescriptionBookFragment";
    private static final String ARG_URL = "arg_url";
    private static final int MAX_LINES = 4;

    @BindView(R.id.title)
    TextView mTitle;
    @BindView(R.id.imageView)
    ImageView mImageView;
    @BindView(R.id.reting)
    TextView mReting;
    @BindView(R.id.genre)
    TextView mGenre;
    @BindView(R.id.time)
    TextView mTime;
    @BindView(R.id.author)
    TextView mAuthor;
    @BindView(R.id.autorConteiner)
    LinearLayout mAutorConteiner;
    @BindView(R.id.artist)
    TextView mArtist;
    @BindView(R.id.artistConteiner)
    LinearLayout mArtistConteiner;
    @BindView(R.id.series)
    TextView mSeries;
    @BindView(R.id.seriesConteiner)
    LinearLayout mSeriesConteiner;
    @BindView(R.id.desc)
    TextView mDesc;
    @BindView(R.id.showMore)
    TextView mShowMore;
    @BindView(R.id.recommendedBooks)
    RecyclerView mRecommendedBooks;
    @BindView(R.id.recommendedBooksTitle)
    TextView mRecommendedBooksTitle;
    @BindView(R.id.scrollView)
    ScrollView mScrollView;
    @BindView(R.id.progressBar)
    ProgressBar mProgressBar;
    Unbinder unbinder;
    @InjectPresenter
    BookDescriptionPresenter mPresenter;
    @BindView(R.id.favorite)
    TextView mFavorite;
    @BindView(R.id.like)
    TextView mLike;
    @BindView(R.id.disLike)
    TextView mDisLike;
    private String mUrl;
    private BooksOtherAdapter mAdapterBooksRecomended;
    private boolean showMore;

    public static DescriptionBookFragment newInstance(@NonNull String url) {
        Bundle args = new Bundle();
        args.putString(ARG_URL, url);
        DescriptionBookFragment fragment = new DescriptionBookFragment();
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
        View view = inflater.inflate(R.layout.fragment_book_description, container, false);
        unbinder = ButterKnife.bind(this, view);
        mAdapterBooksRecomended = new BooksOtherAdapter(Objects.requireNonNull(getContext()));
        mRecommendedBooks.setAdapter(mAdapterBooksRecomended);
        mRecommendedBooks.setLayoutManager(
                new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));
        int margin = (int) getContext().getResources().getDimension(R.dimen.books_other_margin);
        mRecommendedBooks.addItemDecoration(new MarginItemDecoration(margin));
        if (savedInstanceState == null) {
            mPresenter.onCreate(mUrl);
        }
        return view;
    }

    @Override
    public void onDestroyView() {
        mPresenter.onDestroy();
        super.onDestroyView();
        unbinder.unbind();
    }

    @Override
    public void showProgress(boolean b) {
        if (b) {
            mProgressBar.setVisibility(View.VISIBLE);
            mScrollView.setVisibility(View.INVISIBLE);
        } else {
            mProgressBar.setVisibility(View.GONE);
            mScrollView.setVisibility(View.VISIBLE);
        }
    }

    public void showOtherBooks(@NonNull ArrayList<BookPOJO> data) {
        if (mAdapterBooksRecomended != null) mAdapterBooksRecomended.setData(data);
        if (data.size() != 0) {
            mRecommendedBooks.setVisibility(View.VISIBLE);
            mRecommendedBooksTitle.setVisibility(View.VISIBLE);
        } else {
            mRecommendedBooks.setVisibility(View.GONE);
            mRecommendedBooksTitle.setVisibility(View.GONE);
        }

    }

    @Override
    public void showDescription(@NonNull DescriptionPOJO description) {
        mTitle.setText(description.getTitle());
        Picasso.get()
                .load(description.getPoster())
                .placeholder(android.R.drawable.ic_menu_gallery)
                .error(android.R.drawable.ic_menu_gallery)
                .into(mImageView);

        if (!description.getGenre().isEmpty()) {
            mGenre.setText(description.getGenre());
        } else {
            mGenre.setVisibility(View.GONE);
        }

        String reting = String.valueOf(description.getReiting());
        mReting.setText(reting);
        if (description.getTime().isEmpty()) {
            mTime.setVisibility(View.GONE);
        } else {
            mTime.setText(description.getTime());
        }


        mAuthor.setText(description.getAutor());
        mArtist.setText(description.getArtist());
        BookActivity activity = (BookActivity) getActivity();
        if (description.getSeries().isEmpty()) {
            mSeriesConteiner.setVisibility(View.GONE);
        } else {
            mSeries.setText(description.getSeries());
            if (activity != null) {
                activity.showSiries();
            }
        }

        if (description.isOtherReader()) {
            if (activity != null) {
                activity.showOtherArtist();
            }
        }



        mDesc.setMaxLines(MAX_VALUE);
        mDesc.setText(description.getDescription());
        mDesc.post(() -> {
            if (mDesc.getLineCount() <= MAX_LINES) {
                mShowMore.setVisibility(View.GONE);
            } else {
                mShowMore.setVisibility(View.VISIBLE);
            }
            mDesc.setMaxLines(MAX_LINES);
            showMore = false;

            mShowMore.setOnClickListener(view1 -> {
                if (!showMore) {
                    mDesc.setMaxLines(MAX_VALUE);
                    mShowMore.setText(R.string.show_less);
                } else {
                    mDesc.setMaxLines(MAX_LINES);
                    mShowMore.setText(R.string.show_more);
                }
                showMore = !showMore;
            });
        });

        mFavorite.setText(String.valueOf(description.getFavorite()));
        mLike.setText(String.valueOf(description.getLike()));
        mDisLike.setText(String.valueOf(description.getDisLike()));


        mGenre.setOnClickListener(
                view -> MainActivity.startMainActivity(Objects.requireNonNull(getContext()),
                        Consts.FRAGMENT_AUDIOBOOK, description.getGenreUrl()));

        mAuthor.setOnClickListener(
                view -> MainActivity.startMainActivity(Objects.requireNonNull(getContext()),
                        Consts.FRAGMENT_AUDIOBOOK, description.getAutorUrl()));

        mArtist.setOnClickListener(
                view -> MainActivity.startMainActivity(Objects.requireNonNull(getContext()),
                        Consts.FRAGMENT_AUDIOBOOK, description.getArtistUrl()));

        mSeries.setOnClickListener(view -> {
            if (activity != null) {
                activity.setTabPostion(getResources().getString(R.string.tab_text_3));
            }
        });
    }


    @Override
    public void showRefreshDialog() {
        mProgressBar.setVisibility(View.GONE);
        AlertDialog.Builder builder = new AlertDialog.Builder(Objects.requireNonNull(getContext()));
        builder.setTitle(getResources().getString(R.string.error_load_data))
                .setIcon(R.drawable.ic_launcher_foreground)
                .setCancelable(true)
                .setNegativeButton(getResources().getString(R.string.try_again),
                        (dialog, id) -> {
                            mPresenter.loadDescription();
                            dialog.cancel();
                        });
        AlertDialog alert = builder.create();
        alert.show();
    }
}
