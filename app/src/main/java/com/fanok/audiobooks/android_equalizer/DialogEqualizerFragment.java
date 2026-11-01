package com.fanok.audiobooks.android_equalizer;


import static com.fanok.audiobooks.android_equalizer.EqualizerFragment.Broadcast_EqualizerEnabled;

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.media.audiofx.BassBoost;
import android.media.audiofx.Equalizer;
import android.media.audiofx.PresetReverb;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.widget.SwitchCompat;
import androidx.fragment.app.DialogFragment;
import com.db.chart.model.LineSet;
import com.db.chart.view.AxisController;
import com.db.chart.view.ChartView;
import com.db.chart.view.LineChartView;
import com.fanok.audiobooks.R;
import com.fanok.audiobooks.pojo.StorageUtil;
import com.fanok.audiobooks.service.BalanceAudioProcessor;
import java.util.ArrayList;
import java.util.Locale;
import org.jetbrains.annotations.NotNull;


/**
 * The equalizer dialog. Bands come from the device ({@link Equalizer#getNumberOfBands()}), one column each; when
 * the audio effects cannot be created the dialog says so and closes instead of crashing (UI-06).
 */
public class DialogEqualizerFragment extends DialogFragment {
    public static final String ARG_AUDIO_SESSIOIN_ID = "audio_session_id";
    private static final String TAG = DialogEqualizerFragment.class.getSimpleName();
    /** A balance this close to the centre, dragged to, is the centre. */
    private static final int BALANCE_DETENT = 4;
    private static int textColor = Color.WHITE;
    private static int themeColor = Color.parseColor("#B24242");
    private static int backgroundColor = Color.WHITE;
    private static int themeRes = 0;

    private static String titleString = "";

    private static int titleRes = 0;

    private Equalizer mEqualizer;

    private BassBoost bassBoost;

    private PresetReverb presetReverb;

    private LineSet dataset;

    private LineChartView chart;

    private float[] points;

    private int y = 0;

    private SeekBar[] seekBarFinal = new SeekBar[0];

    private Spinner presetSpinner;

    private Context ctx;

    private int audioSesionId;

    private TextView titleTextView;

    private AnalogController bassController;

    private AnalogController reverbController;

    private int[] levels = new int[0];

    public DialogEqualizerFragment() {
        // Required empty public constructor
    }

    public static DialogEqualizerFragment newInstance(int audioSessionId) {
        Bundle args = new Bundle();
        args.putInt(ARG_AUDIO_SESSIOIN_ID, audioSessionId);
        DialogEqualizerFragment fragment = new DialogEqualizerFragment();
        fragment.setArguments(args);
        return fragment;
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    @Override
    public int getTheme() {
        if (themeRes != 0) {
            return themeRes;
        } else {
            return super.getTheme();
        }
    }

    @Override
    public void onAttach(@NotNull Context context) {
        super.onAttach(context);
        ctx = context;
    }

    private void sendEqualizerEnabled(boolean enabled) {
        Intent intent = new Intent(Broadcast_EqualizerEnabled);
        intent.putExtra("enabled", enabled);
        intent.setPackage(requireContext().getPackageName());
        requireContext().sendBroadcast(intent.setPackage(requireContext().getPackageName()));
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        new StorageUtil(requireContext()).loadEqualizerSettings();
        sendEqualizerEnabled(false);

        Settings.isEditing = true;

        if (getArguments() != null && getArguments().containsKey(ARG_AUDIO_SESSIOIN_ID)) {
            audioSesionId = getArguments().getInt(ARG_AUDIO_SESSIOIN_ID);
        }

        if (Settings.equalizerModel == null) {
            Settings.equalizerModel = new EqualizerModel();
            Settings.equalizerModel.setReverbPreset(PresetReverb.PRESET_NONE);
            Settings.equalizerModel.setBassStrength((short) 0);
        }

        try {
            mEqualizer = new Equalizer(0, audioSesionId);
        } catch (RuntimeException e) {
            Log.w(TAG, "No equalizer for session " + audioSesionId, e);
            return; // onViewCreated closes the dialog with a message
        }
        try {
            bassBoost = new BassBoost(0, audioSesionId);
            BassBoost.Settings bassBoostSetting = new BassBoost.Settings(bassBoost.getProperties().toString());
            bassBoostSetting.strength = Settings.equalizerModel.getBassStrength();
            bassBoost.setProperties(bassBoostSetting);
            bassBoost.setEnabled(Settings.isBassOn(bassBoostSetting.strength));
        } catch (RuntimeException e) {
            Log.w(TAG, "No bass boost", e);
            bassBoost = null;
        }
        try {
            presetReverb = new PresetReverb(0, audioSesionId);
            presetReverb.setPreset(Settings.equalizerModel.getReverbPreset());
            presetReverb.setEnabled(Settings.isReverbOn(Settings.equalizerModel.getReverbPreset()));
        } catch (RuntimeException e) {
            Log.w(TAG, "No reverb", e);
            presetReverb = null;
        }

        mEqualizer.setEnabled(Settings.isEqualizerEnabled);
        short bands = mEqualizer.getNumberOfBands();
        levels = Settings.bandLevels(bands);
        try {
            Settings.applyTo(mEqualizer);
        } catch (IllegalArgumentException e) {
            Settings.presetPos = 0;
            Settings.applyTo(mEqualizer);
        }
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_fragment_equalizer, container, false);
    }

