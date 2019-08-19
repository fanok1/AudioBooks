package com.fanok.audiobooks.interface_pacatge.book_content;

import android.support.annotation.NonNull;

import com.fanok.audiobooks.pojo.OtherArtistPOJO;

import java.util.ArrayList;

import io.reactivex.Observable;

public interface OtherArtistModel {
    Observable<ArrayList<OtherArtistPOJO>> getOtherArtist(@NonNull String url);
}
