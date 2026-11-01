package com.fanok.audiobooks.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;
import androidx.annotation.NonNull;
import androidx.media3.common.C;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.database.StandaloneDatabaseProvider;
import androidx.media3.datasource.ByteArrayDataSource;
import androidx.media3.datasource.DataSpec;
import androidx.media3.datasource.cache.CacheDataSource;
import androidx.media3.datasource.cache.CacheWriter;
import androidx.media3.datasource.cache.NoOpCacheEvictor;
import androidx.media3.datasource.cache.SimpleCache;
import androidx.media3.exoplayer.offline.Download;
import androidx.media3.exoplayer.offline.DownloadRequest;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Chapters cut out of downloads as the player's own extractors and the platform muxer see them: an AES-128 HLS stream
 * of MPEG-TS segments with AAC (akniga's form) into .m4a, a single .m4a and a single .mp3 file clipped by time.
 */
@UnstableApi
@RunWith(AndroidJUnit4.class)
public class ChapterExtractorDeviceTest {

    private static final String BASE = "https://cdn.example.org/b/1/";
    private static final byte[] KEY = "0123456789abcdef".getBytes(StandardCharsets.US_ASCII);
    private static final String IV = "0xbb8727fe12457c860e7361e31b3614d0";
    /** AAC-LC frames of 1024 samples at 44.1 kHz. */
    private static final double FRAME_US = 1024 * 1_000_000.0 / 44100;
    private File base;
    /** A real decrypted akniga segment in the app's files, copied by hand for a check on live data; else skipped. */
    private File liveSegment;
    private File out;
    private StandaloneDatabaseProvider database;
    private SimpleCache cache;

    @Before
    public void setUp() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        base = new File(context.getCacheDir(), "chapter-extractor-" + System.nanoTime());
        out = new File(base, "out");
        assertTrue(out.mkdirs());
        database = new StandaloneDatabaseProvider(context);
        cache = new SimpleCache(new File(base, "cache"), new NoOpCacheEvictor(), database);
        liveSegment = new File(context.getFilesDir(), "akniga-seq0.ts");
    }

    @After
    public void tearDown() {
        cache.release();
        SimpleCache.delete(new File(base, "cache"), database);
        deleteRecursively(base);
    }

    @Test
    public void encryptedTsStreamIsCutIntoM4aChapters() throws Exception {
        // two segments of 86 frames each, every one starting with its own PAT and PMT
        byte[] seq0 = TsWriter.segment(0, 86, 90_000);
        byte[] seq1 = TsWriter.segment(86, 86, 90_000);
        Download stream = hls(new double[]{86 * FRAME_US / 1e6, 86 * FRAME_US / 1e6}, seq0, seq1);

        Map<Integer, File> files = extract(stream, new ChapterExtractor.Clip(0, 1_500_000),
                new ChapterExtractor.Clip(1_500_000, Long.MAX_VALUE));

        int first = samples(files.get(0), MimeTypes.AUDIO_AAC);
        int second = samples(files.get(1), MimeTypes.AUDIO_AAC);
        assertEquals(172, first + second);
        assertTrue("first chapter: " + first, Math.abs(first - 65) <= 1);
        assertTrue(files.get(0).getName().endsWith(".m4a"));
    }

    @Test
    public void liveAknigaSegmentIsCutWithoutReencoding() throws Exception {
        Assume.assumeTrue("copy a decrypted akniga segment to " + liveSegment, liveSegment.canRead());
        byte[] ts = readFile(liveSegment);
        Download stream = hls(new double[]{30.000178}, ts);

        Map<Integer, File> files = extract(stream, new ChapterExtractor.Clip(0, 10_000_000),
                new ChapterExtractor.Clip(10_000_000, Long.MAX_VALUE));

        assertEquals(10.0, durationUs(files.get(0)) / 1e6, 0.1);
        assertEquals(20.0, durationUs(files.get(1)) / 1e6, 0.1);
    }

    @Test
    public void singleM4aFileIsClipped() throws Exception {
        Download stream = hls(new double[]{86 * FRAME_US / 1e6}, TsWriter.segment(0, 86, 90_000));
        File whole = extract(stream, new ChapterExtractor.Clip(0, Long.MAX_VALUE)).get(0);
        Download file = progressive("https://cdn.example.org/b/2/book.m4a", readFile(whole));

        File clip = extract(file, new ChapterExtractor.Clip(500_000, 1_500_000)).get(0);

        int frames = samples(clip, MimeTypes.AUDIO_AAC);
        assertTrue("frames: " + frames, Math.abs(frames - 43) <= 1);
    }

    @Test
    public void singleMp3FileIsClippedIntoMp3Frames() throws Exception {
        // MPEG-1 Layer III, 128 kbit/s, 44.1 kHz, no padding: 417-byte frames of 1152 samples
        byte[] frame = new byte[417];
        frame[0] = (byte) 0xff;
        frame[1] = (byte) 0xfb;
        frame[2] = (byte) 0x90;
        frame[3] = (byte) 0x64;
        ByteArrayOutputStream mp3 = new ByteArrayOutputStream();
        for (int i = 0; i < 200; i++) mp3.write(frame);
        Download file = progressive("https://cdn.example.org/b/3/book.mp3", mp3.toByteArray());

        File clip = extract(file, new ChapterExtractor.Clip(1_000_000, 2_000_000)).get(0);

        assertTrue(clip.getName().endsWith(".mp3"));
        byte[] written = readFile(clip);
        assertEquals(0, written.length % 417);
        int frames = written.length / 417;
        // 1 s of 26.12 ms frames
        assertTrue("frames: " + frames, Math.abs(frames - 38) <= 1);
        for (int i = 0; i < written.length; i += 417) {
            assertEquals((byte) 0xff, written[i]);
            assertEquals((byte) 0xfb, written[i + 1]);
        }
    }

    // ---------------------------------------------------------------- helpers

    /** Clips extracted into {@link #out}, by index; any failure fails the test. */
    private Map<Integer, File> extract(Download download, ChapterExtractor.Clip... clips) throws IOException {
        final Map<Integer, File> done = new HashMap<>();
        final List<String> failures = new ArrayList<>();
        ChapterExtractor.extract(cache, download, Arrays.asList(clips), new ChapterExtractor.Output() {
            @NonNull
            @Override
            public File open(int index) throws IOException {
                return File.createTempFile("clip" + index + "-", ".part", out);
            }

            @Override
            public void done(int index, @NonNull File file, @NonNull String extension) {
                File named = new File(out, "clip" + index + "-" + System.nanoTime() + "." + extension);
                assertTrue(file.renameTo(named));
                done.put(index, named);
            }

            @Override
            public void failed(int index, @NonNull Exception error) {
                failures.add(index + ": " + error);
            }
        });
        assertEquals(new ArrayList<String>(), failures);
        assertEquals(clips.length, done.size());
        return done;
    }

    /** A completed HLS download: playlist, key and the segments encrypted with it, in the cache. */
    private Download hls(double[] durations, byte[]... segments) throws Exception {
        StringBuilder playlist = new StringBuilder("#EXTM3U\n#EXT-X-VERSION:3\n#EXT-X-TARGETDURATION:31\n"
                + "#EXT-X-MEDIA-SEQUENCE:0\n#EXT-X-PLAYLIST-TYPE:VOD\n"
                + "#EXT-X-KEY:METHOD=AES-128,URI=\"https://keys.example.org/enc.key\",IV=" + IV + "\n");
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS7Padding");
        for (int i = 0; i < segments.length; i++) {
            playlist.append("#EXTINF:").append(durations[i]).append(",\nseq").append(i).append(".ts\n");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(KEY, "AES"),
                    new IvParameterSpec(ChapterExtractor.ivBytes(IV)));
            put(BASE + "seq" + i + ".ts", null, cipher.doFinal(segments[i]));
        }
        playlist.append("#EXT-X-ENDLIST\n");
        put("https://keys.example.org/enc.key", null, KEY);
        String uri = BASE + "pl.m3u8?res=token&expires=1";
        put(uri, null, playlist.toString().getBytes(StandardCharsets.UTF_8));
        DownloadRequest request = new DownloadRequest.Builder("v1/test/stream/" + System.nanoTime(), Uri.parse(uri))
                .setMimeType(MimeTypes.APPLICATION_M3U8).build();
        return new Download(request, Download.STATE_COMPLETED, 0, 0, C.LENGTH_UNSET, 0, 0);
    }

    private Download progressive(String uri, byte[] data) throws IOException {
        String key = "v1/test/chapter/" + System.nanoTime();
        put(uri, key, data);
        DownloadRequest request = new DownloadRequest.Builder(key, Uri.parse(uri)).setCustomCacheKey(key).build();
        return new Download(request, Download.STATE_COMPLETED, 0, 0, data.length, 0, 0);
    }

    private void put(String uri, String key, byte[] data) throws IOException {
        CacheDataSource writer = new CacheDataSource.Factory().setCache(cache)
                .setUpstreamDataSourceFactory(() -> new ByteArrayDataSource(data)).createDataSource();
        new CacheWriter(writer, new DataSpec.Builder().setUri(Uri.parse(uri)).setKey(key).build(), null, null)
                .cache();
    }

    /** Samples of the one track of {@code file}, which must have {@code mime}. */
    private static int samples(File file, String mime) throws IOException {
        MediaExtractor extractor = new MediaExtractor();
        try {
            extractor.setDataSource(file.getAbsolutePath());
            assertEquals(1, extractor.getTrackCount());
            assertEquals(mime, extractor.getTrackFormat(0).getString(MediaFormat.KEY_MIME));
            extractor.selectTrack(0);
            ByteBuffer buffer = ByteBuffer.allocate(64 * 1024);
            int count = 0;
            while (extractor.readSampleData(buffer, 0) >= 0) {
                count++;
                extractor.advance();
            }
            return count;
        } finally {
            extractor.release();
        }
    }

    private static long durationUs(File file) throws IOException {
        MediaExtractor extractor = new MediaExtractor();
        try {
            extractor.setDataSource(file.getAbsolutePath());
            assertEquals(MimeTypes.AUDIO_AAC, extractor.getTrackFormat(0).getString(MediaFormat.KEY_MIME));
            return extractor.getTrackFormat(0).getLong(MediaFormat.KEY_DURATION);
        } finally {
            extractor.release();
        }
    }

    private static byte[] readFile(File file) throws IOException {
        try (FileInputStream in = new FileInputStream(file)) {
            ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = in.read(buffer)) != -1) outBytes.write(buffer, 0, read);
            return outBytes.toByteArray();
        }
    }

    private static void deleteRecursively(File file) {
        File[] children = file.listFiles();
        if (children != null) for (File child : children) deleteRecursively(child);
        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }

    /** A minimal MPEG-TS writer: PAT, PMT and one ADTS AAC stream (PID 0x100) of silent mono frames in PES. */
    private static final class TsWriter {
        private static final byte[] SILENT_FRAME = {0x21, 0x10, 0x04, 0x60, (byte) 0x8c, 0x1c};
        private static final int FRAMES_PER_PES = 8;

        /** Frames {@code first}..{@code first + count} of the stream, the first one at {@code basePts} (90 kHz). */
        static byte[] segment(int first, int count, long basePts) {
            ByteArrayOutputStream ts = new ByteArrayOutputStream();
            int[] cc = new int[3];
            packets(ts, 0, cc, 0, psi(new byte[]{0x00, (byte) 0xb0, 0x0d, 0x00, 0x01, (byte) 0xc1, 0x00, 0x00,
                    0x00, 0x01, (byte) 0xf0, 0x00}));
            packets(ts, 0x1000, cc, 1, psi(new byte[]{0x02, (byte) 0xb0, 0x12, 0x00, 0x01, (byte) 0xc1, 0x00, 0x00,
                    (byte) 0xe1, 0x00, (byte) 0xf0, 0x00, 0x0f, (byte) 0xe1, 0x00, (byte) 0xf0, 0x00}));
            for (int f = first; f < first + count; f += FRAMES_PER_PES) {
                ByteArrayOutputStream payload = new ByteArrayOutputStream();
                for (int k = f; k < Math.min(f + FRAMES_PER_PES, first + count); k++) payload.write(adts(), 0, 13);
                long pts = basePts + Math.round(f * 1024 * 90_000.0 / 44100);
                packets(ts, 0x100, cc, 2, pes(payload.toByteArray(), pts));
            }
            return ts.toByteArray();
        }

        private static byte[] adts() {
            int length = 7 + SILENT_FRAME.length;
            byte[] frame = new byte[length];
            frame[0] = (byte) 0xff;
            frame[1] = (byte) 0xf1; // MPEG-4, no CRC
            frame[2] = (byte) 0x50; // AAC LC, 44.1 kHz
            frame[3] = (byte) (0x40 | ((length >> 11) & 0x03)); // mono
            frame[4] = (byte) ((length >> 3) & 0xff);
            frame[5] = (byte) (((length & 0x07) << 5) | 0x1f);
            frame[6] = (byte) 0xfc;
            System.arraycopy(SILENT_FRAME, 0, frame, 7, SILENT_FRAME.length);
            return frame;
        }

        private static byte[] pes(byte[] payload, long pts) {
            ByteArrayOutputStream pes = new ByteArrayOutputStream();
            int length = 3 + 5 + payload.length;
            pes.write(new byte[]{0x00, 0x00, 0x01, (byte) 0xc0, (byte) (length >> 8), (byte) length,
                    (byte) 0x80, (byte) 0x80, 0x05}, 0, 9);
            pes.write(new byte[]{(byte) (0x21 | ((pts >> 29) & 0x0e)), (byte) (pts >> 22),
                    (byte) (((pts >> 14) & 0xfe) | 1), (byte) (pts >> 7), (byte) (((pts << 1) & 0xfe) | 1)}, 0, 5);
            pes.write(payload, 0, payload.length);
            return pes.toByteArray();
        }

        /** A PSI section with pointer field and CRC. */
        private static byte[] psi(byte[] section) {
            byte[] withCrc = Arrays.copyOf(section, section.length + 4);
            int crc = crc32(section);
            for (int i = 0; i < 4; i++) withCrc[section.length + i] = (byte) (crc >>> (24 - 8 * i));
            byte[] out = new byte[withCrc.length + 1];
            System.arraycopy(withCrc, 0, out, 1, withCrc.length);
            return out;
        }

        private static int crc32(byte[] data) {
            int crc = 0xffffffff;
            for (byte b : data) {
                crc ^= (b & 0xff) << 24;
                for (int i = 0; i < 8; i++) crc = (crc & 0x80000000) != 0 ? (crc << 1) ^ 0x04c11db7 : crc << 1;
            }
            return crc;
        }

        /** {@code data} in 188-byte packets of {@code pid}, the first with PUSI, the last padded by stuffing. */
        private static void packets(ByteArrayOutputStream ts, int pid, int[] cc, int counter, byte[] data) {
            for (int at = 0; at < data.length; ) {
                int take = Math.min(184, data.length - at);
                byte[] packet = new byte[188];
                packet[0] = 0x47;
                packet[1] = (byte) ((at == 0 ? 0x40 : 0) | ((pid >> 8) & 0x1f));
                packet[2] = (byte) pid;
                int pos = 4;
                if (take < 184) {
                    packet[3] = (byte) (0x30 | (cc[counter] & 0x0f));
                    int adaptation = 183 - take;
                    packet[pos++] = (byte) adaptation;
                    if (adaptation > 0) {
                        packet[pos++] = 0x00;
                        for (int i = 1; i < adaptation; i++) packet[pos++] = (byte) 0xff;
                    }
                } else {
                    packet[3] = (byte) (0x10 | (cc[counter] & 0x0f));
                }
                cc[counter]++;
                System.arraycopy(data, at, packet, pos, take);
                at += take;
                ts.write(packet, 0, 188);
            }
        }
    }
}
