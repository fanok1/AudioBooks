package com.fanok.audiobooks.download;

import static com.fanok.audiobooks.download.DownloadStatus.STATE_COMPLETED;
import static com.fanok.audiobooks.download.DownloadStatus.STATE_DOWNLOADING;
import static com.fanok.audiobooks.download.DownloadStatus.STATE_FAILED;
import static com.fanok.audiobooks.download.DownloadStatus.STATE_QUEUED;
import static com.fanok.audiobooks.download.DownloadStatus.STATE_STOPPED;
import static com.fanok.audiobooks.download.DownloadStatus.UNKNOWN;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

public class DownloadStatusTest {

    private static final long MB = 1024 * 1024;
    private static final String[] BYTES = {"Б", "КБ", "МБ", "ГБ"};
    private static final String[] TIME = {"с", "мин", "ч"};

    private static DownloadStatus.Part part(String id, int state, long bytes, long length) {
        return new DownloadStatus.Part(id, state, bytes, length, UNKNOWN, 0, null);
    }

    private static DownloadStatus.Live running(long speed) {
        return new DownloadStatus.Live(false, false, speed, 0);
    }

    @Test
    public void bytesSpeedAndEtaOfABookHalfWay() {
        // two chapters done, one at 50 %, one queued without a length yet: its size is the average of the known ones
        List<DownloadStatus.Part> parts = Arrays.asList(
                part("a", STATE_COMPLETED, 10 * MB, 10 * MB),
                part("b", STATE_COMPLETED, 10 * MB, 10 * MB),
                part("c", STATE_DOWNLOADING, 5 * MB, 10 * MB),
                part("d", STATE_QUEUED, 0, UNKNOWN));
        DownloadStatus s = DownloadStatus.compute(parts, 2, 4, 4, running(MB));

        assertEquals(DownloadStatus.DOWNLOADING, s.state);
        assertEquals(25 * MB, s.bytesDone);
        assertEquals(40 * MB, s.bytesTotal);
        assertTrue(s.bytesEstimated);
        assertEquals(15, s.etaSeconds);
        assertEquals(63, s.percent());
        assertTrue(s.isActive());
    }

    @Test
    public void noLengthAnywhereMeansNoTotalAndNoEta() {
        DownloadStatus s = DownloadStatus.compute(Collections.singletonList(part("a", STATE_DOWNLOADING, 3 * MB, UNKNOWN)),
                0, 1, 1, running(MB));

        assertEquals(UNKNOWN, s.bytesTotal);
        assertEquals(UNKNOWN, s.etaSeconds);
        assertEquals(0, s.percent()); // by chapters: none done yet
    }

    @Test
    public void hlsPercentGivesTheLengthWhenTheDownloaderHasNone() {
        DownloadStatus.Part hls = new DownloadStatus.Part("s", STATE_DOWNLOADING, 20 * MB, UNKNOWN, 25f, 0, null);
        DownloadStatus s = DownloadStatus.compute(Collections.singletonList(hls), 0, 8, 8, running(2 * MB));

        assertEquals(80 * MB, s.bytesTotal);
        assertFalse(s.bytesEstimated);
        assertEquals(30, s.etaSeconds);
    }

    @Test
    public void reasonsWhyNothingMoves() {
        List<DownloadStatus.Part> queued = Collections.singletonList(part("a", STATE_QUEUED, 0, UNKNOWN));
        assertEquals(DownloadStatus.QUEUED, DownloadStatus.compute(queued, 0, 1, 1, DownloadStatus.Live.NONE).state);
        assertEquals(DownloadStatus.WAITING_NETWORK,
                DownloadStatus.compute(queued, 0, 1, 1, new DownloadStatus.Live(true, false, 0, 0)).state);
        assertEquals(DownloadStatus.PAUSED,
                DownloadStatus.compute(queued, 0, 1, 1, new DownloadStatus.Live(false, true, 0, 0)).state);
        assertEquals(DownloadStatus.PAUSED, DownloadStatus.compute(
                Collections.singletonList(new DownloadStatus.Part("a", STATE_STOPPED, MB, 2 * MB, UNKNOWN, 1001, null)),
                0, 1, 1, DownloadStatus.Live.NONE).state);

        DownloadStatus stalled = DownloadStatus.compute(
                Collections.singletonList(part("a", STATE_DOWNLOADING, MB, 2 * MB)), 0, 1, 1,
                new DownloadStatus.Live(false, false, 0, DownloadStatus.STALL_MS));
        assertEquals(DownloadStatus.STALLED, stalled.state);
        assertEquals(0, stalled.speedBps);
        // a fresh transfer without bytes yet is downloading, not stalled
        assertEquals(DownloadStatus.DOWNLOADING, DownloadStatus.compute(
                Collections.singletonList(part("a", STATE_DOWNLOADING, 0, UNKNOWN)), 0, 1, 1,
                new DownloadStatus.Live(false, false, 0, 1_000)).state);
    }

    @Test
    public void failuresAreReportedButDoNotHideARunningDownload() {
        DownloadStatus.Part failed = new DownloadStatus.Part("a", STATE_FAILED, 0, UNKNOWN, UNKNOWN, 0, "нет места на диске");
        DownloadStatus alone = DownloadStatus.compute(Collections.singletonList(failed), 0, 1, 1, DownloadStatus.Live.NONE);
        assertEquals(DownloadStatus.FAILED, alone.state);
        assertEquals("нет места на диске", alone.failure);

        DownloadStatus mixed = DownloadStatus.compute(Arrays.asList(failed, part("b", STATE_DOWNLOADING, MB, 4 * MB)),
                0, 2, 2, running(MB));
        assertEquals(DownloadStatus.DOWNLOADING, mixed.state);
        assertEquals(1, mixed.failedParts);
    }

