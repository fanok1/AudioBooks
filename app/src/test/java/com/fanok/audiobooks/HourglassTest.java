package com.fanok.audiobooks;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class HourglassTest {

    /** A clock and a tick scheduler that only move when the test says so. */
    private static final class FakeTime implements Hourglass.Ticker {
        long now = 1_000_000;
        final List<Runnable> tasks = new ArrayList<>();
        final List<Long> due = new ArrayList<>();

        @Override
        public void schedule(@NonNull Runnable tick, long delayMs) {
            tasks.add(tick);
            due.add(now + delayMs);
        }

        @Override
        public void cancel(@NonNull Runnable tick) {
            for (int i = tasks.size() - 1; i >= 0; i--) {
                if (tasks.get(i) == tick) {
                    tasks.remove(i);
                    due.remove(i);
                }
            }
        }

        void advance(long ms) {
            long end = now + ms;
            while (true) {
                int next = -1;
                for (int i = 0; i < due.size(); i++) {
                    if (due.get(i) <= end && (next < 0 || due.get(i) < due.get(next))) {
                        next = i;
                    }
                }
                if (next < 0) {
                    break;
                }
                now = Math.max(now, due.get(next));
                Runnable task = tasks.remove(next);
                due.remove(next);
                task.run();
            }
            now = end;
        }
    }

    private static final class Recorder extends Hourglass {
        int finished;
        int ticks;
        long boundary;

        Recorder(long time, boolean endOfChapter, FakeTime time2) {
            super(time, endOfChapter, 100, () -> time2.now, time2);
        }

        @Override
        public void onTimerFinish() {
            finished++;
        }

        @Override
        public void onTimerTick(long timeRemaining) {
            ticks++;
        }

        @Override
        protected long boundaryRemainingMs() {
            return boundary;
        }
    }

    @Test
    public void repeatedResumesKeepRealTime() {
        FakeTime time = new FakeTime();
        Recorder timer = new Recorder(1_109, false, time);
        timer.startTimer();
        for (int i = 0; i < 9; i++) {
            timer.resumeTimer();
        }
        assertEquals("one tick chain", 1, time.tasks.size());

        time.advance(1_100);
        assertEquals(0, timer.finished);
        assertEquals(9, timer.getTimeRemaining());
        time.advance(9);
        assertEquals(1, timer.finished);
        assertFalse(timer.isRunning());
        assertTrue(time.tasks.isEmpty());
    }

    @Test
    public void pauseStopsCounting() {
        FakeTime time = new FakeTime();
        Recorder timer = new Recorder(10_000, false, time);
        timer.startTimer();
        time.advance(3_000);
        timer.pauseTimer();
        assertTrue(timer.isPaused());
        time.advance(100_000);
        assertEquals(7_000, timer.getTimeRemaining());
        assertEquals(0, timer.finished);

        timer.resumeTimer();
        time.advance(6_999);
        assertEquals(0, timer.finished);
        time.advance(1);
        assertEquals(1, timer.finished);
    }

    @Test
    public void cancelNeverFinishes() {
        FakeTime time = new FakeTime();
        Recorder timer = new Recorder(500, false, time);
        timer.startTimer();
        timer.cancel();
        time.advance(10_000);
        assertEquals(0, timer.finished);
        assertTrue(time.tasks.isEmpty());
    }

    @Test
    public void endOfChapterModeArmsABoundaryInsteadOfACountdown() {
        FakeTime time = new FakeTime();
        Recorder timer = new Recorder(0, true, time);
        timer.boundary = 42_000;
        timer.startTimer();
        time.advance(3_600_000);
        assertEquals("time never fires the boundary", 0, timer.finished);
        assertTrue(timer.isRunning());
        assertTrue(timer.ticks > 0);
        assertEquals(42_000, timer.getTimeRemaining());

        assertTrue(timer.chapterEnded());
        assertEquals(1, timer.finished);
        assertFalse(timer.isRunning());
        assertFalse("consumed once", timer.chapterEnded());
        assertEquals(1, timer.finished);
    }

    @Test
    public void countdownIgnoresChapterEnds() {
        FakeTime time = new FakeTime();
        Recorder timer = new Recorder(5_000, false, time);
        timer.startTimer();
        assertFalse(timer.chapterEnded());
        assertTrue(timer.isRunning());
    }

    @Test
    public void extendAddsTimeWhileCounting() {
        FakeTime time = new FakeTime();
        Recorder timer = new Recorder(1_000, false, time);
        timer.startTimer();
        time.advance(600);
        timer.extend(2_000);
        assertEquals(2_400, timer.getTimeRemaining());
        assertEquals("still one tick chain", 1, time.tasks.size());

        time.advance(2_399);
        assertEquals(0, timer.finished);
        time.advance(1);
        assertEquals(1, timer.finished);
        assertTrue(time.tasks.isEmpty());
    }

    @Test
    public void extendAddsTimeWhilePaused() {
        FakeTime time = new FakeTime();
        Recorder timer = new Recorder(1_000, false, time);
        timer.startTimer();
        time.advance(400);
        timer.pauseTimer();
        timer.extend(5_000);
        assertTrue(timer.isPaused());
        assertEquals(5_600, timer.getTimeRemaining());
        time.advance(60_000);
        assertEquals(0, timer.finished);

        timer.resumeTimer();
        time.advance(5_599);
        assertEquals(0, timer.finished);
        time.advance(1);
        assertEquals(1, timer.finished);
    }

    @Test
    public void extendNeedsARunningCountdown() {
        FakeTime time = new FakeTime();
        Recorder done = new Recorder(100, false, time);
        done.startTimer();
        time.advance(100);
        assertEquals(1, done.finished);
        done.extend(1_000);
        assertFalse("a finished timer is not re-armed", done.isRunning());
        time.advance(10_000);
        assertEquals(1, done.finished);

        Recorder boundary = new Recorder(0, true, time);
        boundary.boundary = 42_000;
        boundary.startTimer();
        boundary.extend(1_000);
        assertEquals(42_000, boundary.getTimeRemaining());
        assertTrue(boundary.isRunning());
    }

    @Test
    public void split_hoursDoNotWrapAtADay() {
        assertArrayEquals(new long[]{25, 1, 5}, Hourglass.split((25 * 3600 + 65) * 1000L + 999));
        assertArrayEquals(new long[]{0, 0, 0}, Hourglass.split(-5));
    }
}
