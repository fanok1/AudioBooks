package com.fanok.audiobooks;

import static com.fanok.audiobooks.service.ExoDownloadService.CHANNEL_ID;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.media3.common.util.UnstableApi;

import com.fanok.audiobooks.activity.ClearSavedActivity;
import com.fanok.audiobooks.broadcasts.DownloadActionReceiver;

public class NotificationDownload {

    @UnstableApi
    public static Notification getNotification(Context context, @Nullable String id, int progress, boolean stateDownload) {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = context.getSystemService(NotificationManager.class);
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "Загрузки", NotificationManager.IMPORTANCE_LOW);
                ch.setDescription("Прогресс загрузок");
                ch.setShowBadge(false);
                nm.createNotificationChannel(ch);
            }
        }

        Intent intent = new Intent(context, DownloadActionReceiver.class).setAction("PAUSE_ALL");
        intent.putExtra("id", id);
        PendingIntent pausePi = PendingIntent.getBroadcast(
                context, 1, intent,
                PendingIntent.FLAG_IMMUTABLE);


        PendingIntent cancelPi = PendingIntent.getBroadcast(
                context, 2, new Intent(context, DownloadActionReceiver.class)
                        .setAction("CANCEL_ALL"),
                PendingIntent.FLAG_IMMUTABLE);

        PendingIntent resumePi = PendingIntent.getBroadcast(
                context, 3, new Intent(context, DownloadActionReceiver.class).setAction("RESUME_ALL"),
                PendingIntent.FLAG_IMMUTABLE);



        String title;
        boolean indeterminate = progress <= 0;

        NotificationCompat.Builder b = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_download)
                .setOnlyAlertOnce(true)
                .setContentIntent(PendingIntent.getActivity(context, 0, new Intent(context, ClearSavedActivity.class), PendingIntent.FLAG_IMMUTABLE))
                .setProgress(100, progress, indeterminate);

        if (id != null) b.setContentText(id);
        if (stateDownload){
            b.addAction(0, "Пауза", pausePi);
            b.setOngoing(true);
            title = "Загрузка " + progress + "/100" ;
        }else {
            b.addAction(0, "Возобновить", resumePi);
            b.setOngoing(false);
            title = "Пауза " + progress + "/100" ;
        }
        b.addAction(0, "Отмена", cancelPi);
        b.setContentTitle(title);
        return b.build();

    }

}
