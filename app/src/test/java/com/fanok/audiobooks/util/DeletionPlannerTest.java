package com.fanok.audiobooks.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.fanok.audiobooks.util.DeletionPlanner.Plan;
import com.fanok.audiobooks.util.DeletionPlanner.Row;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Test;

public class DeletionPlannerTest {

    /** The configured root is named like the book, as a user folder could be. */
    private static final String ROOT = "/storage/emulated/0/Мастер и Маргарита";
    private static final List<String> ROOTS = Collections.singletonList(ROOT);
    private static final DeletionPlanner.DownloadIds IDS = url -> url.split("\\?")[0];

    private static final String AKNIGA = "https://akniga.org/bulgakov-master-i-margarita";
    private static final String AKNIGA_OTHER_READER = "https://akniga.org/bulgakov-master-i-margarita-2";
    private static final String KNIGAVUHE = "https://knigavuhe.org/book/master-i-margarita/";

    private static final Row AK_1 = row(AKNIGA, "https://cdn.akniga.org/m/1.mp3", ROOT + "/Akniga/Мастер и Маргарита/Козий/001 - 1.mp3");
    private static final Row AK_10 = row(AKNIGA, "https://cdn.akniga.org/m/10.mp3", ROOT + "/Akniga/Мастер и Маргарита/Козий/002 - 10.mp3");
    private static final Row AK_01 = row(AKNIGA, "https://cdn.akniga.org/m/01.mp3?token=a%25b_c", ROOT + "/Akniga/Мастер и Маргарита/Козий/003 - 01.mp3");
    private static final Row AK_STREAMED = row(AKNIGA, "https://cdn.akniga.org/m/11.mp3", null);
    private static final Row READER_2 = row(AKNIGA_OTHER_READER, "https://cdn.akniga.org/m2/1.mp3", ROOT + "/Akniga/Мастер и Маргарита/Ярмольник/001 - 1.mp3");
    private static final Row OTHER_SOURCE = row(KNIGAVUHE, "https://s1.knigavuhe.org/m/1.mp3", "content://com.android.externalstorage.documents/tree/primary%3AMaster/document/primary%3AMaster%2FKnigaVUhe%2F001%20-%201.mp3");

    @Test
    public void deletingOneChapterPlansExactlyItsRecordedCopyAndDownload() {
        Plan plan = DeletionPlanner.plan(Collections.singletonList(AK_1), ROOTS, true, IDS);

        assertEquals(set(AK_1.localUri), plan.urisToDelete);
        assertEquals(set("https://cdn.akniga.org/m/1.mp3"), plan.downloadIdsToRemove);
        assertEquals(Collections.singletonList(AK_1), plan.rowsToClearLocalUri);
        assertTrue(plan.rowsToRemove.isEmpty());
    }

    @Test
    public void removingABookTouchesOnlyItsOwnRowsAmongSameTitleBooks() {
        List<Row> book = Arrays.asList(AK_1, AK_10, AK_01, AK_STREAMED);
        Plan plan = DeletionPlanner.plan(book, ROOTS, false, IDS);

        assertEquals(set(AK_1.localUri, AK_10.localUri, AK_01.localUri), plan.urisToDelete);
        assertEquals(set("https://cdn.akniga.org/m/1.mp3", "https://cdn.akniga.org/m/10.mp3",
                "https://cdn.akniga.org/m/01.mp3", "https://cdn.akniga.org/m/11.mp3"), plan.downloadIdsToRemove);
        assertEquals(book, plan.rowsToRemove);
        assertTrue(plan.rowsToClearLocalUri.isEmpty());
        for (Row untouched : Arrays.asList(READER_2, OTHER_SOURCE)) {
            assertTrue(!plan.urisToDelete.contains(untouched.localUri));
            assertTrue(!plan.downloadIdsToRemove.contains(IDS.of(untouched.urlAudio)));
        }
        assertTrue(!plan.urisToDelete.contains(ROOT));
    }

    @Test
    public void rowWithoutLocalUriPlansNoFileDeletion() {
        Plan plan = DeletionPlanner.plan(Collections.singletonList(AK_STREAMED), ROOTS, true, IDS);

        assertTrue(plan.urisToDelete.isEmpty());
        assertEquals(set("https://cdn.akniga.org/m/11.mp3"), plan.downloadIdsToRemove);
        assertTrue(plan.rowsToClearLocalUri.isEmpty());
        assertTrue(plan.rowsToRemove.isEmpty());
    }

    @Test
    public void importedChaptersLeaveTheLibraryButTheirFilesStay() {
        String imported = "https://локально.org/imported/Мастер и Маргарита/Основной";
        List<Row> rows = Arrays.asList(
                row(imported, ROOT + "/Мастер и Маргарита/01.mp3", null),
                row(imported, "content://com.android.externalstorage.documents/document/primary%3A10.mp3", null));
        Plan plan = DeletionPlanner.plan(rows, ROOTS, true, IDS);

        assertTrue(plan.urisToDelete.isEmpty());
        assertTrue(plan.downloadIdsToRemove.isEmpty());
        assertEquals(rows, plan.rowsToRemove);
    }

