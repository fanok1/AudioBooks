package com.fanok.audiobooks.pojo;

import androidx.annotation.Nullable;

public class SubComentsPOJO extends ContentParentPOJO {
    private String parentName = "";

    public String getParentName() {
        return parentName;
    }

    public void setParentName(@Nullable String parentName) {
        this.parentName = orEmpty(parentName);
    }
}
