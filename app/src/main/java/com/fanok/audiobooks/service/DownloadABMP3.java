package com.fanok.audiobooks.service;


import static com.fanok.audiobooks.activity.BookActivity.Broadcast_CLEAR_DOWNLOADING;

import android.content.Intent;
import android.widget.Toast;
import com.downloader.Error;
import com.downloader.OnDownloadListener;
import com.downloader.PRDownloader;
import com.downloader.PRDownloaderConfig;
import com.downloader.httpclient.HttpClient;
import com.fanok.audiobooks.ABMP3HttpClient;
import com.fanok.audiobooks.R;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.SequenceInputStream;
import java.util.ArrayList;

public class DownloadABMP3 extends Download {

    public static final int fragmentSize = 1048576;

    private static final int BUFFER_SIZE = 4096;

    @Override
    protected void download(final int postion) {
        downloadFragment(postion, 0);
    }

    private void downloadFragment(final int postion, final long bytes) {
        HttpClient httpClient = new ABMP3HttpClient(bytes);
        String urlPath = mList.get(postion);
        String fileName = urlPath.substring(urlPath.lastIndexOf("/") + 1);

        PRDownloaderConfig config = PRDownloaderConfig.newBuilder()
                .setReadTimeout(30_000)
                .setConnectTimeout(30_000)
                .setUserAgent(
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/89.0.4389.82 Safari/537.36")
                .setHttpClient(httpClient)
                .build();
        PRDownloader.initialize(getApplicationContext(), config);
        downloadId = PRDownloader
                .download(urlPath, path + "/" + dirName.get(postion), fileName + ".temp" + bytes / fragmentSize)
                .build()
                .setOnStartOrResumeListener(() -> {
                    pause = false;
                    showNotification(postion, mProgress, true);
                })
                .setOnPauseListener(() -> {
                    pause = true;
                    showNotification(postion, mProgress, true);
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
                        showNotification(postion, mProgress, true);
                    }
                })
                .start(new OnDownloadListener() {
                    @Override
                    public void onDownloadComplete() {

                        downloadFragment(postion, bytes + fragmentSize);
                    }

                    @Override
                    public void onError(Error error) {
                        if (!error.getConnectionException().getMessage().equals("unexpected end of stream")) {
                            Toast.makeText(DownloadABMP3.this,
                                    getString(R.string.error_load_file) + " " + fileName,
                                    Toast.LENGTH_SHORT).show();
                        } else {
                            int i = 0;
                            ArrayList<File> files = new ArrayList<>();
                            while (true) {
                                File file = new File(path + "/" + dirName.get(postion), fileName + ".temp" + i);
                                if (file.exists()) {
                                    files.add(file);
                                } else {
                                    File file1 = new File(path + "/" + dirName.get(postion),
                                            fileName + ".temp" + i + ".temp");
                                    if (file1.exists()) {
                                        files.add(file1);
                                    }
                                    break;
                                }
                                i++;
                            }
                            if (files.isEmpty()) {
                                Toast.makeText(DownloadABMP3.this,
                                        getString(R.string.error_load_file) + " " + fileName,
                                        Toast.LENGTH_SHORT).show();
                            } else {
                                meargeAudio(files);
                            }

                        }
                        downloadNext(postion + 1);
                    }

                });
    }

    private static void meargeAudio(ArrayList<File> filesToMearge) {

        while (filesToMearge.size() != 1) {
            try {
                FileInputStream fistream1 = new FileInputStream(new File(filesToMearge.get(0).getPath()));
                FileInputStream fistream2 = new FileInputStream(new File(filesToMearge.get(1).getPath()));
                File file1 = new File(filesToMearge.get(0).getPath());
                boolean deleted = file1.delete();
                File file2 = new File(filesToMearge.get(1).getPath());
                boolean deleted1 = file2.delete();

                SequenceInputStream sistream = new SequenceInputStream(fistream1, fistream2);
                FileOutputStream fostream = new FileOutputStream(new File(filesToMearge.get(0).getPath()),
                        true);//destinationfile

                byte[] buffer = new byte[BUFFER_SIZE];
                int bytesRead;

                while ((bytesRead = sistream.read(buffer)) != -1) {
                    fostream.write(buffer, 0, bytesRead);
                }

                filesToMearge.add(0, new File(filesToMearge.get(0).getPath()));
                filesToMearge.remove(1);
                filesToMearge.remove(1);

                fostream.close();
                sistream.close();
                fistream1.close();
                fistream2.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        File oldFile = new File(filesToMearge.get(0).getPath());
        File newFile = new File(filesToMearge.get(0).getPath().replace(".temp0", ""));
        if (!newFile.exists()) {
            boolean success = oldFile.renameTo(newFile);
        }
    }

}
