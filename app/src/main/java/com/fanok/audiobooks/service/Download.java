package com.fanok.audiobooks.service;

import static com.fanok.audiobooks.activity.BookActivity.Broadcast_CLEAR_DOWNLOADING;
import static com.fanok.audiobooks.activity.BookActivity.Broadcast_UPDATE_ADAPTER;

import android.app.DownloadManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.preference.PreferenceManager;
import com.downloader.Error;
import com.downloader.OnDownloadListener;
import com.downloader.PRDownloader;
import com.downloader.PRDownloaderConfig;
import com.downloader.httpclient.DefaultHttpClient;
import com.downloader.httpclient.HttpClient;
import com.fanok.audiobooks.BazaKnigHttpClient;
import com.fanok.audiobooks.Consts;
import com.fanok.audiobooks.R;
import com.fanok.audiobooks.Url;
import com.fanok.audiobooks.broadcasts.OnNotificationButtonClick;
import com.fanok.audiobooks.model.AudioListDBModel;
import com.fanok.audiobooks.model.BooksDBModel;
import com.fanok.audiobooks.pojo.AudioListPOJO;
import com.fanok.audiobooks.pojo.AudioPOJO;
import com.fanok.audiobooks.pojo.BookPOJO;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.File;
import java.lang.reflect.Type;
import java.util.ArrayList;
import org.jetbrains.annotations.NotNull;

public class Download extends Service {

    public static final String ACTION_RESUME = "audioBook.download.ACTION_PLAY";

    public static final String ACTION_PAUSE = "audioBook.download.ACTION_PAUSE";

    public static final String ACTION_STOP = "audioBook.download.ACTION_STOP";

    private static final String TAG = "Download";

    protected static final int notificationId = 487;

    protected static final String chanalId = "574";

    protected static final String chanalName = "Download";

    protected ArrayList<BookPOJO> mBookPOJO;

    protected int downloadId;

    protected ArrayList<String> mList;

    protected int mProgress;

    protected PendingIntent notificationClickIntent;

    protected String path;

    protected boolean pause;

    protected String val;


    @Override
    public void onCreate() {
        super.onCreate();
        mList = new ArrayList<>();
        mBookPOJO = new ArrayList<>();

        SharedPreferences pref = PreferenceManager
                .getDefaultSharedPreferences(this);

        File[] folders = getExternalFilesDirs(null);
        val = pref.getString("pref_downland_path", getString(R.string.dir_value_emulated));
        if (val.equals(getString(R.string.dir_value_emulated))) {
            path = folders[0].getAbsolutePath();
        } else if (val.equals(getString(R.string.dir_value_sdcrd))) {
            if (folders.length == 1) {
                Toast.makeText(this, R.string.no_sdcard, Toast.LENGTH_SHORT).show();
                path = folders[0].getAbsolutePath();
            } else {
                path = folders[1].getAbsolutePath();
            }
        } else if (val.equals(getString(R.string.dir_value_download))) {
            path = Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOWNLOADS).getPath();
        }

        if (path.charAt(path.length() - 1) != '/') {
            path += "/";
        }

