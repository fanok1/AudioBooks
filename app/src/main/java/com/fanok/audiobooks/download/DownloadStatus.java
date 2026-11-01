package com.fanok.audiobooks.download;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * What one book's download is doing, computed in one place from its transfers (the Media3 downloads of its chapters:
 * one per progressive chapter, one per HLS stream) and the live facts of the manager. Pure arithmetic; the Android
 * side ({@code DownloadMonitor}) supplies the parts and the live facts, {@code DownloadStatusText} the words.
 * Chapters are counted separately from transfers: several chapters may share one stream.
 */
public final class DownloadStatus {

    /** Nothing of the book is selected for download. */
    public static final int NONE = 0;
    /** Every transfer finished; {@link #chaptersDone} against {@link #chaptersTotal} says whether all chapters have. */
    public static final int COMPLETE = 1;
    public static final int DOWNLOADING = 2;
    /** A transfer runs but no byte arrived for {@link #STALL_MS}. */
    public static final int STALLED = 3;
    /** Waiting for its turn (another download runs first). */
    public static final int QUEUED = 4;
    /** Queued behind the network requirement: offline, or the network is not validated. */
    public static final int WAITING_NETWORK = 5;
    /** Stopped by the user (stop reason) or the manager is paused. */
    public static final int PAUSED = 6;
    public static final int FAILED = 7;

    /** Media3 {@code Download.STATE_*} values, repeated here so this class stays free of Android. */
    public static final int STATE_QUEUED = 0;
    public static final int STATE_STOPPED = 1;
    public static final int STATE_DOWNLOADING = 2;
    public static final int STATE_COMPLETED = 3;
    public static final int STATE_FAILED = 4;
    public static final int STATE_REMOVING = 5;
    public static final int STATE_RESTARTING = 7;

    public static final long STALL_MS = 5_000;
    /** Unknown length, estimate or ETA. */
    public static final long UNKNOWN = -1;

    /** One transfer of a book as the index or the manager reports it. Equal by value, so snapshots diff cleanly. */
    public static final class Part {
        public final String id;
        public final int state;
        public final long bytes;
        /** Content length, or {@link #UNKNOWN} until the downloader has learnt it. */
        public final long length;
        /** Percent the downloader reports (HLS counts segments), or {@link #UNKNOWN}. */
        public final float percent;
        public final int stopReason;
        /** Why it failed, for {@link #STATE_FAILED}; null when unknown. */
        public final String failure;

        public Part(String id, int state, long bytes, long length, float percent, int stopReason, String failure) {
            this.id = id;
            this.state = state;
            this.bytes = Math.max(0, bytes);
            this.length = length > 0 ? length : UNKNOWN;
            this.percent = percent >= 0 ? percent : UNKNOWN;
            this.stopReason = stopReason;
            this.failure = failure;
        }

        public Part withFailure(String failure) {
            return new Part(id, state, bytes, length, percent, stopReason, failure);
        }

