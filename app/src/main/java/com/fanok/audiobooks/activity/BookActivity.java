package com.fanok.audiobooks.activity;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ShareCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager.widget.ViewPager;

import com.arellomobile.mvp.MvpAppCompatActivity;
import com.arellomobile.mvp.presenter.InjectPresenter;
import com.fanok.audiobooks.Consts;
import com.fanok.audiobooks.R;
import com.fanok.audiobooks.adapter.AudioAdapter;
import com.fanok.audiobooks.adapter.SectionsPagerAdapter;
import com.fanok.audiobooks.interface_pacatge.book_content.Activity;
import com.fanok.audiobooks.pojo.AudioPOJO;
import com.fanok.audiobooks.pojo.BookPOJO;
import com.fanok.audiobooks.presenter.BookPresenter;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.tabs.TabLayout;
import com.google.gson.GsonBuilder;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Locale;
import java.util.Objects;

import butterknife.BindView;
import butterknife.ButterKnife;

public class BookActivity extends MvpAppCompatActivity implements Activity {

    private static final String TAG = "BookActivity";

    public static final String Broadcast_PLAY_NEW_AUDIO = "PlayNewAudio";
    public static final String Broadcast_SET_IMAGE = "SetImage";
    public static final String Broadcast_SET_PROGRESS = "SetProgress";
    public static final String Broadcast_SET_SELECTION = "SetSelection";
    public static final String Broadcast_SET_TITLE = "SetTitle";

    private static final String ARG_BOOK = "arg_book";
    @InjectPresenter
    BookPresenter mPresenter;
    @BindView(R.id.list)
    RecyclerView mList;
    @BindView(R.id.name_curent)
    TextView mNameCurent;
    @BindView(R.id.progressBar)
    ProgressBar mProgressBar;
    @BindView(R.id.previousTop)
    ImageButton mPreviousTop;
    @BindView(R.id.playTop)
    ImageButton mPlayTop;
    @BindView(R.id.nextTop)
    ImageButton mNextTop;
    @BindView(R.id.timeStart)
    TextView mTimeStart;
    @BindView(R.id.seekBar)
    SeekBar mSeekBar;
    @BindView(R.id.timeEnd)
    TextView mTimeEnd;
    @BindView(R.id.rewind)
    ImageButton mRewind;
    @BindView(R.id.previousBottom)
    ImageButton mPreviousBottom;
    @BindView(R.id.playBottom)
    ImageButton mPlayBottom;
    @BindView(R.id.nextBottom)
    ImageButton mNextBottom;
    @BindView(R.id.forward)
    ImageButton mForward;
    private TabLayout tabs;
    private SectionsPagerAdapter sectionsPagerAdapter;
    private BookPOJO mBookPOJO;
    private BottomSheetBehavior bottomSheetBehavior;

    private AudioAdapter mAudioAdapter;

    public static void startNewActivity(@NonNull Context context, @NonNull BookPOJO bookPOJO) {
        Intent intent = new Intent(context, BookActivity.class);
        String json = new GsonBuilder().serializeNulls().create().toJson(bookPOJO);
        intent.putExtra(ARG_BOOK, json);
        context.startActivity(intent);
    }

