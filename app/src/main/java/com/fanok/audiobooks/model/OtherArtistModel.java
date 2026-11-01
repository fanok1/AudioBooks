package com.fanok.audiobooks.model;


import static com.fanok.audiobooks.model.OtherSourceModel.isBlank;
import static com.fanok.audiobooks.model.OtherSourceModel.sameBook;
import static com.fanok.audiobooks.model.OtherSourceModel.samePerson;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.fanok.audiobooks.Url;
import com.fanok.audiobooks.model.OtherSourceModel.Hit;
import com.fanok.audiobooks.pojo.BookPOJO;
import com.fanok.audiobooks.pojo.OtherArtistPOJO;
import com.fanok.audiobooks.search.Provider;
import io.reactivex.Observable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


/** "Other readers" of a book: other pages of the same title and author with another reader. */
public class OtherArtistModel implements
        com.fanok.audiobooks.interface_pacatge.book_content.OtherArtistModel {

    /** Whether a provider can list other readings; {@code UNSUPPORTED} always yields an empty list, not an error. */
    public enum Support {
        SUPPORTED,
        UNSUPPORTED
    }

    /**
     * The site decides ({@link com.fanok.audiobooks.source.SourceParser#findsOtherReaders}); an unknown host throws
     * IllegalArgumentException.
     */
    public static Support support(@Nullable String bookUrl) {
        Provider provider = Provider.require(Url.canonical(bookUrl));
        return provider.parser.findsOtherReaders() ? Support.SUPPORTED : Support.UNSUPPORTED;
    }

    @Override
    public Observable<ArrayList<OtherArtistPOJO>> getOtherArtist(@NonNull BookPOJO bookPOJO) {
        return Observable.create(emitter -> {
            try {
                ArrayList<OtherArtistPOJO> readers = load(bookPOJO);
                if (!emitter.isDisposed()) {
                    emitter.onNext(readers);
                    emitter.onComplete();
                }
            } catch (Exception e) {
                emitter.tryOnError(e); // disposed meanwhile: dropped, not an undeliverable crash
            }
        });
    }

    static ArrayList<OtherArtistPOJO> load(BookPOJO book) throws IOException {
        String url = Url.canonical(book.getUrl());
        Provider site = Provider.require(url);
        if (support(url) == Support.UNSUPPORTED) {
            return new ArrayList<>();
        }
        return site.parser.otherReaders(url, book.getName(), book.getAutor(), book.getArtist());
    }

    /** Hits with our title and author on another page with another reader; one entry per page. */
    public static ArrayList<OtherArtistPOJO> otherReaders(List<Hit> hits, String name, @Nullable String author,
            @Nullable String url, @Nullable String reader) {
        ArrayList<OtherArtistPOJO> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        String self = url == null ? "" : Url.canonical(url);
        for (Hit hit : hits) {
            if (hit.url.isEmpty() || hit.reader.isEmpty() || hit.url.equals(self) || !sameBook(hit, name, author)
                    || (!isBlank(reader) && samePerson(hit.reader, reader)) || !seen.add(hit.url)) {
                continue;
            }
            OtherArtistPOJO pojo = new OtherArtistPOJO();
            pojo.setName("Исполнитель " + hit.reader);
            pojo.setUrl(hit.url);
            result.add(pojo);
        }
        return result;
    }

}
