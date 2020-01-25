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

import androidx.appcompat.widget.Toolbar;
import androidx.preference.PreferenceManager;

import com.arellomobile.mvp.MvpAppCompatActivity;
import com.arellomobile.mvp.presenter.InjectPresenter;
import com.fanok.audiobooks.LocaleManager;
import com.fanok.audiobooks.R;
import com.fanok.audiobooks.interface_pacatge.sleep_timer.SleepTimerView;
import com.fanok.audiobooks.presenter.SleepTimerPresenter;
import com.r0adkll.slidr.Slidr;

import java.util.ArrayList;
import java.util.Objects;

import butterknife.BindView;
import butterknife.ButterKnife;

public class SleepTimerActivity extends MvpAppCompatActivity implements SleepTimerView {

    public static final String bradcast_UpdateTimer = "updateTimer";
    public static final String bradcast_FinishTimer = "finishTimer";
    @BindView(R.id.hours)
    TextView mHours;
    @BindView(R.id.minutes)
    TextView mMinutes;
    @BindView(R.id.seconds)
    TextView mSeconds;
    @BindView(R.id.start)
    ImageButton mStart;
    @BindView(R.id.clear)
    ImageButton mClear;
    @InjectPresenter
    SleepTimerPresenter mPresenter;
    private BroadcastReceiver updateTimerBroadcast;
    private BroadcastReceiver finihTimer;


    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(LocaleManager.onAttach(base));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        UiModeManager uiModeManager = (UiModeManager) getSystemService(UI_MODE_SERVICE);
        if (uiModeManager != null
                && uiModeManager.getCurrentModeType() == Configuration.UI_MODE_TYPE_TELEVISION) {
            setContentView(R.layout.activity_sleep_timer_television);
        } else {
            setContentView(R.layout.activity_sleep_timer);
        }
        ButterKnife.bind(this);
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

    private void registerdBradcast() {
        updateTimerBroadcast = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                long milis = intent.getLongExtra("time", 0);
                int seconds = (int) ((milis / 1000) % 60);
                int minutes = (int) ((milis / (1000 * 60)) % 60);
                int hours = (int) ((milis / (1000 * 60 * 60)) % 24);
                String hoursString = String.valueOf(hours);
                String minutesString = String.valueOf(minutes);
                String secondString = String.valueOf(seconds);

                ArrayList<Character> arrayList = new ArrayList<>();

                if (hours > 0) {
                    if (hoursString.length() == 1) arrayList.add(0, '0');
                    for (int i = 0; i < hoursString.length(); i++) {
                        arrayList.add(0, hoursString.charAt(i));
                    }
                } else {
                    arrayList.add(0, '0');
                    arrayList.add(0, '0');
                }

                if (minutes > 0) {
                    if (minutesString.length() == 1) arrayList.add(0, '0');
                    for (int i = 0; i < minutesString.length(); i++) {
                        arrayList.add(0, minutesString.charAt(i));
                    }
                } else {
                    arrayList.add(0, '0');
                    arrayList.add(0, '0');
                }

                if (seconds > 0) {
                    if (secondString.length() == 1) arrayList.add(0, '0');
                    for (int i = 0; i < secondString.length(); i++) {
                        arrayList.add(0, secondString.charAt(i));
                    }
                } else {
                    arrayList.add(0, '0');
                    arrayList.add(0, '0');
                }

                updateTime(arrayList);

            }
        };
        IntentFilter updateTimerFilter = new IntentFilter(bradcast_UpdateTimer);
        registerReceiver(updateTimerBroadcast, updateTimerFilter);


        finihTimer = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mPresenter.finish();
            }
        };
        IntentFilter finishTimerFilter = new IntentFilter(bradcast_FinishTimer);
        registerReceiver(finihTimer, finishTimerFilter);
    }

    private void initButtonsClick(ViewGroup viewGroup) {
        for (int i = 0; i < viewGroup.getChildCount(); i++) {
            View view = viewGroup.getChildAt(i);
            if (view instanceof Button) {
                Button button = (Button) view;
                button.setOnClickListener(view1 -> mPresenter.numberClick(view1));
            } else if (view instanceof ViewGroup) {
                initButtonsClick((ViewGroup) view);
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(updateTimerBroadcast);
        unregisterReceiver(finihTimer);
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

    @Override
    public void setSrcToStartButton(boolean started) {
        if (started) {
            mStart.setImageDrawable(getDrawable(R.drawable.ic_stop));
        } else {
            mStart.setImageDrawable(getDrawable(R.drawable.ic_play));
        }
    }

    @Override
    public void broadcastStartTimer(Intent intent) {
        int hours = Integer.parseInt(mHours.getText().toString());
        int min = Integer.parseInt(mMinutes.getText().toString());
        int sec = Integer.parseInt(mSeconds.getText().toString());
        long time = (hours * 60 * 60 + min * 60 + sec) * 1000;
        intent.putExtra("time", time);
        sendBroadcast(intent);
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
        }


        return theme;
    }
}
