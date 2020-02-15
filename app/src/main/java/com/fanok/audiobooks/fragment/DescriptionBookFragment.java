package com.fanok.audiobooks.fragment;

import static android.content.Context.UI_MODE_SERVICE;

import static java.lang.Integer.MAX_VALUE;

import android.app.UiModeManager;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Bundle;
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
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.arellomobile.mvp.MvpAppCompatFragment;
import com.arellomobile.mvp.presenter.InjectPresenter;
import com.arellomobile.mvp.presenter.ProvidePresenter;
import com.fanok.audiobooks.Consts;
import com.fanok.audiobooks.MarginItemDecoration;
import com.fanok.audiobooks.R;
import com.fanok.audiobooks.activity.BookActivity;
import com.fanok.audiobooks.activity.ImageFullScreenActivity;
import com.fanok.audiobooks.activity.MainActivity;
import com.fanok.audiobooks.adapter.BooksOtherAdapter;
import com.fanok.audiobooks.interface_pacatge.book_content.Description;
import com.fanok.audiobooks.pojo.BookPOJO;
import com.fanok.audiobooks.pojo.DescriptionPOJO;
import com.fanok.audiobooks.presenter.BookDescriptionPresenter;
import com.google.gson.Gson;
import com.squareup.picasso.Picasso;

import java.util.ArrayList;
import java.util.Objects;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.Unbinder;

public class DescriptionBookFragment extends MvpAppCompatFragment implements Description {

    private static final String TAG = "DescriptionBookFragment";
    private static final String ARG_BOOK_POJO = "arg_book_pojo";
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
    @BindView(R.id.descLine)
    View mDescLine;
    @BindView(R.id.otherBookLine)
    View mOtherBookLine;
    private BooksOtherAdapter mAdapterBooksRecomended;
    private boolean showMore;

    public static DescriptionBookFragment newInstance(@NonNull BookPOJO pojo) {
        Bundle args = new Bundle();
        args.putString(ARG_BOOK_POJO, new Gson().toJson(pojo));
        DescriptionBookFragment fragment = new DescriptionBookFragment();
        fragment.setArguments(args);
        return fragment;
    }

