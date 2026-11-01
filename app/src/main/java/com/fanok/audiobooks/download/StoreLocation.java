package com.fanok.audiobooks.download;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.core.content.ContextCompat;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.offline.DownloadManager;
import com.fanok.audiobooks.App;
import com.fanok.audiobooks.R;
import com.fanok.audiobooks.activity.MainActivity;
import com.fanok.audiobooks.service.MediaPlayerService;
import com.fanok.audiobooks.util.DownloadStorageHelper;
import com.fanok.audiobooks.util.DownloadUtil;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Where the Media3 download store lives: the app's own folder on a storage volume, the phone's memory or a memory
 * card (written without root or any storage permission). An open store cannot move under its running cache, so a
 * move stops playback, pauses the downloads, copies the store on the storage worker, records the new place and
 * restarts the process; the next start opens the store there and deletes the old one ({@link App#onCreate}).
 */
public final class StoreLocation {
    private static final String TAG = "StoreLocation";
    /** Free space left on the target volume besides the copied store. */
    private static final long SPACE_MARGIN = 64L * 1024 * 1024;
    private static final long POLL_MS = 200;
    /** How long a running download may take to stop after the pause. */
    private static final long STOP_WAIT_MS = 15_000;
    /** A move is under way (main thread): a second one waits for its end. */
    private static boolean moving;

    private StoreLocation() {
    }

    /** A volume the store can live on, and the store directory there. */
    public static final class Volume {
        public final File store;
        public final boolean removable;

        Volume(@NonNull File store, boolean removable) {
            this.store = store;
            this.removable = removable;
        }

        public long freeBytes() {
            File base = store.getParentFile();
            return base == null ? 0 : base.getUsableSpace();
        }
    }

    public interface Callback {
        /** On the main thread, when the store stayed where it was. */
        void onFailed(@NonNull String error);
    }

    /** Every mounted volume with an app folder, the primary (phone memory) first. */
    @NonNull
    public static List<Volume> volumes(@NonNull Context context) {
        List<Volume> volumes = new ArrayList<>();
        for (File dir : ContextCompat.getExternalFilesDirs(context, null)) {
            if (dir == null || !Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState(dir))) continue;
            boolean removable;
            try {
                removable = Environment.isExternalStorageRemovable(dir);
            } catch (IllegalArgumentException e) {
                removable = false;
            }
            volumes.add(new Volume(new File(dir, App.DOWNLOAD_CACHE_DIR), removable));
        }
        return volumes;
    }

    /** Bytes of the files below {@code dir}; call off the main thread. */
    public static long size(@NonNull File dir) {
        File[] files = dir.listFiles();
        long size = 0;
        if (files != null) {
            for (File file : files) size += file.isDirectory() ? size(file) : file.length();
        }
        return size;
    }

    /** Main thread. Moves the store to {@code target} and restarts the app; {@code callback} hears a failure. */
    @OptIn(markerClass = UnstableApi.class)
    public static void move(@NonNull Context context, @NonNull File target, @NonNull Callback callback) {
        final Context app = context.getApplicationContext();
        if (moving) {
            callback.onFailed(app.getString(R.string.store_move_failed, app.getString(R.string.store_moving)));
            return;
        }
        moving = true;
        final File current = App.getInstance().getDownloadCacheDir();
        final DownloadManager manager = DownloadUtil.getDownloadManager();
        MediaPlayerService.stopPlayback(app); // saves the position; playback reads the store
        manager.pauseDownloads();
        final Handler main = new Handler(Looper.getMainLooper());
        final long deadline = System.currentTimeMillis() + STOP_WAIT_MS;
        main.post(new Runnable() {
            @Override
            public void run() {
                // a paused download reads QUEUED while its task still writes its last piece: the manager is idle
                // only once every task (removals too) has ended
                if (!manager.isIdle() && System.currentTimeMillis() < deadline) {
                    main.postDelayed(this, POLL_MS);
                    return;
                }
                if (!manager.isIdle()) {
                    fail(app, manager, callback, "downloads did not stop");
                    return;
                }
                DownloadStorageHelper.submit(() -> {
                    String uid;
                    try {
                        uid = copy(app, current, target);
                    } catch (IOException | RuntimeException e) {
                        Log.e(TAG, "store not moved to " + target, e);
                        main.post(() -> fail(app, manager, callback,
                                e.getMessage() != null ? e.getMessage() : e.toString()));
                        return;
                    }
                    main.post(() -> {
                        App.getInstance().moveStoreOnNextStart(target, current, uid);
                        // the stop of playback writes its position meanwhile
                        main.postDelayed(() -> restart(app), 500);
                    });
                });
            }
        });
    }

    /**
     * Copies the store into an emptied {@code target} and returns the name of its id file (SimpleCache's
     * {@code *.uid}, which names its database tables): the next start deletes the old store only where the copy holds
     * that file. The old store stays in use until the restart.
     */
    @NonNull
    private static String copy(@NonNull Context app, @NonNull File current, @NonNull File target) throws IOException {
        if (target.equals(current) || !App.DOWNLOAD_CACHE_DIR.equals(target.getName())) {
            throw new IOException("Not a store directory: " + target);
        }
        try {
            if (!deleteTree(target)) { // what an earlier move left there: a second id file would confuse the cache
                throw new IOException("Cannot clear " + target);
            }
            long need = size(current) + SPACE_MARGIN;
            File base = target.getParentFile();
            long free = base == null ? 0 : base.getUsableSpace();
            if (free < need) {
                throw new IOException(app.getString(R.string.store_no_space,
                        android.text.format.Formatter.formatShortFileSize(app, need),
                        android.text.format.Formatter.formatShortFileSize(app, free)));
            }
            copyTree(current, target);
            String[] ids = target.list((dir, name) -> name.endsWith(".uid"));
            if (ids == null || ids.length != 1) {
                throw new IOException("No store id in " + current);
            }
            return ids[0];
        } catch (IOException | RuntimeException e) {
            deleteTree(target); // a partial copy
            throw e;
        }
    }

    private static void copyTree(@NonNull File from, @NonNull File to) throws IOException {
        if (!to.isDirectory() && !to.mkdirs()) throw new IOException("Cannot create " + to);
        File[] files = from.listFiles();
        if (files == null) throw new IOException("Cannot read " + from);
        for (File file : files) {
            File copy = new File(to, file.getName());
            if (file.isDirectory()) {
                copyTree(file, copy);
            } else {
                copyFile(file, copy);
            }
        }
    }

    /**
     * A file a removal deleted meanwhile (an export's release of its download) is left out: its content left the
     * cache's index, which drops such a file at the next start anyway.
     */
    private static void copyFile(@NonNull File from, @NonNull File to) throws IOException {
        byte[] buffer = new byte[256 * 1024];
        try (InputStream in = new FileInputStream(from); OutputStream out = new FileOutputStream(to)) {
            int n;
            while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
        } catch (java.io.FileNotFoundException e) {
            if (from.exists()) throw e;
        }
        if (!from.exists()) {
            //noinspection ResultOfMethodCallIgnored
            to.delete();
        } else if (to.length() != from.length()) {
            throw new IOException("Copy incomplete: " + to);
        }
    }

    /** Deletes a directory tree file by file (never through SimpleCache, which would drop its database too). */
    public static boolean deleteTree(@NonNull File dir) {
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    deleteTree(file);
                } else {
                    //noinspection ResultOfMethodCallIgnored
                    file.delete();
                }
            }
        }
        return dir.delete() || !dir.exists();
    }

    @OptIn(markerClass = UnstableApi.class)
    private static void fail(@NonNull Context app, @NonNull DownloadManager manager, @NonNull Callback callback,
                             @NonNull String error) {
        moving = false;
        manager.resumeDownloads();
        callback.onFailed(app.getString(R.string.store_move_failed, error));
    }

    /** A new process opens the store at its new place: the main screen in a fresh task, this process ended. */
    private static void restart(@NonNull Context app) {
        app.startActivity(Intent.makeRestartActivityTask(new ComponentName(app, MainActivity.class)));
        Runtime.getRuntime().exit(0);
    }
}
