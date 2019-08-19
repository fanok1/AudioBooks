package com.fanok.audiobooks.interface_pacatge.book_content;

import com.fanok.audiobooks.pojo.BookPOJO;
import com.fanok.audiobooks.pojo.DescriptionPOJO;

import java.util.ArrayList;

import io.reactivex.Observable;

public interface DescriptionModel {
    Observable<DescriptionPOJO> getDescription();

    Observable<ArrayList<BookPOJO>> getBooks();
}
