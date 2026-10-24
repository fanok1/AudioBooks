package com.fanok.audiobooks.presenter;


import android.util.Log;
import androidx.annotation.NonNull;
import com.arellomobile.mvp.InjectViewState;
import com.arellomobile.mvp.MvpPresenter;
import com.fanok.audiobooks.App;
import com.fanok.audiobooks.Consts;
import com.fanok.audiobooks.CookesExeption;
import com.fanok.audiobooks.R;
import com.fanok.audiobooks.interface_pacatge.book_content.Description;
import com.fanok.audiobooks.interface_pacatge.book_content.DescriptionModel;
import com.fanok.audiobooks.interface_pacatge.book_content.DescriptionPresenter;
import com.fanok.audiobooks.model.BookDescriptionModel;
import com.fanok.audiobooks.pojo.BookPOJO;
import com.fanok.audiobooks.pojo.DescriptionPOJO;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;
import java.io.IOException;

@InjectViewState
public class BookDescriptionPresenter extends MvpPresenter<Description> implements
        DescriptionPresenter {

    private static final String TAG = "BookDescriptionPresente";

    private final CompositeDisposable mDisposables = new CompositeDisposable();

    private boolean isLoading = false;

    private boolean mBooksLoading = false;

    private boolean mBooksLoaded = false;

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
    public void attachView(Description view) {
        super.attachView(view);
        // onDestroy() also runs from the fragment's onDestroyView(): restart whatever it cancelled
        if (!isLoading && mDescriptionPOJO == null) {
            loadDescription();
        }
        if (!mBooksLoading && !mBooksLoaded) {
            loadBooks();
        }
    }

    @Override
    public void onDestroy() {
        mDisposables.clear();
        isLoading = false;
        mBooksLoading = false;
    }

    @Override
    public void loadDescription() {
        getViewState().showProgress(true);
        getData();
    }

    private void getData() {
        if (!isLoading) {
            isLoading = true;
            mDisposables.add(mModelDescription.getDescription()
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(this::showDescription, this::onDescriptionError));
        }
    }

    /**
     * Offline (no network at all) a failed load is no error to report: the saved description shows quietly, and a
     * short offline hint tells what is missing when there is no description text (nothing saved, or only the
     * history row). Online every failure keeps its message, with the saved description as a fallback.
     */
    private void onDescriptionError(Throwable e) {
        Log.w(TAG, "description not loaded", e);
        boolean offline = e instanceof IOException && Consts.isOffline(App.getInstance());
        DescriptionPOJO fallback = mBookPOJO.getDescriptionPOJO();
        mDisposables.add(mModelDescription.getSavedDescription()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(saved -> {
                            if (!offline) {
                                getViewState().showToast(errorMessage(e));
                            } else if (saved.getDescription() == null || saved.getDescription().isEmpty()) {
                                getViewState().showToast(R.string.offline_description);
                            }
                            showDescription(saved);
                        },
                        error -> {
                            getViewState().showToast(errorMessage(e));
                            showDescription(fallback);
                        },
                        () -> {
                            getViewState().showToast(offline ? R.string.offline_description : errorMessage(e));
                            showDescription(fallback);
                        }));
    }

    private void showDescription(DescriptionPOJO descriptionPOJO) {
        mDescriptionPOJO = descriptionPOJO;
        getViewState().showDescription(mDescriptionPOJO);
        getViewState().showProgress(false);
        isLoading = false;
    }

    /**
     * The message for a failed load: a site challenge ({@link CookesExeption}), an HTTP error
     * ({@code HttpStatusException}) or other network/page failure ({@link IOException}), an unsupported source.
     */
    static int errorMessage(Throwable e) {
        if (e instanceof CookesExeption) {
            return CookesExeption.messageFor(e.getMessage());
        }
        if (e instanceof IOException) {
            return R.string.error_load_data;
        }
        if (e instanceof IllegalArgumentException) {
            return R.string.worong_data_sorce;
        }
        return R.string.error_display_data;
    }

    private void loadBooks() {
        mBooksLoading = true;
        mDisposables.add(mModelDescription.getBooks()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(bookPOJOS -> {
                    mBooksLoading = false;
                    mBooksLoaded = true;
                    getViewState().showOtherBooks(bookPOJOS);
                }, e -> {
                    mBooksLoading = false;
                    mBooksLoaded = true;
                    Log.d(TAG, "other books not loaded: " + e);
                    getViewState().showOtherBooksLine(false);
                }));
    }


}