    @ProvidePresenter
    BookDescriptionPresenter provide() {
        BookPOJO pojo = new Gson().fromJson(
                Objects.requireNonNull(getArguments()).getString(ARG_BOOK_POJO), BookPOJO.class);
        if (pojo == null) throw new NullPointerException();
        return new BookDescriptionPresenter(pojo);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {

        View view;
        UiModeManager uiModeManager = (UiModeManager) Objects.requireNonNull(
                container).getContext().getSystemService(UI_MODE_SERVICE);

        SharedPreferences pref = PreferenceManager
                .getDefaultSharedPreferences(Objects.requireNonNull(getContext()));

        int isTablet = getResources().getInteger(R.integer.isTablet);
        if (pref.getBoolean("androidAutoPref", false) && isTablet != 0) {
            view = inflater.inflate(R.layout.fragment_book_description_auto, container, false);
        } else if (uiModeManager != null
                && uiModeManager.getCurrentModeType() == Configuration.UI_MODE_TYPE_TELEVISION) {
            view = inflater.inflate(R.layout.fragment_book_description_television, container,
                    false);
        } else {
            view = inflater.inflate(R.layout.fragment_book_description, container, false);
        }
        unbinder = ButterKnife.bind(this, view);
        mAdapterBooksRecomended = new BooksOtherAdapter(Objects.requireNonNull(getContext()));
        mRecommendedBooks.setAdapter(mAdapterBooksRecomended);
        mRecommendedBooks.setLayoutManager(
                new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));
        int margin = (int) getContext().getResources().getDimension(R.dimen.books_other_margin);
        mRecommendedBooks.addItemDecoration(new MarginItemDecoration(margin));
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
            showOtherBooksLine(true);
        } else {
            mRecommendedBooks.setVisibility(View.GONE);
            mRecommendedBooksTitle.setVisibility(View.GONE);
            showOtherBooksLine(false);
        }

    }

    @Override
    public void showDescription(@NonNull DescriptionPOJO description) {
        try {
            if (mTitle != null && description.getTitle() != null
                    && !description.getTitle().isEmpty()) {
                mTitle.setText(description.getTitle());
            }

            if (mImageView != null) {
                Picasso.get()
                        .load(description.getPoster())
                        .placeholder(android.R.drawable.ic_menu_gallery)
                        .error(android.R.drawable.ic_menu_gallery)
                        .into(mImageView);
            }

            BookActivity activity = (BookActivity) getActivity();

            if (activity != null && mImageView != null) {
                mImageView.setOnClickListener(view ->
                        ImageFullScreenActivity.start(activity,
                                description.getPoster(), description.getTitle(),
                                mImageView));
            }

            if (mGenre != null) {
                if (description.getGenre() != null && !description.getGenre().isEmpty()) {
                    mGenre.setText(description.getGenre());
                } else if (mGenre.getVisibility() != View.GONE) {
                    mGenre.setVisibility(View.GONE);
                }
            }

            if (mReting != null) {
                if (description.getReiting() != 0) {
                    String reting = String.valueOf(description.getReiting());
                    mReting.setText(reting);
                    mReting.setVisibility(View.VISIBLE);
                } else {
                    mReting.setVisibility(View.GONE);
                }
            }

            if (mTime != null) {
                if (!description.getTime().isEmpty()) {
                    mTime.setText(description.getTime());
                    mTime.setVisibility(View.VISIBLE);
                } else if (mTime.getVisibility() != View.GONE) {
                    mTime.setVisibility(View.GONE);
                }
            }


            if (mAuthor != null && description.getAutor() != null) {
                mAuthor.setText(description.getAutor());
            }
            if (mArtist != null && description.getArtist() != null) {
                mArtist.setText(description.getArtist());
            }

            if (mSeries != null) {
                if (!description.getSeries().isEmpty()) {
                    mSeries.setText(description.getSeries());
                    if (activity != null) {
                        activity.showSiries();
                    }
                } else {
                    if (mSeriesConteiner != null && mSeriesConteiner.getVisibility() != View.GONE) {
                        mSeriesConteiner.setVisibility(View.GONE);
                    }
                }
            } else if (mSeriesConteiner != null && mSeriesConteiner.getVisibility() != View.GONE) {
                mSeriesConteiner.setVisibility(View.GONE);
            }

            if (description.isOtherReader()) {
                if (activity != null) {
                    activity.showOtherArtist();
                }
            }


            if (mDesc != null) {
                mDesc.setMaxLines(MAX_VALUE);
                if (description.getDescription() != null) {
                    mDesc.setText(description.getDescription());
                } else {
                    mDesc.setText("");
                }
                mDesc.post(() -> {
                    if (mShowMore != null) {
                        if (mDesc == null || mDesc.getLineCount() <= MAX_LINES) {
                            mShowMore.setVisibility(View.GONE);
                        } else {
                            mShowMore.setVisibility(View.VISIBLE);
                        }
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
                    }
                    mDesc.setMaxLines(MAX_LINES);
                    showMore = false;

                });

                if (description.getDescription() == null
                        || description.getDescription().isEmpty()) {
                    mDescLine.setVisibility(View.GONE);
                }
            } else {
                if (mShowMore != null && mShowMore.getVisibility() != View.GONE) {
                    mShowMore.setVisibility(View.GONE);
                }
            }


            if (mFavorite != null) {
                if (description.getFavorite() != 0) {
                    mFavorite.setText(String.valueOf(description.getFavorite()));
                    mFavorite.setVisibility(View.VISIBLE);
                } else {
                    mFavorite.setVisibility(View.GONE);
                }
            }
            if (mLike != null) {
                mLike.setText(String.valueOf(description.getLike()));
            }
            if (mDisLike != null) {
                mDisLike.setText(String.valueOf(description.getDisLike()));
            }

            if (description.getDisLike() == 0 && description.getLike() == 0) {
                mDisLike.setVisibility(View.GONE);
                mLike.setVisibility(View.GONE);
            } else {
                mDisLike.setVisibility(View.VISIBLE);
                mLike.setVisibility(View.VISIBLE);
            }


            if (mGenre != null) {
                mGenre.setOnClickListener(
                        view -> MainActivity.startMainActivity(Objects.requireNonNull(getContext()),
                                Consts.FRAGMENT_AUDIOBOOK, description.getGenreUrl()));
            }

            if (mAuthor != null) {
                mAuthor.setOnClickListener(
                        view -> MainActivity.startMainActivity(Objects.requireNonNull(getContext()),
                                Consts.FRAGMENT_AUDIOBOOK, description.getAutorUrl()));
            }

            if (mArtist != null) {
                mArtist.setOnClickListener(
                        view -> MainActivity.startMainActivity(Objects.requireNonNull(getContext()),
                                Consts.FRAGMENT_AUDIOBOOK, description.getArtistUrl()));
            }

            if (mSeries != null) {
                mSeries.setOnClickListener(view -> {
                    if (activity != null) {
                        activity.setTabPostion(getResources().getString(R.string.tab_text_3));
                    }
                });
            }
        } catch (Exception e) {
            if (getActivity() != null) {
                getActivity().recreate();
            }
        }
    }

    @Override
    public void showOtherBooksLine(boolean b) {
        if (b) {
            mOtherBookLine.setVisibility(View.VISIBLE);
        } else {
            mOtherBookLine.setVisibility(View.GONE);
        }
    }
}
