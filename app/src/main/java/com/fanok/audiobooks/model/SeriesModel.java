package com.fanok.audiobooks.model;


import androidx.annotation.NonNull;
import com.fanok.audiobooks.Url;
import com.fanok.audiobooks.pojo.SeriesPOJO;
import com.fanok.audiobooks.search.Provider;
import io.reactivex.Observable;
import java.io.IOException;
import java.util.ArrayList;

/** The series ("цикл") tab: the other books of the book's series, as each provider lists them. */
public class SeriesModel implements
        com.fanok.audiobooks.interface_pacatge.book_content.SeriesModel {

    @Override
    public Observable<ArrayList<SeriesPOJO>> getSeries(@NonNull String url) {
        return Observable.create(emitter -> {
            try {
                ArrayList<SeriesPOJO> series = loadSeries(url);
                if (!emitter.isDisposed()) {
                    emitter.onNext(series);
                    emitter.onComplete();
                }
            } catch (Exception e) {
                emitter.tryOnError(e); // disposed meanwhile: dropped, not an undeliverable crash
            }
        });
    }

    /** Fetches and parses the series of the book at {@code bookUrl}; an unknown host throws IllegalArgumentException. */
    static ArrayList<SeriesPOJO> loadSeries(String bookUrl) throws IOException {
        String url = Url.canonical(bookUrl);
        Provider provider = Provider.require(url);
        return provider.parser.loadSeries(url);
    }

}
