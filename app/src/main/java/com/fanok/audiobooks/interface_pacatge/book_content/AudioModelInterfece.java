package com.fanok.audiobooks.interface_pacatge.book_content;

import androidx.annotation.NonNull;

import com.fanok.audiobooks.pojo.AudioPOJO;

import java.util.ArrayList;

import io.reactivex.Observable;

public interface AudioModelInterfece {
    /**
     * Emits one non-empty chapter list, then completes; otherwise errors (unknown host →
     * {@link IllegalArgumentException}, challenge page → {@link com.fanok.audiobooks.CookesExeption},
     * network/HTTP → {@link java.io.IOException}, no player or no playable chapter →
     * {@link IllegalStateException}). Errors are never returned as chapters.
     */
    Observable<ArrayList<AudioPOJO>> getAudio(@NonNull String url);
}
