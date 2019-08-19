package com.fanok.audiobooks.interface_pacatge.book_content;

import android.support.annotation.NonNull;

import io.reactivex.Observable;

public interface AudioModelInterfece {
    Observable<Integer> getAudio(@NonNull String url);
}
