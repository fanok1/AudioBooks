package com.fanok.audiobooks.interface_pacatge.books;


import androidx.annotation.NonNull;

public interface AudioDBHelperInterfase {
    boolean isset(@NonNull String url);

    void add(@NonNull String urlBook, @NonNull String name);

    void remove(@NonNull String urlBook);

    String getName(@NonNull String url);

    int getTime(@NonNull String url);

    int setTime(@NonNull String urlBook, int time);

}
