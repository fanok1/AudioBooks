package com.fanok.audiobooks.util;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import androidx.test.platform.app.InstrumentationRegistry;
import com.fanok.audiobooks.model.AudioListDBModel;
import com.fanok.audiobooks.model.BooksDBModel;
import com.fanok.audiobooks.pojo.AudioListPOJO;
import com.fanok.audiobooks.pojo.BookPOJO;
import com.fanok.audiobooks.room.BooksAudioEntity;
import com.fanok.audiobooks.util.DeletionPlanner.Plan;
import com.fanok.audiobooks.util.DeletionPlanner.Row;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class DeletionExecutorTest {

    private static final String BOOK_A = "https://akniga.org/a";

    private Context context;
    private File base;
    private File root;
    private List<String> roots;

    @Before
    public void setUp() throws IOException {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        base = new File(context.getCacheDir(), "deletion-executor-test");
        deleteTree(base);
        root = new File(base, "root");
        roots = Collections.singletonList(root.getAbsolutePath());
        for (String path : new String[]{"A/01.mp3", "A/10.mp3", "A (other reader)/01.mp3", "other/1.mp3", "A.mp3"}) {
            File file = new File(root, path);
            assertTrue(file.getParentFile().isDirectory() || file.getParentFile().mkdirs());
            try (FileOutputStream out = new FileOutputStream(file)) {
                out.write(new byte[]{1, 2, 3});
            }
        }
    }

    @After
    public void tearDown() {
        deleteTree(base);
    }

    @Test
    public void deletesOnlyTheRecordedCopiesAndPrunesTheirEmptyFolder() {
        List<Row> bookA = Arrays.asList(
                new Row(BOOK_A, "https://cdn.akniga.org/a/01.mp3", file("A/01.mp3")),
                new Row(BOOK_A, "https://cdn.akniga.org/a/10.mp3", "file://" + file("A/10.mp3")),
                new Row(BOOK_A, "https://cdn.akniga.org/a/1.mp3", null));
        Plan plan = DeletionPlanner.plan(bookA, roots, false, url -> url);

        Set<String> failed = DownloadStorageHelper.deleteRecordedUris(context, plan.urisToDelete, roots);

        assertTrue(failed.isEmpty());
        assertFalse(new File(root, "A/01.mp3").exists());
        assertFalse(new File(root, "A/10.mp3").exists());
        assertFalse(new File(root, "A").exists());
        assertTrue(new File(root, "A (other reader)/01.mp3").isFile());
        assertTrue(new File(root, "other/1.mp3").isFile());
        assertTrue(new File(root, "A.mp3").isFile());
        assertTrue(root.isDirectory());
    }

    @Test
    public void keepsAFolderThatStillHasAnEntry() {
        Plan plan = DeletionPlanner.plan(
                Collections.singletonList(new Row(BOOK_A, "https://cdn.akniga.org/a/01.mp3", file("A/01.mp3"))),
                roots, true, url -> url);

        Set<String> failed = DownloadStorageHelper.deleteRecordedUris(context, plan.urisToDelete, roots);

        assertTrue(failed.isEmpty());
        assertFalse(new File(root, "A/01.mp3").exists());
        assertTrue(new File(root, "A/10.mp3").isFile());
    }

    @Test
    public void aRootFolderIsNeverPrunedEvenWhenEmpty() {
        File only = new File(root, "other/1.mp3");
        Set<String> failed = DownloadStorageHelper.deleteRecordedUris(context,
                Collections.singletonList(only.getAbsolutePath()), Collections.singletonList(new File(root, "other").getAbsolutePath()));

        assertTrue(failed.isEmpty());
        assertFalse(only.exists());
        assertTrue(new File(root, "other").isDirectory());
    }

    /**
     * DATA-01 regression list with real neighbours: one title from two sources, two readers of it, chapters 1/10/01
     * side by side, unrelated sibling files and a root named like the title. Deleting one book's recorded copies
     * changes no other byte, prunes its emptied folders and never the root.
     */
    @Test
    public void deletingOneBookLeavesEveryNeighbourByteForByte() throws Exception {
        File titleRoot = new File(base, "Мастер и Маргарита");
        List<String> titleRoots = Collections.singletonList(titleRoot.getAbsolutePath());
        String book = "Akniga/Мастер и Маргарита";
        List<String> mine = Arrays.asList(book + "/Козий/001 - 1.mp3", book + "/Козий/002 - 10.mp3",
                book + "/Козий/003 - 01.mp3");
        List<String> others = Arrays.asList(book + "/Ярмольник/001 - 1.mp3", "KnigaVUhe/Мастер и Маргарита/Козий/001 - 1.mp3",
                "Akniga/Мастер и Маргарита.mp3", "1.mp3", "10.mp3", "01.mp3");
        int fill = 0;
        for (String path : concat(mine, others)) write(new File(titleRoot, path), ++fill);
        Map<String, String> before = hashes(titleRoot, others);

        List<Row> rows = new ArrayList<>();
        for (String path : mine) {
            rows.add(new Row(BOOK_A, "https://cdn.akniga.org/m/" + new File(path).getName(),
                    new File(titleRoot, path).getAbsolutePath()));
        }
        Plan plan = DeletionPlanner.plan(rows, titleRoots, false, url -> url);
        Set<String> failed = DownloadStorageHelper.deleteRecordedUris(context, plan.urisToDelete, titleRoots);

        assertTrue(failed.isEmpty());
        for (String path : mine) assertFalse(path, new File(titleRoot, path).exists());
        assertFalse(new File(titleRoot, book + "/Козий").exists());
        assertTrue(new File(titleRoot, book).isDirectory());
        assertEquals(before, hashes(titleRoot, others));

        // The other reader goes too: its reader and book folders are pruned, the source keeps its sibling file.
        String reader2 = new File(titleRoot, book + "/Ярмольник/001 - 1.mp3").getAbsolutePath();
        assertTrue(DownloadStorageHelper.deleteRecordedUris(context, Collections.singletonList(reader2), titleRoots).isEmpty());
        assertFalse(new File(titleRoot, book).exists());
        assertTrue(new File(titleRoot, "Akniga/Мастер и Маргарита.mp3").isFile());
        assertTrue(titleRoot.isDirectory());
    }

    /** Cleanup after failures: a book whose copy cannot be deleted stays saved with exactly that chapter row. */
    @Test
    public void aBookWithAnUndeletableCopyStaysSavedWithThatRow() throws Exception {
        String bookUrl = "https://akniga.org/ws3b-partial-" + System.nanoTime();
        String url1 = "https://cdn.akniga.org/partial/1.mp3";
        String url2 = "https://cdn.akniga.org/partial/2.mp3";
        BooksDBModel books = new BooksDBModel(context);
        AudioListDBModel model = new AudioListDBModel(context);
        BookPOJO book = new BookPOJO();
        book.setUrl(bookUrl);
        book.setName("Partial");
        File undeletable = new File(root, "A");      // a folder with entries: File.delete() fails
        File deletable = new File(root, "other/1.mp3");
        try {
            books.addSaved(book);
            model.syncChapters(bookUrl, Arrays.asList(chapter(bookUrl, "1", url1), chapter(bookUrl, "2", url2)));
            model.getDatabase().booksAudioDao().updateLocalUri(bookUrl, url1, undeletable.getAbsolutePath());
            model.getDatabase().booksAudioDao().updateLocalUri(bookUrl, url2, deletable.getAbsolutePath());
            final int[] result = new int[3];
            final CountDownLatch done = new CountDownLatch(1);

            DownloadStorageHelper.removeBooks(context, Collections.singletonList(bookUrl), (removed, total, failed) -> {
                result[0] = removed;
                result[1] = total;
                result[2] = failed;
                done.countDown();
            });

            assertTrue(done.await(30, TimeUnit.SECONDS));
            assertArrayEquals(new int[]{0, 1, 1}, result);
            assertNotNull(books.getSaved(bookUrl));
            List<BooksAudioEntity> rows = model.getRows(bookUrl);
            assertEquals(1, rows.size());
            assertEquals(url1, rows.get(0).urlAudio);
            assertEquals(undeletable.getAbsolutePath(), rows.get(0).localUri);
            assertFalse(deletable.exists());
            assertTrue(new File(root, "A/01.mp3").isFile());
        } finally {
            model.remove(bookUrl);
            books.removeSavedByUrl(bookUrl);
        }
    }

    private static AudioListPOJO chapter(String bookUrl, String name, String url) {
        AudioListPOJO chapter = new AudioListPOJO();
        chapter.setBookUrl(bookUrl);
        chapter.setBookName("Partial");
        chapter.setAudioName(name);
        chapter.setAudioUrl(url);
        return chapter;
    }

    private static List<String> concat(List<String> a, List<String> b) {
        List<String> all = new ArrayList<>(a);
        all.addAll(b);
        return all;
    }

    private static void write(File file, int fill) throws IOException {
        assertTrue(file.getParentFile().isDirectory() || file.getParentFile().mkdirs());
        byte[] data = new byte[64 + fill];
        Arrays.fill(data, (byte) fill);
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(data);
        }
    }

    private static Map<String, String> hashes(File dir, List<String> paths) throws Exception {
        Map<String, String> result = new LinkedHashMap<>();
        for (String path : paths) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream in = new FileInputStream(new File(dir, path))) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) > 0) digest.update(buffer, 0, read);
            }
            StringBuilder hex = new StringBuilder();
            for (byte b : digest.digest()) hex.append(String.format("%02x", b));
            result.put(path, hex.toString());
        }
        return result;
    }

    private String file(String relative) {
        return new File(root, relative).getAbsolutePath();
    }

    private static void deleteTree(File file) {
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) deleteTree(child);
        }
        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }
}
