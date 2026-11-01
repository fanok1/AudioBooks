package com.fanok.audiobooks;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import androidx.annotation.NonNull;
import com.fanok.audiobooks.interface_pacatge.HourglassListener;
import java.util.function.LongSupplier;

/**
 * Sleep timer. A countdown runs on a deadline of the injected clock ({@code SystemClock.elapsedRealtime()} by
 * default) and counts only while resumed, so pauses and repeated resumes never change how long it lasts. In
 * end-of-chapter mode there is no countdown: the timer is a boundary that {@link #chapterEnded()} consumes.
 */
public abstract class Hourglass implements HourglassListener {

    private static final long INTERVAL = 1000;

    /** Schedules the tick; the default posts to the main looper. */
    public interface Ticker {
        void schedule(@NonNull Runnable tick, long delayMs);

        void cancel(@NonNull Runnable tick);
    }

    private final LongSupplier clock;

    private final Ticker ticker;

    private final long interval;

    private final boolean endOfChapter;

    private final Runnable tick = this::onTick;

    /** Remaining time while not counting. */
    private long remainingMs;

    /** Clock value at which the countdown ends; meaningful while counting. */
    private long deadline;

    private boolean running;

    private boolean counting;

    /** @param endOfChapter true for the end-of-chapter boundary, false for a countdown of {@code timeInMillis} */
    public Hourglass(long timeInMillis, boolean endOfChapter) {
        this(timeInMillis, endOfChapter, INTERVAL, SystemClock::elapsedRealtime, new MainLooperTicker());
    }

    public Hourglass(long timeInMillis, boolean endOfChapter, long intervalInMillis,
            @NonNull LongSupplier clock, @NonNull Ticker ticker) {
        this.remainingMs = Math.max(0, timeInMillis);
        this.endOfChapter = endOfChapter;
        this.interval = intervalInMillis > 0 ? intervalInMillis : INTERVAL;
        this.clock = clock;
        this.ticker = ticker;
    }

    /** Starts the timer counting; a started timer ignores further calls. */
    public synchronized void startTimer() {
        if (running) {
            return;
        }
        running = true;
        resumeTimer();
    }

    /** Stops counting and keeps the remaining time. */
    public synchronized void pauseTimer() {
        if (!counting) {
            return;
        }
        remainingMs = Math.max(0, deadline - clock.getAsLong());
        counting = false;
        ticker.cancel(tick);
    }

    /** Counts again from where it was paused; idempotent while counting, so there is only ever one tick chain. */
    public synchronized void resumeTimer() {
        if (!running || counting) {
            return;
        }
        counting = true;
        deadline = clock.getAsLong() + remainingMs;
        ticker.schedule(tick, 0);
    }

    /**
     * Adds {@code ms} to a running countdown, counting or paused, without re-arming it. An end-of-chapter boundary
     * has no time to add; a finished or cancelled timer stays so.
     */
    public synchronized void extend(long ms) {
        if (!running || endOfChapter || ms <= 0) {
            return;
        }
        if (counting) {
            deadline += ms;
        } else {
            remainingMs += ms;
        }
    }

    /** Stops the timer without finishing it. */
    public synchronized final void cancel() {
        running = false;
        counting = false;
        ticker.cancel(tick);
    }

    /**
     * To be called when the playing chapter ends.
     *
     * @return true when an armed end-of-chapter timer finished at this boundary
     */
    public boolean chapterEnded() {
        synchronized (this) {
            if (!running || !endOfChapter) {
                return false;
            }
            running = false;
            counting = false;
            ticker.cancel(tick);
        }
        onTimerFinish();
        return true;
    }

    public boolean isEndOfChapter() {
        return endOfChapter;
    }

    public synchronized boolean isRunning() {
        return running;
    }

    public synchronized boolean isPaused() {
        return running && !counting;
    }

    /** Remaining countdown; in end-of-chapter mode {@link #boundaryRemainingMs()}. */
    public long getTimeRemaining() {
        if (endOfChapter) {
            return boundaryRemainingMs();
        }
        synchronized (this) {
            return counting ? Math.max(0, deadline - clock.getAsLong()) : remainingMs;
        }
    }

    /** End-of-chapter mode: the time left to the boundary, for display only. */
    protected long boundaryRemainingMs() {
        return 0;
    }

    private void onTick() {
        boolean finished = false;
        long remaining = 0;
        synchronized (this) {
            if (!counting) {
                return;
            }
            if (endOfChapter) {
                ticker.schedule(tick, interval);
            } else {
                remaining = deadline - clock.getAsLong();
                if (remaining <= 0) {
                    running = false;
                    counting = false;
                    finished = true;
                } else {
                    ticker.schedule(tick, Math.min(interval, remaining));
                }
            }
        }
        if (finished) {
            onTimerFinish();
        } else {
            onTimerTick(endOfChapter ? boundaryRemainingMs() : remaining);
        }
    }

    /** {hours, minutes, seconds} of a duration; hours do not wrap at a day. */
    @NonNull
    public static long[] split(long millis) {
        long seconds = Math.max(0, millis) / 1000;
        return new long[]{seconds / 3600, (seconds / 60) % 60, seconds % 60};
    }

    private static final class MainLooperTicker implements Ticker {
        private final Handler handler = new Handler(Looper.getMainLooper());

        @Override
        public void schedule(@NonNull Runnable tick, long delayMs) {
            handler.postDelayed(tick, delayMs);
        }

        @Override
        public void cancel(@NonNull Runnable tick) {
            handler.removeCallbacks(tick);
        }
    }
}
