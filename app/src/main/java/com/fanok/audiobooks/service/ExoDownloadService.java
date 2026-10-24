package com.fanok.audiobooks.service;

import static com.fanok.audiobooks.activity.BookActivity.Broadcast_UPDATE_ADAPTER;

import android.app.Notification;
import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.offline.Download;
import androidx.media3.exoplayer.offline.DownloadIndex;
import androidx.media3.exoplayer.offline.DownloadManager;
import androidx.media3.exoplayer.offline.DownloadService;
import androidx.media3.exoplayer.scheduler.PlatformScheduler;
import androidx.media3.exoplayer.scheduler.Scheduler;
import com.fanok.audiobooks.NotificationDownload;
import com.fanok.audiobooks.download.DownloadMonitor;
import com.fanok.audiobooks.model.AudioListDBModel;
import com.fanok.audiobooks.model.BooksDBModel;
import com.fanok.audiobooks.pojo.BookPOJO;
import com.fanok.audiobooks.room.BooksAudioEntity;
import com.fanok.audiobooks.util.DownloadStorageHelper;
import com.fanok.audiobooks.util.DownloadUtil;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.jetbrains.annotations.NotNull;

/**
 * Runs the downloads of the one process-wide {@link DownloadManager}. Completed media stays in the Media3 cache, the
 * authoritative copy; exporting to the download folder is a separate step the user asks for on the Downloads screen.
 */
@OptIn(markerClass = UnstableApi.class)
public class ExoDownloadService extends DownloadService {


    public static final String CHANNEL_ID = "download_channel";
    private static final int JOB_ID = 1;
    public static final int FOREGROUND_NOTIFICATION_ID = 98;

    private DownloadManagerListener listener;


    public ExoDownloadService() {
        super(FOREGROUND_NOTIFICATION_ID, DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        listener = new DownloadManagerListener(getApplicationContext());
        getDownloadManager().addListener(listener);
    }

    @Override
    public void onDestroy() {
        getDownloadManager().removeListener(listener);
        super.onDestroy();
    }

    @NotNull
    @Override
    protected DownloadManager getDownloadManager() {
        return DownloadUtil.getDownloadManager();
    }

    @Override
    protected Scheduler getScheduler() {
        return new PlatformScheduler(this, JOB_ID);
    }

    /**
     * Called by Media3 every second on the main thread while downloads run or wait: the book of the running download
     * (else of the first one in hand) with its status line, which also says why nothing moves (network, pause,
     * failure). Downloads being deleted are no book to show; book keys and names come from the monitor's cache.
     */
    @NotNull
    @Override
    protected Notification getForegroundNotification(@NotNull List<Download> downloads, int notMetRequirements) {
        DownloadMonitor monitor = DownloadMonitor.get();
        Download current = null;
        boolean removing = false;
        Set<String> books = new HashSet<>();
        for (Download d : downloads) {
            if (d.state == Download.STATE_REMOVING) {
                removing = true;
                continue;
            }
            String key = monitor.bookKeyOf(d);
            books.add(key.isEmpty() ? d.request.id : key);
            if (current == null || (current.state != Download.STATE_DOWNLOADING && d.state == Download.STATE_DOWNLOADING)) {
                current = d;
            }
        }
        if (current == null && removing) return NotificationDownload.removing(this);
        String key = current != null ? monitor.bookKeyOf(current) : "";
        String name = monitor.bookName(key);
        if (name == null && current != null) {
            BookPOJO book = DownloadUtil.bookOf(current.request); // a book added since the monitor last read the index
            name = book != null ? book.getName() : null;
        }
        return NotificationDownload.forStatus(this, key, name, monitor.statusOfBook(key), Math.max(0, books.size() - 1));
    }

    /** Tells this app's screens that downloads changed. */
    public static void notifyChanged(@NonNull Context context) {
        context.sendBroadcast(new Intent(Broadcast_UPDATE_ADAPTER).setPackage(context.getPackageName()));
    }

    /**
     * The manager calls this on the main thread; database work runs off it: the saved-book row on the download worker,
     * row cleanup on the storage worker that also serialises exports, scans and deletions.
     */
    private static class DownloadManagerListener implements DownloadManager.Listener {
        private final Context context;

        DownloadManagerListener(Context context) {
            this.context = context;
        }

        @Override
        public void onDownloadChanged(@NonNull DownloadManager downloadManager, Download download, @Nullable Exception finalException) {
            android.util.Log.d("DL", "id=" + download.request.id
                    + " state=" + download.state
                    + " reason=" + download.failureReason
                    + " stopReason=" + download.stopReason
                    + " bytes=" + download.getBytesDownloaded());
            if (finalException != null) android.util.Log.w("DL", "err", finalException);

            if (download.state == Download.STATE_COMPLETED) {
                DownloadUtil.runInBackground(() -> {
                    BookPOJO book = DownloadUtil.bookOf(download.request);
                    if (book != null && book.getUrl() != null) {
                        new BooksDBModel(context).addSaved(book);
                    }
                    notifyChanged(context);
                });
            } else {
                notifyChanged(context);
            }
        }

        @Override
        public void onDownloadsPausedChanged(@NonNull DownloadManager downloadManager, boolean downloadsPaused) {
            notifyChanged(context);
        }

        @Override
        public void onDownloadRemoved(@NonNull DownloadManager downloadManager, Download download) {
            final DownloadIndex index = downloadManager.getDownloadIndex();
            DownloadStorageHelper.submit(() -> {
                try {
                    forgetBookIfNothingLeft(index, download);
                } catch (Exception e) {
                    android.util.Log.e("DL", "Error updating the library after a removal", e);
                }
                notifyChanged(context);
            });
        }

        /**
         * When the removed download was the last one of its book (same {@code DownloadIds.bookKey}, never a substring
         * match) and no chapter keeps an exported copy, the book leaves the Saved list with its chapter rows.
         */
        private void forgetBookIfNothingLeft(DownloadIndex index, Download download) {
            String bookUrl = DownloadUtil.bookUrlOf(download.request);
            if (bookUrl == null || bookUrl.isEmpty()) return;
            if (!DownloadUtil.downloadIdsOfBook(index, bookUrl).isEmpty()) return;
            AudioListDBModel audioListDBModel = new AudioListDBModel(context);
            for (BooksAudioEntity row : audioListDBModel.getRows(bookUrl)) {
                if (row.localUri != null) return;
            }
            new BooksDBModel(context).removeSavedByUrl(bookUrl);
            audioListDBModel.remove(bookUrl);
        }
    }
}
