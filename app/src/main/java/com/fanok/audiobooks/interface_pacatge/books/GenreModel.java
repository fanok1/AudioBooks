package com.fanok.audiobooks.interface_pacatge.books;

import com.fanok.audiobooks.pojo.GenrePOJO;
import com.fanok.audiobooks.search.ProviderPage;

import io.reactivex.Single;

public interface GenreModel {
    /** One page of a genre, author or performer listing at {@code url}. */
    Single<ProviderPage<GenrePOJO>> getBooks(String url, int page);
}
