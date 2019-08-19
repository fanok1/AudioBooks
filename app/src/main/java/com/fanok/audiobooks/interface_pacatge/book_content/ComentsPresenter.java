package com.fanok.audiobooks.interface_pacatge.book_content;

import android.support.annotation.NonNull;

public interface ComentsPresenter {
    void onCreate(@NonNull String url);

    void onDestroy();

    void loadComents();

}
