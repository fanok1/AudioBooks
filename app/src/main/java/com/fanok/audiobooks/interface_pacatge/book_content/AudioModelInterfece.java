package com.fanok.audiobooks.interface_pacatge.book_content;

import androidx.annotation.NonNull;

import com.fanok.audiobooks.pojo.AudioPOJO;

import java.util.ArrayList;

import io.reactivex.Observable;

public interface AudioModelInterfece {
    Observable<ArrayList<AudioPOJO>> getAudio(@NonNull String url);
}
