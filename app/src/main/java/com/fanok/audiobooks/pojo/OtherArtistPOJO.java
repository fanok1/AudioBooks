package com.fanok.audiobooks.pojo;

import androidx.annotation.NonNull;
import com.fanok.audiobooks.Consts;

public class OtherArtistPOJO {

    private String name = "";
    private String url = "";

    public String getName() {
        return name;
    }

    public void setName(@NonNull String name) {
        this.name = name.substring(0, 1).toUpperCase() + name.substring(1);
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

}
