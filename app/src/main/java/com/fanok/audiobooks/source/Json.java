package com.fanok.audiobooks.source;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

/**
 * What every JSON a source reads needs: a player config, an XHR playlist or a JSON API page ({@code JsonPage.of}).
 * Absent or mistyped values read as "" or 0; a document of the wrong shape is an {@link IllegalStateException}, like
 * every other "page is not what the site serves" case. Pure and static.
 */
public final class Json {

    private Json() {
    }

    /** {@code text} parsed; malformed text is an {@link IllegalStateException}. */
    public static JsonElement parse(String text) {
        try {
            return JsonParser.parseString(text);
        } catch (JsonParseException e) {
            throw new IllegalStateException("malformed JSON", e);
        }
    }

    public static JsonArray array(String text) {
        JsonElement e = parse(text);
        if (!e.isJsonArray()) {
            throw new IllegalStateException("JSON is not a list");
        }
        return e.getAsJsonArray();
    }

    public static JsonObject object(String text) {
        JsonElement e = parse(text);
        if (!e.isJsonObject()) {
            throw new IllegalStateException("JSON is not an object");
        }
        return e.getAsJsonObject();
    }

    /** The list under {@code key}; an {@link IllegalStateException} when there is none. */
    public static JsonArray arrayOf(JsonObject o, String key) {
        JsonElement e = o.get(key);
        if (e == null || !e.isJsonArray()) {
            throw new IllegalStateException("JSON has no '" + key + "' list");
        }
        return e.getAsJsonArray();
    }

    public static String string(JsonObject o, String key) {
        return string(o.get(key));
    }

    /** The text of a string or number; "" for anything else. */
    public static String string(JsonElement e) {
        return e != null && e.isJsonPrimitive() ? e.getAsString() : "";
    }

    /** Whole seconds of a number or numeric string ("12.5" → 12); 0 when absent, malformed or negative. */
    public static int seconds(JsonElement e) {
        if (e == null || !e.isJsonPrimitive()) {
            return 0;
        }
        try {
            double value = e.getAsDouble();
            return value > 0 && value < Integer.MAX_VALUE ? (int) value : 0;
        } catch (NumberFormatException notNumber) {
            return 0;
        }
    }
}
