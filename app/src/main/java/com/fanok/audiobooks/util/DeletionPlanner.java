package com.fanok.audiobooks.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Pure plan for deleting chapter rows. A row owns at most two things: the Media3 download(s) of its remote
 * {@code url_audio} (its {@code DownloadIds} entry; a stream download shared with other chapters only while no other
 * kept row needs it), and the exported copy named exactly by its {@code local_uri}. Nothing else is ever
 * planned for deletion: no name matches, no folders, and no files of imported books (their {@code url_audio} is the
 * user's own file, so removing them from the library removes rows only).
 */
public final class DeletionPlanner {

    /** Derives the Media3 download id from a remote chapter URL, exactly as the download request did. */
    public interface DownloadIds {
        String of(String remoteUrl);
    }

    public static final class Row {
        public final String urlBook;
        public final String urlAudio;
        public final String localUri;
        /** Every Media3 download id the chapter may be stored under; null to derive one from url_audio. */
        public final List<String> downloadIds;

        public Row(String urlBook, String urlAudio, String localUri) {
            this(urlBook, urlAudio, localUri, null);
        }

        public Row(String urlBook, String urlAudio, String localUri, List<String> downloadIds) {
            this.urlBook = urlBook;
            this.urlAudio = urlAudio;
            this.localUri = localUri;
            this.downloadIds = downloadIds;
        }
    }

    public static final class Plan {
        public final Set<String> downloadIdsToRemove = new LinkedHashSet<>();
        public final Set<String> urisToDelete = new LinkedHashSet<>();
        /** Online chapters that stay in the library once their exported copy is gone. */
        public final List<Row> rowsToClearLocalUri = new ArrayList<>();
        /** Rows removed from the library. */
        public final List<Row> rowsToRemove = new ArrayList<>();
    }

    private DeletionPlanner() {
    }

    /**
     * @param keepOnlineRows true to delete only the downloaded copies of the chapters (rows of online books stay,
     *                       rows of imported books leave the library); false to remove the rows themselves.
     * @param roots          configured download roots; a recorded URI equal to a root is never deleted.
     */
    public static Plan plan(Collection<Row> rows, Collection<String> roots, boolean keepOnlineRows, DownloadIds ids) {
        return plan(rows, roots, keepOnlineRows, ids, Collections.<String>emptySet());
    }

    /**
     * @param keptIds download ids still needed by rows that are not being deleted (see {@link #idsOf}): one HLS stream
     *                download serves several chapters and is removed only with the last of them.
     */
    public static Plan plan(Collection<Row> rows, Collection<String> roots, boolean keepOnlineRows, DownloadIds ids,
                            Collection<String> keptIds) {
        Plan plan = new Plan();
        for (Row row : rows) {
            for (String id : idsOf(row, ids)) {
                if (!keptIds.contains(id)) plan.downloadIdsToRemove.add(id);
            }
            boolean hasCopy = !isEmpty(row.localUri) && !isRoot(row.localUri, roots);
            if (hasCopy) plan.urisToDelete.add(row.localUri);
            if (!keepOnlineRows || isImportedBook(row.urlBook)) {
                plan.rowsToRemove.add(row);
            } else if (hasCopy) {
                plan.rowsToClearLocalUri.add(row);
            }
        }
        return plan;
    }

    /** The download ids of a remote row: its recorded ones, else the one derived from url_audio; none when local. */
    public static List<String> idsOf(Row row, DownloadIds ids) {
        List<String> result = new ArrayList<>();
        if (!isRemote(row.urlAudio)) return result;
        if (row.downloadIds != null) {
            for (String id : row.downloadIds) {
                if (!isEmpty(id)) result.add(id);
            }
        } else {
            String id = ids.of(row.urlAudio);
            if (!isEmpty(id)) result.add(id);
        }
        return result;
    }

    /** The download ids of all {@code rows}. */
    public static Set<String> idsOf(Collection<Row> rows, DownloadIds ids) {
        Set<String> result = new LinkedHashSet<>();
        for (Row row : rows) result.addAll(idsOf(row, ids));
        return result;
    }

    public static boolean isRemote(String url) {
        return url != null && (url.startsWith("http://") || url.startsWith("https://"));
    }

    /** Books created by the folder scan carry a synthetic ".../imported/..." URL. */
    public static boolean isImportedBook(String urlBook) {
        return urlBook != null && urlBook.contains("/imported/");
    }

    /**
     * The configured root that strictly contains {@code path} (plain or file:// path), or null. Empty folders are
     * pruned only below it, never the root itself.
     */
    public static String enclosingRoot(String path, Collection<String> roots) {
        String p = normalize(path);
        String best = null;
        for (String root : roots) {
            String r = normalize(root);
            if (!r.isEmpty() && p.startsWith(r + "/") && (best == null || r.length() > best.length())) best = r;
        }
        return best;
    }

    /** Folders the exporter creates for a book's chapters: source, book and reader. */
    public static final int BOOK_FOLDER_LEVELS = 3;

    /**
     * How many of the folders above an exported file may be pruned once empty, counted from the file's own folder.
     * {@code folders} are their paths, outermost first, the file's folder last. At most the
     * {@link #BOOK_FOLDER_LEVELS} the exporter created, and never a configured root nor a folder above one: the
     * count stops at the first such folder, so a root moved to a deeper folder survives.
     */
    public static int prunableDepth(List<String> folders, Collection<String> roots) {
        int depth = 0;
        for (int k = folders.size() - 1; k >= 0 && depth < BOOK_FOLDER_LEVELS; k--) {
            String f = normalize(folders.get(k));
            if (f.isEmpty() || f.equals("/")) break;
            boolean boundary = false;
            for (String root : roots) {
                String r = normalize(root);
                if (!r.isEmpty() && (r.equals(f) || r.startsWith(f + "/"))) boundary = true;
            }
            if (boundary) break;
            depth++;
        }
        return depth;
    }

    private static boolean isRoot(String uri, Collection<String> roots) {
        String u = normalize(uri);
        for (String root : roots) {
            if (u.equals(normalize(root))) return true;
        }
        return false;
    }

    private static String normalize(String s) {
        if (s == null) return "";
        String n = s.startsWith("file://") ? s.substring("file://".length()) : s;
        while (n.length() > 1 && n.endsWith("/")) n = n.substring(0, n.length() - 1);
        return n;
    }

    private static boolean isEmpty(String s) {
        return s == null || s.isEmpty();
    }
}