    @Test
    public void completionIsByStateNeverByARoundedPercent() {
        DownloadStatus.Part almost = new DownloadStatus.Part("a", STATE_DOWNLOADING, 999_999, 1_000_000, 99.9999f, 0, null);
        DownloadStatus s = DownloadStatus.compute(Collections.singletonList(almost), 0, 1, 1, running(MB));
        assertEquals(DownloadStatus.DOWNLOADING, s.state);
        assertEquals(100, s.percent()); // the bar may round, the state may not

        DownloadStatus done = DownloadStatus.compute(Collections.singletonList(part("a", STATE_COMPLETED, MB, MB)), 1, 1, 3,
                DownloadStatus.Live.NONE);
        assertEquals(DownloadStatus.COMPLETE, done.state);
        assertFalse(done.isCompleteBook()); // one of three chapters
        assertEquals(33, done.percent()); // the bar then shows how much of the book is here
        assertTrue(DownloadStatus.compute(Collections.singletonList(part("a", STATE_COMPLETED, MB, MB)), 3, 3, 3,
                DownloadStatus.Live.NONE).isCompleteBook());
        // nothing selected, nothing held
        assertEquals(DownloadStatus.NONE, DownloadStatus.compute(Collections.<DownloadStatus.Part>emptyList(), 0, 0, 3,
                DownloadStatus.Live.NONE).state);
        // only exported copies
        assertEquals(DownloadStatus.COMPLETE, DownloadStatus.compute(Collections.<DownloadStatus.Part>emptyList(), 2, 2, 3,
                DownloadStatus.Live.NONE).state);
    }

    @Test
    public void formatsBytesAndDurations() {
        assertEquals("950 КБ", DownloadStatus.formatBytes(950 * 1024, BYTES));
        assertEquals("245 МБ", DownloadStatus.formatBytes(245 * MB, BYTES));
        assertEquals("512 Б", DownloadStatus.formatBytes(512, BYTES));
        String gb = DownloadStatus.formatBytes(1100 * MB, BYTES);
        assertTrue(gb, gb.startsWith("1") && gb.endsWith(" ГБ") && gb.length() == 6); // 1,1 or 1.1 by locale
        assertEquals("40 с", DownloadStatus.formatDuration(40, TIME));
        assertEquals("15 мин", DownloadStatus.formatDuration(15 * 60 + 20, TIME));
        assertEquals("2 ч 05 мин", DownloadStatus.formatDuration(2 * 3600 + 5 * 60, TIME));
    }

    @Test
    public void speedMeterUsesPerTransferDeltasOverTheWindow() {
        DownloadStatus.SpeedMeter meter = new DownloadStatus.SpeedMeter();
        assertEquals(0, meter.bytesPerSecond(10_000));
        meter.baseline(10_000);
        meter.add(MB, 11_000);
        meter.add(MB, 12_000);
        // 2 MB in the 2 s since measuring started, not over the whole window
        assertEquals(MB, meter.bytesPerSecond(12_000));
        assertEquals(0, meter.msSinceLastByte(12_000));
        // nothing for 5 s: the samples age out of the window and the stall is measured; the one at the window's
        // start (11 s) holds bytes from before it
        assertEquals(5_000, meter.msSinceLastByte(17_000));
        assertEquals(MB * 1000 / DownloadStatus.SpeedMeter.WINDOW_MS, meter.bytesPerSecond(17_000));
        assertEquals(0, meter.bytesPerSecond(19_000));
        meter.reset();
        assertEquals(0, meter.msSinceLastByte(20_000));
    }

    @Test
    public void steadyRateReadsExactlyOverAFullWindow() {
        DownloadStatus.SpeedMeter meter = new DownloadStatus.SpeedMeter();
        meter.account("a", 0, 0);
        for (int s = 1; s <= 7; s++) meter.account("a", s * MB, s * 1_000L);
        assertEquals(MB, meter.bytesPerSecond(7_000)); // 1 MB/s, not 7 MB over 6 s
    }

    @Test
    public void firstSampleIsDueAtAnyClockAndAResumedTransferCanStall() {
        DownloadStatus.SpeedMeter meter = new DownloadStatus.SpeedMeter();
        long min = DownloadStatus.SpeedMeter.SAMPLE_MIN_MS;
        // elapsedRealtime may be small: the first sample must run (a Long.MIN_VALUE start overflowed and never did)
        assertTrue(meter.sampleDue(0));
        assertFalse(meter.sampleDue(min - 1));
        assertTrue(meter.sampleDue(min));

        meter.account("a", 0, 1_000); // first sight: the clocks start, no bytes counted
        meter.account("a", MB, 2_000);
        assertEquals(MB, meter.bytesPerSecond(2_000));
        meter.reset(); // paused: nothing runs
        meter.account("a", MB, 10_000); // resumed, nothing arrives
        assertEquals(DownloadStatus.STALL_MS, meter.msSinceLastByte(10_000 + DownloadStatus.STALL_MS));
        assertEquals(0, meter.bytesPerSecond(10_000 + DownloadStatus.STALL_MS));
    }
}
