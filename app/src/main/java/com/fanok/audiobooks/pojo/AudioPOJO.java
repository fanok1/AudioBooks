package com.fanok.audiobooks.pojo;

import android.net.Uri;

import androidx.annotation.Nullable;
import com.fanok.audiobooks.Consts;

public class AudioPOJO {

    private String bookName = "";
    private String name = "";
    String url = "";
    private int time = 0;
    private int timeStart = -1;
    private int timeFinish = -1;

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

    /** Null is stored as "" so a nameless chapter is kept instead of failing the whole list. */
    public void setName(@Nullable String name) {
        this.name = name == null ? "" : name;
    }

    public String getUrl() {
        return url;
    }

    public String getCleanUrl() {
        if (url == null || url.isEmpty()) return "";
        try {
            Uri uri = Uri.parse(url);
            if (uri.getScheme() == null) return url;
            return new Uri.Builder()
                    .scheme(uri.getScheme())
                    .authority(uri.getAuthority())
                    .path(uri.getPath())
                    .build()
                    .toString();
        } catch (Exception e) {
            return url;
        }
    }

    /** Null or blank is stored as ""; callers drop chapters without a URL. */
    public void setUrl(@Nullable String url) {
        this.url = url == null || url.trim().isEmpty() ? "" : url;
    }

    public int getTime() {
        return time;
    }

    public void setTime(int time) {
        if (time < 0) throw new IllegalArgumentException("Value must be > 0");
        this.time = time;
    }

    public int getTimeStart() {
        return timeStart;
    }

    public void setTimeStart(final int timeStart) {
        if (timeStart < -1) throw new IllegalArgumentException("Value must be > 0");
        this.timeStart = timeStart;
    }

    public int getTimeFinish() {
        return timeFinish;
    }

    public void setTimeFinish(final int timeFinish) {
        if (timeFinish < -1) throw new IllegalArgumentException("Value must be > 0");
        this.timeFinish = timeFinish;
    }
}
