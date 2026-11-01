package com.fanok.audiobooks.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.fanok.audiobooks.source.Akniga;
import com.fanok.audiobooks.source.BazaKnig;
import com.fanok.audiobooks.source.Abmp3;
import com.fanok.audiobooks.source.Audioboo;
import com.fanok.audiobooks.source.Izibuk;
import com.fanok.audiobooks.source.Knigavuhe;
import com.fanok.audiobooks.BookRemovedException;
import com.fanok.audiobooks.CookesExeption;
import com.fanok.audiobooks.net.SiteHttp;
import com.fanok.audiobooks.pojo.AudioPOJO;
import com.fanok.audiobooks.source.Players;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.Test;

/** Chapter parsers against pages captured from the live sites (audioboo: synthetic, see its fixture). */
public class AudioModelTest {

    private static final String KNIGAVUHE = "https://knigavuhe.org/book/mojj-milyjj-poni-1/";
    private static final String IZIBUK = "https://izib.uk/art144179";
    private static final String ABMP3 = "https://audiobook-mp3.com/audio-292-sapiens-kratkaja-istorija-chelovechestva";
    private static final String AKNIGA = "https://akniga.org/teorin-yuhan-sankta-psiho";
    private static final String BAZA_KNIG
            = "https://baza-knig.top/fantastika-fentezii/182353-taksist-iz-forbes-nik-tarasov.html";
    private static final String KNIGOBLUD = "https://www.knigoblud.club/879ec598-85d9-49de-9a8d-c5c325716ddb";
    private static final String AUDIOBOO = "https://audioboo.org/fantastika/1-sinteticheskaja-kniga.html";
    private static final String OTRUB = "https://otrub.in/khozjain-okov-xv.html";
    private static final String POLEKNIG = "https://poleknig.com/books/269329";

    @Test
    public void knigavuheFixture() throws IOException {
        List<AudioPOJO> chapters = Knigavuhe.parseTracks(page("knigavuhe", KNIGAVUHE), KNIGAVUHE);
        assertEquals(1, chapters.size());
        assertEquals("С.Кинг-Мой маленький пони.", chapters.get(0).getName());
        assertEquals("Мой милый пони", chapters.get(0).getBookName());
        assertEquals(5137, chapters.get(0).getTime());
        assertHttps(chapters);
    }

    @Test
    public void izibukFixture() throws IOException {
        List<AudioPOJO> chapters = Izibuk.parseTracks(page("izibuk", IZIBUK), IZIBUK);
        assertEquals(22, chapters.size());
        assertEquals("Мертвая тишина. Том 3 01", chapters.get(0).getName());
        assertEquals("Мертвая тишина. Том 3 22", chapters.get(21).getName());
        assertEquals("https://r7.audioknigi.xyz/23a2bbd78b811014/audio/mertvaja-tishina-tom-3-01.mp3"
                + "?md5=v_Ih6AT1dmMnlcYSKdXxzA&expires=1792887479", chapters.get(0).getUrl());
        assertEquals(1201, chapters.get(0).getTime());
        assertHttps(chapters);
    }

    @Test
    public void abmp3Fixture() throws IOException {
        Document doc = page("abmp3", ABMP3);
        String playlistUrl = Players.resolve(ABMP3, Players.playerjsFile(doc));
        assertEquals("https://9giiu0g54k8c.redirectto.cc/s02/images/audiobooks/2/9/2/292.pl.txt", playlistUrl);
        String bookName = Abmp3.parseBookName(doc);
        assertEquals("Sapiens: краткая история человечества", bookName);
        List<AudioPOJO> chapters = Players.parsePlayerjsPlaylist(text("abmp3/playlist.txt"), playlistUrl, bookName);
        assertEquals(110, chapters.size());
        assertEquals("01_01_01_Glava 1. Nichem ne vydelyayuscheesya zhivotnoe.mp3", chapters.get(0).getName());
        assertEquals("04_21_00_Posleslovie. Zhivotnye, stavshie bogami.mp3", chapters.get(109).getName());
        assertEquals(bookName, chapters.get(109).getBookName());
        assertHttps(chapters);
    }

