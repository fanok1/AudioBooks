package com.fanok.audiobooks;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import butterknife.BindView;
import com.fanok.audiobooks.fragment.BooksFragment;
import com.fanok.audiobooks.interface_pacatge.books.BooksView;
import com.fanok.audiobooks.model.BooksDBModel;
import com.fanok.audiobooks.pojo.BookPOJO;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import org.jetbrains.annotations.NotNull;

public class MenuBSBooks extends BottomSheetDialogFragment {


    @BindView(R.id.open)
    TextView mOpen;
    @BindView(R.id.addFavorite)
    TextView mAddFavorite;
    @BindView(R.id.removeFavorite)
    TextView mRemoveFavorite;
    @BindView(R.id.genre)
    TextView mGenre;
    @BindView(R.id.author)
    TextView mAuthor;
    @BindView(R.id.artist)
    TextView mArtist;
    @BindView(R.id.series)
    TextView mSeries;

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

