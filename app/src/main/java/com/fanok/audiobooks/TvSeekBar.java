package com.fanok.audiobooks;

import android.content.Context;
import android.util.AttributeSet;
import android.view.KeyEvent;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatSeekBar;

/**
 * A seek bar for D-pad use: Left/Right move the position and, once the key is released, seek there; programmatic
 * progress updates are held off while the user is adjusting so the thumb does not jump back (UI-16).
 */
public class TvSeekBar extends AppCompatSeekBar {

    private boolean adjusting = false;
    private int userProgress = -1;
    private OnSeekBarChangedByUserListener userListener;

    public interface OnSeekBarChangedByUserListener {
        void onProgressChangedByUser(TvSeekBar seekBar, int progress, boolean fromUser);
    }


    public TvSeekBar(@NonNull Context context) {
        super(context);
        init();
    }

    public TvSeekBar(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public TvSeekBar(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context,attrs, defStyleAttr);
        init();    }

    private void init() {
        setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                commit();
            }
        });
    }

    @Override
    public synchronized void setProgress(int progress) {
        // the user's own position wins while a seek is being composed
        if (adjusting) {
            return;
        }
        super.setProgress(progress);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            int base = userProgress == -1 ? getProgress() : userProgress;
            int step = calculateStep() * (keyCode == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 1);
            userProgress = Math.max(0, Math.min(getMax(), base + step));
            adjusting = true;
            super.setProgress(userProgress);
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            commit();
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    /** Seeks to the composed position (if any) and lets playback updates through again. */
    private void commit() {
        int progress = userProgress;
        userProgress = -1;
        adjusting = false;
        if (progress != -1 && userListener != null) {
            userListener.onProgressChangedByUser(this, progress, true);
        }
    }

    private int calculateStep() {
        // Шаг перемотки, например, 1% от максимального значения
        int step = getMax() / 100;
        return Math.max(step, 1); // Шаг должен быть минимум 1
    }

    public void setOnSeekBarChangedByUserListener(OnSeekBarChangedByUserListener listener) {
        this.userListener = listener;    }
}
