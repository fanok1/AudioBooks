package com.fanok.audiobooks.activity;

import static android.view.KeyEvent.KEYCODE_MEDIA_PLAY;
import static android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE;
import static com.fanok.audiobooks.Consts.handleUserInput;
import static com.fanok.audiobooks.Consts.isServiceRunning;
import static com.fanok.audiobooks.activity.ParentalControlActivity.PARENTAL_CONTROL_ENABLED;
import static com.fanok.audiobooks.activity.ParentalControlActivity.PARENTAL_CONTROL_PREFERENCES;
import static com.fanok.audiobooks.activity.ParentalControlActivity.PARENTAL_PASSWORD;
import static com.fanok.audiobooks.presenter.BookPresenter.Broadcast_SHOW_TITLE;
import static com.fanok.audiobooks.service.MediaPlayerService.countAudioWereShowingRatingPopUp;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.app.UiModeManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.util.Log;
import android.util.Xml;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewParent;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatSeekBar;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.media3.common.util.UnstableApi;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.alimuzaffar.lib.pin.PinEntryEditText;
import com.arellomobile.mvp.MvpAppCompatActivity;
import com.arellomobile.mvp.presenter.InjectPresenter;
import com.arellomobile.mvp.presenter.ProvidePresenter;
import com.evgenii.jsevaluator.JsEvaluator;
import com.evgenii.jsevaluator.interfaces.JsCallback;
import com.fanok.audiobooks.Consts;
import com.fanok.audiobooks.LocaleManager;
import com.fanok.audiobooks.R;
import com.fanok.audiobooks.TvSeekBar;
import com.fanok.audiobooks.Url;
import com.fanok.audiobooks.adapter.AudioAdapter;
import com.fanok.audiobooks.adapter.SectionsPagerAdapter;
import com.fanok.audiobooks.interface_pacatge.book_content.Activity;
import com.fanok.audiobooks.pojo.AudioPOJO;
import com.fanok.audiobooks.pojo.BookPOJO;
import com.fanok.audiobooks.pojo.StorageUtil;
import com.fanok.audiobooks.presenter.BookPresenter;
import com.fanok.audiobooks.service.MediaPlayerService;
import com.fanok.audiobooks.android_equalizer.DialogEqualizerFragment;
import com.google.android.gms.tasks.Task;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.google.android.play.core.review.ReviewInfo;
import com.google.android.play.core.review.ReviewManager;
import com.google.android.play.core.review.ReviewManagerFactory;
import com.google.gson.GsonBuilder;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.Target;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Locale;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.xmlpull.v1.XmlPullParser;

public class BookActivity extends MvpAppCompatActivity implements Activity {

    private static final String TAG = "BookActivity";

    public static final String Broadcast_PLAY_NEW_AUDIO = "PlayNewAudio";
    public static final String Broadcast_SET_IMAGE = "SetImage";
    public static final String Broadcast_SET_PROGRESS = "SetProgress";
    public static final String Broadcast_SET_SELECTION = "SetSelection";
    public static final String Broadcast_SET_TITLE = "SetTitle";
    public static final String Broadcast_SHOW_RATING = "ShowRating";
    public static final String Broadcast_UPDATE_ADAPTER = "UpdateAdapter";

    public static final String Broadcast_SHOW_EQUALIZER = "ShowEqualizer";


    private static String showingView;


    private ImageButton mButtonCollapse;
    private LinearLayout mTopButtonsControls;
    private LinearLayout mPlayer;
    private RadioButton mRadioAll;
    private ImageButton mDelete;
    private ImageButton mDownland;

    public static String getShowingView() {
        return showingView;
    }

