package com.fanok.audiobooks.model;

import com.fanok.audiobooks.pojo.GenrePOJO;

import org.junit.Test;

import java.util.ArrayList;

public class GenreModelTest {

    @Test
    public void getBooks() {
        final String url = "https://audioknigi.club/sections/";
        GenreModel genreModel = new GenreModel();
        ArrayList<GenrePOJO> genres;
        try {
            genres = genreModel.loadBooksList(url);
            for (GenrePOJO genre : genres) {
                System.out.println(
                        "{" +
                                genre.getName() +
                                " " + genre.getUrl() +
                                " " + genre.getReting() +
                                "}"
                );
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}