        /** The transfer's expected length: known, else from its own percentage, else {@link #UNKNOWN}. */
        long expectedLength() {
            if (length > 0) return length;
            if (percent > 0 && bytes > 0) return Math.round(bytes * 100.0 / percent);
            return UNKNOWN;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Part)) return false;
            Part p = (Part) o;
            return state == p.state && bytes == p.bytes && length == p.length && percent == p.percent
                    && stopReason == p.stopReason && Objects.equals(id, p.id) && Objects.equals(failure, p.failure);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id, state, bytes, length, percent, stopReason, failure);
        }
    }

    /** The manager's facts at the moment of computing. */
    public static final class Live {
        public static final Live NONE = new Live(false, false, 0, 0);
        public final boolean networkMissing;
        public final boolean paused;
        public final long speedBps;
        public final long msSinceLastByte;

        public Live(boolean networkMissing, boolean paused, long speedBps, long msSinceLastByte) {
            this.networkMissing = networkMissing;
            this.paused = paused;
            this.speedBps = speedBps;
            this.msSinceLastByte = msSinceLastByte;
        }
    }

    public final int state;
    public final int chaptersDone;
    /** Chapters chosen for download (or already held locally). */
    public final int chaptersSelected;
    /** Chapters the book lists. */
    public final int chaptersTotal;
    public final long bytesDone;
    /** Expected bytes of all transfers, or {@link #UNKNOWN}. */
    public final long bytesTotal;
    /** True when {@link #bytesTotal} includes transfers whose length is still a guess. */
    public final boolean bytesEstimated;
    public final long speedBps;
    /** Seconds left at the current speed, or {@link #UNKNOWN}. */
    public final long etaSeconds;
    public final int failedParts;
    /** Reason of the first failed transfer that has one; null otherwise. */
    public final String failure;

    private DownloadStatus(int state, int chaptersDone, int chaptersSelected, int chaptersTotal, long bytesDone,
                           long bytesTotal, boolean bytesEstimated, long speedBps, long etaSeconds, int failedParts,
                           String failure) {
        this.state = state;
        this.chaptersDone = chaptersDone;
        this.chaptersSelected = chaptersSelected;
        this.chaptersTotal = chaptersTotal;
        this.bytesDone = bytesDone;
        this.bytesTotal = bytesTotal;
        this.bytesEstimated = bytesEstimated;
        this.speedBps = speedBps;
        this.etaSeconds = etaSeconds;
        this.failedParts = failedParts;
        this.failure = failure;
    }

    /** True while the manager has something to do for the book, i.e. the status should keep refreshing. */
    public boolean isActive() {
        return state == DOWNLOADING || state == STALLED || state == QUEUED || state == WAITING_NETWORK;
    }

    /** Every listed chapter is held. */
    public boolean isCompleteBook() {
        return state == COMPLETE && chaptersTotal > 0 && chaptersDone >= chaptersTotal;
    }

    /** 0..100 of the selected work: by bytes when the total is known, else by chapters; of the whole book once done. */
    public int percent() {
        double p;
        if (state == COMPLETE) {
            p = chaptersTotal > 0 ? 100.0 * chaptersDone / chaptersTotal : 100;
        } else if (bytesTotal > 0) {
            p = 100.0 * bytesDone / bytesTotal;
        } else if (chaptersSelected > 0) {
            p = 100.0 * chaptersDone / chaptersSelected;
        } else {
            return 0;
        }
        return (int) Math.max(0, Math.min(100, Math.round(p)));
    }

    /**
     * The status of a book. {@code parts} are its transfers in any state but removing; {@code chaptersDone} chapters
     * are held (a completed transfer or a local copy), {@code chaptersSelected} were chosen, of {@code chaptersTotal}.
     */
    public static DownloadStatus compute(List<Part> parts, int chaptersDone, int chaptersSelected, int chaptersTotal,
                                         Live live) {
        int downloading = 0, queued = 0, stopped = 0, failed = 0, completed = 0, counted = 0;
        long bytesDone = 0, knownTotal = 0;
        int unknown = 0;
        String failure = null;
        for (Part p : parts) {
            if (p.state == STATE_REMOVING) continue;
            counted++;
            bytesDone += p.bytes;
            long expected = p.expectedLength();
            if (expected > 0) knownTotal += Math.max(expected, p.bytes); else unknown++;
            switch (p.state) {
                case STATE_DOWNLOADING:
                case STATE_RESTARTING:
                    downloading++;
                    break;
                case STATE_QUEUED:
                    queued++;
                    break;
                case STATE_STOPPED:
                    stopped++;
                    break;
                case STATE_FAILED:
                    failed++;
                    if (failure == null) failure = p.failure;
                    break;
                case STATE_COMPLETED:
                    completed++;
                    break;
                default:
                    break;
            }
        }
        long bytesTotal;
        boolean estimated = false;
        if (counted == 0) {
            bytesTotal = UNKNOWN;
        } else if (unknown == 0) {
            bytesTotal = knownTotal;
        } else if (counted > unknown) {
            // transfers not started yet have no length: take the average of the ones that have
            bytesTotal = knownTotal + Math.round((double) knownTotal / (counted - unknown)) * unknown;
            estimated = true;
        } else {
            bytesTotal = UNKNOWN;
        }

        int state;
        if (counted == 0) {
            state = chaptersTotal > 0 && chaptersDone >= chaptersSelected && chaptersSelected > 0 ? COMPLETE : NONE;
        } else if (completed == counted) {
            state = COMPLETE;
        } else if (downloading > 0) {
            state = live.speedBps == 0 && live.msSinceLastByte >= STALL_MS ? STALLED : DOWNLOADING;
        } else if (queued > 0) {
            state = live.networkMissing ? WAITING_NETWORK : live.paused ? PAUSED : QUEUED;
        } else if (stopped > 0) {
            state = PAUSED;
        } else if (failed > 0) {
            state = FAILED;
        } else {
            state = NONE;
        }
        long speed = state == DOWNLOADING ? live.speedBps : 0;
        long eta = speed > 0 && bytesTotal > bytesDone ? (bytesTotal - bytesDone) / speed : UNKNOWN;
        return new DownloadStatus(state, chaptersDone, chaptersSelected, chaptersTotal, bytesDone, bytesTotal,
                estimated, speed, eta, failed, failure);
    }

    // ---------------------------------------------------------------- formatting

    /** "245 МБ", "1,1 ГБ", "950 КБ" with {@code units} = {Б, КБ, МБ, ГБ}. */
    public static String formatBytes(long bytes, String[] units) {
        double value = Math.max(0, bytes);
        int unit = 0;
        while (value >= 1000 && unit < units.length - 1) {
            value /= 1024;
            unit++;
        }
        String number = unit >= 2 && value < 10 ? String.format(Locale.getDefault(), "%.1f", value)
                : String.valueOf(Math.round(value));
        return number + " " + units[unit];
    }

    /** "40 с", "15 мин", "2 ч 05 мин" with {@code units} = {с, мин, ч}. */
    public static String formatDuration(long seconds, String[] units) {
        seconds = Math.max(0, seconds);
        if (seconds < 60) return seconds + " " + units[0];
        long minutes = (seconds + 30) / 60;
        if (minutes < 60) return minutes + " " + units[1];
        return (minutes / 60) + " " + units[2] + " " + String.format(Locale.ROOT, "%02d", minutes % 60)
                + " " + units[1];
    }

    // ---------------------------------------------------------------- speed

    /**
     * Bytes per second over a short sliding window from per-transfer byte deltas: a finished transfer's last delta
     * counts, aggregate totals are never subtracted. Reset when nothing runs so a new transfer starts clean.
     */
    public static final class SpeedMeter {
        public static final long WINDOW_MS = 6_000;
        /** The running transfers are sampled at most this often. */
        public static final long SAMPLE_MIN_MS = 200;
        private final ArrayDeque<long[]> samples = new ArrayDeque<>();
        /** Bytes of each transfer when it was last accounted. */
        private final Map<String, Long> bytesSeen = new HashMap<>();
        private long baselineAt = -1;
        private long lastByteAt = -1;
        /** So that the first sample is due at any clock, 0 included (no overflow of {@code now - sampledAt}). */
        private long sampledAt = -SAMPLE_MIN_MS;

        /** True, and remembered, when the last sample is at least {@link #SAMPLE_MIN_MS} old; the first is due. */
        public boolean sampleDue(long nowMs) {
            if (nowMs - sampledAt < SAMPLE_MIN_MS) return false;
            sampledAt = nowMs;
            return true;
        }

        /**
         * A running transfer has {@code bytes} now: the delta since it was last accounted counts. The clocks start
         * with the first running transfer after a reset, so a transfer resumed without bytes is seen to stall.
         */
        public void account(String id, long bytes, long nowMs) {
            baseline(nowMs);
            Long seen = bytesSeen.put(id, bytes);
            if (seen != null && bytes > seen) add(bytes - seen, nowMs);
        }

        /** The transfer ended (completed, failed, removed). */
        public void forget(String id) {
            bytesSeen.remove(id);
        }

        /** Measuring starts (a transfer runs) without bytes yet. */
        public void baseline(long nowMs) {
            if (baselineAt < 0) baselineAt = nowMs;
            if (lastByteAt < 0) lastByteAt = nowMs;
        }

        public void add(long bytes, long nowMs) {
            if (bytes <= 0) return;
            baseline(nowMs);
            samples.addLast(new long[]{nowMs, bytes});
            lastByteAt = nowMs;
        }

        public void reset() {
            samples.clear();
            bytesSeen.clear();
            baselineAt = -1;
            lastByteAt = -1;
        }

        public long bytesPerSecond(long nowMs) {
            long from = nowMs - WINDOW_MS;
            // a sample holds the bytes up to its time: one at the window's start belongs before it
            while (!samples.isEmpty() && samples.peekFirst()[0] <= from) samples.pollFirst();
            if (samples.isEmpty()) return 0;
            long sum = 0;
            for (long[] s : samples) sum += s[1];
            long spanMs = Math.max(1_000, nowMs - Math.max(from, baselineAt));
            return sum * 1000 / spanMs;
        }

        /** Milliseconds since the last byte arrived, 0 before measuring started. */
        public long msSinceLastByte(long nowMs) {
            return lastByteAt < 0 ? 0 : Math.max(0, nowMs - lastByteAt);
        }
    }
}
