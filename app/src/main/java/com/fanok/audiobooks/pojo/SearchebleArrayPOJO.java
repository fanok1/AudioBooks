package com.fanok.audiobooks.pojo;

import android.support.annotation.NonNull;

import com.fanok.audiobooks.Consts;

public class SearchebleArrayPOJO {
    private String name;
    private String url;

    public SearchebleArrayPOJO(@NonNull String name, @NonNull String url) {
        if (name.isEmpty()) throw new NullPointerException();
        if (!Consts.REGEXP_URL.matcher(url).matches()) {
            throw new IllegalArgumentException(
                    "Value must be url");
        }
        this.name = name;
        this.url = url;
    }

    public String getName() {
        return name;
    }

    public String getUrl() {
        return url;
    }
}