package com.fanok.audiobooks;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.fanok.audiobooks.fragment.BooksFragment;
import com.fanok.audiobooks.interface_pacatge.books.BooksView;
import com.fanok.audiobooks.model.BooksDBModel;
import com.fanok.audiobooks.pojo.BookPOJO;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import org.jetbrains.annotations.NotNull;

public class MenuBSBooks extends BottomSheetDialogFragment {


    private TextView mOpen;
    private TextView mAddFavorite;
    private TextView mRemoveFavorite;
    private TextView mGenre;
    private TextView mAuthor;
    private TextView mArtist;
    private TextView mSeries;

    private BooksDBModel mBooksDBModel;

    private BookPOJO mBook;

    private final BooksView mViewState;

    public MenuBSBooks(@NotNull BooksView viewState) {
        mViewState = viewState;
    }

    public BooksView getViewState() {
        return mViewState;
    }

    public void setBook(@NotNull BookPOJO book) {
        mBook = book;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        mBooksDBModel = new BooksDBModel(getContext());
        return inflater.inflate(R.layout.bootom_sheet_books_menu, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mOpen = view.findViewById(R.id.open);
        mAddFavorite = view.findViewById(R.id.addFavorite);
        mRemoveFavorite = view.findViewById(R.id.removeFavorite);
        mGenre = view.findViewById(R.id.genre);
        mAuthor = view.findViewById(R.id.author);
        mArtist = view.findViewById(R.id.artist);
        mSeries = view.findViewById(R.id.series);

        if (mBook != null) {

            if (mBook.getSeries() == null || mBook.getUrlSeries() == null) {
                mSeries.setVisibility(View.GONE);
            } else {
                mSeries.setVisibility(View.VISIBLE);
            }

            if (mBooksDBModel.inFavorite(mBook)) {
                mAddFavorite.setVisibility(View.GONE);
                mRemoveFavorite.setVisibility(View.VISIBLE);
            } else {
                mAddFavorite.setVisibility(View.VISIBLE);
                mRemoveFavorite.setVisibility(View.GONE);
            }

            mOpen.setOnClickListener(view1 -> {
                dismiss();
                getViewState().showBooksActivity(mBook);
            });

            mAddFavorite.setOnClickListener(view12 -> {
                dismiss();
                mBooksDBModel.addFavorite(mBook);
            });

            mRemoveFavorite.setOnClickListener(view13 -> {
                dismiss();
                mBooksDBModel.removeFavorite(mBook);
            });

            mGenre.setOnClickListener(view14 -> {
                dismiss();
                getViewState().showFragment(BooksFragment.newInstance(
                        mBook.getUrlGenre(),
                        R.string.menu_audiobooks,
                        mBook.getGenre(), Consts.MODEL_BOOKS),
                        "genreBooks");
            });

            mAuthor.setOnClickListener(view15 -> {
                if (!mBook.getUrlAutor().isEmpty()) {
                    getViewState().showFragment(BooksFragment.newInstance(
                            mBook.getUrlAutor(),
                            R.string.menu_audiobooks,
                            mBook.getAutor(), Consts.MODEL_BOOKS),
                            "autorBooks");
                }
            });

            mArtist.setOnClickListener(
                    view16 -> getViewState().showFragment(BooksFragment.newInstance(
                            mBook.getUrlArtist(),
                            R.string.menu_audiobooks,
                            mBook.getArtist(), Consts.MODEL_BOOKS),
                            "artistBooks"));

            mSeries.setOnClickListener(
                    view17 -> getViewState().showFragment(BooksFragment.newInstance(
                            mBook.getUrlSeries() + "?page=",
                            R.string.menu_audiobooks,
                            mBook.getSeries(), Consts.MODEL_BOOKS),
                            "seriesBooks"));

        }

    }

    @Override
    public void onDestroyView() {
        mBooksDBModel.closeDB();
        super.onDestroyView();
    }
}

