package com.fanok.audiobooks.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import androidx.media3.common.util.UnstableApi;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.fanok.audiobooks.download.DownloadIds;
import com.fanok.audiobooks.pojo.AudioListPOJO;
import com.fanok.audiobooks.room.BooksAudioEntity;
import com.fanok.audiobooks.util.DownloadUtil;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;

/** DL-06: a row kept only for its exported copy leaves the ordinals of the list and never shares a chapter id. */
@UnstableApi
@RunWith(AndroidJUnit4.class)
public class SyncChaptersRetainedTest {

    @Test
    public void aRowKeptForItsCopyGetsAUniqueNegativeOrdinalAndItsLegacyIdOnly() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        AudioListDBModel model = new AudioListDBModel(context);
        String book = "https://akniga.org/ws3b-retained-" + System.nanoTime();
        String u1 = "https://cdn.akniga.org/retained/1.mp3";
        String u2 = "https://cdn.akniga.org/retained/2.mp3";
        String u3 = "https://cdn.akniga.org/retained/3.mp3";
        try {
            model.syncChapters(book, Arrays.asList(chapter(book, "1", u1), chapter(book, "2", u2), chapter(book, "3", u3)));
            model.getDatabase().booksAudioDao().updateLocalUri(book, u2, "/storage/emulated/0/x/002 - 2.mp3");
            // Chapter 2 leaves the list; chapter 3 now sits at the position chapter 2 had.
            model.syncChapters(book, Arrays.asList(chapter(book, "1", u1), chapter(book, "3", u3)));

            List<BooksAudioEntity> rows = model.getRows(book);
            assertEquals(3, rows.size());
            BooksAudioEntity kept = null;
            for (BooksAudioEntity row : rows) if (u2.equals(row.urlAudio)) kept = row;
            assertNotNull(kept);
            assertEquals(-kept.id - 1, kept.ordinal);
            assertTrue(kept.ordinal < -1);

            List<DownloadIds.Entry> entries = DownloadUtil.entriesOf(book, rows);
            Set<String> ids = new HashSet<>();
            for (DownloadIds.Entry e : entries) assertTrue(e.id, ids.add(e.id));
            assertEquals(DownloadIds.legacyId(u2), entries.get(rows.indexOf(kept)).id);
            assertTrue(ids.contains(DownloadIds.chapterId(book, 1)));
        } finally {
            model.remove(book);
        }
    }

    private static AudioListPOJO chapter(String book, String name, String url) {
        AudioListPOJO chapter = new AudioListPOJO();
        chapter.setBookUrl(book);
        chapter.setBookName("Retained");
        chapter.setAudioName(name);
        chapter.setAudioUrl(url);
        return chapter;
    }
}
