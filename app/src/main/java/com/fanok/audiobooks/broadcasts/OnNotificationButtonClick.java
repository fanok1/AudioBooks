package com.fanok.audiobooks.broadcasts;

import static com.fanok.audiobooks.activity.BookActivity.Broadcast_SHOW_GET_PLUS;
import static com.fanok.audiobooks.service.Download.ACTION_PAUSE;
import static com.fanok.audiobooks.service.Download.ACTION_RESUME;
import static com.fanok.audiobooks.service.Download.ACTION_STOP;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import com.downloader.PRDownloader;
import com.theo.downloader.IDownloader;
import java.io.File;


public class OnNotificationButtonClick extends BroadcastReceiver {


    Context mContext;
    @Override
    public void onReceive(Context context, Intent intent) {
        mContext = context;
        handleIncomingActions(intent);
    }

    private void handleIncomingActions(Intent playbackAction) {
        if (playbackAction == null) {
            return;
        }
        String actionString = playbackAction.getAction();
        if (actionString == null) {
            return;
        }
        int downloadId = playbackAction.getIntExtra("downloadId", 0);
        String path = playbackAction.getStringExtra("path");
        String fileName = playbackAction.getStringExtra("fileName");
        if (downloadId != 0&&!fileName.contains("m3u8")) {
            switch (actionString) {
                case ACTION_RESUME:
                    PRDownloader.resume(downloadId);
                    break;
                case ACTION_PAUSE:
                    PRDownloader.pause(downloadId);
                    break;
                case ACTION_STOP:
                    PRDownloader.cancel(downloadId);
                    int i = 0;
                    while (true) {
                        File file = new File(path, fileName + ".temp" + i);
                        if (file.exists()) {
                            file.delete();
                        } else {
                            File file1 = new File(path, fileName + ".temp" + i + ".temp");
                            if (file1.exists()) {
                                file1.delete();
                            }
                            break;
                        }
                        i++;
                    }
                    break;
            }
        }else {
            Intent broadcastIntent = new Intent(actionString);
            broadcastIntent.putExtra("path", path);
            if(mContext!=null){
                mContext.sendBroadcast(broadcastIntent);
            }
        }
    }

}
