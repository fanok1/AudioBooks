package com.fanok.audiobooks.pojo;

import androidx.annotation.NonNull;

import java.util.ArrayList;

public class SearcheblPOJO {
    private String autorsCount = "";
    private String seriesCount = "";
    private ArrayList<SearchebleArrayPOJO> autorsList;
    private ArrayList<SearchebleArrayPOJO> seriesList;

    public SearcheblPOJO() {
        autorsList = new ArrayList<>();
        seriesList = new ArrayList<>();
    }

    public String getAutorsCount() {
        return autorsCount;
    }

    public void setAutorsCount(@NonNull String autorsCount) {
        this.autorsCount = autorsCount;
    }

    public String getSeriesCount() {
        return seriesCount;
    }

    public void setSeriesCount(@NonNull String seriesCount) {
        this.seriesCount = seriesCount;
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
}