    @Override
    public void onStart() {
        super.onStart();
        // a dialog keeps its portrait width on its side: in landscape its two columns take most of the screen
        Window window = getDialog() == null ? null : getDialog().getWindow();
        if (window != null && getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            window.setLayout((int) (getResources().getDisplayMetrics().widthPixels * 0.85),
                    ViewGroup.LayoutParams.MATCH_PARENT);
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (mEqualizer == null) {
            Toast.makeText(ctx, R.string.equalizer_unavailable, Toast.LENGTH_SHORT).show();
            dismissAllowingStateLoss();
            return;
        }

        ImageView backBtn = view.findViewById(R.id.equalizer_back_btn);
        backBtn.setOnClickListener(v -> dismiss());
        backBtn.setColorFilter(textColor);
        view.findViewById(R.id.equalizerLayout).setBackgroundColor(backgroundColor);

        titleTextView = view.findViewById(R.id.equalizer_fragment_title);
        titleTextView.setTextColor(textColor);
        if (titleRes != 0) {
            try {
                titleTextView.setText(getString(titleRes));
            } catch (Exception e) {
                Log.e(TAG, "onViewCreated: unable to set title because " + e.getLocalizedMessage());
            }
        } else if (!TextUtils.isEmpty(titleString)) {
            titleTextView.setText(titleString);
        }
        SwitchCompat equalizerSwitch = view.findViewById(R.id.equalizer_switch);
        equalizerSwitch.setChecked(Settings.isEqualizerEnabled);
        equalizerSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            mEqualizer.setEnabled(isChecked);
            Settings.isEqualizerEnabled = isChecked;
            if (bassBoost != null) {
                bassBoost.setEnabled(Settings.isBassOn(Settings.equalizerModel.getBassStrength()));
            }
            if (presetReverb != null) {
                presetReverb.setEnabled(Settings.isReverbOn(Settings.equalizerModel.getReverbPreset()));
            }
        });

        presetSpinner = view.findViewById(R.id.equalizer_preset_spinner);
        presetSpinner.getBackground().setColorFilter(textColor, PorterDuff.Mode.SRC_ATOP);
        bindBalance(view);

        chart = view.findViewById(R.id.lineChart);
        Paint paint = new Paint();
        dataset = new LineSet();

        bassController = view.findViewById(R.id.controllerBass);
        reverbController = view.findViewById(R.id.controller3D);