    private static final String ARG_BOOK = "arg_book";
    @InjectPresenter
    BookPresenter mPresenter;
    private ImageButton mSpeed;
    private RecyclerView mList;
    private TextView mNameCurent;
    private ProgressBar mProgressBar;
    private ImageButton mPreviousTop;
    private ImageButton mPlayTop;
    private ImageButton mNextTop;
    private TextView mTimeStart;
    private AppCompatSeekBar mSeekBar;
    private TextView mTimeEnd;
    private ImageButton mRewind;
    private ImageButton mPreviousBottom;
    private ImageButton mPlayBottom;
    private ImageButton mNextBottom;
    private ImageButton mForward;
    private TabLayout tabs;
    private SectionsPagerAdapter sectionsPagerAdapter;
    private BookPOJO mBookPOJO;
    private BottomSheetBehavior<LinearLayout> bottomSheetBehavior;
    private LinearLayoutManager mLinearLayoutManager;
    private SharedPreferences pref;

    private MenuItem mAddFavorite;
    private MenuItem mRemoveFavorite;

    private boolean mNotificationClick;

    @SuppressLint("UnsafeOptInUsageError")
    private AudioAdapter mAudioAdapter;
    private final BroadcastReceiver setImage = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int id = intent.getIntExtra("id", R.drawable.ic_play);
            mPresenter.setImageDrawable(id);
        }
    };

    private final BroadcastReceiver showRating = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            showRatingDialog();
        }
    };

    private final BroadcastReceiver setProgress = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int timeCurrent = intent.getIntExtra("timeCurrent", 0);
            int timeEnd = intent.getIntExtra("timeEnd", 0);
            int buffered = intent.getIntExtra("buffered", 0);
            mPresenter.updateTime(timeCurrent, timeEnd, buffered);
        }
    };
    private final BroadcastReceiver setSelectionBroadcast = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int pos = intent.getIntExtra("postion", -1);
            String name = intent.getStringExtra("name");
            if (name != null) {
                mPresenter.setSelected(pos, name);
            }
        }
    };
    private final BroadcastReceiver setTitleBroadcast = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String title = intent.getStringExtra("title");
            if (title != null) {
                mPresenter.showTitle(title);
            }
        }
    };


    private final BroadcastReceiver showEqualizer = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int sessionId = intent.getIntExtra("id", 0);
            if (sessionId > 0) {
                try {
                    DialogEqualizerFragment fragment = DialogEqualizerFragment.newBuilder()
                            .setAudioSessionId(sessionId)
                            .themeColor(ContextCompat.getColor(context, R.color.primaryColorEq))
                            .textColor(ContextCompat.getColor(context, R.color.textColor))
                            .setAccentColor(ContextCompat.getColor(context, R.color.secondaryColor))
                            .build();

                    fragment.show(getSupportFragmentManager(), "eq");
                } catch (IllegalStateException ignored) {
                }
            }
        }
    };


    private final BroadcastReceiver updateAdapter = new BroadcastReceiver() {
        @UnstableApi
        @Override
        public void onReceive(Context context, Intent intent) {
            updateAdapter();
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
                Log.d(TAG, "startNewActivity: ", e);
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
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(size == 4);
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

    @UnstableApi
    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate: called");

        Intent intent = getIntent();
        String json = intent.getStringExtra(ARG_BOOK);
        if (json == null) throw new NullPointerException();
        mBookPOJO = BookPOJO.parceJsonToBookPojo(json);

        pref = PreferenceManager
                .getDefaultSharedPreferences(this);

        int isTablet = getResources().getInteger(R.integer.isTablet);
        UiModeManager uiModeManager = (UiModeManager) getSystemService(UI_MODE_SERVICE);
        setContentView(R.layout.activity_book);
        mButtonCollapse = findViewById(R.id.buttonCollapse);
        mTopButtonsControls = findViewById(R.id.topButtonsControls);
        mPlayer = findViewById(R.id.player);
        mRadioAll = findViewById(R.id.radioAll);
        mDelete = findViewById(R.id.delete);
        mDownland = findViewById(R.id.dowland);
        mSpeed = findViewById(R.id.speed);
        mList = findViewById(R.id.list);
        mNameCurent = findViewById(R.id.name_curent);
        mProgressBar = findViewById(R.id.progressBar);
        mPreviousTop = findViewById(R.id.previousTop);
        mPlayTop = findViewById(R.id.playTop);
        mNextTop = findViewById(R.id.nextTop);
        mTimeStart = findViewById(R.id.timeStart);
        mSeekBar = findViewById(R.id.seekBar);
        mTimeEnd = findViewById(R.id.timeEnd);
        mRewind = findViewById(R.id.rewind);
        mPreviousBottom = findViewById(R.id.previousBottom);
        mPlayBottom = findViewById(R.id.playBottom);
        mNextBottom = findViewById(R.id.nextBottom);
        mForward = findViewById(R.id.forward);

        MainActivity.setCloseApp(false);

        mNotificationClick = intent.getBooleanExtra("notificationClick", false);

        String themeName = pref.getString("pref_theme", getString(R.string.theme_dark_value));
        if (themeName.equals(getString(R.string.theme_dark_value))) {
            setTheme(R.style.AppTheme_NoActionBar);
        } else if (themeName.equals(getString(R.string.theme_light_value))) {
            setTheme(R.style.LightAppTheme_NoActionBar);
        } else if (themeName.equals(getString(R.string.theme_black_value))) {
            setTheme(R.style.AppThemeBlack_NoActionBar);
        }

        if (mBookPOJO != null && mBookPOJO.getName() != null) {
            setTitle(mBookPOJO.getName().trim());
        }

        sectionsPagerAdapter = new SectionsPagerAdapter(this, Objects.requireNonNull(mBookPOJO));
        ViewPager2 viewPager = findViewById(R.id.view_pager);
        viewPager.setAdapter(sectionsPagerAdapter);

        tabs = findViewById(R.id.tabs);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        new TabLayoutMediator(tabs, viewPager,
                (tab, position) -> tab.setText(sectionsPagerAdapter.getPageTitle(position))
        ).attach();


        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setHomeButtonEnabled(true);
            actionBar.setDisplayHomeAsUpEnabled(true);
            if (mBookPOJO != null && mBookPOJO.getAutor() != null) {
                actionBar.setSubtitle(mBookPOJO.getAutor().trim());
            }
        }

        if (mButtonCollapse != null && mButtonCollapse.getVisibility() != View.INVISIBLE) {
            mButtonCollapse.setVisibility(View.INVISIBLE);
        }


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(setImage, new IntentFilter(Broadcast_SET_IMAGE), RECEIVER_EXPORTED);
        }else {
            registerReceiver(setImage, new IntentFilter(Broadcast_SET_IMAGE));
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(setProgress, new IntentFilter(Broadcast_SET_PROGRESS), RECEIVER_EXPORTED);
        }else {
            registerReceiver(setProgress, new IntentFilter(Broadcast_SET_PROGRESS));
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(setSelectionBroadcast, new IntentFilter(Broadcast_SET_SELECTION), RECEIVER_EXPORTED);
        }else {
            registerReceiver(setSelectionBroadcast, new IntentFilter(Broadcast_SET_SELECTION));
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(setTitleBroadcast, new IntentFilter(Broadcast_SET_TITLE), RECEIVER_EXPORTED);
        }else{
            registerReceiver(setTitleBroadcast, new IntentFilter(Broadcast_SET_TITLE));
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(showRating, new IntentFilter(Broadcast_SHOW_RATING), RECEIVER_EXPORTED);
        }else {
            registerReceiver(showRating, new IntentFilter(Broadcast_SHOW_RATING));
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(updateAdapter, new IntentFilter(Broadcast_UPDATE_ADAPTER), RECEIVER_EXPORTED);
        }else {
            registerReceiver(updateAdapter, new IntentFilter(Broadcast_UPDATE_ADAPTER));
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(showEqualizer, new IntentFilter(Broadcast_SHOW_EQUALIZER), RECEIVER_EXPORTED);
        }else {
            registerReceiver(showEqualizer, new IntentFilter(Broadcast_SHOW_EQUALIZER));
        }





        if (isTablet == 0 && (uiModeManager == null
                || uiModeManager.getCurrentModeType() != Configuration.UI_MODE_TYPE_TELEVISION)) {
            bottomSheetBehavior = BottomSheetBehavior.from(mPlayer);


            bottomSheetBehavior.addBottomSheetCallback(
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
                            mNameCurent.animate().alpha(alpha).setDuration(0);
                            if (alpha <= 0.0) {
                                mTopButtonsControls.setVisibility(View.GONE);
                                mProgressBar.setVisibility(View.INVISIBLE);
                            } else {
                                mTopButtonsControls.setVisibility(View.VISIBLE);
                                mProgressBar.setVisibility(View.VISIBLE);
                            }

                            if (slideOffset > Consts.COLLAPS_BUTTON_VISIBLE) {
                                if (mButtonCollapse != null
                                        && mButtonCollapse.getVisibility() != View.VISIBLE
                                        && mAudioAdapter.getSelectedItemsSize() == 0) {
                                    mButtonCollapse.setVisibility(View.VISIBLE);
                                }


                                if (mRadioAll != null && mRadioAll.getVisibility() != View.VISIBLE
                                        &&
                                        mAudioAdapter.getSelectedItemsSize() > 0) {
                                    mRadioAll.setVisibility(View.VISIBLE);

                                    if (mButtonCollapse != null) {
                                        mButtonCollapse.setVisibility(View.GONE);
                                    }

                                }

                                if (mDownland != null && mDownland.getVisibility() != View.VISIBLE &&
                                        mAudioAdapter.getSelectedItemsSize() > 0) {
                                    mDownland.setVisibility(View.VISIBLE);
                                    mDelete.setVisibility(View.VISIBLE);
                                }


                                double alphaCollapse = (slideOffset - Consts.COLLAPS_BUTTON_VISIBLE)
                                        / Consts.COLLAPS_BUTTON_VISIBLE_STEP;
                                if (mButtonCollapse != null) {
                                    mButtonCollapse.animate().alpha(
                                            (float) alphaCollapse).setDuration(
                                            0);
                                }

                                if (mRadioAll != null) {
                                    mRadioAll.animate().alpha(
                                            (float) alphaCollapse).setDuration(
                                            0);
                                }

                                if (mDownland != null) {
                                    mDownland.animate().alpha(
                                            (float) alphaCollapse).setDuration(
                                            0);
                                    mDelete.animate().alpha(
                                            (float) alphaCollapse).setDuration(
                                            0);
                                }

                                if (mAudioAdapter.getSelectedItemsSize() == 0) {
                                    if (mNameCurent.getText().toString().equals(
                                            String.valueOf(mAudioAdapter.getSelectedItemsSize()))) {
                                        Intent broadcastIntent = new Intent(Broadcast_SHOW_TITLE);
                                        broadcastSend(broadcastIntent);
                                    }
                                } else {
                                    if (!mNameCurent.getText().toString().equals(
                                            String.valueOf(mAudioAdapter.getSelectedItemsSize()))) {
                                        mNameCurent.setText(String.valueOf(
                                                mAudioAdapter.getSelectedItemsSize()));
                                    }
                                }

                                mNameCurent.animate().alpha((float) alphaCollapse).setDuration(0);
                            } else {
                                if (mButtonCollapse != null
                                        && mButtonCollapse.getVisibility() != View.INVISIBLE) {
                                    mButtonCollapse.setVisibility(View.INVISIBLE);
                                }

                                if (mRadioAll != null && mRadioAll.getVisibility() != View.GONE) {
                                    mRadioAll.setVisibility(View.GONE);
                                }

                                if (mDownland != null && mDownland.getVisibility() != View.GONE) {
                                    mDownland.setVisibility(View.GONE);
                                    mDelete.setVisibility(View.GONE);
                                }

                                if (mNameCurent.getText().toString().equals(
                                        String.valueOf(mAudioAdapter.getSelectedItemsSize()))) {
                                    Intent broadcastIntent = new Intent(Broadcast_SHOW_TITLE);
                                    broadcastSend(broadcastIntent);
                                }

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


        mRadioAll.setOnClickListener(view -> {
            mAudioAdapter.selectedItemsAddAll();
            if (mAudioAdapter.getSelectedItemsSize() > 0) {
                mRadioAll.setChecked(true);
            } else {
                mRadioAll.setChecked(false);
                mRadioAll.setVisibility(View.GONE);
                if (mButtonCollapse != null) {
                    mButtonCollapse.setVisibility(View.VISIBLE);
                }
            }
        });

        mDownland.setOnClickListener(view -> mPresenter.dowland(mAudioAdapter.getSelectedItems()));

        mDelete.setOnClickListener(view -> mPresenter.delete(mAudioAdapter.getSelectedItems()));

        mAudioAdapter = new AudioAdapter();
        mAudioAdapter.setListener((view, position) -> mPresenter.onItemSelected(view, position));
        mList.setAdapter(mAudioAdapter);
        mLinearLayoutManager = new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false);
        mList.setLayoutManager(mLinearLayoutManager);
        mAudioAdapter.setSelectedListner(() -> {
            if (mAudioAdapter.getSelectedItemsSize() == 0) {
                if (mRadioAll != null && mRadioAll.getVisibility() != View.GONE) {
                    mRadioAll.setVisibility(View.GONE);
                }

                if (mDownland != null && mDownland.getVisibility() != View.GONE) {
                    mDownland.setVisibility(View.GONE);
                    mDelete.setVisibility(View.GONE);
                }
                if (mButtonCollapse != null && mButtonCollapse.getVisibility() != View.VISIBLE) {
                    mButtonCollapse.setVisibility(View.VISIBLE);
                }

                Intent broadcastIntent = new Intent(Broadcast_SHOW_TITLE);
                broadcastSend(broadcastIntent);
            } else {

                mNameCurent.setText(String.valueOf(mAudioAdapter.getSelectedItemsSize()));

                if (mRadioAll != null) {
                    if (mRadioAll.getVisibility() != View.VISIBLE) {
                        mRadioAll.setVisibility(View.VISIBLE);
                    }
                    mRadioAll.setChecked(
                            mAudioAdapter.getItemCount() == mAudioAdapter.getSelectedItemsSize());
                    if (mBookPOJO.getUrl().contains(Url.SERVER_AKNIGA)) {
                        mRadioAll.setVisibility(View.GONE);
                    }
                }

                if (mDownland != null) {
                    if (mDownland.getVisibility() != View.VISIBLE) {
                        mDownland.setVisibility(View.VISIBLE);
                        mDelete.setVisibility(View.VISIBLE);
                    }
                }
                if (mButtonCollapse != null && mButtonCollapse.getVisibility() != View.GONE) {
                    mButtonCollapse.setVisibility(View.GONE);
                }
            }
        });


        mSeekBar.setMax(100);
        if (mProgressBar != null) {
            mProgressBar.setMax(100);
        }

        if (mSeekBar instanceof TvSeekBar) {
            // --- Логика для ТВ ---
            // Безопасно приводим тип и устанавливаем кастомный слушатель
            ((TvSeekBar) mSeekBar).setOnSeekBarChangedByUserListener(new TvSeekBar.OnSeekBarChangedByUserListener() {
                @Override
                public void onProgressChangedByUser(TvSeekBar seekBar, int progress, boolean fromUser) {
                    if (fromUser) {
                        // Этот код сработает ОДИН РАЗ, когда фокус уйдет с SeekBar на ТВ
                        mPresenter.seekChange(progress);
                    }
                }
            });
        } else {
            // --- Логика для Телефона ---
            // Если это обычный SeekBar, устанавливаем стандартный слушатель
            mSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    // Можно оставить пустым
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                    // Можно оставить пустым
                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                    // Этот код сработает, когда пользователь отпустит палец от SeekBar на телефоне
                    mPresenter.seekChange(seekBar.getProgress());
                }
            });
        }


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
        mSpeed.setOnClickListener(v -> mPresenter.buttonSpeedClick(v));
        getSystemService(AUDIO_SERVICE);




        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (bottomSheetBehavior != null
                        && bottomSheetBehavior.getState() == BottomSheetBehavior.STATE_EXPANDED) {
                    if (mAudioAdapter.getSelectedItemsSize() > 0) {
                        mAudioAdapter.clearSelected();
                    } else {
                        bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
                    }
                    return;
                }

                if (bottomSheetBehavior == null && mAudioAdapter.getSelectedItemsSize() > 0) {
                    mAudioAdapter.clearSelected();
                    return;
                }

                if (mNotificationClick) {
                    Intent intent = new Intent(BookActivity.this, MainActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    intent.putExtra("notificationClick", true);
                    startActivity(intent);
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                    setEnabled(true);
                }
            }
        });



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

        if (mRadioAll != null && mRadioAll.getVisibility() != View.GONE) {
            mRadioAll.setVisibility(View.GONE);
        }

        if (mDownland != null && mDownland.getVisibility() != View.GONE) {
            mDownland.setVisibility(View.GONE);
            mDelete.setVisibility(View.GONE);
        }

        Intent broadcastIntent = new Intent(Broadcast_SHOW_TITLE);
        broadcastSend(broadcastIntent);
    }

    @UnstableApi
    @Override
    public void stateExpanded() {
        if (mTopButtonsControls != null && mTopButtonsControls.getVisibility() != View.GONE) {
            mTopButtonsControls.setVisibility(View.GONE);
        }
        if (mProgressBar != null && mProgressBar.getVisibility() != View.INVISIBLE) {
            mProgressBar.setVisibility(
                    View.INVISIBLE);
        }
        if (mButtonCollapse != null && mButtonCollapse.getVisibility() != View.VISIBLE
                && mAudioAdapter.getSelectedItemsSize() == 0) {
            mButtonCollapse.setVisibility(View.VISIBLE);
        }

        if (mRadioAll != null && mRadioAll.getVisibility() != View.VISIBLE
                && mAudioAdapter.getSelectedItemsSize() > 0) {
            mRadioAll.setVisibility(View.VISIBLE);
            if (mButtonCollapse != null) {
                mButtonCollapse.setVisibility(View.GONE);
            }
        }

        if (mDownland != null && mDownland.getVisibility() != View.VISIBLE
                && mAudioAdapter.getSelectedItemsSize() > 0) {
            mDownland.setVisibility(View.VISIBLE);
            mDelete.setVisibility(View.VISIBLE);
        }

        if (mAudioAdapter.getSelectedItemsSize() > 0) {
            mNameCurent.setText(String.valueOf(mAudioAdapter.getSelectedItemsSize()));
        } else {
            Intent broadcastIntent = new Intent(Broadcast_SHOW_TITLE);
            broadcastSend(broadcastIntent);
        }

        int selected = mAudioAdapter.getIndexSelected();
        boolean b = pref.getBoolean("scrollToSelectedPref", false);
        if (selected != -1 && b) {
            mLinearLayoutManager.scrollToPositionWithOffset(selected, 0);
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

    @Override
    public void showOtherSource() {
        sectionsPagerAdapter.setArtistPOJO(mPresenter.getOtherArtistPOJOS());
        showPage(getResources().getString(R.string.tab_text_5));
    }

    public void showOtherArtist() {
        showPage(getResources().getString(R.string.tab_text_4));
    }

    private void showPage(String name) {
        boolean temp = false;
        boolean sours = false;
        String otherSours = getString(R.string.tab_text_5);
        for (int i = 0; i < tabs.getTabCount(); i++) {
            String title = Objects.requireNonNull(
                    Objects.requireNonNull(tabs.getTabAt(i)).getText()).toString();
            if (title.equals(name)) {
                temp = true;
            }
            if (title.equals(otherSours) && !otherSours.equals(name)) {
                sours = true;
            }
        }
        if (!temp) {
            if (!sours) {
                tabs.addTab(tabs.newTab().setText(name));
                sectionsPagerAdapter.addTabPage(name);
            } else {
                tabs.addTab(tabs.newTab().setText(name), sectionsPagerAdapter.getItemCount() - 1);
                sectionsPagerAdapter.addTabPage(name, sectionsPagerAdapter.getItemCount() - 1);
            }
        }
    }

    @UnstableApi
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
        } else if (menuItem.getItemId() == R.id.download_delete) {
            if (bottomSheetBehavior != null &&
                    bottomSheetBehavior.getState() != BottomSheetBehavior.STATE_EXPANDED) {
                bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
            }
            mAudioAdapter.selectedItemsAddAll();
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
        unregisterReceiver(showRating);
        unregisterReceiver(updateAdapter);
        unregisterReceiver(showEqualizer);
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
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
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
        Intent sendIntent = new Intent();
        sendIntent.setAction(Intent.ACTION_SEND);
        sendIntent.putExtra(Intent.EXTRA_TEXT, mBookPOJO.getUrl());
        sendIntent.setType("text/plain");
        Intent shareIntent = Intent.createChooser(sendIntent, null);
        startActivity(shareIntent);
    }


    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    public void addToMainScreen(BookPOJO pojo) {
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
                                pinnedShortcutCallbackIntent, PendingIntent.FLAG_IMMUTABLE);
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
    }


    @Override
    public void showToast(int id) {
        Toast.makeText(this, id, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void showToast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    @UnstableApi
    @Override
    public void updateAdapter() {
        if (mAudioAdapter != null) {
            mAudioAdapter.refresh();
        }
    }

    @Override
    public void startMainActivity(int fragmentId) {
        MainActivity.startMainActivity(this, fragmentId, "settings");
    }


    public void showRatingDialog() {
        ReviewManager manager = ReviewManagerFactory.create(this);
        Task<ReviewInfo> request = manager.requestReviewFlow();
        request.addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                // We can get the ReviewInfo object
                new StorageUtil(this).storeShowRating(false);
                // Создаем намерение для открытия ссылки
                Intent browserIntent = new Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://4pda.to/forum/index.php?showtopic=978445"));
                Intent chooser = Intent.createChooser(browserIntent, "Открыть с помощью...");
                startActivity(chooser);
            } else {
                new StorageUtil(this).storeCountAudioListneredForRating(
                        countAudioWereShowingRatingPopUp - 1);
            }
        });
    }

    @Override
    public void showProgres(boolean b) {

    }

    @Override
    public void decode(@NotNull @NonNull final String key) {
        JsEvaluator jsEvaluator = new JsEvaluator(this);
        jsEvaluator.callFunction(Consts.decodeScript,
                new JsCallback() {
                    @Override
                    public void onError(String errorMessage) {
                        showToast(R.string.error_decode);
                    }

                    @Override
                    public void onResult(String result) {
                        mPresenter.addDecodeData(result);
                    }
                }, "strDecode", key);
    }

    @Override
    public void showTitle(@NotNull @NonNull String name) {
        mNameCurent.setText(name);
    }

    @UnstableApi
    @Override
    public void showData(@NonNull ArrayList<AudioPOJO> data, @NonNull String bookUrl) {
        if (mAudioAdapter != null) {
            mAudioAdapter.setData(data);
        }
    }

    @Override
    public void updateTime(int timeCurent, int timeEnd, int buffered) {
        if (mProgressBar != null) {
            mProgressBar.setMax(timeEnd);
            mProgressBar.setProgress(timeCurent);
            mProgressBar.setSecondaryProgress(buffered);
        }
        mSeekBar.setMax(timeEnd);
        mSeekBar.setProgress(timeCurent);
        mSeekBar.setSecondaryProgress(buffered);

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

    @UnstableApi
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
    public void broadcastSend(@NotNull Intent intent) {
        sendBroadcast(intent);
    }

    @Override
    public void activityStart(@NotNull Intent intent) {
        startActivity(intent);
    }

    @Override
    public void myUnbindService(@NotNull ServiceConnection serviceConnection) {
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
        } else if (themeName.equals(getString(R.string.theme_black_value))) {
            theme.applyStyle(R.style.AppThemeBlack_NoActionBar, true);
        }


        return theme;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        View currentFocus = getCurrentFocus();
        if (currentFocus!=null){
            if(keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                // --- НАЧАЛО ИСПРАВЛЕНИЯ ---
                // Проверяем, является ли текущий сфокусированный View дочерним элементом нашего TabLayout
                if (isTabView(currentFocus)) {
                    // Находим ViewPager2
                    ViewPager2 viewPager = findViewById(R.id.view_pager);
                    if (viewPager != null) {
                        // Получаем текущую позицию ViewPager2
                        int currentItem = viewPager.getCurrentItem();
                        // Получаем адаптер ViewPager2
                        RecyclerView.Adapter adapter = viewPager.getAdapter();

                        // Проверяем, что адаптер - это наш SectionsPagerAdapter
                        if (adapter instanceof SectionsPagerAdapter) {
                            // Получаем ID фрагмента по его позиции
                            long fragmentId = adapter.getItemId(currentItem);
                            // Находим сам фрагмент по его ID
                            Fragment currentFragment = getSupportFragmentManager().findFragmentByTag("f" + fragmentId);

                            // Если фрагмент найден, передаем фокус его корневому View
                            if (currentFragment != null && currentFragment.getView() != null) {
                                currentFragment.getView().requestFocus();
                            }
                        }
                    }
                    // Возвращаем true, т.к. мы обработали событие
                    return true;
                }
            }else if(keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                if (currentFocus.getId()==R.id.genre||currentFocus.getId()==R.id.autorConteiner||currentFocus.getId()==R.id.author||
                        currentFocus.getId()==R.id.artistConteiner||currentFocus.getId()==R.id.artist||currentFocus.getId()==R.id.seriesConteiner||
                        currentFocus.getId()==R.id.series||currentFocus.getId()==R.id.showMore) {
                    View playerView = findViewById(R.id.player);
                    if (playerView != null) {
                        playerView.requestFocus();
                    }
                }
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    private boolean isTabView(View view) {
        if (view == null || tabs == null) return false;
        // Каждая вкладка (TabView) является дочерним элементом контейнера внутри TabLayout.
        // Проверяем, совпадает ли родительский View нашего focusedView с контейнером вкладок.
        ViewParent parent = view.getParent();
        // TabLayout содержит дочерний SlidingTabIndicator, который в свою очередь содержит TabView
        return parent != null && parent.getParent() == tabs;
    }

    @UnstableApi
    @Override
    protected void onResume() {
        super.onResume();
        showingView = mBookPOJO.getUrl();
        View decorView = getWindow().getDecorView();
        int uiOptions = View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
        decorView.setSystemUiVisibility(uiOptions);
        mAudioAdapter.refresh();

        if (bottomSheetBehavior != null) {
            bottomSheetBehavior.setState(mPresenter.getState());
        } else {
            if (mAudioAdapter.getSelectedItemsSize() > 0) {
                if (mRadioAll.getVisibility() != View.VISIBLE) {
                    mRadioAll.setVisibility(View.VISIBLE);
                }
            } else {
                if (mRadioAll.getVisibility() != View.GONE) {
                    mRadioAll.setVisibility(View.GONE);
                }
            }
        }
    }


    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            if ((!isServiceRunning(getApplicationContext(), MediaPlayerService.class))
                    && (event.getKeyCode() == KEYCODE_MEDIA_PLAY
                    || event.getKeyCode() == KEYCODE_MEDIA_PLAY_PAUSE)) {
                mPresenter.buttomPlayClick(mPlayTop);
                return true;
            } else if (handleUserInput(getApplicationContext(), event.getKeyCode())) {
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }


}
