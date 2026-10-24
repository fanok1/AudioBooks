package com.fanok.audiobooks.broadcasts;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.fanok.audiobooks.service.MediaPlayerService;


/** The user dismissed the player notification: that is the stop command. */
public class OnCancelBroadcastReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!MediaPlayerService.isRunning()) {
            return;
        }
        try {
            context.startService(new Intent(context, MediaPlayerService.class)
                    .setAction(MediaPlayerService.ACTION_STOP));
        } catch (IllegalStateException e) {
            Log.w("OnCancelBroadcast", "stop command refused, stopping the service", e);
            context.stopService(new Intent(context, MediaPlayerService.class));
        }
    }
}
