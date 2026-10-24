package com.fanok.audiobooks.model;


import androidx.annotation.OptIn;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.offline.Download;
import androidx.media3.exoplayer.offline.DownloadCursor;
import androidx.media3.exoplayer.offline.DownloadIndex;

import com.fanok.audiobooks.download.DownloadIds;
import com.fanok.audiobooks.download.DownloadMonitor;
import com.fanok.audiobooks.download.DownloadStatus;
import com.fanok.audiobooks.pojo.BookPOJO;
import com.fanok.audiobooks.pojo.DownloadItem;
import com.fanok.audiobooks.policy.ParentalControl;
import com.fanok.audiobooks.room.BooksAudioEntity;
import com.fanok.audiobooks.util.DeletionPlanner;
import com.fanok.audiobooks.util.DownloadStorageHelper;
import com.fanok.audiobooks.util.DownloadUtil;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Builds the Downloads screen: one row per book, from the Saved list, the chapter rows and the Media3 index. */
public class ClearSavedModel {

    private final Context mContext;
    private final BooksDBModel mBooksDBModel;
    private final AudioListDBModel mAudioListDBModel;


    public ClearSavedModel(Context mContext) {
        this.mContext = mContext.getApplicationContext();
        mBooksDBModel = new BooksDBModel(mContext);
        mAudioListDBModel = new AudioListDBModel(mContext);
    }

    /** One book being assembled: its saved row or download data, and its downloads. */
    @OptIn(markerClass = UnstableApi.class)
    private static final class Group {
        String bookUrl;
        BookPOJO book;
        final Map<String, Download> downloads = new LinkedHashMap<>();
    }

    /**
     * A fresh immutable snapshot of every book with a download, an exported copy or a Saved row. Call off the main
     * thread; nothing shared is mutated.
     */
    @OptIn(markerClass = UnstableApi.class)
    @NonNull
    public List<DownloadItem> loadSnapshot() throws java.io.IOException {
        Map<String, Download> byId = new HashMap<>();
        Map<String, Group> groups = new LinkedHashMap<>();
        ArrayList<BookPOJO> savedBooks = mBooksDBModel.getAllSaved();
        if (savedBooks != null) {
            for (BookPOJO book : savedBooks) {
                if (book.getUrl() == null || book.getUrl().isEmpty()) continue;
                Group group = group(groups, book.getUrl());
                group.book = book;
                group.bookUrl = book.getUrl();
            }
        }
        DownloadIndex index = DownloadUtil.getDownloadManager().getDownloadIndex();
        try (DownloadCursor cursor = index.getDownloads()) {
            while (cursor.moveToNext()) {
                Download d = cursor.getDownload();
                if (d.state == Download.STATE_REMOVING) continue;
                byId.put(d.request.id, d);
                BookPOJO book = DownloadUtil.bookOf(d.request);
                String bookUrl = DownloadUtil.bookUrlOf(d.request);
                Group group = group(groups, bookUrl != null ? bookUrl : d.request.id);
                if (group.book == null) group.book = book;
                group.downloads.put(d.request.id, d);
            }
        }
        List<DownloadItem> items = new ArrayList<>();
        Map<String, Boolean> present = new HashMap<>();
        for (Group group : groups.values()) {
            if (group.book == null) group.book = mBooksDBModel.getSomewhere(group.bookUrl);
            items.add(aggregate(group, mAudioListDBModel.getRows(group.bookUrl), byId, present));
        }
        return Collections.unmodifiableList(items);
    }

    @NonNull
    private static Group group(@NonNull Map<String, Group> groups, @NonNull String bookUrl) {
        String key = DownloadIds.bookKey(bookUrl);
        Group group = groups.get(key);
        if (group == null) {
            group = new Group();
            group.bookUrl = bookUrl;
            groups.put(key, group);
        }
        return group;
    }

    /** Whether a recorded copy still exists; checked once per URI per snapshot ({@code present}). */
    private boolean copyExists(@NonNull String uri, @NonNull Map<String, Boolean> present) {
        Boolean exists = present.get(uri);
        if (exists == null) {
            exists = DownloadStorageHelper.exists(mContext, uri);
            present.put(uri, exists);
        }
        return exists;
    }

