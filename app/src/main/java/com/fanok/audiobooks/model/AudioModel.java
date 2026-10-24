package com.fanok.audiobooks.model;

import android.content.Context;
import androidx.annotation.NonNull;
import com.fanok.audiobooks.BookRemovedException;
import com.fanok.audiobooks.Url;
import com.fanok.audiobooks.interface_pacatge.book_content.AudioModelInterfece;
import com.fanok.audiobooks.net.JsonSlice;
import com.fanok.audiobooks.net.SiteHttp;
import com.fanok.audiobooks.pojo.AudioPOJO;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import io.reactivex.Observable;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jetbrains.annotations.NotNull;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

/**
 * Chapter lists for all sources. Fetching ({@link SiteHttp}) is kept apart from parsing: every
 * {@code parseXxx} takes already-fetched content, calls no Android API and runs in JVM tests.
 * Failures are errors, never fake chapters: an unknown host, a page without the player or a page
 * that yields no playable chapter make {@link #getAudio} emit {@code onError}, so the caller can
 * fall back to offline chapters. One malformed chapter is skipped and counted, not fatal.
 */
public class AudioModel implements AudioModelInterfece {

    /** Fetches a provider page: {@link #fetchPage} in production, fixtures in tests. */
    interface PageSource {
        Document get(String url) throws IOException;
    }

    static final String BASE64 = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
    private static final String BAZA_KNIG_BASE64 = "Z83UhncLHApBrM7GvdqT4tNWRjemgak9oVzwK1PXDfY5bQOSlsF26yi0JCIuxE+/";
    /** Passphrase reverse-engineered from akniga's obfuscated player script. */
    private static final String AKNIGA_PASSPHRASE = "EKxtcg46V";
    private static final String ACCEPT_LANGUAGE = "ru-RU,ru;q=0.8,en-US;q=0.5,en;q=0.3";
    private static final Pattern PLAYERJS_FILE = Pattern.compile("[\"']?\\bfile[\"']?\\s*:\\s*");
    private static final Pattern AKNIGA_SECURITY_KEY
            = Pattern.compile("LIVESTREET_SECURITY_KEY\\s*=\\s*['\"]([^'\"]+)['\"]");
    private static final Pattern BAZA_KNIG_STR_DECODE = Pattern.compile("strDecode\\(\\s*([\"'])");
    private static final Pattern BAZA_KNIG_VOICE = Pattern.compile("[?&]sorce=(\\d{1,3})");

    Context mContext;

    public AudioModel(@NotNull final Context context) {
        mContext = context;
    }

    @Override
    public Observable<ArrayList<AudioPOJO>> getAudio(@NonNull String url) {
        return audio(url, AudioModel::fetchPage);
    }

    static Observable<ArrayList<AudioPOJO>> audio(String url, PageSource pages) {
        return Observable.create(emitter -> {
            ArrayList<AudioPOJO> chapters;
            try {
                chapters = load(url, pages);
            } catch (Exception e) {
                emitter.tryOnError(e);
                return;
            }
            if (!emitter.isDisposed()) {
                emitter.onNext(chapters);
                emitter.onComplete();
            }
        });
    }

    /** A provider's parser: {@code base} is the page's final URL, which relative media URLs resolve against. */
    private interface PageParser {
        ArrayList<AudioPOJO> parse(Document doc, String base) throws IOException;
    }

    /** Selects the parser by host (moved hosts are rewritten first); never returns an empty list. */
    static ArrayList<AudioPOJO> load(String rawUrl, PageSource pages) throws IOException {
        String url = Url.canonical(rawUrl);
        PageParser parser = parser(url);
        if (parser == null) {
            throw new IllegalArgumentException("unsupported host: " + Url.hostOf(rawUrl));
        }
        Document doc = pages.get(url);
        try {
            return parser.parse(doc, location(doc, url));
        } catch (IllegalStateException e) {
            if (BookRemovedException.isNotice(doc.text())) {
                throw new BookRemovedException(url);
            }
            throw e;
        }
    }

