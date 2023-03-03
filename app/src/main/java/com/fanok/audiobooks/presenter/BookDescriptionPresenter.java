package com.fanok.audiobooks.presenter;


import android.util.Log;
import androidx.annotation.NonNull;
import com.arellomobile.mvp.InjectViewState;
import com.arellomobile.mvp.MvpPresenter;
import com.fanok.audiobooks.CookesExeption;
import com.fanok.audiobooks.R;
import com.fanok.audiobooks.Url;
import com.fanok.audiobooks.interface_pacatge.book_content.Description;
import com.fanok.audiobooks.interface_pacatge.book_content.DescriptionModel;
import com.fanok.audiobooks.interface_pacatge.book_content.DescriptionPresenter;
import com.fanok.audiobooks.model.BookDescriptionModel;
import com.fanok.audiobooks.pojo.BookPOJO;
import com.fanok.audiobooks.pojo.DescriptionPOJO;
import io.reactivex.Observer;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import java.util.ArrayList;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;

@InjectViewState
public class BookDescriptionPresenter extends MvpPresenter<Description> implements
        DescriptionPresenter {

    private static final String TAG = "BookDescriptionPresente";

    private boolean isLoading = false;

    private final BookPOJO mBookPOJO;

    private DescriptionPOJO mDescriptionPOJO;

    private final DescriptionModel mModelDescription;

    public BookDescriptionPresenter(@NonNull BookPOJO bookPOJO) {
        mModelDescription = new BookDescriptionModel(bookPOJO.getUrl());
        mBookPOJO = bookPOJO;
    }

    @Override
    protected void onFirstViewAttach() {
        super.onFirstViewAttach();
        loadDescription();
        loadBooks();
    }

    @Override
    public void onDestroy() {

    }

    @Override
    public void loadDescription() {
        getViewState().showProgress(true);
        getData();
    }

    private void getData() {
        if (!isLoading) {
            isLoading = true;
            mModelDescription.getDescription()
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(new Observer<DescriptionPOJO>() {
                        @Override
                        public void onSubscribe(Disposable d) {
                        }

                        @Override
                        public void onError(@NotNull Throwable e) {
                            if (e.getClass() == CookesExeption.class) {
                                if (Objects.requireNonNull(e.getMessage()).contains(Url.SERVER_BAZA_KNIG)) {
                                    getViewState().showToast(R.string.cookes_baza_knig_exeption);
                                }
                            }
                            mDescriptionPOJO = mBookPOJO.getDescriptionPOJO();
                            onComplete();
                        }

                        @Override
                        public void onNext(@NotNull DescriptionPOJO descriptionPOJO) {
                            mDescriptionPOJO = descriptionPOJO;
                        }

                        @Override
                        public void onComplete() {
                            Log.d(TAG, "onComplete");
                            getViewState().showDescription(mDescriptionPOJO);
                            getViewState().showProgress(false);
                            isLoading = false;
                        }
                    });

        }
    }

    private void loadBooks() {
        mModelDescription.getBooks()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Observer<ArrayList<BookPOJO>>() {
                    @Override
                    public void onComplete() {
                        Log.d(TAG, "onComplete");
                    }

                    @Override
                    public void onError(@NotNull Throwable e) {

                        if (e.getMessage() != null) {
                            Log.d(TAG, e.getMessage());
                        }
                        getViewState().showOtherBooksLine(false);
                    }

                    @Override
                    public void onNext(@NotNull ArrayList<BookPOJO> bookPOJOS) {
                        getViewState().showOtherBooks(bookPOJOS);
                    }

                    @Override
                    public void onSubscribe(@NotNull Disposable d) {
                    }
                });
    }


}
