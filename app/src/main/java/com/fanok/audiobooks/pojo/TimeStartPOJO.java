package com.fanok.audiobooks.pojo;

import androidx.annotation.NonNull;

import com.fanok.audiobooks.Consts;

public class TimeStartPOJO extends AudioPOJO {

    @Override
    public void setUrl(@NonNull String url) {
        if (!Consts.REGEXP_URL.matcher(url).matches()) {
            throw new IllegalArgumentException(
                    "Value must be url");
        }
        this.url = url;
    }
}
