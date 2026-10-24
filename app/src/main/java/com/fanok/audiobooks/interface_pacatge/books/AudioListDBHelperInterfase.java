package com.fanok.audiobooks.interface_pacatge.books;

import androidx.annotation.NonNull;

import com.fanok.audiobooks.pojo.AudioListPOJO;

import java.util.ArrayList;
import java.util.List;

public interface AudioListDBHelperInterfase {
    boolean isset(@NonNull String url);

    void add(@NonNull AudioListPOJO audioListPOJO);

    void remove(@NonNull String urlBook);

    /** Replaces a book's chapter list in one transaction, keeping what each existing chapter records. */
    void syncChapters(@NonNull String urlBook, @NonNull List<AudioListPOJO> chapters);

    void clearAll();

    ArrayList<AudioListPOJO> get(@NonNull String url);

    ArrayList<AudioListPOJO> getAll();

    AudioListPOJO get(@NonNull String url, @NonNull String audioUrl);


}
