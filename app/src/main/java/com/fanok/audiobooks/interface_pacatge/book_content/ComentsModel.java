package com.fanok.audiobooks.interface_pacatge.book_content;

import android.support.annotation.NonNull;

import com.fanok.audiobooks.pojo.ComentsPOJO;

import java.util.ArrayList;

import io.reactivex.Observable;

public interface ComentsModel {
    Observable<ArrayList<ComentsPOJO>> getComents(@NonNull String url);
}
