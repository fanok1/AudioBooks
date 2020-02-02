package com.fanok.audiobooks.pojo;

public class AudioListPOJO {

    private String bookUrl;
    private String bookName;
    private String audioName;
    private String audioUrl;
    private int time = 0;

    public String getBookUrl() {
        return bookUrl;
    }

    public void setBookUrl(String bookUrl) {
        if (bookUrl == null || bookUrl.isEmpty()) {
            this.bookUrl = "";
        } else {
            this.bookUrl = bookUrl;
        }
    }

    public String getBookName() {
        return bookName;
    }

    public void setBookName(String bookName) {
        if (bookName == null || bookName.isEmpty()) {
            this.bookName = "";
        } else {
            this.bookName = bookName;
        }
    }

    public String getAudioName() {
        return audioName;
    }

    public void setAudioName(String audioName) {
        if (audioName == null || audioName.isEmpty()) {
            this.audioName = "";
        } else {
            this.audioName = audioName;
        }
    }

    public String getAudioUrl() {
        return audioUrl;
    }

    public void setAudioUrl(String audioUrl) {
        if (audioUrl == null || audioUrl.isEmpty()) {
            this.audioUrl = "";
        } else {
            this.audioUrl = audioUrl;
        }
    }

    public int getTime() {
        return time;
    }

    public void setTime(int time) {
        if (time < 0) {
            this.time = 0;
        } else {
            this.time = time;
        }
    }
}
