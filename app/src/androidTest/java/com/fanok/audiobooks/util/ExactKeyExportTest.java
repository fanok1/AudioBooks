package com.fanok.audiobooks.util;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;
import android.net.Uri;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.database.StandaloneDatabaseProvider;
import androidx.media3.datasource.ByteArrayDataSource;
import androidx.media3.datasource.DataSpec;
import androidx.media3.datasource.cache.CacheDataSource;
import androidx.media3.datasource.cache.CacheWriter;
import androidx.media3.datasource.cache.NoOpCacheEvictor;
import androidx.media3.datasource.cache.SimpleCache;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Arrays;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/** DL-01: every export holds exactly its own cache key's bytes, never its siblings'. */
@UnstableApi
@RunWith(AndroidJUnit4.class)
public class ExactKeyExportTest {

    /** Sibling keys that the old exporter matched by prefix and substring and concatenated. */
    private static final String[] KEYS = {
            "https://cdn.example.org/book/1.mp3",
            "https://cdn.example.org/book/1.mp3?token=abc",
            "https://cdn.example.org/book/11.mp3",
    };

    private File base;
    private File out;
    private StandaloneDatabaseProvider database;
    private SimpleCache cache;
    private byte[][] blobs;

    @Before
    public void setUp() throws IOException {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        base = new File(context.getCacheDir(), "exact-key-export-" + System.nanoTime());
        out = new File(base, "out");
        database = new StandaloneDatabaseProvider(context);
        cache = new SimpleCache(new File(base, "cache"), new NoOpCacheEvictor(), database);
        blobs = new byte[][]{blob("ID3", 1, 32), blob("", 2, 63), blob("OggS", 3, 100)};
        for (int i = 0; i < KEYS.length; i++) {
            final byte[] data = blobs[i];
            CacheDataSource writer = new CacheDataSource.Factory()
                    .setCache(cache)
                    .setUpstreamDataSourceFactory(() -> new ByteArrayDataSource(data))
                    .createDataSource();
            new CacheWriter(writer, new DataSpec.Builder().setUri(Uri.parse(KEYS[i])).setKey(KEYS[i]).build(),
                    null, null).cache();
        }
    }

    @After
    public void tearDown() {
        cache.release();
        SimpleCache.delete(new File(base, "cache"), database);
        deleteRecursively(base);
    }

    @Test
    public void eachExportHoldsExactlyItsOwnKey() throws IOException {
        File[] files = new File[KEYS.length];
        for (int i = 0; i < KEYS.length; i++) {
            files[i] = DownloadStorageHelper.exportToDir(cache, KEYS[i], Uri.parse(KEYS[i]), blobs[i].length, out,
                    i + 1, "Глава " + (i + 1), "mp3");
        }
        for (int i = 0; i < KEYS.length; i++) {
            assertArrayEquals(KEYS[i], blobs[i], read(files[i]));
        }
        assertTrue(files[0].getName().endsWith(".mp3"));
        assertTrue(files[2].getName().endsWith(".ogg"));
        String[] names = out.list();
        Arrays.sort(names);
        assertEquals("no temp files remain", KEYS.length, names.length);
    }

    @Test
    public void anExistingFileIsNeverReplaced() throws IOException {
        File first = DownloadStorageHelper.exportToDir(cache, KEYS[0], Uri.parse(KEYS[0]), blobs[0].length, out, 1, "Глава", "mp3");
        File second = DownloadStorageHelper.exportToDir(cache, KEYS[1], Uri.parse(KEYS[1]), blobs[1].length, out, 1, "Глава", "mp3");

        assertNotEquals(first, second);
        assertEquals("001 - Глава (2).mp3", second.getName());
        assertArrayEquals(blobs[0], read(first));
        assertArrayEquals(blobs[1], read(second));
    }

    @Test
    public void aMissingOrShortKeyFailsAndLeavesNothing() {
        try {
            DownloadStorageHelper.exportToDir(cache, "https://cdn.example.org/book/2.mp3",
                    Uri.parse("https://cdn.example.org/book/2.mp3"), 10, out, 2, "Глава 2", "mp3");
            fail("exported a key that is not cached");
        } catch (IOException expected) {
        }
        try {
            DownloadStorageHelper.exportToDir(cache, KEYS[0], Uri.parse(KEYS[0]), blobs[0].length + 1, out, 1, "Глава", "mp3");
            fail("exported fewer bytes than the known length");
        } catch (IOException expected) {
        }
        String[] names = out.list();
        assertEquals(0, names == null ? 0 : names.length);
    }

    private static byte[] blob(String magic, int fill, int length) {
        byte[] data = new byte[length];
        Arrays.fill(data, (byte) fill);
        byte[] head = magic.getBytes();
        System.arraycopy(head, 0, data, 0, head.length);
        return data;
    }

    private static byte[] read(File file) throws IOException {
        try (RandomAccessFile f = new RandomAccessFile(file, "r")) {
            byte[] data = new byte[(int) f.length()];
            f.readFully(data);
            return data;
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
