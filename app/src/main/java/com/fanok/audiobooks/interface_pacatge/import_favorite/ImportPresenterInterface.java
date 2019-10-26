package com.fanok.audiobooks.interface_pacatge.import_favorite;

import android.widget.EditText;

import org.jetbrains.annotations.NotNull;

public interface ImportPresenterInterface {

    void validate(@NotNull EditText editText);

    void login(@NotNull String username, @NotNull String password);
}
