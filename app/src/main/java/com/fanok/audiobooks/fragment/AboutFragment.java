package com.fanok.audiobooks.fragment;

import static android.content.Context.CLIPBOARD_SERVICE;
import static android.content.Context.UI_MODE_SERVICE;

import android.app.ActivityOptions;
import android.app.UiModeManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.util.TypedValue;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceScreen;
import com.fanok.audiobooks.BuildConfig;
import com.fanok.audiobooks.R;
import com.fanok.audiobooks.activity.ActivitySendEmail;
import com.fanok.audiobooks.activity.MainActivity;
import com.fanok.audiobooks.activity.PopupGetPlus;
import com.fanok.audiobooks.pojo.StorageAds;
import com.google.android.material.navigation.NavigationView;
import java.util.ArrayList;
import org.jetbrains.annotations.NotNull;

public class AboutFragment extends PreferenceFragmentCompat {

    @Override
    public void onNavigateToScreen(PreferenceScreen preferenceScreen) {
        AboutFragment settingsFragment = new AboutFragment();
        Bundle args = new Bundle();
        args.putString("rootKey", preferenceScreen.getKey());
        settingsFragment.setArguments(args);
        if (getActivity() != null) {
            getActivity().getSupportFragmentManager()
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

        requireActivity().setTitle(R.string.menu_about);
        PreferenceManager.setDefaultValues(
                requireActivity().getApplicationContext(),
                R.xml.about_preferences,
                false);

        preferenceClickListner("privacy", preference -> {
            AlertDialog.Builder builder = new AlertDialog.Builder(
                    requireContext());
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

        preferenceClickListner("yandexForm", preference -> {
            Intent browserIntent = new Intent(Intent.ACTION_VIEW,
                    Uri.parse("http://mdpu.mcdir.ru/yd.html"));
            startActivity(browserIntent);
            return true;
        });

        preferenceClickListner("qiwi", preference -> {
            Intent browserIntent = new Intent(Intent.ACTION_VIEW,
                    Uri.parse("http://qiwi.com/n/ONAMA629"));
            startActivity(browserIntent);
            return true;
        });

        preferenceClickListner("disable_battary_optimize", preference -> {
            boolean enabled;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PowerManager pm = (PowerManager) requireContext().getSystemService(Context.POWER_SERVICE);
                UiModeManager uiModeManager = (UiModeManager) requireContext().getSystemService(
                        UI_MODE_SERVICE);
                if (uiModeManager != null && uiModeManager.getCurrentModeType()
                        == Configuration.UI_MODE_TYPE_TELEVISION) {
                    enabled = false;

                } else if (pm != null) {
                    enabled = !pm.isIgnoringBatteryOptimizations(
                            "com.fanok.audiobooks");
                } else {
                    enabled = false;
                }
            } else {
                enabled = false;
            }

            if (enabled) {
                Toast.makeText(getContext(), R.string.enabled, Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(getContext(), R.string.disenabled, Toast.LENGTH_SHORT).show();
            }

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
                    (ClipboardManager) requireContext().getSystemService(
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

        preferenceClickListner("open_4pda", preference -> {
            Intent browserIntent = new Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://4pda.ru/forum/index.php?showtopic=978445"));
            startActivity(browserIntent);
            return true;
        });

        preferenceClickListner("monoUAH", clickToCopy);
        preferenceClickListner("monoUSD", clickToCopy);
        preferenceClickListner("monoEUR", clickToCopy);
        preferenceClickListner("privat", clickToCopy);
        preferenceClickListner("alpha", clickToCopy);
        //preferenceClickListner("paypal", clickToCopy);
        preferenceClickListner("yd", clickToCopy);
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

        MainActivity activity = (MainActivity) getActivity();
        if (activity != null) {
            NavigationView navigationView = activity.getNavigationView();
            ArrayList<TextView> mTextViewArrayList = activity.getTextViewArrayList();
            final TypedValue SelectedValue = new TypedValue();
            activity.getTheme().resolveAttribute(R.attr.mySelectableItemBackground, SelectedValue,
                    true);
            if (navigationView != null) {
                navigationView.setCheckedItem(R.id.nav_about);
            } else if (mTextViewArrayList != null && mTextViewArrayList.size() > 8) {
                mTextViewArrayList.get(8).setBackgroundResource(SelectedValue.resourceId);
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
