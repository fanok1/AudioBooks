package com.fanok.audiobooks.interface_pacatge.searchable;

import androidx.annotation.NonNull;

import com.fanok.audiobooks.pojo.SearcheblPOJO;

import java.io.IOException;

import io.reactivex.Observable;

public interface SearchableModel {

    SearcheblPOJO getSearcheblPOJO(@NonNull String url) throws IOException;

    Observable<SearcheblPOJO> dowland(String url);
}
