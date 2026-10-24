package com.fanok.audiobooks.download;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class DownloadIdsTest {

    private static final String BOOK = "https://baza-knig.top/books/123-master/";

    @Test
    public void queryAddressedChaptersOfOneBookGetDistinctIds() {
        List<DownloadIds.Entry> e = DownloadIds.forBook(BOOK, Arrays.asList(
                "https://baza-knig.top/get.php?file=3", "https://baza-knig.top/get.php?file=4"), new int[]{2, 3});

        assertNotEquals(e.get(0).id, e.get(1).id);
        assertEquals("v1/baza-knig.top/books/123-master/chapter/2", e.get(0).id);
        assertEquals(e.get(0).id, e.get(0).cacheKey);
        // The legacy ids collide, which is why they are only a fallback for old downloads.
        assertEquals(e.get(0).legacyId, e.get(1).legacyId);
        assertTrue(e.get(0).legacyShared);
    }

    @Test
    public void schemeTrailingSlashAndWwwVariantsOfABookGiveOneId() {
        String id = DownloadIds.chapterId(BOOK, 0);
        for (String variant : Arrays.asList("http://baza-knig.top/books/123-master/", "https://baza-knig.top/books/123-master",
                "https://www.baza-knig.top/books/123-master//", "HTTPS://WWW.Baza-Knig.top/books/123-master#comments")) {
            assertEquals(variant, id, DownloadIds.chapterId(variant, 0));
        }
        assertNotEquals(id, DownloadIds.chapterId("https://baza-knig.top/books/1234-master/", 0));
    }

    @Test
    public void movedHostGivesTheSameIdAsTheCurrentHost() {
        assertEquals(DownloadIds.chapterId(BOOK, 5), DownloadIds.chapterId("https://baza-knig.ink/books/123-master/", 5));
        assertTrue(DownloadIds.belongsTo(DownloadIds.chapterId(BOOK, 5), "http://www.baza-knig.ink/books/123-master"));
        assertFalse(DownloadIds.belongsTo(DownloadIds.chapterId(BOOK, 5), "https://baza-knig.top/books/123-maste"));
    }

    @Test
    public void hlsChaptersShareOneStreamDownloadWithoutCustomCacheKey() {
        String hls = "https://r1.akniga.club/b/111286/pl.m3u8?res=abc";
        String book = "https://akniga.org/bulgakov-master";
        List<DownloadIds.Entry> e = DownloadIds.forBook(book,
                Arrays.asList(hls, hls, "https://cdn.akniga.org/extra.mp3", hls, "https://r2.akniga.club/b/1/PL.M3U8"),
                new int[]{0, 1, 2, 3, 4});

        assertEquals("v1/akniga.org/bulgakov-master/stream/r1.akniga.club/b/111286/pl.m3u8", e.get(0).id);
        assertEquals(e.get(0).id, e.get(1).id);
        assertEquals(e.get(0).id, e.get(3).id);
        assertNull(e.get(0).cacheKey);
        assertTrue(e.get(0).hls);
        assertEquals("v1/akniga.org/bulgakov-master/chapter/2", e.get(2).id);
        assertEquals("v1/akniga.org/bulgakov-master/stream/r2.akniga.club/b/1/PL.M3U8", e.get(4).id);
        assertFalse(e.get(0).legacyShared);
    }

    @Test
    public void theBookKeyIsReadBackFromV1IdsOnly() {
        String book = "https://akniga.org/bulgakov-master";
        assertEquals(DownloadIds.bookKey(BOOK), DownloadIds.bookKeyOfId(DownloadIds.chapterId(BOOK, 12)));
        assertEquals("akniga.org/bulgakov-master",
                DownloadIds.bookKeyOfId(DownloadIds.streamId(book, "https://cdn.x/stream/b/1/chapter/2/pl.m3u8")));
        // a book path holding "/chapter/" itself: the id's own suffix is the ordinal at its end
        assertEquals("x.org/chapter/5/book", DownloadIds.bookKeyOfId(DownloadIds.chapterId("https://x.org/chapter/5/book/", 3)));
        assertNull(DownloadIds.bookKeyOfId(DownloadIds.legacyId("https://h/old.mp3")));
        assertNull(DownloadIds.bookKeyOfId("v1/akniga.org/bulgakov-master/chapter/x"));
        assertNull(DownloadIds.bookKeyOfId(null));
    }

    @Test
    public void aStreamKeepsItsIdWhenAnEarlierStreamIsDropped() {
        String book = "https://akniga.org/bulgakov-master";
        String a = "https://r1.akniga.club/b/1/a.m3u8?res=1";
        String b = "https://r1.akniga.club/b/1/b.m3u8?res=1";
        String bLater = "https://r1.akniga.club/b/1/b.m3u8?res=2";
        // [A, A, B, B] then [B, B]: syncChapters renumbers B's chapters 0 and 1, its new token changes the query.
        String before = DownloadIds.forBook(book, Arrays.asList(a, a, b, b), new int[]{0, 1, 2, 3}).get(2).id;
        List<DownloadIds.Entry> after = DownloadIds.forBook(book, Arrays.asList(bLater, bLater), new int[]{0, 1});

        assertEquals(before, after.get(0).id);
        assertNotEquals(DownloadIds.forBook(book, Arrays.asList(a), new int[]{0}).get(0).id, after.get(0).id);
        // A stream download is accepted only for a request of the same host and path.
        assertTrue(DownloadIds.holdsCurrent(after.get(0), bLater, b));
        assertFalse(DownloadIds.holdsCurrent(after.get(0), bLater, a));
    }

    @Test
    public void aRetainedRowHasItsLegacyIdOnlyAndNeverTakesPositionZero() {
        List<DownloadIds.Entry> e = DownloadIds.forBook(BOOK, Arrays.asList("https://h/old.mp3", "https://h/new.mp3"),
                new int[]{-8, 0});

        assertEquals("https://h/old.mp3", e.get(0).id);
        assertNull(e.get(0).cacheKey);
        assertEquals(DownloadIds.chapterId(BOOK, 0), e.get(1).id);
        assertNotEquals(e.get(1).id, e.get(0).id);
    }

    @Test
    public void onlyTheChapterWhoseUrlMadeASharedLegacyDownloadOwnsIt() {
        String one = "https://baza-knig.top/get.php?file=1";
        String two = "https://baza-knig.top/get.php?file=2";
        List<DownloadIds.Entry> e = DownloadIds.forBook(BOOK, Arrays.asList(one, two), new int[]{0, 1});
        Map<String, String> stored = Collections.singletonMap("https://baza-knig.top/get.php", two);

        assertTrue(DownloadIds.ownedIds(e.get(0), one, stored).isEmpty());
        assertEquals(Collections.singletonList("https://baza-knig.top/get.php"), DownloadIds.ownedIds(e.get(1), two, stored));
    }

    @Test
    public void missingOrdinalFallsBackToThePosition() {
        List<DownloadIds.Entry> e = DownloadIds.forBook(BOOK, Arrays.asList("https://h/1.mp3", "https://h/2.mp3"), new int[]{-1, -1});
        assertEquals(DownloadIds.chapterId(BOOK, 1), e.get(1).id);
    }

    @Test
    public void legacyIdIsTheCleanUrlAsBefore() {
        assertEquals("https://cdn.akniga.org/m/01.mp3", DownloadIds.legacyId("https://cdn.akniga.org/m/01.mp3?token=a%25b_c#t"));
        assertEquals("https://baza-knig.top/get.php", DownloadIds.legacyId("https://baza-knig.top/get.php?file=3"));
        // android.net.Uri.Builder re-encodes the decoded path: raw Cyrillic and spaces become %XX, '/' stays.
        assertEquals("https://h.org/%D0%93%D0%BB%D0%B0%D0%B2%D0%B0%201.mp3", DownloadIds.legacyId("https://h.org/Глава 1.mp3"));
        assertEquals("https://h.org/%D0%93%201.mp3", DownloadIds.legacyId("https://h.org/%d0%93%201.mp3"));
        assertEquals("/storage/a.mp3", DownloadIds.legacyId("/storage/a.mp3"));
    }

    @Test
    public void rowsAlignByUrlAndOccurrence() {
        List<String> rows = Arrays.asList("a", "s", "s", "b");
        assertArrayEquals(new int[]{1, 3, 2, -1}, DownloadIds.align(rows, Arrays.asList("s", "b", "s", "c")));
    }
}
