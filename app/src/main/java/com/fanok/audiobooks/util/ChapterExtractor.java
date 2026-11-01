package com.fanok.audiobooks.util;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.media3.common.C;
import androidx.media3.common.DataReader;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.ParsableByteArray;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.UriUtil;
import androidx.media3.datasource.DataSourceInputStream;
import androidx.media3.datasource.DataSpec;
import androidx.media3.datasource.cache.Cache;
import androidx.media3.datasource.cache.CacheDataSource;
import androidx.media3.exoplayer.hls.playlist.HlsMediaPlaylist;
import androidx.media3.exoplayer.hls.playlist.HlsMultivariantPlaylist;
import androidx.media3.exoplayer.hls.playlist.HlsPlaylist;
import androidx.media3.exoplayer.hls.playlist.HlsPlaylistParser;
import androidx.media3.exoplayer.offline.Download;
import androidx.media3.exoplayer.offline.DownloadRequest;
import androidx.media3.extractor.DefaultExtractorInput;
import androidx.media3.extractor.DiscardingTrackOutput;
import androidx.media3.extractor.Extractor;
import androidx.media3.extractor.ExtractorInput;
import androidx.media3.extractor.ExtractorOutput;
import androidx.media3.extractor.PositionHolder;
import androidx.media3.extractor.SeekMap;
import androidx.media3.extractor.TrackOutput;
import androidx.media3.extractor.mp3.Mp3Extractor;
import androidx.media3.extractor.mp4.Mp4Extractor;
import androidx.media3.extractor.ts.AdtsExtractor;
import androidx.media3.extractor.ts.TsExtractor;

import java.io.BufferedOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Cuts chapters out of a completed download into files any player opens, without re-encoding: the audio samples are
 * demuxed by Media3's own extractors from the download cache alone (an HLS stream: its playlist, keys and segments,
 * AES-128 decrypted; a single file: its one resource) and written as they are, MPEG audio frames as .mp3 and AAC in
 * an MP4 container as .m4a. A chapter is [start, end) of the stream's timeline, the one its clipping plays.
 */
@OptIn(markerClass = UnstableApi.class)
public final class ChapterExtractor {

    /** A chapter of the stream: [startUs, endUs); {@link Long#MAX_VALUE} as end runs to the end of the stream. */
    public static final class Clip {
        final long startUs;
        final long endUs;

        public Clip(long startUs, long endUs) {
            this.startUs = startUs;
            this.endUs = endUs;
        }
    }

    /** Where the clips go, called on the extracting thread in the order the clips end. */
    public interface Output {
        /** An empty file clip {@code index} is written to; called when its first sample arrives. */
        @NonNull
        File open(int index) throws IOException;

        /** Clip {@code index} is complete in {@code file}, whose container has {@code extension}. */
        void done(int index, @NonNull File file, @NonNull String extension) throws IOException;

        /** Clip {@code index} could not be written; its file, if any, is already deleted. */
        void failed(int index, @NonNull Exception error);
    }

    private ChapterExtractor() {
    }

    /**
     * Writes every clip of the completed {@code download} from {@code cache}. Each clip ends in {@link Output#done} or
     * {@link Output#failed}; a failure of the stream itself (unknown container or codec, a missing span) is thrown.
     */
    public static void extract(@NonNull Cache cache, @NonNull Download download, @NonNull List<Clip> clips,
                               @NonNull Output output) throws IOException {
        Router router = new Router(clips, output);
        try {
            if (DownloadUtil.isProgressive(download.request)) {
                extractFile(cache, download.request, DownloadUtil.lengthOf(cache, download), router);
            } else {
                extractHls(cache, download.request, router);
            }
            router.endOfStream();
        } catch (IllegalStateException e) {
            // the extractor's output cannot throw IOException itself
            if (e.getCause() instanceof IOException) throw (IOException) e.getCause();
            throw e;
        } finally {
            router.abort();
        }
    }

    // ---------------------------------------------------------------- inputs

