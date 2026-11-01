package com.fanok.audiobooks.interface_pacatge.book_content;

import com.fanok.audiobooks.pojo.BookPOJO;
import com.fanok.audiobooks.pojo.DescriptionPOJO;

import java.util.ArrayList;

import io.reactivex.Maybe;
import io.reactivex.Observable;

public interface DescriptionModel {
    Observable<DescriptionPOJO> getDescription();

    Observable<ArrayList<BookPOJO>> getBooks();

    /** The locally saved copy of the book, used when the page cannot be loaded. */
    Maybe<DescriptionPOJO> getSavedDescription();
}
