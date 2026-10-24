package com.fanok.audiobooks.util;

import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Pure naming rules for library storage: natural chapter order and safe export file names. */
public final class StorageNames {

    /** Keeps "NNN - title.ext" well below the 255-byte limit of FAT/ext4 and SAF providers. */
    static final int MAX_NAME_BYTES = 120;

    private static final String RESERVED = "\\/:*?\"<>|";

    private static final Set<String> DOS_DEVICES = new HashSet<>(Arrays.asList(
            "CON", "PRN", "AUX", "NUL",
            "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
            "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9"));

    private static final Set<String> AUDIO_EXTENSIONS = new HashSet<>(Arrays.asList(
            "mp3", "m4a", "m4b", "aac", "ogg", "opus", "flac", "wav", "mp4"));

    /** "1" < "2" < "10", "Глава 2" < "Глава 10"; "01" equals "1" numerically and is tie-broken lexically. */
    public static final Comparator<String> NATURAL_ORDER = StorageNames::naturalCompare;

    private StorageNames() {
    }

    public static int naturalCompare(String a, String b) {
        if (a == null) a = "";
        if (b == null) b = "";
        int i = 0;
        int j = 0;
        while (i < a.length() && j < b.length()) {
            if (isDigit(a.charAt(i)) && isDigit(b.charAt(j))) {
                int endA = digitsEnd(a, i);
                int endB = digitsEnd(b, j);
                int startA = skipZeros(a, i, endA);
                int startB = skipZeros(b, j, endB);
                int byLength = Integer.compare(endA - startA, endB - startB);
                if (byLength != 0) return byLength;
                for (int k = 0; k < endA - startA; k++) {
                    int byDigit = Character.compare(a.charAt(startA + k), b.charAt(startB + k));
                    if (byDigit != 0) return byDigit;
                }
                i = endA;
                j = endB;
            } else {
                int byChar = Character.compare(Character.toLowerCase(a.charAt(i)), Character.toLowerCase(b.charAt(j)));
                if (byChar != 0) return byChar;
                i++;
                j++;
            }
        }
        int byRest = Integer.compare(a.length() - i, b.length() - j);
        return byRest != 0 ? byRest : a.compareTo(b);
    }

    /**
     * "NNN - <safe title>.<ext>" for the chapter at 1-based {@code index}. The index keeps chapters with equal or
     * unusable titles apart; the title part is dropped when nothing safe remains.
     */
    public static String exportFileName(int index, String title, String ext) {
        String extension = safeExtension(ext);
        String suffix = extension.isEmpty() ? "" : "." + extension;
        String name = title == null ? "" : title.trim();
        if (!suffix.isEmpty() && name.toLowerCase(Locale.ROOT).endsWith(suffix)) {
            name = name.substring(0, name.length() - suffix.length());
        }
        String prefix = String.format(Locale.ROOT, "%03d", Math.max(index, 0));
        String safe = safeName(name);
        safe = trimTrailingDotsAndSpaces(truncateUtf8(safe, MAX_NAME_BYTES - utf8Length(prefix + " - " + suffix)));
        return safe.isEmpty() ? prefix + suffix : prefix + " - " + safe + suffix;
    }

    /** The audio extension of a file name or URL path ("mp3" for "…/01.MP3?x=1"), or {@code fallback}. */
    public static String audioExtension(String nameOrUrl, String fallback) {
        if (nameOrUrl == null) return fallback;
        String s = nameOrUrl;
        int cut = indexOfAny(s, "?#");
        if (cut >= 0) s = s.substring(0, cut);
        s = s.substring(s.lastIndexOf('/') + 1);
        int dot = s.lastIndexOf('.');
        String ext = dot >= 0 ? s.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
        return AUDIO_EXTENSIONS.contains(ext) ? ext : fallback;
    }

    /**
     * One path segment without control or reserved characters and without trailing dots or spaces; "" for
     * "."/".." and DOS device names.
     */
    static String safeName(String s) {
        StringBuilder sb = new StringBuilder();
        for (int k = 0; k < s.length(); ) {
            int cp = s.codePointAt(k);
            k += Character.charCount(cp);
            if (Character.isISOControl(cp) || Character.getType(cp) == Character.FORMAT
                    || (cp <= 0xFFFF && Character.isSurrogate((char) cp)) || RESERVED.indexOf(cp) >= 0) {
                continue;
            }
            sb.appendCodePoint(cp);
        }
        String safe = trimTrailingDotsAndSpaces(sb.toString().trim());
        int dot = safe.indexOf('.');
        String stem = (dot >= 0 ? safe.substring(0, dot) : safe).trim().toUpperCase(Locale.ROOT);
        return DOS_DEVICES.contains(stem) || safe.equals(".") || safe.equals("..") ? "" : safe;
    }

    /** Longest prefix of {@code s} whose UTF-8 form fits {@code maxBytes}, never splitting a character. */
    static String truncateUtf8(String s, int maxBytes) {
        int bytes = 0;
        for (int k = 0; k < s.length(); ) {
            int cp = s.codePointAt(k);
            int len = utf8Length(cp);
            if (bytes + len > maxBytes) return s.substring(0, k);
            bytes += len;
            k += Character.charCount(cp);
        }
        return s;
    }

    static int utf8Length(String s) {
        int bytes = 0;
        for (int k = 0; k < s.length(); ) {
            int cp = s.codePointAt(k);
            bytes += utf8Length(cp);
            k += Character.charCount(cp);
        }
        return bytes;
    }

    private static int utf8Length(int cp) {
        return cp < 0x80 ? 1 : cp < 0x800 ? 2 : cp < 0x10000 ? 3 : 4;
    }

    private static String safeExtension(String ext) {
        if (ext == null) return "";
        StringBuilder sb = new StringBuilder();
        for (char c : ext.toCharArray()) {
            if (c < 0x80 && Character.isLetterOrDigit(c)) sb.append(Character.toLowerCase(c));
        }
        return sb.length() <= 5 ? sb.toString() : "";
    }

    private static String trimTrailingDotsAndSpaces(String s) {
        int end = s.length();
        while (end > 0 && (s.charAt(end - 1) == '.' || Character.isWhitespace(s.charAt(end - 1)))) end--;
        return s.substring(0, end);
    }

    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private static int digitsEnd(String s, int from) {
        int k = from;
        while (k < s.length() && isDigit(s.charAt(k))) k++;
        return k;
    }

    private static int skipZeros(String s, int from, int end) {
        int k = from;
        while (k < end - 1 && s.charAt(k) == '0') k++;
        return k;
    }

    private static int indexOfAny(String s, String chars) {
        for (int k = 0; k < s.length(); k++) {
            if (chars.indexOf(s.charAt(k)) >= 0) return k;
        }
        return -1;
    }
}
