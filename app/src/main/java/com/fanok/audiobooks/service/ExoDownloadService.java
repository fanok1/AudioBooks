package com.fanok.audiobooks.service;

import static com.fanok.audiobooks.activity.BookActivity.Broadcast_UPDATE_ADAPTER;

import android.app.Notification;
import android.content.Intent;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.offline.Download;
import androidx.media3.exoplayer.offline.DownloadCursor;
import androidx.media3.exoplayer.offline.DownloadIndex;
import androidx.media3.exoplayer.offline.DownloadManager;
import androidx.media3.exoplayer.offline.DownloadService;
import androidx.media3.exoplayer.scheduler.PlatformScheduler;
import androidx.media3.exoplayer.scheduler.Scheduler;
import com.fanok.audiobooks.NotificationDownload;
import com.fanok.audiobooks.model.BooksDBModel;
import com.fanok.audiobooks.pojo.BookPOJO;
import com.fanok.audiobooks.util.DownloadUtil;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.List;
import org.jetbrains.annotations.NotNull;

@UnstableApi
public class ExoDownloadService extends DownloadService {


    public static final String CHANNEL_ID = "download_channel";
    private static final int JOB_ID = 1;
    public static final int FOREGROUND_NOTIFICATION_ID = 98;

    private DownloadManager downloadManager;
    private DownloadManagerListener listener;


    public ExoDownloadService() {
        super(FOREGROUND_NOTIFICATION_ID, DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        downloadManager = getDownloadManager();
        listener = new DownloadManagerListener(this);
        downloadManager.addListener(listener);
    }

    @Override
    public void onDestroy() {
        downloadManager.removeListener(listener);
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

    @NotNull
    @Override
    protected Notification getForegroundNotification(@NotNull List<Download> downloads, int notMetRequirements) {
        Download current = downloads.stream()
                .filter(d -> d.state == Download.STATE_DOWNLOADING)
                .findFirst()
                .orElse(null);

        int progress = 0;
        if (current != null) {
            if (current.getPercentDownloaded() != C.PERCENTAGE_UNSET) {
                progress = Math.max(0, Math.min(100, Math.round(current.getPercentDownloaded())));
            } else if (current.contentLength > 0) {
                progress = (int) Math.round(100.0 * current.getBytesDownloaded() / (double) current.contentLength);
                progress = Math.max(0, Math.min(100, progress));
            }
        }

        boolean isDownloading = current != null && current.state== Download.STATE_DOWNLOADING;
        String id = null;
        if (current != null) {
            id = current.request.id;
        }
        return NotificationDownload.getNotification(this, id, progress, isDownloading);
    }

    private static class DownloadManagerListener implements DownloadManager.Listener {
        private final ExoDownloadService downloadService;

        public DownloadManagerListener(ExoDownloadService downloadService) {
            this.downloadService = downloadService;
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
                String bookJson = new String(download.request.data);
                Gson gson = new Gson();
                Type type = new TypeToken<BookPOJO>() {}.getType();
                BookPOJO book = gson.fromJson(bookJson, type);
                if (book != null) {
                    BooksDBModel dbModel = new BooksDBModel(downloadService);
                    dbModel.addSaved(book);
                    dbModel.closeDB();
                }
            }
            downloadService.sendBroadcast(new Intent(Broadcast_UPDATE_ADAPTER));
        }

        @Override
        public void onDownloadsPausedChanged(@NonNull DownloadManager downloadManager, boolean downloadsPaused) {
            downloadService.sendBroadcast(new Intent(Broadcast_UPDATE_ADAPTER));
        }

        @Override
        public void onDownloadRemoved(@NonNull DownloadManager downloadManager, Download download) throws RuntimeException {
            String bookJson = new String(download.request.data);
            Gson gson = new Gson();
            Type type = new TypeToken<BookPOJO>() {}.getType();
            BookPOJO book = gson.fromJson(bookJson, type);
            DownloadIndex downloadIndex = downloadManager.getDownloadIndex();
            boolean b = false;
            try (DownloadCursor cursor = downloadIndex.getDownloads()) {
                while (cursor.moveToNext()) {
                    Download d = cursor.getDownload();
                    Uri u = d.request.uri;
                    String ref = u.getQueryParameter("__ref");
                    if (ref != null) {
                        if (ref.contains("%2F") || ref.contains("%3A")) {
                            ref = android.net.Uri.decode(ref);
                        }
                        if (!ref.startsWith("http://") && !ref.startsWith("https://")) {
                            ref = "https://" + ref;
                        }
                        if (ref.contains(book.getUrl())) {
                            b = true;
                            break;
                        }
                    }

                }
            } catch (IOException ignored) {
            }
            if (book != null&&!b) {
                BooksDBModel dbModel = new BooksDBModel(downloadService);
                dbModel.removeSaved(book);
                dbModel.closeDB();
            }
            downloadService.sendBroadcast(new Intent(Broadcast_UPDATE_ADAPTER));
        }
    }
}
