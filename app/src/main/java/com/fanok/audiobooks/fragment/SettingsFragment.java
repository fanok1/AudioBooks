package com.fanok.audiobooks.fragment;

import android.Manifest;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.SearchRecentSuggestions;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.MultiSelectListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceGroup;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceScreen;

import com.codekidlabs.storagechooser.Content;
import com.codekidlabs.storagechooser.StorageChooser;
import com.fanok.audiobooks.MySuggestionProvider;
import com.fanok.audiobooks.R;
import com.fanok.audiobooks.model.AudioDBModel;
import com.fanok.audiobooks.model.BooksDBModel;
import com.fanok.audiobooks.pojo.BackupPOJO;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import pub.devrel.easypermissions.EasyPermissions;

public class SettingsFragment extends PreferenceFragmentCompat implements
        SharedPreferences.OnSharedPreferenceChangeListener, EasyPermissions.PermissionCallbacks {
    private static final int CODE_RESTORE = 292;
    private static final int REQUEST_WRITE_STORAGE = 112;
    private static final int EXTERNAL_STORAGE_PERMISSION_REQUEST_CODE = 342;

    private DialogInterface.OnClickListener mOnClickListener = (dialogInterface, i) -> {


        Content content = new Content();
        content.setCancelLabel(getString(R.string.cancel));
        content.setSelectLabel(getString(R.string.select));
        content.setOverviewHeading(getString(R.string.select_folder));
        content.setCreateLabel(getString(R.string.create));
        content.setFolderCreatedToastText(getString(R.string.folder_created));
        content.setFolderErrorToastText(getString(R.string.error_folder_create));
        content.setNewFolderLabel(getString(R.string.new_folder));
        content.setTextfieldHintText(getString(R.string.new_folder));
        content.setTextfieldErrorText(getString(R.string.empty_text));


        StorageChooser.Builder builder = new StorageChooser.Builder()
                .withContent(content)
                .allowAddFolder(true)
                .withActivity(getActivity())
                .withMemoryBar(true)
                .withFragmentManager(Objects.requireNonNull(getActivity()).getFragmentManager())
                .withPredefinedPath(Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_DOWNLOADS).getPath())
                .allowCustomPath(true)
                .setType(StorageChooser.DIRECTORY_CHOOSER);


        SharedPreferences pref = PreferenceManager
                .getDefaultSharedPreferences(Objects.requireNonNull(getContext()));
        String themeName = pref.getString("pref_theme", getString(R.string.theme_light_value));
        if (themeName.equals(getString(R.string.theme_dark_value))) {
            StorageChooser.Theme theme = new StorageChooser.Theme(
                    Objects.requireNonNull(getContext()).getApplicationContext());
            theme.setScheme(getResources().getIntArray(R.array.paranoid_theme));
            builder.setTheme(theme);
        }

        StorageChooser chooser = builder.build();


        chooser.setOnSelectListener(path -> {
            if (backup(path)) {
                Toast.makeText(getContext(), getString(R.string.backup_created),
                        Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(getContext(),
                        getString(R.string.backup_not_created),
                        Toast.LENGTH_SHORT).show();
            }
        });
        chooser.show();
    };

    @Override
    public void onNavigateToScreen(PreferenceScreen preferenceScreen) {
        SettingsFragment settingsFragment = new SettingsFragment();
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
            setPreferencesFromResource(R.xml.preferences, key);
        } else {
            setPreferencesFromResource(R.xml.preferences, rootKey);
        }
        Objects.requireNonNull(getActivity()).setTitle(R.string.menu_settings);
        PreferenceManager.setDefaultValues(
                Objects.requireNonNull(getActivity()).getApplicationContext(), R.xml.preferences,
                false);
        initSummary(getPreferenceScreen());


        preferenceChangeListner("pref_theme", (preference, newValue) -> {
            if (newValue.equals(getString(R.string.theme_dark_value))) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
            } else {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
            }
            getActivity().recreate();
            return true;
        });

        preferenceClickListner("clear_history_search", preference -> {
            showAllert(preference.getContext(), R.string.confirm_clear_history_search,
                    (dialogInterface, i) -> {
                        SearchRecentSuggestions suggestions = new SearchRecentSuggestions(
                                preference.getContext(),
                                MySuggestionProvider.AUTHORITY, MySuggestionProvider.MODE);
                        suggestions.clearHistory();
                    });
            return true;
        });

        preferenceClickListner("clear_history", preference -> {
            showAllert(preference.getContext(), R.string.confirm_clear_history,
                    (dialogInterface, i) -> {
                        new BooksDBModel(preference.getContext()).clearHistory();
                        new AudioDBModel(preference.getContext()).clearAll();
                    });
            return true;
        });

        preferenceClickListner("clear_favorite", preference -> {
            showAllert(preference.getContext(), R.string.confirm_clear_favorite,
                    (dialogInterface, i) -> new BooksDBModel(
                            preference.getContext()).clearFavorite());
            return true;
        });

        preferenceClickListner("backup", preference -> {
            boolean hasPermission = (ContextCompat.checkSelfPermission(
                    Objects.requireNonNull(getContext()),
                    Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED);
            if (!hasPermission) {
                ActivityCompat.requestPermissions(getActivity(),
                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                        REQUEST_WRITE_STORAGE);
            } else {
                showAllert(preference.getContext(), R.string.confirm_backup,
                        mOnClickListener);
            }
            return true;
        });

        preferenceClickListner("restore", preference -> {

            if (!EasyPermissions.hasPermissions(getActivity(),
                    Manifest.permission.READ_EXTERNAL_STORAGE)) {
                EasyPermissions.requestPermissions(this,
                        getString(R.string.permission_read_external_storage),
                        EXTERNAL_STORAGE_PERMISSION_REQUEST_CODE,
                        Manifest.permission.READ_EXTERNAL_STORAGE);
            } else {
                showAllert(preference.getContext(), R.string.confirm_restore,
                        (dialogInterface, i) -> {
                            Intent chooseFile = new Intent(Intent.ACTION_GET_CONTENT);
                            chooseFile.setType("application/octet-stream");
                            chooseFile = Intent.createChooser(chooseFile,
                                    getString(R.string.choose_file));
                            startActivityForResult(chooseFile, CODE_RESTORE);
                        });
            }

            return true;
        });


        /*findPreference("pref_lang").setOnPreferenceChangeListener(
                (preference, newValue) -> {
                    LocaleManager.setLocale(getContext(), newValue.toString());
                    getActivity().recreate();
                    return true;
                });*/
    }

    private void preferenceClickListner(@NotNull String key,
            @NotNull Preference.OnPreferenceClickListener click) {
        Preference preference = findPreference(key);
        if (preference != null) {
            preference.setOnPreferenceClickListener(click);
        }
    }

    private void preferenceChangeListner(@NotNull String key,
            @NotNull Preference.OnPreferenceChangeListener change) {
        Preference preference = findPreference(key);
        if (preference != null) {
            preference.setOnPreferenceChangeListener(change);
        }
    }

    private void showAllert(@NotNull Context context, int message,
            DialogInterface.OnClickListener onClickListener) {
        AlertDialog.Builder alert = new AlertDialog.Builder(context);
        alert.setTitle(R.string.app_name);
        alert.setMessage(message);
        alert.setPositiveButton(R.string.yes, onClickListener);
        alert.setCancelable(true);
        alert.setNegativeButton(R.string.cancel, null);
        alert.show();
    }

    @Override
    public void onResume() {
        super.onResume();
        // Set up a listener whenever a key changes
        getPreferenceScreen().getSharedPreferences()
                .registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onPause() {
        super.onPause();
        // Unregister the listener whenever a key changes
        getPreferenceScreen().getSharedPreferences()
                .unregisterOnSharedPreferenceChangeListener(this);
    }


    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
            String key) {
        updatePrefSummary(findPreference(key));
    }

    private void initSummary(Preference p) {
        if (p instanceof PreferenceGroup) {
            PreferenceGroup pGrp = (PreferenceGroup) p;
            for (int i = 0; i < pGrp.getPreferenceCount(); i++) {
                initSummary(pGrp.getPreference(i));
            }
        } else {
            updatePrefSummary(p);
        }
    }

    private void updatePrefSummary(Preference p) {
        if (p instanceof ListPreference) {
            ListPreference listPref = (ListPreference) p;
            p.setSummary(listPref.getEntry());
        }
        if (p instanceof EditTextPreference) {
            EditTextPreference editTextPref = (EditTextPreference) p;
            if (p.getTitle().toString().toLowerCase().contains("password")) {
                p.setSummary("******");
            } else {
                p.setSummary(editTextPref.getText());
            }
        }
        if (p instanceof MultiSelectListPreference) {
            EditTextPreference editTextPref = (EditTextPreference) p;
            p.setSummary(editTextPref.getText());
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {

        if (data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                if (requestCode == CODE_RESTORE) {
                    if (restore(readTextFile(uri))) {
                        Toast.makeText(getContext(), getString(R.string.restored),
                                Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(getContext(), getString(R.string.error_restored),
                                Toast.LENGTH_SHORT).show();
                    }
                }
            }
        }


        super.onActivityResult(requestCode, resultCode, data);
    }


    private boolean backup(@NotNull String dir) {
        BackupPOJO backup = new BackupPOJO();
        BooksDBModel booksDBModel = new BooksDBModel(getContext());
        AudioDBModel audioDBModel = new AudioDBModel(getContext());
        backup.setBooksFavorite(booksDBModel.getAllFavorite());
        backup.setBooksHistory(booksDBModel.getAllHistory());
        backup.setAudio(audioDBModel.getAll());
        GsonBuilder builder = new GsonBuilder();
        Gson gson = builder.create();
        FileWriter writer;
        Date dateNow = new Date();
        SimpleDateFormat formatForDateNow = new SimpleDateFormat("'Backup_'yyyy_MM_dd_HH_mm_ss",
                Locale.forLanguageTag("UK"));
        try {
            writer = new FileWriter(dir + "/" + formatForDateNow.format(dateNow) + ".json");
            writer.write(gson.toJson(backup));
            writer.close();
        } catch (IOException e) {
            return false;
        }
        return true;
    }

    private boolean restore(@NotNull String file) {
        try {
            Gson gson = new Gson();
            BackupPOJO backup = gson.fromJson(file, BackupPOJO.class);
            BooksDBModel booksDBModel = new BooksDBModel(getContext());
            AudioDBModel audioDBModel = new AudioDBModel(getContext());
            booksDBModel.clearFavorite();
            if (backup.getBooksFavorite() != null) {
                for (int i = 0; i < backup.getBooksFavorite().size(); i++) {
                    booksDBModel.addFavorite(backup.getBooksFavorite().get(
                            backup.getBooksFavorite().size() - 1 - i));
                }
            }

            booksDBModel.clearHistory();
            if (backup.getBooksHistory() != null) {
                for (int i = 0; i < backup.getBooksHistory().size(); i++) {
                    booksDBModel.addHistory(
                            backup.getBooksHistory().get(backup.getBooksHistory().size() - 1 - i));
                }
            }

            audioDBModel.clearAll();
            if (backup.getAudio() != null) {
                for (int i = 0; i < backup.getAudio().size(); i++) {
                    audioDBModel.add(backup.getAudio().get(backup.getAudio().size() - 1 - i));
                }
            }
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    private String readTextFile(Uri uri) {
        BufferedReader reader = null;
        StringBuilder builder = new StringBuilder();
        try {
            reader = new BufferedReader(new InputStreamReader(
                    Objects.requireNonNull(Objects.requireNonNull(
                            getContext()).getContentResolver().openInputStream(uri))));
            String line;

            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }

        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return builder.toString();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NotNull String[] permissions,
            @NotNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_WRITE_STORAGE) {
            if (grantResults.length <= 0 || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(getContext(),
                        getString(R.string.worning_not_allowed_write_storege),
                        Toast.LENGTH_LONG).show();
            } else {
                showAllert(Objects.requireNonNull(getContext()), R.string.confirm_backup,
                        mOnClickListener);
            }
        }
    }


    @Override
    public void onPermissionsGranted(int requestCode, List<String> perms) {
        if (requestCode == EXTERNAL_STORAGE_PERMISSION_REQUEST_CODE) {
            showAllert(Objects.requireNonNull(getContext()), R.string.confirm_restore,
                    (dialogInterface, i) -> {
                        Intent chooseFile = new Intent(Intent.ACTION_GET_CONTENT);
                        chooseFile.setType("application/octet-stream");
                        chooseFile = Intent.createChooser(chooseFile,
                                getString(R.string.choose_file));
                        startActivityForResult(chooseFile, CODE_RESTORE);
                    });
        }
    }

    @Override
    public void onPermissionsDenied(int requestCode, List<String> perms) {
        if (requestCode == EXTERNAL_STORAGE_PERMISSION_REQUEST_CODE) {
            Toast.makeText(getContext(), getString(R.string.worning_not_allowed_read_storege),
                    Toast.LENGTH_SHORT).show();
        }
    }
}
