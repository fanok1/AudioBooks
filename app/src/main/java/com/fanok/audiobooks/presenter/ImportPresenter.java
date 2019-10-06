package com.fanok.audiobooks.presenter;


import android.widget.EditText;

import com.arellomobile.mvp.InjectViewState;
import com.arellomobile.mvp.MvpPresenter;
import com.fanok.audiobooks.R;
import com.fanok.audiobooks.interface_pacatge.import_favorite.ActivityImportInterface;
import com.fanok.audiobooks.interface_pacatge.import_favorite.ImportPresenterInterface;

import org.jetbrains.annotations.NotNull;

@InjectViewState
public class ImportPresenter extends MvpPresenter<ActivityImportInterface> implements
        ImportPresenterInterface {


    @Override
    public void validate(@NotNull EditText editText) {
        if (editText.getText().toString().isEmpty()) {
            editText.setError(editText.getContext().getString(R.string.empty_text));
        }
    }

    @Override
    public void login(@NotNull String username, @NotNull String password) {
        getViewState().showToast(R.string.sort_value_series);
    }
}