package com.fanok.audiobooks.net;

/**
 * Cuts one balanced JSON/JS literal ({@code {…}} or {@code […]}) out of a page script, skipping
 * brackets inside quoted strings. Pure; a missing marker or an unbalanced literal throws a
 * descriptive {@link IllegalStateException} instead of an index error.
 */
public final class JsonSlice {

    private JsonSlice() {
    }

    /**
     * The literal that opens with the first {@code open} after the first {@code marker} in live code:
     * a marker inside a {@code /* … *}{@code /} or {@code //} comment (a commented-out player) is ignored.
     */
    public static String after(String text, String marker, char open) {
        String code = stripComments(text);
        int at = code.indexOf(marker);
        if (at < 0) {
            throw new IllegalStateException("marker not found: " + marker);
        }
        return from(code, at + marker.length(), open);
    }

    /**
     * The literal that opens with the first {@code open} ({@code '{'} or {@code '['}) at or after
     * {@code start} outside string literals, so {@code ("book[1]", [...])} yields the list.
     */
    public static String from(String text, int start, char open) {
        if (open != '{' && open != '[') {
            throw new IllegalArgumentException("open must be '{' or '[': " + open);
        }
        int begin = -1;
        int depth = 0;
        char quote = 0;
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quote != 0) {
                if (c == '\\') {
                    i++;
                } else if (c == quote) {
                    quote = 0;
                }
            } else if (c == '"' || c == '\'') {
                quote = c;
            } else if (begin < 0) {
                if (c == open) {
                    begin = i;
                    depth = 1;
                }
            } else if (c == '{' || c == '[') {
                depth++;
            } else if ((c == '}' || c == ']') && --depth == 0) {
                return text.substring(begin, i + 1);
            }
        }
        throw new IllegalStateException(begin < 0 ? "no '" + open + "' after index " + start
                : "unbalanced '" + open + "' at index " + begin);
    }

    /**
     * {@code script} without its {@code /* … *}{@code /} and {@code //} comments; string literals
     * (including ones holding {@code //}, as URLs do) are kept as they are.
     */
    public static String stripComments(String script) {
        StringBuilder out = new StringBuilder(script.length());
        char quote = 0;
        for (int i = 0; i < script.length(); i++) {
            char c = script.charAt(i);
            char next = i + 1 < script.length() ? script.charAt(i + 1) : 0;
            if (quote != 0) {
                out.append(c);
                if (c == '\\' && next != 0) {
                    out.append(next);
                    i++;
                } else if (c == quote || c == '\n' && quote != '`') {
                    quote = 0; // an unterminated '…' or "…" ends with its line
                }
            } else if (c == '/' && next == '*') {
                int end = script.indexOf("*/", i + 2);
                if (end < 0) {
                    break;
                }
                out.append(' ');
                i = end + 1;
            } else if (c == '/' && next == '/') {
                int end = script.indexOf('\n', i);
                if (end < 0) {
                    break;
                }
                i = end - 1;
            } else {
                if (c == '"' || c == '\'' || c == '`') {
                    quote = c;
                }
                out.append(c);
            }
        }
        return out.toString();
    }
}