        bassController.setMax(Settings.BASS_STEPS);
        reverbController.setMax(PresetReverb.PRESET_PLATE); // one step per room, 0 being none
        bassController.setLabel(getString(R.string.equalizer_bass));
        reverbController.setLabel(getString(R.string.equalizer_3d));
        bassController.setEnabled(bassBoost != null);
        reverbController.setEnabled(presetReverb != null);


        bassController.circlePaint2.setColor(themeColor);
        bassController.linePaint.setColor(themeColor);
        bassController.invalidate();
        reverbController.circlePaint2.setColor(themeColor);
        reverbController.linePaint.setColor(themeColor);
        reverbController.invalidate();

        int x = 0;
        if (!Settings.isEqualizerReloaded) {
            if (bassBoost != null) {
                try {
                    x = Settings.bassStep(bassBoost.getRoundedStrength());
                } catch (Exception e) {
                    Log.e(TAG, "onViewCreated: " + e.getLocalizedMessage());
                }
            }

            if (presetReverb != null) {
                try {
                    y = presetReverb.getPreset();
                } catch (Exception e) {
                    Log.e(TAG, "onViewCreated: " + e.getLocalizedMessage());
                }
            }
        } else {
            x = Settings.bassStep(Settings.bassStrength);
            y = Settings.reverbPreset;
        }
        bassController.setProgress(x);
        reverbController.setProgress(y);

        bassController.setOnProgressChangedListener(
                progress -> {
                    Settings.bassStrength = Settings.bassStrength(progress);
                    try {
                        if (bassBoost != null) {
                            bassBoost.setStrength(Settings.bassStrength);
                            bassBoost.setEnabled(Settings.isBassOn(Settings.bassStrength));
                        }
                        Settings.equalizerModel.setBassStrength(Settings.bassStrength);
                    } catch (Exception e) {
                        Log.e(TAG, "onViewCreated: " + e.getLocalizedMessage());
                    }
                });

        reverbController.setOnProgressChangedListener(
                progress -> {
                    Settings.reverbPreset = (short) progress;
                    try {
                        if (presetReverb != null) {
                            presetReverb.setPreset(Settings.reverbPreset);
                            presetReverb.setEnabled(Settings.isReverbOn(Settings.reverbPreset));
                        }
                        Settings.equalizerModel.setReverbPreset(Settings.reverbPreset);
                    } catch (Exception e) {
                        Log.e(TAG, "onViewCreated: " + e.getLocalizedMessage());
                    }
                    y = progress;
                });

        final short numberOfFrequencyBands = mEqualizer.getNumberOfBands();
        points = new float[numberOfFrequencyBands];
        seekBarFinal = new SeekBar[numberOfFrequencyBands];

        final short lowerEqualizerBandLevel = mEqualizer.getBandLevelRange()[0];
        final short upperEqualizerBandLevel = mEqualizer.getBandLevelRange()[1];

        LinearLayout bandsContainer = view.findViewById(R.id.equalizerContainer);
        bandsContainer.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(ctx);

        for (short i = 0; i < numberOfFrequencyBands; i++) {
            final short equalizerBandIndex = i;
            final int bandPosition = i;
            View column = inflater.inflate(R.layout.equalizer_band, bandsContainer, false);
            bandsContainer.addView(column);
            SeekBar seekBar = column.findViewById(R.id.bandSeekBar);
            TextView textView = column.findViewById(R.id.bandLabel);
            String label = frequencyLabel(mEqualizer.getCenterFreq(equalizerBandIndex));

            seekBarFinal[i] = seekBar;
            seekBar.getProgressDrawable().setColorFilter(
                    new PorterDuffColorFilter(Color.DKGRAY, PorterDuff.Mode.SRC_IN));
            seekBar.getThumb().setColorFilter(
                    new PorterDuffColorFilter(themeColor, PorterDuff.Mode.SRC_IN));
            seekBar.setMax(upperEqualizerBandLevel - lowerEqualizerBandLevel);
            seekBar.setContentDescription(getString(R.string.equalizer_band_description, label));

            textView.setText(label);
            textView.setTextColor(textColor);
            textView.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);

