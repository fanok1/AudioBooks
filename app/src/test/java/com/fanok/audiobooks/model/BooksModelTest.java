package com.fanok.audiobooks.model;

import com.fanok.audiobooks.pojo.BookPOJO;

import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;

public class BooksModelTest {

    @Test
    public void loadBooksList() {
        final String url = "https://audioknigi.club/index/page7/";
        BooksModel booksModel = new BooksModel();
        ArrayList<BookPOJO> books;
        try {
            books = booksModel.loadBooksList(url);
            for (BookPOJO book : books) {
                System.out.println(
                        "{" +
                                book.getName() +
                                " " + book.getUrl() +
                                " " + book.getPhoto() +
                                " " + book.getGenre() +
                                " " + book.getUrlGenre() +
                                " " + book.getReting() +
                                " " + book.getFavorite() +
                                " " + book.getComents() +
                                " " + book.getAutor() +
                                " " + book.getUrlAutor() +
                                " " + book.getArtist() +
                                " " + book.getUrlArtist() +
                                " " + book.getSeries() +
                                " " + book.getUrlSeries() +
                                " " + book.getTime() +
                                "}"
                );
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

    }
}