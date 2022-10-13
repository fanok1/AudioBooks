package com.fanok.audiobooks.presenter;

import static com.fanok.audiobooks.activity.SleepTimerActivity.Broadcast_GET_TIME_LEFT;
import static com.fanok.audiobooks.activity.SleepTimerActivity.Broadcast_UPDATE_TIMER_START;

import android.content.Intent;
import android.view.View;
import android.widget.Button;
import com.arellomobile.mvp.InjectViewState;
import com.arellomobile.mvp.MvpPresenter;
import com.fanok.audiobooks.R;
import com.fanok.audiobooks.interface_pacatge.sleep_timer.SleepTimerView;
import com.fanok.audiobooks.service.MediaPlayerService;
import java.util.ArrayList;


@InjectViewState
public class SleepTimerPresenter extends MvpPresenter<SleepTimerView>
        implements com.fanok.audiobooks.interface_pacatge.sleep_timer.SleepTimerPresenter {


    public static final String Broadcast_SleepTimer = "SleepTimerStart";
    private static boolean timerStarted = false;
    private ArrayList<Character> time;

    public static boolean isTimerStarted() {
        return timerStarted;
    }

    public static void setTimerStarted(boolean timerStarted) {
        SleepTimerPresenter.timerStarted = timerStarted;
    }

    @Override
    public void clear(View view) {
        if (time.size() > 0 && !timerStarted) {
            time.remove(0);
            getViewState().updateTime(time);
        }
    }

    @Override
    public void numberClick(View view) {
        if (view instanceof Button) {
            Button button = (Button) view;
            if (time.size() == 0 && button.getText().toString().equals("0")) {
                return;
            }
            if (time.size() < 6 && !timerStarted) {
                time.add(0, button.getText().charAt(0));
                getViewState().updateTime(time);
            }
        }
    }

    @Override
    public void endChapterClick(final View view) {
        Intent intent = new Intent(Broadcast_GET_TIME_LEFT);
        getViewState().brodcastSend(intent);
    }

    @Override
    public void setEndCapterTime(final long t) {
        timerStarted = true;
        Intent intent = new Intent(Broadcast_SleepTimer);
        intent.putExtra("time", t);
        getViewState().setSrcToStartButton(timerStarted);
        getViewState().brodcastSend(intent);

    }

    @Override
    public void start() {
        if (!timerStarted && time.size() == 0) {
            return;
        }
        timerStarted = !timerStarted;
        getViewState().setSrcToStartButton(timerStarted);
        Intent intent = new Intent(Broadcast_SleepTimer);
        getViewState().broadcastStartTimer(intent);
        if (!MediaPlayerService.isPlay() && timerStarted) {
            getViewState().showToast(R.string.sleep_timer_started);
        }
    }

    @Override
    protected void onFirstViewAttach() {
        super.onFirstViewAttach();
        time = new ArrayList<>();
        getViewState().setSrcToStartButton(timerStarted);
        if (timerStarted) {
            getViewState().brodcastSend(new Intent(Broadcast_UPDATE_TIMER_START));
        }
    }

    @Override
    public void finish() {
        timerStarted = false;
        time.clear();
        getViewState().updateTime(time);
        getViewState().setSrcToStartButton(false);
    }
}
