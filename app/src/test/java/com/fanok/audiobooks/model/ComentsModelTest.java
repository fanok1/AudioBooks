package com.fanok.audiobooks.model;

import com.fanok.audiobooks.pojo.ComentsPOJO;

import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;

public class ComentsModelTest {

    @Test
    public void loadComentsList() {
        ComentsModel comentsModel = new ComentsModel();
        try {
            ArrayList<ComentsPOJO> comentsPOJOArrayList = comentsModel.loadComentsList(
                    "https://audioknigi.club/arsenev-sergey-studentka-komsomolka-sportsmenka"
                            + "#comment258048");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}