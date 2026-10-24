package com.fanok.audiobooks.service;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.Map;

/**
 * Where a book was left: the chapter by its position in the book's chapter list, and the offset in it.
 * Immutable, so a snapshot taken on the player thread can be written later without reading service fields.
 * Resume reads only this record; the legacy chapter-title and seconds fields are written for compatibility
 * and never read back for resume.
 */
public final class Checkpoint {

    /** Key of the most recently written checkpoint, whatever its book. */
    public static final String KEY_LAST = "checkpoint_last";

    private static final String KEY_PREFIX = "checkpoint:";

    private static final String VERSION = "1";

    @NonNull
    public final String bookUrl;

    public final int chapterIndex;

    public final long positionMs;

    public Checkpoint(@NonNull String bookUrl, int chapterIndex, long positionMs) {
        this.bookUrl = bookUrl;
        this.chapterIndex = Math.max(0, chapterIndex);
        this.positionMs = Math.max(0, positionMs);
    }

    /** Preference key of the checkpoint of {@code bookUrl}. */
    @NonNull
    public static String key(@NonNull String bookUrl) {
        return KEY_PREFIX + bookUrl;
    }

    @NonNull
    public String serialize() {
        return VERSION + "|" + chapterIndex + "|" + positionMs + "|" + bookUrl;
    }

    /** @return the checkpoint, or null when {@code raw} is absent or not a valid record. */
    @Nullable
    public static Checkpoint parse(@Nullable String raw) {
        if (raw == null) {
            return null;
        }
        String[] parts = raw.split("\\|", 4);
        if (parts.length != 4 || !VERSION.equals(parts[0]) || parts[3].isEmpty()) {
            return null;
        }
        try {
            int index = Integer.parseInt(parts[1]);
            long position = Long.parseLong(parts[2]);
            if (index < 0 || position < 0) {
                return null;
            }
            return new Checkpoint(parts[3], index, position);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** The checkpoint of {@code bookUrl} among stored preference values; any other key is ignored. */
    @Nullable
    public static Checkpoint read(@NonNull Map<String, ?> values, @NonNull String bookUrl) {
        Object raw = values.get(key(bookUrl));
        Checkpoint checkpoint = raw instanceof String ? parse((String) raw) : null;
        return checkpoint != null && checkpoint.bookUrl.equals(bookUrl) ? checkpoint : null;
    }

    /**
     * This checkpoint for a book of {@code chapterCount} chapters: an index past the end selects the last
     * chapter and, since that is not the chapter the offset was measured in, starts it from the beginning.
     */
    @NonNull
    public Checkpoint clampTo(int chapterCount) {
        if (chapterCount <= 0) {
            return new Checkpoint(bookUrl, 0, 0);
        }
        if (chapterIndex < chapterCount) {
            return this;
        }
        return new Checkpoint(bookUrl, chapterCount - 1, 0);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Checkpoint)) {
            return false;
        }
        Checkpoint other = (Checkpoint) o;
        return chapterIndex == other.chapterIndex && positionMs == other.positionMs
                && bookUrl.equals(other.bookUrl);
    }

    @Override
    public int hashCode() {
        return (bookUrl.hashCode() * 31 + chapterIndex) * 31 + (int) (positionMs ^ (positionMs >>> 32));
    }

    @NonNull
    @Override
    public String toString() {
        return serialize();
    }
}
