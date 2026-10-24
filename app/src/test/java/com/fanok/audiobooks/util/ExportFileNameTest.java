package com.fanok.audiobooks.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import org.junit.Test;

public class ExportFileNameTest {

    @Test
    public void indexPrefixAndTitle() {
        assertEquals("001 - Глава 1.mp3", StorageNames.exportFileName(1, "Глава 1", "mp3"));
        assertEquals("012 - Пролог.m4a", StorageNames.exportFileName(12, " Пролог ", ".m4a"));
        assertEquals("1000 - x.mp3", StorageNames.exportFileName(1000, "x", "mp3"));
        assertEquals("007 - intro.mp3", StorageNames.exportFileName(7, "intro.mp3", "mp3"));
    }

    @Test
    public void unusableTitlesFallBackToTheIndex() {
        assertEquals("001.mp3", StorageNames.exportFileName(1, "", "mp3"));
        assertEquals("002.mp3", StorageNames.exportFileName(2, null, "mp3"));
        assertEquals("003.mp3", StorageNames.exportFileName(3, "..", "mp3"));
        assertEquals("004.mp3", StorageNames.exportFileName(4, ".", "mp3"));
        assertEquals("005.mp3", StorageNames.exportFileName(5, "CON", "mp3"));
        assertEquals("006.mp3", StorageNames.exportFileName(6, "lpt1.txt", "mp3"));
        assertEquals("007.mp3", StorageNames.exportFileName(7, "???", "mp3"));
    }

    @Test
    public void controlAndReservedCharactersAndTrailingDotsAreRemoved() {
        assertEquals("001 - abc.mp3", StorageNames.exportFileName(1, "a\u0000b\tc", "mp3"));
        assertEquals("002 - Конец.mp3", StorageNames.exportFileName(2, "Конец. . .", "mp3"));
        assertEquals("003 - Часть 1 Начало.mp3", StorageNames.exportFileName(3, "Часть 1: Начало", "mp3"));
    }

    @Test
    public void reservedCharactersNeverMakeTwoChaptersShareAName() {
        String slash = StorageNames.exportFileName(5, "A/B", "mp3");
        String colon = StorageNames.exportFileName(6, "A:B", "mp3");
        assertNotEquals(slash, colon);
        for (String name : new String[]{slash, colon}) {
            assertFalse(name, name.contains("/") || name.contains(":"));
        }
    }

    @Test
    public void longCyrillicTitleIsCappedWithoutSplittingACharacter() {
        StringBuilder title = new StringBuilder();
        while (title.length() < 300) title.append("Ж");
        String name = StorageNames.exportFileName(4, title.toString(), "mp3");

        byte[] bytes = name.getBytes(StandardCharsets.UTF_8);
        assertTrue(bytes.length <= StorageNames.MAX_NAME_BYTES);
        assertEquals(name, new String(bytes, StandardCharsets.UTF_8));
        assertTrue(name.startsWith("004 - Ж"));
        assertTrue(name.endsWith("Ж.mp3"));
    }

    @Test
    public void audioExtensionComesFromTheUrlPath() {
        assertEquals("mp3", StorageNames.audioExtension("https://h.org/a/01.MP3?x=1.ogg", "mp3"));
        assertEquals("m4a", StorageNames.audioExtension("https://h.org/a/01.m4a#t=1", "mp3"));
        assertEquals("mp3", StorageNames.audioExtension("https://h.org/play?id=3", "mp3"));
        assertEquals("mp3", StorageNames.audioExtension("https://h.org/a.b/track", "mp3"));
    }
}
