package com.fanok.audiobooks.pojo;

import com.fanok.audiobooks.Consts;

public class DescriptionPOJO {

    private String title = "";

    private String poster = "";

    private String reiting = "";

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

    private String disLike = "0";

    private String favorite = "0";

    private String like = "0";

    private boolean otherReader = false;

    public boolean isOtherReader() {
        return otherReader;
    }

    public void setOtherReader(boolean otherReader) {
        this.otherReader = otherReader;
    }

    public String getDisLike() {
        return disLike;
    }

    public void setDisLike(String disLike) {
        if (disLike != null && !disLike.equals("0") && !disLike.isEmpty()) {
            this.disLike = disLike;
        }
    }

    public String getFavorite() {
        return favorite;
    }

    public void setFavorite(String favorite) {
        if (favorite != null && !favorite.equals(0) && !favorite.isEmpty()) {
            this.favorite = favorite;
        }
    }

    public String getLike() {
        return like;
    }

    public void setLike(String like) {
        if (like != null && !like.equals("0") && !like.isEmpty()) {
            this.like = like;
        }
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        if (title == null || title.isEmpty()) {
            this.title = "";
        } else {
            this.title = title;
        }
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

    public String getReiting() {
        return reiting;
    }

    public void setReiting(String reiting) {
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

    public void setAutor(String autor) {
        if (autor == null || autor.isEmpty()) {
            this.autor = "";
        } else {
            this.autor = autor;
        }
    }

    public String getArtist() {
        return artist;
    }

    public void setArtist(String artist) {
        if (artist == null || artist.isEmpty()) {
            this.artist = "";
        } else {
            this.artist = artist;
        }
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

    public void setAutorUrl(String autorUrl) {
        if (autorUrl == null || !Consts.REGEXP_URL.matcher(autorUrl).matches()) {
            this.autorUrl = "";
        } else {
            this.autorUrl = autorUrl;
        }
    }

    public String getArtistUrl() {
        return artistUrl;
    }

    public void setArtistUrl(String artistUrl) {
        if (artistUrl == null || !Consts.REGEXP_URL.matcher(artistUrl).matches()) {
            this.artistUrl = "";
        } else {
            this.artistUrl = artistUrl;
        }
    }

    public String getGenre() {
        return genre;
    }

    public void setGenre(String genre) {
        if (genre == null || genre.isEmpty()) {
            this.genre = "";
        } else {
            this.genre = genre;
        }
    }

    public String getGenreUrl() {
        return genreUrl;
    }

    public void setGenreUrl(String genreUrl) {
        if (genreUrl == null || !Consts.REGEXP_URL.matcher(genreUrl).matches()) {
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