    private static PageParser parser(String url) {
        if (Url.isHost(url, Url.SERVER)) {
            return AudioModel::parseKnigavuhe;
        } else if (Url.isHost(url, Url.SERVER_IZIBUK)) {
            return AudioModel::parseIzibuk;
        } else if (Url.isHost(url, Url.SERVER_ABMP3)) {
            return (doc, base) -> loadPlayerjs(doc, base, parseAbmp3BookName(doc));
        } else if (Url.isHost(url, Url.SERVER_AKNIGA)) {
            return AudioModel::loadAkniga;
        } else if (Url.isHost(url, Url.SERVER_BAZA_KNIG)) {
            // the voice is the requested one even when a redirect drops the query
            return (doc, base) -> loadBazaKnig(doc, base, url);
        } else if (Url.isHost(url, Url.SERVER_KNIGOBLUD)) {
            return AudioModel::parseKnigoblud;
        } else if (Url.isHost(url, Url.SERVER_AUDIOBOO)) {
            return AudioModel::parseAudioboo;
        } else if (Url.isHost(url, Url.SERVER_OTRUB)) {
            return AudioModel::parseOtrub;
        } else if (Url.isHost(url, Url.SERVER_POLEKNIG)) {
            return (doc, base) -> loadPlayerjs(doc, base, text(doc.selectFirst("h1")));
        }
        return null;
    }

    /** Where {@code doc} was served from after redirects ({@code fallback} when unknown). */
    private static String location(Document doc, String fallback) {
        String location = doc.location();
        return location == null || location.isEmpty() ? fallback : location;
    }

    private static Document fetchPage(String url) throws IOException {
        return SiteHttp.parse(SiteHttp.execute(SiteHttp.connect(url).header("Accept-Language", ACCEPT_LANGUAGE)));
    }

    /** Body of a JSON/text response, classified like a page (challenge, HTTP ≥ 400 → exception). */
    private static String fetchBody(Connection connection) throws IOException {
        Connection.Response response = SiteHttp.execute(connection.ignoreContentType(true)).bufferUp();
        SiteHttp.parse(response);
        return response.body();
    }

    // ---- knigavuhe, izibuk, knigoblud, otrub: player JSON inside a page script ----

    static ArrayList<AudioPOJO> parseKnigavuhe(Document doc, String bookUrl) {
        Chapters chapters = new Chapters(text(doc.selectFirst(".book_title_name")), bookUrl);
        chapters.each(array(playerScript(doc, "new BookPlayer(", '[')), item -> {
            JsonObject o = item.getAsJsonObject();
            return chapters.chapter(string(o, "title"), string(o, "url"), seconds(o.get("duration")));
        });
        return chapters.done("knigavuhe player");
    }

    static ArrayList<AudioPOJO> parseIzibuk(Document doc, String bookUrl) {
        Chapters chapters = new Chapters(text(doc.selectFirst("[itemprop=name]")), bookUrl);
        JsonObject player = object(playerScript(doc, "new XSPlayer(", '{'));
        String prefix = string(player, "mp3_url_prefix");
        if (prefix.isEmpty()) {
            throw new IllegalStateException("izibuk player has no mp3_url_prefix");
        }
        String base = prefix.matches("(?i)^https?://.*") ? prefix : "https://" + prefix.replaceFirst("^/+", "");
        String sign = string(player, "sign");
        String query = sign.isEmpty() || sign.startsWith("?") || sign.startsWith("&") ? sign : "?" + sign;
        chapters.each(arrayOf(player, "tracks"), item -> {
            JsonArray track = item.getAsJsonArray(); // [id, title, seconds, ?, file]
            String file = string(track.get(4));
            return chapters.chapter(string(track.get(1)), file.isEmpty() ? "" : base + "/" + file + query,
                    seconds(track.get(2)));
        });
        return chapters.done("izibuk player");
    }

