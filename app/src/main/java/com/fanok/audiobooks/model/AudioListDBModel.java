package com.fanok.audiobooks.model;

import android.content.Context;
import androidx.annotation.NonNull;
import com.fanok.audiobooks.interface_pacatge.books.AudioListDBHelperInterfase;
import com.fanok.audiobooks.interface_pacatge.books.BooksDBAbstract;
import com.fanok.audiobooks.pojo.AudioListPOJO;
import com.fanok.audiobooks.room.BooksAudioEntity;
import java.util.ArrayList;
import java.util.List;

public class AudioListDBModel extends BooksDBAbstract implements AudioListDBHelperInterfase {

    public AudioListDBModel(Context context) {
        super(context);
    }

    @Override
    public boolean isset(@NonNull String url) {
        return getDatabase().booksAudioDao().count(url) > 0;
    }

    @Override
    public void add(@NonNull AudioListPOJO audioListPOJO) {
        BooksAudioEntity entity = new BooksAudioEntity();
        entity.urlBook = audioListPOJO.getBookUrl();
        entity.booksName = audioListPOJO.getBookName();
        entity.nameAudio = audioListPOJO.getAudioName();
        entity.urlAudio = audioListPOJO.getAudioUrl();
        entity.time = audioListPOJO.getTime();
        entity.timeStart = audioListPOJO.getTimeStart();
        entity.timeEnd = audioListPOJO.getTimeEnd();
        entity.updatedAt = System.currentTimeMillis();
        entity.needSync = true;
        getDatabase().booksAudioDao().insert(entity);
    }

    @Override
    public void remove(@NonNull String urlBook) {
        getDatabase().booksAudioDao().deleteByUrl(urlBook);
    }

    @Override
    public void clearAll() {
        getDatabase().booksAudioDao().deleteAll();
    }

    @Override
    public ArrayList<AudioListPOJO> get(@NonNull String url) {
        List<BooksAudioEntity> entities = getDatabase().booksAudioDao().getByBookUrl(url);
        ArrayList<AudioListPOJO> list = new ArrayList<>();
        for (BooksAudioEntity entity : entities) {
            AudioListPOJO audio = new AudioListPOJO();
            audio.setBookUrl(entity.urlBook);
            audio.setBookName(entity.booksName);
            audio.setAudioName(entity.nameAudio);
            audio.setAudioUrl(entity.urlAudio);
            audio.setTime(entity.time);
            audio.setTimeStart(entity.timeStart);
            audio.setTimeEnd(entity.timeEnd);
            list.add(audio);
        }
        return list;
    }

    @Override
    public AudioListPOJO get(@NonNull String url, @NonNull String audioUrl) {
        BooksAudioEntity entity = getDatabase().booksAudioDao().getByUrlAndAudioUrl(url, audioUrl);
        AudioListPOJO audio = new AudioListPOJO();
        if (entity != null) {
            audio.setBookUrl(entity.urlBook);
            audio.setBookName(entity.booksName);
            audio.setAudioName(entity.nameAudio);
            audio.setAudioUrl(entity.urlAudio);
            audio.setTime(entity.time);
            audio.setTimeStart(entity.timeStart);
            audio.setTimeEnd(entity.timeEnd);
        }
        return audio;
    }

    @Override
    public ArrayList<AudioListPOJO> getAll() {
        List<BooksAudioEntity> entities = getDatabase().booksAudioDao().getAll();
        ArrayList<AudioListPOJO> list = new ArrayList<>();
        for (BooksAudioEntity entity : entities) {
            AudioListPOJO audio = new AudioListPOJO();
            audio.setBookUrl(entity.urlBook);
            audio.setBookName(entity.booksName);
            audio.setAudioName(entity.nameAudio);
            audio.setAudioUrl(entity.urlAudio);
            audio.setTime(entity.time);
            audio.setTimeStart(entity.timeStart);
            audio.setTimeEnd(entity.timeEnd);
            list.add(audio);
        }
        return list;
    }
}