    @Test
    public void aknigaFixtureSharesOneHlsUrlWithMonotonicClips() throws IOException {
        Document doc = page("akniga", AKNIGA);
        assertEquals("a174c633d4a35d4e68bb5e3b31033ea9", Akniga.securityKey(doc));
        assertEquals("113316", Akniga.bid(doc));
        List<AudioPOJO> chapters = Akniga.parseTracks(doc, text("akniga/ajax.json"), AKNIGA);
        assertEquals(15, chapters.size());
        assertEquals("Юхан Теорин - Санкта-Психо_01", chapters.get(0).getName());
        assertEquals("Юхан Теорин - Санкта-Психо_15", chapters.get(14).getName());
        String hls = chapters.get(0).getUrl();
        assertTrue(hls, hls.startsWith("https://") && hls.contains(".m3u8"));
        int previousFinish = 0;
        for (AudioPOJO chapter : chapters) {
            assertEquals(hls, chapter.getUrl());
            assertEquals(previousFinish, chapter.getTimeStart());
            assertTrue(chapter.getTimeFinish() > chapter.getTimeStart());
            previousFinish = chapter.getTimeFinish();
        }
        assertEquals(43510, previousFinish);
    }

    @Test
    public void aknigaWithoutBookJsonOrAudioTagIsAnError() throws IOException {
        assertThrows(IllegalStateException.class, () -> Akniga.parseTracks(page("akniga", AKNIGA), null, AKNIGA));
    }

    @Test
    public void bazaKnigFixture() throws IOException {
        List<AudioPOJO> chapters = BazaKnig.parseTracks(page("bazaknig", BAZA_KNIG), BAZA_KNIG);
        assertEquals(22, chapters.size());
        assertEquals("01. Глава_01", chapters.get(0).getName());
        assertEquals("22. Глава_22", chapters.get(21).getName());
        assertEquals("Таксист из Forbes", chapters.get(0).getBookName());
        assertTrue(chapters.get(0).getUrl().startsWith("https://archive.org/download/"));
        assertHttps(chapters);
    }

    @Test
    public void bazaKnigOtherVoiceIsNotTheFirstVoice() throws IOException {
        // the fixture's content-tab2 has no player: voice 2 must not fall back to voice 1's list
        assertThrows(IllegalStateException.class,
                () -> BazaKnig.parseTracks(page("bazaknig", BAZA_KNIG), BAZA_KNIG + "?sorce=2&utm=x"));
    }

    @Test
    public void movedHostIsParsedAsBazaKnig() throws IOException {
        String[] requested = new String[1];
        List<AudioPOJO> chapters = AudioModel.load(BAZA_KNIG.replace("baza-knig.top", "baza-knig.ink"), url -> {
            requested[0] = url;
            return page("bazaknig", url);
        });
        assertEquals(BAZA_KNIG, requested[0]);
        assertEquals(22, chapters.size());
    }

