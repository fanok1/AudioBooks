package com.fanok.audiobooks.model;

import com.fanok.audiobooks.Url;

import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;

public class ParentalControlModelTest {

    @Test
    public void loadBooksList() {
        ParentalControlModel model = new ParentalControlModel();
        try {
            ArrayList<String> list = model.loadBooksList(Url.SECTIONS);
            System.out.println(list.toString());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}