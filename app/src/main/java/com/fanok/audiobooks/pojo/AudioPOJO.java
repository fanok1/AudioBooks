package com.fanok.audiobooks.pojo;

import androidx.annotation.NonNull;
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

    public void setName(@NonNull String name) {
        this.name = name;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(@NonNull String url) {
        if (!Consts.REGEXP_URL.matcher(url).matches()) {
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
