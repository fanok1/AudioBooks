package com.fanok.audiobooks.model;

import org.junit.Test;

import java.io.IOException;

public class SearchableModelTest {

    @Test
    public void loadBooksList() {
        SearchableModel searchableModel = new SearchableModel();
        try {
            System.out.println(
                    searchableModel.loadBooksList("https://audioknigi.club/performers/ajax-search/",
                            "Кирил"));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}