package com.fanok.audiobooks; // Используйте ваш пакет для view

import android.content.Context;
import android.util.AttributeSet;
import android.view.KeyEvent;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatSeekBar;

public class TvSeekBar extends AppCompatSeekBar {

    private boolean isFocused = false;
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
        // Устанавливаем слушатель изменения фокуса
        setOnFocusChangeListener((v, hasFocus) -> {
            isFocused = hasFocus;
            if (!hasFocus && userProgress!= -1) {
                userProgress = -1; // Сбрасываемпользовательский прогресс
            }
        });
    }

    @Override
    public synchronized void setProgress(int progress) {
        // ИГНОРИРУЕМ программное обновление, если SeekBar сейчас в фокусе
        if (isFocused) {
            return;
        }
        super.setProgress(progress);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // Обрабатываем нажатия "влево" и "вправо" вручную
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
            int newProgress = getProgress() - calculateStep();    if (newProgress < 0) newProgress = 0;
            userProgress = newProgress; // Запоминаем пользовательский выбор
            super.setProgress(userProgress); // Обновляем UI вручную
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT){
            int newProgress = getProgress() + calculateStep();
            if (newProgress > getMax()) newProgress = getMax();
            userProgress = newProgress;
            super.setProgress(userProgress);
            return true;
        }

        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER||keyCode == KeyEvent.KEYCODE_ENTER){
            if (userListener != null && userProgress != -1) {
                // Отправляем финальное значение, которое выбрал пользователь
                userListener.onProgressChangedByUser(this, userProgress, true);
            }
        }
        return super.onKeyDown(keyCode, event);}

    private int calculateStep() {
        // Шаг перемотки, например, 1% от максимального значения
        int step = getMax() / 100;
        return Math.max(step, 1); // Шаг должен быть минимум 1
    }

    public void setOnSeekBarChangedByUserListener(OnSeekBarChangedByUserListener listener) {
        this.userListener = listener;    }
}