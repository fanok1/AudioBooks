package com.fanok.audiobooks.broadcasts;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresPermission;
import androidx.media3.common.util.Log;
import androidx.annotation.OptIn;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.offline.Download;
import androidx.media3.exoplayer.offline.DownloadCursor;
import androidx.media3.exoplayer.offline.DownloadIndex;
import androidx.media3.exoplayer.offline.DownloadService;
import com.fanok.audiobooks.R;
import com.fanok.audiobooks.download.DownloadMonitor;
import com.fanok.audiobooks.service.ExoDownloadService;
import com.fanok.audiobooks.util.DownloadUtil;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Download actions from the notification and the Downloads screen. "id" names one download or, for CANCEL, a book
 * (its URL; for CANCEL_ALL its {@code DownloadMonitor.keyOf} key);
 * "ids" names several downloads (all chapters of a book row). The work runs on the download worker, never on main.
 */
public class DownloadActionReceiver extends BroadcastReceiver {
    private final static int APP_STOP_REASON = 1001;
    public static final String EXTRA_ID = "id";
    public static final String EXTRA_IDS = "ids";


    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    @OptIn(markerClass = UnstableApi.class)
    @Override public void onReceive(Context c, Intent i) {
        final PendingResult result = goAsync();
        final Context app = c.getApplicationContext();
        final Intent intent = new Intent(i);
        DownloadUtil.runInBackground(() -> {
            try {
                handle(app, intent);
            } catch (Exception e) {
                Log.e("DownloadActionReceiver", "Download action failed: " + intent.getAction(), e);
            } finally {
                result.finish();
            }
        });
    }

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    @OptIn(markerClass = UnstableApi.class)
    private void handle(Context c, Intent i) throws IOException {
        String a = i.getAction();
        DownloadIndex idx = DownloadUtil.getDownloadManager().getDownloadIndex();
        String id = i.getStringExtra(EXTRA_ID);
        List<String> ids = idsOf(i);

        // No notification is touched here: DownloadMonitor owns the one left once the service stops and reconciles it
        // with what each action changed ("paused", "waiting", "failed" for the book it names, else none).
        if ("PAUSE_ALL".equals(a)) {
            DownloadService.sendSetStopReason(
                    c, ExoDownloadService.class, /* contentId= */ null, APP_STOP_REASON, /* foreground= */ false);

        } else if ("RESUME_ALL".equals(a)) {
            DownloadService.sendSetStopReason(
                    c, ExoDownloadService.class, /* contentId= */ null, Download.STOP_REASON_NONE, /* foreground= */ true);
        } else if ("CANCEL_ALL".equals(a)) {
            // the notification's Отмена: the unfinished downloads of the book it names ("id", a book key), else of all
            try {
                removeIncomplete(c, idx, id);
            } catch (IOException e) {
                Log.e("Downloads", "Remove incomplete failed", e);
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() ->
                        Toast.makeText(c, R.string.download_cancel_failed, Toast.LENGTH_SHORT).show());
            }

        }  else if ("PAUSE".equals(a)) {
            for (String one : ids) {
                DownloadService.sendSetStopReason(c, ExoDownloadService.class, one, APP_STOP_REASON, false);
            }
        } else if ("RESUME".equals(a)) {
            for (String one : ids) {
                DownloadService.sendSetStopReason(c, ExoDownloadService.class, one, Download.STOP_REASON_NONE, true);
            }
        } else if ("CANCEL".equals(a)) {
            // A book URL removes every download of that book; a download id removes that download.
            Set<String> remove = new LinkedHashSet<>(ids);
            if (id != null) remove.addAll(DownloadUtil.downloadIdsOfBook(idx, id));
            for (String one : remove) {
                DownloadService.sendRemoveDownload(c, ExoDownloadService.class, one, false);
            }
        } else if ("RESTART".equals(a)) {
            // Re-adding a failed download queues it again and keeps the bytes already in the cache.
            for (String one : ids) {
                Download download = idx.getDownload(one);
                if (download != null && download.state == Download.STATE_FAILED) {
                    DownloadService.sendAddDownload(c, ExoDownloadService.class, download.request, false);
                }
            }
        }
    }

    private static List<String> idsOf(Intent i) {
        List<String> ids = new ArrayList<>();
        String[] many = i.getStringArrayExtra(EXTRA_IDS);
        if (many != null) ids.addAll(Arrays.asList(many));
        String one = i.getStringExtra(EXTRA_ID);
        if (one != null && !ids.contains(one)) ids.add(one);
        return ids;
    }

    /** Removes the downloads not completed, of book {@code bookKey} only when given; completed chapters stay. */
    @OptIn(markerClass = UnstableApi.class)
    private void removeIncomplete(Context ctx, DownloadIndex idx, @Nullable String bookKey) throws IOException {
        boolean oneBook = bookKey != null && !bookKey.isEmpty();
        try (DownloadCursor c = idx.getDownloads()) {
            while (c.moveToNext()) {
                Download d = c.getDownload();
                if (d.state == Download.STATE_COMPLETED) continue;
                if (oneBook && !bookKey.equals(DownloadMonitor.keyOf(d.request))) continue;
                DownloadService.sendRemoveDownload(ctx, ExoDownloadService.class, d.request.id, false);
            }
        }
    }
}
