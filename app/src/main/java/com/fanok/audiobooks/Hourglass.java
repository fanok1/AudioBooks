package com.fanok.audiobooks;

import android.annotation.SuppressLint;
import android.os.Handler;
import android.os.Message;
import com.fanok.audiobooks.interface_pacatge.HourglassListener;

public abstract class Hourglass implements HourglassListener {

    private static final int INTERVAL = 1000;

    private static final int MSG = 1;

    private Handler handler;

    private long interval;

    private boolean isCanseled;

    /**
     * To maintain Timer resume and pause status.
     */
    private boolean isPaused;

    /**
     * To maintain Timer start and stop status.
     */
    private boolean isRunning;

    private long localTime;

    /**
     * Timer time.
     */
    private long time;

    public Hourglass() {
        init(0, INTERVAL);
    }

    public Hourglass(long timeInMillis) {
        init(timeInMillis, INTERVAL);
    }

    public Hourglass(long timeInMillis, long intervalInMillis) {
        init(timeInMillis, intervalInMillis);
    }

    public synchronized final void cancel() {
        isRunning = false;
        isCanseled = true;
        handler.removeMessages(MSG);
    }

    public long getTimeRemaining() {
        if (localTime <= time) {
            return time - localTime;
        } else {
            return 0;
        }
    }

    /**
     * Method to check whether the timer is paused.
     *
     * @return: true if timer is paused else false.
     */
    public synchronized boolean isPaused() {
        return isPaused;
    }

    /**
     * To pause the timer from Main thread.
     *
     * @param isPaused: true to pause the timer, false to resume.
     */
    private synchronized void setPaused(boolean isPaused) {
        this.isPaused = isPaused;
    }

    /**
     * Convenience method to check whether the timer is running or not
     *
     * @return: true if timer is running, else false.
     */
    public boolean isRunning() {
        return isRunning;
    }

    /**
     * Convenience method to pause the timer.
     */
    public synchronized void pauseTimer() {
        setPaused(true);
    }

    /**
     * Convenience method to resume the timer.
     */
    public synchronized void resumeTimer() {
        if (!isCanseled) {
            setPaused(false);
            handler.sendMessage(handler.obtainMessage(MSG));
        }
    }

    /**
     * Setter for interval.
     *
     * @param intervalInMillis: in milliseconds
     */
    public void setInterval(long intervalInMillis) {
        if (isRunning) {
            return;
        }

        if (this.interval <= 0) {
            if (intervalInMillis < 0) {
                intervalInMillis *= -1;
            }
        }
        this.interval = intervalInMillis;
    }

    /**
     * Setter for Time.
     *
     * @param timeInMillis: in milliseconds
     */
    public void setTime(long timeInMillis) {
        if (isRunning) {
            return;
        }

        if (this.time <= 0) {
            if (timeInMillis < 0) {
                timeInMillis *= -1;
            }
        }
        this.time = timeInMillis;
    }

    /**
     * Method to start the timer.
     */
    public void startTimer() {
        if (isRunning) {
            return;
        }

        isCanseled = false;
        isRunning = true;
        isPaused = false;
        localTime = 0;
        handler.sendMessage(handler.obtainMessage(MSG));
    }

    /**
     * Method to stop the timer.
     */
    public void stopTimer() {
        isCanseled = true;
        isRunning = false;
        handler.removeMessages(MSG);
        onTimerFinish();

    }

    /**
     * Method to initialize HourGlass.
     *
     * @param time:     Time in milliseconds.
     * @param interval: in milliseconds.
     */
    private void init(long time, long interval) {
        setTime(time);
        setInterval(interval);

        initHourglass();
    }

    @SuppressLint("HandlerLeak")
    private void initHourglass() {

        handler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                super.handleMessage(msg);

                if (msg.what == MSG) {
                    if (!isPaused) {

                        if (localTime <= time) {
                            onTimerTick(time - localTime);
                            localTime += interval;
                            sendMessageDelayed(handler.obtainMessage(MSG), interval);
                        } else {
                            stopTimer();
                        }

                    }
                }
            }
        };
    }

}

