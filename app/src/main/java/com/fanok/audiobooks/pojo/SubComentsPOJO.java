package com.fanok.audiobooks.pojo;

import android.support.annotation.NonNull;

public class SubComentsPOJO extends ContentParentPOJO {
    private String parentName;

    public String getParentName() {
        return parentName;
    }

    public void setParentName(@NonNull String parentName) {
        this.parentName = parentName;
    }
}
