package com.fanok.audiobooks.service;

import android.app.DownloadManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.downloader.Error;
import com.downloader.OnDownloadListener;
import com.downloader.PRDownloader;
import com.downloader.PRDownloaderConfig;
import com.fanok.audiobooks.R;
import com.fanok.audiobooks.broadcasts.OnNotificationButtonClick;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

public class Download extends Service {

    public static final String ACTION_RESUME = "audioBook.download.ACTION_PLAY";
    public static final String ACTION_PAUSE = "audioBook.download.ACTION_PAUSE";
    public static final String ACTION_STOP = "audioBook.download.ACTION_STOP";
    private static final String TAG = "Download";
    private static final int notificationId = 487;
    private static final String chanalId = "574";
    private static final String chanalName = "Download";
    private ArrayList<String> mList;
    private ArrayList<String> dirName;
    private String path;
    private int mProgress;
    private int downloadId;
    private PendingIntent notificationClickIntent;
    private boolean pause;

    @Override
    public void onCreate() {
        super.onCreate();
        mList = new ArrayList<>();
        dirName = new ArrayList<>();
        path = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS).getPath();
        /*SharedPreferences pref = PreferenceManager
                .getDefaultSharedPreferences(this);
        path = pref.getString("pref_dowland_path", "");
        if (path.isEmpty()) {
            path = new ContextWrapper(this).getFilesDir().toString();
        }*/


        Intent resultIntent = new Intent(DownloadManager.ACTION_VIEW_DOWNLOADS);
        notificationClickIntent = PendingIntent.getActivity(this, 0, resultIntent,
                PendingIntent.FLAG_UPDATE_CURRENT);

    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String bookName = intent.getStringExtra("fileName");
            String url = intent.getStringExtra("url");
            if (url != null && !mList.contains(url)) {
                boolean start = mList.isEmpty();
                mList.add(url);
                if (bookName != null) {
                    dirName.add(bookName);
                } else {
                    dirName.add("");
                }
                if (start) start();
            } else {
                Log.d(TAG, "onHandleIntent: url is null");
            }
        } else {
            Log.d(TAG, "onHandleIntent: intent is null");
        }
        return super.onStartCommand(intent, flags, startId);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }


    private void start() {
        if (!mList.isEmpty()) {
            NotificationManager notificationManager =
                    (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && notificationManager != null) {
                NotificationChannel channel = new NotificationChannel(chanalId, chanalName,
                        NotificationManager.IMPORTANCE_LOW);
                channel.enableVibration(true);
                channel.setLightColor(Color.BLUE);
                channel.enableLights(true);
                channel.setShowBadge(true);
                notificationManager.createNotificationChannel(channel);
            }
            NotificationCompat.Builder builder =
                    new NotificationCompat.Builder(getApplicationContext(), chanalId)
                            .setSmallIcon(R.drawable.ic_launcher_foreground)
                            .setGroup("GroupDownload")
                            .setContentTitle(getString(R.string.loading));
            startForeground(notificationId, builder.build());
            download(0);
        } else {
            Log.d(TAG, "start: mList is empty");
        }
    }

    private void download(int postion) {
        String urlPath = mList.get(postion);
        String fileName = urlPath.substring(urlPath.lastIndexOf("/") + 1);

        PRDownloaderConfig config = PRDownloaderConfig.newBuilder()
                .setReadTimeout(30_000)
                .setConnectTimeout(30_000)
                .build();
        PRDownloader.initialize(getApplicationContext(), config);
        downloadId = PRDownloader.download(urlPath, path + "/" + dirName.get(postion), fileName)
                .build()
                .setOnStartOrResumeListener(() -> {
                    pause = false;
                    showNotification(postion, mProgress);
                })
                .setOnPauseListener(() -> {
                    pause = true;
                    showNotification(postion, mProgress);
                })
                .setOnCancelListener(() -> {
                    stopForeground(true);
                    stopSelf();
                })
                .setOnProgressListener(progress -> {
                    int progessProcent = (int) (progress.currentBytes * 100 / progress.totalBytes);
                    if (progessProcent % 5 == 0 && progessProcent != mProgress) {
                        mProgress = progessProcent;
                        showNotification(postion, mProgress);
                    }
                })
                .start(new OnDownloadListener() {
                    @Override
                    public void onDownloadComplete() {
                        downloadNext(postion + 1);
                    }

                    @Override
                    public void onError(Error error) {
                        Toast.makeText(Download.this,
                                getString(R.string.error_load_file) + " " + fileName,
                                Toast.LENGTH_SHORT).show();
                        downloadNext(postion + 1);
                    }

                });


    }

    private void downloadNext(int positionNext) {
        if (positionNext == mList.size()) {
            stopForeground(false);
            showNotification(positionNext, 0);
            Toast.makeText(this, R.string.loading_completed, Toast.LENGTH_SHORT).show();
            stopSelf();
        } else {
            download(positionNext);
        }
    }

    private void showNotification(int postion, int progress) {
        NotificationManager notificationManager =
                (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(getApplicationContext(), chanalId)
                        .setSmallIcon(R.drawable.ic_launcher_foreground)
                        .setGroup("GroupDownload")
                        .setContentTitle(getString(R.string.loading))
                        .setContentIntent(notificationClickIntent);
        if (postion < mList.size()) {
            builder.setContentText(postion + " " + getString(R.string.of) + " " + mList.size())
                    .setProgress(100, progress, false);
            if (!pause) {
                builder.addAction(R.drawable.ic_pause, getString(R.string.pause),
                        playbackAction(ACTION_PAUSE));
            } else {
                builder.addAction(R.drawable.ic_play, getString(R.string.play),
                        playbackAction(ACTION_RESUME));
            }
            builder.addAction(R.drawable.ic_stop, getString(R.string.stop),
                    playbackAction(ACTION_STOP));
            builder.setOngoing(true);
            builder.setAutoCancel(false);
        } else {
            builder.setContentText(getString(R.string.completed))
                    .setProgress(0, postion, false);
            builder.setOngoing(false);
            builder.setAutoCancel(true);
        }

        Notification notification = builder.build();
        if (notificationManager != null) {
            notificationManager.notify(notificationId, notification);
        }
    }

    private PendingIntent playbackAction(@NotNull String action) {
        Intent playbackAction = new Intent(getApplicationContext(),
                OnNotificationButtonClick.class);
        playbackAction.setAction(action);
        playbackAction.putExtra("downloadId", downloadId);
        return PendingIntent.getBroadcast(getApplicationContext(), 0, playbackAction,
                PendingIntent.FLAG_UPDATE_CURRENT);
    }


}