    private BroadcastReceiver setImage = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int id = intent.getIntExtra("id", R.drawable.ic_play);
            setImageDrawable(id);
        }
    };

    private BroadcastReceiver setProgress = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int timeCurrent = intent.getIntExtra("timeCurrent", 0);
            int timeEnd = intent.getIntExtra("timeEnd", 0);
            updateTime(timeCurrent, timeEnd);
        }
    };

    private BroadcastReceiver setSelectionBroadcast = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int pos = intent.getIntExtra("postion", -1);
            String name = intent.getStringExtra("name");
            setSelected(pos, name);
        }
    };

    private BroadcastReceiver setTitleBroadcast = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String title = intent.getStringExtra("title");
            showTitle(title);
        }
    };


    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate: called");
        Intent intent = getIntent();

        String json = intent.getStringExtra(ARG_BOOK);
        if (json == null) throw new NullPointerException();

        mBookPOJO = BookPOJO.parceJsonToBookPojo(json);
        setContentView(R.layout.activity_book);
        ButterKnife.bind(this);
        setTitle(mBookPOJO.getName().trim());
        sectionsPagerAdapter = new SectionsPagerAdapter(this,
                getSupportFragmentManager(), mBookPOJO.getUrl());
        ViewPager viewPager = findViewById(R.id.view_pager);
        viewPager.setAdapter(sectionsPagerAdapter);
        tabs = findViewById(R.id.tabs);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        tabs.setupWithViewPager(viewPager);

        View llBottomSheet = findViewById(R.id.player);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setHomeButtonEnabled(true);
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setSubtitle(mBookPOJO.getAutor().trim());
        }
        View topBarButtonsControl = llBottomSheet.findViewById(R.id.topButtonsControls);
        ImageButton buttonCollapse = findViewById(R.id.buttonCollapse);
        buttonCollapse.setVisibility(View.INVISIBLE);
        ProgressBar progressBar = findViewById(R.id.progressBar);
        TextView nameCurent = findViewById(R.id.name_curent);

        registerReceiver(setImage, new IntentFilter(Broadcast_SET_IMAGE));

        registerReceiver(setProgress, new IntentFilter(Broadcast_SET_PROGRESS));

        registerReceiver(setSelectionBroadcast, new IntentFilter(Broadcast_SET_SELECTION));

        registerReceiver(setTitleBroadcast, new IntentFilter(Broadcast_SET_TITLE));


        bottomSheetBehavior = BottomSheetBehavior.from(llBottomSheet);


        bottomSheetBehavior.setBottomSheetCallback(new BottomSheetBehavior.BottomSheetCallback() {
            @Override
            public void onStateChanged(@NonNull View bottomSheet, int newState) {
                if (actionBar != null) {
                    if (BottomSheetBehavior.STATE_COLLAPSED == newState) {
                        if (topBarButtonsControl.getVisibility() != View.GONE) {
                            topBarButtonsControl.setVisibility(View.GONE);
                        }
                        if (progressBar.getVisibility() != View.VISIBLE) {
                            progressBar.setVisibility(
                                    View.VISIBLE);
                        }
                        if (buttonCollapse.getVisibility() != View.INVISIBLE) {
                            buttonCollapse.setVisibility(View.INVISIBLE);
                        }
                    }
                }

                if (BottomSheetBehavior.STATE_EXPANDED == newState) {
                    if (topBarButtonsControl.getVisibility() != View.GONE) {
                        topBarButtonsControl.setVisibility(View.GONE);
                    }
                    if (progressBar.getVisibility() != View.INVISIBLE) {
                        progressBar.setVisibility(
                                View.INVISIBLE);
                    }
                    if (buttonCollapse.getVisibility() != View.VISIBLE) {
                        buttonCollapse.setVisibility(View.VISIBLE);
                    }
                } else {
                    topBarButtonsControl.setVisibility(View.VISIBLE);
                    progressBar.setVisibility(View.VISIBLE);
                }
            }


            @Override
            public void onSlide(@NonNull View bottomSheet, float slideOffset) {
                float alpha = 1 - slideOffset * 2;
                topBarButtonsControl.animate().alpha(alpha).setDuration(0);
                progressBar.animate().alpha(alpha).setDuration(0);
                nameCurent.animate().alpha(alpha).setDuration(0);
                if (alpha <= 0.0) {
                    topBarButtonsControl.setVisibility(View.GONE);
                    progressBar.setVisibility(View.INVISIBLE);
                } else {
                    topBarButtonsControl.setVisibility(View.VISIBLE);
                    progressBar.setVisibility(View.VISIBLE);
                }

                if (slideOffset > Consts.COLLAPS_BUTTON_VISIBLE) {
                    if (buttonCollapse.getVisibility() != View.VISIBLE) {
                        buttonCollapse.setVisibility(View.VISIBLE);
                    }

                    double alphaCollapse = (slideOffset - Consts.COLLAPS_BUTTON_VISIBLE)
                            / Consts.COLLAPS_BUTTON_VISIBLE_STEP;
                    buttonCollapse.animate().alpha((float) alphaCollapse).setDuration(0);
                    nameCurent.animate().alpha((float) alphaCollapse).setDuration(0);
                } else if (buttonCollapse.getVisibility() != View.INVISIBLE) {
                    buttonCollapse.setVisibility(View.INVISIBLE);
                }
            }
        });

        llBottomSheet.setOnClickListener(
                view -> {
                    if (BottomSheetBehavior.STATE_COLLAPSED == bottomSheetBehavior.getState()) {
                        bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
                    }
                });

        buttonCollapse.setOnClickListener(view -> {
            if (BottomSheetBehavior.STATE_EXPANDED == bottomSheetBehavior.getState()) {
                bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
            }
        });


        if (savedInstanceState == null) mPresenter.onCreate(mBookPOJO, this);
        else {
            mPresenter.onOrintationChangeListner();
        }


        mAudioAdapter = new AudioAdapter();
        mAudioAdapter.setListener((view, position) -> mPresenter.onItemSelected(view, position));
        mList.setAdapter(mAudioAdapter);
        mList.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false));

        if (savedInstanceState == null) {
            mPresenter.getAudio();
        }


        mSeekBar.setMax(100);
        mProgressBar.setMax(100);

        mSeekBar.setOnTouchListener((v, event) -> {
            mPresenter.seekChange(v);
            return false;
        });


        mPlayTop.setOnClickListener(view -> mPresenter.buttomPlayClick(view));
        mPlayBottom.setOnClickListener(view -> mPresenter.buttomPlayClick(view));
        mPreviousTop.setOnClickListener(view -> mPresenter.buttomPreviousClick(view));
        mPreviousBottom.setOnClickListener(view -> mPresenter.buttomPreviousClick(view));
        mNextTop.setOnClickListener(view -> mPresenter.buttomNextClick(view));
        mNextBottom.setOnClickListener(view -> mPresenter.buttomNextClick(view));
        mRewind.setOnClickListener(view -> mPresenter.buttomRewindClick(view));
        mForward.setOnClickListener(view -> mPresenter.buttomForwardClick(view));


    }


    public void showSiries() {
        showPage(getResources().getString(R.string.tab_text_3));
    }

    public void showOtherArtist() {
        showPage(getResources().getString(R.string.tab_text_4));
    }

    private void showPage(String name) {
        boolean temp = false;
        for (int i = 0; i < tabs.getTabCount(); i++) {
            String title = Objects.requireNonNull(
                    Objects.requireNonNull(tabs.getTabAt(i)).getText()).toString();
            if (title.equals(name)) {
                temp = true;
            }
        }
        if (!temp) {
            tabs.addTab(tabs.newTab().setText(name));
            sectionsPagerAdapter.addTabPage(name);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem menuItem) {
        if (menuItem.getItemId() == android.R.id.home) {
            finish();
        }
        mPresenter.onOptionsMenuItemSelected(menuItem);
        return (super.onOptionsItemSelected(menuItem));
    }

    @Override
    protected void onDestroy() {
        mPresenter.onDestroy();
        unregisterReceiver(setImage);
        unregisterReceiver(setProgress);
        unregisterReceiver(setSelectionBroadcast);
        unregisterReceiver(setTitleBroadcast);
        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.book_activity_options_menu, menu);
        mPresenter.onCreateOptionsMenu(menu);
        return true;
    }

    @Override
    public void setTabPostion(String title) {
        for (int i = 0; i < tabs.getTabCount(); i++) {
            TabLayout.Tab tab = tabs.getTabAt(i);
            if (tab != null && Objects.requireNonNull(tab.getText()).toString().equals(title)) {
                tab.select();
            }
        }
    }

    @Override
    public void refreshActivity() {
        finish();
        Intent intent = new Intent(this, BookActivity.class);
        String json = new GsonBuilder().serializeNulls().create().toJson(mBookPOJO);
        intent.putExtra(ARG_BOOK, json);
        overridePendingTransition(0, 0);
        BookActivity.startNewActivity(this, mBookPOJO);
    }

    @Override
    public void shareTextUrl() {
        ShareCompat.IntentBuilder.from(this)
                .setType("text/plain")
                .setText(mBookPOJO.getUrl())
                .startChooser();
    }


    @Override
    public void addToMainScreen() {
        Log.d(TAG, "addToMainScreen: callded");
    }

    @Override
    public void showProgres(boolean b) {

    }

    @Override
    public void onBackPressed() {
        if (bottomSheetBehavior.getState() == BottomSheetBehavior.STATE_EXPANDED) {
            bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public void showData(@NonNull ArrayList<AudioPOJO> data) {
        if (mAudioAdapter != null) mAudioAdapter.setData(data);
    }

    @Override
    public void showTitle(@NotNull @NonNull String name) {
        mNameCurent.setText(name);
    }

    @Override
    public void updateTime(int timeCurent, int timeEnd) {
        mProgressBar.setMax(timeEnd);
        mSeekBar.setMax(timeEnd);
        mProgressBar.setProgress(timeCurent);
        mSeekBar.setProgress(timeCurent);

        timeCurent /= 1000;
        timeEnd /= 1000;
        int minutes = timeCurent / 60;
        int seconds = timeCurent % 60;
        String timeString = String.format(Locale.forLanguageTag("UA"), "%02d:%02d", minutes,
                seconds);

        mTimeStart.setText(timeString);

        minutes = timeEnd / 60;
        seconds = timeEnd % 60;
        timeString = String.format(Locale.forLanguageTag("UA"), "%02d:%02d", minutes, seconds);
        mTimeEnd.setText(timeString);
    }

    @Override
    public void setTimeEnd(int timeEnd) {

    }

    @Override
    public void setImageDrawable(int id) {
        mPlayTop.setImageResource(id);
        mPlayBottom.setImageResource(id);
    }

    @Override
    public void onSaveInstanceState(Bundle outState, PersistableBundle outPersistentState) {
        super.onSaveInstanceState(outState, outPersistentState);
        outState.putBoolean("serviceStatus", mPresenter.isServiceBound());
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        mPresenter.setServiceBound(savedInstanceState.getBoolean("serviceStatus"));
    }

    @Override
    public void setSelected(int id, String name) {
        if (mAudioAdapter != null) {
            if (id >= 0 && id < mAudioAdapter.getItemCount()) {
                if (mAudioAdapter.getData(id).getName().equals(name)) {
                    mAudioAdapter.setIndexSelected(id);
                }
            }
        }
    }
}