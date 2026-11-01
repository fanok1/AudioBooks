package com.fanok.audiobooks.source;

import com.fanok.audiobooks.model.OtherSourceModel;
import com.fanok.audiobooks.net.SiteHttp;
import com.fanok.audiobooks.pojo.AudioPOJO;
import com.fanok.audiobooks.pojo.BookPOJO;
import com.fanok.audiobooks.pojo.ComentsPOJO;
import com.fanok.audiobooks.pojo.DescriptionPOJO;
import com.fanok.audiobooks.pojo.GenrePOJO;
import com.fanok.audiobooks.pojo.OtherArtistPOJO;
import com.fanok.audiobooks.pojo.SeriesPOJO;
import com.fanok.audiobooks.search.ListingFilters;
import com.fanok.audiobooks.search.ProviderPage;
import com.fanok.audiobooks.search.SearchUrls;

import io.reactivex.functions.BooleanSupplier;

import org.jsoup.nodes.Document;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Everything the app asks a site for; one stateless instance per {@link com.fanok.audiobooks.search.Provider} row.
 * The models fetch a page through {@link #fetch} and hand the {@link Document} over, so a site's parsing stays in
 * pure static functions that fixtures can test; only {@link #loadListing}, {@link #tracks}, {@link #series},
 * {@link #comments}, {@link #probeOtherReaders}, {@link #otherReaders}, {@link #people} and {@link #loadPeople} may fetch more themselves. A default means "the site has no such feature".
 */
public interface SourceParser {

    /** GET through {@link SiteHttp}; override to POST ({@link Dle#fetch}) or to add headers. */
    default Document fetch(String url) throws IOException {
        return SiteHttp.get(url);
    }

    /** The Referer sent with every request to the site's hosts (covers); null for none. */
    default String referer() {
        return null;
    }

    /**
     * Hosts besides the server's own that serve this site alone, each with its subdomains: its API, covers or media CDN.
     * Their requests get its {@link #referer} and follow its proxy route ({@link com.fanok.audiobooks.net.AppProxy});
     * they never make a link a book of the site ({@link com.fanok.audiobooks.search.Provider#ofUrl}). A CDN that
     * other sites share is left out: it follows the global proxy setting.
     */
    default Set<String> hosts() {
        return Collections.emptySet();
    }

    /** The read timeout of the site's pages; raise it only for a site whose server is known to be slow. */
    default int timeoutMs() {
        return SiteHttp.TIMEOUT_MS;
    }

    /**
     * True when the site's pages are POSTed instead of fetched with GET (a wall that challenges every GET lets a POST
     * pass); {@link SiteHttp} then follows their redirects by hand with the same POST. False by default.
     */
    default boolean postsPages() {
        return false;
    }

    /**
     * Cookies every page request to the site carries unless the site has issued its own (a session the user keeps in
     * the settings); empty by default.
     */
    default Map<String, String> cookies() {
        return Collections.emptyMap();
    }

    // ---------------------------------------------------------------- listings

    /** One page of the listing at {@code url}: the index, a genre, an author/reader/series page or a {@link #sortUrl}. */
    ProviderPage<BookPOJO> listing(Document page, String url, int pageNo);

    /**
     * One page of the listing at {@code url} as the catalog screen loads it: {@link #listing} of the {@link #fetch}ed
     * page. A site that reads its listings from other pages (another host, several pages) overrides it.
     */
    default ProviderPage<BookPOJO> loadListing(String url, int pageNo) throws IOException {
        return listing(fetch(url), url, pageNo);
    }

    /** The URL of page {@code page} of the listing {@code url} in the site's own scheme; page 1 is the listing itself. */
    String pageUrl(String url, int page);

    /**
     * The Order menu of the listing at {@code url}, in menu order ({@link Sort#menu}, {@link Sort#submenu}); empty
     * for a listing that cannot be re-sorted (an author, reader or series page), which hides the Order button. By
     * default the site has no sorts.
     */
    default List<Sort.Entry> sorts(String url) {
        return Collections.emptyList();
    }

    /**
     * The site-wide listing for {@code sort}, one of {@link #sorts}, as {@link #pageUrl} takes it; {@code listingUrl}
     * itself when it already is that listing; null when the sort is none of this site's. A genre listing's sort is
     * then narrowed by {@link #inGenre}.
     */
    default String sortUrl(Sort sort, String listingUrl) {
        return null;
    }

    /** False for a listing that cannot be re-sorted; derived from {@link #sorts}. */
    default boolean hasSort(String url) {
        return !sorts(url).isEmpty();
    }

    /** True when the cards of the listing at {@code url} carry a duration, so the duration filter can work. */
    default boolean listsDuration(String url) {
        return true;
    }

    /**
     * The ranking periods the listing at {@code url} accepts, in menu order (the filter's period chips); empty when it
     * is no ranking with periods. By default no listing has periods.
     */
    default List<ListingFilters.Period> periods(String url) {
        return Collections.emptyList();
    }

    /** The period {@code url} carries, one of {@link #periods}; null when it carries none. */
    default ListingFilters.Period periodOf(String url, Calendar today) {
        return null;
    }

    /** {@code url} with its period replaced by {@code period}, one of {@link #periods}. */
    default String withPeriod(String url, ListingFilters.Period period, Calendar today) {
        return url;
    }

    /**
     * True when a period is a date bound counted back from today (a publication date, not a ranking window): the
     * listing then keeps the chosen period and recounts its date before each first page.
     */
    default boolean periodsAreDates() {
        return false;
    }

    /**
     * {@code url} with the server's own duration bound for {@code duration} when the site filters by duration
     * itself; the cards are still filtered exactly afterwards. By default the filter runs on the device alone.
     */
    default String withDuration(String url, ListingFilters.Duration duration) {
        return url;
    }

    /**
     * The genre a site-wide ranking is narrowed to on the device (its cards are filtered by their genre link's last
     * segment), for a site whose rankings have no genre of their own; null elsewhere.
     */
    default String cardGenre(String url) {
        return null;
    }

    /** The genre key of a genre listing or of a sort opened from one (what {@link #inGenre} takes); null elsewhere. */
    default String genreOf(String url) {
        return null;
    }

    /** A {@link #sortUrl} result narrowed to {@code genre}; by default the site has no per-genre sorts. */
    default String inGenre(String siteWide, String genre) {
        return siteWide;
    }

    // ---------------------------------------------------------------- sections

    /** The genre list at {@code url} ({@code Provider.sections}). */
    ProviderPage<GenrePOJO> genres(Document page, String url, int pageNo);

    /**
     * The genre list at {@code url} as the genre screen loads it: {@link #genres} of the {@link #fetch}ed page. A site
     * whose genre list is built in overrides it and fetches nothing.
     */
    default ProviderPage<GenrePOJO> loadGenres(String url, int pageNo) throws IOException {
        return genres(fetch(url), url, pageNo);
    }

    /**
     * One page of the authors or readers list ({@code Provider.authors} / {@code performers}); may read further site
     * pages into it when one site page holds too few rows for the list to scroll on.
     */
    default ProviderPage<GenrePOJO> people(Document page, String url, int pageNo, boolean performers)
            throws IOException {
        return ProviderPage.empty();
    }

    /**
     * One page of the authors or readers list as the list screen loads it: {@link #people} of the {@link #fetch}ed
     * page. {@code cursor} is the list's own state, kept by the list between its pages; a site whose page N is not
     * always the same site page (one that skips empty pages) overrides this and keeps its position there.
     */
    default ProviderPage<GenrePOJO> loadPeople(String url, int pageNo, boolean performers, PeopleCursor cursor)
            throws IOException {
        return people(fetch(url), url, pageNo, performers);
    }

    /** What one authors or readers list remembers between its pages ({@link #loadPeople}): site pages skipped so far. */
    final class PeopleCursor {
        public int skipped;
    }

    // ---------------------------------------------------------------- search

    /**
     * Page {@code page} of the {@code category} search for the raw {@code query} (encode it once, see
     * {@link SearchUrls#enc}); null when the site has no such search. {@code BOOKS} is never null.
     */
    String searchUrl(SearchUrls.Category category, String query, int page);

    /** One page of book search results; by default the page is a listing of its own URL, as on most sites. */
    default ProviderPage<BookPOJO> searchResults(Document page, int pageNo) {
        return listing(page, page.location(), pageNo);
    }

    /** One page of author or reader search results. */
    default ProviderPage<GenrePOJO> searchPeople(Document page, int pageNo, boolean performers) {
        return ProviderPage.empty();
    }

    // ---------------------------------------------------------------- book page

    /** The book page; the model rejects an empty title as "no book on page". */
    DescriptionPOJO description(Document page, String bookUrl);

    /**
     * The card of a deep-linked book; the model rejects {@link BookPOJO#isNull()}. By default the card is made of
     * {@link #description} ({@link BookPOJO#of}).
     */
    default BookPOJO book(Document page, String bookUrl) {
        return BookPOJO.of(description(page, bookUrl), bookUrl);
    }

    /**
     * True when a card's {@code url} is a list of books rather than a book (a site's collection): the catalog opens it
     * as a {@link #listing}, never as a book page. False by default.
     */
    default boolean opensAsListing(String url) {
        return false;
    }

    /** The "other books" block of the page. */
    default ArrayList<BookPOJO> recommendations(Document page, String bookUrl) {
        return new ArrayList<>();
    }

    /**
     * The chapters; {@code base} is the page's final URL, which relative media URLs resolve against. May fetch more
     * (a playlist). Never empty: a page without a player is an {@link IllegalStateException}, a page that shows the
     * site's takedown notice a {@link com.fanok.audiobooks.BookRemovedException}.
     */
    ArrayList<AudioPOJO> tracks(Document page, String base) throws IOException;

    /**
     * {@link #tracks} as the chapter list loads them: {@code requestedUrl} is the book URL asked for, which a redirect
     * may have turned into {@code base}. A site whose URL selects what plays (a voice in its query) overrides it.
     */
    default ArrayList<AudioPOJO> tracks(Document page, String base, String requestedUrl) throws IOException {
        return tracks(page, base);
    }

    /** The headers of the media requests of {@code bookUrl}; null for the default ({@code Referer}: the book page). */
    default Map<String, String> mediaHeaders(String bookUrl) {
        return null;
    }

    /**
     * The cookies the media requests of {@code bookUrl} carry to the site's own server, over https only (a session the
     * user keeps in the settings); empty for none.
     */
    default Map<String, String> mediaCookies(String bookUrl) {
        return Collections.emptyMap();
    }

    /**
     * True when the site's chapters are parts of shared progressive files (a whole-book MP3 cut by time bounds): each
     * file downloads once, as a stream of the book ({@link com.fanok.audiobooks.download.DownloadIds}), not once per
     * chapter. False by default: the downloads of the other sites keep their per-chapter ids.
     */
    default boolean downloadsSharedFiles() {
        return false;
    }

    /** The series the book belongs to, as listed on or linked from its page. */
    default ArrayList<SeriesPOJO> series(Document page, String bookUrl) throws IOException {
        return new ArrayList<>();
    }

    /**
     * The series of the book at {@code bookUrl} as the series tab loads them: {@link #series} of the {@link #fetch}ed
     * book page. A site without series overrides it and fetches nothing.
     */
    default ArrayList<SeriesPOJO> loadSeries(String bookUrl) throws IOException {
        return series(fetch(bookUrl), bookUrl);
    }

    /** The comments of the book; {@code cancelled} is polled between pages. */
    default ArrayList<ComentsPOJO> comments(String bookUrl, BooleanSupplier cancelled) throws Exception {
        return new ArrayList<>();
    }

    // ---------------------------------------------------------------- other readers, other sources

    /** True when {@link #otherReaders} can find anything: the description probes it and the tab is offered. */
    default boolean findsOtherReaders() {
        return false;
    }

    /**
     * True when the description runs {@link #probeOtherReaders} after the page is shown; by default whenever
     * {@link #findsOtherReaders}. A site whose page itself tells whether there are other narrations ({@link #description}
     * sets {@link DescriptionPOJO#setOtherReader}) returns false: the tab still searches with {@link #otherReaders}.
     */
    default boolean probesOtherReaders() {
        return findsOtherReaders();
    }

    /**
     * The description's search for other narrations, a request of its own after the page is shown (only when
     * {@link #findsOtherReaders}). By default {@link #otherReaders}, whose list the tab then reuses
     * ({@link DescriptionPOJO#setOtherReaders}); a site whose probe only tells whether there are any sets
     * {@link DescriptionPOJO#setOtherReader} alone, and the tab searches with {@link #otherReaders}.
     */
    default void probeOtherReaders(DescriptionPOJO description, String bookUrl) throws IOException {
        description.setOtherReaders(otherReaders(bookUrl, description.getTitle(), description.getAutor(),
                description.getArtist()));
    }

    /** Other narrations of the same book on this site. */
    default ArrayList<OtherArtistPOJO> otherReaders(String bookUrl, String title, String author, String reader)
            throws IOException {
        return new ArrayList<>();
    }

    /** The words searched on this site for a book of another site. */
    default String otherSourceTerms(BookPOJO book) {
        return OtherSourceModel.query(book.getName(), book.getAutor(), book.getArtist());
    }

    /** The search page for {@link #otherSourceTerms}; by default the book search. */
    default String otherSourceSearchUrl(String terms, int page) {
        return searchUrl(SearchUrls.Category.BOOKS, terms, page);
    }

    /** The search pages the other-source lookup reads at most for one book ({@link #otherSourceSearchUrl}); one by default. */
    default int otherSourcePages() {
        return 1;
    }

    /** The results of {@link #otherSourceSearchUrl}; by default the book search results. */
    default ArrayList<OtherSourceModel.Hit> otherSourceHits(Document searchPage) {
        ArrayList<OtherSourceModel.Hit> hits = new ArrayList<>();
        for (BookPOJO book : searchResults(searchPage, 1).items) {
            hits.add(new OtherSourceModel.Hit(book.getUrl(), book.getName(), book.getAutor(), book.getArtist()));
        }
        return hits;
    }
}
