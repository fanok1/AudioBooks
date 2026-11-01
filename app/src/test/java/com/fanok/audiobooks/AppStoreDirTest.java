package com.fanok.audiobooks;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** NET-05: the download store moves to its new place only by a successful rename; otherwise the old one stays in use. */
public class AppStoreDirTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private File target;
    private File belowDownloads;
    private File cacheFallback;
    private final List<String> moves = new ArrayList<>();

    @Before
    public void setUp() {
        target = new File(tmp.getRoot(), "files/media3_downloads");
        belowDownloads = new File(tmp.getRoot(), "Download/AudioBooks/.audiobooks_cache");
        cacheFallback = new File(tmp.getRoot(), "cache/downloads");
    }

    @Test
    public void aFailedRenameKeepsTheOldStoreAndDoesNotChooseTheNewOne() throws IOException {
        withData(belowDownloads);

        File chosen = App.chooseStoreDir(target, Arrays.asList(belowDownloads, cacheFallback), mover(false));

        assertEquals(belowDownloads, chosen);
        assertEquals(Arrays.asList(belowDownloads + ">" + target), moves);
    }

    @Test
    public void aSuccessfulRenameChoosesTheNewStore() throws IOException {
        withData(belowDownloads);

        assertEquals(target, App.chooseStoreDir(target, Arrays.asList(belowDownloads, cacheFallback), mover(true)));
    }

    @Test
    public void theCacheDirFallbackIsMigratedWhenTheNewStoreIsEmpty() throws IOException {
        assertTrue(belowDownloads.mkdirs()); // created by the old code, never used
        assertTrue(target.mkdirs());
        withData(cacheFallback);

        assertEquals(cacheFallback, App.chooseStoreDir(target, Arrays.asList(belowDownloads, cacheFallback), mover(false)));
        assertEquals(Arrays.asList(cacheFallback + ">" + target), moves);
    }

    @Test
    public void aStoreWithDataOrNoLegacyStoreIsNeverMoved() throws IOException {
        assertEquals(target, App.chooseStoreDir(target, Arrays.asList(belowDownloads, cacheFallback), mover(false)));

        withData(belowDownloads);
        withData(target);
        assertEquals(target, App.chooseStoreDir(target, Arrays.asList(belowDownloads, cacheFallback), mover(false)));
        assertTrue(moves.isEmpty());
    }

    private App.StoreMover mover(boolean succeeds) {
        return (from, to) -> {
            moves.add(from + ">" + to);
            return succeeds;
        };
    }

    private static void withData(File dir) throws IOException {
        assertTrue(dir.isDirectory() || dir.mkdirs());
        assertTrue(new File(dir, ".uid").createNewFile());
    }
}
