package com.fanok.audiobooks.interface_pacatge.import_favorite;

import android.content.Context;
import android.widget.EditText;

import org.jetbrains.annotations.NotNull;

public interface ImportPresenterInterface {

    void onCreate(@NotNull Context context, int src);

    void validate(@NotNull EditText editText);

    void login(@NotNull String username, @NotNull String password);
}
