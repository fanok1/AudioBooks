package com.fanok.audiobooks.source;

import static com.fanok.audiobooks.source.Json.array;
import static com.fanok.audiobooks.source.Json.arrayOf;
import static com.fanok.audiobooks.source.Json.seconds;
import static com.fanok.audiobooks.source.Json.string;

import com.fanok.audiobooks.net.JsonSlice;
import com.fanok.audiobooks.net.SiteHttp;
import com.fanok.audiobooks.pojo.AudioPOJO;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The web players the sites embed, shared by every source: the JSON of a player script ({@code new XSPlayer({..})},
 * {@code KB.playerInit({..})}, ...), the XSPlayer playlist, and Playerjs (an inline list or the URL of a JSON
 * playlist, nested folders, mirrors, obfuscated URLs). Pure except {@link #loadPlayerjs}, which fetches a playlist.
 * A source whose page carries one of these players needs one line in {@link SourceParser#tracks}.
 */
public final class Players {

    public static final String BASE64 = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
    private static final Pattern PLAYERJS_FILE = Pattern.compile("[\"']?\\bfile[\"']?\\s*:\\s*");

    private Players() {
    }

    // ---------------------------------------------------------------- player scripts

    /** The JSON literal after {@code marker} in the first script that has one; other scripts are skipped. */
    public static String playerScript(Document doc, String marker, char open) {
        String json = findPlayerScript(doc, marker, open);
        if (json == null) {
            throw new IllegalStateException("player script not found: " + marker);
        }
        return json;
    }

    /** As {@link #playerScript}, null when no script has the marker. */
    public static String findPlayerScript(Document doc, String marker, char open) {
        for (Element script : doc.select("script")) {
            String data = script.data();
            if (data.contains(marker)) {
                try {
                    return JsonSlice.after(data, marker, open);
                } catch (IllegalStateException notThisOne) {
                    // a loader script that only mentions the marker; keep looking
                }
            }
        }
        return null;
    }

    /**
     * The XSPlayer {@code tracks} list ({@code [id, title, seconds, ?, file]}, the engine of izib.uk and biblus): a
     * file under {@code mp3_url_prefix} (host and path, no scheme) signed with {@code sign}, or, as the player allows,
     * an absolute URL played as it is.
     */
    public static ArrayList<AudioPOJO> xsTracks(JsonObject player, String bookUrl, String bookName, String what) {
        String prefix = string(player, "mp3_url_prefix");
        if (prefix.isEmpty()) {
            throw new IllegalStateException(what + " has no mp3_url_prefix");
        }
        String base = prefix.matches("(?i)^https?://.*") ? prefix : "https://" + prefix.replaceFirst("^/+", "");
        String sign = string(player, "sign");
        String query = sign.isEmpty() || sign.startsWith("?") || sign.startsWith("&") ? sign : "?" + sign;
        Chapters chapters = new Chapters(bookName, bookUrl);
        chapters.each(arrayOf(player, "tracks"), item -> {
            JsonArray track = item.getAsJsonArray();
            String file = string(track.get(4));
            String url = file.isEmpty() || file.matches("(?i)^https?://.*") ? file : base + "/" + file + query;
            return chapters.chapter(string(track.get(1)), url, seconds(track.get(2)));
        });
        return chapters.done(what);
    }

    /** An XSPlayer-style {@code {playlist: [{title, src, duration}]}}. */
    public static ArrayList<AudioPOJO> xsPlaylist(JsonObject player, String bookUrl, String bookName, String what) {
        Chapters chapters = new Chapters(bookName, bookUrl);
        chapters.each(arrayOf(player, "playlist"), item -> {
            JsonObject o = item.getAsJsonObject();
            return chapters.chapter(string(o, "title"), string(o, "src"), seconds(o.get("duration")));
        });
        return chapters.done(what);
    }

    // ---------------------------------------------------------------- Playerjs

    /** A Playerjs page whose {@code file} is an inline list; anything else is an {@link IllegalStateException}. */
    public static ArrayList<AudioPOJO> inlinePlayerjs(Document doc, String bookUrl, String bookName) {
        String file = playerjsFile(doc);
        if (!file.trim().startsWith("[")) {
            throw new IllegalStateException("Playerjs playlist is not inline");
        }
        return parsePlayerjsPlaylist(file, bookUrl, bookName);
    }

    /** Playerjs page whose {@code file} is an inline list or the URL of a JSON playlist (fetched, Referer the book). */
    public static ArrayList<AudioPOJO> loadPlayerjs(Document doc, String bookUrl, String bookName)
            throws IOException {
        String file = playerjsFile(doc);
        if (file.trim().startsWith("[")) {
            return parsePlayerjsPlaylist(file, bookUrl, bookName);
        }
        String playlistUrl = resolve(bookUrl, file);
        if (playlistUrl.isEmpty()) {
            throw new IllegalStateException("Playerjs file is neither a list nor a URL");
        }
        return parsePlayerjsPlaylist(SiteHttp.body(SiteHttp.connect(playlistUrl).referrer(bookUrl)), playlistUrl,
                bookName);
    }

    /** The Playerjs {@code file} option of the first script that has a literal one (list text or string). */
    public static String playerjsFile(Document doc) {
        for (Element script : doc.select("script")) {
            String data = script.data();
            if (data.contains("Playerjs")) {
                String file = playerjsFile(data);
                if (file != null) {
                    return file;
                }
            }
        }
        throw new IllegalStateException("Playerjs script with a file option not found");
    }

    private static String playerjsFile(String script) {
        Matcher m = PLAYERJS_FILE.matcher(script);
        while (m.find()) {
            int at = m.end();
            if (at >= script.length()) {
                break;
            }
            char c = script.charAt(at);
            try {
                if (c == '[') {
                    return JsonSlice.from(script, at, '[');
                } else if (c == '"' || c == '\'') {
                    return stringLiteral(script, at);
                }
            } catch (IllegalStateException malformed) {
                // try the next occurrence
            }
        }
        return null;
    }

    /** A Playerjs playlist: {@code [{title, file, folder?}]}; folders are flattened, URLs resolved. */
    public static ArrayList<AudioPOJO> parsePlayerjsPlaylist(String json, String baseUrl, String bookName) {
        Chapters chapters = new Chapters(bookName, baseUrl);
        addPlayerjsItems(chapters, array(json));
        return chapters.done("Playerjs playlist");
    }

    private static void addPlayerjsItems(Chapters chapters, JsonArray items) {
        chapters.each(items, item -> {
            JsonObject o = item.getAsJsonObject();
            JsonElement folder = o.get("folder");
            if (folder != null && folder.isJsonArray()) {
                addPlayerjsItems(chapters, folder.getAsJsonArray());
                return null;
            }
            return chapters.chapter(string(o, "title"), oneOf(playerjsUrl(string(o, "file"))),
                    seconds(o.get("duration")));
        });
    }

    /**
     * Playerjs "{@code a or b}" lists equivalent mirrors, and some sites (poleknig) shuffle their order on every load.
     * A fixed pick keeps the chapter's URL, which the saved rows, downloads and deletion match it by, the same.
     */
    public static String oneOf(String file) {
        String[] alternatives = file.trim().split("\\s+or\\s+");
        Arrays.sort(alternatives);
        return alternatives[0];
    }

    /**
     * Decodes the obfuscated Playerjs forms ({@code #2…}/{@code //_//}-separated base64 and
     * {@code /engine/go.php?url=<base64>}). The {@code //_//} form is tried with each
     * {@code //_//junk//_//} pair cut out, then with only the markers removed; a value that decodes
     * to no absolute URL either way is dropped (the junk is site-specific and cannot be told apart).
     */
    public static String playerjsUrl(String file) {
        String f = file.trim();
        List<String> candidates = new ArrayList<>();
        int go = f.indexOf("/engine/go.php?url=");
        if (f.startsWith("#") || f.contains("//_//")) {
            String body = f.replaceFirst("^#\\w", "");
            candidates.add(body.replaceAll("//_//.*?//_//", "").replace("//_//", ""));
            candidates.add(body.replace("//_//", ""));
        } else if (go >= 0) {
            // the raw query value: a '+' in it is a base64 letter, not an encoded space
            String value = f.substring(go + 19).replaceFirst("[&#].*$", "").replace("+", "%2B");
            try {
                candidates.add(URLDecoder.decode(value, "UTF-8"));
            } catch (UnsupportedEncodingException e) {
                throw new IllegalStateException(e);
            }
        } else {
            return f;
        }
        for (String candidate : candidates) {
            String decoded = base64Text(candidate);
            if (decoded.matches("(?is)^(https?:)?//.+")) {
                return decoded;
            }
        }
        return "";
    }

    // ---------------------------------------------------------------- helpers

    /**
     * Base64 over any 64-letter alphabet. Characters outside it (padding, whitespace, junk) are
     * skipped and a trailing partial group yields its whole bytes, so no input length throws.
     */
    public static byte[] decodeBase64(String text, String alphabet) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(text.length() * 3 / 4 + 1);
        int buffer = 0;
        int bits = 0;
        for (int i = 0; i < text.length(); i++) {
            int value = alphabet.indexOf(text.charAt(i));
            if (value < 0) {
                continue;
            }
            buffer = ((buffer << 6) | value) & 0xFFFF;
            bits += 6;
            if (bits >= 8) {
                bits -= 8;
                out.write((buffer >> bits) & 0xFF);
            }
        }
        return out.toByteArray();
    }

    private static String base64Text(String text) {
        return new String(decodeBase64(text, BASE64), StandardCharsets.UTF_8).trim();
    }

    /** {@code href} resolved against {@code base}; "" when empty or not an http(s) URL. */
    public static String resolve(String base, String href) {
        if (href == null || href.trim().isEmpty()) {
            return "";
        }
        try {
            URL url = base == null || base.isEmpty() ? new URL(href.trim()) : new URL(new URL(base), href.trim());
            String protocol = url.getProtocol();
            return "https".equals(protocol) || "http".equals(protocol) ? url.toString() : "";
        } catch (MalformedURLException e) {
            return "";
        }
    }

    /** Content of the JS string literal whose opening quote is at {@code at}, escapes decoded. */
    public static String stringLiteral(String text, int at) {
        char quote = text.charAt(at);
        for (int i = at + 1; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\\') {
                i++;
            } else if (c == quote) {
                return string(Json.parse(text.substring(at, i + 1)));
            }
        }
        throw new IllegalStateException("unterminated string literal at index " + at);
    }

    /** Chapters of one book: an item without a usable URL or with bad fields is skipped and counted. */
    public static final class Chapters {

        public interface ItemParser {
            /** The chapter for {@code item}, or null when it adds nothing itself. */
            AudioPOJO parse(JsonElement item);
        }

        private final ArrayList<AudioPOJO> list = new ArrayList<>();
        private final String bookName;
        private final String base;
        public int skipped;

        public Chapters(String bookName, String base) {
            this.bookName = bookName;
            this.base = base;
        }

        /** A chapter of this book (not yet added), or null — counted as skipped — when {@code href} is unusable. */
        public AudioPOJO chapter(String name, String href, int seconds) {
            String url = resolve(base, href);
            if (url.isEmpty()) {
                skipped++;
                return null;
            }
            AudioPOJO audio = new AudioPOJO();
            audio.setBookName(bookName);
            audio.setName(name);
            audio.setUrl(url);
            audio.setTime(seconds);
            return audio;
        }

        public void each(JsonArray items, ItemParser parser) {
            for (JsonElement item : items) {
                try {
                    add(parser.parse(item));
                } catch (RuntimeException badItem) {
                    skipped++;
                }
            }
        }

        public boolean add(AudioPOJO audio) {
            if (audio != null) {
                list.add(audio);
            }
            return audio != null;
        }

        public boolean isEmpty() {
            return list.isEmpty();
        }

        public ArrayList<AudioPOJO> done(String what) {
            if (list.isEmpty()) {
                throw new IllegalStateException("no playable chapters in " + what + " (" + skipped + " skipped)");
            }
            return list;
        }
    }
}
