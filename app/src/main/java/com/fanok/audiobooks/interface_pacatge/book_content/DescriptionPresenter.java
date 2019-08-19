package com.fanok.audiobooks.interface_pacatge.book_content;

import android.support.annotation.NonNull;

public interface DescriptionPresenter {
    void onCreate(@NonNull String url);

    void onDestroy();

    void loadDescription();

}
