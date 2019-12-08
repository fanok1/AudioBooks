package com.fanok.audiobooks.activity;

import static com.fanok.audiobooks.activity.ParentalControlActivity.PARENTAL_CONTROL_ENABLED;
import static com.fanok.audiobooks.activity.ParentalControlActivity.PARENTAL_CONTROL_PREFERENCES;
import static com.fanok.audiobooks.activity.ParentalControlActivity.PARENTAL_PASSWORD;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.util.Log;
import android.util.Xml;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ShareCompat;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager.widget.ViewPager;

import com.alimuzaffar.lib.pin.PinEntryEditText;
import com.arellomobile.mvp.MvpAppCompatActivity;
import com.arellomobile.mvp.presenter.InjectPresenter;
import com.arellomobile.mvp.presenter.ProvidePresenter;
import com.fanok.audiobooks.Consts;
import com.fanok.audiobooks.LocaleManager;
import com.fanok.audiobooks.R;
import com.fanok.audiobooks.adapter.AudioAdapter;
import com.fanok.audiobooks.adapter.SectionsPagerAdapter;
import com.fanok.audiobooks.interface_pacatge.book_content.Activity;
import com.fanok.audiobooks.pojo.AudioPOJO;
import com.fanok.audiobooks.pojo.BookPOJO;
import com.fanok.audiobooks.pojo.StorageAds;
import com.fanok.audiobooks.presenter.BookPresenter;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.tabs.TabLayout;
import com.google.gson.GsonBuilder;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.Target;

import org.jetbrains.annotations.NotNull;
import org.xmlpull.v1.XmlPullParser;

