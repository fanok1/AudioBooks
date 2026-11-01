package com.fanok.audiobooks.model;

import com.fanok.audiobooks.pojo.GenrePOJO;
import com.fanok.audiobooks.search.Provider;
import com.fanok.audiobooks.search.ProviderPage;
import com.fanok.audiobooks.search.SearchUrls;
import com.fanok.audiobooks.source.SourceParser;
import java.io.IOException;

/** Author and performer listings and their search, one page per call; end of list comes from the pager. */
public class AutorsModel extends GenreModel {

    private boolean mPerformerMode = false;
    /** This list's state between its pages, for a site that keeps one ({@link SourceParser#loadPeople}). */
    private final SourceParser.PeopleCursor mPeopleCursor = new SourceParser.PeopleCursor();

    public void setPerformerMode(boolean mode) {
        mPerformerMode = mode;
    }

    /** One page of author/performer search on {@code provider}; EMPTY when the provider has no such search. */
    public ProviderPage<GenrePOJO> search(Provider provider, SearchUrls.Category category, String query, int page)
            throws IOException {
        String url = SearchUrls.url(provider, category, query, page);
        if (url == null) {
            return ProviderPage.empty();
        }
        return provider.parser.searchPeople(provider.parser.fetch(url), page,
                category == SearchUrls.Category.PERFORMERS);
    }

    @Override
    protected ProviderPage<GenrePOJO> loadParser(SourceParser parser, String url, int page) throws IOException {
        return parser.loadPeople(url, page, mPerformerMode, mPeopleCursor);
    }
}
