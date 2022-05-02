package com.fanok.audiobooks.fragment;

import static android.content.Context.UI_MODE_SERVICE;
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
import android.util.TypedValue;
import android.widget.TextView;
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
import com.fanok.audiobooks.Consts;
import com.fanok.audiobooks.MySuggestionProvider;
import com.fanok.audiobooks.R;
import com.fanok.audiobooks.activity.ActivityImport;
import com.fanok.audiobooks.activity.MainActivity;
import com.fanok.audiobooks.activity.ParentalControlActivity;
import com.fanok.audiobooks.activity.PopupClearSaved;
import com.fanok.audiobooks.model.AudioDBModel;
import com.fanok.audiobooks.model.AudioListDBModel;
import com.fanok.audiobooks.model.BooksDBModel;
import com.fanok.audiobooks.pojo.BackupPOJO;
import com.fanok.audiobooks.pojo.StorageUtil;
import com.google.android.material.navigation.NavigationView;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import pub.devrel.easypermissions.EasyPermissions;

public class SettingsFragment extends PreferenceFragmentCompat implements
        SharedPreferences.OnSharedPreferenceChangeListener, EasyPermissions.PermissionCallbacks {
    private static final int CODE_RESTORE = 292;
    private static final int REQUEST_WRITE_STORAGE = 112;
    private static final int EXTERNAL_STORAGE_PERMISSION_REQUEST_CODE = 342;


    private final DialogInterface.OnClickListener mOnClickListener = (dialogInterface, i) -> {


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
                .getDefaultSharedPreferences(getActivity().getApplicationContext());
        String themeName = pref.getString("pref_theme", getString(R.string.theme_light_value));
        if (themeName.equals(getString(R.string.theme_dark_value))) {
            StorageChooser.Theme theme = new StorageChooser.Theme(
                    Objects.requireNonNull(getContext()).getApplicationContext());
            theme.setScheme(getResources().getIntArray(R.array.paranoid_theme));
            builder.setTheme(theme);
        }

        StorageChooser chooser = builder.build();


        chooser.setOnSelectListener(path -> {
            String filePath = backup(path);
            if (filePath != null) {
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
        if (getActivity() != null) {
            getActivity().getSupportFragmentManager()
                    .beginTransaction()
                    .replace(getId(), settingsFragment)
                    .addToBackStack(null)
                    .commit();
        }
    }

    @SuppressLint("InlinedApi")
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

        /*preferenceChangeListner("pref_proxy",
                (preference, newValue) -> {
                    if(!validateIP((String) newValue)){
                        Toast.makeText(preference.getContext(), R.string
                        .incorect_fomat_proxy_server, Toast.LENGTH_LONG).show();
                        return false;
                    }else return true;
                });

        preferenceChangeListner("pref_proxy_prot", new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                if(!validatePORT((String) newValue)){
                    Toast.makeText(preference.getContext(), R.string.incorect_fomat_port, Toast
                    .LENGTH_LONG).show();
                    return false;
                }else return true;
            }
        });

        preferenceChangeListner("proxy_enabled", (preference, newValue) -> {
            setProxy((Boolean) newValue);
            return true;
        });

        SharedPreferences pref = PreferenceManager
                .getDefaultSharedPreferences(Objects.requireNonNull(getContext()));
        setProxy(pref.getBoolean("proxy_enabled", false));


*/
        preferenceChangeListner("pref_theme", (preference, newValue) -> {
            if (newValue.equals(getString(R.string.theme_dark_value)) || newValue
                    .equals(getString(R.string.theme_black_value))) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
            } else {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
            }
            getActivity().recreate();
            return true;
        });

        preferenceChangeListner("bufferSize", (preference, newValue) -> {
            String value = (String) newValue;
            if (value.equals("0")) return false;
            return value.matches("\\d{1,3}");
        });

        preferenceChangeListner("pref_downland_path", (preference, newValue) -> {

            if (newValue.equals(getString(R.string.dir_value_sdcrd))) {
                File[] folders = Objects.requireNonNull(getContext()).getExternalFilesDirs(null);
                if (folders.length == 1) {
                    Toast.makeText(getContext(), R.string.no_sdcard, Toast.LENGTH_SHORT).show();
                    SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(
                            getActivity().getApplicationContext());
                    SharedPreferences.Editor editor = sharedPref.edit();
                    editor.putString("pref_downland_path",
                            getContext().getString(R.string.dir_value_emulated));
                    editor.apply();
                    return false;
                }
            }
            return true;
        });

        preferenceChangeListner("sorce_books", (preference, newValue) -> {
            Consts.setSOURCE(Objects.requireNonNull(getContext()), newValue.toString());
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
                        BooksDBModel booksDBModel = new BooksDBModel(preference.getContext());
                        booksDBModel.clearHistory();
                        booksDBModel.closeDB();
                        AudioDBModel audioDBModel = new AudioDBModel(preference.getContext());
                        audioDBModel.clearAll();
                        audioDBModel.closeDB();
                    });
            return true;
        });

        preferenceClickListner("clear_favorite", preference -> {
            showAllert(preference.getContext(), R.string.confirm_clear_favorite,
                    (dialogInterface, i) -> {
                        BooksDBModel dbModel = new BooksDBModel(
                                preference.getContext());
                        dbModel.clearFavorite();
                        dbModel.closeDB();
                    });
            return true;
        });

        preferenceClickListner("clear_audio_list", preference -> {
            showAllert(preference.getContext(), R.string.confirm_clear_audio_list,
                    (dialogInterface, i) -> {
                        AudioListDBModel dbModel = new AudioListDBModel(
                                preference.getContext());
                        dbModel.clearAll();
                        dbModel.closeDB();
                    });
            return true;
        });

        preferenceClickListner("clear_savind", preference -> {
            startActivity(new Intent(getContext(), PopupClearSaved.class));
            return true;
        });

        preferenceClickListner("parentalControl", preference -> {
            startActivity(new Intent(getContext(), ParentalControlActivity.class));
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

        boolean ignor;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PowerManager pm = (PowerManager) Objects.requireNonNull(getContext()).getSystemService(
                    Context.POWER_SERVICE);
            if (pm != null) {

                UiModeManager uiModeManager = (UiModeManager) getContext().getSystemService(
                        UI_MODE_SERVICE);

                StorageUtil storageUtil = new StorageUtil(getContext());
                if (uiModeManager == null || uiModeManager.getCurrentModeType()
                        == Configuration.UI_MODE_TYPE_TELEVISION) {
                    ignor = true;
                } else {
                    ignor = pm.isIgnoringBatteryOptimizations("com.fanok.audiobooks");
                }
            } else {
                ignor = true;
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
                            new StorageUtil(Objects.requireNonNull(
                                    getContext())).storeBattaryOptimizeDisenbled(true);
                        }
                        return true;
                    });
        }

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

        preferenceClickListner("import_kniga_v_uhe", preference -> {
            ActivityImport.startActivity(Objects.requireNonNull(getActivity()),
                    Consts.IMPORT_SITE_KNIGA_V_UHE);
            return true;
        });

        preferenceClickListner("import_abook", preference -> {
            ActivityImport.startActivity(Objects.requireNonNull(getActivity()),
                    Consts.IMPORT_SITE_ABOOK);
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

        MainActivity activity = (MainActivity) getActivity();
        if (activity != null) {
            NavigationView navigationView = activity.getNavigationView();
            ArrayList<TextView> mTextViewArrayList = activity.getTextViewArrayList();
            final TypedValue SelectedValue = new TypedValue();
            activity.getTheme().resolveAttribute(R.attr.mySelectableItemBackground, SelectedValue,
                    true);
            if (navigationView != null) {
                navigationView.setCheckedItem(R.id.nav_settings);
            } else if (mTextViewArrayList != null && mTextViewArrayList.size() > 7) {
                mTextViewArrayList.get(7).setBackgroundResource(SelectedValue.resourceId);
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

    private String backup(@NotNull String dir) {
        BackupPOJO backup = new BackupPOJO();
        BooksDBModel booksDBModel = new BooksDBModel(getContext());
        AudioDBModel audioDBModel = new AudioDBModel(getContext());
        AudioListDBModel audioListDBModel = new AudioListDBModel(getContext());
        backup.setBooksFavorite(booksDBModel.getAllFavorite());
        backup.setBooksHistory(booksDBModel.getAllHistory());
        backup.setAudio(audioDBModel.getAll());
        backup.setAudioList(audioListDBModel.getAll());
        GsonBuilder builder = new GsonBuilder();
        Gson gson = builder.create();
        FileWriter writer;
        Date dateNow = new Date();
        SimpleDateFormat formatForDateNow = new SimpleDateFormat(
                "'AudioBook_backup_'yyyy_MM_dd_HH_mm_ss",
                Locale.forLanguageTag("UK"));

        String name = dir + "/" + formatForDateNow.format(dateNow) + ".json";

        booksDBModel.closeDB();
        audioDBModel.closeDB();

        try {
            writer = new FileWriter(name);
            writer.write(gson.toJson(backup));
            writer.close();
        } catch (IOException e) {
            return null;
        }

        return name;
    }

    private boolean restore(@NotNull String file) {
        BooksDBModel booksDBModel = new BooksDBModel(getContext());
        AudioDBModel audioDBModel = new AudioDBModel(getContext());
        AudioListDBModel audioListDBModel = new AudioListDBModel(getContext());
        boolean returnValue = true;
        try {
            Gson gson = new Gson();
            BackupPOJO backup = gson.fromJson(file, BackupPOJO.class);
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

            audioListDBModel.clearAll();
            if (backup.getAudioList() != null) {
                for (int i = 0; i < backup.getAudioList().size(); i++) {
                    audioListDBModel.add(backup.getAudioList().get(i));
                }
            }



        } catch (Exception e) {
            returnValue = false;
        }
        audioDBModel.closeDB();
        booksDBModel.closeDB();
        return returnValue;
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

    /*private void setProxy(boolean value){
        Preference preferenceProxy = findPreference("pref_proxy");
        if(preferenceProxy!=null){
            if(value){
                preferenceProxy.setEnabled(true);
            }else {
                preferenceProxy.setEnabled(false);
            }
        }

        Preference preferencePort = findPreference("pref_proxy_prot");
        if(preferencePort!=null){
            if(value){
                preferencePort.setEnabled(true);
            }else {
                preferencePort.setEnabled(false);
            }
        }
    }


    private boolean validateIP(@NonNull String ip) {
        if (ip.isEmpty()) return true;
        Matcher matcher = Consts.REGEXP_IP.matcher(ip);
        return matcher.find();
    }

    private boolean validatePORT(@NonNull String port) {
        if (port.isEmpty()) return true;
        Matcher matcher = Consts.REGEXP_PORT.matcher(port);
        return matcher.find();
    }*/


}
