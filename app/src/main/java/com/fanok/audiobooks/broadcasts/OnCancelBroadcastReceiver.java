package com.fanok.audiobooks.broadcasts;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.fanok.audiobooks.activity.MainActivity;
import com.fanok.audiobooks.service.MediaPlayerService;


public class OnCancelBroadcastReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (MainActivity.isCloseApp()) {
            context.stopService(new Intent(context, MediaPlayerService.class));
        }
    }
}
