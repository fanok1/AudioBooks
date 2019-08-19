package com.fanok.audiobooks.pojo;

import android.support.annotation.NonNull;

import com.fanok.audiobooks.Consts;

public class DescriptionPOJO {

    private String title = "";
    private String poster = "";
    private int reiting = 0;
    private String time = "";
    private String autor = "";
    private String artist = "";
    private String series = "";
    private String seriesUrl = "";
    private String description = "";
    private String autorUrl = "";
    private String artistUrl = "";
    private String genre = "";
    private String genreUrl = "";
    private int favorite = 0;
    private int like = 0;
    private int disLike = 0;
    private boolean otherReader = false;

    public boolean isOtherReader() {
        return otherReader;
    }

    public void setOtherReader(boolean otherReader) {
        this.otherReader = otherReader;
    }

    public int getLike() {
        return like;
    }

    public void setLike(int like) {
        if (like >= 0) this.like = like;
    }

    public int getDisLike() {
        return disLike;
    }

    public void setDisLike(int disLike) {
        if (disLike >= 0) this.disLike = disLike;
    }

    public int getFavorite() {
        return favorite;
    }

    public void setFavorite(int favorite) {
        if (favorite >= 0) this.favorite = favorite;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        if (title == null || title.isEmpty()) throw new NullPointerException();
        this.title = title;
    }

    public String getPoster() {
        return poster;
    }

    public void setPoster(String poster) {
        if (poster != null && Consts.REGEXP_URL_PHOTO.matcher(poster).matches()) {
            this.poster = poster;
        } else {
            this.poster = "";
        }
    }

    public int getReiting() {
        return reiting;
    }

    public void setReiting(int reiting) {
        this.reiting = reiting;
    }

    public String getTime() {
        return time;
    }

    public void setTime(String time) {
        if (time == null) {
            this.time = "";
        } else {
            this.time = time;
        }
    }

    public String getAutor() {
        return autor;
    }

    public void setAutor(@NonNull String autor) {
        if (autor.isEmpty()) throw new NullPointerException();
        this.autor = autor;
    }

    public String getArtist() {
        return artist;
    }

    public void setArtist(@NonNull String artist) {
        if (artist.isEmpty()) throw new NullPointerException();
        this.artist = artist;
    }

    public String getSeries() {
        return series;
    }

    public void setSeries(String series) {
        if (series == null) {
            this.series = "";
        } else {
            this.series = series;
        }
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        if (description == null) {
            this.description = "";
        } else {
            this.description = description;
        }
    }

    public String getAutorUrl() {
        return autorUrl;
    }

    public void setAutorUrl(@NonNull String autorUrl) {
        if (!Consts.REGEXP_URL.matcher(autorUrl).matches()) {
            this.autorUrl = "";
        } else {
            this.autorUrl = autorUrl;
        }
    }

    public String getArtistUrl() {
        return artistUrl;
    }

    public void setArtistUrl(@NonNull String artistUrl) {
        if (!Consts.REGEXP_URL.matcher(artistUrl).matches()) {
            this.artistUrl = "";
        } else {
            this.artistUrl = artistUrl;
        }
    }

    public String getGenre() {
        return genre;
    }

    public void setGenre(@NonNull String genre) {
        this.genre = genre;
    }

    public String getGenreUrl() {
        return genreUrl;
    }

    public void setGenreUrl(@NonNull String genreUrl) {
        if (!Consts.REGEXP_URL.matcher(genreUrl).matches()) {
            this.genreUrl = "";
        } else {
            this.genreUrl = genreUrl;
        }
    }

    public String getSeriesUrl() {
        return seriesUrl;
    }

    public void setSeriesUrl(String seriesUrl) {
        if (!Consts.REGEXP_URL.matcher(seriesUrl).matches()) {
            this.seriesUrl = "";
        } else {
            this.seriesUrl = seriesUrl;
        }
    }
}
