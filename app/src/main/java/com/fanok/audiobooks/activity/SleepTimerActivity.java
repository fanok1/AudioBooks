package com.fanok.audiobooks.activity;

import android.app.UiModeManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;
import com.arellomobile.mvp.MvpAppCompatActivity;
import com.arellomobile.mvp.presenter.InjectPresenter;
import com.fanok.audiobooks.Hourglass;
import com.fanok.audiobooks.R;
import com.fanok.audiobooks.interface_pacatge.sleep_timer.SleepTimerView;
import com.fanok.audiobooks.presenter.SleepTimerPresenter;
import com.r0adkll.slidr.Slidr;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Objects;

public class SleepTimerActivity extends MvpAppCompatActivity implements SleepTimerView {

    public static final String bradcast_UpdateTimer = "updateTimer";

    public static final String bradcast_FinishTimer = "finishTimer";

    public static final String Broadcast_UPDATE_TIMER_START = "updateTimerStart";

    private TextView mHours;
    private TextView mMinutes;
    private TextView mSeconds;
    private ImageButton mStart;
    private ImageButton mClear;

    @InjectPresenter
    SleepTimerPresenter mPresenter;

    private BroadcastReceiver updateTimerBroadcast;

    private BroadcastReceiver finihTimer;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_sleep_timer);

        mHours = findViewById(R.id.hours);
        mMinutes = findViewById(R.id.minutes);
        mSeconds = findViewById(R.id.seconds);
        mStart = findViewById(R.id.start);
        mClear = findViewById(R.id.clear);

        Slidr.attach(this);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);
        initButtons();
        registerdBradcast();
    }

    private void initButtons() {
        LinearLayout numbers = findViewById(R.id.numbers);
        initButtonsClick(numbers);
        mClear.setOnClickListener(view -> mPresenter.clear(view));
        mStart.setOnClickListener(view -> mPresenter.start());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(updateTimerBroadcast);
        unregisterReceiver(finihTimer);
    }

    @Override
    public void broadcastStartTimer(Intent intent) {
        int hours = Integer.parseInt(mHours.getText().toString());
        int min = Integer.parseInt(mMinutes.getText().toString());
        int sec = Integer.parseInt(mSeconds.getText().toString());
        long time = (hours * 60L * 60L + min * 60L + sec) * 1000;
        intent.putExtra("time", time);
        brodcastSend(intent);
    }

    @Override
    public void brodcastSend(final Intent intent) {
        intent.setPackage(getPackageName());
        sendBroadcast(intent);
    }

    @Override
    public void setSrcToStartButton(boolean started) {
        if (started) {
            mStart.setImageResource(R.drawable.ic_stop);
        } else {
            mStart.setImageResource(R.drawable.ic_play);
        }
        mStart.setContentDescription(getString(started ? R.string.stop : R.string.play));
    }

    @Override
    public void showToast(final int id) {
        Toast.makeText(this, id, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void updateTime(ArrayList<Character> time) {
        int size = time.size();
        String hour = "00";
        if (size > 5) {
            hour = time.get(5).toString() + time.get(4).toString();
        } else if (size > 4) {
            hour = "0" + time.get(4).toString();
        }
        mHours.setText(hour);

        String minets = "00";
        if (size > 3) {
            minets = time.get(3).toString() + time.get(2).toString();
        } else if (size > 2) {
            minets = "0" + time.get(2).toString();
        }
        mMinutes.setText(minets);

        String sec = "00";
        if (size > 1) {
            sec = time.get(1).toString() + time.get(0).toString();
        } else if (size > 0) {
            sec = "0" + time.get(0).toString();
        }
        mSeconds.setText(sec);

    }

    private void initButtonsClick(ViewGroup viewGroup) {
        for (int i = 0; i < viewGroup.getChildCount(); i++) {
            View view = viewGroup.getChildAt(i);
            if (view instanceof Button) {
                Button button = (Button) view;
                if (button.getText().toString().equals(getString(R.string.end_chapter))) {
                    button.setOnClickListener(view1 -> mPresenter.endChapterClick(view1));
                } else {
                    button.setOnClickListener(view1 -> mPresenter.numberClick(view1));
                }
            } else if (view instanceof ViewGroup) {
                initButtonsClick((ViewGroup) view);
            }
        }
    }

    /** Only the player service, in this app, sends these: never exported. */
    private void registerdBradcast() {
        updateTimerBroadcast = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                //hours do not wrap at a day: up to 99 h can be entered
                long[] hms = Hourglass.split(intent.getLongExtra("time", 0));
                mHours.setText(String.format(Locale.ROOT, "%02d", hms[0]));
                mMinutes.setText(String.format(Locale.ROOT, "%02d", hms[1]));
                mSeconds.setText(String.format(Locale.ROOT, "%02d", hms[2]));
            }
        };
        ContextCompat.registerReceiver(this, updateTimerBroadcast, new IntentFilter(bradcast_UpdateTimer),
                ContextCompat.RECEIVER_NOT_EXPORTED);

        finihTimer = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mPresenter.finish();
            }
        };
        ContextCompat.registerReceiver(this, finihTimer, new IntentFilter(bradcast_FinishTimer),
                ContextCompat.RECEIVER_NOT_EXPORTED);
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public Resources.Theme getTheme() {
        Resources.Theme theme = super.getTheme();

        SharedPreferences pref = PreferenceManager
                .getDefaultSharedPreferences(this);

        String themeName = pref.getString("pref_theme", getString(R.string.theme_dark_value));
        if (themeName.equals(getString(R.string.theme_dark_value))) {
            theme.applyStyle(R.style.AppTheme_SwipeOnClose, true);
        } else if (themeName.equals(getString(R.string.theme_light_value))) {
            theme.applyStyle(R.style.LightAppTheme_SwipeOnClose, true);
        } else if (themeName.equals(getString(R.string.theme_black_value))) {
            theme.applyStyle(R.style.AppThemeBlack_SwipeOnClose, true);
        }


        return theme;
    }
}
