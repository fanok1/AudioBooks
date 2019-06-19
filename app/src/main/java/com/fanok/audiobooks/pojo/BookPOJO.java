package com.fanok.audiobooks.pojo;

import android.support.annotation.NonNull;

import com.fanok.audiobooks.Consts;

public class BookPOJO {
    private static final String TAG = "BookPOJO";

    private String photo;
    private String autor;
    private String artist;
    private String urlArtist;
    private String urlAutor;
    private String time = "";
    private String series = "";
    private String urlSeries;
    private String genre;
    private String urlGenre;
    private String reting = "0";
    private int favorite = 0;
    private int coments = 0;
    private String name;
    private String url;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        if (name.isEmpty()) throw new IllegalArgumentException("Value must be not empty");
        this.name = name;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        if (!Consts.REGEXP_URL.matcher(url).matches()) {
            throw new IllegalArgumentException(
                    "Value must be url");
        }
        this.url = url;
    }

    public String getPhoto() {
        return photo;
    }

    public void setPhoto(@NonNull String photo) {
        if (!Consts.REGEXP_URL_PHOTO.matcher(photo).matches()) {
            throw new IllegalArgumentException(
                    "Value must be url");
        }
        this.photo = photo;
    }

    public String getAutor() {
        return autor;
    }

    public void setAutor(@NonNull String autor) {
        if (autor.isEmpty()) throw new IllegalArgumentException("Value must be not empty");
        this.autor = autor;
    }

    public String getArtist() {
        return artist;
    }

    public void setArtist(@NonNull String artist) {
        if (artist.isEmpty()) throw new IllegalArgumentException("Value must be not empty");
        this.artist = artist;
    }

    public String getUrlArtist() {
        return urlArtist;
    }

    public void setUrlArtist(@NonNull String urlArtist) {
        if (!Consts.REGEXP_URL.matcher(urlArtist).matches()) {
            throw new IllegalArgumentException(
                    "Value must be url");
        }
        this.urlArtist = urlArtist;
    }

    public String getUrlAutor() {
        return urlAutor;
    }

    public void setUrlAutor(@NonNull String urlAutor) {
        if (!Consts.REGEXP_URL.matcher(urlAutor).matches()) {
            throw new IllegalArgumentException(
                    "Value must be url");
        }
        this.urlAutor = urlAutor;
    }

    public String getTime() {
        return time;
    }

    public void setTime(@NonNull String time) {
        if (time.isEmpty()) throw new IllegalArgumentException("Value must be not empty");
        this.time = time;
    }

    public String getSeries() {
        return series;
    }

    public void setSeries(@NonNull String series) {
        if (series.isEmpty()) throw new IllegalArgumentException("Value must be not empty");
        this.series = series;
    }

    public String getUrlSeries() {
        return urlSeries;
    }

    public void setUrlSeries(String urlSeries) {
        if (!Consts.REGEXP_URL.matcher(urlSeries).matches()) {
            throw new IllegalArgumentException(
                    "Value must be url");
        }
        this.urlSeries = urlSeries;
    }

    public String getGenre() {
        return genre;
    }

    public void setGenre(@NonNull String genre) {
        if (genre.isEmpty()) throw new IllegalArgumentException("Value must be not empty");
        this.genre = genre;
    }

    public String getUrlGenre() {
        return urlGenre;
    }

    public void setUrlGenre(@NonNull String urlGenre) {
        if (!Consts.REGEXP_URL.matcher(urlGenre).matches()) {
            throw new IllegalArgumentException(
                    "Value must be url");
        }
        this.urlGenre = urlGenre;
    }

    public String getReting() {
        return reting;
    }

    public void setReting(@NonNull String reting) {
        if (!Consts.REGEXP_RETING.matcher(reting).matches()) {
            throw new IllegalArgumentException(
                    "Incorect value");
        }
        this.reting = reting;
    }

    public int getFavorite() {
        return favorite;
    }

    public void setFavorite(int favorite) {
        if (favorite < 0) throw new IllegalArgumentException("Value must be more 0");
        this.favorite = favorite;
    }

    public int getComents() {
        return coments;
    }

    public void setComents(@NonNull String coments) {
        coments = coments.replaceAll("[^0-9]", "");
        if (!coments.matches("^\\d+$")) {
            throw new IllegalArgumentException(
                    "Value not conteins integr");
        }
        int count = Integer.parseInt(coments);
        if (count < 0) throw new IllegalArgumentException("Value must be more 0");
        this.coments = count;
    }

    public boolean isNull() {
        return photo == null || autor == null || artist == null || urlArtist == null
                || urlAutor == null || genre == null || urlGenre == null
                || name == null || url == null;
    }

}
