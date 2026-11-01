package com.fanok.audiobooks.util;


import android.content.Context;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.annotation.OptIn;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DataSpec;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.ResolvingDataSource;
import androidx.media3.datasource.cache.Cache;
import androidx.media3.datasource.cache.CacheDataSource;
import androidx.media3.datasource.cache.ContentMetadata;
import androidx.media3.datasource.okhttp.OkHttpDataSource;
import androidx.media3.exoplayer.offline.DefaultDownloaderFactory;
import androidx.media3.exoplayer.offline.Download;
import androidx.media3.exoplayer.offline.DownloadCursor;
import androidx.media3.exoplayer.offline.DownloadIndex;
import androidx.media3.exoplayer.offline.DownloadManager;
import androidx.media3.exoplayer.offline.DownloadRequest;
import androidx.media3.exoplayer.offline.DownloadService;
import androidx.media3.exoplayer.offline.DownloaderFactory;

import com.fanok.audiobooks.App;
import com.fanok.audiobooks.Url;
import com.fanok.audiobooks.download.DownloadIds;
import com.fanok.audiobooks.model.AudioListDBModel;
import com.fanok.audiobooks.pojo.AudioPOJO;
import com.fanok.audiobooks.pojo.BookPOJO;
import com.fanok.audiobooks.room.BooksAudioEntity;
import com.fanok.audiobooks.net.AppProxy;
import com.fanok.audiobooks.search.Provider;
import com.fanok.audiobooks.service.ExoDownloadService;
import com.fanok.audiobooks.source.SourceParser;
import com.google.gson.Gson;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Download identity, request context and cache access around the one {@link DownloadManager} of the process
 * ({@link App#getDownloadManager()}). Chapter ids come from {@link DownloadIds}; every network request of one
 * download or playback carries the same immutable book context (Referer, session cookie) through a factory scoped to
 * that request, so HLS playlists, segments and keys get it as well as the top-level URL.
 */
@OptIn(markerClass = UnstableApi.class)
public class DownloadUtil {

    private static final String TAG = "DownloadUtil";
    public static final String DESKTOP_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/89.0.4389.82 Safari/537.36";
    private static final String LEGACY_REF = "__ref";

    /** Off-main bookkeeping of the download listener and the download actions; one thread for the process. */
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor();

    @OptIn(markerClass = UnstableApi.class)
    public static DownloadManager getDownloadManager() {
        return App.getInstance().getDownloadManager();
    }

    @OptIn(markerClass = UnstableApi.class)
    public static Cache getDownloadCache() {
        return App.getInstance().getDownloadCache();
    }

    public static void runInBackground(@NonNull Runnable task) {
        WORKER.execute(task);
    }

    /** Process end only ({@link App#onTerminate}); the worker is never recreated. */
    public static void shutdown() {
        WORKER.shutdown();
    }

    // ---------------------------------------------------------------- request context

    /** Headers every request for {@code bookUrl} carries; the client's interceptor fills the default User-Agent. */
    @NonNull
    static Map<String, String> requestHeaders(@Nullable String bookUrl) {
        Map<String, String> headers = new HashMap<>();
        if (bookUrl == null || bookUrl.isEmpty()) return headers;
        String book = Url.canonical(bookUrl);
        SourceParser parser = Provider.parserOf(book);
        Map<String, String> own = parser == null ? null : parser.mediaHeaders(book);
        if (own != null) {
            headers.putAll(own);
        } else {
            headers.put("Referer", book);
        }
        return headers;
    }

    /**
     * Cookies the requests for {@code bookUrl} carry to its site's own host over https only, unless it set its own
     * ({@link SourceParser#mediaCookies}); see {@link com.fanok.audiobooks.net.MemoryCookieJar#with}.
     */
    @NonNull
    static Map<String, String> requestCookies(@Nullable String bookUrl) {
        if (bookUrl == null) {
            return Collections.emptyMap();
        }
        String book = Url.canonical(bookUrl);
        SourceParser parser = Provider.parserOf(book);
        return parser == null ? Collections.<String, String>emptyMap() : parser.mediaCookies(book);
    }

    /** Network source for every request of one playback of {@code bookUrl}. */
    @NonNull
    public static DataSource.Factory upstreamFor(@NonNull Context context, @Nullable String bookUrl) {
        return upstreamFor(context, bookUrl, App.getOkHttpClient());
    }

    /** Network source for every request of one download or playback of {@code bookUrl} through {@code client}. */
    @NonNull
    static DataSource.Factory upstreamFor(@NonNull Context context, @Nullable String bookUrl,
                                          @NonNull okhttp3.OkHttpClient client) {
        client = AppProxy.mediaClient(bookUrl == null ? null : Provider.ofUrl(bookUrl), client);
        Map<String, String> cookies = requestCookies(bookUrl);
        if (!cookies.isEmpty()) {
            client = client.newBuilder().cookieJar(App.getCookieJar().with(Provider.ofUrl(bookUrl).server, cookies))
                    .build();
        }
        OkHttpDataSource.Factory http = new OkHttpDataSource.Factory(client)
                .setDefaultRequestProperties(requestHeaders(bookUrl));
        //below the cache: a body cut short over HTTP/2 must not read as the end of the chapter
        return new ResolvingDataSource.Factory(
                new DefaultDataSource.Factory(context.getApplicationContext(), FullBodyDataSource.factory(http)),
                dataSpec -> dataSpec.withUri(withoutLegacyRef(dataSpec.uri)));
    }

    /**
     * Downloader per request: each download fetches through its own book's context, on the HTTP/1.1 client so that
     * parallel segment requests get a connection each ({@link App#getDownloadHttpClient}).
     */
    @NonNull
    public static DownloaderFactory downloaderFactory(@NonNull Context context, @NonNull Cache cache,
                                                      @NonNull Executor executor) {
        final Context app = context.getApplicationContext();
        return request -> new DefaultDownloaderFactory(
                new CacheDataSource.Factory().setCache(cache).setUpstreamDataSourceFactory(
                        upstreamFor(app, bookUrlOf(request), App.getDownloadHttpClient())),
                executor).createDownloader(request);
    }

    /** The URI without the legacy {@code __ref} parameter; other parameters stay byte-identical, no dangling '?'. */
    @NonNull
    static Uri withoutLegacyRef(@NonNull Uri uri) {
        String query = uri.getEncodedQuery();
        if (query == null || !query.contains(LEGACY_REF + "=")) return uri;
        StringBuilder kept = new StringBuilder();
        for (String part : query.split("&")) {
            if (part.isEmpty() || part.startsWith(LEGACY_REF + "=")) continue;
            if (kept.length() > 0) kept.append('&');
            kept.append(part);
        }
        return uri.buildUpon().encodedQuery(kept.length() == 0 ? null : kept.toString()).build();
    }

    /** Book URL a legacy request carried in {@code __ref}, or null. */
    @Nullable
    private static String legacyRefOf(@Nullable Uri uri) {
        if (uri == null || !uri.isHierarchical()) return null;
        String ref = uri.getQueryParameter(LEGACY_REF);
        if (ref == null || ref.isEmpty()) return null;
        if (ref.contains("%2F") || ref.contains("%3A")) ref = Uri.decode(ref);
        return ref.startsWith("http://") || ref.startsWith("https://") ? ref : "https://" + ref;
    }

    // ---------------------------------------------------------------- identity

    /** Thread-safe; one instance spares rebuilding its adapters for every download read. */
    private static final Gson GSON = new Gson();

    /** The book a download belongs to, from its request data. */
    @Nullable
    public static BookPOJO bookOf(@NonNull DownloadRequest request) {
        if (request.data == null || request.data.length == 0) return null;
        try {
            return GSON.fromJson(new String(request.data, StandardCharsets.UTF_8), BookPOJO.class);
        } catch (Exception e) {
            Log.w(TAG, "Unreadable download data of " + request.id, e);
            return null;
        }
    }

    /** URL of the book a download belongs to: its request data, else the legacy {@code __ref}; null if unknown. */
    @Nullable
    public static String bookUrlOf(@NonNull DownloadRequest request) {
        BookPOJO book = bookOf(request);
        if (book != null && book.getUrl() != null && !book.getUrl().isEmpty()) return book.getUrl();
        return legacyRefOf(request.uri);
    }

    /** True when both URLs name one book ({@link DownloadIds#bookKey}); exact, never by substring. */
    public static boolean sameBook(@Nullable String a, @Nullable String b) {
        return a != null && b != null && DownloadIds.bookKey(a).equals(DownloadIds.bookKey(b));
    }

    /** Download entries of the rows of one book, in row order. */
    @NonNull
    public static List<DownloadIds.Entry> entriesOf(@NonNull String bookUrl, @NonNull List<BooksAudioEntity> rows) {
        List<String> urls = new ArrayList<>(rows.size());
        int[] ordinals = new int[rows.size()];
        for (int i = 0; i < rows.size(); i++) {
            urls.add(rows.get(i).urlAudio == null ? "" : rows.get(i).urlAudio);
            ordinals[i] = rows.get(i).ordinal;
        }
        return DownloadIds.forBook(bookUrl, urls, ordinals);
    }

    /**
     * Download entries of {@code urls}, a chapter list of {@code bookUrl}: the recorded row of each chapter decides its
     * ordinal; chapters without a row use their position in {@code urls}.
     */
    @NonNull
    public static List<DownloadIds.Entry> entriesFor(@NonNull Context context, @NonNull String bookUrl,
                                                     @NonNull List<String> urls) {
        return entriesFor(bookUrl, new AudioListDBModel(context).getRows(bookUrl), urls);
    }

    /** {@link #entriesFor(Context, String, List)} with the book's {@code rows} at hand. */
    @NonNull
    static List<DownloadIds.Entry> entriesFor(@NonNull String bookUrl, @NonNull List<BooksAudioEntity> rows,
                                              @NonNull List<String> urls) {
        int[] at = alignRows(rows, urls);
        List<DownloadIds.Entry> byRow = entriesOf(bookUrl, rows);
        List<DownloadIds.Entry> byPosition = DownloadIds.forBook(bookUrl, urls, null);
        List<DownloadIds.Entry> result = new ArrayList<>(urls.size());
        for (int i = 0; i < urls.size(); i++) {
            result.add(at[i] >= 0 ? byRow.get(at[i]) : byPosition.get(i));
        }
        return result;
    }

    /**
     * For each of {@code urls}, the book's chapter list, the index of its recorded row in {@code rows}, or -1: the row
     * with its position as ordinal and its URL, exact where chapters share one URL (a stream) and rows kept for their
     * copy precede them; else by URL ({@link DownloadIds#align}).
     */
    @NonNull
    static int[] alignRows(@NonNull List<BooksAudioEntity> rows, @NonNull List<String> urls) {
        List<String> rowUrls = new ArrayList<>(rows.size());
        Map<Integer, Integer> byOrdinal = new HashMap<>();
        for (int j = 0; j < rows.size(); j++) {
            BooksAudioEntity row = rows.get(j);
            rowUrls.add(row.urlAudio == null ? "" : row.urlAudio);
            if (row.ordinal >= 0) byOrdinal.put(row.ordinal, j);
        }
        int[] at = DownloadIds.align(rowUrls, urls);
        for (int i = 0; i < urls.size(); i++) {
            Integer j = byOrdinal.get(i);
            if (j != null && urls.get(i) != null && urls.get(i).equals(rows.get(j).urlAudio)) at[i] = j;
        }
        return at;
    }

    /**
     * Chapters of {@code urls} (of {@code bookUrl}) whose row records an exported copy that exists: they are held in
     * the download folder whether or not their Media3 download is still there.
     */
    @NonNull
    public static Set<Integer> exportedChapters(@NonNull Context context, @Nullable String bookUrl,
                                                @NonNull List<String> urls) {
        Set<Integer> exported = new HashSet<>();
        if (bookUrl == null || bookUrl.isEmpty()) return exported;
        List<BooksAudioEntity> rows = new AudioListDBModel(context).getRows(bookUrl);
        int[] at = alignRows(rows, urls);
        for (int i = 0; i < urls.size(); i++) {
            if (at[i] >= 0 && ownCopyOf(context, rows.get(at[i])) != null) exported.add(i);
        }
        return exported;
    }

    /** The exported copy the row records, when it still exists; else null. */
    @Nullable
    static String ownCopyOf(@NonNull Context context, @NonNull BooksAudioEntity row) {
        String local = row.localUri;
        return local != null && !local.isEmpty() && DownloadStorageHelper.exists(context, local) ? local : null;
    }

    /** The ids a chapter may be downloaded under: its current id, then its legacy id. */
    @NonNull
    public static List<String> idsOf(@NonNull DownloadIds.Entry entry) {
        List<String> ids = new ArrayList<>(2);
        ids.add(entry.id);
        if (!entry.legacyId.isEmpty() && !entry.legacyId.equals(entry.id)) ids.add(entry.legacyId);
        return ids;
    }

    /**
     * The download holding the chapter {@code url} of {@code entry}: its current id (a stream only when requested for
     * the same host and path), else a legacy download that is this chapter's (same URL, or a legacy id no other
     * chapter shares). Downloads being removed are ignored.
     */
    @Nullable
    public static Download find(@NonNull DownloadIndex index, @NonNull DownloadIds.Entry entry, @Nullable String url) {
        try {
            Download download = entry.id.equals(entry.legacyId) ? null : index.getDownload(entry.id);
            if (holds(download, entry, url, false)) return download;
            download = entry.legacyId.isEmpty() ? null : index.getDownload(entry.legacyId);
            return holds(download, entry, url, true) ? download : null;
        } catch (IOException e) {
            Log.w(TAG, "Download index unreadable", e);
            return null;
        }
    }

    /** {@link #find(DownloadIndex, DownloadIds.Entry, String)} over a snapshot of downloads by id. */
    @Nullable
    public static Download find(@NonNull Map<String, Download> byId, @NonNull DownloadIds.Entry entry, @Nullable String url) {
        Download download = entry.id.equals(entry.legacyId) ? null : byId.get(entry.id);
        if (holds(download, entry, url, false)) return download;
        download = byId.get(entry.legacyId);
        return holds(download, entry, url, true) ? download : null;
    }

    private static boolean holds(@Nullable Download download, @NonNull DownloadIds.Entry entry, @Nullable String url,
                                 boolean legacy) {
        if (download == null || download.state == Download.STATE_REMOVING) return false;
        String requested = withoutLegacyRef(download.request.uri).toString();
        return legacy ? DownloadIds.holdsLegacy(entry, url, requested) : DownloadIds.holdsCurrent(entry, url, requested);
    }

    /**
     * Request URL by id of every download the index holds, not being removed, under one of the ids of
     * {@code entries}: the input of {@link DownloadIds#ownedIds}.
     */
    @NonNull
    public static Map<String, String> storedRequests(@NonNull DownloadIndex index,
                                                     @NonNull List<DownloadIds.Entry> entries) throws IOException {
        Map<String, String> stored = new HashMap<>();
        for (DownloadIds.Entry entry : entries) {
            for (String id : idsOf(entry)) {
                if (id.isEmpty() || stored.containsKey(id)) continue;
                Download d = index.getDownload(id);
                if (d != null && d.state != Download.STATE_REMOVING) {
                    stored.put(id, withoutLegacyRef(d.request.uri).toString());
                }
            }
        }
        return stored;
    }

    /**
     * The download of each of {@code urls}, chapters of {@code bookUrl} (null when unknown: legacy ids only), keyed
     * by chapter URL; chapters without a download are absent.
     */
    @NonNull
    public static Map<String, Download> downloadsByChapterUrl(@NonNull Context context, @NonNull DownloadIndex index,
                                                              @Nullable String bookUrl, @NonNull List<String> urls) {
        List<DownloadIds.Entry> entries = bookUrl != null && !bookUrl.isEmpty()
                ? entriesFor(context, bookUrl, urls) : DownloadIds.forBook("", urls, null);
        Map<String, Download> byUrl = new HashMap<>();
        for (int i = 0; i < urls.size(); i++) {
            String url = urls.get(i);
            if (url == null || byUrl.containsKey(url)) continue;
            Download download = find(index, entries.get(i), url);
            if (download != null) byUrl.put(url, download);
        }
        return byUrl;
    }

    /** True for a single-file download (exportable); HLS and other adaptive media stay Media3 assets. */
    public static boolean isProgressive(@NonNull DownloadRequest request) {
        return Util.inferContentTypeForUriAndMimeType(withoutLegacyRef(request.uri), request.mimeType)
                == C.CONTENT_TYPE_OTHER;
    }

    /** Ids of the downloads of {@code bookUrl} in the index (any state but removing). */
    @NonNull
    public static Set<String> downloadIdsOfBook(@NonNull DownloadIndex index, @NonNull String bookUrl) {
        Set<String> ids = new LinkedHashSet<>();
        try (DownloadCursor cursor = index.getDownloads()) {
            while (cursor.moveToNext()) {
                Download d = cursor.getDownload();
                if (d.state != Download.STATE_REMOVING && sameBook(bookUrl, bookUrlOf(d.request))) ids.add(d.request.id);
            }
        } catch (IOException e) {
            Log.w(TAG, "Download index unreadable", e);
        }
        return ids;
    }

    // ---------------------------------------------------------------- requests

    /**
     * Queues the downloads of the chosen chapters of {@code book}. A progressive chapter is one download keyed by its
     * chapter id; HLS chapters sharing a stream URL are one download of that stream. Completed ones are skipped.
     */
    public static void addDownloads(@NonNull Context context, @NonNull BookPOJO book, @NonNull List<AudioPOJO> chapters,
                                    @NonNull Collection<String> chosenUrls) {
        if (book.getUrl() == null || book.getUrl().isEmpty()) return;
        List<String> urls = new ArrayList<>(chapters.size());
        for (AudioPOJO chapter : chapters) urls.add(chapter.getUrl());
        List<DownloadIds.Entry> entries = entriesFor(context, book.getUrl(), urls);
        Set<String> chosen = new HashSet<>(chosenUrls);
        Set<Integer> exported = exportedChapters(context, book.getUrl(), urls);
        // a download is fetched (again) only while one of its chosen chapters has no exported copy
        Set<String> needed = new HashSet<>();
        for (int i = 0; i < urls.size(); i++) {
            if (chosen.contains(urls.get(i)) && !exported.contains(i)) needed.add(entries.get(i).id);
        }
        byte[] data = new Gson().toJson(book).getBytes(StandardCharsets.UTF_8);
        Set<String> queued = new HashSet<>();
        DownloadIndex index = getDownloadManager().getDownloadIndex();
        for (int i = 0; i < urls.size(); i++) {
            String url = urls.get(i);
            DownloadIds.Entry entry = entries.get(i);
            if (!needed.contains(entry.id) || !DeletionPlanner.isRemote(url) || !queued.add(entry.id)) continue;
            Download existing = find(index, entry, url);
            if (existing != null && existing.state == Download.STATE_COMPLETED) continue;
            DownloadRequest request = new DownloadRequest.Builder(entry.id, Uri.parse(url))
                    .setCustomCacheKey(entry.cacheKey)
                    .setMimeType(entry.hls ? MimeTypes.APPLICATION_M3U8 : null)
                    .setData(data)
                    .build();
            DownloadService.sendAddDownload(context, ExoDownloadService.class, request, false);
        }
    }

    // ---------------------------------------------------------------- playback

    /**
     * What the player plays for one chapter: the media item and its data source. The caller clips the item to the
     * chapter unless it is the chapter's own exported copy ({@link #ownCopy}), which holds exactly the chapter.
     */
    public static final class Playback {
        public final MediaItem.Builder mediaItem;
        public final DataSource.Factory dataSourceFactory;
        public final boolean ownCopy;

        Playback(MediaItem.Builder mediaItem, DataSource.Factory dataSourceFactory, boolean ownCopy) {
            this.mediaItem = mediaItem;
            this.dataSourceFactory = dataSourceFactory;
            this.ownCopy = ownCopy;
        }
    }

    /**
     * Playback of {@code playlist[index]} of {@code bookUrl}: a local file directly; a remote chapter from its own
     * exported copy, else its completed download (by its own id and cache key, legacy downloads by theirs), else the
     * network, both through the read-only download cache and the book's request context.
     */
    @NonNull
    public static Playback playbackFor(@NonNull Context context, @Nullable String bookUrl,
                                       @NonNull List<AudioPOJO> playlist, int index) {
        String url = playlist.get(index).getUrl();
        MediaItem.Builder item = new MediaItem.Builder();
        if (url == null || !DeletionPlanner.isRemote(url)) {
            return new Playback(item.setUri(localUri(url)), new DefaultDataSource.Factory(context), false);
        }
        DownloadIds.Entry entry = null;
        Download download = null;
        if (bookUrl != null && !bookUrl.isEmpty()) {
            List<String> urls = new ArrayList<>(playlist.size());
            for (AudioPOJO audio : playlist) urls.add(audio.getUrl());
            List<BooksAudioEntity> rows = new AudioListDBModel(context).getRows(bookUrl);
            int row = alignRows(rows, urls)[index];
            String own = row >= 0 ? ownCopyOf(context, rows.get(row)) : null;
            if (own != null) {
                return new Playback(item.setUri(localUri(own)), new DefaultDataSource.Factory(context), true);
            }
            entry = entriesFor(bookUrl, rows, urls).get(index);
            download = find(getDownloadManager().getDownloadIndex(), entry, url);
        }
        if (download != null && download.state == Download.STATE_COMPLETED) {
            item.setUri(download.request.uri)
                    .setCustomCacheKey(download.request.customCacheKey)
                    .setMimeType(download.request.mimeType)
                    .setStreamKeys(download.request.streamKeys);
        } else {
            boolean hls = Util.inferContentType(Uri.parse(url)) == C.CONTENT_TYPE_HLS;
            item.setUri(Uri.parse(url))
                    .setCustomCacheKey(entry != null && !hls ? entry.cacheKey : null)
                    .setMimeType(hls ? MimeTypes.APPLICATION_M3U8 : null);
        }
        DataSource.Factory factory = new CacheDataSource.Factory()
                .setCache(getDownloadCache())
                .setUpstreamDataSourceFactory(upstreamFor(context, bookUrl))
                .setCacheWriteDataSinkFactory(null) // read-only: the download cache holds explicit downloads only
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR);
        return new Playback(item, factory, false);
    }

    /** A local file as a URI: content:// and file:// as they are, a path as a file URI. */
    @NonNull
    private static Uri localUri(@Nullable String url) {
        return url == null ? Uri.EMPTY
                : url.startsWith("content://") || url.startsWith("file://") ? Uri.parse(url)
                : Uri.fromFile(new java.io.File(url));
    }

    // ---------------------------------------------------------------- exact-key cache reads

    /** Cache key of a download's single resource: its custom key, else its URI (Media3's default key). */
    @NonNull
    public static String cacheKeyOf(@NonNull DownloadRequest request) {
        return request.customCacheKey != null ? request.customCacheKey : request.uri.toString();
    }

    /** Known length of a completed download's resource, or {@link C#LENGTH_UNSET}. */
    public static long lengthOf(@NonNull Cache cache, @NonNull Download download) {
        if (download.contentLength != C.LENGTH_UNSET) return download.contentLength;
        return ContentMetadata.getContentLength(cache.getContentMetadata(cacheKeyOf(download.request)));
    }

    /**
     * Copies exactly the cached resource {@code key} (bytes 0..length) to {@code out} from the cache alone: no
     * upstream, no cache writes, so a missing span fails instead of reading anything else. Throws unless exactly
     * {@code length} bytes were copied.
     */
    public static void copyExact(@NonNull Cache cache, @NonNull String key, @NonNull Uri uri, long length,
                                 @NonNull OutputStream out) throws IOException {
        if (length <= 0) throw new IOException("Unknown length of " + key);
        CacheDataSource source = new CacheDataSource.Factory()
                .setCache(cache)
                .setCacheWriteDataSinkFactory(null)
                .createDataSource();
        DataSpec spec = new DataSpec.Builder().setUri(uri).setKey(key).setPosition(0).setLength(length).build();
        long copied = 0;
        try {
            source.open(spec);
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = source.read(buffer, 0, buffer.length)) != C.RESULT_END_OF_INPUT) {
                out.write(buffer, 0, read);
                copied += read;
            }
        } finally {
            source.close();
        }
        if (copied != length) {
            throw new IOException("Cached " + key + " has " + copied + " of " + length + " bytes");
        }
    }
}