    private static CacheDataSource cacheOnly(@NonNull Cache cache) {
        return new CacheDataSource.Factory().setCache(cache).setCacheWriteDataSinkFactory(null).createDataSource();
    }

    /** One file: its own cache key, read from {@code position} on; Mp4 asks to jump within it. */
    private static void extractFile(@NonNull Cache cache, @NonNull DownloadRequest request, long length,
                                    @NonNull Router router) throws IOException {
        String key = DownloadUtil.cacheKeyOf(request);
        Extractor extractor = null;
        long position = 0;
        PositionHolder seek = new PositionHolder();
        Collector collector = new Collector(router, StreamClock.identity());
        while (true) {
            CacheDataSource source = cacheOnly(cache);
            try {
                DataSpec spec = new DataSpec.Builder().setUri(request.uri).setKey(key).setPosition(position)
                        .setLength(length > 0 ? length - position : C.LENGTH_UNSET).build();
                source.open(spec);
                ExtractorInput input = new DefaultExtractorInput(source, position, length > 0 ? length : C.LENGTH_UNSET);
                if (extractor == null) {
                    extractor = sniff(input, new Mp4Extractor(), new AdtsExtractor(), new Mp3Extractor());
                    if (extractor == null) throw new IOException("Unknown audio container of " + key);
                    extractor.init(collector);
                }
                int result = readAll(extractor, input, seek, router);
                if (result != Extractor.RESULT_SEEK) return;
                position = seek.position;
            } finally {
                source.close();
            }
        }
    }

    /** An HLS stream: the media playlist in the cache, its segments in order, each through its own extractor. */
    private static void extractHls(@NonNull Cache cache, @NonNull DownloadRequest request, @NonNull Router router)
            throws IOException {
        HlsMediaPlaylist playlist = mediaPlaylist(cache, request.uri);
        Map<Uri, byte[]> keys = new HashMap<>();
        StreamClock clock = new StreamClock();
        PositionHolder seek = new PositionHolder();
        for (HlsMediaPlaylist.Segment segment : playlist.segments) {
            if (router.finished()) return;
            if (segment.hasGapTag) continue;
            if (router.idleUntil(segment.relativeStartTimeUs + segment.durationUs)) {
                // ends before the next clip starts: not read, the clock goes on by the playlist
                clock.skip();
                continue;
            }
            if (segment.initializationSegment != null) {
                throw new IOException("HLS with initialization segments (fMP4) is not supported");
            }
            clock.beginSegment(segment.relativeStartTimeUs, segment.durationUs);
            Uri uri = UriUtil.resolveToUri(playlist.baseUri, segment.url);
            DataSpec spec = new DataSpec.Builder().setUri(uri)
                    .setPosition(segment.byteRangeOffset).setLength(segment.byteRangeLength).build();
            CacheDataSource source = cacheOnly(cache);
            InputStream stream = new DataSourceInputStream(source, spec);
            try {
                if (segment.fullSegmentEncryptionKeyUri != null) {
                    Uri keyUri = UriUtil.resolveToUri(playlist.baseUri, segment.fullSegmentEncryptionKeyUri);
                    byte[] key = keys.get(keyUri);
                    if (key == null) {
                        key = readAll(cache, keyUri);
                        keys.put(keyUri, key);
                    }
                    stream = decrypting(stream, key, ivBytes(segment.encryptionIV));
                }
                final InputStream in = stream;
                DataReader reader = in::read;
                ExtractorInput input = new DefaultExtractorInput(reader, 0, C.LENGTH_UNSET);
                Extractor extractor = sniff(input, new TsExtractor(), new AdtsExtractor(), new Mp3Extractor());
                if (extractor == null) throw new IOException("Unknown container of segment " + uri);
                extractor.init(new Collector(router, clock));
                if (readAll(extractor, input, seek, router) == Extractor.RESULT_SEEK) {
                    throw new IOException("Segment asks for a seek: " + uri);
                }
                extractor.release();
            } finally {
                stream.close();
            }
        }
    }

