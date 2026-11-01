package com.fanok.audiobooks.presenter;

import static org.junit.Assert.assertEquals;

import java.util.Calendar;
import java.util.TimeZone;
import org.junit.Test;

public class SleepTimerPresenterTest {

    private static final long MINUTE = 60_000L;

    private static Calendar at(int hour, int minute, int second) {
        Calendar now = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        now.clear();
        now.set(2026, Calendar.OCTOBER, 28, hour, minute, second);
        return now;
    }

    @Test
    public void clockTimeLaterTodayIsToday() {
        Calendar now = at(22, 10, 30);
        assertEquals(now.getTimeInMillis() + 80 * MINUTE - 30_000,
                SleepTimerPresenter.nextClockTime(now, 23, 30));
    }

    @Test
    public void clockTimeEarlierThanNowIsTomorrow() {
        Calendar now = at(22, 10, 30);
        assertEquals(now.getTimeInMillis() + (23 * 60 + 50) * MINUTE - 30_000,
                SleepTimerPresenter.nextClockTime(now, 22, 0));
    }

    @Test
    public void clockTimeOfThisMinuteIsTomorrow() {
        Calendar now = at(22, 10, 30);
        assertEquals(now.getTimeInMillis() + 24 * 60 * MINUTE - 30_000,
                SleepTimerPresenter.nextClockTime(now, 22, 10));
        Calendar exact = at(22, 10, 0);
        assertEquals(exact.getTimeInMillis() + 24 * 60 * MINUTE, SleepTimerPresenter.nextClockTime(exact, 22, 10));
    }

    @Test
    public void clockTimeAfterMidnight() {
        Calendar now = at(23, 50, 0);
        assertEquals(now.getTimeInMillis() + 40 * MINUTE, SleepTimerPresenter.nextClockTime(now, 0, 30));
    }
}