    @Test
    public void bazaKnigArchiveMirrorKeepsOriginalMp3sOnceSortedAndEncoded() {
        Document doc = Jsoup.parse("<div id=\"fr\" data=\"<iframe src=&quot;https://archive.org/embed/book-1"
                + "?playlist=1&amp;list_height=150&quot;></iframe>\"></div>", BAZA_KNIG);
        assertEquals("https://archive.org/embed/book-1?playlist=1&list_height=150",
                BazaKnig.frameUrl(doc.getElementById("fr"), BAZA_KNIG));
        String metadata = "{\"metadata\":{\"identifier\":\"book-1\"},\"files\":["
                + "{\"name\":\"02 Глава.mp3\",\"format\":\"VBR MP3\",\"source\":\"original\",\"length\":\"61.5\"},"
                + "{\"name\":\"01 Глава.mp3\",\"title\":\"Глава 1\",\"format\":\"VBR MP3\",\"source\":\"original\"},"
                + "{\"name\":\"01 Глава_64kb.mp3\",\"format\":\"64Kbps MP3\",\"source\":\"derivative\"},"
                + "{\"name\":\"cover.jpg\",\"format\":\"JPEG\",\"source\":\"original\"}]}";
        List<AudioPOJO> chapters = BazaKnig.parseArchiveOrgMetadata(metadata, "Книга");
        assertEquals(2, chapters.size());
        assertEquals("Глава 1", chapters.get(0).getName());
        assertEquals("https://archive.org/download/book-1/01%20%D0%93%D0%BB%D0%B0%D0%B2%D0%B0.mp3",
                chapters.get(0).getUrl());
        assertEquals("02 Глава.mp3", chapters.get(1).getName());
        assertEquals(61, chapters.get(1).getTime());
        assertThrows(IllegalStateException.class,
                () -> BazaKnig.parseArchiveOrgMetadata("{\"is_dark\":true,\"files\":[]}", "Книга"));
    }

    @Test
    public void knigobludFixture() throws IOException {
        List<AudioPOJO> chapters = com.fanok.audiobooks.source.Knigoblud.parseTracks(page("knigoblud", KNIGOBLUD), KNIGOBLUD);
        assertEquals(12, chapters.size());
        assertEquals("00_01", chapters.get(0).getName());
        assertEquals("03_02", chapters.get(11).getName());
        assertEquals(51, chapters.get(0).getTime());
        assertHttps(chapters);
    }

    @Test
    public void otrubFixtureHasBookName() throws IOException {
        List<AudioPOJO> chapters = com.fanok.audiobooks.source.Otrub.parseTracks(page("otrub", OTRUB), OTRUB);
        assertEquals(24, chapters.size());
        assertEquals("Хозяин Оков XV 01", chapters.get(0).getName());
        assertEquals("Хозяин Оков XV 24", chapters.get(23).getName());
        assertEquals("Хозяин Оков XV", chapters.get(0).getBookName());
        assertHttps(chapters);
    }

    @Test
    public void poleknigFixture() throws IOException {
        Document doc = page("poleknig", POLEKNIG);
        String playlistUrl = Players.resolve(POLEKNIG, Players.playerjsFile(doc));
        assertEquals("https://poleknig.com/books/269329/playlist.txt?t=1792708495", playlistUrl);
        List<AudioPOJO> chapters = Players.parsePlayerjsPlaylist(text("poleknig/playlist.json"), playlistUrl, "Молчание");
        assertEquals(37, chapters.size());
        assertEquals("00-prolog", chapters.get(0).getName());
        assertEquals("36-glava-36", chapters.get(36).getName());
        assertHttps(chapters);
    }

    @Test
    public void audiobooSyntheticFixtureFlattensFoldersAndDecodesFiles() throws IOException {
        List<AudioPOJO> chapters = Audioboo.parseTracks(page("audioboo", AUDIOBOO), AUDIOBOO);
        assertEquals(4, chapters.size()); // the item without a file is skipped
        assertEquals("Глава 1", chapters.get(0).getName());
        assertEquals("https://audioboo.org/uploads/audio/01.mp3", chapters.get(0).getUrl());
        assertEquals("https://cdn.audioboo.org/02.mp3", chapters.get(1).getUrl()); // unpadded base64
        assertEquals("Глава 3 [бонус]", chapters.get(2).getName());
        assertEquals("https://cdn.audioboo.org/3.mp3", chapters.get(2).getUrl());
        assertEquals("", chapters.get(3).getName());
        assertEquals("Синтетическая книга", chapters.get(3).getBookName());
        assertHttps(chapters);
    }

