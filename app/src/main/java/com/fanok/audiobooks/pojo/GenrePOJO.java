package com.fanok.audiobooks.pojo;

import com.fanok.audiobooks.Consts;

public class GenrePOJO {
    private static final String TAG = "GenrePOJO";

    private String name;
    private String url;
    private int reting = 0;

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


    public int getReting() {
        return reting;
    }

    public void setReting(int reting) {
        //if (reting < 0) throw new IllegalArgumentException("Value must be more 0");
        this.reting = reting;
    }

    public boolean isNull() {
        return name == null || url == null;
    }

}
