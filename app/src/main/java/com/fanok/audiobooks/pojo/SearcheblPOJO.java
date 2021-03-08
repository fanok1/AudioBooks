package com.fanok.audiobooks.pojo;

import androidx.annotation.NonNull;
import java.util.ArrayList;

public class SearcheblPOJO {

    private ArrayList<SearchebleArrayPOJO> autorsList;

    private ArrayList<SearchebleArrayPOJO> seriesList;

    public SearcheblPOJO() {
        autorsList = new ArrayList<>();
        seriesList = new ArrayList<>();
    }

    public SearcheblPOJO concat(SearcheblPOJO a, SearcheblPOJO b) {
        SearcheblPOJO c = new SearcheblPOJO();
        c.autorsList.addAll(a.autorsList);
        c.autorsList.addAll(b.autorsList);
        c.seriesList.addAll(a.seriesList);
        c.seriesList.addAll(b.seriesList);
        return c;
    }

    public String getAutorsCount() {
        return getCount(autorsList.size(), "Автор", "Автора", "Авторов");
    }


    public ArrayList<SearchebleArrayPOJO> getAutorsList() {
        return autorsList;
    }

    public void setAutorsList(@NonNull ArrayList<SearchebleArrayPOJO> autorsList) {
        this.autorsList = autorsList;
    }

    public ArrayList<SearchebleArrayPOJO> getSeriesList() {
        return seriesList;
    }

    public void setSeriesList(@NonNull ArrayList<SearchebleArrayPOJO> seriesList) {
        this.seriesList = seriesList;
    }

    public String getSeriesCount() {
        return getCount(seriesList.size(), "Цикл", "Цикла", "Циклов");
    }

    private String getCount(int n, String forma1, String forma2, String forma5) {
        int n1 = Math.abs(n) % 100;
        int n2 = n1 % 10;
        if (n1 > 10 && n1 < 20) {
            return n + " " + forma5;
        }
        if (n2 > 1 && n2 < 5) {
            return n + " " + forma2;
        }
        if (n2 == 1) {
            return n + " " + forma1;
        }
        return n + " " + forma5;

    }
}
