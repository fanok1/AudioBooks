package com.fanok.audiobooks.net;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import org.jsoup.nodes.Document;

/**
 * A JSON answer as the page a {@link com.fanok.audiobooks.source.SourceParser} parses: a site whose API speaks JSON
 * returns it from {@code fetch} ({@link SiteHttp#getJson}), so the models, the per-book page cache and the parser
 * contract are the same for HTML and JSON sites. Its DOM is empty (an HTML step such as a takedown-notice check finds
 * no text in it) and its {@link #location} is the final URL, which relative URLs of the answer resolve against.
 */
public final class JsonPage extends Document {

    private final JsonElement json;

    JsonPage(String location, JsonElement json) {
        super(location);
        this.json = json;
    }

    /**
     * The JSON {@code text} answered at {@code location} as a page, as {@link SiteHttp#getJson} returns it: what a JSON
     * site's fixture tests hand its parse methods. Malformed text is a {@link com.google.gson.JsonParseException}.
     */
    public static JsonPage parse(String location, String text) {
        return new JsonPage(location, JsonParser.parseString(text));
    }

    /**
     * The JSON of {@code page}; an HTML page here is a parser fetching the wrong way, an {@link IllegalStateException},
     * never parsed as "no items".
     */
    public static JsonElement of(Document page) {
        if (page instanceof JsonPage) {
            return ((JsonPage) page).json;
        }
        throw new IllegalStateException("not a JSON page: " + (page == null ? null : page.location()));
    }
}