    @Test
    public void aRecordedUriNamingTheRootIsNeverDeleted() {
        Plan plan = DeletionPlanner.plan(Collections.singletonList(row(AKNIGA, "https://cdn.akniga.org/m/1.mp3", ROOT + "/")),
                ROOTS, false, IDS);

        assertTrue(plan.urisToDelete.isEmpty());
    }

    @Test
    public void foldersArePrunedOnlyStrictlyInsideTheEnclosingRoot() {
        List<String> roots = Arrays.asList(ROOT, ROOT + "/Akniga", "/storage/emulated/0/Download");

        assertEquals(ROOT + "/Akniga", DeletionPlanner.enclosingRoot(AK_1.localUri, roots));
        assertEquals(ROOT, DeletionPlanner.enclosingRoot("file://" + ROOT + "/x/1.mp3", roots));
        assertNull(DeletionPlanner.enclosingRoot(ROOT, roots));
        assertNull(DeletionPlanner.enclosingRoot(ROOT + " 2/1.mp3", roots));
        assertNull(DeletionPlanner.enclosingRoot("/storage/emulated/0/Music/1.mp3", roots));
    }

    @Test
    public void aStreamSharedWithAKeptChapterIsRemovedOnlyWithTheLastOfThem() {
        String hls = "https://r1.akniga.club/b/111286/pl.m3u8?res=abc";
        List<com.fanok.audiobooks.download.DownloadIds.Entry> e = com.fanok.audiobooks.download.DownloadIds.forBook(
                AKNIGA, Arrays.asList(hls, hls), new int[]{0, 1});
        Row first = new Row(AKNIGA, hls, null, Arrays.asList(e.get(0).id, e.get(0).legacyId));
        Row second = new Row(AKNIGA, hls, null, Arrays.asList(e.get(1).id, e.get(1).legacyId));

        Plan one = DeletionPlanner.plan(Collections.singletonList(first), ROOTS, true, IDS,
                DeletionPlanner.idsOf(Collections.singletonList(second), IDS));
        assertTrue(one.downloadIdsToRemove.isEmpty());

        Plan both = DeletionPlanner.plan(Arrays.asList(first, second), ROOTS, true, IDS, Collections.<String>emptySet());
        assertEquals(set("v1/akniga.org/bulgakov-master-i-margarita/stream/r1.akniga.club/b/111286/pl.m3u8",
                "https://r1.akniga.club/b/111286/pl.m3u8"), both.downloadIdsToRemove);
    }

    @Test
    public void aLegacyDownloadOnlyTheDeletedChapterOwnsIsRemovedThoughASurvivorSharesItsLegacyId() {
        String book = "https://baza-knig.top/books/1-master/";
        String one = "https://baza-knig.top/get.php?file=1";
        String two = "https://baza-knig.top/get.php?file=2";
        String legacy = "https://baza-knig.top/get.php";
        List<com.fanok.audiobooks.download.DownloadIds.Entry> e = com.fanok.audiobooks.download.DownloadIds.forBook(
                book, Arrays.asList(one, two), new int[]{0, 1});
        // Only ?file=2 has a completed legacy download; chapter 1 survives, never downloaded.
        java.util.Map<String, String> stored = Collections.singletonMap(legacy, two);
        Set<String> kept = new HashSet<>(com.fanok.audiobooks.download.DownloadIds.ownedIds(e.get(0), one, stored));
        Row second = new Row(book, two, null, Arrays.asList(e.get(1).id, e.get(1).legacyId));

        Plan plan = DeletionPlanner.plan(Collections.singletonList(second), ROOTS, true, IDS, kept);

        assertTrue(plan.downloadIdsToRemove.contains(legacy));
    }

    @Test
    public void pruningStopsAtTheBookFoldersAndNeverReachesARootMovedDeeper() {
        String source = ROOT + "/Akniga";
        String book = source + "/Мастер и Маргарита";
        String reader = book + "/Козий";
        List<String> folders = Arrays.asList(ROOT + "/x", ROOT + "/x/y", source, book, reader);

        assertEquals(3, DeletionPlanner.prunableDepth(folders, ROOTS));
        // The download root was changed to the book folder: only the reader folder may go.
        assertEquals(1, DeletionPlanner.prunableDepth(folders, Arrays.asList(ROOT, book + "/")));
        assertEquals(0, DeletionPlanner.prunableDepth(folders, Collections.singletonList("file://" + reader)));
        // A root inside the reader folder keeps every folder above it.
        assertEquals(0, DeletionPlanner.prunableDepth(folders, Collections.singletonList(reader + "/Глава 1")));
        assertEquals(2, DeletionPlanner.prunableDepth(Arrays.asList(book, reader), Collections.<String>emptyList()));
    }

    private static Row row(String urlBook, String urlAudio, String localUri) {
        return new Row(urlBook, urlAudio, localUri);
    }

    private static Set<String> set(String... values) {
        return new HashSet<>(Arrays.asList(values));
    }
}