    /**
     * The book's state from all of its chapters; without chapter rows, from its downloads alone. A recorded copy
     * counts only while it exists.
     */
    @OptIn(markerClass = UnstableApi.class)
    @NonNull
    private DownloadItem aggregate(@NonNull Group g, @NonNull List<BooksAudioEntity> rows,
                                   @NonNull Map<String, Download> byId, @NonNull Map<String, Boolean> present) {
        int completed = 0;
        int selected = 0;
        int total;
        int active = 0;
        int queued = 0;
        int failed = 0;
        int stopped = 0;
        float partial = 0;
        List<Integer> exported = new ArrayList<>();
        List<Download> pending = new ArrayList<>();
        Map<String, Download> parts = new LinkedHashMap<>(); // the book's transfers, one per id
        if (!rows.isEmpty()) {
            total = rows.size();
            List<DownloadIds.Entry> entries = DownloadUtil.entriesOf(g.bookUrl, rows);
            for (int i = 0; i < rows.size(); i++) {
                BooksAudioEntity row = rows.get(i);
                boolean copy = row.localUri != null && copyExists(row.localUri, present);
                if (copy) exported.add(row.ordinal >= 0 ? row.ordinal + 1 : i + 1);
                boolean remote = DeletionPlanner.isRemote(row.urlAudio);
                Download d = remote ? DownloadUtil.find(byId, entries.get(i), row.urlAudio) : null;
                if (d != null) parts.put(d.request.id, d);
                if (copy || !remote || (d != null && d.state == Download.STATE_COMPLETED)) {
                    completed++;
                    selected++;
                } else if (d != null) {
                    selected++;
                    pending.add(d);
                }
            }
        } else {
            total = g.downloads.size();
            parts.putAll(g.downloads);
            for (Download d : g.downloads.values()) {
                selected++;
                if (d.state == Download.STATE_COMPLETED) completed++;
                else pending.add(d);
            }
        }
        for (Download d : pending) {
            switch (d.state) {
                case Download.STATE_DOWNLOADING:
                case Download.STATE_RESTARTING:
                    active++;
                    break;
                case Download.STATE_QUEUED:
                    queued++;
                    break;
                case Download.STATE_FAILED:
                    failed++;
                    break;
                case Download.STATE_STOPPED:
                    stopped++;
                    break;
                default:
                    break;
            }
            if (d.getPercentDownloaded() != C.PERCENTAGE_UNSET) partial += Math.max(0, d.getPercentDownloaded()) / 100f;
        }
        int status = failed > 0 ? Download.STATE_FAILED
                : active > 0 ? Download.STATE_DOWNLOADING
                : queued > 0 ? Download.STATE_QUEUED
                : stopped > 0 ? Download.STATE_STOPPED
                : Download.STATE_COMPLETED;
        int progress = total > 0 ? Math.max(0, Math.min(100, Math.round(100f * (completed + partial) / total))) : 0;
        BookPOJO book = g.book;
        // a blocked book's cover is not handed to the Downloads screen (the placeholder shows instead)
        String cover = book != null && ParentalControl.isAllowed(mContext, book) ? book.getPhoto() : null;
        String source = DeletionPlanner.isImportedBook(g.bookUrl) ? "Локально" : DownloadStorageHelper.getSourceName(g.bookUrl);
        List<DownloadStatus.Part> statusParts = new ArrayList<>(parts.size());
        for (Download d : parts.values()) statusParts.add(DownloadMonitor.partOf(d));
        return new DownloadItem(DownloadIds.bookKey(g.bookUrl), g.bookUrl,
                cover, book != null ? book.getName() : nameOf(rows),

                book != null ? book.getAutor() : null, book != null ? book.getArtist() : null, source,
                status, progress, completed, selected, total, new ArrayList<>(g.downloads.keySet()), exported,
                statusParts);
    }

    @Nullable
    private static String nameOf(@NonNull List<BooksAudioEntity> rows) {
        return rows.isEmpty() ? null : rows.get(0).booksName;
    }

    public void closeDB() {
        mBooksDBModel.closeDB();
        mAudioListDBModel.closeDB();
    }
}