    static ArrayList<AudioPOJO> parseKnigoblud(Document doc, String bookUrl) {
        return parseXsPlaylist(object(playerScript(doc, "KB.playerInit(", '{')), bookUrl,
                text(doc.selectFirst("h1")), "knigoblud player");
    }

    static ArrayList<AudioPOJO> parseOtrub(Document doc, String bookUrl) {
        String bookName = text(doc.selectFirst("h1"));
        String player = findPlayerScript(doc, "XSPlayer(", '{');
        if (player == null) {
            return parseInlinePlayerjs(doc, bookUrl, bookName);
        }
        return parseXsPlaylist(object(player), bookUrl, bookName, "otrub player");
    }

    private static ArrayList<AudioPOJO> parseXsPlaylist(JsonObject player, String bookUrl, String bookName,
            String what) {
        Chapters chapters = new Chapters(bookName, bookUrl);
        chapters.each(arrayOf(player, "playlist"), item -> {
            JsonObject o = item.getAsJsonObject();
            return chapters.chapter(string(o, "title"), string(o, "src"), seconds(o.get("duration")));
        });
        return chapters.done(what);
    }

    /** The JSON literal after {@code marker} in the first script that has one; other scripts are skipped. */
    static String playerScript(Document doc, String marker, char open) {
        String json = findPlayerScript(doc, marker, open);
        if (json == null) {
            throw new IllegalStateException("player script not found: " + marker);
        }
        return json;
    }

