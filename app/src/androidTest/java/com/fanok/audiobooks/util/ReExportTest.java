package com.fanok.audiobooks.util;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.ByteArrayDataSource;
import androidx.media3.datasource.DataSpec;
import androidx.media3.datasource.cache.Cache;
import androidx.media3.datasource.cache.CacheDataSource;
import androidx.media3.datasource.cache.CacheWriter;
import androidx.media3.exoplayer.offline.DefaultDownloadIndex;
import androidx.media3.exoplayer.offline.Download;
import androidx.media3.exoplayer.offline.DownloadRequest;
import androidx.preference.PreferenceManager;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.fanok.audiobooks.App;
import com.fanok.audiobooks.download.DownloadIds;
import com.fanok.audiobooks.model.AudioListDBModel;
import com.fanok.audiobooks.pojo.AudioListPOJO;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import org.junit.runner.RunWith;

/** OFF-04: a recorded copy deleted outside the app no longer hides its chapter from an explicit re-export. */
@UnstableApi
@RunWith(AndroidJUnit4.class)
public class ReExportTest {

    @Test
    public void aCopyDeletedExternallyIsWrittenAgain() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        App app = (App) context.getApplicationContext();
        String book = "https://akniga.org/ws3b-reexport-" + System.nanoTime();
        String url = "https://cdn.akniga.org/ws3b-reexport/1.mp3";
        File base = new File(context.getCacheDir(), "reexport-" + System.nanoTime());
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(context);
        String before = pref.getString(DownloadStorageHelper.PREF_DOWNLOAD_PATH, null);
        AudioListDBModel model = new AudioListDBModel(context);
        DefaultDownloadIndex index = new DefaultDownloadIndex(app.getDatabaseProvider());
        Cache cache = DownloadUtil.getDownloadCache();
        final byte[] data = new byte[4096];
        Arrays.fill(data, (byte) 7);
        System.arraycopy("ID3".getBytes(), 0, data, 0, 3);

        AudioListPOJO chapter = new AudioListPOJO();
        chapter.setBookUrl(book);
        chapter.setBookName("Re-export");
        chapter.setAudioName("Глава 1");
        chapter.setAudioUrl(url);
        model.syncChapters(book, Collections.singletonList(chapter));
        DownloadIds.Entry entry = DownloadUtil.entriesOf(book, model.getRows(book)).get(0);
        try {
            assertTrue(pref.edit().putString(DownloadStorageHelper.PREF_DOWNLOAD_PATH, base.getAbsolutePath()).commit());
            CacheDataSource writer = new CacheDataSource.Factory()
                    .setCache(cache)
                    .setUpstreamDataSourceFactory(() -> new ByteArrayDataSource(data))
                    .createDataSource();
            new CacheWriter(writer, new DataSpec.Builder().setUri(Uri.parse(url)).setKey(entry.cacheKey).build(),
                    null, null).cache();
            DownloadRequest request = new DownloadRequest.Builder(entry.id, Uri.parse(url))
                    .setCustomCacheKey(entry.cacheKey)
                    .build();
            long now = System.currentTimeMillis();
            index.putDownload(new Download(request, Download.STATE_COMPLETED, now, now, data.length,
                    Download.STOP_REASON_NONE, Download.FAILURE_REASON_NONE));

            // The chapter was exported once; the user then deleted the file with a file manager.
            File stale = new File(base, "001 - Глава 1.mp3");
            assertTrue(base.mkdirs());
            try (FileOutputStream out = new FileOutputStream(stale)) {
                out.write(data);
            }
            model.getDatabase().booksAudioDao().updateLocalUri(book, url, stale.getAbsolutePath());
            assertTrue(stale.delete());

            final int[] result = new int[2];
            final CountDownLatch done = new CountDownLatch(1);
            DownloadStorageHelper.exportBooks(context, Collections.singletonList(book), (exported, failed, error) -> {
                result[0] = exported;
                result[1] = failed;
                done.countDown();
            });

            assertTrue(done.await(30, TimeUnit.SECONDS));
            assertArrayEquals(new int[]{1, 0}, result);
            String uri = model.getRows(book).get(0).localUri;
            assertNotNull(uri);
            assertNotEquals(stale.getAbsolutePath(), uri);
            assertArrayEquals(data, read(new File(uri)));
            assertEquals(1, model.getRows(book).size());
        } finally {
            index.removeDownload(entry.id);
            cache.removeResource(entry.cacheKey);
            model.remove(book);
            SharedPreferences.Editor editor = pref.edit();
            if (before == null) editor.remove(DownloadStorageHelper.PREF_DOWNLOAD_PATH);
            else editor.putString(DownloadStorageHelper.PREF_DOWNLOAD_PATH, before);
            editor.commit();
            deleteRecursively(base);
        }
    }

    private static byte[] read(File file) throws IOException {
        try (RandomAccessFile f = new RandomAccessFile(file, "r")) {
            byte[] bytes = new byte[(int) f.length()];
            f.readFully(bytes);
            return bytes;
        }
    }

    private static void deleteRecursively(File file) {
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) deleteRecursively(child);
        }
        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }
}
