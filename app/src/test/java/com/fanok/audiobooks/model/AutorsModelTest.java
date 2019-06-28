package com.fanok.audiobooks.model;

import com.fanok.audiobooks.pojo.GenrePOJO;

import org.junit.Test;

import java.util.ArrayList;

public class AutorsModelTest {

    @Test
    public void loadBooksList() {
        final String url = "https://audioknigi.club/performers/";
        AutorsModel genreModel = new AutorsModel();
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