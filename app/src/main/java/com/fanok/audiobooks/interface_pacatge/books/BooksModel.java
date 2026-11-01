package com.fanok.audiobooks.interface_pacatge.books;

import com.fanok.audiobooks.pojo.BookPOJO;
import com.fanok.audiobooks.search.Provider;
import com.fanok.audiobooks.search.ProviderPage;
import io.reactivex.Single;
import java.io.IOException;

public interface BooksModel {
    /** One listing page of {@code url} (a catalog, genre, author, series or similar page). */
    Single<ProviderPage<BookPOJO>> getBooks(String url, int page);

    /** One page of book search results on {@code provider}; blocking, called on an io thread. */
    ProviderPage<BookPOJO> search(Provider provider, String query, int page) throws IOException;
}
