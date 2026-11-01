package com.fanok.audiobooks;

import static com.fanok.audiobooks.service.ExoDownloadService.CHANNEL_ID;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.annotation.OptIn;
import androidx.media3.common.util.UnstableApi;

import com.fanok.audiobooks.activity.ClearSavedActivity;
import com.fanok.audiobooks.broadcasts.DownloadActionReceiver;
import com.fanok.audiobooks.download.DownloadStatus;
import com.fanok.audiobooks.download.DownloadStatusText;

/** The download notification: the book being downloaded and its {@link DownloadStatus} line, or why it waits. */
public class NotificationDownload {

    /** The dismissible notification left once the download service stopped: why the book rests. */
    public static final int RESTING_ID = 564;

    private static final int ACTION_PAUSE = 1;
    private static final int ACTION_RESUME = 2;
    private static final int ACTION_CANCEL = 4;

    /**
     * The service's notification for the book whose download runs or waits ({@code bookKey}, see
     * {@code DownloadMonitor.keyOf}): its name as the title, the status line as the text, a determinate bar;
     * {@code otherBooks} more books are queued behind it. Отмена removes that book's unfinished downloads only;
     * pause and resume act on every book, and say so when others are there.
     */
    public static Notification forStatus(@NonNull Context context, @NonNull String bookKey, @Nullable String bookName,
                                         @NonNull DownloadStatus status, int otherBooks) {
        return forStatus(context, bookKey, bookName, status, otherBooks, true);
    }

    /** The same once the service stopped (paused, waiting for the network, failed): dismissible. */
    public static Notification resting(@NonNull Context context, @NonNull String bookKey, @Nullable String bookName,
                                       @NonNull DownloadStatus status, int otherBooks) {
        return forStatus(context, bookKey, bookName, status, otherBooks, false);
    }

    /** The service's notification while only deletions run: nothing to pause or resume. */
    public static Notification removing(@NonNull Context context) {
        return build(context, context.getString(R.string.download_notification_removing), "", -1, true, 0, "", false);
    }

    private static Notification forStatus(@NonNull Context context, @NonNull String bookKey,
                                          @Nullable String bookName, @NonNull DownloadStatus status, int otherBooks,
                                          boolean ongoing) {
        String title;
        int actions;
        switch (status.state) {
            case DownloadStatus.PAUSED:
                title = context.getString(R.string.download_notification_paused);
                actions = ACTION_RESUME | ACTION_CANCEL;
                break;
            case DownloadStatus.WAITING_NETWORK:
            case DownloadStatus.QUEUED:
                title = context.getString(R.string.download_notification_waiting);
                actions = ACTION_PAUSE | ACTION_CANCEL;
                break;
            case DownloadStatus.FAILED:
                // restarting is per book on the Downloads screen, which the notification opens
                title = context.getString(R.string.download_notification_failed);
                actions = 0;
                break;
            default:
                title = context.getString(R.string.download_notification_title);
                actions = ACTION_PAUSE | ACTION_CANCEL;
                break;
        }
        if (bookName != null && !bookName.isEmpty()) title = title + ": " + bookName;
        String text = DownloadStatusText.line(context, status);
        if (otherBooks > 0) {
            text = (text.isEmpty() ? "" : text + " · ")
                    + context.getResources().getQuantityString(R.plurals.download_more_books, otherBooks, otherBooks);
        }
        return build(context, title, text, status.percent(), ongoing, actions, bookKey, otherBooks > 0);
    }

    /** {@code progress} below 0 shows an indeterminate bar; {@code all}: pause and resume reach other books too. */
    @OptIn(markerClass = UnstableApi.class)
    private static Notification build(Context context, @NonNull String title, @NonNull String contentText,
                                      int progress, boolean ongoing, int actions, @NonNull String bookKey,
                                      boolean all) {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = context.getSystemService(NotificationManager.class);
            if (nm != null) {
                NotificationChannel ch = nm.getNotificationChannel(CHANNEL_ID);
                if (ch == null) {
                    ch = new NotificationChannel(CHANNEL_ID, "Загрузки книг", NotificationManager.IMPORTANCE_LOW);
                    ch.setDescription("Прогресс загрузки аудиокниг");
                    ch.setShowBadge(true);
                    ch.setSound(null, null);
                    ch.enableVibration(false);
                    nm.createNotificationChannel(ch);
                } else {
                    ch.setSound(null, null);
                    ch.enableVibration(false);
                }
            }
        }

        NotificationCompat.Builder b = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_download)
                .setOnlyAlertOnce(true)
                .setOngoing(ongoing)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setSound(null)
                .setVibrate(new long[]{0L})
                .setContentTitle(title)
                .setContentText(contentText)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(contentText))
                .setProgress(100, Math.max(0, Math.min(100, progress)), progress < 0)
                .setContentIntent(PendingIntent.getActivity(context, 0, new Intent(context, ClearSavedActivity.class), PendingIntent.FLAG_IMMUTABLE));

        if ((actions & ACTION_PAUSE) != 0) {
            b.addAction(0, context.getString(all ? R.string.download_notification_pause_all
                    : R.string.download_notification_pause), action(context, 1, "PAUSE_ALL"));
        }
        if ((actions & ACTION_RESUME) != 0) {
            b.addAction(0, context.getString(all ? R.string.download_notification_resume_all
                    : R.string.download_notification_resume), action(context, 3, "RESUME_ALL"));
        }
        if ((actions & ACTION_CANCEL) != 0) {
            b.addAction(0, context.getString(R.string.download_notification_cancel), cancel(context, bookKey));
        }

        return b.build();
    }

    private static PendingIntent action(Context context, int requestCode, String action) {
        return PendingIntent.getBroadcast(context, requestCode,
                new Intent(context, DownloadActionReceiver.class).setAction(action), PendingIntent.FLAG_IMMUTABLE);
    }

    /**
     * Отмена of the book in the title ("" when unknown: every book). Its own request code, and the extra refreshed:
     * an existing immutable PendingIntent would otherwise keep another book's key.
     */
    private static PendingIntent cancel(Context context, @NonNull String bookKey) {
        Intent intent = new Intent(context, DownloadActionReceiver.class).setAction("CANCEL_ALL");
        if (!bookKey.isEmpty()) intent.putExtra(DownloadActionReceiver.EXTRA_ID, bookKey);
        return PendingIntent.getBroadcast(context, bookKey.hashCode(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