    /** The media playlist of {@code uri}: itself, else the first one of its renditions and variants in the cache. */
    @NonNull
    private static HlsMediaPlaylist mediaPlaylist(@NonNull Cache cache, @NonNull Uri uri) throws IOException {
        HlsPlaylist playlist = readPlaylist(cache, uri);
        if (playlist instanceof HlsMediaPlaylist) return (HlsMediaPlaylist) playlist;
        HlsMultivariantPlaylist multivariant = (HlsMultivariantPlaylist) playlist;
        List<Uri> candidates = new ArrayList<>();
        for (HlsMultivariantPlaylist.Rendition audio : multivariant.audios) {
            if (audio.url != null) candidates.add(audio.url);
        }
        for (HlsMultivariantPlaylist.Variant variant : multivariant.variants) candidates.add(variant.url);
        IOException last = new IOException("No media playlist in " + uri);
        for (Uri candidate : candidates) {
            try {
                HlsPlaylist media = readPlaylist(cache, candidate);
                if (media instanceof HlsMediaPlaylist) return (HlsMediaPlaylist) media;
            } catch (IOException e) {
                last = e;
            }
        }
        throw last;
    }

    /** Parsed against the URI the download saw (a redirect it followed included), as Media3 resolves segments. */
    @NonNull
    private static HlsPlaylist readPlaylist(@NonNull Cache cache, @NonNull Uri uri) throws IOException {
        CacheDataSource source = cacheOnly(cache);
        DataSourceInputStream stream = new DataSourceInputStream(source, new DataSpec(uri));
        try {
            stream.open();
            Uri base = source.getUri() != null ? source.getUri() : uri;
            return new HlsPlaylistParser().parse(base, stream);
        } finally {
            stream.close();
        }
    }

    @NonNull
    private static byte[] readAll(@NonNull Cache cache, @NonNull Uri uri) throws IOException {
        try (InputStream in = new DataSourceInputStream(cacheOnly(cache), new DataSpec(uri))) {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
            return out.toByteArray();
        }
    }

