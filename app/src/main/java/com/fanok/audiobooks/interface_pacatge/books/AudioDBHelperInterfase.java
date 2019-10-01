package com.fanok.audiobooks.interface_pacatge.books;


import androidx.annotation.NonNull;

import com.fanok.audiobooks.pojo.TimeStartPOJO;

import java.util.ArrayList;

public interface AudioDBHelperInterfase {
    boolean isset(@NonNull String url);

    void add(@NonNull String urlBook, @NonNull String name);

    void add(@NonNull TimeStartPOJO timeStartPOJO);

    void remove(@NonNull String urlBook);

    void clearAll();

    String getName(@NonNull String url);

    int getTime(@NonNull String url);

    int setTime(@NonNull String urlBook, int time);

    ArrayList<TimeStartPOJO> getAll();

}
