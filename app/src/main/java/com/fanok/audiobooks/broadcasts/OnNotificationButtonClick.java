package com.fanok.audiobooks.broadcasts;

import static com.fanok.audiobooks.service.Download.ACTION_PAUSE;
import static com.fanok.audiobooks.service.Download.ACTION_RESUME;
import static com.fanok.audiobooks.service.Download.ACTION_STOP;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.downloader.PRDownloader;


public class OnNotificationButtonClick extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        handleIncomingActions(intent);
    }

    private void handleIncomingActions(Intent playbackAction) {
        if (playbackAction == null) return;
        String actionString = playbackAction.getAction();
        if (actionString == null) return;
        int downloadId = playbackAction.getIntExtra("downloadId", 0);
        if (downloadId != 0) {
            switch (actionString) {
                case ACTION_RESUME:
                    PRDownloader.resume(downloadId);
                    break;
                case ACTION_PAUSE:
                    PRDownloader.pause(downloadId);
                    break;
                case ACTION_STOP:
                    PRDownloader.cancel(downloadId);
                    break;
            }
        }
    }

}
