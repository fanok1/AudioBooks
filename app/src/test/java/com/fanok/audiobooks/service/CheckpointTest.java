package com.fanok.audiobooks.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

public class CheckpointTest {

    private static final String BOOK = "https://akniga.org/some-book|with-a-pipe";

    @Test
    public void serializeAndParse_roundTripKeepsEveryField() {
        Checkpoint checkpoint = new Checkpoint(BOOK, 7, 123_456L);
        assertEquals(checkpoint, Checkpoint.parse(checkpoint.serialize()));
    }

    @Test
    public void parse_rejectsAbsentMalformedAndNegativeRecords() {
        assertNull(Checkpoint.parse(null));
        assertNull(Checkpoint.parse(""));
        assertNull(Checkpoint.parse("1|3|1000"));
        assertNull(Checkpoint.parse("2|3|1000|" + BOOK));
        assertNull(Checkpoint.parse("1|x|1000|" + BOOK));
        assertNull(Checkpoint.parse("1|-1|1000|" + BOOK));
        assertNull(Checkpoint.parse("1|3|-5|" + BOOK));
        assertNull(Checkpoint.parse("1|3|1000|"));
    }

    @Test
    public void clampTo_indexPastTheEndSelectsLastChapterFromItsStart() {
        Checkpoint saved = new Checkpoint(BOOK, 9, 60_000);
        assertEquals(new Checkpoint(BOOK, 2, 0), saved.clampTo(3));
    }

    @Test
    public void clampTo_validIndexKeepsTheOffset() {
        Checkpoint saved = new Checkpoint(BOOK, 2, 60_000);
        assertSame(saved, saved.clampTo(3));
        assertEquals(new Checkpoint(BOOK, 0, 0), saved.clampTo(0));
    }

    @Test
    public void constructor_neverHoldsNegativeValues() {
        Checkpoint checkpoint = new Checkpoint(BOOK, -4, -1);
        assertEquals(0, checkpoint.chapterIndex);
        assertEquals(0, checkpoint.positionMs);
    }

    @Test
    public void read_ignoresLegacyTitleIndexAndSecondsKeys() {
        Map<String, Object> prefs = new HashMap<>();
        prefs.put("urlBook", BOOK);
        prefs.put("audioIndex", 4);
        prefs.put("timeStart", 600);
        prefs.put("audioArrayList", "[{\"name\":\"Глава 5\"}]");
        assertNull(Checkpoint.read(prefs, BOOK));

        prefs.put(Checkpoint.key(BOOK), new Checkpoint(BOOK, 1, 2_000).serialize());
        assertEquals(new Checkpoint(BOOK, 1, 2_000), Checkpoint.read(prefs, BOOK));
    }

    @Test
    public void read_ignoresARecordOfAnotherBookUnderThisKey() {
        Map<String, Object> prefs = new HashMap<>();
        prefs.put(Checkpoint.key(BOOK), new Checkpoint("https://other.example/book", 1, 2_000).serialize());
        assertNull(Checkpoint.read(prefs, BOOK));
    }
}