    private static String findPlayerScript(Document doc, String marker, char open) {
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

    // ---- Playerjs (ABMP3, audioboo, otrub, poleknig) ----

    static ArrayList<AudioPOJO> parseAudioboo(Document doc, String bookUrl) {
        return parseInlinePlayerjs(doc, bookUrl, text(doc.selectFirst("h1")));
    }

    private static ArrayList<AudioPOJO> parseInlinePlayerjs(Document doc, String bookUrl, String bookName) {
        String file = playerjsFile(doc);
        if (!file.trim().startsWith("[")) {
            throw new IllegalStateException("Playerjs playlist is not inline");
        }
        return parsePlayerjsPlaylist(file, bookUrl, bookName);
    }

    /** Playerjs page whose {@code file} is an inline list or the URL of a JSON playlist. */
    private static ArrayList<AudioPOJO> loadPlayerjs(Document doc, String bookUrl, String bookName)
            throws IOException {
        String file = playerjsFile(doc);
        if (file.trim().startsWith("[")) {
            return parsePlayerjsPlaylist(file, bookUrl, bookName);
        }
        String playlistUrl = resolve(bookUrl, file);
        if (playlistUrl.isEmpty()) {
            throw new IllegalStateException("Playerjs file is neither a list nor a URL");
        }
        return parsePlayerjsPlaylist(fetchBody(SiteHttp.connect(playlistUrl).referrer(bookUrl)), playlistUrl,
                bookName);
    }

    /** The Playerjs {@code file} option of the first script that has a literal one (list text or string). */
    static String playerjsFile(Document doc) {
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
    static ArrayList<AudioPOJO> parsePlayerjsPlaylist(String json, String baseUrl, String bookName) {
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
    static String oneOf(String file) {
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
    static String playerjsUrl(String file) {
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

    // ---- ABMP3 ----

    static String parseAbmp3BookName(Document doc) {
        String author = "";
        for (Element info : doc.select(".panel-item")) {
            Element link = info.selectFirst("a");
            if (info.text().contains("Автор") && link != null) {
                author = link.text();
            }
        }
        String title = text(doc.selectFirst("h1"));
        return author.isEmpty() ? title : title.replace(author + " - ", "");
    }

    // ---- akniga: page → token → book JSON with an encrypted HLS URL ----

    private static ArrayList<AudioPOJO> loadAkniga(Document doc, String url) throws IOException {
        String key = aknigaSecurityKey(doc);
        String bid = aknigaBid(doc);
        String book = null;
        if (!key.isEmpty() && !bid.isEmpty()) {
            String token = string(object(fetchBody(aknigaPost(url, "/ajax/player/token")
                    .data("security_ls_key", key)
                    .data("bid", bid)
                    .data("ts", String.valueOf(System.currentTimeMillis())))), "token");
            book = fetchBody(aknigaPost(url, "/ajax/b/" + bid)
                    .data("security_ls_key", key)
                    .data("bid", bid)
                    .data("token", token)
                    .data("hls", "true"));
        }
        return parseAkniga(doc, book, url);
    }

    /** Session cookies of the book page come from the {@link SiteHttp} jar. */
    private static Connection aknigaPost(String bookUrl, String path) {
        return SiteHttp.connect(Url.SERVER_AKNIGA + path)
                .method(Connection.Method.POST)
                .referrer(bookUrl)
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Origin", Url.SERVER_AKNIGA)
                .header("Accept-Language", ACCEPT_LANGUAGE);
    }

    static String aknigaSecurityKey(Document doc) {
        for (Element script : doc.select("script")) {
            Matcher m = AKNIGA_SECURITY_KEY.matcher(script.data());
            if (m.find()) {
                return m.group(1);
            }
        }
        return "";
    }

    /** The current book's id, from its own page or player container (related books carry theirs too). */
    static String aknigaBid(Document doc) {
        Element el = doc.selectFirst("article.book--page[data-bid~=^\\d+$], .player--chapters[data-bid~=^\\d+$]");
        return el == null ? "" : el.attr("data-bid");
    }

    /**
     * {@code bookJson} is the {@code /ajax/b/<bid>} answer (null when the page had no key): one HLS
     * URL shared by all chapters, each clipped by its start/finish time.
     */
    static ArrayList<AudioPOJO> parseAkniga(Document doc, String bookJson, String bookUrl) {
        String bookName = text(doc.selectFirst(".caption__article-main"));
        Chapters chapters = new Chapters(bookName, bookUrl);
        if (bookJson != null) {
            JsonObject book = object(bookJson);
            JsonElement hres = book.get("hres");
            String hls = hres == null ? "" : decryptAknigaHres(hres.isJsonPrimitive() ? hres.getAsString() : hres.toString());
            if (!hls.isEmpty()) {
                JsonElement items = book.get("items");
                if (items != null && items.isJsonPrimitive()) {
                    try {
                        items = JsonParser.parseString(items.getAsString());
                    } catch (JsonParseException e) {
                        items = null;
                    }
                }
                if (items != null && items.isJsonArray()) {
                    addAknigaClips(chapters, items.getAsJsonArray(), hls);
                }
                if (chapters.isEmpty()) {
                    AudioPOJO whole = chapters.chapter("Аудиокнига (полная)", hls, 0);
                    if (whole != null) {
                        whole.setTimeStart(0);
                    }
                    chapters.add(whole);
                }
            }
            JsonElement legacy = book.get("aItems");
            if (chapters.isEmpty() && legacy != null && legacy.isJsonArray()) {
                int[] accumulated = {0};
                chapters.each(legacy.getAsJsonArray(), item -> {
                    JsonObject o = item.getAsJsonObject();
                    String file = o.has("file") ? string(o, "file") : string(o, "file_url");
                    AudioPOJO audio = chapters.chapter(string(o, "title"), file, 0);
                    if (audio != null && o.has("duration")) {
                        int duration = seconds(o.get("duration"));
                        audio.setTime(duration);
                        audio.setTimeStart(accumulated[0]);
                        accumulated[0] += duration;
                        audio.setTimeFinish(accumulated[0]);
                    }
                    return audio;
                });
            }
        }
        if (chapters.isEmpty()) {
            for (Element audio : doc.select("audio[src]")) {
                if (chapters.add(chapters.chapter(bookName, audio.absUrl("src"), 0))) {
                    break;
                }
            }
        }
        return chapters.done("akniga book");
    }

    /**
     * Chapters clipped out of the one shared HLS stream, as complete monotonic intervals: a start
     * that goes backwards is clamped to the previous end; an item without its own end (no
     * {@code time_finish}/{@code duration}) ends where the next item explicitly starts, else it is
     * skipped. Only the last chapter may stay open-ended (plays to the end of the stream).
     */
    private static void addAknigaClips(Chapters chapters, JsonArray items, String hls) {
        List<AudioPOJO> clips = new ArrayList<>();
        List<JsonObject> times = new ArrayList<>();
        chapters.each(items, item -> {
            JsonObject o = item.getAsJsonObject();
            AudioPOJO audio = chapters.chapter(string(o, "title"), hls, 0);
            if (audio != null) {
                clips.add(audio);
                times.add(o);
            }
            return null;
        });
        int end = 0;
        for (int i = 0; i < clips.size(); i++) {
            JsonObject o = times.get(i);
            int start = Math.max(end, seconds(o.get("time_from_start")));
            int finish = seconds(o.get("time_finish"));
            if (finish <= start && o.has("duration")) {
                finish = start + seconds(o.get("duration"));
            }
            if (finish <= start && i + 1 < times.size()) {
                finish = seconds(times.get(i + 1).get("time_from_start"));
            }
            AudioPOJO clip = clips.get(i);
            clip.setTimeStart(start);
            if (finish > start) {
                clip.setTime(finish - start);
                clip.setTimeFinish(finish);
                end = finish;
                chapters.add(clip);
            } else if (i == clips.size() - 1) {
                chapters.add(clip);
            } else {
                chapters.skipped++;
            }
        }
    }

    /**
     * Decrypts akniga's {@code hres} (the HLS playlist URL): AES-256-CBC with an EVP_BytesToKey (MD5)
     * key, as CryptoJS does. Returns "" when it cannot be decrypted.
     */
    static String decryptAknigaHres(String hresJson) {
        try {
            JsonObject hres = object(hresJson);
            String ivHex = string(hres, "iv");
            String saltHex = string(hres, "s");
            byte[] ct = decodeBase64(string(hres, "ct"), BASE64);
            byte[] salt = saltHex.isEmpty() ? new byte[8] : hexToBytes(saltHex);
            byte[] keyIv = evpBytesToKey(AKNIGA_PASSPHRASE.getBytes(StandardCharsets.UTF_8), salt, 48);
            byte[] iv = ivHex.length() == 32 ? hexToBytes(ivHex) : Arrays.copyOfRange(keyIv, 32, 48);
            javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(javax.crypto.Cipher.DECRYPT_MODE,
                    new javax.crypto.spec.SecretKeySpec(Arrays.copyOfRange(keyIv, 0, 32), "AES"),
                    new javax.crypto.spec.IvParameterSpec(iv));
            String result = new String(cipher.doFinal(ct), StandardCharsets.UTF_8).trim();
            // a JSON string like "https:\/\/r1.akniga.club\/b\/111286\/pl.m3u8?…", a list of them or {url}
            try {
                JsonElement value = JsonParser.parseString(result);
                if (value.isJsonArray() && value.getAsJsonArray().size() > 0) {
                    value = value.getAsJsonArray().get(0);
                } else if (value.isJsonObject()) {
                    value = value.getAsJsonObject().get("url");
                }
                result = string(value);
            } catch (JsonParseException notJson) {
                result = result.replace("\\/", "/");
            }
            return resolve(Url.SERVER_AKNIGA + "/", result);
        } catch (Exception e) {
            return "";
        }
    }

    private static byte[] evpBytesToKey(byte[] passphrase, byte[] salt, int needed) throws Exception {
        byte[] result = new byte[0];
        byte[] prev = new byte[0];
        java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
        while (result.length < needed) {
            md.reset();
            md.update(prev);
            md.update(passphrase);
            md.update(salt);
            prev = md.digest();
            byte[] tmp = new byte[result.length + prev.length];
            System.arraycopy(result, 0, tmp, 0, result.length);
            System.arraycopy(prev, 0, tmp, result.length, prev.length);
            result = tmp;
        }
        return result;
    }

    /** Hex pairs to bytes; a trailing odd digit is ignored. */
    static byte[] hexToBytes(String hex) {
        byte[] data = new byte[hex.length() / 2];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) ((Character.digit(hex.charAt(2 * i), 16) << 4) + Character.digit(hex.charAt(2 * i + 1), 16));
        }
        return data;
    }

    // ---- baza-knig: per-voice strDecode or plain player scripts, else the #fr frame (archive.org) ----

    private static ArrayList<AudioPOJO> loadBazaKnig(Document doc, String base, String url) throws IOException {
        Element scope = bazaKnigScope(doc, url);
        Chapters chapters = bazaKnigScripts(doc, scope, base);
        if (!chapters.isEmpty()) {
            return chapters.done("baza-knig player");
        }
        Element fr = scope.selectFirst("#fr");
        if (fr == null && bazaKnigVoice(url) == 1) {
            fr = doc.getElementById("fr");
        }
        String frame = bazaKnigFrameUrl(fr, base);
        if (frame == null) {
            return chapters.done("baza-knig player");
        }
        String bookName = bazaKnigBookName(doc);
        if (frame.contains("archive.org")) {
            String metadata = frame.replace("/embed/", "/metadata/").replaceFirst("[?&#].*$", "");
            return parseArchiveOrgMetadata(fetchBody(SiteHttp.connect(metadata)), bookName);
        }
        return parseBazaKnigPlay8(SiteHttp.get(frame), bookName);
    }

    /** Chapters of the voice selected by {@code ?sorce=N} from the page's own scripts. */
    static ArrayList<AudioPOJO> parseBazaKnig(Document doc, String bookUrl) {
        return bazaKnigScripts(doc, bazaKnigScope(doc, bookUrl), bookUrl).done("baza-knig player");
    }

    /**
     * Voice N lives in {@code #content-tabN}; pages without tabs have one voice. A voice requested
     * by {@code ?sorce=N} whose tab is missing is an error: the whole page would be another voice.
     */
    private static Element bazaKnigScope(Document doc, String bookUrl) {
        String id = "content-tab" + bazaKnigVoice(bookUrl);
        Element tab = doc.getElementById(id);
        if (tab == null && BAZA_KNIG_VOICE.matcher(bookUrl).find()) {
            throw new IllegalStateException("baza-knig voice tab not found: #" + id);
        }
        return tab != null ? tab : doc;
    }

    private static int bazaKnigVoice(String bookUrl) {
        Matcher m = BAZA_KNIG_VOICE.matcher(bookUrl);
        return m.find() ? Integer.parseInt(m.group(1)) : 1;
    }

    private static Chapters bazaKnigScripts(Document doc, Element scope, String bookUrl) {
        Chapters chapters = new Chapters(bazaKnigBookName(doc), bookUrl);
        Chapters.ItemParser item = element -> {
            JsonObject o = element.getAsJsonObject();
            return chapters.chapter(string(o, "title"), string(o, "file"), 0);
        };
        for (Element script : scope.select("script")) {
            String data = script.data();
            Matcher m = BAZA_KNIG_STR_DECODE.matcher(data);
            try {
                if (m.find()) {
                    String decoded = decodeBazaKnig(stringLiteral(data, m.start(1)));
                    chapters.each(array(JsonSlice.from(decoded, 0, '[')), item);
                } else if (data.contains("id:\"player")) {
                    chapters.each(array(JsonSlice.from(data, 0, '[')), item);
                }
            } catch (IllegalStateException notThisScript) {
                // keep looking
            }
            if (!chapters.isEmpty()) {
                break;
            }
        }
        return chapters;
    }

    /** baza-knig's {@code strDecode}: base64 with a shuffled alphabet around UTF-8 JSON. */
    static String decodeBazaKnig(String key) {
        return new String(decodeBase64(key, BAZA_KNIG_BASE64), StandardCharsets.UTF_8);
    }

    static String bazaKnigBookName(Document doc) {
        Element h1 = doc.selectFirst("h1");
        String text = h1 == null ? "" : h1.ownText();
        int dash = text.indexOf(" - ");
        return (dash > 0 ? text.substring(0, dash) : text).trim();
    }

    /** The player frame kept as an iframe snippet in the {@code data} attribute of {@code #fr}, or null. */
    static String bazaKnigFrameUrl(Element fr, String baseUrl) {
        Element frame = fr == null ? null : Jsoup.parseBodyFragment(fr.attr("data"), baseUrl).selectFirst("[src]");
        String src = frame == null ? "" : frame.absUrl("src");
        return src.isEmpty() ? null : src;
    }

    /**
     * archive.org {@code /metadata/<id>}: one MP3 per recording, by name — its original MP3, else
     * one derivative (VBR over fixed bitrate). A file without a name is skipped.
     */
    static ArrayList<AudioPOJO> parseArchiveOrgMetadata(String json, String bookName) {
        JsonObject root = object(json);
        JsonElement meta = root.get("metadata");
        String id = meta != null && meta.isJsonObject() ? string(meta.getAsJsonObject(), "identifier") : "";
        if (id.isEmpty()) {
            throw new IllegalStateException("archive.org item has no identifier (dark or removed)");
        }
        Chapters chapters = new Chapters(bookName, "https://archive.org/");
        Map<String, JsonObject> recordings = new TreeMap<>();
        for (JsonElement file : arrayOf(root, "files")) {
            JsonObject o = file.isJsonObject() ? file.getAsJsonObject() : null;
            if (o == null || !string(o, "format").contains("MP3")) {
                continue;
            }
            if (string(o, "name").isEmpty()) {
                chapters.skipped++;
                continue;
            }
            String recording = archiveOrgRecording(o);
            JsonObject best = recordings.get(recording);
            if (best == null || archiveOrgRank(o) < archiveOrgRank(best)) {
                recordings.put(recording, o);
            }
        }
        JsonArray files = new JsonArray();
        for (JsonObject file : recordings.values()) {
            files.add(file);
        }
        chapters.each(files, item -> {
            JsonObject o = item.getAsJsonObject();
            String name = string(o, "name");
            String title = string(o, "title");
            try {
                String url = new URI("https", "archive.org", "/download/" + id + "/" + name, null).toASCIIString();
                return chapters.chapter(title.isEmpty() ? name : title, url, seconds(o.get("length")));
            } catch (URISyntaxException e) {
                throw new IllegalStateException(e);
            }
        });
        return chapters.done("archive.org item " + id);
    }

    /** The recording a file belongs to: a derivative's {@code original}, without extension or bitrate suffix. */
    private static String archiveOrgRecording(JsonObject file) {
        String original = string(file, "original");
        String name = original.isEmpty() || "original".equals(string(file, "source")) ? string(file, "name") : original;
        return name.replaceFirst("\\.[^./]*$", "").replaceFirst("(?i)_(\\d+kb|vbr)$", "");
    }

    private static int archiveOrgRank(JsonObject file) {
        return "original".equals(string(file, "source")) ? 0 : "VBR MP3".equals(string(file, "format")) ? 1 : 2;
    }

    /** The frame's own playlist; its relative URLs resolve against the frame's (final) URL. */
    static ArrayList<AudioPOJO> parseBazaKnigPlay8(Document playlist, String bookName) {
        Element player = playlist.selectFirst(".js-play8-playlist");
        if (player == null) {
            throw new IllegalStateException("js-play8 playlist not found");
        }
        Chapters chapters = new Chapters(bookName, location(playlist, ""));
        int[] index = {0};
        chapters.each(array(player.attr("value")), item -> {
            index[0]++;
            JsonArray sources = arrayOf(item.getAsJsonObject(), "sources");
            String file = sources.size() > 0 && sources.get(0).isJsonObject()
                    ? string(sources.get(0).getAsJsonObject(), "file") : "";
            return chapters.chapter(bookName + "_" + index[0], file, seconds(item.getAsJsonObject().get("duration")));
        });
        return chapters.done("baza-knig js-play8 playlist");
    }

    // ---- shared helpers (pure) ----

    /**
     * Base64 over any 64-letter alphabet. Characters outside it (padding, whitespace, junk) are
     * skipped and a trailing partial group yields its whole bytes, so no input length throws.
     */
    static byte[] decodeBase64(String text, String alphabet) {
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
    static String resolve(String base, String href) {
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
    static String stringLiteral(String text, int at) {
        char quote = text.charAt(at);
        for (int i = at + 1; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\\') {
                i++;
            } else if (c == quote) {
                return string(json(text.substring(at, i + 1)));
            }
        }
        throw new IllegalStateException("unterminated string literal at index " + at);
    }

    private static JsonElement json(String text) {
        try {
            return JsonParser.parseString(text);
        } catch (JsonParseException e) {
            throw new IllegalStateException("malformed player JSON", e);
        }
    }

    private static JsonArray array(String text) {
        JsonElement e = json(text);
        if (!e.isJsonArray()) {
            throw new IllegalStateException("player JSON is not a list");
        }
        return e.getAsJsonArray();
    }

    private static JsonObject object(String text) {
        JsonElement e = json(text);
        if (!e.isJsonObject()) {
            throw new IllegalStateException("player JSON is not an object");
        }
        return e.getAsJsonObject();
    }

    private static JsonArray arrayOf(JsonObject o, String key) {
        JsonElement e = o.get(key);
        if (e == null || !e.isJsonArray()) {
            throw new IllegalStateException("player JSON has no '" + key + "' list");
        }
        return e.getAsJsonArray();
    }

    private static String string(JsonObject o, String key) {
        return string(o.get(key));
    }

    private static String string(JsonElement e) {
        return e != null && e.isJsonPrimitive() ? e.getAsString() : "";
    }

    /** Whole seconds of a number or numeric string ("12.5" → 12); 0 when absent, malformed or negative. */
    static int seconds(JsonElement e) {
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

    private static String text(Element e) {
        return e == null ? "" : e.text().trim();
    }

    /** Chapters of one book: an item without a usable URL or with bad fields is skipped and counted. */
    static final class Chapters {

        interface ItemParser {
            /** The chapter for {@code item}, or null when it adds nothing itself. */
            AudioPOJO parse(JsonElement item);
        }

        private final ArrayList<AudioPOJO> list = new ArrayList<>();
        private final String bookName;
        private final String base;
        int skipped;

        Chapters(String bookName, String base) {
            this.bookName = bookName;
            this.base = base;
        }

        /** A chapter of this book (not yet added), or null — counted as skipped — when {@code href} is unusable. */
        AudioPOJO chapter(String name, String href, int seconds) {
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

        void each(JsonArray items, ItemParser parser) {
            for (JsonElement item : items) {
                try {
                    add(parser.parse(item));
                } catch (RuntimeException badItem) {
                    skipped++;
                }
            }
        }

        boolean add(AudioPOJO audio) {
            if (audio != null) {
                list.add(audio);
            }
            return audio != null;
        }

        boolean isEmpty() {
            return list.isEmpty();
        }

        ArrayList<AudioPOJO> done(String what) {
            if (list.isEmpty()) {
                throw new IllegalStateException("no playable chapters in " + what + " (" + skipped + " skipped)");
            }
            return list;
        }
    }
}
