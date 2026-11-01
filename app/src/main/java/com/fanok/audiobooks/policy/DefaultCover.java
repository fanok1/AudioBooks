package com.fanok.audiobooks.policy;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import com.fanok.audiobooks.R;
import com.fanok.audiobooks.Url;
import com.fanok.audiobooks.search.Provider;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Covers that never load and show a default cover instead: audioboo's Cloudflare answers every GET, its cover
 * images included, with a challenge. Each book gets the next of a few muted gradients, so neighbours in a list
 * differ, and keeps it for the process lifetime, so its book page and full-screen view match its list item.
 */
public final class DefaultCover {

    /** Top-left, middle and bottom-right gradient stops: hues apart from their neighbours, all equally muted. */
    private static final int[][] PALETTES = {
            {0xFF5E6D83, 0xFF414E61, 0xFF283240}, // slate
            {0xFF8A6B60, 0xFF664B41, 0xFF432F28}, // terracotta
            {0xFF547874, 0xFF395955, 0xFF233B38}, // teal
            {0xFF7E6783, 0xFF5D4961, 0xFF3D2E40}, // plum
            {0xFF6A7C5A, 0xFF4C5C3E, 0xFF313D27}, // moss
            {0xFF86626B, 0xFF63444C, 0xFF422A30}, // wine
            {0xFF6E6A8A, 0xFF4E4A66, 0xFF322F43}, // indigo
            {0xFF86785B, 0xFF63573E, 0xFF423825}, // ochre
    };

    private static final int REMEMBERED = 512;

    /** Book → its palette, the least recently shown forgotten first. */
    private static final Map<String, Integer> PALETTE_OF = new LinkedHashMap<String, Integer>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Integer> eldest) {
            return size() > REMEMBERED;
        }
    };

    private static int next;

    private DefaultCover() {
    }

    /** Whether the default cover shows instead of {@code photo}, without trying to load it. */
    public static boolean replaces(@Nullable String photo) {
        return Provider.ofUrl(photo) == Provider.AUDIOBOO;
    }

    /** The default cover of the book {@code key} (its page URL, else its cover URL). */
    @NonNull
    public static Drawable drawable(@NonNull Context context, @Nullable String key) {
        GradientDrawable background = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                PALETTES[paletteOf(key)]);
        Drawable overlay = ContextCompat.getDrawable(context, R.drawable.default_cover_overlay);
        return new LayerDrawable(new Drawable[]{background, overlay});
    }

    static synchronized int paletteOf(@Nullable String key) {
        String book = key == null ? "" : Url.canonical(key);
        Integer palette = PALETTE_OF.get(book);
        if (palette == null) {
            palette = next;
            next = (next + 1) % PALETTES.length;
            PALETTE_OF.put(book, palette);
        }
        return palette;
    }
}
