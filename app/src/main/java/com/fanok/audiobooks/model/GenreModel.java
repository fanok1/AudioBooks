package com.fanok.audiobooks.model;

import com.fanok.audiobooks.pojo.GenrePOJO;
import com.fanok.audiobooks.search.Provider;
import com.fanok.audiobooks.search.ProviderPage;
import com.fanok.audiobooks.source.SourceParser;
import io.reactivex.Single;
import java.io.IOException;

/** Genre lists per provider; {@link AutorsModel} overrides the per-provider loaders for authors and performers. */
public class GenreModel implements com.fanok.audiobooks.interface_pacatge.books.GenreModel {

    @Override
    public Single<ProviderPage<GenrePOJO>> getBooks(String url, int page) {
        return Single.fromCallable(() -> load(url, page));
    }

    ProviderPage<GenrePOJO> load(String url, int page) throws IOException {
        Provider provider = Provider.ofUrl(url);
        if (provider == null) {
            return ProviderPage.empty();
        }
        return loadParser(provider.parser, url, page);
    }

    /** The list of a site: genres here, authors or performers in {@link AutorsModel}. */
    protected ProviderPage<GenrePOJO> loadParser(SourceParser parser, String url, int page) throws IOException {
        return parser.loadGenres(url, page);
    }

}
