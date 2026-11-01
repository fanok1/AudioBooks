package com.fanok.audiobooks.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import androidx.media3.exoplayer.offline.Download;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

public class DownloadStorageHelperTest {

    @Test
    public void everySiteHasItsFolderByItsCurrentHostAndAliases() {
        assertEquals("Knigoblud", DownloadStorageHelper.getSourceName("https://www.knigoblud.club/book/x"));
        assertEquals("Knigoblud", DownloadStorageHelper.getSourceName("https://mob.knigoblud.club/book/x"));
        assertEquals("Otrub", DownloadStorageHelper.getSourceName("https://otrub.in/book/x.html"));
        assertEquals("Otrub", DownloadStorageHelper.getSourceName("https://m.otrub.in/book/x.html"));
        assertEquals("BazaKnig", DownloadStorageHelper.getSourceName("https://baza-knig.top/x.html"));
        assertEquals("Audioboo", DownloadStorageHelper.getSourceName("https://audioboo.org/x.html"));
        assertEquals("Uknig", DownloadStorageHelper.getSourceName("https://uknig.com/x.html"));
        for (com.fanok.audiobooks.search.Provider provider : com.fanok.audiobooks.search.Provider.values()) {
            assertNotEquals(provider.name(), "Audiobooks", DownloadStorageHelper.getSourceName(provider.server + "/b"));
        }
        assertEquals("Audiobooks", DownloadStorageHelper.getSourceName("https://example.com/b"));
        assertEquals("Audiobooks", DownloadStorageHelper.getSourceName(null));
    }

    @Test
    public void aPendingDownloadInAnyStateKeepsTheBookRows() {
        for (int state : new int[]{Download.STATE_QUEUED, Download.STATE_DOWNLOADING, Download.STATE_STOPPED,
                Download.STATE_FAILED, Download.STATE_RESTARTING, Download.STATE_COMPLETED}) {
            Map<String, Integer> index = new HashMap<>();
            index.put("v1/b/chapter/0", Download.STATE_COMPLETED);
            index.put("v1/b/chapter/1", state);
            // Chapter 0 was just deleted; chapter 1 is still in the index.
            assertTrue("state " + state, DownloadStorageHelper.stillNeeded(index, Collections.singleton("v1/b/chapter/0")));
        }
        assertFalse(DownloadStorageHelper.stillNeeded(
                Collections.singletonMap("v1/b/chapter/1", Download.STATE_REMOVING), Collections.<String>emptySet()));
        assertFalse(DownloadStorageHelper.stillNeeded(
                Collections.singletonMap("v1/b/chapter/1", Download.STATE_QUEUED), Collections.singleton("v1/b/chapter/1")));
    }

    @Test
    public void importedBooksOfTwoRootsWithTheSameSourceTitleAndReaderStaySeparate() {
        String a = DownloadStorageHelper.importedBookUrl("/storage/emulated/0/Download/AudioBooks", "Локально",
                "Мастер и Маргарита", "Основной");
        String b = DownloadStorageHelper.importedBookUrl(
                "content://com.android.externalstorage.documents/tree/primary%3AMusic", "Локально",
                "Мастер и Маргарита", "Основной");

        assertNotEquals(a, b);
        assertEquals(a, DownloadStorageHelper.importedBookUrl("/storage/emulated/0/Download/AudioBooks/", "Локально",
                "Мастер и Маргарита", "Основной"));
        assertTrue(a.matches("https://локально[.]org/imported/[0-9a-f]{8}/Мастер и Маргарита/Основной"));
        assertTrue(DeletionPlanner.isImportedBook(a));
        assertNotEquals(a, DownloadStorageHelper.importedBookUrl("/storage/emulated/0/Download/AudioBooks", "Локально",
                "Мастер и Маргарита", "Другой"));
    }
}
