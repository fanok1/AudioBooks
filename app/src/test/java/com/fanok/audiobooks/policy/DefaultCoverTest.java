package com.fanok.audiobooks.policy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class DefaultCoverTest {

    @Test
    public void audiobooCoversAreReplaced() {
        assertTrue(DefaultCover.replaces("https://audioboo.org/uploads/posts/2024-01/cover.jpg"));
        assertTrue(DefaultCover.replaces("https://www.audioboo.org/uploads/cover.webp"));
        // saved before the move to audioboo.org
        assertTrue(DefaultCover.replaces("https://audioboo.ru/uploads/cover.jpg"));
    }

    @Test
    public void otherCoversAreLoaded() {
        assertFalse(DefaultCover.replaces("https://izib.uk/covers/1.jpg"));
        assertFalse(DefaultCover.replaces("content://com.android.providers/cover/1"));
        assertFalse(DefaultCover.replaces("/storage/emulated/0/AudioBooks/cover.jpg"));
        assertFalse(DefaultCover.replaces(""));
        assertFalse(DefaultCover.replaces(null));
    }

    @Test
    public void newBooksTakeTheNextPaletteInTurn() {
        int first = DefaultCover.paletteOf("https://audioboo.org/turn/1.html");
        for (int i = 1; i < 10; i++) {
            assertEquals((first + i) % 8, DefaultCover.paletteOf("https://audioboo.org/turn/" + (i + 1) + ".html"));
        }
    }

    @Test
    public void aBookKeepsItsPalette() {
        int palette = DefaultCover.paletteOf("https://audioboo.org/keep/1.html");
        DefaultCover.paletteOf("https://audioboo.org/keep/2.html");
        assertEquals(palette, DefaultCover.paletteOf("https://audioboo.org/keep/1.html"));
        // the same book under the host it had before
        assertEquals(palette, DefaultCover.paletteOf("https://audioboo.ru/keep/1.html"));
    }
}