    @Test
    public void audiobooLivePlaylistDecodesGoPhpLinksToArchiveOrg() throws IOException {
        String url = com.fanok.audiobooks.BookPageFixtures.BOOK_PAGES[6][1];
        List<AudioPOJO> chapters = Audioboo.parseTracks(
                com.fanok.audiobooks.BookPageFixtures.load(com.fanok.audiobooks.BookPageFixtures.BOOK_PAGES[6][0], url),
                url);
        assertEquals(21, chapters.size());
        assertEquals("01. Глава 1", chapters.get(0).getName());
        for (AudioPOJO chapter : chapters) {
            // audio is not on audioboo.org (whose Cloudflare challenges every GET)
            assertTrue(chapter.getUrl(), chapter.getUrl().startsWith("https://archive.org/download/"));
        }
    }

    @Test
    public void audiobooChallengePropagatesAsCookesExeption() throws Exception {
        byte[] challenge = bytes("audioboo/challenge.html");
        try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            Thread thread = new Thread(() -> serveOnce(server, 403, challenge));
            thread.start();
            String local = "http://127.0.0.1:" + server.getLocalPort() + "/";
            AudioModel.audio(AUDIOBOO, url -> SiteHttp.get(local)).test()
                    .assertNoValues()
                    .assertError(CookesExeption.class);
            thread.join(5000);
        }
    }

    @Test
    public void unknownHostIsIllegalArgument() {
        AudioModel.audio("https://example.com/book/1", url -> {
            fail("nothing is fetched for an unsupported host");
            return null;
        }).test().assertNoValues().assertError(IllegalArgumentException.class);
    }

    @Test
    public void pageWithoutPlayerScriptIsIllegalState() {
        Document doc = Jsoup.parse("<html><body><h1>Книга</h1>"
                + "<script>domReady(function() { var faves = new PageFaves(1); });</script></body></html>", KNIGAVUHE);
        assertThrows(IllegalStateException.class, () -> Knigavuhe.parseTracks(doc, KNIGAVUHE));
        assertThrows(IllegalStateException.class, () -> Izibuk.parseTracks(doc, IZIBUK));
        assertThrows(IllegalStateException.class, () -> com.fanok.audiobooks.source.Knigoblud.parseTracks(doc, KNIGOBLUD));
        assertThrows(IllegalStateException.class, () -> com.fanok.audiobooks.source.Otrub.parseTracks(doc, OTRUB));
        assertThrows(IllegalStateException.class, () -> Audioboo.parseTracks(doc, AUDIOBOO));
        assertThrows(IllegalStateException.class, () -> BazaKnig.parseTracks(doc, BAZA_KNIG));
        // the marker without its JSON (cut-off page) is the same typed error
        Document cut = Jsoup.parse("<script>var player = new BookPlayer(1, [{\"title\":\"a\"</script>", KNIGAVUHE);
        assertThrows(IllegalStateException.class, () -> Knigavuhe.parseTracks(cut, KNIGAVUHE));
    }

    @Test
    public void emptyNameIsKeptAndMalformedDurationTolerated() {
        Document doc = Jsoup.parse("<h1>Книга</h1><script>KB.playerInit({\"playlist\":["
                + "{\"title\":\"\",\"duration\":\"12.5\",\"src\":\"https://cdn.example.org/1.mp3\"},"
                + "{\"duration\":\"n/a\",\"src\":\"/2.mp3\"}]});</script>", KNIGOBLUD);
        List<AudioPOJO> chapters = com.fanok.audiobooks.source.Knigoblud.parseTracks(doc, KNIGOBLUD);
        assertEquals(2, chapters.size());
        assertEquals("", chapters.get(0).getName());
        assertEquals(12, chapters.get(0).getTime());
        assertEquals("", chapters.get(1).getName());
        assertEquals(0, chapters.get(1).getTime());
        assertEquals("https://www.knigoblud.club/2.mp3", chapters.get(1).getUrl());
    }

    @Test
    public void shortTrackTupleIsSkippedOthersKept() {
        Document doc = Jsoup.parse("<script>domReady(function() { var player = new XSPlayer({"
                + "\"mp3_url_prefix\":\"cdn.example.org\\/audio\",\"sign\":\"md5=a\",\"tracks\":["
                + "[1,\"A\",10,0,\"a.mp3\"],[2,\"B\",20],[3,null,30,0,\"c.mp3\"]]}); });</script>", IZIBUK);
        List<AudioPOJO> chapters = Izibuk.parseTracks(doc, IZIBUK);
        assertEquals(2, chapters.size());
        assertEquals("https://cdn.example.org/audio/a.mp3?md5=a", chapters.get(0).getUrl());
        assertEquals("", chapters.get(1).getName());
        assertEquals(30, chapters.get(1).getTime());
    }

    @Test
    public void playerWithoutPlayableChaptersIsAnError() {
        Document empty = Jsoup.parse("<script>KB.playerInit({\"playlist\":[]});</script>", KNIGOBLUD);
        assertThrows(IllegalStateException.class, () -> com.fanok.audiobooks.source.Knigoblud.parseTracks(empty, KNIGOBLUD));
        Document bad = Jsoup.parse("<script>KB.playerInit({\"playlist\":[{\"title\":\"x\"},"
                + "{\"src\":\"javascript:alert(1)\"}]});</script>", KNIGOBLUD);
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> com.fanok.audiobooks.source.Knigoblud.parseTracks(bad, KNIGOBLUD));
        assertTrue(e.getMessage(), e.getMessage().contains("2 skipped"));
    }

    @Test
    public void base64OfAnyLengthIsDecoded() {
        assertEquals("hi", decode("aGk="));
        assertEquals("hi", decode("aGk"));          // len % 4 == 3
        assertEquals("hello", decode("aGVsbG8"));   // len % 4 == 3
        assertEquals("h", decode("aA"));            // len % 4 == 2
        assertEquals("", decode("a"));              // len % 4 == 1: no whole byte, no exception
        assertEquals("https://cdn.audioboo.org/02.mp3",
                Players.playerjsUrl("//_//aHR0cHM6Ly9jZG4uYXVkaW9ib28ub3JnLzAyLm1wMw"));
        assertEquals("", Players.playerjsUrl("//_//bm90IGEgdXJs")); // decodes to junk → dropped
    }

    @Test
    public void audioPojoNormalisesNullNameAndUrl() {
        AudioPOJO audio = new AudioPOJO();
        audio.setName(null);
        audio.setUrl(null);
        assertEquals("", audio.getName());
        assertEquals("", audio.getUrl());
        audio.setUrl("https://cdn.example.org/1.mp3");
        audio.setUrl("  ");
        assertEquals("", audio.getUrl());
    }

    @Test
    public void aknigaClipsAreClosedAndMonotonic() throws IOException {
        // the middle item has no duration and nothing after it starts explicitly: no [10,-1] clip
        List<AudioPOJO> chapters = akniga("[{\"duration\":10},{\"title\":\"missing\"},{\"duration\":5}]");
        assertEquals(2, chapters.size());
        assertClip(chapters.get(0), 0, 10);
        assertClip(chapters.get(1), 10, 15);
        // a missing duration ends at the next explicit start; a backwards start is clamped
        chapters = akniga("[{\"duration\":10},{\"title\":\"m\"},{\"time_from_start\":30,\"duration\":5},"
                + "{\"time_from_start\":0,\"duration\":5},{\"title\":\"last\"}]");
        assertEquals(5, chapters.size());
        assertClip(chapters.get(0), 0, 10);
        assertClip(chapters.get(1), 10, 30);
        assertClip(chapters.get(2), 30, 35);
        assertClip(chapters.get(3), 35, 40);
        assertClip(chapters.get(4), 40, -1); // only the last chapter is open-ended
    }

    @Test
    public void commentedOutPlayerIsSkipped() {
        Document doc = Jsoup.parse("<script>/* new BookPlayer(1,[{\"title\":\"old\",\"url\":\"https://cdn.example.org/old.mp3\"}]); */\n"
                + "// new BookPlayer(1,[{\"title\":\"older\",\"url\":\"https://cdn.example.org/older.mp3\"}]);\n"
                + "var p = new BookPlayer(2, [{\"title\":\"new\",\"url\":\"https://cdn.example.org/new.mp3\"}]);</script>",
                KNIGAVUHE);
        List<AudioPOJO> chapters = Knigavuhe.parseTracks(doc, KNIGAVUHE);
        assertEquals(1, chapters.size());
        assertEquals("new", chapters.get(0).getName());
    }

    @Test
    public void bazaKnigRequestedVoiceWithoutItsTabIsAnError() {
        Document doc = Jsoup.parse("<h1>Книга</h1><div id=\"content-tab1\"><script>new Playerjs({id:\"player\","
                + "file:[{\"title\":\"a\",\"file\":\"https://cdn.example.org/1.mp3\"}]});</script></div>", BAZA_KNIG);
        assertEquals(1, BazaKnig.parseTracks(doc, BAZA_KNIG).size());
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> BazaKnig.parseTracks(doc, BAZA_KNIG + "?sorce=3"));
        assertTrue(e.getMessage(), e.getMessage().contains("content-tab3"));
    }

    @Test
    public void izibukTrackWithoutFileIsSkipped() {
        String tracks = "[2,\"B\",20,0,null],[3,\"C\",30,0,\"\"]";
        Document doc = izibuk("[1,\"A\",10,0,\"a.mp3\"]," + tracks);
        List<AudioPOJO> chapters = Izibuk.parseTracks(doc, IZIBUK);
        assertEquals(1, chapters.size());
        assertEquals("https://cdn.example.org/audio/a.mp3?md5=a", chapters.get(0).getUrl());
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> Izibuk.parseTracks(izibuk(tracks), IZIBUK));
        assertTrue(e.getMessage(), e.getMessage().contains("2 skipped"));
    }

    @Test
    public void archiveOrgFileWithoutNameIsSkipped() {
        String metadata = "{\"metadata\":{\"identifier\":\"book-1\"},\"files\":["
                + "{\"format\":\"VBR MP3\",\"source\":\"original\"},"
                + "{\"name\":\"01.mp3\",\"format\":\"VBR MP3\",\"source\":\"original\"}]}";
        List<AudioPOJO> chapters = BazaKnig.parseArchiveOrgMetadata(metadata, "Книга");
        assertEquals(1, chapters.size());
        assertEquals("https://archive.org/download/book-1/01.mp3", chapters.get(0).getUrl());
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> BazaKnig.parseArchiveOrgMetadata(
                "{\"metadata\":{\"identifier\":\"b\"},\"files\":[{\"format\":\"VBR MP3\"}]}", "Книга"));
        assertTrue(e.getMessage(), e.getMessage().contains("1 skipped"));
    }

    @Test
    public void archiveOrgChoosesOneMp3PerRecording() {
        String metadata = "{\"metadata\":{\"identifier\":\"book-1\"},\"files\":["
                + "{\"name\":\"01.mp3\",\"format\":\"VBR MP3\",\"source\":\"original\"},"
                + "{\"name\":\"01_64kb.mp3\",\"format\":\"64Kbps MP3\",\"source\":\"derivative\",\"original\":\"01.mp3\"},"
                + "{\"name\":\"02.flac\",\"format\":\"Flac\",\"source\":\"original\"},"
                + "{\"name\":\"02.mp3\",\"format\":\"VBR MP3\",\"source\":\"derivative\",\"original\":\"02.flac\"},"
                + "{\"name\":\"03_64kb.mp3\",\"format\":\"64Kbps MP3\",\"source\":\"derivative\",\"original\":\"03.flac\"},"
                + "{\"name\":\"03.mp3\",\"format\":\"VBR MP3\",\"source\":\"derivative\",\"original\":\"03.flac\"}]}";
        List<AudioPOJO> chapters = BazaKnig.parseArchiveOrgMetadata(metadata, "Книга");
        assertEquals(3, chapters.size());
        String download = "https://archive.org/download/book-1/";
        assertEquals(download + "01.mp3", chapters.get(0).getUrl());
        assertEquals(download + "02.mp3", chapters.get(1).getUrl());
        assertEquals(download + "03.mp3", chapters.get(2).getUrl());
    }

    @Test
    public void relativeUrlsResolveAgainstTheFinalLocation() throws IOException {
        String player = "<script>var p = new BookPlayer(1, [{\"title\":\"a\",\"url\":\"1.mp3\"}]);</script>";
        List<AudioPOJO> chapters = AudioModel.load("https://knigavuhe.org/book/old",
                url -> Jsoup.parse(player, "https://knigavuhe.org/book/new/")); // after the redirect
        assertEquals("https://knigavuhe.org/book/new/1.mp3", chapters.get(0).getUrl());
        Document frame = Jsoup.parse("<div class=\"js-play8-playlist\" value='[{\"sources\":[{\"file\":\"audio/1.mp3\"}]}]'>"
                + "</div>", "https://player.example/book/");
        assertEquals("https://player.example/book/audio/1.mp3",
                BazaKnig.parsePlay8(frame, "Книга").get(0).getUrl());
    }

    @Test
    public void shuffledPlayerjsMirrorsGiveOneStableUrl() {
        // poleknig answers "a or b" and "b or a" on different loads of the same playlist
        String a = "https://poleknig.com/files/1?d=1&h=x";
        String b = "https://poleknig.com/files/1?h=x";
        String first = Players.parsePlayerjsPlaylist("[{\"title\":\"1\",\"file\":\"" + a + " or " + b + "\"}]",
                "https://poleknig.com/books/1", "Книга").get(0).getUrl();
        String second = Players.parsePlayerjsPlaylist("[{\"title\":\"1\",\"file\":\"" + b + " or " + a + "\"}]",
                "https://poleknig.com/books/1", "Книга").get(0).getUrl();
        assertEquals(first, second);
        assertTrue(first.equals(a) || first.equals(b));
        assertEquals(b, Players.oneOf(b));
    }

    @Test
    public void takenDownBookSaysSoInsteadOfAParserError() {
        // live wording: poleknig "Удалено правообладателем.", otrub "Удалена по жалобе правообладателя"
        for (String[] page : new String[][]{
                {"https://poleknig.com/books/232597", "<h1>Книга</h1><p>Удалено правообладателем.</p>"},
                {"https://otrub.in/x.html", "<h1>Книга</h1><div>Удалена по жалобе правообладателя</div>"},
                {"https://www.knigoblud.club/b1", "<h1>Книга</h1><b>Заблокировано по требованию правообладателя</b>"}}) {
            assertThrows(page[0], BookRemovedException.class,
                    () -> AudioModel.load(page[0], url -> Jsoup.parse(page[1], url)));
        }
        // a footer link alone is no takedown notice: the parser's own error stays
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> AudioModel.load(
                "https://poleknig.com/books/1", url -> Jsoup.parse("<a href=/copyright>Для правообладателей</a>", url)));
        assertTrue(!(e instanceof BookRemovedException));
    }

    @Test
    public void goPhpBase64KeepsItsPlus() {
        // "aHR0cHM6Ly94Lm9yZy9+Pn4vMS5tcDM=" is base64 of https://x.org/~>~/1.mp3
        assertEquals("https://x.org/~>~/1.mp3",
                Players.playerjsUrl("/engine/go.php?url=aHR0cHM6Ly94Lm9yZy9+Pn4vMS5tcDM%3D"));
    }

    @Test
    public void aknigaBidIsTheCurrentBooksNotARelatedOne() {
        Document doc = Jsoup.parse("<div class=\"related\"><a class=\"book\" data-bid=\"2\"></a></div>"
                + "<article class=\"book--page\" data-bid=\"1\"><div class=\"player--chapters\" data-bid=\"1\"></div>"
                + "</article>", AKNIGA);
        assertEquals("1", Akniga.bid(doc));
    }

    @Test
    public void playerjsMarkersWithAndWithoutJunk() {
        // marker+junk+marker: the junk ("$$#!!@#") between the markers is dropped
        assertEquals("https://cdn.example.org/pair.mp3",
                Players.playerjsUrl("#2aHR0cHM6Ly9jZG4uZXhhbXBsZS5vcmcv//_//JCQjISFAIw==//_//cGFpci5tcDM="));
        // markers only: everything between them is payload
        String b64 = "aHR0cHM6Ly9jZG4uZXhhbXBsZS5vcmcvb2svMS5tcDM="; // https://cdn.example.org/ok/1.mp3
        assertEquals("https://cdn.example.org/ok/1.mp3",
                Players.playerjsUrl("#2" + b64.substring(0, 4) + "//_//" + b64.substring(4, 16) + "//_//"
                        + b64.substring(16)));
    }

    private static List<AudioPOJO> akniga(String items) throws IOException {
        JsonObject book = JsonParser.parseString(text("akniga/ajax.json")).getAsJsonObject();
        book.add("items", JsonParser.parseString(items));
        return Akniga.parseTracks(Jsoup.parse(""), book.toString(), AKNIGA);
    }

    private static void assertClip(AudioPOJO clip, int start, int finish) {
        assertEquals(start, clip.getTimeStart());
        assertEquals(finish, clip.getTimeFinish());
    }

    private static Document izibuk(String tracks) {
        return Jsoup.parse("<script>var player = new XSPlayer({\"mp3_url_prefix\":\"cdn.example.org\\/audio\","
                + "\"sign\":\"md5=a\",\"tracks\":[" + tracks + "]});</script>", IZIBUK);
    }

    private static String decode(String base64) {
        return new String(Players.decodeBase64(base64, Players.BASE64), StandardCharsets.UTF_8);
    }

    private static void assertHttps(List<AudioPOJO> chapters) {
        for (AudioPOJO chapter : chapters) {
            assertTrue(chapter.getUrl(), chapter.getUrl().startsWith("https://"));
        }
    }

    private static Document page(String source, String baseUri) throws IOException {
        try (InputStream in = resource(source + "/book.html")) {
            return Jsoup.parse(in, null, baseUri);
        }
    }

    private static String text(String path) throws IOException {
        return new String(bytes(path), StandardCharsets.UTF_8);
    }

    private static byte[] bytes(String path) throws IOException {
        try (InputStream in = resource(path)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            for (int n; (n = in.read(buffer)) > 0; ) {
                out.write(buffer, 0, n);
            }
            return out.toByteArray();
        }
    }

    private static InputStream resource(String path) {
        InputStream in = AudioModelTest.class.getClassLoader().getResourceAsStream("fixtures/" + path);
        assertNotNull("missing fixture " + path, in);
        return in;
    }

    /** Answers one HTTP request with {@code body} and {@code status}, as the challenge wall does. */
    private static void serveOnce(ServerSocket server, int status, byte[] body) {
        try (Socket socket = server.accept()) {
            BufferedReader request = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.ISO_8859_1));
            for (String line = request.readLine(); line != null && !line.isEmpty(); line = request.readLine()) {
                // headers are not needed
            }
            OutputStream out = socket.getOutputStream();
            out.write(("HTTP/1.1 " + status + " Forbidden\r\nContent-Type: text/html; charset=UTF-8\r\n"
                    + "Content-Length: " + body.length + "\r\nConnection: close\r\n\r\n")
                    .getBytes(StandardCharsets.ISO_8859_1));
            out.write(body);
            out.flush();
        } catch (IOException ignored) {
            // the test fails on its own assertions
        }
    }
}
