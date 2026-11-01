package com.fanok.audiobooks.util;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public class ChapterExtractorTest {

    @Test
    public void ivIsHexRightAlignedInSixteenBytes() throws IOException {
        byte[] explicit = ChapterExtractor.ivBytes("0xbb8727fe12457c860e7361e31b3614d0");
        assertEquals(16, explicit.length);
        assertEquals((byte) 0xbb, explicit[0]);
        assertEquals((byte) 0xd0, explicit[15]);
        // without an IV attribute the parser gives the media sequence number in hex
        byte[] sequence = ChapterExtractor.ivBytes("1f");
        byte[] expected = new byte[16];
        expected[15] = 0x1f;
        assertArrayEquals(expected, sequence);
    }

    @Test
    public void segmentsJoinGapFreeAfterTheLastSampleRead() {
        ChapterExtractor.StreamClock clock = new ChapterExtractor.StreamClock();
        // segment 0: playlist says 0..10 s, samples every 0.5 s from an arbitrary PTS base
        clock.beginSegment(0, 10_000_000);
        long last = 0;
        for (int k = 0; k < 21; k++) last = clock.timeOf(90_000_000L + k * 500_000L);
        assertEquals(10_000_000, last);
        // segment 1 starts right after: 10.5 s, not the playlist's (rounded) 10.0 s
        clock.beginSegment(10_000_000, 10_000_000);
        assertEquals(10_500_000, clock.timeOf(7_000_000));
        assertEquals(11_000_000, clock.timeOf(7_500_000));
    }

    @Test
    public void afterASkippedSegmentThePlaylistTimeCounts() {
        ChapterExtractor.StreamClock clock = new ChapterExtractor.StreamClock();
        clock.beginSegment(0, 30_000_000);
        clock.timeOf(0);
        clock.timeOf(23_000);
        clock.skip();
        clock.beginSegment(60_000_178, 30_000_000);
        assertEquals(60_000_178, clock.timeOf(123_456));
    }

    @Test
    public void samplesGoToTheChaptersTheyFallIn() throws IOException {
        List<ChapterExtractor.Clip> clips = Arrays.asList(
                new ChapterExtractor.Clip(1_000_000, 2_000_000),
                new ChapterExtractor.Clip(0, 1_000_000),
                new ChapterExtractor.Clip(2_000_000, Long.MAX_VALUE));
        Recorder out = new Recorder();
        ChapterExtractor.Router router = new ChapterExtractor.Router(clips, out);
        router.sinks(new ChapterExtractor.SinkFactory("mp3", file -> out.sink()));
        for (long t = 0; t < 3_000_000; t += 250_000) {
            router.sample(new byte[]{(byte) (t / 250_000)}, 0, 1, t);
        }
        router.endOfStream();
        assertEquals(Arrays.asList("done 1", "done 0", "done 2"), out.events);
        assertEquals(Arrays.asList(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11), out.written);
        // each chapter starts at its own first sample
        assertEquals(Arrays.asList(0L, 250_000L, 500_000L, 750_000L, 0L, 250_000L, 500_000L, 750_000L,
                0L, 250_000L, 500_000L, 750_000L), out.times);
    }

    @Test
    public void aChapterTheStreamNeverReachesFails() throws IOException {
        Recorder out = new Recorder();
        ChapterExtractor.Router router = new ChapterExtractor.Router(Arrays.asList(
                new ChapterExtractor.Clip(0, 1_000_000), new ChapterExtractor.Clip(5_000_000, 6_000_000)), out);
        router.sinks(new ChapterExtractor.SinkFactory("m4a", file -> out.sink()));
        router.sample(new byte[1], 0, 1, 0);
        router.endOfStream();
        assertEquals(Arrays.asList("done 0", "failed 1"), out.events);
        assertTrue(router.finished());
    }

    /** Records what the router does; sinks append to shared lists. */
    private static final class Recorder implements ChapterExtractor.Output {
        final List<String> events = new ArrayList<>();
        final List<Integer> written = new ArrayList<>();
        final List<Long> times = new ArrayList<>();

        ChapterExtractor.Sink sink() {
            return new ChapterExtractor.Sink() {
                @Override
                public void write(@NonNull byte[] data, int offset, int size, long timeUs) {
                    written.add((int) data[offset]);
                    times.add(timeUs);
                }

                @Override
                public void close() {
                }

                @Override
                public void cancel() {
                }
            };
        }

        @NonNull
        @Override
        public File open(int index) throws IOException {
            File file = File.createTempFile("clip", ".part");
            file.deleteOnExit();
            return file;
        }

        @Override
        public void done(int index, @NonNull File file, @NonNull String extension) {
            events.add("done " + index);
        }

        @Override
        public void failed(int index, @NonNull Exception error) {
            events.add("failed " + index);
        }
    }
}