import java.util.ArrayList;
import java.util.Collections;
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
    public static final String Broadcast_SHOW_GET_PLUS = "ShowGetPlus";

    private static String showingView;
    @Nullable
    @BindView(R.id.buttonCollapse)
    ImageButton mButtonCollapse;
    @Nullable
    @BindView(R.id.topButtonsControls)
    LinearLayout mTopButtonsControls;
    @BindView(R.id.player)
    LinearLayout mPlayer;

    public static String getShowingView() {
        return showingView;
    }

    private static final String ARG_BOOK = "arg_book";
    @InjectPresenter
    BookPresenter mPresenter;
    @BindView(R.id.list)
    RecyclerView mList;
    @BindView(R.id.name_curent)
    TextView mNameCurent;

    @Nullable
    @BindView(R.id.progressBar)
    ProgressBar mProgressBar;
    @Nullable
    @BindView(R.id.previousTop)
    ImageButton mPreviousTop;
    @Nullable
    @BindView(R.id.playTop)
    ImageButton mPlayTop;
    @Nullable
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
    @BindView(R.id.speed)
    ImageButton mSpeed;
    private TabLayout tabs;
    private SectionsPagerAdapter sectionsPagerAdapter;
    private BookPOJO mBookPOJO;
    private BottomSheetBehavior bottomSheetBehavior;

    private MenuItem mAddFavorite;
    private MenuItem mRemoveFavorite;

    private boolean mNotificationClick;

    private AudioAdapter mAudioAdapter;
    private BroadcastReceiver setImage = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int id = intent.getIntExtra("id", R.drawable.ic_play);
            mPresenter.setImageDrawable(id);
        }
    };
    private BroadcastReceiver showGetPlus = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Intent start = new Intent(context, PopupGetPlus.class);
            start.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
            startActivity(start);
        }
    };

    private BroadcastReceiver setProgress = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int timeCurrent = intent.getIntExtra("timeCurrent", 0);
            int timeEnd = intent.getIntExtra("timeEnd", 0);
            mPresenter.updateTime(timeCurrent, timeEnd);
        }
    };
    private BroadcastReceiver setSelectionBroadcast = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int pos = intent.getIntExtra("postion", -1);
            String name = intent.getStringExtra("name");
            if (name != null) {
                mPresenter.setSelected(pos, name);
            }
        }
    };
    private BroadcastReceiver setTitleBroadcast = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String title = intent.getStringExtra("title");
            if (title != null) {
                mPresenter.showTitle(title);
            }
        }
    };

    public static void startNewActivity(@NonNull Context context, @NonNull BookPOJO bookPOJO) {
        startNewActivity(context, bookPOJO, false);
    }

    public static void startNewActivity(@NonNull Context context, @NonNull BookPOJO bookPOJO,
            boolean notificationClick) {
        Intent intent = new Intent(context, BookActivity.class);
        intent.putExtra("notificationClick", notificationClick);
        String json = new GsonBuilder().serializeNulls().create().toJson(bookPOJO);
        intent.putExtra(ARG_BOOK, json);
        SharedPreferences preferences =
                context.getSharedPreferences(PARENTAL_CONTROL_PREFERENCES, MODE_PRIVATE);
        if (preferences.getBoolean(PARENTAL_CONTROL_ENABLED, false) &&
                !preferences.getBoolean(bookPOJO.getGenre(), false) && !notificationClick) {

            XmlPullParser parser = context.getResources().getXml(R.xml.pin_entry);
            try {
                parser.next();
                parser.nextTag();
            } catch (Exception e) {
                e.printStackTrace();
            }

            AttributeSet attr = Xml.asAttributeSet(parser);
            PinEntryEditText editText = new PinEntryEditText(context, attr);


            AlertDialog.Builder builder = new AlertDialog.Builder(context);
            builder.setTitle(R.string.enter_password)
                    .setCancelable(false)
                    .setView(editText)
                    .setNeutralButton(R.string.cancel, null)
                    .setPositiveButton(R.string.confirm, null);

            AlertDialog dialog = builder.create();
            dialog.setOnShowListener(dialogInterface -> {
                Objects.requireNonNull(editText.getText()).clear();
                editText.requestFocus();
                Button b = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
                b.setEnabled(false);
                b.setOnClickListener(view -> {
                    if (preferences.getInt(PARENTAL_PASSWORD, 0) == Integer.parseInt(
                            editText.getText().toString())) {
                        dialog.cancel();
                        context.startActivity(intent);
                    } else {
                        Toast.makeText(context,
                                context.getString(R.string.incorect_password),
                                Toast.LENGTH_SHORT).show();
                        editText.getText().clear();
                    }
                });
            });

            editText.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

                }

                @Override
                public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {

                }

                @Override
                public void afterTextChanged(Editable editable) {
                    int size = Objects.requireNonNull(editText.getText()).toString().length();
                    if (size == 4) {
                        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                    } else {
                        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
                    }
                }
            });

            editText.setOnPinEnteredListener(str -> {
                if (preferences.getInt(PARENTAL_PASSWORD, 0) == Integer.parseInt(
                        Objects.requireNonNull(editText.getText()).toString())) {
                    dialog.cancel();
                    context.startActivity(intent);
                } else {
                    Toast.makeText(context,
                            context.getString(R.string.incorect_password),
                            Toast.LENGTH_SHORT).show();
                    editText.getText().clear();
                }
            });

            dialog.show();

        } else {
            context.startActivity(intent);
        }
    }

    private static void refreshActivity(@NonNull Context context, @NonNull BookPOJO bookPOJO,
            boolean notificationClick) {
        Intent intent = new Intent(context, BookActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
        intent.putExtra("notificationClick", notificationClick);
        String json = new GsonBuilder().serializeNulls().create().toJson(bookPOJO);
        intent.putExtra(ARG_BOOK, json);
        context.startActivity(intent);
        ((AppCompatActivity) context).finish();
    }


    @ProvidePresenter
    BookPresenter provideBookPresenter() {
        Intent intent = getIntent();
        String json = intent.getStringExtra(ARG_BOOK);
        if (json == null) throw new NullPointerException();
        return new BookPresenter(BookPOJO.parceJsonToBookPojo(json), getApplicationContext());
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(LocaleManager.onAttach(base));
    }

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

        MainActivity.setCloseApp(false);

        mNotificationClick = intent.getBooleanExtra("notificationClick", false);

        SharedPreferences pref = PreferenceManager
                .getDefaultSharedPreferences(this);

        String themeName = pref.getString("pref_theme", getString(R.string.theme_dark_value));
        if (themeName.equals(getString(R.string.theme_dark_value))) {
            setTheme(R.style.AppTheme_NoActionBar);
        } else if (themeName.equals(getString(R.string.theme_light_value))) {
            setTheme(R.style.LightAppTheme_NoActionBar);
        }

        setTitle(mBookPOJO.getName().trim());
        sectionsPagerAdapter = new SectionsPagerAdapter(this,
                getSupportFragmentManager(), mBookPOJO.getUrl());
        ViewPager viewPager = findViewById(R.id.view_pager);
        viewPager.setAdapter(sectionsPagerAdapter);
        tabs = findViewById(R.id.tabs);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        tabs.setupWithViewPager(viewPager);

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setHomeButtonEnabled(true);
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setSubtitle(mBookPOJO.getAutor().trim());
        }

        if (mButtonCollapse != null) {
            mButtonCollapse.setVisibility(View.INVISIBLE);
        }

        TextView nameCurent = findViewById(R.id.name_curent);

        registerReceiver(setImage, new IntentFilter(Broadcast_SET_IMAGE));

        registerReceiver(setProgress, new IntentFilter(Broadcast_SET_PROGRESS));

        registerReceiver(setSelectionBroadcast, new IntentFilter(Broadcast_SET_SELECTION));

        registerReceiver(setTitleBroadcast, new IntentFilter(Broadcast_SET_TITLE));
        registerReceiver(showGetPlus, new IntentFilter(Broadcast_SHOW_GET_PLUS));

        int isTablet = getResources().getInteger(R.integer.isTablet);
        if (isTablet == 0) {
            bottomSheetBehavior = BottomSheetBehavior.from(mPlayer);


            bottomSheetBehavior.setBottomSheetCallback(
                    new BottomSheetBehavior.BottomSheetCallback() {
                        @Override
                        public void onStateChanged(@NonNull View bottomSheet, int newState) {
                            if (actionBar != null) {
                                if (BottomSheetBehavior.STATE_COLLAPSED == newState) {
                                    mPresenter.stateCollapsed();
                                }
                            }

                            if (BottomSheetBehavior.STATE_EXPANDED == newState) {
                                mPresenter.stateExpanded();
                            } else {
                                mPresenter.stateElse();
                            }
                        }


                        @Override
                        public void onSlide(@NonNull View bottomSheet, float slideOffset) {
                            float alpha = 1 - slideOffset * 2;
                            if (mTopButtonsControls != null) {
                                mTopButtonsControls.animate().alpha(alpha).setDuration(0);
                            }
                            if (mProgressBar != null) {
                                mProgressBar.animate().alpha(alpha).setDuration(0);
                            }
                            nameCurent.animate().alpha(alpha).setDuration(0);
                            if (alpha <= 0.0) {
                                mTopButtonsControls.setVisibility(View.GONE);
                                mProgressBar.setVisibility(View.INVISIBLE);
                            } else {
                                mTopButtonsControls.setVisibility(View.VISIBLE);
                                mProgressBar.setVisibility(View.VISIBLE);
                            }

                            if (slideOffset > Consts.COLLAPS_BUTTON_VISIBLE) {
                                if (mButtonCollapse != null
                                        && mButtonCollapse.getVisibility() != View.VISIBLE) {
                                    mButtonCollapse.setVisibility(View.VISIBLE);
                                }

                                double alphaCollapse = (slideOffset - Consts.COLLAPS_BUTTON_VISIBLE)
                                        / Consts.COLLAPS_BUTTON_VISIBLE_STEP;
                                if (mButtonCollapse != null) {
                                    mButtonCollapse.animate().alpha(
                                            (float) alphaCollapse).setDuration(
                                            0);
                                }
                                nameCurent.animate().alpha((float) alphaCollapse).setDuration(0);
                            } else if (mButtonCollapse != null
                                    && mButtonCollapse.getVisibility() != View.INVISIBLE) {
                                mButtonCollapse.setVisibility(View.INVISIBLE);
                            }
                        }
                    });
            mPlayer.setOnClickListener(
                    view -> {
                        if (BottomSheetBehavior.STATE_COLLAPSED == bottomSheetBehavior.getState()) {
                            bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
                        }
                    });


            mButtonCollapse.setOnClickListener(view -> {
                if (BottomSheetBehavior.STATE_EXPANDED == bottomSheetBehavior.getState()) {
                    bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
                }
            });

        }




        mAudioAdapter = new AudioAdapter();
        mAudioAdapter.setListener((view, position) -> mPresenter.onItemSelected(view, position));
        mList.setAdapter(mAudioAdapter);
        mList.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false));


        mSeekBar.setMax(100);
        if (mProgressBar != null) {
            mProgressBar.setMax(100);
        }

        mSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                mPresenter.seekChange(seekBar);
            }
        });


        if (mPlayTop != null) {
            mPlayTop.setOnClickListener(view -> mPresenter.buttomPlayClick(view));
        }
        if (mPreviousTop != null) {
            mPreviousTop.setOnClickListener(view -> mPresenter.buttomPreviousClick(view));
        }
        if (mNextTop != null) {
            mNextTop.setOnClickListener(view -> mPresenter.buttomNextClick(view));
        }
        mPlayBottom.setOnClickListener(view -> mPresenter.buttomPlayClick(view));
        mPreviousBottom.setOnClickListener(view -> mPresenter.buttomPreviousClick(view));
        mNextBottom.setOnClickListener(view -> mPresenter.buttomNextClick(view));
        mRewind.setOnClickListener(view -> mPresenter.buttomRewindClick(view));
        mForward.setOnClickListener(view -> mPresenter.buttomForwardClick(view));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            mSpeed.setOnClickListener(v -> mPresenter.buttonSpeedClick(v));
            mSpeed.setVisibility(View.VISIBLE);
        } else {
            mSpeed.setVisibility(View.GONE);
        }

        getSystemService(AUDIO_SERVICE);



    }

    @Override
    public void stateCollapsed() {
        if (mTopButtonsControls != null && mTopButtonsControls.getVisibility() != View.GONE) {
            mTopButtonsControls.setVisibility(View.GONE);
        }
        if (mProgressBar != null && mProgressBar.getVisibility() != View.VISIBLE) {
            mProgressBar.setVisibility(
                    View.VISIBLE);
        }
        if (mButtonCollapse != null && mButtonCollapse.getVisibility() != View.INVISIBLE) {
            mButtonCollapse.setVisibility(View.INVISIBLE);
        }
    }

    @Override
    public void stateExpanded() {
        if (mTopButtonsControls != null && mTopButtonsControls.getVisibility() != View.GONE) {
            mTopButtonsControls.setVisibility(View.GONE);
        }
        if (mProgressBar != null && mProgressBar.getVisibility() != View.INVISIBLE) {
            mProgressBar.setVisibility(
                    View.INVISIBLE);
        }
        if (mButtonCollapse != null && mButtonCollapse.getVisibility() != View.VISIBLE) {
            mButtonCollapse.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void stateElse() {
        if (mTopButtonsControls != null) {
            mTopButtonsControls.setVisibility(View.VISIBLE);
        }
        if (mProgressBar != null) {
            mProgressBar.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void setIsFavorite(boolean b) {
        if (b) {
            mAddFavorite.setVisible(false);
            mRemoveFavorite.setVisible(true);
        } else {
            mAddFavorite.setVisible(true);
            mRemoveFavorite.setVisible(false);
        }
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
            if (mNotificationClick) {
                Intent intent = new Intent(this, MainActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
            } else {
                finish();
            }
        }
        mPresenter.onOptionsMenuItemSelected(menuItem);
        return (super.onOptionsItemSelected(menuItem));
    }

    @Override
    protected void onStop() {
        mPresenter.onStop();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(setImage);
        unregisterReceiver(setProgress);
        unregisterReceiver(setSelectionBroadcast);
        unregisterReceiver(setTitleBroadcast);
        unregisterReceiver(showGetPlus);
        showingView = "";
        mPresenter.onDestroy();
        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.book_activity_options_menu, menu);
        mAddFavorite = menu.findItem(R.id.addFavorite);
        mRemoveFavorite = menu.findItem(R.id.removeFavorite);
        if (android.os.Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            menu.findItem(R.id.addMainScreen).setVisible(false);
        }
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
        BookActivity.refreshActivity(this, mBookPOJO, mNotificationClick);
    }

    @Override
    public void shareTextUrl() {
        ShareCompat.IntentBuilder.from(this)
                .setType("text/plain")
                .setText(mBookPOJO.getUrl())
                .startChooser();
    }


    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    public void addToMainScreen(BookPOJO pojo) {
        if (StorageAds.idDisableAds()) {
            Picasso.get().load(pojo.getPhoto()).into(new Target() {
                @Override
                public void onBitmapLoaded(Bitmap bitmap, Picasso.LoadedFrom from) {
                    ShortcutManager shortcutManager = getSystemService(ShortcutManager.class);

                    Intent intent = new Intent(getApplicationContext(), LoadBook.class);
                    intent.setAction(Intent.ACTION_VIEW);
                    intent.putExtra("url", pojo.getUrl());
                    intent.putExtra("notificationClick", true);

                    ShortcutInfo shortcut = new ShortcutInfo.Builder(getApplicationContext(),
                            pojo.getUrl())
                            .setShortLabel(pojo.getName())
                            .setLongLabel(pojo.getName())
                            .setIcon(Icon.createWithBitmap(bitmap))
                            .setIntent(intent)
                            .build();

                    if (shortcutManager != null) {
                        shortcutManager.setDynamicShortcuts(Collections.singletonList(shortcut));
                        if (shortcutManager.isRequestPinShortcutSupported()) {
                            ShortcutInfo pinShortcutInfo = new ShortcutInfo
                                    .Builder(getApplicationContext(), pojo.getUrl())
                                    .build();
                            Intent pinnedShortcutCallbackIntent =
                                    shortcutManager.createShortcutResultIntent(pinShortcutInfo);


                            PendingIntent successCallback = PendingIntent.getBroadcast(
                                    getApplicationContext(), 0,
                                    pinnedShortcutCallbackIntent, 0);
                            shortcutManager.requestPinShortcut(pinShortcutInfo,
                                    successCallback.getIntentSender());
                        }
                    }
                }

                @Override
                public void onBitmapFailed(Exception e, Drawable errorDrawable) {

                }

                @Override
                public void onPrepareLoad(Drawable placeHolderDrawable) {

                }
            });

        } else {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(R.string.getPlus);
            builder.setMessage(R.string.only_plus);
            builder.setPositiveButton(R.string.buy, (dialogInterface, i) -> {
                Intent start = new Intent(BookActivity.this, PopupGetPlus.class);
                start.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
                startActivity(start);
            });
            builder.setNeutralButton(R.string.cancel, null);
            builder.show();
        }
    }

    @Override
    public void showProgres(boolean b) {

    }

    @Override
    public void onBackPressed() {
        if (bottomSheetBehavior != null
                && bottomSheetBehavior.getState() == BottomSheetBehavior.STATE_EXPANDED) {
            bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
        } else {
            if (mNotificationClick) {
                Intent intent = new Intent(this, MainActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
            } else {
                super.onBackPressed();
            }
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
        if (mProgressBar != null) {
            mProgressBar.setMax(timeEnd);
            mProgressBar.setProgress(timeCurent);
        }
        mSeekBar.setMax(timeEnd);
        mSeekBar.setProgress(timeCurent);

        timeCurent /= 1000;
        timeEnd /= 1000;
        int minutes = timeCurent / 60;
        int seconds = timeCurent % 60;
        String timeString = String.format(Locale.forLanguageTag("UK"), "%02d:%02d", minutes,
                seconds);

        mTimeStart.setText(timeString);

        minutes = timeEnd / 60;
        seconds = timeEnd % 60;
        timeString = String.format(Locale.forLanguageTag("UK"), "%02d:%02d", minutes, seconds);
        mTimeEnd.setText(timeString);
    }

    @Override
    public void setTimeEnd(int timeEnd) {

    }

    @Override
    public void setImageDrawable(int id) {
        if (mPlayTop != null) {
            mPlayTop.setImageResource(id);
        }
        mPlayBottom.setImageResource(id);
    }

    @Override
    public void onSaveInstanceState(@NotNull Bundle outState,
            @NotNull PersistableBundle outPersistentState) {
        super.onSaveInstanceState(outState, outPersistentState);
        outState.putBoolean("serviceStatus", mPresenter.isServiceBound());
    }

    @Override
    protected void onRestoreInstanceState(@NotNull Bundle savedInstanceState) {
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

    @Override
    public void broadcastSend(@NotNull @NonNull Intent intent) {
        sendBroadcast(intent);
    }

    @Override
    public void activityStart(@NotNull @NonNull Intent intent) {
        startActivity(intent);
    }

    @Override
    public void myUnbindService(@NotNull @NonNull ServiceConnection serviceConnection) {
        try {
            unbindService(serviceConnection);
        } catch (Exception ignored) {
        }

    }

    @Override
    public Resources.Theme getTheme() {
        Resources.Theme theme = super.getTheme();

        SharedPreferences pref = PreferenceManager
                .getDefaultSharedPreferences(this);

        String themeName = pref.getString("pref_theme", getString(R.string.theme_dark_value));
        if (themeName.equals(getString(R.string.theme_dark_value))) {
            theme.applyStyle(R.style.AppTheme_NoActionBar, true);
        } else if (themeName.equals(getString(R.string.theme_light_value))) {
            theme.applyStyle(R.style.LightAppTheme_NoActionBar, true);
        }


        return theme;
    }

    @Override
    protected void onResume() {
        super.onResume();
        showingView = mBookPOJO.getUrl();
        View decorView = getWindow().getDecorView();
        int uiOptions = View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
        decorView.setSystemUiVisibility(uiOptions);
    }


}