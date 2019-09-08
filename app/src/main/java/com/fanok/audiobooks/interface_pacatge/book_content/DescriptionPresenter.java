package com.fanok.audiobooks.interface_pacatge.book_content;


import androidx.annotation.NonNull;

public interface DescriptionPresenter {
    void onCreate(@NonNull String url);

    void onDestroy();

    void loadDescription();

}