            if (Settings.isEqualizerReloaded) {
                points[i] = levels[i] - lowerEqualizerBandLevel;
                seekBar.setProgress(levels[i] - lowerEqualizerBandLevel);
            } else {
                points[i] = mEqualizer.getBandLevel(equalizerBandIndex) - lowerEqualizerBandLevel;
                seekBar.setProgress(mEqualizer.getBandLevel(equalizerBandIndex) - lowerEqualizerBandLevel);
                levels[i] = mEqualizer.getBandLevel(equalizerBandIndex);
            }
            dataset.addPoint(label, points[i]);
            seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    mEqualizer.setBandLevel(equalizerBandIndex,
                            (short) (progress + lowerEqualizerBandLevel));
                    points[bandPosition] = mEqualizer.getBandLevel(equalizerBandIndex)
                            - lowerEqualizerBandLevel;
                    levels[bandPosition] = progress + lowerEqualizerBandLevel;
                    dataset.updateValues(points);
                    chart.notifyDataUpdate();
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                    presetSpinner.setSelection(0);
                    Settings.presetPos = 0;
                    Settings.equalizerModel.setPresetPos(0);
                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {

                }
            });

        }
        Settings.isEqualizerReloaded = true;

        equalizeSound();

        paint.setColor(textColor);
        paint.setStrokeWidth((float) (1.10 * Settings.ratio));

        dataset.setColor(themeColor);
        dataset.setSmooth(true);
        dataset.setThickness(5);

        chart.setXAxis(false);
        chart.setYAxis(false);

        chart.setYLabels(AxisController.LabelPosition.NONE);
        chart.setXLabels(AxisController.LabelPosition.NONE);
        chart.setGrid(ChartView.GridType.NONE, 7, 10, paint);

        chart.setAxisBorderValues(Math.min(-300, lowerEqualizerBandLevel - 300),
                Math.max(3300, upperEqualizerBandLevel - lowerEqualizerBandLevel + 300));

        chart.addData(dataset);
        chart.show();
    }

    /**
     * The left/right balance bar: heard at once, saved with the other settings when the dialog closes. It is not an
     * effect of the session, so it plays whether the equalizer switch is on or off.
     */
    private void bindBalance(@NonNull View view) {
        ((TextView) view.findViewById(R.id.equalizer_balance_left)).setTextColor(textColor);
        ((TextView) view.findViewById(R.id.equalizer_balance_right)).setTextColor(textColor);
        view.findViewById(R.id.equalizer_balance_centre).setBackgroundColor(textColor);
        SeekBar balanceBar = view.findViewById(R.id.equalizer_balance);
        balanceBar.getProgressDrawable().setColorFilter(
                new PorterDuffColorFilter(Color.DKGRAY, PorterDuff.Mode.SRC_IN));
        if (balanceBar.getProgressDrawable() instanceof LayerDrawable) {
            // a balance is no amount: one even track, without a fill from the left edge
            LayerDrawable track = (LayerDrawable) balanceBar.getProgressDrawable();
            Drawable fill = track.findDrawableByLayerId(android.R.id.progress);
            Drawable rail = track.findDrawableByLayerId(android.R.id.background);
            if (fill != null) fill.setAlpha(0);
            if (rail != null) rail.setAlpha(255);
        }
        balanceBar.getThumb().setColorFilter(new PorterDuffColorFilter(themeColor, PorterDuff.Mode.SRC_IN));
        balanceBar.setMax(2 * BalanceAudioProcessor.MAX);
        balanceBar.setProgress(Settings.balance + BalanceAudioProcessor.MAX);
        balanceBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int balance = progress - BalanceAudioProcessor.MAX;
                if (fromUser && balance != 0 && Math.abs(balance) <= BALANCE_DETENT) {
                    seekBar.setProgress(BalanceAudioProcessor.MAX); //the centre holds the thumb
                    return;
                }
                Settings.balance = balance;
                BalanceAudioProcessor.setBalance(balance);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });
    }

    /** "60 Hz", "3.6 kHz": the band's centre frequency given in millihertz. */
    static String frequencyLabel(int centerFreqMilliHz) {
        int hz = centerFreqMilliHz / 1000;
        if (hz < 1000) {
            return hz + " Hz";
        }
        if (hz % 1000 == 0) {
            return (hz / 1000) + " kHz";
        }
        return String.format(Locale.ROOT, "%.1f kHz", hz / 1000f);
    }


    public TextView getTitleTextView() {
        return titleTextView;
    }

    public AnalogController getBassController() {
        return bassController;
    }

    public AnalogController getReverbController() {
        return reverbController;
    }


    public void equalizeSound() {
        ArrayList<String> equalizerPresetNames = new ArrayList<>();
        ArrayAdapter<String> equalizerPresetSpinnerAdapter = new ArrayAdapter<>(ctx,
                R.layout.spinner_item,
                equalizerPresetNames);
        equalizerPresetSpinnerAdapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item);
        equalizerPresetNames.add(getString(R.string.equalizer_custom));

        for (short i = 0; i < mEqualizer.getNumberOfPresets(); i++) {
            equalizerPresetNames.add(mEqualizer.getPresetName(i));
        }

        presetSpinner.setAdapter(equalizerPresetSpinnerAdapter);
        if (Settings.isEqualizerReloaded && Settings.presetPos != 0
                && Settings.presetPos < equalizerPresetNames.size()) {
            presetSpinner.setSelection(Settings.presetPos);
        }

        presetSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                try {
                    if (position != 0) {
                        mEqualizer.usePreset((short) (position - 1));
                        Settings.presetPos = position;

                        final short lowerEqualizerBandLevel = mEqualizer.getBandLevelRange()[0];

                        for (short i = 0; i < seekBarFinal.length; i++) {
                            seekBarFinal[i].setProgress(
                                    mEqualizer.getBandLevel(i) - lowerEqualizerBandLevel);
                            points[i] = mEqualizer.getBandLevel(i) - lowerEqualizerBandLevel;
                            levels[i] = mEqualizer.getBandLevel(i);
                        }
                        dataset.updateValues(points);
                        chart.notifyDataUpdate();
                    }
                } catch (Exception e) {
                    Toast.makeText(ctx, R.string.equalizer_error, Toast.LENGTH_SHORT).show();
                }
                Settings.equalizerModel.setPresetPos(position);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });
    }

    @Override
    public void onDestroy() {

        if (mEqualizer != null) {
            new StorageUtil(requireContext()).saveEqualizerSettings();
        }
        sendEqualizerEnabled(true);

        if (mEqualizer != null) {
            mEqualizer.release();
        }

        if (bassBoost != null) {
            bassBoost.release();
        }

        if (presetReverb != null) {
            presetReverb.release();
        }

        Settings.isEditing = false;

        super.onDestroy();

    }

    public static class Builder {
        private int id = -1;

        public Builder setThemeRes(int res) {
            themeRes = res;
            return this;
        }

        public Builder setAudioSessionId(int id) {
            this.id = id;
            return this;
        }

        public Builder setAccentColor(int color) {
            themeColor = color;
            return this;
        }

        public Builder themeColor(int color) {
            backgroundColor = color;
            return this;
        }

        public Builder textColor(int color) {
            textColor = color;
            return this;
        }

        public Builder title(@StringRes int title) {
            titleRes = title;
            return this;
        }

        public Builder title(@NonNull String title) {
            titleString = title;
            return this;
        }

        public DialogEqualizerFragment build() {
            return DialogEqualizerFragment.newInstance(id);
        }
    }


}
