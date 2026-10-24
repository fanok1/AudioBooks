package com.fanok.audiobooks.download;

import com.fanok.audiobooks.Url;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Pure identity of downloads. A progressive chapter is one download whose id and custom cache key are
 * {@code v1/<book>/chapter/<ordinal>}; an HLS stream (one URL for many chapters) is one download
 * {@code v1/<book>/stream/<host/path>} without a custom cache key (Media3 rejects one for HLS), shared by its
 * chapters, which keep their own clipping at playback. A stream is named by its own host and path, not by its
 * position, so streams added or dropped around it never renumber it. {@code <book>} is the canonical book URL
 * without scheme, {@code www.}, trailing slash or fragment, so mirrors of one book and moved hosts give one id.
 * Downloads made before these ids keep the legacy id: the chapter URL without its query ({@link #legacyId}); a row
 * retained only for its exported copy (negative ordinal below -1) is identified by that legacy id alone.
 */
public final class DownloadIds {

    public static final String VERSION = "v1";

    /** The download that holds one chapter. */
    public static final class Entry {
        public final String id;
        /** Custom cache key of the download; null for HLS. */
        public final String cacheKey;
        public final boolean hls;
        public final String legacyId;
        /** True when another chapter URL of the book has the same legacy id (query-addressed files). */
        public final boolean legacyShared;

        Entry(String id, String cacheKey, boolean hls, String legacyId, boolean legacyShared) {
            this.id = id;
            this.cacheKey = cacheKey;
            this.hls = hls;
            this.legacyId = legacyId;
            this.legacyShared = legacyShared;
        }
    }

    private DownloadIds() {
    }

    /** "host/path[?query]" of a book URL: moved hosts rewritten, scheme, www., trailing slashes and fragment dropped. */
    public static String bookKey(String bookUrl) {
        if (bookUrl == null) return "";
        String s = Url.canonical(bookUrl.trim());
        int scheme = s.indexOf("://");
        if (scheme >= 0) s = s.substring(scheme + 3);
        int hash = s.indexOf('#');
        if (hash >= 0) s = s.substring(0, hash);
        String query = "";
        int q = s.indexOf('?');
        if (q >= 0) {
            query = q + 1 < s.length() ? s.substring(q) : "";
            s = s.substring(0, q);
        }
        int slash = s.indexOf('/');
        String host = (slash >= 0 ? s.substring(0, slash) : s).toLowerCase(Locale.ROOT);
        if (host.startsWith("www.")) host = host.substring(4);
        String path = slash >= 0 ? s.substring(slash) : "";
        while (path.endsWith("/")) path = path.substring(0, path.length() - 1);
        return host + path + query;
    }

    public static String chapterId(String bookUrl, int ordinal) {
        return VERSION + "/" + bookKey(bookUrl) + "/chapter/" + ordinal;
    }

    public static String streamId(String bookUrl, String streamUrl) {
        return VERSION + "/" + bookKey(bookUrl) + "/stream/" + streamKey(streamUrl);
    }

    /** Lower-case host and path of a stream URL, encoded as {@link #legacyId}; query and fragment dropped. */
    static String streamKey(String url) {
        String clean = legacyId(url);
        int scheme = clean.indexOf("://");
        if (scheme < 0) return clean;
        String rest = clean.substring(scheme + 3);
        int slash = rest.indexOf('/');
        String host = (slash >= 0 ? rest.substring(0, slash) : rest).toLowerCase(Locale.ROOT);
        return host + (slash >= 0 ? rest.substring(slash) : "");
    }

    /**
     * Whether a download stored under {@code entry.id} and requested for {@code requestUrl} holds chapter {@code url}:
     * a chapter id always does; a stream id only when the request has the stream's host and path.
     */
    public static boolean holdsCurrent(Entry entry, String url, String requestUrl) {
        return !entry.hls || streamKey(url).equals(streamKey(requestUrl));
    }

    /**
     * Whether a download stored under {@code entry.legacyId} and requested for {@code requestUrl} holds chapter
     * {@code url}: the same URL, or a legacy id no other chapter of the book shares.
     */
    public static boolean holdsLegacy(Entry entry, String url, String requestUrl) {
        return (url != null && url.equals(requestUrl)) || !entry.legacyShared;
    }

    /**
     * The ids under which {@code stored} (download id → request URL of each download the index holds and is not
     * removing) keeps chapter {@code url} of {@code entry}: what that chapter owns, and nothing another chapter owns.
     */
    public static List<String> ownedIds(Entry entry, String url, Map<String, String> stored) {
        List<String> owned = new ArrayList<>(2);
        String current = entry.id.equals(entry.legacyId) ? null : stored.get(entry.id);
        if (current != null && holdsCurrent(entry, url, current)) owned.add(entry.id);
        String legacy = entry.legacyId.isEmpty() ? null : stored.get(entry.legacyId);
        if (legacy != null && holdsLegacy(entry, url, legacy)) owned.add(entry.legacyId);
        return owned;
    }

    /** True for ids made by {@link #chapterId}/{@link #streamId} of this book. */
    public static boolean belongsTo(String downloadId, String bookUrl) {
        return downloadId != null && downloadId.startsWith(VERSION + "/" + bookKey(bookUrl) + "/");
    }

    /**
     * The {@link #bookKey} a {@link #chapterId}/{@link #streamId} was made with, read from the id without parsing the
     * request; null for legacy ids. A chapter id ends in its ordinal; a stream id splits at its first "/stream/".
     */
    public static String bookKeyOfId(String downloadId) {
        String prefix = VERSION + "/";
        if (downloadId == null || !downloadId.startsWith(prefix)) return null;
        int chapter = downloadId.lastIndexOf("/chapter/");
        if (chapter > prefix.length() && isOrdinal(downloadId.substring(chapter + "/chapter/".length()))) {
            return downloadId.substring(prefix.length(), chapter);
        }
        int stream = downloadId.indexOf("/stream/", prefix.length());
        return stream > prefix.length() ? downloadId.substring(prefix.length(), stream) : null;
    }

    private static boolean isOrdinal(String s) {
        int start = s.startsWith("-") ? 1 : 0;
        if (s.length() <= start) return false;
        for (int i = start; i < s.length(); i++) {
            if (!Character.isDigit(s.charAt(i))) return false;
        }
        return true;
    }

    /** True for ids made by {@link #streamId}: one download for the chapters sharing an HLS stream. */
    public static boolean isStreamId(String downloadId) {
        return downloadId != null && downloadId.startsWith(VERSION + "/") && downloadId.contains("/stream/");
    }

    /** HLS by media type as Media3 infers it for a URI without MIME type: the last path segment ends in ".m3u8". */
    public static boolean isHls(String url) {
        if (url == null) return false;
        String path = url;
        int cut = indexOfAny(path, "?#");
        if (cut >= 0) path = path.substring(0, cut);
        return path.toLowerCase(Locale.ROOT).endsWith(".m3u8");
    }

    /**
     * Downloads of the chapters of one book, aligned with {@code urls}. {@code ordinals[i]} is the chapter's recorded
     * ordinal, or -1 to use its position {@code i}; below -1 the row was retained for its copy and has its legacy id
     * only. HLS chapters map to the stream of their URL's host and path.
     */
    public static List<Entry> forBook(String bookUrl, List<String> urls, int[] ordinals) {
        List<Entry> entries = new ArrayList<>(urls.size());
        Map<String, String> legacyOwner = new HashMap<>();
        Map<String, Boolean> legacyShared = new HashMap<>();
        for (String url : urls) {
            String legacy = legacyId(url);
            String owner = legacyOwner.get(legacy);
            if (owner == null) {
                legacyOwner.put(legacy, url);
            } else if (!owner.equals(url)) {
                legacyShared.put(legacy, true);
            }
        }
        for (int i = 0; i < urls.size(); i++) {
            String url = urls.get(i);
            String legacy = legacyId(url);
            boolean shared = legacyShared.containsKey(legacy);
            int recorded = ordinals != null && i < ordinals.length ? ordinals[i] : -1;
            if (isHls(url)) {
                entries.add(new Entry(streamId(bookUrl, url), null, true, legacy, shared));
            } else if (recorded < -1) {
                entries.add(new Entry(legacy, null, false, legacy, shared));
            } else {
                String id = chapterId(bookUrl, recorded >= 0 ? recorded : i);
                entries.add(new Entry(id, id, false, legacy, shared));
            }
        }
        return entries;
    }

    /**
     * For each of {@code urls} the index of the row with the same URL (the k-th occurrence maps to the k-th row
     * with that URL), or -1 when no row has it.
     */
    public static int[] align(List<String> rowUrls, List<String> urls) {
        Map<String, List<Integer>> byUrl = new HashMap<>();
        for (int j = 0; j < rowUrls.size(); j++) {
            String url = rowUrls.get(j);
            List<Integer> same = byUrl.get(url);
            if (same == null) {
                same = new ArrayList<>();
                byUrl.put(url, same);
            }
            same.add(j);
        }
        Map<String, Integer> seen = new HashMap<>();
        int[] result = new int[urls.size()];
        for (int i = 0; i < urls.size(); i++) {
            String url = urls.get(i);
            List<Integer> same = byUrl.get(url);
            int k = seen.containsKey(url) ? seen.get(url) : 0;
            seen.put(url, k + 1);
            result[i] = same == null ? -1 : same.get(Math.min(k, same.size() - 1));
        }
        return result;
    }

    /**
     * The id downloads had before {@code v1}: scheme, authority and path without query, encoded exactly as
     * {@code android.net.Uri.Builder} re-encodes the decoded parts ({@code AudioPOJO.getCleanUrl()}).
     */
    public static String legacyId(String url) {
        if (url == null || url.isEmpty()) return "";
        int colon = url.indexOf(':');
        int stop = indexOfAny(url, "/?#");
        if (colon <= 0 || (stop >= 0 && stop < colon)) return url;
        String scheme = url.substring(0, colon);
        String ssp = url.substring(colon + 1);
        int end = indexOfAny(ssp, "?#");
        if (end >= 0) ssp = ssp.substring(0, end);
        StringBuilder b = new StringBuilder(scheme).append(':');
        String path = ssp;
        if (ssp.startsWith("//")) {
            int slash = ssp.indexOf('/', 2);
            String authority = slash >= 0 ? ssp.substring(2, slash) : ssp.substring(2);
            path = slash >= 0 ? ssp.substring(slash) : "";
            b.append("//").append(encode(decode(authority), ""));
        }
        return b.append(encode(decode(path), "/")).toString();
    }

    private static int indexOfAny(String s, String chars) {
        for (int i = 0; i < s.length(); i++) {
            if (chars.indexOf(s.charAt(i)) >= 0) return i;
        }
        return -1;
    }

    /** android.net.Uri.decode: %XX sequences as UTF-8, malformed escapes kept literally. */
    private static String decode(String s) {
        if (s.indexOf('%') < 0) return s;
        StringBuilder out = new StringBuilder();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == '%' && i + 2 < s.length()
                    && Character.digit(s.charAt(i + 1), 16) >= 0 && Character.digit(s.charAt(i + 2), 16) >= 0) {
                bytes.write((Character.digit(s.charAt(i + 1), 16) << 4) | Character.digit(s.charAt(i + 2), 16));
                i += 3;
                continue;
            }
            if (bytes.size() > 0) {
                out.append(new String(bytes.toByteArray(), StandardCharsets.UTF_8));
                bytes.reset();
            }
            out.append(c);
            i++;
        }
        if (bytes.size() > 0) out.append(new String(bytes.toByteArray(), StandardCharsets.UTF_8));
        return out.toString();
    }

    /** android.net.Uri.encode: everything but [A-Za-z0-9_!.~'()*-] and {@code allow} as %XX of its UTF-8 bytes. */
    private static String encode(String s, String allow) {
        StringBuilder out = new StringBuilder();
        for (byte raw : s.getBytes(StandardCharsets.UTF_8)) {
            int c = raw & 0xff;
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')
                    || "_-!.~'()*".indexOf(c) >= 0 || (c < 0x80 && allow.indexOf(c) >= 0)) {
                out.append((char) c);
            } else {
                out.append('%').append(Character.toUpperCase(Character.forDigit(c >> 4, 16)))
                        .append(Character.toUpperCase(Character.forDigit(c & 0xf, 16)));
            }
        }
        return out.toString();
    }
}
