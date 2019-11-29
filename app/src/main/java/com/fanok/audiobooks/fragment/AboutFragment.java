package com.fanok.audiobooks.fragment;

import static android.content.Context.CLIPBOARD_SERVICE;

import android.app.ActivityOptions;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceScreen;

import com.fanok.audiobooks.BuildConfig;
import com.fanok.audiobooks.R;
import com.fanok.audiobooks.activity.ActivitySendEmail;
import com.fanok.audiobooks.activity.PopupGetPlus;
import com.fanok.audiobooks.pojo.StorageAds;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public class AboutFragment extends PreferenceFragmentCompat {

    @Override
    public void onNavigateToScreen(PreferenceScreen preferenceScreen) {
        AboutFragment settingsFragment = new AboutFragment();
        Bundle args = new Bundle();
        args.putString("rootKey", preferenceScreen.getKey());
        settingsFragment.setArguments(args);
        if (getFragmentManager() != null) {
            getFragmentManager()
                    .beginTransaction()
                    .replace(getId(), settingsFragment)
                    .addToBackStack(null)
                    .commit();
        }
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {

        if (getArguments() != null) {
            String key = getArguments().getString("rootKey");
            setPreferencesFromResource(R.xml.about_preferences, key);
        } else {
            setPreferencesFromResource(R.xml.about_preferences, rootKey);
        }

        Objects.requireNonNull(getActivity()).setTitle(R.string.menu_about);
        PreferenceManager.setDefaultValues(
                Objects.requireNonNull(getActivity()).getApplicationContext(),
                R.xml.about_preferences,
                false);

        preferenceClickListner("privacy", preference -> {
            AlertDialog.Builder builder = new AlertDialog.Builder(
                    Objects.requireNonNull(getContext()));
            builder.setTitle(getString(R.string.privacy))
                    .setMessage(getString(R.string.privacy_message))
                    .setIcon(R.drawable.ic_privacy)
                    .setCancelable(false)
                    .setNegativeButton(getString(R.string.yes),
                            (dialog, id) -> dialog.cancel());
            AlertDialog alert = builder.create();
            alert.show();
            return true;
        });

        preferenceClickListner("version_plus", preference -> {
            ActivityOptions options = ActivityOptions
                    .makeSceneTransitionAnimation(getActivity(), getView(), "robot");
            startActivity(new Intent(getContext(), PopupGetPlus.class), options.toBundle());
            return true;
        });


        Preference.OnPreferenceClickListener clickToCopy = preference -> {
            ClipboardManager clipboardManager =
                    (ClipboardManager) Objects.requireNonNull(getContext()).getSystemService(
                            CLIPBOARD_SERVICE);
            String text = preference.getSummary().toString().replaceAll(" ", "");
            ClipData clipData = ClipData.newPlainText("Source Text", text);
            if (clipboardManager != null) {
                clipboardManager.setPrimaryClip(clipData);
                Toast.makeText(getContext(), getString(R.string.copied), Toast.LENGTH_SHORT).show();
            }
            return true;
        };

        preferenceClickListner("write_autor", preference -> {
            Intent intent = new Intent(getContext(), ActivitySendEmail.class);
            startActivity(intent);
            return true;
        });

        preferenceClickListner("monoUAH", clickToCopy);
        preferenceClickListner("monoUSD", clickToCopy);
        preferenceClickListner("monoEUR", clickToCopy);
        preferenceClickListner("privat", clickToCopy);
        preferenceClickListner("alpha", clickToCopy);
        preferenceClickListner("paypal", clickToCopy);
        preferenceClickListner("qiwi", clickToCopy);
        preferenceClickListner("wmu", clickToCopy);
        preferenceClickListner("wmr", clickToCopy);
        preferenceClickListner("wmz", clickToCopy);
        preferenceClickListner("wme", clickToCopy);


    }

    @Override
    public void onResume() {
        super.onResume();
        Preference version = findPreference("version");
        if (version != null) {
            version.setSummary(getString(R.string.version) + " " + BuildConfig.VERSION_NAME);
            if (StorageAds.idDisableAds()) {
                version.setTitle(getString(R.string.app_name) + " Plus");
            } else {
                version.setTitle(getString(R.string.app_name));
            }
        }
    }

    private void preferenceClickListner(@NotNull String key,
            @NotNull Preference.OnPreferenceClickListener click) {
        Preference preference = findPreference(key);
        if (preference != null) {
            preference.setOnPreferenceClickListener(click);
        }
    }
}