        Intent resultIntent = new Intent(DownloadManager.ACTION_VIEW_DOWNLOADS);
        notificationClickIntent = PendingIntent.getActivity(this, 0, resultIntent,
                PendingIntent.FLAG_UPDATE_CURRENT);

    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String json = intent.getStringExtra("book");
            if(json!=null&&!json.isEmpty()){
                Gson gson = new Gson();
                Type type = new TypeToken<BookPOJO>() {
                }.getType();
                BookPOJO bookPOJO = gson.fromJson(json, type);
                String url = intent.getStringExtra("url");
                if (url != null && bookPOJO!=null) {
                    if(!mList.contains(url)) {
                        boolean start = mList.isEmpty();
                        mList.add(url);
                        mBookPOJO.add(bookPOJO);
                        if (start)
                            start();
                    }
                } else {
                    Log.d(TAG, "onHandleIntent: url or bookPojo is null");
                }
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

    protected void download(int postion) {
        String urlPath = mList.get(postion);
        String fileName = urlPath.substring(urlPath.lastIndexOf("/") + 1);
        HttpClient httpClient;
        if(mBookPOJO.get(postion).getUrl().contains(Url.SERVER_BAZA_KNIG)){
            httpClient = new BazaKnigHttpClient(Url.SERVER_BAZA_KNIG+"/");
        }else {
            httpClient = new DefaultHttpClient();
        }

        PRDownloaderConfig config = PRDownloaderConfig.newBuilder()
                .setReadTimeout(30_000)
                .setConnectTimeout(30_000)
                .setUserAgent(
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/89.0.4389.82 Safari/537.36")
                .setHttpClient(httpClient)
                .build();
        PRDownloader.initialize(getApplicationContext(), config);
        String source = Consts.getSorceName(this, mBookPOJO.get(postion).getUrl());
        String filePath = path+"/"+source
                +"/"+mBookPOJO.get(postion).getAutor()
                +"/"+mBookPOJO.get(postion).getArtist()
                +"/"+mBookPOJO.get(postion).getName();
        downloadId = PRDownloader.download(urlPath, filePath, fileName)
                .build()
                .setOnStartOrResumeListener(() -> {
                    pause = false;
                    showNotification(postion, mProgress, false);
                })
                .setOnPauseListener(() -> {
                    pause = true;
                    showNotification(postion, mProgress, false);
                })
                .setOnCancelListener(() -> {
                    sendBroadcast(new Intent(Broadcast_CLEAR_DOWNLOADING));
                    stopForeground(true);
                    stopSelf();
                })
                .setOnProgressListener(progress -> {
                    int progessProcent = (int) (progress.currentBytes * 100 / progress.totalBytes);
                    if (progessProcent % 5 == 0 && progessProcent != mProgress) {
                        mProgress = progessProcent;
                        showNotification(postion, mProgress, false);
                    }
                })
                .start(new OnDownloadListener() {
                    @Override
                    public void onDownloadComplete() {
                        addSaveFile(postion);
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

    protected void addSaveFile(final int postion) {
        BooksDBModel dbModel = new BooksDBModel(this);
        dbModel.addSaved(mBookPOJO.get(postion));
        dbModel.closeDB();
    }

    protected void downloadNext(int positionNext) {
        sendBroadcast(new Intent(Broadcast_UPDATE_ADAPTER));
        if (positionNext == mList.size()) {
            stopForeground(false);
            showNotification(positionNext, 0, false);
            Toast.makeText(this, R.string.loading_completed, Toast.LENGTH_SHORT).show();
            stopSelf();
        } else {
            download(positionNext);
        }
    }

    protected void showNotification(int postion, int progress, boolean indeteminate) {
        NotificationManager notificationManager =
                (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(getApplicationContext(), chanalId)
                        .setSmallIcon(R.drawable.ic_launcher_foreground)
                        .setGroup("GroupDownload")
                        .setContentTitle(getString(R.string.loading));
        if (val.equals(getString(R.string.dir_value_download))) {
            builder.setContentIntent(notificationClickIntent);
        }
        if (postion < mList.size()) {
            builder.setContentText(postion + " " + getString(R.string.of) + " " + mList.size())
                    .setProgress(100, progress, indeteminate);
            if (!pause) {
                builder.addAction(R.drawable.ic_pause, getString(R.string.pause),
                        playbackAction(ACTION_PAUSE, postion));
            } else {
                builder.addAction(R.drawable.ic_play, getString(R.string.play),
                        playbackAction(ACTION_RESUME, postion));
            }
            builder.addAction(R.drawable.ic_stop, getString(R.string.stop),
                    playbackAction(ACTION_STOP, postion));
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

    private PendingIntent playbackAction(@NotNull String action, int postion) {
        Intent playbackAction = new Intent(getApplicationContext(),
                OnNotificationButtonClick.class);
        String urlPath = mList.get(postion);
        String fileName = urlPath.substring(urlPath.lastIndexOf("/") + 1);
        playbackAction.setAction(action);
        playbackAction.putExtra("downloadId", downloadId);
        playbackAction.putExtra("path", path + "/" + mBookPOJO.get(postion).getName());
        playbackAction.putExtra("fileName", fileName);
        return PendingIntent.getBroadcast(getApplicationContext(), 0, playbackAction,
                PendingIntent.FLAG_UPDATE_CURRENT);
    }


}
