package com.fanok.audiobooks.model;

import org.junit.Test;

import java.io.IOException;

public class AudioModelTest {

    @Test
    public void loadSeriesList() {
        try {
            new AudioModel().loadSeriesList("https://knigavuhe.org/book/belorskie-khroniki/");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}