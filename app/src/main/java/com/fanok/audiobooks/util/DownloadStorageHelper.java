package com.fanok.audiobooks.util;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.DocumentsContract;
import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.preference.PreferenceManager;
import androidx.documentfile.provider.DocumentFile;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.cache.Cache;
import androidx.media3.exoplayer.offline.Download;
import androidx.media3.exoplayer.offline.DownloadIndex;
import androidx.media3.exoplayer.offline.DownloadService;
import com.fanok.audiobooks.App;
import com.fanok.audiobooks.activity.BookActivity;
import com.fanok.audiobooks.download.DownloadIds;
import com.fanok.audiobooks.broadcasts.DownloadActionReceiver;
import com.fanok.audiobooks.model.AudioListDBModel;
import com.fanok.audiobooks.model.BooksDBModel;
import com.fanok.audiobooks.pojo.BookPOJO;
import com.fanok.audiobooks.room.BooksAudioEntity;
import com.fanok.audiobooks.search.Provider;
import com.fanok.audiobooks.service.ExoDownloadService;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DownloadStorageHelper {

    public static final String PREF_DOWNLOAD_PATH = "pref_download_path";
    public static final String PREF_AUTO_EXPORT = "auto_export";

    /**
     * Whether completed downloads go to the download folder on their own (the default); off, a book stays in the
     * download store only, played offline by the app, and "Экспорт в папку" copies it on request.
     */
    public static boolean autoExport(@NonNull Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context).getBoolean(PREF_AUTO_EXPORT, true);
    }
    public static final String PREF_DOWNLOAD_URI = "pref_download_uri";

    /** One worker serialises library scans and deletions so their read-modify-write passes never interleave. */
    private static final ExecutorService STORAGE = Executors.newSingleThreadExecutor();

    public static boolean openFolderPicker(@NonNull ActivityResultLauncher<Intent> launcher) {
        try {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                    | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            launcher.launch(intent);
            return true;
        } catch (android.content.ActivityNotFoundException e) {
            return false;
        } catch (Exception e) {
            e.printStackTrace();
            try {
                Intent simpleIntent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                launcher.launch(simpleIntent);
                return true;
            } catch (Exception ex) {
                ex.printStackTrace();
                return false;
            }
        }
    }

    public static void saveDownloadPath(@NonNull Context context, @NonNull String path) {
        try {
            SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(context);
            pref.edit()
                .remove(PREF_DOWNLOAD_URI)
                .putString(PREF_DOWNLOAD_PATH, path)
                .apply();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static class StorageOption {
        public final String title;
        public final String path;
        public StorageOption(String title, String path) {
            this.title = title;
            this.path = path;
        }
    }

    public static java.util.List<StorageOption> getAvailableStorageOptions(@NonNull Context context) {
        java.util.List<StorageOption> list = new java.util.ArrayList<>();
        try {
            File pubDownload = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            if (pubDownload != null) {
                File target = new File(pubDownload, "AudioBooks");
                list.add(new StorageOption("📂 Папка «Скачанное» в памяти телефона", target.getAbsolutePath()));
            }
        } catch (Exception ignored) {}

        try {
            File[] extDirs = context.getExternalFilesDirs(Environment.DIRECTORY_DOWNLOADS);
            if (extDirs != null) {
                for (int i = 0; i < extDirs.length; i++) {
                    File f = extDirs[i];
                    if (f != null) {
                        String p = f.getAbsolutePath();
                        if (i == 0 && list.isEmpty()) {
                            list.add(new StorageOption("📁 Папка приложения", p));
                        } else if (i > 0) {
                            list.add(new StorageOption("💳 Карта памяти SD Card", p));
                        }
                    }
                }
            }
        } catch (Exception ignored) {}

        return list;
    }

    public static void saveDownloadUri(@NonNull Context context, @NonNull Intent data) {
        Uri uri = data.getData();
        if (uri == null) return;

        try {
            int granted = data.getFlags();
            int takeFlags = ((granted & Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0 ? Intent.FLAG_GRANT_READ_URI_PERMISSION : 0)
                    | ((granted & Intent.FLAG_GRANT_WRITE_URI_PERMISSION) != 0 ? Intent.FLAG_GRANT_WRITE_URI_PERMISSION : 0);
            if (takeFlags != 0) {
                context.getContentResolver().takePersistableUriPermission(uri, takeFlags);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        String path = getPathFromUri(context, uri);
        try {
            SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(context);
            pref.edit()
                .putString(PREF_DOWNLOAD_URI, uri.toString())
                .putString(PREF_DOWNLOAD_PATH, path)
                .apply();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void resetToDefault(@NonNull Context context) {
        try {
            SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(context);
            pref.edit()
                .remove(PREF_DOWNLOAD_URI)
                .remove(PREF_DOWNLOAD_PATH)
                .apply();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @NonNull
    public static String getDefaultPath(@NonNull Context context) {
        try {
            File extDownloads = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
            if (extDownloads != null) {
                if (!extDownloads.exists()) {
                    extDownloads.mkdirs();
                }
                return extDownloads.getAbsolutePath();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        try {
            File publicDownloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            if (publicDownloads != null) {
                return publicDownloads.getAbsolutePath();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return context.getFilesDir().getAbsolutePath();
    }

    @NonNull
    public static String getDownloadPath(@NonNull Context context) {
        try {
            SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(context);
            String savedPath = pref.getString(PREF_DOWNLOAD_PATH, "");
            if (savedPath != null && !savedPath.isEmpty()) {
                File dir = new File(savedPath);
                if (!dir.exists()) {
                    //noinspection ResultOfMethodCallIgnored
                    dir.mkdirs();
                }
                return savedPath;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return getDefaultPath(context);
    }

    public static boolean canCreateFileInDir(@NonNull File dir) {
        try {
            File tempFile = new File(dir, ".test_write_" + System.currentTimeMillis());
            if (tempFile.createNewFile()) {
                //noinspection ResultOfMethodCallIgnored
                tempFile.delete();
                return true;
            }
        } catch (Exception e) {
            // Ignore
        }
        return false;
    }

    /** Runs {@code task} on the storage worker, after and before any export, scan or deletion. */
    public static void submit(@NonNull Runnable task) {
        STORAGE.execute(task);
    }

    /** Free space kept besides an exported file. */
    private static final long SPACE_MARGIN = 8L * 1024 * 1024;

    /** Receives, on the main thread, how many chapters were exported, how many failed and the first error. */
    public interface ExportCallback {
        void onExported(int exported, int failed, @Nullable String error);
    }

    /**
     * Exports, on the storage worker, every completed download of the given books whose chapter has no copy yet, into
     * "source/book/reader/NNN - title.ext" below the download folder, as a file any player opens: a single-file
     * chapter as it was downloaded, a chapter clipped out of a stream (HLS, or one file shared by several chapters) cut
     * out of it without re-encoding ({@link ChapterExtractor}), so every copy is exactly its chapter. A chapter's
     * local_uri is recorded only once its file is complete under its final name; asking again resumes after an
     * interruption, and a recorded copy that no longer exists is forgotten and written again. Then the Media3
     * downloads all of whose chapters have their copy are removed ({@link #releaseExported}): the book stays in the
     * download folder only.
     */
    @androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
    public static void exportBooks(@NonNull Context context, @NonNull Collection<String> bookUrls,
                                   @Nullable ExportCallback callback) {
        exportBooks(context, bookUrls, false, callback);
    }

    /** {@code automatic}: asked by the "auto_export" setting, and dropped if it is off by the time the export runs. */
    @androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
    public static void exportBooks(@NonNull Context context, @NonNull Collection<String> bookUrls, boolean automatic,
                                   @Nullable ExportCallback callback) {
        final Context app = context.getApplicationContext();
        final List<String> urls = new ArrayList<>(bookUrls);
        STORAGE.execute(() -> {
            if (automatic && !autoExport(app)) {
                return;
            }
            Tally tally = new Tally();
            try {
                Cache cache = DownloadUtil.getDownloadCache();
                DownloadIndex index = DownloadUtil.getDownloadManager().getDownloadIndex();
                AudioListDBModel model = new AudioListDBModel(app);
                BooksDBModel books = new BooksDBModel(app);
                for (String bookUrl : urls) {
                    exportBook(app, cache, index, model, books, bookUrl, tally);
                }
            } catch (Exception e) {
                tally.fail(e);
                android.util.Log.e("DownloadStorageHelper", "Export failed", e);
            }
            app.sendBroadcast(new Intent(BookActivity.Broadcast_UPDATE_ADAPTER).setPackage(app.getPackageName()));
            if (callback != null) {
                new android.os.Handler(android.os.Looper.getMainLooper())
                        .post(() -> callback.onExported(tally.exported, tally.failed, tally.error));
            }
        });
    }

    /**
     * Exports, on the storage worker, the books of every completed download: what a download finished while the app
     * was not running, or an interrupted export left, is written at the next start.
     */
    @androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
    public static void exportPending(@NonNull Context context) {
        final Context app = context.getApplicationContext();
        STORAGE.execute(() -> {
            if (!autoExport(app)) return;
            Set<String> bookUrls = new java.util.LinkedHashSet<>();
            try (androidx.media3.exoplayer.offline.DownloadCursor cursor = DownloadUtil.getDownloadManager()
                    .getDownloadIndex().getDownloads(Download.STATE_COMPLETED)) {
                while (cursor.moveToNext()) {
                    String bookUrl = DownloadUtil.bookUrlOf(cursor.getDownload().request);
                    if (bookUrl != null && !bookUrl.isEmpty()) bookUrls.add(bookUrl);
                }
            } catch (Exception e) {
                android.util.Log.w("DownloadStorageHelper", "Download index unreadable", e);
            }
            if (!bookUrls.isEmpty()) exportBooks(app, bookUrls, true, null);
        });
    }

    /** Counts of one export run and its first error. */
    private static final class Tally {
        int exported;
        int failed;
        String error;

        void fail(@NonNull Exception e) {
            failed++;
            if (error == null) error = e.getMessage() != null ? e.getMessage() : e.toString();
        }
    }

    /** True when playback clips the chapter out of its stream (the rule of the player's clipping). */
    static boolean isClipped(@NonNull BooksAudioEntity row) {
        return (row.timeStart >= 0 && row.timeEnd >= 0) || row.timeStart > 0;
    }

    @androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
    private static void exportBook(@NonNull Context app, @NonNull Cache cache, @NonNull DownloadIndex index,
                                   @NonNull AudioListDBModel model, @NonNull BooksDBModel books,
                                   @NonNull String bookUrl, @NonNull Tally tally) {
        BookPOJO book = books.getSomewhere(bookUrl);
        List<BooksAudioEntity> rows = model.getRows(bookUrl);
        List<DownloadIds.Entry> entries = DownloadUtil.entriesOf(bookUrl, rows);
        // chapters cut out of one download, in one pass over it
        java.util.Map<String, Download> streams = new java.util.LinkedHashMap<>();
        java.util.Map<String, List<Integer>> clipped = new java.util.HashMap<>();
        int bookExported = 0;
        for (int i = 0; i < rows.size(); i++) {
            BooksAudioEntity row = rows.get(i);
            if (!DeletionPlanner.isRemote(row.urlAudio)) continue;
            if (row.localUri != null) {
                if (exists(app, row.localUri)) continue;
                // Deleted outside the app: the stale record must not hide the chapter from export.
                model.getDatabase().booksAudioDao().updateLocalUriById(row.id, null);
                row.localUri = null;
            }
            Download download = DownloadUtil.find(index, entries.get(i), row.urlAudio);
            if (download == null || download.state != Download.STATE_COMPLETED) continue;
            if (book == null) book = DownloadUtil.bookOf(download.request);
            if (DownloadUtil.isProgressive(download.request) && !isClipped(row)) {
                try {
                    String uri = exportChapter(app, cache, download, book, bookUrl, row, numberOf(row, i));
                    model.getDatabase().booksAudioDao().updateLocalUriById(row.id, uri);
                    row.localUri = uri;
                    bookExported++;
                } catch (Exception e) {
                    tally.fail(e);
                    android.util.Log.e("DownloadStorageHelper", "Export failed: " + download.request.id, e);
                }
            } else {
                String id = download.request.id;
                streams.put(id, download);
                if (!clipped.containsKey(id)) clipped.put(id, new ArrayList<>());
                clipped.get(id).add(i);
            }
        }
        for (java.util.Map.Entry<String, Download> stream : streams.entrySet()) {
            bookExported += exportClips(app, cache, stream.getValue(), book, bookUrl, rows,
                    clipped.get(stream.getKey()), model, tally);
        }
        if (bookExported > 0) exportCover(app, book, bookUrl);
        tally.exported += bookExported;
        releaseExported(app, index, model, bookUrl, rows, entries);
    }

    /** Temporary files of an export the process died in; no other export runs meanwhile (one storage worker). */
    private static void removeStaleTemps(@NonNull File dir) {
        File[] stale = dir.listFiles((d, name) -> name.startsWith(".export-") && name.endsWith(".part"));
        if (stale == null) return;
        //noinspection ResultOfMethodCallIgnored
        for (File file : stale) file.delete();
    }

    /** "NNN" of a chapter file: its recorded position, else its place in the list. */
    private static int numberOf(@NonNull BooksAudioEntity row, int i) {
        return row.ordinal >= 0 ? row.ordinal + 1 : i + 1;
    }

    /**
     * Cuts the chapters {@code at} (indexes into {@code rows}) out of {@code download} in one pass, each into its own
     * file below the download folder (file path first, else the selected SAF tree through a temporary file of the
     * app), recording each copy as soon as it is complete; returns how many were exported.
     */
    @androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
    private static int exportClips(@NonNull Context context, @NonNull Cache cache, @NonNull Download download,
                                   @Nullable BookPOJO book, @NonNull String bookUrl,
                                   @NonNull List<BooksAudioEntity> rows, @NonNull List<Integer> at,
                                   @NonNull AudioListDBModel model, @NonNull Tally tally) {
        BooksAudioEntity first = rows.get(at.get(0));
        String bookName = book != null ? book.getName() : first.booksName;
        String artist = book != null ? book.getArtist() : null;
        String autor = book != null ? book.getAutor() : null;
        final File dir = getBookDownloadDir(context, bookUrl, bookName, artist, autor);
        final boolean toDir = (dir.isDirectory() || dir.mkdirs()) && canCreateFileInDir(dir);
        final DocumentFile voiceDoc;
        if (toDir) {
            voiceDoc = null;
        } else {
            DocumentFile root = getRootDocumentFile(context);
            DocumentFile source = root != null ? findOrCreateSubdir(root, getSourceName(bookUrl)) : null;
            DocumentFile bookDoc = source != null ? findOrCreateSubdir(source, sanitizeFileName(bookName)) : null;
            voiceDoc = bookDoc != null ? findOrCreateSubdir(bookDoc, voiceFolder(artist, autor)) : null;
            if (voiceDoc == null) {
                tally.failed += at.size();
                if (tally.error == null) tally.error = "Download folder does not accept files: " + dir;
                return 0;
            }
        }
        // a stream's chapter is cut out next to the store, on the volume chosen for it, before going to a SAF folder
        final File work = toDir ? dir : App.getInstance().getDownloadCacheDir().getParentFile();
        removeStaleTemps(work);
        List<ChapterExtractor.Clip> clips = new ArrayList<>(at.size());
        for (int i : at) {
            BooksAudioEntity row = rows.get(i);
            long startUs = Math.max(0, row.timeStart) * 1_000_000L;
            long endUs = row.timeEnd >= 0 ? row.timeEnd * 1_000_000L : Long.MAX_VALUE;
            clips.add(new ChapterExtractor.Clip(startUs, endUs));
        }
        final int[] done = {0};
        final int[] failed = {0};
        try {
            ChapterExtractor.extract(cache, download, clips, new ChapterExtractor.Output() {
                @NonNull
                @Override
                public File open(int clip) throws IOException {
                    if (work.getUsableSpace() < SPACE_MARGIN) throw new IOException("Not enough free space in " + work);
                    return File.createTempFile(".export-", ".part", work);
                }

                @Override
                public void done(int clip, @NonNull File file, @NonNull String extension) throws IOException {
                    BooksAudioEntity row = rows.get(at.get(clip));
                    int number = numberOf(row, at.get(clip));
                    String uri;
                    if (toDir) {
                        uri = placeInDir(file, dir, StorageNames.exportFileName(number, row.nameAudio, extension))
                                .getAbsolutePath();
                    } else {
                        try {
                            uri = exportToTree(context, voiceDoc, out -> copyFile(file, out), number, row.nameAudio,
                                    extension);
                        } finally {
                            //noinspection ResultOfMethodCallIgnored
                            file.delete();
                        }
                    }
                    model.getDatabase().booksAudioDao().updateLocalUriById(row.id, uri);
                    row.localUri = uri;
                    done[0]++;
                }

                @Override
                public void failed(int clip, @NonNull Exception error) {
                    failed[0]++;
                    tally.fail(error);
                    android.util.Log.e("DownloadStorageHelper", "Export failed: " + rows.get(at.get(clip)).nameAudio,
                            error);
                }
            });
        } catch (Exception e) {
            // the stream itself: the chapters it did not report fail with it
            int left = at.size() - done[0] - failed[0];
            if (left > 0) {
                tally.fail(e);
                tally.failed += left - 1;
            }
            android.util.Log.e("DownloadStorageHelper", "Export failed: " + download.request.id, e);
        }
        return done[0];
    }

    /** Downloads removed because their chapters are exported, not deleted by the user, until the manager is done. */
    private static final Set<String> RELEASED =
            Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<String, Boolean>());

    /** True while the download {@code id} is being removed after its chapters were exported. */
    public static boolean isReleased(@NonNull String id) {
        return RELEASED.contains(id);
    }

    /** The manager removed the download {@code id}. */
    public static void forgetReleased(@NonNull String id) {
        RELEASED.remove(id);
    }

    /**
     * Removes the completed Media3 downloads of the book that only chapters with their exported copy use (the book's
     * own rows and those of another saved entry of it), so it is kept once, in the download folder. Not while the
     * player holds the book: it may be reading the cache; a later export (at the latest the next start) removes them.
     */
    @androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
    private static void releaseExported(@NonNull Context context, @NonNull DownloadIndex index,
                                        @NonNull AudioListDBModel model, @NonNull String bookUrl,
                                        @NonNull List<BooksAudioEntity> rows, @NonNull List<DownloadIds.Entry> entries) {
        if (DownloadUtil.sameBook(bookUrl, com.fanok.audiobooks.service.MediaPlayerService.currentBookUrl())) return;
        java.util.Map<String, Boolean> copied = new java.util.HashMap<>();
        for (int i = 0; i < rows.size(); i++) {
            BooksAudioEntity row = rows.get(i);
            if (!DeletionPlanner.isRemote(row.urlAudio)) continue;
            Download download = DownloadUtil.find(index, entries.get(i), row.urlAudio);
            if (download == null || download.state != Download.STATE_COMPLETED) continue;
            String id = download.request.id;
            boolean has = row.localUri != null && exists(context, row.localUri);
            copied.put(id, has && (!copied.containsKey(id) || copied.get(id)));
        }
        for (java.util.Map.Entry<String, List<BooksAudioEntity>> alias : model.getAliasRows(bookUrl).entrySet()) {
            for (BooksAudioEntity row : alias.getValue()) {
                if (row.localUri != null && exists(context, row.localUri)) continue;
                for (DownloadIds.Entry entry : DownloadUtil.entriesOf(alias.getKey(), Collections.singletonList(row))) {
                    for (String id : DownloadUtil.idsOf(entry)) copied.remove(id);
                }
            }
        }
        for (java.util.Map.Entry<String, Boolean> download : copied.entrySet()) {
            if (!download.getValue()) continue;
            final String id = download.getKey();
            RELEASED.add(id);
            // the manager itself, on its looper: no service start, which Android may refuse in the background
            new android.os.Handler(android.os.Looper.getMainLooper())
                    .post(() -> DownloadUtil.getDownloadManager().removeDownload(id));
        }
    }

    /** Writes one chapter below the download folder (file path first, else the selected SAF tree); returns its URI. */
    @androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
    @NonNull
    private static String exportChapter(@NonNull Context context, @NonNull Cache cache, @NonNull Download download,
                                        @Nullable BookPOJO book, @NonNull String bookUrl, @NonNull BooksAudioEntity row,
                                        int number) throws IOException {
        long length = DownloadUtil.lengthOf(cache, download);
        String key = DownloadUtil.cacheKeyOf(download.request);
        String fallbackExt = StorageNames.audioExtension(row.urlAudio, "mp3");
        String bookName = book != null ? book.getName() : row.booksName;
        String artist = book != null ? book.getArtist() : null;
        String autor = book != null ? book.getAutor() : null;
        File dir = getBookDownloadDir(context, bookUrl, bookName, artist, autor);
        removeStaleTemps(dir);
        IOException fileError;
        try {
            return exportToDir(cache, key, download.request.uri, length, dir, number, row.nameAudio, fallbackExt)
                    .getAbsolutePath();
        } catch (IOException e) {
            fileError = e;
        }
        DocumentFile root = getRootDocumentFile(context);
        if (root == null) throw fileError;
        DocumentFile source = findOrCreateSubdir(root, getSourceName(bookUrl));
        DocumentFile bookDoc = source != null ? findOrCreateSubdir(source, sanitizeFileName(bookName)) : null;
        DocumentFile voiceDoc = bookDoc != null ? findOrCreateSubdir(bookDoc, voiceFolder(artist, autor)) : null;
        if (voiceDoc == null) throw new IOException("Download folder does not accept " + row.nameAudio, fileError);
        long free = new File(getDownloadPath(context)).getUsableSpace();
        if (free > 0 && free < length + SPACE_MARGIN) throw new IOException("Not enough free space");
        Uri uri = download.request.uri;
        return exportToTree(context, voiceDoc, out -> DownloadUtil.copyExact(cache, key, uri, length, out), number,
                row.nameAudio, fallbackExt);
    }

    /**
     * Copies the cached resource {@code key} into {@code dir} as "NNN - title.ext": into a temp file, then renamed
     * onto a name reserved with {@link File#createNewFile} (" (2)", " (3)" ... when taken), so an existing file is
     * never replaced or truncated. The extension is sniffed from the first bytes. Throws, leaving nothing behind,
     * unless exactly {@code length} bytes were copied.
     */
    @androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
    @NonNull
    public static File exportToDir(@NonNull Cache cache, @NonNull String key, @NonNull Uri uri, long length,
                                   @NonNull File dir, int number, @Nullable String title,
                                   @NonNull String fallbackExt) throws IOException {
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("Cannot create " + dir);
        if (dir.getUsableSpace() < length + SPACE_MARGIN) throw new IOException("Not enough free space in " + dir);
        File temp = File.createTempFile(".export-", ".part", dir);
        boolean done = false;
        try {
            HeadOutputStream head;
            try (java.io.FileOutputStream os = new java.io.FileOutputStream(temp)) {
                head = new HeadOutputStream(os);
                DownloadUtil.copyExact(cache, key, uri, length, head);
                os.getFD().sync();
            }
            File target = placeInDir(temp, dir,
                    StorageNames.exportFileName(number, title, sniffExtension(head.head(), fallbackExt)));
            done = true;
            return target;
        } finally {
            //noinspection ResultOfMethodCallIgnored
            if (!done) temp.delete();
        }
    }

    /**
     * Renames the complete {@code temp} (in {@code dir}) onto {@code base}, or "base (n).ext" when taken, a name
     * reserved with {@link File#createNewFile} so an existing file is never replaced; deletes {@code temp} on failure.
     */
    @NonNull
    private static File placeInDir(@NonNull File temp, @NonNull File dir, @NonNull String base) throws IOException {
        File target = null;
        boolean done = false;
        try {
            for (int n = 1; target == null; n++) {
                if (n > 999) throw new IOException("No free name for " + base);
                File candidate = new File(dir, uniqueName(base, n));
                if (candidate.createNewFile()) target = candidate;
            }
            if (!temp.renameTo(target)) throw new IOException("Cannot rename to " + target);
            done = true;
            return target;
        } finally {
            if (!done) {
                //noinspection ResultOfMethodCallIgnored
                temp.delete();
                //noinspection ResultOfMethodCallIgnored
                if (target != null && target.length() == 0) target.delete();
            }
        }
    }

    /** The bytes of one exported file, written to {@code out}. */
    private interface Body {
        void writeTo(@NonNull OutputStream out) throws IOException;
    }

    private static void copyFile(@NonNull File file, @NonNull OutputStream out) throws IOException {
        try (java.io.InputStream in = new java.io.FileInputStream(file)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
        }
    }

    /** SAF variant of {@link #exportToDir}: a temp document renamed with renameDocument; returns the renamed URI. */
    @NonNull
    private static String exportToTree(@NonNull Context context, @NonNull DocumentFile dir, @NonNull Body body,
                                       int number, @Nullable String title, @NonNull String fallbackExt)
            throws IOException {
        DocumentFile temp = dir.createFile("application/octet-stream", ".export-" + System.nanoTime() + ".part");
        if (temp == null) throw new IOException("Download folder does not accept a new file");
        boolean done = false;
        try {
            HeadOutputStream head;
            try (OutputStream os = context.getContentResolver().openOutputStream(temp.getUri(), "w")) {
                if (os == null) throw new IOException("Cannot write " + temp.getUri());
                head = new HeadOutputStream(os);
                body.writeTo(head);
            }
            String base = StorageNames.exportFileName(number, title, sniffExtension(head.head(), fallbackExt));
            String name = base;
            for (int n = 2; dir.findFile(name) != null; n++) {
                if (n > 999) throw new IOException("No free name for " + base);
                name = uniqueName(base, n);
            }
            Uri renamed = DocumentsContract.renameDocument(context.getContentResolver(), temp.getUri(), name);
            if (renamed == null) throw new IOException("Cannot rename to " + name);
            done = true;
            return renamed.toString();
        } finally {
            if (!done) temp.delete();
        }
    }

    /** {@code name} for n = 1, else "base (n).ext". */
    @NonNull
    static String uniqueName(@NonNull String name, int n) {
        if (n <= 1) return name;
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) + " (" + n + ")" + name.substring(dot) : name + " (" + n + ")";
    }

    /** Audio container of the first bytes: "mp3", "m4a", "ogg", "aac", "flac", "wav", "wma"; else {@code fallback}. */
    @NonNull
    static String sniffExtension(@NonNull byte[] head, @NonNull String fallback) {
        if (startsWith(head, 0, "ID3")) return "mp3";
        if (startsWith(head, 0, "0&²ufÏ")) return "wma";
        if (startsWith(head, 4, "ftyp")) return "m4a";
        if (startsWith(head, 0, "OggS")) return "ogg";
        if (startsWith(head, 0, "fLaC")) return "flac";
        if (startsWith(head, 0, "RIFF")) return "wav";
        if (head.length >= 2 && (head[0] & 0xff) == 0xff && (head[1] & 0xe0) == 0xe0) {
            return (head[1] & 0x06) == 0 ? "aac" : "mp3";
        }
        return fallback;
    }

    private static boolean startsWith(byte[] head, int offset, String magic) {
        if (head.length < offset + magic.length()) return false;
        for (int i = 0; i < magic.length(); i++) {
            if (head[offset + i] != (byte) magic.charAt(i)) return false;
        }
        return true;
    }

    /** Passes bytes through and keeps the first 12 for {@link #sniffExtension}. */
    private static final class HeadOutputStream extends java.io.FilterOutputStream {
        private final byte[] head = new byte[12];
        private int size;

        HeadOutputStream(OutputStream out) {
            super(out);
        }

        @Override
        public void write(@NonNull byte[] b, int off, int len) throws IOException {
            int keep = Math.min(len, head.length - size);
            if (keep > 0) {
                System.arraycopy(b, off, head, size, keep);
                size += keep;
            }
            out.write(b, off, len);
        }

        byte[] head() {
            return java.util.Arrays.copyOf(head, size);
        }
    }

    /** Saves the book cover next to its exported chapters once (file path only); failures leave no file. */
    private static void exportCover(@NonNull Context context, @Nullable BookPOJO book, @NonNull String bookUrl) {
        if (book == null || book.getPhoto() == null || !DeletionPlanner.isRemote(book.getPhoto())) return;
        File dir = getBookDownloadDir(context, bookUrl, book.getName(), book.getArtist(), book.getAutor());
        File cover = new File(dir, "cover.jpg");
        if (!dir.isDirectory() || cover.exists()) return;
        File temp = null;
        try {
            temp = File.createTempFile(".cover-", ".part", dir);
            okhttp3.Request request = new okhttp3.Request.Builder().url(book.getPhoto()).build();
            try (okhttp3.Response response = com.fanok.audiobooks.App.getOkHttpClient().newCall(request).execute();
                 java.io.FileOutputStream os = new java.io.FileOutputStream(temp)) {
                if (!response.isSuccessful()) return;
                os.write(response.body().bytes());
            }
            if (cover.createNewFile() && !temp.renameTo(cover)) {
                //noinspection ResultOfMethodCallIgnored
                cover.delete();
            }
        } catch (Exception e) {
            android.util.Log.w("DownloadStorageHelper", "Cover not saved", e);
        } finally {
            //noinspection ResultOfMethodCallIgnored
            if (temp != null) temp.delete();
        }
    }

    private static androidx.documentfile.provider.DocumentFile findOrCreateSubdir(androidx.documentfile.provider.DocumentFile parent, String name) {
        if (parent == null || name == null || name.isEmpty()) return null;
        androidx.documentfile.provider.DocumentFile child = parent.findFile(name);
        if (child != null && child.isDirectory()) {
            return child;
        }
        return parent.createDirectory(name);
    }

    @Nullable
    public static String getSavedUriString(@NonNull Context context) {
        try {
            SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(context);
            return pref.getString(PREF_DOWNLOAD_URI, null);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    @NonNull
    public static String getPathFromUri(@NonNull Context context, @Nullable Uri uri) {
        if (uri == null) return getDefaultPath(context);

        try {
            String docId = null;
            if (DocumentsContract.isTreeUri(uri)) {
                docId = DocumentsContract.getTreeDocumentId(uri);
            } else if (DocumentsContract.isDocumentUri(context, uri)) {
                docId = DocumentsContract.getDocumentId(uri);
            }

            if (docId != null) return pathOfDocumentId(docId);
        } catch (Exception e) {
            e.printStackTrace();
        }

        try {
            String path = uri.getPath();
            if (path != null) {
                if (path.contains(":")) {
                    String[] split = path.split(":");
                    String volume = split[0].substring(path.lastIndexOf('/') + 1);
                    String subPath = split.length > 1 ? split[1] : "";
                    if ("primary".equalsIgnoreCase(volume)) {
                        return Environment.getExternalStorageDirectory() + "/" + subPath;
                    }
                    return "/storage/" + volume + "/" + subPath;
                }
                return path;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return uri.toString();
    }

    /** File path of an external-storage document id: "primary:a/b" below the external storage, "XXXX-XXXX:a" below /storage. */
    @NonNull
    private static String pathOfDocumentId(@NonNull String docId) {
        String[] split = docId.split(":");
        String type = split[0];
        String path = split.length > 1 ? split[1] : "";

        if ("primary".equalsIgnoreCase(type)) {
            File extDir = Environment.getExternalStorageDirectory();
            return path.isEmpty() ? extDir.getAbsolutePath() : extDir.getAbsolutePath() + "/" + path;
        } else {
            return path.isEmpty() ? "/storage/" + type : "/storage/" + type + "/" + path;
        }
    }

    /** The book's site folder below the download folder ({@link Provider#folder}), by {@link Provider#ofUrl}: every host alias of a site. */
    @NonNull
    public static String getSourceName(@Nullable String bookUrl) {
        Provider provider = bookUrl == null || bookUrl.isEmpty() ? null : Provider.ofUrl(bookUrl);
        return provider == null ? "Audiobooks" : provider.folder;
    }

    @NonNull
    public static String sanitizeFileName(@Nullable String name) {
        if (name == null || name.trim().isEmpty()) return "Unknown";
        String sanitized = name.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        return sanitized.isEmpty() ? "Unknown" : sanitized;
    }

    @NonNull
    public static File getBookDownloadDir(@NonNull Context context, @Nullable String bookUrl, @Nullable String bookName, @Nullable String artistName, @Nullable String autorName) {
        return new File(getDownloadPath(context), exportRelativeDir(bookUrl, bookName, artistName, autorName));
    }

    /** "source/book/reader": where below the download root the exporter writes a book's chapters. */
    @NonNull
    static String exportRelativeDir(@Nullable String bookUrl, @Nullable String bookName,
                                    @Nullable String artistName, @Nullable String autorName) {
        return getSourceName(bookUrl) + "/" + sanitizeFileName(bookName) + "/" + voiceFolder(artistName, autorName);
    }

    @NonNull
    private static String voiceFolder(@Nullable String artistName, @Nullable String autorName) {
        if (artistName != null && !artistName.trim().isEmpty()) return sanitizeFileName(artistName);
        if (autorName != null && !autorName.trim().isEmpty()) return sanitizeFileName(autorName);
        return "DefaultVoice";
    }

    @Nullable
    public static androidx.documentfile.provider.DocumentFile getRootDocumentFile(@NonNull Context context) {
        try {
            String uriStr = getSavedUriString(context);
            if (uriStr != null && !uriStr.isEmpty()) {
                Uri uri = Uri.parse(uriStr);
                androidx.documentfile.provider.DocumentFile docFile = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, uri);
                if (docFile != null && docFile.exists()) {
                    return docFile;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    @Nullable
    public static String findLocalCoverForBook(@NonNull Context context, @Nullable BookPOJO book) {
        if (book == null || book.getUrl() == null || book.getUrl().isEmpty()) return null;
        try {
            String photo = book.getPhoto();
            if (photo != null && !photo.isEmpty()) {
                if (photo.startsWith("content://")) {
                    androidx.documentfile.provider.DocumentFile df =
                            androidx.documentfile.provider.DocumentFile.fromSingleUri(context, android.net.Uri.parse(photo));
                    if (df != null && df.exists()) return photo;
                } else if (photo.startsWith("/") || photo.startsWith("file://")) {
                    File f = new File(photo.replace("file://", ""));
                    if (f.exists()) return photo;
                }
            }

            File dir = getBookDownloadDir(context, book.getUrl(), book.getName(), book.getArtist(), book.getAutor());
            if (dir != null && dir.exists()) {
                String cover = findCoverImage(dir);
                if (!cover.isEmpty()) return cover;
                File[] files = dir.listFiles();
                if (files != null) {
                    for (File f : files) {
                        if (f.isFile() && isImageFileName(f.getName())) {
                            return f.getAbsolutePath();
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static boolean isImageFileName(@Nullable String name) {
        if (name == null) return false;
        String lname = name.toLowerCase();
        return lname.endsWith(".jpg") || lname.endsWith(".jpeg") || lname.endsWith(".png")
                || lname.endsWith(".webp") || lname.endsWith(".bmp") || lname.endsWith(".gif");
    }

    private static boolean isAudioFileName(@Nullable String name) {
        if (name == null) return false;
        String lname = name.toLowerCase();
        return lname.endsWith(".mp3") || lname.endsWith(".m4a") || lname.endsWith(".m4b")
                || lname.endsWith(".aac") || lname.endsWith(".ts") || lname.endsWith(".flac")
                || lname.endsWith(".ogg") || lname.endsWith(".wav") || lname.endsWith(".wma")
                || lname.endsWith(".mp4");
    }

    public static void scanAndImportDownloadedBooks(@NonNull Context context) {
        scanAndImportDownloadedBooks(context, null);
    }

    public static void scanAndImportDownloadedBooks(@NonNull Context context, @Nullable Runnable onDone) {
        final Context app = context.getApplicationContext();
        STORAGE.execute(() -> {
            try {
                BooksDBModel booksDBModel = new BooksDBModel(app);
                AudioListDBModel audioListDBModel = new AudioListDBModel(app);

                String downloadPath = getDownloadPath(app);
                File baseDir = new File(downloadPath);
                if (baseDir.exists() && baseDir.isDirectory() && baseDir.canRead()) {
                    scanDirRecursive(app, booksDBModel, audioListDBModel, baseDir, baseDir.getAbsolutePath(),
                            exportedLocations(app, booksDBModel, audioListDBModel, false));
                } else {
                    androidx.documentfile.provider.DocumentFile rootDoc = getRootDocumentFile(app);
                    if (rootDoc != null) {
                        scanDocDirRecursive(app, booksDBModel, audioListDBModel, rootDoc, "", rootDoc.getUri().toString(),
                                exportedLocations(app, booksDBModel, audioListDBModel, true));
                    }
                }

                Intent intent = new Intent(BookActivity.Broadcast_UPDATE_ADAPTER).setPackage(app.getPackageName());
                app.sendBroadcast(intent);
            } catch (Throwable t) {
                android.util.Log.e("ScanHelper", "Error scanning download folder", t);
            } finally {
                if (onDone != null) {
                    android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
                    mainHandler.post(onDone);
                }
            }
        });
    }

    /**
     * What the scan must not import as separate books: the copies recorded for online books (local_uri, or a local
     * url_audio written before local_uri existed) and the export folder of every saved online book, as absolute
     * paths or, for a SAF scan, as "source/book/reader" below the tree.
     */
    @NonNull
    private static Set<String> exportedLocations(@NonNull Context context, @NonNull BooksDBModel booksDBModel,
                                                 @NonNull AudioListDBModel audioListDBModel, boolean saf) {
        Set<String> exported = new HashSet<>();
        for (BooksAudioEntity row : audioListDBModel.getDatabase().booksAudioDao().getAll()) {
            if (DeletionPlanner.isImportedBook(row.urlBook)) continue;
            if (row.localUri != null) exported.add(row.localUri);
            if (row.urlAudio != null && !DeletionPlanner.isRemote(row.urlAudio)) exported.add(row.urlAudio);
        }
        java.util.ArrayList<BookPOJO> saved = booksDBModel.getAllSaved();
        if (saved != null) {
            for (BookPOJO book : saved) {
                if (book.getUrl() == null || DeletionPlanner.isImportedBook(book.getUrl())) continue;
                exported.add(saf
                        ? exportRelativeDir(book.getUrl(), book.getName(), book.getArtist(), book.getAutor())
                        : getBookDownloadDir(context, book.getUrl(), book.getName(), book.getArtist(), book.getAutor()).getAbsolutePath());
            }
        }
        return exported;
    }

    private static void scanDocDirRecursive(@NonNull Context context,
                                             @NonNull com.fanok.audiobooks.model.BooksDBModel booksDBModel,
                                             @NonNull com.fanok.audiobooks.model.AudioListDBModel audioListDBModel,
                                             @NonNull androidx.documentfile.provider.DocumentFile dir,
                                             @NonNull String relativePath, @NonNull String root,
                                             @NonNull Set<String> exported) {
        if (dir.getName() != null && dir.getName().startsWith(".")) return;

        androidx.documentfile.provider.DocumentFile[] files = dir.listFiles();
        if (files == null) return;

        java.util.List<androidx.documentfile.provider.DocumentFile> audioDocFiles = new java.util.ArrayList<>();
        for (androidx.documentfile.provider.DocumentFile f : files) {
            if (f.isFile() && isAudioFileName(f.getName()) && !exported.contains(f.getUri().toString())) {
                audioDocFiles.add(f);
            }
        }

        if (!audioDocFiles.isEmpty() && !exported.contains(relativePath)) {
            String dirName = dir.getName() != null ? dir.getName() : "Voice";
            String voiceName = "Основной";
            String bookName = dirName;
            String sourceName = "Локально";

            androidx.documentfile.provider.DocumentFile parent = dir.getParentFile();
            if (parent != null && parent.getName() != null && !parent.getName().equalsIgnoreCase("Download") && !parent.getName().equalsIgnoreCase("0")) {
                if (dirName.equalsIgnoreCase("DefaultVoice") || dirName.equalsIgnoreCase("Voice") || dirName.toLowerCase().startsWith("озвуч")) {
                    voiceName = dirName;
                    bookName = parent.getName();
                    androidx.documentfile.provider.DocumentFile grandParent = parent.getParentFile();
                    if (grandParent != null && grandParent.getName() != null && !grandParent.getName().equalsIgnoreCase("Download")) {
                        sourceName = grandParent.getName();
                    }
                } else {
                    sourceName = parent.getName();
                }
            }

            processDocBookDir(context, booksDBModel, audioListDBModel, root, sourceName, bookName, voiceName, dir,
                    audioDocFiles);
        }

        for (androidx.documentfile.provider.DocumentFile f : files) {
            if (f.isDirectory()) {
                scanDocDirRecursive(context, booksDBModel, audioListDBModel, f,
                        relativePath.isEmpty() ? f.getName() : relativePath + "/" + f.getName(), root, exported);
            }
        }
    }

    private static String findCoverImage(@NonNull File dir) {
        File[] files = dir.listFiles();
        if (files == null) return "";
        File candidate = null;
        for (File f : files) {
            if (f.isFile()) {
                String name = f.getName().toLowerCase();
                if (name.equals("cover.jpg") || name.equals("cover.png") || name.equals("cover.jpeg") || name.equals("folder.jpg") || name.equals("folder.png")) {
                    return f.getAbsolutePath();
                }
                if (candidate == null && (name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png") || name.endsWith(".webp"))) {
                    candidate = f;
                }
            }
        }
        return candidate != null ? candidate.getAbsolutePath() : "";
    }

    private static String findDocCoverImage(@NonNull androidx.documentfile.provider.DocumentFile dir) {
        androidx.documentfile.provider.DocumentFile[] files = dir.listFiles();
        if (files == null) return "";
        androidx.documentfile.provider.DocumentFile candidate = null;
        for (androidx.documentfile.provider.DocumentFile f : files) {
            if (f.isFile() && f.getName() != null) {
                String name = f.getName().toLowerCase();
                if (name.equals("cover.jpg") || name.equals("cover.png") || name.equals("cover.jpeg") || name.equals("folder.jpg") || name.equals("folder.png")) {
                    return f.getUri() != null ? f.getUri().toString() : "";
                }
                if (candidate == null && (name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png") || name.endsWith(".webp"))) {
                    candidate = f;
                }
            }
        }
        return (candidate != null && candidate.getUri() != null) ? candidate.getUri().toString() : "";
    }

    private static void processDocBookDir(@NonNull Context context,
                                          @NonNull com.fanok.audiobooks.model.BooksDBModel booksDBModel,
                                          @NonNull com.fanok.audiobooks.model.AudioListDBModel audioListDBModel,
                                          @NonNull String root,
                                          @NonNull String sourceName, @NonNull String bookName, @NonNull String voiceName,
                                          @NonNull androidx.documentfile.provider.DocumentFile dir,
                                          @NonNull java.util.List<androidx.documentfile.provider.DocumentFile> audioDocFiles) {
        List<String[]> tracks = new ArrayList<>();
        for (androidx.documentfile.provider.DocumentFile audioDoc : audioDocFiles) {
            tracks.add(new String[]{audioDoc.getName() != null ? audioDoc.getName() : "track",
                    audioDoc.getUri() != null ? audioDoc.getUri().toString() : ""});
        }
        importPlaylist(booksDBModel, audioListDBModel, root, sourceName, bookName, voiceName, findDocCoverImage(dir), tracks);
    }

    private static void scanDirRecursive(@NonNull Context context,
                                         @NonNull com.fanok.audiobooks.model.BooksDBModel booksDBModel,
                                         @NonNull com.fanok.audiobooks.model.AudioListDBModel audioListDBModel,
                                         @NonNull File dir, @NonNull String root, @NonNull Set<String> exported) {
        if (dir.getName().startsWith(".")) return;

        File[] audioFiles = dir.listFiles(new java.io.FilenameFilter() {
            @Override
            public boolean accept(File d, String name) {
                return isAudioFileName(name) && !exported.contains(new File(d, name).getAbsolutePath());
            }
        });

        if (audioFiles != null && audioFiles.length > 0 && !exported.contains(dir.getAbsolutePath())) {
            String dirName = dir.getName();
            String voiceName = "Основной";
            String bookName = dirName;
            String sourceName = "Локально";

            File parent = dir.getParentFile();
            if (parent != null && !parent.getName().equalsIgnoreCase("Download") && !parent.getName().equalsIgnoreCase("0") && !parent.getName().equalsIgnoreCase("sdcard")) {
                if (dirName.equalsIgnoreCase("DefaultVoice") || dirName.equalsIgnoreCase("Voice") || dirName.toLowerCase().startsWith("озвуч")) {
                    voiceName = dirName;
                    bookName = parent.getName();
                    File grandParent = parent.getParentFile();
                    if (grandParent != null && !grandParent.getName().equalsIgnoreCase("Download") && !grandParent.getName().equalsIgnoreCase("0")) {
                        sourceName = grandParent.getName();
                    }
                } else {
                    sourceName = parent.getName();
                }
            }

            processBookDir(context, booksDBModel, audioListDBModel, root, sourceName, bookName, voiceName, dir, audioFiles);
        }

        File[] subDirs = dir.listFiles(new java.io.FileFilter() {
            @Override
            public boolean accept(File pathname) {
                return pathname != null && pathname.isDirectory();
            }
        });
        if (subDirs != null) {
            for (File subDir : subDirs) {
                scanDirRecursive(context, booksDBModel, audioListDBModel, subDir, root, exported);
            }
        }
    }

    private static void processBookDir(@NonNull Context context,
                                       @NonNull com.fanok.audiobooks.model.BooksDBModel booksDBModel,
                                       @NonNull com.fanok.audiobooks.model.AudioListDBModel audioListDBModel,
                                       @NonNull String root,
                                       @NonNull String sourceName, @NonNull String bookName, @NonNull String voiceName,
                                       @NonNull File dir, @NonNull File[] audioFiles) {
        List<String[]> tracks = new ArrayList<>();
        for (File audioFile : audioFiles) {
            tracks.add(new String[]{audioFile.getName(), audioFile.getAbsolutePath()});
        }
        importPlaylist(booksDBModel, audioListDBModel, root, sourceName, bookName, voiceName, findCoverImage(dir), tracks);
    }

    /**
     * Synthetic URL of a book imported from the scanned folder {@code root} (tree URI or path), as
     * ".../imported/rootHash/title/reader": two roots, or two readers of one title, stay separate books.
     */
    @NonNull
    static String importedBookUrl(@NonNull String root, @NonNull String sourceName, @NonNull String bookName,
                                  @NonNull String voiceName) {
        String r = root;
        while (r.length() > 1 && r.endsWith("/")) r = r.substring(0, r.length() - 1);
        return "https://" + sourceName.toLowerCase() + ".org/imported/"
                + String.format(java.util.Locale.ROOT, "%08x", r.hashCode()) + "/"
                + sanitizeFileName(bookName) + "/" + sanitizeFileName(voiceName);
    }

    /**
     * Writes one scanned folder as an imported book in one transaction, tracks ({name, uri}) in natural order.
     * The identity is root, source, title and reader folder ({@link #importedBookUrl}).
     */
    private static void importPlaylist(@NonNull BooksDBModel booksDBModel, @NonNull AudioListDBModel audioListDBModel,
                                       @NonNull String root,
                                       @NonNull String sourceName, @NonNull String bookName, @NonNull String voiceName,
                                       @NonNull String photoUrl, @NonNull List<String[]> tracks) {
        String titleUrl = "https://" + sourceName.toLowerCase() + ".org/imported/" + sanitizeFileName(bookName);
        String readerUrl = titleUrl + "/" + sanitizeFileName(voiceName);
        String fakeUrl = importedBookUrl(root, sourceName, bookName, voiceName);
        Collections.sort(tracks, (t1, t2) -> StorageNames.naturalCompare(t1[0], t2[0]));

        audioListDBModel.getDatabase().runInTransaction(() -> {
            // Imports made before the reader, and then the root, were part of the identity.
            for (String legacyUrl : new String[]{titleUrl, readerUrl}) {
                booksDBModel.removeSavedByUrl(legacyUrl);
                audioListDBModel.remove(legacyUrl);
            }

            BookPOJO book = booksDBModel.getSaved(fakeUrl);
            if (book == null) {
                book = new BookPOJO();
                book.setUrl(fakeUrl);
                book.setName(bookName.replace("_", " "));
                book.setArtist(voiceName.replace("_", " "));
                book.setAutor("Импортировано");
                book.setPhoto(photoUrl);

                booksDBModel.addSaved(book);
            } else if ((book.getPhoto() == null || book.getPhoto().isEmpty()) && !photoUrl.isEmpty()) {
                book.setPhoto(photoUrl);
                booksDBModel.addSaved(book);
            }

            audioListDBModel.remove(fakeUrl);
            for (String[] track : tracks) {
                com.fanok.audiobooks.pojo.AudioListPOJO audioPOJO = new com.fanok.audiobooks.pojo.AudioListPOJO();
                audioPOJO.setBookUrl(fakeUrl);
                audioPOJO.setBookName(bookName.replace("_", " "));
                String nameNoExt = track[0];
                int dotPos = nameNoExt.lastIndexOf('.');
                if (dotPos > 0) nameNoExt = nameNoExt.substring(0, dotPos);
                audioPOJO.setAudioName(nameNoExt);
                audioPOJO.setAudioUrl(track[1]);
                audioListDBModel.add(audioPOJO);
            }
        });
    }

    /** Media3 download id of a remote chapter URL: scheme, authority and path without the query. */
    @NonNull
    public static String downloadIdOf(@NonNull String remoteUrl) {
        Uri uri = Uri.parse(remoteUrl);
        return new Uri.Builder()
                .scheme(uri.getScheme())
                .authority(uri.getAuthority())
                .path(uri.getPath())
                .build()
                .toString();
    }

    /** Whether the exact file named by a recorded URI or path opens; directories are never searched. */
    public static boolean exists(@NonNull Context context, @NonNull String uriOrPath) {
        try {
            if (uriOrPath.startsWith("content://")) {
                DocumentFile doc = DocumentFile.fromSingleUri(context, Uri.parse(uriOrPath));
                return doc != null && doc.exists();
            }
            String path = uriOrPath.startsWith("file://") ? Uri.parse(uriOrPath).getPath() : uriOrPath;
            return path != null && new File(path).isFile();
        } catch (Exception e) {
            return false;
        }
    }

    /** The row's own playable local copy: its recorded export, else a local url_audio (imported books); or null. */
    @Nullable
    public static String localCopyOf(@NonNull Context context, @NonNull BooksAudioEntity row) {
        if (row.localUri != null && !row.localUri.isEmpty() && exists(context, row.localUri)) {
            return row.localUri;
        }
        if (row.urlAudio != null && !row.urlAudio.isEmpty() && !DeletionPlanner.isRemote(row.urlAudio)
                && exists(context, row.urlAudio)) {
            return row.urlAudio;
        }
        return null;
    }

    /** Receives, on the main thread, how many of the chosen chapters were fully deleted. */
    public interface DeleteCallback {
        void onDeleted(int deleted, int total);
    }

    /**
     * Deletes the downloaded copies of the chosen chapters of one book on the storage worker. A chapter is chosen
     * when its url_audio or its recorded local_uri is in {@code chapterUrls}. Online chapters stay in the list;
     * chapters of imported books leave the library while their files stay. When nothing failed and no chapter keeps a
     * copy or a download in any state (queued, downloading, stopped, failed or completed) the book leaves the Saved
     * list. A chapter whose download another kept chapter shares counts as kept, not deleted.
     */
    public static void deleteChapters(@NonNull Context context, @NonNull String bookUrl,
                                      @NonNull Collection<String> chapterUrls, @Nullable DeleteCallback callback) {
        final Context app = context.getApplicationContext();
        final Set<String> chosenUrls = new HashSet<>(chapterUrls);
        STORAGE.execute(() -> {
            int deleted = 0;
            int total = 0;
            try {
                AudioListDBModel model = new AudioListDBModel(app);
                List<BooksAudioEntity> bookRows = model.getRows(bookUrl);
                List<BooksAudioEntity> chosen = new ArrayList<>();
                for (BooksAudioEntity row : bookRows) {
                    if (chosenUrls.contains(row.urlAudio) || (row.localUri != null && chosenUrls.contains(row.localUri))) {
                        chosen.add(row);
                    }
                }
                total = chosen.size();
                Set<String> removedIds = new HashSet<>();
                Outcome outcome = applyPlan(app, model, bookUrl, bookRows, chosen, true, removedIds);
                deleted = outcome.deleted;
                if (total > 0 && outcome.failed == 0 && !anyStillNeeded(app, bookUrl, model.getRows(bookUrl), removedIds)) {
                    model.remove(bookUrl);
                    new BooksDBModel(app).removeSavedByUrl(bookUrl);
                }
            } catch (Exception e) {
                android.util.Log.e("DownloadStorageHelper", "Error deleting chapters", e);
            }
            app.sendBroadcast(new Intent(BookActivity.Broadcast_UPDATE_ADAPTER).setPackage(app.getPackageName()));
            if (callback != null) {
                final int finalDeleted = deleted;
                final int finalTotal = total;
                new android.os.Handler(android.os.Looper.getMainLooper())
                        .post(() -> callback.onDeleted(finalDeleted, finalTotal));
            }
        });
    }

    /** Receives, on the main thread, how many books left the library and how many chapters could not be deleted. */
    public interface RemoveCallback {
        void onRemoved(int removedBooks, int totalBooks, int failedChapters);
    }

    public static void removeBooks(@NonNull Context context, @NonNull Collection<String> bookUrls) {
        removeBooks(context, bookUrls, null);
    }

    /**
     * Removes books from the library on the storage worker: their Media3 downloads, the copies recorded in
     * local_uri and their rows. Files the app did not write (imported folders) are never touched. A book of which a
     * chapter could not be deleted stays saved, with the rows (and recorded copies) of those chapters, so the removal
     * can be retried; {@code callback} reports the counts.
     */
    public static void removeBooks(@NonNull Context context, @NonNull Collection<String> bookUrls,
                                   @Nullable RemoveCallback callback) {
        final Context app = context.getApplicationContext();
        final List<String> urls = new ArrayList<>(bookUrls);
        STORAGE.execute(() -> {
            AudioListDBModel model = new AudioListDBModel(app);
            BooksDBModel books = new BooksDBModel(app);
            int removed = 0;
            int failedChapters = 0;
            for (String url : urls) {
                try {
                    List<BooksAudioEntity> rows = model.getRows(url);
                    Outcome outcome = applyPlan(app, model, url, rows, rows, false, new HashSet<>());
                    failedChapters += outcome.failed;
                    if (outcome.failed == 0) {
                        books.removeSavedByUrl(url);
                        removed++;
                    }
                    Intent cancel = new Intent(app, DownloadActionReceiver.class);
                    cancel.setAction("CANCEL");
                    cancel.setPackage(app.getPackageName());
                    cancel.putExtra("id", url);
                    app.sendBroadcast(cancel);
                } catch (Exception e) {
                    failedChapters++;
                    android.util.Log.e("DownloadStorageHelper", "Error removing book " + url, e);
                }
            }
            app.sendBroadcast(new Intent(BookActivity.Broadcast_UPDATE_ADAPTER).setPackage(app.getPackageName()));
            if (callback != null) {
                final int removedBooks = removed;
                final int failed = failedChapters;
                new android.os.Handler(android.os.Looper.getMainLooper())
                        .post(() -> callback.onRemoved(removedBooks, urls.size(), failed));
            }
        });
    }

    /** What a deletion did to its chosen rows. */
    static final class Outcome {
        /** Copies and downloads gone. */
        int deleted;
        /** Nothing failed, but a download the chapter owns stays for a kept chapter that shares it. */
        int kept;
        /** A download or a copy could not be removed: the row and its local_uri stay. */
        int failed;
    }

    /**
     * Applies the deletion plan of {@code chosen} (rows of {@code bookRows}): removes their Media3 downloads, except a
     * download that a row which is not chosen owns ({@link DownloadIds#ownedIds}: a shared HLS stream, never a legacy
     * download another chapter's URL produced), deletes exactly their recorded copies, then removes the rows, or
     * clears their local_uri, only as far as that succeeded.
     */
    @androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
    @NonNull
    private static Outcome applyPlan(@NonNull Context context, @NonNull AudioListDBModel model, @NonNull String bookUrl,
                                     @NonNull List<BooksAudioEntity> bookRows, @NonNull List<BooksAudioEntity> chosen,
                                     boolean keepOnlineRows, @NonNull Set<String> removedIds) {
        List<DownloadIds.Entry> entries = DownloadUtil.entriesOf(bookUrl, bookRows);
        DownloadIndex index = null;
        java.util.Map<String, String> stored = null;
        try {
            index = DownloadUtil.getDownloadManager().getDownloadIndex();
            stored = DownloadUtil.storedRequests(index, entries);
        } catch (Exception e) {
            android.util.Log.w("DownloadStorageHelper", "Download index unreadable", e);
        }
        Set<Integer> chosenIds = new HashSet<>();
        for (BooksAudioEntity row : chosen) chosenIds.add(row.id);
        List<DeletionPlanner.Row> planRows = new ArrayList<>();
        // the chosen rows themselves: chapters of one stream share their URL, so a row is updated by its id
        List<BooksAudioEntity> planEntities = new ArrayList<>();
        List<List<String>> owned = new ArrayList<>();
        Set<String> keptIds = new HashSet<>();
        for (int i = 0; i < bookRows.size(); i++) {
            BooksAudioEntity row = bookRows.get(i);
            List<String> ids = DownloadUtil.idsOf(entries.get(i));
            // Without a readable index every id a row may use counts as its own, so shared downloads stay.
            List<String> own = !DeletionPlanner.isRemote(row.urlAudio) ? Collections.<String>emptyList()
                    : stored != null ? DownloadIds.ownedIds(entries.get(i), row.urlAudio, stored) : ids;
            if (chosenIds.contains(row.id)) {
                planRows.add(new DeletionPlanner.Row(row.urlBook, row.urlAudio, row.localUri, ids));
                planEntities.add(row);
                owned.add(own);
            } else {
                keptIds.addAll(own);
            }
        }
        // Another saved entry of the same book under another URL (alias) owns the same ids: never remove those.
        for (java.util.Map.Entry<String, List<BooksAudioEntity>> alias : model.getAliasRows(bookUrl).entrySet()) {
            for (DownloadIds.Entry entry : DownloadUtil.entriesOf(alias.getKey(), alias.getValue())) {
                keptIds.addAll(DownloadUtil.idsOf(entry));
            }
        }
        List<String> roots = downloadRoots(context);
        DeletionPlanner.Plan plan = DeletionPlanner.plan(planRows, roots, keepOnlineRows,
                DownloadStorageHelper::downloadIdOf, keptIds);

        Set<String> failed = new HashSet<>();
        for (String id : plan.downloadIdsToRemove) {
            try {
                if (index == null || index.getDownload(id) != null) {
                    DownloadService.sendRemoveDownload(context, ExoDownloadService.class, id, false);
                }
                removedIds.add(id);
            } catch (Exception e) {
                failed.add(id);
                android.util.Log.w("DownloadStorageHelper", "Error removing download " + id, e);
            }
        }
        failed.addAll(deleteRecordedUris(context, plan.urisToDelete, roots));

        Set<DeletionPlanner.Row> toRemove = new HashSet<>(plan.rowsToRemove);
        Set<DeletionPlanner.Row> toClear = new HashSet<>(plan.rowsToClearLocalUri);
        Outcome outcome = new Outcome();
        try {
            model.getDatabase().runInTransaction(() -> {
                for (int k = 0; k < planRows.size(); k++) {
                    DeletionPlanner.Row row = planRows.get(k);
                    boolean copyPlanned = plan.urisToDelete.contains(row.localUri);
                    boolean copyFailed = copyPlanned && failed.contains(row.localUri);
                    boolean idFailed = false;
                    for (String id : DeletionPlanner.idsOf(row, DownloadStorageHelper::downloadIdOf)) {
                        idFailed |= failed.contains(id);
                    }
                    if (toRemove.contains(row) && !copyFailed && !idFailed) {
                        model.getDatabase().booksAudioDao().deleteById(planEntities.get(k).id);
                    } else if (copyPlanned && !copyFailed && (toRemove.contains(row) || toClear.contains(row))) {
                        // The copy is gone even when the row stays for a download that could not be removed.
                        model.getDatabase().booksAudioDao().updateLocalUriById(planEntities.get(k).id, null);
                    }
                    boolean shared = false;
                    for (String id : owned.get(k)) shared |= keptIds.contains(id);
                    if (copyFailed || idFailed) outcome.failed++;
                    else if (shared) outcome.kept++;
                    else outcome.deleted++;
                }
            });
        } catch (Exception e) {
            android.util.Log.e("DownloadStorageHelper", "Error updating deleted chapters", e);
            Outcome none = new Outcome();
            none.failed = planRows.size();
            return none;
        }
        return outcome;
    }

    /**
     * True while a row of the book still has a local copy, or a download in the index that this deletion did not
     * remove ({@link #stillNeeded}); also when the index cannot be read.
     */
    @androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
    private static boolean anyStillNeeded(@NonNull Context context, @NonNull String bookUrl,
                                          @NonNull List<BooksAudioEntity> rows, @NonNull Set<String> removedIds) {
        DownloadIndex index;
        try {
            index = DownloadUtil.getDownloadManager().getDownloadIndex();
        } catch (Exception e) {
            return true;
        }
        java.util.Map<String, Integer> downloads = new java.util.HashMap<>();
        List<DownloadIds.Entry> entries = DownloadUtil.entriesOf(bookUrl, rows);
        for (int i = 0; i < rows.size(); i++) {
            BooksAudioEntity row = rows.get(i);
            if (localCopyOf(context, row) != null) return true;
            if (DeletionPlanner.isRemote(row.urlAudio)) {
                Download download = DownloadUtil.find(index, entries.get(i), row.urlAudio);
                if (download != null) downloads.put(download.request.id, download.state);
            }
        }
        return stillNeeded(downloads, removedIds);
    }

    /**
     * Whether a book's rows are still needed, given the downloads its chapters have in the index (id to state): any
     * one in a state but removing (queued, downloading, stopped, failed or completed) that this deletion did not
     * remove keeps them, so a pending download never loses its chapter row.
     */
    @OptIn(markerClass = UnstableApi.class)
    static boolean stillNeeded(
@NonNull java.util.Map<String, Integer> downloads, @NonNull Set<String> removedIds) {
        for (java.util.Map.Entry<String, Integer> d : downloads.entrySet()) {
            if (d.getValue() != Download.STATE_REMOVING && !removedIds.contains(d.getKey())) return true;
        }
        return false;
    }

    /** Roots the exporter writes below (and the selected SAF tree); folders are pruned only strictly inside them. */
    @NonNull
    private static List<String> downloadRoots(@NonNull Context context) {
        List<String> roots = new ArrayList<>();
        roots.add(getDownloadPath(context));
        String defaultPath = getDefaultPath(context);
        if (!roots.contains(defaultPath)) roots.add(defaultPath);
        String tree = getSavedUriString(context);
        if (tree != null && !tree.isEmpty()) roots.add(tree);
        return roots;
    }

    /**
     * Deletes exactly the given recorded copies (content:// through SAF, file:// and plain paths through File), then
     * prunes the folders above each one that are now empty: at most the source/book/reader folders, never a
     * configured root nor a folder above one ({@link DeletionPlanner#prunableDepth}). Returns the URIs still present.
     */
    @NonNull
    public static Set<String> deleteRecordedUris(@NonNull Context context, @NonNull Collection<String> uris,
                                                 @NonNull Collection<String> roots) {
        Set<String> failed = new HashSet<>();
        for (String uri : uris) {
            try {
                if (!deleteRecordedUri(context, uri, roots)) failed.add(uri);
            } catch (Exception e) {
                failed.add(uri);
                android.util.Log.w("DownloadStorageHelper", "Error deleting " + uri, e);
            }
        }
        return failed;
    }

    private static boolean deleteRecordedUri(@NonNull Context context, @NonNull String uri,
                                             @NonNull Collection<String> roots) {
        if (uri.startsWith("content://")) {
            Uri docUri = Uri.parse(uri);
            List<String> folders = safFoldersAbove(context, docUri);
            DocumentFile doc = DocumentFile.fromSingleUri(context, docUri);
            boolean gone = doc == null || doc.delete() || !doc.exists();
            if (gone) pruneEmptySafFolders(context, docUri, folders, rootPaths(context, roots));
            return gone;
        }
        File file = new File(uri.startsWith("file://") ? Uri.parse(uri).getPath() : uri);
        boolean gone = file.delete() || !file.exists();
        if (gone) pruneEmptyFolders(file, rootPaths(context, roots));
        return gone;
    }

    /** The roots as file paths: tree URIs through {@link #getPathFromUri}, paths as they are. */
    @NonNull
    private static List<String> rootPaths(@NonNull Context context, @NonNull Collection<String> roots) {
        List<String> paths = new ArrayList<>();
        for (String root : roots) {
            paths.add(root.startsWith("content://") ? getPathFromUri(context, Uri.parse(root)) : root);
        }
        return paths;
    }

    /** Deletes, while each is empty, the prunable folders above the deleted {@code file} strictly below its root. */
    private static void pruneEmptyFolders(@NonNull File file, @NonNull Collection<String> roots) {
        String root = DeletionPlanner.enclosingRoot(file.getAbsolutePath(), roots);
        if (root == null) return;
        List<File> chain = new ArrayList<>();
        for (File dir = file.getParentFile(); dir != null && dir.getAbsolutePath().startsWith(root + "/");
             dir = dir.getParentFile()) {
            chain.add(0, dir);
        }
        List<String> paths = new ArrayList<>();
        for (File dir : chain) paths.add(dir.getAbsolutePath());
        int depth = DeletionPlanner.prunableDepth(paths, roots);
        for (int k = chain.size() - 1; k >= chain.size() - depth; k--) {
            File dir = chain.get(k);
            String[] children = dir.list();
            if (children == null || children.length > 0 || !dir.delete()) return;
        }
    }

    /** Document ids of the folders between the tree root (excluded) and the document (excluded), nearest last. */
    @NonNull
    private static List<String> safFoldersAbove(@NonNull Context context, @NonNull Uri docUri) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || !DocumentsContract.isTreeUri(docUri)) {
            return Collections.emptyList();
        }
        try {
            DocumentsContract.Path path = DocumentsContract.findDocumentPath(context.getContentResolver(), docUri);
            List<String> ids = path != null ? path.getPath() : null;
            if (ids == null || ids.size() < 3) return Collections.emptyList();
            return new ArrayList<>(ids.subList(1, ids.size() - 1));
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    /**
     * Deletes, nearest first and while each is empty, the {@link DeletionPlanner#prunableDepth} of {@code folders},
     * compared with {@code rootPaths} by their file paths. SAF deletes folders recursively, so a folder is deleted
     * only after a query shows it has no children.
     */
    private static void pruneEmptySafFolders(@NonNull Context context, @NonNull Uri docUri, @NonNull List<String> folders,
                                             @NonNull List<String> rootPaths) {
        List<String> paths = new ArrayList<>();
        for (String id : folders) paths.add(pathOfDocumentId(id));
        int depth = DeletionPlanner.prunableDepth(paths, rootPaths);
        android.content.ContentResolver resolver = context.getContentResolver();
        for (int k = folders.size() - 1; k >= folders.size() - depth; k--) {
            Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(docUri, folders.get(k));
            int count;
            try (android.database.Cursor cursor = resolver.query(children,
                    new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID}, null, null, null)) {
                count = cursor != null ? cursor.getCount() : -1;
            } catch (Exception e) {
                return;
            }
            try {
                if (count != 0 || !DocumentsContract.deleteDocument(resolver,
                        DocumentsContract.buildDocumentUriUsingTree(docUri, folders.get(k)))) {
                    return;
                }
            } catch (Exception e) {
                return;
            }
        }
    }
}
