package com.fanok.audiobooks.model;

import com.fanok.audiobooks.net.SiteHttp;
import com.fanok.audiobooks.pojo.BookPOJO;
import com.fanok.audiobooks.search.Provider;
import com.fanok.audiobooks.search.ProviderPage;
import com.fanok.audiobooks.search.SearchUrls;
import io.reactivex.Single;

import java.io.IOException;

/**
 * Book listings and book search: one fetch per page through {@link SiteHttp}, one parser per provider.
 * Parsers are static and take a {@link Document}, so they are tested from saved pages.
 */
public class BooksModel implements com.fanok.audiobooks.interface_pacatge.books.BooksModel {

    @Override
    public Single<ProviderPage<BookPOJO>> getBooks(String url, int page) {
        return Single.fromCallable(() -> listing(url, page));
    }

    @Override
    public ProviderPage<BookPOJO> search(Provider provider, String query, int page) throws IOException {
        String url = SearchUrls.url(provider, SearchUrls.Category.BOOKS, query, page);
        if (url == null) {
            return ProviderPage.empty();
        }
        return provider.parser.searchResults(provider.parser.fetch(url), page);
    }

    ProviderPage<BookPOJO> listing(String url, int page) throws IOException {
        Provider provider = Provider.ofUrl(url);
        if (provider == null) {
            return ProviderPage.empty();
        }
        return provider.parser.loadListing(url, page);
    }
}
