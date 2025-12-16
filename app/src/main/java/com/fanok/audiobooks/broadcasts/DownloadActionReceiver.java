package com.fanok.audiobooks.broadcasts;

import android.Manifest;
import android.app.Notification;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresPermission;
import androidx.core.app.NotificationManagerCompat;
import androidx.media3.common.util.Log;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.offline.Download;
import androidx.media3.exoplayer.offline.DownloadCursor;
import androidx.media3.exoplayer.offline.DownloadIndex;
import androidx.media3.exoplayer.offline.DownloadManager;
import androidx.media3.exoplayer.offline.DownloadService;
import com.fanok.audiobooks.NotificationDownload;
import com.fanok.audiobooks.service.ExoDownloadService;
import com.fanok.audiobooks.util.DownloadUtil;
import java.io.IOException;

public class DownloadActionReceiver extends BroadcastReceiver {
    private final static int APP_STOP_REASON = 1001;
    private final static int NOTIFICATION_ID = 564;


    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    @UnstableApi
    @Override public void onReceive(Context c, Intent i) {
        String a = i.getAction();
        DownloadManager downloadManager = DownloadUtil.getDownloadManager();
        DownloadIndex idx = DownloadUtil.getDownloadManager().getDownloadIndex();

        if ("PAUSE_ALL".equals(a)) {
            String id = i.getStringExtra("id");
            int progress = 0;
            if (id!=null) {
                try {
                    Download d = idx.getDownload(id);
                    if (d != null) {
                        progress = (int) d.getPercentDownloaded();
                    }
                } catch (IOException ignored) {
                }
            }

            DownloadService.sendSetStopReason(
                    c, ExoDownloadService.class, /* contentId= */ null, APP_STOP_REASON, /* foreground= */ false);
            Notification n = NotificationDownload.getNotification(c, id, progress, false);
            NotificationManagerCompat.from(c).notify(NOTIFICATION_ID, n);

        } else if ("RESUME_ALL".equals(a)) {
            NotificationManagerCompat.from(c).cancel(NOTIFICATION_ID);
            DownloadService.sendSetStopReason(
                    c, ExoDownloadService.class, /* contentId= */ null, Download.STOP_REASON_NONE, /* foreground= */ true);
        } else if ("CANCEL_ALL".equals(a)) {
            try {
                removeIncomplete(c, DownloadUtil.getDownloadManager());
            } catch (IOException e) {
                Log.e("Downloads", "Remove incomplete failed", e);
                Toast.makeText(c, "Ошибка очистки незавершённых", Toast.LENGTH_SHORT).show();
            }

        }  else if ("PAUSE".equals(a)) {
            String id = i.getStringExtra("id");
            if (id != null) {
                DownloadService.sendSetStopReason(c, ExoDownloadService.class, id, APP_STOP_REASON, false);
                Download nextActive = findNextActiveDownload(downloadManager, id);
                if (nextActive == null) {
                    int progress = 0;
                    try {
                        Download d = idx.getDownload(id);
                        if (d != null) {
                            progress = (int) d.getPercentDownloaded();
                        }
                    } catch (IOException ignored) {
                    }
                    Notification n = NotificationDownload.getNotification(c, id, progress, true);
                    NotificationManagerCompat.from(c).notify(NOTIFICATION_ID, n);
                }
            }
        } else if ("RESUME".equals(a)) {
            String id = i.getStringExtra("id");
            if (id != null) {
                NotificationManagerCompat.from(c).cancel(NOTIFICATION_ID);
                DownloadService.sendSetStopReason(c, ExoDownloadService.class, id, Download.STOP_REASON_NONE, true);
            }
        } else if ("CANCEL".equals(a)) {String id = i.getStringExtra("id");
            if (id != null) {
                DownloadService.sendRemoveDownload(c, ExoDownloadService.class,id, false);
            }
        }else if ("RESTART".equals(a)) {
            String id = i.getStringExtra("id");
            if (id != null) {
                try {
                    // Получаем информацию о загрузке, которую нужно перезапустить
                    Download download = idx.getDownload(id);
                    if (download != null) {
                        // 1. Сначала отправляем команду на УДАЛЕНИЕ старой (упавшей) загрузки
                        DownloadService.sendRemoveDownload(c, ExoDownloadService.class, download.request.id, false);

                        // 2. Затем отправляем команду на ДОБАВЛЕНИЕ этой же загрузки заново
                        DownloadService.sendAddDownload(c, ExoDownloadService.class, download.request, false);
                    }
                } catch (IOException e) {
                    Log.e("DownloadActionReceiver", "Failed to restart download", e);
                }
            }
        }
    }

    @Nullable
    @UnstableApi
    private Download findNextActiveDownload(DownloadManager downloadManager, String excludeId) {
        try (DownloadCursor cursor = downloadManager.getDownloadIndex().getDownloads()) {
            while (cursor.moveToNext()) {
                Download download = cursor.getDownload();
                if ((download.state == Download.STATE_DOWNLOADING || download.state == Download.STATE_QUEUED)
                        && !download.request.id.equals(excludeId)) {
                    return download;
                }
            }
        } catch (IOException e) {
            Log.e("DownloadActionReceiver", "Failed to find next active download", e);
        }
        return null;
    }

    @UnstableApi
    private void removeIncomplete(Context ctx, DownloadManager dm) throws IOException {
        androidx.media3.exoplayer.offline.DownloadIndex idx = dm.getDownloadIndex();
        try (androidx.media3.exoplayer.offline.DownloadCursor c = idx.getDownloads()) {
            while (c.moveToNext()) {
                androidx.media3.exoplayer.offline.Download d = c.getDownload();
                if (d.state != androidx.media3.exoplayer.offline.Download.STATE_COMPLETED) {
                    androidx.media3.exoplayer.offline.DownloadService.sendRemoveDownload(
                            ctx, ExoDownloadService.class, d.request.id, false);
                }
            }
        }
    }
}

