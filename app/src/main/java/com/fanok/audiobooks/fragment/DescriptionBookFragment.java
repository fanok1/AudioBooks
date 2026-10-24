package com.fanok.audiobooks.fragment;

import static android.content.Context.UI_MODE_SERVICE;

import android.app.UiModeManager;
import android.content.res.Configuration;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
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
import com.fanok.audiobooks.adapter.ExpandableText;
import com.fanok.audiobooks.interface_pacatge.book_content.Description;
import com.fanok.audiobooks.pojo.BookPOJO;
import com.fanok.audiobooks.pojo.DescriptionPOJO;
import com.fanok.audiobooks.presenter.BookDescriptionPresenter;
import com.google.gson.Gson;
import com.squareup.picasso.Picasso;
import java.util.ArrayList;

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
    private TextView mArtist;
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
    /** Whether the description is unfolded; survives re-binds of the same view. */
    private boolean descExpanded;

    public static DescriptionBookFragment newInstance(@NonNull BookPOJO pojo) {
        Bundle args = new Bundle();
        args.putString(ARG_BOOK_POJO, new Gson().toJson(pojo));
        DescriptionBookFragment fragment = new DescriptionBookFragment();
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    private String getLocalCoverFallback() {
        try {
            Bundle args = getArguments();
            if (args != null && args.containsKey(ARG_BOOK_POJO)) {
                BookPOJO pojo = new Gson().fromJson(args.getString(ARG_BOOK_POJO), BookPOJO.class);
                if (pojo != null && getContext() != null) {
                    String cover = com.fanok.audiobooks.util.DownloadStorageHelper.findLocalCoverForBook(getContext(), pojo);
                    if (cover != null && !cover.isEmpty()) {
                        return cover;
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {


        View view = inflater.inflate(R.layout.fragment_book_description, container, false);

        mTitle = view.findViewById(R.id.title);
        mImageView = view.findViewById(R.id.imageView);
        mReting = view.findViewById(R.id.reting);
        mGenre = view.findViewById(R.id.genre);
        mTime = view.findViewById(R.id.time);
        mAuthor = view.findViewById(R.id.author);
        mArtist = view.findViewById(R.id.artist);
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
        UiModeManager uiModeManager = (UiModeManager) requireContext().getSystemService(UI_MODE_SERVICE);
        if (uiModeManager.getCurrentModeType() == Configuration.UI_MODE_TYPE_TELEVISION) {

            // Вешаем слушатель на ПОСЛЕДНИЙ интерактивный элемент в этом фрагменте.
            // В данном случае, это RecyclerView с рекомендованными книгами.
            mRecommendedBooks.setOnKeyListener((v, keyCode, event) -> {
                // Реагируем только на нажатие кнопки
                if (event.getAction() != KeyEvent.ACTION_DOWN) {
                    return false;
                }

                RecyclerView.LayoutManager layoutManager = mRecommendedBooks.getLayoutManager();
                if (layoutManager == null) {
                    return false;
                }

                // Получаем позицию текущего сфокусированного элемента
                View focusedChild = layoutManager.getFocusedChild();
                if (focusedChild == null) {
                    return false;
                }
                int focusedPos = layoutManager.getPosition(focusedChild);

                // Получаем общее количество элементов
                int itemCount = layoutManager.getItemCount();

                // Если нажата кнопка "ВНИЗ" и фокус на ПОСЛЕДНЕМ элементе списка
                if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN && focusedPos == itemCount - 1) {
                    // Находим плеер в родительской Activity
                    View playerView = requireActivity().findViewById(R.id.player);
                    if (playerView != null) {
                        // Передаем фокус плееру
                        playerView.requestFocus();
                    }
                    // Возвращаем true, говоря, что мы обработали это событие
                    return true;
                }

                // Для всех остальных случаев позволяем RecyclerView работать как обычно
                return false;
            });
        }


        return view;
    }

    @Override
    public void onDestroyView() {
        // the presenter outlives this view: drop every reference to the destroyed tree (UI-12)
        if (mImageView != null) {
            Picasso.get().cancelRequest(mImageView);
        }
        mTitle = null;
        mImageView = null;
        mReting = null;
        mGenre = null;
        mTime = null;
        mAuthor = null;
        mArtist = null;
        mSeries = null;
        mSeriesConteiner = null;
        mDesc = null;
        mShowMore = null;
        mRecommendedBooks = null;
        mRecommendedBooksTitle = null;
        mScrollView = null;
        mProgressBar = null;
        mFavorite = null;
        mLike = null;
        mDisLike = null;
        mDescLine = null;
        mOtherBookLine = null;
        mAdapterBooksRecomended = null;
        super.onDestroyView();
    }

    @Override
    public void showDescription(@NonNull DescriptionPOJO description) {
        if (mTitle == null) {
            return; // no view attached; Moxy re-delivers on the next attach
        }

        UiModeManager uiModeManager = (UiModeManager) requireContext().getSystemService(UI_MODE_SERVICE);
        if (uiModeManager.getCurrentModeType() == Configuration.UI_MODE_TYPE_TELEVISION) {
            // Устанавливаем наш селектор в качестве фона для всех интерактивных элементов
            // Система сама будет показывать/скрывать рамку при смене фокуса.
            mImageView.setBackgroundResource(R.drawable.focusable);
            mGenre.setBackgroundResource(R.drawable.focusable);
            mAuthor.setBackgroundResource(R.drawable.focusable);
            mArtist.setBackgroundResource(R.drawable.focusable);
            mSeries.setBackgroundResource(R.drawable.focusable);
            mShowMore.setBackgroundResource(R.drawable.focusable);
        }
        try {
            if (description.getTitle() != null && !description.getTitle().isEmpty()) {
                mTitle.setText(description.getTitle());
            }

            String poster = description.getPoster();
            String localCover = getLocalCoverFallback();
            if (localCover != null && !localCover.isEmpty()) {
                poster = localCover;
            }
            if (poster != null && !poster.trim().isEmpty()) {
                if (poster.startsWith("content://")) {
                    Picasso.get()
                            .load(android.net.Uri.parse(poster))
                            .placeholder(android.R.drawable.ic_menu_gallery)
                            .error(android.R.drawable.ic_menu_gallery)
                            .into(mImageView);
                } else if (poster.startsWith("/") || poster.contains(":\\")) {
                    Picasso.get()
                            .load(new java.io.File(poster))
                            .placeholder(android.R.drawable.ic_menu_gallery)
                            .error(android.R.drawable.ic_menu_gallery)
                            .into(mImageView);
                } else {
                    Picasso.get()
                            .load(poster)
                            .placeholder(android.R.drawable.ic_menu_gallery)
                            .error(android.R.drawable.ic_menu_gallery)
                            .into(mImageView);
                }
            } else {
                Picasso.get().load(android.R.drawable.ic_menu_gallery)
                        .into(mImageView);
            }

            BookActivity activity = (BookActivity) getActivity();

            String remotePoster = description.getPoster();
            if (activity != null && remotePoster != null && !remotePoster.trim().isEmpty()) {
                mImageView.setOnClickListener(view ->
                        ImageFullScreenActivity.start(activity,
                                remotePoster, description.getTitle(),
                                mImageView));
            }

            if (description.getGenre() != null && !description.getGenre().isEmpty()) {
                mGenre.setText(description.getGenre());
            } else if (mGenre.getVisibility() != View.GONE) {
                mGenre.setVisibility(View.GONE);
            }

            String retingStr = description.getReiting();
            if (retingStr != null && !retingStr.equals("0") && !retingStr.isEmpty()) {
                mReting.setText(retingStr);
                mReting.setVisibility(View.VISIBLE);
            } else {
                mReting.setVisibility(View.GONE);
            }

            String timeStr = description.getTime();
            if (timeStr != null && !timeStr.isEmpty()) {
                mTime.setText(timeStr);
                mTime.setVisibility(View.VISIBLE);
            } else if (mTime.getVisibility() != View.GONE) {
                mTime.setVisibility(View.GONE);
            }


            if (description.getAutor() != null) {
                mAuthor.setText(description.getAutor());
            }
            if (description.getArtist() != null) {
                mArtist.setText(description.getArtist());
            }

            String seriesStr = description.getSeries();
            if (seriesStr != null && !seriesStr.isEmpty()) {
                mSeries.setText(seriesStr);
                if (activity != null) {
                    activity.showSiries();
                }
            } else if (mSeriesConteiner.getVisibility() != View.GONE) {
                mSeriesConteiner.setVisibility(View.GONE);
            }

            if (description.isOtherReader()) {
                if (activity != null) {
                    activity.showOtherArtist();
                }
            }


            final String text = description.getDescription();
            ExpandableText.bind(mDesc, mShowMore, text, MAX_LINES, descExpanded);
            mShowMore.setOnClickListener(view1 -> {
                descExpanded = !descExpanded;
                if (mDesc != null) {
                    ExpandableText.bind(mDesc, mShowMore, text, MAX_LINES, descExpanded);
                }
            });
            if (text == null || text.isEmpty()) {
                mDescLine.setVisibility(View.GONE);
            }


            if (!description.getFavorite().equals("0")) {
                mFavorite.setText(description.getFavorite());
                mFavorite.setVisibility(View.VISIBLE);
            } else {
                mFavorite.setVisibility(View.GONE);
            }
            mLike.setText(String.valueOf(description.getLike()));
            mDisLike.setText(String.valueOf(description.getDisLike()));

            if (description.getDisLike().equals("0") && description.getLike().equals("0")) {
                mDisLike.setVisibility(View.GONE);
                mLike.setVisibility(View.GONE);
            } else {
                mDisLike.setVisibility(View.VISIBLE);
                mLike.setVisibility(View.VISIBLE);
            }

            if (description.getGenreUrl() != null && !description.getGenreUrl().isEmpty()) {
                mGenre.setOnClickListener(
                        view -> MainActivity.startMainActivity(requireContext(),
                                Consts.FRAGMENT_AUDIOBOOK, description.getGenreUrl()));
            }

            if (description.getAutorUrl() != null && !description.getAutorUrl().isEmpty()) {
                mAuthor.setOnClickListener(
                        view -> MainActivity.startMainActivity(requireContext(),
                                Consts.FRAGMENT_AUDIOBOOK, description.getAutorUrl()));
            }

            if (description.getArtistUrl() != null && !description.getArtistUrl().isEmpty()) {
                mArtist.setOnClickListener(
                        view -> MainActivity.startMainActivity(requireContext(),
                                Consts.FRAGMENT_AUDIOBOOK, description.getArtistUrl()));
            }

            mSeries.setOnClickListener(view -> {
                if (activity != null) {
                    activity.setTabPostion(getResources().getString(R.string.tab_text_3));
                }
            });
        } catch (RuntimeException e) {
            // a bad field must not take the screen down, and never restart it: the same data would come back
            Log.e(TAG, "showDescription: ", e);
            showToast(R.string.error_load_data);
        }
    }

    public void showOtherBooks(@NonNull ArrayList<BookPOJO> data) {
        if (mAdapterBooksRecomended == null || mRecommendedBooks == null) {
            return;
        }
        mAdapterBooksRecomended.setData(data);
        if (!data.isEmpty()) {
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
        if (mProgressBar == null) {
            return;
        }
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
        if (mOtherBookLine != null) {
            mOtherBookLine.setVisibility(b ? View.VISIBLE : View.GONE);
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
