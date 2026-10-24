package com.fanok.audiobooks.fragment;

import static android.content.Context.UI_MODE_SERVICE;
import static android.os.Build.VERSION.SDK_INT;
import static android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.UiModeManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.PowerManager;
import android.provider.SearchRecentSuggestions;
import android.util.Log;
import android.util.TypedValue;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.media3.common.util.UnstableApi;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.MultiSelectListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceGroup;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceScreen;
import com.fanok.audiobooks.AppThemes;
import com.fanok.audiobooks.Consts;
import com.fanok.audiobooks.MySuggestionProvider;
import com.fanok.audiobooks.R;
import com.fanok.audiobooks.activity.ActivityCookies;
import com.fanok.audiobooks.activity.ActivityImport;
import com.fanok.audiobooks.activity.ClearSavedActivity;
import com.fanok.audiobooks.activity.MainActivity;
import com.fanok.audiobooks.activity.ParentalControlActivity;
import com.fanok.audiobooks.model.AudioDBModel;
import com.fanok.audiobooks.model.AudioListDBModel;
import com.fanok.audiobooks.model.BooksDBModel;
import com.fanok.audiobooks.pojo.BackupPOJO;
import com.fanok.audiobooks.pojo.BookPOJO;
import com.fanok.audiobooks.pojo.StorageUtil;
import com.fanok.audiobooks.util.DownloadStorageHelper;
import com.google.android.material.navigation.NavigationView;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.jetbrains.annotations.NotNull;
import pub.devrel.easypermissions.EasyPermissions;

