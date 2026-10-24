package com.fanok.audiobooks;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Looper;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.cache.Cache;
import androidx.media3.exoplayer.offline.DownloadCursor;
import androidx.media3.exoplayer.offline.DownloadManager;
import androidx.preference.PreferenceManager;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.fanok.audiobooks.util.DownloadStorageHelper;
import com.fanok.audiobooks.util.DownloadUtil;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import org.junit.Test;
import org.junit.runner.RunWith;

/** NET-05/NET-06: one download manager and cache for the process, independent of the download folder setting. */
@UnstableApi
@RunWith(AndroidJUnit4.class)
public class DownloadManagerSingletonTest {

    @Test
    public void theManagerLivesOnTheMainLooperWhoeverAsksFirst() throws Exception {
        final DownloadManager[] seen = new DownloadManager[1];
        Thread background = new Thread(() -> seen[0] = DownloadUtil.getDownloadManager());
        background.start();
        background.join();

        assertNotNull(seen[0]);
        assertSame(Looper.getMainLooper(), seen[0].getApplicationLooper());
    }

    @Test
    public void changingTheDownloadFolderKeepsTheOneManagerUsable() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        App app = (App) context.getApplicationContext();

        // Concurrent first use publishes one instance.
        final DownloadManager[] seen = new DownloadManager[8];
        final CountDownLatch start = new CountDownLatch(1);
        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < seen.length; i++) {
            final int k = i;
            Thread t = new Thread(() -> {
                try {
                    start.await();
                    seen[k] = DownloadUtil.getDownloadManager();
                } catch (InterruptedException ignored) {
                }
            });
            threads.add(t);
            t.start();
        }
        start.countDown();
        for (Thread t : threads) t.join();
        DownloadManager first = seen[0];
        assertNotNull(first);
        for (DownloadManager m : seen) assertSame(first, m);
        Cache cache = DownloadUtil.getDownloadCache();

        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(context);
        String before = pref.getString(DownloadStorageHelper.PREF_DOWNLOAD_PATH, null);
        File other = new File(context.getCacheDir(), "other-download-folder-" + System.nanoTime());
        try {
            DownloadStorageHelper.saveDownloadPath(context, other.getAbsolutePath());

            assertSame(first, DownloadUtil.getDownloadManager());
            assertSame(first, app.getDownloadManager());
            assertSame(cache, DownloadUtil.getDownloadCache());
            // Still usable: a released SimpleCache or manager would throw here.
            cache.getKeys();
            try (DownloadCursor cursor = first.getDownloadIndex().getDownloads()) {
                cursor.getCount();
            }
            String store = app.getDownloadCacheDir().getAbsolutePath();
            assertFalse(store.startsWith(other.getAbsolutePath()));
            assertFalse(store.startsWith(DownloadStorageHelper.getDownloadPath(context) + "/"));
            assertFalse(store.startsWith(context.getCacheDir().getAbsolutePath()));
        } finally {
            SharedPreferences.Editor editor = pref.edit();
            if (before == null) editor.remove(DownloadStorageHelper.PREF_DOWNLOAD_PATH);
            else editor.putString(DownloadStorageHelper.PREF_DOWNLOAD_PATH, before);
            editor.commit();
        }
    }
}
