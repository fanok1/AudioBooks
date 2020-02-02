package com.fanok.audiobooks.interface_pacatge.books;

import androidx.annotation.NonNull;

import com.fanok.audiobooks.pojo.AudioListPOJO;

import java.util.ArrayList;

public interface AudioListDBHelperInterfase {
    boolean isset(@NonNull String url);

    void add(@NonNull AudioListPOJO audioListPOJO);

    void remove(@NonNull String urlBook);

    void clearAll();

    ArrayList<AudioListPOJO> get(@NonNull String url);

    ArrayList<AudioListPOJO> getAll();


}
