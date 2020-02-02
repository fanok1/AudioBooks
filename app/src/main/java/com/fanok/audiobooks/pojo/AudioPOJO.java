package com.fanok.audiobooks.pojo;

import androidx.annotation.NonNull;

import com.fanok.audiobooks.Consts;

public class AudioPOJO {

    private String bookName = "";
    private String name = "";
    String url = "";
    private int time = 0;

    public String getBookName() {
        return bookName;
    }

    public void setBookName(String bookName) {
        if (bookName != null) {
            this.bookName = bookName;
        }
    }

    public String getName() {
        return name;
    }

    public void setName(@NonNull String name) {
        this.name = name;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(@NonNull String url) {
        if (!Consts.REGEXP_URL_MP3.matcher(url).matches()) {
            throw new IllegalArgumentException(
                    "Value must be url");
        }
        this.url = url;
    }

    public int getTime() {
        return time;
    }

    public void setTime(int time) {
        if (time < 0) throw new IllegalArgumentException("Value must be > 0");
        this.time = time;
    }
}
