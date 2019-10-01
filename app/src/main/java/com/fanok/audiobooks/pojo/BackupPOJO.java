package com.fanok.audiobooks.pojo;

import java.util.ArrayList;

public class BackupPOJO {
    private ArrayList<BookPOJO> mBooksFavorite;
    private ArrayList<BookPOJO> mBooksHistory;
    private ArrayList<TimeStartPOJO> mAudio;

    public ArrayList<BookPOJO> getBooksFavorite() {
        return mBooksFavorite;
    }

    public void setBooksFavorite(ArrayList<BookPOJO> booksFavorite) {
        mBooksFavorite = booksFavorite;
    }

    public ArrayList<BookPOJO> getBooksHistory() {
        return mBooksHistory;
    }

    public void setBooksHistory(ArrayList<BookPOJO> booksHistory) {
        mBooksHistory = booksHistory;
    }

    public ArrayList<TimeStartPOJO> getAudio() {
        return mAudio;
    }

    public void setAudio(ArrayList<TimeStartPOJO> audio) {
        mAudio = audio;
    }
}
