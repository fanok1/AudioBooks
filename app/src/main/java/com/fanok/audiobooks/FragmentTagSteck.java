package com.fanok.audiobooks;

import android.support.annotation.NonNull;

public class FragmentTagSteck {
    private String tag;
    private boolean skip;

    public FragmentTagSteck(@NonNull String tag) {
        this.tag = tag;
        skip = false;
    }

    public String getTag() {
        return tag;
    }

    public boolean isSkip() {
        return skip;
    }

    public void setSkip(boolean skip) {
        this.skip = skip;
    }
}