package com.fanok.audiobooks.activity;

import static android.view.KeyEvent.KEYCODE_MEDIA_PLAY;
import static android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE;
import static com.fanok.audiobooks.Consts.handleUserInput;
import static com.fanok.audiobooks.Consts.isServiceRunning;
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
import android.os.SystemClock;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.util.Log;
import android.util.Xml;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatSeekBar;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Lifecycle;
import androidx.media3.common.util.UnstableApi;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.alimuzaffar.lib.pin.PinEntryEditText;
import com.arellomobile.mvp.MvpAppCompatActivity;
import com.arellomobile.mvp.presenter.InjectPresenter;
import com.arellomobile.mvp.presenter.ProvidePresenter;
import com.fanok.audiobooks.Consts;
import com.fanok.audiobooks.R;
import com.fanok.audiobooks.TvSeekBar;
import com.fanok.audiobooks.Url;
import com.fanok.audiobooks.adapter.AudioAdapter;
import com.fanok.audiobooks.download.DownloadStatus;
import com.fanok.audiobooks.download.DownloadStatusText;
import com.fanok.audiobooks.adapter.SectionsPagerAdapter;
import com.fanok.audiobooks.interface_pacatge.book_content.Activity;
import com.fanok.audiobooks.pojo.AudioPOJO;
import com.fanok.audiobooks.pojo.BookPOJO;
import com.fanok.audiobooks.pojo.OtherArtistPOJO;
import com.fanok.audiobooks.pojo.StorageUtil;
import com.fanok.audiobooks.policy.ParentalControl;
import com.fanok.audiobooks.presenter.BookPresenter;
import com.fanok.audiobooks.service.MediaPlayerService;
import com.fanok.audiobooks.service.SeekStep;
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
    private TextView mRadioAll;
    private ImageButton mSelectionClose;
    /** The top row shows how many chapters are chosen, not the playing chapter (bindSelectionBar). */
    private boolean mShowingCount;
    private ImageButton mDelete;
    private ImageButton mDownland;

    public static String getShowingView() {
        return showingView;
    }

    private static final String ARG_BOOK = "arg_book";
    /** Far beyond the system long press (~500 ms), so a slow tap never switches books by accident. */
    private static final long SWITCH_BOOK_HOLD_MS = 2500;
    private static final String PREF_SEEK_BUTTONS = "seekButtonsPref";
    private static final String PREF_SAFE_NAVIGATION = "safeNavigationPref";
    /**
     * Holding rewind/forward scrubs at {@code START_SPEED} times real time, doubling every {@code DOUBLING_MS} up to
     * {@code MAX_SPEED}; a chapter of many hours allows up to {@code MAX_SPEED_LONG}, crossing it in about
     * {@code CROSSING_MS}. The scrub stops {@code END_MARGIN_MS} short of the end, so a release there does not end
     * the chapter.
     */
    private static final double SEEK_HOLD_START_SPEED = 10;
    private static final double SEEK_HOLD_DOUBLING_MS = 1500;
    private static final double SEEK_HOLD_MAX_SPEED = 300;
    private static final double SEEK_HOLD_MAX_SPEED_LONG = 600;
    private static final double SEEK_HOLD_CROSSING_MS = 20_000;
    private static final int SEEK_HOLD_END_MARGIN_MS = 1000;
    /** After a scrub's seek, progress this far from its target and this soon is a tick sent before the seek. */
    private static final long SEEK_COMMIT_SETTLE_MS = 1000;
    private static final int SEEK_COMMIT_TOLERANCE_MS = 1500;
    /** Menu items that can be toolbar icons, in toolbar order, and the setting of each; the favourite pair shares one. */
    private static final int[] TOOLBAR_ITEMS = {R.id.addFavorite, R.id.share, R.id.download_delete, R.id.sleep_timer,
            R.id.equalizer, R.id.settings, R.id.openSite, R.id.addMainScreen, R.id.refresh};
    private static final String[] TOOLBAR_KEYS = {"toolbar_favorite", "toolbar_share", "toolbar_download",
            "toolbar_sleep_timer", "toolbar_equalizer", "toolbar_settings", "toolbar_open_site", "toolbar_main_screen",
            "toolbar_refresh"};
    /** Widths in the book toolbar: the back arrow, an action icon (styles.xml Widget.App.ActionButton), the overflow. */
    private static final int TOOLBAR_NAV_DP = 56;
    private static final int TOOLBAR_ICON_DP = 32;
    private static final int TOOLBAR_OVERFLOW_DP = 40;
    /** Below this width left by the icons the title and author are not shown at all. */
    private static final int TOOLBAR_TITLE_MIN_DP = 120;
    /** Set once the hint on holding play and the seek buttons was shown; it is shown only once. */
    private static final String PREF_HINT_SWITCH_BOOK = "hint_switch_book_shown";
    /** The toolbar's height from the layout, for when title and author are shown. */
    private int mToolbarHeight;
    private boolean mSwitchHintShowing;
    @InjectPresenter
    BookPresenter mPresenter;
    private ImageButton mSpeed;
    private RecyclerView mList;
    private TextView mNameCurent;
    private ProgressBar mProgressBar;
    private View mDownloadStatus;
    private TextView mDownloadStatusText;
    private ProgressBar mDownloadStatusBar;
    /** The book's download status as the chapter list last computed it; drives the status row and the menu title. */
    private DownloadStatus mLastDownloadStatus;
    private ImageButton mPreviousTop;
    private ImageButton mPlayTop;
    private ImageButton mNextTop;
    /** The collapsed player's buttons beside play rewind/forward instead of changing chapters ("Кнопки плеера"). */
    private boolean mTopSeek;
    /** The rewind/forward button being held: it shows its own position meanwhile. */
    @Nullable
    private SeekHold mActiveSeekHold;
    /** The position the last hold sought to, and when. */
    private int mSeekCommitTarget;
    private long mSeekCommitAt;
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
    /** The comments fetch that decides whether their tab shows. */
    private io.reactivex.disposables.Disposable comentsCount;
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


    /** The player's audio session for an equalizer dialog that arrived while this screen was not resumed, or 0. */
    private int pendingEqualizerSession;

    private final BroadcastReceiver showEqualizer = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int sessionId = intent.getIntExtra("id", 0);
            if (sessionId <= 0) {
                return;
            }
            if (getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.RESUMED)) {
                showEqualizer(sessionId);
            } else {
                //a dialog cannot be shown now: onResume shows it
                pendingEqualizerSession = sessionId;
            }
        }
    };

    private void showEqualizer(int sessionId) {
        try {
            DialogEqualizerFragment fragment = DialogEqualizerFragment.newBuilder()
                    .setAudioSessionId(sessionId)
                    .themeColor(ContextCompat.getColor(this, R.color.primaryColorEq))
                    .textColor(ContextCompat.getColor(this, R.color.textColor))
                    .setAccentColor(ContextCompat.getColor(this, R.color.secondaryColor))
                    .build();

            fragment.show(getSupportFragmentManager(), "eq");
        } catch (IllegalStateException ignored) {
        }
    }


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
        //one policy for every way in; a "notificationClick" extra is never a reason to skip it
        if (!ParentalControl.isAllowed(context, bookPOJO)) {

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
                b.setOnClickListener(view -> openWithPin(context, editText, dialog, intent));
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

            editText.setOnPinEnteredListener(str -> openWithPin(context, editText, dialog, intent));

            dialog.show();

        } else {
            context.startActivity(intent);
        }
    }

    /** Opens the book when the PIN is right; otherwise says why not (wrong, or locked for a while). */
    private static void openWithPin(@NonNull Context context, @NonNull PinEntryEditText editText,
            @NonNull AlertDialog dialog, @NonNull Intent intent) {
        ParentalControl.Verdict verdict = ParentalControl.verifyPin(context,
                Objects.requireNonNull(editText.getText()).toString());
        if (verdict.result == ParentalControl.Result.OK) {
            dialog.cancel();
            context.startActivity(intent);
        } else {
            Toast.makeText(context, ParentalControlActivity.pinMessage(context, verdict), Toast.LENGTH_SHORT).show();
            editText.getText().clear();
        }
    }

    /**
     * The player notification's content intent: the book screen from the saved book itself, no page fetch. Single
     * top, so tapping it while this book's screen is on top only brings it forward.
     */
    @NonNull
    public static Intent notificationIntent(@NonNull Context context, @NonNull BookPOJO bookPOJO) {
        Intent intent = new Intent(context, BookActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        intent.putExtra("notificationClick", true);
        intent.putExtra(ARG_BOOK, new GsonBuilder().serializeNulls().create().toJson(bookPOJO));
        return intent;
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

    @UnstableApi
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
        UiModeManager uiModeManager = (UiModeManager) getApplicationContext().getSystemService(UI_MODE_SERVICE);
        setContentView(R.layout.activity_book);
        mButtonCollapse = findViewById(R.id.buttonCollapse);
        mTopButtonsControls = findViewById(R.id.topButtonsControls);
        mPlayer = findViewById(R.id.player);
        mRadioAll = findViewById(R.id.radioAll);
        mSelectionClose = findViewById(R.id.selectionClose);
        mDelete = findViewById(R.id.delete);
        mDownland = findViewById(R.id.dowland);
        mSpeed = findViewById(R.id.speed);
        mList = findViewById(R.id.list);
        mNameCurent = findViewById(R.id.name_curent);
        mProgressBar = findViewById(R.id.progressBar);
        mDownloadStatus = findViewById(R.id.downloadStatus);
        mDownloadStatusText = findViewById(R.id.downloadStatusText);
        mDownloadStatusBar = findViewById(R.id.downloadStatusBar);
        if (mDownloadStatus != null) {
            mDownloadStatus.setOnClickListener(view -> ClearSavedActivity.startActivity(this));
        }
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
        // the comments tab shows only when there are comments; its list reuses this fetch
        comentsCount = com.fanok.audiobooks.model.ComentsModel.shared(mBookPOJO.getUrl(), savedInstanceState == null)
                .observeOn(io.reactivex.android.schedulers.AndroidSchedulers.mainThread())
                .subscribe(comments -> {
                    if (!comments.isEmpty()) {
                        sectionsPagerAdapter.showComents(comments.size());
                    }
                }, e -> Log.d(TAG, "no comments tab: " + e));


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
                                if (mPlayer != null && mPlayer.getParent() instanceof View) {
                                    View parent = (View) mPlayer.getParent();
                                    parent.bringToFront();
                                    androidx.core.view.ViewCompat.setZ(parent, 32f);
                                }
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
                                bindSelectionBar(true);
                                if (mButtonCollapse != null) {
                                    mButtonCollapse.setRotation(0);
                                    mButtonCollapse.setContentDescription(getString(R.string.collapse));
                                }
                                float alphaCollapse = (float) ((slideOffset - Consts.COLLAPS_BUTTON_VISIBLE)
                                        / Consts.COLLAPS_BUTTON_VISIBLE_STEP);
                                for (View view : new View[] {mButtonCollapse, mSelectionClose, mRadioAll, mDownland,
                                        mDelete, mNameCurent}) {
                                    if (view != null) {
                                        view.animate().alpha(alphaCollapse).setDuration(0);
                                    }
                                }
                            } else {
                                showExpandHint(Math.max(0, alpha));
                                bindSelectionBar(false);
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
                } else if (BottomSheetBehavior.STATE_COLLAPSED == bottomSheetBehavior.getState()) {
                    bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
                }
            });
            showExpandHint(1);
        }


        // the chapter selection: all <-> none, close, and the actions, which end it
        mRadioAll.setOnClickListener(view -> mAudioAdapter.setAllSelected(!mAudioAdapter.isAllSelected()));
        mSelectionClose.setOnClickListener(view -> mAudioAdapter.endSelection());
        mDownland.setOnClickListener(view -> {
            if (mAudioAdapter.getSelectedItemsSize() > 0) {
                mPresenter.dowland(mAudioAdapter.getSelectedItems());
                mAudioAdapter.endSelection();
            }
        });

        mDelete.setOnClickListener(view -> {
            if (mAudioAdapter != null && mAudioAdapter.getSelectedItemsSize() > 0) {
                final java.util.HashSet<String> chosen = mAudioAdapter.getSelectedItems();
                new AlertDialog.Builder(this)
                        .setTitle("Удаление загрузок")
                        .setMessage("Вы уверены, что хотите удалить выбранные файлы (" + chosen.size() + ")?")
                        .setPositiveButton("ОК", (dialog, which) -> {
                            mPresenter.delete(chosen);
                            mAudioAdapter.endSelection();
                        })
                        .setNegativeButton("Отмена", null)
                        .show();
            }
        });

        mAudioAdapter = new AudioAdapter();
        mAudioAdapter.setBookUrl(mBookPOJO.getUrl());
        mAudioAdapter.setListener((view, position) -> mPresenter.onItemSelected(view, position));
        mAudioAdapter.setStatusListener(this::showDownloadStatus);
        mList.setAdapter(mAudioAdapter);
        mLinearLayoutManager = new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false);
        mList.setLayoutManager(mLinearLayoutManager);
        mAudioAdapter.setSelectedListner(() -> bindSelectionBar(bottomSheetBehavior == null
                || bottomSheetBehavior.getState() != BottomSheetBehavior.STATE_COLLAPSED));


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
                    // the drag wins over a button held meanwhile
                    if (mActiveSeekHold != null) {
                        mActiveSeekHold.finish(false);
                    }
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
            new SwitchBookHold().attach(mPlayTop);
        }
        //icons and descriptions of these two follow the setting in onResume
        if (mPreviousTop != null) {
            mPreviousTop.setOnClickListener(view -> {
                if (mTopSeek) {
                    mPresenter.buttomRewindClick(view);
                } else {
                    jumpChapter(view, false);
                }
            });
            new SeekHold(mPreviousTop, false, true);
        }
        if (mNextTop != null) {
            mNextTop.setOnClickListener(view -> {
                if (mTopSeek) {
                    mPresenter.buttomForwardClick(view);
                } else {
                    jumpChapter(view, true);
                }
            });
            new SeekHold(mNextTop, true, true);
        }
        mPlayBottom.setOnClickListener(view -> mPresenter.buttomPlayClick(view));
        new SwitchBookHold().attach(mPlayBottom);
        mPreviousBottom.setOnClickListener(view -> jumpChapter(view, false));
        mNextBottom.setOnClickListener(view -> jumpChapter(view, true));
        mRewind.setOnClickListener(view -> mPresenter.buttomRewindClick(view));
        mForward.setOnClickListener(view -> mPresenter.buttomForwardClick(view));
        new SeekHold(mRewind, false, false);
        new SeekHold(mForward, true, false);
        mSpeed.setOnClickListener(v -> mPresenter.buttonSpeedClick(v));
        getSystemService(AUDIO_SERVICE);




        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (mAudioAdapter.isSelecting()) {
                    mAudioAdapter.endSelection();
                    return;
                }
                if (bottomSheetBehavior != null
                        && bottomSheetBehavior.getState() == BottomSheetBehavior.STATE_EXPANDED) {
                    bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
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
        if (bottomSheetBehavior != null) {
            showExpandHint(1);
        } else if (mButtonCollapse != null && mButtonCollapse.getVisibility() != View.INVISIBLE) {
            mButtonCollapse.setVisibility(View.INVISIBLE);
        }

        mAudioAdapter.endSelection();
        bindSelectionBar(false);
        broadcastSend(new Intent(Broadcast_SHOW_TITLE));
    }

    @UnstableApi
    @Override
    public void stateExpanded() {
        if (mButtonCollapse != null) {
            mButtonCollapse.setRotation(0);
            mButtonCollapse.setContentDescription(getString(R.string.collapse));
        }
        if (mTopButtonsControls != null && mTopButtonsControls.getVisibility() != View.GONE) {
            mTopButtonsControls.setVisibility(View.GONE);
        }
        if (mProgressBar != null && mProgressBar.getVisibility() != View.INVISIBLE) {
            mProgressBar.setVisibility(
                    View.INVISIBLE);
        }
        if (mButtonCollapse != null && mButtonCollapse.getVisibility() != View.VISIBLE
                && !mAudioAdapter.isSelecting()) {
            mButtonCollapse.setVisibility(View.VISIBLE);
        }
        bindSelectionBar(true);

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
        sectionsPagerAdapter.showTab(SectionsPagerAdapter.SERIES);
    }

    @Override
    public void showOtherSource() {
        sectionsPagerAdapter.setArtistPOJO(mPresenter.getOtherArtistPOJOS());
        sectionsPagerAdapter.showTab(SectionsPagerAdapter.OTHER_SOURCE);
    }

    /** The description says other narrations exist: the presenter loads them and shows the tab only if any. */
    public void loadOtherArtist() {
        mPresenter.loadOtherArtists();
    }

    /** The description already found the other narrations: shown through the presenter's view state, not searched again. */
    public void otherArtistsFound(@NonNull ArrayList<OtherArtistPOJO> artists) {
        mPresenter.otherArtistsLoaded(artists);
    }

    @Override
    public void showOtherArtist(@NonNull ArrayList<OtherArtistPOJO> artists) {
        sectionsPagerAdapter.setOtherArtists(artists);
        sectionsPagerAdapter.showTab(SectionsPagerAdapter.OTHER_ARTIST);
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
            mAudioAdapter.startSelection();
        }

        mPresenter.onOptionsMenuItemSelected(menuItem);
        return (super.onOptionsItemSelected(menuItem));
    }

    /** The player UI listens only while visible; on the way back the service resends chapter, icon and progress. */
    @Override
    protected void onStart() {
        super.onStart();
        registerInternalReceiver(setImage, Broadcast_SET_IMAGE);
        registerInternalReceiver(setProgress, Broadcast_SET_PROGRESS);
        registerInternalReceiver(setSelectionBroadcast, Broadcast_SET_SELECTION);
        registerInternalReceiver(setTitleBroadcast, Broadcast_SET_TITLE);
        registerInternalReceiver(showRating, Broadcast_SHOW_RATING);
        registerInternalReceiver(updateAdapter, Broadcast_UPDATE_ADAPTER);
        registerInternalReceiver(showEqualizer, Broadcast_SHOW_EQUALIZER);
        broadcastSend(new Intent(Broadcast_SHOW_TITLE));
        if (mAudioAdapter != null) mAudioAdapter.startLive();
    }

    @Override
    protected void onStop() {
        if (mAudioAdapter != null) mAudioAdapter.stopLive();
        unregisterReceiver(setImage);
        unregisterReceiver(setProgress);
        unregisterReceiver(setSelectionBroadcast);
        unregisterReceiver(setTitleBroadcast);
        unregisterReceiver(showRating);
        unregisterReceiver(updateAdapter);
        unregisterReceiver(showEqualizer);
        mPresenter.dismissSpeedDialog();
        mPresenter.onStop();
        super.onStop();
    }

    /** App-internal actions are never exported: other apps must not be able to feed or read the player UI. */
    private void registerInternalReceiver(BroadcastReceiver receiver, String action) {
        ContextCompat.registerReceiver(this, receiver, new IntentFilter(action),
                ContextCompat.RECEIVER_NOT_EXPORTED);
    }

    @Override
    protected void onDestroy() {
        showingView = "";
        comentsCount.dispose();
        if (isFinishing()) {
            com.fanok.audiobooks.model.ComentsModel.cancel(mBookPOJO.getUrl());
        }
        //Moxy destroys the presenter (and its player binding) when this screen is finished for good
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
        layoutToolbar(menu);

        return true;
    }

    /**
     * Makes the actions chosen in the settings toolbar icons, as many as fit beside the back arrow; the others stay
     * in the overflow menu, which disappears when it is empty. Title and author take the width left, and are not
     * shown at all when that is too little to read (or when they are not wanted).
     */
    private void layoutToolbar(@NonNull Menu menu) {
        int wanted = 0;
        boolean others = false;
        for (int i = 0; i < TOOLBAR_ITEMS.length; i++) {
            MenuItem item = menu.findItem(TOOLBAR_ITEMS[i]);
            //one of the favourite pair is visible, whichever it is
            if (item == null || !item.isVisible() && TOOLBAR_ITEMS[i] != R.id.addFavorite) {
                continue;
            }
            if (onToolbar(TOOLBAR_KEYS[i])) {
                wanted++;
            } else {
                others = true;
            }
        }
        int room = getResources().getConfiguration().screenWidthDp - TOOLBAR_NAV_DP;
        int shown = Math.min(wanted, room / TOOLBAR_ICON_DP);
        if (shown < wanted || others) {
            room -= TOOLBAR_OVERFLOW_DP;
            shown = Math.min(wanted, room / TOOLBAR_ICON_DP);
        }
        room -= shown * TOOLBAR_ICON_DP;
        for (int i = 0; i < TOOLBAR_ITEMS.length; i++) {
            MenuItem item = menu.findItem(TOOLBAR_ITEMS[i]);
            if (item == null || !item.isVisible() && TOOLBAR_ITEMS[i] != R.id.addFavorite) {
                continue;
            }
            boolean icon = shown > 0 && onToolbar(TOOLBAR_KEYS[i]);
            if (icon) {
                shown--;
            }
            int action = icon ? MenuItem.SHOW_AS_ACTION_ALWAYS : MenuItem.SHOW_AS_ACTION_NEVER;
            item.setShowAsAction(action);
            if (TOOLBAR_ITEMS[i] == R.id.addFavorite) {
                menu.findItem(R.id.removeFavorite).setShowAsAction(action);
            }
        }
        boolean title = pref.getBoolean("toolbar_title", true) && room >= TOOLBAR_TITLE_MIN_DP;
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayShowTitleEnabled(title);
        }
        // without title and author the bar is only as tall as its icons, not an empty band above the tabs
        Toolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) {
            ViewGroup.LayoutParams params = toolbar.getLayoutParams();
            if (mToolbarHeight == 0) {
                mToolbarHeight = params.height;
            }
            int height = title ? mToolbarHeight : getResources().getDimensionPixelSize(R.dimen.book_toolbar_compact);
            if (params.height != height) {
                params.height = height;
                toolbar.setLayoutParams(params);
            }
        }
    }

    private boolean onToolbar(@NonNull String key) {
        return pref.getBoolean(key, "toolbar_favorite".equals(key) || "toolbar_share".equals(key));
    }

    /** In the collapsed player the collapse button, turned upwards, shows that the chapter list opens. */
    private void showExpandHint(float alpha) {
        if (mButtonCollapse == null) {
            return;
        }
        mButtonCollapse.setRotation(180);
        mButtonCollapse.setContentDescription(getString(R.string.expand));
        mButtonCollapse.animate().alpha(alpha).setDuration(0);
        mButtonCollapse.setVisibility(View.VISIBLE);
    }

    /**
     * The previous or next chapter. With "Безопасная навигация" on (the default) only after a confirmation: a
     * stray tap would lose the place in what may be a chapter of hours.
     */
    private void jumpChapter(@NonNull View view, boolean next) {
        Runnable jump = () -> {
            if (next) {
                mPresenter.buttomNextClick(view);
            } else {
                mPresenter.buttomPreviousClick(view);
            }
        };
        if (!pref.getBoolean(PREF_SAFE_NAVIGATION, true)) {
            jump.run();
            return;
        }
        new AlertDialog.Builder(this)
                .setMessage(next ? R.string.confirm_next_chapter : R.string.confirm_previous_chapter)
                .setPositiveButton(R.string.go_to, (dialog, which) -> jump.run())
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /** The collapsed player's buttons beside play: chapters, or rewind/forward as "Кнопки плеера" asks. */
    private void bindTopSideButtons() {
        mTopSeek = pref.getBoolean(PREF_SEEK_BUTTONS, false);
        if (mPreviousTop != null) {
            mPreviousTop.setImageResource(mTopSeek ? R.drawable.ic_fast_rewind : R.drawable.ic_skip_previous);
            mPreviousTop.setContentDescription(getString(mTopSeek ? R.string.rewind_back : R.string.previous));
        }
        if (mNextTop != null) {
            mNextTop.setImageResource(mTopSeek ? R.drawable.ic_fast_forward : R.drawable.ic_skip_next);
            mNextTop.setContentDescription(getString(mTopSeek ? R.string.fast_forward : R.string.next));
        }
        //a tap seeks by the step set in the settings, and the icons show it
        mRewind.setImageResource(SeekStep.rewindIcon(this));
        mRewind.setContentDescription(SeekStep.rewindLabel(this));
        mForward.setImageResource(SeekStep.forwardIcon(this));
        mForward.setContentDescription(SeekStep.forwardLabel(this));
    }

    @Override
    public void setTabPostion(String title) {
        TabLayout.Tab tab = tabs.getTabAt(sectionsPagerAdapter.positionOf(title));
        if (tab != null) {
            tab.select();
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

    @Override
    public void showLoadError(String message, String report) {
        if (!PreferenceManager.getDefaultSharedPreferences(this).getBoolean("pref_error_reports", true)) {
            showToast(message);
            return;
        }
        String full = message + "\n\n" + report;
        TextView text = new TextView(this);
        text.setText(full);
        text.setTextIsSelectable(true);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        text.setPadding(pad, pad / 2, pad, 0);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(text);
        new AlertDialog.Builder(this)
                .setTitle(R.string.error_report_title)
                .setView(scroll)
                .setPositiveButton(R.string.copy, (d, which) -> {
                    android.content.ClipboardManager clipboard =
                            (android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    if (clipboard != null) {
                        clipboard.setPrimaryClip(android.content.ClipData.newPlainText(
                                getString(R.string.error_report_title), full));
                        showToast(R.string.copied);
                    }
                })
                .setNegativeButton(R.string.close, null)
                .show();
    }

    @Override
    public void showLongToast(int id) {
        Toast.makeText(this, id, Toast.LENGTH_LONG).show();
    }

    @UnstableApi
    @Override
    public void updateAdapter() {
        if (mAudioAdapter != null) {
            mAudioAdapter.refresh();
        }
    }

    /**
     * The row above the chapter list: what the book's download is doing (state, chapters, bytes, speed, time left),
     * hidden while nothing is selected for download. Tapping it opens the Downloads screen.
     */
    private void showDownloadStatus(@NonNull DownloadStatus status) {
        mLastDownloadStatus = status;
        if (mDownloadStatus == null) return;
        String line = DownloadStatusText.line(this, status);
        if (line.isEmpty()) {
            mDownloadStatus.setVisibility(View.GONE);
            return;
        }
        mDownloadStatusText.setText(line);
        mDownloadStatusBar.setProgress(status.percent());
        mDownloadStatusBar.setVisibility(status.state == DownloadStatus.COMPLETE ? View.GONE : View.VISIBLE);
        mDownloadStatus.setVisibility(View.VISIBLE);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem download = menu.findItem(R.id.download_delete);
        if (download != null) {
            String brief = mLastDownloadStatus == null ? null : DownloadStatusText.brief(this, mLastDownloadStatus);
            download.setTitle(brief == null ? getString(R.string.dowland_delete)
                    : getString(R.string.download_menu_status, brief));
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public void startMainActivity(int fragmentId) {
        MainActivity.startMainActivity(this, fragmentId, "settings");
    }


    public void showRatingDialog() {
        new StorageUtil(this).storeShowRating(false);
    }

    @Override
    public void showProgres(boolean b) {

    }

    @Override
    public void showTitle(@NotNull @NonNull String name) {
        if (!mShowingCount) {
            mNameCurent.setText(name);
        }
    }

    /**
     * The top row of the chapter list while chapters are chosen for download/delete (the Download/Delete menu or a
     * long press): close, all/some/none chosen, "N из M" and download and delete stand in place of the collapse
     * button and the playing chapter. Choosing goes on until it is closed, Back, an action or the sheet collapsing,
     * however many chapters are chosen. {@code shown}: the list is open, not a sheet at its collapsed height.
     */
    private void bindSelectionBar(boolean shown) {
        boolean selecting = shown && mAudioAdapter != null && mAudioAdapter.isSelecting();
        int bar = selecting ? View.VISIBLE : View.GONE;
        for (View view : new View[] {mSelectionClose, mRadioAll, mDownland, mDelete}) {
            if (view != null && view.getVisibility() != bar) {
                view.setVisibility(bar);
            }
        }
        if (mButtonCollapse != null && shown && bottomSheetBehavior != null) {
            mButtonCollapse.setVisibility(selecting ? View.GONE : View.VISIBLE);
        }
        if (!selecting) {
            if (mShowingCount) {
                mShowingCount = false;
                broadcastSend(new Intent(Broadcast_SHOW_TITLE));
            }
            return;
        }
        int chosen = mAudioAdapter.getSelectedChapters();
        int total = mAudioAdapter.getItemCount();
        mShowingCount = true;
        mNameCurent.setText(getString(R.string.selection_count, chosen, total));
        boolean all = total > 0 && chosen == total;
        mRadioAll.setCompoundDrawablesRelativeWithIntrinsicBounds(all ? R.drawable.ic_check_circle_outline
                : chosen == 0 ? R.drawable.ic_checkbox_blank_circle_outline : R.drawable.ic_selection_partial, 0, 0, 0);
        mRadioAll.setContentDescription(getString(all ? R.string.selection_clear_all : R.string.selection_select_all));
        boolean any = mAudioAdapter.getSelectedItemsSize() > 0;
        for (ImageButton action : new ImageButton[] {mDownland, mDelete}) {
            action.setEnabled(any);
            action.setImageAlpha(any ? 255 : 97);
        }
    }

    @UnstableApi
    @Override
    public void showData(@NonNull ArrayList<AudioPOJO> data, @NonNull String bookUrl, int chaptersTotal) {
        if (mAudioAdapter != null) {
            mAudioAdapter.setData(data, chaptersTotal);
        }
    }

    /**
     * Says once what the hidden gestures do while another book keeps playing: holding play switches the player to
     * this book ({@link SwitchBookHold}), holding a seek button speeds up ({@link SeekHold}). Spent when dismissed, so
     * a rotation that drops the dialog unread does not spend it.
     */
    @Override
    public void showSwitchBookHint() {
        if (mSwitchHintShowing || pref.getBoolean(PREF_HINT_SWITCH_BOOK, false) || isFinishing()) {
            return;
        }
        mSwitchHintShowing = true; // a reload of the chapters while it is open does not stack a second one
        new AlertDialog.Builder(this)
                .setTitle(R.string.hint_switch_book_title)
                .setMessage(R.string.hint_switch_book)
                .setPositiveButton(R.string.got_it, null)
                .setOnDismissListener(dialog -> pref.edit().putBoolean(PREF_HINT_SWITCH_BOOK, true).apply())
                .show();
    }

    @Override
    public void updateTime(int timeCurent, int timeEnd, int buffered) {
        // the chapter played to its end under a hold: the position held belongs to the one before
        if (mActiveSeekHold != null && timeEnd != mSeekBar.getMax()) {
            mActiveSeekHold.finish(false);
        }
        // a hold shows its own position; a tick sent before its seek arrived would jump back for a moment
        if (mActiveSeekHold != null || (SystemClock.uptimeMillis() - mSeekCommitAt < SEEK_COMMIT_SETTLE_MS
                && Math.abs(timeCurent - mSeekCommitTarget) > SEEK_COMMIT_TOLERANCE_MS)) {
            return;
        }
        showTime(timeCurent, timeEnd, buffered);
    }

    private void showTime(int timeCurent, int timeEnd, int buffered) {
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
        String label = getString(id == R.drawable.ic_pause ? R.string.pause : R.string.play);
        if (mPlayTop != null) {
            mPlayTop.setImageResource(id);
            mPlayTop.setContentDescription(label);
        }
        mPlayBottom.setImageResource(id);
        mPlayBottom.setContentDescription(label);
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
        intent.setPackage(getPackageName());
        sendBroadcast(intent);
    }

    @Override
    public void activityStart(@NotNull Intent intent) {
        startActivity(intent);
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

    /**
     * Holding a play button (touch, or the D-pad centre / Enter key) for {@link #SWITCH_BOOK_HOLD_MS} while another
     * book plays switches the player to this one; a shorter press stays an ordinary click.
     */
    private final class SwitchBookHold implements View.OnTouchListener, View.OnKeyListener, Runnable {

        private View mButton;

        private boolean mArmed;

        private boolean mFired;

        void attach(@NonNull View button) {
            mButton = button;
            button.setOnTouchListener(this);
            button.setOnKeyListener(this);
        }

        private void arm() {
            disarm();
            mFired = false;
            if (mPresenter.canSwitchToThisBook()) {
                mArmed = true;
                mButton.postDelayed(this, SWITCH_BOOK_HOLD_MS);
            }
        }

        private void disarm() {
            mArmed = false;
            mButton.removeCallbacks(this);
        }

        /** Ends the press; true when the hold fired, so the click that would follow is swallowed. */
        private boolean release() {
            disarm();
            if (!mFired) {
                return false;
            }
            mFired = false;
            mButton.setPressed(false);
            return true;
        }

        @Override
        public void run() {
            boolean armed = mArmed;
            mArmed = false;
            // still held: a press whose release was missed (focus moved, screen left) ends unpressed
            if (!armed || !mButton.isPressed() || !mButton.hasWindowFocus() || !mPresenter.canSwitchToThisBook()) {
                return;
            }
            mFired = true;
            mButton.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            showToast(R.string.switched_to_this_book);
            mPresenter.switchToThisBook();
        }

        @SuppressLint("ClickableViewAccessibility")
        @Override
        public boolean onTouch(View v, MotionEvent event) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    arm();
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (event.getX() < 0 || event.getY() < 0 || event.getX() > v.getWidth()
                            || event.getY() > v.getHeight()) {
                        disarm();
                    }
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    return release();
                default:
                    break;
            }
            return false;
        }

        @Override
        public boolean onKey(View v, int keyCode, KeyEvent event) {
            if (keyCode != KeyEvent.KEYCODE_DPAD_CENTER && keyCode != KeyEvent.KEYCODE_ENTER) {
                return false;
            }
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                if (event.getRepeatCount() == 0) {
                    arm();
                }
                return mFired;
            }
            return event.getAction() == KeyEvent.ACTION_UP && release();
        }
    }

    /**
     * Holding a rewind/forward button (touch, or the D-pad centre) scrubs the shown position, faster and faster
     * the longer it is held, as hardware players do; the player seeks once, on release, so a stream does not
     * rebuffer on the way. A tap stays the ordinary click. The collapsed player's buttons do it only in seek mode.
     */
    private final class SeekHold implements View.OnLongClickListener, View.OnTouchListener, View.OnKeyListener,
            Runnable {

        private final View mButton;

        private final boolean mForward;

        private final boolean mTop;

        /** The position shown (ms) and the top speed of this hold. */
        private double mPosition;

        private double mMaxSpeed;

        /** The hold's start and its last frame, uptime ms. */
        private long mStart;

        private long mLast;

        private boolean mAtEdge;

        SeekHold(@NonNull View button, boolean forward, boolean top) {
            mButton = button;
            mForward = forward;
            mTop = top;
            button.setOnLongClickListener(this);
            button.setOnTouchListener(this);
            button.setOnKeyListener(this);
        }

        /** Any end of the press (a cancel too: the sheet took the gesture) seeks to the position the user saw. */
        @SuppressLint("ClickableViewAccessibility")
        @Override
        public boolean onTouch(View v, MotionEvent event) {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                finish(true);
            }
            return false;
        }

        @Override
        public boolean onKey(View v, int keyCode, KeyEvent event) {
            if (event.getAction() == KeyEvent.ACTION_UP) {
                finish(true);
            }
            return false;
        }

        @Override
        public boolean onLongClick(View v) {
            int duration = mSeekBar.getMax();
            //no chapter length known yet: nothing to scrub, a tap still seeks
            if ((mTop && !mTopSeek) || duration <= SEEK_HOLD_END_MARGIN_MS) {
                return false;
            }
            if (mActiveSeekHold != null) {
                mActiveSeekHold.finish(true);
            }
            mActiveSeekHold = this;
            mPosition = mSeekBar.getProgress();
            mMaxSpeed = Math.max(SEEK_HOLD_MAX_SPEED,
                    Math.min(SEEK_HOLD_MAX_SPEED_LONG, duration / SEEK_HOLD_CROSSING_MS));
            mStart = SystemClock.uptimeMillis();
            mLast = mStart;
            mAtEdge = false;
            mButton.postOnAnimation(this);
            return true;
        }

        /** One frame: the position moves by the speed reached so far, times the time since the last frame. */
        @Override
        public void run() {
            if (mActiveSeekHold != this) {
                return;
            }
            //released, slid off, or the screen left while held: the press is over
            if (!mButton.isPressed() || !mButton.hasWindowFocus()) {
                finish(true);
                return;
            }
            long now = SystemClock.uptimeMillis();
            double speed = Math.min(mMaxSpeed,
                    SEEK_HOLD_START_SPEED * Math.pow(2, (now - mStart) / SEEK_HOLD_DOUBLING_MS));
            double next = mPosition + (mForward ? speed : -speed) * (now - mLast);
            mLast = now;
            int end = Math.max(0, mSeekBar.getMax() - SEEK_HOLD_END_MARGIN_MS);
            mPosition = Math.max(0, Math.min(end, next));
            boolean atEdge = next <= 0 || next >= end;
            if (atEdge && !mAtEdge) {
                mButton.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            }
            mAtEdge = atEdge;
            showTime((int) mPosition, mSeekBar.getMax(), mSeekBar.getSecondaryProgress());
            mButton.postOnAnimation(this);
        }

        /** Ends the hold: {@code commit} seeks to the position shown, else the player's own position returns. */
        void finish(boolean commit) {
            if (mActiveSeekHold != this) {
                return;
            }
            mActiveSeekHold = null;
            mButton.removeCallbacks(this);
            if (commit) {
                mSeekCommitTarget = (int) mPosition;
                mSeekCommitAt = SystemClock.uptimeMillis();
                mPresenter.seekChange(mSeekCommitTarget);
                mButton.announceForAccessibility(mTimeStart.getText());
            }
        }
    }

    private boolean isTabView(View view) {
        if (view == null || tabs == null) return false;
        // Каждая вкладка (TabView) является дочерним элементом контейнера внутри TabLayout.
        // Проверяем, совпадает ли родительский View нашего focusedView с контейнером вкладок.
        ViewParent parent = view.getParent();
        // TabLayout содержит дочерний SlidingTabIndicator, который в свою очередь содержит TabView
        return parent != null && parent.getParent() == tabs;
    }

    /** The notification of another book reached this single-top screen: open that book's own screen. */
    @Override
    protected void onNewIntent(@NonNull Intent intent) {
        super.onNewIntent(intent);
        String json = intent.getStringExtra(ARG_BOOK);
        BookPOJO other = json != null ? BookPOJO.parceJsonToBookPojo(json) : null;
        if (other != null && other.getUrl() != null && !other.getUrl().equals(mBookPOJO.getUrl())) {
            Intent fresh = new Intent(intent);
            fresh.setFlags(intent.getFlags() & ~(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NEW_TASK));
            startActivity(fresh);
        }
    }

    @UnstableApi
    @Override
    protected void onResume() {
        super.onResume();
        if (pendingEqualizerSession > 0) {
            int sessionId = pendingEqualizerSession;
            pendingEqualizerSession = 0;
            showEqualizer(sessionId);
        }
        showingView = mBookPOJO.getUrl();
        bindTopSideButtons();
        invalidateOptionsMenu(); //the toolbar settings may have changed meanwhile
        View decorView = getWindow().getDecorView();
        int uiOptions = View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
        decorView.setSystemUiVisibility(uiOptions);
        mAudioAdapter.refresh();

        if (bottomSheetBehavior != null) {
            bottomSheetBehavior.setState(mPresenter.getState());
        } else {
            bindSelectionBar(true);
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
