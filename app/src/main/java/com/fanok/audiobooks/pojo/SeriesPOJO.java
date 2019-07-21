package com.fanok.audiobooks.pojo;

import android.support.annotation.NonNull;

import com.fanok.audiobooks.Consts;

public class SeriesPOJO {

    private String name = "";
    private String url = "";
    private String reting = "";
    private String coments = "";

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

    public String getReting() {
        return reting;
    }

    public void setReting(@NonNull String reting) {
        this.reting = reting;
    }

    public String getComents() {
        return coments;
    }

    public void setComents(@NonNull String coments) {
        this.coments = coments;
    }
}
