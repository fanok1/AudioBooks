package com.fanok.audiobooks.pojo;

import androidx.annotation.NonNull;
import java.io.File;

public class ClearSavedPOJO {

    private final File mFile;

    private final String mStorege;

    public ClearSavedPOJO(@NonNull File file, @NonNull String storege) {
        mFile = file;
        mStorege = storege;
    }

    public File getFile() {
        return mFile;
    }

    public String getStorege() {
        return mStorege;
    }
}
