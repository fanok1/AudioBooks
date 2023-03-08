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
import android.widget.Toast;
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

public class DescriptionBookFragment extends MvpAppCompatFragment implements Description {

    private static final String TAG = "DescriptionBookFragment";
    private static final String ARG_BOOK_POJO = "arg_book_pojo";
    private static final int MAX_LINES = 4;

    private TextView mTitle;
    private ImageView mImageView;
    private TextView mReting;
    private TextView mGenre;
    private TextView mTime;
    private TextView mAuthor;
    private LinearLayout mAutorConteiner;
    private TextView mArtist;
    private LinearLayout mArtistConteiner;
    private TextView mSeries;
    private LinearLayout mSeriesConteiner;
    private TextView mDesc;
    private TextView mShowMore;
    private RecyclerView mRecommendedBooks;
    private TextView mRecommendedBooksTitle;
    private ScrollView mScrollView;
    private ProgressBar mProgressBar;
    private TextView mFavorite;
    private TextView mLike;
    private TextView mDisLike;
    private View mDescLine;
    private View mOtherBookLine;
    @InjectPresenter
    BookDescriptionPresenter mPresenter;
    private BooksOtherAdapter mAdapterBooksRecomended;
    private boolean showMore;

    public static DescriptionBookFragment newInstance(@NonNull BookPOJO pojo) {
        Bundle args = new Bundle();
        args.putString(ARG_BOOK_POJO, new Gson().toJson(pojo));
        DescriptionBookFragment fragment = new DescriptionBookFragment();
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {

        View view;
        UiModeManager uiModeManager = (UiModeManager) Objects.requireNonNull(
                container).getContext().getSystemService(UI_MODE_SERVICE);

        SharedPreferences pref = PreferenceManager
                .getDefaultSharedPreferences(requireContext());

        if (uiModeManager != null && uiModeManager.getCurrentModeType() == Configuration.UI_MODE_TYPE_TELEVISION) {
            view = inflater.inflate(R.layout.fragment_book_description_television, container, false);
        } else {
            view = inflater.inflate(R.layout.fragment_book_description, container, false);
        }

        mTitle = view.findViewById(R.id.title);
        mImageView = view.findViewById(R.id.imageView);
        mReting = view.findViewById(R.id.reting);
        mGenre = view.findViewById(R.id.genre);
        mTime = view.findViewById(R.id.time);
        mAuthor = view.findViewById(R.id.author);
        mAutorConteiner = view.findViewById(R.id.autorConteiner);
        mArtist = view.findViewById(R.id.artist);
        mArtistConteiner = view.findViewById(R.id.artistConteiner);
        mSeries = view.findViewById(R.id.series);
        mSeriesConteiner = view.findViewById(R.id.seriesConteiner);
        mDesc = view.findViewById(R.id.desc);
        mShowMore = view.findViewById(R.id.showMore);
        mRecommendedBooks = view.findViewById(R.id.recommendedBooks);
        mRecommendedBooksTitle = view.findViewById(R.id.recommendedBooksTitle);
        mScrollView = view.findViewById(R.id.scrollView);
        mProgressBar = view.findViewById(R.id.progressBar);
        mFavorite = view.findViewById(R.id.favorite);
        mLike = view.findViewById(R.id.like);
        mDisLike = view.findViewById(R.id.disLike);
        mDescLine = view.findViewById(R.id.descLine);
        mOtherBookLine = view.findViewById(R.id.otherBookLine);

        mAdapterBooksRecomended = new BooksOtherAdapter();
        mRecommendedBooks.setAdapter(mAdapterBooksRecomended);
        mRecommendedBooks.setLayoutManager(
                new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));
        int margin = (int) requireContext().getResources().getDimension(R.dimen.books_other_margin);
        mRecommendedBooks.addItemDecoration(new MarginItemDecoration(margin));
        return view;
    }

    @Override
    public void onDestroyView() {
        mPresenter.onDestroy();
        super.onDestroyView();
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
                if (!description.getReiting().equals("0") && !description.getReiting().isEmpty()) {
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
                if (!description.getFavorite().equals("0")) {
                    mFavorite.setText(description.getFavorite());
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

            if (description.getDisLike().equals("0") && description.getLike().equals("0")) {
                mDisLike.setVisibility(View.GONE);
                mLike.setVisibility(View.GONE);
            } else {
                mDisLike.setVisibility(View.VISIBLE);
                mLike.setVisibility(View.VISIBLE);
            }

            if (mGenre != null && description.getGenreUrl() != null && !description.getGenreUrl().isEmpty()) {
                mGenre.setOnClickListener(
                        view -> MainActivity.startMainActivity(requireContext(),
                                Consts.FRAGMENT_AUDIOBOOK, description.getGenreUrl()));
            }

            if (mAuthor != null && description.getAutorUrl() != null && !description.getAutorUrl().isEmpty()) {
                mAuthor.setOnClickListener(
                        view -> MainActivity.startMainActivity(requireContext(),
                                Consts.FRAGMENT_AUDIOBOOK, description.getAutorUrl()));
            }

            if (mArtist != null && description.getArtistUrl() != null && !description.getArtistUrl().isEmpty()) {
                mArtist.setOnClickListener(
                        view -> MainActivity.startMainActivity(requireContext(),
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

    public void showOtherBooks(@NonNull ArrayList<BookPOJO> data) {
        if (mAdapterBooksRecomended != null) {
            mAdapterBooksRecomended.setData(data);
        }
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
    public void showProgress(boolean b) {
        if (b) {
            mProgressBar.setVisibility(View.VISIBLE);
            mScrollView.setVisibility(View.INVISIBLE);
        } else {
            mProgressBar.setVisibility(View.GONE);
            mScrollView.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void showToast(final int id) {
        Toast.makeText(getContext(), getResources().getText(id),
                Toast.LENGTH_LONG).show();
    }

    @Override
    public void showOtherBooksLine(boolean b) {
        if (b) {
            mOtherBookLine.setVisibility(View.VISIBLE);
        } else {
            mOtherBookLine.setVisibility(View.GONE);
        }
    }

    @ProvidePresenter
    BookDescriptionPresenter provide() {
        BookPOJO pojo = new Gson().fromJson(
                requireArguments().getString(ARG_BOOK_POJO), BookPOJO.class);
        if (pojo == null) {
            throw new NullPointerException();
        }
        return new BookDescriptionPresenter(pojo);
    }
}
