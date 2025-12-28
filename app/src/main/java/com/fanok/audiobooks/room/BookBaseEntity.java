package com.fanok.audiobooks.room;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.PrimaryKey;
import com.fanok.audiobooks.pojo.BookPOJO;

public class BookBaseEntity {
    @PrimaryKey(autoGenerate = true)
    public int id;

    @NonNull
    @ColumnInfo(name = "name")
    public String name = "";

    @NonNull
    @ColumnInfo(name = "url_book")
    public String urlBook = "";

    @ColumnInfo(name = "photo")
    public String photo;

    @ColumnInfo(name = "genre")
    public String genre;

    @ColumnInfo(name = "url_genre")
    public String urlGenre;

    @ColumnInfo(name = "author")
    public String author;

    @ColumnInfo(name = "url_author")
    public String urlAuthor;

    @ColumnInfo(name = "artist")
    public String artist;

    @ColumnInfo(name = "url_artist")
    public String urlArtist;

    @ColumnInfo(name = "series")
    public String series;

    @ColumnInfo(name = "url_series")
    public String urlSeries;

    @ColumnInfo(name = "time")
    public String time;

    @ColumnInfo(name = "reting")
    public String rating;

    @ColumnInfo(name = "coments")
    public Integer comments;

    @ColumnInfo(name = "description")
    public String description;

    public BookPOJO toPojo() {
        BookPOJO book = new BookPOJO();
        book.setName(name);
        book.setUrl(urlBook);
        book.setPhoto(photo);
        book.setGenre(genre);
        book.setUrlGenre(urlGenre);
        book.setAutor(author);
        book.setUrlAutor(urlAuthor);
        book.setArtist(artist);
        book.setUrlArtist(urlArtist);
        book.setSeries(series);
        book.setUrlSeries(urlSeries);
        book.setTime(time);
        book.setReting(rating);
        book.setComents(String.valueOf(comments != null ? comments : 0));
        book.setDesc(description);
        return book;
    }

    public void fromPojo(BookPOJO book) {
        this.name = book.getName();
        this.urlBook = book.getUrl();
        this.photo = book.getPhoto();
        this.genre = book.getGenre();
        this.urlGenre = book.getUrlGenre();
        this.author = book.getAutor();
        this.urlAuthor = book.getUrlAutor();
        this.artist = book.getArtist();
        this.urlArtist = book.getUrlArtist();
        this.series = book.getSeries();
        this.urlSeries = book.getUrlSeries();
        this.time = book.getTime();
        this.rating = book.getReting();
        this.comments = book.getComents();
        this.description = book.getDesc();
    }
}
