package com.fanok.audiobooks.presenter;

import static com.fanok.audiobooks.activity.SleepTimerActivity.Broadcast_UPDATE_TIMER_START;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.text.format.DateFormat;
import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;
import com.arellomobile.mvp.InjectViewState;
import com.arellomobile.mvp.MvpPresenter;
import com.fanok.audiobooks.R;
import com.fanok.audiobooks.interface_pacatge.sleep_timer.SleepTimerView;
import com.fanok.audiobooks.service.MediaPlayerService;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;


@InjectViewState
public class SleepTimerPresenter extends MvpPresenter<SleepTimerView>
        implements com.fanok.audiobooks.interface_pacatge.sleep_timer.SleepTimerPresenter {


    /**
     * To the player service. Extras: "start" (boolean; false cancels), "time" (ms, countdown), "at" (epoch ms, clock
     * mode), "endOfChapter" (boolean), "warn", "shake" (booleans), "extend" (ms added to the running timer without
     * re-arming it; "start" is then ignored).
     */
    public static final String Broadcast_SleepTimer = "SleepTimerStart";

    public static final int MODE_COUNTDOWN = 0;
    public static final int MODE_CLOCK = 1;
    public static final int MODE_END_OF_CHAPTER = 2;

    private static final String PREF_MODE = "sleep_timer_mode";
    private static final String PREF_LAST_MINUTES = "sleep_timer_last_minutes";
    private static final String PREF_LAST_CLOCK = "sleep_timer_last_clock";
    private static final String PREF_WARN = "sleep_timer_warn";
    private static final String PREF_SHAKE = "sleep_timer_shake";
    private static final long EXTEND_MS = 10 * 60_000L;

    private static boolean timerStarted = false;

    private final Context mContext;
    private final SharedPreferences mPrefs;
    private int mode;
    private int hours;
    private int minutes;
    private int clockHour;
    private int clockMinute;
    private boolean warn;
    private boolean shake;

    public SleepTimerPresenter(@NonNull Context context) {
        mContext = context;
        mPrefs = PreferenceManager.getDefaultSharedPreferences(context);
    }

    public static boolean isTimerStarted() {
        return timerStarted;
    }

    /** The notification's timer button: a countdown of the last duration set here (30 min if none), its options too. */
    @NonNull
    public static Intent quickStart(@NonNull SharedPreferences prefs) {
        int minutes = Math.min(23 * 60 + 59, prefs.getInt(PREF_LAST_MINUTES, 30));
        if (minutes <= 0) {
            minutes = 30;
        }
        return new Intent(Broadcast_SleepTimer).putExtra("start", true).putExtra("time", minutes * 60_000L)
                .putExtra("warn", prefs.getBoolean(PREF_WARN, true)).putExtra("shake", prefs.getBoolean(PREF_SHAKE, true));
    }

    /** The player service sets this when it arms the timer and resets it when the timer fires, is cancelled or ends. */
    public static void setTimerStarted(boolean timerStarted) {
        SleepTimerPresenter.timerStarted = timerStarted;
    }

    /** Epoch ms of the next {@code hour:minute} after {@code now}: a time not later than now means tomorrow. */
    public static long nextClockTime(@NonNull Calendar now, int hour, int minute) {
        Calendar at = (Calendar) now.clone();
        at.set(Calendar.HOUR_OF_DAY, hour);
        at.set(Calendar.MINUTE, minute);
        at.set(Calendar.SECOND, 0);
        at.set(Calendar.MILLISECOND, 0);
        if (!at.after(now)) {
            at.add(Calendar.DAY_OF_MONTH, 1);
        }
        return at.getTimeInMillis();
    }

    @Override
    protected void onFirstViewAttach() {
        super.onFirstViewAttach();
        mode = mPrefs.getInt(PREF_MODE, MODE_COUNTDOWN) == MODE_CLOCK ? MODE_CLOCK : MODE_COUNTDOWN;
        int total = Math.max(0, Math.min(23 * 60 + 59, mPrefs.getInt(PREF_LAST_MINUTES, 30)));
        hours = total / 60;
        minutes = total % 60;
        clockHour = 23;
        clockMinute = 0;
        String clock = mPrefs.getString(PREF_LAST_CLOCK, "23:00");
        String[] parts = clock != null ? clock.split(":") : new String[0];
        if (parts.length == 2) {
            try {
                clockHour = Math.max(0, Math.min(23, Integer.parseInt(parts[0])));
                clockMinute = Math.max(0, Math.min(59, Integer.parseInt(parts[1])));
            } catch (NumberFormatException ignored) {
                //the default stays
            }
        }
        warn = mPrefs.getBoolean(PREF_WARN, true);
        shake = mPrefs.getBoolean(PREF_SHAKE, true);
        showSetup();
        if (timerStarted) {
            //the service replies with the state
            getViewState().showRunning(-1, 0, false, -1);
            getViewState().brodcastSend(new Intent(Broadcast_UPDATE_TIMER_START));
        }
    }

    @Override
    public void setMode(int mode) {
        this.mode = mode == MODE_CLOCK ? MODE_CLOCK : MODE_COUNTDOWN;
        mPrefs.edit().putInt(PREF_MODE, this.mode).apply();
        showSetup();
    }

    @Override
    public void setCountdown(int hours, int minutes) {
        this.hours = Math.max(0, Math.min(23, hours));
        this.minutes = Math.max(0, Math.min(59, minutes));
        mPrefs.edit().putInt(PREF_LAST_MINUTES, this.hours * 60 + this.minutes).apply();
        showSetup();
    }

    @Override
    public void setClock(int hour, int minute) {
        clockHour = Math.max(0, Math.min(23, hour));
        clockMinute = Math.max(0, Math.min(59, minute));
        mPrefs.edit().putString(PREF_LAST_CLOCK, String.format(Locale.ROOT, "%02d:%02d", clockHour, clockMinute))
                .apply();
        showSetup();
    }

    @Override
    public void setWarn(boolean warn) {
        this.warn = warn;
        mPrefs.edit().putBoolean(PREF_WARN, warn).apply();
        showSetup();
    }

    @Override
    public void setShake(boolean shake) {
        this.shake = shake;
        mPrefs.edit().putBoolean(PREF_SHAKE, shake).apply();
        showSetup();
    }

    @Override
    public void start() {
        long countdownMs = (hours * 60L + minutes) * 60_000L;
        if ((mode == MODE_COUNTDOWN && countdownMs == 0) || !playerRunning()) {
            return;
        }
        Intent intent = new Intent(Broadcast_SleepTimer).putExtra("start", true).putExtra("warn", warn)
                .putExtra("shake", shake);
        long now = System.currentTimeMillis();
        boolean bookPaused = !MediaPlayerService.isPlay();
        if (mode == MODE_CLOCK) {
            long at = nextClockTime(Calendar.getInstance(), clockHour, clockMinute);
            intent.putExtra("at", at);
            getViewState().showRunning(at - now, at, false, MODE_CLOCK);
            if (bookPaused) {
                getViewState().showToast(mContext.getString(R.string.sleep_timer_started_clock,
                        DateFormat.getTimeFormat(mContext).format(new Date(at))));
            }
        } else {
            intent.putExtra("time", countdownMs);
            getViewState().showRunning(countdownMs, now + countdownMs, bookPaused, MODE_COUNTDOWN);
            if (bookPaused) {
                getViewState().showToast(mContext.getString(R.string.sleep_timer_started));
            }
        }
        timerStarted = true;
        getViewState().brodcastSend(intent);
    }

    /** Arms the end-of-chapter boundary: playback pauses when the current chapter ends, at any speed. */
    @Override
    public void endOfChapter() {
        if (!playerRunning()) {
            return;
        }
        timerStarted = true;
        getViewState().showRunning(-1, 0, false, MODE_END_OF_CHAPTER);
        getViewState().brodcastSend(new Intent(Broadcast_SleepTimer).putExtra("start", true)
                .putExtra("endOfChapter", true));
    }

    @Override
    public void extend() {
        getViewState().brodcastSend(new Intent(Broadcast_SleepTimer).putExtra("extend", EXTEND_MS));
    }

    @Override
    public void cancel() {
        timerStarted = false;
        getViewState().brodcastSend(new Intent(Broadcast_SleepTimer).putExtra("start", false));
        showSetup();
    }

    @Override
    public void onTimerUpdate(long remainingMs, long endAt, boolean paused, int mode) {
        //a tick sent before a cancel arrives after it
        if (timerStarted) {
            getViewState().showRunning(remainingMs, endAt, paused, mode);
        }
    }

    @Override
    public void finish() {
        timerStarted = false;
        showSetup();
    }

    private void showSetup() {
        getViewState().showSetup(mode, hours, minutes, clockHour, clockMinute, warn, shake);
        getViewState().setStartEnabled(mode == MODE_CLOCK || hours + minutes > 0);
    }

    /** Only the player service runs the timer: without it nothing would count down or stop. */
    private boolean playerRunning() {
        if (MediaPlayerService.isRunning() && MediaPlayerService.currentBookUrl() != null) {
            return true;
        }
        getViewState().showToast(mContext.getString(R.string.sleep_timer_no_playback));
        return false;
    }
}
