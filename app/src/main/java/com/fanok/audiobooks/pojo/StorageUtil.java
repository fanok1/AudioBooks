package com.fanok.audiobooks.pojo;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.fanok.audiobooks.android_equalizer.EqualizerModel;
import com.fanok.audiobooks.android_equalizer.Settings;
import com.fanok.audiobooks.service.Checkpoint;
import com.google.gson.Gson;

public class StorageUtil {
    private final String STORAGE = "STORAGE";

    /** Keys through which the player once received the chapter list and play state; dropped on the next checkpoint. */
    private static final String[] LEGACY_PLAYBACK_KEYS = {"audioArrayList", "audioIndex", "urlBook", "imageUrl", "timeStart"};

    private SharedPreferences preferences;

    private final Context context;

    public StorageUtil(Context context) {
        this.context = context;
        this.preferences = context.getSharedPreferences(STORAGE, Context.MODE_PRIVATE);
    }

    /** Writes the checkpoint as one record, under its book and as the latest one. */
    public void storeCheckpoint(@NonNull Checkpoint checkpoint, boolean synchronous) {
        String value = checkpoint.serialize();
        SharedPreferences.Editor editor = preferences.edit()
                .putString(Checkpoint.key(checkpoint.bookUrl), value)
                .putString(Checkpoint.KEY_LAST, value);
        for (String legacy : LEGACY_PLAYBACK_KEYS) {
            editor.remove(legacy);
        }
        if (synchronous) {
            editor.commit();
        } else {
            editor.apply();
        }
    }

    /** @return the checkpoint of {@code bookUrl}, or null when none was written */
    @Nullable
    public Checkpoint loadCheckpoint(@NonNull String bookUrl) {
        Checkpoint checkpoint = Checkpoint.parse(preferences.getString(Checkpoint.key(bookUrl), null));
        return checkpoint != null && checkpoint.bookUrl.equals(bookUrl) ? checkpoint : null;
    }

    /** @return the most recently written checkpoint, or null */
    @Nullable
    public Checkpoint loadLastCheckpoint() {
        return Checkpoint.parse(preferences.getString(Checkpoint.KEY_LAST, null));
    }

    public void storeCountAudioListnered(int count) {
        SharedPreferences.Editor editor = preferences.edit();
        editor.putInt("countAudioListnered", count);
        editor.apply();
    }

    public int loadCountAudioListnered() {
        return preferences.getInt("countAudioListnered", 0);
    }

    public void storeCountAudioListneredForRating(int count) {
        SharedPreferences.Editor editor = preferences.edit();
        editor.putInt("countAudioListneredForRating", count);
        editor.apply();
    }

    public int loadCountAudioListneredForRating() {
        return preferences.getInt("countAudioListneredForRating", 0);
    }

    public void storeShowRating(boolean b) {
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean("showRating", b);
        editor.apply();
    }

    public boolean loadShowRating() {
        return preferences.getBoolean("showRating", true);
    }

    public void storeBattaryOptimizeDisenbled(boolean b) {
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean("battaryOptimizeDisenbled", b);
        editor.apply();
    }

    public boolean loadBattaryOptimizeDisenbled() {
        preferences = context.getSharedPreferences(STORAGE, Context.MODE_PRIVATE);
        return preferences.getBoolean("battaryOptimizeDisenbled", false);
    }

    public void storeSpeed(float speed) {
        SharedPreferences.Editor editor = preferences.edit();
        editor.putFloat("speed", speed);
        editor.apply();
    }

    public float loadSpeed() {
        preferences = context.getSharedPreferences(STORAGE, Context.MODE_PRIVATE);
        return preferences.getFloat("speed", 1);
    }

    public void saveEqualizerSettings() {
        if (Settings.equalizerModel != null) {
            EqualizerSettings settings = new EqualizerSettings();
            settings.bassStrength = Settings.equalizerModel.getBassStrength();
            settings.presetPos = Settings.equalizerModel.getPresetPos();
            settings.reverbPreset = Settings.equalizerModel.getReverbPreset();
            settings.seekbarpos = Settings.equalizerModel.getSeekbarpos();
            settings.equalizerEnabled = Settings.isEqualizerEnabled;
            settings.balance = Settings.balance;

            Gson gson = new Gson();
            preferences.edit()
                    .putString("equalizer", gson.toJson(settings))
                    .apply();
        }
    }

    public void loadEqualizerSettings() {

        Gson gson = new Gson();
        EqualizerSettings settings = gson.fromJson(preferences.getString("equalizer", "{}"),
                EqualizerSettings.class);
        if (settings == null) {
            settings = new EqualizerSettings();
        }
        EqualizerModel model = new EqualizerModel();
        model.setBassStrength(settings.bassStrength);
        model.setPresetPos(settings.presetPos);
        model.setReverbPreset(settings.reverbPreset);
        model.setSeekbarpos(settings.seekbarpos);
        model.setEqualizerEnabled(settings.equalizerEnabled);

        Settings.isEqualizerReloaded = true;
        Settings.isEqualizerEnabled = settings.equalizerEnabled;
        Settings.bassStrength = settings.bassStrength;
        Settings.presetPos = settings.presetPos;
        Settings.reverbPreset = settings.reverbPreset;
        Settings.seekbarpos = settings.seekbarpos;
        Settings.balance = settings.balance;
        Settings.equalizerModel = model;
    }
}