    @NonNull
    private static InputStream decrypting(@NonNull InputStream in, @NonNull byte[] key, @NonNull byte[] iv)
            throws IOException {
        try {
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS7Padding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(iv));
            return new CipherInputStream(in, cipher);
        } catch (java.security.GeneralSecurityException e) {
            throw new IOException("Cannot decrypt the segments", e);
        }
    }

    /** The 16-byte IV of an HLS segment, as Media3 makes it: hex ("0x" optional), right-aligned, zero-padded. */
    @NonNull
    static byte[] ivBytes(@Nullable String iv) throws IOException {
        if (iv == null || iv.isEmpty()) throw new IOException("Encrypted segment without IV");
        String hex = iv.toLowerCase(Locale.ROOT).startsWith("0x") ? iv.substring(2) : iv;
        byte[] value;
        try {
            value = new BigInteger(hex, 16).toByteArray();
        } catch (NumberFormatException e) {
            throw new IOException("Bad IV " + iv, e);
        }
        byte[] padded = new byte[16];
        int from = Math.max(0, value.length - 16);
        System.arraycopy(value, from, padded, 16 - (value.length - from), value.length - from);
        return padded;
    }

    @Nullable
    private static Extractor sniff(@NonNull ExtractorInput input, @NonNull Extractor... candidates) throws IOException {
        for (Extractor candidate : candidates) {
            try {
                if (candidate.sniff(input)) return candidate;
            } catch (EOFException tooShort) {
                // not this container
            } finally {
                input.resetPeekPosition();
            }
        }
        return null;
    }

    /** Reads until the end of the input, a seek it asks for (returned) or all clips are done. */
    private static int readAll(@NonNull Extractor extractor, @NonNull ExtractorInput input,
                               @NonNull PositionHolder seek, @NonNull Router router) throws IOException {
        int result = Extractor.RESULT_CONTINUE;
        while (result == Extractor.RESULT_CONTINUE && !router.finished()) {
            result = extractor.read(input, seek);
        }
        return result;
    }

    // ---------------------------------------------------------------- timeline

    /**
     * Time of a sample on the stream's timeline. The samples of a segment keep their spacing from its first one; the
     * next segment starts right after the last sample read (its spacing as duration), so the timeline is the
     * gap-free audio as played; after a segment that was not read it starts where the playlist says.
     */
    static final class StreamClock {
        private final boolean identity;
        private long offsetUs;
        private long nextOffsetUs = C.TIME_UNSET;
        private long segmentDurationUs;
        private long firstUs = C.TIME_UNSET;
        private long lastRelativeUs;
        private long spacingUs;

        StreamClock() {
            this(false);
        }

        private StreamClock(boolean identity) {
            this.identity = identity;
        }

        /** A single file: sample times are the timeline. */
        static StreamClock identity() {
            return new StreamClock(true);
        }

        void beginSegment(long playlistStartUs, long durationUs) {
            endSegment();
            offsetUs = nextOffsetUs != C.TIME_UNSET ? nextOffsetUs : playlistStartUs;
            nextOffsetUs = C.TIME_UNSET;
            segmentDurationUs = durationUs;
            firstUs = C.TIME_UNSET;
            lastRelativeUs = 0;
        }

        /** The segment before the next one was not read. */
        void skip() {
            firstUs = C.TIME_UNSET;
            nextOffsetUs = C.TIME_UNSET;
            segmentDurationUs = 0;
        }

        long timeOf(long sampleTimeUs) {
            if (identity) return sampleTimeUs;
            if (firstUs == C.TIME_UNSET) firstUs = sampleTimeUs;
            long relative = Math.max(0, sampleTimeUs - firstUs);
            if (relative > lastRelativeUs) spacingUs = relative - lastRelativeUs;
            lastRelativeUs = relative;
            return offsetUs + relative;
        }

        private void endSegment() {
            if (identity || firstUs == C.TIME_UNSET) return;
            long length = spacingUs > 0 ? lastRelativeUs + spacingUs : segmentDurationUs;
            nextOffsetUs = offsetUs + Math.max(length, 0);
        }
    }

    // ---------------------------------------------------------------- clips

    /** Hands each audio sample, by its timeline time, to the clips it falls in; opens and ends their sinks. */
    static final class Router {
        private final List<Clip> clips;
        private final Output output;
        /** Clip indexes by start. */
        private final Integer[] order;
        private int next;
        private final List<Integer> active = new ArrayList<>();
        private final Map<Integer, Sink> sinks = new HashMap<>();
        private final Map<Integer, File> files = new HashMap<>();
        private SinkFactory factory;

        Router(@NonNull List<Clip> clips, @NonNull Output output) {
            this.clips = clips;
            this.output = output;
            order = new Integer[clips.size()];
            for (int i = 0; i < order.length; i++) order[i] = i;
            Arrays.sort(order, (a, b) -> Long.compare(clips.get(a).startUs, clips.get(b).startUs));
        }

        /** The audio format, once known: decides the container; a codec that has none here fails the stream. */
        void format(@NonNull Format format) throws IOException {
            if (factory == null) sinks(SinkFactory.of(format));
        }

        void sinks(@NonNull SinkFactory factory) {
            this.factory = factory;
        }

        boolean finished() {
            return next >= order.length && active.isEmpty();
        }

        /** True when no clip is open and the next one starts at or after {@code untilUs}: nothing before it is needed. */
        boolean idleUntil(long untilUs) {
            return active.isEmpty() && (next >= order.length || clips.get(order[next]).startUs >= untilUs);
        }

        void sample(@NonNull byte[] data, int offset, int size, long timeUs) throws IOException {
            if (factory == null) throw new IOException("Audio samples before their format");
            for (int k = active.size() - 1; k >= 0; k--) {
                int index = active.get(k);
                if (timeUs >= clips.get(index).endUs) {
                    active.remove(k);
                    finish(index);
                }
            }
            while (next < order.length && clips.get(order[next]).startUs <= timeUs) {
                int index = order[next++];
                if (timeUs < clips.get(index).endUs) {
                    active.add(index);
                } else {
                    output.failed(index, new IOException("No audio in the chapter"));
                }
            }
            for (int index : active) {
                Sink sink = sinks.get(index);
                if (sink == null) {
                    try {
                        File file = output.open(index);
                        files.put(index, file);
                        sink = factory.create(file);
                    } catch (IOException | RuntimeException e) {
                        fail(index, e);
                        continue;
                    }
                    sinks.put(index, sink);
                }
                try {
                    sink.write(data, offset, size, timeUs - clips.get(index).startUs);
                } catch (IOException | RuntimeException e) {
                    fail(index, e);
                }
            }
            active.removeAll(failedNow);
            failedNow.clear();
        }

        private final List<Integer> failedNow = new ArrayList<>();

        /** The stream ended: open clips end with it, clips it never reached fail. */
        void endOfStream() {
            for (int index : new ArrayList<>(active)) finish(index);
            active.clear();
            while (next < order.length) output.failed(order[next++], new IOException("No audio in the chapter"));
        }

        /** After a failure of the stream: every clip still open fails, leaving no file. */
        void abort() {
            for (int index : new ArrayList<>(sinks.keySet())) {
                fail(index, new IOException("Extraction stopped"));
            }
            active.clear();
        }

        private void finish(int index) {
            Sink sink = sinks.remove(index);
            File file = files.remove(index);
            if (sink == null || file == null) {
                output.failed(index, new IOException("No audio in the chapter"));
                return;
            }
            try {
                sink.close();
                output.done(index, file, factory.extension);
            } catch (IOException | RuntimeException e) {
                //noinspection ResultOfMethodCallIgnored
                file.delete();
                output.failed(index, e);
            }
        }

        private void fail(int index, @NonNull Exception error) {
            Sink sink = sinks.remove(index);
            File file = files.remove(index);
            if (sink != null) sink.cancel();
            //noinspection ResultOfMethodCallIgnored
            if (file != null) file.delete();
            failedNow.add(index);
            output.failed(index, error);
        }
    }

    /** Receives the samples of one input; only the first audio track counts. */
    private static final class Collector implements ExtractorOutput, TrackOutput {
        private final Router router;
        private final StreamClock clock;
        private int audioId = C.INDEX_UNSET;
        private byte[] buffer = new byte[64 * 1024];
        private int size;

        Collector(@NonNull Router router, @NonNull StreamClock clock) {
            this.router = router;
            this.clock = clock;
        }

        @NonNull
        @Override
        public TrackOutput track(int id, int type) {
            if (type == C.TRACK_TYPE_AUDIO && (audioId == C.INDEX_UNSET || audioId == id)) {
                audioId = id;
                return this;
            }
            return new DiscardingTrackOutput();
        }

        @Override
        public void endTracks() {
        }

        @Override
        public void seekMap(@NonNull SeekMap seekMap) {
        }

        @Override
        public void format(@NonNull Format format) {
            if (format.sampleMimeType == null) return;
            try {
                router.format(format);
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }

        @Override
        public int sampleData(@NonNull DataReader input, int length, boolean allowEndOfInput, int sampleDataPart)
                throws IOException {
            ensure(length);
            int read = input.read(buffer, size, length);
            if (read == C.RESULT_END_OF_INPUT) {
                if (allowEndOfInput) return C.RESULT_END_OF_INPUT;
                throw new EOFException();
            }
            size += read;
            return read;
        }

        @Override
        public void sampleData(@NonNull ParsableByteArray data, int length, int sampleDataPart) {
            ensure(length);
            data.readBytes(buffer, size, length);
            size += length;
        }

        @Override
        public void sampleMetadata(long timeUs, int flags, int sampleSize, int offset,
                                   @Nullable CryptoData cryptoData) {
            int start = size - offset - sampleSize;
            try {
                if (start >= 0) router.sample(buffer, start, sampleSize, clock.timeOf(timeUs));
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
            System.arraycopy(buffer, size - offset, buffer, 0, offset);
            size = offset;
        }

        private void ensure(int more) {
            if (size + more > buffer.length) buffer = Arrays.copyOf(buffer, Math.max(buffer.length * 2, size + more));
        }
    }

    // ---------------------------------------------------------------- sinks

    interface Sink {
        void write(@NonNull byte[] data, int offset, int size, long timeUs) throws IOException;

        void close() throws IOException;

        /** Stops writing; the caller deletes the file. */
        void cancel();
    }

    interface SinkOpener {
        @NonNull
        Sink open(@NonNull File file) throws IOException;
    }

    /** The container a codec is written in, without re-encoding. */
    static final class SinkFactory {
        final String extension;
        private final SinkOpener opener;

        SinkFactory(@NonNull String extension, @NonNull SinkOpener opener) {
            this.extension = extension;
            this.opener = opener;
        }

        @NonNull
        Sink create(@NonNull File file) throws IOException {
            return opener.open(file);
        }

        @NonNull
        static SinkFactory of(@NonNull Format format) throws IOException {
            String mime = format.sampleMimeType;
            if (MimeTypes.AUDIO_MPEG.equals(mime)) return new SinkFactory("mp3", FrameSink::new);
            if (MimeTypes.AUDIO_MPEG_L2.equals(mime)) return new SinkFactory("mp2", FrameSink::new);
            if (MimeTypes.AUDIO_AAC.equals(mime) && !format.initializationData.isEmpty()) {
                return new SinkFactory("m4a", file -> new Mp4Sink(file, format));
            }
            throw new IOException("Audio codec " + mime + " cannot be saved without re-encoding");
        }
    }

    /** MPEG audio frames as they are: an .mp3 (.mp2) file. */
    private static final class FrameSink implements Sink {
        private final OutputStream out;

        FrameSink(@NonNull File file) throws IOException {
            out = new BufferedOutputStream(new FileOutputStream(file), 64 * 1024);
        }

        @Override
        public void write(@NonNull byte[] data, int offset, int size, long timeUs) throws IOException {
            out.write(data, offset, size);
        }

        @Override
        public void close() throws IOException {
            out.close();
        }

        @Override
        public void cancel() {
            try {
                out.close();
            } catch (IOException ignored) {
            }
        }
    }

    /** AAC access units in an MP4 container (.m4a) by the platform muxer. */
    private static final class Mp4Sink implements Sink {
        private final MediaMuxer muxer;
        private final int track;
        private final MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        private ByteBuffer chunk = ByteBuffer.allocate(8 * 1024);
        private long firstUs = C.TIME_UNSET;
        private boolean started;

        Mp4Sink(@NonNull File file, @NonNull Format format) throws IOException {
            MediaFormat media = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC,
                    format.sampleRate != Format.NO_VALUE ? format.sampleRate : 44100,
                    format.channelCount != Format.NO_VALUE ? format.channelCount : 2);
            media.setByteBuffer("csd-0", ByteBuffer.wrap(format.initializationData.get(0)));
            muxer = new MediaMuxer(file.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            try {
                track = muxer.addTrack(media);
                muxer.start();
                started = true;
            } catch (RuntimeException e) {
                muxer.release();
                throw new IOException("Cannot start the MP4 muxer", e);
            }
        }

        @Override
        public void write(@NonNull byte[] data, int offset, int size, long timeUs) {
            if (chunk.capacity() < size) chunk = ByteBuffer.allocate(size);
            chunk.clear();
            chunk.put(data, offset, size);
            chunk.flip();
            // the file starts with its first sample
            if (firstUs == C.TIME_UNSET) firstUs = timeUs;
            info.set(0, size, timeUs - firstUs, MediaCodec.BUFFER_FLAG_KEY_FRAME);
            muxer.writeSampleData(track, chunk, info);
        }

        @Override
        public void close() throws IOException {
            try {
                if (started) muxer.stop();
            } catch (RuntimeException e) {
                throw new IOException("Cannot finish the MP4 file", e);
            } finally {
                started = false;
                muxer.release();
            }
        }

        @Override
        public void cancel() {
            try {
                close();
            } catch (IOException ignored) {
            }
        }
    }
}