public class SettingsFragment extends PreferenceFragmentCompat implements
        SharedPreferences.OnSharedPreferenceChangeListener, EasyPermissions.PermissionCallbacks {


    private static final int EXTERNAL_STORAGE_PERMISSION_REQUEST_CODE = 342;


    private final ActivityResultLauncher<Intent> createBackupLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == android.app.Activity.RESULT_OK && result.getData() != null && result.getData().getData() != null) {
                    Uri uri = result.getData().getData();
                    Context context = getContext();
                    if (context == null) return;
                    new Thread(() -> {
                        int skipped = -1;
                        try (java.io.OutputStream os = context.getContentResolver().openOutputStream(uri)) {
                            if (os != null) {
                                skipped = performBackupToStream(context, os);
                            }
                        } catch (Exception e) {
                            Log.e("SettingsFragment", "Error writing backup to URI", e);
                        }
                        int finalSkipped = skipped;
                        if (getActivity() != null) {
                            getActivity().runOnUiThread(() -> {
                                Toast.makeText(getContext(), backupResultMessage(finalSkipped),
                                        Toast.LENGTH_SHORT).show();
                            });
                        }
                    }).start();
                }
            });

    private final ActivityResultLauncher<Intent> restoreBackupLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == android.app.Activity.RESULT_OK && result.getData() != null && result.getData().getData() != null) {
                    Uri uri = result.getData().getData();
                    Context context = getContext();
                    if (context == null) return;
                    new Thread(() -> {
                        boolean success = false;
                        try (java.io.InputStream is = context.getContentResolver().openInputStream(uri)) {
                            if (is != null) {
                                success = performRestoreFromStream(context, is);
                            }
                        } catch (Exception e) {
                            Log.e("SettingsFragment", "Error reading restore from URI", e);
                        }
                        boolean finalSuccess = success;
                        if (getActivity() != null) {
                            getActivity().runOnUiThread(() -> {
                                Toast.makeText(getContext(),
                                        finalSuccess ? getString(R.string.restored) : getString(R.string.error_restored),
                                        Toast.LENGTH_SHORT).show();
                            });
                        }
                    }).start();
                }
            });

    private final DialogInterface.OnClickListener mOnClickListener = (dialogInterface, i) -> {
        try {
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/json");
            SimpleDateFormat formatForDateNow = new SimpleDateFormat("'AudioBook_backup_'yyyy_MM_dd_HH_mm_ss'.json'", Locale.forLanguageTag("UK"));
            intent.putExtra(Intent.EXTRA_TITLE, formatForDateNow.format(new Date()));
            createBackupLauncher.launch(intent);
        } catch (Exception e) {
            Context context = getContext();
            if (context != null) {
                new Thread(() -> {
                    String path = DownloadStorageHelper.getDownloadPath(context);
                    int skipped = backup(context, path);
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            Toast.makeText(getContext(), backupResultMessage(skipped),
                                    Toast.LENGTH_SHORT).show();
                        });
                    }
                }).start();
            }
        }
    };

    private final DialogInterface.OnClickListener restoreClickListnere = (dialogInterface, i) -> {
        try {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            restoreBackupLauncher.launch(intent);
        } catch (Exception e) {
            e.printStackTrace();
        }
    };

    @Override
    public void onNavigateToScreen(PreferenceScreen preferenceScreen) {
        SettingsFragment settingsFragment = new SettingsFragment();
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
    public void onResume() {
        super.onResume();
        // Set up a listener whenever a key changes
        getPreferenceScreen().getSharedPreferences()
                .registerOnSharedPreferenceChangeListener(this);

        MainActivity activity = (MainActivity) getActivity();
        if (activity != null) {
            NavigationView navigationView = activity.getNavigationView();
            ArrayList<TextView> mTextViewArrayList = activity.getTextViewArrayList();
            final TypedValue SelectedValue = new TypedValue();
            activity.getTheme().resolveAttribute(R.attr.mySelectableItemBackground, SelectedValue,
                    true);
            if (navigationView != null) {
                navigationView.setCheckedItem(R.id.nav_settings);
            } else if (mTextViewArrayList != null && mTextViewArrayList.size() > 8) {
                mTextViewArrayList.get(8).setBackgroundResource(SelectedValue.resourceId);
            }
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        // Unregister the listener whenever a key changes
        getPreferenceScreen().getSharedPreferences()
                .unregisterOnSharedPreferenceChangeListener(this);
    }

    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted || Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                   showAllert(requireContext(), R.string.confirm_backup, mOnClickListener);
                } else {
                    Toast.makeText(getContext(),
                            getString(R.string.worning_not_allowed_write_storege),
                            Toast.LENGTH_LONG).show();
                }
            });


    @UnstableApi
    @SuppressLint("InlinedApi")
    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        if (getArguments() != null) {
            String key = getArguments().getString("rootKey");
            setPreferencesFromResource(R.xml.preferences, key);
        } else {
            setPreferencesFromResource(R.xml.preferences, rootKey);
        }
        requireActivity().setTitle(R.string.menu_settings);
        PreferenceManager.setDefaultValues(
                requireActivity().getApplicationContext(), R.xml.preferences,
                false);
        initSummary(getPreferenceScreen());

        preferenceChangeListner("pref_theme", (preference, newValue) -> {
            // the value is stored after this returns; apply night mode and restart once it is
            Context context = requireContext().getApplicationContext();
            requireActivity().getWindow().getDecorView().post(() -> {
                AppThemes.applyNightMode(context);
                if (getActivity() != null) {
                    // dark and black share a night mode, so the restart is not implied
                    getActivity().recreate();
                }
            });
            return true;
        });

        preferenceChangeListner("bufferSize", (preference, newValue) -> {
            String value = (String) newValue;
            if (value.equals("0")) return false;
            return value.matches("\\d{1,3}");
        });

        preferenceChangeListner("sorce_books", (preference, newValue) -> {
            Consts.setSOURCE(requireContext(), newValue.toString());
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

        // database work on the storage worker, never on the main thread (DATA-07)
        preferenceClickListner("clear_history", preference -> {
            final Context app = preference.getContext().getApplicationContext();
            showAllert(preference.getContext(), R.string.confirm_clear_history,
                    (dialogInterface, i) -> DownloadStorageHelper.submit(() -> {
                        BooksDBModel booksDBModel = new BooksDBModel(app);
                        booksDBModel.clearHistory();
                        booksDBModel.closeDB();
                        AudioDBModel audioDBModel = new AudioDBModel(app);
                        audioDBModel.clearAll();
                        audioDBModel.closeDB();
                    }));
            return true;
        });

        preferenceClickListner("clear_favorite", preference -> {
            final Context app = preference.getContext().getApplicationContext();
            showAllert(preference.getContext(), R.string.confirm_clear_favorite,
                    (dialogInterface, i) -> DownloadStorageHelper.submit(() -> {
                        BooksDBModel dbModel = new BooksDBModel(app);
                        dbModel.clearFavorite();
                        dbModel.closeDB();
                    }));
            return true;
        });

        preferenceClickListner("clear_audio_list", preference -> {
            final Context app = preference.getContext().getApplicationContext();
            showAllert(preference.getContext(), R.string.confirm_clear_audio_list,
                    (dialogInterface, i) -> DownloadStorageHelper.submit(() -> {
                        AudioListDBModel dbModel = new AudioListDBModel(app);
                        dbModel.clearAll();
                        dbModel.closeDB();
                    }));
            return true;
        });

        preferenceClickListner("clear_savind", preference -> {
            final Context app = preference.getContext().getApplicationContext();
            showAllert(preference.getContext(), R.string.confirm_clear_saved,
                    (dialogInterface, i) -> DownloadStorageHelper.submit(() -> {
                        // every saved book: rows and recorded copies; imported books lose rows only
                        BooksDBModel dbModel = new BooksDBModel(app);
                        List<String> urls = new ArrayList<>();
                        for (BookPOJO book : dbModel.getAllSaved()) {
                            if (book.getUrl() != null) {
                                urls.add(book.getUrl());
                            }
                        }
                        dbModel.closeDB();
                        DownloadStorageHelper.removeBooks(app, urls);
                    }));
            return true;
        });

        preferenceClickListner("parentalControl", preference -> {
            startActivity(new Intent(getContext(), ParentalControlActivity.class));
            return true;
        });

        preferenceClickListner("backup", preference -> {
            try {
                Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("application/json");
                SimpleDateFormat formatForDateNow = new SimpleDateFormat("'AudioBook_backup_'yyyy_MM_dd_HH_mm_ss'.json'", Locale.forLanguageTag("UK"));
                intent.putExtra(Intent.EXTRA_TITLE, formatForDateNow.format(new Date()));
                createBackupLauncher.launch(intent);
            } catch (Exception e) {
                Toast.makeText(requireContext(), R.string.backup_not_created, Toast.LENGTH_SHORT).show();
            }
            return true;
        });

        preferenceClickListner("cookies", preference -> {
            startActivity(new Intent(getContext(), ActivityCookies.class));
            return true;
        });

        boolean ignor;

        PowerManager pm = (PowerManager) requireContext().getSystemService(
                Context.POWER_SERVICE);
        if (pm != null) {

            UiModeManager uiModeManager = (UiModeManager) requireContext().getSystemService(
                    UI_MODE_SERVICE);

            if (uiModeManager == null || uiModeManager.getCurrentModeType()
                    == Configuration.UI_MODE_TYPE_TELEVISION) {
                ignor = true;
            } else {
                ignor = pm.isIgnoringBatteryOptimizations("com.fanok.audiobooks");
            }
        } else {
            ignor = true;
        }

        if (ignor) {
            Preference preference = findPreference("reqest_ignor_battery_optimizetion");
            if (preference != null) {
                preference.setVisible(false);
            }
        } else {
            preferenceClickListner("reqest_ignor_battery_optimizetion",
                    preference -> {
                        try {
                            startActivity(new Intent(ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
                        } catch (ActivityNotFoundException e) {
                            new StorageUtil(requireContext()).storeBattaryOptimizeDisenbled(true);
                        }
                        return true;
                    });
        }

        preferenceClickListner("restore", preference -> {
            try {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("*/*");
                restoreBackupLauncher.launch(intent);
            } catch (Exception e) {
                Toast.makeText(requireContext(), R.string.error_restored, Toast.LENGTH_SHORT).show();
            }
            return true;
        });

        preferenceClickListner("import_kniga_v_uhe", preference -> {
            ActivityImport.startActivity(requireActivity(), Consts.IMPORT_SITE_KNIGA_V_UHE);
            return true;
        });

        preferenceClickListner("import_abook", preference -> {
            ActivityImport.startActivity(requireActivity(), Consts.IMPORT_SITE_ABOOK);
            return true;
        });

        preferenceClickListner("download", preference -> {
            ClearSavedActivity.startActivity(requireActivity());
            return true;
        });

        Preference downloadPathPref = findPreference(DownloadStorageHelper.PREF_DOWNLOAD_PATH);
        if (downloadPathPref != null) {
            downloadPathPref.setSummary(DownloadStorageHelper.getDownloadPath(requireContext()));
        }

        preferenceClickListner("pref_download_path", preference -> {
            showDownloadFolderDialog();
            return true;
        });
    }

    private void showDownloadFolderDialog() {
        Context context = getContext();
        if (context == null) return;

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 101);
            }
        }

        CharSequence[] options = new CharSequence[]{"Выбрать папку (Проводник)", "Сбросить на стандартную"};
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Папка для скачивания");
        builder.setItems(options, (dialog, which) -> {
            if (which == 0) {
                if (!DownloadStorageHelper.openFolderPicker(folderPickerLauncher)) {
                    showFallbackFolderSelectionDialog(context);
                }
            } else if (which == 1) {
                DownloadStorageHelper.resetToDefault(context);
                Preference pref = findPreference(DownloadStorageHelper.PREF_DOWNLOAD_PATH);
                if (pref != null) {
                    pref.setSummary(DownloadStorageHelper.getDownloadPath(context));
                }
                DownloadStorageHelper.scanAndImportDownloadedBooks(context.getApplicationContext());
                Toast.makeText(context, "Сброшено на стандартную папку", Toast.LENGTH_SHORT).show();
            }
        });
        builder.show();
    }

    private void showFallbackFolderSelectionDialog(Context context) {
        java.util.List<DownloadStorageHelper.StorageOption> options = DownloadStorageHelper.getAvailableStorageOptions(context);
        ArrayList<String> titles = new ArrayList<>();
        for (DownloadStorageHelper.StorageOption opt : options) {
            titles.add(opt.title + "\n" + opt.path);
        }
        titles.add("✏️ Указать свой путь вручную");

        new AlertDialog.Builder(context)
                .setTitle("Выберите место для скачивания книг")
                .setItems(titles.toArray(new CharSequence[0]), (dialog, which) -> {
                    if (which < options.size()) {
                        DownloadStorageHelper.StorageOption selected = options.get(which);
                        File dir = new File(selected.path);
                        if (!dir.exists()) {
                            dir.mkdirs();
                        }
                        DownloadStorageHelper.saveDownloadPath(context, selected.path);
                        Preference pref = findPreference(DownloadStorageHelper.PREF_DOWNLOAD_PATH);
                        if (pref != null) {
                            pref.setSummary(DownloadStorageHelper.getDownloadPath(context));
                        }
                        DownloadStorageHelper.scanAndImportDownloadedBooks(context.getApplicationContext());
                        Toast.makeText(context, "Папка установлена: " + selected.path, Toast.LENGTH_LONG).show();
                    } else {
                        showManualPathInputDialog(context);
                    }
                })
                .show();
    }

    private void showManualPathInputDialog(Context context) {
        android.widget.EditText input = new android.widget.EditText(context);
        input.setHint("/storage/emulated/0/Download/AudioBooks");
        input.setText(DownloadStorageHelper.getDownloadPath(context));
        new AlertDialog.Builder(context)
                .setTitle("Укажите путь к папке")
                .setView(input)
                .setPositiveButton("ОК", (dialog, which) -> {
                    String path = input.getText().toString().trim();
                    if (!path.isEmpty()) {
                        File dir = new File(path);
                        if (!dir.exists()) {
                            dir.mkdirs();
                        }
                        DownloadStorageHelper.saveDownloadPath(context, path);
                        Preference pref = findPreference(DownloadStorageHelper.PREF_DOWNLOAD_PATH);
                        if (pref != null) {
                            pref.setSummary(DownloadStorageHelper.getDownloadPath(context));
                        }
                        DownloadStorageHelper.scanAndImportDownloadedBooks(context.getApplicationContext());
                        Toast.makeText(context, "Папка установлена: " + path, Toast.LENGTH_LONG).show();
                    }
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private final ActivityResultLauncher<Intent> folderPickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                try {
                    if (result.getResultCode() == android.app.Activity.RESULT_OK && result.getData() != null) {
                        Context context = getContext();
                        if (context != null) {
                            DownloadStorageHelper.saveDownloadUri(context.getApplicationContext(), result.getData());
                            Preference pref = findPreference(DownloadStorageHelper.PREF_DOWNLOAD_PATH);
                            if (pref != null) {
                                pref.setSummary(DownloadStorageHelper.getDownloadPath(context));
                            }
                            DownloadStorageHelper.scanAndImportDownloadedBooks(context.getApplicationContext());
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });

    @Override
    public void onPermissionsGranted(int requestCode, @NonNull List<String> perms) {
        if (requestCode == EXTERNAL_STORAGE_PERMISSION_REQUEST_CODE) {
            showAllert(requireContext(), R.string.confirm_restore,
                    restoreClickListnere);
        }
    }

    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
            String key) {
        updatePrefSummary(findPreference(key));
    }

    /** Writes a backup; returns the number of unreadable rows left out, or -1 when nothing was written. */
    private int performBackupToStream(Context context, java.io.OutputStream os) {
        try {
            BackupPOJO backup = createBackup(context);
            Gson gson = new GsonBuilder().create();
            try (java.io.OutputStreamWriter writer = new java.io.OutputStreamWriter(os, java.nio.charset.StandardCharsets.UTF_8)) {
                writer.write(gson.toJson(backup));
                writer.flush();
            }
            return backup.getSkippedRows();
        } catch (Exception e) {
            Log.e("SettingsFragment", "Backup error", e);
            return -1;
        }
    }

    /** Every table restore reads, plus the format version; rows that cannot be read are skipped and counted. */
    private static BackupPOJO createBackup(Context context) {
        BooksDBModel booksDBModel = new BooksDBModel(context);
        AudioDBModel audioDBModel = new AudioDBModel(context);
        AudioListDBModel audioListDBModel = new AudioListDBModel(context);
        BackupPOJO backup = new BackupPOJO();
        backup.setBooksFavorite(booksDBModel.getAllFavorite());
        backup.setBooksHistory(booksDBModel.getAllHistory());
        backup.setBooksSaved(booksDBModel.getAllSaved());
        backup.setAudio(audioDBModel.getAll());
        backup.setAudioList(audioListDBModel.getAll());
        backup.setSkippedRows(booksDBModel.getSkippedRows() + audioDBModel.getSkippedRows());
        if (backup.getSkippedRows() > 0) {
            Log.w("SettingsFragment", "Backup left out " + backup.getSkippedRows() + " unreadable rows");
        }
        return backup;
    }

    private String backupResultMessage(int skipped) {
        if (skipped > 0) return getString(R.string.backup_created_skipped, skipped);
        return getString(skipped == 0 ? R.string.backup_created : R.string.backup_not_created);
    }

    private boolean performRestoreFromStream(Context context, java.io.InputStream is) {
        try {
            StringBuilder sb = new StringBuilder();
            try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(is, java.nio.charset.StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append("\n");
                }
            }
            return restore(context, sb.toString());
        } catch (Exception e) {
            Log.e("SettingsFragment", "Restore error", e);
            return false;
        }
    }

    /** Returns the number of unreadable rows left out of the written file, or -1 when nothing was written. */
    private int backup(@NotNull Context context, @NotNull String dir) {
        BackupPOJO backup = createBackup(context);
        GsonBuilder builder = new GsonBuilder();
        Gson gson = builder.create();
        FileWriter writer;
        Date dateNow = new Date();
        SimpleDateFormat formatForDateNow = new SimpleDateFormat(
                "'AudioBook_backup_'yyyy_MM_dd_HH_mm_ss",
                Locale.forLanguageTag("UK"));

        String name = dir + "/" + formatForDateNow.format(dateNow) + ".json";

        try {
            File targetDir = new File(dir);
            if (!targetDir.exists()) {
                //noinspection ResultOfMethodCallIgnored
                targetDir.mkdirs();
            }
            writer = new FileWriter(name);
            writer.write(gson.toJson(backup));
            writer.close();
        } catch (IOException e) {
            try {
                Context ctx = getContext();
                if (ctx != null) {
                    File fallbackDir = ctx.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
                    if (fallbackDir != null) {
                        if (!fallbackDir.exists()) {
                            //noinspection ResultOfMethodCallIgnored
                            fallbackDir.mkdirs();
                        }
                        name = fallbackDir.getAbsolutePath() + "/" + formatForDateNow.format(dateNow) + ".json";
                        writer = new FileWriter(name);
                        writer.write(gson.toJson(backup));
                        writer.close();
                        return backup.getSkippedRows();
                    }
                }
            } catch (Exception ignored) {}
            return -1;
        }

        return backup.getSkippedRows();
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

    private void preferenceChangeListner(@NotNull String key,
            @NotNull Preference.OnPreferenceChangeListener change) {
        Preference preference = findPreference(key);
        if (preference != null) {
            preference.setOnPreferenceChangeListener(change);
        }
    }

    private void preferenceClickListner(@NotNull String key,
            @NotNull Preference.OnPreferenceClickListener click) {
        Preference preference = findPreference(key);
        if (preference != null) {
            preference.setOnPreferenceClickListener(click);
        }
    }

    private String readTextFile(File file) {
        StringBuilder myData = new StringBuilder();
        try {
            FileInputStream fis = new FileInputStream(file);
            DataInputStream in = new DataInputStream(fis);
            BufferedReader br = new BufferedReader(new InputStreamReader(in));

            String strLine;
            while ((strLine = br.readLine()) != null) {
                myData.append(strLine).append("\n");
            }
            br.close();
            in.close();
            fis.close();
        } catch (IOException e) {
            Log.e("login activity", "Can not read file: " + e);
        }
        return myData.toString();
    }

    /**
     * Validates the whole document first, then replaces every table it carries in one transaction, so a bad
     * file or a failed write leaves the database as it was. A table whose collection is absent is not touched.
     */
    private boolean restore(@NotNull Context context, @NotNull String file) {
        final BackupPOJO backup;
        try {
            backup = BackupPOJO.parse(file);
        } catch (BackupPOJO.InvalidBackupException e) {
            Log.w("SettingsFragment", "Backup rejected", e);
            return false;
        }
        BooksDBModel booksDBModel = new BooksDBModel(context);
        AudioDBModel audioDBModel = new AudioDBModel(context);
        AudioListDBModel audioListDBModel = new AudioListDBModel(context);
        com.fanok.audiobooks.room.AppDatabase database = booksDBModel.getDatabase();
        try {
            database.runInTransaction(() -> {
                if (backup.getBooksFavorite() != null) {
                    database.favoriteDao().deleteAll();
                    for (int i = 0; i < backup.getBooksFavorite().size(); i++) {
                        booksDBModel.addFavorite(backup.getBooksFavorite().get(
                                backup.getBooksFavorite().size() - 1 - i));
                    }
                }

                if (backup.getBooksHistory() != null) {
                    database.historyDao().deleteAll();
                    for (int i = 0; i < backup.getBooksHistory().size(); i++) {
                        booksDBModel.addHistory(
                                backup.getBooksHistory().get(backup.getBooksHistory().size() - 1 - i));
                    }
                }

                if (backup.getBooksSaved() != null) {
                    database.savedDao().deleteAll();
                    for (int i = 0; i < backup.getBooksSaved().size(); i++) {
                        booksDBModel.addSaved(
                                backup.getBooksSaved().get(backup.getBooksSaved().size() - 1 - i));
                    }
                }

                if (backup.getAudio() != null) {
                    database.audioDao().deleteAll();
                    for (int i = 0; i < backup.getAudio().size(); i++) {
                        audioDBModel.add(backup.getAudio().get(backup.getAudio().size() - 1 - i));
                    }
                }

                if (backup.getAudioList() != null) {
                    audioListDBModel.clearAll();
                    for (int i = 0; i < backup.getAudioList().size(); i++) {
                        audioListDBModel.add(backup.getAudioList().get(i));
                    }
                }
            });
            return true;
        } catch (RuntimeException e) {
            Log.e("SettingsFragment", "Restore failed, database left unchanged", e);
            return false;
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

    private void updatePrefSummary(Preference p) {
        if (p instanceof ListPreference) {
            ListPreference listPref = (ListPreference) p;
            p.setSummary(listPref.getEntry());
        } else if (p instanceof EditTextPreference) {
            EditTextPreference editTextPref = (EditTextPreference) p;
            if (p.getTitle().toString().toLowerCase().contains("password")) {
                p.setSummary("******");
            } else {
                p.setSummary(editTextPref.getText());
            }
        } else if (p instanceof MultiSelectListPreference) {
            MultiSelectListPreference multiSelectPref = (MultiSelectListPreference) p;
            Set<String> values = multiSelectPref.getValues();
            CharSequence[] entries = multiSelectPref.getEntries();
            CharSequence[] entryValues = multiSelectPref.getEntryValues();
            StringBuilder summary = new StringBuilder();
            boolean isFirst = true;
            for (String value : values) {
                for (int i = 0; i < entryValues.length; i++) {
                    if (entryValues[i].equals(value)) {
                        if (!isFirst) {
                            summary.append(", ");
                        }
                        summary.append(entries[i]);
                        isFirst = false;
                        break;
                    }
                }
            }
            p.setSummary(summary.toString());
        }
    }

    @Override
    public void onPermissionsDenied(int requestCode, @NonNull List<String> perms) {
        if (requestCode == EXTERNAL_STORAGE_PERMISSION_REQUEST_CODE) {
            Toast.makeText(getContext(), getString(R.string.worning_not_allowed_read_storege),
                    Toast.LENGTH_SHORT).show();
        }
    }

}
