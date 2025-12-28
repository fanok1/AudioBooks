package com.fanok.audiobooks.model;

import android.content.Context;
import androidx.annotation.NonNull;
import com.fanok.audiobooks.interface_pacatge.books.AudioDBHelperInterfase;
import com.fanok.audiobooks.interface_pacatge.books.BooksDBAbstract;
import com.fanok.audiobooks.pojo.TimeStartPOJO;
import com.fanok.audiobooks.room.AudioEntity;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AudioDBModel extends BooksDBAbstract implements AudioDBHelperInterfase {
    private static final String TAG = "AudioDBModel";


    public AudioDBModel(Context context) {
        super(context);
    }

    @Override
    public boolean isset(@NonNull String url) {
        return getDatabase().audioDao().count(url) > 0;
    }

    @Override
    public void add(@NonNull String urlBook, @NonNull String name) {
        AudioEntity entity = new AudioEntity();
        entity.urlBook = urlBook;
        entity.name = name;
        entity.time = 0;
        getDatabase().audioDao().insert(entity);
    }

    @Override
    public void add(@NonNull TimeStartPOJO timeStartPOJO) {
        AudioEntity entity = new AudioEntity();
        entity.urlBook = timeStartPOJO.getUrl();
        entity.name = timeStartPOJO.getName();
        entity.time = timeStartPOJO.getTime();
        getDatabase().audioDao().insert(entity);
    }

    @Override
    public void remove(@NonNull String urlBook) {
        getDatabase().audioDao().deleteByUrl(urlBook);
    }

    @Override
    public void clearAll() {
        getDatabase().audioDao().deleteAll();
    }

    @Override
    public String getName(@NonNull String url) {
        String result = getDatabase().audioDao().getName(url);
        return result != null ? result : "";
    }

    @Override
    public int getTime(@NonNull String url) {
        return getDatabase().audioDao().getTime(url);
    }

    @Override
    public int setTime(@NonNull String urlBook, int time) {
        if (time < 0 || urlBook.isEmpty()) throw new IllegalArgumentException();
        return getDatabase().audioDao().setTime(urlBook, time);
    }

    @Override
    public ArrayList<TimeStartPOJO> getAll() {
        List<AudioEntity> entities = getDatabase().audioDao().getAll();
        ArrayList<TimeStartPOJO> list = new ArrayList<>();
        for (AudioEntity entity : entities) {
            TimeStartPOJO audio = new TimeStartPOJO();
            audio.setUrl(entity.urlBook);
            audio.setName(entity.name);
            audio.setTime(entity.time);
            list.add(audio);
        }
        Collections.reverse(list);
        return list;
    }
}
