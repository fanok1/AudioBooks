package com.fanok.audiobooks.pojo;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.annotation.NonNull;
import com.fanok.audiobooks.android_equalizer.EqualizerModel;
import com.fanok.audiobooks.android_equalizer.Settings;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.ArrayList;

public class StorageUtil {
    private final String STORAGE = "STORAGE";
    private SharedPreferences preferences;

    private final Context context;

    public StorageUtil(Context context) {
        this.context = context;
        this.preferences = context.getSharedPreferences(STORAGE, Context.MODE_PRIVATE);
    }

    public void storeAudio(ArrayList<AudioPOJO> arrayList) {
        SharedPreferences.Editor editor = preferences.edit();
        Gson gson = new Gson();
        String json = gson.toJson(arrayList);
        editor.putString("audioArrayList", json);
        editor.apply();
    }

    public ArrayList<AudioPOJO> loadAudio() {
        Gson gson = new Gson();
        String json = preferences.getString("audioArrayList", null);
        Type type = new TypeToken<ArrayList<AudioPOJO>>() {
        }.getType();
        return gson.fromJson(json, type);
    }

    public void storeAudioIndex(int index) {
        SharedPreferences.Editor editor = preferences.edit();
        editor.putInt("audioIndex", index);
        editor.apply();
    }

    public int loadAudioIndex() {
        return preferences.getInt("audioIndex", -1);//return -1 if no data found
    }

    public String loadUrlBook() {
        return preferences.getString("urlBook", "");
    }

    public void storeUrlBook(@NonNull String url) {
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString("urlBook", url);
        editor.apply();
    }

    public String loadImageUrl() {
        return preferences.getString("imageUrl", "");
    }

    public void storeImageUrl(@NonNull String url) {
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString("imageUrl", url);
        editor.apply();
    }

    public void storeTimeStart(int time) {
        SharedPreferences.Editor editor = preferences.edit();
        editor.putInt("timeStart", time);
        editor.apply();
    }

    public int loadTimeStart() {
        return preferences.getInt("timeStart", 0);
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
        Settings.equalizerModel = model;
    }
}
